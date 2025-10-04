import { Layout } from 'antd';
import { Outlet } from 'react-router-dom';
import Sidebar from '../widgets/Sidebar';
import Topbar from '../widgets/Topbar';

const { Content } = Layout;

const App = () => (
  <Layout style={{ minHeight: '100vh' }}>
    <Sidebar />
    <Layout>
      <Topbar />
      <Content style={{ padding: 16 }}>
        <Outlet />
      </Content>
    </Layout>
  </Layout>
);

export default App;
