/** 金额展示：空值占位，否则保留 2～4 位小数。 */
export function formatAdjustmentAmount(value: unknown, placeholder = '—'): string {
  if (value === null || value === undefined || value === '') {
    return placeholder;
  }
  const n = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(n)) {
    return placeholder;
  }
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
}
