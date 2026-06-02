import { mergeAttributes, Node } from '@tiptap/core';

import { DEFAULT_HORIZONTAL_LINE_WIDTH } from './emr-styled-horizontal-rule';

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    emrHorizontalDashed: {
      insertEmrHorizontalDashed: (attrs?: { lineColor?: string; lineWidth?: number }) => ReturnType;
    };
  }
}

function clampLineWidth(n: number): number {
  if (!Number.isFinite(n)) return DEFAULT_HORIZONTAL_LINE_WIDTH;
  return Math.min(24, Math.max(1, Math.round(n)));
}

const DEFAULT_DASHED_COLOR = '#000000';

/** 横向虚线分隔：颜色、粗细可配（打印/PDF 由行内样式体现） */
export const EmrHorizontalDashed = Node.create({
  name: 'emrHorizontalDashed',

  group: 'block',
  atom: true,
  selectable: true,

  addAttributes() {
    return {
      lineColor: {
        default: DEFAULT_DASHED_COLOR,
        parseHTML: (element) => {
          if (typeof element === 'string') return DEFAULT_DASHED_COLOR;
          const el = element as HTMLElement;
          return el.dataset.lineColor ?? DEFAULT_DASHED_COLOR;
        },
        renderHTML: (attrs) => ({
          'data-line-color': String(attrs.lineColor ?? DEFAULT_DASHED_COLOR),
        }),
      },
      lineWidth: {
        default: DEFAULT_HORIZONTAL_LINE_WIDTH,
        parseHTML: (element) => {
          if (typeof element === 'string') return DEFAULT_HORIZONTAL_LINE_WIDTH;
          const el = element as HTMLElement;
          const raw = el.dataset.lineWidth;
          if (raw === null || raw === '') return DEFAULT_HORIZONTAL_LINE_WIDTH;
          return clampLineWidth(Number(raw));
        },
        renderHTML: (attrs) => ({
          'data-line-width': String(clampLineWidth(Number(attrs.lineWidth))),
        }),
      },
    };
  },

  parseHTML() {
    return [{ tag: 'hr[data-emr-horizontal-dashed]' }];
  },

  renderHTML({ HTMLAttributes, node }) {
    const color = String(node.attrs.lineColor ?? DEFAULT_DASHED_COLOR);
    const w = clampLineWidth(Number(node.attrs.lineWidth));
    return [
      'hr',
      mergeAttributes(HTMLAttributes, {
        'data-emr-horizontal-dashed': '1',
        'data-line-color': color,
        'data-line-width': String(w),
        class: 'emr-horizontal-dashed',
        style: `border: none; border-top: ${w}px dashed ${color}; margin: 1em 0;`,
      }),
    ];
  },

  addCommands() {
    return {
      insertEmrHorizontalDashed:
        (attrs) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: {
              lineColor: attrs?.lineColor ?? DEFAULT_DASHED_COLOR,
              lineWidth: clampLineWidth(attrs?.lineWidth ?? DEFAULT_HORIZONTAL_LINE_WIDTH),
            },
          }),
    };
  },
});
