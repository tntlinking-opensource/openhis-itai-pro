import type { Editor } from '@tiptap/core';
import type { EditorView } from '@tiptap/pm/view';

import { commitFillableIslandValue } from '#/emrEditor/components/simple-editor/fillable-island-commit';
import {
  pinIslandCaret,
  readContentEditablePlainText,
  readContentEditableSelectionOffsets,
  syncContentEditableFromValue,
} from '#/emrEditor/components/simple-editor/inline-contenteditable-caret';

/** 旧 textarea/input 数据元岛（辅助检查等） */
export interface EmrInputIslandInsertTarget {
  kind: 'input';
  el: HTMLInputElement | HTMLTextAreaElement;
  end: number;
  start: number;
}

/**
 * 方案 B contenteditable 填写岛（自由文本 / 数据元文本槽）。
 * 工具栏抢焦后选区会丢，须在聚焦时快照 start/end。
 */
export interface EmrContentEditableIslandInsertTarget {
  kind: 'contentEditable';
  el: HTMLElement;
  end: number;
  start: number;
}

export type EmrIslandInsertTarget =
  | EmrContentEditableIslandInsertTarget
  | EmrInputIslandInsertTarget;

/** TipTap 选区快照：工具栏抢焦点后仍插入到打开弹窗前的光标处 */
export interface EmrEditorInsertSelection {
  from: number;
  to: number;
}

/** 与 NodeView 可编辑区类名对齐（不含只读展示） */
const CONTENTEDITABLE_ISLAND_SELECTOR = [
  '.emr-fillable-field__input',
  '.emr-inline-text__field',
  '.emr-de__text-field',
  '.emr-free-text__text',
].join(', ');

function resolveContentEditableIslandEl(
  from: EventTarget | Node | null | undefined,
): HTMLElement | null {
  if (!(from instanceof Node)) return null;
  const el = from instanceof HTMLElement ? from : from.parentElement;
  if (!el) return null;
  if (el.isContentEditable && el.matches(CONTENTEDITABLE_ISLAND_SELECTOR)) return el;
  const closest = el.closest(CONTENTEDITABLE_ISLAND_SELECTOR);
  if (closest instanceof HTMLElement && closest.isContentEditable) return closest;
  return null;
}

/** 岛屿 DOM → 带 value 的 atom 文档位 */
function resolveFillableAtomPosFromIsland(view: EditorView, island: HTMLElement): number | null {
  const wrapper = island.closest('[data-node-view-wrapper]');
  const target = wrapper instanceof HTMLElement ? wrapper : island;
  try {
    const pos = view.posAtDOM(target, 0);
    const direct = view.state.doc.nodeAt(pos);
    if (direct && 'value' in direct.attrs) return pos;
    const $pos = view.state.doc.resolve(Math.max(0, Math.min(pos, view.state.doc.content.size)));
    const after = $pos.nodeAfter;
    if (after && 'value' in after.attrs) return $pos.pos;
    const before = $pos.nodeBefore;
    if (before && 'value' in before.attrs) return $pos.pos - before.nodeSize;
  } catch {
    /* NodeView 偶发解析失败 */
  }
  return null;
}

/** 捕获当前焦点是否在 EMR 可编辑岛上（含自由文本 contenteditable） */
export function captureEmrIslandInsertTarget(
  root?: HTMLElement | null,
): EmrIslandInsertTarget | null {
  const ae = document.activeElement;

  if (ae instanceof HTMLTextAreaElement || ae instanceof HTMLInputElement) {
    if (ae.disabled || ae.readOnly) return null;
    const island = ae.closest('.emr-de');
    if (!island) return null;
    if (root && !root.contains(island)) return null;
    const start = ae.selectionStart ?? ae.value.length;
    const end = ae.selectionEnd ?? start;
    return { kind: 'input', el: ae, start, end };
  }

  const island = resolveContentEditableIslandEl(ae);
  if (!island) return null;
  if (root && !root.contains(island)) return null;
  const offsets = readContentEditableSelectionOffsets(island);
  if (!offsets) {
    const len = readContentEditablePlainText(island).length;
    return { kind: 'contentEditable', el: island, start: len, end: len };
  }
  return {
    kind: 'contentEditable',
    el: island,
    start: offsets.from,
    end: offsets.to,
  };
}

