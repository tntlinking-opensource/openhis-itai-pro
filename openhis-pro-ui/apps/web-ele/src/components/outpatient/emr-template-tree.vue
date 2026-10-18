<script setup lang="ts">
import type Node from 'element-plus/es/components/tree/src/model/node';
import type { TreeNodeData } from 'element-plus/es/components/tree/src/tree.type';

import type { EmrTemplateTreeNode } from './build-emr-template-tree';

import type { DocumentSummaryDto } from '#/api/emr/document';
import type { DocumentTypeDto } from '#/api/emr/document-type';
import type { TemplateDto } from '#/api/emr/template';
import type { EmrTemplateStationKind } from '#/views/basic/emr-template/emr-template-scope.constants';

import { nextTick, onMounted, ref, watch } from 'vue';

import { useUserStore } from '@zxplat/stores';

import { Document, EditPen, Folder, FolderOpened, Tickets } from '@element-plus/icons-vue';
import {
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElIcon,
  ElMessage,
  ElMessageBox,
  ElTree,
} from 'element-plus';

import { getDocumentTypeListApi } from '#/api/emr/document-type';
import { getTemplateListApi, listTemplatesForStationApi } from '#/api/emr/template';
import { useAuthStore } from '#/store';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import {
  EMR_TEMPLATE_ENCOUNTER_CLASS,
  EMR_TEMPLATE_SCOPE_LEVEL,
  EMR_TEMPLATE_STATION_KIND,
} from '#/views/basic/emr-template/emr-template-scope.constants';

import {
  buildEmrTemplateTree,
  formatDocumentTreeSaver,
  formatDocumentTreeTime,
  type EmrDocumentSortMode,
  type EmrSidebarTreeMode,
} from './build-emr-template-tree';

defineOptions({
  name: 'EmrTemplateTree',
});

const props = withDefaults(
  defineProps<{
    /** 当前激活文书 ID */
    currentDocumentId?: string;
    /** 当前医生站就诊 ID；用于标记非本次就诊文书 */
    currentEncounterId?: string;
    /** 可选：挂到对应模板下的文书（医生站已改为全部走 historyDocuments） */
    documents?: DocumentSummaryDto[];
    /** 历史病历（本次 + 跨就诊，按就诊分组挂「历史病历」） */
    historyDocuments?: DocumentSummaryDto[];
    /** 「历史病历」根节点文案 */
    historyRootLabel?: string;
    /** 仅展示启用文书类型；默认 true */
    documentTypesEnabledOnly?: boolean;
    /** 与外侧搜索框联动，筛选节点展示 */
    filterText?: string;
    /** 已打开（含非激活）的文书 ID */
    openDocumentIds?: string[];
    /** 医生站场景：门诊 / 住院；传入时走 listForStation */
    stationKind?: EmrTemplateStationKind;
    /** 仅加载启用模板；默认 true */
    templatesEnabledOnly?: boolean;
    /** 只读调阅：不加载模板节点，禁止删除 */
    viewOnly?: boolean;
    /**
     * 仅护士站病历调阅：侧栏只展示住院病历目录分类。
     * 病案只读查阅勿开（同就诊下告知/手术等文书也要能看）。
     */
    restrictHistoryToInpatient?: boolean;
    /** 当前编辑器是否可用来覆盖更新模板正文 */
    canUpdateTemplateContent?: boolean;
    /**
     * 侧栏分页内容：整树 / 仅模板 / 仅已写文书。
     * documents 时不拉模板；templates 时不挂历史根。
     */
    sidebarMode?: EmrSidebarTreeMode;
    /** documentsOnly 时分组：本诊扁平 none，历史按科室 dept */
    documentGroupBy?: 'none' | 'dept';
  }>(),
  {
    currentDocumentId: '',
    currentEncounterId: '',
    documents: () => [],
    historyDocuments: () => [],
    historyRootLabel: '历史病历',
    documentTypesEnabledOnly: true,
    filterText: '',
    openDocumentIds: () => [],
    templatesEnabledOnly: true,
    stationKind: undefined,
    viewOnly: false,
    restrictHistoryToInpatient: false,
    canUpdateTemplateContent: false,
    sidebarMode: 'all',
    documentGroupBy: 'dept',
  },
);

const emit = defineEmits<{
  select: [node: EmrTemplateTreeNode];
  selectDocument: [row: DocumentSummaryDto];
  openDocumentInNewTab: [row: DocumentSummaryDto];
  deleteDocument: [row: DocumentSummaryDto];
  /** 已写文书：修改显示标题（非模板） */
  renameDocument: [row: DocumentSummaryDto];
  /** 历史/关联文书：复制正文到本次就诊新建草稿 */
  reuseDocument: [row: DocumentSummaryDto];
  /** 重命名个人/科室模板 */
  renameTemplate: [tpl: TemplateDto];
  /** 用当前病历正文覆盖模板 */
  updateTemplateContent: [tpl: TemplateDto];
  /** 删除个人/科室模板 */
  deleteTemplate: [tpl: TemplateDto];
}>();

