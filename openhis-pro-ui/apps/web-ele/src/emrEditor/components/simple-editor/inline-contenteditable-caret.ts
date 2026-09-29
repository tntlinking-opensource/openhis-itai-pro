import {
  EMR_ISLAND_SOFT_BREAK_SELECTOR,
  emrIslandPlainTextOffsetBefore,
  stripEmrIslandSoftBreaksForRead,
} from './emr-island-soft-break';
import { FILLABLE_ZWSP, stripFillablePlaceholders } from './emr-fillable-json';

const ZWSP = /[\u200b\u200c\u200d\ufeff]/g;

/** 槽内无实质文本（含仅 ZWSP / 空白） */
export function isFillableContentEditableEmpty(el: HTMLElement): boolean {
  return stripFillablePlaceholders(readContentEditablePlainText(el)).length === 0;
}

/** 无实质文本（仅 :empty 占位、全空白、零宽字符等）时，各浏览器 Range 边界不一致，无法可靠比较首尾 */
function isContentEditablePlainEmpty(el: HTMLElement): boolean {
  return el.innerText.replace(/\r\n/g, '\n').replace(ZWSP, '').trim().length === 0;
}

/** contenteditable 内选区是否在开头 / 末尾（用于方向键串联） */
export function isCaretAtContentEditableStart(el: HTMLElement): boolean {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) {
    return document.activeElement === el && isContentEditablePlainEmpty(el);
  }
  const r = sel.getRangeAt(0);
  if (!r.collapsed) return false;
  if (!el.contains(r.startContainer) || !el.contains(r.endContainer)) return false;
  if (isContentEditablePlainEmpty(el)) return true;
  const start = document.createRange();
  start.selectNodeContents(el);
  start.collapse(true);
  return r.compareBoundaryPoints(Range.START_TO_START, start) === 0;
}

export function isCaretAtContentEditableEnd(el: HTMLElement): boolean {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) {
    return document.activeElement === el && isContentEditablePlainEmpty(el);
  }
  const r = sel.getRangeAt(0);
  if (!r.collapsed) return false;
  if (!el.contains(r.startContainer) || !el.contains(r.endContainer)) return false;
  if (isContentEditablePlainEmpty(el)) return true;
  const end = document.createRange();
  end.selectNodeContents(el);
  end.collapse(false);
  return r.compareBoundaryPoints(Range.END_TO_END, end) === 0;
}

export function isContentEditableSelectionCollapsed(el: HTMLElement): boolean {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) {
    return document.activeElement === el;
  }
  const r = sel.getRangeAt(0);
  if (!el.contains(r.startContainer) || !el.contains(r.endContainer)) return false;
  return r.collapsed;
}

/**
 * 将光标移到可编辑区首或尾。
 * 空槽只 focus，不写 ZWSP（写 DOM 会触发 atom NodeView 重建导致闪退）。
 */
export function setContentEditableCaret(el: HTMLElement, position: 'end' | 'start'): void {
  el.focus({ preventScroll: true });
  if (isFillableContentEditableEmpty(el) || el.childNodes.length === 0) {
    return;
  }
  const range = document.createRange();
  range.selectNodeContents(el);
  range.collapse(position === 'start');
  const sel = window.getSelection();
  if (!sel) return;
  sel.removeAllRanges();
  sel.addRange(range);
}

/** 粘贴为纯文本，避免带进 HTML 样式 */
export function insertPlainTextAtCaret(text: string): void {
  const sel = window.getSelection();
  if (!sel?.rangeCount) return;
  const range = sel.getRangeAt(0);
  range.deleteContents();
  const node = document.createTextNode(text);
  range.insertNode(node);
  range.setStartAfter(node);
  range.collapse(true);
  sel.removeAllRanges();
  sel.addRange(range);
}

/**
 * 光标前若是换行，则删掉（短时双击回车跳槽时清掉第一次插入的换行）。
 * 仅处理折叠选区。
 */
