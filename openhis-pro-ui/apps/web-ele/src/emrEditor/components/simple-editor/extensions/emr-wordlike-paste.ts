import type { Mark as PMMark, Schema } from '@tiptap/pm/model';

import { Extension } from '@tiptap/core';
import { Fragment, Slice } from '@tiptap/pm/model';
import { Plugin, PluginKey } from '@tiptap/pm/state';

import { resolveFillableAt } from '../../../fillable-field';

const key = new PluginKey('emrWordlikePaste');

/** 纯文本 → 行内 Fragment（换行变 hardBreak）；域外也可作段落切片 */
function plainTextToInlineFragment(schema: Schema, text: string): Fragment {
  const parts = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
  const nodes: import('@tiptap/pm/model').Node[] = [];
  for (let i = 0; i < parts.length; i++) {
    const part = parts[i] ?? '';
    if (part) {
      nodes.push(schema.text(part));
    }
    if (i < parts.length - 1 && schema.nodes.hardBreak) {
      nodes.push(schema.nodes.hardBreak.create());
    }
  }
  return Fragment.from(nodes);
}

function addMarkToFragment(fragment: Fragment, mark: PMMark): Fragment {
  const nodes: import('@tiptap/pm/model').Node[] = [];
  fragment.forEach((child) => {
    if (child.isText) {
      nodes.push(child.mark(mark.addToSet(child.marks)));
      return;
    }
    nodes.push(child);
  });
  return Fragment.from(nodes);
}

function plainTextToBlockSlice(schema: Schema, text: string): Slice {
  const lines = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
  const paraType = schema.nodes.paragraph;
  if (!paraType) {
    return new Slice(plainTextToInlineFragment(schema, text), 0, 0);
  }
  const blocks = lines.map((line) => paraType.create(null, line ? schema.text(line) : null));
  return new Slice(Fragment.from(blocks), 0, 0);
}

/**
 * 从 Word/浏览器 HTML 抽出有限格式：保留粗体/斜体/下划线/换行，去掉色/字号/表格杂项。
 */
function sanitizeHtmlToPlainishText(html: string): string {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const root = doc.body;
  const out: string[] = [];

  const walk = (node: Node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      out.push(node.textContent ?? '');
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;
    const el = node as HTMLElement;
    const tag = el.tagName.toLowerCase();
    if (tag === 'br') {
      out.push('\n');
      return;
    }
    if (tag === 'style' || tag === 'script' || tag === 'meta' || tag === 'link') {
      return;
    }
    const block =
      tag === 'p' ||
      tag === 'div' ||
      tag === 'li' ||
      tag === 'tr' ||
      tag === 'h1' ||
      tag === 'h2' ||
      tag === 'h3' ||
      tag === 'h4';
    if (block && out.length > 0 && !out[out.length - 1]?.endsWith('\n')) {
      out.push('\n');
    }
    el.childNodes.forEach((c) => walk(c));
    if (block) {
      out.push('\n');
    }
  };

  walk(root);
  return out
    .join('')
    .replace(/\u00a0/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

/**
 * Word 化粘贴：
 * - 可填域内：一律纯文本（换行→hardBreak），避免嵌套 HTML/元数据
 * - 域外：有限纯文本段落（不灌 Word 色/字号）；Ctrl+Shift+V 同纯文本
 */
export const EmrWordlikePaste = Extension.create({
  name: 'emrWordlikePaste',

  addProseMirrorPlugins() {
    return [
      new Plugin({
        key,
        props: {
          handlePaste(view, event) {
            if (!view.editable) return false;
            const cd = event.clipboardData;
            if (!cd) return false;

            const { state } = view;
            const at = resolveFillableAt(state.doc, state.selection.from);
            if (at && !at.editable) return true;
            const inside = Boolean(at?.editable);

            const forcePlain = inside || !cd.types.includes('text/html');

            let text = '';
            if (!forcePlain && cd.types.includes('text/html')) {
              const html = cd.getData('text/html') || '';
              text = sanitizeHtmlToPlainishText(html);
            }
            if (!text) {
              text = cd.getData('text/plain') || '';
            }
            if (!text) return false;

            event.preventDefault();
            const next = view.state;
            const hit = resolveFillableAt(next.doc, next.selection.from);
            const inlineFrag = plainTextToInlineFragment(next.schema, text);
            const markedFrag =
              inside && hit?.mark ? addMarkToFragment(inlineFrag, hit.mark) : inlineFrag;
            const slice = inside
              ? new Slice(markedFrag, 0, 0)
              : plainTextToBlockSlice(next.schema, text);
            view.dispatch(next.tr.replaceSelection(slice).scrollIntoView());
            return true;
          },
        },
      }),
    ];
  },
});
