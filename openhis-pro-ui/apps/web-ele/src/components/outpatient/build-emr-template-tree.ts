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

/** 侧栏目录分类（顺序即展示顺序） */
export const EMR_DIRECTORY_CATEGORIES = [
  { code: 'GUOJI', label: '国基病历' },
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

export type EmrDocumentSortMode = 'updatedAtDesc' | 'createdAtAsc';

export function formatDocumentTreeTime(
  doc: DocumentSummaryDto,
  sortMode: EmrDocumentSortMode = 'updatedAtDesc',
): string {
  const raw =
    sortMode === 'createdAtAsc' ? (doc.createdAt ?? doc.updatedAt) : (doc.updatedAt ?? doc.createdAt);
  return formatUpdatedAt(raw);
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
 * 全院模板再按文书类型分一层（入院记录、病程记录等）。
 * 模板进入顺序即类型 sortOrder，文件夹按首次出现顺序排列。
 */
function groupHospitalTemplatesByDocumentType(
  catCode: string,
  templates: EmrTemplateTreeNode[],
): EmrTemplateTreeNode[] {
  const buckets = new Map<string, EmrTemplateTreeNode[]>();
  const order: string[] = [];
  for (const node of templates) {
    const code = (node.documentTypeCode ?? '').trim() || '__unknown__';
    if (!buckets.has(code)) {
      order.push(code);
      buckets.set(code, []);
    }
    buckets.get(code)!.push(node);
  }
  return order.map((code) => {
    const list = buckets.get(code) ?? [];
    const named = list.find((node) => (node.documentTypeName ?? '').trim());
    const typeName =
      named?.documentTypeName?.trim() || (code === '__unknown__' ? '未归类' : code);
    return {
      nodeKey: `type:${catCode}:${code}`,
      label: typeName,
      documentTypeCode: code === '__unknown__' ? undefined : code,
      documentTypeName: typeName,
      categoryCode: catCode,
      isDocumentType: true,
      children: list,
    };
  });
}

/**
 * 将模板叶子按个人 / 科室 / 全院分组；空组不展示。
 * 范围已体现在父节点，模板名不再拼接 [个人]/[科室]/[全院]。
 * 住院全院组可再按文书类型分一层；个人 / 科室仍直接挂模板。
 */
function groupTemplateNodesByScope(
  catCode: string,
  templateNodes: EmrTemplateTreeNode[],
  hospitalByDocumentType = false,
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
    const children =
      hospitalByDocumentType && code === EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL
        ? groupHospitalTemplatesByDocumentType(catCode, list)
        : list;
    groups.push({
      nodeKey: `scope:${catCode}:${code}`,
      label,
      categoryCode: catCode,
      isGroup: true,
      children,
    });
  }
  return groups;
}

/** 住院侧栏只保留一个「住院病历模板」，顺序为全院（按文书类型）、个人、科室 */
const INPATIENT_TEMPLATE_ROOT_SCOPES = [
  { code: EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL, label: '全院' },
  { code: EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL, label: '个人' },
  { code: EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT, label: '科室' },
] as const;

function takeTemplatesMatchingType(
  typeCode: string,
  templatesByCode: Map<string, TemplateDto[]>,
): TemplateDto[] {
  const matched: TemplateDto[] = [];
  const direct = templatesByCode.get(typeCode);
  if (direct?.length) {
    matched.push(...direct);
  }
  templatesByCode.delete(typeCode);
  const suffix = `::${typeCode}`;
  for (const key of [...templatesByCode.keys()]) {
    if (!key.endsWith(suffix)) continue;
    matched.push(...(templatesByCode.get(key) ?? []));
    templatesByCode.delete(key);
  }
  return matched;
}

function appendInpatientTemplateRoot(
  nodes: EmrTemplateTreeNode[],
  sortedTypes: DocumentTypeDto[],
  typeByCode: Map<string, DocumentTypeDto>,
  templatesByCode: Map<string, TemplateDto[]>,
  docsByTemplateId: Map<string, DocumentSummaryDto[]>,
  orphanDocsByType: Map<string, DocumentSummaryDto[]>,
  allowedCategorySet: Set<string> | null,
): void {
  const personalNodes: EmrTemplateTreeNode[] = [];
  const departmentNodes: EmrTemplateTreeNode[] = [];
  const typeFolders: EmrTemplateTreeNode[] = [];
  const emitted = new Set<string>();

  const pushTemplates = (typeCode: string, typeName: string, rawList: TemplateDto[]) => {
    if (emitted.has(typeCode)) return;
    emitted.add(typeCode);
    const hospitalNodes: EmrTemplateTreeNode[] = [];
    for (const tpl of sortTemplates(rawList)) {
      const tplId = tpl.id === null || tpl.id === undefined ? '' : String(tpl.id);
      const docs = tplId ? (docsByTemplateId.get(tplId) ?? []) : [];
      if (tplId) docsByTemplateId.delete(tplId);
      const leaf = toTemplateNode(tpl, hospitalNodes.length + personalNodes.length, docs, typeName);
      const scope = resolveTemplateScopeGroupCode(tpl.scopeLevel);
      if (scope === EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL) {
        personalNodes.push(leaf);
        continue;
      }
      if (scope === EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT) {
        departmentNodes.push(leaf);
        continue;
      }
      hospitalNodes.push(leaf);
    }
    orphanDocsByType.delete(typeCode);
    if (hospitalNodes.length === 0) return;
    typeFolders.push({
      nodeKey: `type:INPATIENT_TEMPLATES:${typeCode || '__empty__'}`,
      label: typeName || '未归类',
      documentTypeCode: typeCode || undefined,
      documentTypeName: typeName || undefined,
      isDocumentType: true,
      children: hospitalNodes,
    });
  };

  for (const dt of sortedTypes) {
    const typeCode = String(dt.code ?? '').trim();
    if (!typeCode) continue;
    const cat = resolveEmrDirectoryCategory(typeCode, dt.categoryCode);
    const rawList = takeTemplatesMatchingType(typeCode, templatesByCode);
    if (allowedCategorySet && !allowedCategorySet.has(cat)) {
      orphanDocsByType.delete(typeCode);
      continue;
    }
    pushTemplates(typeCode, dt.name?.trim() || typeCode, rawList);
  }

  for (const bucketKey of [...templatesByCode.keys()]) {
    const typeCode = bucketKey.includes('::')
      ? bucketKey.slice(bucketKey.indexOf('::') + 2)
      : bucketKey;
    const known = typeByCode.get(typeCode) ?? typeByCode.get(bucketKey);
    const cat = resolveEmrDirectoryCategory(typeCode, known?.categoryCode);
    const rawList = takeTemplatesMatchingType(typeCode || bucketKey, templatesByCode);
    if (allowedCategorySet && !allowedCategorySet.has(cat)) continue;
    pushTemplates(typeCode || bucketKey, known?.name?.trim() || typeCode || '未归类', rawList);
  }

  const scopeChildren: EmrTemplateTreeNode[] = [];
  for (const { code, label } of INPATIENT_TEMPLATE_ROOT_SCOPES) {
    const children =
      code === EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL
        ? typeFolders
        : code === EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL
          ? personalNodes
          : departmentNodes;
    if (children.length === 0) continue;
    scopeChildren.push({
      nodeKey: `scope:INPATIENT_TEMPLATES:${code}`,
      label,
      isGroup: true,
      children,
    });
  }
  if (scopeChildren.length === 0) return;
  nodes.push({
    nodeKey: 'cat:INPATIENT_TEMPLATES',
    label: '住院病历模板',
    isDocumentType: true,
    children: scopeChildren,
  });
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
  sortMode: EmrDocumentSortMode = 'updatedAtDesc',
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

  // 组间按组内最新文书时间降序；组内按传入排序
  const groups = [...byDept.entries()].map(([dept, docs]) => {
    const sorted = sortDocuments(docs, sortMode);
    const latest = sortMode === 'createdAtAsc' ? sorted[sorted.length - 1] : sorted[0];
    return {
      dept,
      latestTs: documentSortTime(latest ?? docs[0]!, sortMode),
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
    documentSort?: EmrDocumentSortMode;
  },
): EmrTemplateTreeNode[] {
  const currentEncounterId = options?.currentEncounterId;
  const groupBy = options?.groupBy ?? 'none';
  const sortMode = options?.documentSort ?? 'updatedAtDesc';
  if (documents.length === 0) return [];

  if (groupBy === 'none') {
    // 本诊：直接叶子列表，避免再套「历史」根节点占行
    return sortDocuments(documents, sortMode).map((d) =>
      toDocumentLeaf(d, { history: true, currentEncounterId }),
    );
  }

  const root = buildHistoryDocumentTree(
    documents,
    currentEncounterId,
    options?.rootLabel?.trim() || '历史',
    sortMode,
  );
  return root ? [root] : [];
}

function toTemplateNode(
  tpl: TemplateDto,
  leafIndex: number,
  docs: DocumentSummaryDto[],
  documentTypeName?: string,
  sortMode: EmrDocumentSortMode = 'updatedAtDesc',
): EmrTemplateTreeNode {
  const codeText = String(tpl.code ?? 'tpl');
  const idStr =
    tpl.id === null || tpl.id === undefined
      ? `noid-${codeText.replace(/\W/g, '')}-${leafIndex}`
      : String(tpl.id);
  const documentTypeCode = String(tpl.documentTypeCode ?? '').trim() || undefined;
  const children = sortDocuments(docs, sortMode).map((d) => toDocumentLeaf(d));
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

function documentSortTime(doc: DocumentSummaryDto, mode: EmrDocumentSortMode): number {
  if (mode === 'createdAtAsc') {
    return Date.parse(doc.createdAt ?? doc.updatedAt ?? '') || 0;
  }
  return Date.parse(doc.updatedAt ?? doc.createdAt ?? '') || 0;
}

function sortDocuments(
  list: DocumentSummaryDto[],
  mode: EmrDocumentSortMode = 'updatedAtDesc',
) {
  return [...list].toSorted((a, b) => {
    const ta = documentSortTime(a, mode);
    const tb = documentSortTime(b, mode);
    return mode === 'createdAtAsc' ? ta - tb : tb - ta;
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
  if (code === 'GUOJI_RECORD' || code === 'GUOJI_OUTPATIENT' || code.startsWith('GUOJI::')) {
    return 'GUOJI';
  }
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

/** 模板侧栏分类：本地 categoryCode 可把模板挂到国基病历等父级，不改文书类型 */
function resolveTemplateTreeCategory(
  tpl: TemplateDto,
  typeByCode: Map<string, DocumentTypeDto>,
): string {
  const override = String(tpl.categoryCode ?? '').trim().toUpperCase();
  if (override && CATEGORY_LABEL_BY_CODE[override]) {
    return override;
  }
  const typeCode = String(tpl.documentTypeCode ?? '').trim();
  return resolveEmrDirectoryCategory(typeCode, typeByCode.get(typeCode)?.categoryCode);
}

/** 与文书类型桶错开时用「分类::类型」键，避免国基模板混进门诊目录 */
function templateTreeBucketKey(
  tpl: TemplateDto,
  typeByCode: Map<string, DocumentTypeDto>,
): string {
  const typeCode = String(tpl.documentTypeCode ?? '').trim();
  const cat = resolveTemplateTreeCategory(tpl, typeByCode);
  const typeCat = resolveEmrDirectoryCategory(typeCode, typeByCode.get(typeCode)?.categoryCode);
  if (cat !== typeCat) {
    return `${cat}::${typeCode}`;
  }
  return typeCode;
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
    /**
     * 住院侧栏：一个「住院病历模板」。
     * 其下为全院（再按文书类型）、个人、科室；个人和科室直接挂模板。
     */
    groupByDocumentType?: boolean;
    /** 已写病历排序：住院按创建时间升序，门诊按更新时间降序 */
    documentSort?: EmrDocumentSortMode;
  },
): EmrTemplateTreeNode[] {
  const currentEncounterId = options?.currentEncounterId;
  const allowedCategories = options?.allowedCategories;
  const historyRootLabel = options?.historyRootLabel?.trim() || '历史病历';
  const sidebarMode = options?.sidebarMode ?? 'all';
  const sortMode = options?.documentSort ?? 'updatedAtDesc';
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
      documentSort: sortMode,
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
      children: sortDocuments(currentEncounterDocs, sortMode).map((d) =>
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
    const code = templateTreeBucketKey(t, typeByCode);
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
    const realTypeCode = typeCode.includes('::')
      ? typeCode.slice(typeCode.indexOf('::') + 2)
      : typeCode;
    const placeholder: DocumentTypeDto = {
      code: typeCode,
      name:
        typeByCode.get(realTypeCode)?.name?.trim() ||
        CATEGORY_LABEL_BY_CODE[cat] ||
        realTypeCode ||
        '未归类',
      categoryCode: cat,
    };
    typesByCategory.get(cat)!.push(placeholder);
    typeByCode.set(typeCode, placeholder);
  }

  const allowedCategorySet = allowedCategories?.length
    ? new Set(allowedCategories.map((c) => c.trim().toUpperCase()).filter(Boolean))
    : null;
  const hospitalByDocumentType = Boolean(options?.groupByDocumentType);

  if (hospitalByDocumentType) {
    appendInpatientTemplateRoot(
      nodes,
      sortedTypes,
      typeByCode,
      templatesByCode,
      docsByTemplateId,
      orphanDocsByType,
      allowedCategorySet,
    );
  } else {
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

    const children: EmrTemplateTreeNode[] = [
      ...groupTemplateNodesByScope(catCode, templateNodes, hospitalByDocumentType),
    ];

    if (orphanDocs.length > 0) {
      children.push({
        nodeKey: `orphan:cat:${catCode}`,
        label: '其他病历',
        categoryCode: catCode,
        isGroup: true,
        children: sortDocuments(orphanDocs, sortMode).map((d) => toDocumentLeaf(d)),
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
    const children: EmrTemplateTreeNode[] = [
      ...groupTemplateNodesByScope('OTHER', templateNodes, hospitalByDocumentType),
    ];
    const leftovers = [...orphanDocsByType.values()].flat();
    if (leftovers.length > 0) {
      children.push({
        nodeKey: 'orphan:__untyped__',
        label: '其他病历',
        isGroup: true,
        children: sortDocuments(leftovers, sortMode).map((d) => toDocumentLeaf(d)),
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
  }

  if (sidebarMode !== 'templatesOnly') {
    // 仅跨就诊进「历史」；本诊已在顶部，避免重复
    const historyRoot = buildHistoryDocumentTree(
      currentEncId ? crossEncounterDocs : scopedHistoryDocuments,
      currentEncounterId,
      currentEncId ? '历史病历' : historyRootLabel,
      sortMode,
    );
    if (historyRoot) {
      nodes.push(historyRoot);
    }
  }

  return nodes;
}
