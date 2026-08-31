import type { JSONContent } from '@tiptap/core';

import { isPromotableFillableJson } from './emr-data-element-text';

/** 编辑器内连续正文：可填域是 mark，不是行内节点 */
export const EMR_FILLABLE_MARK = 'emrFillable';

/** 方案 B：atom 节点，值仅存 attrs.value */
export const EMR_FILLABLE_USE_INLINE_NODES = true;
export const EMR_FILLABLE_USE_ATOM_NODES = true;

/**
 * 空槽占位：mark 不能包空字符串。
 * 用 ZWSP（零宽）避免「[」后出现可见空白；caret 靠 CSS caret-color + 空槽聚焦态保证可见。
 * 曾用 NBSP 撑 caret，会留下一段空白，存量空槽仍按空处理。
 */
export const FILLABLE_ZWSP = '\u200B';
export const FILLABLE_EMPTY_CARET = FILLABLE_ZWSP;
/** 旧版空槽占位（可见空格），读入时当空槽、写入时剥掉 */
export const FILLABLE_EMPTY_CARET_LEGACY = '\u00A0';

export type EmrFillableKind = 'dataElement' | 'freeText' | 'metadata';

export interface EmrFillableMarkAttrs {
  editable: boolean;
  elementId: string;
  fieldKey: string;
  kind: EmrFillableKind;
  label: string;
  labelSnapshot: string;
  optionsJson: string;
  placeholder: string;
  /** 文书保存前必须填写 */
  required: boolean;
  /** 必填失败提示；空则默认「请填写{名称}」 */
  requiredMessage: string;
  slotId: string;
  widgetOverride: string;
}

function asRecord(v: unknown): Record<string, unknown> {
  if (v && typeof v === 'object') {
    return v as Record<string, unknown>;
  }
  return {};
}

function asString(v: unknown, fallback = ''): string {
  if (v == null) return fallback;
  return String(v);
}

function asBool(v: unknown, fallback = true): boolean {
  if (v === false || v === '0' || v === 'false') return false;
  if (v === true || v === '1' || v === 'true') return true;
  return fallback;
}

/** 生成一次后随 JSON 持久化，避免相邻空槽被 ProseMirror 合并 */
export function createFillableSlotId(path: string): string {
  return `f-${path}`;
}

export function stripFillableZwsp(text: string): string {
  return text.replaceAll(FILLABLE_ZWSP, '');
}

export function isFillableSlotEmptyText(text: string): boolean {
  const s = stripFillableZwsp(text).replaceAll(FILLABLE_EMPTY_CARET_LEGACY, '');
  return s.length === 0;
}

/** 输入后清掉空槽占位（ZWSP / 旧 NBSP） */
export function stripFillablePlaceholders(text: string): string {
  return stripFillableZwsp(text).replaceAll(FILLABLE_EMPTY_CARET_LEGACY, '');
}

export function normalizeFillableMarkAttrs(
  raw: Record<string, unknown>,
  path: string,
): EmrFillableMarkAttrs {
  const kind = (raw.kind as EmrFillableKind) || 'freeText';
  return {
    kind,
    slotId: asString(raw.slotId) || createFillableSlotId(path),
    editable: asBool(raw.editable, true),
    label: asString(raw.label),
    placeholder: asString(raw.placeholder || raw.label, '填写'),
    fieldKey: asString(raw.fieldKey),
    elementId: asString(raw.elementId),
    labelSnapshot: asString(raw.labelSnapshot),
    widgetOverride: asString(raw.widgetOverride),
    optionsJson: asString(raw.optionsJson, '[]'),
    required: asBool(raw.required, false),
    requiredMessage: asString(raw.requiredMessage),
  };
}

function nodeTypeToKind(type: string): EmrFillableKind {
  if (type === 'emrMetadataChip') return 'metadata';
  if (type === 'emrDataElement') return 'dataElement';
  return 'freeText';
}

function kindToNodeType(kind: EmrFillableKind): string {
  if (kind === 'metadata') return 'emrMetadataChip';
  if (kind === 'dataElement') return 'emrDataElement';
  return 'emrFreeText';
}

function flattenInlineText(nodes: JSONContent[] | undefined): string {
  if (!Array.isArray(nodes)) return '';
  let out = '';
  for (const n of nodes) {
    if (n.type === 'text') {
      out += String(n.text ?? '');
      continue;
    }
    if (n.type === 'hardBreak') {
      out += '\n';
      continue;
    }
    if (Array.isArray(n.content)) {
      out += flattenInlineText(n.content);
    }
  }
  return out;
}

