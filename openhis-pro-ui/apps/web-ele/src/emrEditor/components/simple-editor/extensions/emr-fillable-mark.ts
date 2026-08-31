import type { EditorState, Transaction } from '@tiptap/pm/state';

import { Mark, mergeAttributes } from '@tiptap/core';
import { Plugin, PluginKey } from '@tiptap/pm/state';
import { Mapping, ReplaceStep } from '@tiptap/pm/transform';
import { Decoration, DecorationSet } from '@tiptap/pm/view';

import {
  EMR_FILLABLE_MARK,
  FILLABLE_EMPTY_CARET,
  FILLABLE_EMPTY_CARET_LEGACY,
  isFillableSlotEmptyText,
  stripFillablePlaceholders,
  type EmrFillableMarkAttrs,
} from '../emr-fillable-json';
import {
  collectFillableMarkRanges,
  isFillableHitEmpty,
  isImeCompositionGhostAfterFillable,
  recoverLostFillableMarkSlot,
  replaceFillableHitPlainText,
} from '../../../fillable-field/core/resolve';
import { getFillableImeStorage } from '../../../fillable-field/runtime/ime-controller';
import { EMR_SKIP_FILLABLE_RESTORE_META } from '../../../fillable-field/kit/meta';

export { EMR_SKIP_FILLABLE_RESTORE_META };

const emptyDecoKey = new PluginKey('emrFillableEmptyDeco');
const keepEmptyKey = new PluginKey('emrFillableKeepEmpty');

function extraClass(attrs: Record<string, unknown>): string {
  const readonly =
    attrs.editable === false || attrs.editable === '0' ? ' emr-fillable--readonly' : '';
  const kind = String(attrs.kind ?? 'freeText');
  const required =
    attrs.required === true || attrs.required === '1' || attrs.required === 'true'
      ? ' emr-fillable--required'
      : '';
  return `emr-fillable emr-bracket-field emr-bracket-field--css emr-fillable--${kind}${readonly}${required}`;
}

/**
 * 是否处于「正在编辑该槽」：
 * - 光标在半开区间 (from, to) 内；或
 * - 光标在 from 且未显式出域（storedMarks=[] 表示停在 [ 前）；或
 * - 光标在槽尾 to，且 storedMarks 仍带着本槽 mark（续写）
 * 槽首/槽尾 storedMarks=[] = 已出域，不得亮 active。
 */
function isFillableEditingHit(
  hit: {
    from: number;
    to: number;
    mark?: { attrs: Record<string, unknown>; type: { name: string } };
  },
  head: number,
  selFrom: number,
  selTo: number,
  storedMarks: readonly import('@tiptap/pm/model').Mark[] | null,
): boolean {
  const selOverlap = selTo > selFrom && selFrom < hit.to && selTo > hit.from;
  if (selOverlap) return true;
  const explicitlyOutside =
    storedMarks != null && !storedMarks.some((m) => m.type.name === EMR_FILLABLE_MARK);
  // 槽首外侧：停在 from 且 marks 已清 → 视觉在 [ 前，不算编辑中
  if (head === hit.from && explicitlyOutside) return false;
  if (head >= hit.from && head < hit.to) return true;
  if (head !== hit.to || !hit.mark) return false;
  if (explicitlyOutside) return false;
  const slotId = String(hit.mark.attrs.slotId ?? '');
  return Boolean(
    storedMarks?.some(
      (m) => m.type.name === EMR_FILLABLE_MARK && String(m.attrs.slotId ?? '') === slotId,
    ),
  );
}

function createBracketWidget(ch: '[' | ']', active: boolean) {
  return () => {
    const el = document.createElement('span');
    el.className = `emr-fillable-bracket emr-fillable-bracket--${ch === '[' ? 'open' : 'close'}${
      active ? ' is-active' : ''
    }`;
    el.textContent = ch;
    el.contentEditable = 'false';
    el.setAttribute('aria-hidden', 'true');
    return el;
  };
}

/**
 * 连续正文可填域：和加粗一样是 mark，DOM 仍是一段字。
 * inclusive:false → 光标在段首/段尾外侧时不再「粘」在域上，避免吞掉同行普通正文。
 */
