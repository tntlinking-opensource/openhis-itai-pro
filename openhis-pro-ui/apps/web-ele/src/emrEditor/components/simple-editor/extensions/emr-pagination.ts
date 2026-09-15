import type { Editor } from '@tiptap/core';
import type { Node as PMNode } from '@tiptap/pm/model';
import type { EditorView } from '@tiptap/pm/view';

import { Extension } from '@tiptap/core';
import { Plugin, PluginKey } from '@tiptap/pm/state';
import { Decoration, DecorationSet } from '@tiptap/pm/view';

import { isFillableImeActive } from '../../../fillable-field/runtime/ime-controller';
import {
  clearEmrIslandSoftBreaks,
  EMR_ISLAND_INPUT_SELECTOR,
  EMR_ISLAND_SOFT_BREAK_SELECTOR,
  emrIslandPlainTextOffsetBefore,
  insertEmrIslandSoftBreakAt,
} from '../emr-island-soft-break';
import {
  contentEditableCaretOffset,
  peekPinnedIslandCaret,
  pinIslandCaret,
  setContentEditableCaretOffset,
} from '../inline-contenteditable-caret';
import {
  emrPageLayoutPlanKey,
  clampSoftSpacersInPlan,
  planEmrPageLayout,
  type EmrPageItem,
  type EmrPageLayoutPlan,
  type EmrPageMetrics,
  type EmrSoftSpacer,
} from '../emr-page-layout';
import {
  applyEmrPageChromeBandDomStyle,
  buildChromeEditHtml,
  cloneEmrPageChrome,
  createDefaultEmrPageChrome,
  formatChromeDisplayHtml,
  measureEmrChromeBandHeightPx,
  parseCssLengthToMm,
  resolveEmrPageChromeTemplate,
  resolveScreenChromeBandPaddingMm,
  type EmrPageChrome,
  type EmrPageChromeContext,
} from '../emr-page-chrome';
import {
  bumpMetricsForChromeShortfall,
  collectMeasuredChromeBandsMm,
} from '../emr-page-header-guard';

const paginationKey = new PluginKey<DecorationSet>('emrPagination');
const SOFT_SPACER_META = 'emrPageSoftSpacers';
export const EMR_PAGE_CHROME_META = 'emrPageChrome';
/** 页眉/页脚 DOM 实测高度（mm），回写 heightMm 缓存 */
export const EMR_MEASURED_CHROME_META = 'emrMeasuredChrome';

export interface EmrMeasuredChromeMeta {
  footerMm?: number;
  headerMm?: number;
}

export interface EmrPaginationStorage {
  chrome: EmrPageChrome;
  context: EmrPageChromeContext;
  /** 正在纸面编辑的页眉/页脚；该带不刷新 text，避免冲掉输入 */
  editingBand: 'footer' | 'header' | null;
  /** 页眉/页边距变更时递增，驱动正文未变也重算分页 */
  relayoutNonce: number;
}

/** 页眉页脚或页边距变更后请求重排（医生站载入模版等场景） */
export function requestEmrPaginationRelayout(editor: Editor) {
  const store = editor.storage.emrPagination as EmrPaginationStorage | undefined;
  if (!store) return;
  store.relayoutNonce = (store.relayoutNonce ?? 0) + 1;
  const tr = editor.state.tr.setMeta(EMR_PAGE_CHROME_META, true).setMeta('addToHistory', false);
  editor.view.dispatch(tr);
}

function emptyChromeContext(): EmrPageChromeContext {
  return {
    age: '',
    deptName: '',
    documentTitle: '',
    documentTypeName: '',
    gender: '',
    orgName: '',
    patientName: '',
    printTime: '',
    values: {},
  };
}

function readCssLengthPx(host: HTMLElement, cssLength: string): number {
  const raw = cssLength.trim();
  if (!raw) return 0;
  const probe = document.createElement('div');
  probe.style.cssText = 'position:absolute;visibility:hidden;pointer-events:none;height:' + raw;
  host.appendChild(probe);
  const px = probe.offsetHeight;
  probe.remove();
  return px;
}

function readVarPx(host: HTMLElement, varName: string): number {
  const value = getComputedStyle(host).getPropertyValue(varName);
  return readCssLengthPx(host, value);
}

function readEditorCornerMarginMm(pm: HTMLElement, edge: 'bottom' | 'top'): number {
  const root = pm.closest('.simple-editor');
  if (!(root instanceof HTMLElement)) return edge === 'top' ? 25.4 : 25.4;
  const varName = edge === 'top' ? '--simple-editor-corner-top' : '--simple-editor-corner-bottom';
  const raw = getComputedStyle(root).getPropertyValue(varName).trim();
  return parseCssLengthToMm(raw, 25.4);
}

function mmToPx(mm: number): number {
  return (mm * 96) / 25.4;
}

/** 屏上：页边距 + heightMm 缓存（DOM 实测回写）；未实测时仅页边距 */
function resolvePageMetricsPadding(
  pm: HTMLElement,
  chrome: EmrPageChrome,
): { paddingBottomPx: number; paddingTopPx: number } {
  const marginTopMm = readEditorCornerMarginMm(pm, 'top');
  const marginBottomMm = readEditorCornerMarginMm(pm, 'bottom');
  let paddingTopPx = mmToPx(marginTopMm);
  let paddingBottomPx = mmToPx(marginBottomMm);

  if (chrome.header.enabled) {
    const headerMm = resolveScreenChromeBandPaddingMm(chrome.header, marginTopMm);
    paddingTopPx = mmToPx(headerMm);
  }
  if (chrome.footer.enabled) {
    const footerMm = resolveScreenChromeBandPaddingMm(chrome.footer, marginBottomMm);
    paddingBottomPx = mmToPx(footerMm);
  }
  return { paddingBottomPx, paddingTopPx };
}

/** 先画纸面页眉/页脚再实测，供分页 plan 使用（屏上共通） */
function finalizeChromeMetricsForLayout(
  pm: HTMLElement,
  layer: HTMLElement,
  chrome: EmrPageChrome,
  metrics: EmrPageMetrics,
  context: EmrPageChromeContext,
  editingBand: 'footer' | 'header' | null,
  pageCountHint: number,
): { measured: EmrMeasuredChromeMeta | null; metrics: EmrPageMetrics } {
  const paintCount = Math.max(1, pageCountHint);
  syncPaperCount(layer, paintCount);
  syncPaperChrome(layer, paintCount, chrome, context, editingBand);
  let measured: EmrMeasuredChromeMeta | null = null;
  let nextMetrics = metrics;
  let bandResult = applyMeasuredChromeBands(pm, layer, chrome, nextMetrics);
  for (let pass = 0; pass < 3; pass++) {
    if (!bandResult.measured) break;
    measured = { ...measured, ...bandResult.measured };
    const delta =
      Math.abs(bandResult.metrics.paddingTopPx - nextMetrics.paddingTopPx) > 1 ||
      Math.abs(bandResult.metrics.paddingBottomPx - nextMetrics.paddingBottomPx) > 1;
    nextMetrics = bandResult.metrics;
    if (!delta) break;
    bandResult = applyMeasuredChromeBands(pm, layer, chrome, nextMetrics);
  }
  return { measured, metrics: nextMetrics };
}

