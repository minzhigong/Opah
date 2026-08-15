import { Card, Typography } from 'antd';
import { useHosts } from '../api/hosts';

export default function Dashboard() {
  const hosts = useHosts();

  return (
    <Card title="总览">
      <Typography.Text type="secondary">
        M1 骨架阶段：当前已有 {hosts.data?.length ?? 0} 台主机接入。
        项目、构建与部署功能将在 M2/M3 里程碑中提供。
      </Typography.Text>
    </Card>
  );
}
