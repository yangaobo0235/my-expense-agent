import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Result } from 'antd';
import { hasAnyRole, useAuthStore, UserRole } from '../features/auth/auth-store';

export function ProtectedRoute({ roles }: { roles?: UserRole[] }) {
  const location = useLocation();
  const authenticated = useAuthStore((state) => state.authenticated);
  const user = useAuthStore((state) => state.user);

  if (!authenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (roles && !hasAnyRole(user?.roles, roles)) {
    return <Result status="403" title="无权访问" subTitle="当前角色不能访问此页面。" />;
  }
  return <Outlet />;
}
