/** Opensource stub. */
export function assignMsfxTraceCodesToRows<T extends { traceNo?: string }>(
  rows: T[],
  _codes: string[],
): { rows: T[]; unassignedCount: number } {
  return { rows, unassignedCount: 0 };
}
