import { Extension } from '@tiptap/core';
import { Fragment } from '@tiptap/pm/model';
import { NodeSelection, Plugin, PluginKey, TextSelection } from '@tiptap/pm/state';
import { canJoin, canSplit } from '@tiptap/pm/transform';

import type { EditorState } from '@tiptap/pm/state';
import type { EditorView } from '@tiptap/pm/view';

import type { EmrFillableHit } from '../../../fillable-field/core/resolve';
import {
  collectAllFillableHits,
  isFillableHitEmpty,
  resolveFillableAt,
} from '../../../fillable-field/core/resolve';
import { EMR_SKIP_FILLABLE_RESTORE_META } from '../../../fillable-field/kit/meta';
import { EMR_PM_CONTENT_FIELD_TYPES } from '../emr-data-element-text';
import { FILLABLE_EMPTY_CARET } from '../emr-fillable-json';
import { isFillableIslandElement } from '../node-views/fillable-field-view.shared';
import { consumeTimedDoubleEnter, isPlainEnterKey, resetDoubleEnterNav } from '../double-enter-nav';
import {
  focusAdjacentEditableFillable,
  focusEditableInlineFieldDOM,
} from '../focus-next-metadata-chip';
import { insertNewlineInContentEditableIsland } from '../use-inline-field-keydown';
import { EMR_CONTENT_LOCK_BYPASS_META } from './emr-content-lock';

const pluginKey = new PluginKey('emrFillableKeymap');

/**
 * 槽尾续写位回车：在 mark 域末插入 hardBreak + 零宽占位，光标留在域内。
 * 若当正文分段，文书锁下会出域且无法再输入。
 */
function insertNewlineAtFillableExclusiveEnd(
  view: EditorView,
  state: EditorState,
  hit: EmrFillableHit,
): boolean {
  const mark = hit.mark;
  const hardBreak = state.schema.nodes.hardBreak;
  if (!mark || !hardBreak || hit.kind !== 'mark') return false;
  const oldSize = hit.to - hit.from;
  const frag = state.doc
    .slice(hit.from, hit.to)
    .content.append(
      Fragment.from([hardBreak.create(), state.schema.text(FILLABLE_EMPTY_CARET, [mark])]),
    );
  const caret = hit.from + oldSize + 1;
  const tr = state.tr.replaceWith(hit.from, hit.to, frag);
  tr.setSelection(TextSelection.create(tr.doc, caret));
  tr.setStoredMarks([mark]);
  view.dispatch(tr);
  return true;
}

/** 域内任意位置回车：hardBreak + 零宽占位，避免光标落在 mark 外被文书锁挡住 */
function insertNewlineInsideFillableMark(
  view: EditorView,
  state: EditorState,
  mark: NonNullable<EmrFillableHit['mark']>,
): boolean {
  const hardBreak = state.schema.nodes.hardBreak;
  if (!hardBreak) return false;
  const tr = state.tr.replaceSelectionWith(
    Fragment.from([hardBreak.create(), state.schema.text(FILLABLE_EMPTY_CARET, [mark])]),
  );
  tr.setStoredMarks([mark]);
  view.dispatch(tr);
  return true;
}

/**
 * Word 化可填域快捷键（mark 槽 + 遗留/控件节点）：
 * - Enter：可编辑域内硬换行；短时双击 Enter → 下一可填域（仅域内，正文不参与）
 * - 正文 Enter / 删换行：文书锁下须 bypass（默认 split/join/hardBreak 会被 contentLock 静默丢掉）
 * - Tab / Shift+Tab：下一/上一可填域（域外也可跳）
 * - Escape：选中整槽（再 Delete 删除槽位）
 * - 空域 Backspace/Delete：删除整槽
 */
