/** 结算队列页签（与后端 InpatientSettleQueueTab 一致） */
export const SETTLE_QUEUE_TAB = {
  PENDING_SETTLE: 'PENDING_SETTLE',
  SETTLED: 'SETTLED',
} as const;

/** 费用呈现模式 */
export const SETTLE_PRESENTATION_MODE = {
  DETAIL: 'DETAIL',
  SUMMARY: 'SUMMARY',
} as const;

export {
  FALLBACK_DEFAULT_PAY_CHANNEL,
  FALLBACK_PAY_CHANNEL_OPTIONS,
} from '#/utils/payment/payment-channel-options';

/** 结算模式：收费处正式结算仅出院全结；医护站完整预结算可走中途试算 */
export const INPATIENT_SETTLE_MODE = {
  DISCHARGE_FULL: 'DISCHARGE_FULL',
  MID_SETTLE: 'MID_SETTLE',
} as const;

export type InpatientSettleModeCode =
  (typeof INPATIENT_SETTLE_MODE)[keyof typeof INPATIENT_SETTLE_MODE];

/** 就诊状态：待出院结算（全选待结算可走出院全结） */
export const ENCOUNTER_STATUS_PENDING = 'PENDING';

/** 就诊状态：待出院（校对出院医嘱后，禁止结算） */
export const ENCOUNTER_STATUS_ON_HOLD = 'ON_HOLD';

/** 就诊状态：已结算待清床 */
export const ENCOUNTER_STATUS_COMPLETED = 'COMPLETED';

/** 就诊状态：已清床离院（已结算页签队列仍可见，仅查阅） */
export const ENCOUNTER_STATUS_DISCHARGED = 'DISCHARGED';
