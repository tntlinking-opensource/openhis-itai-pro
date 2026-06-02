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

export const FALLBACK_DEFAULT_PAY_CHANNEL = 'CASH';