const userStore = useUserStore();
const authStore = useAuthStore();
const treeRef = ref<InstanceType<typeof ElTree>>();
const loading = ref(false);
const treeData = ref<EmrTemplateTreeNode[]>([]);
const cachedTypes = ref<DocumentTypeDto[]>([]);
const cachedTemplates = ref<TemplateDto[]>([]);

const PAGE_SIZE = 200;
/** 病历侧栏树展开状态 localStorage 前缀（刷新保留） */
const EXPAND_STORAGE_PREFIX = 'emr-template-tree-expanded';

type TreeStoreNode = {
  expanded?: boolean;
  childNodes?: unknown[];
};

function resolveExpandStorageKey(): string {
  const userId = preserveApiIdForRequest(userStore.userInfo?.id) ?? 'anon';
  const station = props.stationKind ?? 'default';
  const mode = props.viewOnly ? 'view' : 'edit';
  const sidebar = props.sidebarMode ?? 'all';
  return `${EXPAND_STORAGE_PREFIX}:${userId}:${station}:${mode}:${sidebar}`;
}

function readCachedExpandedKeys(): string[] | null {
  try {
    const raw = localStorage.getItem(resolveExpandStorageKey());
    if (!raw) return null;
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return null;
    return parsed.filter((k): k is string => typeof k === 'string' && k.length > 0);
  } catch {
    return null;
  }
}

function writeCachedExpandedKeys(keys: string[]) {
  try {
    localStorage.setItem(resolveExpandStorageKey(), JSON.stringify(keys));
  } catch {
    // quota / 隐私模式：忽略
  }
}

function getTreeNodesMap(): Record<string, TreeStoreNode> | undefined {
  const store = treeRef.value?.store as undefined | { nodesMap?: Record<string, TreeStoreNode> };
  return store?.nodesMap;
}

/** 收集当前已展开且有子节点的 nodeKey */
function collectExpandedKeys(): string[] {
  const nodesMap = getTreeNodesMap();
  if (!nodesMap) return [];
  const keys: string[] = [];
  for (const [key, node] of Object.entries(nodesMap)) {
    if (node.expanded && (node.childNodes?.length ?? 0) > 0) {
      keys.push(key);
    }
  }
  return keys;
}

/** 用户展开/收起后写入缓存 */
function persistExpandedState() {
  void nextTick(() => {
    writeCachedExpandedKeys(collectExpandedKeys());
  });
}

/**
 * 恢复展开状态：有缓存则按缓存；首次无缓存时：
 * - 医生站整树：本诊展开；住院/门诊模板展开个人科室；告知书/全院/历史默认收起
 * - 仅模板页：同上述模板规则
 * - 其余：全部展开
 */
function restoreExpandedState() {
  void nextTick(() => {
    const nodesMap = getTreeNodesMap();
    if (!nodesMap) return;
    const cached = readCachedExpandedKeys();
    if (!cached) {
      const collapseTemplateNoise =
        props.sidebarMode === 'templatesOnly' || props.sidebarMode === 'all';
      if (collapseTemplateNoise) {
        for (const [key, node] of Object.entries(nodesMap)) {
          if ((node.childNodes?.length ?? 0) === 0) continue;
          // 本诊置顶区始终展开
          if (key === 'cat:CURRENT') {
            node.expanded = true;
            continue;
          }
          // 告知书/手术/其他/跨就诊历史默认收起；文书类型文件夹默认收起；全院范围默认收起
          if (
            key === 'cat:NOTICE' ||
            key === 'cat:SURGERY' ||
            key === 'cat:OTHER' ||
            key === 'cat:HISTORY' ||
            key.startsWith('type:') ||
            key.endsWith(`:${EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL}`)
          ) {
            node.expanded = false;
            continue;
          }
          node.expanded = true;
        }
        return;
      }
      for (const node of Object.values(nodesMap)) {
        if ((node.childNodes?.length ?? 0) > 0) {
          node.expanded = true;
        }
      }
      return;
    }
    const expandedSet = new Set(cached);
    for (const [key, node] of Object.entries(nodesMap)) {
      if ((node.childNodes?.length ?? 0) === 0) continue;
      // 本诊已写始终展开，不被旧缓存压扁
      if (key === 'cat:CURRENT') {
        node.expanded = true;
        continue;
      }
      node.expanded = expandedSet.has(key);
    }
  });
}

