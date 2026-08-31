import { Extension } from '@tiptap/core';
import type { EditorView } from '@tiptap/pm/view';
import { NodeSelection, Plugin, PluginKey, TextSelection } from '@tiptap/pm/state';
import { canJoin } from '@tiptap/pm/transform';

import {
  collectAllFillableHits,
  isCaretAtFillableContentStart,
  isEditableFillableNode,
  isFillableHitEmpty,
  isSelectingEntireFillableContent,
  resolveFillableAt,
  selectionTouchesNonFillableText,
} from '../../../fillable-field/core/resolve';
import { applyFillableCaretPolicy } from '../../../fillable-field/runtime/caret-policy';
import { EMR_FIXED_TEXT_ALLOW_MUTATE_META } from './emr-fixed-text';
import { focusEditableInlineFieldDOM } from '../focus-next-metadata-chip';
import { isFillableIslandElement } from '../node-views/fillable-field-view.shared';

const key = new PluginKey('emrWordlikeOps');

/** 选区落在填写岛 contenteditable 内：由岛屿自己处理，禁止映成 PM 选区 */
function domSelectionInsideFillableIsland(range: Range): boolean {
  const node = range.commonAncestorContainer;
  const el = node instanceof Element ? node : node.parentElement;
  if (!el) return false;
  return Boolean(
    el.closest(
      '.emr-fillable-field__input, .emr-inline-text__field, .emr-de__text-field, .emr-free-text__text',
    ),
  );
}

/** 文档位是否贴着 atom（填写岛等）：岛内拖选 posAtDOM 常塌成 atom 起/止同位 */
function pmPosTouchesAtom(doc: import('@tiptap/pm/model').Node, pos: number): boolean {
  const clamped = Math.max(0, Math.min(pos, doc.content.size));
  if (doc.nodeAt(clamped)?.isAtom) return true;
  if (clamped > 0) {
    const before = doc.resolve(clamped).nodeBefore;
    if (before?.isAtom) return true;
  }
  return false;
}

/** DOM 拖选 → PM 文档坐标（仅正文；岛内选区返回 null） */
function mapDomSelectionToPm(view: EditorView): { from: number; to: number } | null {
  const domSel = window.getSelection();
  if (!domSel?.rangeCount || domSel.isCollapsed) return null;
  const range = domSel.getRangeAt(0);
  if (!view.dom.contains(range.commonAncestorContainer)) return null;
  if (domSelectionInsideFillableIsland(range)) return null;
  const { doc } = view.state;

  try {
    let from = view.posAtDOM(range.startContainer, range.startOffset);
    let to = view.posAtDOM(range.endContainer, range.endOffset);
    if (from === to) {
      if (pmPosTouchesAtom(doc, from)) return null;
      from = view.posAtDOM(range.startContainer, range.startOffset, -1);
      to = view.posAtDOM(range.endContainer, range.endOffset, 1);
      if (from === to) return null;
    }
    if (from > to) {
      const tmp = from;
      from = to;
      to = tmp;
    }
    from = Math.max(0, Math.min(from, doc.content.size));
    to = Math.max(0, Math.min(to, doc.content.size));
    if (to > from) return { from, to };
  } catch {
    return null;
  }
  return null;
}

/**
 * 批量删用的起止位：DOM 拖选与 PM 选区取更长的一段。
 * 跨 soft-break 时常见「浏览器有蓝选区、PM 空选/残选」。
 */
function resolveBatchDeleteRange(view: EditorView): { from: number; to: number } | null {
  const { selection } = view.state;
  const domRange = mapDomSelectionToPm(view);
  let pmRange: { from: number; to: number } | null = null;
  if (!selection.empty && !(selection instanceof NodeSelection)) {
    const from = Math.min(selection.from, selection.to);
    const to = Math.max(selection.from, selection.to);
    if (to > from) pmRange = { from, to };
  }

  if (domRange && pmRange) {
    return domRange.to - domRange.from >= pmRange.to - pmRange.from ? domRange : pmRange;
  }
  return domRange ?? pmRange;
}

