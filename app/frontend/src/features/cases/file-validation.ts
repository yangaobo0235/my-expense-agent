const allowedTypes = new Set(['application/pdf', 'image/png', 'image/jpeg']);
export const maxExpenseFileSize = 10 * 1024 * 1024;

export function isValidExpenseFile(file: Pick<File, 'type' | 'size'>) {
  return allowedTypes.has(file.type) && file.size > 0 && file.size <= maxExpenseFileSize;
}
