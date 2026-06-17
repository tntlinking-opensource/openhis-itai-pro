import type { NurseAdviceExecuteRow } from '#/views/outpatient/nurse-station/components/types';

export interface InfusionLabelPrintGroup {
  groupId?: string;
  medicationRequestIds: string[];
}

/** 将护士站药品执行行按输液组成组，供瓶贴逐组打印 */
export function groupInfusionLabelPrintTargets(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): InfusionLabelPrintGroup[] {
  const medicationRows = rows.filter(
    (row) => row.type === '药品' && row.requestId && row.key.startsWith('MR:'),
  );
  const keyed =
    selectedKeys && selectedKeys.length > 0
      ? medicationRows.filter((row) => selectedKeys.includes(row.key))
      : medicationRows.filter(
          (row) => row.status !== '已完成' && row.status !== '已退费' && row.status !== '待退费',
        );

  const map = new Map<string, InfusionLabelPrintGroup>();
  for (const row of keyed) {
    const requestId = String(row.requestId).trim();
    if (!requestId) {
      continue;
    }
    const groupKey = row.groupId?.trim() ? `g:${row.groupId}` : `r:${requestId}`;
    const existing = map.get(groupKey) ?? {
      groupId: row.groupId?.trim() || undefined,
      medicationRequestIds: [],
    };
    if (!existing.medicationRequestIds.includes(requestId)) {
      existing.medicationRequestIds.push(requestId);
    }
    map.set(groupKey, existing);
  }
  return [...map.values()];
}
