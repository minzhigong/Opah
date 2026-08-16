import { useState, useEffect } from 'react';
import { Card, Row, Col, Statistic, Alert, Table, Tag, Spin } from 'antd';
import { getOverview } from '../api';

export default function Dashboard() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getOverview().then((r) => { setData(r.data); setLoading(false); });
  }, []);

  if (loading) return <Spin />;

  const docker = data?.docker || {};
  const hosts = data?.hosts || {};
  const services = data?.services || {};

  return (
    <div>
      {!docker.healthy && (
        <Alert type="warning" showIcon style={{ marginBottom: 16 }}
          message="Docker 环境异常"
          description={docker.message || '未检测到 Docker Desktop，请先安装并启动（构建功能依赖本机 Docker）。'}
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
