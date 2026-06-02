import type { DoctorStationMedicationUnitBandDto } from '#/api/doctor-station/advice.types';

import {
  convertProductUnitPriceBetweenBandKinds,
  PRODUCT_CATALOG_PRICE_SOURCE_BAND_KIND,
} from '#/utils/product-unit-band-price';
import { decimalDiv, decimalMul } from '#/utils/decimal-util';

/** 解析目录行上的多计量带（数组或 JSON 字符串） */
export function normalizeCatalogUnitBands(
  unitBands?: DoctorStationMedicationUnitBandDto[] | null,
  unitBandsJson?: string | null,
): DoctorStationMedicationUnitBandDto[] {
  if (Array.isArray(unitBands) && unitBands.length > 0) {
    return unitBands;
  }
  if (typeof unitBandsJson === 'string' && unitBandsJson.trim().length > 0) {
    try {
      const v = JSON.parse(unitBandsJson) as unknown;
      if (Array.isArray(v)) {
        return v as DoctorStationMedicationUnitBandDto[];
      }
    } catch {
      /* ignore */
    }
  }
  return [];
}

export function unitNameByBandKind(
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
  kind: string,
): string {
  const k = kind.trim().toUpperCase();
  const hit = (bands ?? []).find(
    (b) =>
      String(b.bandKind ?? '')
        .trim()
        .toUpperCase() === k,
  );
  return String(hit?.unitName ?? '').trim();
}

export function unitCodeByBandKind(
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
  kind: string,
): string {
  const k = kind.trim().toUpperCase();
  const hit = (bands ?? []).find(
    (b) =>
      String(b.bandKind ?? '')
        .trim()
        .toUpperCase() === k,
  );
  return String(hit?.unitCode ?? '').trim();
}

/** 单次用量/总量等小数位：保留 6 位小数 */
export function roundMedicationQty(n: number): number {
  if (!Number.isFinite(n)) return 0;
  return Math.round(n * 1_000_000) / 1_000_000;
}

export interface TotalUnitSelectOption {
  value: string;
  label: string;
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
  return roundMedicationQty(decimalDiv(decimalMul(q, rf, 8), rt, 6));
}

/** 总量默认单位编码：优先指定 bandKind（PACKAGE / SALES），否则 SPLIT。 */
export function defaultTotalAmountUnitCode(
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
  preferredKind: 'PACKAGE' | 'SALES' = 'SALES',
): string {
  const pkg = unitCodeByBandKind(bands, 'PACKAGE');
  const sales = unitCodeByBandKind(bands, 'SALES');
  const split = unitCodeByBandKind(bands, 'SPLIT');
  if (preferredKind === 'PACKAGE' && pkg) return pkg;
  if (preferredKind === 'SALES' && sales) return sales;
  return pkg || sales || split || '';
}

/** 拆零基准数量 → 当前总量 unitCode 下数量（自动推算总量用）。 */
export function convertSplitQtyToTotalUnitCode(
  splitQty: number,
  targetUnitCode: string,
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
): number {
  const splitCode = unitCodeByBandKind(bands, 'SPLIT');
  if (!splitCode) return roundMedicationQty(splitQty);
  const converted = convertQtyBetweenUnitCodes(splitQty, splitCode, targetUnitCode, bands);
  return converted !== undefined ? converted : roundMedicationQty(splitQty);
}

/**
 * 行单价：优先目标 unitCode 对应带的 unitPrice；否则目录价（默认 SALES 带）换算到该带。
 */
export function resolveAdviceLineUnitPrice(params: {
  bands: DoctorStationMedicationUnitBandDto[] | undefined;
  catalogUnitPrice?: null | number | string;
  totalAmountUnit: string;
}): number | undefined {
  const code = String(params.totalAmountUnit ?? '').trim();
  if (!code) return undefined;
  const bands = params.bands ?? [];
  const band = findBandByUnitCode(bands, code);
  const direct = band?.unitPrice;
  if (direct !== undefined && direct !== null && String(direct).trim() !== '') {
    const n = Number(direct);
    if (Number.isFinite(n)) return n;
  }
  const raw = params.catalogUnitPrice;
  if (raw === null || raw === undefined || String(raw).trim() === '') return undefined;
  const catalogPrice = Number(raw);
  if (!Number.isFinite(catalogPrice)) return undefined;
  if (bands.length === 0) return catalogPrice;
  const kind = band ? bandKindKeyOf(band) : undefined;
  if (!kind) return catalogPrice;
  const fromKind = PRODUCT_CATALOG_PRICE_SOURCE_BAND_KIND;
  if (kind === fromKind) return catalogPrice;
  const converted = convertProductUnitPriceBetweenBandKinds(catalogPrice, fromKind, kind, bands);
  return converted !== undefined && Number.isFinite(converted) ? converted : catalogPrice;
}