export function deleteCharBeforeCaretIfNewline(): boolean {
  const sel = window.getSelection();
  if (!sel?.rangeCount || !sel.isCollapsed) return false;
  const range = sel.getRangeAt(0);
  const node = range.startContainer;
  if (node.nodeType === Node.TEXT_NODE) {
    const text = node.textContent ?? '';
    const i = range.startOffset - 1;
    if (i < 0) return false;
    if (text[i] !== '\n') return false;
    const del = range.cloneRange();
    del.setStart(node, i);
    del.setEnd(node, range.startOffset);
    del.deleteContents();
    return true;
  }
  if (node.nodeType === Node.ELEMENT_NODE && range.startOffset > 0) {
    const prev = node.childNodes[range.startOffset - 1];
    if (prev?.nodeType === Node.TEXT_NODE) {
      const t = prev.textContent ?? '';
      if (!t.length || t[t.length - 1] !== '\n') return false;
      prev.textContent = t.slice(0, -1);
      return true;
    }
    // contenteditable 里换行常落成 <br>
    if (prev instanceof HTMLBRElement) {
      prev.remove();
      return true;
    }
  }
  return false;
}

/**
 * input / textarea：光标前若是换行则删一格，并派发 input 以同步 NodeView。
 */
export function deleteCharBeforeCaretIfNewlineInInput(
  el: HTMLInputElement | HTMLTextAreaElement,
): boolean {
  const start = el.selectionStart ?? 0;
  const end = el.selectionEnd ?? 0;
  if (start !== end || start <= 0) return false;
  if (el.value[start - 1] !== '\n') return false;
  el.value = el.value.slice(0, start - 1) + el.value.slice(start);
  el.setSelectionRange(start - 1, start - 1);
  el.dispatchEvent(new Event('input', { bubbles: true }));
  return true;
}

/** 读取可编辑区纯文本（与落库/光标同一套：textContent + 跳过垫片 + 剥 ZWSP） */
export function readContentEditablePlainText(el: HTMLElement): string {
  return stripFillablePlaceholders(
    (stripEmrIslandSoftBreaksForRead(el).textContent ?? '').replace(/\r\n/g, '\n'),
  );
}

/**
 * 折叠选区在纯文本中的字符偏移。
 * 必须跳过岛屿换页垫片：垫片为 display:block，Range.toString() 会多算换行，
 * 分页重挂垫片时用偏大偏移夹到 length → 光标飞到末页末尾。
 */
export function contentEditableCaretOffset(el: HTMLElement): number {
  const sel = window.getSelection();
  if (!sel?.rangeCount) return -1;
  const range = sel.getRangeAt(0);
  if (!el.contains(range.startContainer)) return -1;
  return emrIslandPlainTextOffsetBefore(el, range.startContainer, range.startOffset);
}

function clipIslandSelectionEndpoint(
  el: HTMLElement,
  islandRange: Range,
  node: Node | null,
  offset: number,
): { container: Node; offset: number } {
  if (node && (el.contains(node) || node === el)) {
    return { container: node, offset };
  }
  if (!node) {
    return { container: islandRange.startContainer, offset: islandRange.startOffset };
  }
  const probe = document.createRange();
  probe.setStart(node, offset);
  probe.collapse(true);
  if (probe.compareBoundaryPoints(Range.END_TO_START, islandRange) <= 0) {
    return { container: islandRange.startContainer, offset: islandRange.startOffset };
  }
  return { container: islandRange.endContainer, offset: islandRange.endOffset };
}

function readIslandPlainRangeEndpoints(
  el: HTMLElement,
  startContainer: Node,
  startOffset: number,
  endContainer: Node,
  endOffset: number,
): { from: number; to: number } | null {
  const a = emrIslandPlainTextOffsetBefore(el, startContainer, startOffset);
  const b = emrIslandPlainTextOffsetBefore(el, endContainer, endOffset);
  const from = Math.min(a, b);
  const to = Math.max(a, b);
  if (to <= from) return null;
  return { from, to };
}

/**
 * 岛屿内当前选区的纯文本起止（折叠则 from===to）。
 * 失焦后选区会丢，调用方须在聚焦时快照。
 */
export function readContentEditableSelectionOffsets(
  el: HTMLElement,
): { from: number; to: number } | null {
  const sel = window.getSelection();
  if (!sel?.rangeCount) return null;
  const range = sel.getRangeAt(0);
  const islandRange = document.createRange();
  islandRange.selectNodeContents(el);
  if (
    range.compareBoundaryPoints(Range.END_TO_START, islandRange) <= 0 ||
    range.compareBoundaryPoints(Range.START_TO_END, islandRange) >= 0
  ) {
    return null;
  }

  let startContainer: Node = range.startContainer;
  let startOffset = range.startOffset;
  let endContainer: Node = range.endContainer;
  let endOffset = range.endOffset;
  if (!el.contains(startContainer)) {
    startContainer = islandRange.startContainer;
    startOffset = islandRange.startOffset;
  }
  if (!el.contains(endContainer)) {
    endContainer = islandRange.endContainer;
    endOffset = islandRange.endOffset;
  }

  const a = emrIslandPlainTextOffsetBefore(el, startContainer, startOffset);
  const b = emrIslandPlainTextOffsetBefore(el, endContainer, endOffset);
  // 折叠光标也要 from===to；不可走非折叠助手（to<=from 会当成没选区）
  return { from: Math.min(a, b), to: Math.max(a, b) };
}

