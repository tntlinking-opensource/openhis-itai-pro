/**
 * 页眉/页脚实测与 plan 预留高度对齐；第 2 页起按 DOM 校正 soft spacer。
 */
import type { EditorView } from '@tiptap/pm/view';

import { measureEmrChromeBandHeightPx, measureEmrChromeBandHeightMm } from './emr-page-chrome';
import {
  maxSoftSpacerHeightPx,
  type EmrPageLayoutPlan,
  type EmrPageMetrics,
  type EmrSoftSpacer,
} from './emr-page-layout';

function isPaginationChromeEl(el: HTMLElement): boolean {
  return el.classList.contains('emr-page-soft-break') || el.hasAttribute('data-emr-page-break');
}

/** 正文顶层块（排除分页 widget，避免把 spacer 当首块） */
export function listPmContentBlocks(pm: HTMLElement): HTMLElement[] {
  return [...pm.children].filter(
    (c): c is HTMLElement =>
      c instanceof HTMLElement &&
      !isPaginationChromeEl(c) &&
      c.getBoundingClientRect().height > 0.5,
  );
}

function findTopLevelBlockPos(view: EditorView, target: HTMLElement): number | null {
  let found: number | null = null;
  view.state.doc.forEach((node, pos) => {
    if (node.type.name === 'emrPageBreak') return;
    const dom = view.nodeDOM(pos);
    if (dom === target || (dom instanceof Node && dom.contains(target))) found = pos;
  });
  return found;
}

/** spacer.pos 可能在段落行内：用 domAtPos 取邻近元素 */
function resolveDomNearPos(view: EditorView, pos: number): HTMLElement | null {
  const clamped = Math.max(0, Math.min(pos, view.state.doc.content.size));
  try {
    const hit = view.domAtPos(clamped);
    let node: Node | null = hit.node;
    if (node.nodeType === Node.TEXT_NODE) node = node.parentElement;
    if (node instanceof HTMLElement) {
      if (node.classList.contains('emr-page-soft-break')) {
        const next = node.nextElementSibling;
        if (next instanceof HTMLElement) return next;
        const parent = node.parentElement;
        if (parent instanceof HTMLElement) return parent;
      }
      return node;
    }
  } catch {
    // ignore
  }
  let found: HTMLElement | null = null;
  view.state.doc.forEach((_node, nodePos) => {
    if (nodePos !== pos) return;
    const dom = view.nodeDOM(nodePos);
    if (dom instanceof HTMLElement) found = dom;
  });
  return found;
}

/** 该页首个可见正文块（跳过 spacer；优先有文字的块） */
function findFirstPageContentBlock(blocks: HTMLElement[], paperRect: DOMRect): HTMLElement | null {
  const onPage = blocks
    .filter((block) => {
      const r = block.getBoundingClientRect();
      return r.top >= paperRect.top + 1 && r.top < paperRect.bottom - 1 && r.height > 0.5;
    })
    .sort((a, b) => a.getBoundingClientRect().top - b.getBoundingClientRect().top);

  for (const block of onPage) {
    if (block.textContent?.trim()) return block;
  }
  return onPage[0] ?? null;
}

function measureBandMaxPx(layer: HTMLElement, band: 'footer' | 'header'): number {
  const cls = band === 'header' ? '.simple-editor__page-header' : '.simple-editor__page-footer';
  let maxPx = 0;
  for (const el of layer.querySelectorAll<HTMLElement>(
    `:scope > .simple-editor__paper > ${cls}:not([hidden])`,
  )) {
    maxPx = Math.max(maxPx, measureEmrChromeBandHeightPx(el));
  }
  return maxPx;
}

/** 页眉/页脚 DOM 超出 plan 预留的 px 缺口 */
export function measureChromeBandPaddingShortfallPx(
  layer: HTMLElement,
  metrics: EmrPageMetrics,
  band: 'footer' | 'header',
): number {
  const maxPx = measureBandMaxPx(layer, band);
  const reserved = band === 'header' ? metrics.paddingTopPx : metrics.paddingBottomPx;
  return Math.max(0, maxPx - reserved);
}