function resolveEncounterClassCode(kind: EmrTemplateStationKind | undefined): string | undefined {
  if (kind === EMR_TEMPLATE_STATION_KIND.OUTPATIENT) {
    return EMR_TEMPLATE_ENCOUNTER_CLASS.AMB;
  }
  if (kind === EMR_TEMPLATE_STATION_KIND.INPATIENT) {
    return EMR_TEMPLATE_ENCOUNTER_CLASS.IMP;
  }
  return undefined;
}

/** 文书类型：走 documentType/getList（维护页同源，含 sortOrder） */
async function fetchDocumentTypesForTree(): Promise<DocumentTypeDto[]> {
  const params = props.documentTypesEnabledOnly ? { enabled: true } : {};
  const list = await getDocumentTypeListApi(params);
  const rows = unwrapApiList<DocumentTypeDto>(list);
  if (rows.length > 0 || !props.documentTypesEnabledOnly) {
    return rows;
  }
  const fallback = await getDocumentTypeListApi({});
  return unwrapApiList<DocumentTypeDto>(fallback);
}

/** 模板分页拉全量（listForStation 不可用时的回退） */
async function fetchTemplatesByPagedList(encounterClassCode?: string): Promise<TemplateDto[]> {
  const all: TemplateDto[] = [];
  let pageNo = 1;
  while (true) {
    const res = await getTemplateListApi({
      pageNo,
      pageSize: PAGE_SIZE,
      enabled: props.templatesEnabledOnly ? true : undefined,
      encounterClassCode,
    });
    const batch = res?.content ?? [];
    all.push(...batch);
    if (batch.length === 0 || batch.length < PAGE_SIZE) break;
    pageNo += 1;
  }
  return all;
}

async function fetchTemplatesForTree(): Promise<TemplateDto[]> {
  const kind = props.stationKind;
  const encounterClassCode = resolveEncounterClassCode(kind);
  if (
    kind !== EMR_TEMPLATE_STATION_KIND.OUTPATIENT &&
    kind !== EMR_TEMPLATE_STATION_KIND.INPATIENT
  ) {
    return fetchTemplatesByPagedList(undefined);
  }

  try {
    // 医生站必须走可见性过滤（全院 + 当前登录科室 + 本人个人模板）；
    // 空列表也是合法结果，禁止回退到 getList（否则会漏科室过滤或串科室）。
    const list = await listTemplatesForStationApi({
      stationKind: kind,
      enabled: props.templatesEnabledOnly ? true : undefined,
    });
    return unwrapApiList<TemplateDto>(list);
  } catch (stationError) {
    console.warn('[EmrTemplateTree] listForStation failed, fallback to getList', stationError);
    return fetchTemplatesByPagedList(encounterClassCode);
  }
}

function resolveDocumentSortMode(): EmrDocumentSortMode {
  return props.stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT
    ? 'createdAtAsc'
    : 'updatedAtDesc';
}

function rebuildTree() {
  // 仅护士站「住院病历调阅」收窄目录；病案只读/医生站按就诊打开须展示本就诊全部文书
  const allowedCategories =
    props.restrictHistoryToInpatient && props.stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT
      ? ['INPATIENT']
      : undefined;
  const sidebarMode = props.sidebarMode ?? 'all';
  treeData.value = buildEmrTemplateTree(
    cachedTypes.value,
    sidebarMode === 'documentsOnly' ? [] : cachedTemplates.value,
    props.documents ?? [],
    sidebarMode === 'templatesOnly' ? [] : (props.historyDocuments ?? []),
    {
      allowedCategories,
      currentEncounterId: props.currentEncounterId,
      groupByDocumentType: props.stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT,
      historyRootLabel: props.historyRootLabel,
      sidebarMode,
      documentGroupBy: props.documentGroupBy,
      documentSort: resolveDocumentSortMode(),
    },
  );
}

async function syncCurrentHighlight() {
  await nextTick();
  const id = preserveApiIdForRequest(props.currentDocumentId) ?? '';
  if (!id) {
    treeRef.value?.setCurrentKey?.(undefined);
    return;
  }
  // 本次文书用 doc:，历史节点用 hist-doc:
  const docKey = `doc:${id}`;
  const histKey = `hist-doc:${id}`;
  const store = treeRef.value?.store as undefined | { nodesMap?: Record<string, unknown> };
  const nodesMap = store?.nodesMap;
  if (nodesMap?.[docKey]) {
    treeRef.value?.setCurrentKey?.(docKey);
    return;
  }
  if (nodesMap?.[histKey]) {
    treeRef.value?.setCurrentKey?.(histKey);
    return;
  }
  treeRef.value?.setCurrentKey?.(docKey);
}