/** 渲染后按页眉/页脚 DOM 实测高度校正 padding；可下调，避免估算/旧缓存把空白锁死 */
function applyMeasuredChromeBands(
  pm: HTMLElement,
  layer: HTMLElement,
  chrome: EmrPageChrome,
  metrics: EmrPageMetrics,
): { measured: EmrMeasuredChromeMeta | null; metrics: EmrPageMetrics } {
  const floorTopPx = mmToPx(readEditorCornerMarginMm(pm, 'top'));
  const floorBottomPx = mmToPx(readEditorCornerMarginMm(pm, 'bottom'));
  let paddingTopPx = metrics.paddingTopPx;
  let paddingBottomPx = metrics.paddingBottomPx;

  const measureBandMaxPx = (selector: string): number => {
    let maxPx = 0;
    for (const el of layer.querySelectorAll<HTMLElement>(selector)) {
      maxPx = Math.max(maxPx, measureEmrChromeBandHeightPx(el));
    }
    return maxPx;
  };

  const measured = collectMeasuredChromeBandsMm(layer, chrome);
  const hasMeasured = measured.headerMm != null || measured.footerMm != null;

  if (chrome.header.enabled) {
    const headerPx = measureBandMaxPx(
      ':scope > .simple-editor__paper > .simple-editor__page-header:not([hidden])',
    );
    if (headerPx > 0) {
      // 页眉 position:absolute，边框盒常是 min-height（页边距），float 行会溢出。
      // 正文 padding 必须盖住视觉底边，否则姓名/病房行压住表头（gap 约 -17px）。
      const HEADER_BODY_GAP_PX = 8;
      paddingTopPx = Math.max(floorTopPx, headerPx + HEADER_BODY_GAP_PX);
      if (measured.headerMm != null) {
        paddingTopPx = Math.max(paddingTopPx, mmToPx(measured.headerMm));
      }
    }
  }

  if (chrome.footer.enabled) {
    const footerPx = measureBandMaxPx(
      ':scope > .simple-editor__paper > .simple-editor__page-footer:not([hidden])',
    );
    if (footerPx > 0) {
      paddingBottomPx = Math.max(floorBottomPx, footerPx);
      if (measured.footerMm != null) {
        paddingBottomPx = Math.max(paddingBottomPx, mmToPx(measured.footerMm));
      }
    }
  }

  // 页眉/页脚预留过高时正文区被压没 → syncPageIndexWithY 会在文首就推到第 2 页（首页空白）
  const minContentPx = mmToPx(48);
  const maxPadTotal = Math.max(floorTopPx + floorBottomPx, metrics.pageHeightPx - minContentPx);
  if (paddingTopPx + paddingBottomPx > maxPadTotal + 0.5) {
    const scale = maxPadTotal / (paddingTopPx + paddingBottomPx);
    paddingTopPx = Math.max(floorTopPx, paddingTopPx * scale);
    paddingBottomPx = Math.max(floorBottomPx, paddingBottomPx * scale);
  }

  return {
    measured: hasMeasured ? measured : null,
    metrics: { ...metrics, paddingBottomPx, paddingTopPx },
  };
}

/** 从编辑器 CSS 变量读取纸张高度 / 页缝 / 页边距 */
function readPageMetrics(pm: HTMLElement, chrome?: EmrPageChrome): EmrPageMetrics | null {
  let pageHeightPx = readVarPx(pm, '--simple-editor-a4-min-h');
  const pageGapPx = readVarPx(pm, '--simple-editor-page-gap');
  const style = getComputedStyle(pm);
  let paddingTopPx = Number.parseFloat(style.paddingTop) || 0;
  let paddingBottomPx = Number.parseFloat(style.paddingBottom) || 0;

  // 纸面 DOM 实测优先（避免 297mm 被误读成 297px）
  const paper = pm.parentElement?.querySelector(
    ':scope > .simple-editor__papers > .simple-editor__paper',
  );
  if (paper instanceof HTMLElement) {
    const measured = Math.round(paper.getBoundingClientRect().height);
    if (measured > pageHeightPx + 8) pageHeightPx = measured;
  }

  if (pageHeightPx <= 0) return null;
  if (chrome) {
    const resolved = resolvePageMetricsPadding(pm, chrome);
    paddingTopPx = resolved.paddingTopPx;
    paddingBottomPx = resolved.paddingBottomPx;
  }
  return { pageGapPx, pageHeightPx, paddingBottomPx, paddingTopPx };
}

/**
 * 把实测页眉/页脚高度写到 ProseMirror padding。
 * plan 原先只用 metrics，CSS 仍走 --simple-editor-margin-*（常是页边距 20mm），
 * 坐标系差 20～30px → 页眉压正文，且 plan 提前换页虚增页数。
 */
function applyMeasuredPaddingToEditorDom(pm: HTMLElement, metrics: EmrPageMetrics) {
  const top = `${Math.round(metrics.paddingTopPx)}px`;
  const bottom = `${Math.round(metrics.paddingBottomPx)}px`;
  if (pm.style.paddingTop !== top) pm.style.paddingTop = top;
  if (pm.style.paddingBottom !== bottom) pm.style.paddingBottom = bottom;
}

/** 表格等不可从中间切开的块：整段推页（table 在 splitBlock 内单独按行处理） */
function isAtomicPageBlock(node: PMNode): boolean {
  return node.type.name === 'table' || node.type.name === 'emrToc' || !node.isTextblock;
}

function tableRowColCount(row: PMNode): number {
  let n = 0;
  row.forEach((cell) => {
    n += Math.max(1, Number(cell.attrs.colspan) || 1);
  });
  return Math.max(1, n);
}

/**
 * 表格按行切开。仅影响 table 节点。
 * 续行换页锚点 = tableRow pos：用 Decoration.widget 画无边框垫片 <tr>（PM 管理，不手改 DOM）。
 * 禁止把垫片塞进单元格——会留下带竖线的空白表体。
 */
