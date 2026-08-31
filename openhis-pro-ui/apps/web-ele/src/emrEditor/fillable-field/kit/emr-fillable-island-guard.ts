import { Extension } from '@tiptap/core';
import { NodeSelection, Plugin, PluginKey } from '@tiptap/pm/state';

import { isFillableIslandElement } from '../../components/simple-editor/node-views/fillable-field-view.shared';
import {
  focusEditableInlineFieldDOM,
  isEditableInlineFieldNode,
} from '../../components/simple-editor/focus-next-metadata-chip';
import { EMR_FILLABLE_FOCUS_ISLAND_META } from './meta';

const key = new PluginKey('emrFillableIslandGuard');

/**
 * 方案 B：阻止 ProseMirror 接管岛屿内 pointer/keyboard 事件；
 * 插入进槽后 requestAnimationFrame 聚焦 contenteditable。
 */
export const EmrFillableIslandGuard = Extension.create({
  name: 'emrFillableIslandGuard',

  addProseMirrorPlugins() {
    const editor = this.editor;

    return [
      new Plugin({
        key,
        props: {
          stopEvent(event) {
            const t = event.target;
            if (!(t instanceof Node)) return false;
            const el = t instanceof Element ? t : t.parentElement;
            if (!isFillableIslandElement(el)) return false;
            // 岛屿内 pointer/keyboard/drag 一律不交给 PM（微移拖选会抢焦）
            return true;
          },
          handleDOMEvents: {
            dragstart(_view, event) {
              if (event.target instanceof Element && isFillableIslandElement(event.target)) {
                event.preventDefault();
                return true;
              }
              return false;
            },
          },
        },
        appendTransaction(trs, _oldState, newState) {
          const focusMeta = trs
            .map(
              (tr) =>
                tr.getMeta(EMR_FILLABLE_FOCUS_ISLAND_META) as
                  | { caret?: 'end' | 'start'; pos?: number }
                  | undefined,
            )
            .find(Boolean);
          if (!focusMeta) return null;

          const sel = newState.selection;
          const pos =
            typeof focusMeta.pos === 'number'
              ? focusMeta.pos
              : sel instanceof NodeSelection
                ? sel.from
                : null;
          if (pos == null) return null;
          const node = newState.doc.nodeAt(pos);
          if (!node || !isEditableInlineFieldNode(node)) return null;

          const caret = focusMeta.caret ?? 'start';
          requestAnimationFrame(() => {
            if (editor.isDestroyed) return;
            focusEditableInlineFieldDOM(editor.view, pos, caret);
          });
          return null;
        },
      }),
    ];
  },
});
