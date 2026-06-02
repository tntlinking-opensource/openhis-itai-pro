import type { JSONContent } from '@tiptap/core';

import { defaultEmrEditorDoc } from './components/simple-editor/editor-defaults';

/** 将接口返回的 content 解析为 Tiptap 节点 */
export function parseEmrContentToDoc(raw: unknown): JSONContent | null {
  if (raw === undefined || raw === null) return null;
  if (typeof raw === 'object' && raw !== null) {
    if (!('type' in raw)) return null;
    return raw as JSONContent;
  }
  if (typeof raw === 'string') {
    const s = raw.trim();
    if (!s) return null;
    try {
      const parsed = JSON.parse(s) as unknown;
      if (typeof parsed !== 'object' || parsed === null || !('type' in parsed)) return null;
      return parsed as JSONContent;
    } catch {
      return null;
    }
  }
  return null;
}

/** 规范为 Tiptap 根节点 `doc` */
export function normalizeEmrRootDoc(raw: unknown): JSONContent {
  const node = parseEmrContentToDoc(raw);
  if (!node) {
    return structuredClone(defaultEmrEditorDoc);
  }
  if (node.type === 'doc') {
    return node;
  }
  return {
    type: 'doc',
    content: [node],
  };
}