function splitTableToRowItems(
  view: EditorView,
  tableNode: PMNode,
  tablePos: number,
  tableDom: HTMLElement,
  occupiedHeightPx: number,
): EmrPageItem[] {
  const occupied = Math.max(1, occupiedHeightPx);
  type RowRef = { bottom: number; rowPos: number; top: number };
  const rows: RowRef[] = [];
  tableNode.forEach((row, offset) => {
    if (row.type.name !== 'tableRow') return;
    const rowPos = tablePos + 1 + offset;
    const dom = view.nodeDOM(rowPos);
    if (!(dom instanceof HTMLElement)) return;
    const tr = dom.tagName === 'TR' ? dom : dom.closest('tr');
    if (!(tr instanceof HTMLElement)) return;
    if (tr.hasAttribute('data-emr-table-row-soft')) return;
    const rect = tr.getBoundingClientRect();
    if (rect.height < 0.5) return;
    rows.push({ rowPos, top: rect.top, bottom: rect.bottom });
  });

  if (rows.length <= 1) {
    // 单行超高表：按单元格内视觉行切开（岛屿长文），避免整表一块撑破页
    if (occupied > 80) {
      const cellLines = collectVisualLines(tableDom);
      if (cellLines.length > 1) {
        return visualLinesToPageItems(view, tablePos, tableDom, cellLines, occupied);
      }
    }
    return [{ anchorPos: tablePos, compactHeightPx: occupied, kind: 'block', pos: tablePos }];
  }

  const raw: number[] = [];
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i]!;
    const next = rows[i + 1];
    if (!next) {
      raw.push(Math.max(1, row.bottom - row.top));
      continue;
    }
    const gap = next.top - row.top;
    const spacerH = sumSoftSpacerHeightsInYRange(tableDom, row.top, next.top);
    raw.push(Math.max(1, gap - spacerH));
  }
  // 不用全局 scale：后排输入会改 occupied，比例缩放会晃动前排行高 → 换页垫片每字抖一次
  const items: EmrPageItem[] = [];
  let assigned = 0;
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i]!;
    let height: number;
    if (i === rows.length - 1) {
      height = Math.max(1, occupied - assigned);
    } else {
      height = Math.max(1, raw[i]!);
      assigned += height;
    }
    items.push({
      anchorPos: tablePos,
      compactHeightPx: height,
      kind: 'block',
      // 首行：表前推页；续行：tableRow pos（行间垫片 tr）
      pos: i === 0 ? tablePos : row.rowPos,
    });
  }
  return items;
}

type EmrVisualLine = {
  bottom: number;
  left: number;
  top: number;
  /** 行起点所在文本节点（用于岛屿偏移，避免 caretRangeFromPoint 点到页眉） */
  textNode?: Text;
  /** 行起点在 textNode 内的字符偏移 */
  textNodeOffset?: number;
};

const SOFT_SPACER_DOM_SELECTOR = `.emr-page-soft-break, ${EMR_ISLAND_SOFT_BREAK_SELECTOR}`;

/**
 * 统计 [startY, endY) 内已有换页垫片高度。
 * 测高时不摘垫片，避免输入过程中「先塌再撑」窜行；占用高度需扣掉垫片。
 * 同一 <tr> 内多格各挂一份等高垫片时只计一次，避免重复扣高。
 */
function sumSoftSpacerHeightsInYRange(root: HTMLElement, startY: number, endY: number): number {
  if (endY <= startY + 0.5) return 0;
  let sum = 0;
  const countedTr = new Set<Element>();
  for (const el of root.querySelectorAll(SOFT_SPACER_DOM_SELECTOR)) {
    if (!(el instanceof HTMLElement)) continue;
    const rect = el.getBoundingClientRect();
    if (rect.top < startY - 0.5 || rect.top >= endY - 0.5) continue;
    const tr = el.closest('tr');
    if (tr) {
      if (countedTr.has(tr)) continue;
      countedTr.add(tr);
    }
    sum += rect.height;
  }
  return sum;
}

/** 文本节点中与目标行 top/left 对齐的起始字符偏移 */
function textNodeOffsetForLineRect(textNode: Text, target: DOMRectReadOnly): number {
  const text = textNode.textContent ?? '';
  if (!text.length) return 0;
  const range = document.createRange();
  const topAt = (i: number) => {
    range.setStart(textNode, i);
    range.setEnd(textNode, Math.min(i + 1, text.length));
    const rects = range.getClientRects();
    if (rects.length > 0) return rects[0]!.top;
    return range.getBoundingClientRect().top;
  };
  let lo = 0;
  let hi = text.length;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (topAt(mid) < target.top - 1) lo = mid + 1;
    else hi = mid;
  }
  while (lo < text.length) {
    range.setStart(textNode, lo);
    range.setEnd(textNode, lo + 1);
    const r = range.getBoundingClientRect();
    if (r.height < 0.5 && r.width < 0.5) {
      lo += 1;
      continue;
    }
    if (Math.abs(r.top - target.top) > 2) break;
    if (r.left >= target.left - 2) break;
    lo += 1;
  }
  return lo;
}

/** 由视觉行携带的 textNode 解析岛屿换页锚点（不依赖 hit-test，叠页眉时仍可用） */
function resolveIslandLinePlacement(
  view: EditorView,
  line: EmrVisualLine,
): { islandTextOffset: number; pos: number } | null {
  const textNode = line.textNode;
  if (!textNode || textNode.parentElement?.closest(EMR_ISLAND_SOFT_BREAK_SELECTOR)) {
    return null;
  }
  const island = textNode.parentElement?.closest(EMR_ISLAND_INPUT_SELECTOR);
  if (!(island instanceof HTMLElement)) return null;

  const atomPos = resolveAtomPosForIslandDom(view, island);
  if (atomPos == null) return null;

  const nodeOffset = line.textNodeOffset ?? 0;
  return {
    pos: atomPos,
    islandTextOffset: emrIslandPlainTextOffsetBefore(island, textNode, nodeOffset),
  };
}

/** 岛屿 DOM → inline atom 文档位置 */
function resolveAtomPosForIslandDom(view: EditorView, island: HTMLElement): number | null {
  const wrapper = island.closest('[data-node-view-wrapper]');
  const target = wrapper instanceof HTMLElement ? wrapper : island;
  try {
    const pos = view.posAtDOM(target, 0);
    const direct = view.state.doc.nodeAt(pos);
    if (direct?.isAtom && direct.isInline) return pos;
    return resolveInlineAtomPos(view, pos, pos);
  } catch {
    return null;
  }
}

/** 坐标命中处的 inline atom 文档位置 */
function resolveInlineAtomPos(
  view: EditorView,
  pos: number,
  inside: number | null | undefined,
): number | null {
  const doc = view.state.doc;
  if (typeof inside === 'number' && inside >= 0) {
    const node = doc.nodeAt(inside);
    if (node?.isAtom && node.isInline) return inside;
  }
  const $pos = doc.resolve(Math.max(0, Math.min(pos, doc.content.size)));
  const after = $pos.nodeAfter;
  if (after?.isAtom && after.isInline) return $pos.pos;
  const before = $pos.nodeBefore;
  if (before?.isAtom && before.isInline) return $pos.pos - before.nodeSize;
  return null;
}

