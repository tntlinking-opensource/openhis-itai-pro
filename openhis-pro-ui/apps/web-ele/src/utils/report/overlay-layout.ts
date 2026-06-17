import type { PrintElementDefDto } from '#/api/report/report-design';
import type { LayoutBox, LayoutItem, LayoutPage } from '#/types/report-layout';

export const PAGE_SIZE_MM: Record<string, { width: number; height: number }> = {
  A4: { width: 210, height: 297 },
  A5: { width: 148, height: 210 },
};

/** 与后端 roll 设计/渲染默认页高一致（mm） */
export const ROLL_DESIGN_HEIGHT_MM = 400;

export interface PageSizeOptions {
  rollDesignHeightMm?: number;
  /** PDF 实际页宽高（mm），overlay 预览与落格框对齐时使用 */
  pageSizeOverride?: { widthMm: number; heightMm: number };
}

export const OVERFLOW_OPTIONS = [
  { label: '裁剪 (clip)', value: 'clip' },
  { label: '缩字 (shrinkFont)', value: 'shrinkFont' },
] as const;

export function isCustomPageSize(page?: LayoutPage | null): boolean {
  return page?.size === 'custom' && (page.widthMm ?? 0) > 0 && (page.heightMm ?? 0) > 0;
}

export function pageSizeMm(
  page?: LayoutPage | null,
  options?: PageSizeOptions,
): { widthMm: number; heightMm: number } {
  if (options?.pageSizeOverride) {
    return { ...options.pageSizeOverride };
  }
  if (isCustomPageSize(page)) {
    return {
      widthMm: page!.widthMm!,
      heightMm: page!.heightMm!,
    };
  }
  if (page?.size === 'custom') {
    const fallback = PAGE_SIZE_MM.A4;
    return {
      widthMm: (page.widthMm ?? 0) > 0 ? page.widthMm! : fallback.width,
      heightMm: (page.heightMm ?? 0) > 0 ? page.heightMm! : fallback.height,
    };
  }
  if (page?.widthMm && page.widthMm > 0 && !page.size) {
    return {
      widthMm: page.widthMm,
      heightMm: options?.rollDesignHeightMm ?? ROLL_DESIGN_HEIGHT_MM,
    };
  }
  const key = (page?.size ?? 'A4').toUpperCase();
  const base = PAGE_SIZE_MM[key] ?? PAGE_SIZE_MM.A4;
  if (page?.orientation === 'landscape') {
    return { widthMm: base.height, heightMm: base.width };
  }
  return { widthMm: base.width, heightMm: base.height };
}

export function hasBoxGeometry(box?: LayoutBox | null): boolean {
  return box?.xMm != null && box?.yMm != null && box?.widthMm != null && box?.heightMm != null;
}

export function readBoxFromRecord(raw?: Record<string, unknown> | null): LayoutBox | null {
  if (!raw) {
    return null;
  }
  const box: LayoutBox = {
    xMm: toNum(raw.xMm),
    yMm: toNum(raw.yMm),
    widthMm: toNum(raw.widthMm),
    heightMm: toNum(raw.heightMm),
    overflow: typeof raw.overflow === 'string' ? raw.overflow : undefined,
    rowHeightMm: toNum(raw.rowHeightMm),
  };
  return hasBoxGeometry(box) ? box : null;
}

export function readBoxFromDef(def?: PrintElementDefDto | null): LayoutBox | null {
  return readBoxFromRecord(def?.defaultBox ?? null);
}

/** 模板 items[].box 优先，否则元素目录 defaultBox */
export function resolveBaseBox(
  item: LayoutItem,
  def?: PrintElementDefDto | null,
): LayoutBox | null {
  if (hasBoxGeometry(item.box)) {
    return { ...item.box };
  }
  return readBoxFromDef(def);
}

/** 与后端 OverlayBoxResolver 一致：base + boxDelta + printOffsetMm */
export function computeEffectiveBox(
  item: LayoutItem,
  def: PrintElementDefDto | null | undefined,
  printOffsetMm?: number[] | null,
): LayoutBox | null {
  const base = resolveBaseBox(item, def);
  if (!base || !hasBoxGeometry(base)) {
    return null;
  }
  const dx = item.boxDelta?.dxMm ?? 0;
  const dy = item.boxDelta?.dyMm ?? 0;
  const ox = printOffsetMm?.[0] ?? 0;
  const oy = printOffsetMm?.[1] ?? 0;
  return {
    xMm: round1((base.xMm ?? 0) + dx + ox),
    yMm: round1((base.yMm ?? 0) + dy + oy),
    widthMm: base.widthMm,
    heightMm: base.heightMm,
    overflow: item.box?.overflow ?? base.overflow,
    rowHeightMm: item.box?.rowHeightMm ?? base.rowHeightMm,
  };
}

export function formatBoxSummary(box?: LayoutBox | null): string {
  if (!box || !hasBoxGeometry(box)) {
    return '未设置';
  }
  return `${box.xMm},${box.yMm} · ${box.widthMm}×${box.heightMm}mm`;
}

export function boxFromDef(def?: PrintElementDefDto | null): LayoutBox | undefined {
  const parsed = readBoxFromDef(def);
  if (!parsed) {
    return undefined;
  }
  return { ...parsed };
}

