<script setup lang="ts">
import type Node from 'element-plus/es/components/tree/src/model/node';
import type { TreeNodeData } from 'element-plus/es/components/tree/src/tree.type';

import type { EmrTemplateTreeNode } from './build-emr-template-tree';

import type { DocumentSummaryDto } from '#/api/emr/document';
import type { DocumentTypeDto } from '#/api/emr/document-type';
import type { TemplateDto } from '#/api/emr/template';
import type { EmrTemplateStationKind } from '#/views/basic/emr-template/emr-template-scope.constants';

import { nextTick, onMounted, ref, watch } from 'vue';

import { Document, EditPen, Folder, FolderOpened, Tickets } from '@element-plus/icons-vue';
import {
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElIcon,
  ElMessage,
  ElTree,
} from 'element-plus';

import { getDocumentTypeListApi } from '#/api/emr/document-type';
import { getTemplateListApi, listTemplatesForStationApi } from '#/api/emr/template';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  EMR_TEMPLATE_ENCOUNTER_CLASS,
  EMR_TEMPLATE_STATION_KIND,
} from '#/views/basic/emr-template/emr-template-scope.constants';

import {
  buildEmrTemplateTree,
  formatDocumentTreeDept,
  formatDocumentTreeSaver,
  formatDocumentTreeTime,
} from './build-emr-template-tree';

defineOptions({
  name: 'EmrTemplateTree',
});

const props = withDefaults(
  defineProps<{
    /** 当前激活文书 ID */
    currentDocumentId?: string;
    /** 当前医生站就诊 ID；用于标记关联对端文书 */
    currentEncounterId?: string;
    /** 当前患者下已有文书（挂到对应模板下） */
    documents?: DocumentSummaryDto[];
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
  }>(),
  {
    currentDocumentId: '',
    currentEncounterId: '',
    documents: () => [],
    documentTypesEnabledOnly: true,
    filterText: '',
    openDocumentIds: () => [],
    templatesEnabledOnly: true,
    stationKind: undefined,
  },
);

const emit = defineEmits<{
  select: [node: EmrTemplateTreeNode];
  selectDocument: [row: DocumentSummaryDto];
  openDocumentInNewTab: [row: DocumentSummaryDto];
}>();

const treeRef = ref<InstanceType<typeof ElTree>>();
const loading = ref(false);
const treeData = ref<EmrTemplateTreeNode[]>([]);
const cachedTypes = ref<DocumentTypeDto[]>([]);
const cachedTemplates = ref<TemplateDto[]>([]);

const PAGE_SIZE = 200;

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
    const list = await listTemplatesForStationApi({
      stationKind: kind,
      enabled: props.templatesEnabledOnly ? true : undefined,
    });
    const rows = unwrapApiList<TemplateDto>(list);
    if (rows.length > 0) {
      return rows;
    }
    return fetchTemplatesByPagedList(encounterClassCode);
  } catch (stationError) {
    console.warn('[EmrTemplateTree] listForStation failed, fallback to getList', stationError);
    return fetchTemplatesByPagedList(encounterClassCode);
  }
}

function rebuildTree() {
  treeData.value = buildEmrTemplateTree(
    cachedTypes.value,
    cachedTemplates.value,
    props.documents ?? [],
  );
}

/** 全部展开（data 刷新后 default-expand-all 不会再生效） */
function expandAllNodes() {
  void nextTick(() => {
    const store = treeRef.value?.store as
      | undefined
      | { nodesMap?: Record<string, { expanded: boolean }> };
    const nodesMap = store?.nodesMap;
    if (!nodesMap) return;
    for (const node of Object.values(nodesMap)) {
      node.expanded = true;
    }
  });
}

async function syncCurrentHighlight() {
  await nextTick();
  const id = preserveApiIdForRequest(props.currentDocumentId) ?? '';
  if (!id) {
    treeRef.value?.setCurrentKey?.(undefined);
    return;
  }
  treeRef.value?.setCurrentKey?.(`doc:${id}`);
}

