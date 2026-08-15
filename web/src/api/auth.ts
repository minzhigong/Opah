import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';

export interface UserInfo {
  id: number;
  username: string;
  role: string;
}

export function useMe() {
  return useQuery<UserInfo>({
    queryKey: ['me'],
    queryFn: async () => (await api.get<UserInfo>('/auth/me')).data,
  });
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (params: { username: string; password: string }) =>
      (await api.post<UserInfo>('/auth/login', params)).data,
    onSuccess: (user) => queryClient.setQueryData(['me'], user),
  });
}

export function useLogout() {
  return useMutation({
    mutationFn: async () => api.post('/auth/logout'),
  });
}
