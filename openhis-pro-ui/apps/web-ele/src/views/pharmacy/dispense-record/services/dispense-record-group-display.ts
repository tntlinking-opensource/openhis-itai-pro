/** 发退药记录列表展示模式：平铺 / 按就诊 / 按处方号合并列。 */
export type DispenseRecordDisplayMode = 'BY_ENCOUNTER' | 'BY_PRESCRIPTION' | 'FLAT';

export type DispenseRecordScope = 'inpatient' | 'outpatient';

/** 退药负向处方号前缀（与后端 markAsRefundFromPrescription 一致）。 */
export const REFUND_PRESCRIPTION_PREFIX = 'T';

export interface DispenseRecordSourceRow {
  dispenseId?: string;
  encounterId?: string;
  prescriptionNo?: string;
  recordTypeCode?: string;
  dispenseTime?: string;
  [key: string]: unknown;
}

export type DispenseRecordTableRow = DispenseRecordSourceRow & {
  displayGroupKey?: string;
  displayGroupLineCount?: number;
  isFirstLineInDisplayGroup?: boolean;
  rowKey: string;
};

/** 按处方合并时纵向合并的列（与表格列顺序一致：处方/患者/就诊 → 发药操作）。 */
export const DISPENSE_RECORD_PRESCRIPTION_MERGE_FIELDS = [
  'prescriptionNo',
  'recordTypeText',
  'patientName',
  'encounterBusNo',
  'inpatientNo',
  'wardName',
  'bedName',
  'orderingDepartmentName',
  'dispenseTime',
  'pharmacyLocationName',
  'dispenserName',
] as const;

/** 按就诊合并时纵向合并的列（处方号保留逐行展示）。 */
export const DISPENSE_RECORD_ENCOUNTER_MERGE_FIELDS = [
  'patientName',
  'encounterBusNo',
  'inpatientNo',
  'wardName',
  'bedName',
] as const;

const prescriptionMergeFieldSet = new Set<string>(DISPENSE_RECORD_PRESCRIPTION_MERGE_FIELDS);
const encounterMergeFieldSet = new Set<string>(DISPENSE_RECORD_ENCOUNTER_MERGE_FIELDS);

export interface DispenseRecordSpanMethodParams {
  column: { field?: string };
  displayMode: DispenseRecordDisplayMode;
  row: DispenseRecordTableRow;
}

interface DispenseRecordRowGroup {
  groupKey: string;
  rows: DispenseRecordSourceRow[];
  recordTypeCode: string;
  clinicalRxKey: string;
  encounterKey: string;
  maxDispenseTimeMs: number;
}

/** 归一化临床处方号：退药 TCP… → CP…，用于一发一退相邻排序。 */
export function normalizeClinicalPrescriptionNo(prescriptionNo?: string): string {
  const rx = String(prescriptionNo ?? '').trim();
  if (!rx) {
    return '';
  }
  if (rx.startsWith(REFUND_PRESCRIPTION_PREFIX) && rx.length > REFUND_PRESCRIPTION_PREFIX.length) {
    return rx.slice(REFUND_PRESCRIPTION_PREFIX.length);
  }
  return rx;
}

/** 退药行处方号列：主展示原 CP 号，存库 TCP 号放 Tooltip。 */
export function formatDispenseRecordPrescriptionNoDisplay(row: DispenseRecordSourceRow): {
  text: string;
  tooltip?: string;
} {
  const stored = String(row.prescriptionNo ?? '').trim();
  if (!stored) {
    return { text: '—' };
  }
  if (
    row.recordTypeCode === 'REFUND' &&
    stored.startsWith(REFUND_PRESCRIPTION_PREFIX) &&
    stored.length > REFUND_PRESCRIPTION_PREFIX.length
  ) {
    return {
      text: stored.slice(REFUND_PRESCRIPTION_PREFIX.length),
      tooltip: `退费处方号：${stored}`,
    };
  }
  return { text: stored };
}

function parseDispenseTimeMs(value?: string): number {
  const trimmed = String(value ?? '').trim();
  if (!trimmed) {
    return 0;
  }
  const normalized = trimmed.includes('T') ? trimmed : trimmed.replace(' ', 'T');
  const parsed = Date.parse(normalized);
  return Number.isNaN(parsed) ? 0 : parsed;
}

function resolveRecordTypeCode(row: DispenseRecordSourceRow): string {
  const code = String(row.recordTypeCode ?? '').trim();
  if (code) {
    return code;
  }
  return 'DISPENSE';
}

function resolveEncounterKey(row: DispenseRecordSourceRow): string {
  const encounterId = String(row.encounterId ?? '').trim();
  if (encounterId) {
    return encounterId;
  }
  return `dispense:${String(row.dispenseId ?? '')}`;
}

/** 构建分组 key：处方模式优先 encounter+处方号；无处方号时退化为单次发放行。 */
export function buildDispenseRecordGroupKey(
  row: DispenseRecordSourceRow,
  mode: DispenseRecordDisplayMode,
): string {
  const encounterPart = resolveEncounterKey(row);
  if (mode === 'BY_ENCOUNTER') {
    return `enc:${encounterPart}`;
  }
  if (mode === 'BY_PRESCRIPTION') {
    const rx = String(row.prescriptionNo ?? '').trim();
    if (rx) {
      return `rx:${encounterPart}|${rx}`;
    }
    return `rx-single:${String(row.dispenseId ?? '')}`;
  }
  return `flat:${String(row.dispenseId ?? '')}`;
}

