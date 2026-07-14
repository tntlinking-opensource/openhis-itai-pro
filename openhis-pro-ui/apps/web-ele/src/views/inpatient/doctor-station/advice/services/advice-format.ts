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
