import type { InfusionLabelPrintGroup } from '#/utils/report/iv-infusion-label-print';
import type {
  SpecimenBarcodePrintTarget,
  TreatmentExecutePrintTarget,
} from '#/utils/report/nurse-station-print-targets';
import type { NurseStationOrderAuditTableRow } from '#/views/inpatient/nurse-station/order-audit/services/map-nurse-station-order-audit';

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

function isLabServiceOrder(row: NurseStationOrderAuditTableRow): boolean {
  if (row.orderType !== 'SERVICE_REQUEST') {
    return false;
  }
  return containsLabKeyword(row.content);
}

function filterSelectedAuditRows(
  rows: NurseStationOrderAuditTableRow[],
  selectedRowKeys?: Set<string>,
): NurseStationOrderAuditTableRow[] {
  if (!selectedRowKeys || selectedRowKeys.size === 0) {
    return rows;
  }
  return rows.filter((row) => selectedRowKeys.has(row.id));
}

function filterMedicationOrders(
  rows: NurseStationOrderAuditTableRow[],
): NurseStationOrderAuditTableRow[] {
  return rows.filter((row) => row.orderType === 'MED_REQUEST' && row.requestId);
}

function filterServiceOrders(
  rows: NurseStationOrderAuditTableRow[],
): NurseStationOrderAuditTableRow[] {
  return rows.filter((row) => row.orderType === 'SERVICE_REQUEST' && row.requestId);
}

/** 住院医嘱校对：输液瓶贴分组（按 requestId，无组号时逐条） */
export function groupInpatientAuditInfusionLabelPrintTargets(
  rows: NurseStationOrderAuditTableRow[],
  selectedRowKeys?: Set<string>,
): InfusionLabelPrintGroup[] {
  const keyed = filterMedicationOrders(filterSelectedAuditRows(rows, selectedRowKeys));
  const map = new Map<string, InfusionLabelPrintGroup>();
  for (const row of keyed) {
    const requestId = String(row.requestId).trim();
    if (!requestId) {
      continue;
    }
    const groupKey = `r:${requestId}`;
    const existing = map.get(groupKey) ?? { medicationRequestIds: [] };
    if (!existing.medicationRequestIds.includes(requestId)) {
      existing.medicationRequestIds.push(requestId);
    }
    map.set(groupKey, existing);
  }
  return [...map.values()];
}

/** 住院医嘱校对：采血条码 */
export function groupInpatientAuditSpecimenBarcodePrintTargets(
  rows: NurseStationOrderAuditTableRow[],
  selectedRowKeys?: Set<string>,
): SpecimenBarcodePrintTarget[] {
  const keyed = filterServiceOrders(filterSelectedAuditRows(rows, selectedRowKeys)).filter(
    isLabServiceOrder,
  );
  const ids: string[] = [];
  for (const row of keyed) {
    const requestId = String(row.requestId).trim();
    if (requestId && !ids.includes(requestId)) {
      ids.push(requestId);
    }
  }
  return ids.map((serviceRequestId) => ({ serviceRequestId }));
}

/** 住院医嘱校对：处置执行单 */
export function groupInpatientAuditTreatmentExecutePrintTargets(
  rows: NurseStationOrderAuditTableRow[],
  selectedRowKeys?: Set<string>,
): TreatmentExecutePrintTarget | null {
  const keyed = filterServiceOrders(filterSelectedAuditRows(rows, selectedRowKeys)).filter(
    (row) => !isLabServiceOrder(row),
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

/** 住院医嘱校对：护士处方打印 */
export function groupInpatientAuditMedicationRequestIds(
  rows: NurseStationOrderAuditTableRow[],
  selectedRowKeys?: Set<string>,
): string[] {
  const ids: string[] = [];
  for (const row of filterMedicationOrders(filterSelectedAuditRows(rows, selectedRowKeys))) {
    const requestId = String(row.requestId).trim();
    if (requestId && !ids.includes(requestId)) {
      ids.push(requestId);
    }
  }
  return ids;
}