async function loadTree() {
  loading.value = true;
  let partialFailure = false;
  const sidebarMode = props.sidebarMode ?? 'all';
  const skipTemplates = props.viewOnly || sidebarMode === 'documentsOnly';
  try {
    const [typesResult, templatesResult] = await Promise.allSettled([
      fetchDocumentTypesForTree(),
      // 只读 / 仅文书页：不拉可新建模板
      skipTemplates ? Promise.resolve([] as TemplateDto[]) : fetchTemplatesForTree(),
    ]);

    const typeList =
      typesResult.status === 'fulfilled'
        ? typesResult.value
        : ((partialFailure = true), console.error(typesResult.reason), []);
    const templates =
      templatesResult.status === 'fulfilled'
        ? templatesResult.value
        : ((partialFailure = true), console.error(templatesResult.reason), []);

    if (
      typeList.length === 0 &&
      templates.length === 0 &&
      !skipTemplates &&
      sidebarMode !== 'documentsOnly'
    ) {
      const reason =
        typesResult.status === 'rejected'
          ? typesResult.reason
          : templatesResult.status === 'rejected'
            ? templatesResult.reason
            : undefined;
      if (reason) {
        throw reason;
      }
    }

    cachedTypes.value = typeList;
    cachedTemplates.value = templates;
    rebuildTree();
    if (partialFailure) {
      ElMessage.warning('部分模板数据加载失败，已展示可用内容');
    }
    await nextTick();
    treeRef.value?.filter((props.filterText ?? '').trim());
    restoreExpandedState();
    await syncCurrentHighlight();
  } catch (error) {
    console.error(error);
    cachedTypes.value = [];
    cachedTemplates.value = [];
    treeData.value = [];
    ElMessage.error(sidebarMode === 'documentsOnly' ? '加载已写病历失败' : '加载病历模板树失败');
  } finally {
    loading.value = false;
  }
}

function filterNode(value: string, data: TreeNodeData, _child: Node) {
  if (!value) return true;
  const node = data as EmrTemplateTreeNode;
  const kw = value.toLowerCase();
  return (node.label ?? '').toLowerCase().includes(kw);
}

function isDocumentActive(node: EmrTemplateTreeNode): boolean {
  if (!node.isDocument || !node.document) return false;
  const id = preserveApiIdForRequest(node.document.id) ?? '';
  const current = preserveApiIdForRequest(props.currentDocumentId) ?? '';
  return Boolean(id && current && id === current);
}

/** 文书归属就诊 ≠ 当前医生站就诊 */
function isOtherEncounterDocument(doc: DocumentSummaryDto | undefined): boolean {
  if (!doc) return false;
  const current = preserveApiIdForRequest(props.currentEncounterId) ?? '';
  if (!current) return false;
  const docEnc = preserveApiIdForRequest(doc.encounterId) ?? '';
  return Boolean(docEnc && docEnc !== current);
}

/** 是否本次就诊文书 */
function isCurrentEncounterDocument(doc: DocumentSummaryDto | undefined): boolean {
  if (!doc) return false;
  const current = preserveApiIdForRequest(props.currentEncounterId) ?? '';
  if (!current) return false;
  const docEnc = preserveApiIdForRequest(doc.encounterId) ?? '';
  return Boolean(docEnc && docEnc === current);
}

/** 卡片角标：本次 / 历史（有接诊上下文时始终展示） */
function formatDocumentCardBadge(doc: DocumentSummaryDto | undefined): 'current' | 'history' | '' {
  if (!doc) return '';
  if (isCurrentEncounterDocument(doc)) return 'current';
  if (isOtherEncounterDocument(doc)) return 'history';
  return '';
}

/** 当前登录用户是否为文书创建人 */
function isDocumentCreatedByCurrentUser(doc: DocumentSummaryDto | undefined): boolean {
  if (!doc) return false;
  const creatorId = preserveApiIdForRequest(doc.createdBy) ?? '';
  const me = preserveApiIdForRequest(userStore.userInfo?.id) ?? '';
  return Boolean(creatorId && me && creatorId === me);
}

/** 是否展示右键「删除病历」：本次就诊且本人创建 */
function canDeleteDocument(node: EmrTemplateTreeNode): boolean {
  if (props.viewOnly) return false;
  if (!node.isDocument || !node.document) return false;
  if (isOtherEncounterDocument(node.document)) return false;
  const current = preserveApiIdForRequest(props.currentEncounterId) ?? '';
  const docEnc = preserveApiIdForRequest(node.document.encounterId) ?? '';
  if (!current || !docEnc || docEnc !== current) return false;
  const id = preserveApiIdForRequest(node.document.id) ?? '';
  if (!id) return false;
  return isDocumentCreatedByCurrentUser(node.document);
}

/**
 * 是否展示右键「重命名」：本次就诊、未归档/待归档；
 * 住院站与保存正文同口径（可多人改），门诊仅创建者。
 */
