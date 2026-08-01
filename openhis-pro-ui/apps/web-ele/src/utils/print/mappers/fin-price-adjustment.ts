import { invPrintAmountText, invPrintStr } from './inv-common';

/** 调价单明细行（与 fin-price-adjustment-print 模板 lineRows 列一致） */
export interface FinPriceAdjustmentPrintLineRow {
  seqNo: string;
  itemName: string;
  specText: string;
  categoryText: string;
  oldPurchasePriceText: string;
  oldSellingPriceText: string;
  quantityText: string;
  newPurchasePriceText: string;
  newSellingPriceText: string;
}

/** 调价单 PrintData */
export interface FinPriceAdjustmentPrintData {
  hospitalName: string;
  documentTitle: string;
  docNo: string;
  applyTimeText: string;
  adjustmentReasonText: string;
  itemTypeText: string;
  statusText: string;
  approvedByName: string;
  lineRows: FinPriceAdjustmentPrintLineRow[];
  preparedByName: string;
  totalPurchaseImpactText: string;
  totalSellingImpactText: string;
}

/** 明细行入参（页面网格展示值） */
export interface FinPriceAdjustmentPrintLineSource {
  itemName?: string;
  specText?: string;
  categoryText?: string;
  oldPurchasePrice?: null | number | string;
  oldSellingPrice?: null | number | string;
  quantity?: null | number | string;
  newPurchasePrice?: null | number | string;
  newSellingPrice?: null | number | string;
}

/**
 * 调价单 mapper 入参（页面已有字段为主，P；医院名 C）
 * SOURCE: hospitalName=C；其余均为 P（单头/明细/审批区）
 */
export interface FinPriceAdjustmentPrintSource {
  hospitalName?: string;
  documentTitle?: string;
  docNo?: string;
  applyTimeText?: string;
  adjustmentReasonText?: string;
  itemTypeText?: string;
  statusText?: string;
  approvedByName?: string;
  lineRows?: FinPriceAdjustmentPrintLineSource[];
  preparedByName?: string;
  totalPurchaseImpact?: null | number | string;
  totalSellingImpact?: null | number | string;
}

/** 空价/空量与页面一致展示为 — */
function displayOrDash(value: null | number | string | undefined): string {
  const text = invPrintAmountText(value);
  if (text) {
    return text;
  }
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const raw = invPrintStr(value);
  return raw || '—';
}

function quantityText(value: null | number | string | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const n = typeof value === 'number' ? value : Number(String(value).trim());
  if (!Number.isFinite(n)) {
    return '—';
  }
  return String(n);
}

function mapLine(
  row: FinPriceAdjustmentPrintLineSource,
  index: number,
): FinPriceAdjustmentPrintLineRow {
  return {
    seqNo: String(index + 1),
    itemName: invPrintStr(row.itemName),
    specText: invPrintStr(row.specText),
    categoryText: invPrintStr(row.categoryText),
    oldPurchasePriceText: displayOrDash(row.oldPurchasePrice),
    oldSellingPriceText: displayOrDash(row.oldSellingPrice),
    quantityText: quantityText(row.quantity),
    newPurchasePriceText: displayOrDash(row.newPurchasePrice),
    newSellingPriceText: displayOrDash(row.newSellingPrice),
  };
}

/** 财务调价单 → PrintData */
export function buildFinPriceAdjustmentPrintData(
  source: FinPriceAdjustmentPrintSource,
): FinPriceAdjustmentPrintData {
  return {
    hospitalName: invPrintStr(source.hospitalName),
    documentTitle: invPrintStr(source.documentTitle) || '调价单',
    docNo: invPrintStr(source.docNo),
    applyTimeText: invPrintStr(source.applyTimeText).replace('T', ' '),
    adjustmentReasonText: invPrintStr(source.adjustmentReasonText),
    itemTypeText: invPrintStr(source.itemTypeText),
    statusText: invPrintStr(source.statusText),
    approvedByName: invPrintStr(source.approvedByName),
    lineRows: (source.lineRows ?? []).map((row, index) => mapLine(row, index)),
    preparedByName: invPrintStr(source.preparedByName),
    totalPurchaseImpactText: invPrintAmountText(source.totalPurchaseImpact) || '0.00',
    totalSellingImpactText: invPrintAmountText(source.totalSellingImpact) || '0.00',
  };
}
