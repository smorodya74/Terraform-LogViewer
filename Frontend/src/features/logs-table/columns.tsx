import { ColumnDef } from '@tanstack/react-table';
import { Badge, Button, Space, Tag, Typography } from 'antd';
import { LogEntry } from '../../entities/logs/types';
import { ColumnKey } from '../../shared/store/ui';
import { formatTimestamp, levelColorMap } from '../../shared/utils/formatters';

interface CreateColumnsParams {
  visibleColumns: ColumnKey[];
  onMarkRead: (id: string) => Promise<void>;
}

const columnMap: Partial<Record<ColumnKey, ColumnDef<LogEntry>>> = {
  ts: {
    accessorKey: 'ts',
    header: 'Время',
    cell: ({ row }) => formatTimestamp(row.original.ts),
  },
  level: {
    accessorKey: 'level',
    header: 'Уровень',
    cell: ({ row }) => {
      const level = row.original.level ?? '';
      const color = levelColorMap[level] || 'default';
      return level ? <Tag color={color}>{level}</Tag> : null;
    },
  },
  section: {
    accessorKey: 'section',
    header: 'Секция',
    cell: ({ row }) => row.original.section,
  },
  module: {
    accessorKey: 'module',
    header: 'Модуль',
    cell: ({ row }) => row.original.module,
  },
  message: {
    accessorKey: 'message',
    header: 'Сообщение',
    cell: ({ row }) => <Typography.Text>{row.original.message}</Typography.Text>,
  },
  req_id: {
    accessorKey: 'req_id',
    header: 'Request ID',
    cell: ({ row }) => row.original.req_id,
  },
  trans_id: {
    accessorKey: 'trans_id',
    header: 'Transaction ID',
    cell: ({ row }) => row.original.trans_id,
  },
  rpc: {
    accessorKey: 'rpc',
    header: 'RPC',
    cell: ({ row }) => row.original.rpc,
  },
  resource_type: {
    accessorKey: 'resource_type',
    header: 'Resource type',
    cell: ({ row }) => row.original.resource_type,
  },
  data_source_type: {
    accessorKey: 'data_source_type',
    header: 'Источник',
    cell: ({ row }) => row.original.data_source_type,
  },
  http_op_type: {
    accessorKey: 'http_op_type',
    header: 'HTTP операция',
    cell: ({ row }) => row.original.http_op_type,
  },
  status_code: {
    accessorKey: 'status_code',
    header: 'Код',
    cell: ({ row }) => row.original.status_code,
  },
  file_name: {
    accessorKey: 'file_name',
    header: 'Файл',
    cell: ({ row }) => row.original.file_name,
  },
  import_id: {
    accessorKey: 'import_id',
    header: 'Импорт',
    cell: ({ row }) => row.original.import_id,
  },
};

const createColumns = ({ visibleColumns, onMarkRead }: CreateColumnsParams): ColumnDef<LogEntry>[] => {
  const mapped = visibleColumns
    .map((key) => columnMap[key])
    .filter((column): column is ColumnDef<LogEntry> => Boolean(column));

  const actionColumn: ColumnDef<LogEntry> = {
    id: 'actions',
    header: 'Действия',
    cell: ({ row }) => (
      <Space>
        {row.original.unread && <Badge status="processing" text="Новый" />}
        <Button
          size="small"
          onClick={() => {
            void onMarkRead(row.original.id);
          }}
        >
          Прочитано
        </Button>
      </Space>
    ),
  };

  return [...mapped, actionColumn];
};

export default createColumns;