export const EmrFillable = Mark.create({
  name: EMR_FILLABLE_MARK,

  spanning: true,
  inclusive: false,
  excludes: EMR_FILLABLE_MARK,

  addAttributes() {
    return {
      slotId: { default: '' },
      kind: { default: 'freeText' },
      editable: {
        default: true,
        parseHTML: (el) => (el as HTMLElement).dataset.editable !== '0',
        renderHTML: (attrs) => ({
          'data-editable': attrs.editable === false ? '0' : '1',
        }),
      },
      label: { default: '' },
      placeholder: { default: '填写' },
      fieldKey: { default: '' },
      elementId: { default: '' },
      labelSnapshot: { default: '' },
      widgetOverride: { default: '' },
      optionsJson: { default: '[]' },
      required: {
        default: false,
        parseHTML: (el) => (el as HTMLElement).dataset.required === '1',
        renderHTML: (attrs) => ({
          'data-required': attrs.required === true ? '1' : '0',
        }),
      },
      requiredMessage: {
        default: '',
        parseHTML: (el) => (el as HTMLElement).dataset.requiredMessage ?? '',
        renderHTML: (attrs) => ({
          'data-required-message': String(attrs.requiredMessage ?? ''),
        }),
      },
    };
  },

  parseHTML() {
    return [
      { tag: `span[data-emr-fillable]` },
      { tag: 'span[data-emr-free-text]' },
      { tag: 'span[data-emr-metadata-chip]' },
      {
        tag: 'span[data-emr-data-element]',
        getAttrs: (el) => {
          if (!(el instanceof HTMLElement)) return false;
          const widget = el.dataset.widgetOverride || '';
          if (widget === 'select' || widget === 'checkbox') return false;
          return {};
        },
      },
    ];
  },

  renderHTML({ HTMLAttributes }) {
    const placeholder = String(HTMLAttributes.placeholder ?? HTMLAttributes.label ?? '填写');
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        'data-emr-fillable': '1',
        'data-placeholder': placeholder,
        class: extraClass(HTMLAttributes),
      }),
    ];
  },

  addProseMirrorPlugins() {
    const editor = this.editor;
    return [
      new Plugin({
        key: emptyDecoKey,
        props: {
          decorations(state) {
            const decos: Decoration[] = [];
            const hits = collectFillableMarkRanges(state.doc);
            const head = state.selection.head;
            const from = Math.min(state.selection.from, state.selection.to);
            const to = Math.max(state.selection.from, state.selection.to);
            const stored = state.storedMarks;
            const composing = Boolean(editor.view?.composing);
            const imeStorage = getFillableImeStorage(editor);
            const imeSlotId = imeStorage?.lockedSlotId ?? imeStorage?.imeSlotId ?? null;
            for (const hit of hits) {
              const text = state.doc.textBetween(hit.from, hit.to, '\n', '\n');
              const empty = isFillableSlotEmptyText(text);
              const imeGhost = isImeCompositionGhostAfterFillable(
                state.doc,
                hit,
                head,
                stored,
                composing,
                imeSlotId,
              );
              const editing = isFillableEditingHit(hit, head, from, to, stored) || imeGhost;
              if (empty) {
                const ph = String(hit.mark?.attrs?.placeholder || hit.mark?.attrs?.label || '填写');
                decos.push(
                  Decoration.inline(hit.from, hit.to, {
                    class: 'emr-fillable--empty',
                    'data-placeholder': ph,
                  }),
                );
                const req = hit.mark?.attrs?.required;
                if (req === true || req === '1' || req === 'true') {
                  decos.push(
                    Decoration.inline(hit.from, hit.to, {
                      class: 'emr-fillable--required-empty',
                    }),
                  );
                }
              }
              /**
               * 括号用 widget（每槽一对，含空槽）：
               * - 编辑中 ] side=1；已出域 ] side=-1
               * - 组字拼音落在槽外时：不改 doc，把 ] 画到 head 后，避免叠字又避免拼音看起来在 ] 外
               */
              {
                const slotId = String(hit.mark?.attrs?.slotId ?? `${hit.from}-${hit.to}`);
                const closeAt = imeGhost ? head : hit.to;
                decos.push(
                  Decoration.widget(hit.from, createBracketWidget('[', editing), {
                    // 编辑中：[ 在光标左；已出域停在 from 时：[ 在光标右（可停在元数据前）
                    side: editing ? -1 : 1,
                    key: `emr-ob-${slotId}`,
                  }),
                );
                decos.push(
                  Decoration.widget(closeAt, createBracketWidget(']', editing), {
                    side: editing ? 1 : -1,
                    key: `emr-cb-${slotId}`,
                  }),
                );
              }
              if (editing) {
                const activeTo = imeGhost ? head : hit.to;
                decos.push(
                  Decoration.inline(hit.from, activeTo, {
                    class: 'emr-fillable--active',
                  }),
                );
              }
            }
            return DecorationSet.create(state.doc, decos);
          },
        },
      }),
      new Plugin({
        key: keepEmptyKey,
        appendTransaction(trs, oldState, newState) {
          if (!trs.some((tr) => tr.docChanged)) return null;
          if (trs.some((tr) => tr.getMeta(EMR_SKIP_FILLABLE_RESTORE_META))) {
            return null;
          }
          // 组字中 doc 变更：仅 recover（完整 restore 等 compositionend 结算）
          if (editor.view?.composing) {
            const recover = recoverLostFillableMarkSlot(trs, oldState, newState);
            if (recover) recover.setMeta('addToHistory', false);
            return recover;
          }
          return restoreFillableAfterEdit(trs, oldState, newState);
        },
      }),
    ];
  },
});

