import type {
  FeeDetailAmountSummaryDto,
  FeeDetailReportSummaryFooterDto,
} from '#/api/report/fee-detail';

/** 金额列 prop，模板内统一套负数样式（进/售价/优惠/实收）。 */
export const FEE_DETAIL_AMOUNT_PROPS = [
  'purchaseUnitPrice',
  'purchaseAmount',
  'saleUnitPrice',
  'saleAmount',
  'discountAmount',
  'actualReceivedAmount',
] as const;

/** 明细 Tab 全局合计金额字段展示配置（售价/优惠/实收靠前，进价殿后）。 */
export const FEE_DETAIL_SUMMARY_DISPLAY = [
  { key: 'saleAmountTotal', label: '售价合计' },
  { key: 'discountAmountTotal', label: '优惠合计' },
  { key: 'actualReceivedAmountTotal', label: '实收合计' },
  { key: 'purchaseAmountTotal', label: '进价合计' },
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