/** 段落内视觉行（跳过 soft-break / 岛屿换页垫片） */
function collectVisualLines(dom: HTMLElement): EmrVisualLine[] {
  const lines: EmrVisualLine[] = [];
  const range = document.createRange();
  const walker = document.createTreeWalker(dom, NodeFilter.SHOW_TEXT);
  let textNode = walker.nextNode();
  while (textNode) {
    if (
      textNode.parentElement?.closest(`.emr-page-soft-break, ${EMR_ISLAND_SOFT_BREAK_SELECTOR}`)
    ) {
      textNode = walker.nextNode();
      continue;
    }
    const raw = textNode.textContent ?? '';
    if (!raw) {
      textNode = walker.nextNode();
      continue;
    }
    const tn = textNode as Text;
    range.selectNodeContents(tn);
    for (const rect of range.getClientRects()) {
      if (rect.height < 1) continue;
      const last = lines[lines.length - 1];
      if (last && Math.abs(rect.top - last.top) < 2) {
        last.bottom = Math.max(last.bottom, rect.bottom);
        if (rect.left < last.left - 0.5) {
          last.left = rect.left;
          last.textNode = tn;
          last.textNodeOffset = textNodeOffsetForLineRect(tn, rect);
        }
        continue;
      }
      lines.push({
        top: rect.top,
        bottom: rect.bottom,
        left: rect.left,
        textNode: tn,
        textNodeOffset: textNodeOffsetForLineRect(tn, rect),
      });
    }
    textNode = walker.nextNode();
  }
  return lines;
}

/**
 * 把「块在文档流中占用的真实高度」按行缝切开。
 * 占用高度必须用相邻顶层块 top 之差（自然含 margin 折叠），禁止逐行加 slop：
 * 每行多记几 px → 多页后累计数百 px → 换页点漂移 → 页眉间距逐页崩坏。
 */
function visualLinesToPageItems(
  view: EditorView,
  pos: number,
  dom: HTMLElement,
  lines: EmrVisualLine[],
  occupiedHeightPx: number,
): EmrPageItem[] {
  const occupied = Math.max(1, occupiedHeightPx);
  if (lines.length <= 1) {
    return [{ anchorPos: pos, compactHeightPx: occupied, kind: 'block', pos }];
  }

  const raw: number[] = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]!;
    const next = lines[i + 1];
    if (!next) {
      raw.push(Math.max(1, line.bottom - line.top));
      continue;
    }
    const gap = next.top - line.top;
    const spacerH = sumSoftSpacerHeightsInYRange(dom, line.top, next.top);
    raw.push(Math.max(1, gap - spacerH));
  }
  const rawSum = raw.reduce((a, b) => a + b, 0);
  // 行缝之和对齐到块占用高度，消除 ceil/测量噪声，保证 plan 的 y 与 DOM 同构
  const scale = rawSum > 0.5 ? occupied / rawSum : 1;
  const items: EmrPageItem[] = [];
  let assigned = 0;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]!;
    let linePos = pos;
    let islandTextOffset: number | undefined;
    if (i > 0) {
      const island = resolveIslandLinePlacement(view, line);
      if (island) {
        linePos = island.pos;
        islandTextOffset = island.islandTextOffset;
      } else {
        const hit = view.posAtCoords({ left: line.left + 2, top: line.top + 1 });
        if (hit) linePos = hit.pos;
      }
    }
    let height: number;
    if (i === lines.length - 1) {
      height = Math.max(1, occupied - assigned);
    } else {
      height = Math.max(1, raw[i]! * scale);
      assigned += height;
    }
    items.push({
      anchorPos: pos,
      compactHeightPx: height,
      kind: 'block',
      pos: linePos,
      ...(islandTextOffset != null ? { islandTextOffset } : {}),
    });
  }
  return items;
}

/** 无正文空段：不单独撑出新纸（对齐 PDF hide 空页） */
function isEmptyParagraphNode(node: PMNode): boolean {
  if (node.type.name !== 'paragraph') return false;
  if (node.content.size === 0) return true;
  let hasAtom = false;
  node.descendants((child) => {
    if (child.isAtom) {
      hasAtom = true;
      return false;
    }
    return true;
  });
  if (hasAtom) return false;
  return node.textContent.trim() === '';
}

function markFillerItems(items: EmrPageItem[], filler: boolean): EmrPageItem[] {
  if (!filler) return items;
  return items.map((item) => ({ ...item, filler: true }));
}

function splitBlockToLineItems(
  view: EditorView,
  node: PMNode,
  pos: number,
  dom: HTMLElement,
  occupiedHeightPx: number,
): EmrPageItem[] {
  const occupied = Math.max(1, occupiedHeightPx);

  // 表格：按行切，行间 Decoration.widget 无边框 <tr>。禁止手插 tbody TR。
  if (node.type.name === 'table') {
    return splitTableToRowItems(view, node, pos, dom, occupied);
  }

  const filler = isEmptyParagraphNode(node);
  if (isAtomicPageBlock(node)) {
    return markFillerItems(
      [{ anchorPos: pos, compactHeightPx: occupied, kind: 'block', pos }],
      filler,
    );
  }

  return markFillerItems(
    visualLinesToPageItems(view, pos, dom, collectVisualLines(dom), occupied),
    filler,
  );
}

/**
 * 顶层块按 DOM 流式占位计高；textblock 再按行切开。
 * 相邻块 top 差 = 真实占位（含 margin 折叠），与 plan 坐标系一致。
 */
function collectPageItems(view: EditorView): EmrPageItem[] {
  const pm = view.dom;
  if (!(pm instanceof HTMLElement)) return [];

  type TopBlock = { dom: HTMLElement; node: PMNode; pos: number; top: number };
  const blocks: TopBlock[] = [];
  view.state.doc.forEach((node, pos) => {
    const dom = view.nodeDOM(pos);
    if (!(dom instanceof HTMLElement)) return;
    blocks.push({ dom, node, pos, top: dom.getBoundingClientRect().top });
  });

  const contentTop =
    pm.getBoundingClientRect().top + (Number.parseFloat(getComputedStyle(pm).paddingTop) || 0);

  const items: EmrPageItem[] = [];
  for (let i = 0; i < blocks.length; i++) {
    const cur = blocks[i]!;
    if (cur.node.type.name === 'emrPageBreak') {
      items.push({ anchorPos: cur.pos, compactHeightPx: 0, kind: 'break', pos: cur.pos });
      continue;
    }
    const next = blocks[i + 1];
    const startY = i === 0 ? contentTop : cur.top;
    let endY: number;
    if (next) {
      endY = next.top;
    } else {
      const mb = Number.parseFloat(getComputedStyle(cur.dom).marginBottom) || 0;
      endY = cur.dom.getBoundingClientRect().bottom + mb;
    }
    // 保留旧垫片测高：扣掉区间内 soft/岛屿垫片，以及下一块硬分页符的 marginTop（页余白）
    let occupied = endY - startY;
    occupied -= sumSoftSpacerHeightsInYRange(pm, startY, endY);
    if (next?.node.type.name === 'emrPageBreak') {
      occupied -= Number.parseFloat(getComputedStyle(next.dom).marginTop) || 0;
    }
    occupied = Math.max(1, occupied);
    items.push(...splitBlockToLineItems(view, cur.node, cur.pos, cur.dom, occupied));
  }
  return items;
}

