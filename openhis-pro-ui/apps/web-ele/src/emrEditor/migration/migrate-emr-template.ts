import type { JSONContent } from '@tiptap/core';

import { normalizeEmrRootDoc, parseEmrContentToDoc } from '../document-content';
import {
  createFillableSlotId,
  EMR_FILLABLE_MARK,
  isFillableSlotEmptyText,
  stripFillableZwsp,
} from '../components/simple-editor/emr-fillable-json';
import {
  packEmrDocWithSidecar,
  readEmrDocSidecar,
  stripEmrDocSidecar,
} from '../components/simple-editor/emr-doc-sidecar';
import { isPromotableFillableJson } from '../components/simple-editor/emr-data-element-text';

const FILLABLE_NODE_TYPES = new Set([
  'emrDataElement',
  'emrFreeText',
  'emrMetadataChip',
  'emrCheckboxField',
]);

const SUPPORTED_NODE_TYPES = new Set([
  'doc',
  'paragraph',
  'text',
  'hardBreak',
  'heading',
  'bulletList',
  'orderedList',
  'listItem',
  'table',
  'tableRow',
  'tableCell',
  'tableHeader',
  'horizontalRule',
  'emrDataElement',
  'emrFreeText',
  'emrMetadataChip',
  'emrCheckboxField',
  'emrPageBreak',
  'image',
]);

export interface EmrTemplateMigrationReport {
  /** 补全的 slotId 数量 */
  slotIdsAssigned: number;
  /** 删除的尾部空段落数 */
  emptyParagraphsRemoved: number;
  /** 可填域节点总数 */
  fillableNodeCount: number;
  /** 遗留 emrFillable mark 段数（迁移后应为 0） */
  legacyFillableMarkCount: number;
  /** 仍带 content[] 的 atom 可填节点（迁移后应为 0） */
  atomNodesWithContent: number;
  /** 未知节点 type */
  unknownNodeTypes: string[];
  warnings: string[];
}

function isEmptyParagraph(node: JSONContent): boolean {
  if (node.type !== 'paragraph') return false;
  const content = node.content;
  if (!Array.isArray(content) || content.length === 0) return true;
  return content.every((n) => {
    if (n.type === 'hardBreak') return true;
    if (n.type !== 'text') return false;
    return isFillableSlotEmptyText(stripFillableZwsp(String(n.text ?? '')));
  });
}

/** 去掉文档末尾连续空段落 */
export function stripTrailingEmptyParagraphs(doc: JSONContent): JSONContent {
  if (!Array.isArray(doc.content)) return doc;
  const content = [...doc.content];
  let removed = 0;
  while (content.length > 0 && isEmptyParagraph(content[content.length - 1]!)) {
    content.pop();
    removed += 1;
  }
  return removed > 0 ? { ...doc, content } : doc;
}

/** 为缺 slotId 的可填域补稳定 id（路径派生，同文档内可复现） */
export function assignFillableSlotIds(doc: JSONContent): { doc: JSONContent; assigned: number } {
  let assigned = 0;

  const visit = (node: JSONContent, path: string): JSONContent => {
    const type = node.type ?? '';
    if (FILLABLE_NODE_TYPES.has(type)) {
      const attrs = { ...(node.attrs ?? {}) };
      if (!String(attrs.slotId ?? '').trim()) {
        attrs.slotId = createFillableSlotId(path.replace(/\//g, '-'));
        assigned += 1;
      }
      if (isPromotableFillableJson(type, attrs)) {
        const { content: _drop, ...rest } = node;
        return { ...rest, attrs };
      }
      return { ...node, attrs };
    }

    const children = node.content;
    if (!Array.isArray(children)) return node;
    return {
      ...node,
      content: children.map((child, index) => visit(child, `${path}/${index}`)),
    };
  };

  const root = doc.type === 'doc' ? doc : { type: 'doc', content: [doc] };
  return { doc: visit(root, 'doc'), assigned };
}

function scanTemplateIssues(doc: JSONContent): {
  legacyFillableMarkCount: number;
  atomNodesWithContent: number;
  fillableNodeCount: number;
  unknownNodeTypes: string[];
} {
  const unknown = new Set<string>();
  let legacyFillableMarkCount = 0;
  let atomNodesWithContent = 0;
  let fillableNodeCount = 0;

  const walk = (node: JSONContent) => {
    const type = node.type ?? '';
    if (type && type !== 'doc' && !SUPPORTED_NODE_TYPES.has(type)) {
      unknown.add(type);
    }
    if (FILLABLE_NODE_TYPES.has(type)) {
      fillableNodeCount += 1;
      const attrs = node.attrs ?? {};
      if (isPromotableFillableJson(type, attrs) && Array.isArray(node.content) && node.content.length) {
        atomNodesWithContent += 1;
      }
    }
    if (node.type === 'text' && Array.isArray(node.marks)) {
      for (const m of node.marks) {
        if (m.type === EMR_FILLABLE_MARK) legacyFillableMarkCount += 1;
      }
    }
    if (Array.isArray(node.content)) node.content.forEach(walk);
  };

  walk(doc);
  return {
    legacyFillableMarkCount,
    atomNodesWithContent,
    fillableNodeCount,
    unknownNodeTypes: [...unknown].sort(),
  };
}

/**
 * 将存量 EMR 模版 JSON 迁移为当前编辑器（方案 B atom + sidecar）可稳定打开的格式。
 * 幂等：可重复执行。
 */
export function migrateEmrTemplate(raw: unknown): { doc: JSONContent; report: EmrTemplateMigrationReport } {
  const warnings: string[] = [];

  if (!parseEmrContentToDoc(raw)) {
    throw new Error('EMR 模版不是合法的 Tiptap doc JSON');
  }

  let doc = normalizeEmrRootDoc(raw);

  const { doc: withSlots, assigned } = assignFillableSlotIds(doc);
  doc = withSlots;

  const beforeLen = doc.content?.length ?? 0;
  doc = stripTrailingEmptyParagraphs(doc);
  const emptyParagraphsRemoved = beforeLen - (doc.content?.length ?? 0);

  const sidecar = readEmrDocSidecar(doc);
  doc = packEmrDocWithSidecar(stripEmrDocSidecar(doc), sidecar);

  const scan = scanTemplateIssues(doc);
  if (scan.legacyFillableMarkCount > 0) {
    warnings.push(`仍有 ${scan.legacyFillableMarkCount} 处 emrFillable mark，需人工检查`);
  }
  if (scan.atomNodesWithContent > 0) {
    warnings.push(`仍有 ${scan.atomNodesWithContent} 个可填域带 content[]，需人工检查`);
  }
  if (scan.unknownNodeTypes.length > 0) {
    warnings.push(`未知节点：${scan.unknownNodeTypes.join(', ')}`);
  }

  return {
    doc,
    report: {
      slotIdsAssigned: assigned,
      emptyParagraphsRemoved,
      fillableNodeCount: scan.fillableNodeCount,
      legacyFillableMarkCount: scan.legacyFillableMarkCount,
      atomNodesWithContent: scan.atomNodesWithContent,
      unknownNodeTypes: scan.unknownNodeTypes,
      warnings,
    },
  };
}
