/** 解析盈亏数值；无效值返回 null。 */
export function parseImpactNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const n = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}

export type ImpactTone = 'loss' | 'neutral' | 'profit';

/** 盈亏方向：正为盈、负为亏、零或空为中性。 */
export function resolveImpactTone(value: unknown): ImpactTone {
  const n = parseImpactNumber(value);
  if (n === null || n === 0) {
    return 'neutral';
  }
  return n > 0 ? 'profit' : 'loss';
}

/** 盈亏文字色 class。 */
export function resolveImpactTextClass(value: unknown): string {
  const tone = resolveImpactTone(value);
  if (tone === 'profit') {
    return 'text-[var(--el-color-success)]';
  }
  if (tone === 'loss') {
    return 'text-[var(--el-color-danger)]';
  }
  return 'text-[var(--el-text-color-regular)]';
}