/** overlay 模板项缺 box 时，从元素目录 defaultBox 补齐（与后端 OverlayBoxResolver 一致） */
export function hydrateOverlayLayoutItems(
  doc: LayoutDocument,
  elements: PrintElementDefDto[] | undefined,
): LayoutDocument {
  if (doc.page?.layoutMode !== 'overlay' || !elements?.length) {
    return doc;
  }
  const defMap = new Map(elements.map((el) => [el.elementCode, el]));
  let changed = false;
  const items = (doc.items ?? []).map((item) => {
    if (item.visible === false || hasBoxGeometry(item.box)) {
      return item;
    }
    const fromDef = boxFromDef(defMap.get(item.elementCode));
    if (!fromDef) {
      return item;
    }
    changed = true;
    return { ...item, box: fromDef };
  });
  return changed ? { ...doc, items } : doc;
}

export function isOverlayCapableElement(def?: PrintElementDefDto | null): boolean {
  if (!def?.elementCode) {
    return false;
  }
  if (def.layoutMode === 'overlay') {
    return true;
  }
  return readBoxFromDef(def) != null;
}

/** 有效落格 → 写入 items[].box 的基准坐标（扣除 boxDelta 与全局偏移） */
export function effectiveToBaseBox(
  effective: LayoutBox,
  item: LayoutItem,
  printOffsetMm?: number[] | null,
): LayoutBox {
  const dx = item.boxDelta?.dxMm ?? 0;
  const dy = item.boxDelta?.dyMm ?? 0;
  const ox = printOffsetMm?.[0] ?? 0;
  const oy = printOffsetMm?.[1] ?? 0;
  return {
    ...effective,
    xMm: round1((effective.xMm ?? 0) - dx - ox),
    yMm: round1((effective.yMm ?? 0) - dy - oy),
    widthMm: effective.widthMm != null ? round1(effective.widthMm) : effective.widthMm,
    heightMm: effective.heightMm != null ? round1(effective.heightMm) : effective.heightMm,
  };
}

/** 将有效落格限制在纸型范围内（拖拽预览用） */
export function clampEffectiveBoxToPage(
  box: LayoutBox,
  page?: LayoutPage | null,
  options?: PageSizeOptions,
): LayoutBox {
  const { widthMm: pageW, heightMm: pageH } = pageSizeMm(page, options);
  let w = Math.max(1, box.widthMm ?? 1);
  let h = Math.max(1, box.heightMm ?? 1);
  w = Math.min(w, pageW);
  h = Math.min(h, pageH);
  let x = box.xMm ?? 0;
  let y = box.yMm ?? 0;
  x = Math.max(0, Math.min(x, pageW - w));
  y = Math.max(0, Math.min(y, pageH - h));
  return {
    ...box,
    xMm: round1(x),
    yMm: round1(y),
    widthMm: round1(w),
    heightMm: round1(h),
  };
}

/** 拖拽结束后写入模板的 box（保留 overflow / rowHeightMm） */
export function persistBoxFromEffective(
  effective: LayoutBox,
  item: LayoutItem,
  def: PrintElementDefDto | null | undefined,
  page: LayoutPage | null | undefined,
  printOffsetMm?: number[] | null,
  options?: PageSizeOptions,
): LayoutBox {
  const clamped = clampEffectiveBoxToPage(effective, page, options);
  const base = effectiveToBaseBox(clamped, item, printOffsetMm);
  const seed = resolveBaseBox(item, def) ?? clamped;
  return {
    xMm: base.xMm,
    yMm: base.yMm,
    widthMm: base.widthMm,
    heightMm: base.heightMm,
    overflow: item.box?.overflow ?? seed.overflow,
    rowHeightMm: item.box?.rowHeightMm ?? seed.rowHeightMm,
  };
}

export function clientPointToPageMm(
  svg: SVGSVGElement,
  clientX: number,
  clientY: number,
  page?: LayoutPage | null,
  options?: PageSizeOptions,
  round = true,
): { xMm: number; yMm: number } {
  const rect = svg.getBoundingClientRect();
  const { widthMm, heightMm } = pageSizeMm(page, options);
  const xMm = ((clientX - rect.left) / Math.max(rect.width, 1)) * widthMm;
  const yMm = ((clientY - rect.top) / Math.max(rect.height, 1)) * heightMm;
  if (round) {
    return { xMm: round1(xMm), yMm: round1(yMm) };
  }
  return { xMm, yMm };
}

function toNum(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const n = Number(value);
    return Number.isFinite(n) ? n : undefined;
  }
  return undefined;
}

export function isPositionedRenderType(renderType?: string | null): boolean {
  const rt = (renderType ?? '').toLowerCase();
  return rt === 'qrcode' || rt === 'barcode' || rt === 'image';
}

function round1(n: number): number {
  return Math.round(n * 10) / 10;
}

/** 落格框内样例/标签文字位置（mm，与 PDF 顶对齐一致） */
export interface OverlayBoxTextMetrics {
  padXMm: number;
  labelY: number;
  previewY: number;
  labelFontMm: number;
  previewFontMm: number;
}

export function overlayBoxTextMetrics(box: LayoutBox): OverlayBoxTextMetrics {
  const y = box.yMm ?? 0;
  const h = Math.max(1, box.heightMm ?? 5);
  const padXMm = 0.8;
  const labelFontMm = Math.min(2.6, h * 0.55);
  const previewFontMm = Math.min(2.2, h * 0.5);
  const compact = h <= 6;
  const labelY = y + labelFontMm;
  const previewY = compact ? y + Math.min(h * 0.78, h - 0.5) : y + labelFontMm;
  return {
    padXMm,
    labelY,
    previewY,
    labelFontMm,
    previewFontMm,
  };
}
