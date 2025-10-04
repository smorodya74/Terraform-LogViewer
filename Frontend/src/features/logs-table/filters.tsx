import { useEffect } from 'react';
import {
  Form,
  Input,
  Select,
  Button,
  Space,
  App,
  Tag,
  Flex,
  Checkbox,
} from 'antd';
import { useUIStore, useGroupByReqId } from '../../shared/store/ui';
import DateRange from '../../shared/ui/DateRange';
import ColumnToggles from '../../shared/ui/ColumnToggles';
import dayjs from '../../shared/utils/dayjs';

const levels = ['ERROR', 'WARN', 'INFO', 'DEBUG'];

interface LogsFiltersProps {
  hideSearch?: boolean;
  hideColumns?: boolean;
}

const LogsFilters = ({ hideSearch, hideColumns }: LogsFiltersProps) => {
  const [form] = Form.useForm();
  const { filters, setFilters, resetFilters } = useUIStore((state) => ({
    filters: state.filters,
    setFilters: state.setFilters,
    resetFilters: state.resetFilters,
  }));
  const { groupByReqId, setGroupByReqId } = useGroupByReqId();
  const { message } = App.useApp();

  useEffect(() => {
    form.setFieldsValue({
      ...filters,
      status_code: filters.status_code,
      timestamp:
        filters.ts_from && filters.ts_to
          ? [dayjs(filters.ts_from), dayjs(filters.ts_to)]
          : undefined,
    });
  }, [filters, form]);

  const handleValuesChange = (_: unknown, values: Record<string, unknown>) => {
    const timestamp = values.timestamp as
      | [dayjs.Dayjs | null, dayjs.Dayjs | null]
      | undefined;
    const [from, to] = timestamp || [];
    const reqIdValue = (values.req_id as string) || undefined;
    const payload = {
      q: (values.q as string) || undefined,
      level: (values.level as string) || undefined,
      section: (values.section as string) || undefined,
      resource_type: (values.resource_type as string) || undefined,
      status_code: values.status_code ? Number(values.status_code) : undefined,
      req_id: reqIdValue,
      tf_req_id: reqIdValue,
      ts_from: from ? from.toISOString() : undefined,
      ts_to: to ? to.toISOString() : undefined,
    };
    setFilters(payload);
  };

  return (
    <Form
      form={form}
      layout="vertical"
      onValuesChange={handleValuesChange}
      initialValues={filters}
    >
      <Flex gap={16} wrap align="end">
        {!hideSearch && (
          <Form.Item name="q" label="Поиск">
            <Input.Search allowClear placeholder="Полнотекстовый поиск" />
          </Form.Item>
        )}
        <Form.Item name="level" label="Уровень">
          <Select allowClear placeholder="Все уровни">
            {levels.map((lvl) => (
              <Select.Option value={lvl} key={lvl}>
                <Tag color={lvl === 'ERROR' ? 'red' : lvl === 'WARN' ? 'orange' : lvl === 'INFO' ? 'blue' : 'default'}>
                  {lvl}
                </Tag>
              </Select.Option>
            ))}
          </Select>
        </Form.Item>
        <Form.Item name="section" label="Секция">
          <Input allowClear placeholder="section" />
        </Form.Item>
        <Form.Item name="resource_type" label="Resource type">
          <Input allowClear placeholder="resource_type" />
        </Form.Item>
        <Form.Item name="req_id" label="tf_req_id">
          <Input allowClear placeholder="tf_req_id" />
        </Form.Item>
        <Form.Item name="status_code" label="HTTP код">
          <Select allowClear placeholder="Любой код">
            {[200, 400, 404, 500].map((code) => (
              <Select.Option value={code} key={code}>
                {code}
              </Select.Option>
            ))}
          </Select>
        </Form.Item>
        <Form.Item name="timestamp" label="Диапазон времени">
          <DateRange />
        </Form.Item>
        {!hideColumns && (
          <Form.Item label=" ">
            <Space direction="vertical" size={8}>
              <ColumnToggles />
              <Checkbox
                checked={groupByReqId}
                onChange={(event) => setGroupByReqId(event.target.checked)}
              >
                Группировать по tf_req_id
              </Checkbox>
            </Space>
          </Form.Item>
        )}
        <Form.Item label=" ">
          <Space>
            <Button
              onClick={() => {
                resetFilters();
                form.resetFields();
                message.success('Фильтры сброшены');
              }}
            >
              Сбросить
            </Button>
          </Space>
        </Form.Item>
      </Flex>
    </Form>
  );
};

export default LogsFilters;
