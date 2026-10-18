/**
 * 前端 SpreadsheetML 工作簿下载（Excel 可直接打开）。
 * 无 xlsx 依赖时使用。内容为 Excel 2003 XML（SpreadsheetML），
 * **扩展名须为 `.xls`**：写成 `.xlsx` 会被新版 Excel 以「格式或扩展名无效」拒开。
 *
 * 数量/金额须传 number，写出 ss:Type="Number"；否则 Excel 只能计数不能求和。
 * 口径对齐 docs monetary-amount-contract §1.1。
 */

/** 单元格：文本 / 数字；空值写空白。 */
export type SpreadsheetCell = string | number | null | undefined;

/**
 * XML 特殊字符转义（防破坏 SpreadsheetML 结构）。
 *
 * @param value 任意单元格原文
 */
export function escapeSpreadsheetXml(value: unknown): string {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

/**
 * 单个单元格 → SpreadsheetML。
 * 有限数字写 Number；空/NaN/Infinity/文本写 String（含「—」占位）。
 *
 * @param cell 单元格值
 */
function buildCellXml(cell: SpreadsheetCell): string {
  // 空：空白字符串单元格
  if (cell === null || cell === undefined || cell === '') {
    return '<Cell><Data ss:Type="String"></Data></Cell>';
  }
  if (typeof cell === 'number') {
    // 非有限数不能写 Number，回落空串避免坏 XML
    if (!Number.isFinite(cell)) {
      return '<Cell><Data ss:Type="String"></Data></Cell>';
    }
    return `<Cell><Data ss:Type="Number">${cell}</Data></Cell>`;
  }
  return `<Cell><Data ss:Type="String">${escapeSpreadsheetXml(cell)}</Data></Cell>`;
}

/**
 * 二维行数据 → SpreadsheetML 工作簿 XML。
 *
 * @param sheetName 工作表名（超长截断至 31）
 * @param rows 行；金额/数量请传 number
 */
export function buildSpreadsheetMlWorkbook(sheetName: string, rows: SpreadsheetCell[][]): string {
  const safeSheetName = escapeSpreadsheetXml(sheetName.slice(0, 31) || 'Sheet1');
  const rowXml = rows
    .map((row) => {
      const cells = (row ?? []).map((cell) => buildCellXml(cell)).join('');
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

/**
 * 将下载名规范为 SpreadsheetML 可用的 `.xls`（误传 `.xlsx` 时改写）。
 *
 * @param fileName 原始文件名
 */
function resolveSpreadsheetMlFileName(fileName: string): string {
  let base = fileName.trim() || 'export';
  const lower = base.toLowerCase();
  // 误用 OOXML 扩展名：新版 Excel 会拒开 SpreadsheetML 内容
  if (lower.endsWith('.xlsx')) {
    base = `${base.slice(0, -5)}.xls`;
  } else if (!lower.endsWith('.xls')) {
    base = `${base}.xls`;
  }
  return base;
}

/**
 * 触发浏览器下载 Excel（SpreadsheetML → `.xls`）。
 *
 * @param content SpreadsheetML XML 正文
 * @param fileName 下载名；`.xlsx` 会改写为 `.xls`，无后缀时自动补 `.xls`
 */
export function triggerSpreadsheetMlDownload(content: string, fileName: string) {
  const safeName = resolveSpreadsheetMlFileName(fileName);
  // MIME 用 ms-excel；正文为 SpreadsheetML XML（非 OOXML zip）
  const blob = new Blob([content], {
    type: 'application/vnd.ms-excel;charset=utf-8;',
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = safeName;
  link.click();
  URL.revokeObjectURL(url);
}

/**
 * 按本地日期生成 yyyyMMdd（文件名时间戳）。
 *
 * @param date 默认当前本地日
 */
export function formatExportDayStamp(date = new Date()): string {
  /** 两位补零。 */
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`;
}
