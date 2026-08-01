import type {
  FeeDetailAmountSummaryDto,
  FeeDetailReportSummaryFooterDto,
} from '#/api/report/fee-detail';

/** 金额列 prop，模板内统一套负数样式。 */
export const FEE_DETAIL_AMOUNT_PROPS = [
  'purchaseUnitPrice',
  'purchaseAmount',
  'saleUnitPrice',
  'saleAmount',
  'actualReceivedAmount',
] as const;

/** 明细 Tab 全局合计金额字段展示配置。 */
export const FEE_DETAIL_SUMMARY_DISPLAY = [
  { key: 'purchaseAmountTotal', label: '进价合计' },
  { key: 'saleAmountTotal', label: '售价合计' },
  { key: 'actualReceivedAmountTotal', label: '实收合计' },
] as const satisfies ReadonlyArray<{
  key: keyof FeeDetailAmountSummaryDto;
  label: string;
}>;

/** 汇总 Tab 页脚字段展示配置。 */
export const SUMMARY_FOOTER_DISPLAY = [
  { key: 'purchaseAmountTotal', label: '进价合计', kind: 'money' },
  { key: 'saleAmountTotal', label: '售价合计', kind: 'money' },
  { key: 'actualReceivedAmountTotal', label: '实收合计', kind: 'money' },
  { key: 'lineCountTotal', label: '行数合计', kind: 'count' },
] as const satisfies ReadonlyArray<{
  key: keyof FeeDetailReportSummaryFooterDto;
  label: string;
  kind: 'count' | 'money';
}>;