/** 纯文本 → 带同一 fillable mark 的 inline JSON */
export function plainTextToFillableMarkJson(
  text: string,
  attrs: EmrFillableMarkAttrs,
): JSONContent[] {
  const body = text.length > 0 ? text : FILLABLE_EMPTY_CARET;
  const parts = body.split('\n');
  const out: JSONContent[] = [];
  const mark = { type: EMR_FILLABLE_MARK, attrs: { ...attrs } };
  for (let i = 0; i < parts.length; i++) {
    const part = parts[i] ?? '';
    if (part) {
      out.push({ type: 'text', text: part, marks: [mark] });
    } else if (i === 0 && parts.length === 1) {
      out.push({ type: 'text', text: FILLABLE_EMPTY_CARET, marks: [mark] });
    }
    if (i < parts.length - 1) {
      out.push({ type: 'hardBreak' });
    }
  }
  if (out.length === 0) {
    out.push({ type: 'text', text: FILLABLE_EMPTY_CARET, marks: [mark] });
  }
  return out;
}

function fillableMarkFromNode(
  type: string,
  attrs: Record<string, unknown>,
  path: string,
): EmrFillableMarkAttrs {
  const kind = nodeTypeToKind(type);
  return normalizeFillableMarkAttrs(
    {
      kind,
      slotId: attrs.slotId,
      editable: attrs.editable,
      label: attrs.label ?? attrs.labelSnapshot ?? attrs.placeholder,
      placeholder: attrs.placeholder ?? attrs.label ?? attrs.labelSnapshot,
      fieldKey: attrs.fieldKey,
      elementId: attrs.elementId,
      labelSnapshot: attrs.labelSnapshot,
      widgetOverride: attrs.widgetOverride,
      optionsJson: attrs.optionsJson,
      required: attrs.required,
      requiredMessage: attrs.requiredMessage,
    },
    path,
  );
}

function isDataImageValue(attrs: Record<string, unknown>): boolean {
  return asString(attrs.value).trim().startsWith('data:image/');
}

function flattenNode(node: JSONContent, path: string): JSONContent | JSONContent[] {
  const type = node.type ?? '';
  const attrs = asRecord(node.attrs);
  if (isPromotableFillableJson(type, attrs) && !isDataImageValue(attrs)) {
    const markAttrs = fillableMarkFromNode(type, attrs, path);
    const fromContent = flattenInlineText(node.content);
    const value = fromContent || asString(attrs.value);
    return plainTextToFillableMarkJson(stripFillableZwsp(value), markAttrs);
  }
  const children = node.content;
  if (!Array.isArray(children)) {
    return node;
  }
  const next: JSONContent[] = [];
  for (let i = 0; i < children.length; i++) {
    const child = children[i];
    if (!child) continue;
    const flattened = flattenNode(child, `${path}.${i}`);
    if (Array.isArray(flattened)) {
      next.push(...flattened);
    } else {
      next.push(flattened);
    }
  }
  return { ...node, content: next };
}

/**
 * 打开文档：把可填节点摊成 mark。
 * 下拉/单选/日期/勾选仍保留节点。
 * @deprecated 方案 A 使用 {@link normalizeFillableDocForEdit}
 */
export function flattenFillableNodesToMarks(doc: JSONContent): JSONContent {
  const flattened = flattenNode(doc, '0');
  if (Array.isArray(flattened)) {
    return { type: 'doc', content: flattened };
  }
  return flattened;
}

function readFillableMark(
  node: JSONContent,
): { attrs: EmrFillableMarkAttrs; rest: JSONContent['marks'] } | null {
  const marks = node.marks;
  if (!Array.isArray(marks)) return null;
  const found = marks.find((m) => m.type === EMR_FILLABLE_MARK);
  if (!found) return null;
  const rest = marks.filter((m) => m.type !== EMR_FILLABLE_MARK);
  return {
    attrs: normalizeFillableMarkAttrs(asRecord(found.attrs), asString(found.attrs?.slotId, 'x')),
    rest: rest.length ? rest : undefined,
  };
}