export const EmrFillableKeymap = Extension.create({
  name: 'emrFillableKeymap',
  /** 高于 StarterKit(100)：正文 Enter/删换行须先走本扩展，否则默认键位 dispatch 会被 contentLock 静默丢掉 */
  priority: 1000,

  addProseMirrorPlugins() {
    const editor = this.editor;

    return [
      new Plugin({
        key: pluginKey,
        props: {
          handleKeyDown(view, event) {
            if (event.isComposing) {
              return false;
            }
            if (isFillableIslandElement(document.activeElement)) {
              return false;
            }
            const { state } = view;
            const { selection } = state;

            if (selection instanceof NodeSelection) {
              const n = selection.node;
              if (EMR_PM_CONTENT_FIELD_TYPES.has(n.type.name)) {
                if (event.key === 'Backspace' || event.key === 'Delete') {
                  event.preventDefault();
                  // 禁止 scrollIntoView：分页后会把视口/光标拽到错误页或页尾
                  view.dispatch(
                    state.tr
                      .delete(selection.from, selection.to)
                      .setMeta(EMR_SKIP_FILLABLE_RESTORE_META, true),
                  );
                  return true;
                }
                if (event.key === 'Enter' && !event.ctrlKey && !event.metaKey && !event.altKey) {
                  event.preventDefault();
                  if (consumeTimedDoubleEnter(event)) {
                    focusAdjacentEditableFillable(editor, selection.from, 'next', 'start');
                    resetDoubleEnterNav();
                    return true;
                  }
                  focusEditableInlineFieldDOM(view, selection.from, 'end');
                  requestAnimationFrame(() => {
                    const dom = view.nodeDOM(selection.from);
                    const root = dom instanceof HTMLElement ? dom : null;
                    const island = root?.querySelector(
                      '.emr-fillable-field__input, .emr-inline-text__field, .emr-de__text-field',
                    );
                    if (island instanceof HTMLElement && island.isContentEditable) {
                      insertNewlineInContentEditableIsland(editor, selection.from, island);
                    }
                  });
                  return true;
                }
              }
            }

            /** Tab：域内/域外都能跳到下一/上一可填槽（含下拉/勾选） */
            if (event.key === 'Tab') {
              event.preventDefault();
              focusAdjacentEditableFillable(
                editor,
                selection.head,
                event.shiftKey ? 'prev' : 'next',
                'start',
              );
              resetDoubleEnterNav();
              return true;
            }

            /** 严格域内（半开区间）；槽尾外侧 = 续写位（仍属可填域，勿当正文） */
            const hitStrict = resolveFillableAt(state.doc, selection.head);
            const hitExclusiveEnd =
              !hitStrict && selection.empty
                ? (() => {
                    const end = resolveFillableAt(state.doc, Math.max(0, selection.head - 1));
                    return end && end.to === selection.head ? end : null;
                  })()
                : null;
            const hit = hitStrict ?? hitExclusiveEnd;

            /**
             * 槽尾续写位回车：域内换行（或双击跳下一项）。
             * 合并后曾把此处当成正文分段，文书锁下光标出域后无法再输入。
             */
            if (
              event.key === 'Enter' &&
              !event.ctrlKey &&
              !event.metaKey &&
              !event.altKey &&
              !hitStrict &&
              hitExclusiveEnd?.editable &&
              hitExclusiveEnd.kind === 'mark' &&
              hitExclusiveEnd.mark
            ) {
              if (consumeTimedDoubleEnter(event)) {
                event.preventDefault();
                focusAdjacentEditableFillable(editor, selection.head, 'next', 'start');
                resetDoubleEnterNav();
                return true;
              }
              event.preventDefault();
              return insertNewlineAtFillableExclusiveEnd(view, state, hitExclusiveEnd);
            }

            /**
             * 正文回车（非可填域）：不参与双击跳槽。
             * 文书锁会拦默认 splitBlock / 域外 hardBreak，须 bypass 才能换行。
             */
            if (
              event.key === 'Enter' &&
              !event.ctrlKey &&
              !event.metaKey &&
              !event.altKey &&
              !hitStrict
            ) {
              if (isPlainEnterKey(event) || event.shiftKey) {
                resetDoubleEnterNav();
              }
              if (!editor.storage.emrContentLock?.enabled) {
                return false;
              }
              event.preventDefault();
              // Shift+Enter → 硬换行；Enter → 分段（不能分段再硬换行）
              if (event.shiftKey) {
                const hardBreak = state.schema.nodes.hardBreak;
                if (!hardBreak) return false;
                view.dispatch(
                  state.tr
                    .replaceSelectionWith(hardBreak.create())
                    .setMeta(EMR_CONTENT_LOCK_BYPASS_META, true),
                );
                return true;
              }
              if (selection.empty && canSplit(state.doc, selection.head)) {
                view.dispatch(
                  state.tr.split(selection.head).setMeta(EMR_CONTENT_LOCK_BYPASS_META, true),
                );
                return true;
              }
              const hardBreak = state.schema.nodes.hardBreak;
              if (!hardBreak) return true;
              view.dispatch(
                state.tr
                  .replaceSelectionWith(hardBreak.create())
                  .setMeta(EMR_CONTENT_LOCK_BYPASS_META, true),
              );
              return true;
            }

            /**
             * 正文删换行：与上面 bypass 回车对称。
             * - hardBreak：退格/Delete 删节点
             * - 分段后段首退格 / 段尾 Delete：join 合并段落（默认路径会被 contentLock 拦）
             * 禁止 scrollIntoView：分页后会把视口拽到错误页/页尾
             */
            if (
              (event.key === 'Backspace' || event.key === 'Delete') &&
              !event.shiftKey &&
              !event.altKey &&
              !event.metaKey &&
              !event.ctrlKey &&
              !hitStrict &&
              selection.empty &&
              editor.storage.emrContentLock?.enabled
            ) {
              const head = selection.head;
              const $head = state.doc.resolve(head);
              if (event.key === 'Backspace') {
                const before = $head.nodeBefore;
                if (before?.type.name === 'hardBreak') {
                  event.preventDefault();
                  view.dispatch(
                    state.tr
                      .delete(head - before.nodeSize, head)
                      .setMeta(EMR_CONTENT_LOCK_BYPASS_META, true),
                  );
                  return true;
                }
                if ($head.parentOffset === 0 && $head.depth > 0) {
                  const joinPos = $head.before($head.depth);
                  if (canJoin(state.doc, joinPos)) {
                    event.preventDefault();
                    view.dispatch(
                      state.tr.join(joinPos).setMeta(EMR_CONTENT_LOCK_BYPASS_META, true),
                    );
                    return true;
                  }
                }
              } else {
                const after = $head.nodeAfter;
                if (after?.type.name === 'hardBreak') {
                  event.preventDefault();
                  view.dispatch(
                    state.tr
                      .delete(head, head + after.nodeSize)
                      .setMeta(EMR_CONTENT_LOCK_BYPASS_META, true),
                  );
                  return true;
                }
                if ($head.parentOffset === $head.parent.content.size && $head.depth > 0) {
                  const joinPos = $head.after($head.depth);
                  if (canJoin(state.doc, joinPos)) {
                    event.preventDefault();
                    view.dispatch(
                      state.tr.join(joinPos).setMeta(EMR_CONTENT_LOCK_BYPASS_META, true),
                    );
                    return true;
                  }
                }
              }
            }

            if (!hit) {
              return false;
            }

            /** 仅严格域内：短时双击回车 → 下一可填槽 */
            if (hitStrict?.editable && consumeTimedDoubleEnter(event)) {
              event.preventDefault();
              let jumpFrom = selection.head;
              if (selection.empty && jumpFrom > 0) {
                const ch = state.doc.textBetween(jumpFrom - 1, jumpFrom);
                if (ch === '\n') {
                  view.dispatch(state.tr.delete(jumpFrom - 1, jumpFrom));
                  jumpFrom -= 1;
                } else {
                  const $pos = state.doc.resolve(jumpFrom);
                  const before = $pos.nodeBefore;
                  if (before?.type.name === 'hardBreak') {
                    view.dispatch(state.tr.delete(jumpFrom - before.nodeSize, jumpFrom));
                    jumpFrom -= before.nodeSize;
                  }
                }
              }
              focusAdjacentEditableFillable(editor, jumpFrom, 'next', 'start');
              resetDoubleEnterNav();
              return true;
            }

            if (event.key === 'Escape') {
              event.preventDefault();
              if (hit.kind === 'widget' && hit.node) {
                view.dispatch(state.tr.setSelection(NodeSelection.create(state.doc, hit.pos)));
                return true;
              }
              view.dispatch(
                state.tr.setSelection(TextSelection.create(state.doc, hit.innerFrom, hit.innerTo)),
              );
              return true;
            }

            if (event.key === 'Backspace' || event.key === 'Delete') {
              const coversAll =
                !selection.empty && selection.from <= hit.innerFrom && selection.to >= hit.innerTo;
              const empty = isFillableHitEmpty(state.doc, hit);
              if (empty || coversAll) {
                event.preventDefault();
                // 禁止 scrollIntoView：分页后会把视口/光标拽到错误页或页尾
                view.dispatch(
                  state.tr.delete(hit.from, hit.to).setMeta(EMR_SKIP_FILLABLE_RESTORE_META, true),
                );
                return true;
              }
            }

            if (!hitStrict) {
              return false;
            }

            if (!hitStrict.editable) {
              if (event.key === 'Enter') {
                event.preventDefault();
                return true;
              }
              return false;
            }

            if (event.key === 'Enter' && !event.ctrlKey && !event.metaKey && !event.altKey) {
              // atom 可填域：回车须进岛屿换行，禁止默认分段把焦点甩到 ProseMirror 根
              if (hitStrict.kind === 'widget') {
                event.preventDefault();
                if (consumeTimedDoubleEnter(event)) {
                  focusAdjacentEditableFillable(editor, selection.head, 'next', 'start');
                  resetDoubleEnterNav();
                  return true;
                }
                focusEditableInlineFieldDOM(view, hitStrict.from, 'end');
                requestAnimationFrame(() => {
                  const dom = view.nodeDOM(hitStrict.pos);
                  const root = dom instanceof HTMLElement ? dom : null;
                  const island = root?.querySelector(
                    '.emr-fillable-field__input, .emr-inline-text__field, .emr-de__text-field',
                  );
                  if (island instanceof HTMLElement && island.isContentEditable) {
                    insertNewlineInContentEditableIsland(editor, hitStrict.pos, island);
                  }
                });
                return true;
              }
              const mark = hitStrict.mark;
              if (!mark || hitStrict.kind !== 'mark') {
                return false;
              }
              event.preventDefault();
              return insertNewlineInsideFillableMark(view, state, mark);
            }

            return false;
          },
        },
      }),
    ];
  },
});

export function selectInsideFillableAt(
  view: import('@tiptap/pm/view').EditorView,
  from: number,
  caret: 'end' | 'start',
) {
  const hit =
    resolveFillableAt(view.state.doc, from) ??
    collectAllFillableHits(view.state.doc).find((h) => h.from === from);
  if (!hit) return;
  const pos = caret === 'end' ? hit.innerTo : hit.innerFrom;
  view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, pos)));
  view.focus();
}

export function selectFillableNodeAt(view: import('@tiptap/pm/view').EditorView, from: number) {
  const hit =
    resolveFillableAt(view.state.doc, from) ??
    collectAllFillableHits(view.state.doc).find((h) => h.from === from);
  if (!hit) return;
  if (hit.kind === 'widget') {
    view.dispatch(view.state.tr.setSelection(NodeSelection.create(view.state.doc, hit.pos)));
  } else {
    view.dispatch(
      view.state.tr.setSelection(TextSelection.create(view.state.doc, hit.innerFrom, hit.innerTo)),
    );
  }
  view.focus();
}
