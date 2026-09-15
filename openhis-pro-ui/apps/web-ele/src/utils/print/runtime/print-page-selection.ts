/**
 * 多页预览：页码选择与正反（双面）打印。
 * pageIndexes 从 1 起，与预览纸张顺序一致。
 */

/** electron-hiprint / Chromium duplexMode */
export type HiprintDuplexMode = 'longEdge' | 'shortEdge' | 'simplex';

/** 预览内用户选择的打印页与正反模式 */
export interface PrintPageSelection {
  /** 1-based 页码，至少一页 */
  pageIndexes: number[];
  /** 双面正反；仅多页时传 longEdge/shortEdge */
  duplexMode?: HiprintDuplexMode;
}

/** 生成 1..pageCount 的页码列表 */
export function buildDefaultPageIndexes(pageCount: number): number[] {
  const total = Math.max(1, Math.floor(pageCount || 1));
  return Array.from({ length: total }, (_, i) => i + 1);
}

/** 规范化页码：去重、排序、夹在 1..pageCount；空则回落全部 */
export function normalizePageIndexes(
  pageIndexes: number[] | undefined,
  pageCount: number,
): number[] {
  const total = Math.max(1, Math.floor(pageCount || 1));
  const all = buildDefaultPageIndexes(total);
  if (!pageIndexes?.length) {
    return all;
  }
  const uniq = [...new Set(pageIndexes.map((n) => Math.floor(Number(n))))]
    .filter((n) => n >= 1 && n <= total)
    .sort((a, b) => a - b);
  return uniq.length > 0 ? uniq : all;
}

/**
 * 按所选页过滤 PrintJson.panels（只打选中页）。
 * 未选或模板无 panels 时原样返回。
 */
export function filterPrintJsonByPages(
  printJson: Record<string, unknown>,
  pageIndexes: number[],
): Record<string, unknown> {
  const panels = Array.isArray(printJson.panels) ? (printJson.panels as unknown[]) : [];
  if (panels.length === 0) {
    return printJson;
  }
  const indexes = normalizePageIndexes(pageIndexes, panels.length);
  if (indexes.length === panels.length) {
    return printJson;
  }
  const selected = indexes.map((i) => panels[i - 1]).filter((p) => p != null);
  if (selected.length === 0) {
    return printJson;
  }
  return {
    ...printJson,
    panels: selected,
  };
}

/**
 * 解析送客户端的 duplexMode。
 * 多页且勾选双面 → longEdge/shortEdge；多页单面 → simplex；单页不传。
 */
export function resolveDuplexModeForPrint(
  selection: PrintPageSelection | undefined,
): HiprintDuplexMode | undefined {
  const pages = selection?.pageIndexes ?? [];
  if (pages.length < 2) {
    return undefined;
  }
  const mode = selection?.duplexMode;
  if (mode === 'longEdge' || mode === 'shortEdge' || mode === 'simplex') {
    return mode;
  }
  return undefined;
}
