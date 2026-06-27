/**
 * 进货/选品等多计量带：PURCHASE、SPLIT、STOCK 等；`ratioToSplit` 表示「1 个本带单位 = 多少个拆零基准（SPLIT）单位」。
 * 保存接口时数量通常持久化为 **SPLIT**（见 {@link persistDisplayQtyAsSplitForBands}）；药库非入库界面默认展示带为 **STOCK**，进货默认 **PURCHASE**（不用 STOCK 作默认）。
 * 药库非入库展示单价优先取目标带 `unitPrice`，缺省时以 **PACKAGE** 单价换算到展示带（见 {@link warehouseNonInboundResolveDisplayUnitPrice}）。
 * 跨域目录单价换算见 {@link ../product-unit-band-price.ts}。
 */

import { decimalDiv, decimalMul } from '#/utils/format/decimal-util';

export interface InboundProductUnitBand {
  bandKind?: string;
  unitCode?: string;
  unitName?: string;
  ratioToSplit?: number;
  unitPrice?: null | number;
}

/** 解析接口返回的 `unitBands` 数组或 JSON 字符串 */
export function normalizeInboundProductUnitBands(
  raw?: unknown,
  jsonStr?: unknown,
): InboundProductUnitBand[] {
  if (Array.isArray(raw) && raw.length > 0) {
    return raw as InboundProductUnitBand[];
  }
  if (typeof jsonStr === 'string' && jsonStr.trim().length > 0) {
    try {
      const v = JSON.parse(jsonStr) as unknown;
      if (Array.isArray(v)) return v as InboundProductUnitBand[];
    } catch {
      /* ignore */
    }
  }
  return [];
}

function bandKindKey(b: InboundProductUnitBand): string {
  return String(b.bandKind ?? '')
    .trim()
    .toUpperCase();
}

/** 优先 PURCHASE，否则第一条 */
export function defaultInboundUnitBandKind(bands: InboundProductUnitBand[]): string {
  const hit = bands.find((b) => bandKindKey(b) === 'PURCHASE');
  if (hit) return 'PURCHASE';
  const first = bands[0];
  return first ? bandKindKey(first) : '';
}

export function findInboundUnitBandByKind(
  bands: InboundProductUnitBand[] | undefined,
  kind: string,
): InboundProductUnitBand | undefined {
  const k = kind.trim().toUpperCase();
  return (bands ?? []).find((b) => bandKindKey(b) === k);
}

function ratioOf(band: InboundProductUnitBand | undefined): number | undefined {
  if (!band) return undefined;
  const r = Number(band.ratioToSplit);
  if (!Number.isFinite(r) || r <= 0) return undefined;
  return r;
}

/**
 * 在两种 band 之间换算数量：`qtyTo = qtyFrom * ratioFrom / ratioTo`（均以 SPLIT 为基准）。
 */
export function convertInboundQtyBetweenBandKinds(
  qtyFrom: number,
  fromKind: string,
  toKind: string,
  bands: InboundProductUnitBand[],
): number | undefined {
  const fk = fromKind.trim().toUpperCase();
  const tk = toKind.trim().toUpperCase();
  if (!fk || !tk || fk === tk) return undefined;
  const rf = ratioOf(findInboundUnitBandByKind(bands, fk));
  const rt = ratioOf(findInboundUnitBandByKind(bands, tk));
  if (rf === undefined || rt === undefined) return undefined;
  const q = Number(qtyFrom);
  if (!Number.isFinite(q) || q < 0) return undefined;
  return decimalDiv(decimalMul(q, rf, 8), rt, 6);
}

/** 读取指定计量带上的 `unitPrice`（非换算）。 */
export function parseInboundBandUnitPrice(
  band: InboundProductUnitBand | undefined,
): number | undefined {
  if (!band) return undefined;
  const up = band.unitPrice;
  if (up === undefined || up === null || String(up) === '') return undefined;
  const n = Number(up);
  return Number.isFinite(n) ? n : undefined;
}