function canRenameDocument(node: EmrTemplateTreeNode): boolean {
  if (props.viewOnly) return false;
  if (!node.isDocument || !node.document) return false;
  if (isOtherEncounterDocument(node.document)) return false;
  const current = preserveApiIdForRequest(props.currentEncounterId) ?? '';
  const docEnc = preserveApiIdForRequest(node.document.encounterId) ?? '';
  if (!current || !docEnc || docEnc !== current) return false;
  const id = preserveApiIdForRequest(node.document.id) ?? '';
  if (!id) return false;
  const status = (node.document.status ?? '').trim();
  // 待归档/已归档标题冻结，与后端 updateTitle 门禁一致
  if (status === 'ARCHIVED' || status === 'PENDING_ARCHIVE') return false;
  if (props.stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT) return true;
  return isDocumentCreatedByCurrentUser(node.document);
}

/** 是否可「复用到本次就诊」：非本次就诊文书（非只读调阅） */
function canReuseDocument(node: EmrTemplateTreeNode): boolean {
  if (props.viewOnly) return false;
  if (!node.isDocument || !node.document) return false;
  const id = preserveApiIdForRequest(node.document.id) ?? '';
  if (!id) return false;
  return isOtherEncounterDocument(node.document);
}

/**
 * 医生站可维护的模板：个人（本人）/ 科室（当前登录科室）；全院模板请走基础数据维护。
 */
function canManageTemplate(node: EmrTemplateTreeNode): boolean {
  if (props.viewOnly) return false;
  if (!node.isTemplate || !node.template) return false;
  const id = preserveApiIdForRequest(node.template.id) ?? '';
  if (!id) return false;
  const scope = String(node.template.scopeLevel ?? '')
    .trim()
    .toUpperCase();
  if (scope === EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL) {
    const ownerId = preserveApiIdForRequest(node.template.ownerPractitionerId) ?? '';
    const me = resolveLoginPractitionerIdForRequest(
      userStore.userInfo as Record<string, unknown> | undefined,
    );
    return Boolean(ownerId && me && ownerId === me);
  }
  if (scope === EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT) {
    const orgId = preserveApiIdForRequest(node.template.organizationId) ?? '';
    const loginOrg = preserveApiIdForRequest(authStore.getSelectedOrganization()) ?? '';
    return Boolean(orgId && loginOrg && orgId === loginOrg);
  }
  return false;
}

function isDocumentOpen(node: EmrTemplateTreeNode): boolean {
  if (!node.isDocument || !node.document) return false;
  const id = preserveApiIdForRequest(node.document.id) ?? '';
  if (!id || isDocumentActive(node)) return false;
  return (props.openDocumentIds ?? []).some(
    (openId) => (preserveApiIdForRequest(openId) ?? '') === id,
  );
}

/** 门诊病历文书类型；侧栏固定显示类型名，不用主诉或模板名 */
const OUTPATIENT_RECORD_TYPE_CODE = 'OUTPATIENT_RECORD';

function isOutpatientRecordDoc(doc: DocumentSummaryDto): boolean {
  return (doc.documentTypeCode ?? '').trim() === OUTPATIENT_RECORD_TYPE_CODE;
}

/** 门诊病历侧栏文案：文书类型名，缺省「门诊病历」 */
function formatOutpatientRecordLabel(doc: DocumentSummaryDto): string {
  const typeName = (doc.documentTypeName ?? '').trim();
  if (typeName && !/^[A-Z][A-Z0-9_]*$/.test(typeName)) return typeName;
  return '门诊病历';
}

/** 卡片主行：具体文书标题优先（与打印页眉一致），勿只用类型名（告知书会糊成一片） */
function formatDocumentCardPrimary(doc: DocumentSummaryDto): string {
  if (isOutpatientRecordDoc(doc)) return formatOutpatientRecordLabel(doc);
  const rawTitle = (doc.title ?? '').trim();
  const strippedTitle = rawTitle
    .replace(/-\d{4}-\d{2}-\d{2}(?:[ T]\d{2}:\d{2}(?::\d{2})?)?.*$/u, '')
    .replace(/[（(]\s*标准\s*[）)]/g, '')
    .replace(/标准$/u, '')
    .trim();
  return (
    strippedTitle ||
    (doc.documentTypeName ?? '').trim() ||
    (doc.chiefComplaint ?? '').trim() ||
    '病历'
  );
}

/** 卡片副行主诉（门诊病历不展示） */
function formatDocumentCardChiefComplaint(doc: DocumentSummaryDto): string {
  if (isOutpatientRecordDoc(doc)) return '';
  return (doc.chiefComplaint ?? '').trim();
}

/** 卡片第二行：门诊病历固定类型名；其余展示文书标题（右键可重命名） */
function formatDocumentCardSubline(doc: DocumentSummaryDto): string {
  if (isOutpatientRecordDoc(doc)) return formatOutpatientRecordLabel(doc);
  return formatDocumentCardPrimary(doc);
}

