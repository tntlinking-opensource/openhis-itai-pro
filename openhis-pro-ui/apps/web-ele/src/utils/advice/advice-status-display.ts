import type { TagProps } from 'element-plus';

type AdviceStatusTagType = NonNullable<TagProps['type']>;

/** 医嘱签发/请求态 Tag（含列表历史合并展示中的退费码，兼容住院仍用 displayStatus 的列） */
const ADVICE_STATUS_TAG_BY_CODE: Record<string, AdviceStatusTagType> = {
  DRAFT: 'primary',
  ACTIVE: 'success',
  CANCELLED: 'info',
  VERIFIED: 'warning',
  HOLD: 'warning',
  STOPPED: 'danger',
  COMPLETED: 'success',
  REFUNDING: 'warning',
  REFUNDED: 'danger',
};

/** 费用状态 Tag（ChargeItemStatus） */
const ADVICE_CHARGE_STATUS_TAG_BY_CODE: Record<string, AdviceStatusTagType> = {
  DRAFT: 'info',
  PLANNED: 'warning',
  PENDING_SETTLEMENT: 'warning',
  SETTLED: 'success',
  REFUNDING: 'warning',
  REFUNDED: 'danger',
};

/** 医嘱签发态 Tag：仅认 statusEnum code，不认展示文案 */
export function resolveAdviceStatusTagTypeFromCode(
  statusEnum?: null | string,
): AdviceStatusTagType {
  const code = String(statusEnum ?? '')
    .trim()
    .toUpperCase();
  return ADVICE_STATUS_TAG_BY_CODE[code] ?? 'info';
}

/** 费用状态 Tag：仅认 chargeStatusEnum code */
export function resolveAdviceChargeStatusTagTypeFromCode(
  chargeStatusEnum?: null | string,
): AdviceStatusTagType {
  const code = String(chargeStatusEnum ?? '')
    .trim()
    .toUpperCase();
  return ADVICE_CHARGE_STATUS_TAG_BY_CODE[code] ?? 'info';
}

/** 与 ClinicalStatus 持久化 code 一致（小写）：active=阳性 / inactive=阴性 */
const SKIN_TEST_TAG_BY_CODE: Record<string, AdviceStatusTagType> = {
  active: 'danger',
  positive: 'danger',
  inactive: 'success',
  negative: 'success',
  resolved: 'info',
  unknown: 'warning',
};

/** 皮试结果 Tag：仅认 skinTestOutcomeCode（ClinicalStatus code，如 active） */
export function resolveSkinTestTagTypeFromCode(
  skinTestOutcomeCode?: null | string,
  skinTestFlag?: boolean | null,
): AdviceStatusTagType | null {
  const code = String(skinTestOutcomeCode ?? '')
    .trim()
    .toLowerCase();
  if (code && SKIN_TEST_TAG_BY_CODE[code]) {
    return SKIN_TEST_TAG_BY_CODE[code];
  }
  if (skinTestFlag === true) {
    return 'primary';
  }
  if (skinTestFlag === false) {
    return 'info';
  }
  return null;
}

/** 皮试结果后缀：跟在药名规格后，如 `头孢…【1g】 【阳性】` */
export function appendSkinTestOutcomeToMedicationTitle(
  title: string,
  outcomeText?: null | string,
): string {
  const base = String(title ?? '').trim();
  const outcome = String(outcomeText ?? '').trim();
  if (!outcome) {
    return base || '—';
  }
  if (!base) {
    return `— 【${outcome}】`;
  }
  return `${base} 【${outcome}】`;
}

/** 护士站医嘱列表皮试结果角标 CSS 类（仅认 outcomeCode） */
export function resolveSkinTestOutcomePillClass(outcomeCode?: null | string): string {
  const tagType = resolveSkinTestTagTypeFromCode(outcomeCode, undefined);
  if (tagType === 'danger') {
    return 'skin-test-outcome-pill--positive';
  }
  if (tagType === 'success') {
    return 'skin-test-outcome-pill--negative';
  }
  return 'skin-test-outcome-pill--default';
}
