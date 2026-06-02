import type { TagProps } from 'element-plus';

/** 退费明细状态 → 文案 + 标签类型（与域枚举 ChargeItemStatus code 一致） */
export const REFUND_LINE_STATUS_LABEL_TAG: Record<
  string,
  { label: string; tagType: NonNullable<TagProps['type']> }
> = {
  PLANNED: { label: '待收费', tagType: 'warning' },
  REFUNDING: { label: '退款中', tagType: 'warning' },
  REFUNDED: { label: '已退款', tagType: 'info' },
};

/** 执行前门禁拒绝码 → 用户提示 */
export const REFUND_REJECT_REASON_MESSAGES: Record<string, string> = {
  'EX-REF-201': '请先完成退药后再退费',
  'EX-REF-202': '请先完成退耗材后再退费',
};
