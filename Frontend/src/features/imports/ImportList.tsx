import { useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { App, Button, Empty, List, Skeleton, Space, Tag, Typography } from 'antd';
import dayjs from '../../shared/utils/dayjs';
import { fetchImportSummaries } from '../../entities/imports/api';
import { useUIStore } from '../../shared/store/ui';

const ImportList = () => {
  const { message } = App.useApp();
  const setFilters = useUIStore((state) => state.setFilters);
  const [searchParams, setSearchParams] = useSearchParams();

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['import-summaries'],
    queryFn: fetchImportSummaries,
    staleTime: 60_000,
  });

  const activeImportId = searchParams.get('importId') ?? undefined;

  useEffect(() => {
    if (isError) {
      const description =
        error instanceof Error ? error.message : 'Не удалось загрузить импорты';
      message.error(description);
    }
  }, [error, isError, message]);

  const items = useMemo(() => data ?? [], [data]);

  const applyImport = (nextId?: string) => {
    const next = new URLSearchParams(searchParams);
    if (nextId) {
      next.set('importId', nextId);
    } else {
      next.delete('importId');
    }
    setSearchParams(next, { replace: true });
  };

  useEffect(() => {
    if (activeImportId) {
      setFilters({ import_id: activeImportId });
    } else {
      setFilters({ import_id: undefined });
    }
  }, [activeImportId, setFilters]);

  if (isLoading) {
    return <Skeleton active paragraph={{ rows: 4 }} />;
  }

  if (!items.length) {
    return <Empty description="Импорты не найдены" />;
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Button
        type={activeImportId ? 'default' : 'primary'}
        onClick={() => applyImport(undefined)}
      >
        Все импорты
      </Button>
      <List
        dataSource={items}
        renderItem={(item) => {
          const isActive = item.importId === activeImportId;
          return (
            <List.Item
              key={item.importId}
              onClick={() => applyImport(item.importId)}
              style={{
                borderRadius: 8,
                border: isActive ? '1px solid #1677ff' : '1px solid #f0f0f0',
                cursor: 'pointer',
                padding: '12px 16px',
                background: isActive ? 'rgba(22, 119, 255, 0.08)' : '#fff',
              }}
            >
              <Space direction="vertical" size={4} style={{ width: '100%' }}>
                <Typography.Text strong ellipsis>
                  {item.fileName}
                </Typography.Text>
                <Space size={8} wrap>
                  <Tag color="blue">Импорт: {item.importId}</Tag>
                  <Tag>Записей: {item.total}</Tag>
                  {item.firstTimestamp && (
                    <Tag color="geekblue">
                      От {dayjs(item.firstTimestamp).format('DD.MM.YYYY HH:mm:ss')}
                    </Tag>
                  )}
                  {item.lastTimestamp && (
                    <Tag color="purple">
                      До {dayjs(item.lastTimestamp).format('DD.MM.YYYY HH:mm:ss')}
                    </Tag>
                  )}
                </Space>
              </Space>
            </List.Item>
          );
        }}
      />
    </Space>
  );
};

export default ImportList;
