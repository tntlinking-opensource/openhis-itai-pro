import type { DocumentSummaryDto } from '#/api/emr/document';
import type { DocumentTypeDto } from '#/api/emr/document-type';
import type { TemplateDto } from '#/api/emr/template';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { EMR_TEMPLATE_SCOPE_LEVEL } from '#/views/basic/emr-template/emr-template-scope.constants';

/** 病历侧栏树节点：目录分类 → 范围分组 → 模板；已写病历挂「历史病历」 */
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
  /** 是否为目录分类父节点（门诊病历模板/住院病历/…） */
  isDocumentType?: boolean;
  /** 是否为分组节点（如「个人」「科室」「全院」「其他病历」，不可点选） */
  isGroup?: boolean;
  /** 是否为模板节点（点选 → 新建） */
  isTemplate?: boolean;
  /** 是否为已有病历节点（点选 → 打开） */
  isDocument?: boolean;
  /** 是否挂在「历史病历」下（只读浏览，不可删） */
  isHistoryDocument?: boolean;
  /** 模板列表 DTO */
  template?: TemplateDto;
  /** 病历摘要 DTO */
  document?: DocumentSummaryDto;
  children?: EmrTemplateTreeNode[];
}

/** 侧栏目录五类（顺序即展示顺序） */
export const EMR_DIRECTORY_CATEGORIES = [
  { code: 'OUTPATIENT', label: '门诊病历模板' },
  { code: 'INPATIENT', label: '住院病历' },
  { code: 'NOTICE', label: '告知书' },
  { code: 'SURGERY', label: '手术病历' },
  { code: 'OTHER', label: '其他' },
] as const;

/** 模板范围子父级（个人 / 科室 / 全院），顺序即展示顺序 */
const EMR_TEMPLATE_SCOPE_GROUPS = [
  { code: EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL, label: '个人' },
  { code: EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT, label: '科室' },
  { code: EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL, label: '全院' },
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
    parts.push('[历史]');
  }
  return parts.length > 0 ? parts.join(' ') : '未保存';
}

function buildTemplateLeafLabel(tpl: TemplateDto): string {
  return (
    tpl.name ??
    tpl.code ??
    (tpl.id === null || tpl.id === undefined ? '未命名模板' : String(tpl.id))
  );
}

/** 归一化模板范围；未知/空按全院归组（与历史默认全院模板一致） */
function resolveTemplateScopeGroupCode(scopeLevel?: string | null): string {
  const scope = String(scopeLevel ?? '')
    .trim()
    .toUpperCase();
  if (scope === EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL) {
    return EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL;
  }
  if (scope === EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT) {
    return EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT;
  }
  return EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL;
}

/**
 * 将模板叶子按个人 / 科室 / 全院分组；空组不展示。
 * 范围已体现在父节点，模板名不再拼接 [个人]/[科室]/[全院]。
 */
function groupTemplateNodesByScope(
  catCode: string,
  templateNodes: EmrTemplateTreeNode[],
): EmrTemplateTreeNode[] {
  if (templateNodes.length === 0) return [];

  const buckets = new Map<string, EmrTemplateTreeNode[]>();
  for (const node of templateNodes) {
    const key = resolveTemplateScopeGroupCode(node.template?.scopeLevel);
    const list = buckets.get(key) ?? [];
    list.push(node);
    buckets.set(key, list);
  }

  const groups: EmrTemplateTreeNode[] = [];
  for (const { code, label } of EMR_TEMPLATE_SCOPE_GROUPS) {
    const list = buckets.get(code);
    if (!list?.length) continue;
    groups.push({
      nodeKey: `scope:${catCode}:${code}`,
      label,
      categoryCode: catCode,
      isGroup: true,
      children: list,
    });
  }
  return groups;
}

function toDocumentLeaf(
  doc: DocumentSummaryDto,
  options?: { history?: boolean; currentEncounterId?: string },
): EmrTemplateTreeNode {
  const idStr = preserveApiIdForRequest(doc.id) ?? `noid-${String(doc.updatedAt ?? '')}`;
  const history = Boolean(options?.history);
  return {
    nodeKey: history ? `hist-doc:${idStr}` : `doc:${idStr}`,
    label: buildDocumentTreeLabel(doc, { currentEncounterId: options?.currentEncounterId }),
    documentTypeCode: (doc.documentTypeCode ?? '').trim() || undefined,
    documentTypeName: (doc.documentTypeName ?? '').trim() || undefined,
    isDocument: true,
    isHistoryDocument: history || undefined,
    document: doc,
  };
}

/** 侧栏树内容模式：整树 / 仅模板 / 仅已写文书 */
export type EmrSidebarTreeMode = 'all' | 'templatesOnly' | 'documentsOnly';

