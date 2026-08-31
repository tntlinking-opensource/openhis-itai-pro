import type { AdviceDrugItem, AdviceRowItem } from './advice.types';

import type { DoctorStationAdviceResourceKeyDto } from '#/api/doctor-station/advice.types';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { ADVICE_ROW_PRODUCT_TABLE_FALLBACK, DEVICE_DEFINITION_TABLE } from './advice-tab.constants';

/** 复合 selectedId：`{productTableName}:{definitionId}` */

export function parseProductTableNameFromSelectedId(selectedId: string): string | undefined {
  const s = String(selectedId || '').trim();
  const idx = s.indexOf(':');
  if (idx <= 0) return undefined;
  const tbl = s.slice(0, idx).trim();
  return tbl || undefined;
}

export function parseColonDefinitionId(selectedId: string): string | undefined {
  const s = String(selectedId || '').trim();
  const idx = s.indexOf(':');
  if (idx === -1 || idx >= s.length - 1) return undefined;
  const id = s.slice(idx + 1).trim();
  return id || undefined;
}

export type AdviceLineEditorKind = 'DEVICE' | 'MEDICATION' | 'SERVICE';

export function inferAdviceLineEditorKind(
  item: Pick<AdviceDrugItem, 'productTableName' | 'selectedId'>,
): AdviceLineEditorKind {
  const direct = String(item.productTableName ?? '')
    .trim()
    .toLowerCase();
  if (direct === 'adm_device_definition') return 'DEVICE';
  if (direct === 'wkf_activity_definition') return 'SERVICE';
  if (direct === 'med_medication_definition' || direct.includes('medication_definition')) {
    return 'MEDICATION';
  }
  const fromId = String(parseProductTableNameFromSelectedId(item.selectedId) ?? '')
    .trim()
    .toLowerCase();
  if (fromId === 'adm_device_definition') return 'DEVICE';
  if (fromId === 'wkf_activity_definition') return 'SERVICE';
  return 'MEDICATION';
}

function validateWesternMedicationLineItem(item: AdviceDrugItem): boolean {
  const hasDiagnosis =
    Boolean(String(item.diagnosis ?? '').trim()) || (item.encounterDiagnosisIds?.length ?? 0) > 0;
  return Boolean(
    item.selectedId &&
      hasDiagnosis &&
      item.contractId &&
      item.usage &&
      item.frequency &&
      Number(item.singleDose) > 0 &&
      Number(item.totalAmount) > 0 &&
      String(item.totalAmountUnit ?? '').trim() !== '',
  );
}

export function validateWesternEditLineItem(item: AdviceDrugItem): boolean {
  if (!String(item.selectedId ?? '').trim()) return false;
  const kind = inferAdviceLineEditorKind(item);
  if (kind === 'DEVICE') {
    return (
      Number(item.totalAmount) > 0 &&
      String(item.totalAmountUnit ?? '').trim() !== '' &&
      Boolean(String(item.contractId ?? '').trim())
    );
  }
  if (kind === 'SERVICE') {
    return (
      Number(item.serviceQuantity) > 0 &&
      Number(item.totalAmount) > 0 &&
      String(item.totalAmountUnit ?? '').trim() !== '' &&
      String(item.performOrgId ?? '').trim() !== '' &&
      Boolean(String(item.contractId ?? '').trim())
    );
  }
  return validateWesternMedicationLineItem(item);
}

/** 诊疗绑定带出的耗材行（派生 DeviceRequest，basedOnServiceRequestId 锚定父诊疗） */
export function isAdviceRowBoundConsumableFromService(row: AdviceRowItem): boolean {
  const basedOn = String(row.basedOnServiceRequestId ?? '').trim();
  if (!basedOn) {
    return false;
  }
  const rt = String(row.resourceType ?? '')
    .trim()
    .toUpperCase();
  if (rt === 'DEVICE') {
    return true;
  }
  const tbl = String(row.productTableName ?? '')
    .trim()
    .toLowerCase();
  return tbl === DEVICE_DEFINITION_TABLE || tbl.includes('device');
}

function normalizeAdviceRowRequestId(value: unknown): string {
  const preserved = preserveApiIdForRequest(value);
  if (preserved) {
    return preserved;
  }
  return String(value ?? '').trim();
}

/** 申请单诊疗带出的绑定耗材（父诊疗行带 applyFormNo）；不含正常开立医嘱诊疗耗材 */
export function isAdviceRowServiceApplyFormBoundConsumable(
  row: AdviceRowItem,
  allRows: readonly AdviceRowItem[],
): boolean {
  if (!isAdviceRowBoundConsumableFromService(row)) {
    return false;
  }
  if (String(row.applyFormNo ?? '').trim()) {
    return true;
  }
  const anchorId = normalizeAdviceRowRequestId(row.basedOnServiceRequestId);
  if (!anchorId) {
    return false;
  }
  for (const parent of allRows) {
    const parentId = normalizeAdviceRowRequestId(parent.requestId);
    if (parentId !== anchorId) {
      continue;
    }
    return Boolean(String(parent.applyFormNo ?? '').trim());
  }
  return false;
}

export function productTableNameForAdviceRow(row: AdviceRowItem): string | undefined {
  const fromList = String(row.productTableName ?? '').trim();
  if (fromList) return fromList;
  const rt = String(row.resourceType ?? '')
    .trim()
    .toUpperCase();
  const fb = ADVICE_ROW_PRODUCT_TABLE_FALLBACK[rt];
  return fb?.trim() || undefined;
}

export function resourceKeyFromRow(row: AdviceRowItem): DoctorStationAdviceResourceKeyDto | null {
  const requestId = String(row.requestId ?? '').trim();
  const productTableName = productTableNameForAdviceRow(row);
  if (!requestId || !productTableName) return null;
  return { requestId, productTableName };
}

export function rowLooksDraftForSign(row: AdviceRowItem): boolean {
  if (row.isEditor) return false;
  if (!resourceKeyFromRow(row)) return false;
  const code = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase();
  if (code === 'DRAFT') return true;
  const blob = `${row.status ?? ''} ${row.statusEnum ?? ''}`.toUpperCase();
  return /\bDRAFT\b/.test(blob);
}

export function rowLooksWithdrawable(
  row: AdviceRowItem,
  isDraftRow: (r: AdviceRowItem) => boolean,
) {
  if (row.isEditor) return false;
  if (!resourceKeyFromRow(row)) return false;
  if (isDraftRow(row)) return false;
  // 已结算/待结算不可撤回
  const chargeCode = String(row.chargeStatusEnum ?? '')
    .trim()
    .toUpperCase();
  if (chargeCode === 'SETTLED' || chargeCode === 'PENDING_SETTLEMENT') {
    return false;
  }
  return true;
}

export function rowLooksDeletable(row: AdviceRowItem, isDraftRow: (r: AdviceRowItem) => boolean) {
  if (row.isEditor) return false;
  if (!isDraftRow(row)) return false;
  return Boolean(resourceKeyFromRow(row));
}

export function rowLooksDraft(row: AdviceRowItem): boolean {
  const text = `${row.status} ${row.statusEnum || ''}`;
  return /待签发|草稿|DRAFT|暂存|新开/i.test(text);
}
