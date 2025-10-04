import { Layout, Typography } from 'antd';
import dayjs from '../shared/utils/dayjs';

const { Header } = Layout;

const Topbar = () => (
  <Header
    style={{
      background: '#fff',
      padding: '0 16px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      borderBottom: '1px solid #f0f0f0',
    }}
  >
    <Typography.Title level={4} style={{ margin: 0 }}>
      Лог-центр
    </Typography.Title>
    <Typography.Text type="secondary">
      {dayjs().format('YYYY-MM-DD HH:mm:ss')}
    </Typography.Text>
  </Header>
);

export default Topbar;
