/**
 * 交账审批详情导出：按收款日结 / 药房日结结构生成 Excel。
 * 无后端导出接口、无 xlsx 依赖时，用 Excel 可直接打开的 SpreadsheetML 工作簿（`.xls`）。
 *
 * 数量/金额写 Number 类型，便于 Excel 框选求和（对齐 monetary-amount-contract §1.1）。
 */
import type { LoadCashierSettlementApprovalDetailResponse } from '#/api/finance/cashier-settlement-approval';
import type {
  DailySettlementExpenseNatureSummaryDto,
  DailySettlementInsuranceTypeSummaryDto,
} from '#/api/outpatient/daily-settlement';

import {
  buildSpreadsheetMlWorkbook,
  formatExportDayStamp,
  type SpreadsheetCell,
  triggerSpreadsheetMlDownload,
} from '#/utils/export/download-spreadsheet-ml-excel';
import { appendPharmacyDailySettlementExcelSections } from '#/utils/export/pharmacy-daily-settlement-excel';
import { scaleMoneySettle, sumMoneyThenSettle } from '#/utils/format/money-scale';

const TYPE_OP_PHARMACY = 'OP_PHARMACY';
const TYPE_IP_PHARMACY = 'IP_PHARMACY';

/** 空值展示为 —。 */
function displayText(value: unknown): string {
  const raw = value === undefined || value === null ? '' : String(value).trim();
  return raw || '—';
}

/** 金额 → 数字单元格（2 位）；空则「—」。 */
function moneyCell(value: unknown): SpreadsheetCell {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return scaleMoneySettle(num);
}