/** 按纸面实测抬高 metrics.padding（仅当缺口 > 1px） */
export function bumpMetricsForChromeShortfall(
  layer: HTMLElement,
  metrics: EmrPageMetrics,
): { changed: boolean; metrics: EmrPageMetrics } {
  const topShort = measureChromeBandPaddingShortfallPx(layer, metrics, 'header');
  const bottomShort = measureChromeBandPaddingShortfallPx(layer, metrics, 'footer');
  if (topShort <= 1 && bottomShort <= 1) {
    return { changed: false, metrics };
  }
  return {
    changed: true,
    metrics: {
      ...metrics,
      paddingBottomPx: metrics.paddingBottomPx + bottomShort,
      paddingTopPx: metrics.paddingTopPx + topShort,
    },
  };
}

/** 从纸面页眉/页脚带收集实测 mm（回写 heightMm 缓存；取各页最大值） */
export function collectMeasuredChromeBandsMm(
  layer: HTMLElement,
  chrome: { footer: { enabled: boolean }; header: { enabled: boolean } },
): { footerMm?: number; headerMm?: number } {
  const measured: { footerMm?: number; headerMm?: number } = {};
  if (chrome.header.enabled) {
    let maxMm = 0;
    for (const el of layer.querySelectorAll<HTMLElement>(
      ':scope > .simple-editor__paper > .simple-editor__page-header:not([hidden])',
    )) {
      maxMm = Math.max(maxMm, measureEmrChromeBandHeightMm(el));
    }
    if (maxMm > 0) measured.headerMm = maxMm;
  }
  if (chrome.footer.enabled) {
    let maxMm = 0;
    for (const el of layer.querySelectorAll<HTMLElement>(
      ':scope > .simple-editor__paper > .simple-editor__page-footer:not([hidden])',
    )) {
      maxMm = Math.max(maxMm, measureEmrChromeBandHeightMm(el));
    }
    if (maxMm > 0) measured.footerMm = maxMm;
  }
  return measured;
}

const GAP_ALIGN_TOLERANCE_PX = 2;
/** 同页内 spacer 超过此高度视为「页中大空白」，应收掉 */
const MID_PAGE_SPACER_KEEP_PX = 8;

/** 把持久化的 spacer 追加量合并进 base plan */
export function mergeSpacerExtrasIntoPlan(
  plan: EmrPageLayoutPlan,
  extras: ReadonlyMap<number, number>,
): EmrPageLayoutPlan {
  if (extras.size === 0) return plan;
  const byPos = new Map<number, number>();
  for (const spacer of plan.softSpacers) {
    byPos.set(spacer.pos, spacer.heightPx);
  }
  for (const [pos, extra] of extras) {
    if (extra <= 0) continue;
    byPos.set(pos, (byPos.get(pos) ?? 0) + extra);
  }
  const softSpacers: EmrSoftSpacer[] = [...byPos.entries()]
    .map(([pos, heightPx]) => ({ pos, heightPx }))
    .sort((a, b) => a.pos - b.pos);
  return { ...plan, softSpacers };
}

/** 从 base / merged plan 差分更新持久化追加量 */
export function syncSpacerExtrasFromPlans(
  basePlan: EmrPageLayoutPlan,
  mergedPlan: EmrPageLayoutPlan,
  extras: Map<number, number>,
): void {
  const baseByPos = new Map(basePlan.softSpacers.map((s) => [s.pos, s.heightPx]));
  const mergedByPos = new Map(mergedPlan.softSpacers.map((s) => [s.pos, s.heightPx]));
  const posSet = new Set([...baseByPos.keys(), ...mergedByPos.keys(), ...extras.keys()]);
  for (const pos of posSet) {
    const extra = (mergedByPos.get(pos) ?? 0) - (baseByPos.get(pos) ?? 0);
    if (extra > 0.5) extras.set(pos, Math.ceil(extra));
    else extras.delete(pos);
  }
}

/**
 * 第 2 页起：正文顶对齐到 paperTop + paddingTop（与第 1 页 CSS padding 一致）。
 * 优先按 soft-break 后沿校正（行内切开）；再回退到页首块顶。
 */
