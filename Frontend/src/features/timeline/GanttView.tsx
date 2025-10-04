import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Empty, Spin } from 'antd';
import { Timeline } from 'vis-timeline/standalone';
import { DataSet } from 'vis-data/peer';
import type { Dayjs } from 'dayjs';
import '../../../assets/vis-timeline.css';
import dayjs from '../../shared/utils/dayjs';
import { useTimelineQuery } from './useTimelineQuery';

import { useFilters, useUIStore } from '../../shared/store/ui';
import type { LogGroup } from '../../entities/logs/types';
import { levelColorMap } from '../../shared/utils/formatters';

type TimelineGroupNode = {
  id: string;
  content: string;
  title?: string;
  nestedGroups?: string[];
  treeLevel?: number;
};

type TimelineDataItem = {
  id: string | number;
  content: string;
  group: string;
  start: Date;
  end?: Date;
  title?: string;
  style?: string;
  type?: 'range' | 'point';
};

type LookupMeta = {
  reqId?: string;
  logId?: string | number;
  kind: 'summary' | 'entry';
  internalKey: string;
};

const GanttView = () => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const timelineRef = useRef<Timeline | null>(null);

  const groupsData = useRef(new DataSet<TimelineGroupNode>());
  const itemsData = useRef(new DataSet<TimelineDataItem>());
  const itemLookup = useRef<Map<string, LookupMeta>>(new Map());
  const initialFitDone = useRef(false);

  const setSelectedLogId = useUIStore((state) => state.setSelectedLogId);
  const filters = useFilters();
  const requestedReqId = useMemo(
    () => filters.req_id?.trim() || filters.tf_req_id?.trim() || null,
    [filters.req_id, filters.tf_req_id],
  );

  const { data, isLoading } = useTimelineQuery();
  const timelineGroups = data?.groups ?? [];

  const [expandedKey, setExpandedKey] = useState<string | null>(null);

  useEffect(() => {
    if (requestedReqId) {
      setExpandedKey(requestedReqId);
    }
  }, [requestedReqId]);

  useEffect(() => {
    if (!timelineGroups.length) {
      setExpandedKey(null);
      return;
    }
    if (!expandedKey) {
      return;
    }
    const available = new Set(timelineGroups.map((group) => buildGroupKey(group.req_id)));
    if (!available.has(expandedKey)) {
      setExpandedKey(null);
    }
  }, [expandedKey, timelineGroups]);

  const handleSelect = useCallback(
    (properties: { items?: (string | number)[] }) => {
      const itemId = properties.items?.[0];
      if (!itemId) {
        setSelectedLogId(undefined);
        return;
      }
      const meta = itemLookup.current.get(String(itemId));
      if (!meta) {
        return;
      }

      if (meta.kind === 'summary') {
        setSelectedLogId(undefined);
        setExpandedKey((prev) => (prev === meta.internalKey ? null : meta.internalKey));
        const summaryId = buildSummaryItemId(meta.internalKey);
        requestAnimationFrame(() => {
          timelineRef.current?.focus(summaryId, {
            animation: { duration: 260, easingFunction: 'easeInOutQuad' },
          });
        });
        return;
      }

      if (meta.logId != null) {
        setSelectedLogId(String(meta.logId));
        requestAnimationFrame(() => {
          timelineRef.current?.focus(String(itemId), {
            animation: { duration: 220, easingFunction: 'easeInOutQuad' },
          });
        });
      }
    },
    [setSelectedLogId],
  );

  useEffect(() => {
    if (!containerRef.current || timelineRef.current) {
      return;
    }
    const timeline = new Timeline(
      containerRef.current,
      itemsData.current,
      groupsData.current,
      {
        stack: false,
        horizontalScroll: true,
        zoomKey: 'ctrlKey',
        multiselect: false,
        orientation: 'top',
      },
    );
    timelineRef.current = timeline;
    timeline.on('select', handleSelect);

    return () => {
      timeline.off('select', handleSelect as any);
      timeline.destroy();
      timelineRef.current = null;
      initialFitDone.current = false;
    };
  }, [handleSelect]);

  const buildTimelineData = useCallback(
    (groupsSource: LogGroup[], expanded: string | null) => {
      const groupList: TimelineGroupNode[] = [];
      const visItems: TimelineDataItem[] = [];
      const lookup = new Map<string, LookupMeta>();

      groupsSource.forEach((group) => {
        const rawReqId = group.req_id?.trim() || '';
        const groupKey = buildGroupKey(group.req_id);
        const summaryGroupId = buildGroupId(groupKey);
        const summaryItemId = buildSummaryItemId(groupKey);
        const normalizedReq = rawReqId || 'Без req_id';
        const firstTs = group.group_first_ts || group.items[0]?.ts;
        const lastTs = group.group_last_ts || group.items[group.items.length - 1]?.ts || firstTs;
        const severityRank = computeGroupSeverity(group.items);
        const summaryColors = summaryStyleForRank(severityRank);
        const nested: string[] = [];

        groupList.push({
          id: summaryGroupId,
          content: `${formatReqId(normalizedReq)} · ${group.items.length}`,
          title: buildSummaryTooltip(normalizedReq, firstTs, lastTs, group.items.length, severityRank),
          nestedGroups: expanded === groupKey ? nested : undefined,
          treeLevel: 0,
        });

        if (firstTs) {
          visItems.push({
            id: summaryItemId,
            group: summaryGroupId,
            content: summaryLabel(severityRank),
            start: dayjs(firstTs).toDate(),
            end: lastTs ? dayjs(lastTs).toDate() : undefined,
            title: buildSummaryTooltip(normalizedReq, firstTs, lastTs, group.items.length, severityRank),
            style: summaryColors,
            type: 'range',
          });
        }

        lookup.set(summaryItemId, {
          reqId: rawReqId || undefined,
          logId: undefined,
          kind: 'summary',
          internalKey: groupKey,
        });

        if (expanded !== groupKey) {
          return;
        }

        // Группируем записи по типу ресурса/модулю
        const operations = new Map<string, LogGroup['items']>();
        group.items.forEach((item) => {
          const resource = item.resource_type?.trim() || item.module?.trim() || 'operation';
          if (!operations.has(resource)) {
            operations.set(resource, []);
          }
          operations.get(resource)!.push(item);
        });

        // Детализированные группы и элементы
        operations.forEach((entries, resource) => {
          const detailGroupId = buildResourceGroupId(groupKey, resource);
          nested.push(detailGroupId);
          groupList.push({
            id: detailGroupId,
            content: resource,
            treeLevel: 1,
            title: [`req_id: ${normalizedReq}`, `resource: ${resource}`].join('\n'),
          });

          entries.forEach((entry, index) => {
            const start = dayjs(entry.ts);
            const next = entries[index + 1];
            const nextStart = next ? dayjs(next.ts) : null;
            const level = (entry.level ?? '').toUpperCase();
            const { background, text } = levelToTimelineColor(level);
            const type: 'range' | 'point' = level === 'ERROR' ? 'point' : 'range';
            const entryId = buildEntryId(entry.id);
            const message = entry.message?.trim();

            const item: TimelineDataItem = {
              id: entryId,
              group: detailGroupId,
              content: message && message.length > 64 ? `${message.slice(0, 64)}…` : message || level || 'event',
              start: start.toDate(),
              title: buildEntryTooltip(normalizedReq, resource, level, start, message),
              style: entryStyle(background, text, type),
              type,
            };

            if (type === 'range') {
              if (nextStart && nextStart.isAfter(start)) {
                item.end = nextStart.toDate();
              } else {
                item.end = start.add(1, 'second').toDate();
              }
            }

            visItems.push(item);
            lookup.set(String(entryId), {
              reqId: rawReqId || undefined,
              logId: entry.id,
              kind: 'entry',
              internalKey: groupKey,
            });
          });
        });
      });

      return { groups: groupList, items: visItems, lookup };
    },
    [],
  );

  useEffect(() => {
    if (!timelineRef.current) {
      return;
    }
    const { groups, items, lookup } = buildTimelineData(timelineGroups, expandedKey);
    itemLookup.current = lookup;

    groupsData.current.clear();
    if (groups.length) {
      groupsData.current.add(groups);
    }

    itemsData.current.clear();
    if (items.length) {
      itemsData.current.add(items);
    }

    if (!initialFitDone.current && items.length) {
      timelineRef.current.fit({ animation: { duration: 240, easingFunction: 'easeInOutQuad' } });
      initialFitDone.current = true;
      return;
    }

    if (expandedKey) {
      const summaryId = buildSummaryItemId(expandedKey);
      if (lookup.has(summaryId)) {
        timelineRef.current.focus(summaryId, {
          animation: { duration: 240, easingFunction: 'easeInOutQuad' },
        });
      }
    }
  }, [buildTimelineData, expandedKey, timelineGroups]);

  const timelineContent = useMemo(() => {
    if (isLoading) {
      return (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin />
        </div>
      );
    }
    if (!timelineGroups.length) {
      return <Empty description="Нет данных" />;
    }
    return <div ref={containerRef} style={{ height: 420 }} />;
  }, [isLoading, timelineGroups.length]);

  return timelineContent;
};

