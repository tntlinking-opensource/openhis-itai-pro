import type { NurseAdviceExecuteRow } from '#/views/outpatient/nurse-station/components/types';

export interface SpecimenBarcodePrintTarget {
  serviceRequestId: string;
}

export interface TreatmentExecutePrintTarget {
  serviceRequestIds: string[];
}

const LAB_CATEGORY_CODES = new Set(['1', '22']);

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

/** 判断执行行是否为检验/采血类诊疗 */
export function isLabSpecimenExecuteRow(row: NurseAdviceExecuteRow): boolean {
  if (row.type !== '处置' || !row.key.startsWith('SR:')) {
    return false;
  }
  const categoryCode = (row.serviceCategoryCode ?? '').trim();
  if (LAB_CATEGORY_CODES.has(categoryCode)) {
    return true;
  }
  const categoryText = (row.serviceCategoryCodeText ?? '').trim();
  if (containsLabKeyword(categoryText)) {
    return true;
  }
  const content = (row.content ?? '').trim();
  if (containsLabKeyword(content)) {
    return true;
  }
  const name = (row.name ?? '').trim();
  return containsLabKeyword(name);
}

function filterServiceExecuteRows(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): NurseAdviceExecuteRow[] {
  const serviceRows = rows.filter(
    (row) => row.type === '处置' && row.requestId && row.key.startsWith('SR:'),
  );
  if (selectedKeys && selectedKeys.length > 0) {
    return serviceRows.filter((row) => selectedKeys.includes(row.key));
  }
  return serviceRows.filter(
    (row) => row.status !== '已完成' && row.status !== '已退费' && row.status !== '待退费',
  );
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

function filterMedicationExecuteRows(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): NurseAdviceExecuteRow[] {
  const medicationRows = rows.filter(
    (row) => row.type === '药品' && row.requestId && row.key.startsWith('MR:'),
  );
  if (selectedKeys && selectedKeys.length > 0) {
    return medicationRows.filter((row) => selectedKeys.includes(row.key));
  }
  return medicationRows.filter(
    (row) => row.status !== '已完成' && row.status !== '已退费' && row.status !== '待退费',
  );
}

function medicationRouteText(row: NurseAdviceExecuteRow): string {
  return `${row.content ?? ''} ${row.extension ?? ''}`.toLowerCase();
}

function isSkinTestMedicationRow(row: NurseAdviceExecuteRow): boolean {
  return medicationRouteText(row).includes('皮试');
}

function isNebulizationMedicationRow(row: NurseAdviceExecuteRow): boolean {
  const text = medicationRouteText(row);
  return text.includes('雾化') || text.includes('吸入');
}

/** 药品标签分组（按输液组 + labelScope 过滤） */
export function groupMedicationLabelPrintTargets(
  rows: NurseAdviceExecuteRow[],
  selectedKeys: string[] | undefined,
  scope: 'injection' | 'nebulization' | 'oral' | 'skin_test',
): import('#/utils/report/iv-infusion-label-print').InfusionLabelPrintGroup[] {
  const medicationRows = rows.filter(
    (row) => row.type === '药品' && row.requestId && row.key.startsWith('MR:'),
  );
  const keyed =
    selectedKeys && selectedKeys.length > 0
      ? medicationRows.filter((row) => selectedKeys.includes(row.key))
      : medicationRows.filter(
          (row) => row.status !== '已完成' && row.status !== '已退费' && row.status !== '待退费',
        );
  const filtered = keyed.filter((row) => {
    if (scope === 'skin_test') {
      return isSkinTestMedicationRow(row);
    }
    if (scope === 'nebulization') {
      return isNebulizationMedicationRow(row);
    }
    const text = medicationRouteText(row);
    const isInfusion = text.includes('静滴') || text.includes('输液') || text.includes('静脉滴');
    const isInjection =
      !isInfusion && (text.includes('注射') || text.includes('肌注') || text.includes('皮下'));
    const isOral = text.includes('口服') || text.includes('po') || text.includes('顿服');
    if (scope === 'injection') {
      return isInjection;
    }
    return isOral;
  });

  const map = new Map<
    string,
    import('#/utils/report/iv-infusion-label-print').InfusionLabelPrintGroup
  >();
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

/** 检查类处置行 */
export function isExamExecuteRow(row: NurseAdviceExecuteRow): boolean {
  if (row.type !== '处置' || !row.key.startsWith('SR:')) {
    return false;
  }
  const categoryText = (row.serviceCategoryCodeText ?? '').trim();
  if (containsExamKeyword(categoryText)) {
    return true;
  }
  const content = (row.content ?? '').trim();
  if (containsExamKeyword(content)) {
    return true;
  }
  const name = (row.name ?? '').trim();
  return containsExamKeyword(name);
}

/** 护士站处方打印：从执行行解析药品申请主键 */
export function groupMedicationExecuteRequestIds(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): string[] {
  const ids: string[] = [];
  for (const row of filterMedicationExecuteRows(rows, selectedKeys)) {
    const requestId = String(row.requestId).trim();
    if (requestId && !ids.includes(requestId)) {
      ids.push(requestId);
    }
  }
  return ids;
}
