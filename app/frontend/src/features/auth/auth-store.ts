import { create } from 'zustand';

export type UserRole =
  | 'STUDENT'
  | 'ADVISOR'
  | 'COLLEGE_REVIEWER'
  | 'FINANCE_ADMIN'
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

export const hasOnlyRole = (
  roles: UserRole[] | undefined,
  expected: UserRole,
) => roles?.length === 1 && roles[0] === expected;

const roleLabels: Record<UserRole, string> = {
  STUDENT: '学生',
  ADVISOR: '指导教师',
  COLLEGE_REVIEWER: '学院审核员',
  FINANCE_ADMIN: '财务管理员',
  AUDITOR: '审计员',
};

export const roleLabel = (role: UserRole) => roleLabels[role];
