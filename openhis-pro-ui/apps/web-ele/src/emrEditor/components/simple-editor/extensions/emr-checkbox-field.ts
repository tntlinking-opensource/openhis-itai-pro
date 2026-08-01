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

/** 行内可点复选框（纸质勾选：□清醒 □嗜睡）；文书固定模板下也可改 checked */
export const EmrCheckboxField = Node.create({
  name: 'emrCheckboxField',

  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,
  draggable: false,

  addAttributes() {
    return {
      fieldKey: { default: 'checkbox' },
      label: { default: '复选框' },
      checked: {
        default: false,
        parseHTML: (el) => {
          if (typeof el === 'string') return false;
          return (el as HTMLElement).dataset.checked === 'true';
        },
        renderHTML: (attrs) => ({
          'data-checked': attrs.checked ? 'true' : 'false',
        }),
      },
    };
  },

  parseHTML() {
    return [{ tag: 'span[data-emr-checkbox-field]' }, { tag: 'div[data-emr-checkbox-field]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'span',
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
