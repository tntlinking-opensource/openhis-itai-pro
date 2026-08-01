import type { TraceBatchScanLine, TraceScanBindMode } from './trace-types';

import { isTraceSlotEmpty } from './trace-code-parse';
import { splitTraceNoCommaString } from './trace-no-string';

/**
 * 应扫追溯码个数（与后端 TraceScanCountSupport 口径一致）。
 *
 * <p>进货/期初硬 Gate 仍用个数；消耗/流转侧交互优先 {@link isRemainingCoverageReached}（盖住即合法）。</p>
 *
 * - 操作单位 == 追溯单位：ceil(operationQty)（同阶不绕拆零）
 * - 否则：ceil(operationSplitQty / perTraceSplitQty)
 * - outbound：结果至少为 1；inbound 亦 ceil（与后端 TraceScanCountSupport 同口径）
 */
export function computeRequiredTraceCount(args: {
  operationQty: number;
  operationUnitCode?: string;
  traceUnitCode?: string;
  /** 本行操作量换算为拆零后的数量；单位不同时必填 */
  operationSplitQty?: number;
  /** 1 个追溯单位对应的拆零量；单位不同时必填 */
  perTraceSplitQty?: number;
  /** 出库/发药至少 1；进货同式 */
  mode?: 'inbound' | 'outbound';
}): number | undefined {
  const opQty = Number(args.operationQty);
  if (!Number.isFinite(opQty) || opQty <= 0) return undefined;

  const opUnit = String(args.operationUnitCode ?? '').trim();
  const traceUnit = String(args.traceUnitCode ?? '').trim();
  // 同阶：直接用操作数量向上取整
  if (opUnit && traceUnit && opUnit === traceUnit) {
    return Math.max(1, Math.ceil(opQty));
  }

  const opSplit = Number(args.operationSplitQty);
  const perTrace = Number(args.perTraceSplitQty);
  if (Number.isFinite(opSplit) && opSplit > 0 && Number.isFinite(perTrace) && perTrace > 0) {
    const n = Math.ceil(opSplit / perTrace);
    return Math.max(1, n);
  }

  // 缺换算数据时回落操作数量（与历史「1 数量 ≈ 1 码」兼容）
  return Math.max(1, Math.ceil(opQty));
}

/**
 * 已扫码剩余合计是否盖住本次操作拆零量（消耗/流转侧合法口径）。
 *
 * @returns true=已盖住；false=未盖住；null=缺数据无法判定（回落个数）
 */
export function isRemainingCoverageReached(
  operationSplitQty: number | undefined | null,
  boundRemainingSum: number | undefined | null,
): boolean | null {
  const need = Number(operationSplitQty);
  const have = Number(boundRemainingSum);
  if (!Number.isFinite(need) || need <= 0) return null;
  if (!Number.isFinite(have) || have < 0) return null;
  return have + 1e-9 >= need;
}

/** 某行已绑追溯码数量 */
export function countBoundTraceCodesOnLine(
  trace: string | undefined,
  bindMode: TraceScanBindMode,
): number {
  if (isTraceSlotEmpty(trace)) return 0;
  if (bindMode === 'comma') return splitTraceNoCommaString(trace).length;
  return 1;
}

/** 行应扫追溯码总数；comma 模式无行数量时返回 null */
export function resolveRequiredTraceCount(
  row: TraceBatchScanLine,
  bindMode: TraceScanBindMode,
): number | null {
  const raw = row.requiredTraceCount;
  if (raw !== undefined && raw !== null && Number.isFinite(Number(raw))) {
    const n = Math.ceil(Number(raw));
    if (n > 0) return n;
  }
  if (bindMode === 'single') return 1;
  return null;
}

/**
 * 汇总行上已绑码的剩余拆零量（由扫码反查缓存传入）。
 *
 * @param trace 行上逗号串或单码
 * @param remainingByTraceNo 码 → 主档剩余拆零
 */
export function sumBoundRemainingSplit(
  trace: string | undefined,
  remainingByTraceNo: Record<string, number> | undefined,
): number | undefined {
  if (!remainingByTraceNo || isTraceSlotEmpty(trace)) return undefined;
  const codes = splitTraceNoCommaString(trace);
  if (codes.length === 0) return undefined;
  let sum = 0;
  let any = false;
  for (const code of codes) {
    const rem = Number(remainingByTraceNo[code]);
    if (!Number.isFinite(rem) || rem < 0) continue;
    sum += rem;
    any = true;
  }
  return any ? sum : undefined;
}

/** 行剩余待扫数量；无法计算时返回 null（个数口径，进货用） */
export function countPendingTraceScan(
  row: TraceBatchScanLine,
  trace: string | undefined,
  bindMode: TraceScanBindMode,
): number | null {
  const required = resolveRequiredTraceCount(row, bindMode);
  if (required === null) return null;
  const bound = countBoundTraceCodesOnLine(trace, bindMode);
  return Math.max(0, required - bound);
}

/**
 * 行是否还能再绑一个追溯码。
 *
 * <p>消耗侧若提供 {@code operationSplitQty} + 已绑剩余合计：已盖住则不可再扫；
 * 否则回落个数口径（进货硬控勿传 operationSplitQty）。</p>
 */
export function canAcceptMoreTraceOnLine(
  row: TraceBatchScanLine,
  trace: string | undefined,
  bindMode: TraceScanBindMode,
  opts?: { boundRemainingSum?: number },
): boolean {
  const covered = isRemainingCoverageReached(row.operationSplitQty, opts?.boundRemainingSum);
  // 已盖住发药/出库量：勿再扫
  if (covered === true) return false;
  // 未盖住且有剩余口径：仍可继续扫
  if (covered === false) return true;

  const pending = countPendingTraceScan(row, trace, bindMode);
  if (pending === null) return true;
  return pending > 0;
}

/** 行追溯码已满/已盖住时的提示文案 */
export function traceLineScanFullMessage(
  row: TraceBatchScanLine,
  bindMode: TraceScanBindMode,
  opts?: { boundRemainingSum?: number },
): string {
  const covered = isRemainingCoverageReached(row.operationSplitQty, opts?.boundRemainingSum);
  if (covered === true) {
    return '已扫码剩余合计已盖住本次发药量，请勿再扫';
  }
  const required = resolveRequiredTraceCount(row, bindMode);
  if (required !== null) {
    return `追溯码数量不能超过药品数量（${required}）`;
  }
  return '该行已扫满，不能再录入追溯码';
}
