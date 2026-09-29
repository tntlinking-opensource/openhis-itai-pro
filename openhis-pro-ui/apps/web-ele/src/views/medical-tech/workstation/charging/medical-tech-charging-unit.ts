/**
 * 医技计费选品：单位槽解析（对齐护士划价包装/拆零切换）。
 */
import type {
  MedicalTechDeviceCatalogRowDto,
  MedicalTechDeviceCatalogUnitBandDto,
} from '#/api/medical-tech/medical-tech-charging.types';

import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';

/** 拆零槽 */
export const MT_BAND_KIND_SPLIT = 'SPLIT';

/** 包装槽 */
export const MT_BAND_KIND_PACKAGE = 'PACKAGE';

export interface MedicalTechUnitBandSelectOption {
  bandKind: string;
  unitCode: string;
  unitName: string;
  unitPrice?: null | number | string;
}

/** 规范化 bandKind */
function normalizeBandKind(kind?: null | string): string {
  return String(kind ?? '')
    .trim()
    .toUpperCase();
}

/** 按 bandKind 找槽 */
function findBand(
  bands: MedicalTechDeviceCatalogUnitBandDto[] | null | undefined,
  bandKind: string,
): MedicalTechDeviceCatalogUnitBandDto | undefined {
  const key = normalizeBandKind(bandKind);
  if (!key) {
    return undefined;
  }
  return (bands ?? []).find((band) => normalizeBandKind(band.bandKind) === key);
}

/**
 * 耗材默认单位槽：优先 SPLIT，再 PACKAGE，再首个有 unitCode 的槽。
 */
export function resolveMedicalTechDefaultBandKind(row: MedicalTechDeviceCatalogRowDto): string {
  const split = findBand(row.unitBands, MT_BAND_KIND_SPLIT);
  if (split && String(split.unitCode ?? '').trim()) {
    return MT_BAND_KIND_SPLIT;
  }
  const pkg = findBand(row.unitBands, MT_BAND_KIND_PACKAGE);
  if (pkg && String(pkg.unitCode ?? '').trim()) {
    return MT_BAND_KIND_PACKAGE;
  }
  const first = (row.unitBands ?? []).find((band) => String(band.unitCode ?? '').trim());
  return normalizeBandKind(first?.bandKind) || MT_BAND_KIND_PACKAGE;
}

/**
 * 可切换单位选项（仅包装/拆零）。
 * 同 unitCode 或同展示名去重：主数据包装=拆零时常码不同但仍显示「盒/袋」。
 */
export function listMedicalTechUnitBandOptions(
  row: MedicalTechDeviceCatalogRowDto,
): MedicalTechUnitBandSelectOption[] {
  const preferred = [MT_BAND_KIND_SPLIT, MT_BAND_KIND_PACKAGE];
  const seenCodes = new Set<string>();
  const seenNames = new Set<string>();
  const options: MedicalTechUnitBandSelectOption[] = [];
  for (const kind of preferred) {
    const band = findBand(row.unitBands, kind);
    if (!band) {
      continue;
    }
    const unitCode = String(band.unitCode ?? '').trim();
    if (!unitCode) {
      continue;
    }
    const unitName = String(band.unitName ?? '').trim() || unitCode;
    // 码相同或展示名相同：只保留先扫到的槽（优先拆零）
    if (seenCodes.has(unitCode) || seenNames.has(unitName)) {
      continue;
    }
    seenCodes.add(unitCode);
    seenNames.add(unitName);
    options.push({
      bandKind: normalizeBandKind(band.bandKind) || kind,
      unitCode,
      unitName,
      unitPrice: band.unitPrice,
    });
  }
  // 无双槽时回落行头单位，保证至少可展示
  if (options.length === 0) {
    const code = String(row.unitCode ?? '').trim();
    if (code) {
      options.push({
        bandKind: MT_BAND_KIND_PACKAGE,
        unitCode: code,
        unitName: String(row.unitName ?? '').trim() || code,
        unitPrice: row.unitPrice,
      });
    }
  }
  return options;
}

/** 按槽解析 unitCode */
export function resolveMedicalTechUnitCodeByBand(
  row: MedicalTechDeviceCatalogRowDto,
  bandKind: string,
): string {
  const band = findBand(row.unitBands, bandKind);
  const fromBand = String(band?.unitCode ?? '').trim();
  if (fromBand) {
    return fromBand;
  }
  return String(row.unitCode ?? '').trim();
}

/** 按槽解析展示名 */
export function resolveMedicalTechUnitNameByBand(
  row: MedicalTechDeviceCatalogRowDto,
  bandKind: string,
): string {
  const band = findBand(row.unitBands, bandKind);
  const fromBand = String(band?.unitName ?? '').trim();
  if (fromBand) {
    return fromBand;
  }
  return String(row.unitName ?? '').trim() || resolveMedicalTechUnitCodeByBand(row, bandKind);
}

/** 按槽解析单价（禁止前端再 ÷ratio） */
export function resolveMedicalTechUnitPriceByBand(
  row: MedicalTechDeviceCatalogRowDto,
  bandKind: string,
): null | number | string {
  const band = findBand(row.unitBands, bandKind);
  if (band?.unitPrice !== null && band?.unitPrice !== undefined && band.unitPrice !== '') {
    return band.unitPrice;
  }
  return row.unitPrice ?? null;
}

/**
 * 包装/拆零之间换算数量（ratioToSplit 相对拆零）。
 * 缺 ratio 时原样返回，避免误改数量。
 */
export function convertMedicalTechQtyBetweenBandKinds(
  quantity: number,
  fromBandKind: string,
  toBandKind: string,
  row: MedicalTechDeviceCatalogRowDto,
): number {
  const fromKind = normalizeBandKind(fromBandKind);
  const toKind = normalizeBandKind(toBandKind);
  if (!fromKind || !toKind || fromKind === toKind) {
    return quantity;
  }
  const fromBand = findBand(row.unitBands, fromKind);
  const toBand = findBand(row.unitBands, toKind);
  const fromRatio = Number(fromBand?.ratioToSplit);
  const toRatio = Number(toBand?.ratioToSplit);
  if (!Number.isFinite(fromRatio) || fromRatio <= 0 || !Number.isFinite(toRatio) || toRatio <= 0) {
    return quantity;
  }
  // 展示量 → 拆零 → 目标单位
  const splitQty = quantity * fromRatio;
  const converted = splitQty / toRatio;
  if (!Number.isFinite(converted) || converted <= 0) {
    return 1;
  }
  return Math.max(1, Math.round(converted * 1000) / 1000);
}

/** 单价展示（带 ¥） */
export function formatMedicalTechUnitPrice(unitPrice: null | number | string | undefined): string {
  if (unitPrice === null || unitPrice === undefined || unitPrice === '') {
    return '—';
  }
  const n = Number(unitPrice);
  if (!Number.isFinite(n)) {
    return '—';
  }
  return `¥ ${formatMoneyDisplay(unitPrice)}`;
}

/** 行金额展示（带 ¥） */
export function formatMedicalTechDraftLineAmount(
  quantity: number,
  unitPrice: null | number | string | undefined,
): string {
  if (unitPrice === null || unitPrice === undefined || unitPrice === '') {
    return '—';
  }
  return `¥ ${formatMoneyDisplay(calcMoneyLineTotal(unitPrice, quantity))}`;
}
