import type {
  DoctorStationAdviceInventoryRowDto,
  DoctorStationMedicationUnitBandDto,
} from '#/api/doctor-station/advice.types';
import {
  convertInboundQtyBetweenBandKinds,
  inboundUnitBandNameByKind,
  normalizeInboundProductUnitBands,
} from '#/utils/inventory/inbound-product-unit-bands';

export type AdviceCatalogStockDisplayRow = {
  availableQuantity?: null | number | string;
  inventoryList?: DoctorStationAdviceInventoryRowDto[];
  unitBands?: DoctorStationMedicationUnitBandDto[];
  unitName?: string;
  defaultUnitName?: string;
};

function parseAdviceAvailableQuantity(raw: null | number | string | undefined): number | undefined {
  if (raw === null || raw === undefined || String(raw).trim() === '') {
    return undefined;
  }
  const n = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) ? n : undefined;
}

/** 汇总 inventoryList 各行 quantity（拆零 SPLIT 基准）。 */
export function sumAdviceCatalogInventorySplitQty(
  inventoryList?: DoctorStationAdviceInventoryRowDto[],
): number | undefined {
  if (!Array.isArray(inventoryList) || inventoryList.length === 0) return undefined;
  let sum = 0;
  let has = false;
  for (const item of inventoryList) {
    const n = Number(item.quantity);
    if (Number.isFinite(n)) {
      sum += n;
      has = true;
    }
  }
  return has ? sum : undefined;
}

/** SPLIT 数量 → PACKAGE 展示数量（医生站目录库存列默认单位）。 */
export function convertAdviceCatalogSplitQtyToPackageDisplay(
  splitQty: number,
  unitBands?: DoctorStationMedicationUnitBandDto[],
): number {
  const q = Number(splitQty);
  if (!Number.isFinite(q)) return 0;
  const bands = normalizeInboundProductUnitBands(unitBands);
  if (bands.length > 0) {
    const converted = convertInboundQtyBetweenBandKinds(q, 'SPLIT', 'PACKAGE', bands);
    if (converted !== undefined && Number.isFinite(converted)) return converted;
  }
  return q;
}

/** PACKAGE 计量带展示名；无带时回落目录 unitName。 */
export function resolveAdviceCatalogPackageUnitName(row: AdviceCatalogStockDisplayRow): string {
  const bands = normalizeInboundProductUnitBands(row.unitBands);
  const pkg = inboundUnitBandNameByKind(bands, 'PACKAGE');
  if (pkg) return pkg;
  const direct = String(row.unitName ?? row.defaultUnitName ?? '').trim();
  if (direct) return direct;
  return inboundUnitBandNameByKind(bands, 'STOCK');
}

/**
 * 医生站目录库存列展示数量（PACKAGE 单位）：
 * - `inventoryList[].quantity` 为拆零 SPLIT，需汇总后换算；
 * - 无明细时 `availableQuantity` 已由后端换算为 PACKAGE，直接展示。
 */
export function resolveAdviceCatalogPackageDisplayQty(
  row: AdviceCatalogStockDisplayRow,
): number | undefined {
  const splitSum = sumAdviceCatalogInventorySplitQty(row.inventoryList);
  if (splitSum !== undefined) {
    return convertAdviceCatalogSplitQtyToPackageDisplay(splitSum, row.unitBands);
  }
  return parseAdviceAvailableQuantity(row.availableQuantity);
}

export function isAdviceCatalogZeroStock(row: AdviceCatalogStockDisplayRow): boolean {
  const q = resolveAdviceCatalogPackageDisplayQty(row);
  return q !== undefined && q <= 0;
}

/** 如 `20 盒`；无库存数据时返回 `—`。 */
export function formatAdviceCatalogStockCellText(row: AdviceCatalogStockDisplayRow): string {
  const displayQty = resolveAdviceCatalogPackageDisplayQty(row);
  if (displayQty === undefined) return '—';
  const qtyPart = String(displayQty);
  const unitPart = resolveAdviceCatalogPackageUnitName(row);
  if (!unitPart) return qtyPart;
  return `${qtyPart} ${unitPart}`;
}
