import { Card, Space, Typography } from 'antd';
import LogsFilters from '../features/logs-table/filters';
import LogsTable from '../features/logs-table/LogsTable';
import HeatmapView from '../features/heatmap/HeatmapView';
import ImportList from '../features/imports/ImportList';
import LogDetailsDrawer from '../features/logs-table/LogDetailsDrawer';

const AnalyzePage = () => (
  <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Card title="Импорты" bordered={false}>
      <Typography.Paragraph type="secondary">
        Выберите загрузку, чтобы увидеть только связанные записи. По умолчанию
        отображаются все импорты.
      </Typography.Paragraph>
      <ImportList />
    </Card>
    <Card title="Фильтры" bordered={false}>
      <LogsFilters />
    </Card>
    <Card title="Логи" bordered={false}>
      <LogsTable />
    </Card>
    <Card title="Тепловая карта" bordered={false}>
      <Typography.Paragraph type="secondary">
        Тепловая карта помогает оценить интенсивность событий по времени и
        уровням.
      </Typography.Paragraph>
      <HeatmapView />
    </Card>
    <LogDetailsDrawer />
  </Space>
);

export default AnalyzePage;