/**
 * 批量/单字删除。禁止 scrollIntoView：分页后会把视口/光标拽到错误页或页尾。
 */
function dispatchBatchDelete(view: EditorView, from: number, to: number): boolean {
  const { state } = view;
  const { doc } = state;
  if (to <= from) return false;

  if (!selectionTouchesNonFillableText(doc, from, to)) {
    view.dispatch(state.tr.delete(from, to));
    return true;
  }

  // 含结构字 / 固定文本：直接删（文书锁已下线）
  view.dispatch(state.tr.delete(from, to).setMeta(EMR_FIXED_TEXT_ALLOW_MUTATE_META, true));
  return true;
}

/** 空选时退格/Delete：等价默认键位，但不带 scrollIntoView */
function dispatchCaretDelete(view: EditorView, key: 'Backspace' | 'Delete'): boolean {
  const { state } = view;
  const { doc, selection } = state;
  if (!selection.empty) return false;
  const head = selection.head;
  const $head = doc.resolve(head);

  if (key === 'Backspace') {
    const atStart = isCaretAtFillableContentStart(doc, head);
    if (atStart) {
      if (atStart.from <= 1) return true;
      view.dispatch(state.tr.delete(atStart.from - 1, atStart.from));
      return true;
    }
    const before = $head.nodeBefore;
    if (before?.type.name === 'hardBreak') {
      view.dispatch(state.tr.delete(head - before.nodeSize, head));
      return true;
    }
    if ($head.parentOffset === 0 && $head.depth > 0) {
      const joinPos = $head.before($head.depth);
      if (canJoin(doc, joinPos)) {
        view.dispatch(state.tr.join(joinPos));
        return true;
      }
    }
    if (!before) return true;
    // 文本：删一字符；atom/整节点：整颗删
    if (before.isText) {
      view.dispatch(state.tr.delete(head - 1, head));
      return true;
    }
    view.dispatch(state.tr.delete(head - before.nodeSize, head));
    return true;
  }

  const inside = resolveFillableAt(doc, head);
  if (inside?.editable && head === inside.innerTo && !isFillableHitEmpty(doc, inside)) {
    if (head >= doc.content.size) return true;
    const afterInside = doc.resolve(head).nodeAfter;
    if (!afterInside) return true;
    if (afterInside.isText) {
      view.dispatch(state.tr.delete(head, head + 1));
      return true;
    }
    view.dispatch(state.tr.delete(head, head + afterInside.nodeSize));
    return true;
  }

  const after = $head.nodeAfter;
  if (after?.type.name === 'hardBreak') {
    view.dispatch(state.tr.delete(head, head + after.nodeSize));
    return true;
  }
  if ($head.parentOffset === $head.parent.content.size && $head.depth > 0) {
    const joinPos = $head.after($head.depth);
    if (canJoin(doc, joinPos)) {
      view.dispatch(state.tr.join(joinPos));
      return true;
    }
  }
  if (!after) return true;
  if (after.isText) {
    view.dispatch(state.tr.delete(head, head + 1));
    return true;
  }
  view.dispatch(state.tr.delete(head, head + after.nodeSize));
  return true;
}

/**
 * 方案 B：可填域为 atom + NodeView；PM 仅管段落正文与节点选区。
 * 文书锁已下线：不再把点击结构字吸附到可填域；删除禁止 scrollIntoView。
 */
