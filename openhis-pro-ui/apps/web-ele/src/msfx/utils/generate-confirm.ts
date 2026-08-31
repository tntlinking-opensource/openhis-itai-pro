import type { MsfxUpstreamBillRowDto, MsfxUpstreamTraceCodeItem } from '#/api/msfx/types';
import { parseMsfxPkgToSplitRatioFromSpec } from '#/msfx/utils/match-upstream-drug-to-product';
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';
import {
  convertInboundQtyBetweenBandKinds,
  defaultInboundUnitBandKind,
  findInboundUnitBandByKind,
  inboundUnitBandSelectOptions,
  resolveCatalogTraceUnitHint,
} from '#/utils/inventory/inbound-product-unit-bands';
import { computeRequiredTraceCount } from '#/utils/trace/trace-line-scan-count';

/** 确认生成面板输出的一行（已选院内品 + 数量/计量带） */
export type MsfxGenerateConfirmLine = {
  drug: MsfxUpstreamBillRowDto;
  product: Record<string, unknown>;
  purchaseQty?: number;
  selectedBandKind: string;
};

/** OrderCalcRule：1/3=拆零单位可拆；2/4=包装单位 */
const SPLIT_PART_ATTR_CODES = new Set(['1', '3']);
const PACKAGE_PART_ATTR_CODES = new Set(['2', '4']);

function uniqCodes(list: string[]): string[] {
  const out: string[] = [];
  const seen = new Set<string>();
  for (const c of list) {
    if (seen.has(c)) continue;
    seen.add(c);
    out.push(c);
  }
  return out;
}

function ratioOfBand(band: InboundProductUnitBand | undefined): number | undefined {
  if (!band) return undefined;
  const r = Number(band.ratioToSplit);
  return Number.isFinite(r) && r > 0 ? r : undefined;
}

/**
 * 将写入进货明细的正式追溯码。
 * 同时有中大/小码时看 {@code inboundCodeRank}：PACK=中大，SMALL=小码。
 */
export function resolveMsfxWriteableTraceCodes(
  drug: Pick<MsfxUpstreamBillRowDto, 'traceCodes' | 'packTraceCodes' | 'inboundCodeRank'>,
): string[] {
  const smallList = (drug.traceCodes ?? []).map((c) => String(c ?? '').trim()).filter(Boolean);
  const packList = (drug.packTraceCodes ?? []).map((c) => String(c ?? '').trim()).filter(Boolean);
  const small = uniqCodes(smallList);
  const pack = uniqCodes(packList);
  const prefer = String(drug.inboundCodeRank ?? '')
    .trim()
    .toUpperCase();
  if (prefer === 'PACK' && pack.length > 0) return pack;
  if (prefer === 'SMALL' && small.length > 0) return small;
  if (pack.length === 0) return small;
  const packSet = new Set(pack);
  const sameAsFallback =
    small.length > 0 && small.length === pack.length && small.every((c) => packSet.has(c));
  if (sameAsFallback || small.length === 0) return pack;
  return small;
}

/**
 * 可写入码是否落在中大码（未展开小码、或小码与中大码回填同一批）。
 */
export function isMsfxWriteablePackLevelCodes(
  drug: Pick<MsfxUpstreamBillRowDto, 'traceCodes' | 'packTraceCodes' | 'inboundCodeRank'>,
): boolean {
  const pack = uniqCodes(
    (drug.packTraceCodes ?? []).map((c) => String(c ?? '').trim()).filter(Boolean),
  );
  if (pack.length === 0) return false;
  const writeable = resolveMsfxWriteableTraceCodes(drug);
  if (writeable.length === 0) return false;
  const packSet = new Set(pack);
  return writeable.length === pack.length && writeable.every((c) => packSet.has(c));
}

/** 可写入码条数（与 {@link resolveMsfxWriteableTraceCodes} 同口径） */
export function countMsfxWriteableTraceCodes(
  drug: Pick<MsfxUpstreamBillRowDto, 'traceCodes' | 'packTraceCodes' | 'inboundCodeRank'>,
): number {
  return resolveMsfxWriteableTraceCodes(drug).length;
}

