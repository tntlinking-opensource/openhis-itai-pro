import type { Transaction } from '@tiptap/pm/state';

import { buildFillableNodeInsertJson } from '../core/model';
import { applyFillableCaretPolicy } from '../runtime/caret-policy';

export interface InsertFillableFromDropAttrs {
  editable?: boolean;
  fieldKey: string;
  label: string;
  value?: string;
}

/** 拖拽元数据进正文：插入行内节点 + 光标进槽 */
export function buildFillableInsertJsonFromDrop(attrs: InsertFillableFromDropAttrs) {
  return buildFillableNodeInsertJson({
    kind: 'metadata',
    editable: attrs.editable,
    fieldKey: attrs.fieldKey,
    label: attrs.label,
    placeholder: attrs.label,
    value: attrs.value ?? '',
  });
}

/** Tiptap chain.command：拖拽插入后光标策略 */
export function fillableCaretAfterDropCommand() {
  return ({ tr }: { tr: Transaction }) => {
    applyFillableCaretPolicy(tr, 'after-insert-drop');
    return true;
  };
}
