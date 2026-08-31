/**
 * 将上游追溯码写入进货明细行（空码行优先一对一）。
 * 分隔符与 HIS {@code splitInboundTraceCodes} 一致（英文逗号）。
 *
 * <p>多余码不再砸到末行（避免串行）；由调用方提示未分配数量。</p>
 */
export function assignMsfxTraceCodesToRows<T extends { traceNo?: string }>(
  rows: T[],
  codes: string[],
): { rows: T[]; unassignedCount: number } {
  const cleaned: string[] = [];
  const seen = new Set<string>();
  for (const c of codes) {
    const t = String(c ?? '').trim();
    if (!t || seen.has(t)) continue;
    seen.add(t);
    cleaned.push(t);
  }
  if (cleaned.length === 0 || rows.length === 0) {
    return { rows, unassignedCount: cleaned.length };
  }
  const next = rows.map((r) => ({ ...r }));
  let idx = 0;
  for (const row of next) {
    if (idx >= cleaned.length) break;
    const existing = String(row.traceNo ?? '').trim();
    if (existing) continue;
    row.traceNo = cleaned[idx]!;
    idx += 1;
  }
  return { rows: next, unassignedCount: cleaned.length - idx };
}
