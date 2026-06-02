import { mergeAttributes, Node } from '@tiptap/core';
import { VueNodeViewRenderer } from '@tiptap/vue-3';

import EmrCheckboxFieldView from '../node-views/EmrCheckboxFieldView.vue';

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    emrCheckboxField: {
      insertEmrCheckboxField: (
        attrs?: Partial<{ checked: boolean; fieldKey: string; label: string }>,
      ) => ReturnType;
    };
  }
}

export const EmrCheckboxField = Node.create({
  name: 'emrCheckboxField',

  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,

  addAttributes() {
    return {
      fieldKey: { default: 'checkbox' },
      label: { default: '复选框' },
      checked: {
        default: false,
        parseHTML: (el) => el.dataset.checked === 'true',
        renderHTML: (attrs) => ({
          'data-checked': attrs.checked ? 'true' : 'false',
        }),
      },
    };
  },

  parseHTML() {
    return [{ tag: 'div[data-emr-checkbox-field]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'div',
      mergeAttributes(HTMLAttributes, {
        'data-emr-checkbox-field': '1',
        class: 'emr-checkbox-field-fallback',
      }),
    ];
  },

  addNodeView() {
    return VueNodeViewRenderer(EmrCheckboxFieldView);
  },

  addCommands() {
    return {
      insertEmrCheckboxField:
        (attrs) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: { fieldKey: 'checkbox', label: '复选框', checked: false, ...attrs },
          }),
    };
  },
});
