import type { Editor } from '@tiptap/core';
import type { Node as PMNode } from '@tiptap/pm/model';

import { TextSelection } from '@tiptap/pm/state';

import { resolveDataElementWidget } from './emr-data-element-text';
import { isFillableSlotEmptyText } from './emr-fillable-json';
import { collectFillableMarkRanges } from '../../fillable-field';

export interface EmrRequiredViolation {
  /** 文档绝对位置（用于聚焦） */
  pos: number;
  /** 展示名 */
  label: string;
  /** 提示文案 */
  message: string;
}

function asBool(v: unknown, fallback = false): boolean {
  if (v === true || v === '1' || v === 'true') return true;
  if (v === false || v === '0' || v === 'false') return false;
  return fallback;
}

function asString(v: unknown, fallback = ''): string {
  if (v == null) return fallback;
  return String(v);
}

/** 必填未填时的默认提示 */
export function defaultRequiredMessage(label: string): string {
  const name = label.trim() || '该项';
  return `请填写${name}`;
}

/** 控件岛是否视为未填 */
export function isWidgetValueEmpty(attrs: Record<string, unknown>): boolean {
  const widget = resolveDataElementWidget(attrs);
  const raw = asString(attrs.value).trim();
  if (widget === 'checkbox') {
    return raw !== 'true' && raw !== '1';
  }
  return raw.length === 0;
}

/**
 * 收集文档内未填的必填槽（自由文本 mark + 下拉/单选/日期/复选节点）。
 * 元数据只读槽不参与。
 */
export function collectRequiredViolations(doc: PMNode): EmrRequiredViolation[] {
  const out: EmrRequiredViolation[] = [];

  for (const hit of collectFillableMarkRanges(doc)) {
    const attrs = (hit.mark?.attrs ?? {}) as Record<string, unknown>;
    if (!asBool(attrs.required, false)) continue;
    if (attrs.editable === false) continue;
    const text = doc.textBetween(hit.from, hit.to, '\n', '\n');
    if (!isFillableSlotEmptyText(text)) continue;
    const label =
      asString(attrs.labelSnapshot) ||
      asString(attrs.label) ||
      asString(attrs.placeholder) ||
      '填写';
    const custom = asString(attrs.requiredMessage).trim();
    out.push({
      pos: hit.from,
      label,
      message: custom || defaultRequiredMessage(label),
    });
  }

  doc.descendants((node, pos) => {
    if (node.type.name !== 'emrDataElement') return true;
    const attrs = node.attrs as Record<string, unknown>;
    if (!asBool(attrs.required, false)) return true;
    if (attrs.editable === false) return true;
    if (!isWidgetValueEmpty(attrs)) return true;
    const label = asString(attrs.labelSnapshot) || asString(attrs.placeholder) || '填写';
    const custom = asString(attrs.requiredMessage).trim();
    out.push({
      pos,
      label,
      message: custom || defaultRequiredMessage(label),
    });
    return true;
  });

  out.sort((a, b) => a.pos - b.pos);
  return out;
}

/** 聚焦到第一个（或指定）必填违规处 */
export function focusRequiredViolation(editor: Editor, violation: EmrRequiredViolation): void {
  const { pos } = violation;
  const max = editor.state.doc.content.size;
  const safe = Math.min(Math.max(1, pos), Math.max(1, max));
  try {
    editor
      .chain()
      .focus()
      .command(({ tr }) => {
        tr.setSelection(TextSelection.near(tr.doc.resolve(safe)));
        return true;
      })
      .scrollIntoView()
      .run();
  } catch {
    editor.commands.focus();
  }
}

/**
 * 校验必填：有违规则聚焦首项并返回 false。
 * @returns true 表示通过
 */
export function validateRequiredFillables(editor: Editor): {
  ok: boolean;
  violations: EmrRequiredViolation[];
} {
  const violations = collectRequiredViolations(editor.state.doc);
  if (violations.length === 0) {
    return { ok: true, violations };
  }
  const first = violations[0];
  if (first) {
    focusRequiredViolation(editor, first);
  }
  return { ok: false, violations };
}
