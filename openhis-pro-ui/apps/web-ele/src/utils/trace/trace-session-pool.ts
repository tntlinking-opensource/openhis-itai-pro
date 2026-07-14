import type { TraceBatchScanLine } from './trace-types';

import { isTraceSlotEmpty } from './trace-code-parse';
import { splitTraceNoCommaString } from './trace-no-string';

/** 从弹窗当前绑定状态汇总已占用追溯码 */
export function collectBoundTraceCodes(
  lines: TraceBatchScanLine[],
  traceByLineId: Record<string, string>,
): Set<string> {
  const pool = new Set<string>();
  for (const line of lines) {
    const raw = (traceByLineId[line.id] ?? line.traceNo ?? '').trim();
    if (isTraceSlotEmpty(raw)) continue;
    for (const code of splitTraceNoCommaString(raw)) {
      pool.add(code);
    }
  }
  return pool;
}

/** 输入码去重并排除会话已绑定码（重复码静默跳过） */
export function filterNewTraceCodes(codes: string[], boundPool: Set<string>): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const code of codes) {
    const trimmed = code.trim();
    if (!trimmed || seen.has(trimmed) || boundPool.has(trimmed)) continue;
    seen.add(trimmed);
    result.push(trimmed);
  }
  return result;
}
