const ZWSP = /[\u200b\u200c\u200d\ufeff]/g;

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

/** 将光标移到可编辑区首或尾 */
export function setContentEditableCaret(el: HTMLElement, position: 'end' | 'start'): void {
  el.focus();
  if (el.childNodes.length === 0) {
    el.appendChild(document.createTextNode(''));
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

/** 读取可编辑区纯文本（与 innerText 一致，便于换行） */
export function readContentEditablePlainText(el: HTMLElement): string {
  return el.innerText.replace(/\r\n/g, '\n');
}