/** 按科室聚合历史文书；时间放在卡片内展示 */
function buildHistoryDocumentTree(
  historyDocuments: DocumentSummaryDto[],
  currentEncounterId?: string,
  historyRootLabel = '历史病历',
): EmrTemplateTreeNode | null {
  if (historyDocuments.length === 0) return null;

  // 按科室名分桶（同科室跨就诊合并）
  const byDept = new Map<string, DocumentSummaryDto[]>();
  for (const doc of historyDocuments) {
    const dept = formatDocumentTreeDept(doc) || '未知科室';
    const bucket = byDept.get(dept) ?? [];
    bucket.push(doc);
    byDept.set(dept, bucket);
  }

  // 组间按组内最新文书时间降序
  const groups = [...byDept.entries()].map(([dept, docs]) => {
    const sorted = sortDocuments(docs);
    const latest = sorted[0];
    return {
      dept,
      latestTs: Date.parse(latest?.updatedAt ?? latest?.createdAt ?? '') || 0,
      node: {
        nodeKey: `hist-dept:${dept}`,
        label: dept,
        isGroup: true,
        children: sorted.map((d) => toDocumentLeaf(d, { history: true, currentEncounterId })),
      } satisfies EmrTemplateTreeNode,
    };
  });
  groups.sort((a, b) => b.latestTs - a.latestTs);

  return {
    nodeKey: 'cat:HISTORY',
    label: historyRootLabel,
    categoryCode: 'HISTORY',
    isDocumentType: true,
    children: groups.map((g) => g.node),
  };
}

/**
 * 仅已写文书树（本诊扁平 / 历史按科室）。
 * 供侧栏「本诊病历」「历史」分页使用，不混入模板。
 */