/**
 * 岛屿内非折叠选区（删键专用）。
 * anchor/focus 在 keydown 比 range.start 更可靠；仍可见选区时不当作折叠。
 */
export function readContentEditableNonCollapsedRange(
  el: HTMLElement,
): { from: number; to: number } | null {
  const sel = window.getSelection();
  if (!sel?.rangeCount) return null;
  const domRange = sel.getRangeAt(0);
  const islandRange = document.createRange();
  islandRange.selectNodeContents(el);
  if (
    domRange.compareBoundaryPoints(Range.END_TO_START, islandRange) <= 0 ||
    domRange.compareBoundaryPoints(Range.START_TO_END, islandRange) >= 0
  ) {
    return null;
  }

  const selectedInIsland =
    sel.toString().length > 0 && el.contains(domRange.commonAncestorContainer);
  if (sel.isCollapsed && !selectedInIsland) return null;

  const anchor = clipIslandSelectionEndpoint(el, islandRange, sel.anchorNode, sel.anchorOffset);
  const focus = clipIslandSelectionEndpoint(el, islandRange, sel.focusNode, sel.focusOffset);
  const byAnchorFocus = readIslandPlainRangeEndpoints(
    el,
    anchor.container,
    anchor.offset,
    focus.container,
    focus.offset,
  );
  if (byAnchorFocus) return byAnchorFocus;

  const byRange = readContentEditableSelectionOffsets(el);
  if (byRange && byRange.to > byRange.from) return byRange;

  return null;
}

/** keydown capture 阶段快照：bubble 时选区可能已被删键折叠 */
const deleteSelectionSnapshots = new WeakMap<HTMLElement, { from: number; to: number }>();

export function snapshotIslandDeleteSelection(el: HTMLElement | null | undefined): void {
  if (!el?.isContentEditable) return;
  const range = readContentEditableNonCollapsedRange(el);
  if (range) deleteSelectionSnapshots.set(el, range);
}

export function readIslandDeleteSelection(el: HTMLElement): { from: number; to: number } | null {
  return deleteSelectionSnapshots.get(el) ?? readContentEditableNonCollapsedRange(el);
}

export function clearIslandDeleteSelectionSnapshot(el: HTMLElement): void {
  deleteSelectionSnapshots.delete(el);
}

export function islandHasVisibleTextSelection(el: HTMLElement): boolean {
  const sel = window.getSelection();
  if (!sel?.rangeCount || !sel.toString()) return false;
  const range = sel.getRangeAt(0);
  return el.contains(range.commonAncestorContainer);
}

/**
 * 手改岛屿 value 后，在分页重挂垫片的几帧内临时固定光标偏移。
 * 禁止长时间 pin：否则每次 attrs 同步都会把用户光标拽回旧位置。
 */
let pinnedIslandCaret: null | { el: HTMLElement; offset: number; until: number } = null;

/** 分页 / commit 后短暂钉住光标（默认 400ms，仅覆盖 layout rAF） */
export function pinIslandCaret(el: HTMLElement, offset: number, ttlMs = 400): void {
  pinnedIslandCaret = {
    el,
    offset: Math.max(0, Math.floor(offset)),
    until: performance.now() + Math.max(80, ttlMs),
  };
}

export function peekPinnedIslandCaret(el: HTMLElement): number | null {
  if (!pinnedIslandCaret) return null;
  if (performance.now() > pinnedIslandCaret.until) {
    pinnedIslandCaret = null;
    return null;
  }
  if (pinnedIslandCaret.el === el) return pinnedIslandCaret.offset;
  // NodeView 偶发换 DOM：旧节点已卸、当前岛仍聚焦 → 迁移 pin，避免重测飞末页
  if (
    !pinnedIslandCaret.el.isConnected &&
    (document.activeElement === el || el.contains(document.activeElement))
  ) {
    pinnedIslandCaret.el = el;
    return pinnedIslandCaret.offset;
  }
  return null;
}