function applyBreakBoxes(view: EditorView, plan: EmrPageLayoutPlan) {
  const breaks = [...view.dom.querySelectorAll<HTMLElement>('[data-emr-page-break]')];
  const count = Math.min(breaks.length, plan.breakBoxes.length);
  for (let i = 0; i < count; i++) {
    const el = breaks[i];
    const box = plan.breakBoxes[i];
    if (!el || !box) continue;
    el.style.marginTop = `${box.marginTopPx}px`;
    el.style.height = `${box.heightPx}px`;
    el.style.marginBottom = `${box.marginBottomPx}px`;
  }
  for (let i = count; i < breaks.length; i++) {
    const el = breaks[i];
    if (!el) continue;
    el.style.marginTop = '';
    el.style.height = '';
    el.style.marginBottom = '';
  }
}

function ensurePaperLayer(pm: HTMLElement): HTMLElement | null {
  const parent = pm.parentElement;
  if (!parent) return null;
  parent.classList.add('simple-editor__paper-stack');
  let layer = parent.querySelector(':scope > .simple-editor__papers');
  if (!(layer instanceof HTMLElement)) {
    layer = document.createElement('div');
    layer.className = 'simple-editor__papers';
    layer.setAttribute('aria-hidden', 'true');
    parent.insertBefore(layer, pm);
  }
  return layer;
}

function ensureBand(page: HTMLElement, className: string): HTMLElement {
  let el = page.querySelector(`:scope > .${className}`);
  if (!(el instanceof HTMLElement)) {
    el = document.createElement('div');
    el.className = className;
    page.appendChild(el);
  }
  return el;
}

function syncPaperCount(layer: HTMLElement, pageCount: number) {
  const desired = Math.max(1, pageCount);
  while (layer.childElementCount > desired) {
    layer.lastElementChild?.remove();
  }
  while (layer.childElementCount < desired) {
    const page = document.createElement('div');
    page.className = 'simple-editor__paper';
    layer.appendChild(page);
  }
}

function setChromeBandInnerHtml(inner: HTMLElement, html: string) {
  const next = html || '';
  if (inner.dataset.emrChromeHtml === next) return;
  inner.dataset.emrChromeHtml = next;
  inner.innerHTML = next;
}

/** 进入页眉/页脚编辑：写入 HTML 并同步缓存键，避免分页重绘用旧缓存覆盖 x/y */
export function assignChromeBandEditHtml(inner: HTMLElement, html: string) {
  setChromeBandInnerHtml(inner, html);
  inner.contentEditable = 'true';
  inner.spellcheck = false;
}

export function mountChromeBandEditHtml(inner: HTMLElement, template: string) {
  const html = template ? buildChromeEditHtml(template) : '';
  assignChromeBandEditHtml(inner, html);
}

function paintChromeBand(
  el: HTMLElement,
  band: 'footer' | 'header',
  chrome: EmrPageChrome,
  page: number,
  pageCount: number,
  context: EmrPageChromeContext,
  editingBand: 'footer' | 'header' | null,
) {
  const conf = chrome[band];
  el.dataset.emrChrome = band;
  el.dataset.chromeLabel = band === 'header' ? '页眉' : '页脚';
  applyEmrPageChromeBandDomStyle(el, conf);
  const editing = editingBand === band;
  el.classList.toggle('simple-editor__page-band--editing', editing && page === 1);
  el.classList.toggle('simple-editor__page-band--echo', editing && page !== 1);
  if (editing) {
    el.hidden = false;
    el.contentEditable = 'false';
    el.spellcheck = false;
    el.style.display = '';
    const inner = ensureEmrChromeBandInner(el);
    if (page === 1) {
      inner.contentEditable = 'true';
      inner.spellcheck = false;
      // 编辑态 DOM 仅由 enterChromeEdit / mountChromeBandEditHtml 写入，分页勿覆盖
    } else {
      inner.contentEditable = 'false';
      const text = conf.template
        ? resolveEmrPageChromeTemplate(conf.template, page, pageCount, context)
        : '';
      setChromeBandInnerHtml(inner, text ? formatChromeDisplayHtml(text) : '');
    }
    return;
  }
  el.style.display = '';
  el.contentEditable = 'false';
  if (!conf.enabled) {
    el.textContent = '';
    el.hidden = true;
    return;
  }
  el.hidden = false;
  const text = conf.template
    ? resolveEmrPageChromeTemplate(conf.template, page, pageCount, context)
    : '';
  const inner = ensureEmrChromeBandInner(el);
  inner.contentEditable = 'false';
  setChromeBandInnerHtml(inner, text ? formatChromeDisplayHtml(text) : '');
}

/** 页眉/页脚内容容器（唯一 flex 子项，避免行内字段被拉满宽） */
export function ensureEmrChromeBandInner(bandEl: HTMLElement): HTMLElement {
  let inner = bandEl.querySelector(':scope > .simple-editor__page-band-inner');
  if (inner instanceof HTMLElement) return inner;
  const legacy = document.createDocumentFragment();
  while (bandEl.firstChild) legacy.append(bandEl.firstChild);
  inner = document.createElement('div');
  inner.className = 'simple-editor__page-band-inner';
  inner.append(legacy);
  bandEl.append(inner);
  return inner;
}

/** 取首页页眉/页脚带（纸面外壳） */
export function queryEmrChromeBandEl(
  pm: HTMLElement,
  band: 'footer' | 'header',
): HTMLElement | null {
  const parent = pm.parentElement;
  const cls = band === 'header' ? 'simple-editor__page-header' : 'simple-editor__page-footer';
  const el = parent?.querySelector(
    `:scope > .simple-editor__papers > .simple-editor__paper:first-child > .${cls}`,
  );
  return el instanceof HTMLElement ? el : null;
}

/** 取首页页眉/页脚可编辑内容区 */
export function queryEmrChromeEditHost(
  pm: HTMLElement,
  band: 'footer' | 'header',
): HTMLElement | null {
  const shell = queryEmrChromeBandEl(pm, band);
  if (!shell) return null;
  return ensureEmrChromeBandInner(shell);
}