export function refinePlanSoftSpacersForUniformHeaderGap(
  pm: HTMLElement,
  layer: HTMLElement,
  plan: EmrPageLayoutPlan,
  view: EditorView,
  paddingTopPx: number,
): { changed: boolean; plan: EmrPageLayoutPlan } {
  const papers = [...layer.querySelectorAll<HTMLElement>(':scope > .simple-editor__paper')];
  const blocks = listPmContentBlocks(pm);
  const softSpacers = plan.softSpacers.map((s) => ({ ...s }));
  let changed = false;
  const alignedPages = new Set<number>();

  const bumpSpacerAt = (pos: number, extra: number) => {
    const add = Math.ceil(extra);
    if (add <= 0) return;
    const idx = softSpacers.findIndex((s) => s.pos === pos);
    if (idx >= 0) {
      softSpacers[idx] = { pos, heightPx: softSpacers[idx]!.heightPx + add };
    } else {
      softSpacers.push({ pos, heightPx: add });
    }
    changed = true;
  };

  // A. 行内/块前 soft-break：用垫片底边当「下页正文顶」
  for (const el of pm.querySelectorAll<HTMLElement>('.emr-page-soft-break')) {
    const spacerBottom = el.getBoundingClientRect().bottom;
    const pageIndex = resolveContentStartPageAfterSoftBreak(el, papers);
    if (pageIndex <= 0) continue;

    const paper = papers[pageIndex]!;
    const expectedTop = paper.getBoundingClientRect().top + paddingTopPx;
    const delta = expectedTop - spacerBottom;
    if (delta <= GAP_ALIGN_TOLERANCE_PX) {
      alignedPages.add(pageIndex);
      continue;
    }

    let spacerPos: number | null = null;
    try {
      spacerPos = view.posAtDOM(el, 0);
    } catch {
      spacerPos = null;
    }
    if (spacerPos == null) continue;

    let best = softSpacers[0];
    let bestDist = Number.POSITIVE_INFINITY;
    for (const s of softSpacers) {
      const d = Math.abs(s.pos - spacerPos);
      if (d < bestDist) {
        bestDist = d;
        best = s;
      }
    }
    if (!best || bestDist > 4) {
      bumpSpacerAt(spacerPos, delta);
    } else {
      bumpSpacerAt(best.pos, delta);
    }
    alignedPages.add(pageIndex);
  }

  // C. 兜底：正文顶仍压进页眉带（垫片被误删时），在段内 soft-break 或行首补 spacer
  for (let pageIndex = 1; pageIndex < papers.length; pageIndex++) {
    const paper = papers[pageIndex];
    if (!paper) continue;
    const paperRect = paper.getBoundingClientRect();
    const expectedTop = paperRect.top + paddingTopPx;
    if (alignedPages.has(pageIndex)) {
      // 已对齐也复核：soft-break 后沿仍可能在页眉内
      let ok = true;
      for (const el of pm.querySelectorAll<HTMLElement>('.emr-page-soft-break')) {
        const sb = el.getBoundingClientRect().bottom;
        if (resolveContentStartPageAfterSoftBreak(el, papers) !== pageIndex) continue;
        if (sb < expectedTop - GAP_ALIGN_TOLERANCE_PX) {
          ok = false;
          break;
        }
      }
      if (ok) continue;
    }

    for (const el of pm.querySelectorAll<HTMLElement>('.emr-page-soft-break')) {
      const sb = el.getBoundingClientRect().bottom;
      if (resolveContentStartPageAfterSoftBreak(el, papers) !== pageIndex) continue;
      const delta = expectedTop - sb;
      if (delta <= GAP_ALIGN_TOLERANCE_PX) continue;
      let spacerPos: number | null = null;
      try {
        spacerPos = view.posAtDOM(el, 0);
      } catch {
        spacerPos = null;
      }
      if (spacerPos == null) continue;
      bumpSpacerAt(spacerPos, delta);
      alignedPages.add(pageIndex);
      break;
    }
  }

  // B. 仍未对齐的页：用页首可见块顶（块级换页）
  for (let pageIndex = 0; pageIndex < papers.length; pageIndex++) {
    if (pageIndex === 0 || alignedPages.has(pageIndex)) continue;

    const paper = papers[pageIndex];
    if (!paper) continue;

    const paperRect = paper.getBoundingClientRect();
    const expectedTop = paperRect.top + paddingTopPx;
    const firstBlock = findFirstPageContentBlock(blocks, paperRect);
    if (!firstBlock) continue;

    const firstBlockTop = firstBlock.getBoundingClientRect().top;
    // 块从上一页续排：首块顶在纸外，只能在段内 soft-break 上补 spacer，勿在块首插
    if (firstBlockTop < paperRect.top + paddingTopPx * 0.35) continue;

    const delta = expectedTop - firstBlockTop;
    if (delta <= GAP_ALIGN_TOLERANCE_PX) continue;

    const pos = findTopLevelBlockPos(view, firstBlock);
    if (pos == null) continue;
    bumpSpacerAt(pos, delta);
  }

  if (!changed) return { changed: false, plan };
  softSpacers.sort((a, b) => a.pos - b.pos);
  return { changed: true, plan: { ...plan, softSpacers } };
}

