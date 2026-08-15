import type { DoctorStationMedicationUnitBandDto } from '#/api/doctor-station/advice.types';

import { decimalDiv, decimalMul } from '#/utils/format/decimal-util';

/** 剂量单位落库/传参及换算中间量：保留 6 位小数。 */
export function roundMedicationQty(n: number): number {
  if (!Number.isFinite(n)) return 0;
  return Math.round(n * 1_000_000) / 1_000_000;
}

/** 剂量单位界面展示：保留 3 位小数（不改变落库 6 位精度源值）。 */
export function roundMedicationDoseDisplayQty(n: number): number {
  if (!Number.isFinite(n)) return 0;
  return Math.round(n * 1_000) / 1_000;
}

/** 最小单位（拆零）换算结果：保留 2 位小数。 */
export function roundMedicationSplitQty(n: number): number {
  if (!Number.isFinite(n)) return 0;
  return Math.round(n * 100) / 100;
}

function bandKindKeyOf(b: DoctorStationMedicationUnitBandDto): string {
  return String(b.bandKind ?? '')
    .trim()
    .toUpperCase();
}

function ratioToSplitOf(b: DoctorStationMedicationUnitBandDto | undefined): number | undefined {
  if (!b) return undefined;
  const r = Number(b.ratioToSplit);
  return Number.isFinite(r) && r > 0 ? r : undefined;
}

export function unitCodeByBandKind(
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
  kind: string,
): string {
  const k = kind.trim().toUpperCase();
  const hit = (bands ?? []).find((b) => bandKindKeyOf(b) === k);
  return String(hit?.unitCode ?? '').trim();
}

/** 同 unitCode 多带时优先 PACKAGE → SALES → SPLIT → STOCK。 */
export function findBandByUnitCode(
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
  unitCode: string,
): DoctorStationMedicationUnitBandDto | undefined {
  const code = String(unitCode ?? '').trim();
  if (!code || !bands?.length) return undefined;
  const kindsOrder = ['PACKAGE', 'SALES', 'SPLIT', 'STOCK', 'PURCHASE'] as const;
  for (const kk of kindsOrder) {
    const b = bands.find(
      (x) => bandKindKeyOf(x) === kk && String(x.unitCode ?? '').trim() === code,
    );
    if (b) return b;
  }
  return bands.find((x) => String(x.unitCode ?? '').trim() === code);
}

/** 总量单位 code → 计量带 kind；优先与行上 PACKAGE/SALES/SPLIT 三码精确匹配。 */
export function resolveBandKindByUnitCode(
  unitCode: string,
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
  knownCodes?: {
    packageCode?: string;
    salesCode?: string;
    splitCode?: string;
  },
): string {
  const code = String(unitCode ?? '').trim();
  if (!code) return '';
  const pkg = String(knownCodes?.packageCode ?? '').trim();
  const sales = String(knownCodes?.salesCode ?? '').trim();
  const split = String(knownCodes?.splitCode ?? '').trim();
  if (split && code === split) return 'SPLIT';
  if (sales && code === sales) return 'SALES';
  if (pkg && code === pkg) return 'PACKAGE';
  const band = findBandByUnitCode(bands, code);
  return band ? bandKindKeyOf(band) : '';
}

/** 两 unitCode 间换算数量（基于 ratioToSplit）。 */
export function convertQtyBetweenUnitCodes(
  qtyFrom: number,
  fromUnitCode: string,
  toUnitCode: string,
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
): number | undefined {
  const fk = String(fromUnitCode ?? '').trim();
  const tk = String(toUnitCode ?? '').trim();
  if (!fk || !tk || fk === tk) {
    const q = Number(qtyFrom);
    return Number.isFinite(q) ? q : undefined;
  }
  const fb = findBandByUnitCode(bands, fk);
  const tb = findBandByUnitCode(bands, tk);
  const rf = ratioToSplitOf(fb);
  const rt = ratioToSplitOf(tb);
  if (rf === undefined || rt === undefined) return undefined;
  const q = Number(qtyFrom);
  if (!Number.isFinite(q) || q < 0) return undefined;
  return roundMedicationQty(decimalDiv(decimalMul(q, rf, 8), rt, 8));
}

/** 总量默认单位编码：优先指定 bandKind（PACKAGE / SALES / SPLIT），否则回落其它带。 */
export function defaultTotalAmountUnitCode(
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
  preferredKind: 'PACKAGE' | 'SALES' | 'SPLIT' = 'SALES',
): string {
  const pkg = unitCodeByBandKind(bands, 'PACKAGE');
  const sales = unitCodeByBandKind(bands, 'SALES');
  const split = unitCodeByBandKind(bands, 'SPLIT');
  if (preferredKind === 'PACKAGE' && pkg) return pkg;
  if (preferredKind === 'SALES' && sales) return sales;
  if (preferredKind === 'SPLIT' && split) return split;
  return pkg || sales || split || '';
}
