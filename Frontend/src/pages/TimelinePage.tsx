import { Card, Space } from 'antd';
import GanttView from '../features/timeline/GanttView';
import LogsFilters from '../features/logs-table/filters';

const TimelinePage = () => (
  <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Card title="Фильтры" bordered={false}>
      <LogsFilters hideSearch hideColumns />
    </Card>
    <Card title="Диаграмма Ганта" bordered={false}>
      <GanttView />
    </Card>
  </Space>
);

export default TimelinePage;
