/**
 * 医保页契约下拉：与 {@code ContractCategory} / InsuranceSchemeCode 对齐。
 * SELF_CHARGE（NONE）不参与 insurance 编排，不得出现在医保契约选择中。
 */

/** {@link com.openhis.health.domain.share.enums.ContractCategory#SELF_CHARGE} */
export const CONTRACT_CATEGORY_SELF_CHARGE = 'SELF_CHARGE';

/** 历史主数据：{@code ChargeTypeCategory} 自费 numeric code */
const LEGACY_SELF_CHARGE_CATEGORY = '1';

/** 是否为自费契约（不走 chs-contract / 医保网关） */
export function isSelfChargeCategory(categoryEnum?: string | null): boolean {
  if (categoryEnum == null || categoryEnum === '') {
    return false;
  }
  const key = String(categoryEnum).trim();
  return key === CONTRACT_CATEGORY_SELF_CHARGE || key === LEGACY_SELF_CHARGE_CATEGORY;
}

/** 过滤出可用于医保契约下拉的项（有 busNo 且非自费） */
export function filterInsuranceChargeTypes<T extends { busNo?: string; categoryEnum?: string }>(
  items: T[],
): T[] {
  return items.filter(
    (item) => Boolean(item.busNo?.trim()) && !isSelfChargeCategory(item.categoryEnum),
  );
}

/** 若当前 busNo 不在可选项内则清空；否则返回首个可选项 busNo */
export function resolveDefaultInsuranceBusNo(
  eligible: Array<{ busNo?: string }>,
  current?: string | null,
): string | undefined {
  const trimmed = current?.trim();
  if (trimmed && eligible.some((item) => item.busNo === trimmed)) {
    return trimmed;
  }
  return eligible[0]?.busNo?.trim() || undefined;
}
