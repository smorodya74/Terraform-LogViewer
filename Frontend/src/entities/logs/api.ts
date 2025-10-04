import { protoInt64 } from '@bufbuild/protobuf';
import { ConnectError } from '@bufbuild/connect';
import { logQueryClient, reportClient } from '../../shared/grpc/client';
import type { LogsFiltersState } from '../../shared/store/ui';
import { LogEntry, TimelineItem as UITimelineItem, HeatBucket, LogGroup } from './types';
import dayjs from '../../shared/utils/dayjs';
import { ReportFormat } from '../../gen/logviewer_pb.js';

export interface LogsQueryParams extends LogsFiltersState {
  page: number;
  pageSize: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  groupByReqId?: boolean;
}

export interface LogsResponse {
  items: LogEntry[];
  total: number;
  page: number;
  size: number;
}

export interface GroupedLogsResponse {
  groups: LogGroup[];
  totalGroups: number;
  page: number;
  size: number;
}

const mapRecord = (record: import('../../gen/logviewer_pb.js').LogRecord): LogEntry => ({
  id: record.id.toString(),
  ts: record.ts,
  level: record.level || null,
  section: record.section || null,
  module: record.module || null,
  message: record.message || null,
  req_id: record.reqId || null,
  trans_id: record.transId || null,
  rpc: record.rpc || null,
  resource_type: record.resourceType || null,
  data_source_type: record.dataSourceType || null,
  http_op_type: record.httpOpType || null,
  status_code: record.statusCode === 0 ? null : record.statusCode,
  file_name: record.fileName || null,
  import_id: record.importId || null,
  unread: record.unread,
});

const FILTER_KEYS = new Set([
  'resource_type',
  'status_code',
  'import_id',
  'req_id',
  'tf_req_id',
  'trans_id',
  'rpc',
  'data_source_type',
  'http_op_type',
]);

const normalizeFilters = (filters: LogsFiltersState) => {
  const map: Record<string, string> = {};
  Object.entries(filters).forEach(([key, value]) => {
    if (!FILTER_KEYS.has(key) || value === undefined || value === null || value === '') return;
    map[key] = String(value);
  });
  return map;
};

const buildQuery = (params: LogsQueryParams) => {
  const { page, pageSize, ...rest } = params;
  return {
    page: page - 1,
    size: pageSize,
    tsFrom: rest.ts_from ?? '',
    tsTo: rest.ts_to ?? '',
    level: rest.level ?? '',
    section: rest.section ?? '',
    unreadOnly: Boolean((rest as LogsFiltersState).unread_only),
    q: rest.q ?? '',
    filters: normalizeFilters(rest as LogsFiltersState),
    ...(rest.sortBy ? { sortBy: rest.sortBy } : {}),
    ...(rest.sortDir ? { sortDesc: rest.sortDir === 'desc' } : {}),
    ...(rest.groupByReqId ? { groupByReqId: true } : {}),
  };
};

const searchLogs = async (params: LogsQueryParams): Promise<LogsResponse> => {
  const response = await logQueryClient.search(buildQuery(params));
  return {
    items: response.items.map(mapRecord),
    total: Number(response.total),
    page: response.page,
    size: response.size,
  };
};

const searchLogGroups = async (
  params: LogsQueryParams,
): Promise<GroupedLogsResponse> => {
  const response = await logQueryClient.searchGroups({
    query: buildQuery({ ...params, groupByReqId: true }),
  });
  return {
    groups: response.groups.map((group) => ({
      req_id: group.reqId || null,
      group_first_ts: group.groupFirstTs || null,
      group_last_ts: group.groupLastTs || null,
      items: group.items.map(mapRecord),
    })),
    totalGroups: Number(response.totalGroups),
    page: response.page,
    size: response.size,
  };
};

const markLogRead = async (id: string, markRead = true) => {
  await logQueryClient.markRead({ ids: [protoInt64.parse(id)], markRead });
};

