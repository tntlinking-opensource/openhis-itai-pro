import type { DocumentTypeDto } from '#/api/emr/document-type';
import type { TemplateDto } from '#/api/emr/template';

import { formatTemplateScopeTag } from '#/views/basic/emr-template/emr-template-scope.constants';

/** 病历模板树节点（文书类型为父、模板为叶） */
export interface EmrTemplateTreeNode {
  /** Element Plus Tree `node-key`，全局唯一 */
  nodeKey: string;
  label: string;
  /** 文书类型编码，父节点有值 */
  documentTypeCode?: string;
  /** 模板主键字符串，仅叶子节点 */
  templateId?: string;
  /** 是否为文书类型父节点 */
  isDocumentType?: boolean;
  /** 叶子节点携带列表 DTO，便于扩展展示 */
  template?: TemplateDto;
  children?: EmrTemplateTreeNode[];
}

function buildTemplateLeafLabel(tpl: TemplateDto): string {
  const base =
    tpl.name ??
    tpl.code ??
    (tpl.id === null || tpl.id === undefined ? '未命名模板' : String(tpl.id));
  const scopeTag = formatTemplateScopeTag(tpl.scopeLevel);
  return scopeTag ? `${base} [${scopeTag}]` : base;
}

function toLeaf(tpl: TemplateDto, leafIndex: number): EmrTemplateTreeNode {
  const idStr =
    tpl.id === null || tpl.id === undefined
      ? `noid-${(tpl.code ?? 'tpl').replaceAll(/\W/g, '')}-${leafIndex}`
      : String(tpl.id);
  return {
    nodeKey: `tpl:${idStr}`,
    label: buildTemplateLeafLabel(tpl),
    templateId: tpl.id === null || tpl.id === undefined ? undefined : String(tpl.id),
    isDocumentType: false,
    template: tpl,
  };
}

function sortTemplates(list: TemplateDto[]) {
  return [...list].toSorted((a, b) =>
    (a.name ?? '').localeCompare(b.name ?? '', 'zh-CN', { numeric: true }),
  );
}

/**
 * 以文书类型为父节点、模板列表为子节点组装树；未匹配到类型的模板归入「未归类」。
 */
export function buildEmrTemplateTree(
  documentTypes: DocumentTypeDto[],
  templates: TemplateDto[],
): EmrTemplateTreeNode[] {
  const byCode = new Map<string, TemplateDto[]>();
  for (const t of templates) {
    const code = (t.documentTypeCode ?? '').trim();
    const bucket = byCode.get(code) ?? [];
    bucket.push(t);
    byCode.set(code, bucket);
  }

  const sortedTypes = [...documentTypes].toSorted((a, b) => {
    const order = (a.sortOrder ?? 0) - (b.sortOrder ?? 0);
    if (order !== 0) return order;
    return (a.name ?? '').localeCompare(b.name ?? '', 'zh-CN', { numeric: true });
  });

  const nodes: EmrTemplateTreeNode[] = [];

  sortedTypes.forEach((dt, index) => {
    const code = (dt.code ?? '').trim();
    const rawList = byCode.get(code) ?? [];
    byCode.delete(code);
    const children = sortTemplates(rawList).map((tpl, i) => toLeaf(tpl, i));
    const typeId = dt.id === null || dt.id === undefined ? `idx-${index}` : String(dt.id);
    nodes.push({
      nodeKey: `dtype:${typeId}:${code || 'empty'}`,
      label: dt.name?.trim() || code || '文书类型',
      documentTypeCode: code || undefined,
      isDocumentType: true,
      children,
    });
  });

  const orphanLists = [...byCode.values()].filter((l) => l.length > 0);
  if (orphanLists.length > 0) {
    const merged = sortTemplates(orphanLists.flat());
    nodes.push({
      nodeKey: 'dtype:__orphan__',
      label: '未归类',
      isDocumentType: true,
      children: merged.map((tpl, i) => toLeaf(tpl, i)),
    });
  }

  return nodes;
}
