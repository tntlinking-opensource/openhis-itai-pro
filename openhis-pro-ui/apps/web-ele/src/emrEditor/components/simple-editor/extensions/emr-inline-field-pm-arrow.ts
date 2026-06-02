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
 * 选区在 ProseMirror 正文内、紧贴可编辑行内域（元数据/自由文本等）外侧时，
 * 按方向键进入域内，避免 PM 原子节点被方向键「跳过」后无法回到 contenteditable。
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
