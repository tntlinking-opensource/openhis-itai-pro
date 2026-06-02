import type { Editor } from '@tiptap/core';
import type { Node as PMNode } from '@tiptap/pm/model';

import { getDataElementDef } from './data-element-registry';
import { isEmrDataElementWidget } from './data-element-types';

/** 从正文提取的绑定数据元取值（elementId 非空） */
export interface ExtractedDataElementRow {
  elementId: string;
  value: string;
  widget?: string;
}

function effectiveWidgetForNode(attrs: {
  elementId?: string;
  widgetOverride?: string;
}): string | undefined {
  const o = String(attrs.widgetOverride ?? '').trim();
  if (o && isEmrDataElementWidget(o)) return o;
  const id = String(attrs.elementId ?? '').trim();
  if (!id) return undefined;
  return getDataElementDef(id)?.widget;
}

function pushIfBound(out: ExtractedDataElementRow[], row: ExtractedDataElementRow) {
  if (!row.elementId.trim()) return;
  out.push(row);
}

/**
 * 遍历 ProseMirror 文档，收集绑定数据元的取值。
 * - `emrDataElement`：elementId 非空视为绑定项
 * - 兼容旧节点 `emrMetadataChip`
 */
export function extractDataElementsFromDoc(doc: PMNode): ExtractedDataElementRow[] {
  const out: ExtractedDataElementRow[] = [];

  doc.descendants((node) => {
    if (node.type.name === 'emrDataElement') {
      const attrs = node.attrs as Record<string, unknown>;
      pushIfBound(out, {
        elementId: String(attrs.elementId ?? ''),
        value: String(attrs.value ?? ''),
        widget: effectiveWidgetForNode({
          elementId: attrs.elementId as string,
          widgetOverride: attrs.widgetOverride as string,
        }),
      });
      return true;
    }
    if (node.type.name === 'emrMetadataChip') {
      pushIfBound(out, {
        elementId: String(node.attrs.fieldKey ?? ''),
        value: String(node.attrs.value ?? ''),
        widget: 'text',
      });
    }
    return true;
  });

  return out;
}

export function extractDataElementsFromEditor(editor: Editor): ExtractedDataElementRow[] {
  return extractDataElementsFromDoc(editor.state.doc);
}
