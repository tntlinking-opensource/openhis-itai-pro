import type { DispensePrescriptionItemRow } from '../components/types';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { decodeInventoryPick } from './inventory-pick-codec';

export interface DispenseTraceScanInventory {
  locationId: string;
  lotNumber: string;
}

/** 从发药行已选/默认发放库房解析库取追溯码用的 locationId 与批号 */
export function resolveDispenseTraceScanInventory(
  row: DispensePrescriptionItemRow,
  inventoryPickByLineId?: Record<string, string>,
): DispenseTraceScanInventory {
  let pickRaw = inventoryPickByLineId?.[row.id];
  if (!pickRaw && row.inventoryPickOptions?.[0]) {
    pickRaw = row.inventoryPickOptions[0].value;
  }
  if (pickRaw) {
    try {
      const decoded = decodeInventoryPick(pickRaw);
      const lotFromPick = decoded.lotNumber?.trim() ?? '';
      const lotFromRow = row.batchNo === '—' ? '' : String(row.batchNo ?? '').trim();
      return {
        locationId: preserveApiIdForRequest(decoded.pharmacyOrganizationId) ?? '',
        lotNumber: lotFromPick || lotFromRow,
      };
    } catch {
      /* 无效 pick 编码时走行上 dispenseLocationId 兜底 */
    }
  }

  const locationId = preserveApiIdForRequest(row.dispenseLocationId) ?? '';
  const lotNumber = row.batchNo === '—' ? '' : String(row.batchNo ?? '').trim();
  return { locationId, lotNumber };
}

/** 从发药行「数量」展示串解析应绑追溯码个数（1 单位 = 1 码） */
export function parseDispenseLineQuantity(row: DispensePrescriptionItemRow): number {
  const raw = String(row.quantity ?? '').trim();
  if (!raw || raw === '—') return 1;
  const matched = raw.match(/^(\d+(?:\.\d+)?)/);
  if (!matched) return 1;
  const n = Math.ceil(Number(matched[1]));
  return Number.isFinite(n) && n > 0 ? n : 1;
}
