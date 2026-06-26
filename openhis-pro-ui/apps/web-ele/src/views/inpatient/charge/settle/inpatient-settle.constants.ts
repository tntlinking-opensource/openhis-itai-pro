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

/** 结算模式：出院全结 / 中途结算 */
export const INPATIENT_SETTLE_MODE = {
  DISCHARGE_FULL: 'DISCHARGE_FULL',
  MID_SETTLE: 'MID_SETTLE',
} as const;

export type InpatientSettleModeCode =
  (typeof INPATIENT_SETTLE_MODE)[keyof typeof INPATIENT_SETTLE_MODE];

/** 就诊状态：待出院结算（全选待结算可走出院全结） */
export const ENCOUNTER_STATUS_PENDING = 'PENDING';
