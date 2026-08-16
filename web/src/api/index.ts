import { client } from './client';

// system
export const getSetupStatus = () => client.get('/system/setup-status');
export const setup = (username: string, password: string) => client.post('/system/setup', { username, password });
export const getDockerStatus = () => client.get('/system/docker-status');
export const login = (username: string, password: string) => client.post('/auth/login', { username, password });

// dashboard
export const getOverview = () => client.get('/dashboard/overview');

// hosts
export const listHosts = () => client.get('/hosts');
export const addHost = (data: any) => client.post('/hosts', data);
export const testHost = (id: number) => client.post(`/hosts/${id}/test`);
export const listHostContainers = (id: number) => client.get(`/hosts/${id}/containers`);
export const deleteHost = (id: number) => client.delete(`/hosts/${id}`);
export const containerAction = (hostId: number, cid: string, action: string) =>
  client.post(`/hosts/${hostId}/containers/${cid}/actions`, { action });
export const containerLogs = (hostId: number, cid: string, lines = 200) =>
  client.post(`/hosts/${hostId}/containers/${cid}/logs`, { lines });

// projects
export const listProjects = () => client.get('/projects');
export const addProject = (data: any) => client.post('/projects', data);
export const scanProject = (id: number) => client.post(`/projects/${id}/scan`);
export const confirmServices = (id: number, units: any[]) => client.post(`/projects/${id}/services`, units);
export const listServices = (id: number) => client.get(`/projects/${id}/services`);
export const deleteProject = (id: number) => client.delete(`/projects/${id}`);

// services
export const getService = (id: number) => client.get(`/services/${id}`);
export const deleteService = (id: number) => client.delete(`/services/${id}`);
export const listBuilds = (id: number) => client.get(`/services/${id}/builds`);
export const triggerBuild = (id: number, ref: string) => client.post(`/services/${id}/builds`, { ref });
export const buildLogs = (id: number, buildId: number, afterLine = 0) =>
  client.get(`/services/${id}/builds/${buildId}/logs`, { params: { afterLine } });
export const listEntries = (id: number) => client.get(`/services/${id}/config-entries`);
export const saveEntries = (id: number, entries: any[]) => client.put(`/services/${id}/config-entries`, entries);
export const listConfigFiles = (id: number) => client.get(`/services/${id}/config-files`);
export const saveConfigFiles = (id: number, files: Record<string, string>) =>
  client.put(`/services/${id}/config-files`, files);
export const updateNginx = (id: number, cfg: any) => client.put(`/services/${id}/nginx-config`, cfg);
export const deploy = (id: number, data: any) => client.post(`/services/${id}/deployments`, data);
export const listDeployments = (id: number) => client.get(`/services/${id}/deployments`);
export const rollback = (deploymentId: number) => client.post(`/services/deployments/${deploymentId}/rollback`);
