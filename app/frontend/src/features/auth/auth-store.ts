import { create } from 'zustand';

export type UserRole =
  | 'EMPLOYEE'
  | 'REVIEWER'
  | 'FINANCE_ADMIN'
  | 'PROMPT_AUTHOR'
  | 'PROMPT_REVIEWER'
  | 'PROMPT_PUBLISHER'
  | 'AUDITOR';

export interface AuthUser {
  subject: string;
  displayName: string;
  roles: UserRole[];
}

interface AuthState {
  ready: boolean;
  authenticated: boolean;
  user?: AuthUser;
  setSession: (user?: AuthUser) => void;
  setReady: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  ready: false,
  authenticated: false,
  setSession: (user) => set({ user, authenticated: Boolean(user), ready: true }),
  setReady: () => set({ ready: true }),
}));

export const hasAnyRole = (
  roles: UserRole[] | undefined,
  expected: UserRole[],
) => Boolean(roles?.some((role) => expected.includes(role)));