function findPaperIndexAtY(papers: HTMLElement[], y: number): number {
  for (let i = 0; i < papers.length; i++) {
    const r = papers[i]!.getBoundingClientRect();
    if (y >= r.top - 1 && y < r.bottom + 1) return i;
  }
  return -1;
}

/**
 * soft-break 后正文起始落在第几页（0-based）。
 * 禁止无条件 pageAtTop+1：页中垫片会被误判成跨页，从而被拉到数百 px。
 * 偏短跨页垫片：后随正文可能仍落在本页，但垫片底已贴近页底/页脚 → 视为推到下页。
 */
function resolveContentStartPageAfterSoftBreak(
  el: HTMLElement,
  papers: HTMLElement[],
  paddingBottomPx = 0,
): number {
  const r = el.getBoundingClientRect();
  const pageAtTop = findPaperIndexAtY(papers, r.top + 1);
  const pageAtBottom = findPaperIndexAtY(papers, r.bottom + 1);

  let probeY = r.bottom + 2;
  let foundFollowing = false;
  let node: Node | null = el.nextSibling;
  while (node) {
    if (node instanceof HTMLElement) {
      if (node.classList.contains('emr-page-soft-break')) {
        node = node.nextSibling;
        continue;
      }
      const br = node.getBoundingClientRect();
      if (br.height > 0.5) {
        probeY = br.top + 1;
        foundFollowing = true;
        break;
      }
    } else if (node.nodeType === Node.TEXT_NODE) {
      const raw = node.textContent ?? '';
      if (raw.trim()) {
        const range = document.createRange();
        range.selectNodeContents(node);
        for (const rect of range.getClientRects()) {
          if (rect.height > 0.5) {
            probeY = rect.top + 1;
            foundFollowing = true;
            break;
          }
        }
        if (foundFollowing) break;
      }
    }
    node = node.nextSibling;
  }

  const pageAtContent = findPaperIndexAtY(papers, probeY);
  if (pageAtContent > pageAtTop) return pageAtContent;
  if (pageAtBottom > pageAtTop) return pageAtBottom;

  // 后随正文仍在本页，但垫片已伸到页底/页脚带：典型「偏短跨页垫片」
  if (pageAtTop >= 0 && pageAtTop + 1 < papers.length) {
    const paper = papers[pageAtTop]!;
    const paperBottom = paper.getBoundingClientRect().bottom;
    const nearFooter = r.bottom >= paperBottom - Math.max(24, paddingBottomPx * 0.85) - 8;
    if (nearFooter || !foundFollowing) return pageAtTop + 1;
  }
  return pageAtContent >= 0 ? pageAtContent : -1;
}

/** 垫片是否跨页缝（段内行切开时 spacer 顶/底可能在不同纸上） */
function isPageBoundarySoftBreak(el: HTMLElement, papers: HTMLElement[]): boolean {
  const r = el.getBoundingClientRect();
  if (r.height < 0.5) return false;
  const pageAtTop = findPaperIndexAtY(papers, r.top + 1);
  const pageAtBottom = findPaperIndexAtY(papers, r.bottom + 1);
  return pageAtBottom > pageAtTop;
}

function findSoftBreakElNearPos(
  pm: HTMLElement,
  view: EditorView,
  pos: number,
): HTMLElement | null {
  let best: HTMLElement | null = null;
  let bestDist = Number.POSITIVE_INFINITY;
  for (const el of pm.querySelectorAll<HTMLElement>('.emr-page-soft-break')) {
    try {
      const hit = view.posAtDOM(el, 0);
      const d = Math.abs(hit - pos);
      if (d < bestDist) {
        bestDist = d;
        best = el;
      }
    } catch {
      // ignore
    }
  }
  return bestDist <= 4 ? best : null;
}

/**
 * 仅收掉「明显在页中」的过大 soft spacer。
 * 绝不改页首块的 spacer（否则会把第 2/3 页正文顶回页眉下）。
 * 不做页首 overshoot 下调，避免与 refine 互相打架。
 */
