/**
 * 进货/选品等多计量带：PURCHASE、SPLIT、STOCK、PACKAGE 等；`ratioToSplit` 表示「1 个本带单位 = 多少个拆零基准（SPLIT）单位」。
 * 保存接口时数量通常持久化为 **SPLIT**（见 {@link persistDisplayQtyAsSplitForBands}）；药库非入库界面默认展示带为 **STOCK**，进货默认 **PURCHASE**（不用 STOCK 作默认）。
 * 药库非入库展示单价优先取目标带 `unitPrice`，缺省时以 **PACKAGE** 单价换算到展示带（见 {@link warehouseNonInboundResolveDisplayUnitPrice}）。
 * 单位下拉：仅业务四槽；同 `unitCode` 且同 `ratioToSplit` 合并为一项，选项 value 优先 **PACKAGE → SPLIT → STOCK → PURCHASE**；**TRACE 不进下拉**（追溯贴码单位仅扫码桥使用）。
 * 跨域目录单价换算见 {@link ../product-unit-band-price.ts}。
 *
 * **`LEGACY` / `legacyUnitCode`**：前端会话态占位，表示「当前行尚无 `unitBands`（接口未带回或选品未装配）」时的单码数量路径；
 * **不是**库内历史双轨字段。药典 `purchase_unit_code`/`split_unit_code` 与供应请求 `unit_code` 在运行库均为齐备口径；
 * 有带后须走四槽换算，禁止把 LEGACY 当成「旧数据兼容契约」。
 */

import { decimalDiv, decimalMul } from '#/utils/format/decimal-util';

export interface InboundProductUnitBand {
  bandKind?: string;
  unitCode?: string;
  unitName?: string;
  ratioToSplit?: number;
  unitPrice?: null | number;
}

/**
 * 库房单据数量单位下拉允许的业务槽；同码同倍合并时的优先级（包装 > 拆零 > 库存 > 采购）。
 * TRACE / DOSE 等不在此列，不得进入数量单位下拉。
 */
export const INVENTORY_UNIT_BAND_SELECT_PREFER = ['PACKAGE', 'SPLIT', 'STOCK', 'PURCHASE'] as const;

type InventoryUnitBandSelectKind = (typeof INVENTORY_UNIT_BAND_SELECT_PREFER)[number];

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

function isInventoryUnitBandSelectKind(kind: string): kind is InventoryUnitBandSelectKind {
  return (INVENTORY_UNIT_BAND_SELECT_PREFER as readonly string[]).includes(kind);
}

function ratioOf(band: InboundProductUnitBand | undefined): number | undefined {
  if (!band) return undefined;
  const r = Number(band.ratioToSplit);
  if (!Number.isFinite(r) || r <= 0) return undefined;
  return r;
}

/** 业务四槽中与指定 unitCode 同码的带（忽略 TRACE 等）。 */
function businessBandsWithUnitCode(
  bands: InboundProductUnitBand[],
  unitCode: string,
): InboundProductUnitBand[] {
  const code = String(unitCode ?? '').trim();
  if (!code) return [];
  return bands.filter((b) => {
    const k = bandKindKey(b);
    if (!isInventoryUnitBandSelectKind(k)) return false;
    return String(b.unitCode ?? '').trim() === code;
  });
}

/** 同码各带 ratioToSplit 均有值且相等时才可合并。 */
function canMergeSameUnitCodeBands(sameCodeBands: InboundProductUnitBand[]): boolean {
  if (sameCodeBands.length <= 1) return true;
  const ratios: number[] = [];
  for (const b of sameCodeBands) {
    const r = ratioOf(b);
    if (r === undefined) return false;
    ratios.push(r);
  }
  const first = ratios[0];
  return ratios.every((r) => r === first);
}

/** 同码可合并时取优先槽；不可合并时返回空（由调用方按页偏好再选）。 */
function preferredSelectKindAmong(sameCodeBands: InboundProductUnitBand[]): string {
  for (const k of INVENTORY_UNIT_BAND_SELECT_PREFER) {
    if (sameCodeBands.some((b) => bandKindKey(b) === k)) return k;
  }
  const first = sameCodeBands[0];
  return first ? bandKindKey(first) : '';
}

