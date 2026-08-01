import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';

import {
  isInsuranceContractCategory,
  isPublicChargeContractCategory,
  isSelfPayContractCategory,
} from '#/views/outpatient/charge/register/services/charge-type-category';

import { formatMoneyYuan } from '#/utils/format/money-scale';

/** 金额、术语、费用性质等展示/默认值 */

export function formatUnitPriceYuan(value: number | string | null | undefined): string {
  if (value === null || value === undefined || String(value).trim() === '') return '—';
  return formatMoneyYuan(value);
}

export function formatMoneyStrYuan(amountTwoDecimals: string): string {
  const t = String(amountTwoDecimals ?? '').trim();
  if (t === '' || t === '—') return '—';
  return formatMoneyYuan(t);
}

/** 费用性质 contract category code → 费用性质 code（逻辑判断用） */
export function mapPatientServiceTypeToFeeNature(categoryCode: string): string {
  const code = String(categoryCode ?? '').trim();
  if (!code) return '';
  if (
    isSelfPayContractCategory(code) ||
    isInsuranceContractCategory(code) ||
    isPublicChargeContractCategory(code)
  ) {
    return code;
  }
  return code;
}

export function firstTerminologyCode(options: TerminologyOptionDto[] | undefined): string {
  const raw = options?.[0]?.value;
  if (typeof raw === 'string' || typeof raw === 'number') {
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return '';
}

/** 按展示文案匹配术语 value；未命中返回空串 */
export function terminologyCodeByLabel(
  options: TerminologyOptionDto[] | undefined,
  label: string,
): string {
  const want = String(label ?? '').trim();
  if (!want) return '';
  const hit = (options ?? []).find((o) => String(o.label ?? '').trim() === want);
  const raw = hit?.value;
  if (typeof raw === 'string' || typeof raw === 'number') {
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return '';
}

/**
 * 医嘱频次默认值：临时 →「立即」；其余 → 术语首项。
 * 「立即」未配置时回落首项，避免空频次。
 */
export function defaultMedicationRateCode(
  options: TerminologyOptionDto[] | undefined,
  therapy?: null | string,
): string {
  const therapyCode = String(therapy ?? '')
    .trim()
    .toUpperCase();
  if (therapyCode === 'TEMPORARY') {
    return terminologyCodeByLabel(options, '立即') || firstTerminologyCode(options);
  }
  return firstTerminologyCode(options);
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

/** init 返回的费用性质下拉：取首项作为新开医嘱默认（后端已将就诊优先合同排首位）。 */
export function defaultContractSelection(options: TerminologyOptionDto[] | undefined): {
  contractId: string;
  feeNature: string;
} {
  const contractId = firstTerminologyCode(options);
  const label = terminologyLabel(options, contractId);
  return {
    contractId,
    feeNature: label === '—' ? '' : label,
  };
}

export function contractSelectionFromValue(
  contractId: string,
  options: TerminologyOptionDto[] | undefined,
): { contractId: string; feeNature: string } {
  const id = String(contractId ?? '').trim();
  const label = terminologyLabel(options, id);
  return {
    contractId: id,
    feeNature: label === '—' ? id : label,
  };
}
