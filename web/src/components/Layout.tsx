import { Layout as AntLayout, Menu } from 'antd';
import { DashboardOutlined, ClusterOutlined, CloudServerOutlined, LogoutOutlined, SettingOutlined } from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';

const { Header, Sider, Content } = AntLayout;

export default function Layout() {
  const navigate = useNavigate();
  const location = useLocation();

  const selected = location.pathname.startsWith('/projects') ? 'projects'
    : location.pathname.startsWith('/hosts') ? 'hosts'
    : location.pathname.startsWith('/settings') ? 'settings'
    : 'dashboard';

  const logout = () => {
    localStorage.removeItem('opah_token');
    navigate('/login');
  };

  return (
    <AntLayout style={{ height: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', color: '#fff', padding: '0 24px' }}>
        <div style={{ fontSize: 18, fontWeight: 600, marginRight: 40 }}>Opah</div>
        <div style={{ color: 'rgba(255,255,255,0.7)', fontSize: 13 }}>一站式部署运维</div>
        <div style={{ flex: 1 }} />
        <a style={{ color: '#fff' }} onClick={logout}><LogoutOutlined /> 退出</a>
      </Header>
      <AntLayout>
        <Sider width={180} theme="light">
          <Menu
            mode="inline"
            selectedKeys={[selected]}
            onClick={(e) => {
              if (e.key === 'dashboard') navigate('/');
              if (e.key === 'projects') navigate('/projects');
              if (e.key === 'hosts') navigate('/hosts');
              if (e.key === 'settings') navigate('/settings');
            }}
            items={[
              { key: 'dashboard', icon: <DashboardOutlined />, label: '总览' },
              { key: 'projects', icon: <ClusterOutlined />, label: '项目' },
              { key: 'hosts', icon: <CloudServerOutlined />, label: '主机' },
              { key: 'settings', icon: <SettingOutlined />, label: '设置' }
            ]}
          />
        </Sider>
        <Content style={{ padding: 24, overflow: 'auto' }}>
          <Outlet />
        </Content>
      </AntLayout>
    </AntLayout>
  );
}