/**
 * 将期望 bandKind 收敛到 {@link inboundUnitBandSelectOptions} 中实际存在的 value，
 * 避免同码合并后默认/回显仍指向已被合并掉的槽（如下拉无 STOCK 却 selectedBandKind=STOCK）。
 */
function coerceBandKindToSelectOption(
  bands: InboundProductUnitBand[],
  desiredKind: string,
): string {
  const desired = String(desiredKind ?? '')
    .trim()
    .toUpperCase();
  if (desired === '') return '';
  if (desired === 'LEGACY') return 'LEGACY';
  const opts = inboundUnitBandSelectOptions(bands);
  if (opts.length === 0) return desired;
  if (opts.some((o) => o.value === desired)) return desired;
  const desiredBand = findInboundUnitBandByKind(bands, desired);
  const code = String(desiredBand?.unitCode ?? '').trim();
  if (code) {
    for (const o of opts) {
      const b = findInboundUnitBandByKind(bands, o.value);
      if (b && String(b.unitCode ?? '').trim() === code) return o.value;
    }
  }
  const first = opts[0];
  return first ? first.value : desired;
}

/**
 * 同码多带推断：可合并时统一 **PACKAGE → SPLIT → STOCK → PURCHASE**；
 * 同码不同倍时按 `conflictPrefer` 顺序取（进货偏采购、非入库偏库存）。
 */
function inferSelectBandKindFromUnitCode(
  bands: InboundProductUnitBand[],
  unitCode: string,
  conflictPrefer: readonly InventoryUnitBandSelectKind[],
  whenEmpty: (bands: InboundProductUnitBand[]) => string,
): string {
  const code = String(unitCode ?? '').trim();
  if (!code || bands.length === 0) return whenEmpty(bands);
  const same = businessBandsWithUnitCode(bands, code);
  if (same.length === 0) {
    const hit = bands.find((b) => String(b.unitCode ?? '').trim() === code);
    return hit ? bandKindKey(hit) : whenEmpty(bands);
  }
  if (canMergeSameUnitCodeBands(same)) {
    return preferredSelectKindAmong(same);
  }
  for (const k of conflictPrefer) {
    if (same.some((b) => bandKindKey(b) === k)) return k;
  }
  return preferredSelectKindAmong(same) || whenEmpty(bands);
}

/** 进货默认：优先 PURCHASE；若该槽已同码合并进下拉他项，则收敛到下拉实际 value。 */
export function defaultInboundUnitBandKind(bands: InboundProductUnitBand[]): string {
  if (bands.length === 0) return '';
  const hit = bands.find((b) => bandKindKey(b) === 'PURCHASE');
  const desired = hit ? 'PURCHASE' : bandKindKey(bands[0]!);
  return coerceBandKindToSelectOption(bands, desired);
}

export function findInboundUnitBandByKind(
  bands: InboundProductUnitBand[] | undefined,
  kind: string,
): InboundProductUnitBand | undefined {
  const k = kind.trim().toUpperCase();
  return (bands ?? []).find((b) => bandKindKey(b) === k);
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
  if (!Number.isFinite(q)) return undefined;
  // 盘点盈亏、损溢等允许负数；比例换算对符号线性成立
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

/** 进货/订货等：按当前计量带写入 purchaseUnit 与展示单价 */
export interface InboundBandKindEditableRow {
  unitBands?: InboundProductUnitBand[];
  selectedBandKind?: string;
  __prevBandKind?: string;
  purchaseUnit?: string;
  unitPrice?: number;
}

export function applyInboundBandKindToRow(
  row: InboundBandKindEditableRow,
  kind: string,
  opts?: { convertFromCurrentPrice?: boolean; fromBandKind?: string },
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
    row.purchaseUnit = code;
  }
  const unitPrice = row.unitPrice ?? 0;
  if (opts?.convertFromCurrentPrice) {
    const fromK = String(opts.fromBandKind ?? '')
      .trim()
      .toUpperCase();
    if (fromK && fromK !== k && fromK !== 'LEGACY') {
      const converted = convertInboundUnitPriceBetweenBandKinds(unitPrice, fromK, k, row.unitBands);
      if (converted !== undefined && Number.isFinite(converted)) {
        row.unitPrice = converted;
        return;
      }
    }
  }
  const resolved = inboundResolveDisplayUnitPrice({
    bands: row.unitBands,
    displayBandKind: k,
    legacyUnitPrice: unitPrice,
  });
  if (resolved !== undefined) {
    row.unitPrice = resolved;
  }
}

