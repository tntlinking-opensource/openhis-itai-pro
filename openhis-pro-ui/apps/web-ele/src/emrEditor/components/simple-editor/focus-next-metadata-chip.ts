import type { Editor } from '@tiptap/core';
import type { Node as PMNode } from '@tiptap/pm/model';
import type { EditorView } from '@tiptap/pm/view';

import { setContentEditableCaret } from './inline-contenteditable-caret';

export type MetadataChipCaret = 'end' | 'start';

/** 可与元数据芯片一样用回车/左右键串联焦点的行内可编辑块 */
export function isEditableInlineFieldNode(node: PMNode) {
  if (node.type.name === 'emrFreeText') return true;
  if (node.type.name === 'emrMetadataChip' && node.attrs.editable !== false) return true;
  if (node.type.name === 'emrDataElement' && node.attrs.editable !== false) return true;
  return false;
}

/** 由 NodeView 内 contenteditable 的 posAtDOM 反推行内可编辑节点在文档中的起始位置 */
export function inlineEditableFieldDocPosFromDOMPos(
  doc: PMNode,
  domMappedPos: number,
): null | number {
  const size = doc.content.size;
  const p = Math.min(Math.max(0, domMappedPos), size);
  const $p = doc.resolve(p);
  for (let d = $p.depth; d > 0; d--) {
    const n = $p.node(d);
    if (isEditableInlineFieldNode(n)) {
      return $p.before(d);
    }
  }
  const nb = $p.nodeBefore;
  if (nb && isEditableInlineFieldNode(nb)) {
    return p - nb.nodeSize;
  }
  const na = $p.nodeAfter;
  if (na && isEditableInlineFieldNode(na)) {
    return p;
  }
  return null;
}

/** 当前行内块所在文本块（段落/标题等）在文档内的 [start, end) 区间 */
function paragraphBounds(doc: PMNode, nodeStartPos: number): { end: number; start: number } | null {
  const inner = Math.min(Math.max(nodeStartPos + 1, 1), doc.content.size);
  const $pos = doc.resolve(inner);
  for (let d = $pos.depth; d > 0; d--) {
    const n = $pos.node(d);
    if (n.isTextblock) {
      return { start: $pos.start(d), end: $pos.end(d) };
    }
  }
  return null;
}

/** 由文档中节点起始位置聚焦其 NodeView 内控件（供插件与命令共用） */
export function focusEditableInlineFieldDOM(
  view: EditorView,
  nodeStartPos: number,
  caret: MetadataChipCaret,
): void {
  requestAnimationFrame(() => {
    const dom = view.nodeDOM(nodeStartPos);
    const root = dom instanceof HTMLElement ? dom : null;
    const ce = root?.querySelector(
      '[contenteditable="true"].emr-inline-text__field',
    ) as HTMLElement | null;
    if (ce) {
      setContentEditableCaret(ce, caret);
      return;
    }
    const control = root?.querySelector(
      'input.emr-metadata-chip__input, input.emr-free-text__input, input.emr-de__control, textarea.emr-de__control, select.emr-de__control, input.emr-de__checkbox',
    ) as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement | null;
    if (!control) return;
    control.focus();
    if (control instanceof HTMLSelectElement) return;
    if (control instanceof HTMLInputElement && control.type === 'checkbox') return;
    const textControl = control as HTMLInputElement | HTMLTextAreaElement;
    const len = textControl.value.length;
    const at = caret === 'end' ? len : 0;
    textControl.setSelectionRange(at, at);
  });
}

function focusEditableChipInputAt(
  editor: Editor,
  nodeStartPos: number,
  caret: MetadataChipCaret,
): void {
  focusEditableInlineFieldDOM(editor.view, nodeStartPos, caret);
}

/**
 * 将焦点移到当前行内可编辑块之后的下一个（元数据芯片可编辑态 / 自由文本）。
 * @param caret 进入下一项后光标位置：`end` 便于接着输入（如回车）；`start` 便于从左侧移入（如行尾按右方向键）
 */
export function focusNextEditableMetadataChip(
  editor: Editor,
  currentNodeStartPos: number,
  options?: { caret?: MetadataChipCaret },
): boolean {
  const { doc } = editor.state;
  let nextPos: null | number = null;

  doc.descendants((node, pos) => {
    if (nextPos !== null) return false;
    if (!isEditableInlineFieldNode(node)) return true;
    if (pos <= currentNodeStartPos) return true;
    nextPos = pos;
    return false;
  });

  if (nextPos === null) return false;
  focusEditableChipInputAt(editor, nextPos, options?.caret ?? 'end');
  return true;
}

/**
 * 将焦点移到上一个行内可编辑块（元数据芯片可编辑态 / 自由文本），光标落在该项末尾。
 */
export function focusPreviousEditableMetadataChip(
  editor: Editor,
  currentNodeStartPos: number,
): boolean {
  const { doc } = editor.state;
  let prevPos: null | number = null;

  doc.descendants((node, pos) => {
    if (!isEditableInlineFieldNode(node)) return true;
    if (pos >= currentNodeStartPos) return true;
    prevPos = pos;
    return true;
  });

  if (prevPos === null) return false;
  focusEditableChipInputAt(editor, prevPos, 'end');
  return true;
}

/**
 * 跳到「下一段」里第一个可编辑行内块（↓，不按同段内书写顺序）。
 */
export function focusNextEditableFieldNextParagraph(
  editor: Editor,
  currentNodeStartPos: number,
): boolean {
  const { doc } = editor.state;
  const bounds = paragraphBounds(doc, currentNodeStartPos);
  if (!bounds) return false;
  let nextPos: null | number = null;
  doc.nodesBetween(bounds.end, doc.content.size, (node, pos) => {
    if (nextPos !== null) return false;
    if (!isEditableInlineFieldNode(node)) return true;
    nextPos = pos;
    return false;
  });
  if (nextPos === null) return false;
  focusEditableChipInputAt(editor, nextPos, 'start');
  return true;
}

/**
 * 跳到「上一段」里最后一个可编辑行内块（↑）。
 */
export function focusPreviousEditableFieldPrevParagraph(
  editor: Editor,
  currentNodeStartPos: number,
): boolean {
  const { doc } = editor.state;
  const bounds = paragraphBounds(doc, currentNodeStartPos);
  if (!bounds) return false;
  let prevPos: null | number = null;
  doc.nodesBetween(0, bounds.start, (node, pos) => {
    if (!isEditableInlineFieldNode(node)) return true;
    prevPos = pos;
    return true;
  });
  if (prevPos === null) return false;
  focusEditableChipInputAt(editor, prevPos, 'end');
  return true;
}
