import { mergeAttributes, Node } from '@tiptap/core';
import { VueNodeViewRenderer } from '@tiptap/vue-3';

import EmrTextFieldView from '../node-views/EmrTextFieldView.vue';

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    emrTextField: {
      insertEmrTextField: (
        attrs?: Partial<{ fieldKey: string; label: string; value: string }>,
      ) => ReturnType;
    };
  }
}

export const EmrTextField = Node.create({
  name: 'emrTextField',

  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,

  addAttributes() {
    return {
      fieldKey: { default: 'freeText' },
      label: { default: '文本框' },
      value: { default: '' },
    };
  },

  parseHTML() {
    return [{ tag: 'div[data-emr-text-field]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'div',
      mergeAttributes(HTMLAttributes, {
        'data-emr-text-field': '1',
        class: 'emr-text-field-fallback',
      }),
    ];
  },

  addNodeView() {
    return VueNodeViewRenderer(EmrTextFieldView);
  },

  addCommands() {
    return {
      insertEmrTextField:
        (attrs) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: { fieldKey: 'freeText', label: '文本框', value: '', ...attrs },
          }),
    };
  },
});
