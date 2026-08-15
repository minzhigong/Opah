import { App, Button, Form, Input, InputNumber, Modal, Popconfirm, Space, Table, Tag } from 'antd';
import { useState } from 'react';
import { useCheckHost, useCreateHost, useDeleteHost, useHosts, type Host } from '../api/hosts';

const STATUS_COLORS: Record<string, string> = {
  ONLINE: 'green',
  SSH_OK_DOCKER_MISSING: 'orange',
  OFFLINE: 'red',
  UNKNOWN: 'default',
};

export default function Hosts() {
  const { message } = App.useApp();
  const hosts = useHosts();
  const createHost = useCreateHost();
  const deleteHost = useDeleteHost();
  const checkHost = useCheckHost();
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  const columns = [
    { title: '名称', dataIndex: 'name' },
    { title: 'IP', dataIndex: 'ip' },
    { title: 'SSH 端口', dataIndex: 'sshPort', width: 100 },
    { title: '用户名', dataIndex: 'username' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 200,
      render: (status: string) => (
        <Tag color={STATUS_COLORS[status] ?? 'default'}>{status}</Tag>
      ),
    },
    { title: 'Docker', dataIndex: 'dockerVersion', render: (v: string | null) => v ?? '-' },
    { title: '系统', dataIndex: 'osInfo', render: (v: string | null) => v ?? '-' },
    {
      title: '操作',
      width: 200,
      render: (_: unknown, host: Host) => (
        <Space>
          <Button
            size="small"
            loading={checkHost.isPending && checkHost.variables === host.id}
            onClick={() =>
              checkHost.mutate(host.id, {
                onSuccess: (result) =>
                  result.ok
                    ? message.success(`连接成功，Docker ${result.dockerVersion}`)
                    : message.error(result.error ?? '检测失败'),
              })
            }
          >
            检测
          </Button>
          <Popconfirm title="确认删除该主机？" onConfirm={() => deleteHost.mutate(host.id)}>
            <Button size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Table
        rowKey="id"
        title={() => (
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ fontSize: 16, fontWeight: 600 }}>主机管理</span>
            <Button type="primary" onClick={() => setOpen(true)}>
              添加主机
            </Button>
          </div>
        )}
        columns={columns}
        dataSource={hosts.data}
        loading={hosts.isLoading}
        pagination={false}
      />
      <Modal
        title="添加主机"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() =>
          form.validateFields().then((values) =>
            createHost.mutate(values, {
              onSuccess: () => {
                message.success('已添加');
                setOpen(false);
                form.resetFields();
              },
              onError: () => message.error('添加失败'),
            }),
          )
        }
        confirmLoading={createHost.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ sshPort: 22 }}>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="如：生产服务器-01" />
          </Form.Item>
          <Form.Item name="ip" label="IP / 主机名" rules={[{ required: true }]}>
            <Input placeholder="如：192.168.1.100" />
          </Form.Item>
          <Form.Item name="sshPort" label="SSH 端口" rules={[{ required: true }]}>
            <InputNumber min={1} max={65535} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input placeholder="如：root" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true }]}>
            <Input.Password placeholder="SSH 登录密码（加密存储）" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