export function clearPinnedIslandCaret(el?: HTMLElement): void {
  if (!pinnedIslandCaret) return;
  if (el && pinnedIslandCaret.el !== el) return;
  pinnedIslandCaret = null;
}

/**
 * 岛屿内 Home / End。
 * 原生 End 在 inline contenteditable（尤其有换页垫片）下常把 Range 折到文首，
 * 随后 insertText 就会插到开头。Ctrl/Meta = 整段首尾，否则当前视觉行首尾。
 */
export function moveContentEditableCaretHomeEnd(
  el: HTMLElement,
  edge: 'end' | 'home',
  wholeIsland: boolean,
): void {
  const plain = readContentEditablePlainText(el);
  if (!plain.length) {
    setContentEditableCaret(el, edge === 'home' ? 'start' : 'end');
    pinIslandCaret(el, 0, 400);
    return;
  }
  let next = edge === 'home' ? 0 : plain.length;
  if (!wholeIsland) {
    const caret = contentEditableCaretOffset(el);
    const at = caret >= 0 ? caret : edge === 'home' ? 0 : plain.length;
    next = findVisualLineEdgeOffset(el, plain, at, edge);
  }
  setContentEditableCaretOffset(el, next);
  pinIslandCaret(el, next, 400);
}

function rangeAtIslandPlainOffset(el: HTMLElement, offset: number): Range | null {
  const clamped = Math.max(0, offset);
  const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      if (node.parentElement?.closest(EMR_ISLAND_SOFT_BREAK_SELECTOR)) {
        return NodeFilter.FILTER_REJECT;
      }
      return NodeFilter.FILTER_ACCEPT;
    },
  });
  let remaining = clamped;
  let node = walker.nextNode();
  let lastText: Node | null = null;
  while (node) {
    lastText = node;
    const len = (node.textContent ?? '').replace(/\r\n/g, '\n').length;
    if (remaining <= len) {
      const range = document.createRange();
      range.setStart(node, remaining);
      range.collapse(true);
      return range;
    }
    remaining -= len;
    node = walker.nextNode();
  }
  if (!lastText) return null;
  const range = document.createRange();
  range.setStart(lastText, (lastText.textContent ?? '').length);
  range.collapse(true);
  return range;
}

function findVisualLineEdgeOffset(
  el: HTMLElement,
  plain: string,
  caret: number,
  edge: 'end' | 'home',
): number {
  const start = Math.max(0, Math.min(caret, plain.length));
  const origin = rangeAtIslandPlainOffset(el, start);
  if (!origin) return edge === 'home' ? 0 : plain.length;
  const originRect = origin.getBoundingClientRect();
  if (originRect.height < 0.5 && originRect.width < 0.5) {
    return edge === 'home' ? 0 : plain.length;
  }
  const lineTop = originRect.top;
  if (edge === 'home') {
    let lo = 0;
    let hi = start;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      const r = rangeAtIslandPlainOffset(el, mid);
      if (r && Math.abs(r.getBoundingClientRect().top - lineTop) <= 2) hi = mid;
      else lo = mid + 1;
    }
    return lo;
  }
  let lo = start;
  let hi = plain.length;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    const r = rangeAtIslandPlainOffset(el, mid);
    if (r && Math.abs(r.getBoundingClientRect().top - lineTop) <= 2) lo = mid;
    else hi = mid - 1;
  }
  return lo;
}

