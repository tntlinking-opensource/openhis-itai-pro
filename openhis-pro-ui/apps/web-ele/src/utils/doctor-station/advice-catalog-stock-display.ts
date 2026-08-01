import type {
  DoctorStationAdviceInventoryRowDto,
  DoctorStationMedicationUnitBandDto,
} from '#/api/doctor-station/advice.types';

import { decimalMul, decimalSub } from '#/utils/format/decimal-util';
import {
  convertInboundQtyBetweenBandKinds,
  findInboundUnitBandByKind,
  inboundUnitBandNameByKind,
  normalizeInboundProductUnitBands,
} from '#/utils/inventory/inbound-product-unit-bands';

export type AdviceCatalogStockDisplayRow = {
  availableQuantity?: null | number | string;
  /** 后端满包+拆零展示文案（如 4盒6片），优先用于库存列 */
  availableQuantityText?: null | string;
  defaultUnitName?: string;
  inventoryList?: DoctorStationAdviceInventoryRowDto[];
  unitBands?: DoctorStationMedicationUnitBandDto[];
  unitName?: string;
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
  // 逐行累加拆零净量，供对比与本地满包+拆零兜底
  for (const item of inventoryList) {
    const n = Number(item.quantity);
    if (Number.isFinite(n)) {
      sum += n;
      has = true;
    }
  }
  return has ? sum : undefined;
}

/** SPLIT 数量 → PACKAGE 展示数量（仅库存对比用，界面勿直接展示小数盒）。 */
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
 * 医生站目录库存对比用包装数量：
 * - `inventoryList[].quantity` 为拆零 SPLIT，汇总后换算 PACKAGE；
 * - 无明细时回落 `availableQuantity`（后端已换算为 PACKAGE）。
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

/**
 * 拆零净量 → 满包整数 + 拆零余量（对齐后端 MedicationPackageSplitQuantityDisplayAssembler）。
 * 例：34 片、1 盒=7 片 → `4盒6片`。
 */
export function formatAdviceCatalogPackageSplitStockText(
  splitQty: number,
  unitBands?: DoctorStationMedicationUnitBandDto[],
  fallbackPackageUnit?: string,
  fallbackSplitUnit?: string,
): string {
  const q = Number(splitQty);
  if (!Number.isFinite(q)) return '—';

  const bands = normalizeInboundProductUnitBands(unitBands);
  const packageBand = findInboundUnitBandByKind(bands, 'PACKAGE');
  const splitBand = findInboundUnitBandByKind(bands, 'SPLIT');
  const ratio = Number(packageBand?.ratioToSplit);
  const packageLabel = String(packageBand?.unitName ?? fallbackPackageUnit ?? '').trim();
  const splitLabel = String(splitBand?.unitName ?? fallbackSplitUnit ?? '').trim();

  // 无有效包装倍数：整数量 + 拆零/包装单位名
  if (!Number.isFinite(ratio) || ratio <= 0) {
    const qtyPart = String(Math.trunc(q));
    const unitPart = splitLabel || packageLabel;
    if (!unitPart) return qtyPart;
    return `${qtyPart}${unitPart}`;
  }

  // 满包向下取整，余量用拆零单位拼接（禁止小数盒）
  const packageQty = Math.floor(q / ratio);
  const usedInPackages = decimalMul(packageQty, ratio, 8);
  const remainderRaw = decimalSub(q, usedInPackages, 8);
  const remainder = Number(remainderRaw);
  const parts: string[] = [];

  if (packageQty > 0) {
    parts.push(packageLabel ? `${packageQty}${packageLabel}` : String(packageQty));
  } else if (!(Number.isFinite(remainder) && remainder > 1e-9)) {
    parts.push(packageLabel ? `0${packageLabel}` : '0');
  }

  if (Number.isFinite(remainder) && remainder > 1e-9) {
    // 去掉浮点尾差，按整数拆零展示
    const remInt = Math.round(remainder);
    const remText = String(remInt);
    parts.push(splitLabel ? `${remText}${splitLabel}` : remText);
  }

  return parts.join('') || '—';
}

/** 如 `4盒6片`；优先后端 availableQuantityText；无库存数据时返回 `—`。 */
export function formatAdviceCatalogStockCellText(row: AdviceCatalogStockDisplayRow): string {
  const backendText = String(row.availableQuantityText ?? '').trim();
  if (backendText) return backendText;

  const splitSum = sumAdviceCatalogInventorySplitQty(row.inventoryList);
  if (splitSum !== undefined) {
    return formatAdviceCatalogPackageSplitStockText(
      splitSum,
      row.unitBands,
      resolveAdviceCatalogPackageUnitName(row),
    );
  }

  const packageQty = parseAdviceAvailableQuantity(row.availableQuantity);
  if (packageQty === undefined) return '—';
  // 无明细仅有包装换算值时，无法拆余量，退化为整数盒展示
  const unitPart = resolveAdviceCatalogPackageUnitName(row);
  const qtyPart = String(Math.floor(packageQty));
  if (!unitPart) return qtyPart;
  return `${qtyPart}${unitPart}`;
}

/**
 * 药品信息行库存文案：有库存字段时返回「库存：4盒6片」；
 * 无 availableQuantity / Text / inventoryList 时返回空串（不展示）。
 */
export function formatAdviceDrugInfoStockText(row: AdviceCatalogStockDisplayRow): string {
  const hasText = String(row.availableQuantityText ?? '').trim() !== '';
  const hasQty =
    row.availableQuantity !== null &&
    row.availableQuantity !== undefined &&
    String(row.availableQuantity).trim() !== '';
  const hasInv = Array.isArray(row.inventoryList) && row.inventoryList.length > 0;
  if (!hasText && !hasQty && !hasInv) {
    return '';
  }
  const cell = formatAdviceCatalogStockCellText(row);
  if (!cell) {
    return '';
  }
  return `库存：${cell}`;
}
