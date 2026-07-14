import {
  resolveTerminologyOptionLabel,
  type TerminologyOptionItem,
} from './resolve-terminology-option-label';

/** 联调前持久化的 legacy 病情 code → adm_cond_code 术语码 */
const LEGACY_ADM_COND_CODE_TO_TERM: Record<string, string> = {
  GENERAL: '3',
  URGENT: '2',
  CRITICAL: '1',
};

/**
 * 将持久化病情 code 归一为 adm_cond_code 术语码（1/2/3）。
 * 已是术语码或未知值时原样返回。
 */
export function normalizeAdmCondTermCode(code: string | null | undefined): string {
  const raw = String(code ?? '').trim();
  if (!raw) {
    return '';
  }
  return LEGACY_ADM_COND_CODE_TO_TERM[raw] ?? raw;
}

/**
 * adm_cond_code 病情展示名：legacy code 先归一，再查字典；未命中不展示 raw code。
 */
export function resolveAdmCondCodeLabel(
  code: string | null | undefined,
  options: TerminologyOptionItem[] | null | undefined,
): string {
  const raw = String(code ?? '').trim();
  if (!raw) {
    return '';
  }
  const termCode = normalizeAdmCondTermCode(raw);
  const byTerm = resolveTerminologyOptionLabel(termCode, options, '');
  if (byTerm && byTerm !== termCode) {
    return byTerm;
  }
  const byRaw = resolveTerminologyOptionLabel(raw, options, '');
  if (byRaw && byRaw !== raw) {
    return byRaw;
  }
  return '';
}
