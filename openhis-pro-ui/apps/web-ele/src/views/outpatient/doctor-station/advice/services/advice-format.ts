import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';

import { isSelfChargeCategory } from '#/chs/constants/insurance-contract';
import {
  isInsuranceContractCategory,
  isPublicChargeContractCategory,
  isSelfPayContractCategory,
} from '#/views/outpatient/charge/register/services/charge-type-category';

import { formatMoneyYuan } from '#/utils/format/money-scale';

/** 金额、术语、费用性质等展示/默认值 */

/** 院内医保等级码：1=自费（开立时费用性质仅允许自费合同） */
export const CHRGITM_LV_SELF_PAY = '1';

/** 费用性质下拉项（可附带合同 categoryEnum，供自费过滤） */
export type FeeNatureOption = TerminologyOptionDto & { categoryEnum?: string };

/** 药品医保等级是否为「仅自费」 */
export function isSelfPayOnlyChrgitmLv(chrgitmLv?: number | string | null): boolean {
  return String(chrgitmLv ?? '').trim() === CHRGITM_LV_SELF_PAY;
}

export function formatUnitPriceYuan(value: null | number | string | undefined): string {
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

/** 用户选择费用性质后同步 contractId 与展示名 feeNature。 */
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

/** 用 chargeType 列表给费用性质下拉补 categoryEnum（按 contractId / busNo） */
export function enrichFeeNatureOptionsWithCategory(
  options: readonly TerminologyOptionDto[] | undefined,
  chargeTypes:
    | readonly Array<{ busNo?: string; categoryEnum?: string; value?: string }>
    | undefined,
): FeeNatureOption[] {
  const types = chargeTypes ?? [];
  return (options ?? []).map((opt) => {
    const id = String(opt.value ?? '').trim();
    const busNo = String(opt.busNo ?? '').trim();
    // 先按合同主键匹配，再按 busNo 兜底
    const hit = types.find((t) => {
      const tv = String(t.value ?? '').trim();
      if (id && tv === id) return true;
      const tb = String(t.busNo ?? '').trim();
      return Boolean(busNo && tb && tb === busNo);
    });
    return {
      ...opt,
      categoryEnum: hit?.categoryEnum,
    };
  });
}

/** 是否自费费用性质选项（按 categoryEnum code） */
export function isSelfPayFeeNatureOption(opt: FeeNatureOption): boolean {
  return isSelfPayContractCategory(opt.categoryEnum) || isSelfChargeCategory(opt.categoryEnum);
}

/**
 * 按药品医保等级收窄费用性质下拉：仅自费等级时只保留自费合同。
 * 无法识别自费项时回落原列表，避免下拉被清空。
 */
export function filterFeeNatureOptionsByChrgitmLv(
  options: readonly FeeNatureOption[] | undefined,
  chrgitmLv?: number | string | null,
): FeeNatureOption[] {
  const list = [...(options ?? [])];
  if (!isSelfPayOnlyChrgitmLv(chrgitmLv)) {
    return list;
  }
  const selfPay = list.filter((o) => isSelfPayFeeNatureOption(o));
  return selfPay.length > 0 ? selfPay : list;
}

/**
 * 按医保等级解析应选合同：自费等级强制自费合同；否则尽量保留当前选中。
 */
export function resolveContractForChrgitmLv(
  options: readonly FeeNatureOption[] | undefined,
  chrgitmLv?: number | string | null,
  preferredContractId?: string,
): { contractId: string; feeNature: string } {
  const filtered = filterFeeNatureOptionsByChrgitmLv(options, chrgitmLv);
  const preferred = String(preferredContractId ?? '').trim();
  if (preferred && filtered.some((o) => String(o.value ?? '').trim() === preferred)) {
    return contractSelectionFromValue(preferred, filtered);
  }
  return defaultContractSelection(filtered);
}

/** 处方内是否存在「仅自费」等级药材/药品 */
export function hasSelfPayOnlyChrgitmLvItem(
  items: ReadonlyArray<{ chrgitmLv?: number | string | null }>,
): boolean {
  return items.some((item) => isSelfPayOnlyChrgitmLv(item.chrgitmLv));
}
