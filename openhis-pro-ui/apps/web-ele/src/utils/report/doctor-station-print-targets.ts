import type { TreatmentExecutePrintTarget } from '#/utils/report/nurse-station-print-targets';
import type { AdviceRowItem } from '#/views/outpatient/doctor-station/advice/services/advice.types';
import { rowLooksDraft } from '#/views/outpatient/doctor-station/advice/services/advice-row';
import { flattenAdviceRowsForResourceOps } from '#/views/outpatient/doctor-station/advice/services/advice-list';

function containsLabKeyword(text: string): boolean {
  const value = text.trim().toLowerCase();
  if (!value) {
    return false;
  }
  return (
    value.includes('采血') ||
    value.includes('抽血') ||
    value.includes('检验') ||
    value.includes('化验') ||
    value.includes('lab')
  );
}

function containsExamKeyword(text: string): boolean {
  const value = text.trim().toLowerCase();
  if (!value) {
    return false;
  }
  return (
    value.includes('检查') ||
    value.includes('超声') ||
    value.includes('ct') ||
    value.includes('mri') ||
    value.includes('影像') ||
    value.includes('dr') ||
    value.includes('x线') ||
    value.includes('x-ray')
  );
}

function isServiceAdviceRow(row: AdviceRowItem): boolean {
  const resourceType = String(row.resourceType ?? '')
    .trim()
    .toUpperCase();
  const requestId = String(row.requestId ?? '').trim();
  return resourceType === 'SERVICE' && requestId.length > 0;
}

function isPrintableAdviceRow(row: AdviceRowItem): boolean {
  if (row.isEditor || rowLooksDraft(row)) {
    return false;
  }
  const blob = `${row.statusEnum ?? ''} ${row.status ?? ''}`.toUpperCase();
  return !/退费|REFUND/.test(blob);
}

function rowCategoryText(row: AdviceRowItem): string {
  return `${row.type ?? ''} ${row.content ?? ''} ${row.adviceDisplayName ?? ''}`;
}

function isLabServiceAdviceRow(row: AdviceRowItem): boolean {
  if (!isServiceAdviceRow(row)) {
    return false;
  }
  return containsLabKeyword(rowCategoryText(row));
}

function isExamServiceAdviceRow(row: AdviceRowItem): boolean {
  if (!isServiceAdviceRow(row) || isLabServiceAdviceRow(row)) {
    return false;
  }
  return containsExamKeyword(rowCategoryText(row));
}

function filterAdviceRowsForApplyPrint(
  rows: AdviceRowItem[],
  selectedKeys?: string[],
): AdviceRowItem[] {
  const flattened = flattenAdviceRowsForResourceOps(rows).filter(isPrintableAdviceRow);
  if (selectedKeys && selectedKeys.length > 0) {
    const keySet = new Set(selectedKeys);
    return flattened.filter((row) => keySet.has(row.key));
  }
  return flattened;
}

function collectServiceRequestIds(
  rows: AdviceRowItem[],
  predicate: (row: AdviceRowItem) => boolean,
): TreatmentExecutePrintTarget | null {
  const serviceRequestIds: string[] = [];
  for (const row of rows) {
    if (!predicate(row)) {
      continue;
    }
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

/** 门诊医生站：检验申请单 */
export function groupDoctorStationLabApplyPrintTarget(
  rows: AdviceRowItem[],
  selectedKeys?: string[],
): TreatmentExecutePrintTarget | null {
  const keyed = filterAdviceRowsForApplyPrint(rows, selectedKeys).filter(isLabServiceAdviceRow);
  return collectServiceRequestIds(keyed, () => true);
}

/** 门诊医生站：检查申请单 */
export function groupDoctorStationExamApplyPrintTarget(
  rows: AdviceRowItem[],
  selectedKeys?: string[],
): TreatmentExecutePrintTarget | null {
  const keyed = filterAdviceRowsForApplyPrint(rows, selectedKeys).filter(isExamServiceAdviceRow);
  return collectServiceRequestIds(keyed, () => true);
}
