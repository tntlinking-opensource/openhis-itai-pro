import type { AdviceDrugItem, AdviceRowItem } from './advice.types';

import type { DoctorStationAdviceResourceKeyDto } from '#/api/doctor-station/advice.types';

import { ADVICE_ROW_PRODUCT_TABLE_FALLBACK } from './advice-tab.constants';

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
  return Boolean(
    item.selectedId &&
      item.diagnosis &&
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
    return Number(item.totalAmount) > 0 && String(item.totalAmountUnit ?? '').trim() !== '';
  }
  if (kind === 'SERVICE') {
    return (
      Number(item.serviceQuantity) > 0 &&
      Number(item.totalAmount) > 0 &&
      String(item.totalAmountUnit ?? '').trim() !== '' &&
      String(item.performOrgId ?? '').trim() !== ''
    );
  }
  return validateWesternMedicationLineItem(item);
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
  const text = `${row.status} ${row.statusEnum || ''}`;
  if (/已收费|SETTLED|已结算|计费完成/i.test(text)) return false;
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
