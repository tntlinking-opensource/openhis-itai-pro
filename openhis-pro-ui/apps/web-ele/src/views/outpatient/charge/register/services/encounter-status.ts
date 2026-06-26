import type { ElTagVisualType } from '#/utils/format/status-tag';

/**
 * 门诊就诊/挂号流水状态编码（与域枚举一致，接口多为字符串）。
 */
export const ENCOUNTER_STATUS_CODE = {
  CANCELLED: 'CANCELLED',
  COMPLETED: 'COMPLETED',
  /** 完诊（医生已结束接诊） */
  DISCHARGED: 'DISCHARGED',
  IN_PROGRESS: 'IN_PROGRESS',
  ON_HOLD: 'ON_HOLD',
  /** 待挂号（预结算后、正式收费前） */
  PLANNED: 'PLANNED',
  REGISTERED: 'REGISTERED',
  TRIAGED: 'TRIAGED',
} as const;

/** 窗口「退号」可展示的状态（与 Encounter.assertCancelable 门诊口径一致） */
export const ENCOUNTER_CANCELABLE_STATUS_CODES: ReadonlySet<string> = new Set([
  ENCOUNTER_STATUS_CODE.REGISTERED,
  ENCOUNTER_STATUS_CODE.TRIAGED,
  ENCOUNTER_STATUS_CODE.IN_PROGRESS,
  ENCOUNTER_STATUS_CODE.DISCHARGED,
]);

/** 状态列 `ElTag` 配色：编码或枚举名 → type（列表接口可能用 `statusEnum` 文本） */
export const ENCOUNTER_STATUS_EL_TAG_MAP: Readonly<Record<string, ElTagVisualType>> = {
  PLANNED: 'warning',
  [ENCOUNTER_STATUS_CODE.REGISTERED]: 'success',
  [ENCOUNTER_STATUS_CODE.TRIAGED]: 'info',
  [ENCOUNTER_STATUS_CODE.IN_PROGRESS]: 'warning',
  [ENCOUNTER_STATUS_CODE.DISCHARGED]: 'success',
  [ENCOUNTER_STATUS_CODE.ON_HOLD]: 'warning',
  [ENCOUNTER_STATUS_CODE.COMPLETED]: 'success',
  [ENCOUNTER_STATUS_CODE.CANCELLED]: 'info',
};
