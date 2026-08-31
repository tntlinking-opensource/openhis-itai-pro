import { Extension } from '@tiptap/core';
import { NodeSelection, Plugin, PluginKey, TextSelection } from '@tiptap/pm/state';

import {
  focusEditableInlineFieldDOM,
  inlineEditableFieldDocPosFromDOMPos,
  isEditableInlineFieldNode,
} from '../focus-next-metadata-chip';
import { handleInlineFieldKeydown } from '../use-inline-field-keydown';

const key = new PluginKey('emrInlineFieldPmArrow');

/**
 * 行内可填域岛屿方向键：PM 正文 ↔ contenteditable 跳进跳出。
 */
export const EmrInlineFieldPmArrow = Extension.create({
  name: 'emrInlineFieldPmArrow',

  addProseMirrorPlugins() {
    const editor = this.editor;
    return [
      new Plugin({
        key,
        props: {
          handleKeyDown(view, event) {
            if (!view.editable) return false;
            if (event.shiftKey || event.altKey || event.metaKey || event.ctrlKey) return false;
            if (event.isComposing) return false;

            if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
              const t = event.target;
              if (!(t instanceof Node)) return false;
              const root =
                t instanceof Element
                  ? t.closest('[contenteditable="true"].emr-inline-text__field')
                  : null;
              if (!root || !(root instanceof HTMLElement) || !view.dom.contains(root)) return false;
              let domPos: number;
              try {
                domPos = view.posAtDOM(root, 0);
              } catch {
                return false;
              }
              const start = inlineEditableFieldDocPosFromDOMPos(view.state.doc, domPos);
              if (start == null) return false;
              handleInlineFieldKeydown(editor, start, event, 'contentEditable');
              return event.defaultPrevented;
            }

            if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return false;

            const { state } = view;
            const sel = state.selection;

            if (sel instanceof TextSelection && sel.empty) {
              const $from = sel.$from;
              if (event.key === 'ArrowRight') {
                const after = $from.nodeAfter;
                if (after && isEditableInlineFieldNode(after)) {
                  event.preventDefault();
                  focusEditableInlineFieldDOM(view, $from.pos, 'start');
                  return true;
                }
              } else {
                const before = $from.nodeBefore;
                if (before && isEditableInlineFieldNode(before)) {
                  event.preventDefault();
                  const nodeStart = $from.pos - before.nodeSize;
                  focusEditableInlineFieldDOM(view, nodeStart, 'end');
                  return true;
                }
              }
              return false;
            }

            if (sel instanceof NodeSelection) {
              const n = sel.node;
              if (!isEditableInlineFieldNode(n)) return false;
              event.preventDefault();
              if (event.key === 'ArrowRight') {
                focusEditableInlineFieldDOM(view, sel.from, 'start');
              } else {
                focusEditableInlineFieldDOM(view, sel.from, 'end');
              }
              return true;
            }

            return false;
          },
        },
      }),
    ];
  },
});
