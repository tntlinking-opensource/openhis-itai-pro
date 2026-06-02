import type { Node as PMNode, ResolvedPos } from '@tiptap/pm/model';

import { Extension } from '@tiptap/core';

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    emrBlockLayout: {
      setBlockLineHeight: (lineHeight: string) => ReturnType;
      setParagraphSpacing: (opts: {
        marginBottom?: null | string;
        marginTop?: null | string;
      }) => ReturnType;
      setTextIndent: (indent: string) => ReturnType;
      unsetBlockLineHeight: () => ReturnType;
      unsetParagraphSpacing: () => ReturnType;
      unsetTextIndent: () => ReturnType;
    };
  }
}

function nearestParagraphOrHeading($pos: ResolvedPos): null | {
  depth: number;
  node: PMNode;
  pos: number;
} {
  for (let d = $pos.depth; d > 0; d--) {
    const node = $pos.node(d);
    if (node.type.name === 'paragraph' || node.type.name === 'heading') {
      return { depth: d, node, pos: $pos.before(d) };
    }
  }
  return null;
}

function nearestParagraph($pos: ResolvedPos): null | {
  depth: number;
  node: PMNode;
  pos: number;
} {
  for (let d = $pos.depth; d > 0; d--) {
    const node = $pos.node(d);
    if (node.type.name === 'paragraph') {
      return { depth: d, node, pos: $pos.before(d) };
    }
  }
  return null;
}

/**
 * 块级行高（段落/标题）与段间距（仅段落 margin-top/bottom）。
 * 替代仅作用于 textStyle 标记的官方 LineHeight，避免与 .ProseMirror 继承行高冲突导致「行距不生效」。
 */
export const EmrBlockLayout = Extension.create({
  name: 'emrBlockLayout',

  addGlobalAttributes() {
    return [
      {
        types: ['paragraph', 'heading'],
        attributes: {
          lineHeight: {
            default: null,
            parseHTML: (element) => {
              const el = element as HTMLElement;
              const lh = el.style?.lineHeight?.trim();
              return lh || null;
            },
            renderHTML: (attributes) => {
              if (!attributes.lineHeight) {
                return {};
              }
              return { style: `line-height: ${attributes.lineHeight}` };
            },
          },
        },
      },
      {
        types: ['paragraph'],
        attributes: {
          textIndent: {
            default: null,
            parseHTML: (element) => {
              const ti = (element as HTMLElement).style?.textIndent?.trim();
              return ti || null;
            },
            renderHTML: (attributes) => {
              if (!attributes.textIndent) {
                return {};
              }
              return { style: `text-indent: ${attributes.textIndent}` };
            },
          },
          paragraphMarginTop: {
            default: null,
            parseHTML: (element) => {
              const mt = (element as HTMLElement).style?.marginTop?.trim();
              return mt || null;
            },
            renderHTML: (attributes) => {
              if (!attributes.paragraphMarginTop) {
                return {};
              }
              return { style: `margin-top: ${attributes.paragraphMarginTop}` };
            },
          },
          paragraphMarginBottom: {
            default: null,
            parseHTML: (element) => {
              const mb = (element as HTMLElement).style?.marginBottom?.trim();
              return mb || null;
            },
            renderHTML: (attributes) => {
              if (!attributes.paragraphMarginBottom) {
                return {};
              }
              return { style: `margin-bottom: ${attributes.paragraphMarginBottom}` };
            },
          },
        },
      },
    ];
  },

  addCommands() {
    return {
      setBlockLineHeight:
        (lineHeight: string) =>
        ({ tr, state, dispatch }) => {
          const block = nearestParagraphOrHeading(state.selection.$from);
          if (!block) {
            return false;
          }
          tr.setNodeMarkup(block.pos, undefined, {
            ...block.node.attrs,
            lineHeight,
          });
          dispatch?.(tr);
          return true;
        },

      unsetBlockLineHeight:
        () =>
        ({ tr, state, dispatch }) => {
          const block = nearestParagraphOrHeading(state.selection.$from);
          if (!block) {
            return false;
          }
          tr.setNodeMarkup(block.pos, undefined, {
            ...block.node.attrs,
            lineHeight: null,
          });
          dispatch?.(tr);
          return true;
        },

      setTextIndent:
        (indent: string) =>
        ({ tr, state, dispatch }) => {
          const block = nearestParagraph(state.selection.$from);
          if (!block) return false;
          tr.setNodeMarkup(block.pos, undefined, {
            ...block.node.attrs,
            textIndent: indent,
          });
          dispatch?.(tr);
          return true;
        },

      unsetTextIndent:
        () =>
        ({ tr, state, dispatch }) => {
          const block = nearestParagraph(state.selection.$from);
          if (!block) return false;
          tr.setNodeMarkup(block.pos, undefined, {
            ...block.node.attrs,
            textIndent: null,
          });
          dispatch?.(tr);
          return true;
        },

      setParagraphSpacing:
        (opts: { marginBottom?: null | string; marginTop?: null | string }) =>
        ({ tr, state, dispatch }) => {
          const $from = state.selection.$from;
          let depth = $from.depth;
          let paraDepth: null | number = null;
          while (depth > 0) {
            if ($from.node(depth).type.name === 'paragraph') {
              paraDepth = depth;
              break;
            }
            depth -= 1;
          }
          if (paraDepth === null) {
            return false;
          }
          const node = $from.node(paraDepth);
          const pos = $from.before(paraDepth);
          const next = { ...node.attrs } as Record<string, unknown>;
          if (Object.prototype.hasOwnProperty.call(opts, 'marginTop')) {
            next.paragraphMarginTop = opts.marginTop;
          }
          if (Object.prototype.hasOwnProperty.call(opts, 'marginBottom')) {
            next.paragraphMarginBottom = opts.marginBottom;
          }
          tr.setNodeMarkup(pos, undefined, next);
          dispatch?.(tr);
          return true;
        },

      unsetParagraphSpacing:
        () =>
        ({ tr, state, dispatch }) => {
          const $from = state.selection.$from;
          let depth = $from.depth;
          let paraDepth: null | number = null;
          while (depth > 0) {
            if ($from.node(depth).type.name === 'paragraph') {
              paraDepth = depth;
              break;
            }
            depth -= 1;
          }
          if (paraDepth === null) {
            return false;
          }
          const node = $from.node(paraDepth);
          const pos = $from.before(paraDepth);
          tr.setNodeMarkup(pos, undefined, {
            ...node.attrs,
            paragraphMarginBottom: null,
            paragraphMarginTop: null,
          });
          dispatch?.(tr);
          return true;
        },
    };
  },
});