export function shrinkOversizedSoftSpacers(
  pm: HTMLElement,
  layer: HTMLElement,
  plan: EmrPageLayoutPlan,
  view: EditorView,
  paddingTopPx: number,
): { changed: boolean; plan: EmrPageLayoutPlan } {
  const papers = [...layer.querySelectorAll<HTMLElement>(':scope > .simple-editor__paper')];
  if (papers.length === 0 || plan.softSpacers.length === 0) {
    return { changed: false, plan };
  }

  const blocks = listPmContentBlocks(pm);
  const softSpacers = plan.softSpacers.map((s) => ({ ...s }));
  let changed = false;

  /** 距页内容顶至少这么多，才视为「页中」而非页首 */
  const midPageMinFromTopPx = Math.max(48, paddingTopPx * 0.35);

  const setSpacerHeight = (pos: number, heightPx: number) => {
    const idx = softSpacers.findIndex((s) => s.pos === pos);
    if (idx < 0) return;
    const next = Math.max(0, Math.round(heightPx));
    if (Math.abs(softSpacers[idx]!.heightPx - next) <= GAP_ALIGN_TOLERANCE_PX) return;
    softSpacers[idx] = { pos, heightPx: next };
    changed = true;
  };

  for (const spacer of [...softSpacers]) {
    if (spacer.heightPx <= MID_PAGE_SPACER_KEEP_PX) continue;

    const softBreakEl = findSoftBreakElNearPos(pm, view, spacer.pos);
    // 跨页缝垫片：删了正文会顶进页眉，绝不收
    if (softBreakEl && isPageBoundarySoftBreak(softBreakEl, papers)) continue;

    const targetDom = resolveDomNearPos(view, spacer.pos);
    if (!targetDom) continue;

    const currTop = targetDom.getBoundingClientRect().top;
    const currPaper = findPaperIndexAtY(papers, currTop);
    if (currPaper < 0) continue;

    const paper = papers[currPaper]!;
    const paperRect = paper.getBoundingClientRect();
    const expectedTop = paperRect.top + paddingTopPx;

    // 页首块 / 仍在页眉带附近：绝不收
    const firstOnPage = findFirstPageContentBlock(blocks, paperRect);
    if (firstOnPage && (firstOnPage === targetDom || firstOnPage.contains(targetDom))) continue;
    if (softBreakEl) {
      const sbBottom = softBreakEl.getBoundingClientRect().bottom;
      if (sbBottom <= expectedTop + midPageMinFromTopPx) continue;
    }
    if (currTop <= expectedTop + midPageMinFromTopPx) continue;

    let prevBlock: HTMLElement | null = null;
    const children = [...pm.children].filter((c): c is HTMLElement => c instanceof HTMLElement);
    // 行内 spacer：爬到顶层块再找前驱
    let top = targetDom;
    while (top.parentElement && top.parentElement !== pm) {
      top = top.parentElement;
    }
    const targetIndex = children.indexOf(top);
    for (let i = targetIndex - 1; i >= 0; i--) {
      const child = children[i]!;
      if (isPaginationChromeEl(child)) continue;
      if (child.getBoundingClientRect().height < 0.5) continue;
      prevBlock = child;
      break;
    }
    if (!prevBlock) continue;

    const prevPaper = findPaperIndexAtY(papers, prevBlock.getBoundingClientRect().top);
    if (prevPaper !== currPaper || prevPaper < 0) continue;

    // 同页且目标已离开页首区：页中大空白，收掉
    setSpacerHeight(spacer.pos, 0);
  }

  if (!changed) return { changed: false, plan };
  const nextSpacers = softSpacers.filter((s) => s.heightPx > 0.5);
  return { changed: true, plan: { ...plan, softSpacers: nextSpacers } };
}

/**
 * 按「垫片文档序 ↔ 目标页」对齐：第 i 个垫片负责推到第 i+1 页。
 * 不依赖 posAtDOM / target 解析（二者在偏短未跨页时经常匹配失败，导致高度锁死在 256）。
 */
