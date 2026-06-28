import type {
  ContractInsuranceProfileDto,
  LoadRegisterFormInitResult,
} from '#/api/outpatient-registration';

import { loadRegisterFormInit } from '#/views/outpatient/charge/register/services/register.service';
import {
  INSURANCE_CONTRACT_CATEGORY_PRIORITY,
  inferInsuranceCategoryFromInsuplc,
  normalizeInsuplcAdmdvs,
} from '#/views/outpatient/charge/register/services/charge-type-category';
import { isInsuranceContractCategory } from '#/views/outpatient/charge/register/services/register-benefit.service';

export type InpatientRegisterContractMeta = Pick<
  LoadRegisterFormInitResult,
  'contractCategoryById' | 'contractInsuranceProfileById'
>;

/** 住院登记页加载费用性质元数据（类别码、busNo），供 1101/2001 路由与签到。 */
export async function loadInpatientRegisterContractMeta(): Promise<InpatientRegisterContractMeta> {
  const init = await loadRegisterFormInit({});
  return {
    contractCategoryById: init.contractCategoryById ?? {},
    contractInsuranceProfileById: init.contractInsuranceProfileById ?? {},
  };
}

export function getInpatientContractCategory(
  contractId: string | undefined,
  meta: InpatientRegisterContractMeta,
): string | undefined {
  if (!contractId) {
    return undefined;
  }
  return meta.contractCategoryById[String(contractId)];
}

export function getInpatientContractBusNo(
  contractId: string | undefined,
  meta: InpatientRegisterContractMeta,
): string | undefined {
  if (!contractId) {
    return undefined;
  }
  const profile: ContractInsuranceProfileDto | undefined =
    meta.contractInsuranceProfileById[String(contractId)];
  const busNo = String(profile?.busNo ?? '').trim();
  return busNo || undefined;
}

export function findInpatientBootstrapInsuranceContractId(
  meta: InpatientRegisterContractMeta,
): string | undefined {
  for (const code of INSURANCE_CONTRACT_CATEGORY_PRIORITY) {
    const matched = Object.entries(meta.contractCategoryById).find(([, cat]) => cat === code);
    if (matched) {
      return matched[0];
    }
  }
  return Object.entries(meta.contractCategoryById).find(([, cat]) =>
    isInsuranceContractCategory(cat),
  )?.[0];
}

export function findInpatientBootstrapInsuranceContractIdByInsuplc(
  insuplcAdmdvs: string | undefined,
  meta: InpatientRegisterContractMeta,
): string | undefined {
  const insu = normalizeInsuplcAdmdvs(insuplcAdmdvs);
  if (insu.length >= 4) {
    const preferredCategory = inferInsuranceCategoryFromInsuplc(insu);
    if (preferredCategory) {
      const matched = Object.entries(meta.contractCategoryById).find(
        ([, cat]) => cat === preferredCategory,
      );
      if (matched) {
        return matched[0];
      }
    }
    let bestId: string | undefined;
    let bestLen = -1;
    for (const [id, profile] of Object.entries(meta.contractInsuranceProfileById)) {
      const treatment = normalizeInsuplcAdmdvs(profile.treatmentAreaCode);
      if (!treatment || !isInsuranceContractCategory(meta.contractCategoryById[id])) {
        continue;
      }
      let len = 0;
      while (len < Math.min(insu.length, treatment.length) && insu[len] === treatment[len]) {
        len += 1;
      }
      if (len > bestLen) {
        bestLen = len;
        bestId = id;
      }
    }
    if (bestId && bestLen >= 4) {
      return bestId;
    }
  }
  return findInpatientBootstrapInsuranceContractId(meta);
}

export function resolveInpatientContractLabel(
  contractId: string | undefined,
  contractOptions: Array<{ label: string; value: string }>,
): string {
  if (!contractId) {
    return '';
  }
  const opt = contractOptions.find((item) => String(item.value) === String(contractId));
  return String(opt?.label ?? '');
}
