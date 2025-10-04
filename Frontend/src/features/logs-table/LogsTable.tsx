import {
  ColumnDef,
  SortingState,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { useCallback, useEffect, useMemo, useState, type HTMLAttributes } from 'react';
import { App, Button, Empty, Pagination, Space, Spin, Tag, Typography } from 'antd';
import { TableVirtuoso, type TableComponents } from 'react-virtuoso';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { LogEntry } from '../../entities/logs/types';
import { useLogsQuery } from './hooks';
import createColumns from './columns';
import { useUIStore } from '../../shared/store/ui';
import logApi from '../../entities/logs/api';
import {
  CaretDownOutlined,
  CaretRightOutlined,
  DownloadOutlined,
  FilterOutlined,
} from '@ant-design/icons';
import { formatTimestamp } from '../../shared/utils/formatters';

const EMPTY_GROUP_KEY = '__empty__';
const EMPTY_GROUP_LABEL = 'Без tf_req_id';

const formatGroupLabel = (value: string | null) => {
  if (!value) {
    return EMPTY_GROUP_LABEL;
  }
  const trimmed = value.trim();
  if (!trimmed) {
    return EMPTY_GROUP_LABEL;
  }
  if (trimmed.length <= 24) {
    return trimmed;
  }
  return `${trimmed.slice(0, 12)}…${trimmed.slice(-6)}`;
};

const LogsTable = () => {
  const queryClient = useQueryClient();
  const { message } = App.useApp();
  const {
    visibleColumns,
    filters,
    setSelectedLogId,
    selectedLogId,
    groupByReqId,
    setFilters,
  } = useUIStore((state) => ({
    visibleColumns: state.visibleColumns,
    filters: state.filters,
    setSelectedLogId: state.setSelectedLogId,
    selectedLogId: state.selectedLogId,
    groupByReqId: state.groupByReqId,
    setFilters: state.setFilters,
  }));
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [sorting, setSorting] = useState<SortingState>([]);
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());

  useEffect(() => {
    setPage(1);
  }, [filters]);

  useEffect(() => {
    setCollapsedGroups(new Set());
  }, [groupByReqId]);

  useEffect(() => {
    setPage(1);
  }, [groupByReqId]);

  const sortBy = sorting[0]?.id;
  const sortDir = sorting[0]?.desc ? 'desc' : 'asc';

  const { data, isLoading, isFetching } = useLogsQuery(
    page,
    pageSize,
    sortBy,
    sorting.length ? sortDir : undefined,
    groupByReqId,
  );

  const mutation = useMutation({
    mutationFn: (id: string) => logApi.markLogRead(id, true),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['logs'] });
      message.success('Отмечено как прочитанное');
    },
    onError: (error: Error) => message.error(error.message),
  });

  const exportMutation = useMutation({
    mutationFn: async (format: 'CSV' | 'JSON' | 'PDF') =>
      logApi.exportReport({
        ...filters,
        page,
        pageSize,
        sortBy,
        sortDir,
        groupByReqId,
        format,
      }),
    onSuccess: ({ blob, fileName }, format) => {
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = fileName;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      URL.revokeObjectURL(url);
      message.success(`Отчет ${format} сформирован`);
    },
    onError: (error: Error) => message.error(error.message),
  });

  const columns = useMemo<ColumnDef<LogEntry, unknown>[]>(
    () =>
      createColumns({
        visibleColumns,
        onMarkRead: (id) => mutation.mutateAsync(id),
      }),
    [mutation, visibleColumns],
  );

  const tableData = useMemo<LogEntry[]>(() => {
    if (!data) {
      return [];
    }
    if (data.kind === 'group') {
      return data.payload.groups.flatMap((group) => group.items);
    }
    return data.payload.items;
  }, [data]);

  const table = useReactTable({
    data: tableData,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
  });

  const rows = table.getRowModel().rows;
  type TableRowItem = (typeof rows)[number];

  type DisplayRow =
    | {
        kind: 'group';
        groupId: string;
        label: string;
        rawValue: string | null;
        count: number;
        collapsed: boolean;
        firstTs?: string | null;
        lastTs?: string | null;
      }
    | { kind: 'data'; row: TableRowItem };

  const rowMap = useMemo(() => {
    const map = new Map<string, TableRowItem>();
    rows.forEach((row) => {
      map.set(row.original.id, row);
    });
    return map;
  }, [rows]);

  const displayRows = useMemo<DisplayRow[]>(() => {
    if (!groupByReqId || !data || data.kind !== 'group') {
      return rows.map((row) => ({ kind: 'data', row }));
    }
    const result: DisplayRow[] = [];
    data.payload.groups.forEach((group) => {
      const key = group.req_id ?? EMPTY_GROUP_KEY;
      const collapsed = collapsedGroups.has(key);
      result.push({
        kind: 'group',
        groupId: key,
        label: formatGroupLabel(group.req_id),
        rawValue: group.req_id,
        count: group.items.length,
        collapsed,
        firstTs: group.group_first_ts,
        lastTs: group.group_last_ts,
      });
      if (!collapsed) {
        group.items.forEach((item) => {
          const row = rowMap.get(item.id);
          if (row) {
            result.push({ kind: 'data', row });
          }
        });
      }
    });
    return result;
  }, [collapsedGroups, data, groupByReqId, rowMap, rows]);

  const columnCount = table.getVisibleLeafColumns().length || 1;
  const activeReqId = filters.req_id?.trim() ?? filters.tf_req_id?.trim() ?? undefined;

  const toggleGroup = useCallback((groupId: string) => {
    setCollapsedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(groupId)) {
        next.delete(groupId);
      } else {
        next.add(groupId);
      }
      return next;
    });
  }, []);

  const components = useMemo<TableComponents<DisplayRow>>(
    () => ({
      Table: (props: HTMLAttributes<HTMLTableElement>) => (
        <table {...props} style={{ borderCollapse: 'separate', width: '100%' }} />
      ),
      TableRow: (
        props: HTMLAttributes<HTMLTableRowElement> & {
          item: DisplayRow;
        },
      ) => {
        const { item, style, ...rest } = props;
        if (item.kind === 'group') {
          return (
            <tr
              {...rest}
              onClick={() => toggleGroup(item.groupId)}
              style={{
                cursor: 'pointer',
                background: '#f6f9ff',
                fontWeight: 600,
                ...(style || {}),
              }}
            />
          );
        }
        return (
          <tr
            {...rest}
            onClick={() => setSelectedLogId(item.row.original.id)}
            style={{ cursor: 'pointer', ...(style || {}) }}
          />
        );
      },
    }),
    [setSelectedLogId, toggleGroup],
  );

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Spin />
      </div>
    );
  }

  if (!tableData.length) {
    return <Empty description="Нет данных" />;
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Space wrap>
        <Typography.Text strong>Экспорт:</Typography.Text>
        {(['CSV', 'JSON', 'PDF'] as const).map((format) => (
          <Button
            key={format}
            icon={<DownloadOutlined />}
            loading={
              exportMutation.isPending && exportMutation.variables === format
            }
            onClick={() => exportMutation.mutate(format)}
          >
            {format}
          </Button>
        ))}
      </Space>
      <div style={{ border: '1px solid #f0f0f0', borderRadius: 8 }}>
        <TableVirtuoso<DisplayRow>
          data={displayRows}
          totalCount={displayRows.length}
          style={{ height: 520, width: '100%' }}
          computeItemKey={(index, item) =>
            item.kind === 'group'
              ? `group-${item.groupId}`
              : `row-${item.row.original.id}`
          }
          fixedHeaderContent={() => (
            <tr>
              {table.getFlatHeaders().map((header) => (
                <th
                  key={header.id}
                  style={{
                    background: '#fafafa',
                    padding: '8px 12px',
                    fontWeight: 600,
                    position: 'sticky',
                    top: 0,
                    zIndex: 1,
                    cursor: header.column.getCanSort() ? 'pointer' : 'default',
                  }}
                  onClick={header.column.getToggleSortingHandler()}
                >
                  <Space>
                    {flexRender(header.column.columnDef.header, header.getContext())}
                    {{
                      asc: '↑',
                      desc: '↓',
                    }[header.column.getIsSorted() as string] || null}
                  </Space>
                </th>
              ))}
            </tr>
          )}
          itemContent={(index, item) => {
            if (item.kind === 'group') {
              const isFiltered = Boolean(
                item.rawValue && activeReqId && activeReqId === item.rawValue,
              );
              return [
                <td
                  key={`group-${item.groupId}`}
                  colSpan={columnCount}
                  style={{
                    padding: '8px 12px',
                    background: '#f6f9ff',
                    borderBottom: '1px solid #dbe6ff',
                  }}
                >
                  <Space size={8} wrap align="center">
                    <Button
                      type="text"
                      size="small"
                      icon={item.collapsed ? <CaretRightOutlined /> : <CaretDownOutlined />}
                      onClick={(event) => {
                        event.stopPropagation();
                        toggleGroup(item.groupId);
                      }}
                    />
                    <Typography.Text strong>{item.label}</Typography.Text>
                    <Tag color="geekblue">Записей: {item.count}</Tag>
                    {item.firstTs && item.lastTs && (
                      <Tag color="blue">
                        {formatTimestamp(item.firstTs)} → {formatTimestamp(item.lastTs)}
                      </Tag>
                    )}
                    {item.rawValue && (
                      <Button
                        type="text"
                        size="small"
                        icon={<FilterOutlined />}
                        onClick={(event) => {
                          event.stopPropagation();
                          const nextValue = isFiltered ? undefined : item.rawValue || undefined;
                          setFilters({ req_id: nextValue, tf_req_id: nextValue });
                        }}
                      >
                        {isFiltered ? 'Сбросить фильтр' : 'Фильтр по группе'}
                      </Button>
                    )}
                    {!item.rawValue && (
                      <Typography.Text type="secondary">
                        Отсутствует tf_req_id
                      </Typography.Text>
                    )}
                    {isFiltered && <Tag color="green">Фильтр активен</Tag>}
                  </Space>
                </td>,
              ];
            }
            const row = item.row;
            return row.getVisibleCells().map((cell) => (
              <td
                key={cell.id}
                style={{
                  padding: '8px 12px',
                  background:
                    row.original.id === selectedLogId ? 'rgba(22, 119, 255, 0.08)' : '#fff',
                  borderBottom: '1px solid #f0f0f0',
                  verticalAlign: 'top',
                }}
              >
                {flexRender(cell.column.columnDef.cell, cell.getContext())}
              </td>
            ));
          }}
          components={components}
        />
      </div>
      <Space align="center" style={{ justifyContent: 'space-between' }}>
        {isFetching && <Tag>Обновление…</Tag>}
        <Pagination
          current={page}
          pageSize={pageSize}
          total={(() => {
            if (!data) return 0;
            return data.kind === 'group'
              ? data.payload.totalGroups
              : data.payload.total;
          })()}
          showSizeChanger
          pageSizeOptions={['10', '25', '50', '100']}
          onChange={(nextPage, nextSize) => {
            setPage(nextPage);
            setPageSize(nextSize);
          }}
        />
        <Button
          disabled={!selectedLogId}
          loading={mutation.isPending}
          onClick={() => selectedLogId && mutation.mutate(selectedLogId)}
        >
          Пометить как прочитанное
        </Button>
      </Space>
    </Space>
  );
};

export default LogsTable;
