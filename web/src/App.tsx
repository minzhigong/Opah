import { Spin } from 'antd';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useMe } from './api/auth';
import Dashboard from './pages/Dashboard';
import Hosts from './pages/Hosts';
import Layout from './pages/Layout';
import Login from './pages/Login';

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { data, isLoading, isError } = useMe();
  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 200 }}>
        <Spin size="large" />
      </div>
    );
  }
  if (isError || !data) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Dashboard />} />
        <Route path="hosts" element={<Hosts />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
