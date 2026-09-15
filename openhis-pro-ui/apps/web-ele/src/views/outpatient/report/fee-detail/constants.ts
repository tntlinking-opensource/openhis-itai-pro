/**
 * 历史门诊费用明细页常量（已废弃）。
 *
 * 路由壳 `index.vue` 仅挂载 `#/views/report/fee-detail/fee-detail-report-page.vue`；
 * 真展示配置以 `views/report/fee-detail/constants.ts` 为准（仅进/售价）。
 * 本文件勿再扩展；待删壳页时一并移除。
 */
import type { OutpatientFeeDetailAmountSummaryDto } from '#/api/outpatient/report/fee-detail';

/** 金额列 prop，模板内统一套负数样式。 */
export const FEE_DETAIL_AMOUNT_PROPS = [
  'baseAmount',
  'discountAmount',
  'receivableAmount',
  'discountedAmount',
  'unitPrice',
  'actualReceivedAmount',
  'insuranceAmount',
  'selfPayAmount',
] as const;

/** 全局/就诊小计金额字段展示配置。 */
export const FEE_DETAIL_SUMMARY_DISPLAY = [
  { key: 'receivableAmountTotal', label: '应收合计' },
  { key: 'discountedAmountTotal', label: '折后合计' },
  { key: 'actualReceivedAmountTotal', label: '实收合计' },
] as const satisfies ReadonlyArray<{
  key: keyof OutpatientFeeDetailAmountSummaryDto;
  label: string;
}>;