export function buildEmrDocumentOnlyTree(
  documents: DocumentSummaryDto[],
  options?: {
    currentEncounterId?: string;
    /** none=本诊扁平列表；dept=按科室分组（跨就诊历史） */
    groupBy?: 'none' | 'dept';
    rootLabel?: string;
  },
): EmrTemplateTreeNode[] {
  const currentEncounterId = options?.currentEncounterId;
  const groupBy = options?.groupBy ?? 'none';
  if (documents.length === 0) return [];

  if (groupBy === 'none') {
    // 本诊：直接叶子列表，避免再套「历史」根节点占行
    return sortDocuments(documents).map((d) =>
      toDocumentLeaf(d, { history: true, currentEncounterId }),
    );
  }

  const root = buildHistoryDocumentTree(
    documents,
    currentEncounterId,
    options?.rootLabel?.trim() || '历史',
  );
  return root ? [root] : [];
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

/** 按目录分类白名单过滤文书（类型表优先带 categoryCode） */
export function filterDocumentsByDirectoryCategories(
  documents: DocumentSummaryDto[],
  documentTypes: DocumentTypeDto[],
  allowedCategories: string[] | undefined,
): DocumentSummaryDto[] {
  if (!allowedCategories?.length) {
    return documents;
  }
  const allowed = new Set(allowedCategories.map((c) => c.trim().toUpperCase()).filter(Boolean));
  if (allowed.size === 0) {
    return documents;
  }
  const categoryByTypeCode = new Map<string, string>();
  for (const dt of documentTypes) {
    const code = String(dt.code ?? '').trim();
    if (!code) continue;
    categoryByTypeCode.set(code, resolveEmrDirectoryCategory(code, dt.categoryCode));
  }
  return documents.filter((doc) => {
    const typeCode = (doc.documentTypeCode ?? '').trim();
    const cat =
      categoryByTypeCode.get(typeCode) ?? resolveEmrDirectoryCategory(typeCode, undefined);
    return allowed.has(cat);
  });
}

/**
 * 以目录分类为父、范围（个人/科室/全院）为子、模板为孙；
 * documents 若传入仍可挂在对应模板下（兼容）；医生站侧栏已改为全部走 historyDocuments；
 * 追加顶级「历史病历」（按科室合并）。
 * sidebarMode=templatesOnly 时不挂历史；documentsOnly 时仅返回文书树。
 */
export function buildEmrTemplateTree(
  documentTypes: DocumentTypeDto[],
  templates: TemplateDto[],
  documents: DocumentSummaryDto[] = [],
  historyDocuments: DocumentSummaryDto[] = [],
  options?: {
    allowedCategories?: string[];
    currentEncounterId?: string;
    /** 「历史病历」根节点文案；病案按就诊打开时可改为「本次就诊病历」 */
    historyRootLabel?: string;
    /** 侧栏分页：整树 / 仅模板 / 仅文书 */
    sidebarMode?: EmrSidebarTreeMode;
    /** documentsOnly 时：none 扁平，dept 按科室 */
    documentGroupBy?: 'none' | 'dept';
  },
): EmrTemplateTreeNode[] {
  const currentEncounterId = options?.currentEncounterId;
  const allowedCategories = options?.allowedCategories;
  const historyRootLabel = options?.historyRootLabel?.trim() || '历史病历';
  const sidebarMode = options?.sidebarMode ?? 'all';
  const scopedDocuments = filterDocumentsByDirectoryCategories(
    documents,
    documentTypes,
    allowedCategories,
  );
  const scopedHistoryDocuments = filterDocumentsByDirectoryCategories(
    historyDocuments,
    documentTypes,
    allowedCategories,
  );

  if (sidebarMode === 'documentsOnly') {
    return buildEmrDocumentOnlyTree(scopedHistoryDocuments, {
      currentEncounterId,
      groupBy: options?.documentGroupBy ?? 'dept',
      rootLabel: historyRootLabel,
    });
  }

  // 本诊 / 跨就诊拆分：本诊置顶，历史垫底，中间才是模板
  const currentEncId = preserveApiIdForRequest(currentEncounterId) ?? '';
  const currentEncounterDocs: DocumentSummaryDto[] = [];
  const crossEncounterDocs: DocumentSummaryDto[] = [];
  if (currentEncId) {
    for (const doc of scopedHistoryDocuments) {
      const docEnc = preserveApiIdForRequest(doc.encounterId) ?? '';
      if (docEnc && docEnc === currentEncId) {
        currentEncounterDocs.push(doc);
      } else {
        crossEncounterDocs.push(doc);
      }
    }
  } else {
    crossEncounterDocs.push(...scopedHistoryDocuments);
  }

  const nodes: EmrTemplateTreeNode[] = [];

  // 本诊已写置顶（有文书才挂根节点，避免空文件夹挡模板）
  if (sidebarMode !== 'templatesOnly' && currentEncounterDocs.length > 0) {
    const currentLabel =
      historyRootLabel === '本次就诊病历'
        ? `${historyRootLabel} (${currentEncounterDocs.length})`
        : `本诊已写 (${currentEncounterDocs.length})`;
    nodes.push({
      nodeKey: 'cat:CURRENT',
      label: currentLabel,
      categoryCode: 'CURRENT',
      isDocumentType: true,
      children: sortDocuments(currentEncounterDocs).map((d) =>
        toDocumentLeaf(d, { history: true, currentEncounterId }),
      ),
    });
  }

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
  for (const doc of scopedDocuments) {
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

  const allowedCategorySet = allowedCategories?.length
    ? new Set(allowedCategories.map((c) => c.trim().toUpperCase()).filter(Boolean))
    : null;

  for (const { code: catCode, label: catLabel } of EMR_DIRECTORY_CATEGORIES) {
    if (allowedCategorySet && !allowedCategorySet.has(catCode)) {
      typesByCategory.delete(catCode);
      continue;
    }
    const typesInCat = typesByCategory.get(catCode) ?? [];
    typesByCategory.delete(catCode);
    if (typesInCat.length === 0) continue;

    const templateNodes: EmrTemplateTreeNode[] = [];
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
        templateNodes.push(toTemplateNode(tpl, templateNodes.length, docs, typeName || undefined));
      }
      orphanDocs.push(...(orphanDocsByType.get(typeCode) ?? []));
      orphanDocsByType.delete(typeCode);
    }

    const children: EmrTemplateTreeNode[] = [...groupTemplateNodesByScope(catCode, templateNodes)];

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

  /** 未落入五类的残余（理论上不应出现；白名单模式下若未放行 OTHER 则跳过） */
  const leftoverTypeBuckets = [...typesByCategory.entries()];
  const allowLeftoverCategory = !allowedCategorySet || allowedCategorySet.has('OTHER');
  if (
    allowLeftoverCategory &&
    (leftoverTypeBuckets.length > 0 || templatesByCode.size > 0 || orphanDocsByType.size > 0)
  ) {
    const templateNodes: EmrTemplateTreeNode[] = [];
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
          templateNodes.push(
            toTemplateNode(tpl, templateNodes.length, docs, typeName || undefined),
          );
        }
      }
    }
    for (const [, rawList] of templatesByCode) {
      for (const tpl of sortTemplates(rawList)) {
        const tplId = tpl.id === null || tpl.id === undefined ? '' : String(tpl.id);
        const docs = tplId ? (docsByTemplateId.get(tplId) ?? []) : [];
        if (tplId) docsByTemplateId.delete(tplId);
        templateNodes.push(toTemplateNode(tpl, templateNodes.length, docs));
      }
    }
    const children: EmrTemplateTreeNode[] = [...groupTemplateNodesByScope('OTHER', templateNodes)];
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

  if (sidebarMode !== 'templatesOnly') {
    // 仅跨就诊进「历史」；本诊已在顶部，避免重复
    const historyRoot = buildHistoryDocumentTree(
      currentEncId ? crossEncounterDocs : scopedHistoryDocuments,
      currentEncounterId,
      currentEncId ? '历史病历' : historyRootLabel,
    );
    if (historyRoot) {
      nodes.push(historyRoot);
    }
  }

  return nodes;
}
