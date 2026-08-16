import { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, InputNumber, Tag, message, Popconfirm, Space, Alert } from 'antd';
import { PlusOutlined, BuildOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { listHosts, addHost, testHost, deleteHost, setBuildMachine, unsetBuildMachine } from '../api';

const STATUS_COLOR: Record<string, string> = { ONLINE: 'green', OFFLINE: 'red', UNKNOWN: 'default' };

export default function Hosts() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const [setupHost, setSetupHost] = useState<any>(null);
  const [setupLoading, setSetupLoading] = useState(false);
  const [setupResult, setSetupResult] = useState<any>(null);

  const load = () => {
    setLoading(true);
    listHosts().then((r) => { setRows(r.data); setLoading(false); });
  };

  useEffect(load, []);

  const onCreate = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const r = await addHost(values);
      if (r.data.status === 'ONLINE') {
        message.success('主机接入成功');
      } else {
        message.warning('主机已保存但连接失败，请检查凭据');
      }
      setOpen(false);
      form.resetFields();
      load();
    } catch (e: any) {
      message.error(e.response?.data?.error || '接入失败');
    } finally {
      setSubmitting(false);
    }
  };

  const doSetBuildMachine = async (host: any) => {
    setSetupHost(host);
    setSetupResult(null);
    setSetupLoading(true);
    try {
      const r = await setBuildMachine(host.id);
      setSetupResult(r.data);
      if (r.data.ok) message.success('已设为构建机，并自动绑定为 Docker 环境');
      else message.error(r.data.message);
    } catch (e: any) {
      setSetupResult({ ok: false, message: e.response?.data?.message || '设置失败', steps: [] });
    } finally {
      setSetupLoading(false);
      load();
    }
  };

  const doUnsetBuildMachine = async (host: any) => {
    try {
      await unsetBuildMachine(host.id);
      message.success('已取消构建机，Docker 环境切回本机');
      load();
    } catch (e: any) {
      message.error(e.response?.data?.message || '操作失败');
    }
  };

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <h2 style={{ margin: 0 }}>主机</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>添加主机</Button>
      </div>
      <Table
        rowKey="id"
        loading={loading}
        dataSource={rows}
        onRow={(r) => ({ onClick: () => navigate('/hosts/' + r.id), style: { cursor: 'pointer' } })}
        columns={[
          { title: '名称', dataIndex: 'name', render: (v: string, r: any) => (
            <Space>
              <span>{v}</span>
              {r.role === 'build' && <Tag color="geekblue" icon={<BuildOutlined />}>构建机</Tag>}
            </Space>
          ) },
          { title: 'IP', dataIndex: 'ip' },
          { title: '端口', dataIndex: 'sshPort', width: 80 },
          { title: '用户', dataIndex: 'username' },
          { title: '状态', dataIndex: 'status', render: (s: string) => <Tag color={STATUS_COLOR[s]}>{s}</Tag> },
          { title: 'Docker', dataIndex: 'dockerVersion', ellipsis: true },
          { title: '操作', width: 200, render: (_, r: any) => (
            <Space>
              <a onClick={async (e) => { e.stopPropagation(); await testHost(r.id); load(); }}>检测</a>
              {r.role === 'build' ? (
                <Popconfirm title="取消该主机的构建机标记？" onConfirm={() => doUnsetBuildMachine(r)}>
                  <a onClick={(e) => e.stopPropagation()}>取消构建机</a>
                </Popconfirm>
              ) : (
                <a onClick={(e) => { e.stopPropagation(); doSetBuildMachine(r); }}>设为构建机</a>
              )}
              <Popconfirm title="删除该主机？" onConfirm={async () => { await deleteHost(r.id); load(); }}>
                <a style={{ color: 'red' }} onClick={(e) => e.stopPropagation()}>删除</a>
              </Popconfirm>
            </Space>
          ) }
        ]}
      />
      <Modal title="添加主机" open={open} onOk={onCreate} onCancel={() => setOpen(false)}
        confirmLoading={submitting} okText="接入">
        <Form form={form} layout="vertical" initialValues={{ sshPort: 22 }}>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="ip" label="IP 地址" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="sshPort" label="SSH 端口"><InputNumber min={1} max={65535} /></Form.Item>
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="password" label="密码（或用私钥）"><Input.Password /></Form.Item>
          <Form.Item name="privateKey" label="私钥（可选，内容粘贴）"><Input.TextArea rows={3} /></Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`设为构建机：${setupHost?.name || ''}`}
        open={!!setupHost}
        onCancel={() => setSetupHost(null)}
        footer={<Button onClick={() => setSetupHost(null)}>关闭</Button>}
        width={620}
      >
        <Alert
          type="info" showIcon style={{ marginBottom: 12 }}
          message="将自动完成：安装 Docker → 开启 2375 端口 → 重启 → 验证 → 绑定为 Docker 环境"
          description="要求 SSH 用户为 root 或 sudo 免密。全程约 1-3 分钟，请勿关闭。该主机仍可同时作为部署目标。"
        />
        {setupLoading && <div style={{ color: '#1677ff' }}>正在执行，请稍候…</div>}
        {setupResult && (
          <div>
            <Alert
              style={{ marginBottom: 12 }}
              type={setupResult.ok ? 'success' : 'error'}
              showIcon
              message={setupResult.message}
            />
            <div className="log-viewer" style={{ maxHeight: 320 }}>
              {(setupResult.steps || []).map((s: string, i: number) => <div key={i}>{s}</div>)}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
