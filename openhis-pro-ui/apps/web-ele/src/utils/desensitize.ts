/**
 * 身份证号脱敏：保留前 6 位与后 4 位，中间使用 *。
 */
export function maskIdCard(idCardNo: string): string {
  const raw = idCardNo.trim();
  if (!raw) return '';
  if (raw.length <= 10) return raw;
  const prefix = raw.slice(0, 6);
  const suffix = raw.slice(-4);
  const middle = '*'.repeat(raw.length - 10);
  return `${prefix}${middle}${suffix}`;
}
