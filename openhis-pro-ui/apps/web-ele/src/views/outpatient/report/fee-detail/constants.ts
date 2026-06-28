import type { TableColumn } from '@vben-core/table-layout-ui';

import type { OutpatientFeeDetailAmountSummaryDto } from '#/api/outpatient/report/fee-detail';

/**
 * 列表列顺序：序号 → 就诊头（门诊号优先，便于按号分组阅读）→ 明细基础列 → 来源/医保 → 金额列。
 * 金额列顺序对齐后端导出 Excel（AS-OP-FIN-005 IF-OP-FDT-03）。
 */
export const FEE_DETAIL_TABLE_COLUMNS: TableColumn[] = [
  { type: 'index', label: '序号', width: 56, align: 'center', headerAlign: 'center' },
  { prop: 'busNo', label: '门诊号', width: 120, showOverflowTooltip: true },
  { prop: 'patientName', label: '患者', width: 90, showOverflowTooltip: true },
  {
    prop: 'registrationDepartmentName',
    label: '挂号科室',
    width: 100,
    showOverflowTooltip: true,
  },
  { prop: 'encounterDate', label: '就诊日期', width: 110, showOverflowTooltip: true },
  { prop: 'feeCategoryDisplay', label: '大类', width: 72, showOverflowTooltip: true },
  { prop: 'subCategoryDisplay', label: '细分类', width: 88, showOverflowTooltip: true },
  { prop: 'productName', label: '项目名称', minWidth: 120, showOverflowTooltip: true },
  { prop: 'productCode', label: '院内编码', width: 100, showOverflowTooltip: true },
  { prop: 'specification', label: '规格', width: 88, showOverflowTooltip: true },
  { prop: 'quantityValue', label: '数量', width: 72, align: 'right', slot: 'quantityValue' },
  { prop: 'quantityUnitDisplay', label: '单位', width: 56, align: 'center' },
  { prop: 'orderingDoctorName', label: '开单医生', width: 88, showOverflowTooltip: true },
  { prop: 'cashierName', label: '收费人', width: 88, showOverflowTooltip: true },
  { prop: 'billDate', label: '结算时间', width: 160, showOverflowTooltip: true },
  {
    prop: 'tradeDirection',
    label: '来源',
    width: 72,
    align: 'center',
    slot: 'tradeDirection',
  },
  { prop: 'insuranceLevelDisplay', label: '医保等级', width: 88, showOverflowTooltip: true },
  { prop: 'insuranceTypeDisplay', label: '医保类型', width: 88, showOverflowTooltip: true },
  { prop: 'baseAmount', label: '原价', width: 88, align: 'right', slot: 'baseAmount' },
  { prop: 'discountAmount', label: '优惠金额', width: 88, align: 'right', slot: 'discountAmount' },
  {
    prop: 'receivableAmount',
    label: '应收金额',
    width: 88,
    align: 'right',
    slot: 'receivableAmount',
  },
  {
    prop: 'discountedAmount',
    label: '折后金额',
    width: 88,
    align: 'right',
    slot: 'discountedAmount',
  },
  { prop: 'unitPrice', label: '单价', width: 88, align: 'right', slot: 'unitPrice' },
  { prop: 'lineAmount', label: '行金额', width: 88, align: 'right', slot: 'lineAmount' },
  {
    prop: 'actualReceivedAmount',
    label: '实收金额',
    width: 88,
    align: 'right',
    slot: 'actualReceivedAmount',
  },
  {
    prop: 'insuranceAmount',
    label: '医保金额',
    width: 88,
    align: 'right',
    slot: 'insuranceAmount',
  },
  { prop: 'selfPayAmount', label: '自费金额', width: 88, align: 'right', slot: 'selfPayAmount' },
];

/** 全局/就诊小计金额字段展示配置。 */
export const FEE_DETAIL_SUMMARY_DISPLAY = [
  { key: 'baseAmountTotal', label: '原价合计' },
  { key: 'discountAmountTotal', label: '优惠合计' },
  { key: 'receivableAmountTotal', label: '应收合计' },
  { key: 'discountedAmountTotal', label: '折后合计' },
  { key: 'lineAmountTotal', label: '行金额合计' },
  { key: 'actualReceivedAmountTotal', label: '实收合计' },
  { key: 'insuranceAmountTotal', label: '医保合计' },
  { key: 'selfPayAmountTotal', label: '自费合计' },
] as const satisfies ReadonlyArray<{
  key: keyof OutpatientFeeDetailAmountSummaryDto;
  label: string;
}>;
