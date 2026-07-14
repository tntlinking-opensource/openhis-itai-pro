import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';

import { terminologyLabel } from '#/views/outpatient/doctor-station/advice/services/advice-format';

/** 解析单味特殊煎法展示名 */
export function resolveTcmDosageInstructionLabel(
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

/** 特殊煎法下拉；已选但不在字典中的编码补一项 */
export function buildTcmDosageInstructionSelectOptions(
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
  const label = resolveTcmDosageInstructionLabel(code, options);
  return [{ value: code, label: label || code }, ...list];
}