function markAttrsToNodeAttrs(attrs: EmrFillableMarkAttrs, value: string): Record<string, unknown> {
  if (attrs.kind === 'metadata') {
    return {
      slotId: attrs.slotId,
      fieldKey: attrs.fieldKey,
      label: attrs.label || attrs.placeholder,
      value,
      editable: attrs.editable,
    };
  }
  if (attrs.kind === 'dataElement') {
    return {
      slotId: attrs.slotId,
      elementId: attrs.elementId,
      labelSnapshot: attrs.labelSnapshot || attrs.label,
      value,
      placeholder: attrs.placeholder || attrs.label,
      widgetOverride: attrs.widgetOverride,
      optionsJson: attrs.optionsJson || '[]',
      editable: attrs.editable,
      required: attrs.required,
      requiredMessage: attrs.requiredMessage,
    };
  }
  return {
    slotId: attrs.slotId,
    label: attrs.label || attrs.placeholder || '填写',
    placeholder: attrs.placeholder || attrs.label || '填写',
    value,
    required: attrs.required,
    requiredMessage: attrs.requiredMessage,
  };
}

function serializeInline(children: JSONContent[] | undefined): JSONContent[] {
  if (!Array.isArray(children)) return [];
  const out: JSONContent[] = [];
  let buf: { attrs: EmrFillableMarkAttrs; pieces: JSONContent[] } | null = null;

  const flush = () => {
    if (!buf) return;
    const raw = stripFillableZwsp(flattenInlineText(buf.pieces));
    const value = isFillableSlotEmptyText(raw) ? '' : raw;
    out.push({
      type: kindToNodeType(buf.attrs.kind),
      attrs: markAttrsToNodeAttrs(buf.attrs, value),
    });
    buf = null;
  };

  for (const child of children) {
    if (child.type === 'text') {
      const hit = readFillableMark(child);
      if (hit) {
        const nextChild: JSONContent = { type: 'text', text: child.text };
        if (hit.rest) {
          nextChild.marks = hit.rest;
        }
        if (buf && buf.attrs.slotId === hit.attrs.slotId) {
          buf.pieces.push(nextChild);
        } else {
          flush();
          buf = { attrs: hit.attrs, pieces: [nextChild] };
        }
        continue;
      }
    }
    if (child.type === 'hardBreak' && buf) {
      buf.pieces.push(child);
      continue;
    }
    flush();
    if (Array.isArray(child.content)) {
      out.push({ ...child, content: serializeInline(child.content) });
    } else {
      out.push(child);
    }
  }
  flush();
  return out;
}

function readFillableMarkFromText(node: JSONContent): EmrFillableMarkAttrs | null {
  const hit = readFillableMark(node);
  return hit?.attrs ?? null;
}

/**
 * 打开文档（方案 B）：mark → atom 节点；存量 content 提升为 attrs.value。
 */
export function flattenFillableMarksToNodes(doc: JSONContent): JSONContent {
  if (!doc || typeof doc !== 'object') {
    return { type: 'doc', content: [] };
  }
  const visit = (node: JSONContent): JSONContent => {
    const children = node.content;
    if (!Array.isArray(children)) {
      return node;
    }

    const next: JSONContent[] = [];
    let buf: { attrs: EmrFillableMarkAttrs; pieces: JSONContent[] } | null = null;

    const flush = () => {
      if (!buf) return;
      const raw = stripFillableZwsp(flattenInlineText(buf.pieces));
      const value = isFillableSlotEmptyText(raw) ? '' : raw;
      next.push({
        type: kindToNodeType(buf.attrs.kind),
        attrs: markAttrsToNodeAttrs(buf.attrs, value),
      });
      buf = null;
    };

    for (const child of children) {
      if (child.type === 'text') {
        const attrs = readFillableMarkFromText(child);
        if (attrs) {
          const nextChild: JSONContent = { type: 'text', text: child.text };
          const rest = child.marks?.filter((m) => m.type !== EMR_FILLABLE_MARK);
          if (rest?.length) {
            nextChild.marks = rest;
          }
          if (buf && buf.attrs.slotId === attrs.slotId) {
            buf.pieces.push(nextChild);
          } else {
            flush();
            buf = { attrs, pieces: [nextChild] };
          }
          continue;
        }
      }
      if (child.type === 'hardBreak' && buf) {
        buf.pieces.push(child);
        continue;
      }
      flush();
      if (Array.isArray(child.content)) {
        next.push(visit(child));
      } else {
        next.push(child);
      }
    }
    flush();
    return { ...node, content: next };
  };

  if (doc.type === 'doc' || !('type' in doc)) {
    return visit(doc.type === 'doc' ? doc : { type: 'doc', content: [doc as JSONContent] });
  }
  return visit(doc);
}

/**
 * 方案 B：把节点 content 文本合并进 attrs.value，并去掉 content。
 */