/** 每页纸上画页眉页脚（边距带内，不进正文） */
function syncPaperChrome(
  layer: HTMLElement,
  pageCount: number,
  chrome: EmrPageChrome,
  context: EmrPageChromeContext,
  editingBand: 'footer' | 'header' | null,
) {
  const papers = [...layer.querySelectorAll<HTMLElement>(':scope > .simple-editor__paper')];
  for (let i = 0; i < papers.length; i++) {
    const page = papers[i];
    if (!page) continue;
    const n = i + 1;
    const header = ensureBand(page, 'simple-editor__page-header');
    const footer = ensureBand(page, 'simple-editor__page-footer');
    paintChromeBand(header, 'header', chrome, n, pageCount, context, editingBand);
    paintChromeBand(footer, 'footer', chrome, n, pageCount, context, editingBand);
  }
}

function bindChromeImageRelayout(layer: HTMLElement, schedule: () => void) {
  for (const img of layer.querySelectorAll<HTMLImageElement>('.simple-editor__chrome-img')) {
    if (img.complete) continue;
    img.addEventListener('load', schedule, { once: true });
    img.addEventListener('error', schedule, { once: true });
  }
}

/** 文档内手动分页符数量 + 1，用于首轮就画出全部纸面页眉再实测 */
function countDocPagesFromBreaks(doc: EditorView['state']['doc']): number {
  let pages = 1;
  doc.forEach((node) => {
    if (node.type.name === 'emrPageBreak') pages += 1;
  });
  return pages;
}

/** 垫片高度噪声阈值：同位置变化小于此值则沿用上次，避免输入时 TR 垫片反复改高抖动 */
const SOFT_SPACER_STABILITY_PX = 8;

function stabilizeSoftSpacers(
  next: readonly EmrSoftSpacer[],
  prev: readonly EmrSoftSpacer[],
): EmrSoftSpacer[] {
  if (prev.length === 0) return next.map((s) => ({ ...s }));
  return next.map((s) => {
    const old = prev.find(
      (p) => p.pos === s.pos && (p.islandTextOffset ?? null) === (s.islandTextOffset ?? null),
    );
    if (!old) return { ...s };
    if (Math.abs(old.heightPx - s.heightPx) < SOFT_SPACER_STABILITY_PX) {
      return { ...s, heightPx: old.heightPx };
    }
    return { ...s };
  });
}

function buildSoftDecorations(
  doc: EditorView['state']['doc'],
  spacers: EmrPageLayoutPlan['softSpacers'],
): DecorationSet {
  const docSpacers = spacers.filter((s) => s.islandTextOffset == null);
  const decos = docSpacers.map((spacer) => {
    const at = doc.nodeAt(spacer.pos);
    const isTableRow = at?.type.name === 'tableRow';
    return Decoration.widget(
      spacer.pos,
      () => {
        if (isTableRow && at) {
          // 行间无边框垫片：落在 tbody 里、续行 <tr> 之前（Decoration 管理，非手改 DOM）
          const tr = document.createElement('tr');
          tr.className = 'emr-page-soft-break emr-table-row-soft-break';
          tr.setAttribute('aria-hidden', 'true');
          tr.setAttribute('data-emr-table-row-soft', '1');
          tr.contentEditable = 'false';
          const td = document.createElement('td');
          td.colSpan = tableRowColCount(at);
          td.style.cssText = [
            `height:${Math.max(1, Math.round(spacer.heightPx))}px`,
            'padding:0',
            'border:none',
            'background:transparent',
            'line-height:0',
            'font-size:0',
            'pointer-events:none',
          ].join(';');
          tr.append(td);
          return tr;
        }
        const el = document.createElement('span');
        el.className = 'emr-page-soft-break';
        el.setAttribute('aria-hidden', 'true');
        el.style.display = 'block';
        el.style.width = '100%';
        el.style.height = `${spacer.heightPx}px`;
        el.style.margin = '0';
        el.style.padding = '0';
        el.style.pointerEvents = 'none';
        el.contentEditable = 'false';
        return el;
      },
      {
        key: `emr-soft-${isTableRow ? 'tr' : 'sp'}-${spacer.pos}-${Math.round(spacer.heightPx)}`,
        side: -1,
      },
    );
  });
  return DecorationSet.create(doc, decos);
}

/** 捕获当前聚焦岛屿的纯文本光标（pin 优先，避免重挂后落到文末） */
function captureFocusedIslandCaret(pm: HTMLElement): { el: HTMLElement; offset: number } | null {
  const active = document.activeElement;
  let island: HTMLElement | null = null;
  if (active instanceof HTMLElement) {
    if (active.matches?.(EMR_ISLAND_INPUT_SELECTOR)) {
      island = active;
    } else {
      const closest = active.closest(EMR_ISLAND_INPUT_SELECTOR);
      if (closest instanceof HTMLElement) island = closest;
    }
  }
  if (!island || !pm.contains(island)) {
    const focused = pm.querySelector(`${EMR_ISLAND_INPUT_SELECTOR}:focus`);
    if (focused instanceof HTMLElement) island = focused;
  }
  if (!island || !pm.contains(island)) return null;
  const pinned = peekPinnedIslandCaret(island);
  const offset = pinned ?? contentEditableCaretOffset(island);
  if (offset < 0) return null;
  return { el: island, offset };
}

/** 把跨页垫片画进 atom 填写岛屿（自由文本等多行内容） */
function applyIslandSoftBreaks(view: EditorView, spacers: readonly EmrSoftSpacer[]): void {
  const pm = view.dom;
  if (!(pm instanceof HTMLElement)) return;

  type IslandSpacer = { heightPx: number; islandTextOffset: number; pos: number };
  const islandSpacers: IslandSpacer[] = [];
  for (const s of spacers) {
    if (s.islandTextOffset == null) continue;
    islandSpacers.push({
      pos: s.pos,
      heightPx: s.heightPx,
      islandTextOffset: s.islandTextOffset,
    });
  }

  // 必须在清垫片前记下光标：clear 会拆 DOM，选区一丢重测就飞末尾
  const caret = captureFocusedIslandCaret(pm);

  clearEmrIslandSoftBreaks(pm);
  if (islandSpacers.length === 0) {
    if (caret && pm.contains(caret.el)) {
      setContentEditableCaretOffset(caret.el, caret.offset);
      pinIslandCaret(caret.el, caret.offset, 500);
    }
    return;
  }

  const byAtom = new Map<number, IslandSpacer[]>();
  for (const spacer of islandSpacers) {
    const list = byAtom.get(spacer.pos);
    if (list) {
      list.push(spacer);
    } else {
      byAtom.set(spacer.pos, [spacer]);
    }
  }

  for (const [atomPos, list] of byAtom) {
    const node = view.state.doc.nodeAt(atomPos);
    if (!node?.isAtom) continue;
    const dom = view.nodeDOM(atomPos);
    if (!(dom instanceof HTMLElement)) continue;
    const island = dom.querySelector(EMR_ISLAND_INPUT_SELECTOR);
    if (!(island instanceof HTMLElement)) continue;
    // 从大偏移往小插，避免先插的垫片打乱后续偏移
    const ordered = list.slice().sort((a, b) => b.islandTextOffset - a.islandTextOffset);
    for (const spacer of ordered) {
      insertEmrIslandSoftBreakAt(island, spacer.islandTextOffset, spacer.heightPx);
    }
  }

  if (caret && pm.contains(caret.el)) {
    setContentEditableCaretOffset(caret.el, caret.offset);
    pinIslandCaret(caret.el, caret.offset, 500);
  }
}

