import type { JSONContent } from '@tiptap/core';
import type { Node as PMNode, Schema } from '@tiptap/pm/model';
import type { Transaction } from '@tiptap/pm/state';

import { Fragment } from '@tiptap/pm/model';

import { getDataElementDef } from './data-element-registry';
import { isEmrDataElementWidget, isTextLikeEmrDataElementWidget } from './data-element-types';
import { isFillableSlotEmptyText } from './emr-fillable-json';

/** Word 化后走 inline 正文的可填节点 */
export const EMR_PM_CONTENT_FIELD_TYPES = new Set([
  'emrDataElement',
  'emrFreeText',
  'emrMetadataChip',
]);

/** 数据元 attrs → 实际控件类型 */
export function resolveDataElementWidget(attrs: Record<string, unknown>): string {
  const o = String(attrs.widgetOverride ?? '').trim();
  if (o && isEmrDataElementWidget(o)) return o;
  const id = String(attrs.elementId ?? '');
  if (id) return getDataElementDef(id)?.widget ?? 'text';
  return 'text';
}

/** 行内可填域是否为 atom（方案 B：值在 attrs，PM 不托管正文） */
export function isAtomFillableNode(node: {
  attrs: Record<string, unknown>;
  type: { name: string };
}): boolean {
  return isTextLikeFillableNode(node);
}

/** 自由文本 / 元数据 / 文本类数据元：atom 节点，字在 attrs.value */
export function isTextLikeFillableNode(node: {
  attrs: Record<string, unknown>;
  type: { name: string };
}): boolean {
  const name = node.type.name;
  if (name === 'emrFreeText' || name === 'emrMetadataChip') return true;
  if (name !== 'emrDataElement') return false;
  return isTextLikeEmrDataElementWidget(resolveDataElementWidget(node.attrs));
}

/** JSON 节点是否应把 value 提升为 inline 正文 */
export function isPromotableFillableJson(type: string, attrs: Record<string, unknown>): boolean {
  if (type === 'emrFreeText' || type === 'emrMetadataChip') return true;
  if (type !== 'emrDataElement') return false;
  return isTextLikeEmrDataElementWidget(resolveDataElementWidget(attrs));
}

/** attrs.value 变化时同步 JSON；atom 节点不写 content */
export function syncFillableJsonNodeContent(node: JSONContent): void {
  const type = node.type ?? '';
  const attrs = node.attrs ?? {};
  if (!isPromotableFillableJson(type, attrs)) return;
  delete node.content;
}

/**
 * 若 pos 落在可填域内部，抬到该域之后，避免元数据嵌套。
 */
export function liftPosOutOfFillableField(doc: PMNode, pos: number): number {
  const safe = Math.min(Math.max(0, pos), doc.content.size);
  const $pos = doc.resolve(safe);
  for (let d = $pos.depth; d > 0; d--) {
    if (EMR_PM_CONTENT_FIELD_TYPES.has($pos.node(d).type.name)) {
      return $pos.after(d);
    }
  }
  return safe;
}

/**
 * 是否整块删除某个可填节点（含只读）。用于放行 Delete，同时仍禁止改只读域内正文。
 */
export function isWholeFillableNodeDelete(
  doc: PMNode,
  from: number,
  to: number,
  insertedSize: number,
): boolean {
  if (insertedSize > 0) {
    return false;
  }
  const node = doc.nodeAt(from);
  if (!node || !EMR_PM_CONTENT_FIELD_TYPES.has(node.type.name)) {
    return false;
  }
  return to === from + node.nodeSize;
}

/**
 * 可填节点纯文本：方案 B 优先 attrs.value。
 */
export function readEmrInlineFieldPlainText(node: PMNode): string {
  if (!EMR_PM_CONTENT_FIELD_TYPES.has(node.type.name)) {
    return '';
  }
  if (isAtomFillableNode(node)) {
    return String(node.attrs.value ?? '');
  }
  const fromContent = node.textContent;
  if (fromContent.length > 0) {
    return fromContent;
  }
  return String(node.attrs.value ?? '');
}

/** @deprecated 使用 {@link readEmrInlineFieldPlainText} */
export function readDataElementPlainText(node: PMNode): string {
  if (node.type.name !== 'emrDataElement') {
    return '';
  }
  return readEmrInlineFieldPlainText(node);
}

/**
 * 打开文档：合并遗留 content 到 attrs.value，不写 content（方案 B）。
 */
export function promoteDataElementValuesToContent(doc: JSONContent): JSONContent {
  const visit = (node: JSONContent): JSONContent => {
    const children = node.content;
    const nextChildren = Array.isArray(children) ? children.map((c) => visit(c)) : undefined;
    const type = node.type ?? '';

    if (!EMR_PM_CONTENT_FIELD_TYPES.has(type)) {
      if (nextChildren) {
        return { ...node, content: nextChildren };
      }
      return node;
    }

    const attrs = { ...(node.attrs ?? {}) };
    if (!isPromotableFillableJson(type, attrs)) {
      return { ...node, attrs, content: [] };
    }
    const value = String(attrs.value ?? '');
    if (value.trim().startsWith('data:image/')) {
      const { content: _c, ...rest } = node;
      return { ...rest, attrs };
    }

    const fromContent = Array.isArray(nextChildren)
      ? flattenJsonInlineText(flattenFillableInlineChildren(nextChildren))
      : '';
    const merged = fromContent || value;
    attrs.value = isFillableSlotEmptyText(merged) ? '' : merged;
    const { content: _drop, ...rest } = node;
    return { ...rest, attrs };
  };

  return visit(doc);
}