/** 卡片时间：住院按创建时间，门诊按更新时间 */
function formatDocumentCardTime(doc: DocumentSummaryDto): string {
  return formatDocumentTreeTime(doc, resolveDocumentSortMode()) || '未保存';
}

/** 开立医生显示名 */
function formatDocumentCardDoctor(doc: DocumentSummaryDto): string {
  return formatDocumentTreeSaver(doc);
}

/** 归档状态短标；草稿不占卡片 */
function archiveStatusLabel(doc?: DocumentSummaryDto): string {
  const code = (doc?.status ?? '').trim();
  if (code === 'ARCHIVED') return '已归档';
  if (code === 'PENDING_ARCHIVE') return '待归档';
  return '';
}

/** 悬停完整信息 */
function formatDocumentCardTooltip(doc: DocumentSummaryDto): string {
  const typeName = formatDocumentCardPrimary(doc);
  const badge = formatDocumentCardBadge(doc);
  let badgeText = '';
  if (badge === 'current') badgeText = '本次';
  else if (badge === 'history') badgeText = '历史';
  const doctor = formatDocumentCardDoctor(doc);
  const parts = [
    badgeText,
    typeName,
    formatDocumentCardTime(doc),
    doctor,
    formatDocumentCardChiefComplaint(doc),
  ].filter(Boolean);
  return parts.join(' · ');
}

function handleNodeClick(
  data: EmrTemplateTreeNode,
  _node: Node,
  _nodeInstance?: unknown,
  event?: MouseEvent,
) {
  if (data.isDocumentType || data.isGroup) return;
  if (data.isDocument && data.document) {
    if (event?.ctrlKey || event?.metaKey) {
      emit('openDocumentInNewTab', data.document);
      return;
    }
    emit('selectDocument', data.document);
    return;
  }
  if (data.isTemplate || data.templateId) {
    // 只读调阅不响应模板点击（目录中也不应出现模板节点）
    if (props.viewOnly) return;
    emit('select', data);
  }
}

/** 文书右键菜单：复用 / 新标签打开 / 重命名 / 删除（仅创建人） */
async function handleDocumentCommand(
  command: string,
  doc: DocumentSummaryDto,
  node?: EmrTemplateTreeNode,
) {
  if (command === 'reuse') {
    if (node && !canReuseDocument(node)) {
      ElMessage.warning('仅历史或关联病历可复用到本次就诊');
      return;
    }
    emit('reuseDocument', doc);
    return;
  }
  if (command === 'openInNewTab') {
    emit('openDocumentInNewTab', doc);
    return;
  }
  if (command === 'rename') {
    if (node && !canRenameDocument(node)) {
      ElMessage.warning('当前病历不可重命名');
      return;
    }
    emit('renameDocument', doc);
    return;
  }
  if (command === 'delete') {
    if (node && !canDeleteDocument(node)) {
      ElMessage.warning('仅创建者可删除该病历');
      return;
    }
    const title = formatDocumentCardPrimary(doc);
    try {
      await ElMessageBox.confirm(`确定删除病历「${title}」吗？删除后列表不再显示。`, '提示', {
        cancelButtonText: '取消',
        confirmButtonText: '确定',
        type: 'warning',
      });
    } catch {
      return;
    }
    emit('deleteDocument', doc);
  }
}

/** 模板右键：重命名 / 用当前病历更新 / 删除 */
async function handleTemplateCommand(
  command: string,
  tpl: TemplateDto,
  node?: EmrTemplateTreeNode,
) {
  if (node && !canManageTemplate(node)) {
    ElMessage.warning('仅可维护本人个人模板或本登录科室的科室模板');
    return;
  }
  if (command === 'rename') {
    emit('renameTemplate', tpl);
    return;
  }
  if (command === 'updateContent') {
    if (!props.canUpdateTemplateContent) {
      ElMessage.warning('请先打开可编辑病历后再更新模板内容');
      return;
    }
    emit('updateTemplateContent', tpl);
    return;
  }
  if (command === 'delete') {
    const title = (tpl.name ?? '').trim() || (tpl.code ?? '').trim() || '模板';
    try {
      await ElMessageBox.confirm(`确定删除模板「${title}」吗？删除后左侧树不再显示。`, '提示', {
        cancelButtonText: '取消',
        confirmButtonText: '确定',
        type: 'warning',
      });
    } catch {
      return;
    }
    emit('deleteTemplate', tpl);
  }
}

watch(
  () => props.filterText,
  (val) => {
    treeRef.value?.filter((val ?? '').trim());
  },
);

watch(
  () => props.stationKind,
  () => {
    void loadTree();
  },
);

watch(
  () => [props.sidebarMode, props.documentGroupBy] as const,
  () => {
    void loadTree();
  },
);