/** 总量单位下拉：包装 PACKAGE、售卖 SALES、拆零 SPLIT（同 unitCode 去重）。 */
export function totalUnitSelectOptions(item: {
  totalUnitPackageCode?: string;
  totalUnitPackageName?: string;
  totalUnitSplitCode?: string;
  totalUnitSplitName?: string;
  totalUnitSalesCode?: string;
  totalUnitSalesName?: string;
}): TotalUnitSelectOption[] {
  const seen = new Set<string>();
  const out: TotalUnitSelectOption[] = [];
  const push = (code: string, name: string) => {
    const c = String(code ?? '').trim();
    if (!c || seen.has(c)) return;
    seen.add(c);
    out.push({ value: c, label: String(name ?? '').trim() || c });
  };
  push(String(item.totalUnitPackageCode ?? ''), String(item.totalUnitPackageName ?? ''));
  push(String(item.totalUnitSalesCode ?? ''), String(item.totalUnitSalesName ?? ''));
  push(String(item.totalUnitSplitCode ?? ''), String(item.totalUnitSplitName ?? ''));
  return out;
}

/** 总量存 `unitCode` 时，摘要/展示用名称 */
export function totalAmountUnitDisplayLabel(item: {
  totalAmountUnit: string;
  totalUnitPackageCode?: string;
  totalUnitPackageName?: string;
  totalUnitSplitCode?: string;
  totalUnitSplitName?: string;
  totalUnitSalesCode?: string;
  totalUnitSalesName?: string;
}): string {
  const c = String(item.totalAmountUnit || '').trim();
  if (!c) return '';
  const pc = String(item.totalUnitPackageCode ?? '').trim();
  const sc = String(item.totalUnitSalesCode ?? '').trim();
  const spc = String(item.totalUnitSplitCode ?? '').trim();
  if (pc && c === pc) return String(item.totalUnitPackageName ?? '').trim() || c;
  if (sc && c === sc) return String(item.totalUnitSalesName ?? '').trim() || c;
  if (spc && c === spc) return String(item.totalUnitSplitName ?? '').trim() || c;
  return c;
}

/**
 * 单次用量「拆零 = 换算 × 剂量」中的换算系数：剂量带 `ratioToSplit` / 拆零带 `ratioToSplit`。
 * 缺任一带或非正拆零比时返回 undefined。
 */
/** 按展示单位名称在 unitBands 中解析 unitCode。 */
export function unitCodeByUnitName(
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
  unitName?: string,
): string {
  const name = String(unitName ?? '').trim();
  if (!name) return '';
  const hit = (bands ?? []).find((b) => String(b.unitName ?? '').trim() === name);
  return String(hit?.unitCode ?? '').trim();
}

/**
 * 长期医嘱草稿：总量 quantity/unitCode 取单次用量左侧（拆零 SPLIT 数量与单位，页面上不展示总量）。
 */
export function resolveLongTermMedicationQuantityPayload(item: {
  singleDose?: number;
  doseMainUnit?: string;
  unitBands?: DoctorStationMedicationUnitBandDto[];
}): { quantity: number; unitCode: string } {
  const qtyRaw = Number(item.singleDose ?? Number.NaN);
  const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
  const unitCode =
    unitCodeByBandKind(item.unitBands, 'SPLIT') ||
    unitCodeByUnitName(item.unitBands, item.doseMainUnit) ||
    'U';
  return { quantity, unitCode };
}

/** 草稿保存：单次剂量取 DOSE 带数值（右侧剂量单位），单位取 DOSE unitCode。 */
export function resolveMedicationDraftDosePayload(item: {
  singleDose?: number;
  doseConvertRatio?: number;
  doseSubUnit?: string;
  unitBands?: DoctorStationMedicationUnitBandDto[];
}): { dose?: number; doseUnitCode?: string } {
  const doseUnitCode =
    unitCodeByBandKind(item.unitBands, 'DOSE') ||
    String(item.doseSubUnit ?? '').trim() ||
    undefined;

  const doseQtyRaw = Number(item.doseConvertRatio ?? Number.NaN);
  if (Number.isFinite(doseQtyRaw) && doseQtyRaw > 0) {
    return {
      dose: doseQtyRaw,
      ...(doseUnitCode ? { doseUnitCode } : {}),
    };
  }

  const singleRaw = Number(item.singleDose ?? Number.NaN);
  const dose = Number.isFinite(singleRaw) && singleRaw > 0 ? singleRaw : undefined;
  const fallbackUnitCode = unitCodeByBandKind(item.unitBands, 'SPLIT') || doseUnitCode;
  return {
    ...(dose === undefined ? {} : { dose }),
    ...(fallbackUnitCode ? { doseUnitCode: fallbackUnitCode } : {}),
  };
}