/** 人数/笔数 → 整数数字单元格。 */
function countCell(value: unknown): SpreadsheetCell {
  if (value === null || value === undefined || value === '') {
    return 0;
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return Math.trunc(num);
}

/** 是否药房日结类型。 */
function isPharmacyType(typeEnum?: string): boolean {
  return typeEnum === TYPE_OP_PHARMACY || typeEnum === TYPE_IP_PHARMACY;
}

/** 药房/科室展示：药房名优先。 */
function formatScopeLabel(header: { locationName?: string; orgName?: string }): string {
  const locationName = header.locationName?.trim();
  if (locationName) {
    return locationName;
  }
  const orgName = header.orgName?.trim();
  if (orgName) {
    return orgName;
  }
  return '—';
}

/** 大类是否含有效进/售金额。 */
function hasPharmacyCategoryAmount(
  categoryRows: LoadCashierSettlementApprovalDetailResponse['pharmacyCategoryRows'],
): boolean {
  if (!categoryRows?.length) {
    return false;
  }
  return categoryRows.some((row) => {
    const sale = Number(row.saleAmount);
    const purchase = Number(row.purchaseAmount);
    return (Number.isFinite(sale) && sale !== 0) || (Number.isFinite(purchase) && purchase !== 0);
  });
}

/** 审批金额：药房优先售价合计，否则头表 totalIncome。 */
function resolveApprovalAmount(
  detail: LoadCashierSettlementApprovalDetailResponse,
): SpreadsheetCell {
  const header = detail.header;
  const typeEnum = header?.typeEnum;
  const categoryRows = detail.pharmacyCategoryRows;
  if (isPharmacyType(typeEnum) && hasPharmacyCategoryAmount(categoryRows)) {
    const saleAmounts = (categoryRows ?? []).map((row) => row.saleAmount);
    return moneyCell(sumMoneyThenSettle(saleAmounts));
  }
  return moneyCell(header?.totalIncome);
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

/** 按费用性质关键字取总额。 */
function findNatureTotalAmount(
  natures: DailySettlementExpenseNatureSummaryDto[] | undefined,
  keyword: string,
): SpreadsheetCell {
  const hit = (natures ?? []).find((nature) => (nature?.contractName ?? '').includes(keyword));
  return moneyCell(hit?.totalAmount ?? '0.00');
}

/** 险种子行 → 导出行。 */
function mapInsuranceTypeRow(type: DailySettlementInsuranceTypeSummaryDto): SpreadsheetCell[] {
  const title = type.insutypeName?.trim() || '未分险种';
  return [
    `　${title}`,
    countCell(type.personCount),
    moneyCell(type.cashAmount),
    moneyCell(type.fundAmount),
    moneyCell(type.accountAmount),
    moneyCell(type.totalAmount),
  ];
}

/** 费用性质 / 险种子行展开。 */
function flattenNatureRows(
  natures: DailySettlementExpenseNatureSummaryDto[] | undefined,
): SpreadsheetCell[][] {
  const rows: SpreadsheetCell[][] = [];
  for (const nature of natures ?? []) {
    if (!nature) {
      continue;
    }
    const name = nature.contractName?.trim() || '未标注费用性质';
    const insurance = Boolean(nature.insurance);
    if (insurance) {
      rows.push([
        name,
        countCell(nature.personCount),
        moneyCell(nature.cashAmount),
        moneyCell(nature.fundAmount),
        moneyCell(nature.accountAmount),
        moneyCell(nature.totalAmount),
      ]);
      for (const type of nature.insuranceTypeSummaries ?? []) {
        rows.push(mapInsuranceTypeRow(type));
      }
      continue;
    }
    rows.push([name, countCell(nature.personCount), '', '', '', moneyCell(nature.totalAmount)]);
  }
  return rows;
}

/** 默认导出文件名（SpreadsheetML → `.xls`）。 */
function buildDefaultFileName(busNo?: string): string {
  const day = formatExportDayStamp();
  const no = busNo?.trim() || '交账详情';
  return `交账详情_${no}_${day}.xls`;
}

/**
 * 下载交账审批详情 Excel（收款日结 / 药房日结）。
 *
 * @param detail 详情接口响应
 * @param fileName 可选文件名（建议 `.xls`；误传 `.xlsx` 时公共下载器会改写）
 */
export function downloadCashierSettlementApprovalDetailExcel(
  detail: LoadCashierSettlementApprovalDetailResponse,
  fileName?: string,
): void {
  const header = detail.header;
  if (!header) {
    return;
  }

  const rows: SpreadsheetCell[][] = [];
  pushSectionTitle(rows, '交账头信息');
  pushKv(rows, '交账单号', displayText(header.busNo));
  pushKv(rows, '类型', displayText(header.typeEnumText));
  pushKv(rows, '状态', displayText(header.statusEnumText));
  pushKv(rows, '药房/科室', formatScopeLabel(header));
  pushKv(rows, '挂载笔数', countCell(detail.mountedSourceCount ?? header.paymentCount));
  pushKv(rows, '金额', resolveApprovalAmount(detail));
  pushKv(rows, '业务窗起', displayText(header.dateFrom));
  pushKv(rows, '业务窗止', displayText(header.dateTo));
  pushKv(rows, '交账人', displayText(header.submitterName));
  pushKv(rows, '交账时间', displayText(header.submittedAt));
  pushKv(rows, '审批人', displayText(header.approverName));
  pushKv(rows, '审批时间', displayText(header.approvedAt));
  if (detail.detailBodyRecomputeNote?.trim()) {
    pushKv(rows, '说明', detail.detailBodyRecomputeNote);
  }

  if (isPharmacyType(header.typeEnum)) {
    appendPharmacyDailySettlementExcelSections(rows, {
      categoryRows: detail.pharmacyCategoryRows,
      productRows: detail.pharmacyProductRows,
    });
  } else {
    appendPaymentSections(rows, detail);
  }

  const xml = buildSpreadsheetMlWorkbook('交账详情', rows);
  triggerSpreadsheetMlDownload(xml, fileName?.trim() || buildDefaultFileName(header.busNo));
}

/** 追加收款日结：费用科目 / 收入汇总 / 费用性质明细。 */
function appendPaymentSections(
  rows: SpreadsheetCell[][],
  detail: LoadCashierSettlementApprovalDetailResponse,
) {
  const paymentRows = detail.paymentRows ?? [];
  if (paymentRows.length > 0) {
    pushBlank(rows);
    pushSectionTitle(rows, '费用科目');
    pushTable(
      rows,
      ['科目', '金额', '单位'],
      paymentRows.map((row) => [row.category ?? '', moneyCell(row.amount), row.unit ?? '元']),
    );
  }

  const summary = detail.paymentSummary;
  if (summary) {
    pushBlank(rows);
    pushSectionTitle(rows, '收入汇总');
    pushKv(rows, '总收入', moneyCell(summary.totalIncome));
    pushKv(rows, '微信', moneyCell(summary.wechatAmount));
    pushKv(rows, '支付宝', moneyCell(summary.alipayAmount));
    pushKv(rows, '银行卡', moneyCell(summary.onlineBankAmount));
    pushKv(rows, '应交现金', moneyCell(summary.cashDueAmount ?? summary.cashAmount));
    pushKv(rows, '医保统筹', moneyCell(summary.insuranceCompensationAmount));
    pushKv(rows, '个人账户', moneyCell(summary.insuranceAccountAmount));
    if (summary.chsRetailUploadDiffAmount?.trim()) {
      pushKv(rows, '医保上传差额', moneyCell(summary.chsRetailUploadDiffAmount));
    }
    const natures = summary.expenseNatureSummaries;
    pushKv(rows, '市医保合计', findNatureTotalAmount(natures, '市医保'));
    pushKv(rows, '省医保合计', findNatureTotalAmount(natures, '省医保'));
    pushKv(rows, '自费合计', findNatureTotalAmount(natures, '自费'));
    if (summary.depositAmount !== undefined) {
      pushKv(rows, '预交金', moneyCell(summary.depositAmount));
    }

    const natureRows = flattenNatureRows(natures);
    if (natureRows.length > 0) {
      pushBlank(rows);
      pushSectionTitle(rows, '费用性质明细');
      pushTable(
        rows,
        ['费用性质/险种', '人数', '医保现金(元)', '医保统筹(元)', '个人账户(元)', '总额(元)'],
        natureRows,
      );
    }
  }
}
