import axios from 'axios';

const token = () => localStorage.getItem('opah_token') || '';

export const client = axios.create({
  baseURL: '/api/v1'
});

client.interceptors.request.use((config) => {
  config.headers['X-Auth-Token'] = token();
  return config;
});

client.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('opah_token');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);
