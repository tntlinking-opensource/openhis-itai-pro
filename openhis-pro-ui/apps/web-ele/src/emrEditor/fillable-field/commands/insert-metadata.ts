import {
  getDataElementDef,
  serializeOptionsFromDef,
} from '../../components/simple-editor/data-element-registry';
import { isTextLikeEmrDataElementWidget } from '../../components/simple-editor/data-element-types';
import { buildFillableNodeInsertJson } from '../core/model';
import { fillableCaretAfterInsertCommand } from '../runtime/caret-policy';

export interface InsertEmrMetadataChipAttrs {
  editable?: boolean;
  fieldKey: string;
  label: string;
  value: string;
  dataType?: string;
}

function resolveMetadataInsertWidget(dataType?: string, fieldKey?: string): string {
  const def = fieldKey ? getDataElementDef(fieldKey) : undefined;
  if (def?.widget) return def.widget;
  const t = String(dataType ?? '')
    .trim()
    .toLowerCase();
  if (t === 'date' || t === 'datetime') return 'date';
  return 'text';
}

function normalizeDatePlaceholder(label: string): string {
  const trimmed = label.trim();
  return trimmed ? `请在这里选择${trimmed}` : '请在这里选择日期';
}

export type InsertEmrMetadataChipResult =
  | { kind: 'dataElement'; content: Record<string, unknown> }
  | {
      kind: 'fillableNode';
      insertContent: Record<string, unknown>;
      afterInsert: ReturnType<typeof fillableCaretAfterInsertCommand>;
    };

/**
 * 工具栏插入元数据：文本类走行内节点；非文本类仍插 emrDataElement 控件岛。
 */
export function resolveInsertEmrMetadataChip(
  attrs: InsertEmrMetadataChipAttrs,
): InsertEmrMetadataChipResult {
  const widget = resolveMetadataInsertWidget(attrs.dataType, attrs.fieldKey);
  if (!isTextLikeEmrDataElementWidget(widget)) {
    const def = getDataElementDef(attrs.fieldKey);
    const optionsJson = def ? serializeOptionsFromDef(def) : '[]';
    const label = attrs.label || attrs.fieldKey;
    return {
      kind: 'dataElement',
      content: {
        type: 'emrDataElement',
        attrs: {
          editable: attrs.editable !== false,
          elementId: attrs.fieldKey,
          labelSnapshot: label,
          optionsJson,
          placeholder: widget === 'date' ? normalizeDatePlaceholder(label) : label || '',
          value: attrs.value ?? '',
          widgetOverride: widget === 'text' ? '' : widget,
        },
      },
    };
  }
  return {
    kind: 'fillableNode',
    insertContent: buildFillableNodeInsertJson({
      kind: 'metadata',
      editable: attrs.editable,
      fieldKey: attrs.fieldKey,
      label: attrs.label,
      placeholder: attrs.label,
      value: attrs.value ?? '',
    }),
    afterInsert: fillableCaretAfterInsertCommand('toolbar'),
  };
}
