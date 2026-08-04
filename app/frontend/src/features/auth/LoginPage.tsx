import { BankOutlined, LockOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, Button, Form, Input, Typography } from 'antd';
import axios from 'axios';
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { login } from './AuthProvider';
import { useAuthStore } from './auth-store';

interface LoginValues {
  username: string;
  password: string;
}

export function LoginPage() {
  const authenticated = useAuthStore((state) => state.authenticated);
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string>();
  const [submitting, setSubmitting] = useState(false);
  const expired = new URLSearchParams(location.search).get('reason') === 'session-expired';
  if (authenticated) return <Navigate to="/cases" replace />;

  const submit = async (values: LoginValues) => {
    setError(undefined);
    setSubmitting(true);
    try {
      const user = await login(values.username, values.password);
      if (!user) throw new Error('登录响应无效');
      navigate('/cases', { replace: true });
    } catch (requestError) {
      const message = axios.isAxiosError(requestError)
        ? requestError.response?.data?.message
        : undefined;
      setError(message ?? '账号或密码错误，请重新输入。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="login-page">
      <section className="login-masthead" aria-label="系统信息">
        <div className="login-seal"><BankOutlined /></div>
        <div>
          <Typography.Text className="login-masthead-kicker">校园财务服务中心</Typography.Text>
          <Typography.Title id="login-title" level={1}>财务管理信息平台</Typography.Title>
        </div>
        <div className="login-masthead-rule" />
        <Typography.Text className="login-masthead-meta">经费申请 · 分级审核 · 财务入账</Typography.Text>
      </section>
      <section className="login-panel" aria-labelledby="login-form-title">
        <div className="login-form-heading">
          <Typography.Text className="login-kicker">用户认证</Typography.Text>
          <Typography.Title id="login-form-title" level={2}>账号登录</Typography.Title>
        </div>
        <div className="login-form-body">
          {(error || expired) && (
            <Alert
              className="login-alert"
              type={error ? 'error' : 'warning'}
              showIcon
              message={error ?? '登录状态已过期，请重新登录。'}
            />
          )}
          <Form<LoginValues> layout="vertical" requiredMark={false} onFinish={submit}>
            <Form.Item
              label="账号"
              name="username"
              rules={[{ required: true, message: '请输入账号' }]}
            >
              <Input prefix={<UserOutlined />} size="large" autoComplete="username" autoFocus />
            </Form.Item>
            <Form.Item
              label="密码"
              name="password"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password prefix={<LockOutlined />} size="large" autoComplete="current-password" />
            </Form.Item>
            <Button type="primary" size="large" htmlType="submit" block loading={submitting}>
              登录
            </Button>
          </Form>
          <Typography.Text type="secondary" className="login-footnote">
            账号权限由财务管理员统一维护
          </Typography.Text>
        </div>
      </section>
    </main>
  );
}
