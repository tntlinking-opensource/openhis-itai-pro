import type { DocumentSummaryDto } from '#/api/emr/document';
import type { DocumentTypeDto } from '#/api/emr/document-type';
import type { TemplateDto } from '#/api/emr/template';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { formatTemplateScopeTag } from '#/views/basic/emr-template/emr-template-scope.constants';

/** 病历侧栏树节点：目录分类 → 模板 → 该模板建出的病历 */
export interface EmrTemplateTreeNode {
  /** Element Plus Tree `node-key`，全局唯一 */
  nodeKey: string;
  label: string;
  /** 文书类型编码（模板/病历节点携带；分类节点不带） */
  documentTypeCode?: string;
  /** 文书类型中文名（模板节点携带，供新建 Tab 标题） */
  documentTypeName?: string;
  /** 目录分类编码 */
  categoryCode?: string;
  /** 模板主键字符串（模板节点） */
  templateId?: string;
  /** 是否为目录分类父节点（门诊病历/住院病历/…） */
  isDocumentType?: boolean;
  /** 是否为分组节点（如「其他病历」，不可点选） */
  isGroup?: boolean;
  /** 是否为模板节点（点选 → 新建） */
  isTemplate?: boolean;
  /** 是否为已有病历节点（点选 → 打开） */
  isDocument?: boolean;
  /** 模板列表 DTO */
  template?: TemplateDto;
  /** 病历摘要 DTO */
  document?: DocumentSummaryDto;
  children?: EmrTemplateTreeNode[];
}

/** 侧栏目录五类（顺序即展示顺序） */
export const EMR_DIRECTORY_CATEGORIES = [
  { code: 'OUTPATIENT', label: '门诊病历' },
  { code: 'INPATIENT', label: '住院病历' },
  { code: 'NOTICE', label: '告知书' },
  { code: 'SURGERY', label: '手术病历' },
  { code: 'OTHER', label: '其他' },
] as const;

const CATEGORY_LABEL_BY_CODE: Record<string, string> = Object.fromEntries(
  EMR_DIRECTORY_CATEGORIES.map((c) => [c.code, c.label]),
);

function formatUpdatedAt(raw?: string): string {
  const s = (raw ?? '').trim();
  if (!s) return '';
  return s.includes('T') ? s.replace('T', ' ').slice(0, 16) : s.slice(0, 16);
}

export function formatDocumentTreeTime(doc: DocumentSummaryDto): string {
  return formatUpdatedAt(doc.updatedAt ?? doc.createdAt);
}

export function formatDocumentTreeDept(doc: DocumentSummaryDto): string {
  return (doc.deptName ?? '').trim();
}

export function formatDocumentTreeSaver(doc: DocumentSummaryDto): string {
  return (doc.savedByName ?? '').trim();
}

/** 书写文书节点：(时间) 科室 保存人（筛选/title 用；树内用卡片分开展示） */
export function buildDocumentTreeLabel(
  doc: DocumentSummaryDto,
  options?: { currentEncounterId?: string },
): string {
  const dept = formatDocumentTreeDept(doc);
  const saver = formatDocumentTreeSaver(doc);
  const time = formatDocumentTreeTime(doc);
  const parts: string[] = [];
  if (time) parts.push(`(${time})`);
  if (dept) parts.push(dept);
  if (saver) parts.push(saver);
  const current = preserveApiIdForRequest(options?.currentEncounterId) ?? '';
  const docEnc = preserveApiIdForRequest(doc.encounterId) ?? '';
  if (current && docEnc && docEnc !== current) {
    parts.push('[关联]');
  }
  return parts.length > 0 ? parts.join(' ') : '未保存';
}

function buildTemplateLeafLabel(tpl: TemplateDto): string {
  const base =
    tpl.name ??
    tpl.code ??
    (tpl.id === null || tpl.id === undefined ? '未命名模板' : String(tpl.id));
  const scopeTag = formatTemplateScopeTag(tpl.scopeLevel);
  return scopeTag ? `${base} [${scopeTag}]` : base;
}

function toDocumentLeaf(doc: DocumentSummaryDto): EmrTemplateTreeNode {
  const idStr = preserveApiIdForRequest(doc.id) ?? `noid-${String(doc.updatedAt ?? '')}`;
  return {
    nodeKey: `doc:${idStr}`,
    label: buildDocumentTreeLabel(doc),
    documentTypeCode: (doc.documentTypeCode ?? '').trim() || undefined,
    isDocument: true,
    document: doc,
  };
}

