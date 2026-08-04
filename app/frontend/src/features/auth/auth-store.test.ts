import { describe, expect, it } from 'vitest';
import { hasAnyRole, hasOnlyRole } from './auth-store';

describe('hasAnyRole', () => {
  it('matches at least one required role', () => {
    expect(hasAnyRole(['STUDENT', 'COLLEGE_REVIEWER'], ['COLLEGE_REVIEWER'])).toBe(true);
    expect(hasAnyRole(['STUDENT'], ['FINANCE_ADMIN'])).toBe(false);
  });
});

describe('hasOnlyRole', () => {
  it('treats only an exclusive auditor account as read-only', () => {
    expect(hasOnlyRole(['AUDITOR'], 'AUDITOR')).toBe(true);
    expect(hasOnlyRole(['FINANCE_ADMIN', 'AUDITOR'], 'AUDITOR')).toBe(false);
    expect(hasOnlyRole(undefined, 'AUDITOR')).toBe(false);
  });
});
