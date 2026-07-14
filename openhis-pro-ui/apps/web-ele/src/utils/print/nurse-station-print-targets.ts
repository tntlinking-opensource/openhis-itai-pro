import type { NurseAdviceExecuteRow } from '#/views/outpatient/nurse-station/components/types';

import {
  INHALATION_METHOD_CODES,
  INFUSION_METHOD_CODES,
  INJECTION_METHOD_CODES,
  normalizeMedicationMethodCode,
  ORAL_METHOD_CODES,
} from './constants/medication-method-codes';
import {
  isNurseExecuteRowCompleted,
  type NurseExecuteDisplayStatusCode,
} from '#/utils/nurse-station/nurse-execute-status';
import {
  SERVICE_CATEGORY_PROOF,
  SERVICE_CATEGORY_TEST,
} from './constants/service-request-category-codes';

export interface SpecimenBarcodePrintTarget {
  serviceRequestId: string;
}

export interface TreatmentExecutePrintTarget {
  serviceRequestIds: string[];
}

export interface MedicationLabelPrintGroup {
  groupId?: string;
  medicationRequestIds: string[];
}

/** 判断执行行是否为检验/采血类诊疗（serviceCategoryCode=22） */
export function isLabSpecimenExecuteRow(row: NurseAdviceExecuteRow): boolean {
  if (row.rowType !== 'SERVICE' || !row.key.startsWith('SR:')) {
    return false;
  }
  return (row.serviceCategoryCode ?? '').trim() === SERVICE_CATEGORY_PROOF;
}

function isServiceExecuteRow(row: NurseAdviceExecuteRow): boolean {
  return row.rowType === 'SERVICE' && Boolean(row.requestId) && row.key.startsWith('SR:');
}

function isMedicationExecuteRowKey(row: NurseAdviceExecuteRow): boolean {
  return (
    row.rowType === 'MEDICATION' &&
    Boolean(row.requestId) &&
    (row.key.startsWith('MR:') || row.key.startsWith('merged-med-'))
  );
}

function isExecuteRowPending(row: NurseAdviceExecuteRow): boolean {
  const code = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase() as NurseExecuteDisplayStatusCode;
  if (!code) {
    return true;
  }
  if (code === 'REFUNDED' || code === 'REFUNDING') {
    return false;
  }
  return !isNurseExecuteRowCompleted(code);
}

function filterServiceExecuteRows(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): NurseAdviceExecuteRow[] {
  const serviceRows = rows.filter(isServiceExecuteRow);
  if (selectedKeys && selectedKeys.length > 0) {
    return serviceRows.filter((row) => selectedKeys.includes(row.key));
  }
  return serviceRows.filter(isExecuteRowPending);
}

/** 采血条码：每个检验医嘱一张 roll 标签 */
export function groupSpecimenBarcodePrintTargets(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): SpecimenBarcodePrintTarget[] {
  const keyed = filterServiceExecuteRows(rows, selectedKeys).filter(isLabSpecimenExecuteRow);
  const ids: string[] = [];
  for (const row of keyed) {
    const requestId = String(row.requestId).trim();
    if (requestId && !ids.includes(requestId)) {
      ids.push(requestId);
    }
  }
  return ids.map((serviceRequestId) => ({ serviceRequestId }));
}

/** 处置执行单：一张 A4 汇总所选（或全部待执行）非检验处置行 */
export function groupTreatmentExecutePrintTargets(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): TreatmentExecutePrintTarget | null {
  const keyed = filterServiceExecuteRows(rows, selectedKeys).filter(
    (row) => !isLabSpecimenExecuteRow(row),
  );
  const serviceRequestIds: string[] = [];
  for (const row of keyed) {
    const requestId = String(row.requestId).trim();
    if (requestId && !serviceRequestIds.includes(requestId)) {
      serviceRequestIds.push(requestId);
    }
  }
  if (!serviceRequestIds.length) {
    return null;
  }
  return { serviceRequestIds };
}