/** 中大码与小码是否两套都在，可供确认窗选择入库档。 */
export function canChooseMsfxInboundCodeRank(
  drug: Pick<MsfxUpstreamBillRowDto, 'traceCodeItems' | 'traceCodes' | 'packTraceCodes'>,
): boolean {
  const split = splitMsfxTraceCodesByRank(drug);
  const packN = split.large.length + split.medium.length;
  return packN > 0 && split.small.length > 0;
}

/** 追溯码包装级别：大 / 中 / 小 */
export type MsfxTracePackRank = 'LARGE' | 'MEDIUM' | 'SMALL';

/** 无层级标记且码很少时，先当中大（与后端稀疏试探同一门槛）。 */
const UNKNOWN_PACK_PROBE_MAX = 15;

function parseMsfxPositiveAmount(raw: unknown): number {
  const n = Number(String(raw ?? '').trim());
  return Number.isFinite(n) && n > 0 ? n : 0;
}

type MsfxTraceRankFields = Pick<
  MsfxUpstreamTraceCodeItem,
  'isSmallest' | 'codePackLevel' | 'pkgAmount'
>;

/**
 * 是否已有平台最小/包装标记（不含 {@code code_level}：两套口径不能单独判大/小）。
 */
export function hasExplicitMsfxTracePackRank(item: MsfxTraceRankFields): boolean {
  return tryResolveExplicitMsfxTracePackRank(item) !== null;
}

/**
 * 平台是否明确标记为最小包装。无 {@code is_smallest}/{@code code_pack_level} 时不当小码。
 */
export function isMsfxPlatformSmallestCode(item: MsfxTraceRankFields): boolean {
  return tryResolveExplicitMsfxTracePackRank(item) === 'SMALL';
}

/**
 * 只认 {@code is_smallest} / {@code code_pack_level} / {@code pkg_amount&gt;1}。
 * 无标记返回 null，不默认小码。
 */
export function tryResolveExplicitMsfxTracePackRank(
  item: MsfxTraceRankFields,
): MsfxTracePackRank | null {
  const smallest = String(item.isSmallest ?? '').trim();
  if (smallest) {
    const s = smallest.toLowerCase();
    if (s === 'y' || s === '1' || s === 'true') return 'SMALL';
    if (s === 'n' || s === '0' || s === 'false') {
      return rankFromPackLevel(item, 'MEDIUM');
    }
  }
  const fromPack = rankFromPackLevel(item, null);
  if (fromPack) return fromPack;
  if (parseMsfxPositiveAmount(item.pkgAmount) > 1) return 'MEDIUM';
  return null;
}

/**
 * 解析单码包装级别。无平台标记时记为中（可再拉），不默认小。
 */
export function resolveMsfxTracePackRank(item: MsfxTraceRankFields): MsfxTracePackRank {
  return tryResolveExplicitMsfxTracePackRank(item) ?? 'MEDIUM';
}

function rankFromPackLevel(
  item: Pick<MsfxUpstreamTraceCodeItem, 'codePackLevel'>,
  fallback: MsfxTracePackRank | null,
): MsfxTracePackRank | null {
  const pack = String(item.codePackLevel ?? '').trim();
  if (pack === '1') return 'SMALL';
  if (pack === '2') return 'MEDIUM';
  if (pack === '3') return 'LARGE';
  return fallback;
}

export function msfxTracePackRankLabel(rank: MsfxTracePackRank): string {
  if (rank === 'LARGE') return '大';
  if (rank === 'MEDIUM') return '中';
  return '小';
}

export type MsfxRankedTraceCode = {
  code: string;
  rank: MsfxTracePackRank;
  item: MsfxUpstreamTraceCodeItem;
};

/**
 * 将行上追溯码按大/中/小拆开（去重保序）。
 * 有 items 时也看 packTraceCodes（后端可能已把无标记码提成中大）。
 * 无标记且码很少时当中，避免 1 个盒码被画成小码后藏掉「拉取」。
 */
