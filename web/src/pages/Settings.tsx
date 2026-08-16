import { useState, useEffect } from 'react';
import { Card, Form, Input, Button, Radio, Alert, Space, Typography, message } from 'antd';
import { ApiOutlined, SaveOutlined } from '@ant-design/icons';
import { getDockerSettings, testDockerHost, saveDockerHost } from '../api';

const { Text } = Typography;

export default function Settings() {
  const [form] = Form.useForm();
  const [mode, setMode] = useState<'local' | 'remote'>('local');
  const [loading, setLoading] = useState(true);
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testResult, setTestResult] = useState<{ healthy: boolean; message: string } | null>(null);
  const [current, setCurrent] = useState<any>(null);

  useEffect(() => {
    getDockerSettings().then((r) => {
      const d = r.data;
      setCurrent(d);
      if (d.host && d.host !== 'local') {
        setMode('remote');
        form.setFieldValue('host', d.host);
      }
      setLoading(false);
    });
  }, []);

  const hostValue = () => (mode === 'local' ? 'local' : form.getFieldValue('host') || '');

  const doTest = async () => {
    if (mode === 'remote') {
      const host = form.getFieldValue('host');
      if (!host || !/^tcp:\/\/.+:\d+$/.test(host)) {
        setTestResult({ healthy: false, message: '地址格式应为 tcp://服务器IP:2375' });
        return;
      }
    }
    setTesting(true);
    setTestResult(null);
    try {
      const r = await testDockerHost(hostValue());
      setTestResult(r.data);
    } catch (e: any) {
      setTestResult({ healthy: false, message: e.response?.data?.message || '请求失败' });
    } finally {
      setTesting(false);
    }
  };

  const doSave = async () => {
    if (mode === 'remote') {
      const host = form.getFieldValue('host');
      if (!host || !/^tcp:\/\/.+:\d+$/.test(host)) {
        setTestResult({ healthy: false, message: '地址格式应为 tcp://服务器IP:2375' });
        return;
      }
    }
    setSaving(true);
    try {
      await saveDockerHost(hostValue());
      message.success('已保存');
      const r = await getDockerSettings();
      setCurrent(r.data);
    } finally {
      setSaving(false);
    }
  };

  if (loading) return null;

  return (
    <Card title="Docker 环境" style={{ maxWidth: 720 }}>
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Text type="secondary">
          构建镜像依赖 Docker 引擎。本机无 Docker Desktop 时，可使用远程 Linux 服务器作为构建机
          （需在服务器上开启 Docker TCP API，默认端口 2375）。
        </Text>
        {current && (
          <Alert
            type={current.healthy ? 'success' : 'warning'}
            showIcon
            message={
              `当前: ${current.remote ? `远程 ${current.endpoint}` : '本机 Docker'} · ` +
              (current.healthy ? '引擎正常' : '引擎不可用')
            }
            description={current.healthy ? undefined : current.message}
          />
        )}
        <Form form={form} layout="vertical">
          <Form.Item label="模式">
            <Radio.Group
              value={mode}
              onChange={(e) => { setMode(e.target.value); setTestResult(null); }}
              optionType="button"
              buttonStyle="solid"
            >
              <Radio.Button value="local">本机 Docker</Radio.Button>
              <Radio.Button value="remote">远程 Linux 构建机</Radio.Button>
            </Radio.Group>
          </Form.Item>
          {mode === 'remote' && (
            <Form.Item
              name="host"
              label="Docker API 地址"
              rules={[{ required: true, message: '请输入 tcp://IP:端口' }]}
              extra="示例：tcp://123.45.67.89:2375"
            >
              <Input placeholder="tcp://服务器IP:2375" style={{ width: 360 }} />
            </Form.Item>
          )}
          {testResult && (
            <Alert
              style={{ marginBottom: 12 }}
              type={testResult.healthy ? 'success' : 'error'}
              showIcon
              message={testResult.healthy ? '连接成功，Docker 引擎可用' : `连接失败：${testResult.message}`}
            />
          )}
          <Space>
            <Button icon={<ApiOutlined />} loading={testing} onClick={doTest}>测试连接</Button>
            <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={doSave}>保存</Button>
          </Space>
        </Form>
      </Space>
    </Card>
  );
}
