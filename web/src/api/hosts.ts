import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';

export interface Host {
  id: number;
  name: string;
  ip: string;
  sshPort: number;
  username: string;
  status: string;
  dockerVersion: string | null;
  osInfo: string | null;
  lastSeenAt: string | null;
  createdAt: string;
}

export interface CreateHostParams {
  name: string;
  ip: string;
  sshPort: number;
  username: string;
  password: string;
}

export interface CheckResult {
  ok: boolean;
  dockerVersion: string | null;
  osInfo: string | null;
  error: string | null;
}

export function useHosts() {
  return useQuery<Host[]>({
    queryKey: ['hosts'],
    queryFn: async () => (await api.get<Host[]>('/hosts')).data,
    refetchInterval: 15000,
  });
}

export function useCreateHost() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (params: CreateHostParams) =>
      (await api.post<Host>('/hosts', params)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['hosts'] }),
  });
}

export function useDeleteHost() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => api.delete(`/hosts/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['hosts'] }),
  });
}

export function useCheckHost() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) =>
      (await api.post<CheckResult>(`/hosts/${id}/check`)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['hosts'] }),
  });
}