export function splitMsfxTraceCodesByRank(
  drug: Pick<MsfxUpstreamBillRowDto, 'traceCodeItems' | 'traceCodes' | 'packTraceCodes'>,
): { large: MsfxRankedTraceCode[]; medium: MsfxRankedTraceCode[]; small: MsfxRankedTraceCode[] } {
  const large: MsfxRankedTraceCode[] = [];
  const medium: MsfxRankedTraceCode[] = [];
  const small: MsfxRankedTraceCode[] = [];
  const seen = new Set<string>();
  const push = (row: MsfxRankedTraceCode) => {
    if (!row.code || seen.has(row.code)) return;
    seen.add(row.code);
    if (row.rank === 'LARGE') large.push(row);
    else if (row.rank === 'MEDIUM') medium.push(row);
    else small.push(row);
  };

  const pack = uniqCodes(
    (drug.packTraceCodes ?? []).map((c) => String(c ?? '').trim()).filter(Boolean),
  );
  const rawSmall = uniqCodes(
    (drug.traceCodes ?? []).map((c) => String(c ?? '').trim()).filter(Boolean),
  );
  const packSet = new Set(pack);
  const items = drug.traceCodeItems ?? [];
  if (items.length > 0) {
    const unmarked: MsfxUpstreamTraceCodeItem[] = [];
    let explicitSmall = 0;
    for (const item of items) {
      const code = String(item.code ?? '').trim();
      if (!code) continue;
      const explicit = tryResolveExplicitMsfxTracePackRank(item);
      if (explicit === 'SMALL') explicitSmall += 1;
      if (explicit) {
        push({ code, rank: explicit, item: { ...item, code } });
        continue;
      }
      unmarked.push({ ...item, code });
    }
    const sparseUnmarked =
      unmarked.length > 0 && unmarked.length <= UNKNOWN_PACK_PROBE_MAX && explicitSmall === 0;
    for (const item of unmarked) {
      const code = String(item.code ?? '').trim();
      const rank = packSet.has(code) || sparseUnmarked ? 'MEDIUM' : 'SMALL';
      push({ code, rank, item });
    }
    return { large, medium, small };
  }

  for (const code of pack) {
    push({
      code,
      rank: 'MEDIUM',
      item: { code, isSmallest: 'N', codePackLevel: '2' },
    });
  }
  const sameAsFallback =
    rawSmall.length > 0 && rawSmall.length === pack.length && rawSmall.every((c) => packSet.has(c));
  if (!sameAsFallback) {
    for (const code of rawSmall) {
      if (packSet.has(code)) continue;
      push({
        code,
        rank: 'SMALL',
        item: { code, isSmallest: 'Y', codePackLevel: '1' },
      });
    }
  }
  if (pack.length === 0) {
    const asPack = rawSmall.length > 0 && rawSmall.length <= UNKNOWN_PACK_PROBE_MAX;
    for (const code of rawSmall) {
      push({
        code,
        rank: asPack ? 'MEDIUM' : 'SMALL',
        item: asPack
          ? { code, isSmallest: 'N', codePackLevel: '2' }
          : { code, isSmallest: 'Y', codePackLevel: '1' },
      });
    }
  }
  return { large, medium, small };
}

/** 按级别写回平台字段，便于下次拆分。 */
export function stampMsfxTraceRank(
  item: MsfxUpstreamTraceCodeItem,
  rank: MsfxTracePackRank,
): MsfxUpstreamTraceCodeItem {
  if (rank === 'SMALL') {
    return { ...item, isSmallest: 'Y', codePackLevel: '1', codeLevel: '3' };
  }
  if (rank === 'MEDIUM') {
    return { ...item, isSmallest: 'N', codePackLevel: '2', codeLevel: '2' };
  }
  return { ...item, isSmallest: 'N', codePackLevel: '3', codeLevel: '1' };
}

/**
 * 解析院内品门诊拆分属性码（OrderCalcRule 1～4）。
 */
export function resolveMsfxProductPartAttributeCode(
  product?: Record<string, unknown> | null,
): string {
  if (!product) return '';
  return String(product.partAttributeEnum ?? '').trim();
}