export function alignCrossPageSoftSpacerBottoms(
  pm: HTMLElement,
  layer: HTMLElement,
  plan: EmrPageLayoutPlan,
  _view: EditorView,
  paddingTopPx: number,
  metrics: EmrPageMetrics,
): EmrPageLayoutPlan {
  const papers = [...layer.querySelectorAll<HTMLElement>(':scope > .simple-editor__paper')];
  if (papers.length < 2 || plan.softSpacers.length === 0) return plan;

  // 纸面实测高度优先，避免 CSS 变量读成 297（mm 被当 px）把 maxH 卡死
  const paperH = Math.round(papers[0]!.getBoundingClientRect().height);
  const safeMetrics: EmrPageMetrics = {
    ...metrics,
    pageHeightPx: Math.max(metrics.pageHeightPx, paperH),
  };
  const maxH = maxSoftSpacerHeightPx(safeMetrics);

  const ordered = plan.softSpacers.map((s) => ({ ...s })).sort((a, b) => a.pos - b.pos);
  const breaks = [...pm.querySelectorAll<HTMLElement>('.emr-page-soft-break')].sort(
    (a, b) => a.getBoundingClientRect().top - b.getBoundingClientRect().top,
  );

  const findFirstTextTop = (paperRect: DOMRect): number | null => {
    let minTop = Infinity;
    const range = document.createRange();
    const walker = document.createTreeWalker(pm, NodeFilter.SHOW_TEXT);
    let n = walker.nextNode();
    while (n) {
      if (n.parentElement?.closest('.emr-page-soft-break, [data-emr-page-break]')) {
        n = walker.nextNode();
        continue;
      }
      const raw = n.textContent ?? '';
      if (!raw.trim()) {
        n = walker.nextNode();
        continue;
      }
      range.selectNodeContents(n);
      for (const rect of range.getClientRects()) {
        if (rect.height < 1) continue;
        // 含页眉带内的「顶进页眉」正文，否则 HEADER_OVERLAP 时找不到 firstTop
        if (rect.top < paperRect.top - 4 || rect.top >= paperRect.bottom - 1) continue;
        if (rect.top < minTop) minTop = rect.top;
      }
      n = walker.nextNode();
    }
    return Number.isFinite(minTop) ? minTop : null;
  };

  let changed = false;
  let shiftPx = 0;

  const n = Math.min(ordered.length, breaks.length, papers.length - 1);
  for (let i = 0; i < n; i++) {
    const spacer = ordered[i]!;
    const el = breaks[i]!;
    const targetPage = i + 1;
    const paper = papers[targetPage];
    if (!paper) continue;

    const paperRect = paper.getBoundingClientRect();
    const expectedTop = paperRect.top + paddingTopPx;
    const firstTop = findFirstTextTop(paperRect);

    let delta = 0;
    if (firstTop != null) {
      delta = expectedTop - (firstTop + shiftPx);
    } else {
      // 目标页暂无正文：把垫片底边推到内容顶
      const spacerTop = el.getBoundingClientRect().top + shiftPx;
      delta = expectedTop - spacerTop - spacer.heightPx;
    }

    if (Math.abs(delta) <= GAP_ALIGN_TOLERANCE_PX) continue;

    const nextH = Math.min(maxH, Math.max(1, Math.round(spacer.heightPx + delta)));
    if (Math.abs(nextH - spacer.heightPx) <= GAP_ALIGN_TOLERANCE_PX) continue;

    shiftPx += nextH - spacer.heightPx;
    ordered[i] = { pos: spacer.pos, heightPx: nextH };
    changed = true;
  }

  if (!changed) return plan;
  ordered.sort((a, b) => a.pos - b.pos);
  return { ...plan, softSpacers: ordered };
}

/** @deprecated 使用 alignCrossPageSoftSpacerBottoms */
export function snapSoftSpacersToContentTop(
  pm: HTMLElement,
  layer: HTMLElement,
  plan: EmrPageLayoutPlan,
  view: EditorView,
  paddingTopPx: number,
  metrics: EmrPageMetrics,
): EmrPageLayoutPlan {
  return alignCrossPageSoftSpacerBottoms(pm, layer, plan, view, paddingTopPx, metrics);
}

/** @deprecated 使用 alignCrossPageSoftSpacerBottoms */
export function adjustSoftSpacersForHeaderOverlapOnce(
  pm: HTMLElement,
  layer: HTMLElement,
  plan: EmrPageLayoutPlan,
  view: EditorView,
  paddingTopPx: number,
  metrics?: EmrPageMetrics,
): EmrPageLayoutPlan {
  if (!metrics) return plan;
  return alignCrossPageSoftSpacerBottoms(pm, layer, plan, view, paddingTopPx, metrics);
}

/** @deprecated 使用 refinePlanSoftSpacersForUniformHeaderGap */
export function refinePlanSoftSpacersForHeaderOverlap(
  pm: HTMLElement,
  layer: HTMLElement,
  plan: EmrPageLayoutPlan,
  view: EditorView,
): { changed: boolean; plan: EmrPageLayoutPlan } {
  const paddingTopPx = Number.parseFloat(getComputedStyle(pm).paddingTop) || 0;
  return refinePlanSoftSpacersForUniformHeaderGap(pm, layer, plan, view, paddingTopPx);
}