/** 明细表单位带切换：数量/单价按带换算（进货单、订货单等同构页面共用） */
export function handleInventoryUnitBandChanged(
  row: InboundBandKindEditableRow,
  args: {
    getQty: () => number;
    setQty: (qty: number) => void;
    onAfterChanged?: () => void;
  },
): void {
  const newK = String(row.selectedBandKind ?? '')
    .trim()
    .toUpperCase();
  const oldK = String(row.__prevBandKind ?? '')
    .trim()
    .toUpperCase();
  const bands = row.unitBands ?? [];
  if (oldK && newK && oldK !== newK && oldK !== 'LEGACY' && newK !== 'LEGACY' && bands.length > 0) {
    const nq = convertInboundQtyBetweenBandKinds(args.getQty(), oldK, newK, bands);
    if (nq !== undefined) {
      args.setQty(nq);
    }
  }
  applyInboundBandKindToRow(row, newK, {
    convertFromCurrentPrice: true,
    fromBandKind: oldK,
  });
  row.__prevBandKind = newK;
  args.onAfterChanged?.();
}

/**
 * 数量单位下拉选项：仅业务四槽（排除 TRACE/DOSE 等）；
 * 同 `unitCode` 且同 `ratioToSplit` 合并为一项，value 取 {@link INVENTORY_UNIT_BAND_SELECT_PREFER}；
 * 同码不同倍不合并（保留各槽，避免静默错算）。
 */
export function inboundUnitBandSelectOptions(
  bands: InboundProductUnitBand[],
): { label: string; value: string }[] {
  const business = (bands ?? []).filter((b) => isInventoryUnitBandSelectKind(bandKindKey(b)));
  const groups = new Map<string, InboundProductUnitBand[]>();
  for (const b of business) {
    const code = String(b.unitCode ?? '').trim();
    const key = code || `__kind:${bandKindKey(b)}`;
    const list = groups.get(key);
    if (list) {
      list.push(b);
    } else {
      groups.set(key, [b]);
    }
  }

  const kindToBand = new Map<string, InboundProductUnitBand>();
  for (const group of groups.values()) {
    if (group.length === 1) {
      const only = group[0]!;
      kindToBand.set(bandKindKey(only), only);
      continue;
    }
    if (canMergeSameUnitCodeBands(group)) {
      const prefer = preferredSelectKindAmong(group);
      const band = group.find((b) => bandKindKey(b) === prefer) ?? group[0]!;
      kindToBand.set(prefer, band);
      continue;
    }
    for (const b of group) {
      kindToBand.set(bandKindKey(b), b);
    }
  }

  const out: { label: string; value: string }[] = [];
  for (const k of INVENTORY_UNIT_BAND_SELECT_PREFER) {
    const b = kindToBand.get(k);
    if (!b) continue;
    const name = String(b.unitName ?? '').trim() || k;
    out.push({ value: k, label: name });
  }
  return out;
}

/**
 * 目录追溯码贴码单位（可游离于五类业务计量带；与 {@code ProductDefinitionDisplayDto} 对齐）。
 */
export interface CatalogTraceUnitHint {
  traceUnitCode?: string;
  traceUnitRatio?: number | string | null;
}