/** 拆分属性是否为「拆零单位」 */
export function isMsfxSplitPartAttribute(partAttributeEnum?: string | null): boolean {
  return SPLIT_PART_ATTR_CODES.has(String(partAttributeEnum ?? '').trim());
}

/** 拆分属性是否为「包装单位」 */
export function isMsfxPackagePartAttribute(partAttributeEnum?: string | null): boolean {
  return PACKAGE_PART_ATTR_CODES.has(String(partAttributeEnum ?? '').trim());
}

/** 每码对应拆零量：目录追溯倍数 → PACKAGE 倍数 → 规格「盒10支」 */
export function resolveMsfxPerTraceSplitQty(args: {
  drug?: Pick<MsfxUpstreamBillRowDto, 'packageSpec'>;
  product?: Record<string, unknown> | null;
  bands?: InboundProductUnitBand[];
}): number {
  const bands = args.bands ?? [];
  const catalog = args.product
    ? {
        traceUnitCode: String(args.product.traceUnitCode ?? '').trim() || undefined,
        traceUnitRatio: args.product.traceUnitRatio as number | string | null | undefined,
      }
    : null;
  const hint = resolveCatalogTraceUnitHint(catalog, bands);
  if (hint && hint.traceUnitRatio > 0) return hint.traceUnitRatio;

  for (const kind of ['PACKAGE', 'PURCHASE'] as const) {
    const r = ratioOfBand(findInboundUnitBandByKind(bands, kind));
    if (r !== undefined && r > 1) return r;
  }

  const fromSpec = parseMsfxPkgToSplitRatioFromSpec(args.drug?.packageSpec);
  if (fromSpec !== undefined && fromSpec > 1) return fromSpec;
  return 1;
}

/**
 * 入库展示数量 → 拆零数量。
 */
export function resolveMsfxDisplayQtyAsSplit(args: {
  purchaseQty: number;
  selectedBandKind?: string | null;
  bands?: InboundProductUnitBand[];
  drug?: Pick<MsfxUpstreamBillRowDto, 'packageSpec'>;
}): number | undefined {
  const qty = Number(args.purchaseQty);
  if (!Number.isFinite(qty) || qty <= 0) return undefined;
  const kind = String(args.selectedBandKind ?? '')
    .trim()
    .toUpperCase();
  const bands = args.bands ?? [];
  if (!kind || kind === 'SPLIT' || kind === 'LEGACY' || bands.length === 0) {
    if (kind === 'SPLIT') return qty;
    // LEGACY / 无带：若规格有盒→支倍数且数量像包装量，不在此臆测；直接当拆零
    return qty;
  }
  const converted = convertInboundQtyBetweenBandKinds(qty, kind, 'SPLIT', bands);
  return converted !== undefined ? converted : qty;
}

/** 所选计量带是否为拆零阶（SPLIT，或倍数=1 的「支」）。 */
export function isMsfxSelectedSplitBand(
  selectedBandKind?: string | null,
  bands?: InboundProductUnitBand[],
): boolean {
  const kind = String(selectedBandKind ?? '')
    .trim()
    .toUpperCase();
  if (kind === 'SPLIT') return true;
  const r = ratioOfBand(findInboundUnitBandByKind(bands ?? [], kind));
  return r === 1;
}

/**
 * 中大码覆盖拆零量时的每码倍数：优先包装/采购倍数或规格「盒10支」。
 * 目录追溯单位若已是拆零（倍数=1），不能拿来对盒码做 1:1。
 */
export function resolveMsfxPackCodeSplitMultiplier(args: {
  drug?: Pick<MsfxUpstreamBillRowDto, 'packageSpec'>;
  product?: Record<string, unknown> | null;
  bands?: InboundProductUnitBand[];
}): number {
  const bands = args.bands ?? [];
  for (const kind of ['PACKAGE', 'PURCHASE'] as const) {
    const r = ratioOfBand(findInboundUnitBandByKind(bands, kind));
    if (r !== undefined && r > 1) return r;
  }
  const fromSpec = parseMsfxPkgToSplitRatioFromSpec(args.drug?.packageSpec);
  if (fromSpec !== undefined && fromSpec > 1) return fromSpec;
  const catalog = resolveMsfxPerTraceSplitQty(args);
  return catalog > 1 ? catalog : 1;
}

