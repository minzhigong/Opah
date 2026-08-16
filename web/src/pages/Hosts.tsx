import { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, InputNumber, Tag, message, Popconfirm, Space } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { listHosts, addHost, testHost, deleteHost } from '../api';

const STATUS_COLOR: Record<string, string> = { ONLINE: 'green', OFFLINE: 'red', UNKNOWN: 'default' };

export default function Hosts() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

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
        message.success('主机接入成功，Docker: ' + (r.data.dockerVersion || '未知'));
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
          { title: '名称', dataIndex: 'name' },
          { title: 'IP', dataIndex: 'ip' },
          { title: '端口', dataIndex: 'sshPort', width: 80 },
          { title: '用户', dataIndex: 'username' },
          { title: '状态', dataIndex: 'status', render: (s: string) => <Tag color={STATUS_COLOR[s]}>{s}</Tag> },
          { title: 'Docker', dataIndex: 'dockerVersion', ellipsis: true },
          { title: '操作', width: 140, render: (_, r: any) => (
            <Space>
              <a onClick={async (e) => { e.stopPropagation(); await testHost(r.id); load(); }}>检测</a>
              <Popconfirm title="删除该主机？" onConfirm={async () => { await deleteHost(r.id); load(); }}>
                <a style={{ color: 'red' }} onClick={(e) => e.stopPropagation()}>删除</a>
              </Popconfirm>
            </Space>
          ) }
        ]}
      />
      <Modal title="添加主机" open={open} onOk={onCreate} onCancel={() => setOpen(false)}
        confirmLoading={submitting} okText="接入">
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="ip" label="IP 地址" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="sshPort" label="SSH 端口" initialValue={22}><InputNumber min={1} max={65535} /></Form.Item>
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="password" label="密码（或用私钥）"><Input.Password /></Form.Item>
          <Form.Item name="privateKey" label="私钥（可选，内容粘贴）"><Input.TextArea rows={3} /></Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
