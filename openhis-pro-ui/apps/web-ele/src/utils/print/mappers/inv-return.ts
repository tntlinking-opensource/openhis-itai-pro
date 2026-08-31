import {
  buildInvDocumentTitle,
  invPrintAmountText,
  invPrintExpiryDateText,
  invPrintPurchaseUnitPriceText,
  invPrintQtyUnitText,
  invPrintStr,
} from './inv-common';

/** 退货单明细行（与对齐后的 inv-return-print 表列一致） */
export interface InvReturnPrintLineRow {
  seqNo: string;
  /** 项目编码（药品/耗材 productBusNo） */
  productBusNo: string;
  productName: string;
  specText: string;
  manufacturerText: string;
  qtyUnitText: string;
  unitPriceText: string;
  amountText: string;
  lotNumber: string;
  /** 有效期（YYYY-MM-DD），不含生产日 */
  expiryText: string;
  reasonText: string;
}

/** 退货单 PrintData */
export interface InvReturnPrintData {
  hospitalName: string;
  documentTitle: string;
  docNo: string;
  applyTimeText: string;
  itemTypeText: string;
  sourceLocationText: string;
  supplierText: string;
  linkDocText: string;
  destLocationText: string;
  invoiceNoText: string;
  lineRows: InvReturnPrintLineRow[];
  totalAmountText: string;
  /** 制单人（单据创建人，非当前打印人） */
  preparedByName: string;
  approvedByName: string;
}

/** 明细行入参（展示字段，由页面组装） */
export interface InvReturnPrintLineSource {
  productBusNo?: string;
  productName?: string;
  specText?: string;
  manufacturerText?: string;
  qty?: null | number | string;
  unitLabel?: string;
  /** 已拼好的数量单位，优先于 qty+unitLabel */
  qtyUnitText?: string;
  unitPrice?: null | number | string;
  amount?: null | number | string;
  lotNumber?: string;
  /** @deprecated 打印不再展示生产日；仅兼容旧入参 */
  productionDate?: string;
  expiryDate?: string;
  /** 已拼好的有效期；若含 `生产~效期` 则 mapper 只取效期段 */
  expiryText?: string;
  reasonText?: string;
}

/** 退货单 mapper 入参 */
export interface InvReturnPrintSource {
  hospitalName?: string;
  /** 完整标题；缺省时用 locationName+docKindLabel 拼 */
  documentTitle?: string;
  locationName?: string;
  docKindLabel?: string;
  docNo?: string;
  applyTimeText?: string;
  itemTypeText?: string;
  sourceLocationText?: string;
  supplierText?: string;
  linkDocText?: string;
  destLocationText?: string;
  invoiceNoText?: string;
  lineRows?: InvReturnPrintLineSource[];
  totalAmountText?: string;
  preparedByName?: string;
  approvedByName?: string;
}

function mapLine(row: InvReturnPrintLineSource, index: number): InvReturnPrintLineRow {
  const qtyUnitText = invPrintStr(row.qtyUnitText) || invPrintQtyUnitText(row.qty, row.unitLabel);
  const expiryText = invPrintExpiryDateText(row.expiryDate, row.expiryText);
  return {
    seqNo: String(index + 1),
    productBusNo: invPrintStr(row.productBusNo),
    productName: invPrintStr(row.productName),
    specText: invPrintStr(row.specText),
    manufacturerText: invPrintStr(row.manufacturerText),
    qtyUnitText,
    unitPriceText: invPrintPurchaseUnitPriceText(row.unitPrice),
    amountText: invPrintAmountText(row.amount),
    lotNumber: invPrintStr(row.lotNumber),
    expiryText,
    reasonText: invPrintStr(row.reasonText),
  };
}

/** 库存退货单 → PrintData */
export function buildInvReturnPrintData(source: InvReturnPrintSource): InvReturnPrintData {
  const lineRows = (source.lineRows ?? []).map((row, index) => mapLine(row, index));
  let totalAmountText = invPrintStr(source.totalAmountText);
  if (!totalAmountText && lineRows.length > 0) {
    let sum = 0;
    let has = false;
    // 无合计时按明细金额汇总
    for (const row of lineRows) {
      const n = Number.parseFloat(row.amountText);
      if (Number.isFinite(n)) {
        sum += n;
        has = true;
      }
    }
    if (has) {
      totalAmountText = sum.toFixed(2);
    }
  }
  return {
    hospitalName: invPrintStr(source.hospitalName),
    documentTitle:
      invPrintStr(source.documentTitle) ||
      buildInvDocumentTitle(source.locationName ?? '', source.docKindLabel ?? '退货单'),
    docNo: invPrintStr(source.docNo),
    applyTimeText: invPrintStr(source.applyTimeText),
    itemTypeText: invPrintStr(source.itemTypeText),
    sourceLocationText: invPrintStr(source.sourceLocationText),
    supplierText: invPrintStr(source.supplierText),
    linkDocText: invPrintStr(source.linkDocText),
    destLocationText: invPrintStr(source.destLocationText),
    invoiceNoText: invPrintStr(source.invoiceNoText),
    lineRows,
    totalAmountText,
    preparedByName: invPrintStr(source.preparedByName),
    approvedByName: invPrintStr(source.approvedByName),
  };
}
