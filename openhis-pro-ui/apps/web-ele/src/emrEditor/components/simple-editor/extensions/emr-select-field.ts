import { mergeAttributes, Node } from '@tiptap/core';
import { VueNodeViewRenderer } from '@tiptap/vue-3';

import EmrSelectFieldView from '../node-views/EmrSelectFieldView.vue';

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    emrSelectField: {
      insertEmrSelectField: (
        attrs?: Partial<{ fieldKey: string; label: string; value: string; optionsJson: string }>,
      ) => ReturnType;
    };
  }
}

const defaultOptions = JSON.stringify(['选项甲', '选项乙', '选项丙']);

export const EmrSelectField = Node.create({
  name: 'emrSelectField',

  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,

  addAttributes() {
    return {
      fieldKey: { default: 'select' },
      label: { default: '下拉框' },
      optionsJson: { default: defaultOptions },
      value: { default: '选项甲' },
    };
  },

  parseHTML() {
    return [{ tag: 'div[data-emr-select-field]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'div',
      mergeAttributes(HTMLAttributes, {
        'data-emr-select-field': '1',
        class: 'emr-select-field-fallback',
      }),
    ];
  },

  addNodeView() {
    return VueNodeViewRenderer(EmrSelectFieldView);
  },

  addCommands() {
    return {
      insertEmrSelectField:
        (attrs) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: {
              fieldKey: 'select',
              label: '下拉框',
              optionsJson: defaultOptions,
              value: '选项甲',
              ...attrs,
            },
          }),
    };
  },
});
