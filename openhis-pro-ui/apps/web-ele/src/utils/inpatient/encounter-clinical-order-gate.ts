/** 就诊已办理出院/待结算/已结算/已离院：禁止医嘱执行与开立变更（BR-IP-FLW-008）。 */

const ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED = new Set(['PENDING', 'COMPLETED', 'DISCHARGED']);

/** 就诊 statusEnum 是否禁止医嘱临床写操作 */
export function isEncounterClinicalOrderMutationBlocked(statusEnum?: string | null): boolean {
  return ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED.has(String(statusEnum ?? '').trim());
}

/** 就诊已办理出院/已结算/已离院：禁止产生或修改费用（非 ChargeItem 账单态）。 */
export const ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE =
  '患者已办理出院或已结算，不可变更医嘱及费用';
