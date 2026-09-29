/**
 * 编辑器 ↔ 打印表格布局 attrs（09 §5.1）。
 * 列宽用相对份额或 %，不要 px；格高 / padding 用 mm。
 * L1 只解释这些 attrs；无 attrs 时走角色目录，不按表名补丁。
 */

/** 单列最小份额，拖到下限停住，避免列宽写成 0 */
export const EMR_MIN_COL_SHARE = 0.04;

/** 格最小高度下限（mm）；0 表示恢复自动 */
export const EMR_MIN_CELL_HEIGHT_MM = 4;

/** 四边 padding（mm）：上、右、下、左 */
export type EmrPaddingMm = [number, number, number, number];

/**
 * 把 table.attrs.colWidths 收成正数份额数组。
 * 支持 number[]、带 % 的字符串、逗号分隔；px 一律丢弃。
 */
export function parseColWidths(raw: unknown): null | number[] {
  if (raw == null) {
    return null;
  }
  const parts: unknown[] = [];
  if (Array.isArray(raw)) {
    parts.push(...raw);
  } else if (typeof raw === 'string') {
    const text = raw.trim();
    if (!text) {
      return null;
    }
    parts.push(...text.split(/[\s,]+/));
  } else {
    return null;
  }
  const shares: number[] = [];
  for (const part of parts) {
    const share = parseColWidthToken(part);
    if (share == null || share <= 0) {
      return null;
    }
    shares.push(share);
  }
  if (shares.length === 0) {
    return null;
  }
  return shares;
}

/**
 * 单个列宽 token → 份额。`13%` 与 `1.3` 都当相对份额，不当 px。
 */
function parseColWidthToken(token: unknown): null | number {
  if (typeof token === 'number' && Number.isFinite(token) && token > 0) {
    return token;
  }
  if (typeof token !== 'string') {
    return null;
  }
  const text = token.trim().toLowerCase();
  if (!text || text.endsWith('px')) {
    return null;
  }
  const percent = text.endsWith('%');
  const numText = percent ? text.slice(0, -1).trim() : text;
  const value = Number(numText);
  if (!Number.isFinite(value) || value <= 0) {
    return null;
  }
  return value;
}

/**
 * 份额 → 百分比字符串，供 colgroup / CSS 使用。
 */
export function colWidthsToPercents(shares: number[]): string[] {
  const sum = shares.reduce((acc, item) => acc + item, 0);
  if (sum <= 0) {
    return [];
  }
  return shares.map((share) => `${(share / sum) * 100}%`);
}

/**
 * 份额收成正数、每列不低于最小份额，末列吃余数使合计 100。
 */
export function normalizeColShares(shares: number[], minShare = EMR_MIN_COL_SHARE): number[] {
  const count = shares.length;
  if (count <= 0) {
    return [];
  }
  const floor = Math.min(minShare, 1 / count);
  const positive = shares.map((share) => (Number.isFinite(share) && share > 0 ? share : floor));
  const sum = positive.reduce((acc, item) => acc + item, 0);
  if (sum <= 0) {
    return Array.from({ length: count }, () => 100 / count);
  }
  const percents = positive.map((share) => (share / sum) * 100);
  const minPercent = floor * 100;
  const clamped = percents.map((item) => Math.max(item, minPercent));
  const clampedSum = clamped.reduce((acc, item) => acc + item, 0);
  const scaled = clamped.map((item) => (item / clampedSum) * 100);
  const rounded = scaled.map((item) => Math.round(item * 10) / 10);
  const head = rounded.slice(0, -1);
  const headSum = head.reduce((acc, item) => acc + item, 0);
  const last = Math.round((100 - headSum) * 10) / 10;
  return [...head, last > 0 ? last : minPercent];
}

/**
 * 增删列后把旧份额改成新列数等长。删列按下标去掉，加列在插入点补均份额。
 */
export function rebalanceColShares(
  oldShares: number[],
  nextCount: number,
  removedIndex: number | null = null,
  insertAfterIndex: number | null = null,
): number[] {
  if (nextCount <= 0) {
    return [];
  }
  if (oldShares.length === nextCount) {
    return normalizeColShares(oldShares);
  }
  if (nextCount < oldShares.length) {
    const dropAt =
      removedIndex != null && removedIndex >= 0 && removedIndex < oldShares.length
        ? removedIndex
        : oldShares.length - 1;
    const kept = oldShares.filter((_, index) => index !== dropAt);
    if (kept.length === nextCount) {
      return normalizeColShares(kept);
    }
    return normalizeColShares(oldShares.slice(0, nextCount));
  }
  const avg = oldShares.reduce((acc, item) => acc + item, 0) / oldShares.length;
  const extra = nextCount - oldShares.length;
  const next = oldShares.slice();
  const insertAt =
    insertAfterIndex != null && insertAfterIndex >= 0
      ? Math.min(insertAfterIndex + 1, next.length)
      : next.length;
  for (let i = 0; i < extra; i++) {
    next.splice(insertAt + i, 0, avg);
  }
  return normalizeColShares(next);
}

/**
 * 本页内容区高度（纸高 − 上下页边），格 minHeightMm 不得超过此值。
 */
