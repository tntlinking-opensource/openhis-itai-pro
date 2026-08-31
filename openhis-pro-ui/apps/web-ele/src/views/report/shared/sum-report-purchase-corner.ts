/*
 * 报表进价列角：与当次返回的汇总/卡片行同源加总（Type B），保证页脚=可见行进价代数和。
 */
import { decimalAdd } from '#/utils/format/decimal-util';

/**
 * 将进价列角金额规范为可加总数值；空/非数字视为 0（诊疗无进价不入列角）。
 *
 * @param value 行进价字段
 */
function toPurchaseAddend(value: unknown): number {
  if (value === null || value === undefined || value === '') {
    return 0;
  }
  const n = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(n)) {
    return 0;
  }
  return n;
}

/**
 * 由当次接口返回的汇总行/卡片行加总进价列角（与查询同筛选、与表内进价列同管线）。
 *
 * <p>Type B 报表（发药/耗材汇总、费用明细汇总 Tab、护士卡片墙等）：
 * 页脚进价<strong>禁止</strong>另扫一套事实；须等于本批可见行进价代数和。</p>
 *
 * @param rows 当次返回的行列表
 * @param field 行上进价字段名
 * @returns 加总结果（字符串，便于绑定页脚）；无行时为空串
 */
export function sumReportPurchaseCornerFromRows<T extends Record<string, unknown>>(
  rows: T[] | null | undefined,
  field: keyof T & string,
): string {
  if (!rows || rows.length === 0) {
    return '';
  }
  let sum = 0;
  // 逐行累加：与表内展示进价同字段，避免页脚另口径
  for (const row of rows) {
    if (!row) {
      continue;
    }
    sum = Number(decimalAdd(sum, toPurchaseAddend(row[field])));
  }
  return String(sum);
}
