import type { TraceBatchScanLine, TraceScanBindMode } from './trace-types';

import { isTraceSlotEmpty } from './trace-code-parse';
import { canAcceptMoreTraceOnLine } from './trace-line-scan-count';
import { traceNoCommaContains } from './trace-no-string';

/** 归一化品项主键用于行匹配 */
function normItemId(value: string | undefined): string {
  return String(value ?? '').trim();
}

/** 归一化批号用于行匹配 */
function normLotNumber(value: string | undefined): string {
  return String(value ?? '').trim();
}

/** 按 itemId + lotNumber 筛选候选行 */
export function findTraceMatchCandidateLines(
  lines: TraceBatchScanLine[],
  itemId: string,
  lotNumber: string,
): TraceBatchScanLine[] {
  const wantItem = normItemId(itemId);
  const wantLot = normLotNumber(lotNumber);
  if (!wantItem || !wantLot) return [];
  return lines.filter((line) => {
    return normItemId(line.itemId) === wantItem && normLotNumber(line.lotNumber) === wantLot;
  });
}

/** 在候选行中挑选绑定目标：优先未配满的行 */
export function pickTraceBindTargetLine(
  candidates: TraceBatchScanLine[],
  traceNo: string,
  bindMode: TraceScanBindMode,
  effectiveTraceByLineId: Record<string, string>,
): TraceBatchScanLine | undefined {
  if (candidates.length === 0) return undefined;

  const resolveTrace = (line: TraceBatchScanLine): string =>
    String(effectiveTraceByLineId[line.id] ?? line.traceNo ?? '').trim();

  const scoreLine = (line: TraceBatchScanLine): number => {
    const cur = resolveTrace(line);
    if (!canAcceptMoreTraceOnLine(line, cur, bindMode)) return 99;
    if (bindMode === 'single') {
      return isTraceSlotEmpty(cur) ? 0 : 1;
    }
    return traceNoCommaContains(cur, traceNo) ? 2 : isTraceSlotEmpty(cur) ? 0 : 1;
  };

  const sorted = [...candidates].sort((a, b) => scoreLine(a) - scoreLine(b));
  const best = sorted[0];
  if (!best) return undefined;

  const cur = resolveTrace(best);
  if (!canAcceptMoreTraceOnLine(best, cur, bindMode)) return undefined;
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
): TraceBatchScanLine | undefined {
  for (const line of lines) {
    const cur = String(effectiveTraceByLineId[line.id] ?? line.traceNo ?? '').trim();
    if (!canAcceptMoreTraceOnLine(line, cur, bindMode)) continue;
    if (bindMode === 'single' && isTraceSlotEmpty(cur)) return line;
    if (bindMode === 'comma') return line;
  }
  return undefined;
}