/**
 * 应扫码数：
 * - 中大回填：ceil(拆零量 / 每盒拆零)，如 500 支 / 10 = 50
 * - 已展开小码：走进货 Gate（同阶 1:1 或 ceil(拆零/每码拆零)）
 */
export function resolveMsfxExpectedTraceCount(args: {
  purchaseQty: number;
  selectedBandKind?: string | null;
  bands?: InboundProductUnitBand[];
  product?: Record<string, unknown> | null;
  drug?: Pick<MsfxUpstreamBillRowDto, 'packageSpec' | 'traceCodes' | 'packTraceCodes'>;
}): number | undefined {
  const splitQty = resolveMsfxDisplayQtyAsSplit(args);
  if (splitQty === undefined) return undefined;

  // 拆零入库 + 未展开中大码：码贴在盒上，应扫=盒数，不是支数
  if (args.drug && isMsfxWriteablePackLevelCodes(args.drug)) {
    const perPack = resolveMsfxPackCodeSplitMultiplier(args);
    if (perPack > 1) {
      return Math.max(1, Math.ceil(splitQty / perPack));
    }
  }

  const perTrace = resolveMsfxPerTraceSplitQty(args);
  const band = findInboundUnitBandByKind(args.bands ?? [], String(args.selectedBandKind ?? ''));
  const opUnit = String(band?.unitCode ?? '').trim();
  const catalog = args.product ? String(args.product.traceUnitCode ?? '').trim() : '';
  return computeRequiredTraceCount({
    operationQty: Number(args.purchaseQty),
    operationUnitCode: opUnit || undefined,
    traceUnitCode: catalog || undefined,
    operationSplitQty: splitQty,
    perTraceSplitQty: perTrace,
    mode: 'inbound',
  });
}

/**
 * 拆零属性 + 中大回填：若追溯倍数&gt;1（码贴包装），允许中大码；
 * 仅当倍数=1（码应贴拆零）仍只有中大时拦截。
 */
export function isMsfxSplitAttrBlockedByPackCodes(
  drug: Pick<MsfxUpstreamBillRowDto, 'traceCodes' | 'packTraceCodes' | 'packageSpec'>,
  product?: Record<string, unknown> | null,
  bands?: InboundProductUnitBand[],
): boolean {
  const part = resolveMsfxProductPartAttributeCode(product);
  if (!isMsfxSplitPartAttribute(part)) return false;
  if (!isMsfxWriteablePackLevelCodes(drug)) return false;
  const perPack = resolveMsfxPackCodeSplitMultiplier({ drug, product, bands });
  // 码贴在盒/包装上：中大码即正式贴码单位，拆零入库不拦
  if (perPack > 1) return false;
  return true;
}

/**
 * 入库数量与应扫码数是否不一致（不一致则禁止带入）。
 * 拆零入库 + 中大码：按「盒码 × 每盒支数」覆盖拆零量（50×10=500），不要求 1 支 1 码。
 */
export function isMsfxQtyCodeMismatch(
  purchaseQty: number | undefined | null,
  drug: Pick<MsfxUpstreamBillRowDto, 'traceCodes' | 'packTraceCodes' | 'packageSpec'>,
  opts?: {
    selectedBandKind?: string | null;
    bands?: InboundProductUnitBand[];
    product?: Record<string, unknown> | null;
  },
): boolean {
  const codeN = countMsfxWriteableTraceCodes(drug);
  const qty = Number(purchaseQty);
  if (!Number.isFinite(qty) || qty <= 0) return true;
  if (codeN <= 0) return true;

  const bands = opts?.bands ?? [];
  const splitQty = resolveMsfxDisplayQtyAsSplit({
    purchaseQty: qty,
    selectedBandKind: opts?.selectedBandKind,
    bands,
    drug,
  });

  // 拆零 + 中大：50 盒码覆盖 500 支是正常的
  if (
    isMsfxWriteablePackLevelCodes(drug) &&
    isMsfxSelectedSplitBand(opts?.selectedBandKind, bands)
  ) {
    const perPack = resolveMsfxPackCodeSplitMultiplier({
      drug,
      product: opts?.product,
      bands,
    });
    if (perPack > 1 && splitQty !== undefined) {
      return Math.abs(splitQty - codeN * perPack) > 0.000_1;
    }
  }

  const expected = resolveMsfxExpectedTraceCount({
    purchaseQty: qty,
    selectedBandKind: opts?.selectedBandKind,
    bands,
    product: opts?.product,
    drug,
  });
  if (expected === undefined) {
    return Math.abs(qty - codeN) > 0.000_1;
  }
  return codeN !== expected;
}

