/**
 * 报表「退/冲减」行：整行标红（勿只标单个金额字段）。
 * 与 ReportElTable 中 `.report-list-page__refund-row` 配套。
 */

import { REPORT_TABLE_REFUND_ROW } from './report-list-page-classes';

/** 数值是否为有限负数（退费/退药/冲减）。 */
export function isReportNegativeNumber(value: unknown): boolean {
  if (value === undefined || value === null || value === '') {
    return false;
  }
  const num = Number(value);
  return Number.isFinite(num) && num < 0;
}

/**
 * 行上任一指定字段为负则视为退/冲减行。
 *
 * @param row 表格行
 * @param fields 数量/金额等带符号字段
 */
export function isReportRefundBySignedFields(
  row: Record<string, unknown>,
  fields: readonly string[],
): boolean {
  // 任一数量/金额为负即可判定退冲减
  for (const field of fields) {
    if (isReportNegativeNumber(row[field])) {
      return true;
    }
  }
  return false;
}

/**
 * 拼 row-class-name：退行加统一 class，可附带业务行 class。
 *
 * @param isRefund 是否退/冲减行
 * @param extraClass 业务附加 class（就诊块底色、小计等）
 */
export function resolveReportRefundRowClassName(isRefund: boolean, extraClass = ''): string {
  const parts: string[] = [];
  const extra = extraClass.trim();
  if (extra) {
    parts.push(extra);
  }
  if (isRefund) {
    parts.push(REPORT_TABLE_REFUND_ROW);
  }
  return parts.join(' ');
}
