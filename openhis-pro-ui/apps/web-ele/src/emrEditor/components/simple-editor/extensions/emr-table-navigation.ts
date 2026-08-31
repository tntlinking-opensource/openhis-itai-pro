import { Extension } from '@tiptap/core';
import { Plugin, PluginKey, TextSelection } from '@tiptap/pm/state';

import { EMR_PM_CONTENT_FIELD_TYPES } from '../emr-data-element-text';

const key = new PluginKey('emrTableNavigation');

function isInTable($from: import('@tiptap/pm/model').ResolvedPos): boolean {
  for (let d = $from.depth; d > 0; d--) {
    if ($from.node(d).type.name === 'table') return true;
  }
  return false;
}

function isInsideFillable($from: import('@tiptap/pm/model').ResolvedPos): boolean {
  for (let d = $from.depth; d > 0; d--) {
    if (EMR_PM_CONTENT_FIELD_TYPES.has($from.node(d).type.name)) return true;
  }
  return false;
}

/**
 * 表格内 Tab 导航（仅当光标不在可填域内时）：
 * 可填域内 Tab 仍由 EmrFillableKeymap 跳下一填空。
 */
export const EmrTableNavigation = Extension.create({
  name: 'emrTableNavigation',

  addProseMirrorPlugins() {
    const editor = this.editor;

    return [
      new Plugin({
        key,
        props: {
          handleKeyDown(view, event) {
            if (!view.editable) return false;
            if (event.key !== 'Tab') return false;
            if (event.altKey || event.metaKey || event.ctrlKey) return false;

            const { state } = view;
            const sel = state.selection;
            if (!(sel instanceof TextSelection) && sel.constructor.name !== 'CellSelection') {
              return false;
            }
            if (!isInTable(sel.$from)) return false;
            // 可填域内让填空 Tab 优先
            if (isInsideFillable(sel.$from)) return false;

            event.preventDefault();
            if (event.shiftKey) {
              return editor.commands.goToPreviousCell();
            }
            return editor.commands.goToNextCell();
          },
        },
      }),
    ];
  },
});
