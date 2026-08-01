import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';

import { terminologyLabel } from '#/views/outpatient/doctor-station/advice/services/advice-format';

/** 中药用法默认编码（种子 tcm_method_code.T01 水煎服 / 煎服） */
export const DEFAULT_TCM_METHOD_CODE = 'T01';

/** 中药默认用药频次：每日两次（种子 medication_rate.BID） */
export const DEFAULT_TCM_RATE_CODE = 'BID';

/** 解析中药用法展示名 */
export function resolveTcmMethodLabel(
  code: string | undefined,
  options: TerminologyOptionDto[] | undefined,
  fallbackName?: null | string,
): string {
  const trimmed = String(code ?? '').trim();
  if (!trimmed) return '';
  const fromOptions = terminologyLabel(options, trimmed);
  if (fromOptions !== '—' && fromOptions !== trimmed) return fromOptions;
  const fb = String(fallbackName ?? '').trim();
  return fb || trimmed;
}

/** 新建处方默认用法：优先 T01（水煎服），否则标签含「煎服」，再否则字典首项 */
export function resolveDefaultTcmMethodCode(options: TerminologyOptionDto[] | undefined): string {
  const list = options ?? [];
  const hasDefault = list.some((opt) => String(opt.value ?? '').trim() === DEFAULT_TCM_METHOD_CODE);
  if (hasDefault) return DEFAULT_TCM_METHOD_CODE;
  // 兼容院点改名（如仅显示「煎服」）
  const byLabel = list.find((opt) => String(opt.label ?? '').includes('煎服'));
  if (byLabel) {
    return String(byLabel.value ?? '').trim();
  }
  return String(list[0]?.value ?? '').trim() || DEFAULT_TCM_METHOD_CODE;
}

/** 解析用药频次展示名 */
export function resolveTcmRateLabel(
  code: string | undefined,
  options: TerminologyOptionDto[] | undefined,
  fallbackName?: null | string,
): string {
  const trimmed = String(code ?? '').trim();
  if (!trimmed) return '';
  const fromOptions = terminologyLabel(options, trimmed);
  if (fromOptions !== '—' && fromOptions !== trimmed) return fromOptions;
  const fb = String(fallbackName ?? '').trim();
  return fb || trimmed;
}

/** 新建处方默认频次：住院临嘱优先 STAT，长期/门诊优先 BID（每日两次） */
export function resolveDefaultTcmRateCodeForTherapy(
  therapyEnum: string | undefined,
  options: TerminologyOptionDto[] | undefined,
): string {
  const therapy = String(therapyEnum ?? '')
    .trim()
    .toUpperCase();
  if (therapy === 'TEMPORARY') {
    const hasStat = (options ?? []).some(
      (opt) =>
        String(opt.value ?? '')
          .trim()
          .toUpperCase() === 'STAT',
    );
    if (hasStat) return 'STAT';
  }
  return resolveDefaultTcmRateCode(options);
}

/** 新建处方默认频次：优先 BID（每日两次），否则字典首项 */
export function resolveDefaultTcmRateCode(options: TerminologyOptionDto[] | undefined): string {
  const hasDefault = (options ?? []).some(
    (opt) => String(opt.value ?? '').trim() === DEFAULT_TCM_RATE_CODE,
  );
  if (hasDefault) return DEFAULT_TCM_RATE_CODE;
  // 兼容标签文案「每日两次」
  const byLabel = (options ?? []).find((opt) => {
    const label = String(opt.label ?? '');
    return label.includes('每日两次') || label.toLowerCase().includes('bid');
  });
  if (byLabel) {
    return String(byLabel.value ?? '').trim();
  }
  const first = String(options?.[0]?.value ?? '').trim();
  return first || DEFAULT_TCM_RATE_CODE;
}

/** 用药频次下拉；已选但不在字典中的编码补一项 */
export function buildTcmRateSelectOptions(
  options: TerminologyOptionDto[],
  selectedCode?: null | string,
): Array<{ label: string; value: string }> {
  const list = options
    .filter((opt) => String(opt.value ?? '').trim())
    .map((opt) => ({
      value: String(opt.value),
      label: String(opt.label ?? opt.value),
    }));
  const code = String(selectedCode ?? '').trim();
  if (!code) return list;
  if (list.some((opt) => opt.value === code)) return list;
  const label = resolveTcmRateLabel(code, options);
  return [{ value: code, label: label || code }, ...list];
}

/** 下拉选项：仅 tcm_method_code 术语；已选但不在字典中的编码补一项 */
export function buildTcmMethodSelectOptions(
  options: TerminologyOptionDto[],
  selectedCode?: null | string,
): Array<{ label: string; value: string }> {
  const list = options
    .filter((opt) => String(opt.value ?? '').trim())
    .map((opt) => ({
      value: String(opt.value),
      label: String(opt.label ?? opt.value),
    }));
  const code = String(selectedCode ?? '').trim();
  if (!code) return list;
  if (list.some((opt) => opt.value === code)) return list;
  const label = resolveTcmMethodLabel(code, options);
  return [{ value: code, label: label || code }, ...list];
}
