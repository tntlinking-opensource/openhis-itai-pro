import type { Node as PMNode } from '@tiptap/pm/model';
import type { Transaction } from '@tiptap/pm/state';

import {
  isFillableHitEmpty,
  selectInsideFillableSlot,
  selectOutsideFillableSlot,
  type EmrFillableHit,
} from '../core/resolve';

/**
 * 可填域光标策略（产品规则单一真源）。
 * 改分页/页眉/打印时不得改此文件，除非产品明确变更交互。
 */
export type FillableCaretPolicy =
  /** 工具栏插入元数据/自由文本：停在 ] 外，继续输入不进槽 */
  | 'after-insert-toolbar'
  /** 拖拽插入：进槽内，可立刻填写 */
  | 'after-insert-drop'
  /** 点击空槽括号 [ 或 ] */
  | 'click-empty-bracket'
  /** 点击空槽高亮区 / 同行吸附空槽 */
  | 'click-inside-empty';

export function applyFillableCaretPolicy(
  tr: Transaction,
  policy: FillableCaretPolicy,
  pos?: number,
): boolean {
  switch (policy) {
    case 'after-insert-toolbar':
      return selectOutsideFillableSlot(tr, pos);
    case 'after-insert-drop':
    case 'click-empty-bracket':
    case 'click-inside-empty':
      return selectInsideFillableSlot(tr, pos);
    default:
      return false;
  }
}

/** Tiptap chain.command 回调：插入后应用光标策略 */
export function fillableCaretAfterInsertCommand(policy: 'toolbar' | 'drop') {
  return ({ tr }: { tr: Transaction }) => {
    applyFillableCaretPolicy(
      tr,
      policy === 'toolbar' ? 'after-insert-toolbar' : 'after-insert-drop',
    );
    return true;
  };
}

/**
 * 空槽同行吸附：仅槽尾之前（结构字/槽内）才进槽。
 * pos >= hit.to 表示已在 ] 外或槽尾，禁止吸回（否则插入元数据后点外面又跳回）。
 */
export function shouldSnapClickToEmptyFillable(
  doc: PMNode,
  pos: number,
  hit: EmrFillableHit,
): boolean {
  if (!hit.editable || !isFillableHitEmpty(doc, hit)) return false;
  return pos < hit.to;
}
