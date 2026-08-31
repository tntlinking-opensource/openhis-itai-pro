import { buildFillableNodeInsertJson } from '../core/model';
import { fillableCaretAfterInsertCommand } from '../runtime/caret-policy';

export interface InsertEmrFreeTextAttrs {
  label?: string;
  placeholder?: string;
  required?: boolean;
  requiredMessage?: string;
  value?: string;
}

/**
 * 工具栏插入自由文本：写入行内节点 + 光标停在槽外。
 */
export function buildInsertEmrFreeTextChainSteps(attrs?: InsertEmrFreeTextAttrs) {
  const label = attrs?.label ?? attrs?.placeholder ?? '填写';
  const placeholder = attrs?.placeholder ?? label;
  const value = attrs?.value !== undefined ? attrs.value : label;
  return {
    insertContent: buildFillableNodeInsertJson({
      kind: 'freeText',
      label,
      placeholder,
      value,
      required: attrs?.required === true,
      requiredMessage: attrs?.requiredMessage ?? '',
    }),
    afterInsert: fillableCaretAfterInsertCommand('toolbar'),
  };
}
