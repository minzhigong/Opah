import { useState, useEffect } from 'react';
import { Card, Row, Col, Statistic, Alert, Table, Tag, Spin, Button, Space } from 'antd';
import { CloudServerOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { getOverview } from '../api';

export default function Dashboard() {
  const navigate = useNavigate();
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = () => getOverview().then((r) => { setData(r.data); setLoading(false); });
    load();
    const t = setInterval(load, 10000);
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
          message="Docker 环境未就绪"
          description={
            <Space direction="vertical" size={4}>
              <span>
                {docker.message || '尚未检测到可用的 Docker 引擎。'}
                {docker.remote && <Tag color="blue" style={{ marginLeft: 8 }}>远程 {docker.endpoint}</Tag>}
              </span>
              <span>
                构建镜像需要 Docker 引擎。本机没有 Docker Desktop？去「主机」页添加一台
                Linux 服务器，点「设为构建机」，Opah 会自动安装 Docker 并绑定（该主机仍可作部署目标）。
              </span>
              <Button
                size="small" type="primary" ghost
                icon={<CloudServerOutlined />}
                onClick={() => navigate('/hosts')}
              >
                去添加构建机
              </Button>
            </Space>
          }
        />
      )}
      {docker.healthy && docker.remote && (
        <Alert type="success" showIcon style={{ marginBottom: 16 }}
          message={<Space>Docker 引擎就绪 <Tag color="green">远程 {docker.endpoint}</Tag></Space>}
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
    </div>
  );
}
