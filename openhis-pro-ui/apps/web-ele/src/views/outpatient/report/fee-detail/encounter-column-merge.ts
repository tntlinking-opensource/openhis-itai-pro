import type { FeeDetailTableRow } from './flatten-encounter-blocks';

/** 同一就诊块内需纵向合并的列（顺序与表格就诊头列一致：门诊号 → 患者 → 科室 → 日期）。 */
export const FEE_DETAIL_ENCOUNTER_MERGE_FIELDS = [
  'busNo',
  'patientName',
  'registrationDepartmentName',
  'encounterDate',
] as const;

export type FeeDetailEncounterMergeField = (typeof FEE_DETAIL_ENCOUNTER_MERGE_FIELDS)[number];

const encounterMergeFieldSet = new Set<string>(FEE_DETAIL_ENCOUNTER_MERGE_FIELDS);

export interface FeeDetailSpanMethodParams {
  row: FeeDetailTableRow;
  column: { field?: string; property?: string };
}

/** ElTable / VxeGrid spanMethod：明细行按就诊块合并头列，小计行不参与合并。 */
export function resolveFeeDetailEncounterSpanMethod({
  row,
  column,
}: FeeDetailSpanMethodParams): undefined | { colspan: number; rowspan: number } {
  const field = column.property ?? column.field;
  if (!field || !encounterMergeFieldSet.has(field)) {
    return undefined;
  }
  if (row.rowType === 'subtotal') {
    return undefined;
  }
  if (row.isFirstLineInEncounter) {
    return { rowspan: row.encounterLineCount ?? 1, colspan: 1 };
  }
  return { rowspan: 0, colspan: 0 };
}
