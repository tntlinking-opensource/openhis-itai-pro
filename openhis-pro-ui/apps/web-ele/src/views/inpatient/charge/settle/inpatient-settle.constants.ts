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

/** 初始化接口不可用时的支付渠道兜底 */
export const FALLBACK_PAY_CHANNEL_OPTIONS = [
  { label: '现金', value: 'CASH' },
  { label: '微信', value: 'WECHAT' },
  { label: '银行卡', value: 'BANK_CARD' },
] as const;

/** 初始化接口不可用时的默认支付渠道 */
export const FALLBACK_DEFAULT_PAY_CHANNEL = 'CASH';

/** 结算模式：出院全结 / 中途结算 */
export const INPATIENT_SETTLE_MODE = {
  DISCHARGE_FULL: 'DISCHARGE_FULL',
  MID_SETTLE: 'MID_SETTLE',
} as const;

export type InpatientSettleModeCode =
  (typeof INPATIENT_SETTLE_MODE)[keyof typeof INPATIENT_SETTLE_MODE];

/** 结算提交模式（与后端 InsuranceCoordinationConstants 一致） */
export const INPATIENT_SETTLEMENT_MODE = {
  INSURANCE_ASYNC: 'INSURANCE_ASYNC',
  SYNC: 'SYNC',
} as const;

/** 医保协同单轮询间隔（毫秒，对齐门诊 §2.4） */
export const IP_INSURANCE_SETTLE_POLL_INTERVAL_MS = 2000;

/** 医保协同单轮询最大次数（约 3 分钟） */
export const IP_INSURANCE_SETTLE_POLL_MAX_ATTEMPTS = 90;

/** 就诊状态：待出院结算（全选待结算可走出院全结） */
export const ENCOUNTER_STATUS_PENDING = 'PENDING';
