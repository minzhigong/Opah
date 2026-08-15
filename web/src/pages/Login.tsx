import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { App, Button, Card, Form, Input } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useLogin } from '../api/auth';

export default function Login() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const login = useLogin();

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f0f2f5',
      }}
    >
      <Card title="Opah 登录" style={{ width: 360 }}>
        <Form
          onFinish={(values) =>
            login.mutate(values, {
              onSuccess: () => {
                message.success('登录成功');
                navigate('/');
              },
              onError: () => message.error('用户名或密码错误'),
            })
          }
        >
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名" autoFocus />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={login.isPending}>
            登录
          </Button>
        </Form>
      </Card>
    </div>
  );
}
