import type { InventoryItemTraceNumberRowDto } from '#/api/inventory/warehouse/inventory-detail';

import { listInStockTraceNumbersByInventoryItemApi } from '#/api/inventory/warehouse/inventory-detail';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { TRACE_MOCK_IDENTITY_BY_CODE } from './trace-mock-data';

/** 按库存行四元组查询本库仍在库追溯码明细 */
export async function fetchInStockTraceItems(params: {
  itemId: string;
  locationId: string;
  lotNumber: string;
  useMock?: boolean;
}): Promise<InventoryItemTraceNumberRowDto[]> {
  const locationId = preserveApiIdForRequest(params.locationId);
  const itemId = preserveApiIdForRequest(params.itemId);
  const lotNumber = String(params.lotNumber ?? '').trim();
  if (!locationId || !itemId || !lotNumber) return [];

  if (params.useMock) {
    return Object.entries(TRACE_MOCK_IDENTITY_BY_CODE)
      .filter(([, identity]) => identity.itemId === itemId && identity.lotNumber === lotNumber)
      .map(([code]) => ({ traceNo: code, locationNetSplitQuantity: 1 }));
  }

  const res = await listInStockTraceNumbersByInventoryItemApi({
    locationId,
    itemId,
    lotNumber,
  });
  return (res.items ?? []).filter((item) => String(item.traceNo ?? '').trim() !== '');
}

/** 按库存行四元组查询本库仍在库追溯码 */
export async function fetchInStockTraceCodes(params: {
  itemId: string;
  locationId: string;
  lotNumber: string;
  useMock?: boolean;
}): Promise<string[]> {
  const items = await fetchInStockTraceItems(params);
  return items.map((item) => String(item.traceNo ?? '').trim()).filter((code) => code !== '');
}
