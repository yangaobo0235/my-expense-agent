import { describe, expect, it } from 'vitest';
import { isValidExpenseFile, maxExpenseFileSize } from './file-validation';

describe('isValidExpenseFile', () => {
  it('accepts supported non-empty files within the limit', () => {
    expect(isValidExpenseFile({ type: 'application/pdf', size: 1024 })).toBe(true);
    expect(isValidExpenseFile({ type: 'image/jpeg', size: maxExpenseFileSize })).toBe(true);
  });

  it('rejects spoof-prone types, empty files and oversized files', () => {
    expect(isValidExpenseFile({ type: 'text/plain', size: 1024 })).toBe(false);
    expect(isValidExpenseFile({ type: 'image/png', size: 0 })).toBe(false);
    expect(isValidExpenseFile({ type: 'image/png', size: maxExpenseFileSize + 1 })).toBe(false);
  });
});
