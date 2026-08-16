import { useState, useEffect } from 'react';
import { Card, Button, Table, Modal, Tag, message, Space, Spin, Checkbox, Input } from 'antd';
import { useParams, useNavigate } from 'react-router-dom';
import { listServices, scanProject, confirmServices } from '../api';

const TYPE_COLOR: Record<string, string> = {
  JAVA: 'blue', REACT: 'cyan', VUE: 'green', COMPOSE: 'purple', CUSTOM: 'orange'
};

export default function ProjectDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [services, setServices] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [candidates, setCandidates] = useState<any[]>([]);
  const [checked, setChecked] = useState<boolean[]>([]);

  const load = () => {
    setLoading(true);
    listServices(Number(id)).then((r) => { setServices(r.data); setLoading(false); });
  };

  useEffect(load, [id]);

  const onScan = async () => {
    setScanning(true);
    try {
      const r = await scanProject(Number(id));
      setCandidates(r.data);
      setChecked(r.data.map((c: any) => c.recommended));
    } finally {
      setScanning(false);
    }
  };

  const onConfirm = async () => {
    const units = candidates.filter((_, i) => checked[i]).map((c) => ({
      name: c.subPath === '.' ? 'default' : c.subPath.replace(/\//g, '-'),
      type: c.type,
      subPath: c.subPath
    }));
    if (units.length === 0) { message.warning('请至少勾选一个单元'); return; }
    await confirmServices(Number(id), units);
    message.success('已创建 ' + units.length + ' 个部署单元');
    setCandidates([]);
    load();
  };

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <h2 style={{ margin: 0 }}>部署单元</h2>
        <Button type="primary" onClick={onScan} loading={scanning}>扫描仓库</Button>
      </div>

      {candidates.length > 0 && (
        <Card title="扫描结果（勾选要纳管的单元）" style={{ marginBottom: 16 }}>
          <Table
            rowKey={(r, i) => String(i)}
            size="small"
            pagination={false}
            dataSource={candidates}
            columns={[
              { title: '', width: 50, render: (_, __, i) => (
                <Checkbox checked={checked[i]} onChange={(e) => {
                  const n = [...checked]; n[i] = e.target.checked; setChecked(n);
                }} />
              ) },
              { title: '路径', dataIndex: 'subPath' },
              { title: '类型', dataIndex: 'type', render: (t: string) => <Tag color={TYPE_COLOR[t]}>{t}</Tag> },
              { title: '说明', dataIndex: 'detail' }
            ]}
          />
          <Space style={{ marginTop: 12 }}>
            <Button type="primary" onClick={onConfirm}>确认创建</Button>
            <Button onClick={() => setCandidates([])}>取消</Button>
          </Space>
        </Card>
      )}

      <Table
        rowKey="id"
        loading={loading}
        dataSource={services}
        onRow={(r) => ({ onClick: () => navigate('/services/' + r.id), style: { cursor: 'pointer' } })}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '类型', dataIndex: 'type', render: (t: string) => <Tag color={TYPE_COLOR[t]}>{t}</Tag> },
          { title: '路径', dataIndex: 'subPath' },
          { title: '当前版本', dataIndex: 'currentBuildId', render: (v: number) => v ? <Tag color="green">已部署</Tag> : <Tag>未部署</Tag> }
        ]}
      />
    </div>
  );
}
