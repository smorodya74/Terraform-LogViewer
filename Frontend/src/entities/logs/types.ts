export interface LogEntry {
  id: string;
  ts: string;
  level: string | null;
  section: string | null;
  module: string | null;
  message: string | null;
  req_id: string | null;
  trans_id: string | null;
  rpc: string | null;
  resource_type: string | null;
  data_source_type: string | null;
  http_op_type: string | null;
  status_code: number | null;
  file_name: string | null;
  import_id: string | null;
  unread: boolean;
}

export interface LogGroup {
  req_id: string | null;
  group_first_ts: string | null;
  group_last_ts: string | null;
  items: LogEntry[];
}

export interface TimelineItem {
  id: string | number;
  reqId: string;
  importId?: string;
  label: string;
  startTs: string;
  endTs: string;
  count: number;
}

export interface HeatBucket {
  ts: string;
  level: string;
  count: number;
}
