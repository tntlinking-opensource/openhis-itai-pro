import { canInsertNode, isNodeSelection, mergeAttributes } from '@tiptap/core';
import { HorizontalRule } from '@tiptap/extension-horizontal-rule';
import { NodeSelection, TextSelection } from '@tiptap/pm/state';

export const DEFAULT_HORIZONTAL_LINE_COLOR = '#000000';
export const DEFAULT_HORIZONTAL_LINE_WIDTH = 1;

function clampLineWidth(n: number): number {
  if (!Number.isFinite(n)) return DEFAULT_HORIZONTAL_LINE_WIDTH;
  return Math.min(24, Math.max(1, Math.round(n)));
}

/** 实线横线：可设颜色与粗细（覆盖 StarterKit 默认 horizontalRule） */
export const EmrStyledHorizontalRule = HorizontalRule.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      lineColor: {
        default: DEFAULT_HORIZONTAL_LINE_COLOR,
        parseHTML: (element) => {
          if (typeof element === 'string') return DEFAULT_HORIZONTAL_LINE_COLOR;
          const el = element as HTMLElement;
          return el.dataset.lineColor ?? DEFAULT_HORIZONTAL_LINE_COLOR;
        },
        renderHTML: (attrs) => ({
          'data-line-color': String(attrs.lineColor ?? DEFAULT_HORIZONTAL_LINE_COLOR),
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
    return [{ tag: 'hr:not([data-emr-horizontal-dashed])' }];
  },

  renderHTML({ node, HTMLAttributes }) {
    const color = String(node.attrs.lineColor ?? DEFAULT_HORIZONTAL_LINE_COLOR);
    const w = clampLineWidth(Number(node.attrs.lineWidth));
    return [
      'hr',
      mergeAttributes(this.options.HTMLAttributes, HTMLAttributes, {
        'data-line-color': color,
        'data-line-width': String(w),
        style: `border: none; border-top: ${w}px solid ${color}; margin: 1em 0;`,
      }),
    ];
  },

  addCommands() {
    return {
      setHorizontalRule:
        (attrs?: { lineColor?: string; lineWidth?: number }) =>
        ({ chain, state }) => {
          const ruleType = state.schema.nodes[this.name];
          if (!ruleType || !canInsertNode(state, ruleType)) {
            return false;
          }

          const lineColor = attrs?.lineColor ?? DEFAULT_HORIZONTAL_LINE_COLOR;
          const lineWidth = clampLineWidth(attrs?.lineWidth ?? DEFAULT_HORIZONTAL_LINE_WIDTH);
          const nodeAttrs = { lineColor, lineWidth };

          const { selection } = state;
          const { $to: $originTo } = selection;

          const currentChain = chain();

          if (isNodeSelection(selection)) {
            currentChain.insertContentAt($originTo.pos, {
              type: this.name,
              attrs: nodeAttrs,
            });
          } else {
            currentChain.insertContent({ type: this.name, attrs: nodeAttrs });
          }

          return currentChain
            .command(({ state: chainState, tr, dispatch }) => {
              if (dispatch) {
                const { $to } = tr.selection;
                const posAfter = $to.end();

                if ($to.nodeAfter) {
                  if ($to.nodeAfter.isTextblock) {
                    tr.setSelection(TextSelection.create(tr.doc, $to.pos + 1));
                  } else if ($to.nodeAfter.isBlock) {
                    tr.setSelection(NodeSelection.create(tr.doc, $to.pos));
                  } else {
                    tr.setSelection(TextSelection.create(tr.doc, $to.pos));
                  }
                } else {
                  const nodeType =
                    chainState.schema.nodes[this.options.nextNodeType] ||
                    $to.parent.type.contentMatch.defaultType;
                  const node = nodeType?.create();

                  if (node) {
                    tr.insert(posAfter, node);
                    tr.setSelection(TextSelection.create(tr.doc, posAfter + 1));
                  }
                }

                tr.scrollIntoView();
              }

              return true;
            })
            .run();
        },
    };
  },
});