function toTemplateNode(
  tpl: TemplateDto,
  leafIndex: number,
  docs: DocumentSummaryDto[],
  documentTypeName?: string,
): EmrTemplateTreeNode {
  const codeText = String(tpl.code ?? 'tpl');
  const idStr =
    tpl.id === null || tpl.id === undefined
      ? `noid-${codeText.replace(/\W/g, '')}-${leafIndex}`
      : String(tpl.id);
  const documentTypeCode = String(tpl.documentTypeCode ?? '').trim() || undefined;
  const children = sortDocuments(docs).map((d) => toDocumentLeaf(d));
  return {
    nodeKey: `tpl:${idStr}`,
    label: buildTemplateLeafLabel(tpl),
    documentTypeCode,
    documentTypeName: documentTypeName?.trim() || undefined,
    templateId: tpl.id === null || tpl.id === undefined ? undefined : String(tpl.id),
    isTemplate: true,
    template: tpl,
    children: children.length > 0 ? children : undefined,
  };
}

function sortTemplates(list: TemplateDto[]) {
  return [...list].toSorted((a, b) =>
    (a.name ?? '').localeCompare(b.name ?? '', 'zh-CN', { numeric: true }),
  );
}

function sortDocuments(list: DocumentSummaryDto[]) {
  return [...list].toSorted((a, b) => {
    const ta = Date.parse(a.updatedAt ?? a.createdAt ?? '') || 0;
    const tb = Date.parse(b.updatedAt ?? b.createdAt ?? '') || 0;
    return tb - ta;
  });
}

function docSourceTemplateId(doc: DocumentSummaryDto): string {
  return preserveApiIdForRequest(doc.sourceTemplateId) ?? '';
}

/**
 * 将文书类型归入五类目录（优先 categoryCode；旧 MEDICAL_RECORD/缺省按类型编码兜底）。
 */
export function resolveEmrDirectoryCategory(
  typeCode: string,
  categoryCode?: string | null,
): string {
  const cat = (categoryCode ?? '').trim().toUpperCase();
  if (cat && CATEGORY_LABEL_BY_CODE[cat]) {
    return cat;
  }
  const code = typeCode.trim().toUpperCase();
  if (code === 'OUTPATIENT_RECORD') return 'OUTPATIENT';
  if (code === 'NOTICE') return 'NOTICE';
  if (code === 'PREOP_SUMMARY' || code === 'PREOP_DISCUSSION' || code === 'SURGERY_HANDOVER') {
    return 'SURGERY';
  }
  if (code === 'PASTE_SHEET') return 'OTHER';
  if (
    code === 'ADMISSION_RECORD' ||
    code === 'PROGRESS_NOTE' ||
    code === 'DISCHARGE_SUMMARY' ||
    code === 'TWENTY_FOUR_HOUR_RECORD'
  ) {
    return 'INPATIENT';
  }
  // 旧 MEDICAL_RECORD / CONSENT / 未知
  if (cat === 'CONSENT' || cat === 'MEDICAL_RECORD') {
    return code === 'OUTPATIENT_RECORD' ? 'OUTPATIENT' : 'INPATIENT';
  }
  return 'OTHER';
}

/**
 * 以目录分类为父、模板为子、该模板建出的病历为孙节点；
 * 无 sourceTemplateId 的病历挂在分类下「其他病历」。
 */