/** PACKAGE 带 `unitPrice`（后端 / catalog 单价真源） */
export function inboundPackageBandUnitPrice(bands: InboundProductUnitBand[]): number | undefined {
  return parseInboundBandUnitPrice(findInboundUnitBandByKind(bands, 'PACKAGE'));
}

/**
 * 计量带之间换算单价：`priceTo = priceFrom * ratioTo / ratioFrom`（与 {@link convertInboundQtyBetweenBandKinds} 对称）。
 */
export function convertInboundUnitPriceBetweenBandKinds(
  priceFrom: number,
  fromKind: string,
  toKind: string,
  bands: InboundProductUnitBand[],
): number | undefined {
  const fk = fromKind.trim().toUpperCase();
  const tk = toKind.trim().toUpperCase();
  if (!fk || !tk) return undefined;
  if (fk === tk) {
    const p = Number(priceFrom);
    return Number.isFinite(p) ? p : undefined;
  }
  const rf = ratioOf(findInboundUnitBandByKind(bands, fk));
  const rt = ratioOf(findInboundUnitBandByKind(bands, tk));
  if (rf === undefined || rt === undefined) return undefined;
  const p = Number(priceFrom);
  if (!Number.isFinite(p)) return undefined;
  return decimalDiv(decimalMul(p, rt, 8), rf, 6);
}

/**
 * 进货行展示单价：优先目标带 `unitPrice`；其次 PACKAGE 锚换算；最后用 `legacyUnitPrice`。
 */
export function inboundResolveDisplayUnitPrice(args: {
  bands: InboundProductUnitBand[];
  displayBandKind?: string;
  legacyUnitPrice?: null | number;
}): number | undefined {
  const target = String(args.displayBandKind ?? 'PURCHASE')
    .trim()
    .toUpperCase();
  if (target && target !== 'LEGACY' && args.bands.length > 0) {
    const direct = parseInboundBandUnitPrice(findInboundUnitBandByKind(args.bands, target));
    if (direct !== undefined) return direct;
  }
  const pkgPrice = inboundPackageBandUnitPrice(args.bands);
  if (pkgPrice !== undefined && target && target !== 'LEGACY') {
    const converted = convertInboundUnitPriceBetweenBandKinds(
      pkgPrice,
      'PACKAGE',
      target,
      args.bands,
    );
    if (converted !== undefined && Number.isFinite(converted)) return converted;
  }
  if (pkgPrice !== undefined) return pkgPrice;
  const leg = Number(args.legacyUnitPrice);
  return Number.isFinite(leg) ? leg : undefined;
}

/**
 * 药库非入库展示单价：以 **PACKAGE** `unitPrice` 为真源，换算为 `displayBandKind`（默认 **STOCK**）下「每 1 个目标单位」单价。
 */
export function warehouseNonInboundPackagePriceForDisplayBand(
  bands: InboundProductUnitBand[],
  displayBandKind?: string,
): number | undefined {
  if (bands.length === 0) return undefined;
  const pkgPrice = inboundPackageBandUnitPrice(bands);
  if (pkgPrice === undefined) return undefined;
  const target = String(displayBandKind ?? 'STOCK')
    .trim()
    .toUpperCase();
  if (!target || target === 'LEGACY') return pkgPrice;
  const converted = convertInboundUnitPriceBetweenBandKinds(pkgPrice, 'PACKAGE', target, bands);
  return converted !== undefined && Number.isFinite(converted) ? converted : pkgPrice;
}

/**
 * 药库非入库行单价：优先目标带自身 `unitPrice`；其次 PACKAGE→展示带换算；最后用 `legacyUnitPrice`。
 */
