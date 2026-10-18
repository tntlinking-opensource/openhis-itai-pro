/**
 * 药房日结导出（门诊/住院预览、交账审批详情共用）：大类汇总 + 品规汇总。
 *
 * 数量/金额单元格传 number，由 SpreadsheetML 写成 Number 类型，便于 Excel 求和。
 * 金额刻度：售价 2 位；进价按中药规则可 3 位（对齐 money-scale / BR-MON-001 展示层）。
 */
import type {
  PharmacyDailySettlementCategoryRowDto,
  PharmacyDailySettlementProductRowDto,
} from '#/api/outpatient/pharmacy-daily-settlement';

import {
  buildSpreadsheetMlWorkbook,
  formatExportDayStamp,
  type SpreadsheetCell,
  triggerSpreadsheetMlDownload,
} from './download-spreadsheet-ml-excel';
import {
  MONEY_DISPLAY_DP,
  resolveMedicationCatalogPurchasePriceDisplayDp,
  scaleMoneySettle,
} from '#/utils/format/money-scale';
import Decimal from 'decimal.js';

export interface PharmacyDailySettlementExcelSource {
  /** 工作表 / 文件名前缀，如「门诊药房日结」 */
  documentTitle: string;
  pharmacyName?: string;
  settlementPeriodText?: string;
  dispenserNamesText?: string;
  pendingCount?: number;
  totalIncome?: string | number;
  categoryRows?: PharmacyDailySettlementCategoryRowDto[];
  productRows?: PharmacyDailySettlementProductRowDto[];
  /** 有交账单号时写入文件名 */
  busNo?: string;
}

/** 空值展示为 —。 */
function displayText(value: unknown): string {
  const raw = value === undefined || value === null ? '' : String(value).trim();
  return raw || '—';
}

/**
 * 售价/普通金额 → 数字单元格（展示 2 位）；空则「—」文本。
 *
 * @param value 原始金额
 */
function moneyCell(value: unknown): SpreadsheetCell {
  // 空占位：文本「—」，避免 0 误导
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  // 非数字原文原样写出，便于排错
  if (Number.isNaN(num)) {
    return String(value);
  }
  return scaleMoneySettle(num);
}

/**
 * 进价金额 → 数字单元格（中药可 3 位，其它 2 位）；空则「—」。
 *
 * @param value 进价
 * @param categoryCode medication_category_code（中草药=4）
 */
function purchaseMoneyCell(value: unknown, categoryCode?: unknown): SpreadsheetCell {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  const dp = resolveMedicationCatalogPurchasePriceDisplayDp(categoryCode);
  // 非中药：与售价同 2 位
  if (dp === MONEY_DISPLAY_DP) {
    return scaleMoneySettle(num);
  }
  // 中药进价展示 3 位：对齐 formatPurchaseMoneyDisplay
  return new Decimal(num).toDecimalPlaces(dp, Decimal.ROUND_HALF_UP).toNumber();
}

/**
 * 数量 → 整数数字单元格；空则「—」。
 *
 * @param value 数量
 */
function quantityCell(value: unknown): SpreadsheetCell {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return new Decimal(num).toDecimalPlaces(0, Decimal.ROUND_HALF_UP).toNumber();
}

/** 追加空行。 */
function pushBlank(rows: SpreadsheetCell[][]) {
  rows.push([]);
}

/** 追加区块标题行。 */
function pushSectionTitle(rows: SpreadsheetCell[][], title: string) {
  rows.push([title]);
}

/** 追加键值行。 */
function pushKv(rows: SpreadsheetCell[][], label: string, value: SpreadsheetCell) {
  rows.push([label, value]);
}

/** 追加表头 + 数据行。 */
function pushTable(rows: SpreadsheetCell[][], headers: string[], dataRows: SpreadsheetCell[][]) {
  rows.push(headers);
  for (const row of dataRows) {
    rows.push(row);
  }
}

/** 追加大类 / 品规汇总区块（交账审批详情复用）。 */
export function appendPharmacyDailySettlementExcelSections(
  rows: SpreadsheetCell[][],
  source: {
    categoryRows?: PharmacyDailySettlementCategoryRowDto[];
    productRows?: PharmacyDailySettlementProductRowDto[];
  },
) {
  const categoryRows = source.categoryRows ?? [];
  // 无大类则跳过整块，避免空表头
  if (categoryRows.length > 0) {
    pushBlank(rows);
    pushSectionTitle(rows, '大类汇总');
    pushTable(
      rows,
      ['大类', '进价总额', '售价总额'],
      categoryRows.map((row) => [
        row.categoryLabel ?? '',
        purchaseMoneyCell(row.purchaseAmount, row.categoryCode),
        moneyCell(row.saleAmount),
      ]),
    );
  }

  const productRows = source.productRows ?? [];
  if (productRows.length > 0) {
    pushBlank(rows);
    pushSectionTitle(rows, '品规汇总');
    pushTable(
      rows,
      ['药品', '编码', '规格', '厂家', '净数量', '进价单价', '售价单价', '进价总额', '售价总额'],
      productRows.map((row) => [
        row.medicationName ?? '',
        row.busNo ?? '',
        row.specification ?? '',
        row.manufacturer ?? '',
        quantityCell(row.netQuantity),
        purchaseMoneyCell(row.purchaseUnitPrice),
        moneyCell(row.saleUnitPrice),
        purchaseMoneyCell(row.purchaseAmount),
        moneyCell(row.saleAmount),
      ]),
    );
  }
}

/**
 * 默认导出文件名（有交账单号则拼入）。
 *
 * @param source 日结导出源
 */
function buildDefaultFileName(source: PharmacyDailySettlementExcelSource): string {
  const day = formatExportDayStamp();
  const no = source.busNo?.trim();
  // 有单号：单据_单号_日期，便于归档对账
  // SpreadsheetML 须用 .xls（.xlsx 会被新版 Excel 拒开）
  if (no) {
    return `${source.documentTitle}_${no}_${day}.xls`;
  }
  return `${source.documentTitle}_${day}.xls`;
}

/**
 * 下载药房日结 Excel（预览汇总口径：头信息 + 大类 + 品规）。
 *
 * @param source 头信息与汇总行
 * @param fileName 可选覆盖文件名
 */
export function downloadPharmacyDailySettlementExcel(
  source: PharmacyDailySettlementExcelSource,
  fileName?: string,
): void {
  const rows: SpreadsheetCell[][] = [];
  pushSectionTitle(rows, '日结头信息');
  pushKv(rows, '单据', displayText(source.documentTitle));
  pushKv(rows, '药房', displayText(source.pharmacyName));
  pushKv(rows, '日结时间', displayText(source.settlementPeriodText));
  pushKv(rows, '发药人', displayText(source.dispenserNamesText));
  pushKv(rows, '待交笔数', source.pendingCount ?? 0);
  pushKv(rows, '售价合计', moneyCell(source.totalIncome));

  appendPharmacyDailySettlementExcelSections(rows, source);

  const sheetName = source.documentTitle.slice(0, 31) || '药房日结';
  const xml = buildSpreadsheetMlWorkbook(sheetName, rows);
  triggerSpreadsheetMlDownload(xml, fileName?.trim() || buildDefaultFileName(source));
}