export default GanttView;

const buildGroupKey = (reqId: string | null | undefined) => reqId?.trim() || '__empty__';

const buildGroupId = (key: string) => `group::${key}`;
const buildSummaryItemId = (key: string) => `summary::${key}`;
const buildResourceGroupId = (key: string, resource: string) => `resource::${key}::${resource}`;
const buildEntryId = (id: string | number) => `entry::${id}`;

const formatReqId = (value: string) => {
  const normalized = value?.trim();
  if (!normalized || normalized === 'Без req_id') {
    return 'Без req_id';
  }
  if (normalized.length <= 16) {
    return normalized;
  }
  return `${normalized.slice(0, 8)}…${normalized.slice(-4)}`;
};

const computeGroupSeverity = (items: LogGroup['items']) => {
  let max = 0;
  items.forEach((item) => {
    max = Math.max(max, levelRank(item.level));
  });
  return max;
};

const levelRank = (level?: string | null) => {
  switch ((level ?? '').toUpperCase()) {
    case 'ERROR':
      return 3;
    case 'WARN':
    case 'WARNING':
      return 2;
    case 'INFO':
      return 1;
    case 'DEBUG':
      return 0;
    default:
      return 0;
  }
};

const summaryLabel = (rank: number) => {
  switch (rank) {
    case 3:
      return 'Ошибка';
    case 2:
      return 'Предупреждения';
    case 1:
      return 'Операции';
    default:
      return 'Трассировка';
  }
};