export function warehouseNonInboundResolveDisplayUnitPrice(args: {
  bands: InboundProductUnitBand[];
  displayBandKind?: string;
  legacyUnitPrice?: null | number;
}): number | undefined {
  const target = String(args.displayBandKind ?? 'STOCK')
    .trim()
    .toUpperCase();
  if (target && target !== 'LEGACY' && args.bands.length > 0) {
    const direct = parseInboundBandUnitPrice(findInboundUnitBandByKind(args.bands, target));
    if (direct !== undefined) return direct;
  }
  const fromPackage = warehouseNonInboundPackagePriceForDisplayBand(
    args.bands,
    args.displayBandKind,
  );
  if (fromPackage !== undefined) return fromPackage;
  const leg = Number(args.legacyUnitPrice);
  return Number.isFinite(leg) ? leg : undefined;
}

/** 药库非入库：按计量带写入 unitCode 与展示单价（默认 STOCK 带）。 */
export function applyWarehouseNonInboundBandKindToRow(
  row: {
    price?: number;
    unitBands?: InboundProductUnitBand[];
    unitCode?: string;
  },
  kind: string,
  legacyUnitPrice?: null | number,
): void {
  const k = kind.trim().toUpperCase();
  if (k === 'LEGACY' || !row.unitBands?.length) {
    return;
  }
  const b = findInboundUnitBandByKind(row.unitBands, k);
  if (!b) {
    return;
  }
  const code = String(b.unitCode ?? '').trim();
  if (code !== '') {
    row.unitCode = code;
  }
  const unitPrice = warehouseNonInboundResolveDisplayUnitPrice({
    bands: row.unitBands,
    displayBandKind: k,
    legacyUnitPrice,
  });
  if (unitPrice !== undefined) {
    row.price = unitPrice;
  }
}

/** 下拉选项：按 bandKind 去重，展示 unitName */
export function inboundUnitBandSelectOptions(
  bands: InboundProductUnitBand[],
): { label: string; value: string }[] {
  const seen = new Set<string>();
  const out: { label: string; value: string }[] = [];
  for (const b of bands) {
    const k = bandKindKey(b);
    if (!k || seen.has(k)) continue;
    seen.add(k);
    const name = String(b.unitName ?? '').trim() || k;
    out.push({ value: k, label: name });
  }
  return out;
}

/** 根据当前行的 unitCode 反推 bandKind（优先 PURCHASE） */
export function inferInboundBandKindFromUnitCode(
  bands: InboundProductUnitBand[],
  unitCode: string,
): string {
  const code = String(unitCode ?? '').trim();
  if (!code || bands.length === 0) return defaultInboundUnitBandKind(bands);
  const pur = bands.find(
    (b) => bandKindKey(b) === 'PURCHASE' && String(b.unitCode ?? '').trim() === code,
  );
  if (pur) return 'PURCHASE';
  const hit = bands.find((b) => String(b.unitCode ?? '').trim() === code);
  return hit ? bandKindKey(hit) : defaultInboundUnitBandKind(bands);
}

// --- 多计量带通用：落库 **SPLIT** 数量（进货/出库等均适用；仅「默认展示带」策略不同） ---

/**
 * 保存时：界面数量为当前 `selectedBandKind` 下数量；持久化为 **SPLIT** 数量并带 SPLIT 的 `unitCode`。
 * `LEGACY` 或无带时保持原数量与 `legacyUnitCode`。
 */
