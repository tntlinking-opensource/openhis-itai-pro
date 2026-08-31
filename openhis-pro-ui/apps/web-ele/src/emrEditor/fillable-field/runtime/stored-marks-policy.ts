import type { Mark as PMMark, Node as PMNode } from '@tiptap/pm/model';
import type { EditorState, Transaction } from '@tiptap/pm/state';

import { EMR_FILLABLE_MARK } from '../core/model';
import {
  isEmptyFillableExclusiveEndTrap,
  isExplicitlyOutsideFillable,
  resolveFillableAt,
  resolveFillableAtExclusiveEnd,
} from '../core/resolve';

function hasFillableStoredMark(storedMarks: readonly PMMark[] | null): boolean {
  return Boolean(storedMarks?.some((m) => m.type.name === EMR_FILLABLE_MARK));
}

/**
 * 光标是否在槽尾续写位：仅此处置信 storedMarks 里的 fillable mark。
 * 槽内、槽前普通字、] 外 null/[] 均不算续写。
 */
export function isFillableTailContinuation(
  doc: PMNode,
  head: number,
  storedMarks: readonly PMMark[] | null,
): boolean {
  if (!hasFillableStoredMark(storedMarks) || isExplicitlyOutsideFillable(storedMarks)) {
    return false;
  }
  const end =
    resolveFillableAtExclusiveEnd(doc, head) ?? isEmptyFillableExclusiveEndTrap(doc, head);
  if (!end?.editable || end.to !== head || !end.mark) return false;
  const slotId = String(end.mark.attrs.slotId ?? '');
  return Boolean(
    storedMarks!.some(
      (m) => m.type.name === EMR_FILLABLE_MARK && String(m.attrs.slotId ?? '') === slotId,
    ),
  );
}

/** 域外却带着 fillable storedMarks → 必须清掉，否则新输入会吸进元数据 */
export function shouldClearStaleFillableStoredMarks(
  doc: PMNode,
  head: number,
  storedMarks: readonly PMMark[] | null,
): boolean {
  if (!hasFillableStoredMark(storedMarks)) return false;
  if (isExplicitlyOutsideFillable(storedMarks)) return false;
  if (isFillableTailContinuation(doc, head, storedMarks)) return false;
  if (resolveFillableAt(doc, head)?.editable) return false;
  return true;
}

/** 选区变化后：清掉域外残留的 fillable storedMarks */
export function normalizeStaleFillableStoredMarks(state: EditorState): Transaction | null {
  const { head } = state.selection;
  if (!state.selection.empty) return null;
  if (!shouldClearStaleFillableStoredMarks(state.doc, head, state.storedMarks)) {
    return null;
  }
  const tr = state.tr.setStoredMarks([]);
  tr.setMeta('addToHistory', false);
  return tr;
}
