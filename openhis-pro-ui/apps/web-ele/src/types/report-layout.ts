export interface LayoutStyle {
  fontSizePt?: number;
  align?: string;
  bold?: boolean;
}

export interface LayoutBox {
  xMm?: number;
  yMm?: number;
  widthMm?: number;
  heightMm?: number;
  overflow?: string;
  rowHeightMm?: number;
  /** 表格最大行槽（处方药品表固定 5 行 + 空位斜线） */
  maxRows?: number;
}

export interface LayoutBoxDelta {
  dxMm?: number;
  dyMm?: number;
}

export interface LayoutItem {
  elementCode: string;
  visible?: boolean;
  labelOverride?: string;
  hiddenColumns?: string[];
  /** 表格列 field → left/center/right，覆盖元素定义默认对齐 */
  columnAligns?: Record<string, string>;
  hiddenItems?: string[];
  /** kvRow 每行键值列数，默认 1 */
  columns?: number;
  style?: LayoutStyle;
  box?: LayoutBox;
  boxDelta?: LayoutBoxDelta;
}

export interface PageOverlayConfig {
  printOffsetMm?: number[];
  guideImageUrl?: string;
}

export interface LayoutPage {
  size?: string;
  orientation?: string;
  /** 热敏/roll 管线自定义纸宽，或 page 管线 size=custom 时的纸宽（mm） */
  widthMm?: number;
  /** page 管线 size=custom 时的纸高（mm） */
  heightMm?: number;
  marginMm?: number[];
  layoutMode?: string;
  overlay?: PageOverlayConfig;
}

export interface LayoutDocument {
  pipeline?: string;
  page?: LayoutPage;
  items?: LayoutItem[];
}

export function createDefaultLayoutDocument(pipeline = 'page'): LayoutDocument {
  const isRoll = pipeline === 'roll';
  return {
    pipeline,
    page: {
      size: isRoll ? undefined : 'A4',
      orientation: isRoll ? undefined : 'portrait',
      widthMm: isRoll ? 80 : undefined,
      layoutMode: 'flow',
      marginMm: isRoll ? [2, 2, 2, 2] : [12, 10, 12, 10],
    },
    items: [],
  };
}

export function normalizeLayoutDocument(
  raw?: LayoutDocument | null,
  pipeline = 'page',
): LayoutDocument {
  const effectivePipeline = raw?.pipeline ?? pipeline;
  const base = createDefaultLayoutDocument(effectivePipeline);
  if (!raw) {
    return base;
  }
  const isRoll = effectivePipeline === 'roll';
  const mergedPage = {
    ...base.page,
    ...raw.page,
    overlay: {
      ...base.page?.overlay,
      ...raw.page?.overlay,
    },
  };
  if (isRoll) {
    delete mergedPage.size;
    delete mergedPage.orientation;
    if (mergedPage.overlay && Object.keys(mergedPage.overlay).length === 0) {
      delete mergedPage.overlay;
    }
  }
  if (mergedPage.overlay?.guideImageUrl?.trim().startsWith('/assets/print-guides/')) {
    const { guideImageUrl: _removed, ...restOverlay } = mergedPage.overlay;
    mergedPage.overlay = Object.keys(restOverlay).length > 0 ? restOverlay : undefined;
  }
  if (mergedPage.layoutMode === 'overlay') {
    mergedPage.marginMm = [0, 0, 0, 0];
  }
  return {
    pipeline: effectivePipeline,
    page: mergedPage,
    items: Array.isArray(raw.items) ? raw.items.map((i) => ({ ...i })) : [],
  };
}