export const EmrWordlikeOps = Extension.create({
  name: 'emrWordlikeOps',

  addProseMirrorPlugins() {
    return [
      new Plugin({
        key,
        props: {
          /**
           * 正文输入不带 scrollIntoView：分页 soft spacer 重挂后默认滚动会把视口/光标拽到页尾。
           * 岛屿 contenteditable 自行处理 input，此处不拦截。
           */
          handleTextInput(view, from, to, text) {
            if (!view.editable) return false;
            if (isFillableIslandElement(document.activeElement)) return false;
            view.dispatch(view.state.tr.insertText(text, from, to));
            return true;
          },

          handleClick(view, pos, event) {
            if (!view.editable) return false;
            if (event.detail > 1 || event.shiftKey) return false;

            if (event.target instanceof Element && isFillableIslandElement(event.target)) {
              const hit =
                resolveFillableAt(view.state.doc, pos) ??
                resolveFillableAt(view.state.doc, Math.max(0, pos - 1));
              if (hit?.editable && hit.kind === 'widget') {
                focusEditableInlineFieldDOM(
                  view,
                  hit.from,
                  isFillableHitEmpty(view.state.doc, hit) ? 'start' : 'end',
                );
                return true;
              }
              if (hit?.editable) {
                const tr = view.state.tr;
                applyFillableCaretPolicy(tr, 'click-empty-bracket', hit.from);
                view.dispatch(tr);
                return true;
              }
              return false;
            }

            const fieldEl =
              event.target instanceof Element
                ? event.target.closest(
                    '.emr-bracket-field--node, .emr-bracket-field--css, .emr-fillable-field__shell',
                  )
                : null;
            if (fieldEl instanceof HTMLElement) {
              const hit =
                resolveFillableAt(view.state.doc, pos) ??
                resolveFillableAt(view.state.doc, Math.max(0, pos - 1));
              if (hit?.kind === 'widget') {
                if (hit.editable) {
                  focusEditableInlineFieldDOM(
                    view,
                    hit.from,
                    isFillableHitEmpty(view.state.doc, hit) ? 'start' : 'end',
                  );
                }
                return true;
              }
              if (hit?.editable && isFillableHitEmpty(view.state.doc, hit)) {
                const tr = view.state.tr;
                applyFillableCaretPolicy(tr, 'click-empty-bracket', hit.from);
                view.dispatch(tr);
                return true;
              }
              if (
                fieldEl.classList.contains('emr-fillable-field__shell') &&
                !isFillableIslandElement(event.target instanceof Element ? event.target : null) &&
                hit?.editable
              ) {
                return true;
              }
              if (hit && !isFillableHitEmpty(view.state.doc, hit) && event.target !== fieldEl) {
                return false;
              }
            }

            // 结构字/正文：交给 ProseMirror 定点，禁止再吸附到空可填域
            return false;
          },

          handleKeyDown(view, event) {
            if (!view.editable) return false;
            if (isFillableIslandElement(document.activeElement)) {
              return false;
            }
            if (event.isComposing || event.key === 'Process') {
              const hit = resolveFillableAt(view.state.doc, view.state.selection.head);
              if (hit && !hit.editable) return true;
              return false;
            }

            const { state } = view;
            const { selection, doc } = state;

            if (
              (event.key === 'Backspace' || event.key === 'Delete') &&
              !event.shiftKey &&
              !event.altKey &&
              !event.metaKey &&
              !event.ctrlKey &&
              !(selection instanceof NodeSelection)
            ) {
              const range = resolveBatchDeleteRange(view);
              if (range) {
                if (dispatchBatchDelete(view, range.from, range.to)) {
                  event.preventDefault();
                  return true;
                }
              }
              if (selection.empty) {
                // 可填 mark 域内删字交给 fillable-keymap / 默认；此处只拦「结构字」避免默认 scrollIntoView
                const hit = resolveFillableAt(doc, selection.head);
                if (!hit?.editable) {
                  event.preventDefault();
                  dispatchCaretDelete(view, event.key);
                  return true;
                }
              }
            }

            if (
              selection.empty &&
              !event.shiftKey &&
              !event.altKey &&
              !event.metaKey &&
              !event.ctrlKey &&
              (event.key === 'ArrowLeft' || event.key === 'ArrowRight')
            ) {
              if (handleFillableArrowBoundary(view, event.key)) {
                event.preventDefault();
                return true;
              }
            }

            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a') {
              // NodeSelection 落在 atom 可填域：选中岛屿内容，禁止整篇 selectAll
              if (selection instanceof NodeSelection && isAnyFillableSel(selection)) {
                event.preventDefault();
                const hit =
                  resolveFillableAt(doc, selection.from) ??
                  collectAllFillableHits(doc).find((h) => h.from === selection.from);
                if (hit?.kind === 'widget') {
                  focusEditableInlineFieldDOM(view, hit.from, 'start');
                  requestAnimationFrame(() => {
                    const dom = view.nodeDOM(hit.pos);
                    const root = dom instanceof HTMLElement ? dom : null;
                    const island = root?.querySelector(
                      '.emr-fillable-field__input, .emr-inline-text__field, .emr-de__text-field',
                    );
                    if (island instanceof HTMLElement && island.isContentEditable) {
                      const range = document.createRange();
                      range.selectNodeContents(island);
                      const sel = window.getSelection();
                      sel?.removeAllRanges();
                      sel?.addRange(range);
                    }
                  });
                  return true;
                }
                if (hit?.editable) {
                  view.dispatch(
                    state.tr.setSelection(TextSelection.create(doc, hit.innerFrom, hit.innerTo)),
                  );
                  return true;
                }
                return true;
              }
              const anchorHit =
                resolveFillableAt(doc, selection.$from.pos) ??
                resolveFillableAt(doc, selection.from);
              if (anchorHit?.editable) {
                if (!isSelectingEntireFillableContent(anchorHit, selection.from, selection.to)) {
                  event.preventDefault();
                  view.dispatch(
                    state.tr.setSelection(
                      TextSelection.create(doc, anchorHit.innerFrom, anchorHit.innerTo),
                    ),
                  );
                  return true;
                }
              }
              return false;
            }

            if (
              (event.key === 'Home' || event.key === 'End') &&
              !event.ctrlKey &&
              !event.metaKey &&
              !event.altKey
            ) {
              const hit = resolveFillableAt(doc, selection.head);
              if (hit?.editable && selection.empty) {
                event.preventDefault();
                const pos = event.key === 'Home' ? hit.innerFrom : hit.innerTo;
                view.dispatch(state.tr.setSelection(TextSelection.create(doc, pos)));
                return true;
              }
            }

            return false;
          },
          handleDOMEvents: {
            // 拖选跨 soft-break 时浏览器有蓝选区、PM 仍空选 → 松手后映回 TextSelection
            mouseup(view) {
              if (!view.editable || !view.state.selection.empty) return false;
              const domSel = window.getSelection();
              if (domSel?.rangeCount && domSelectionInsideFillableIsland(domSel.getRangeAt(0))) {
                return false;
              }
              const range = resolveBatchDeleteRange(view);
              if (!range) return false;
              try {
                view.dispatch(
                  view.state.tr.setSelection(
                    TextSelection.create(view.state.doc, range.from, range.to),
                  ),
                );
              } catch {
                /* 跨 atom 等非法选区忽略 */
              }
              return false;
            },
          },
        },
      }),
    ];
  },
});

