import { Button, Card, Space, Typography } from 'antd';
import { Navigate } from 'react-router-dom';
import { login } from './AuthProvider';
import { useAuthStore } from './auth-store';

export function LoginPage() {
  const authenticated = useAuthStore((state) => state.authenticated);
  if (authenticated) return <Navigate to="/cases" replace />;
  return (
    <div className="login-page">
      <Card className="login-card">
        <div className="brand-mark large">CF</div>
        <Space orientation="vertical" size="large">
          <div>
            <Typography.Title level={2}>my-expense-agent</Typography.Title>
            <Typography.Paragraph type="secondary">
              登录后查看校园经费申请进度、合规证据与人工复核任务。
            </Typography.Paragraph>
          </div>
          <Button type="primary" size="large" block onClick={() => void login()}>
            使用 Keycloak 安全登录
          </Button>
        </Space>
      </Card>
    </div>
  );
}
