import type { TagProps } from 'element-plus';

import {
  FALLBACK_DEFAULT_PAY_CHANNEL,
  FALLBACK_PAY_CHANNEL_OPTIONS,
} from '#/utils/payment/payment-channel-options';

/** 收费项状态（与域枚举 `ChargeItemStatus` code 一致）→ 文案 + 标签类型 */
export const CHARGE_ITEM_STATUS_LABEL_TAG: Record<
  string,
  { label: string; tagType: NonNullable<TagProps['type']> }
> = {
  DRAFT: { label: '草稿', tagType: 'info' },
  PLANNED: { label: '待收费', tagType: 'warning' },
  SETTLED: { label: '已结算', tagType: 'success' },
  REFUNDING: { label: '退款中', tagType: 'warning' },
  REFUNDED: { label: '已退款', tagType: 'info' },
};

/** 正式结算支付结果枚举编码（与支付流水域 `PaymentOutcome` 表 4.4 一致：`COMPLETED`=1） */
export const OUTPATIENT_CHARGE_SETTLE_OUTCOME_COMPLETED = '1';

/** 正式结算失败：医嘱已变更须重新预结算（GAP-STALE-001 / EX-OPC-411） */
export const OUTPATIENT_CHARGE_SETTLE_ADVICE_CHANGED_CODE = 'EX-OPC-411';

/** 默认支付渠道 */
export const DEFAULT_OUTPATIENT_CHARGE_PAY_CHANNEL_CODE = FALLBACK_DEFAULT_PAY_CHANNEL;

/** 结算弹窗可选渠道（API 不可用时的 fallback，不含 SCAN_PAY） */
export const OUTPATIENT_CHARGE_PAY_CHANNEL_OPTIONS = [...FALLBACK_PAY_CHANNEL_OPTIONS];

/** 确认收费弹窗「费用明细」固定字段（常显；金额由 `insuranceBreakdownItems` 按 key 覆盖，缺省为 0） */
export const OUTPATIENT_CHARGE_FEE_DETAIL_FIELD_DEFS: readonly { key: string; label: string }[] = [
  { key: 'fundPay', label: '基金支出' },
  { key: 'poolingPay', label: '统筹基金支付' },
  { key: 'accountPay', label: '个人账户支付' },
  { key: 'largeDiseasePay', label: '大病保险支付' },
  { key: 'deductibleLine', label: '起付线' },
  { key: 'selfPayInScope', label: '范围内个人自付' },
  { key: 'selfPayOutScope', label: '范围外个人自费' },
  { key: 'policyDiscount', label: '政策性减免' },
  { key: 'otherPay', label: '其他支付' },
  { key: 'cashPay', label: '现金补缴' },
  { key: 'finalReceivable', label: '最终应收' },
];
