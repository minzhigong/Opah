import { useState, useEffect } from 'react';
import { Card, Form, Select, Button, Alert, Space, Tag, message } from 'antd';
import { BuildOutlined } from '@ant-design/icons';
import { getDockerSettings, listHosts, setBuildMachine, unsetBuildMachine } from '../api';

export default function Settings() {
  const [hosts, setHosts] = useState<any[]>([]);
  const [docker, setDocker] = useState<any>(null);
  const [selectedHostId, setSelectedHostId] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<any>(null);

  const load = () => {
    listHosts().then((r) => setHosts(r.data));
    getDockerSettings().then((r) => {
      setDocker(r.data);
      if (r.data.buildHostId) setSelectedHostId(r.data.buildHostId);
    });
  };

  useEffect(load, []);

  const doSet = async () => {
    if (!selectedHostId) {
      message.warning('请先选择一台主机');
      return;
    }
    setLoading(true);
    setResult(null);
    try {
      const r = await setBuildMachine(selectedHostId);
      setResult(r.data);
      if (r.data.ok) message.success('构建机设置成功，Docker 环境已绑定');
      else message.error(r.data.message);
    } catch (e: any) {
      setResult({ ok: false, message: e.response?.data?.message || '设置失败', steps: [] });
    } finally {
      setLoading(false);
      load();
    }
  };

  const doUnset = async () => {
    try {
      await unsetBuildMachine();
      message.success('已取消构建机，Docker 环境切回本机');
      setResult(null);
      load();
    } catch (e: any) {
      message.error(e.response?.data?.message || '操作失败');
    }
  };

  return (
    <Card title="Docker 构建机" style={{ maxWidth: 720 }}>
      <Space direction="vertical" size={14} style={{ width: '100%' }}>
        {docker && (
          <Alert
            type={docker.healthy ? 'success' : 'warning'}
            showIcon
            message={
              docker.buildHostId
                ? `当前构建机：${docker.buildHostName} (${docker.buildHostIp}) · ` +
                  (docker.healthy ? '引擎正常' : '引擎不可用')
                : '尚未指定构建机（当前使用本机 Docker，不可用）'
            }
            description={docker.healthy ? undefined : docker.message}
          />
        )}

        <div>
          <div style={{ marginBottom: 8 }}>
            从已有主机中指定一台作为构建机。Opah 会自动在其上安装 Docker 并绑定为构建环境，
            该主机仍可同时作为部署目标。若主机列表为空，请先到「主机」页添加。
          </div>
          <Select
            placeholder="选择主机"
            style={{ width: 380 }}
            value={selectedHostId}
            onChange={setSelectedHostId}
            options={hosts.map((h) => ({
              value: h.id,
              label: `${h.name} (${h.ip})${h.role === 'build' ? ' · 当前构建机' : ''}`,
            }))}
            optionRender={(o) => (
              <Space>
                <span>{o.label}</span>
                {hosts.find((h) => h.id === o.value)?.role === 'build' && (
                  <Tag color="geekblue" icon={<BuildOutlined />}>构建机</Tag>
                )}
              </Space>
            )}
          />
        </div>

        <Space>
          <Button type="primary" loading={loading} onClick={doSet}>设为构建机</Button>
          {docker?.buildHostId && <Button danger onClick={doUnset}>取消构建机</Button>}
        </Space>

        {result && (
          <div>
            <Alert
              style={{ marginBottom: 12 }}
              type={result.ok ? 'success' : 'error'}
              showIcon
              message={result.message}
            />
            {result.steps && result.steps.length > 0 && (
              <div className="log-viewer" style={{ maxHeight: 300 }}>
                {result.steps.map((s: string, i: number) => <div key={i}>{s}</div>)}
              </div>
            )}
          </div>
        )}
      </Space>
    </Card>
  );
}
