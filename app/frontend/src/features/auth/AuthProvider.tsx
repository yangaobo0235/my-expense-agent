import { PropsWithChildren, useEffect } from 'react';
import { Spin } from 'antd';
import { httpClient, setAuthenticationFailureHandler } from '../../api/http-client';
import { AuthUser, UserRole, useAuthStore } from './auth-store';

const developmentMode = import.meta.env.VITE_AUTH_MODE === 'development';
const developmentSignedOutKey = 'expense-development-signed-out';
const developmentUser: AuthUser = {
  subject: 'development-user',
  displayName: '本地开发用户',
  roles: ['STUDENT', 'COLLEGE_REVIEWER', 'FINANCE_ADMIN'],
};

interface SessionResponse {
  authenticated: boolean;
  subject?: string;
  displayName?: string;
  roles: string[];
}

function asUser(session: SessionResponse): AuthUser | undefined {
  if (!session.authenticated || !session.subject) return undefined;
  return {
    subject: session.subject,
    displayName: session.displayName ?? session.subject,
    roles: session.roles.filter((role): role is UserRole =>
      ['STUDENT', 'ADVISOR', 'COLLEGE_REVIEWER', 'FINANCE_ADMIN', 'AUDITOR'].includes(role),
    ),
  };
}

export function AuthProvider({ children }: PropsWithChildren) {
  const ready = useAuthStore((state) => state.ready);
  const setSession = useAuthStore((state) => state.setSession);

  useEffect(() => {
    setAuthenticationFailureHandler(() => {
      if (developmentMode) localStorage.setItem(developmentSignedOutKey, 'true');
      setSession(undefined);
    });
    if (developmentMode) {
      if (localStorage.getItem(developmentSignedOutKey) === 'true') {
        setSession(undefined);
        return;
      }
      const localUser = localStorage.getItem('expense-e2e-user');
      setSession(localUser ? JSON.parse(localUser) : developmentUser);
      return;
    }
    void httpClient
      .get<SessionResponse>('/api/v1/auth/session')
      .then((response) => setSession(asUser(response.data)))
      .catch(() => setSession(undefined));
  }, [setSession]);

  if (!ready) {
    return (
      <div className="center-screen">
        <Spin size="large" description="正在读取登录状态" />
      </div>
    );
  }
  return children;
}

export async function login(username: string, password: string) {
  if (developmentMode) {
    localStorage.removeItem(developmentSignedOutKey);
    const user = useAuthStore.getState().user ?? developmentUser;
    useAuthStore.getState().setSession(user);
    return user;
  }
  const response = await httpClient.post<SessionResponse>('/api/v1/auth/login', {
    username,
    password,
  });
  const user = asUser(response.data);
  useAuthStore.getState().setSession(user);
  return user;
}

export async function logout() {
  if (developmentMode) {
    localStorage.setItem(developmentSignedOutKey, 'true');
  } else {
    await httpClient.post('/api/v1/auth/logout');
  }
  useAuthStore.getState().setSession(undefined);
  window.location.assign('/login');
}
