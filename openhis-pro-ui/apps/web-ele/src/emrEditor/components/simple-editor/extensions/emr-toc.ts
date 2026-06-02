import { mergeAttributes, Node } from '@tiptap/core';

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    emrToc: {
      insertEmrToc: () => ReturnType;
    };
  }
}

/** 目录占位块（后续可接标题扫描自动生成） */
export const EmrToc = Node.create({
  name: 'emrToc',

  group: 'block',
  atom: true,
  selectable: true,

  addAttributes() {
    return {
      title: { default: '目录' },
    };
  },

  parseHTML() {
    return [{ tag: 'div[data-emr-toc]' }];
  },

  renderHTML({ HTMLAttributes, node }) {
    return [
      'div',
      mergeAttributes(HTMLAttributes, {
        'data-emr-toc': '1',
        class: 'emr-toc',
        contenteditable: 'false',
      }),
      ['div', { class: 'emr-toc__title' }, String(node.attrs.title)],
      ['div', { class: 'emr-toc__hint' }, '此处将按标题自动生成目录，或由元数据配置驱动。'],
    ];
  },

  addCommands() {
    return {
      insertEmrToc:
        () =>
        ({ commands }) =>
          commands.insertContent({ type: this.name }),
    };
  },
});
