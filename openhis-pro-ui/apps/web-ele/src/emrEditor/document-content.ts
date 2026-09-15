import type { JSONContent } from '@tiptap/core';

import { defaultEmrEditorDoc } from './components/simple-editor/editor-defaults';
import { promoteDataElementValuesToContent } from './components/simple-editor/emr-data-element-text';
import { normalizeFillableDocForEdit } from './components/simple-editor/emr-fillable-json';

/**
 * 剔除空 text 节点。ProseMirror 拒收 `{"type":"text","text":""}`，
 * 种子/脏模板若带此类节点，setContent 会失败并表现为医生站「套模板空白」。
 */
export function scrubEmptyTextNodes(node: unknown): unknown {
  if (Array.isArray(node)) {
    return node
      .filter(
        (item) =>
          !(
            item &&
            typeof item === 'object' &&
            (item as JSONContent).type === 'text' &&
            (item as { text?: string }).text === ''
          ),
      )
      .map((item) => scrubEmptyTextNodes(item));
  }
  if (node && typeof node === 'object') {
    const n = node as JSONContent & { content?: unknown };
    if (Array.isArray(n.content)) {
      return { ...n, content: scrubEmptyTextNodes(n.content) };
    }
  }
  return node;
}

/** 将接口返回的 content 解析为 Tiptap 节点 */
export function parseEmrContentToDoc(raw: unknown): JSONContent | null {
  if (raw === undefined || raw === null) return null;
  if (typeof raw === 'object' && raw !== null) {
    if (!('type' in raw)) return null;
    return scrubEmptyTextNodes(raw) as JSONContent;
  }
  if (typeof raw === 'string') {
    const s = raw.trim();
    if (!s) return null;
    try {
      const parsed = JSON.parse(s) as unknown;
      if (typeof parsed !== 'object' || parsed === null || !('type' in parsed)) return null;
      return scrubEmptyTextNodes(parsed) as JSONContent;
    } catch {
      return null;
    }
  }
  return null;
}

/**
 * 去掉文首连续硬分页（迁移/误插常见）。
 * 否则编辑器软分页会先撑出空白第 1 页，正文从第 2 页起，而打印仍可能只有 1 页。
 */
export function stripLeadingEmrPageBreaks(doc: JSONContent): JSONContent {
  if (doc.type !== 'doc' || !Array.isArray(doc.content) || doc.content.length === 0) {
    return doc;
  }
  let i = 0;
  while (i < doc.content.length && doc.content[i]?.type === 'emrPageBreak') {
    i += 1;
  }
  if (i === 0) return doc;
  return { ...doc, content: doc.content.slice(i) };
}

/**
 * 去掉正文顶层硬分页符。
 * 历史入院记录模版用章节硬分页模拟 Word 多页；软分页上线后会把半页撑成空白。
 * 仅套模版时调用；已保存文书保留医生 Ctrl+Enter 插入的分页符。
 */
export function stripEmrPageBreaks(doc: JSONContent): JSONContent {
  if (doc.type !== 'doc' || !Array.isArray(doc.content)) {
    return doc;
  }
  const content = doc.content.filter((node) => node?.type !== 'emrPageBreak');
  if (content.length === doc.content.length) {
    return doc;
  }
  return { ...doc, content };
}

/** 规范为 Tiptap 根节点 `doc`（含空 text 节点清洗） */
export function normalizeEmrRootDoc(raw: unknown): JSONContent {
  const node = parseEmrContentToDoc(raw);
  if (!node) {
    return structuredClone(defaultEmrEditorDoc);
  }
  if (node.type === 'doc') {
    return stripLeadingEmrPageBreaks(
      normalizeFillableDocForEdit(promoteDataElementValuesToContent(node)),
    );
  }
  return stripLeadingEmrPageBreaks(
    normalizeFillableDocForEdit(
      promoteDataElementValuesToContent({
        type: 'doc',
        content: [node],
      }),
    ),
  );
}
