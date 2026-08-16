import { useState, useEffect } from 'react';
import { Table, Tag, Button, Space, Modal, message, Input } from 'antd';
import { useParams } from 'react-router-dom';
import { listHostContainers, containerAction, containerLogs } from '../api';

export default function HostDetail() {
  const { id } = useParams();
  const hostId = Number(id);
  const [rows, setRows] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [logOpen, setLogOpen] = useState(false);
  const [logs, setLogs] = useState('');

  const load = () => {
    setLoading(true);
    listHostContainers(hostId).then((r) => { setRows(r.data); setLoading(false); });
  };

  useEffect(() => {
    load();
    const t = setInterval(load, 5000);
    return () => clearInterval(t);
  }, [hostId]);

  const doAction = async (cid: string, action: string) => {
    await containerAction(hostId, cid, action);
    message.success('操作成功');
    load();
  };

  const showLogs = async (cid: string) => {
    const r = await containerLogs(hostId, cid, 300);
    setLogs(r.data.logs);
    setLogOpen(true);
  };

  return (
    <div>
      <h2>容器概览</h2>
      <Table
        rowKey="id"
        loading={loading}
        dataSource={rows}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '镜像', dataIndex: 'image', ellipsis: true },
          { title: '状态', dataIndex: 'state', render: (s: string) => (
            <Tag color={s === 'running' ? 'green' : 'default'}>{s}</Tag>
          ) },
          { title: '详情', dataIndex: 'status', ellipsis: true },
          { title: '操作', width: 220, render: (_, r: any) => (
            <Space>
              <a onClick={() => doAction(r.id, 'start')}>启动</a>
              <a onClick={() => doAction(r.id, 'stop')}>停止</a>
              <a onClick={() => doAction(r.id, 'restart')}>重启</a>
              <a onClick={() => showLogs(r.id)}>日志</a>
            </Space>
          ) }
        ]}
      />
      <Modal title="容器日志" open={logOpen} onCancel={() => setLogOpen(false)} footer={null} width={720}>
        <div className="log-viewer">{logs || '（无输出）'}</div>
      </Modal>
    </div>
  );
}