/** 捕获编辑器当前选区（Word 化正文流插入用） */
export function captureEmrEditorInsertSelection(
  editor: Editor | null | undefined,
): EmrEditorInsertSelection | null {
  if (!editor || editor.isDestroyed) return null;
  const { from, to } = editor.state.selection;
  return { from, to };
}

/** 向 textarea/input 光标处插入纯文本，并触发 input 以便 NodeView 回写 attrs */
function insertTextIntoInputIsland(target: EmrInputIslandInsertTarget, text: string): boolean {
  const { el } = target;
  if (!el.isConnected) return false;
  const start = Math.max(0, Math.min(target.start, el.value.length));
  const end = Math.max(start, Math.min(target.end, el.value.length));
  const next = `${el.value.slice(0, start)}${text}${el.value.slice(end)}`;
  const proto =
    el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  const valueSetter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
  if (valueSetter) {
    valueSetter.call(el, next);
  } else {
    el.value = next;
  }
  const caret = start + text.length;
  try {
    el.setSelectionRange(caret, caret);
  } catch {
    /* number input 等可能不支持 */
  }
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.focus();
  return true;
}

/** 向 contenteditable 填写岛插入纯文本（写 attrs.value，可撤销） */
function insertTextIntoContentEditableIsland(
  editor: Editor,
  target: EmrContentEditableIslandInsertTarget,
  text: string,
): boolean {
  const { el } = target;
  if (!el.isConnected || editor.isDestroyed) return false;
  const pos = resolveFillableAtomPosFromIsland(editor.view, el);
  if (pos == null) return false;
  const node = editor.state.doc.nodeAt(pos);
  if (!node || !('value' in node.attrs)) return false;
  if (node.attrs.editable === false || node.attrs.editable === '0') return false;

  const current = String(node.attrs.value ?? readContentEditablePlainText(el));
  const start = Math.max(0, Math.min(target.start, current.length));
  const end = Math.max(start, Math.min(target.end, current.length));
  const next = `${current.slice(0, start)}${text}${current.slice(end)}`;
  if (next === current) return false;

  const caret = start + text.length;
  pinIslandCaret(el, caret);
  if (!commitFillableIslandValue(editor, pos, next)) {
    syncContentEditableFromValue(el, next, caret);
    return true;
  }
  requestAnimationFrame(() => {
    syncContentEditableFromValue(el, next, caret);
  });
  return true;
}

/** 向岛控件插入纯文本（input 或 contenteditable） */
export function insertTextIntoIslandTarget(
  target: EmrIslandInsertTarget,
  text: string,
  editor?: Editor | null,
): boolean {
  if (target.kind === 'contentEditable') {
    if (!editor || editor.isDestroyed) return false;
    return insertTextIntoContentEditableIsland(editor, target, text);
  }
  return insertTextIntoInputIsland(target, text);
}

/** TipTap：在指定选区（或当前光标）插入多行纯文本（换行 → hardBreak） */
export function insertPlainTextAtEditorCursor(
  editor: Editor,
  text: string,
  selection?: EmrEditorInsertSelection | null,
): boolean {
  if (!text) return false;
  const parts = text.split('\n');
  const content: Array<{ type: string; text?: string }> = [];
  for (let i = 0; i < parts.length; i += 1) {
    const line = parts[i] ?? '';
    if (line) {
      content.push({ type: 'text', text: line });
    }
    if (i < parts.length - 1) {
      content.push({ type: 'hardBreak' });
    }
  }
  if (content.length === 0) return false;
  let chain = editor.chain().focus();
  if (selection) {
    const size = editor.state.doc.content.size;
    const from = Math.max(0, Math.min(selection.from, size));
    const to = Math.max(from, Math.min(selection.to, size));
    chain = chain.setTextSelection({ from, to });
  }
  return chain.insertContent(content).run();
}

/**
 * 优先写入打开弹窗前聚焦的填写岛（自由文本 / 辅助检查等）；
 * 否则按冻结的编辑器选区（或当前光标）插入正文流。
 */
export function insertEmrImportText(options: {
  editor: Editor | null;
  editorSelection?: EmrEditorInsertSelection | null;
  islandTarget?: EmrIslandInsertTarget | null;
  text: string;
}): boolean {
  const text = options.text;
  if (!text) return false;
  if (options.islandTarget) {
    const ok = insertTextIntoIslandTarget(options.islandTarget, text, options.editor);
    if (ok) return true;
  }
  if (!options.editor || options.editor.isDestroyed) return false;
  return insertPlainTextAtEditorCursor(options.editor, text, options.editorSelection);
}