const summaryStyleForRank = (rank: number) => {
  const palette = {
    3: { background: '#ff4d4f', text: '#fff' },
    2: { background: '#faad14', text: '#000' },
    1: { background: '#1677ff', text: '#fff' },
    0: { background: '#595959', text: '#fff' },
  } as const;
  const { background, text } = palette[rank as keyof typeof palette] ?? palette[0];
  return `background-color:${withAlpha(background, '26')};border-color:${background};color:${text};`;
};

const entryStyle = (background: string, text: string, type: 'range' | 'point') => {
  if (type === 'point') {
    return `color:${background};border-color:${background};background-color:${background};`;
  }
  return `background-color:${withAlpha(background, '33')};border-color:${background};color:${text};`;
};

const withAlpha = (color: string, alpha: string) => {
  if (!color.startsWith('#') || color.length !== 7) {
    return color;
  }
  return `${color}${alpha}`;
};

const buildSummaryTooltip = (
  reqId: string,
  firstTs: string | null,
  lastTs: string | null,
  count: number,
  severity: number,
) => {
  const status = summaryLabel(severity);
  return [
    `req_id: ${reqId}`,
    `Записей: ${count}`,
    firstTs ? `Начало: ${formatDate(firstTs)}` : null,
    lastTs ? `Конец: ${formatDate(lastTs)}` : null,
    `Статус: ${status}`,
  ]
    .filter(Boolean)
    .join('\n');
};

const buildEntryTooltip = (
  reqId: string,
  resource: string,
  level: string,
  start: Dayjs,
  message?: string | null,
) =>
  [
    `req_id: ${reqId}`,
    `resource: ${resource}`,
    `level: ${level || 'UNKNOWN'}`,
    `ts: ${start.format('DD.MM.YYYY HH:mm:ss')}`,
    message ? `message: ${message}` : null,
  ]
    .filter(Boolean)
    .join('\n');

const levelToTimelineColor = (level: string) => {
  const normalized = level.toUpperCase();
  switch (normalized) {
    case 'ERROR':
      return { background: '#ff4d4f', text: '#fff' };
    case 'WARN':
    case 'WARNING':
      return { background: '#faad14', text: '#000' };
    case 'DEBUG':
      return { background: '#722ed1', text: '#fff' };
    case 'INFO':
      return { background: '#1677ff', text: '#fff' };
    default: {
      const fallback = levelColorMap[normalized];
      const color = fallback && fallback !== 'default' ? fallback : '#595959';
      return { background: color, text: '#fff' };
    }
  }
};

const formatDate = (value: string | null) => {
  if (!value) return '';
  return dayjs(value).format('DD.MM.YYYY HH:mm:ss');
};