async function loadTree() {
  loading.value = true;
  let partialFailure = false;
  try {
    const [typesResult, templatesResult] = await Promise.allSettled([
      fetchDocumentTypesForTree(),
      fetchTemplatesForTree(),
    ]);

    const typeList =
      typesResult.status === 'fulfilled'
        ? typesResult.value
        : ((partialFailure = true), console.error(typesResult.reason), []);
    const templates =
      templatesResult.status === 'fulfilled'
        ? templatesResult.value
        : ((partialFailure = true), console.error(templatesResult.reason), []);

    if (typeList.length === 0 && templates.length === 0) {
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
    expandAllNodes();
    await syncCurrentHighlight();
  } catch (error) {
    console.error(error);
    cachedTypes.value = [];
    cachedTemplates.value = [];
    treeData.value = [];
    ElMessage.error('加载病历模板树失败');
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

/** 文书归属就诊 ≠ 当前医生站就诊 → 关联对端文书 */
function isLinkedDocument(doc: DocumentSummaryDto | undefined): boolean {
  if (!doc) return false;
  const current = preserveApiIdForRequest(props.currentEncounterId) ?? '';
  if (!current) return false;
  const docEnc = preserveApiIdForRequest(doc.encounterId) ?? '';
  return Boolean(docEnc && docEnc !== current);
}

function isDocumentOpen(node: EmrTemplateTreeNode): boolean {
  if (!node.isDocument || !node.document) return false;
  const id = preserveApiIdForRequest(node.document.id) ?? '';
  if (!id || isDocumentActive(node)) return false;
  return (props.openDocumentIds ?? []).some(
    (openId) => (preserveApiIdForRequest(openId) ?? '') === id,
  );
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
    emit('select', data);
  }
}

function handleDocumentCommand(command: string, doc: DocumentSummaryDto) {
  if (command === 'openInNewTab') {
    emit('openDocumentInNewTab', doc);
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
  () => props.documents,
  () => {
    rebuildTree();
    void nextTick(async () => {
      treeRef.value?.filter((props.filterText ?? '').trim());
      expandAllNodes();
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
      <p>暂无文书类型或模板</p>
      <p v-if="stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT" class="mt-2 text-xs">
        请在「基础数据 → 病历模板」维护住院（IMP）文书类型与模板，或联系管理员配置全院模板
      </p>
    </div>
    <ElTree
      v-else
      ref="treeRef"
      :data="treeData"
      :props="{ children: 'children', label: 'label' }"
      default-expand-all
      :expand-on-click-node="false"
      highlight-current
      node-key="nodeKey"
      :filter-node-method="filterNode"
      @node-click="handleNodeClick"
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
            @command="(cmd) => handleDocumentCommand(String(cmd), data.document)"
          >
            <div
              class="emr-template-tree__doc-card"
              :title="'单击打开；Ctrl+单击或右键可在新标签打开'"
            >
              <ElIcon class="emr-template-tree__icon emr-template-tree__icon--doc">
                <EditPen />
              </ElIcon>
              <div class="emr-template-tree__doc-body">
                <div class="emr-template-tree__doc-time">
                  {{ formatDocumentTreeTime(data.document) || '未保存' }}
                  <span
                    v-if="isLinkedDocument(data.document)"
                    class="emr-template-tree__doc-linked"
                  >
                    关联
                  </span>
                </div>
                <div
                  v-if="formatDocumentTreeDept(data.document)"
                  class="emr-template-tree__doc-meta"
                >
                  {{ formatDocumentTreeDept(data.document) }}
                </div>
                <div
                  v-if="formatDocumentTreeSaver(data.document)"
                  class="emr-template-tree__doc-meta"
                >
                  {{ formatDocumentTreeSaver(data.document) }}
                </div>
              </div>
            </div>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem command="openInNewTab">在新标签打开</ElDropdownItem>
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
    --el-tree-node-indent: 8px;
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
  margin-top: 2px;
  color: var(--el-color-success);
}

.emr-template-tree__node--doc {
  width: 100%;
  margin: 2px 0;
}

.emr-template-tree__doc-dropdown {
  display: block;
  width: 100%;
  min-width: 0;
}

.emr-template-tree__doc-card {
  display: flex;
  gap: 6px;
  width: 100%;
  min-width: 0;
  padding: 6px 8px;
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

.emr-template-tree__doc-body {
  flex: 1;
  min-width: 0;
}

.emr-template-tree__doc-time {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--el-text-color-primary);
  word-break: break-all;
  white-space: normal;
}

.emr-template-tree__doc-linked {
  margin-left: 6px;
  padding: 0 4px;
  font-size: 10px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--el-color-warning);
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: 3px;
  vertical-align: middle;
}

.emr-template-tree__doc-meta {
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.35;
  color: var(--el-text-color-secondary);
  word-break: break-all;
  white-space: normal;
}

.emr-template-tree__node--doc-active .emr-template-tree__doc-time {
  color: var(--el-color-primary);
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