export function persistDisplayQtyAsSplitForBands(args: {
  bands: InboundProductUnitBand[];
  displayQty: number;
  legacyUnitCode?: string;
  selectedBandKind?: string;
}): { itemQuantity: number; unitCode?: string } {
  const bandsArr = args.bands ?? [];
  const kind = String(args.selectedBandKind ?? 'LEGACY')
    .trim()
    .toUpperCase();
  const q = Number(args.displayQty);
  const displayQty = Number.isFinite(q) ? q : 0;
  const displayUnit = String(args.legacyUnitCode ?? '').trim();

  if (kind === 'LEGACY' || bandsArr.length === 0) {
    return { itemQuantity: displayQty, unitCode: displayUnit || undefined };
  }

  const splitBand = findInboundUnitBandByKind(bandsArr, 'SPLIT');
  const splitUnitCode = String(splitBand?.unitCode ?? '').trim();
  if (!splitUnitCode) {
    return { itemQuantity: displayQty, unitCode: displayUnit || undefined };
  }

  if (kind === 'SPLIT') {
    return { itemQuantity: displayQty, unitCode: splitUnitCode };
  }

  const splitQty = convertInboundQtyBetweenBandKinds(displayQty, kind, 'SPLIT', bandsArr);
  const iq = splitQty !== undefined && Number.isFinite(splitQty) ? splitQty : displayQty;
  return { itemQuantity: iq, unitCode: splitUnitCode };
}

/**
 * 保存时：`itemQuantity` 为 SPLIT；`unitCode` 为用户当前选中计量带的单位编码（录入/展示单位，与 {@code SupplyPersistedSplitQuantity} 一致）。
 */
export function resolveInboundLinePersistUnitCodeForBands(args: {
  bands: InboundProductUnitBand[];
  legacyUnitCode?: string;
  selectedBandKind?: string;
}): string | undefined {
  const bandsArr = args.bands ?? [];
  const kind = String(args.selectedBandKind ?? 'LEGACY')
    .trim()
    .toUpperCase();
  const legacy = String(args.legacyUnitCode ?? '').trim();
  if (kind === 'LEGACY' || bandsArr.length === 0) {
    return legacy || undefined;
  }
  const b = findInboundUnitBandByKind(bandsArr, kind);
  const code = String(b?.unitCode ?? '').trim();
  return code || legacy || undefined;
}

/** 保存前校验：非 LEGACY 且存在 SPLIT 带时，必须能换算到 SPLIT。 */
export function canConvertDisplayQtyToSplitForBands(
  bands: InboundProductUnitBand[],
  displayQty: number,
  selectedBandKind?: string,
): boolean {
  const kind = String(selectedBandKind ?? 'LEGACY')
    .trim()
    .toUpperCase();
  const qty = Number(displayQty);
  if (!Number.isFinite(qty)) return false;
  if (kind === 'LEGACY' || bands.length === 0) return true;
  const splitUnit = String(findInboundUnitBandByKind(bands, 'SPLIT')?.unitCode ?? '').trim();
  if (!splitUnit) return true;
  if (kind === 'SPLIT') return true;
  return convertInboundQtyBetweenBandKinds(qty, kind, 'SPLIT', bands) !== undefined;
}

/**
 * 进货详情回显（只读）：`itemQuantity` 恒为 SPLIT；`unitCode` 为保存时录入单位。
 * 仅将 SPLIT 数量换算到 `unitCode` 对应计量带下的展示数量；单价/总价由调用方原样使用，不在此换算。
 */
export function hydrateInboundDetailQtyFromPersisted(args: {
  bands: InboundProductUnitBand[];
  persistedItemQuantity: number;
  persistedUnitCode?: string;
  /** 同码多带时的带推断；默认进货侧 {@link inferInboundBandKindFromUnitCode} */
  inferBandKindFromUnitCode?: (bands: InboundProductUnitBand[], unitCode: string) => string;
  /** `unitCode` 为空时的默认带；默认 {@link defaultInboundUnitBandKind} */
  defaultBandKindWhenEmpty?: (bands: InboundProductUnitBand[]) => string;
}): { bandKind: string; displayQty: number; unitCode: string } {
  const bandsArr = args.bands ?? [];
  const splitQty = Number(args.persistedItemQuantity);
  const persistedQty = Number.isFinite(splitQty) ? splitQty : 0;
  const persistedCode = String(args.persistedUnitCode ?? '').trim();
  const inferBand = args.inferBandKindFromUnitCode ?? inferInboundBandKindFromUnitCode;
  const defaultBand = args.defaultBandKindWhenEmpty ?? defaultInboundUnitBandKind;

  if (bandsArr.length === 0) {
    return { displayQty: persistedQty, bandKind: 'LEGACY', unitCode: persistedCode };
  }

  const bandKind = persistedCode ? inferBand(bandsArr, persistedCode) : defaultBand(bandsArr);
  const band = findInboundUnitBandByKind(bandsArr, bandKind);
  const unitCode = String(band?.unitCode ?? '').trim() || persistedCode;

  if (bandKind === 'SPLIT') {
    return { displayQty: persistedQty, bandKind, unitCode };
  }

  const displayQty = convertInboundQtyBetweenBandKinds(persistedQty, 'SPLIT', bandKind, bandsArr);
  return {
    displayQty: displayQty !== undefined && Number.isFinite(displayQty) ? displayQty : persistedQty,
    bandKind,
    unitCode,
  };
}

