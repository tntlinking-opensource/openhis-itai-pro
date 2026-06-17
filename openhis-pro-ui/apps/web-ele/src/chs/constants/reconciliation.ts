/** 3201 / 3203A 常用字典（吉林省基线） */

export const YB_INSUTYPE_OPTIONS = [
  { label: '职工基本医疗保险', value: '310' },
  { label: '城乡居民基本医疗保险', value: '390' },
  { label: '离休人员医疗保障', value: '340' },
] as const;

export const YB_CLR_TYPE_OPTIONS = [
  { label: '门诊', value: '11' },
  { label: '住院', value: '21' },
  { label: '药店购药', value: '41' },
] as const;

/** 3205A 清算申请状态 */
export const YB_CLR_STAS_OPTIONS = [
  { label: '已申请', value: '10' },
  { label: '已受理', value: '20' },
  { label: '已清算', value: '50' },
] as const;

const INSUTYPE_LABEL = Object.fromEntries(
  YB_INSUTYPE_OPTIONS.map((item) => [item.value, item.label]),
);
const CLR_TYPE_LABEL = Object.fromEntries(
  YB_CLR_TYPE_OPTIONS.map((item) => [item.value, item.label]),
);
const CLR_STAS_LABEL = Object.fromEntries(
  YB_CLR_STAS_OPTIONS.map((item) => [item.value, item.label]),
);

export function formatInsutype(code?: string | null): string {
  if (!code) return '—';
  return INSUTYPE_LABEL[code] ?? code;
}

export function formatClrType(code?: string | null): string {
  if (!code) return '—';
  return CLR_TYPE_LABEL[code] ?? code;
}

export function formatClrStas(code?: string | null): string {
  if (!code) return '—';
  return CLR_STAS_LABEL[code] ?? code;
}

/** 3201 对账结果 stmt_rslt */
export function formatStmtRslt(code?: string | null): string {
  if (!code) return '—';
  if (code === '0') return '平';
  if (code === '1') return '不平';
  return code;
}

export function isStmtBalanced(code?: string | null): boolean {
  return code === '0';
}
