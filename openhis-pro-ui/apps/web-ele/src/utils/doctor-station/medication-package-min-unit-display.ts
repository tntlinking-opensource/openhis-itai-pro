import type { DoctorStationMedicationUnitBandDto } from '#/api/doctor-station/advice.types';

import { unitCodeByBandKind } from '#/utils/doctor-station/medication-unit-band-query';

/** 药品信息行：包装 ↔ 最小单位关系展示入参 */
export interface MedicationPackageMinUnitDisplayInput {
  totalUnitPackageCode?: null | string;
  totalUnitPackageName?: null | string;
  totalUnitSplitCode?: null | string;
  totalUnitSplitName?: null | string;
  unitBands?: DoctorStationMedicationUnitBandDto[] | null;
}

function trimText(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function bandKindKeyOf(b: DoctorStationMedicationUnitBandDto): string {
  return String(b.bandKind ?? '')
    .trim()
    .toUpperCase();
}

function findBandByKind(
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
  kind: string,
): DoctorStationMedicationUnitBandDto | undefined {
  const k = kind.trim().toUpperCase();
  return (bands ?? []).find((b) => bandKindKeyOf(b) === k);
}

/** 包装倍数展示：整数去小数位，否则原样数字串 */
function formatPackageRatioQty(ratio: number): string {
  if (!Number.isFinite(ratio) || ratio <= 0) {
    return '';
  }
  if (Number.isInteger(ratio) || Math.abs(ratio - Math.round(ratio)) < 1e-9) {
    return String(Math.round(ratio));
  }
  return String(ratio);
}

/**
 * 药品信息：包装与最小单位不同时展示「每{包装单位}{数量}{最小单位}」；
 * 单位相同、缺单位名或缺有效包装倍数时返回空串。
 */
export function formatPackagePerMinUnitText(item: MedicationPackageMinUnitDisplayInput): string {
  const bands = item.unitBands ?? undefined;
  const packageBand = findBandByKind(bands, 'PACKAGE');
  const splitBand = findBandByKind(bands, 'SPLIT');

  const packageCode =
    trimText(item.totalUnitPackageCode) ||
    trimText(packageBand?.unitCode) ||
    unitCodeByBandKind(bands, 'PACKAGE');
  const splitCode =
    trimText(item.totalUnitSplitCode) ||
    trimText(splitBand?.unitCode) ||
    unitCodeByBandKind(bands, 'SPLIT');
  const packageName = trimText(item.totalUnitPackageName) || trimText(packageBand?.unitName);
  const splitName = trimText(item.totalUnitSplitName) || trimText(splitBand?.unitName);

  // 包装单位与最小单位相同：不展示
  if (packageCode && splitCode && packageCode === splitCode) {
    return '';
  }
  if (packageName && splitName && packageName === splitName) {
    return '';
  }
  if (!packageName || !splitName) {
    return '';
  }

  const ratio = Number(packageBand?.ratioToSplit);
  const qtyText = formatPackageRatioQty(ratio);
  if (!qtyText) {
    return '';
  }
  return `每${packageName}${qtyText}${splitName}`;
}
