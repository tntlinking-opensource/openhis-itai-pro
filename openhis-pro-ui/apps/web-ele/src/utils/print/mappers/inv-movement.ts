import {
  buildInvDocumentTitle,
  invPrintAmountText,
  invPrintExpiryRangeText,
  invPrintQtyUnitText,
  invPrintStr,
} from './inv-common';

/** 计价流转单明细行（与 inv-movement-print 模板 lineRows 列一致） */
export interface InvMovementPrintLineRow {
  seqNo: string;
  productName: string;
  specText: string;
  manufacturerText: string;
  qtyUnitText: string;
  unitPriceText: string;
  amountText: string;
  lotNumber: string;
  /** 生产日~效期，模板内两行展示 */
  expiryText: string;
}

/** 计价流转单 PrintData */
export interface InvMovementPrintData {
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
  practitionerName: string;
  lineRows: InvMovementPrintLineRow[];
  totalAmountText: string;
  preparedByName: string;
  approvedByName: string;
}

/** 明细行入参（展示字段，由页面组装） */
export interface InvMovementPrintLineSource {
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
  productionDate?: string;
  expiryDate?: string;
  /** 已拼好的生产~效期 */
  expiryText?: string;
}

/** 计价流转单 mapper 入参 */
export interface InvMovementPrintSource {
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
  practitionerName?: string;
  lineRows?: InvMovementPrintLineSource[];
  totalAmountText?: string;
  preparedByName?: string;
  approvedByName?: string;
}

function mapLine(row: InvMovementPrintLineSource, index: number): InvMovementPrintLineRow {
  const qtyUnitText = invPrintStr(row.qtyUnitText) || invPrintQtyUnitText(row.qty, row.unitLabel);
  const expiryText =
    invPrintStr(row.expiryText) || invPrintExpiryRangeText(row.productionDate, row.expiryDate);
  return {
    seqNo: String(index + 1),
    productName: invPrintStr(row.productName),
    specText: invPrintStr(row.specText),
    manufacturerText: invPrintStr(row.manufacturerText),
    qtyUnitText,
    unitPriceText: invPrintAmountText(row.unitPrice),
    amountText: invPrintAmountText(row.amount),
    lotNumber: invPrintStr(row.lotNumber),
    expiryText,
  };
}

/** 库存计价流转单 → PrintData */
export function buildInvMovementPrintData(source: InvMovementPrintSource): InvMovementPrintData {
  const lineRows = (source.lineRows ?? []).map((row, index) => mapLine(row, index));
  let totalAmountText = invPrintStr(source.totalAmountText);
  if (!totalAmountText && lineRows.length > 0) {
    let sum = 0;
    let has = false;
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
      buildInvDocumentTitle(source.locationName ?? '', source.docKindLabel ?? '流转单'),
    docNo: invPrintStr(source.docNo),
    applyTimeText: invPrintStr(source.applyTimeText),
    itemTypeText: invPrintStr(source.itemTypeText),
    sourceLocationText: invPrintStr(source.sourceLocationText),
    supplierText: invPrintStr(source.supplierText),
    linkDocText: invPrintStr(source.linkDocText),
    destLocationText: invPrintStr(source.destLocationText),
    invoiceNoText: invPrintStr(source.invoiceNoText),
    practitionerName: invPrintStr(source.practitionerName),
    lineRows,
    totalAmountText,
    preparedByName: invPrintStr(source.preparedByName),
    approvedByName: invPrintStr(source.approvedByName),
  };
}
