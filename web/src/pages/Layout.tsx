import { DashboardOutlined, HddOutlined, ProjectOutlined } from '@ant-design/icons';
import { App, Button, Layout as AntLayout, Menu } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useLogout, useMe } from '../api/auth';

const { Header, Sider, Content } = AntLayout;

export default function Layout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = App.useApp();
  const me = useMe();
  const logout = useLogout();

  return (
    <AntLayout style={{ minHeight: '100vh' }}>
      <Sider theme="dark">
        <div style={{ color: '#fff', fontSize: 20, fontWeight: 700, padding: 20, textAlign: 'center' }}>
          Opah
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          onClick={({ key }) => navigate(key)}
          items={[
            { key: '/', icon: <DashboardOutlined />, label: '总览' },
            { key: '/projects', icon: <ProjectOutlined />, label: '项目' },
            { key: '/hosts', icon: <HddOutlined />, label: '主机' },
          ]}
        />
      </Sider>
      <AntLayout>
        <Header
          style={{
            background: '#fff',
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
            gap: 12,
          }}
        >
          <span>{me.data?.username}</span>
          <Button
            size="small"
            onClick={() =>
              logout.mutate(undefined, {
                onSuccess: () => {
                  message.success('已退出');
                  window.location.href = '/login';
                },
              })
            }
          >
            退出
          </Button>
        </Header>
        <Content style={{ margin: 16 }}>
          <Outlet />
        </Content>
      </AntLayout>
    </AntLayout>
  );
}
