/**
 * 产品多计量带（unitBands）单价换算 — 全局公共工具。
 *
 * ## 背景
 * 药品/耗材等目录接口常返回 `unitBands`（PURCHASE / SPLIT / STOCK / SALES / DOSE 等），
 * 每条带含 `ratioToSplit`（1 个本带单位 = 多少个拆零基准 SPLIT 单位）及可选 `unitPrice`。
 *
 * 门诊医嘱选品、库房展示等场景下，后端列表价 `unitPrice` 通常按 **SALES（售卖单位）** 报价，
 * 而库存数量、部分界面展示按 **STOCK（库存单位）** 对齐，需将单价换算到同一计量带再展示或计费。
 *
 * ## 换算公式（与数量换算对称）
 * 设 from 带 ratio 为 Rf、to 带 ratio 为 Rt（均为相对 SPLIT 的 `ratioToSplit`）：
 *
 *   priceTo = priceFrom × (Rt / Rf)
 *
 * 例：SALES 1 箱 = 100 瓶（ratio=100），STOCK 1 瓶 = 1 瓶（ratio=1），
 * 箱价 300 元 → 瓶价 = 300 × (1/100) = 3 元。
 *
 * ## 使用建议
 * - 仅做**单价**换算；数量换算见库房 `inbound-product-unit-bands` 或医嘱 `medication-unit-bands`。
 * - 无 `unitBands` 或缺少任一带的 `ratioToSplit` 时，`convertProductUnitPriceForDisplay` 返回原 `unitPrice`，不抛错。
 */

import { decimalDiv, decimalMul } from '#/utils/format/decimal-util';

/**
 * 单价换算所需的计量带字段（与目录/选品接口 `unitBands[]` 元素结构一致）。
 * 调用方传入的数组元素至少应含 `bandKind`、`ratioToSplit`；`unitPrice` 仅在读取带内标价时使用。
 */
export interface ProductUnitBandPriceInput {
  bandKind?: string;
  unitCode?: string;
  unitName?: string;
  /** 1 个本带单位 = 多少个 SPLIT 单位；换算单价/数量的核心系数 */
  ratioToSplit?: null | number | string;
  /** 该带下的单价（部分接口仅在 PACKAGE/SALES 等带上填充） */
  unitPrice?: null | number | string;
}

/** 目录/列表接口 `unitPrice` 的默认来源带（与后端定价口径一致，当前为售卖单位 SALES） */
export const PRODUCT_CATALOG_PRICE_SOURCE_BAND_KIND = 'SALES';

/** 与库存数量列、STOCK 库存单位展示对齐时的默认目标带 */
export const PRODUCT_STOCK_DISPLAY_BAND_KIND = 'STOCK';

/** 规范化 bandKind 为大写字符串，用于带匹配 */
function bandKindKeyOf(band: ProductUnitBandPriceInput): string {
  return String(band.bandKind ?? '')
    .trim()
    .toUpperCase();
}

/**
 * 解析 `unitBands`：优先使用已解析的数组，否则尝试解析 `unitBandsJson` 字符串。
 * 解析失败或非数组时返回空数组（调用方应回退为「不换算、使用原单价」）。
 */
export function normalizeProductUnitBandsForPrice(
  raw?: unknown,
  jsonStr?: unknown,
): ProductUnitBandPriceInput[] {
  if (Array.isArray(raw) && raw.length > 0) {
    return raw as ProductUnitBandPriceInput[];
  }
  if (typeof jsonStr === 'string' && jsonStr.trim().length > 0) {
    try {
      const v = JSON.parse(jsonStr) as unknown;
      if (Array.isArray(v)) return v as ProductUnitBandPriceInput[];
    } catch {
      /* ignore */
    }
  }
  return [];
}

/** 按 bandKind（不区分大小写）查找一条计量带 */
function findBandByKind(
  bands: ProductUnitBandPriceInput[] | undefined,
  kind: string,
): ProductUnitBandPriceInput | undefined {
  const k = kind.trim().toUpperCase();
  return (bands ?? []).find((b) => bandKindKeyOf(b) === k);
}

/** 读取有效的 ratioToSplit；缺失、非数字或 ≤0 时视为无法参与换算 */
function ratioToSplitOf(band: ProductUnitBandPriceInput | undefined): number | undefined {
  if (!band) return undefined;
  const r = Number(band.ratioToSplit);
  if (!Number.isFinite(r) || r <= 0) return undefined;
  return r;
}

/**
 * 解析单条计量带上的 `unitPrice` 为 number。
 * 空值、非数字返回 undefined。
 */