/** 可填域内只保留 text/hardBreak；嵌套的元数据/数据元摊成纯文本 */
function flattenFillableInlineChildren(nodes: JSONContent[]): JSONContent[] {
  const out: JSONContent[] = [];
  for (const n of nodes) {
    const t = n.type ?? '';
    if (t === 'text' || t === 'hardBreak') {
      out.push(n);
      continue;
    }
    if (EMR_PM_CONTENT_FIELD_TYPES.has(t)) {
      const nestedPlain =
        (Array.isArray(n.content) && n.content.length ? flattenJsonInlineText(n.content) : '') ||
        String(n.attrs?.value ?? '');
      if (nestedPlain) {
        out.push(...plainTextToJsonInline(nestedPlain));
      }
      continue;
    }
    if (Array.isArray(n.content) && n.content.length) {
      const plain = flattenJsonInlineText(n.content);
      if (plain) {
        out.push(...plainTextToJsonInline(plain));
      }
    }
  }
  return out;
}

/** 扁平化 inline JSON 为纯文本（忽略 marks） */
function flattenJsonInlineText(nodes: JSONContent[]): string {
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
      out += flattenJsonInlineText(n.content);
    }
  }
  return out;
}

/** 纯文本 → JSON inline（换行变 hardBreak） */
export function plainTextToJsonInline(text: string): JSONContent[] {
  const parts = text.split('\n');
  const out: JSONContent[] = [];
  for (let i = 0; i < parts.length; i++) {
    const part = parts[i] ?? '';
    if (part) {
      out.push({ type: 'text', text: part });
    }
    if (i < parts.length - 1) {
      out.push({ type: 'hardBreak' });
    }
  }
  return out;
}

/** 纯文本 → PM Fragment */
function plainTextToFragment(schema: Schema, text: string): Fragment | null {
  if (!text) {
    return null;
  }
  const parts = text.split('\n');
  const nodes: PMNode[] = [];
  for (let i = 0; i < parts.length; i++) {
    const part = parts[i] ?? '';
    if (part) {
      nodes.push(schema.text(part));
    }
    if (i < parts.length - 1 && schema.nodes.hardBreak) {
      nodes.push(schema.nodes.hardBreak.create());
    }
  }
  if (nodes.length === 0) {
    return null;
  }
  return Fragment.from(nodes);
}

/**
 * 在 transaction 内把可填节点改成指定纯文本（同时写 attrs.value 与 inline content）。
 */
export function replaceEmrInlineFieldPlainText(
  tr: Transaction,
  schema: Schema,
  pos: number,
  node: PMNode,
  text: string,
): Transaction {
  if (!EMR_PM_CONTENT_FIELD_TYPES.has(node.type.name)) {
    return tr;
  }
  const content = plainTextToFragment(schema, text);
  const next = node.type.create({ ...node.attrs, value: text }, content);
  return tr.replaceWith(pos, pos + node.nodeSize, next);
}

/** @deprecated 使用 {@link replaceEmrInlineFieldPlainText} */
export function replaceDataElementPlainText(
  tr: Transaction,
  schema: Schema,
  pos: number,
  node: PMNode,
  text: string,
): Transaction {
  return replaceEmrInlineFieldPlainText(tr, schema, pos, node, text);
}

/**
 * 把可填节点 attrs.value 与 textContent 对齐。
 * 必须用 setNodeAttribute（AttrStep），禁止 setNodeMarkup：后者会换节点实例，导致 NodeView/光标乱跳。
 * IME 合成期间跳过，避免中文输入时选区被拽走。
 */
export function appendFillableValueSyncTransaction(
  editor: { view?: { composing?: boolean } } | null | undefined,
  newState: import('@tiptap/pm/state').EditorState,
  typeName: string,
  options?: {
    shouldSync?: (node: PMNode) => boolean;
    skipDataImageValue?: boolean;
  },
): Transaction | null {
  if (editor?.view?.composing) {
    return null;
  }
  const updates: { pos: number; text: string }[] = [];
  newState.doc.descendants((node, pos) => {
    if (node.type.name !== typeName) {
      return;
    }
    if (options?.shouldSync && !options.shouldSync(node)) {
      return;
    }
    const raw = String(node.attrs.value ?? '');
    if (options?.skipDataImageValue && raw.trim().startsWith('data:image/')) {
      return;
    }
    const text = node.textContent;
    if (raw === text) {
      return;
    }
    updates.push({ pos, text });
  });
  if (updates.length === 0) {
    return null;
  }
  let tr = newState.tr;
  for (const u of updates) {
    tr = tr.setNodeAttribute(u.pos, 'value', u.text);
  }
  tr.setMeta('addToHistory', false);
  return tr;
}
