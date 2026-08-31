import type { Editor } from '@tiptap/core';
import type { Node as PMNode } from '@tiptap/pm/model';
import type { EditorView } from '@tiptap/pm/view';

import { TextSelection } from '@tiptap/pm/state';

import { EMR_PM_CONTENT_FIELD_TYPES } from './emr-data-element-text';
import { collectAllFillableHits, type EmrFillableHit } from '../../fillable-field';
import { setContentEditableCaret } from './inline-contenteditable-caret';

export type MetadataChipCaret = 'end' | 'start';

export function isEditableInlineFieldNode(node: PMNode) {
  if (node.type.name === 'emrFreeText') return true;
  if (node.type.name === 'emrMetadataChip' && node.attrs.editable !== false) return true;
  if (node.type.name === 'emrDataElement' && node.attrs.editable !== false) return true;
  return false;
}

export function inlineEditableFieldDocPosFromDOMPos(
  doc: PMNode,
  domMappedPos: number,
): null | number {
  const size = doc.content.size;
  const p = Math.min(Math.max(0, domMappedPos), size);
  const hit = collectAllFillableHits(doc).find((h) => h.editable && p >= h.from && p <= h.to);
  if (hit) return hit.from;
  const $p = doc.resolve(p);
  for (let d = $p.depth; d > 0; d--) {
    const n = $p.node(d);
    if (isEditableInlineFieldNode(n)) {
      return $p.before(d);
    }
  }
  return null;
}

function paragraphBounds(doc: PMNode, pos: number): { end: number; start: number } | null {
  const inner = Math.min(Math.max(pos, 1), doc.content.size);
  const $pos = doc.resolve(inner);
  for (let d = $pos.depth; d > 0; d--) {
    const n = $pos.node(d);
    if (n.isTextblock) {
      return { start: $pos.start(d), end: $pos.end(d) };
    }
  }
  return null;
}

function focusFillableHit(view: EditorView, hit: EmrFillableHit, caret: MetadataChipCaret) {
  if (hit.kind === 'widget') {
    requestAnimationFrame(() => {
      const node = view.state.doc.nodeAt(hit.pos);
      if (!node || !EMR_PM_CONTENT_FIELD_TYPES.has(node.type.name)) {
        const pos = caret === 'end' ? hit.innerTo : hit.innerFrom;
        view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, pos)));
        view.focus();
        return;
      }
      const dom = view.nodeDOM(hit.pos);
      const root = dom instanceof HTMLElement ? dom : null;
      const control = root?.querySelector(
        'input.emr-metadata-chip__input, input.emr-free-text__input, .emr-inline-text__field, .emr-fillable-field__input, input.emr-de__control, textarea.emr-de__control, select.emr-de__control, input.emr-de__checkbox',
      ) as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement | null;
      if (control) {
        control.focus();
        if (control instanceof HTMLSelectElement) return;
        if (control instanceof HTMLInputElement && control.type === 'checkbox') return;
        if (control instanceof HTMLElement && control.isContentEditable) {
          setContentEditableCaret(control, caret);
          return;
        }
        const textControl = control as HTMLInputElement | HTMLTextAreaElement;
        const len = textControl.value.length;
        const at = caret === 'end' ? len : 0;
        textControl.setSelectionRange(at, at);
        return;
      }
      const pos = caret === 'end' ? hit.innerTo : hit.innerFrom;
      view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, pos)));
      view.focus();
    });
    return;
  }
  const pos = caret === 'end' ? hit.innerTo : hit.innerFrom;
  view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, pos)));
  view.focus();
}

function editableHits(doc: PMNode): EmrFillableHit[] {
  return collectAllFillableHits(doc).filter((h) => h.editable);
}

function hitAtFrom(doc: PMNode, from: number): EmrFillableHit | null {
  return collectAllFillableHits(doc).find((h) => h.from === from) ?? null;
}

export function focusEditableInlineFieldDOM(
  view: EditorView,
  nodeStartPos: number,
  caret: MetadataChipCaret,
): void {
  const hit = hitAtFrom(view.state.doc, nodeStartPos);
  if (!hit) return;
  focusFillableHit(view, hit, caret);
}

export function focusNextEditableMetadataChip(
  editor: Editor,
  currentNodeStartPos: number,
  options?: { caret?: MetadataChipCaret },
): boolean {
  const hits = editableHits(editor.state.doc);
  const next = hits.find((h) => h.from > currentNodeStartPos);
  if (!next) return false;
  focusFillableHit(editor.view, next, options?.caret ?? 'end');
  return true;
}

export function focusPreviousEditableMetadataChip(
  editor: Editor,
  currentNodeStartPos: number,
): boolean {
  const hits = editableHits(editor.state.doc);
  let prev: EmrFillableHit | null = null;
  for (const h of hits) {
    if (h.from >= currentNodeStartPos) break;
    prev = h;
  }
  if (!prev) return false;
  focusFillableHit(editor.view, prev, 'end');
  return true;
}

/**
 * 从任意光标位置 Tab / Shift+Tab：按文档顺序跳到下一/上一可填槽。
 * 当前若已在某槽内，则跳过该槽。
 */
export function focusAdjacentEditableFillable(
  editor: Editor,
  fromPos: number,
  direction: 'next' | 'prev',
  caret: MetadataChipCaret = 'start',
): boolean {
  const hits = editableHits(editor.state.doc);
  if (!hits.length) return false;
  const at =
    hits.find((h) => fromPos >= h.from && fromPos < h.to) ??
    hits.find((h) => h.to === fromPos) ??
    null;

  if (direction === 'next') {
    const startAfter = at ? at.from : fromPos;
    const next = hits.find((h) => h.from > startAfter);
    if (!next) return false;
    focusFillableHit(editor.view, next, caret);
    return true;
  }

  const endBefore = at ? at.from : fromPos;
  let prev: EmrFillableHit | null = null;
  for (const h of hits) {
    if (h.from >= endBefore) break;
    prev = h;
  }
  if (!prev) return false;
  focusFillableHit(editor.view, prev, 'end');
  return true;
}

export function focusNextEditableFieldNextParagraph(
  editor: Editor,
  currentNodeStartPos: number,
): boolean {
  const { doc } = editor.state;
  const bounds = paragraphBounds(doc, currentNodeStartPos);
  if (!bounds) return false;
  const next = editableHits(doc).find((h) => h.from >= bounds.end);
  if (!next) return false;
  focusFillableHit(editor.view, next, 'start');
  return true;
}

export function focusPreviousEditableFieldPrevParagraph(
  editor: Editor,
  currentNodeStartPos: number,
): boolean {
  const { doc } = editor.state;
  const bounds = paragraphBounds(doc, currentNodeStartPos);
  if (!bounds) return false;
  const hits = editableHits(doc).filter((h) => h.to <= bounds.start);
  const prev = hits[hits.length - 1];
  if (!prev) return false;
  focusFillableHit(editor.view, prev, 'end');
  return true;
}
