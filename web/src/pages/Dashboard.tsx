import { useState, useEffect } from 'react';
import { Card, Row, Col, Statistic, Alert, Table, Tag, Spin, Button, Space, Tag as AntTag } from 'antd';
import { SettingOutlined } from '@ant-design/icons';
import { getOverview } from '../api';
import DockerConfigModal from '../components/DockerConfigModal';

export default function Dashboard() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [dockerModalOpen, setDockerModalOpen] = useState(false);

  useEffect(() => {
    const load = () => getOverview().then((r) => { setData(r.data); setLoading(false); });
    load();
    const t = setInterval(load, 10000);   // 每 10s 轮询，Docker 状态后台探测完成后自动更新
    return () => clearInterval(t);
  }, []);

  if (loading) return <Spin />;

  const docker = data?.docker || {};
  const hosts = data?.hosts || {};
  const services = data?.services || {};

  return (
    <div>
      {!docker.healthy && (
        <Alert type="warning" showIcon style={{ marginBottom: 16 }}
          message={
            <Space>
              <span>Docker 环境异常</span>
              {docker.remote && <AntTag color="blue">远程 {docker.endpoint}</AntTag>}
            </Space>
          }
          description={
            <Space direction="vertical" size={4}>
              <span>
                {docker.message || '未检测到可用的 Docker 引擎。'}
                {docker.remote
                  ? '（当前使用远程构建机，请确认服务器 Docker 服务、端口开放与防火墙）'
                  : '本机无 Docker？可改用远程 Linux 服务器作为构建机。'}
              </span>
              <Button
                size="small"
                type="primary"
                ghost
                icon={<SettingOutlined />}
                onClick={() => setDockerModalOpen(true)}
              >
                配置 Docker（本机 / 远程构建机）
              </Button>
            </Space>
          }
        />
      )}
      {docker.healthy && docker.remote && (
        <Alert type="success" showIcon style={{ marginBottom: 16 }}
          message={<Space>Docker 引擎就绪 <AntTag color="green">远程 {docker.endpoint}</AntTag></Space>}
          description="构建任务将发送到远程构建机执行。"
        />
      )}
      <Row gutter={16}>
        <Col span={8}><Card><Statistic title="服务总数" value={services.total || 0} /></Card></Col>
        <Col span={8}><Card><Statistic title="运行中服务" value={services.running || 0} valueStyle={{ color: '#3f8600' }} /></Card></Col>
        <Col span={8}><Card><Statistic title="在线主机" value={(hosts.online || 0) + ' / ' + (hosts.total || 0)} /></Card></Col>
      </Row>
      <Card title="最近部署" style={{ marginTop: 16 }}>
        <Table
          rowKey="id"
          size="small"
          dataSource={data?.recentDeployments || []}
          pagination={false}
          columns={[
            { title: 'ID', dataIndex: 'id' },
            { title: '单元', dataIndex: 'serviceId' },
            { title: '状态', dataIndex: 'status', render: (s: string) => (
              <Tag color={s === 'SUCCESS' ? 'green' : s === 'FAILED' ? 'red' : 'blue'}>{s}</Tag>
            ) },
            { title: '时间', dataIndex: 'startedAt' }
          ]}
        />
      </Card>

      <DockerConfigModal
        open={dockerModalOpen}
        onClose={() => setDockerModalOpen(false)}
        onSaved={() => window.location.reload()}
      />
    </div>
  );
}