function isAnyFillableSel(selection: NodeSelection): boolean {
  return Boolean(
    selection.node &&
      (selection.node.type.name === 'emrDataElement' ||
        selection.node.type.name === 'emrFreeText' ||
        selection.node.type.name === 'emrMetadataChip'),
  );
}

function handleFillableArrowBoundary(
  view: import('@tiptap/pm/view').EditorView,
  key: 'ArrowLeft' | 'ArrowRight',
): boolean {
  const { state } = view;
  const { doc, selection } = state;
  const head = selection.head;
  const inside = resolveFillableAt(doc, head);

  if (key === 'ArrowLeft') {
    if (inside?.editable && head === inside.innerFrom) {
      view.dispatch(state.tr.setSelection(TextSelection.create(doc, inside.from)));
      return true;
    }
    return false;
  }

  if (inside?.editable) {
    if (isFillableHitEmpty(doc, inside) && head === inside.innerFrom) {
      view.dispatch(state.tr.setSelection(TextSelection.create(doc, inside.to)));
      return true;
    }
    if (head >= inside.innerTo) {
      view.dispatch(state.tr.setSelection(TextSelection.create(doc, inside.to)));
      return true;
    }
    return false;
  }

  const $pos = doc.resolve(head);
  const after = $pos.nodeAfter;
  if (after && isEditableFillableNode(after) && head === $pos.pos && key === 'ArrowRight') {
    view.dispatch(state.tr.setSelection(TextSelection.create(doc, head + 1)));
    return true;
  }
  return false;
}
