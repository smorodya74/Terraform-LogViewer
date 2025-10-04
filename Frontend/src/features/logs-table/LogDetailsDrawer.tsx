import { useMemo, useState } from 'react';
import {
  App,
  Badge,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Space,
  Spin,
  Tabs,
  Typography,
} from 'antd';
import type { TabsProps } from 'antd';
import { useQuery } from '@tanstack/react-query';
import logApi from '../../entities/logs/api';
import { useUIStore } from '../../shared/store/ui';
import JsonInspector from '../../shared/ui/JsonInspector';
import { formatTimestamp, levelColorMap } from '../../shared/utils/formatters';

const safeParse = (value?: string) => {
  if (!value) return undefined;
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
};

const buildBodies = (
  bodies: Array<{ id: string; kind: string; body_json: string }> | undefined,
) => {
  if (!bodies?.length) {
    return {
      request: undefined,
      response: undefined,
      other: [] as Array<{ id: string; kind: string; body_json: string }>,
    };
  }
  const requestCandidates = bodies.filter((body) =>
    body.kind?.toLowerCase().includes('req'),
  );
  const responseCandidates = bodies.filter((body) =>
    body.kind?.toLowerCase().includes('res'),
  );
  const request = requestCandidates[0];
  const response = responseCandidates[0];
  const other = bodies.filter((body) => body !== request && body !== response);
  return { request, response, other };
};

const LogDetailsDrawer = () => {
  const { message } = App.useApp();
  const { selectedLogId, setSelectedLogId } = useUIStore((state) => ({
    selectedLogId: state.selectedLogId,
    setSelectedLogId: state.setSelectedLogId,
  }));
  const [activeTab, setActiveTab] = useState('request');

  const activeLogId = selectedLogId ?? '';
  const { data, isLoading, isFetching, error } = useQuery({
    queryKey: ['log-details', selectedLogId],
    enabled: Boolean(selectedLogId),
    queryFn: () => logApi.fetchLogDetails(activeLogId),
  });

  const parsedRaw = useMemo(() => safeParse(data?.raw_json), [data?.raw_json]);
  const parsedAttrs = useMemo(() => safeParse(data?.attrs_json), [data?.attrs_json]);
  const parsedAnnotations = useMemo(
    () => safeParse(data?.annotations_json),
    [data?.annotations_json],
  );

  const parsedBodies = useMemo(() => buildBodies(data?.bodies), [data?.bodies]);

  const handleCopy = async (payload?: unknown) => {
    if (!payload) {
      message.warning('Нет данных для копирования');
      return;
    }
    try {
      const text = typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2);
      await navigator.clipboard.writeText(text);
      message.success('Скопировано в буфер обмена');
    } catch (copyError) {
      message.error('Не удалось скопировать JSON');
    }
  };

  const isOpen = Boolean(selectedLogId);
  const header = data?.record;
  const levelColor = header?.level ? levelColorMap[header.level] ?? 'default' : undefined;

  const tabItems = useMemo(() => {
    const items: NonNullable<TabsProps['items']> = [];
    items.push({
      key: 'request',
      label: 'Request',
      children: parsedBodies.request ? (
        <JsonInspector value={safeParse(parsedBodies.request.body_json)} />
      ) : (
        <Empty description="Нет данных" />
      ),
    });
    items.push({
      key: 'response',
      label: 'Response',
      children: parsedBodies.response ? (
        <JsonInspector value={safeParse(parsedBodies.response.body_json)} />
      ) : (
        <Empty description="Нет данных" />
      ),
    });
    items.push({
      key: 'raw',
      label: 'Raw',
      children: (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          {parsedRaw ? (
            <JsonInspector value={parsedRaw} />
          ) : (
            <Typography.Text type="secondary">raw_json отсутствует</Typography.Text>
          )}
          {parsedAttrs && (
            <div>
              <Typography.Title level={5}>attrs_json</Typography.Title>
              <JsonInspector value={parsedAttrs} />
            </div>
          )}
          {parsedAnnotations && (
            <div>
              <Typography.Title level={5}>annotations_json</Typography.Title>
              <JsonInspector value={parsedAnnotations} />
            </div>
          )}
          {parsedBodies.other.length > 0 && (
            <div>
              <Typography.Title level={5}>Дополнительные payloads</Typography.Title>
              <Space direction="vertical" style={{ width: '100%' }}>
                {parsedBodies.other.map((body) => (
                  <div key={body.id}>
                    <Typography.Text strong>{body.kind || `payload #${body.id}`}</Typography.Text>
                    <JsonInspector value={safeParse(body.body_json)} />
                  </div>
                ))}
              </Space>
            </div>
          )}
        </Space>
      ),
    });
    return items;
  }, [parsedAnnotations, parsedAttrs, parsedBodies, parsedRaw]);

  const getPayloadForTab = (key: string): unknown => {
    if (key === 'request') {
      return parsedBodies.request?.body_json ?? parsedBodies.request;
    }
    if (key === 'response') {
      return parsedBodies.response?.body_json ?? parsedBodies.response;
    }
    return data?.raw_json ?? parsedRaw;
  };

  return (
    <Drawer
      width={640}
      title="Детали лога"
      placement="right"
      open={isOpen}
      destroyOnClose
      onClose={() => setSelectedLogId(undefined)}
      footer={
        <Space>
          <Button onClick={() => handleCopy(getPayloadForTab(activeTab))}>
            Скопировать вкладку
          </Button>
          <Button type="primary" onClick={() => setSelectedLogId(undefined)}>
            Закрыть
          </Button>
        </Space>
      }
    >
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin />
        </div>
      ) : error ? (
        <Typography.Text type="danger">{(error as Error).message}</Typography.Text>
      ) : !data ? (
        <Empty description="Выберите запись" />
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          {header && (
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="Timestamp">
                {header.ts ? formatTimestamp(header.ts) : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Уровень">
                {header.level ? <Badge color={levelColor} text={header.level} /> : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="req_id">
                {header.req_id || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Ресурс/операция">
                {header.resource_type || header.module || '—'}
              </Descriptions.Item>
            </Descriptions>
          )}
          <Tabs
            activeKey={activeTab}
            onChange={(key) => setActiveTab(key)}
            items={tabItems}
          />
          {isFetching && <Typography.Text type="secondary">Обновление…</Typography.Text>}
        </Space>
      )}
    </Drawer>
  );
};

export default LogDetailsDrawer;
