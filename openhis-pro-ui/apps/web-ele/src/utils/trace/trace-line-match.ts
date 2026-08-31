import type { TraceBatchScanLine, TraceScanBindMode } from './trace-types';

import { isNonStandardTraceDrugName, isTraceSlotEmpty } from './trace-code-parse';
import { canAcceptMoreTraceOnLine, sumBoundRemainingSplit } from './trace-line-scan-count';
import { traceNoCommaContains } from './trace-no-string';

/** 归一化品项主键用于行匹配 */
function normItemId(value: string | undefined): string {
  return String(value ?? '').trim();
}

/** 归一化批号用于行匹配 */
function normLotNumber(value: string | undefined): string {
  return String(value ?? '').trim();
}

/** 按药名关键字筛选非标追溯码品行（如依洛尤单抗） */
export function findNonStandardTraceDrugLines(lines: TraceBatchScanLine[]): TraceBatchScanLine[] {
  return lines.filter((line) => isNonStandardTraceDrugName(line.itemName));
}

/** 按 itemId + lotNumber 筛选候选行 */
export function findTraceMatchCandidateLines(
  lines: TraceBatchScanLine[],
  itemId: string,
  lotNumber: string,
): TraceBatchScanLine[] {
  const wantItem = normItemId(itemId);
  const wantLot = normLotNumber(lotNumber);
  if (!wantItem) return [];

  // 优先品+批精确匹配
  if (wantLot) {
    const exact = lines.filter((line) => {
      return normItemId(line.itemId) === wantItem && normLotNumber(line.lotNumber) === wantLot;
    });
    if (exact.length > 0) return exact;
  }

  // 自动匹配兜底：行批号空或与主档批号不一致时，同品未满行仍可绑（避免误逼用户点选）
  return lines.filter((line) => normItemId(line.itemId) === wantItem);
}

/** 在候选行中挑选绑定目标：优先未盖住/未配满的行 */
export function pickTraceBindTargetLine(
  candidates: TraceBatchScanLine[],
  traceNo: string,
  bindMode: TraceScanBindMode,
  effectiveTraceByLineId: Record<string, string>,
  remainingByTraceNo?: Record<string, number>,
): TraceBatchScanLine | undefined {
  if (candidates.length === 0) return undefined;

  const resolveTrace = (line: TraceBatchScanLine): string =>
    String(effectiveTraceByLineId[line.id] ?? line.traceNo ?? '').trim();

  const coverageOpts = (line: TraceBatchScanLine) => {
    if (!remainingByTraceNo || line.operationSplitQty == null) return undefined;
    const boundRemainingSum = sumBoundRemainingSplit(resolveTrace(line), remainingByTraceNo);
    if (boundRemainingSum === undefined) return undefined;
    return { boundRemainingSum };
  };

  const scoreLine = (line: TraceBatchScanLine): number => {
    const cur = resolveTrace(line);
    if (!canAcceptMoreTraceOnLine(line, cur, bindMode, coverageOpts(line))) return 99;
    if (bindMode === 'single') {
      return isTraceSlotEmpty(cur) ? 0 : 1;
    }
    return traceNoCommaContains(cur, traceNo) ? 2 : isTraceSlotEmpty(cur) ? 0 : 1;
  };

  const sorted = [...candidates].sort((a, b) => scoreLine(a) - scoreLine(b));
  const best = sorted[0];
  if (!best) return undefined;

  const cur = resolveTrace(best);
  if (!canAcceptMoreTraceOnLine(best, cur, bindMode, coverageOpts(best))) return undefined;
  if (bindMode === 'comma' && traceNoCommaContains(cur, traceNo)) {
    return undefined;
  }
  if (
    bindMode === 'single' &&
    !isTraceSlotEmpty(cur) &&
    String(cur ?? '').trim() !== traceNo.trim()
  ) {
    return best;
  }
  return best;
}

/** NOT_FOUND 时绑定到首个 trace 为空的行（进货/盘点新码场景） */
export function pickTraceFallbackLineForNotFound(
  lines: TraceBatchScanLine[],
  effectiveTraceByLineId: Record<string, string>,
  bindMode: TraceScanBindMode,
  remainingByTraceNo?: Record<string, number>,
): TraceBatchScanLine | undefined {
  for (const line of lines) {
    const cur = String(effectiveTraceByLineId[line.id] ?? line.traceNo ?? '').trim();
    let opts: { boundRemainingSum?: number } | undefined;
    if (remainingByTraceNo && line.operationSplitQty != null) {
      const boundRemainingSum = sumBoundRemainingSplit(cur, remainingByTraceNo);
      if (boundRemainingSum !== undefined) {
        opts = { boundRemainingSum };
      }
    }
    if (!canAcceptMoreTraceOnLine(line, cur, bindMode, opts)) continue;
    if (bindMode === 'single' && isTraceSlotEmpty(cur)) return line;
    if (bindMode === 'comma') return line;
  }
  return undefined;
}
