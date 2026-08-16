import { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, message, Popconfirm, Tag } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { listProjects, addProject, deleteProject } from '../api';

export default function Projects() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const load = () => {
    setLoading(true);
    listProjects().then((r) => { setRows(r.data); setLoading(false); });
  };

  useEffect(load, []);

  const onCreate = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const r = await addProject(values);
      message.success('项目已接入（HEAD: ' + (r.data.head || '未知') + '）');
      setOpen(false);
      form.resetFields();
      load();
    } catch (e: any) {
      message.error(e.response?.data?.error || '接入失败，请检查仓库地址与凭据');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <h2 style={{ margin: 0 }}>项目</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>接入项目</Button>
      </div>
      <Table
        rowKey="id"
        loading={loading}
        dataSource={rows}
        onRow={(r) => ({ onClick: () => navigate('/projects/' + r.id), style: { cursor: 'pointer' } })}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '仓库', dataIndex: 'gitUrl', ellipsis: true },
          { title: '默认分支', dataIndex: 'defaultBranch', width: 120 },
          { title: '单元数', dataIndex: 'serviceCount', width: 80, render: (c: number) => <Tag>{c}</Tag> },
          { title: '操作', width: 100, render: (_, r: any) => (
            <Popconfirm title="删除该项目及其所有单元？" onConfirm={async () => {
              await deleteProject(r.id); load(); message.success('已删除');
            }}>
              <Button danger size="small" onClick={(e) => e.stopPropagation()}>删除</Button>
            </Popconfirm>
          ) }
        ]}
      />
      <Modal title="接入 Git 项目" open={open} onOk={onCreate} onCancel={() => setOpen(false)}
        confirmLoading={submitting} okText="接入">
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="项目名" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="gitUrl" label="仓库地址 (HTTPS)" rules={[{ required: true }]}>
            <Input placeholder="https://github.com/you/proj.git" />
          </Form.Item>
          <Form.Item name="defaultBranch" label="默认分支" initialValue="main"><Input /></Form.Item>
          <Form.Item name="credentialId" label="凭据 ID（可选，私有仓库）"><Input placeholder="留空表示公开仓库" /></Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
