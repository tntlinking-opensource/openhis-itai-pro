/**
 * 交账审批详情导出：按收款日结 / 药房日结结构生成 Excel（SpreadsheetML .xls）。
 * 无后端导出接口、无 xlsx 依赖时，用 Excel 可直接打开的 XML 工作簿。
 */
import type { LoadCashierSettlementApprovalDetailResponse } from '#/api/finance/cashier-settlement-approval';
import type {
  DailySettlementExpenseNatureSummaryDto,
  DailySettlementInsuranceTypeSummaryDto,
} from '#/api/outpatient/daily-settlement';

import { formatMoneyDisplay, sumMoneyThenSettle } from '#/utils/format/money-scale';

const TYPE_OP_PHARMACY = 'OP_PHARMACY';
const TYPE_IP_PHARMACY = 'IP_PHARMACY';

/** 空值展示为 —。 */
function displayText(value: unknown): string {
  const raw = value === undefined || value === null ? '' : String(value).trim();
  return raw || '—';
}

/** 金额两位小数；空则 —。 */
function displayAmount(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return formatMoneyDisplay(num);
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
function resolveApprovalAmount(detail: LoadCashierSettlementApprovalDetailResponse): string {
  const header = detail.header;
  const typeEnum = header?.typeEnum;
  const categoryRows = detail.pharmacyCategoryRows;
  if (isPharmacyType(typeEnum) && hasPharmacyCategoryAmount(categoryRows)) {
    const saleAmounts = (categoryRows ?? []).map((row) => row.saleAmount);
    return displayAmount(sumMoneyThenSettle(saleAmounts));
  }
  return displayAmount(header?.totalIncome);
}

/** XML 特殊字符转义。 */
function escapeXml(value: unknown): string {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

/** 追加空行。 */
function pushBlank(rows: string[][]) {
  rows.push([]);
}

/** 追加区块标题行。 */
function pushSectionTitle(rows: string[][], title: string) {
  rows.push([title]);
}

/** 追加键值行。 */
function pushKv(rows: string[][], label: string, value: unknown) {
  rows.push([label, displayText(value)]);
}

/** 追加表头 + 数据行。 */
function pushTable(rows: string[][], headers: string[], dataRows: unknown[][]) {
  rows.push(headers);
  for (const row of dataRows) {
    rows.push(row.map((cell) => (cell === undefined || cell === null ? '' : String(cell))));
  }
}

/** 按费用性质关键字取总额。 */
function findNatureTotalAmount(
  natures: DailySettlementExpenseNatureSummaryDto[] | undefined,
  keyword: string,
): string {
  const hit = (natures ?? []).find((nature) => (nature?.contractName ?? '').includes(keyword));
  return displayAmount(hit?.totalAmount ?? '0.00');
}

/** 险种子行 → 导出行。 */
function mapInsuranceTypeRow(type: DailySettlementInsuranceTypeSummaryDto): string[] {
  const title = type.insutypeName?.trim() || '未分险种';
  return [
    `　${title}`,
    String(type.personCount ?? 0),
    displayAmount(type.cashAmount),
    displayAmount(type.fundAmount),
    displayAmount(type.accountAmount),
    displayAmount(type.totalAmount),
  ];
}

/** 费用性质 / 险种子行展开。 */
function flattenNatureRows(
  natures: DailySettlementExpenseNatureSummaryDto[] | undefined,
): string[][] {
  const rows: string[][] = [];
  for (const nature of natures ?? []) {
    if (!nature) {
      continue;
    }
    const name = nature.contractName?.trim() || '未标注费用性质';
    const insurance = Boolean(nature.insurance);
    if (insurance) {
      rows.push([
        name,
        String(nature.personCount ?? 0),
        displayAmount(nature.cashAmount),
        displayAmount(nature.fundAmount),
        displayAmount(nature.accountAmount),
        displayAmount(nature.totalAmount),
      ]);
      // 险种子行缩进展示
      for (const type of nature.insuranceTypeSummaries ?? []) {
        rows.push(mapInsuranceTypeRow(type));
      }
      continue;
    }
    rows.push([
      name,
      String(nature.personCount ?? 0),
      '',
      '',
      '',
      displayAmount(nature.totalAmount),
    ]);
  }
  return rows;
}

/** 默认导出文件名（.xls，Excel 直接打开）。 */
function buildDefaultFileName(busNo?: string): string {
  const stamp = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  const day = `${stamp.getFullYear()}${pad(stamp.getMonth() + 1)}${pad(stamp.getDate())}`;
  const no = busNo?.trim() || '交账详情';
  return `交账详情_${no}_${day}.xls`;
}

/** 二维行数据 → SpreadsheetML 工作簿 XML。 */
function buildSpreadsheetMl(sheetName: string, rows: string[][]): string {
  const safeSheetName = escapeXml(sheetName.slice(0, 31) || '交账详情');
  const rowXml = rows
    .map((row) => {
      const cells = row
        .map((cell) => `<Cell><Data ss:Type="String">${escapeXml(cell)}</Data></Cell>`)
        .join('');
      return `<Row>${cells}</Row>`;
    })
    .join('');
  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<?mso-application progid="Excel.Sheet"?>',
    '<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"',
    ' xmlns:o="urn:schemas-microsoft-com:office:office"',
    ' xmlns:x="urn:schemas-microsoft-com:office:excel"',
    ' xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"',
    ' xmlns:html="http://www.w3.org/TR/REC-html40">',
    `<Worksheet ss:Name="${safeSheetName}"><Table>${rowXml}</Table></Worksheet>`,
    '</Workbook>',
  ].join('');
}

/** 触发浏览器下载 Excel。 */
function triggerExcelDownload(content: string, fileName: string) {
  const blob = new Blob([`\uFEFF${content}`], {
    type: 'application/vnd.ms-excel;charset=utf-8;',
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

/**
 * 下载交账审批详情 Excel（收款日结 / 药房日结）。
 *
 * @param detail 详情接口响应
 * @param fileName 可选文件名（建议 .xls）
 */
export function downloadCashierSettlementApprovalDetailExcel(
  detail: LoadCashierSettlementApprovalDetailResponse,
  fileName?: string,
): void {
  const header = detail.header;
  if (!header) {
    return;
  }

  const rows: string[][] = [];
  pushSectionTitle(rows, '交账头信息');
  pushKv(rows, '交账单号', header.busNo);
  pushKv(rows, '类型', header.typeEnumText);
  pushKv(rows, '状态', header.statusEnumText);
  pushKv(rows, '药房/科室', formatScopeLabel(header));
  pushKv(rows, '挂载笔数', detail.mountedSourceCount ?? header.paymentCount);
  pushKv(rows, '金额', resolveApprovalAmount(detail));
  pushKv(rows, '业务窗起', header.dateFrom);
  pushKv(rows, '业务窗止', header.dateTo);
  pushKv(rows, '交账人', header.submitterName);
  pushKv(rows, '交账时间', header.submittedAt);
  pushKv(rows, '审批人', header.approverName);
  pushKv(rows, '审批时间', header.approvedAt);
  if (detail.detailBodyRecomputeNote?.trim()) {
    pushKv(rows, '说明', detail.detailBodyRecomputeNote);
  }

  if (isPharmacyType(header.typeEnum)) {
    appendPharmacySections(rows, detail);
  } else {
    appendPaymentSections(rows, detail);
  }

  const xml = buildSpreadsheetMl('交账详情', rows);
  triggerExcelDownload(xml, fileName?.trim() || buildDefaultFileName(header.busNo));
}

/** 追加收款日结：费用科目 / 收入汇总 / 费用性质明细。 */
function appendPaymentSections(
  rows: string[][],
  detail: LoadCashierSettlementApprovalDetailResponse,
) {
  const paymentRows = detail.paymentRows ?? [];
  if (paymentRows.length > 0) {
    pushBlank(rows);
    pushSectionTitle(rows, '费用科目');
    pushTable(
      rows,
      ['科目', '金额', '单位'],
      paymentRows.map((row) => [row.category ?? '', row.amount ?? '', row.unit ?? '元']),
    );
  }

  const summary = detail.paymentSummary;
  if (summary) {
    pushBlank(rows);
    pushSectionTitle(rows, '收入汇总');
    pushKv(rows, '总收入', summary.totalIncome);
    pushKv(rows, '微信', summary.wechatAmount);
    pushKv(rows, '支付宝', summary.alipayAmount);
    pushKv(rows, '银行卡', summary.onlineBankAmount);
    pushKv(rows, '应交现金', summary.cashDueAmount ?? summary.cashAmount);
    pushKv(rows, '医保统筹', summary.insuranceCompensationAmount);
    pushKv(rows, '个人账户', summary.insuranceAccountAmount);
    if (summary.chsRetailUploadDiffAmount?.trim()) {
      pushKv(rows, '医保上传差额', summary.chsRetailUploadDiffAmount);
    }
    const natures = summary.expenseNatureSummaries;
    pushKv(rows, '市医保合计', findNatureTotalAmount(natures, '市医保'));
    pushKv(rows, '省医保合计', findNatureTotalAmount(natures, '省医保'));
    pushKv(rows, '自费合计', findNatureTotalAmount(natures, '自费'));
    if (summary.depositAmount !== undefined) {
      pushKv(rows, '预交金', summary.depositAmount);
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

/** 追加药房日结：大类汇总 / 品规汇总。 */
function appendPharmacySections(
  rows: string[][],
  detail: LoadCashierSettlementApprovalDetailResponse,
) {
  const categoryRows = detail.pharmacyCategoryRows ?? [];
  if (categoryRows.length > 0) {
    pushBlank(rows);
    pushSectionTitle(rows, '大类汇总');
    pushTable(
      rows,
      ['大类', '进价总额', '售价总额'],
      categoryRows.map((row) => [
        row.categoryLabel ?? '',
        displayAmount(row.purchaseAmount),
        displayAmount(row.saleAmount),
      ]),
    );
  }

  const productRows = detail.pharmacyProductRows ?? [];
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
        displayAmount(row.netQuantity),
        displayAmount(row.purchaseUnitPrice),
        displayAmount(row.saleUnitPrice),
        displayAmount(row.purchaseAmount),
        displayAmount(row.saleAmount),
      ]),
    );
  }
}
