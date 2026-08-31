import type { TraceBatchScanLine, TraceScanBindMode } from './trace-types';

import { isTraceSlotEmpty } from './trace-code-parse';
import { splitTraceNoCommaString } from './trace-no-string';

/**
 * 绑码硬上限：ceil(操作量)。码倍不参与个数（见 {@link resolveMaxTraceCount}）。
 *
 * @param operationQty 操作数量（与操作单位同阶）
 * @returns ≥1；无法计算时 undefined
 */
export function computeMaxTraceCount(operationQty: number | undefined | null): number | undefined {
  const qty = Number(operationQty);
  if (!Number.isFinite(qty) || qty <= 0) return undefined;
  return Math.max(1, Math.ceil(qty));
}

/**
 * 应扫追溯码个数（与后端 TraceScanCountSupport 口径一致）。
 *
 * <p>进货硬 Gate 仍用个数；消耗/流转侧本值为「待扫」软提示，硬上限见 {@link computeMaxTraceCount}；
 * 交互优先 {@link isRemainingCoverageReached}（盖住即合法）。</p>
 *
 * - 操作单位 == 追溯单位：ceil(operationQty)；若调用方已按码倍换算则自行处理
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
 * <p>须<strong>每一枚</strong>已绑码都有剩余缓存，才返回合计；缺任一码则 undefined，
 * 避免用部分码合计去比整行拆零需求，误判「盖不住」。</p>
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
  for (const code of codes) {
    // 缺缓存：无法判定盖住，回落调用方个数口径
    if (!(code in remainingByTraceNo)) return undefined;
    const rem = Number(remainingByTraceNo[code]);
    if (!Number.isFinite(rem) || rem < 0) return undefined;
    sum += rem;
  }
  return sum;
}

/**
 * 解析单行绑码硬上限（消耗侧扫多硬拦 / 「待扫」列真源）。
 *
 * <p>优先行上 {@code maxTraceCount}（=ceil(操作量)）；缺省回落建议应扫（同阶时亦为操作量）。
 * <strong>禁止</strong>用 {@code operationSplitQty}/码倍推个数。</p>
 *
 * @param row 扫码行
 * @returns ≥1；无法计算时 null
 */
export function resolveMaxTraceCount(row: TraceBatchScanLine): number | null {
  const raw = row.maxTraceCount;
  if (raw !== undefined && raw !== null && Number.isFinite(Number(raw))) {
    const n = Math.ceil(Number(raw));
    if (n > 0) return n;
  }
  // 回落建议应扫（发药同阶=操作量；勿用拆零量）
  const required = resolveRequiredTraceCount(row, 'comma');
  if (required !== null) return required;
  return null;
}

/**
 * 行剩余待扫数量（弹窗「待扫」列）。
 *
 * <p>消耗/发药：硬上限=ceil(操作量)（开 2→2、开 1 盒→1）；已盖住（真拆零需求）则 0。
 * 进货无上限时回落建议应扫。码倍不参与待扫个数；盖住左侧 {@code operationSplitQty} 须已是拆零。</p>
 *
 * @param row 扫码行
 * @param trace 行上已绑码
 * @param bindMode 绑定模式
 * @param opts.boundRemainingSum 已绑码主档剩余合计（盖住判定）
 * @returns 待扫个数；无法计算时 null
 */
export function countPendingTraceScan(
  row: TraceBatchScanLine,
  trace: string | undefined,
  bindMode: TraceScanBindMode,
  opts?: { boundRemainingSum?: number },
): number | null {
  const bound = countBoundTraceCodesOnLine(trace, bindMode);
  const covered = isRemainingCoverageReached(row.operationSplitQty, opts?.boundRemainingSum);
  // 剩余已盖住：不必再扫
  if (covered === true) return 0;

  // 消耗侧：待扫=操作量剩余（禁止用码倍/拆零推个数）
  const maxCodes = resolveMaxTraceCount(row);
  if (maxCodes !== null) {
    return Math.max(0, maxCodes - bound);
  }

  // 进货等：回落建议应扫
  const required = resolveRequiredTraceCount(row, bindMode);
  if (required === null) return null;
  return Math.max(0, required - bound);
}

/**
 * 行是否还能再绑一个追溯码。
 *
 * <p>消耗侧硬拦仅两类：① 剩余合计已盖住；② 绑码个数 ≥ {@link resolveMaxTraceCount}（开几支最多几码）。
 * 「建议应扫」{@link resolveRequiredTraceCount} 不足只作软提示，不在此硬拒。
 * 进货硬控勿传 operationSplitQty，走个数 pending。</p>
 */
export function canAcceptMoreTraceOnLine(
  row: TraceBatchScanLine,
  trace: string | undefined,
  bindMode: TraceScanBindMode,
  opts?: { boundRemainingSum?: number },
): boolean {
  const bound = countBoundTraceCodesOnLine(trace, bindMode);
  const maxCodes = resolveMaxTraceCount(row);
  // 扫多了：开 2 支不能扫第 3 个码
  if (maxCodes !== null && bound >= maxCodes) return false;

  const covered = isRemainingCoverageReached(row.operationSplitQty, opts?.boundRemainingSum);
  // 已盖住发药/出库量：勿再扫（亦属扫多）
  if (covered === true) return false;
  // 未盖住且有剩余口径：仍可继续拼码（即使已达建议应扫个数）
  if (covered === false) return true;

  // 缺剩余数据：仅按硬上限放行；无上限时回落建议应扫（进货）
  if (maxCodes !== null) return bound < maxCodes;

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
  const maxCodes = resolveMaxTraceCount(row);
  if (maxCodes !== null) {
    return `追溯码数量不能超过发药量（最多 ${maxCodes} 个）`;
  }
  const required = resolveRequiredTraceCount(row, bindMode);
  if (required !== null) {
    return `追溯码数量不能超过药品数量（${required}）`;
  }
  return '该行已扫满，不能再录入追溯码';
}
