/**
 * 按合同主键解析医保契约 busNo（费用性质列表）。
 */
import type { ChargeType } from '#/api/core/common';

import { getChargeTypeListApi } from '#/api/core/common';
import { isSelfChargeCategory } from '#/chs/constants/insurance-contract';

export interface ResolvedInsuranceContract {
  categoryEnum?: string;
  contractBusNo: string;
  contractId: string;
}

/** 是否可作为医保契约（有 busNo 且非自费）。 */
function isInsuranceEligible(item: ChargeType): boolean {
  const busNo = String(item.busNo ?? '').trim();
  if (!busNo) {
    return false;
  }
  return !isSelfChargeCategory(item.categoryEnum);
}

/** 按 contractId 解析可走医保的契约；自费或不存在则 undefined。 */
export async function resolveInsuranceContractById(
  contractId: number | string | undefined | null,
): Promise<ResolvedInsuranceContract | undefined> {
  const id = String(contractId ?? '').trim();
  if (!id) {
    return undefined;
  }
  const list = await getChargeTypeListApi();
  const hit = (list ?? []).find((t) => String(t.value) === id);
  if (!hit || !isInsuranceEligible(hit)) {
    return undefined;
  }
  return {
    contractId: id,
    contractBusNo: String(hit.busNo).trim(),
    categoryEnum: hit.categoryEnum,
  };
}

/** 从多个 contractId 中取第一个医保契约。 */
export async function pickFirstInsuranceContract(
  contractIds: Array<number | string | undefined | null>,
): Promise<ResolvedInsuranceContract | undefined> {
  const seen = new Set<string>();
  const ordered: string[] = [];
  for (const raw of contractIds) {
    const id = String(raw ?? '').trim();
    if (!id || seen.has(id)) {
      continue;
    }
    seen.add(id);
    ordered.push(id);
  }
  if (ordered.length === 0) {
    return undefined;
  }
  const list = await getChargeTypeListApi();
  for (const id of ordered) {
    const hit = (list ?? []).find((t) => String(t.value) === id);
    if (hit && isInsuranceEligible(hit)) {
      return {
        contractId: id,
        contractBusNo: String(hit.busNo).trim(),
        categoryEnum: hit.categoryEnum,
      };
    }
  }
  return undefined;
}