export function pageContentHeightMm(
  paperHeightMm: number,
  marginTopMm: number,
  marginBottomMm: number,
): number {
  const raw = paperHeightMm - marginTopMm - marginBottomMm;
  return Math.max(EMR_MIN_CELL_HEIGHT_MM, raw);
}

/**
 * 用户输入的格高收到 [下限, 本页内容高]；≤0 表示清掉 attrs。
 */
export function clampCellMinHeightMm(
  value: number,
  pageContentMm: number,
): { clamped: boolean; value: null | number } {
  if (!Number.isFinite(value) || value <= 0) {
    return { clamped: false, value: null };
  }
  const maxMm = Math.max(EMR_MIN_CELL_HEIGHT_MM, pageContentMm);
  if (value < EMR_MIN_CELL_HEIGHT_MM) {
    return { clamped: value !== EMR_MIN_CELL_HEIGHT_MM, value: EMR_MIN_CELL_HEIGHT_MM };
  }
  if (value > maxMm) {
    return { clamped: true, value: Math.round(maxMm) };
  }
  return { clamped: false, value: Math.round(value) };
}

/**
 * paddingMm：单值、CSS 简写数组、或 {top,right,bottom,left}。
 */
export function parsePaddingMm(raw: unknown): EmrPaddingMm | null {
  if (typeof raw === 'number' && Number.isFinite(raw) && raw >= 0) {
    return [raw, raw, raw, raw];
  }
  if (typeof raw === 'string') {
    const nums = raw
      .split(/[\s,]+/)
      .map((item) => Number(item))
      .filter((item) => Number.isFinite(item) && item >= 0);
    return paddingFromList(nums);
  }
  if (Array.isArray(raw)) {
    const nums = raw
      .map((item) => Number(item))
      .filter((item) => Number.isFinite(item) && item >= 0);
    return paddingFromList(nums);
  }
  if (raw && typeof raw === 'object') {
    const rec = raw as Record<string, unknown>;
    const top = Number(rec.top ?? rec.t);
    const right = Number(rec.right ?? rec.r);
    const bottom = Number(rec.bottom ?? rec.b);
    const left = Number(rec.left ?? rec.l);
    if ([top, right, bottom, left].every((item) => Number.isFinite(item) && item >= 0)) {
      return [top, right, bottom, left];
    }
  }
  return null;
}

/**
 * CSS padding 简写：1～4 个值扩成四边。
 */
function paddingFromList(nums: number[]): EmrPaddingMm | null {
  if (nums.length === 1) {
    return [nums[0], nums[0], nums[0], nums[0]];
  }
  if (nums.length === 2) {
    return [nums[0], nums[1], nums[0], nums[1]];
  }
  if (nums.length === 3) {
    return [nums[0], nums[1], nums[2], nums[1]];
  }
  if (nums.length >= 4) {
    return [nums[0], nums[1], nums[2], nums[3]];
  }
  return null;
}

/**
 * 单元格布局 attrs → 内联 style（L1）。表单元格认 height 当最小高度。
 */
export function buildCellLayoutStyle(attrs: Record<string, unknown>): string {
  const parts: string[] = [];
  const minHeightMm = Number(attrs.minHeightMm);
  if (Number.isFinite(minHeightMm) && minHeightMm > 0) {
    parts.push(`height: ${minHeightMm}mm`);
    parts.push(`min-height: ${minHeightMm}mm`);
  }
  const padding = parsePaddingMm(attrs.paddingMm);
  if (padding) {
    parts.push(`padding: ${padding[0]}mm ${padding[1]}mm ${padding[2]}mm ${padding[3]}mm`);
  }
  const align = typeof attrs.align === 'string' ? attrs.align.trim().toLowerCase() : '';
  if (align === 'left' || align === 'center' || align === 'right') {
    parts.push(`text-align: ${align}`);
  }
  const valign = typeof attrs.valign === 'string' ? attrs.valign.trim().toLowerCase() : '';
  if (valign === 'top' || valign === 'middle' || valign === 'bottom') {
    parts.push(`vertical-align: ${valign}`);
  }
  const nowrap = attrs.nowrap === true || attrs.nowrap === 'true';
  const wrap = attrs.wrap === true || attrs.wrap === 'true';
  if (nowrap) {
    parts.push('white-space: nowrap');
    parts.push('word-break: keep-all');
  } else if (wrap) {
    parts.push('white-space: normal');
    parts.push('word-break: break-word');
    parts.push('overflow-wrap: break-word');
  }
  return parts.join('; ');
}

/**
 * 从 HTML dataset 读回 colWidths（逗号分隔份额）。
 */
export function parseColWidthsAttr(element: HTMLElement): null | number[] {
  return parseColWidths(element.getAttribute('data-col-widths'));
}

/**
 * 布尔 attrs：HTML data-* 只有出现才算写过。
 */
export function parseOptionalBoolAttr(element: HTMLElement, attr: string): boolean | null {
  const raw = element.getAttribute(attr);
  if (raw == null) {
    return null;
  }
  if (raw === 'false' || raw === '0') {
    return false;
  }
  return true;
}