function collectRowGroups(
  rows: DispenseRecordSourceRow[],
  mode: DispenseRecordDisplayMode,
): DispenseRecordRowGroup[] {
  const groupOrder: string[] = [];
  const groupRowsMap = new Map<string, DispenseRecordSourceRow[]>();
  for (const row of rows) {
    const groupKey = buildDispenseRecordGroupKey(row, mode);
    if (!groupRowsMap.has(groupKey)) {
      groupOrder.push(groupKey);
      groupRowsMap.set(groupKey, []);
    }
    groupRowsMap.get(groupKey)!.push(row);
  }
  const groups: DispenseRecordRowGroup[] = [];
  for (const groupKey of groupOrder) {
    const groupRows = groupRowsMap.get(groupKey) ?? [];
    if (!groupRows.length) {
      continue;
    }
    const head = groupRows[0]!;
    const clinicalRx = normalizeClinicalPrescriptionNo(head.prescriptionNo);
    const clinicalRxKey = clinicalRx || `single:${String(head.dispenseId ?? groupKey)}`;
    groups.push({
      groupKey,
      rows: groupRows,
      recordTypeCode: resolveRecordTypeCode(head),
      clinicalRxKey,
      encounterKey: resolveEncounterKey(head),
      maxDispenseTimeMs: Math.max(...groupRows.map((row) => parseDispenseTimeMs(row.dispenseTime))),
    });
  }
  return groups;
}

/**
 * 同一临床处方下发药组与退药组相邻：发药在上、退药在下；集群按最近发/退时间倒序。
 */
function reorderRowGroupsForDispenseRefundPairs(
  groups: DispenseRecordRowGroup[],
): DispenseRecordRowGroup[] {
  if (groups.length <= 1) {
    return groups;
  }
  const clusterOrder: string[] = [];
  const clusterMap = new Map<string, DispenseRecordRowGroup[]>();
  for (const group of groups) {
    const clusterKey = `${group.encounterKey}|${group.clinicalRxKey}`;
    if (!clusterMap.has(clusterKey)) {
      clusterMap.set(clusterKey, []);
      clusterOrder.push(clusterKey);
    }
    clusterMap.get(clusterKey)!.push(group);
  }
  const orderedClusters = clusterOrder.map((clusterKey) => {
    const clusterGroups = clusterMap.get(clusterKey) ?? [];
    clusterGroups.sort((left, right) => {
      const leftRank = left.recordTypeCode === 'REFUND' ? 1 : 0;
      const rightRank = right.recordTypeCode === 'REFUND' ? 1 : 0;
      if (leftRank !== rightRank) {
        return leftRank - rightRank;
      }
      return right.maxDispenseTimeMs - left.maxDispenseTimeMs;
    });
    const clusterMaxTime = Math.max(...clusterGroups.map((item) => item.maxDispenseTimeMs));
    return { clusterKey, clusterGroups, clusterMaxTime };
  });
  orderedClusters.sort((left, right) => right.clusterMaxTime - left.clusterMaxTime);
  return orderedClusters.flatMap((cluster) => cluster.clusterGroups);
}

/** 将接口平铺行按展示模式重排并标注组合并元数据（保持 API 返回的组首次出现顺序）。 */
export function enrichDispenseRecordRowsForDisplay(
  rows: DispenseRecordSourceRow[],
  mode: DispenseRecordDisplayMode,
): DispenseRecordTableRow[] {
  if (!rows.length) {
    return [];
  }
  if (mode === 'FLAT') {
    return rows.map((row) => ({
      ...row,
      rowKey: String(row.dispenseId ?? ''),
    }));
  }
  const groups = collectRowGroups(rows, mode);
  const orderedGroups =
    mode === 'BY_PRESCRIPTION' ? reorderRowGroupsForDispenseRefundPairs(groups) : groups;
  const result: DispenseRecordTableRow[] = [];
  for (const group of orderedGroups) {
    const lineCount = group.rows.length;
    group.rows.forEach((row, lineIndex) => {
      result.push({
        ...row,
        displayGroupKey: group.groupKey,
        displayGroupLineCount: lineCount,
        isFirstLineInDisplayGroup: lineIndex === 0,
        rowKey: String(row.dispenseId ?? ''),
      });
    });
  }
  return result;
}

/** VxeGrid spanMethod：按展示模式合并组头列；平铺模式不合并。 */
export function resolveDispenseRecordSpanMethod({
  row,
  column,
  displayMode,
}: DispenseRecordSpanMethodParams): { colspan: number; rowspan: number } | undefined {
  if (displayMode === 'FLAT') {
    return undefined;
  }
  const field = column.field;
  if (!field) {
    return undefined;
  }
  const mergeSet =
    displayMode === 'BY_PRESCRIPTION' ? prescriptionMergeFieldSet : encounterMergeFieldSet;
  if (!mergeSet.has(field)) {
    return undefined;
  }
  if (row.isFirstLineInDisplayGroup) {
    return { rowspan: row.displayGroupLineCount ?? 1, colspan: 1 };
  }
  return { rowspan: 0, colspan: 0 };
}
