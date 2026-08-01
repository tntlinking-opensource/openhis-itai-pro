import type {
  PharmacyDailySettlementCategoryRowDto,
  PharmacyDailySettlementProductRowDto,
} from '#/api/outpatient/pharmacy-daily-settlement';

import { invPrintAmountText, invPrintStr } from './inv-common';

/** 大类汇总行（模板 HTML 一行卡片读 categoryRows） */
export interface PharmacyDailySettlementCategoryPrintRow {
  categoryLabel: string;
  purchaseAmountText: string;
  saleAmountText: string;
}

/** 品规汇总行（与模板 productRows 列一致） */
export interface PharmacyDailySettlementProductPrintRow {
  busNo: string;
  medicationName: string;
  specification: string;
  manufacturer: string;
  netQuantityText: string;
  purchaseUnitPriceText: string;
  saleUnitPriceText: string;
  purchaseAmountText: string;
  saleAmountText: string;
}

/**
 * 药房日结单 PrintData（门诊/住院共用同一模板）
 * SOURCE: hospitalName=C；其余均为 P（预览页筛选条件 + 汇总结果）
 */
export interface PharmacyDailySettlementPrintData {
  hospitalName: string;
  documentTitle: string;
  pharmacyName: string;
  settlementPeriodText: string;
  dispenserNamesText: string;
  pendingCountText: string;
  totalIncomeText: string;
  categoryRows: PharmacyDailySettlementCategoryPrintRow[];
  productRows: PharmacyDailySettlementProductPrintRow[];
}

/** 药房日结 mapper 入参（P：预览页；C：机构名） */
export interface PharmacyDailySettlementPrintSource {
  hospitalName?: string;
  documentTitle?: string;
  pharmacyName?: string;
  settlementPeriodText?: string;
  dispenserNamesText?: string;
  pendingCount?: null | number | string;
  totalIncome?: null | number | string;
  categoryRows?: PharmacyDailySettlementCategoryRowDto[];
  productRows?: PharmacyDailySettlementProductRowDto[];
}

/** 金额空值展示为 0.00（与页面汇总口径一致） */
function moneyText(value: null | number | string | undefined): string {
  return invPrintAmountText(value) || '0.00';
}

/** 大类行 → 打印行 */
function mapCategoryRow(
  row: PharmacyDailySettlementCategoryRowDto,
): PharmacyDailySettlementCategoryPrintRow {
  return {
    categoryLabel: invPrintStr(row.categoryLabel) || '—',
    purchaseAmountText: moneyText(row.purchaseAmount),
    saleAmountText: moneyText(row.saleAmount),
  };
}

/** 品规行 → 打印行 */
function mapProductRow(
  row: PharmacyDailySettlementProductRowDto,
): PharmacyDailySettlementProductPrintRow {
  return {
    busNo: invPrintStr(row.busNo),
    medicationName: invPrintStr(row.medicationName),
    specification: invPrintStr(row.specification),
    manufacturer: invPrintStr(row.manufacturer),
    netQuantityText: moneyText(row.netQuantity),
    purchaseUnitPriceText: moneyText(row.purchaseUnitPrice),
    saleUnitPriceText: moneyText(row.saleUnitPrice),
    purchaseAmountText: moneyText(row.purchaseAmount),
    saleAmountText: moneyText(row.saleAmount),
  };
}

/** 药房日结预览 → PrintData（门诊/住院共用） */
export function buildPharmacyDailySettlementPrintData(
  source: PharmacyDailySettlementPrintSource,
): PharmacyDailySettlementPrintData {
  const pendingRaw = source.pendingCount;
  let pendingCountText = '0';
  if (pendingRaw !== null && pendingRaw !== undefined && pendingRaw !== '') {
    pendingCountText = String(pendingRaw).trim();
  }
  return {
    hospitalName: invPrintStr(source.hospitalName),
    documentTitle: invPrintStr(source.documentTitle) || '药房日结单',
    pharmacyName: invPrintStr(source.pharmacyName),
    settlementPeriodText: invPrintStr(source.settlementPeriodText),
    dispenserNamesText: invPrintStr(source.dispenserNamesText) || '全部',
    pendingCountText,
    totalIncomeText: moneyText(source.totalIncome),
    categoryRows: (source.categoryRows ?? []).map((row) => mapCategoryRow(row)),
    productRows: (source.productRows ?? []).map((row) => mapProductRow(row)),
  };
}
