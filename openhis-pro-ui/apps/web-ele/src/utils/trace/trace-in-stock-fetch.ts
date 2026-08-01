import type { InventoryItemTraceNumberRowDto } from '#/api/inventory/warehouse/inventory-detail';

import { listInStockTraceNumbersByInventoryItemApi } from '#/api/inventory/warehouse/inventory-detail';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 扫码弹窗展开：按批号分组的全院在院码 */
export interface HospitalWideLotGroup {
  lotNumber: string;
  codes: InventoryItemTraceNumberRowDto[];
}

/** 按库存行查询在库追溯码；发药时本库空才回落全院同品同批 */
export async function fetchInStockTraceItems(params: {
  itemId: string;
  locationId: string;
  lotNumber: string;
  /** 发药库取：本库无码才全院同批；本库有码只用本库 */
  hospitalWideFallbackWhenEmpty?: boolean;
}): Promise<InventoryItemTraceNumberRowDto[]> {
  const locationId = preserveApiIdForRequest(params.locationId);
  const itemId = preserveApiIdForRequest(params.itemId);
  const lotNumber = String(params.lotNumber ?? '').trim();
  if (!locationId || !itemId || !lotNumber) return [];

  const res = await listInStockTraceNumbersByInventoryItemApi({
    locationId,
    itemId,
    lotNumber,
    hospitalWideFallbackWhenEmpty: params.hospitalWideFallbackWhenEmpty === true,
  });
  return (res.items ?? []).filter((item) => String(item.traceNo ?? '').trim() !== '');
}

/** 按库存行查询在库追溯码字符串列表 */
export async function fetchInStockTraceCodes(params: {
  itemId: string;
  locationId: string;
  lotNumber: string;
  hospitalWideFallbackWhenEmpty?: boolean;
}): Promise<string[]> {
  const items = await fetchInStockTraceItems(params);
  return items.map((item) => String(item.traceNo ?? '').trim()).filter((code) => code !== '');
}

/**
 * 按药品查全院在院码（含全部批号；不按发药行批号过滤）。
 * 仅需 itemId；locationId/lotNumber 可不传。
 */
export async function fetchHospitalWideInHospitalTraceItems(params: {
  itemId: string;
}): Promise<InventoryItemTraceNumberRowDto[]> {
  const itemId = preserveApiIdForRequest(params.itemId);
  if (!itemId) return [];

  const res = await listInStockTraceNumbersByInventoryItemApi({
    itemId,
    hospitalWide: true,
  });
  return (res.items ?? []).filter((item) => String(item.traceNo ?? '').trim() !== '');
}

/**
 * 将全院在院码按批号分组（批号空归为「-」）；组内保持接口顺序。
 *
 * @param items 全院在院码扁平行
 */
export function groupHospitalWideTracesByLot(
  items: InventoryItemTraceNumberRowDto[],
): HospitalWideLotGroup[] {
  const groups: HospitalWideLotGroup[] = [];
  const indexByLot = new Map<string, number>();
  for (const item of items) {
    if (!item) continue;
    const lot = String(item.lotNumber ?? '').trim() || '-';
    const existing = indexByLot.get(lot);
    if (existing === undefined) {
      indexByLot.set(lot, groups.length);
      groups.push({ lotNumber: lot, codes: [item] });
    } else {
      groups[existing]!.codes.push(item);
    }
  }
  return groups;
}