/** 将期望 bandKind 收敛到下拉实际存在的 value。 */
function coerceMsfxBandKindToSelectOption(
  bands: InboundProductUnitBand[],
  desiredKind: string,
): string {
  const desired = String(desiredKind ?? '')
    .trim()
    .toUpperCase();
  if (!desired) return '';
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
  return opts[0]?.value ?? desired;
}

/**
 * 确认生成时的默认计量带：
 * - 拆零属性：优先 **SPLIT**（50盒→500支）
 * - 包装属性且仍为中大回填：优先 **PACKAGE**
 * - 其余：记忆 / 进货默认
 */
export function resolveMsfxConfirmBandKind(args: {
  drug: Pick<MsfxUpstreamBillRowDto, 'traceCodes' | 'packTraceCodes' | 'pkgUnitDesc'>;
  bands: InboundProductUnitBand[];
  memoryBandKind?: string | null;
  product?: Record<string, unknown> | null;
}): string {
  const bands = args.bands ?? [];
  if (bands.length === 0) return 'PURCHASE';

  const mem = String(args.memoryBandKind ?? '')
    .trim()
    .toUpperCase();
  const part = resolveMsfxProductPartAttributeCode(args.product);
  const packLevel = isMsfxWriteablePackLevelCodes(args.drug);
  const splitAttr = isMsfxSplitPartAttribute(part);
  const packageAttr = isMsfxPackagePartAttribute(part);
  const upstreamUnit = String(args.drug.pkgUnitDesc ?? '')
    .trim()
    .toLowerCase();

  const matchUpstreamUnitKind = (): string | undefined => {
    if (!upstreamUnit) return undefined;
    for (const prefer of ['PACKAGE', 'PURCHASE', 'STOCK'] as const) {
      const b = findInboundUnitBandByKind(bands, prefer);
      const name = String(b?.unitName ?? '')
        .trim()
        .toLowerCase();
      if (name && name === upstreamUnit) return prefer;
    }
    return undefined;
  };

  if (splitAttr && findInboundUnitBandByKind(bands, 'SPLIT')) {
    return coerceMsfxBandKindToSelectOption(bands, 'SPLIT');
  }

  if (packLevel && !splitAttr) {
    const byUpstream = matchUpstreamUnitKind();
    if (byUpstream) {
      return coerceMsfxBandKindToSelectOption(bands, byUpstream);
    }
    for (const prefer of ['PACKAGE', 'PURCHASE'] as const) {
      if (!findInboundUnitBandByKind(bands, prefer)) continue;
      return coerceMsfxBandKindToSelectOption(bands, prefer);
    }
    if (mem && mem !== 'SPLIT' && findInboundUnitBandByKind(bands, mem)) {
      return coerceMsfxBandKindToSelectOption(bands, mem);
    }
    return coerceMsfxBandKindToSelectOption(bands, defaultInboundUnitBandKind(bands) || 'PURCHASE');
  }

  if (!packLevel && packageAttr && findInboundUnitBandByKind(bands, 'PACKAGE')) {
    return coerceMsfxBandKindToSelectOption(bands, 'PACKAGE');
  }

  if (mem && findInboundUnitBandByKind(bands, mem)) {
    return coerceMsfxBandKindToSelectOption(bands, mem);
  }
  return coerceMsfxBandKindToSelectOption(bands, defaultInboundUnitBandKind(bands) || 'PURCHASE');
}
