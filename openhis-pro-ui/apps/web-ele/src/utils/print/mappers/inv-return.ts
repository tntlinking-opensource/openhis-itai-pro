import { buildInvDocumentTitle, invPrintQtyUnitText, invPrintStr } from './inv-common';

/** 退货单明细行 */
export interface InvReturnPrintLineRow {
  seqNo: string;
  productName: string;
  qtyUnitText: string;
  lotNumber: string;
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
  practitionerName: string;
  invoiceNoText: string;
  lineRows: InvReturnPrintLineRow[];
  totalAmountText: string;
  preparedByName: string;
  approvedByName: string;
}

export interface InvReturnPrintLineSource {
  productName?: string;
  qty?: null | number | string;
  unitLabel?: string;
  qtyUnitText?: string;
  lotNumber?: string;
  reasonText?: string;
}

export interface InvReturnPrintSource {
  hospitalName?: string;
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
  practitionerName?: string;
  invoiceNoText?: string;
  lineRows?: InvReturnPrintLineSource[];
  totalAmountText?: string;
  preparedByName?: string;
  approvedByName?: string;
}

function mapLine(row: InvReturnPrintLineSource, index: number): InvReturnPrintLineRow {
  return {
    seqNo: String(index + 1),
    productName: invPrintStr(row.productName),
    qtyUnitText: invPrintStr(row.qtyUnitText) || invPrintQtyUnitText(row.qty, row.unitLabel),
    lotNumber: invPrintStr(row.lotNumber),
    reasonText: invPrintStr(row.reasonText),
  };
}

/** 库存退货单 → PrintData */
export function buildInvReturnPrintData(source: InvReturnPrintSource): InvReturnPrintData {
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
    practitionerName: invPrintStr(source.practitionerName),
    invoiceNoText: invPrintStr(source.invoiceNoText),
    lineRows: (source.lineRows ?? []).map((row, index) => mapLine(row, index)),
    totalAmountText: invPrintStr(source.totalAmountText),
    preparedByName: invPrintStr(source.preparedByName),
    approvedByName: invPrintStr(source.approvedByName),
  };
}
