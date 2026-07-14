import type { TagProps } from 'element-plus';

type AdviceStatusTagType = NonNullable<TagProps['type']>;

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

/** 医嘱展示态 Tag：仅认 statusEnum code，不认展示文案 */
export function resolveAdviceStatusTagTypeFromCode(
  statusEnum?: null | string,
): AdviceStatusTagType {
  const code = String(statusEnum ?? '')
    .trim()
    .toUpperCase();
  return ADVICE_STATUS_TAG_BY_CODE[code] ?? 'info';
}

const SKIN_TEST_TAG_BY_CODE: Record<string, AdviceStatusTagType> = {
  ACTIVE: 'danger',
  INACTIVE: 'success',
  RESOLVED: 'info',
  UNKNOWN: 'warning',
};

/** 皮试结果 Tag：仅认 skinTestOutcomeCode */
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
