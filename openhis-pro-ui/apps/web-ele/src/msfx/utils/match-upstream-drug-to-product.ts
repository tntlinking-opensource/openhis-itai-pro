/**
 * 上游出库药品行 ↔ 进货选品：按名称/规格/厂家打分挑选。
 */
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';
import {
  convertInboundQtyBetweenBandKinds,
  findInboundUnitBandByKind,
} from '#/utils/inventory/inbound-product-unit-bands';

export type MsfxDrugLineForMatch = {
  drugName?: string;
  physicName?: string;
  prodName?: string;
  packageSpec?: string;
  /** 上游包装单位文案，如「盒」 */
  pkgUnitDesc?: string;
  productEntName?: string;
  leastPkgAmount?: string;
  leastPrepnAmount?: string;
  produceBatchNo?: string;
  produceDate?: string;
  validEndDate?: string;
  approvalNo?: string;
  traceCodes?: string[];
};

export function resolveMsfxDrugDisplayName(drug: MsfxDrugLineForMatch): string {
  return (
    String(drug.drugName ?? '').trim() ||
    String(drug.physicName ?? '').trim() ||
    String(drug.prodName ?? '').trim()
  );
}

export function parseMsfxPkgQty(drug: MsfxDrugLineForMatch): number | undefined {
  const raw = String(drug.leastPkgAmount ?? '')
    .trim()
    .replace(/,/g, '');
  if (!raw) return undefined;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

export function parseMsfxPrepnQty(drug: MsfxDrugLineForMatch): number | undefined {
  const raw = String(drug.leastPrepnAmount ?? '')
    .trim()
    .replace(/,/g, '');
  if (!raw) return undefined;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

/**
 * 从规格文案解析「1 盒 = N 支/片…」倍数（如「盒10支」「10支/盒」）。
 */
export function parseMsfxPkgToSplitRatioFromSpec(packageSpec?: string): number | undefined {
  const s = String(packageSpec ?? '')
    .replace(/\s+/g, '')
    .trim();
  if (!s) return undefined;
  const patterns = [
    /盒[x×*]?\s*(\d+)\s*(支|片|粒|袋|瓶|枚)/u,
    /(\d+)\s*(支|片|粒|袋|瓶|枚)\s*[/／]\s*盒/u,
    /1\s*盒\s*[=＝]\s*(\d+)\s*(支|片|粒|袋|瓶|枚)/u,
  ];
  for (const re of patterns) {
    const m = s.match(re);
    if (!m?.[1]) continue;
    const n = Number(m[1]);
    if (Number.isFinite(n) && n > 1) return n;
  }
  return undefined;
}

function ratioOfBand(band: InboundProductUnitBand | undefined): number | undefined {
  if (!band) return undefined;
  const r = Number(band.ratioToSplit);
  return Number.isFinite(r) && r > 0 ? r : undefined;
}

function normUnitLabel(v: unknown): string {
  return String(v ?? '')
    .replace(/\s+/g, '')
    .trim()
    .toLowerCase();
}

/** 解析「盒→拆零」倍数：院内 PACKAGE/PURCHASE 倍数，或规格「盒10支」。 */
function resolveMsfxPkgToSplitMultiplier(
  drug: MsfxDrugLineForMatch,
  bands: InboundProductUnitBand[],
): number | undefined {
  for (const from of ['PACKAGE', 'PURCHASE'] as const) {
    const r = ratioOfBand(findInboundUnitBandByKind(bands, from));
    if (r !== undefined && r > 1) return r;
  }
  return parseMsfxPkgToSplitRatioFromSpec(drug.packageSpec);
}

/**
 * 上游 `leastPkgAmount` 恒为包装件数（如 50 盒）。
 * 按所选计量带换算：选「支/拆零」→ 50×10=500；选「盒/包装」→ 50。
 * 不依赖 bandKind 是否叫 SPLIT（同码合并后下拉 value 可能是 PACKAGE 但展示「支」）。
 */
export function resolveMsfxQtyForBandKind(
  drug: MsfxDrugLineForMatch,
  bandKind?: string | null,
  bands?: InboundProductUnitBand[],
): number | undefined {
  const kind = String(bandKind ?? '')
    .trim()
    .toUpperCase();
  const pkgQty = parseMsfxPkgQty(drug);
  const prepnQty = parseMsfxPrepnQty(drug);
  const bandsArr = bands ?? [];
  const selected = kind ? findInboundUnitBandByKind(bandsArr, kind) : undefined;
  const selectedRatio = ratioOfBand(selected);
  const upstreamUnit = normUnitLabel(drug.pkgUnitDesc);
  const selectedUnit = normUnitLabel(selected?.unitName);

  if (pkgQty === undefined) {
    return prepnQty;
  }

  // 所选单位与上游包装单位同名（都是「盒」）→ 直接用包装量
  if (upstreamUnit && selectedUnit && upstreamUnit === selectedUnit) {
    return pkgQty;
  }

  // 明确选包装/采购/库存槽，且单位名也是包装侧 → 包装量
  // （若单位名是「支」则继续往下换成拆零量，避免同码合并成 PACKAGE 却展示支）
  const packLikeKind = kind === 'PACKAGE' || kind === 'PURCHASE' || kind === 'STOCK';
  if (
    packLikeKind &&
    selectedRatio !== undefined &&
    selectedRatio > 1 &&
    !(selectedUnit && upstreamUnit && selectedUnit !== upstreamUnit)
  ) {
    return pkgQty;
  }

  // 需要拆零量：SPLIT、或所选倍数=1、或单位名与上游包装不一致（盒→支）
  const needSplitQty =
    kind === 'SPLIT' ||
    kind === '' ||
    kind === 'LEGACY' ||
    selectedRatio === 1 ||
    (!!upstreamUnit && !!selectedUnit && upstreamUnit !== selectedUnit) ||
    (!selectedUnit && kind === 'SPLIT');

  if (!needSplitQty && packLikeKind) {
    return pkgQty;
  }

  // 上游误把盒数写入制剂量且更大时才信制剂量
  if (prepnQty !== undefined && prepnQty > pkgQty + 0.000_1) {
    return prepnQty;
  }

  // 计量带换算：PACKAGE/PURCHASE → 目标带
  if (bandsArr.length > 0 && kind && kind !== 'LEGACY') {
    for (const from of ['PACKAGE', 'PURCHASE'] as const) {
      const fromRatio = ratioOfBand(findInboundUnitBandByKind(bandsArr, from));
      if (fromRatio === undefined || fromRatio <= 1) continue;
      const converted = convertInboundQtyBetweenBandKinds(pkgQty, from, kind, bandsArr);
      if (converted !== undefined) return converted;
    }
  }

  const mult = resolveMsfxPkgToSplitMultiplier(drug, bandsArr);
  if (mult !== undefined && mult > 1) {
    return pkgQty * mult;
  }

  return pkgQty;
}

function normalizeMatchText(v: unknown): string {
  return String(v ?? '')
    .replace(/\s+/g, '')
    .trim()
    .toLowerCase();
}

/** 常见剂型/修饰后缀，剥离后便于「布地奈德片」≈「布地奈德」 */
const DOSAGE_FORM_SUFFIX_RE =
  /(肠溶缓释|缓释肠溶|分散|咀嚼|泡腾|控释|缓释|肠溶|薄膜衣|糖衣)?(片|胶囊|软胶囊|颗粒|丸|散|膏|栓|滴丸|口服液|注射液|注射用|粉针|混悬液|溶液|糖浆|滴眼液|喷雾剂|吸入剂|贴剂|凝胶|乳膏|软膏|干混悬剂)$/u;

/**
 * 去掉剂型后缀后的药名主干（用于检索与模糊匹配）。
 */
export function stripMsfxDrugDosageForm(name: string): string {
  const n = normalizeMatchText(name);
  if (!n) return '';
  const stripped = n.replace(DOSAGE_FORM_SUFFIX_RE, '');
  return stripped.length >= 2 ? stripped : n;
}

/**
 * 自动匹配用的检索词：全名 → 主干 → 批准文号（调用方可按序尝试）。
 */
export function resolveMsfxMatchSearchKeywords(drug: MsfxDrugLineForMatch): string[] {
  const full = resolveMsfxDrugDisplayName(drug);
  const stem = stripMsfxDrugDosageForm(full);
  const approval = String(drug.approvalNo ?? '').trim();
  const out: string[] = [];
  const push = (v: string) => {
    const t = String(v ?? '').trim();
    if (!t) return;
    if (!out.some((x) => normalizeMatchText(x) === normalizeMatchText(t))) {
      out.push(t);
    }
  };
  push(full);
  if (stem && normalizeMatchText(stem) !== normalizeMatchText(full)) {
    push(stem);
  }
  push(approval);
  return out;
}

function scoreNamePair(upstreamName: string, productName: string): number {
  const u = normalizeMatchText(upstreamName);
  const p = normalizeMatchText(productName);
  if (!u || !p) return 0;
  if (u === p) return 100;
  const us = stripMsfxDrugDosageForm(u);
  const ps = stripMsfxDrugDosageForm(p);
  if (us && ps && us === ps) return 88;
  if (p.includes(u) || u.includes(p)) return 72;
  if (us.length >= 2 && ps.length >= 2 && (ps.includes(us) || us.includes(ps))) return 64;
  return 0;
}

function bestNameScore(drug: MsfxDrugLineForMatch, c: Record<string, unknown>): number {
  const up = resolveMsfxDrugDisplayName(drug);
  const pn = String(c.productName ?? '');
  const mn = String(c.merchandiseName ?? '');
  return Math.max(scoreNamePair(up, pn), scoreNamePair(up, mn));
}

/**
 * 从选品候选中挑最佳匹配；无法唯一/明确确定时返回 null（留给人工选）。
 */
export function pickBestInboundProductMatch(
  candidates: Record<string, unknown>[],
  drug: MsfxDrugLineForMatch,
): Record<string, unknown> | null {
  if (!candidates.length) return null;
  const name = normalizeMatchText(resolveMsfxDrugDisplayName(drug));
  if (!name) return null;

  const spec = normalizeMatchText(drug.packageSpec);
  const mfr = normalizeMatchText(drug.productEntName);

  type Scored = { row: Record<string, unknown>; score: number };
  const scored: Scored[] = candidates.map((row) => {
    let score = bestNameScore(drug, row);
    if (score <= 0) {
      return { row, score: 0 };
    }
    if (spec) {
      const tv = normalizeMatchText(row.totalVolume);
      if (tv && (tv === spec || tv.includes(spec) || spec.includes(tv))) {
        score += 12;
      }
    }
    if (mfr) {
      const mf = normalizeMatchText(row.manufacturer);
      if (mf && (mf.includes(mfr) || mfr.includes(mf))) {
        score += 10;
      }
    }
    return { row, score };
  });

  scored.sort((a, b) => b.score - a.score);
  const best = scored[0];
  if (!best || best.score < 64) return null;

  const second = scored[1];
  // 唯一高分，或明显领先第二名
  if (!second || second.score <= 0 || best.score - second.score >= 8) {
    return best.row;
  }
  // 并列：若名称完全一致且仅此一条精确名，仍可选
  const exactNameHits = scored.filter((s) => s.score >= 100);
  if (exactNameHits.length === 1) return exactNameHits[0]!.row;

  return null;
}