/**
 * 进货详情回填：后端按 **SPLIT** 持久化时，将数量换到 **PURCHASE** 展示（有采购带时）；否则 SPLIT 或按 `unitCode` 推断。
 * 与 {@link warehouseNonInboundLoadPersistedSplitAsStockDisplay} 对称，入库不默认 STOCK。
 * 药库进货单详情请用 {@link hydrateInboundDetailQtyFromPersisted}（按保存时 `unitCode` 回显）。
 */
export function inboundLoadPersistedSplitAsPurchaseDisplay(args: {
  bands: InboundProductUnitBand[];
  persistedItemQuantity: number;
  persistedUnitCode?: string;
}): { bandKind: string; displayQty: number; unitCode: string } {
  const bandsArr = args.bands ?? [];
  const pq = Number(args.persistedItemQuantity);
  const persistedQty = Number.isFinite(pq) ? pq : 0;
  const persistedCode = String(args.persistedUnitCode ?? '').trim();

  if (bandsArr.length === 0) {
    return { displayQty: persistedQty, bandKind: 'LEGACY', unitCode: persistedCode };
  }

  const splitBand = findInboundUnitBandByKind(bandsArr, 'SPLIT');
  const splitCode = String(splitBand?.unitCode ?? '').trim();
  const purchaseBand = findInboundUnitBandByKind(bandsArr, 'PURCHASE');

  const treatAsSplitPersisted =
    splitCode !== '' && (persistedCode === '' || persistedCode === splitCode);

  if (treatAsSplitPersisted && purchaseBand && ratioOf(purchaseBand)) {
    const purchaseQty = convertInboundQtyBetweenBandKinds(
      persistedQty,
      'SPLIT',
      'PURCHASE',
      bandsArr,
    );
    const dq =
      purchaseQty !== undefined && Number.isFinite(purchaseQty) ? purchaseQty : persistedQty;
    const pc = String(purchaseBand.unitCode ?? '').trim();
    return {
      displayQty: dq,
      bandKind: 'PURCHASE',
      unitCode: pc || persistedCode,
    };
  }

  if (treatAsSplitPersisted && splitBand) {
    return {
      displayQty: persistedQty,
      bandKind: 'SPLIT',
      unitCode: splitCode,
    };
  }

  const kind = inferInboundBandKindFromUnitCode(bandsArr, persistedCode);
  const b = findInboundUnitBandByKind(bandsArr, kind);
  const uc = String(b?.unitCode ?? '').trim() || persistedCode;
  return { displayQty: persistedQty, bandKind: kind, unitCode: uc };
}

// --- 药库「非入库」：界面默认 **STOCK** 计量带（入库不用本段默认逻辑） ---

/**
 * 药库除入库外的业务：有 `unitBands` 时默认选 **STOCK**（无可用 STOCK 则 SPLIT，再退化为进货默认带）。
 */