/** 将折叠选区移到纯文本指定偏移（跳过岛屿换页垫片内文本） */
export function setContentEditableCaretOffset(el: HTMLElement, offset: number): void {
  if (offset < 0) return;
  const plain = stripFillablePlaceholders(
    (stripEmrIslandSoftBreaksForRead(el).textContent ?? '').replace(/\r\n/g, '\n'),
  );
  const textLen = plain.length;
  const clamped = Math.max(0, Math.min(offset, textLen));
  el.focus({ preventScroll: true });

  // 光标落在「末尾连续换行」区间内：浏览器会吸回换行前，续写粘同行。
  // 一律落到空 text park，保证 insertText 在全部尾部 \\n 之后。
  const contentBeforeTrailingNl = plain.replace(/\n+$/, '');
  const inTrailingNewlineZone = plain.endsWith('\n') && clamped > contentBeforeTrailingNl.length;
  if (inTrailingNewlineZone || (clamped === textLen && plain.endsWith('\n'))) {
    ensureTrailingCaretPark(el);
    const park = el.lastChild;
    if (
      park?.nodeType === Node.TEXT_NODE &&
      (park.textContent === FILLABLE_ZWSP || park.textContent === '')
    ) {
      const range = document.createRange();
      // 落在 ZWSP 之后，后续 insertText 不会回到 \\n 前
      const at = park.textContent === FILLABLE_ZWSP ? 1 : 0;
      range.setStart(park, at);
      range.collapse(true);
      const sel = window.getSelection();
      sel?.removeAllRanges();
      sel?.addRange(range);
      return;
    }
  }

  const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      if (node.parentElement?.closest(EMR_ISLAND_SOFT_BREAK_SELECTOR)) {
        return NodeFilter.FILTER_REJECT;
      }
      // 跳过 ZWSP 落点节点（上面已处理末尾换行）
      if (
        (node.textContent === FILLABLE_ZWSP || node.textContent === '') &&
        node === el.lastChild
      ) {
        return NodeFilter.FILTER_REJECT;
      }
      return NodeFilter.FILTER_ACCEPT;
    },
  });
  let remaining = clamped;
  let node = walker.nextNode();
  let lastText: Node | null = null;
  while (node) {
    lastText = node;
    const len = (node.textContent ?? '').replace(/\r\n/g, '\n').length;
    if (remaining <= len) {
      const range = document.createRange();
      // 落库与写入均规范为 \n，textContent 下标与 normalized 一致
      range.setStart(node, remaining);
      range.collapse(true);
      const sel = window.getSelection();
      sel?.removeAllRanges();
      sel?.addRange(range);
      return;
    }
    remaining -= len;
    node = walker.nextNode();
  }
  // 空槽：只 focus
  if (clamped === 0 || textLen === 0) {
    return;
  }
  // 偏移落在已有文本末尾（非 trailing-\\n 特例）：贴到最后 text 末
  if (lastText) {
    const range = document.createRange();
    range.setStart(lastText, (lastText.textContent ?? '').length);
    range.collapse(true);
    const sel = window.getSelection();
    sel?.removeAllRanges();
    sel?.addRange(range);
  }
}

/** 末尾换行后的 ZWSP 落点：空 text 节点上 insertText 会被浏览器吸回 \\n 前 */
function ensureTrailingCaretPark(el: HTMLElement): void {
  const last = el.lastChild;
  if (last?.nodeType === Node.TEXT_NODE && last.textContent === FILLABLE_ZWSP) return;
  if (last?.nodeType === Node.TEXT_NODE && last.textContent === '') {
    last.textContent = FILLABLE_ZWSP;
    return;
  }
  el.appendChild(document.createTextNode(FILLABLE_ZWSP));
}

/** attrs 与 DOM 不一致时强制对齐（撤销/重做后） */
export function syncContentEditableFromValue(
  el: HTMLElement,
  value: string,
  caretOffset?: number,
  options?: { pinCaret?: boolean },
): void {
  const next = value.replace(/\r\n/g, '\n');
  if (readContentEditablePlainText(el) !== next) {
    el.textContent = next;
  }
  // 以 \\n 结尾时补空 text 落点，供 caret 停在换行后
  if (next.endsWith('\n')) {
    ensureTrailingCaretPark(el);
  }
  if (typeof caretOffset === 'number') {
    setContentEditableCaretOffset(el, caretOffset);
    // textContent 赋值会同步触发 input，把 pin 盖成 0；必须在设光标后再钉一次
    pinIslandCaret(el, Math.min(caretOffset, next.length), options?.pinCaret ? 800 : 400);
  }
}

/** 分页 / commit 后多帧回焦：避免文书锁下 PM 抢焦导致换行后无法再输入 */
export function scheduleIslandCaretRestore(el: HTMLElement, value: string, caret: number): void {
  const clamped = Math.max(0, Math.min(caret, value.replace(/\r\n/g, '\n').length));
  // 钉住足够久，覆盖分页重挂岛屿垫片（约 2～3 帧 + layout）
  pinIslandCaret(el, clamped, 1200);
  const apply = () => {
    if (!el.isConnected) return;
    syncContentEditableFromValue(el, value, clamped, { pinCaret: true });
    if (document.activeElement !== el) {
      el.focus({ preventScroll: true });
      setContentEditableCaretOffset(el, clamped);
    }
  };
  apply();
  requestAnimationFrame(() => {
    apply();
    requestAnimationFrame(() => {
      apply();
      requestAnimationFrame(() => {
        apply();
        window.setTimeout(() => {
          apply();
          clearPinnedIslandCaret(el);
        }, 80);
      });
    });
  });
}
