import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';

/** 金额、术语、费用性质等展示/默认值 */

export function formatUnitPriceYuan(value: number | string | null | undefined): string {
  if (value === null || value === undefined || String(value).trim() === '') return '—';
  const n = typeof value === 'number' ? value : Number(String(value).trim());
  if (!Number.isFinite(n)) return '—';
  return `${n.toFixed(2)}元`;
}

export function formatMoneyStrYuan(amountTwoDecimals: string): string {
  const t = String(amountTwoDecimals ?? '').trim();
  if (t === '' || t === '—') return '—';
  const n = Number(t);
  if (!Number.isFinite(n)) return `${t}元`;
  return `${n.toFixed(2)}元`;
}

export function mapPatientServiceTypeToFeeNature(label: string): string {
  const s = String(label || '').trim();
  if (!s) return '';
  if (s.includes('自费')) return '自费';
  if (/医保|医疗保险|社保|公费/.test(s)) return '医保';
  return '医保';
}

export function firstTerminologyCode(options: TerminologyOptionDto[] | undefined): string {
  const raw = options?.[0]?.value;
  if (typeof raw === 'string' || typeof raw === 'number') {
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return '';
}

export function terminologyLabel(
  options: TerminologyOptionDto[] | undefined,
  code: string,
): string {
  const c = String(code || '').trim();
  if (!c) return '—';
  const hit = (options ?? []).find((o) => String(o.value ?? '').trim() === c);
  const lb = String(hit?.label ?? '').trim();
  return lb || c;
}
