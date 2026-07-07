import { describe, expect, it } from 'vitest';
import { hasAnyRole } from './auth-store';

describe('hasAnyRole', () => {
  it('matches at least one required role', () => {
    expect(hasAnyRole(['EMPLOYEE', 'REVIEWER'], ['REVIEWER'])).toBe(true);
    expect(hasAnyRole(['EMPLOYEE'], ['FINANCE_ADMIN'])).toBe(false);
  });
});
