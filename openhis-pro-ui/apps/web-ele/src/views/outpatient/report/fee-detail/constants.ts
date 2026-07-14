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
  { key: 'baseAmountTotal', label: '原价合计' },
  { key: 'discountAmountTotal', label: '优惠合计' },
  { key: 'receivableAmountTotal', label: '应收合计' },
  { key: 'discountedAmountTotal', label: '折后合计' },
  { key: 'actualReceivedAmountTotal', label: '实收合计' },
  { key: 'insuranceAmountTotal', label: '医保合计' },
  { key: 'selfPayAmountTotal', label: '自费合计' },
] as const satisfies ReadonlyArray<{
  key: keyof OutpatientFeeDetailAmountSummaryDto;
  label: string;
}>;