const fetchLogDetails = async (id: string) => {
  try {
    const response = await logQueryClient.getLog({ id: protoInt64.parse(id) });
    const bodies = response.bodiesCount ? await fetchBodies(id) : [];
    return {
      record: mapRecord(response.record),
      raw_json: response.rawJson,
      attrs_json: response.attrsJson,
      annotations_json: response.annotationsJson,
      bodies_count: response.bodiesCount,
      bodies,
    };
  } catch (error) {
    if (error instanceof ConnectError) {
      const raw = (error.rawMessage || '').trim();
      if (!raw || raw.toLowerCase() === 'unknown' || raw === '[unknown]') {
        throw new Error('Не удалось загрузить детали лога');
      }
      throw new Error(raw);

    }
    if (error instanceof Error) {
      throw error;
    }
    throw new Error('Не удалось загрузить детали лога');
  }
};

const fetchBodies = async (id: string) => {
  const items: Array<{ id: string; kind: string; body_json: string }> = [];
  for await (const chunk of logQueryClient.bodies({ logId: protoInt64.parse(id) })) {
    items.push({
      id: chunk.id.toString(),
      kind: chunk.kind,
      body_json: chunk.bodyJson,
    });
  }
  return items;
};

const fetchTimeline = async (
  params: Partial<LogsFiltersState> & { req_id?: string },
): Promise<UITimelineItem[]> => {
  const iterator = logQueryClient.timeline({
    reqId: params.req_id ?? '',
    tsFrom: params.ts_from ?? '',
    tsTo: params.ts_to ?? '',
    importId: params.import_id ?? '',
  });
  const items: UITimelineItem[] = [];
  for await (const item of iterator) {
    const reqId = item.reqId || 'Без req_id';
    const id = item.reqId || `${item.startTs}-${item.endTs}`;
    items.push({
      id,
      reqId,
      importId: item.importId || undefined,
      label: reqId,
      startTs: item.startTs,
      endTs: item.endTs,
      count: item.count,
    });
  }
  return items;
};

const fetchHeatmap = async (
  bucket: 'minute' | 'hour' | 'day',
  filters: LogsFiltersState,
): Promise<HeatBucket[]> => {
  const query = buildQuery({
    ...filters,
    page: 1,
    pageSize: 500,
  });
  const iterator = logQueryClient.export({ query });
  const counter = new Map<string, number>();
  for await (const record of iterator) {
    const timestamp = dayjs(record.ts);
    const rounded = timestamp.startOf(bucket).toISOString();
    const level = (record.level || 'UNKNOWN').toUpperCase();
    const key = `${rounded}_${level}`;
    counter.set(key, (counter.get(key) ?? 0) + 1);
  }
  return Array.from(counter.entries()).map(([key, count]) => {
    const [ts, level] = key.split('_');
    return { ts, level, count } satisfies HeatBucket;
  });
};

const exportReport = async (
  params: LogsQueryParams & { format: 'CSV' | 'JSON' | 'PDF'; groupByReqId: boolean },
) => {
  const iterator = reportClient.export({
    query: buildQuery({ ...params }),
    format:
      params.format === 'CSV'
        ? ReportFormat.CSV
        : params.format === 'JSON'
          ? ReportFormat.JSON
          : ReportFormat.PDF,
  });
  const blobParts: BlobPart[] = [];
  let fileName: string | undefined;
  for await (const chunk of iterator) {
    if (chunk.fileName) {
      fileName = chunk.fileName;
    }
    if (chunk.payload?.length) {
      const buffer = chunk.payload.buffer.slice(
        chunk.payload.byteOffset,
        chunk.payload.byteOffset + chunk.payload.byteLength,
      ) as ArrayBuffer;
      blobParts.push(buffer);
    }
    if (chunk.eof) {
      break;
    }
  }
  const mimeType =
    params.format === 'PDF'
      ? 'application/pdf'
      : params.format === 'JSON'
        ? 'application/json'
        : 'text/csv';
  return {
    fileName:
      fileName || `report_${dayjs().format('YYYYMMDD_HHmmss')}.${params.format.toLowerCase()}`,
    blob: new Blob(blobParts, { type: mimeType }),
  };
};


const logApi = {
  searchLogs,
  searchLogGroups,
  markLogRead,
  fetchLogDetails,
  fetchBodies,
  fetchTimeline,
  fetchHeatmap,
  exportReport,
};

export default logApi;
