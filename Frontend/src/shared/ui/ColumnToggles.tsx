import { Checkbox, Popover } from 'antd';
import { SettingOutlined } from '@ant-design/icons';
import { useMemo, useState } from 'react';
import { useUIStore, ColumnKey } from '../store/ui';

interface ColumnOption {
  key: ColumnKey;
  label: string;
}

const ALL_COLUMNS: ColumnOption[] = [
  { key: 'ts', label: 'Timestamp' },
  { key: 'level', label: 'Level' },
  { key: 'section', label: 'Section' },
  { key: 'module', label: 'Module' },
  { key: 'message', label: 'Message' },
  { key: 'req_id', label: 'Request ID' },
  { key: 'trans_id', label: 'Transaction ID' },
  { key: 'rpc', label: 'RPC' },
  { key: 'resource_type', label: 'Resource Type' },
  { key: 'data_source_type', label: 'Data Source Type' },
  { key: 'http_op_type', label: 'HTTP Op' },
  { key: 'status_code', label: 'Status Code' },
  { key: 'file_name', label: 'File Name' },
  { key: 'import_id', label: 'Import ID' },
];

const ColumnToggles = () => {
  const { visibleColumns, setVisibleColumns } = useUIStore((state) => ({
    visibleColumns: state.visibleColumns,
    setVisibleColumns: state.setVisibleColumns,
  }));

  const order = useMemo(() => ALL_COLUMNS.map((column) => column.key), []);

  const [open, setOpen] = useState(false);

  const content = useMemo(
    () => (
      <div style={{ maxHeight: 280, overflowY: 'auto', paddingRight: 4 }}>
        {ALL_COLUMNS.map((column) => (
          <div key={column.key} style={{ padding: '4px 0' }}>
            <Checkbox
              checked={visibleColumns.includes(column.key)}
              onChange={(event) => {
                event.stopPropagation();
                const checked = event.target.checked;
                const next = checked
                  ? Array.from(new Set([...visibleColumns, column.key]))
                  : visibleColumns.filter((key) => key !== column.key);
                const ordered = order.filter((key) => next.includes(key));
                setVisibleColumns(ordered);
              }}
            >
              {column.label}
            </Checkbox>
          </div>
        ))}
      </div>
    ),
    [order, setVisibleColumns, visibleColumns],
  );

  return (
    <Popover
      content={content}
      trigger="click"
      placement="bottomLeft"
      open={open}
      onOpenChange={setOpen}
    >
      <a onClick={(e) => e.preventDefault()}>
        <SettingOutlined /> Колонки
      </a>
    </Popover>
  );
};

export default ColumnToggles;