/** 解析目录追溯单位与倍数；缺码或缺正数倍数时返回空。 */
export function resolveCatalogTraceUnitHint(
  catalog?: CatalogTraceUnitHint | null,
  bands?: InboundProductUnitBand[],
): { traceUnitCode: string; traceUnitRatio: number } | undefined {
  if (catalog) {
    const code = String(catalog.traceUnitCode ?? '').trim();
    const ratio = Number(catalog.traceUnitRatio);
    if (code && Number.isFinite(ratio) && ratio > 0) {
      return { traceUnitCode: code, traceUnitRatio: ratio };
    }
  }
  // 兼容仅挂 TRACE 槽、顶层字段暂未透出的选品载荷
  const traceBand = findInboundUnitBandByKind(bands ?? [], 'TRACE');
  const bandCode = String(traceBand?.unitCode ?? '').trim();
  const bandRatio = ratioOf(traceBand);
  if (bandCode && bandRatio !== undefined) {
    return { traceUnitCode: bandCode, traceUnitRatio: bandRatio };
  }
  return undefined;
}

/**
 * 按单位 code 取 ratioToSplit（1 该单位 = 多少拆零）。
 * 同码多带时优先 PACKAGE → SPLIT → STOCK → PURCHASE（与下拉合并优先级一致）。
 * 目录游离追溯单位：传 {@code catalogTrace}，码命中时直接用 {@code traceUnitRatio}（不依赖下拉）。
 */
export function resolveUnitCodeRatioToSplit(
  bands: InboundProductUnitBand[],
  unitCode: string,
  catalogTrace?: CatalogTraceUnitHint | null,
): number | undefined {
  const code = String(unitCode ?? '').trim();
  if (!code) return undefined;
  // 游离追溯单位：不要求出现在 unitBands 内，也不进数量单位下拉
  const catalogHint = resolveCatalogTraceUnitHint(catalogTrace, bands);
  if (catalogHint && catalogHint.traceUnitCode === code) {
    return catalogHint.traceUnitRatio;
  }
  if (bands.length === 0) return undefined;
  for (const k of INVENTORY_UNIT_BAND_SELECT_PREFER) {
    const b = findInboundUnitBandByKind(bands, k);
    if (b && String(b.unitCode ?? '').trim() === code) {
      return ratioOf(b);
    }
  }
  const hit = bands.find((b) => String(b.unitCode ?? '').trim() === code);
  return ratioOf(hit);
}

/** 根据当前行的 unitCode 反推 bandKind（同码可合并时与下拉一致；冲突时偏 PURCHASE）。 */
export function inferInboundBandKindFromUnitCode(
  bands: InboundProductUnitBand[],
  unitCode: string,
): string {
  return inferSelectBandKindFromUnitCode(
    bands,
    unitCode,
    ['PURCHASE', 'PACKAGE', 'STOCK', 'SPLIT'],
    defaultInboundUnitBandKind,
  );
}

// --- 多计量带通用：落库 **SPLIT** 数量（进货/出库等均适用；仅「默认展示带」策略不同） ---

/**
 * 保存时：界面数量为当前 `selectedBandKind` 下数量；持久化为 **SPLIT** 数量并带 SPLIT 的 `unitCode`。
 * 无 `unitBands` 或仍为会话态 `LEGACY` 时，原样保留数量与 `legacyUnitCode`（见文件头：非库内双轨）。
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

/** 保存前校验：非 LEGACY 且存在 SPLIT 带时，必须能换算到 SPLIT（含负数盈亏/损溢）。 */
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
 * 进货详情回填（legacy）：后端 SPLIT 时强制换到 PURCHASE 展示。
 * @deprecated 新页面请用 {@link hydrateInboundDetailQtyFromPersisted}（按保存 unitCode 回显）。
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
 * STOCK 与 PACKAGE 同单位码但倍数不一致时（如均为「盒」），库存数量展示须按 **PACKAGE** 倍数换算（对齐 AS-INV-WHS-004）。
 */
