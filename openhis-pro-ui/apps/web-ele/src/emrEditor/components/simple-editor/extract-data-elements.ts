import type { Editor } from '@tiptap/core';
import type { Node as PMNode } from '@tiptap/pm/model';

import { getDataElementDef } from './data-element-registry';
import { isEmrDataElementWidget } from './data-element-types';
import { readEmrInlineFieldPlainText } from './emr-data-element-text';
import { stripFillableZwsp } from './emr-fillable-json';
import { collectAllFillableHits } from '../../fillable-field';

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
 * 文本槽来自 emrFillable mark；下拉/勾选仍是节点。
 */
export function extractDataElementsFromDoc(doc: PMNode): ExtractedDataElementRow[] {
  const out: ExtractedDataElementRow[] = [];
  const seen = new Set<string>();

  for (const hit of collectAllFillableHits(doc)) {
    const elementId = hit.mark
      ? String(hit.mark.attrs.elementId || hit.mark.attrs.fieldKey || '')
      : String(hit.node?.attrs.elementId || hit.node?.attrs.fieldKey || '');
    if (!elementId || seen.has(`${hit.from}:${elementId}`)) continue;
    seen.add(`${hit.from}:${elementId}`);
    const value = hit.mark
      ? stripFillableZwsp(doc.textBetween(hit.from, hit.to, '\n', '\n'))
      : hit.node
        ? readEmrInlineFieldPlainText(hit.node)
        : '';
    pushIfBound(out, {
      elementId,
      value,
      widget: effectiveWidgetForNode({
        elementId,
        widgetOverride: String(
          hit.mark?.attrs.widgetOverride ?? hit.node?.attrs.widgetOverride ?? '',
        ),
      }),
    });
  }

  return out;
}

export function extractDataElementsFromEditor(editor: Editor): ExtractedDataElementRow[] {
  return extractDataElementsFromDoc(editor.state.doc);
}
