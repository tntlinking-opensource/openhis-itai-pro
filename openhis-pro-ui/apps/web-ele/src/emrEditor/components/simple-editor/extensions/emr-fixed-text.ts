import type { Node as PMNode, Schema } from '@tiptap/pm/model';

import type { EmrFixedTextSegmentJSON } from '../emr-fixed-text-types';

import { mergeAttributes, Node } from '@tiptap/core';
import { isHistoryTransaction } from '@tiptap/pm/history';
import { Fragment, Mark } from '@tiptap/pm/model';
import { NodeSelection, Plugin, PluginKey } from '@tiptap/pm/state';
import { VueNodeViewRenderer } from '@tiptap/vue-3';

import EmrFixedTextView from '../node-views/EmrFixedTextView.vue';

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    emrFixedText: {
      fixSelectionAsEmrFixedText: () => ReturnType;
      insertEmrFixedText: (attrs: {
        segments?: EmrFixedTextSegmentJSON[] | null;
        text: string;
      }) => ReturnType;
      unfixEmrFixedText: () => ReturnType;
    };
  }
}

/** 仅此 meta 为 true 时允许减少文档中的 emrFixedText 节点（取消固定、父组件 setContent 等） */
export const EMR_FIXED_TEXT_ALLOW_MUTATE_META = 'emrAllowFixedMutate';

const MAX_LEN = 2000;

const protectPluginKey = new PluginKey('emrFixedTextProtect');

function countEmrFixedText(doc: PMNode) {
  let n = 0;
  doc.descendants((node) => {
    if (node.type.name === 'emrFixedText') n += 1;
  });
  return n;
}

export function plainFromSegments(segments: EmrFixedTextSegmentJSON[]): string {
  return segments.map((s) => s.text).join('');
}

/** 从选区切片提取带 marks 的文本段（与 ProseMirror slice 一致） */
export function extractFixedTextSegments(
  doc: PMNode,
  from: number,
  to: number,
): EmrFixedTextSegmentJSON[] {
  const slice = doc.slice(from, to);
  const out: EmrFixedTextSegmentJSON[] = [];
  slice.content.forEach((node) => {
    if (node.isText) {
      out.push({
        text: node.text ?? '',
        marks: node.marks.map((m) => m.toJSON()),
      });
    }
  });
  return out;
}

function clampSegments(segments: EmrFixedTextSegmentJSON[]): EmrFixedTextSegmentJSON[] {
  let total = 0;
  const out: EmrFixedTextSegmentJSON[] = [];
  for (const seg of segments) {
    if (total >= MAX_LEN) break;
    const left = MAX_LEN - total;
    const take = Math.min(seg.text.length, left);
    if (take <= 0) break;
    const piece =
      take === seg.text.length ? seg : { marks: seg.marks, text: seg.text.slice(0, take) };
    out.push(piece);
    total += take;
  }
  return out;
}

function selectionHasNonTextInline(doc: PMNode, from: number, to: number) {
  let found = false;
  doc.nodesBetween(from, to, (node) => {
    if (node.isBlock) return true;
    if (node.isText) return true;
    found = true;
    return false;
  });
  return found;
}

function fragmentFromSegments(
  schema: Schema,
  segments: EmrFixedTextSegmentJSON[] | null | undefined,
  legacyText: string,
): Fragment {
  if (segments?.length) {
    const pieces: PMNode[] = [];
    for (const seg of segments) {
      const marks = seg.marks
        .map((mj) => {
          try {
            return Mark.fromJSON(schema, mj);
          } catch {
            return null;
          }
        })
        .filter((m): m is Mark => m !== null);
      if (seg.text) pieces.push(schema.text(seg.text, marks));
    }
    if (pieces.length > 0) return Fragment.fromArray(pieces);
  }
  if (legacyText) return Fragment.fromArray([schema.text(legacyText, [])]);
  return Fragment.empty;
}

/**
 * 模板版式固定文案：行内原子节点，不可改字、不可删除；
 * 可选 segments 保留粗体/颜色等 marks；须先「取消固定」或撤销/重做后再编辑。
 */
