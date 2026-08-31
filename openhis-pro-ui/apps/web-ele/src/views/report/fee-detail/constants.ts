import type {
  FeeDetailAmountSummaryDto,
  FeeDetailReportSummaryFooterDto,
} from '#/api/report/fee-detail';

/** 金额列 prop，模板内统一套负数样式（仅进/售价）。 */
export const FEE_DETAIL_AMOUNT_PROPS = [
  'purchaseUnitPrice',
  'purchaseAmount',
  'saleUnitPrice',
  'saleAmount',
] as const;

/** 明细 Tab 全局合计金额字段展示配置（本轮不展示实收）。 */
export const FEE_DETAIL_SUMMARY_DISPLAY = [
  { key: 'purchaseAmountTotal', label: '进价合计' },
  { key: 'saleAmountTotal', label: '售价合计' },
] as const satisfies ReadonlyArray<{
  key: keyof FeeDetailAmountSummaryDto;
  label: string;
}>;

/** 汇总 Tab 页脚字段展示配置（本轮不展示实收/行数）。 */
export const SUMMARY_FOOTER_DISPLAY = [
  { key: 'quantityTotal', label: '数量合计', kind: 'qty' },
  { key: 'purchaseAmountTotal', label: '进价合计', kind: 'money' },
  { key: 'saleAmountTotal', label: '售价合计', kind: 'money' },
] as const satisfies ReadonlyArray<{
  key: keyof FeeDetailReportSummaryFooterDto;
  label: string;
  kind: 'count' | 'money' | 'qty';
}>;
