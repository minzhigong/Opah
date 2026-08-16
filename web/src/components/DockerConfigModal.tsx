import { useState } from 'react';
import { Modal, Form, Input, Button, Radio, Alert, Space, Typography } from 'antd';
import { ApiOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { testDockerHost, saveDockerHost } from '../api';

const { Text } = Typography;

/**
 * Docker 环境配置弹窗：
 * - local：本机 Docker Desktop（Windows named pipe / Linux socket）
 * - remote：远程 Linux 构建机（tcp://host:2375）
 */
export default function DockerConfigModal({
  open,
  onClose,
  onSaved,
}: {
  open: boolean;
  onClose: () => void;
  onSaved?: () => void;
}) {
  const [form] = Form.useForm();
  const [mode, setMode] = useState<'local' | 'remote'>('remote');
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testResult, setTestResult] = useState<{ healthy: boolean; message: string } | null>(null);

  const currentHost = () => (mode === 'local' ? 'local' : form.getFieldValue('host') || '');

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
      const r = await testDockerHost(currentHost());
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
      await saveDockerHost(currentHost());
      onSaved?.();
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title="配置 Docker 环境"
      onCancel={onClose}
      footer={null}
      width={560}
      destroyOnClose
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="构建镜像需要 Docker 引擎"
        description="本机没有 Docker Desktop？用一台 Linux 云服务器做构建机即可——填入它的 Docker API 地址，Opah 会把构建任务发到那台服务器上执行。"
      />
      <Form form={form} layout="vertical" initialValues={{ host: '' }}>
        <Form.Item label="模式">
          <Radio.Group
            value={mode}
            onChange={(e) => {
              setMode(e.target.value);
              setTestResult(null);
            }}
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
            extra="服务器需开启 Docker TCP API（默认 2375）。示例：tcp://123.45.67.89:2375"
          >
            <Input placeholder="tcp://服务器IP:2375" style={{ width: '100%' }} />
          </Form.Item>
        )}
        {mode === 'local' && (
          <Text type="secondary">
            使用本机 Docker Desktop（Windows named pipe / Linux socket）。请确保 Docker Desktop 已安装并启动。
          </Text>
        )}

        {testResult && (
          <Alert
            style={{ marginTop: 12, marginBottom: 12 }}
            type={testResult.healthy ? 'success' : 'error'}
            showIcon
            message={testResult.healthy ? '连接成功，Docker 引擎可用' : `连接失败：${testResult.message}`}
          />
        )}

        <div style={{ marginTop: 20, display: 'flex', justifyContent: 'flex-end' }}>
          <Space>
            <Button icon={<ApiOutlined />} loading={testing} onClick={doTest}>
              测试连接
            </Button>
            <Button type="primary" icon={<CheckCircleOutlined />} loading={saving} onClick={doSave}>
              保存
            </Button>
          </Space>
        </div>
      </Form>
    </Modal>
  );
}
