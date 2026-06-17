import type { InfusionLabelPrintGroup } from '#/utils/report/iv-infusion-label-print';
import type {
  SpecimenBarcodePrintTarget,
  TreatmentExecutePrintTarget,
} from '#/utils/report/nurse-station-print-targets';
import type { NurseStationOrderExecuteTableRow } from '#/views/inpatient/nurse-station/order-execute/services/map-nurse-station-order-execute';

const LAB_CATEGORY_CODES = new Set(['1', '22']);
const EXAM_CATEGORY_CODES = new Set(['2', '11']);

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

function isLabServiceOrder(row: NurseStationOrderExecuteTableRow): boolean {
  if (row.orderType !== 'SERVICE_REQUEST') {
    return false;
  }
  const categoryCode = (row.categoryEnum ?? '').trim();
  if (LAB_CATEGORY_CODES.has(categoryCode)) {
    return true;
  }
  return containsLabKeyword(row.content);
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

function isExamServiceOrder(row: NurseStationOrderExecuteTableRow): boolean {
  if (row.orderType !== 'SERVICE_REQUEST') {
    return false;
  }
  if (isLabServiceOrder(row)) {
    return false;
  }
  const categoryCode = (row.categoryEnum ?? '').trim();
  if (EXAM_CATEGORY_CODES.has(categoryCode)) {
    return true;
  }
  return containsExamKeyword(row.content);
}

function collectServiceRequestIds(
  rows: NurseStationOrderExecuteTableRow[],
  predicate: (row: NurseStationOrderExecuteTableRow) => boolean,
): TreatmentExecutePrintTarget | null {
  const serviceRequestIds: string[] = [];
  for (const row of rows) {
    if (!predicate(row)) {
      continue;
    }
    const requestId = String(row.orderId).trim();
    if (requestId && !serviceRequestIds.includes(requestId)) {
      serviceRequestIds.push(requestId);
    }
  }
  if (!serviceRequestIds.length) {
    return null;
  }
  return { serviceRequestIds };
}

function filterOrdersBySlotSelection(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys?: Set<string>,
): NurseStationOrderExecuteTableRow[] {
  if (!selectedSlotKeys || selectedSlotKeys.size === 0) {
    return rows;
  }
  return rows.filter((row) =>
    row.planSchedules.some((schedule) =>
      schedule.slots.some((slot) => selectedSlotKeys.has(slot.slotKey)),
    ),
  );
}

function filterMedicationOrders(
  rows: NurseStationOrderExecuteTableRow[],
): NurseStationOrderExecuteTableRow[] {
  return rows.filter((row) => row.orderType === 'MED_REQUEST' && row.orderId);
}

function filterServiceOrders(
  rows: NurseStationOrderExecuteTableRow[],
): NurseStationOrderExecuteTableRow[] {
  return rows.filter((row) => row.orderType === 'SERVICE_REQUEST' && row.orderId);
}

/** 住院医嘱执行：按用药途径分组打印标签 */
export function groupInpatientMedicationLabelPrintTargets(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys: Set<string> | undefined,
  scope: 'infusion' | 'injection' | 'nebulization' | 'oral' | 'skin_test',
): InfusionLabelPrintGroup[] {
  const keyed = filterMedicationOrders(filterOrdersBySlotSelection(rows, selectedSlotKeys)).filter(
    (row) => matchesMedicationLabelScope(row, scope),
  );
  const map = new Map<string, InfusionLabelPrintGroup>();
  for (const row of keyed) {
    const requestId = String(row.orderId).trim();
    if (!requestId) {
      continue;
    }
    const groupKey = row.groupNo != null && row.groupNo > 0 ? `g:${row.groupNo}` : `r:${requestId}`;
    const existing = map.get(groupKey) ?? {
      groupId: row.groupNo != null && row.groupNo > 0 ? String(row.groupNo) : undefined,
      medicationRequestIds: [],
    };
    if (!existing.medicationRequestIds.includes(requestId)) {
      existing.medicationRequestIds.push(requestId);
    }
    map.set(groupKey, existing);
  }
  return [...map.values()];
}

function matchesMedicationLabelScope(
  row: NurseStationOrderExecuteTableRow,
  scope: 'infusion' | 'injection' | 'nebulization' | 'oral' | 'skin_test',
): boolean {
  const text = `${row.content} ${row.extension ?? ''} ${row.dosage}`.toLowerCase();
  const isSkinTest = text.includes('皮试');
  const isNebulization = text.includes('雾化') || text.includes('吸入');
  const isInfusion = text.includes('静滴') || text.includes('输液') || text.includes('静脉滴');
  const isInjection =
    !isInfusion && (text.includes('注射') || text.includes('肌注') || text.includes('皮下'));
  const isOral = text.includes('口服') || text.includes('po') || text.includes('顿服');
  if (scope === 'skin_test') {
    return isSkinTest;
  }
  if (scope === 'nebulization') {
    return isNebulization;
  }
  if (scope === 'infusion') {
    return isInfusion;
  }
  if (scope === 'injection') {
    return isInjection;
  }
  return isOral;
}

/** 住院医嘱执行：输液瓶贴分组（兼容：未限定途径时包含全部药嘱组） */
export function groupInpatientInfusionLabelPrintTargets(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys?: Set<string>,
): InfusionLabelPrintGroup[] {
  const keyed = filterMedicationOrders(filterOrdersBySlotSelection(rows, selectedSlotKeys));
  const map = new Map<string, InfusionLabelPrintGroup>();
  for (const row of keyed) {
    const requestId = String(row.orderId).trim();
    if (!requestId) {
      continue;
    }
    const groupKey = row.groupNo != null && row.groupNo > 0 ? `g:${row.groupNo}` : `r:${requestId}`;
    const existing = map.get(groupKey) ?? {
      groupId: row.groupNo != null && row.groupNo > 0 ? String(row.groupNo) : undefined,
      medicationRequestIds: [],
    };
    if (!existing.medicationRequestIds.includes(requestId)) {
      existing.medicationRequestIds.push(requestId);
    }
    map.set(groupKey, existing);
  }
  return [...map.values()];
}

/** 住院医嘱执行：采血条码 */
export function groupInpatientSpecimenBarcodePrintTargets(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys?: Set<string>,
): SpecimenBarcodePrintTarget[] {
  const keyed = filterServiceOrders(filterOrdersBySlotSelection(rows, selectedSlotKeys)).filter(
    isLabServiceOrder,
  );
  const ids: string[] = [];
  for (const row of keyed) {
    const requestId = String(row.orderId).trim();
    if (requestId && !ids.includes(requestId)) {
      ids.push(requestId);
    }
  }
  return ids.map((serviceRequestId) => ({ serviceRequestId }));
}

/** 住院医嘱执行：处置执行单 */
export function groupInpatientTreatmentExecutePrintTargets(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys?: Set<string>,
): TreatmentExecutePrintTarget | null {
  const keyed = filterServiceOrders(filterOrdersBySlotSelection(rows, selectedSlotKeys)).filter(
    (row) => !isLabServiceOrder(row),
  );
  const serviceRequestIds: string[] = [];
  for (const row of keyed) {
    const requestId = String(row.orderId).trim();
    if (requestId && !serviceRequestIds.includes(requestId)) {
      serviceRequestIds.push(requestId);
    }
  }
  if (!serviceRequestIds.length) {
    return null;
  }
  return { serviceRequestIds };
}

/** 住院医嘱执行：检验申请单 */
export function groupInpatientLabApplyPrintTarget(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys?: Set<string>,
): TreatmentExecutePrintTarget | null {
  const keyed = filterServiceOrders(filterOrdersBySlotSelection(rows, selectedSlotKeys)).filter(
    isLabServiceOrder,
  );
  return collectServiceRequestIds(keyed, () => true);
}

/** 住院医嘱执行：治疗/处置申请单（非检验/非检查） */
export function groupInpatientProcedureApplyPrintTarget(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys?: Set<string>,
): TreatmentExecutePrintTarget | null {
  const keyed = filterServiceOrders(filterOrdersBySlotSelection(rows, selectedSlotKeys)).filter(
    (row) => !isLabServiceOrder(row) && !isExamServiceOrder(row),
  );
  return collectServiceRequestIds(keyed, () => true);
}

/** 住院医嘱执行：检查申请单 */
export function groupInpatientExamApplyPrintTarget(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys?: Set<string>,
): TreatmentExecutePrintTarget | null {
  const keyed = filterServiceOrders(filterOrdersBySlotSelection(rows, selectedSlotKeys)).filter(
    isExamServiceOrder,
  );
  return collectServiceRequestIds(keyed, () => true);
}

/** 住院医嘱执行：护士处方打印 */
export function groupInpatientMedicationExecuteRequestIds(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys?: Set<string>,
): string[] {
  const ids: string[] = [];
  for (const row of filterMedicationOrders(filterOrdersBySlotSelection(rows, selectedSlotKeys))) {
    const requestId = String(row.orderId).trim();
    if (requestId && !ids.includes(requestId)) {
      ids.push(requestId);
    }
  }
  return ids;
}

/** 住院医嘱执行单：按勾选槽位收窄 listRowKeys（未勾选则打印当前列表全部行） */
export function groupInpatientOrderExecuteSheetListRowKeys(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys?: Set<string>,
): string[] | undefined {
  const filtered = filterOrdersBySlotSelection(rows, selectedSlotKeys);
  if (!filtered.length) {
    return undefined;
  }
  return filtered.map((row) => row.id);
}
