import { PropsWithChildren, useEffect } from 'react';
import Keycloak, { KeycloakTokenParsed } from 'keycloak-js';
import { Spin } from 'antd';
import { setAccessTokenProvider } from '../../api/http-client';
import { useAuthStore, UserRole } from './auth-store';

const developmentMode = import.meta.env.VITE_AUTH_MODE === 'development';

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:18080',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'my-expense-agent',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'my-expense-agent-web',
});

function rolesOf(token?: KeycloakTokenParsed): UserRole[] {
  const realmAccess = token?.realm_access as { roles?: string[] } | undefined;
  return (realmAccess?.roles ?? []).filter((role): role is UserRole =>
    [
      'STUDENT',
      'ADVISOR',
      'COLLEGE_REVIEWER',
      'FINANCE_ADMIN',
      'PROMPT_AUTHOR',
      'PROMPT_REVIEWER',
      'PROMPT_PUBLISHER',
      'AUDITOR',
    ].includes(role),
  );
}

export function AuthProvider({ children }: PropsWithChildren) {
  const ready = useAuthStore((state) => state.ready);
  const setSession = useAuthStore((state) => state.setSession);

  useEffect(() => {
    if (developmentMode) {
      setAccessTokenProvider(() => undefined);
      if (new URLSearchParams(window.location.search).get('reason') === 'session-expired') {
        setSession(undefined);
        return;
      }
      const localUser = localStorage.getItem('expense-e2e-user');
      if (localUser) {
        setSession(JSON.parse(localUser));
        return;
      }
      setSession({
        subject: 'development-user',
        displayName: '本地开发用户',
        roles: ['STUDENT', 'COLLEGE_REVIEWER', 'FINANCE_ADMIN'],
      });
      return;
    }

    void keycloak
      .init({ onLoad: 'check-sso', pkceMethod: 'S256', checkLoginIframe: false })
      .then((authenticated) => {
        setAccessTokenProvider(async () => {
          if (!keycloak.authenticated) return undefined;
          await keycloak.updateToken(30);
          return keycloak.token;
        });
        if (!authenticated || !keycloak.tokenParsed) {
          setSession(undefined);
          return;
        }
        setSession({
          subject: keycloak.subject ?? '',
          displayName:
            keycloak.tokenParsed.preferred_username ??
            keycloak.tokenParsed.name ??
            'my-expense-agent 用户',
          roles: rolesOf(keycloak.tokenParsed),
        });
      })
      .catch(() => setSession(undefined));
  }, [setSession]);

  if (!ready) {
    return (
      <div className="center-screen">
        <Spin size="large" description="正在建立安全会话…" />
      </div>
    );
  }
  return children;
}

export function login() {
  return developmentMode
    ? Promise.resolve()
    : keycloak.login({ redirectUri: window.location.origin });
}

export function logout() {
  if (developmentMode) {
    window.location.href = '/login';
    return Promise.resolve();
  }
  return keycloak.logout({ redirectUri: `${window.location.origin}/login` });
}
