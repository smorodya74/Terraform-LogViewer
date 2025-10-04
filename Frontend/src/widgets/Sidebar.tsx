import { Layout, Menu } from 'antd';
import {
  InboxOutlined,
  TableOutlined,
  ClusterOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';

const { Sider } = Layout;

const Sidebar = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);

  const selectedKey = location.pathname.startsWith('/')
    ? location.pathname.split('/')[1] || 'import'
    : 'import';

  return (
    <Sider collapsible collapsed={collapsed} onCollapse={setCollapsed} width={220}>
      <div
        style={{
          height: 48,
          margin: 16,
          background: 'rgba(255, 255, 255, 0.2)',
          borderRadius: 6,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          fontWeight: 600,
        }}
      >
        Logs
      </div>
      <Menu
        theme="dark"
        mode="inline"
        selectedKeys={[selectedKey]}
        onClick={(info) => navigate(`/${info.key}`)}
        items={[
          {
            key: 'import',
            icon: <InboxOutlined />,
            label: 'Импорт',
          },
          {
            key: 'analyze',
            icon: <TableOutlined />,
            label: 'Анализ',
          },
          {
            key: 'timeline',
            icon: <ClusterOutlined />,
            label: 'Таймлайн',
          },
          {
            key: 'reports',
            icon: <FileTextOutlined />,
            label: 'Отчёты',
          },
        ]}
      />
    </Sider>
  );
};

export default Sidebar;