/** 成组合并行展开为成员行，便于收集全部 medicationRequestId */
function expandMedicationExecuteRows(rows: NurseAdviceExecuteRow[]): NurseAdviceExecuteRow[] {
  const expanded: NurseAdviceExecuteRow[] = [];
  for (const row of rows) {
    const members = row.mergedMedicationMembers;
    if (members && members.length > 0) {
      expanded.push(...members);
      continue;
    }
    expanded.push(row);
  }
  return expanded;
}

function filterMedicationExecuteRows(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): NurseAdviceExecuteRow[] {
  const medicationRows = rows.filter(isMedicationExecuteRowKey);
  if (selectedKeys && selectedKeys.length > 0) {
    return medicationRows.filter((row) => selectedKeys.includes(row.key));
  }
  return medicationRows.filter(isExecuteRowPending);
}

function isSkinTestMedicationRow(row: NurseAdviceExecuteRow): boolean {
  return row.skinTestFlag === true;
}

function isNebulizationMedicationRow(row: NurseAdviceExecuteRow): boolean {
  return INHALATION_METHOD_CODES.has(normalizeMedicationMethodCode(row.methodCode));
}

function isInjectionMedicationRow(row: NurseAdviceExecuteRow): boolean {
  if (row.infusionFlag === true) {
    return false;
  }
  const methodCode = normalizeMedicationMethodCode(row.methodCode);
  if (INFUSION_METHOD_CODES.has(methodCode)) {
    return false;
  }
  return INJECTION_METHOD_CODES.has(methodCode);
}

function isOralMedicationRow(row: NurseAdviceExecuteRow): boolean {
  return ORAL_METHOD_CODES.has(normalizeMedicationMethodCode(row.methodCode));
}

/** 药品标签分组（按输液组 + labelScope 过滤） */
export function groupMedicationLabelPrintTargets(
  rows: NurseAdviceExecuteRow[],
  selectedKeys: string[] | undefined,
  scope: 'injection' | 'nebulization' | 'oral' | 'skin_test',
): MedicationLabelPrintGroup[] {
  const medicationRows = expandMedicationExecuteRows(rows.filter(isMedicationExecuteRowKey));
  const keyed =
    selectedKeys && selectedKeys.length > 0
      ? medicationRows.filter((row) => selectedKeys.includes(row.key))
      : medicationRows.filter(isExecuteRowPending);
  const filtered = keyed.filter((row) => {
    if (scope === 'skin_test') {
      return isSkinTestMedicationRow(row);
    }
    if (scope === 'nebulization') {
      return isNebulizationMedicationRow(row);
    }
    if (scope === 'injection') {
      return isInjectionMedicationRow(row);
    }
    return isOralMedicationRow(row);
  });

  const map = new Map<string, MedicationLabelPrintGroup>();
  for (const row of filtered) {
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

/** 检查类处置行（serviceCategoryCode=23） */
export function isExamExecuteRow(row: NurseAdviceExecuteRow): boolean {
  if (row.rowType !== 'SERVICE' || !row.key.startsWith('SR:')) {
    return false;
  }
  return (row.serviceCategoryCode ?? '').trim() === SERVICE_CATEGORY_TEST;
}

/** 治疗/处置申请单（非检验/非检查处置） */
export function groupProcedureApplyPrintTargets(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): TreatmentExecutePrintTarget | null {
  const keyed = filterServiceExecuteRows(rows, selectedKeys).filter(
    (row) => !isLabSpecimenExecuteRow(row) && !isExamExecuteRow(row),
  );
  const serviceRequestIds: string[] = [];
  for (const row of keyed) {
    const requestId = String(row.requestId).trim();
    if (requestId && !serviceRequestIds.includes(requestId)) {
      serviceRequestIds.push(requestId);
    }
  }
  if (!serviceRequestIds.length) {
    return null;
  }
  return { serviceRequestIds };
}

/** 护士站处方打印：从执行行解析药品申请主键 */
export function groupMedicationExecuteRequestIds(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): string[] {
  const ids: string[] = [];
  for (const row of expandMedicationExecuteRows(filterMedicationExecuteRows(rows, selectedKeys))) {
    const requestId = String(row.requestId).trim();
    if (requestId && !ids.includes(requestId)) {
      ids.push(requestId);
    }
  }
  return ids;
}
