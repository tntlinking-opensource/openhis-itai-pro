import type { TraceBatchScanLine, TraceScanBindMode } from './trace-types';

import { isTraceSlotEmpty } from './trace-code-parse';
import { splitTraceNoCommaString } from './trace-no-string';

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

/** 行剩余待扫数量；无法计算时返回 null */
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

/** 行是否还能再绑一个追溯码（无应扫总数时不限制） */
export function canAcceptMoreTraceOnLine(
  row: TraceBatchScanLine,
  trace: string | undefined,
  bindMode: TraceScanBindMode,
): boolean {
  const pending = countPendingTraceScan(row, trace, bindMode);
  if (pending === null) return true;
  return pending > 0;
}

/** 行追溯码已满时的提示文案 */
export function traceLineScanFullMessage(
  row: TraceBatchScanLine,
  bindMode: TraceScanBindMode,
): string {
  const required = resolveRequiredTraceCount(row, bindMode);
  if (required !== null) {
    return `追溯码数量不能超过药品数量（${required}）`;
  }
  return '该行已扫满，不能再录入追溯码';
}
