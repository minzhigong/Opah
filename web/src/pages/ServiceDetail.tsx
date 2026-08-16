import { useState, useEffect, useRef } from 'react';
import { Tabs, Card, Button, Select, Table, Tag, message, Space, Input, Form, Spin, Modal } from 'antd';
import { useParams } from 'react-router-dom';
import { Client } from '@stomp/stompjs';
import {
  getService, listBuilds, triggerBuild, buildLogs,
  listDeployments, deploy, rollback, listHosts,
  listEntries, saveEntries, listConfigFiles, saveConfigFiles
} from '../api';

const STATUS_COLOR: Record<string, string> = {
  SUCCESS: 'green', FAILED: 'red', RUNNING: 'blue', PENDING: 'orange', ROLLED_BACK: 'default'
};

export default function ServiceDetail() {
  const { id } = useParams();
  const svcId = Number(id);
  const [svc, setSvc] = useState<any>(null);
  const [builds, setBuilds] = useState<any[]>([]);
  const [deployments, setDeployments] = useState<any[]>([]);
  const [hosts, setHosts] = useState<any[]>([]);
  const [ref, setRef] = useState('main');
  const [logLines, setLogLines] = useState<string[]>([]);
  const [activeBuildId, setActiveBuildId] = useState<number | null>(null);
  const logRef = useRef<HTMLDivElement>(null);
  const stompRef = useRef<Client | null>(null);

  // deploy modal state
  const [deployOpen, setDeployOpen] = useState(false);
  const [deployBuildId, setDeployBuildId] = useState<number | null>(null);
  const [deployHostId, setDeployHostId] = useState<number | null>(null);
  const [deployPorts, setDeployPorts] = useState('');

  const loadAll = () => {
    getService(svcId).then((r) => setSvc(r.data));
    listBuilds(svcId).then((r) => setBuilds(r.data));
    listDeployments(svcId).then((r) => setDeployments(r.data));
    listHosts().then((r) => setHosts(r.data));
  };

  useEffect(loadAll, [svcId]);

  // poll builds while running
  useEffect(() => {
    const t = setInterval(() => {
      listBuilds(svcId).then((r) => setBuilds(r.data));
      listDeployments(svcId).then((r) => setDeployments(r.data));
    }, 3000);
    return () => clearInterval(t);
  }, [svcId]);

  useEffect(() => () => stompRef.current?.deactivate(), []);

  const onBuild = async () => {
    const r = await triggerBuild(svcId, ref);
    setActiveBuildId(r.data.id);
    setLogLines([]);
    message.success('构建已触发 #' + r.data.id);
    subscribeLogs(r.data.id);
  };

  const subscribeLogs = (buildId: number) => {
    stompRef.current?.deactivate();
    const client = new Client({
      brokerURL: (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws'
    });
    client.onConnect = () => {
      client.subscribe('/topic/builds/' + buildId, (frame) => {
        const msg = JSON.parse(frame.body);
        if (msg.type === 'log') {
          setLogLines((prev) => [...prev, msg.content]);
          requestAnimationFrame(() => {
            if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
          });
        } else if (msg.type === 'state' && msg.status === 'SUCCESS') {
          message.success('构建成功：' + msg.versionTag);
        } else if (msg.type === 'state' && msg.status === 'FAILED') {
          message.error('构建失败');
        }
      });
    };
    client.activate();
    stompRef.current = client;
    // backfill existing logs
    buildLogs(svcId, buildId, 0).then((r) => {
      setLogLines(r.data.map((l: any) => l.content));
    });
  };

  const onDeploy = async () => {
    if (!deployBuildId || !deployHostId) { message.warning('请选择版本和主机'); return; }
    const ports = deployPorts.split('\n').filter(Boolean).map((line) => {
      const [hostPort, containerPort] = line.split(':');
      return { hostPort, containerPort };
    });
    await deploy(svcId, { buildId: deployBuildId, hostId: deployHostId, ports, restartPolicy: 'unless-stopped' });
    message.success('部署已开始');
    setDeployOpen(false);
    loadAll();
  };

  const onRollback = async (deploymentId: number) => {
    await rollback(deploymentId);
    message.success('回滚已开始');
    loadAll();
  };

  if (!svc) return <Spin />;

  const buildColumns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '版本', dataIndex: 'versionTag', ellipsis: true },
    { title: '状态', dataIndex: 'status', render: (s: string) => <Tag color={STATUS_COLOR[s]}>{s}</Tag> },
    { title: '提交', dataIndex: 'commitMsg', ellipsis: true },
    { title: '耗时(ms)', dataIndex: 'durationMs', width: 90 },
    { title: '操作', width: 160, render: (_: any, r: any) => (
      <Space>
        <a onClick={() => { setActiveBuildId(r.id); setLogLines([]); subscribeLogs(r.id); }}>日志</a>
        {r.status === 'SUCCESS' && (
          <a onClick={() => { setDeployBuildId(r.id); setDeployOpen(true); }}>部署</a>
        )}
      </Space>
    ) }
  ];

  const deployColumns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '状态', dataIndex: 'status', render: (s: string) => <Tag color={STATUS_COLOR[s]}>{s}</Tag> },
    { title: '主机', dataIndex: 'hostId' },
    { title: '时间', dataIndex: 'startedAt' },
    { title: '操作', width: 100, render: (_: any, r: any) => (
      r.status === 'SUCCESS' || r.status === 'ROLLED_BACK'
        ? <a onClick={() => onRollback(r.id)}>回滚到此</a> : null
    ) }
  ];

  return (
    <div>
      <h2>{svc.name} <Tag>{svc.type}</Tag></h2>
      <Tabs
        items={[
          {
            key: 'build', label: '构建',
            children: (
              <div>
                <Space style={{ marginBottom: 12 }}>
                  <Select value={ref} onChange={setRef} style={{ width: 160 }} options={[
                    { value: 'main', label: 'main' }, { value: 'master', label: 'master' }
                  ]} />
                  <Button type="primary" onClick={onBuild}>触发构建</Button>
                </Space>
                <Table rowKey="id" size="small" dataSource={builds} columns={buildColumns} pagination={{ pageSize: 10 }} />
                {activeBuildId && (
                  <Card title={'构建日志 #' + activeBuildId} size="small" style={{ marginTop: 16 }}>
                    <div className="log-viewer" ref={logRef}>
                      {logLines.map((l, i) => (
                        <div key={i} className={l.includes('ERROR') || l.includes('FAILED') ? 'err' : ''}>{l}</div>
                      ))}
                    </div>
                  </Card>
                )}
              </div>
            )
          },
          {
            key: 'deploy', label: '部署历史',
            children: <Table rowKey="id" size="small" dataSource={deployments} columns={deployColumns} pagination={{ pageSize: 10 }} />
          },
          { key: 'config', label: '配置', children: <ConfigTab svcId={svcId} /> }
        ]}
      />

      <Modal title="部署" open={deployOpen} onOk={onDeploy} onCancel={() => setDeployOpen(false)} okText="部署">
        <Form layout="vertical">
          <Form.Item label="目标主机">
            <Select value={deployHostId} onChange={setDeployHostId} placeholder="选择主机"
              options={hosts.filter((h) => h.status === 'ONLINE').map((h) => ({ value: h.id, label: h.name + ' (' + h.ip + ')' }))} />
          </Form.Item>
          <Form.Item label="端口映射（每行 host:container）">
            <Input.TextArea value={deployPorts} onChange={(e) => setDeployPorts(e.target.value)}
              placeholder={'8080:8080\n80:80'} rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

function ConfigTab({ svcId }: { svcId: number }) {
  const [entries, setEntries] = useState<any[]>([]);
  const [files, setFiles] = useState<any[]>([]);
  const [entryText, setEntryText] = useState('');

  const load = () => {
    listEntries(svcId).then((r) => {
      setEntries(r.data);
      setEntryText(r.data.map((e: any) => e.key + '=' + e.maskedValue).join('\n'));
    });
    listConfigFiles(svcId).then((r) => setFiles(r.data));
  };

  useEffect(load, [svcId]);

  const onSaveEntries = async () => {
    const parsed = entryText.split('\n').filter(Boolean).map((line) => {
      const idx = line.indexOf('=');
      return { key: line.substring(0, idx), value: line.substring(idx + 1), sensitive: false };
    });
    await saveEntries(svcId, parsed);
    message.success('环境变量已保存');
    load();
  };

  const [filePath, setFilePath] = useState('');
  const [fileContent, setFileContent] = useState('');

  const onAddFile = async () => {
    const map: Record<string, string> = {};
    files.forEach((f) => (map[f.path] = f.content));
    map[filePath] = fileContent;
    await saveConfigFiles(svcId, map);
    message.success('配置文件已保存');
    setFilePath(''); setFileContent('');
    load();
  };

  return (
    <div>
      <Card title="环境变量" size="small" extra={<Button size="small" type="primary" onClick={onSaveEntries}>保存</Button>}>
        <Input.TextArea value={entryText} onChange={(e) => setEntryText(e.target.value)}
          placeholder="KEY=value（每行一个，敏感值以 opah:secret: 前缀标记）" rows={8} />
      </Card>
      <Card title="配置文件" size="small" style={{ marginTop: 16 }}>
        <Table rowKey="path" size="small" dataSource={files} pagination={false}
          columns={[{ title: '路径', dataIndex: 'path' }]} />
        <Space.Compact style={{ width: '100%', marginTop: 12 }}>
          <Input value={filePath} onChange={(e) => setFilePath(e.target.value)} placeholder="文件路径，如 application-prod.yml" />
        </Space.Compact>
        <Input.TextArea value={fileContent} onChange={(e) => setFileContent(e.target.value)}
          placeholder="文件内容" rows={5} style={{ marginTop: 8 }} />
        <Button size="small" type="primary" style={{ marginTop: 8 }} onClick={onAddFile}>添加/更新</Button>
      </Card>
    </div>
  );
}