export function buildEmrTemplateTree(
  documentTypes: DocumentTypeDto[],
  templates: TemplateDto[],
  documents: DocumentSummaryDto[] = [],
): EmrTemplateTreeNode[] {
  const typeByCode = new Map<string, DocumentTypeDto>();
  for (const dt of documentTypes) {
    const code = String(dt.code ?? '').trim();
    if (code) typeByCode.set(code, dt);
  }

  const templatesByCode = new Map<string, TemplateDto[]>();
  for (const t of templates) {
    const code = (t.documentTypeCode ?? '').trim();
    const bucket = templatesByCode.get(code) ?? [];
    bucket.push(t);
    templatesByCode.set(code, bucket);
  }

  const docsByTemplateId = new Map<string, DocumentSummaryDto[]>();
  const orphanDocsByType = new Map<string, DocumentSummaryDto[]>();
  for (const doc of documents) {
    const tplId = docSourceTemplateId(doc);
    if (tplId) {
      const bucket = docsByTemplateId.get(tplId) ?? [];
      bucket.push(doc);
      docsByTemplateId.set(tplId, bucket);
      continue;
    }
    const typeCode = (doc.documentTypeCode ?? '').trim();
    const bucket = orphanDocsByType.get(typeCode) ?? [];
    bucket.push(doc);
    orphanDocsByType.set(typeCode, bucket);
  }

  const knownTemplateIds = new Set(
    templates.map((t) => (t.id === null || t.id === undefined ? '' : String(t.id))).filter(Boolean),
  );

  /** 模板已删但仍有病历：归入对应类型的「其他病历」 */
  for (const [tplId, docs] of docsByTemplateId) {
    if (knownTemplateIds.has(tplId)) continue;
    for (const doc of docs) {
      const typeCode = (doc.documentTypeCode ?? '').trim();
      const bucket = orphanDocsByType.get(typeCode) ?? [];
      bucket.push(doc);
      orphanDocsByType.set(typeCode, bucket);
    }
    docsByTemplateId.delete(tplId);
  }

  const typesByCategory = new Map<string, DocumentTypeDto[]>();
  const sortedTypes = [...documentTypes].toSorted((a, b) => {
    const order = (a.sortOrder ?? 0) - (b.sortOrder ?? 0);
    if (order !== 0) return order;
    return (a.name ?? '').localeCompare(b.name ?? '', 'zh-CN', { numeric: true });
  });
  for (const dt of sortedTypes) {
    const code = String(dt.code ?? '').trim();
    const cat = resolveEmrDirectoryCategory(code, dt.categoryCode);
    const bucket = typesByCategory.get(cat) ?? [];
    bucket.push(dt);
    typesByCategory.set(cat, bucket);
  }

  /** 模板挂在未知类型：按模板上的 documentTypeCode 推断分类 */
  const orphanTypeCodes = [...templatesByCode.keys()].filter((c) => !typeByCode.has(c));
  for (const typeCode of orphanTypeCodes) {
    const cat = resolveEmrDirectoryCategory(typeCode, undefined);
    if (!typesByCategory.has(cat)) {
      typesByCategory.set(cat, []);
    }
    // 用占位类型，便于后续收集模板
    const placeholder: DocumentTypeDto = {
      code: typeCode,
      name: typeCode || '未归类',
      categoryCode: cat,
    };
    typesByCategory.get(cat)!.push(placeholder);
    typeByCode.set(typeCode, placeholder);
  }

  const nodes: EmrTemplateTreeNode[] = [];

  for (const { code: catCode, label: catLabel } of EMR_DIRECTORY_CATEGORIES) {
    const typesInCat = typesByCategory.get(catCode) ?? [];
    typesByCategory.delete(catCode);
    if (typesInCat.length === 0) continue;

    const children: EmrTemplateTreeNode[] = [];
    const orphanDocs: DocumentSummaryDto[] = [];

    for (const dt of typesInCat) {
      const typeCode = String(dt.code ?? '').trim();
      const typeName = dt.name?.trim() || typeCode;
      const rawList = templatesByCode.get(typeCode) ?? [];
      templatesByCode.delete(typeCode);
      for (const tpl of sortTemplates(rawList)) {
        const tplId = tpl.id === null || tpl.id === undefined ? '' : String(tpl.id);
        const docs = tplId ? (docsByTemplateId.get(tplId) ?? []) : [];
        if (tplId) docsByTemplateId.delete(tplId);
        children.push(toTemplateNode(tpl, children.length, docs, typeName || undefined));
      }
      orphanDocs.push(...(orphanDocsByType.get(typeCode) ?? []));
      orphanDocsByType.delete(typeCode);
    }

    if (orphanDocs.length > 0) {
      children.push({
        nodeKey: `orphan:cat:${catCode}`,
        label: '其他病历',
        categoryCode: catCode,
        isGroup: true,
        children: sortDocuments(orphanDocs).map((d) => toDocumentLeaf(d)),
      });
    }

    if (children.length === 0) continue;

    nodes.push({
      nodeKey: `cat:${catCode}`,
      label: catLabel,
      categoryCode: catCode,
      isDocumentType: true,
      children,
    });
  }

  /** 未落入五类的残余（理论上不应出现） */
  const leftoverTypeBuckets = [...typesByCategory.entries()];
  if (leftoverTypeBuckets.length > 0 || templatesByCode.size > 0 || orphanDocsByType.size > 0) {
    const children: EmrTemplateTreeNode[] = [];
    for (const [, types] of leftoverTypeBuckets) {
      for (const dt of types) {
        const typeCode = String(dt.code ?? '').trim();
        const typeName = dt.name?.trim() || typeCode;
        const rawList = templatesByCode.get(typeCode) ?? [];
        templatesByCode.delete(typeCode);
        for (const tpl of sortTemplates(rawList)) {
          const tplId = tpl.id === null || tpl.id === undefined ? '' : String(tpl.id);
          const docs = tplId ? (docsByTemplateId.get(tplId) ?? []) : [];
          if (tplId) docsByTemplateId.delete(tplId);
          children.push(toTemplateNode(tpl, children.length, docs, typeName || undefined));
        }
      }
    }
    for (const [, rawList] of templatesByCode) {
      for (const tpl of sortTemplates(rawList)) {
        const tplId = tpl.id === null || tpl.id === undefined ? '' : String(tpl.id);
        const docs = tplId ? (docsByTemplateId.get(tplId) ?? []) : [];
        if (tplId) docsByTemplateId.delete(tplId);
        children.push(toTemplateNode(tpl, children.length, docs));
      }
    }
    const leftovers = [...orphanDocsByType.values()].flat();
    if (leftovers.length > 0) {
      children.push({
        nodeKey: 'orphan:__untyped__',
        label: '其他病历',
        isGroup: true,
        children: sortDocuments(leftovers).map((d) => toDocumentLeaf(d)),
      });
    }
    if (children.length > 0) {
      nodes.push({
        nodeKey: 'cat:__orphan__',
        label: '其他',
        categoryCode: 'OTHER',
        isDocumentType: true,
        children,
      });
    }
  }

  return nodes;
}