watch(
  () => [props.documents, props.historyDocuments, props.historyRootLabel] as const,
  () => {
    rebuildTree();
    void nextTick(async () => {
      treeRef.value?.filter((props.filterText ?? '').trim());
      restoreExpandedState();
      await syncCurrentHighlight();
    });
  },
  { deep: true },
);

watch(
  () => props.currentDocumentId,
  () => {
    void syncCurrentHighlight();
  },
);

onMounted(() => {
  loadTree();
});

defineExpose({
  reload: loadTree,
});
</script>

<template>
  <div v-loading="loading" class="emr-template-tree min-h-[120px]">
    <div v-if="!loading && treeData.length === 0" class="text-text-sub py-8 text-center text-sm">
      <p v-if="sidebarMode === 'documentsOnly'">暂无已写病历</p>
      <template v-else>
        <p>暂无文书类型或模板</p>
        <p v-if="stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT" class="mt-2 text-xs">
          请在「基础数据 → 病历模板」维护住院（IMP）文书类型与模板，或联系管理员配置全院模板
        </p>
      </template>
    </div>
    <ElTree
      v-else
      ref="treeRef"
      :data="treeData"
      :props="{ children: 'children', label: 'label' }"
      :expand-on-click-node="true"
      highlight-current
      node-key="nodeKey"
      :filter-node-method="filterNode"
      @node-click="handleNodeClick"
      @node-expand="persistExpandedState"
      @node-collapse="persistExpandedState"
    >
      <template #default="{ data, node }">
        <div
          class="emr-template-tree__node"
          :class="{
            'emr-template-tree__node--type': data.isDocumentType || data.isGroup,
            'emr-template-tree__node--doc': data.isDocument,
            'emr-template-tree__node--doc-active': isDocumentActive(data),
            'emr-template-tree__node--doc-open': isDocumentOpen(data),
            'emr-template-tree__node--tpl': data.isTemplate,
          }"
        >
          <ElDropdown
            v-if="data.isDocument && data.document"
            trigger="contextmenu"
            class="emr-template-tree__doc-dropdown"
            @command="(cmd) => handleDocumentCommand(String(cmd), data.document, data)"
          >
            <div
              class="emr-template-tree__doc-card"
              :title="
                (canReuseDocument(data)
                  ? '单击打开；右键可复用到本次就诊或新标签打开'
                  : '单击打开；Ctrl+单击或右键可在新标签打开') +
                (formatDocumentCardTooltip(data.document)
                  ? `\n${formatDocumentCardTooltip(data.document)}`
                  : '')
              "
            >
              <div class="emr-template-tree__doc-line1">
                <ElIcon class="emr-template-tree__icon emr-template-tree__icon--doc">
                  <EditPen />
                </ElIcon>
                <span
                  class="emr-template-tree__doc-title"
                  :title="formatDocumentCardSubline(data.document)"
                >
                  {{ formatDocumentCardSubline(data.document) }}
                </span>
              </div>
              <div class="emr-template-tree__doc-line2">
                <span
                  v-if="archiveStatusLabel(data.document)"
                  class="emr-template-tree__doc-badge"
                  :class="
                    archiveStatusLabel(data.document) === '已归档'
                      ? 'emr-template-tree__doc-badge--archived'
                      : 'emr-template-tree__doc-badge--pending'
                  "
                >
                  {{ archiveStatusLabel(data.document) }}
                </span>
                <span
                  v-if="formatDocumentCardBadge(data.document) === 'history'"
                  class="emr-template-tree__doc-badge emr-template-tree__doc-badge--history"
                >
                  历史
                </span>
                <span class="emr-template-tree__doc-time">
                  {{ formatDocumentCardTime(data.document) }}
                </span>
                <span
                  v-if="formatDocumentCardDoctor(data.document)"
                  class="emr-template-tree__doc-doctor"
                  :title="formatDocumentCardDoctor(data.document)"
                >
                  {{ formatDocumentCardDoctor(data.document) }}
                </span>
              </div>
            </div>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem v-if="canReuseDocument(data)" command="reuse">
                  复用到本次就诊
                </ElDropdownItem>
                <ElDropdownItem command="openInNewTab">在新标签打开</ElDropdownItem>
                <ElDropdownItem v-if="canRenameDocument(data)" command="rename">
                  重命名
                </ElDropdownItem>
                <ElDropdownItem v-if="canDeleteDocument(data)" command="delete" divided>
                  删除病历
                </ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>
          <ElDropdown
            v-else-if="data.isTemplate && data.template && canManageTemplate(data)"
            trigger="contextmenu"
            class="emr-template-tree__tpl-dropdown"
            @command="(cmd) => handleTemplateCommand(String(cmd), data.template, data)"
          >
            <div
              class="emr-template-tree__tpl-row"
              :title="'单击使用该模板新建；右键可重命名、更新内容或删除'"
            >
              <ElIcon class="emr-template-tree__icon emr-template-tree__icon--tpl">
                <Tickets />
              </ElIcon>
              <span class="emr-template-tree__label">{{ data.label }}</span>
            </div>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem command="rename">重命名</ElDropdownItem>
                <ElDropdownItem command="updateContent" :disabled="!canUpdateTemplateContent">
                  用当前病历更新内容
                </ElDropdownItem>
                <ElDropdownItem command="delete" divided>删除模板</ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>
          <template v-else>
            <ElIcon
              class="emr-template-tree__icon"
              :class="{
                'emr-template-tree__icon--type': data.isDocumentType || data.isGroup,
                'emr-template-tree__icon--tpl': data.isTemplate,
              }"
            >
              <FolderOpened v-if="(data.isDocumentType || data.isGroup) && node.expanded" />
              <Folder v-else-if="data.isDocumentType || data.isGroup" />
              <Tickets v-else-if="data.isTemplate" />
              <Document v-else />
            </ElIcon>
            <span
              class="emr-template-tree__label"
              :title="data.isTemplate ? '单击使用该模板新建' : undefined"
            >
              {{ data.label }}
            </span>
          </template>
        </div>
      </template>
    </ElTree>
  </div>