export function hoistFillableAtomContent(doc: JSONContent): JSONContent {
  const visit = (node: JSONContent): JSONContent => {
    const children = node.content;
    const nextChildren = Array.isArray(children) ? children.map((c) => visit(c)) : undefined;
    const type = node.type ?? '';

    if (isPromotableFillableJson(type, asRecord(node.attrs))) {
      const attrs = { ...(node.attrs ?? {}) };
      const fromContent = stripFillableZwsp(flattenInlineText(nextChildren));
      const legacy = stripFillableZwsp(String(attrs.value ?? ''));
      const merged = fromContent || legacy;
      attrs.value = isFillableSlotEmptyText(merged) ? '' : merged;
      const { content: _drop, ...rest } = node;
      return { ...rest, attrs };
    }

    if (nextChildren) {
      return { ...node, content: nextChildren };
    }
    return node;
  };

  if (!doc || typeof doc !== 'object') {
    return { type: 'doc', content: [] };
  }
  return visit(doc);
}

/**
 * 打开文档：方案 B — mark 迁移 + content 提升为 attrs.value。
 */
export function normalizeFillableDocForEdit(doc: JSONContent): JSONContent {
  return hoistFillableAtomContent(flattenFillableMarksToNodes(doc));
}

/**
 * 保存/回传父组件：mark 写回节点，打印与存量抽取仍认节点。
 */
export function serializeFillableMarksToNodes(doc: JSONContent): JSONContent {
  if (!EMR_FILLABLE_USE_INLINE_NODES) {
    return serializeFillableMarksToNodesLegacy(doc);
  }
  return syncFillableNodeValuesFromContent(doc);
}

/** 存盘：确保 attrs.value 规范，去掉 atom 节点 content */
export function syncFillableNodeValuesFromContent(doc: JSONContent): JSONContent {
  return hoistFillableAtomContent(doc);
}

function serializeFillableMarksToNodesLegacy(doc: JSONContent): JSONContent {
  if (!doc || typeof doc !== 'object') {
    return { type: 'doc', content: [] };
  }
  if (!Array.isArray(doc.content)) {
    return doc;
  }
  return { ...doc, content: serializeInline(doc.content) };
}

/** 插入命令 / 拖拽：生成 atom 可填节点 JSON（方案 B） */
export function buildFillableNodeInsertJson(input: {
  editable?: boolean;
  elementId?: string;
  fieldKey?: string;
  kind: EmrFillableKind;
  label?: string;
  labelSnapshot?: string;
  optionsJson?: string;
  placeholder?: string;
  required?: boolean;
  requiredMessage?: string;
  value?: string;
  widgetOverride?: string;
}): JSONContent {
  const label = input.label || input.labelSnapshot || input.placeholder || '填写';
  const attrs = normalizeFillableMarkAttrs(
    {
      kind: input.kind,
      editable: input.editable,
      label,
      placeholder: input.placeholder || label,
      fieldKey: input.fieldKey,
      elementId: input.elementId,
      labelSnapshot: input.labelSnapshot || label,
      widgetOverride: input.widgetOverride,
      optionsJson: input.optionsJson,
      required: input.required,
      requiredMessage: input.requiredMessage,
    },
    `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
  );
  const value = stripFillableZwsp(String(input.value ?? ''));
  const normalized = isFillableSlotEmptyText(value) ? '' : value;
  return {
    type: kindToNodeType(attrs.kind),
    attrs: markAttrsToNodeAttrs(attrs, normalized),
  };
}

/** @deprecated 方案 A 请用 {@link buildFillableNodeInsertJson} */
export function buildFillableInsertJson(input: {
  editable?: boolean;
  elementId?: string;
  fieldKey?: string;
  kind: EmrFillableKind;
  label?: string;
  labelSnapshot?: string;
  optionsJson?: string;
  placeholder?: string;
  required?: boolean;
  requiredMessage?: string;
  value?: string;
  widgetOverride?: string;
}): JSONContent[] {
  const label = input.label || input.labelSnapshot || input.placeholder || '填写';
  const attrs = normalizeFillableMarkAttrs(
    {
      kind: input.kind,
      editable: input.editable,
      label,
      placeholder: input.placeholder || label,
      fieldKey: input.fieldKey,
      elementId: input.elementId,
      labelSnapshot: input.labelSnapshot || label,
      widgetOverride: input.widgetOverride,
      optionsJson: input.optionsJson,
      required: input.required,
      requiredMessage: input.requiredMessage,
    },
    `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
  );
  return plainTextToFillableMarkJson(stripFillableZwsp(String(input.value ?? '')), attrs);
}