export const EmrFixedText = Node.create({
  name: 'emrFixedText',

  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,
  draggable: false,

  addAttributes() {
    return {
      text: {
        default: '',
      },
      segments: {
        default: null as EmrFixedTextSegmentJSON[] | null,
        parseHTML: (element) => {
          if (typeof element === 'string') return null;
          const el = element as HTMLElement;
          const raw = el.dataset.segments;
          if (!raw) return null;
          try {
            return JSON.parse(raw) as EmrFixedTextSegmentJSON[];
          } catch {
            return null;
          }
        },
        renderHTML: (attrs) => {
          const segs = attrs.segments as EmrFixedTextSegmentJSON[] | null | undefined;
          if (segs?.length) {
            return { 'data-segments': JSON.stringify(segs) };
          }
          return {};
        },
      },
    };
  },

  parseHTML() {
    return [
      {
        tag: 'span[data-emr-fixed-text]',
        getAttrs: (el) => {
          if (!(el instanceof HTMLElement)) return false;
          const rawSeg = el.dataset.segments;
          let segments: EmrFixedTextSegmentJSON[] | null = null;
          if (rawSeg) {
            try {
              segments = JSON.parse(rawSeg) as EmrFixedTextSegmentJSON[];
            } catch {
              segments = null;
            }
          }
          const rawText = el.dataset.text ?? el.textContent ?? '';
          const text = String(rawText).slice(0, MAX_LEN);
          if (segments?.length) {
            return { segments, text: plainFromSegments(segments).slice(0, MAX_LEN) || text };
          }
          return { segments: null, text };
        },
      },
    ];
  },

  renderHTML({ node, HTMLAttributes }) {
    const text = String(node.attrs.text ?? '').slice(0, MAX_LEN);
    const segments = node.attrs.segments as EmrFixedTextSegmentJSON[] | null | undefined;
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        'data-emr-fixed-text': '1',
        'data-text': text,
        class: 'emr-fixed-text',
        contenteditable: 'false',
        spellcheck: 'false',
        ...(segments?.length ? { 'data-segments': JSON.stringify(segments) } : {}),
      }),
      text,
    ];
  },

  addNodeView() {
    return VueNodeViewRenderer(EmrFixedTextView);
  },

  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: protectPluginKey,
        filterTransaction(tr, oldState) {
          if (!tr.docChanged) return true;
          if (tr.getMeta(EMR_FIXED_TEXT_ALLOW_MUTATE_META) === true) return true;
          if (isHistoryTransaction(tr)) return true;
          const oldN = countEmrFixedText(oldState.doc);
          const newN = countEmrFixedText(tr.doc);
          return newN >= oldN;
        },
      }),
    ];
  },

  addCommands() {
    return {
      insertEmrFixedText:
        (attrs) =>
        ({ commands }) => {
          const rawSegs = attrs.segments;
          let plain: string;
          let segments: EmrFixedTextSegmentJSON[] | null = null;
          if (Array.isArray(rawSegs) && rawSegs.length > 0) {
            segments = clampSegments(rawSegs);
            plain = plainFromSegments(segments).slice(0, MAX_LEN);
          } else {
            plain = String(attrs.text ?? '')
              .trim()
              .slice(0, MAX_LEN);
          }
          if (!plain) return false;
          return commands.insertContent({
            type: this.name,
            attrs: {
              text: plain,
              segments: segments !== null && segments.length > 0 ? segments : null,
            },
          });
        },
      fixSelectionAsEmrFixedText:
        () =>
        ({ state, commands }) => {
          const { from, to, empty } = state.selection;
          if (empty) return false;
          const $from = state.doc.resolve(from);
          const $to = state.doc.resolve(to);
          if ($from.parent !== $to.parent) return false;
          if (selectionHasNonTextInline(state.doc, from, to)) return false;
          const segments = extractFixedTextSegments(state.doc, from, to);
          if (segments.length === 0) return false;
          const clamped = clampSegments(segments);
          const plain = plainFromSegments(clamped);
          if (!plain.trim()) return false;
          return commands.insertContentAt(
            { from, to },
            {
              type: this.name,
              attrs: { text: plain, segments: clamped },
            },
          );
        },
      unfixEmrFixedText:
        () =>
        ({ state, dispatch }) => {
          const sel = state.selection;
          if (!(sel instanceof NodeSelection)) return false;
          if (sel.node.type.name !== 'emrFixedText') return false;
          const segments = sel.node.attrs.segments as EmrFixedTextSegmentJSON[] | null | undefined;
          const text = String(sel.node.attrs.text ?? '');
          const frag = fragmentFromSegments(state.schema, segments, text);
          const tr = state.tr;
          tr.setMeta(EMR_FIXED_TEXT_ALLOW_MUTATE_META, true);
          tr.replaceWith(sel.from, sel.to, frag);
          if (dispatch) dispatch(tr);
          return true;
        },
    };
  },
});
