import {
  buildInpatientFeeListPrintData,
  type InpatientFeeListFeeRow,
  type InpatientFeeListPrintData,
  type InpatientFeeListPrintSource,
} from './inpatient-fee-list';

/** 住院费用明细单明细行（与 ip-feeDetail-print 模板 feeRows 列一致；仅 category/item） */
export type InpatientFeeDetailFeeRow = InpatientFeeListFeeRow;

/** 住院费用明细单 PrintData（字段与费用清单模板同源，标题默认「住院费用明细单」） */
export type InpatientFeeDetailPrintData = InpatientFeeListPrintData;

/** 明细单 mapper 入参 */
export type InpatientFeeDetailPrintSource = InpatientFeeListPrintSource;

/** 费用流水 → PrintData（无分类合计；默认标题为明细单） */
export function buildInpatientFeeDetailPrintData(
  source: InpatientFeeDetailPrintSource,
): InpatientFeeDetailPrintData {
  return buildInpatientFeeListPrintData({
    ...source,
    documentTitle: source.documentTitle?.trim() || '住院费用明细单',
    feeRows: (source.feeRows ?? []).filter((row) => row.rowType !== 'subtotal'),
  });
}
