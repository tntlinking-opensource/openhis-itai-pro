import type { DoctorStationMedicationUnitBandDto } from '#/api/doctor-station/advice.types';

import { isPackageBoundOrderCalcRule } from '#/constants/domain/order-calc-rule';
import {
  convertProductUnitPriceBetweenBandKinds,
  PRODUCT_CATALOG_PRICE_SOURCE_BAND_KIND,
} from '#/utils/doctor-station/product-unit-band-price';
import {
  convertQtyBetweenUnitCodes,
  defaultTotalAmountUnitCode,
  findBandByUnitCode,
  resolveBandKindByUnitCode,
  roundMedicationDoseDisplayQty,
  roundMedicationQty,
  roundMedicationSplitQty,
  unitCodeByBandKind,
} from '#/utils/doctor-station/medication-unit-band-query';

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

export {
  convertQtyBetweenUnitCodes,
  defaultTotalAmountUnitCode,
  findBandByUnitCode,
  resolveBandKindByUnitCode,
  roundMedicationDoseDisplayQty,
  roundMedicationQty,
  roundMedicationSplitQty,
  unitCodeByBandKind,
};

export interface TotalUnitSelectOption {
  value: string;
  label: string;
}

function bandKindKeyOf(b: DoctorStationMedicationUnitBandDto): string {
  return String(b.bandKind ?? '')
    .trim()
    .toUpperCase();
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

/**
 * 总量单位下拉：包装 PACKAGE、售卖 SALES、拆零 SPLIT（同 unitCode 去重）。
 * 包装类拆分属性（整包不拆等）不展示拆零单位；若无任何包装/售卖可选则回落拆零。
 */
export function totalUnitSelectOptions(item: {
  partAttributeEnum?: string;
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
  const hideSplit = isPackageBoundOrderCalcRule(item.partAttributeEnum) && out.length > 0;
  if (!hideSplit) {
    push(String(item.totalUnitSplitCode ?? ''), String(item.totalUnitSplitName ?? ''));
  }
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
  const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? roundMedicationSplitQty(qtyRaw) : 1;
  const unitCode =
    unitCodeByBandKind(item.unitBands, 'SPLIT') ||
    unitCodeByUnitName(item.unitBands, item.doseMainUnit) ||
    'U';
  return { quantity, unitCode };
}

/**
 * 首日用量落库：编辑卡按计量单位录入（同写剂量），保存时换算为最小单位（SPLIT），
 * 与长期 `quantity`/`unit_code` 同口径，执行侧无需再区分计量/拆零。
 *
 * @returns 有有效计量数量且能解析拆零单位时返回落库数量+单位；
 *          无首日量（空/≤0）时 undefined（调用方对长期 UPDATE 应显式传 null 清空）；
 *          有数量但缺拆零单位时也 undefined（调用方须报错，禁止误清）
 */
export function resolveFirstDosePersistPayload(item: {
  firstDose?: null | number;
  doseBandConvertK?: number;
  doseMainUnit?: string;
  unitBands?: DoctorStationMedicationUnitBandDto[];
}): { firstDose: number; firstDoseUnitCode: string } | undefined {
  const doseQty = Number(item.firstDose);
  if (!Number.isFinite(doseQty) || doseQty <= 0) {
    return undefined;
  }
  const splitUnit =
    unitCodeByBandKind(item.unitBands, 'SPLIT') ||
    unitCodeByUnitName(item.unitBands, item.doseMainUnit);
  if (!splitUnit) {
    return undefined;
  }
  const k = item.doseBandConvertK ?? doseConvertRatioSplitToDose(item.unitBands);
  const doseUnit = unitCodeByBandKind(item.unitBands, 'DOSE');
  // 计量与拆零同码，或无换算比：数量按拆零精度落库
  if (!k || !Number.isFinite(k) || k <= 0 || (doseUnit && doseUnit === splitUnit)) {
    return {
      firstDose: roundMedicationSplitQty(doseQty),
      firstDoseUnitCode: splitUnit,
    };
  }
  // 例：界面 12ml、k=12 → 落库 1 支
  return {
    firstDose: roundMedicationSplitQty(doseQty / k),
    firstDoseUnitCode: splitUnit,
  };
}

/**
 * 库内/列表首日量（最小单位）→ 编辑卡计量数量（与写剂量右侧同口径）。
 * 若历史误存为剂量单位码则原样展示，避免二次放大。
 */
export function hydrateFirstDoseDisplayQty(params: {
  firstDose?: null | number;
  firstDoseUnitCode?: null | string;
  doseBandConvertK?: number;
  unitBands?: DoctorStationMedicationUnitBandDto[];
}): number | undefined {
  const qty = Number(params.firstDose);
  if (!Number.isFinite(qty) || qty <= 0) {
    return undefined;
  }
  const unit = String(params.firstDoseUnitCode ?? '').trim();
  const doseCode = unitCodeByBandKind(params.unitBands, 'DOSE');
  const splitCode = unitCodeByBandKind(params.unitBands, 'SPLIT');
  const k = params.doseBandConvertK ?? doseConvertRatioSplitToDose(params.unitBands);
  // 已是计量单位：界面不再 ×k
  if (doseCode && unit === doseCode) {
    return roundMedicationQty(qty);
  }
  // 最小单位（或未带单位按拆零）：×k 还原计量展示
  if (!unit || (splitCode && unit === splitCode)) {
    if (k !== undefined && Number.isFinite(k) && k > 0) {
      return roundMedicationQty(qty * k);
    }
    return roundMedicationQty(qty);
  }
  // 其它历史单位无法可靠换算：原样
  return roundMedicationQty(qty);
}

/**
 * 分次剂量框（计量）→ 落库 doseSchedule + quantitySchedule（拆零串）。
 * 关开关或无框时返回 undefined（调用方对长期 UPDATE 应显式传 null 清空）。
 */
export function resolveDoseQuantitySchedulePersistPayload(item: {
  unevenDoseEnabled?: boolean;
  doseScheduleSlots?: Array<number | undefined | null>;
  doseBandConvertK?: number;
  doseMainUnit?: string;
  unitBands?: DoctorStationMedicationUnitBandDto[];
}):
  | {
      doseSchedule: string;
      quantitySchedule: string;
      /** 序列首项计量，写入 dose 列（有 schedule 时读侧仍按序列） */
      firstDoseQty: number;
      /** 序列首项拆零，写入 quantity 列（有 schedule 时算量仍按序列） */
      firstQuantity: number;
      doseUnitCode: string;
      unitCode: string;
    }
  | undefined {
  if (!item.unevenDoseEnabled) {
    return undefined;
  }
  const slots = item.doseScheduleSlots ?? [];
  if (slots.length === 0) {
    return undefined;
  }
  const splitUnit =
    unitCodeByBandKind(item.unitBands, 'SPLIT') ||
    unitCodeByUnitName(item.unitBands, item.doseMainUnit);
  const doseUnit =
    unitCodeByBandKind(item.unitBands, 'DOSE') ||
    String(item.doseMainUnit ?? '').trim() ||
    splitUnit;
  if (!splitUnit || !doseUnit) {
    return undefined;
  }
  const k = item.doseBandConvertK ?? doseConvertRatioSplitToDose(item.unitBands);
  const doseParts: string[] = [];
  const qtyParts: string[] = [];
  // 逐框换算：展开几个必填几个；计量≠拆零时必须有换算比，禁止把微克数当片数写入
  for (const raw of slots) {
    const doseQty = Number(raw);
    if (!Number.isFinite(doseQty) || doseQty <= 0) {
      return undefined;
    }
    doseParts.push(String(roundMedicationQty(doseQty)));
    let splitQty: number;
    if (doseUnit && splitUnit && doseUnit !== splitUnit) {
      if (!k || !Number.isFinite(k) || k <= 0) {
        return undefined;
      }
      splitQty = roundMedicationSplitQty(doseQty / k);
    } else {
      splitQty = roundMedicationSplitQty(doseQty);
    }
    if (!(splitQty > 0)) {
      return undefined;
    }
    qtyParts.push(String(splitQty));
  }
  const firstDoseQty = Number(doseParts[0]);
  const firstQuantity = Number(qtyParts[0]);
  return {
    doseSchedule: doseParts.join(','),
    quantitySchedule: qtyParts.join(','),
    firstDoseQty,
    firstQuantity,
    doseUnitCode: doseUnit,
    unitCode: splitUnit,
  };
}

/**
 * 库内 doseSchedule（计量串）→ 编辑卡分次框；有值则开启不一致开关。
 */
export function hydrateDoseScheduleSlots(params: { doseSchedule?: null | string }): {
  unevenDoseEnabled: boolean;
  doseScheduleSlots?: number[];
} {
  const raw = String(params.doseSchedule ?? '').trim();
  if (!raw) {
    return { unevenDoseEnabled: false };
  }
  const slots: number[] = [];
  // 按逗号还原各计量框
  for (const part of raw.split(',')) {
    const n = Number(String(part ?? '').trim());
    if (!Number.isFinite(n) || n <= 0) {
      return { unevenDoseEnabled: false };
    }
    slots.push(roundMedicationQty(n));
  }
  if (slots.length === 0) {
    return { unevenDoseEnabled: false };
  }
  return { unevenDoseEnabled: true, doseScheduleSlots: slots };
}

/** 草稿保存：单次剂量取 DOSE 带数值（右侧剂量单位，落库 6 位），单位取 DOSE unitCode。 */
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
      dose: roundMedicationQty(doseQtyRaw),
      ...(doseUnitCode ? { doseUnitCode } : {}),
    };
  }

  const singleRaw = Number(item.singleDose ?? Number.NaN);
  const dose =
    Number.isFinite(singleRaw) && singleRaw > 0 ? roundMedicationQty(singleRaw) : undefined;
  const fallbackUnitCode = unitCodeByBandKind(item.unitBands, 'SPLIT') || doseUnitCode;
  return {
    ...(dose === undefined ? {} : { dose }),
    ...(fallbackUnitCode ? { doseUnitCode: fallbackUnitCode } : {}),
  };
}

/** 后端 dose（DOSE 带）→ 编辑行单次剂量双字段（剂量存 6 位，最小单位 2 位）。 */
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
      singleDose: roundMedicationSplitQty(doseQty / k),
    };
  }

  return {
    ...base,
    singleDose: roundMedicationSplitQty(doseQty),
    doseConvertRatio: roundMedicationQty(doseQty),
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

/** 切换总量单位时换算数量；优先经拆零基准中转，避免盒↔片往返丢精度。 */
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
    const splitCode =
      String(params.splitCode ?? '').trim() || unitCodeByBandKind(params.bands, 'SPLIT');
    if (splitCode && ou !== nu) {
      const viaSplit =
        ou === splitCode ? amt : convertQtyBetweenUnitCodes(amt, ou, splitCode, params.bands);
      if (viaSplit !== undefined) {
        const toTarget =
          nu === splitCode
            ? viaSplit
            : convertQtyBetweenUnitCodes(viaSplit, splitCode, nu, params.bands);
        if (toTarget !== undefined) return toTarget;
      }
    }
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
