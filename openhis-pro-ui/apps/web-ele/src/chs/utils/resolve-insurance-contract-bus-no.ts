/**
 * 按合同主键解析省/市医保契约 busNo（费用性质列表）。
 *
 * <p>仅用于 CHS 事前提醒 3101 / OAuth 签到等省医保链路；
 * 工伤（WORKER_INSURANCE / *-GS）走 Win，不得当作 CHS 契约。</p>
 */
import type { ChargeType } from '#/api/core/common';

import { getChargeTypeListApi } from '#/api/core/common';
import { isSelfChargeCategory } from '#/chs/constants/insurance-contract';
import { isWorkInjuryContractCategory } from '#/views/outpatient/charge/register/services/charge-type-category';

export interface ResolvedInsuranceContract {
  categoryEnum?: string;
  contractBusNo: string;
  contractId: string;
}

/** 是否可作为省/市医保 CHS 契约（有 busNo、非自费、非工伤）。 */
function isInsuranceEligible(item: ChargeType): boolean {
  const busNo = String(item.busNo ?? '').trim();
  if (!busNo) {
    return false;
  }
  if (isSelfChargeCategory(item.categoryEnum)) {
    return false;
  }
  // 工伤契约无省医保 OAuth，3101/9001 会报「契约未配置 OAuth 密码」
  if (isWorkInjuryContractCategory(item.categoryEnum) || busNo.endsWith('-GS')) {
    return false;
  }
  return true;
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