export function warehouseNonInboundDefaultBandKind(bands: InboundProductUnitBand[]): string {
  if (bands.length === 0) return 'LEGACY';
  if (ratioOf(findInboundUnitBandByKind(bands, 'STOCK'))) return 'STOCK';
  if (ratioOf(findInboundUnitBandByKind(bands, 'SPLIT'))) return 'SPLIT';
  const k = defaultInboundUnitBandKind(bands);
  return k || 'LEGACY';
}

/**
 * 下拉/列表展示用单位编码：有 **STOCK** 带则取其 `unitCode`，否则取 SPLIT / 首条。
 */
/** 计量带展示名（仅取该 bandKind 的 unitName，不回落）。 */
export function inboundUnitBandNameByKind(
  bands: InboundProductUnitBand[] | undefined,
  kind: string,
): string {
  const b = findInboundUnitBandByKind(bands, kind);
  return String(b?.unitName ?? '').trim();
}

/**
 * 药库界面展示：后端按 **SPLIT** 持久化的数量 → **STOCK** 数量（有 unitBands 用带换算，否则用 legacy 倍数）。
 */
export function warehouseNonInboundDisplayQtyFromSplit(
  splitQty: number,
  bands: InboundProductUnitBand[],
  legacyStockRatio?: null | number | string,
): number {
  const q = Number(splitQty);
  if (!Number.isFinite(q)) return 0;
  if (bands.length > 0) {
    const stockBand = findInboundUnitBandByKind(bands, 'STOCK');
    if (stockBand && ratioOf(stockBand)) {
      const converted = convertInboundQtyBetweenBandKinds(q, 'SPLIT', 'STOCK', bands);
      if (converted !== undefined && Number.isFinite(converted)) return converted;
    }
  }
  const ratio = Number(legacyStockRatio);
  if (Number.isFinite(ratio) && ratio > 0) {
    return decimalDiv(q, ratio, 6);
  }
  return q;
}

/** 药库 STOCK 单位展示名：优先 STOCK 带，其次 PACKAGE 带，最后 legacy 文案。 */
export function warehouseNonInboundStockUnitLabel(
  bands: InboundProductUnitBand[] | undefined,
  legacyUnitName?: string,
): string {
  if (bands && bands.length > 0) {
    const stock = inboundUnitBandNameByKind(bands, 'STOCK');
    if (stock) return stock;
    const pkg = inboundUnitBandNameByKind(bands, 'PACKAGE');
    if (pkg) return pkg;
  }
  return String(legacyUnitName ?? '').trim();
}

/** 格式化「数量 + STOCK 单位」展示（数量已由 SPLIT 换算为 STOCK 时传入）。 */
export function formatWarehouseStockQtyUnitText(
  displayQty: number | string | undefined,
  bands: InboundProductUnitBand[] | undefined,
  legacyUnitName?: string,
): string {
  const n = Number(displayQty);
  const qtyPart = Number.isFinite(n) ? String(n) : String(displayQty ?? '').trim();
  const unitPart = warehouseNonInboundStockUnitLabel(bands, legacyUnitName);
  if (!qtyPart && !unitPart) return '';
  if (!unitPart) return qtyPart;
  if (!qtyPart) return unitPart;
  return `${qtyPart} ${unitPart}`;
}

export function warehouseNonInboundPickListUnitCode(unitBands: unknown): string {
  if (!Array.isArray(unitBands) || unitBands.length === 0) return '';
  const bands = unitBands as InboundProductUnitBand[];
  const stock = findInboundUnitBandByKind(bands, 'STOCK');
  const sc = String(stock?.unitCode ?? '').trim();
  if (sc) return sc;
  const split = findInboundUnitBandByKind(bands, 'SPLIT');
  const sp = String(split?.unitCode ?? '').trim();
  if (sp) return sp;
  return String(bands[0]?.unitCode ?? '').trim();
}

/**
 * 同码多带（如盒）时优先匹配 **STOCK**，再 SPLIT / PACKAGE / PURCHASE，与进货侧「优先 PURCHASE」区分。
 */