/** 后端 dose（DOSE 带）→ 编辑行单次剂量双字段。 */
export function hydrateAdviceRowDoseFromBackend(params: {
  dose?: null | number | string;
  doseUnitCode?: null | string;
  doseUnitName?: null | string;
  unitBands?: DoctorStationMedicationUnitBandDto[];
}): {
  singleDose?: number;
  doseConvertRatio?: number;
  doseMainUnit: string;
  doseSubUnit: string;
  doseBandConvertK?: number;
} {
  const doseRaw = Number(params.dose);
  const doseQty = Number.isFinite(doseRaw) && doseRaw > 0 ? doseRaw : undefined;
  const bands = params.unitBands;
  const splitName = unitNameByBandKind(bands, 'SPLIT');
  const doseName = String(params.doseUnitName ?? '').trim() || unitNameByBandKind(bands, 'DOSE');
  const k = doseConvertRatioSplitToDose(bands);

  const base = {
    doseMainUnit: splitName,
    doseSubUnit: doseName,
    doseBandConvertK: k !== undefined && Number.isFinite(k) && k > 0 ? k : undefined,
    singleDose: undefined as number | undefined,
    doseConvertRatio: undefined as number | undefined,
  };

  if (doseQty === undefined) return base;

  if (k !== undefined && Number.isFinite(k) && k > 0) {
    return {
      ...base,
      doseConvertRatio: roundMedicationQty(doseQty),
      singleDose: roundMedicationQty(doseQty / k),
    };
  }

  return {
    ...base,
    singleDose: doseQty,
    doseConvertRatio: doseQty,
  };
}

export function doseConvertRatioSplitToDose(
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
): number | undefined {
  const splitBand = (bands ?? []).find(
    (b) =>
      String(b.bandKind ?? '')
        .trim()
        .toUpperCase() === 'SPLIT',
  );
  const doseBand = (bands ?? []).find(
    (b) =>
      String(b.bandKind ?? '')
        .trim()
        .toUpperCase() === 'DOSE',
  );
  const sr = Number(splitBand?.ratioToSplit);
  const dr = Number(doseBand?.ratioToSplit);
  if (!Number.isFinite(sr) || !Number.isFinite(dr) || sr <= 0) {
    return undefined;
  }
  return dr / sr;
}

/**
 * 总量拆零(SPLIT)与售卖(SALES)换算：售卖数量 = 拆零数量 × k，k = split.ratioToSplit / sales.ratioToSplit。
 */
export function totalAmountSplitToSalesFactor(
  bands: DoctorStationMedicationUnitBandDto[] | undefined,
): number | undefined {
  const splitBand = (bands ?? []).find(
    (b) =>
      String(b.bandKind ?? '')
        .trim()
        .toUpperCase() === 'SPLIT',
  );
  const salesBand = (bands ?? []).find(
    (b) =>
      String(b.bandKind ?? '')
        .trim()
        .toUpperCase() === 'SALES',
  );
  const sr = Number(splitBand?.ratioToSplit);
  const br = Number(salesBand?.ratioToSplit);
  if (!Number.isFinite(sr) || !Number.isFinite(br) || sr <= 0 || br <= 0) {
    return undefined;
  }
  return sr / br;
}

/** 切换总量单位时换算数量；优先 unitBands，回落 SPLIT↔SALES 系数。 */
export function convertTotalAmountWhenUnitChanges(params: {
  oldUnit: string;
  newUnit: string;
  bands?: DoctorStationMedicationUnitBandDto[] | undefined;
  splitCode?: string;
  salesCode?: string;
  splitToSalesK?: number | undefined;
  currentAmount: number;
}): number | undefined {
  const ou = String(params.oldUnit ?? '').trim();
  const nu = String(params.newUnit ?? '').trim();
  if (!ou || !nu || ou === nu) return undefined;
  const amt = Number(params.currentAmount);
  if (!Number.isFinite(amt) || amt < 0) return undefined;

  if (params.bands?.length) {
    const fromBands = convertQtyBetweenUnitCodes(amt, ou, nu, params.bands);
    if (fromBands !== undefined) return fromBands;
  }

  const sc = String(params.splitCode ?? '').trim();
  const sac = String(params.salesCode ?? '').trim();
  const k = params.splitToSalesK;
  if (k === undefined || !Number.isFinite(k) || k <= 0) return undefined;
  if (ou === sc && nu === sac) return roundMedicationQty(amt * k);
  if (ou === sac && nu === sc) return roundMedicationQty(amt / k);
  return undefined;
}
