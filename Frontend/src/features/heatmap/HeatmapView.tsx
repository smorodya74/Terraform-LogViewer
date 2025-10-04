import { useMemo, useState } from 'react';
import { Empty, Segmented, Spin } from 'antd';
import {
  ResponsiveContainer,
  ScatterChart,
  CartesianGrid,
  XAxis,
  YAxis,
  Tooltip,
  Scatter,
  ZAxis,
} from 'recharts';
import dayjs from '../../shared/utils/dayjs';
import { useHeatmapQuery } from './useHeatmapQuery';

const buckets: Array<'minute' | 'hour' | 'day'> = ['minute', 'hour', 'day'];

const HeatmapView = () => {
  const [bucket, setBucket] = useState<'minute' | 'hour' | 'day'>('hour');
  const { data, isLoading } = useHeatmapQuery(bucket);

  const dataset = useMemo(() => {
    if (!data?.length) return [];
    return data.map((item) => ({
      ...item,
      timestamp: dayjs(item.ts).valueOf(),
    }));
  }, [data]);

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Spin />
      </div>
    );
  }

  if (!dataset.length) {
    return <Empty description="Нет данных" />;
  }

  return (
    <div style={{ width: '100%', height: 320 }}>
      <Segmented
        options={buckets.map((value) => ({ label: value, value }))}
        value={bucket}
        onChange={(value) => setBucket(value as 'minute' | 'hour' | 'day')}
        style={{ marginBottom: 16 }}
      />
      <ResponsiveContainer>
        <ScatterChart>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="timestamp"
            name="Время"
            type="number"
            domain={['auto', 'auto']}
            tickFormatter={(value) => dayjs(value).format('DD.MM HH:mm')}
          />
          <YAxis dataKey="level" type="category" name="Уровень" />
          <ZAxis dataKey="count" range={[0, 400]} />
          <Tooltip
            formatter={(value: number, _name, props) => [value, props.payload.level]}
            labelFormatter={(label) => dayjs(label as number).format('YYYY-MM-DD HH:mm')}
          />
          <Scatter data={dataset} fill="#1677ff" />
        </ScatterChart>
      </ResponsiveContainer>
    </div>
  );
};

export default HeatmapView;
