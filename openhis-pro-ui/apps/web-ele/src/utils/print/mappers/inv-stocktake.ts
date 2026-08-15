import {
  buildInvDocumentTitle,
  invPrintAmountText,
  invPrintQtyUnitText,
  invPrintStr,
} from './inv-common';

/** 盘点盈亏明细行 */
export interface InvStocktakePrintLineRow {
  seqNo: string;
  productName: string;
  specText: string;
  manufacturerText: string;
  lotNumber: string;
  bookQtyText: string;
  actualQtyText: string;
  diffQtyText: string;
  unitPriceText: string;
  actualAmountText: string;
  diffAmountText: string;
  reasonText: string;
}

/** 盘点盈亏 PrintData */
export interface InvStocktakePrintData {
  hospitalName: string;
  documentTitle: string;
  docNo: string;
  applyTimeText: string;
  itemTypeText: string;
  sourceLocationText: string;
  destLocationText: string;
  lineRows: InvStocktakePrintLineRow[];
  totalAmountText: string;
  /** 制单人（单据创建人，非当前打印人） */
  preparedByName: string;
  approvedByName: string;
}

export interface InvStocktakePrintLineSource {
  productName?: string;
  specText?: string;
  manufacturerText?: string;
  lotNumber?: string;
  bookQtyText?: string;
  actualQtyText?: string;
  diffQtyText?: string;
  bookQty?: null | number | string;
  actualQty?: null | number | string;
  diffQty?: null | number | string;
  unitLabel?: string;
  unitPrice?: null | number | string;
  actualAmount?: null | number | string;
  diffAmount?: null | number | string;
  reasonText?: string;
}

export interface InvStocktakePrintSource {
  hospitalName?: string;
  documentTitle?: string;
  locationName?: string;
  docKindLabel?: string;
  docNo?: string;
  applyTimeText?: string;
  itemTypeText?: string;
  sourceLocationText?: string;
  destLocationText?: string;
  lineRows?: InvStocktakePrintLineSource[];
  totalAmountText?: string;
  preparedByName?: string;
  approvedByName?: string;
}

function mapLine(row: InvStocktakePrintLineSource, index: number): InvStocktakePrintLineRow {
  return {
    seqNo: String(index + 1),
    productName: invPrintStr(row.productName),
    specText: invPrintStr(row.specText),
    manufacturerText: invPrintStr(row.manufacturerText),
    lotNumber: invPrintStr(row.lotNumber),
    bookQtyText: invPrintStr(row.bookQtyText) || invPrintQtyUnitText(row.bookQty, row.unitLabel),
    actualQtyText:
      invPrintStr(row.actualQtyText) || invPrintQtyUnitText(row.actualQty, row.unitLabel),
    diffQtyText: invPrintStr(row.diffQtyText) || invPrintQtyUnitText(row.diffQty, row.unitLabel),
    unitPriceText: invPrintAmountText(row.unitPrice),
    actualAmountText: invPrintAmountText(row.actualAmount),
    diffAmountText: invPrintAmountText(row.diffAmount),
    reasonText: invPrintStr(row.reasonText),
  };
}

/** 库存盘点盈亏单 → PrintData */
export function buildInvStocktakePrintData(source: InvStocktakePrintSource): InvStocktakePrintData {
  const lineRows = (source.lineRows ?? []).map((row, index) => mapLine(row, index));
  // 页脚「盈亏合计」：优先用入参；否则按明细盈亏额求和
  let totalAmountText = invPrintStr(source.totalAmountText);
  if (!totalAmountText && lineRows.length > 0) {
    let sum = 0;
    let has = false;
    for (const row of lineRows) {
      const n = Number.parseFloat(row.diffAmountText);
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
      buildInvDocumentTitle(source.locationName ?? '', source.docKindLabel ?? '盘点单'),
    docNo: invPrintStr(source.docNo),
    applyTimeText: invPrintStr(source.applyTimeText),
    itemTypeText: invPrintStr(source.itemTypeText),
    sourceLocationText: invPrintStr(source.sourceLocationText),
    destLocationText: invPrintStr(source.destLocationText),
    lineRows,
    totalAmountText,
    preparedByName: invPrintStr(source.preparedByName),
    approvedByName: invPrintStr(source.approvedByName),
  };
}
