import { mergeAttributes, Node } from '@tiptap/core';

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    emrPageBreak: {
      insertEmrPageBreak: () => ReturnType;
    };
  }
}

export const EmrPageBreak = Node.create({
  name: 'emrPageBreak',

  group: 'block',
  atom: true,
  selectable: true,

  parseHTML() {
    return [{ tag: 'div[data-emr-page-break]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'div',
      mergeAttributes(HTMLAttributes, {
        'data-emr-page-break': '1',
        class: 'emr-page-break',
        contenteditable: 'false',
      }),
    ];
  },

  addCommands() {
    return {
      insertEmrPageBreak:
        () =>
        ({ commands }) =>
          commands.insertContent({ type: this.name }),
    };
  },
});
