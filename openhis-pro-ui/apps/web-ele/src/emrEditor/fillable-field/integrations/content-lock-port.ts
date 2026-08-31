import type { Node as PMNode } from '@tiptap/pm/model';

import {
  collectEditableFillableContentRanges,
  resolveFillableAt,
  resolveFillableAtExclusiveEnd,
  selectionTouchesNonFillableText,
} from '../core/resolve';

/** 选区是否完全落在某个允许改写的可填域内（mark 或 atom 节点） */
export function isSelectionInsideEditableFillableContent(
  doc: PMNode,
  from: number,
  to: number,
): boolean {
  const hit = resolveFillableAt(doc, from) ?? resolveFillableAt(doc, Math.max(0, to - 1));
  if (hit?.editable) {
    return from >= hit.innerFrom && to <= hit.innerTo;
  }
  // 槽尾续写位折叠插入：文书锁下仍属可填域
  if (from === to) {
    const exclusive = resolveFillableAtExclusiveEnd(doc, from);
    if (exclusive?.editable) return true;
  }
  return false;
}

/** 选区是否完全落在某一可编辑可填域内 */
export function isRangeInsideEditableFillable(doc: PMNode, from: number, to: number): boolean {
  if (to <= from) return false;
  const hit = resolveFillableAt(doc, from);
  if (!hit?.editable) return false;
  return to <= hit.to && from >= hit.from;
}

/** 文书锁：选区是否碰到域外普通字 */
export function doesSelectionTouchLockedPlainText(doc: PMNode, from: number, to: number): boolean {
  return selectionTouchesNonFillableText(doc, from, to);
}

/** 文书锁：混合选区内可编辑可填域片段 */
export function collectEditableFillableRangesInSelection(doc: PMNode, from: number, to: number) {
  return collectEditableFillableContentRanges(doc, from, to);
}