function warehouseStockPackageSameCodeRatioConflict(bands: InboundProductUnitBand[]): boolean {
  const stock = findInboundUnitBandByKind(bands, 'STOCK');
  const pkg = findInboundUnitBandByKind(bands, 'PACKAGE');
  if (!stock || !pkg) return false;
  const stockRatio = ratioOf(stock);
  const packageRatio = ratioOf(pkg);
  if (stockRatio === undefined || packageRatio === undefined) return false;
  const stockCode = String(stock.unitCode ?? '').trim();
  const packageCode = String(pkg.unitCode ?? '').trim();
  return stockCode !== '' && stockCode === packageCode && stockRatio !== packageRatio;
}

/**
 * SPLIT 账面量 → 界面展示计量带：默认可编辑 **STOCK**；同码多带冲突时回落 **PACKAGE**；
 * 再收敛到下拉实际选项（同码同倍合并后可能仅剩 PACKAGE）。
 */
function resolveWarehouseNonInboundSplitDisplayBandKind(bands: InboundProductUnitBand[]): string {
  if (warehouseStockPackageSameCodeRatioConflict(bands)) {
    return coerceBandKindToSelectOption(bands, 'PACKAGE');
  }
  if (ratioOf(findInboundUnitBandByKind(bands, 'STOCK'))) {
    return coerceBandKindToSelectOption(bands, 'STOCK');
  }
  if (ratioOf(findInboundUnitBandByKind(bands, 'PACKAGE'))) {
    return coerceBandKindToSelectOption(bands, 'PACKAGE');
  }
  if (ratioOf(findInboundUnitBandByKind(bands, 'SPLIT'))) {
    return coerceBandKindToSelectOption(bands, 'SPLIT');
  }
  return coerceBandKindToSelectOption(
    bands,
    warehouseNonInboundDefaultBandKindWithoutConflict(bands),
  );
}

function warehouseNonInboundDefaultBandKindWithoutConflict(
  bands: InboundProductUnitBand[],
): string {
  if (bands.length === 0) return 'LEGACY';
  if (ratioOf(findInboundUnitBandByKind(bands, 'STOCK'))) return 'STOCK';
  if (ratioOf(findInboundUnitBandByKind(bands, 'SPLIT'))) return 'SPLIT';
  const k = defaultInboundUnitBandKind(bands);
  return k || 'LEGACY';
}

/**
 * 药库除入库外的业务：有 `unitBands` 时默认选 **STOCK**（无可用 STOCK 则 SPLIT，再退化为进货默认带）。
 * 同码多带倍数冲突时默认 **PACKAGE**；最终收敛到 {@link inboundUnitBandSelectOptions} 的 value。
 */