export function inferWarehouseNonInboundBandKindFromUnitCode(
  bands: InboundProductUnitBand[],
  unitCode: string,
): string {
  const code = String(unitCode ?? '').trim();
  if (!code || bands.length === 0) return warehouseNonInboundDefaultBandKind(bands);
  const kindsOrder = ['STOCK', 'SPLIT', 'PACKAGE', 'PURCHASE'] as const;
  for (const kk of kindsOrder) {
    const b = findInboundUnitBandByKind(bands, kk);
    if (b && String(b.unitCode ?? '').trim() === code) return kk;
  }
  const hit = bands.find((b) => String(b.unitCode ?? '').trim() === code);
  return hit ? bandKindKey(hit) : warehouseNonInboundDefaultBandKind(bands);
}

/** 药库出库等详情回显：SPLIT 数量按保存时 `unitCode` 换算；同码多带优先 STOCK（见 {@link inferWarehouseNonInboundBandKindFromUnitCode}）。 */
export function hydrateWarehouseDetailQtyFromPersisted(args: {
  bands: InboundProductUnitBand[];
  persistedItemQuantity: number;
  persistedUnitCode?: string;
}): { bandKind: string; displayQty: number; unitCode: string } {
  return hydrateInboundDetailQtyFromPersisted({
    ...args,
    inferBandKindFromUnitCode: inferWarehouseNonInboundBandKindFromUnitCode,
    defaultBandKindWhenEmpty: warehouseNonInboundDefaultBandKind,
  });
}

/**
 * 详情回填：后端按 **SPLIT** 持久化时，将数量换到 **STOCK** 展示（有 STOCK 带时）；否则保持 SPLIT 或按 `unitCode` 推断带。
 * 药库出库单详情请用 {@link hydrateWarehouseDetailQtyFromPersisted}（按保存时 `unitCode` 回显）。
 */
export function warehouseNonInboundLoadPersistedSplitAsStockDisplay(args: {
  bands: InboundProductUnitBand[];
  persistedItemQuantity: number;
  persistedUnitCode?: string;
}): { bandKind: string; displayQty: number; unitCode: string } {
  const bandsArr = args.bands ?? [];
  const pq = Number(args.persistedItemQuantity);
  const persistedQty = Number.isFinite(pq) ? pq : 0;
  const persistedCode = String(args.persistedUnitCode ?? '').trim();

  if (bandsArr.length === 0) {
    return { displayQty: persistedQty, bandKind: 'LEGACY', unitCode: persistedCode };
  }

  const splitBand = findInboundUnitBandByKind(bandsArr, 'SPLIT');
  const splitCode = String(splitBand?.unitCode ?? '').trim();
  const stockBand = findInboundUnitBandByKind(bandsArr, 'STOCK');

  const treatAsSplitPersisted =
    splitCode !== '' && (persistedCode === '' || persistedCode === splitCode);

  if (treatAsSplitPersisted && stockBand && ratioOf(stockBand)) {
    const stockQty = convertInboundQtyBetweenBandKinds(persistedQty, 'SPLIT', 'STOCK', bandsArr);
    const dq = stockQty !== undefined && Number.isFinite(stockQty) ? stockQty : persistedQty;
    const stockCode = String(stockBand.unitCode ?? '').trim();
    return {
      displayQty: dq,
      bandKind: 'STOCK',
      unitCode: stockCode || persistedCode,
    };
  }

  if (treatAsSplitPersisted && splitBand) {
    return {
      displayQty: persistedQty,
      bandKind: 'SPLIT',
      unitCode: splitCode,
    };
  }

  const kind = inferWarehouseNonInboundBandKindFromUnitCode(bandsArr, persistedCode);
  const b = findInboundUnitBandByKind(bandsArr, kind);
  const uc = String(b?.unitCode ?? '').trim() || persistedCode;
  return { displayQty: persistedQty, bandKind: kind, unitCode: uc };
}