</template>

<style scoped lang="scss">
.emr-template-tree {
  :deep(.el-tree) {
    --el-tree-node-indent: 10px;
  }

  :deep(.el-tree-node__content) {
    height: auto;
    min-height: 28px;
    align-items: flex-start;
    padding-top: 2px;
    padding-bottom: 2px;
  }

  /* 已写文书用卡片表达选中态，避免与树行高亮叠色 */
  :deep(.el-tree-node.is-current > .el-tree-node__content) {
    background-color: transparent;
  }
}

.emr-template-tree__node {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: flex-start;
  gap: 6px;
  padding-right: 4px;
}

.emr-template-tree__node--type,
.emr-template-tree__node--tpl {
  align-items: center;
  min-height: 28px;
}

.emr-template-tree__icon {
  flex-shrink: 0;
  margin-top: 1px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.emr-template-tree__icon--type {
  color: var(--el-color-warning);
}

.emr-template-tree__icon--tpl {
  color: var(--el-color-primary);
}

.emr-template-tree__icon--doc {
  margin-top: 0;
  color: var(--el-color-success);
}

.emr-template-tree__node--doc {
  width: 100%;
  margin: 1px 0;
}

.emr-template-tree__doc-dropdown {
  display: block;
  width: 100%;
  min-width: 0;
}

.emr-template-tree__tpl-dropdown {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
}

.emr-template-tree__tpl-row {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.emr-template-tree__doc-card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  min-width: 0;
  padding: 5px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease,
    box-shadow 0.15s ease;

  &:hover {
    border-color: var(--el-color-primary-light-5);
    background: var(--el-color-primary-light-9);
  }
}

.emr-template-tree__node--doc-active .emr-template-tree__doc-card {
  border-color: var(--el-color-primary-light-3);
  background: var(--el-color-primary-light-9);
  box-shadow: inset 2px 0 0 var(--el-color-primary);
}

.emr-template-tree__node--doc-open .emr-template-tree__doc-card {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-fill-color-light);
}

.emr-template-tree__doc-line1 {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.emr-template-tree__doc-title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.emr-template-tree__node--doc-active .emr-template-tree__doc-title {
  color: var(--el-color-primary);
}

.emr-template-tree__doc-line2 {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding-left: 20px;
}

.emr-template-tree__doc-time {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 400;
  line-height: 1.35;
  color: var(--el-text-color-secondary);
}

.emr-template-tree__doc-doctor {
  min-width: 0;
  overflow: hidden;
  font-size: 11px;
  font-weight: 400;
  line-height: 1.35;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.emr-template-tree__doc-badge {
  flex-shrink: 0;
  padding: 0 4px;
  font-size: 10px;
  font-weight: 500;
  line-height: 1.4;
  border-radius: 3px;
}

.emr-template-tree__doc-badge--current {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
  border: 1px solid var(--el-color-success-light-5);
}

.emr-template-tree__doc-badge--history {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
}

.emr-template-tree__doc-badge--archived {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
  border: 1px solid var(--el-color-success-light-5);
}

.emr-template-tree__doc-badge--pending {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
}

.emr-template-tree__label {
  display: block;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 1.4;
}

.emr-template-tree__node--tpl .emr-template-tree__label {
  color: var(--el-text-color-primary);
}

.emr-template-tree__node--type .emr-template-tree__label {
  font-weight: 500;
  color: var(--el-text-color-primary);
}
</style>