function dispatchSoftSpacers(view: EditorView, plan: EmrPageLayoutPlan): void {
  const decos = buildSoftDecorations(view.state.doc, plan.softSpacers);
  const tr = view.state.tr.setMeta(SOFT_SPACER_META, decos).setMeta('addToHistory', false);
  view.dispatch(tr);
  applyIslandSoftBreaks(view, plan.softSpacers);
}

function measuredChromeChanged(
  prev: EmrMeasuredChromeMeta | null,
  next: EmrMeasuredChromeMeta,
): boolean {
  if (next.headerMm != null) {
    const base = prev?.headerMm ?? -1;
    if (Math.abs(next.headerMm - base) > 0.3) return true;
  }
  if (next.footerMm != null) {
    const base = prev?.footerMm ?? -1;
    if (Math.abs(next.footerMm - base) > 0.3) return true;
  }
  return false;
}

/**
 * Word 式多页纸：分页符撑满当前页；超高段落按行切开推到下一页。
 * 页眉页脚画在纸张边距带，不进文档 JSON。
 */
export const EmrPagination = Extension.create({
  name: 'emrPagination',

  addStorage() {
    return {
      chrome: createDefaultEmrPageChrome(),
      context: emptyChromeContext(),
      editingBand: null,
      relayoutNonce: 0,
    } satisfies EmrPaginationStorage;
  },

  addProseMirrorPlugins() {
    const editor = this.editor;
    return [
      new Plugin({
        key: paginationKey,
        state: {
          init: () => DecorationSet.empty,
          apply(tr, old) {
            const next = tr.getMeta(SOFT_SPACER_META) as DecorationSet | undefined;
            if (next) return next;
            if (tr.docChanged) {
              return old.map(tr.mapping, tr.doc);
            }
            return old;
          },
        },
        props: {
          decorations(state) {
            return paginationKey.getState(state) ?? DecorationSet.empty;
          },
        },
        view(editorView) {
          let raf = 0;
          let scheduleTimer = 0;
          let lastKey = '';
          let lastAppliedSpacers: EmrSoftSpacer[] = [];
          let layouting = false;
          let layoutPassesLeft = 0;
          let lastDispatchedChrome: EmrMeasuredChromeMeta | null = null;
          let lastAppliedPaddingTopPx = -1;
          let lastAppliedPaddingBottomPx = -1;
          const storeInit = editor.storage.emrPagination as EmrPaginationStorage | undefined;
          let lastRelayoutNonce = storeInit?.relayoutNonce ?? 0;

          const onWindowResize = () => schedule({ immediate: true });
          window.addEventListener('resize', onWindowResize);

          const layout = () => {
            if (editorView.isDestroyed) return;
            if (editorView.composing) return;
            if (isFillableImeActive(editor)) return;
            if (layouting) return;
            if (layoutPassesLeft <= 0) return;

            layouting = true;
            layoutPassesLeft -= 1;
            try {
              const pm = editorView.dom;
              if (!(pm instanceof HTMLElement)) return;

              const layer = ensurePaperLayer(pm);
              const store = editor.storage.emrPagination as EmrPaginationStorage;
              const chrome = store?.chrome ?? createDefaultEmrPageChrome();
              const context = store?.context ?? emptyChromeContext();
              const editingBand = store?.editingBand ?? null;

              let metrics = readPageMetrics(pm, chrome);
              if (!metrics) return;

              let chromeMeasured: EmrMeasuredChromeMeta | null = null;
              if (layer) {
                const countHint = Math.max(
                  countDocPagesFromBreaks(editorView.state.doc),
                  1,
                  layer.childElementCount,
                  Number.parseInt(pm.style.getPropertyValue('--emr-page-count') || '1', 10) || 1,
                );
                const pre = finalizeChromeMetricsForLayout(
                  pm,
                  layer,
                  chrome,
                  metrics,
                  context,
                  editingBand,
                  countHint,
                );
                metrics = pre.metrics;
                chromeMeasured = pre.measured;
              }

              if (layer) {
                const applyChromePadding = () => {
                  const bandResult = applyMeasuredChromeBands(pm, layer!, chrome, metrics);
                  if (bandResult.measured) {
                    chromeMeasured = { ...chromeMeasured, ...bandResult.measured };
                  }
                  metrics = bandResult.metrics;
                  const guard = bumpMetricsForChromeShortfall(layer!, metrics);
                  if (guard.changed) {
                    metrics = guard.metrics;
                  }
                  applyMeasuredPaddingToEditorDom(pm, metrics);
                };
                applyChromePadding();
              } else {
                applyMeasuredPaddingToEditorDom(pm, metrics);
              }

              // 必须先写回 padding 再测高：collect 用 CSS paddingTop 当 contentTop，
              // 与 plan 的 metrics.paddingTopPx 必须同一套，否则会提前换页。
              const items = collectPageItems(editorView);
              let plan = planEmrPageLayout(items, metrics);

              if (layer) {
                syncPaperCount(layer, plan.pageCount);
                syncPaperChrome(layer, plan.pageCount, chrome, context, editingBand);
                bindChromeImageRelayout(layer, schedule);

                const metricsBeforeChrome = metrics;
                const bandResult = applyMeasuredChromeBands(pm, layer, chrome, metrics);
                if (bandResult.measured) {
                  chromeMeasured = { ...chromeMeasured, ...bandResult.measured };
                }
                metrics = bandResult.metrics;
                const guard = bumpMetricsForChromeShortfall(layer, metrics);
                if (guard.changed) metrics = guard.metrics;
                applyMeasuredPaddingToEditorDom(pm, metrics);
                const chromePaddingGrew =
                  Math.abs(metrics.paddingTopPx - metricsBeforeChrome.paddingTopPx) > 0.5 ||
                  Math.abs(metrics.paddingBottomPx - metricsBeforeChrome.paddingBottomPx) > 0.5;
                if (chromePaddingGrew) {
                  plan = planEmrPageLayout(collectPageItems(editorView), metrics);
                  syncPaperCount(layer, plan.pageCount);
                  syncPaperChrome(layer, plan.pageCount, chrome, context, editingBand);
                }
              }

              const clamped = clampSoftSpacersInPlan(plan, metrics);
              const finalPlan: EmrPageLayoutPlan = {
                ...clamped,
                softSpacers: stabilizeSoftSpacers(clamped.softSpacers, lastAppliedSpacers),
              };
              const key = emrPageLayoutPlanKey(finalPlan);
              const planChanged = key !== lastKey;
              const paddingChanged =
                Math.abs(metrics.paddingTopPx - lastAppliedPaddingTopPx) > 0.5 ||
                Math.abs(metrics.paddingBottomPx - lastAppliedPaddingBottomPx) > 0.5;
              // 禁止「有岛屿垫片就每字重挂」：plan key 已含 islandTextOffset，不变则不动 DOM，
              // 否则 clear+重插会丢选区，重测光标常落到文末（输入/删除都窜尾）。
              pm.style.setProperty('--emr-page-count', String(finalPlan.pageCount));
              if (planChanged || paddingChanged) {
                lastKey = key;
                lastAppliedSpacers = finalPlan.softSpacers.map((s) => ({ ...s }));
                lastAppliedPaddingTopPx = metrics.paddingTopPx;
                lastAppliedPaddingBottomPx = metrics.paddingBottomPx;
                applyBreakBoxes(editorView, finalPlan);
                dispatchSoftSpacers(editorView, finalPlan);
              }

              if (chromeMeasured && measuredChromeChanged(lastDispatchedChrome, chromeMeasured)) {
                lastDispatchedChrome = { ...lastDispatchedChrome, ...chromeMeasured };
                layoutPassesLeft = Math.max(layoutPassesLeft, 1);
                const tr = editorView.state.tr
                  .setMeta('addToHistory', false)
                  .setMeta(EMR_MEASURED_CHROME_META, chromeMeasured);
                editorView.dispatch(tr);
              }
            } finally {
              layouting = false;
            }
          };

          const schedule = (opts?: { immediate?: boolean }) => {
            layoutPassesLeft = 3;
            if (raf) cancelAnimationFrame(raf);
            raf = 0;
            if (scheduleTimer) {
              window.clearTimeout(scheduleTimer);
              scheduleTimer = 0;
            }
            const run = () => {
              raf = requestAnimationFrame(() => {
                raf = 0;
                layout();
              });
            };
            // 连打合并重排，减轻第二页输入抖动；resize / nonce 立即跑
            if (opts?.immediate) {
              run();
              return;
            }
            scheduleTimer = window.setTimeout(() => {
              scheduleTimer = 0;
              run();
            }, 80);
          };

          schedule({ immediate: true });

          // 控制台调试：__emrPageDebug.dump()
          const dumpPageDebug = () => {
            const pm = editorView.dom;
            if (!(pm instanceof HTMLElement)) return null;
            const store = editor.storage.emrPagination as EmrPaginationStorage;
            const chrome = store?.chrome ?? createDefaultEmrPageChrome();
            const metrics = readPageMetrics(pm, chrome);
            const items = collectPageItems(editorView);
            const plan = metrics ? planEmrPageLayout(items, metrics) : null;
            const softInTables = (plan?.softSpacers ?? []).filter((sp) => {
              const $pos = editorView.state.doc.resolve(sp.pos);
              for (let d = $pos.depth; d > 0; d--) {
                if ($pos.node(d).type.name === 'table') return true;
              }
              return false;
            });
            const tables = [];
            editorView.state.doc.descendants((node, pos) => {
              if (node.type.name !== 'table') return;
              const dom = editorView.nodeDOM(pos);
              const rect = dom instanceof HTMLElement ? dom.getBoundingClientRect() : null;
              tables.push({
                pos,
                h: rect ? Math.round(rect.height) : null,
                rows: (() => {
                  let n = 0;
                  node.forEach((c) => {
                    if (c.type.name === 'tableRow') n += 1;
                  });
                  return n;
                })(),
              });
            });
            return {
              pageCount: plan?.pageCount ?? null,
              softSpacers: (plan?.softSpacers ?? []).map((sp) => ({
                pos: sp.pos,
                h: Math.round(sp.heightPx),
                island: sp.islandTextOffset ?? null,
                at: editorView.state.doc.nodeAt(sp.pos)?.type.name ?? null,
              })),
              softInTablesApprox: softInTables.map((sp) => ({
                pos: sp.pos,
                h: Math.round(sp.heightPx),
              })),
              tables,
              softBreakDom: pm.querySelectorAll('.emr-page-soft-break').length,
              tableRowSoftDom: pm.querySelectorAll('[data-emr-table-row-soft]').length,
              tableCellSoftDom: pm.querySelectorAll('[data-emr-table-cell-soft]').length,
              lastApplied: lastAppliedSpacers.map((sp) => ({
                pos: sp.pos,
                h: Math.round(sp.heightPx),
                island: sp.islandTextOffset ?? null,
              })),
            };
          };
          (window as unknown as { __emrPageDebug?: { dump: () => unknown } }).__emrPageDebug = {
            dump: dumpPageDebug,
          };

          return {
            update(view, prevState) {
              if (view.composing) return;
              if (isFillableImeActive(editor)) return;
              const store = editor.storage.emrPagination as EmrPaginationStorage | undefined;
              if (store && store.relayoutNonce !== lastRelayoutNonce) {
                lastRelayoutNonce = store.relayoutNonce;
                lastKey = '';
                schedule({ immediate: true });
                return;
              }
              if (view.state.doc.eq(prevState.doc)) return;
              // 不强制清 lastKey：计划不变则跳过重挂垫片（表格第二页输入抖的主因之一）
              schedule();
            },
            destroy() {
              if (raf) cancelAnimationFrame(raf);
              if (scheduleTimer) window.clearTimeout(scheduleTimer);
              window.removeEventListener('resize', onWindowResize);
              const w = window as unknown as { __emrPageDebug?: unknown };
              if (w.__emrPageDebug) delete w.__emrPageDebug;
              const pm = editorView.dom;
              if (pm instanceof HTMLElement) {
                clearEmrIslandSoftBreaks(pm);
              }
              const parent = pm.parentElement;
              parent?.querySelector(':scope > .simple-editor__papers')?.remove();
              parent?.classList.remove('simple-editor__paper-stack');
            },
          };
        },
      }),
    ];
  },
});

export function clonePaginationChrome(chrome: EmrPageChrome): EmrPageChrome {
  return cloneEmrPageChrome(chrome);
}