function slotDeletedWithOutside(
  trs: readonly Transaction[],
  hitFrom: number,
  hitTo: number,
): boolean {
  for (const tr of trs) {
    for (const step of tr.steps) {
      if (!(step instanceof ReplaceStep)) continue;
      if (step.from < hitFrom || step.to > hitTo) {
        return true;
      }
    }
  }
  return false;
}

function slotTouched(trs: readonly Transaction[], hitFrom: number, hitTo: number): boolean {
  for (const tr of trs) {
    for (const step of tr.steps) {
      if (!(step instanceof ReplaceStep)) continue;
      if (step.from < hitTo && step.to > hitFrom) {
        return true;
      }
    }
  }
  return false;
}

/**
 * 输入后：仅清掉空槽占位符；不再把槽后普通正文「吸」进 mark（吞字根因）。
 * 自由文本清空后不回填空槽（行为接近加粗：删光即无）。
 */
function restoreFillableAfterEdit(
  trs: readonly Transaction[],
  oldState: EditorState,
  newState: EditorState,
): Transaction | null {
  let tr: Transaction | null = recoverLostFillableMarkSlot(trs, oldState, newState);
  let docState = tr ? newState.apply(tr) : newState;

  const restored = restoreClearedFillableSlots(trs, oldState, docState);
  if (restored) {
    tr = restored;
    docState = docState.apply(restored);
  }

  const schema = docState.schema;
  let cleanTr = docState.tr;
  let cleanChanged = false;

  const hits = collectFillableMarkRanges(docState.doc);
  for (let i = hits.length - 1; i >= 0; i--) {
    const hit = hits[i];
    if (!hit?.mark) continue;
    const raw = docState.doc.textBetween(hit.from, hit.to, '\n', '\n');
    if (!raw.includes(FILLABLE_EMPTY_CARET) && !raw.includes(FILLABLE_EMPTY_CARET_LEGACY)) {
      continue;
    }
    if (isFillableHitEmpty(docState.doc, hit)) continue;
    const cleaned = stripFillablePlaceholders(raw);
    if (!cleaned) continue;
    cleanTr = replaceFillableHitPlainText(cleanTr, schema, hit, cleaned);
    cleanChanged = true;
  }

  if (cleanChanged) {
    cleanTr.setMeta('addToHistory', false);
    return cleanTr;
  }
  if (tr?.docChanged) return tr;
  return null;
}

function restoreClearedFillableSlots(
  trs: readonly Transaction[],
  oldState: EditorState,
  newState: EditorState,
): Transaction | null {
  const markType = newState.schema.marks[EMR_FILLABLE_MARK];
  if (!markType) return null;

  const oldHits = collectFillableMarkRanges(oldState.doc);
  const newHits = collectFillableMarkRanges(newState.doc);
  const alive = new Set(newHits.map((h) => String(h.mark?.attrs.slotId ?? '')));

  const mapping = new Mapping();
  for (const stepTr of trs) {
    mapping.appendMapping(stepTr.mapping);
  }

  const restores: { at: number; attrs: EmrFillableMarkAttrs }[] = [];
  for (const old of oldHits) {
    const kind = String(old.mark?.attrs.kind ?? 'freeText');
    /** 自由文本：删光即消失，不回填 ZWSP（避免光标乱跳 / 幽灵空槽） */
    if (kind === 'freeText') continue;

    const slotId = String(old.mark?.attrs.slotId ?? '');
    if (!slotId || alive.has(slotId)) continue;
    if (!slotTouched(trs, old.from, old.to)) continue;
    if (slotDeletedWithOutside(trs, old.from, old.to)) continue;
    const oldText = oldState.doc.textBetween(old.from, old.to, '', '');
    if (isFillableSlotEmptyText(oldText)) {
      continue;
    }
    restores.push({
      at: mapping.map(old.from, 1),
      attrs: { ...(old.mark!.attrs as EmrFillableMarkAttrs) },
    });
  }
  if (!restores.length) return null;

  restores.sort((a, b) => b.at - a.at);
  let tr = newState.tr;
  for (const item of restores) {
    const safe = Math.min(Math.max(0, item.at), tr.doc.content.size);
    const $pos = tr.doc.resolve(safe);
    if (!$pos.parent.inlineContent) continue;
    const mark = markType.create({ ...item.attrs });
    tr = tr.insert(safe, newState.schema.text(FILLABLE_EMPTY_CARET, [mark]));
  }
  if (!tr.docChanged) return null;
  tr.setMeta('addToHistory', false);
  return tr;
}