export function parseProductUnitBandPrice(
  band: ProductUnitBandPriceInput | undefined,
): number | undefined {
  if (!band) return undefined;
  const up = band.unitPrice;
  if (up === undefined || up === null || String(up) === '') return undefined;
  const n = Number(up);
  return Number.isFinite(n) ? n : undefined;
}

/**
 * 将单价从来源计量带换算到目标计量带。
 *
 * @param priceFrom 来源带下的单价（每 1 个来源单位的价格）
 * @param fromKind 来源带 bandKind，如 `SALES`
 * @param toKind 目标带 bandKind，如 `STOCK`
 * @param bands 完整 unitBands 列表（须同时包含 from/to 且均有有效 ratioToSplit）
 * @returns 目标带下单价；from/to 相同返回原值；缺带或无法换算返回 undefined
 */
export function convertProductUnitPriceBetweenBandKinds(
  priceFrom: number,
  fromKind: string,
  toKind: string,
  bands: ProductUnitBandPriceInput[],
): number | undefined {
  const fk = fromKind.trim().toUpperCase();
  const tk = toKind.trim().toUpperCase();
  if (!fk || !tk) return undefined;
  if (fk === tk) {
    const p = Number(priceFrom);
    return Number.isFinite(p) ? p : undefined;
  }
  const rf = ratioToSplitOf(findBandByKind(bands, fk));
  const rt = ratioToSplitOf(findBandByKind(bands, tk));
  if (rf === undefined || rt === undefined) return undefined;
  const p = Number(priceFrom);
  if (!Number.isFinite(p)) return undefined;
  return decimalDiv(decimalMul(p, rt, 8), rf, 6);
}

/** {@link convertProductUnitPriceForDisplay} 入参 */
export interface ConvertProductUnitPriceForDisplayArgs {
  /** 接口返回的单价（默认视为 sourceBandKind 口径） */
  unitPrice?: null | number | string;
  unitBands?: null | ProductUnitBandPriceInput[];
  unitBandsJson?: null | string;
  /**
   * 后端单价所在计量带。
   * 默认 {@link PRODUCT_CATALOG_PRICE_SOURCE_BAND_KIND}（SALES）。
   */
  sourceBandKind?: string;
  /**
   * 界面展示目标计量带。
   * 默认 {@link PRODUCT_STOCK_DISPLAY_BAND_KIND}（STOCK）。
   */
  targetBandKind?: string;
}

/**
 * 将接口单价换算为「展示用」单价（默认 SALES → STOCK）。
 *
 * 典型用法：医嘱选品下拉 `mapCatalogRowToOption`，把 `row.unitPrice` 转为与库存单位一致的单价再展示。
 *
 * 行为说明：
 * - `unitPrice` 无效 → undefined
 * - 无 unitBands → 原样返回 unitPrice（不换算）
 * - source 与 target 相同 → 原样返回
 * - 换算失败（缺带、缺 ratio）→ 回退为原 unitPrice，避免界面无价
 */
export function convertProductUnitPriceForDisplay(
  args: ConvertProductUnitPriceForDisplayArgs,
): number | undefined {
  const raw = args.unitPrice;
  if (raw === null || raw === undefined || String(raw).trim() === '') {
    return undefined;
  }
  const price = typeof raw === 'number' ? raw : Number(raw);
  if (!Number.isFinite(price)) return undefined;

  const bands = normalizeProductUnitBandsForPrice(args.unitBands, args.unitBandsJson);
  if (bands.length === 0) return price;

  const fromKind = String(args.sourceBandKind ?? PRODUCT_CATALOG_PRICE_SOURCE_BAND_KIND)
    .trim()
    .toUpperCase();
  const toKind = String(args.targetBandKind ?? PRODUCT_STOCK_DISPLAY_BAND_KIND)
    .trim()
    .toUpperCase();
  if (!fromKind || !toKind || fromKind === toKind) return price;

  const converted = convertProductUnitPriceBetweenBandKinds(price, fromKind, toKind, bands);
  return converted !== undefined && Number.isFinite(converted) ? converted : price;
}

/**
 * 直接读取某条计量带上配置的 `unitPrice`（非换算）。
 * 例如读取 PACKAGE 带标价后再自行调用 {@link convertProductUnitPriceBetweenBandKinds}。
 */
export function productUnitPriceByBandKind(
  bands: ProductUnitBandPriceInput[] | undefined,
  kind: string,
): number | undefined {
  return parseProductUnitBandPrice(findBandByKind(bands, kind));
}