export function warehouseNonInboundDefaultBandKind(bands: InboundProductUnitBand[]): string {
  if (bands.length === 0) return 'LEGACY';
  if (warehouseStockPackageSameCodeRatioConflict(bands)) {
    return coerceBandKindToSelectOption(bands, 'PACKAGE');
  }
  return coerceBandKindToSelectOption(
    bands,
    warehouseNonInboundDefaultBandKindWithoutConflict(bands),
  );
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
 * 药库界面展示：后端按 **SPLIT** 持久化的数量 → 展示计量带数量（默认可编辑 STOCK；同码多带冲突时用 PACKAGE）。
 * 无 unitBands 时 legacy 倍数优先 **packageUnitRatio**（对齐 AS-INV-WHS-004）。
 */
export function warehouseNonInboundDisplayQtyFromSplit(
  splitQty: number,
  bands: InboundProductUnitBand[],
  legacyStockRatio?: null | number | string,
): number {
  const q = Number(splitQty);
  if (!Number.isFinite(q)) return 0;
  if (bands.length > 0) {
    const displayKind = resolveWarehouseNonInboundSplitDisplayBandKind(bands);
    const displayBand = findInboundUnitBandByKind(bands, displayKind);
    if (displayBand && ratioOf(displayBand)) {
      const converted = convertInboundQtyBetweenBandKinds(q, 'SPLIT', displayKind, bands);
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
 * 同码多带（如盒）可合并时与下拉一致（PACKAGE 优先）；同码不同倍时优先 **STOCK**，再 SPLIT / PACKAGE / PURCHASE。
 */
export function inferWarehouseNonInboundBandKindFromUnitCode(
  bands: InboundProductUnitBand[],
  unitCode: string,
): string {
  return inferSelectBandKindFromUnitCode(
    bands,
    unitCode,
    ['STOCK', 'SPLIT', 'PACKAGE', 'PURCHASE'],
    warehouseNonInboundDefaultBandKind,
  );
}

/** 药库出库等详情回显：SPLIT 数量按保存时 `unitCode` 换算；同码推断见 {@link inferWarehouseNonInboundBandKindFromUnitCode}。 */
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
 * 供应单行（请领→出库、出库详情）数量回显：`itemQuantity` 为 SPLIT，`unitCode` 为保存时录入单位。
 * 兼容旧请领单：`item_quantity` 仍为录入带展示数且 `unit_code` 非拆零码时直读数量。
 */
export function hydrateWarehouseSupplyLineQtyForGrid(args: {
  bands: InboundProductUnitBand[];
  persistedItemQuantity: number;
  persistedUnitCode?: string;
}): { bandKind: string; displayQty: number; unitCode: string } {
  const bandsArr = args.bands ?? [];
  const pq = Number(args.persistedItemQuantity);
  const rawQty = Number.isFinite(pq) ? pq : 0;
  const persistedCode = String(args.persistedUnitCode ?? '').trim();

  if (bandsArr.length === 0) {
    return { displayQty: rawQty, bandKind: 'LEGACY', unitCode: persistedCode };
  }

  const splitBand = findInboundUnitBandByKind(bandsArr, 'SPLIT');
  const splitCode = String(splitBand?.unitCode ?? '').trim();

  if (persistedCode && splitCode && persistedCode !== splitCode) {
    const legacyKind = inferWarehouseNonInboundBandKindFromUnitCode(bandsArr, persistedCode);
    if (legacyKind !== 'SPLIT') {
      const legacyBand = findInboundUnitBandByKind(bandsArr, legacyKind);
      const asDisplayFromSplit = convertInboundQtyBetweenBandKinds(
        rawQty,
        'SPLIT',
        legacyKind,
        bandsArr,
      );
      if (
        legacyBand &&
        rawQty >= 1 &&
        Number.isInteger(rawQty) &&
        asDisplayFromSplit !== undefined &&
        asDisplayFromSplit > 0 &&
        asDisplayFromSplit < 1
      ) {
        return {
          displayQty: rawQty,
          bandKind: legacyKind,
          unitCode: String(legacyBand.unitCode ?? '').trim() || persistedCode,
        };
      }
    }
  }

  return hydrateWarehouseDetailQtyFromPersisted({
    bands: bandsArr,
    persistedItemQuantity: rawQty,
    persistedUnitCode: persistedCode,
  });
}

/**
 * 详情回填（legacy）：后端 SPLIT 时强制换到 STOCK 展示。
 * @deprecated 新页面请用 {@link hydrateWarehouseDetailQtyFromPersisted}（按保存 unitCode 回显）。
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

  const treatAsSplitPersisted =
    splitCode !== '' && (persistedCode === '' || persistedCode === splitCode);

  if (treatAsSplitPersisted) {
    const displayKind = resolveWarehouseNonInboundSplitDisplayBandKind(bandsArr);
    const displayBand = findInboundUnitBandByKind(bandsArr, displayKind);
    if (displayBand && ratioOf(displayBand)) {
      const displayQty = convertInboundQtyBetweenBandKinds(
        persistedQty,
        'SPLIT',
        displayKind,
        bandsArr,
      );
      const dq =
        displayQty !== undefined && Number.isFinite(displayQty) ? displayQty : persistedQty;
      const displayCode = String(displayBand.unitCode ?? '').trim();
      return {
        displayQty: dq,
        bandKind: displayKind,
        unitCode: displayCode || persistedCode,
      };
    }
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
