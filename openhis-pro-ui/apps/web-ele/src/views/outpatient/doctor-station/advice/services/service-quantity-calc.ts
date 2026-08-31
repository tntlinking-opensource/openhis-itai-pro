import { decimalDiv, decimalMul } from '#/utils/format/decimal-util';

/**
 * 门诊诊疗：UI「单次执行数量 × 执行次数」↔ 落库/账单 `quantity`（总量）。
 *
 * <p>后端契约不变：`quantity` 仍为账单计价乘数，`executionCount` 仍为护士站可执行次数。
 * 前端录入单次量与次数，提交前乘成总量；回显时再整除拆回单次量。</p>
 */

/** 单次量与次数是否可整乘还原为总量（含小数位容差）。 */
export function canEvenlySplitServiceQuantity(
  totalQuantity: number,
  executionCount: number,
): boolean {
  const total = Number(totalQuantity);
  const count = Math.trunc(Number(executionCount));
  if (!Number.isFinite(total) || total <= 0) return false;
  if (!Number.isFinite(count) || count <= 0) return false;
  const per = decimalDiv(total, count);
  if (!(per > 0)) return false;
  return decimalMul(per, count) === total;
}

/**
 * 提交用计费总量：单次执行数量 × 执行次数。
 *
 * @returns 正总量；入参非法时返回 `undefined`
 */
export function resolveServiceBillingQuantity(
  perExecutionQuantity: number | undefined | null,
  executionCount: number | undefined | null,
): number | undefined {
  const per = Number(perExecutionQuantity);
  const count = Math.trunc(Number(executionCount));
  if (!Number.isFinite(per) || per <= 0) return undefined;
  if (!Number.isFinite(count) || count <= 0) return undefined;
  const total = decimalMul(per, count);
  return total > 0 ? total : undefined;
}

/**
 * 回显用单次执行数量：总量 ÷ 执行次数（须可整除还原）。
 *
 * @returns 正单次量；不可整除或入参非法时返回 `undefined`
 */
export function resolveServicePerExecutionQuantity(
  totalQuantity: number | undefined | null,
  executionCount: number | undefined | null,
): number | undefined {
  const total = Number(totalQuantity);
  const count = Math.trunc(Number(executionCount));
  if (!Number.isFinite(total) || total <= 0) return undefined;
  if (!Number.isFinite(count) || count <= 0) return undefined;
  if (!canEvenlySplitServiceQuantity(total, count)) return undefined;
  const per = decimalDiv(total, count);
  return per > 0 ? per : undefined;
}

/**
 * 编辑态金额预览：单价 × 单次量 × 执行次数。
 *
 * @returns 计价数量；缺任一项时返回 0
 */
export function resolveServicePreviewBillQuantity(
  perExecutionQuantity: number | undefined | null,
  executionCount: number | undefined | null,
): number {
  return resolveServiceBillingQuantity(perExecutionQuantity, executionCount) ?? 0;
}
