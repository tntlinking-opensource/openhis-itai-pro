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

/**
 * 落库 quantity/unitCode 归一：须落在目录 unitBands 内（与发药拆零换算同口径）。
 *
 * 成组临嘱偶发把溶媒「袋」等异品单位带到本药；若有单次剂量则按 dose÷ratio→拆零纠正，
 * 否则抛错阻止脏数据入库。
 */
export function resolvePersistMedicationQuantityUnit(params: {
  quantity: number;
  unitCode: string;
  bands?: DoctorStationMedicationUnitBandDto[] | null;
  dose?: number;
  doseUnitCode?: string;
  drugName?: string;
}): { quantity: number; unitCode: string } {
  const bands = params.bands ?? [];
  const qtyRaw = Number(params.quantity);
  const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
  const unitCode = String(params.unitCode ?? '').trim();
  if (!unitCode) {
    throw new Error('药品开立单位不能为空');
  }
  if (!bands.length) {
    return { quantity, unitCode };
  }
  if (findBandByUnitCode(bands, unitCode)) {
    return { quantity, unitCode };
  }
  // 总量槽偶存展示名：按 unitName 反查 code
  const byName = bands.find((b) => String(b.unitName ?? '').trim() === unitCode);
  const namedCode = String(byName?.unitCode ?? '').trim();
  if (namedCode && findBandByUnitCode(bands, namedCode)) {
    return { quantity, unitCode: namedCode };
  }
  const corrected = tryCorrectQuantityUnitFromDose(bands, params.dose, params.doseUnitCode);
  if (corrected) {
    return corrected;
  }
  const label = String(params.drugName ?? '').trim() || '该药品';
  throw new Error(
    `开立单位不在「${label}」目录可换算单位内，请重新选择总量/发药单位后再保存`,
  );
}

/** 非法开立单位时：用 dose（计量）÷ dose.ratioToSplit 得到拆零量。 */
function tryCorrectQuantityUnitFromDose(
  bands: DoctorStationMedicationUnitBandDto[],
  dose?: number,
  doseUnitCode?: string,
): { quantity: number; unitCode: string } | undefined {
  const splitCode = unitCodeByBandKind(bands, 'SPLIT');
  const doseCode = unitCodeByBandKind(bands, 'DOSE');
  const doseQty = Number(dose);
  if (!splitCode || !Number.isFinite(doseQty) || doseQty <= 0) {
    return undefined;
  }
  const doseUnit = String(doseUnitCode ?? '').trim();
  // 剂量码即拆零：剂量数量直接落拆零
  if (doseUnit === splitCode || (doseCode && doseCode === splitCode)) {
    return { quantity: roundMedicationSplitQty(doseQty), unitCode: splitCode };
  }
  if (doseCode && doseUnit && doseUnit !== doseCode) {
    return undefined;
  }
  const doseBand = findBandByUnitCode(bands, doseCode || doseUnit);
  const ratio = ratioToSplitOf(doseBand);
  if (ratio === undefined || ratio <= 0) {
    return undefined;
  }
  // 1 拆零 = ratio 计量 → 拆零量 = 计量 ÷ ratio
  return {
    quantity: roundMedicationSplitQty(doseQty / ratio),
    unitCode: splitCode,
  };
}
