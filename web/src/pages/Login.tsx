import { useState, useEffect } from 'react';
import { Form, Input, Button, Card, Alert, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { getSetupStatus, setup, login } from '../api';

export default function Login() {
  const navigate = useNavigate();
  const [initialized, setInitialized] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    getSetupStatus().then((r) => setInitialized(r.data.initialized));
  }, []);

  const onFinish = async (values: any) => {
    setLoading(true);
    try {
      if (!initialized) {
        const r = await setup(values.username || 'admin', values.password);
        localStorage.setItem('opah_token', r.data.token);
      } else {
        const r = await login(values.username || 'admin', values.password);
        localStorage.setItem('opah_token', r.data.token);
      }
      message.success('登录成功');
      navigate('/');
    } catch (e: any) {
      message.error(e.response?.data?.error || '操作失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', background: '#f0f2f5' }}>
      <Card style={{ width: 380 }} title="Opah 登录">
        {initialized === false && (
          <Alert type="info" message="首次使用，请设置管理员账号" style={{ marginBottom: 16 }} />
        )}
        <Form onFinish={onFinish} layout="vertical">
          <Form.Item name="username" label="用户名" initialValue="admin">
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, min: 6, message: '密码至少 6 位' }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            {initialized ? '登录' : '创建并进入'}
          </Button>
        </Form>
      </Card>
    </div>
  );
}
