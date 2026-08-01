/**
 * 库房/药房/科室「库存批号行」选品下拉：表格列与接口字段 1:1，禁止「A 为空取 B」式回退。
 * 行键为 `inventoryItemId`（批号级展开，非订货/进货的产品定义展开）；**须展示 `lotNumber`（批号）**。
 * 库存数量列：接口 `availableQuantity` 为 SPLIT 基准，展示按包装倍数换算（同码多带冲突时优先 PACKAGE，见 inbound-product-unit-bands）。
 */

import {
  normalizeInboundProductUnitBands,
  warehouseNonInboundDisplayQtyFromSplit,
  warehouseNonInboundStockUnitLabel,
} from '#/utils/inventory/inbound-product-unit-bands';

export const INVENTORY_PRODUCT_PICK_TABLE_COLUMNS = [
  { prop: 'productBusNo', label: '项目编码', width: 140 },
  { prop: 'productName', label: '项目名称', width: 180 },
  { prop: 'specification', label: '规格', width: 120 },
  { prop: 'lotNumber', label: '批号', width: 120 },
  { prop: 'expirationDate', label: '有效期', width: 120 },
  { prop: 'stockAvailableDisplay', label: '库存数量', width: 112 },
  { prop: 'manufacturer', label: '厂家/产地', width: 180 },
  { prop: 'purchaseUnitPrice', label: '进货价', width: 96 },
  { prop: 'retailPrice', label: '零售价', width: 96 },
  { prop: 'stockUnitName', label: '单位', width: 96 },
  { prop: 'approvalNumber', label: '批准文号', width: 140 },
] as const;

interface UnitBandLike {
  bandKind?: string;
  unitName?: string;
}

/** 从 unitBands 中取指定 bandKind 的 unitName（仅此字段，不回落到其他带或 unitCode）。 */
export function unitBandNameByKind(unitBands: unknown, bandKind: string): string {
  if (!Array.isArray(unitBands)) return '';
  const target = bandKind.trim().toUpperCase();
  for (const raw of unitBands) {
    const b = raw as UnitBandLike;
    if (String(b.bandKind ?? '').toUpperCase() !== target) continue;
    if (b.unitName === undefined || b.unitName === null) return '';
    return String(b.unitName);
  }
  return '';
}

export function inventoryPickNumberField(pick: Record<string, unknown>, key: string): number {
  const n = Number(pick[key]);
  return Number.isFinite(n) ? n : 0;
}

/**
 * 选品列表行：保留接口原字段，仅补充下拉表格展示列与 SelectTable 的 value-key（inventoryItemId）。
 */
export function mapInventoryProductPickListRow(
  r: Record<string, unknown>,
): Record<string, unknown> {
  const inventoryItemId = r.inventoryItemId;
  const id =
    inventoryItemId === undefined || inventoryItemId === null ? '' : String(inventoryItemId);

  const bands = normalizeInboundProductUnitBands(r.unitBands);
  const splitAvail = inventoryPickNumberField(r, 'availableQuantity');
  const legacyRatio = r.packageUnitRatio ?? r.stockUnitRatio;
  const stockQty = warehouseNonInboundDisplayQtyFromSplit(splitAvail, bands, legacyRatio);
  const stockUnit = warehouseNonInboundStockUnitLabel(
    bands,
    unitBandNameByKind(r.unitBands, 'STOCK') || unitBandNameByKind(r.unitBands, 'PACKAGE'),
  );
  const stockAvailableDisplay = stockUnit !== '' ? `${stockQty} ${stockUnit}` : String(stockQty);

  const productBusNo =
    inventoryPickStringField(r, 'productBusNo') || inventoryPickStringField(r, 'busNo');

  // 有效期展示：截取日期部分，便于选品表与回填一致
  const expirationRaw = r.expirationDate ?? r.endTime;
  let expirationDate = r.expirationDate;
  if (
    expirationRaw !== undefined &&
    expirationRaw !== null &&
    String(expirationRaw).trim() !== ''
  ) {
    const s = String(expirationRaw).trim();
    if (s.includes('T')) {
      expirationDate = s.split('T')[0] ?? s;
    } else if (s.includes(' ')) {
      expirationDate = s.split(' ')[0] ?? s;
    } else {
      expirationDate = s;
    }
  }

  return {
    ...r,
    ...(productBusNo ? { productBusNo } : {}),
    ...(expirationDate !== undefined && expirationDate !== null && expirationDate !== ''
      ? { expirationDate }
      : {}),
    id,
    packageUnitName: unitBandNameByKind(r.unitBands, 'PACKAGE'),
    splitUnitName: unitBandNameByKind(r.unitBands, 'SPLIT'),
    stockUnitName: stockUnit,
    stockAvailableQty: stockQty,
    stockAvailableDisplay,
  };
}

/** 选品行可用库存（SPLIT 基准）→ 药库界面 STOCK 数量（盘点/出库选品回填用）。 */
export function inventoryPickAvailableQtyAsStockDisplay(pick: Record<string, unknown>): number {
  const bands = normalizeInboundProductUnitBands(pick.unitBands);
  const splitAvail = inventoryPickNumberField(pick, 'availableQuantity');
  const legacyRatio = pick.packageUnitRatio ?? pick.stockUnitRatio;
  return warehouseNonInboundDisplayQtyFromSplit(splitAvail, bands, legacyRatio);
}

export function inventoryPickStringField(pick: Record<string, unknown>, key: string): string {
  const v = pick[key];
  if (v === undefined || v === null) return '';
  return String(v).trim();
}

/** 选品格展示：仅用 productName + inventoryItemId。 */
export function inventoryPickMedicineCell(
  pick: Record<string, unknown>,
): { id?: number | string; name: string } | undefined {
  const name = inventoryPickStringField(pick, 'productName');
  const idRaw = pick.inventoryItemId;
  const id = idRaw === undefined || idRaw === null ? undefined : (idRaw as number | string);
  if (!name && id === undefined) return undefined;
  return { id, name };
}
