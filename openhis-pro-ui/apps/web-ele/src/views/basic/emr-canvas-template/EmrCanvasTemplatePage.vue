<script setup lang="ts">
import type { FormItemConfig, FormItemOption, TableColumn } from '@zxplat-core/table-layout-ui';

import type { DocumentTypeDto } from '#/api/emr/document-type';
import type { QueryTemplateParams, TemplateDto } from '#/api/emr/template';

import { computed, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import {
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElMessage,
  ElMessageBox,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import { deleteDocumentTypeApi, getDocumentTypeListApi } from '#/api/emr/document-type';
import { deleteTemplateApi, getTemplateListApi } from '#/api/emr/template';
import { useRequest } from '#/hooks/use-request';

import { BASIC_TABLE_MAX_HEIGHT } from '#/views/basic/basic-table-max-height';
import DocumentTypeEditDialog from '#/views/basic/emr-template/document-type-edit-dialog.vue';
import {
  EMR_TEMPLATE_ENCOUNTER_CLASS_OPTIONS,
  EMR_TEMPLATE_SCOPE_LEVEL,
  EMR_TEMPLATE_SCOPE_LEVEL_OPTIONS,
  formatEncounterClassLabel,
  formatTemplateScopeTag,
} from '#/views/basic/emr-template/emr-template-scope.constants';
import MetadataFieldTab from '#/views/basic/emr-template/metadata-field-tab.vue';
import { purgeLegacyEmrTemplateCaches } from '#/views/basic/emr-template/purge-legacy-emr-template-cache';
import EditDialog from '#/views/basic/emr-canvas-template/edit-dialog.vue';

defineOptions({
  name: 'EmrCanvasTemplatePage',
});

/** 主区 Tab：病历模板 | 元数据字段 */
const mainTab = ref<'metadata' | 'template'>('template');

/** 当前页模板列表 */
const templateList = ref<TemplateDto[]>([]);
/** 分页总条数 */
const total = ref(0);

/** 文书类型全量，供左侧列表与弹窗下拉 */
const documentTypeList = ref<DocumentTypeDto[]>([]);
/** 当前选中的文书类型编码（左栏唯一上下文） */
const selectedTypeCode = ref<string | undefined>(undefined);
/** 各文书类型下模板条数（全院范围快照） */
const typeCountMap = ref<Record<string, number>>({});
const sideLoading = ref(false);

/** 新建 / 编辑文书类型 */
const documentTypeEditRef = ref<InstanceType<typeof DocumentTypeEditDialog> | null>(null);

/** 新建 / 编辑 / 查看 / 复制 模板弹窗 */
const editDialogRef = ref<InstanceType<typeof EditDialog> | null>(null);

/** 列表 / 删除加载态 */
const tableLoading = computed(() => listLoading.value || deleteLoading.value);

const { run: runList, loading: listLoading } = useRequest(getTemplateListApi, {
  manual: true,
});
const { run: runDelete, loading: deleteLoading } = useRequest(deleteTemplateApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('删除成功');
    getList();
    refreshTypeCounts();
  },
});

/** 默认全院；文书类型由左侧列表带入 */
const queryParams = reactive<{
  documentTypeCode?: string;
  scopeLevel?: string;
  encounterClassCode?: string;
  enabled?: boolean;
  pageNo: number;
  pageSize: number;
  keyword?: string;
}>({
  pageNo: 1,
  pageSize: 50,
  scopeLevel: EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL,
});

/** 顶部查询：文书类型由左栏筛选，这里不再重复 */
const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '名称 / 编码',
  },
  {
    label: '适用就诊',
    prop: 'encounterClassCode',
    type: 'select',
    options: [{ label: '全部', value: '' }, ...EMR_TEMPLATE_ENCOUNTER_CLASS_OPTIONS],
    clearable: true,
    multiple: false,
  },
  {
    label: '使用范围',
    prop: 'scopeLevel',
    type: 'select',
    options: [...EMR_TEMPLATE_SCOPE_LEVEL_OPTIONS],
    clearable: false,
    multiple: false,
  },
  {
    label: '状态',
    prop: 'enabled',
    type: 'select',
    options: [
      { label: '全部', value: undefined },
      { label: '启用', value: true },
      { label: '停用', value: false },
    ],
    clearable: true,
    multiple: false,
  },
]);

/** 按排序号、名称排出的左栏列表 */
const sortedDocumentTypes = computed(() => {
  return [...documentTypeList.value].sort((a, b) => {
    const sa = a.sortOrder ?? 0;
    const sb = b.sortOrder ?? 0;
    if (sa !== sb) return sa - sb;
    return String(a.name ?? '').localeCompare(String(b.name ?? ''), 'zh');
  });
});

/** 弹窗内文书类型下拉 */
const docTypeSelectOptions = computed<FormItemOption[]>(() =>
  documentTypeList.value
    .filter((d) => d.code)
    .map((d) => ({
      label: d.name ?? String(d.code),
      value: String(d.code),
    })),
);

/** 右侧标题用当前文书类型名称 */
const selectedTypeTitle = computed(() => {
  const code = selectedTypeCode.value;
  if (!code) return '病历模板';
  const hit = documentTypeList.value.find((item) => String(item.code) === code);
  return hit?.name?.trim() || code;
});

/** 表格列与操作列 slot */
const tableColumns: TableColumn[] = [
  {
    prop: 'name',
    label: '模板名称',
    align: 'left',
    minWidth: 180,
    showOverflowTooltip: true,
  },
  {
    label: '适用就诊',
    align: 'center',
    width: 88,
    slot: 'encounterClass',
  },
  {
    label: '适用科室',
    align: 'center',
    width: 140,
    slot: 'dept',
    showOverflowTooltip: true,
  },
  {
    label: '使用范围',
    align: 'center',
    width: 100,
    slot: 'scopeLevel',
  },
  {
    prop: 'updatedAt',
    label: '更新时间',
    align: 'center',
    width: 180,
  },
  {
    prop: 'isEnabled',
    label: '状态',
    align: 'center',
    width: 100,
    slot: 'enabled',
  },
  { align: 'left', fixed: 'right', label: '操作', slot: 'actions', width: 220 },
];

/** 拉取文书类型并默认选中第一项 */
async function loadDocumentTypes() {
  sideLoading.value = true;
  try {
    const list = await getDocumentTypeListApi({});
    documentTypeList.value = Array.isArray(list) ? list : [];
    editDialogRef.value?.setDocumentTypeOptions(docTypeSelectOptions.value);
    ensureSelectedType();
    await Promise.all([getList(), refreshTypeCounts()]);
  } catch (error) {
    console.error(error);
    documentTypeList.value = [];
    selectedTypeCode.value = undefined;
    ElMessage.error('加载文书类型失败');
    await getList();
  } finally {
    sideLoading.value = false;
  }
}

/**
 * 选中项失效时落到第一项。
 * 左栏始终保持一个类型选中，与右侧列表一一对应。
 */
function ensureSelectedType() {
  const list = sortedDocumentTypes.value.filter((item) => item.code);
  if (list.length === 0) {
    selectedTypeCode.value = undefined;
    queryParams.documentTypeCode = undefined;
    return;
  }
  const stillExists = list.some((item) => String(item.code) === selectedTypeCode.value);
  if (!stillExists) {
    selectedTypeCode.value = String(list[0]?.code ?? '');
  }
  queryParams.documentTypeCode = selectedTypeCode.value;
  queryParams.pageNo = 1;
}

/** 按全院范围统计各文书类型条数，给左栏角标 */
async function refreshTypeCounts() {
  const types = documentTypeList.value.filter((item) => item.code);
  const entries = await Promise.all(
    types.map(async (item) => {
      const code = String(item.code);
      try {
        const res = await getTemplateListApi({
          pageNo: 1,
          pageSize: 1,
          documentTypeCode: code,
          scopeLevel: EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL,
        });
        return [code, Number(res?.totalCount ?? 0)] as const;
      } catch {
        return [code, 0] as const;
      }
    }),
  );
  const next: Record<string, number> = {};
  for (const [code, count] of entries) {
    next[code] = count;
  }
  typeCountMap.value = next;
}

/**
 * 查询当前页列表。
 * 默认带全院范围；空串字段不传，避免后端当成有效条件。
 */
async function getList() {
  try {
    const params: QueryTemplateParams = {
      documentTypeCode: queryParams.documentTypeCode || undefined,
      scopeLevel: queryParams.scopeLevel || undefined,
      encounterClassCode: queryParams.encounterClassCode || undefined,
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
      keyword: queryParams.keyword,
    };
    if (typeof queryParams.enabled === 'boolean') {
      params.enabled = queryParams.enabled;
    }
    Object.keys(params).forEach((key) => {
      const v = params[key as keyof QueryTemplateParams];
      if (v === undefined || v === '') {
        delete params[key as keyof QueryTemplateParams];
      }
    });

    const res = await runList(params);
    templateList.value = res?.content ?? [];
    total.value = Number(res?.totalCount ?? 0);
  } catch (error) {
    console.error(error);
    templateList.value = [];
    total.value = 0;
  }
}

/** 清空筛选，范围回到全院；左栏当前文书类型保留 */
function handleReset() {
  queryParams.keyword = undefined;
  queryParams.encounterClassCode = undefined;
  queryParams.scopeLevel = EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL;
  queryParams.enabled = undefined;
  queryParams.pageNo = 1;
  getList();
}

/** 翻页或改每页条数 */
function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  getList();
}

/** 左栏点选文书类型，刷新右侧列表 */
function selectDocumentType(item: DocumentTypeDto) {
  const code = item.code?.trim();
  if (!code || code === selectedTypeCode.value) return;
  selectedTypeCode.value = code;
  queryParams.documentTypeCode = code;
  queryParams.pageNo = 1;
  getList();
}

/** 左栏更多菜单：编辑或删除文书类型 */
function handleTypeCommand(command: string, item: DocumentTypeDto) {
  if (command === 'edit') {
    documentTypeEditRef.value?.openEdit(item);
    return;
  }
  if (command === 'delete') {
    handleDeleteDocumentType(item);
  }
}

/** 空白新建模板，带入当前左栏文书类型 */
function openCreate() {
  editDialogRef.value?.open({
    defaultDocumentTypeCode: selectedTypeCode.value,
    documentTypeOptions: docTypeSelectOptions.value,
    mode: 'create',
  });
}

/** 编辑：拉详情并允许保存更新 */
function openEdit(row: TemplateDto) {
  editDialogRef.value?.open({
    documentTypeOptions: docTypeSelectOptions.value,
    mode: 'edit',
    row,
  });
}

/** 查看：只读，仍可点弹窗内「复制为新模板」 */
function openView(row: TemplateDto) {
  editDialogRef.value?.open({
    documentTypeOptions: docTypeSelectOptions.value,
    mode: 'view',
    row,
  });
}

/** 列表复制：子组件拉取正文后切新建并预填 */
function handleCopyRow(row: TemplateDto) {
  editDialogRef.value?.open({
    documentTypeOptions: docTypeSelectOptions.value,
    mode: 'copy',
    row,
  });
}

/** 二次确认后删除模板 */
function handleDelete(row: TemplateDto) {
  const id = row.id;
  if (id === undefined || id === null) return;
  ElMessageBox.confirm('确定删除该病历模板吗？', '提示', {
    cancelButtonText: '取消',
    confirmButtonText: '确定',
    type: 'warning',
  }).then(() => {
    runDelete({ id: String(id) });
  });
}

/** 二次确认后删除文书类型 */
async function handleDeleteDocumentType(row: DocumentTypeDto) {
  const id = row.id;
  if (id === undefined || id === null) return;
  try {
    await ElMessageBox.confirm(`确定删除文书类型「${row.name ?? row.code}」吗？`, '提示', {
      cancelButtonText: '取消',
      confirmButtonText: '确定',
      type: 'warning',
    });
    await deleteDocumentTypeApi({ id: String(id) });
    ElMessage.success('删除成功');
    if (selectedTypeCode.value && selectedTypeCode.value === row.code) {
      selectedTypeCode.value = undefined;
    }
    await loadDocumentTypes();
  } catch (error: unknown) {
    if (error === 'cancel') return;
    console.error(error);
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err.msg || err.message || '删除失败');
  }
}

/** 列表行是否启用 */
function isRowEnabled(row: TemplateDto) {
  return row.isEnabled !== false && row.enabled !== false;
}

/** 适用就诊：门诊 / 住院 / 留观 */
function formatEncounterClass(row: TemplateDto) {
  return row.encounterClassName || formatEncounterClassLabel(row.encounterClassCode) || '—';
}

/** 适用科室：科室范围用组织名，否则用范围文案 */
function formatDept(row: TemplateDto) {
  const org = row.organizationName?.trim();
  if (org) return org;
  return row.scopeLevelName || formatTemplateScopeTag(row.scopeLevel) || '全院';
}

/** 左栏角标数字 */
function typeCount(item: DocumentTypeDto) {
  const code = String(item.code ?? '');
  return typeCountMap.value[code] ?? 0;
}

/** 模板保存成功后刷新列表与角标 */
function handleTemplateSaved() {
  getList();
  refreshTypeCounts();
}

purgeLegacyEmrTemplateCaches();
loadDocumentTypes();
</script>

<template>
  <Page auto-content-height>
    <div class="emr-canvas-template-page">
      <ElTabs v-model="mainTab" class="emr-canvas-template-page__tabs">
        <ElTabPane label="病历模板" name="template">
          <div class="emr-canvas-template-page__workspace">
            <aside class="emr-canvas-template-page__side" v-loading="sideLoading">
              <header class="emr-canvas-template-page__side-head">
                <span class="emr-canvas-template-page__side-title">文书类型</span>
                <ElButton
                  link
                  type="primary"
                  @click="documentTypeEditRef?.openCreate()"
                >
                  + 新增
                </ElButton>
              </header>
              <ul class="emr-canvas-template-page__type-list">
                <li
                  v-for="item in sortedDocumentTypes"
                  :key="String(item.code ?? item.id)"
                  class="emr-canvas-template-page__type-item"
                  :class="{
                    'is-active': String(item.code) === selectedTypeCode,
                  }"
                  @click="selectDocumentType(item)"
                >
                  <span class="emr-canvas-template-page__type-name">
                    {{ item.name ?? item.code }}
                    <span v-if="item.isEnabled === false">（停）</span>
                  </span>
                  <span class="emr-canvas-template-page__type-ops" @click.stop>
                    <ElDropdown
                      trigger="click"
                      @command="(command: string) => handleTypeCommand(command, item)"
                    >
                      <span class="emr-canvas-template-page__type-more">⋯</span>
                      <template #dropdown>
                        <ElDropdownMenu>
                          <ElDropdownItem command="edit">编辑</ElDropdownItem>
                          <ElDropdownItem command="delete">删除</ElDropdownItem>
                        </ElDropdownMenu>
                      </template>
                    </ElDropdown>
                  </span>
                  <span class="emr-canvas-template-page__type-count">
                    {{ typeCount(item) }}
                  </span>
                </li>
              </ul>
            </aside>

            <section class="emr-canvas-template-page__main-card">
              <header class="emr-canvas-template-page__main-head">
                <div class="emr-canvas-template-page__main-titles">
                  <h2 class="emr-canvas-template-page__main-title">
                    {{ selectedTypeTitle }} - 模板列表
                  </h2>
                  <p class="emr-canvas-template-page__main-desc">
                    管理该类型下的所有具体文书模板，可进行编辑、复制或停用操作。
                  </p>
                </div>
                <div class="emr-canvas-template-page__main-actions">
                  <ElButton type="primary" @click="openCreate">新建模板</ElButton>
                </div>
              </header>

              <TableLayout
                :form-items="formItems"
                :loading="tableLoading"
                :query-params="queryParams"
                :show-pagination="true"
                :show-top-query="true"
                :max-height="BASIC_TABLE_MAX_HEIGHT"
                :table-columns="tableColumns"
                :table-data="templateList"
                :toolbar-config="{
                  custom: false,
                  export: false,
                  refresh: false,
                  zoom: false,
                  enabled: false,
                }"
                :total="total"
                border
                stripe
                size="default"
                row-key="id"
                @pagination="handlePagination"
                @query="getList"
                @reset="handleReset"
              >
                <template #encounterClass="{ row }">
                  {{ formatEncounterClass(row) }}
                </template>
                <template #dept="{ row }">
                  {{ formatDept(row) }}
                </template>
                <template #scopeLevel="{ row }">
                  {{ row.scopeLevelName || formatTemplateScopeTag(row.scopeLevel) || '—' }}
                </template>
                <template #enabled="{ row }">
                  <ElTag
                    :type="isRowEnabled(row) ? 'success' : 'info'"
                    size="small"
                    effect="light"
                    round
                  >
                    {{ isRowEnabled(row) ? '已启用' : '已停用' }}
                  </ElTag>
                </template>
                <template #actions="{ row }">
                  <ElButton link type="primary" @click="openView(row)">查看</ElButton>
                  <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
                  <ElButton link type="primary" @click="handleCopyRow(row)">复制</ElButton>
                  <ElButton link type="danger" @click="handleDelete(row)">删除</ElButton>
                </template>
              </TableLayout>
            </section>
          </div>
        </ElTabPane>
        <ElTabPane label="元数据字段" name="metadata" lazy>
          <MetadataFieldTab />
        </ElTabPane>
      </ElTabs>
    </div>
    <EditDialog ref="editDialogRef" @success="handleTemplateSaved" />
    <DocumentTypeEditDialog ref="documentTypeEditRef" @success="loadDocumentTypes" />
  </Page>
</template>

<style scoped>
.emr-canvas-template-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.emr-canvas-template-page__tabs {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.emr-canvas-template-page__tabs :deep(.el-tabs__header) {
  margin: 0 0 8px;
}

.emr-canvas-template-page__tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
}

.emr-canvas-template-page__tabs :deep(.el-tab-pane) {
  height: 100%;
}

.emr-canvas-template-page__workspace {
  display: flex;
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.emr-canvas-template-page__side {
  display: flex;
  flex-direction: column;
  width: 240px;
  min-width: 220px;
  height: 100%;
  padding: 12px 10px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.emr-canvas-template-page__side-head {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding: 0 6px 10px;
}

.emr-canvas-template-page__side-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.emr-canvas-template-page__type-list {
  flex: 1;
  min-height: 0;
  margin: 0;
  padding: 0;
  overflow: auto;
  list-style: none;
}

.emr-canvas-template-page__type-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  color: var(--el-text-color-primary);
}

.emr-canvas-template-page__type-item:hover {
  background: var(--el-fill-color-light);
}

.emr-canvas-template-page__type-item.is-active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.emr-canvas-template-page__type-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.emr-canvas-template-page__type-ops {
  display: none;
  flex-shrink: 0;
}

.emr-canvas-template-page__type-item:hover .emr-canvas-template-page__type-ops {
  display: inline-flex;
}

.emr-canvas-template-page__type-more {
  padding: 0 4px;
  font-size: 16px;
  line-height: 1;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.emr-canvas-template-page__type-count {
  flex-shrink: 0;
  min-width: 22px;
  padding: 0 7px;
  font-size: 12px;
  line-height: 20px;
  text-align: center;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
  border-radius: 10px;
}

.emr-canvas-template-page__type-item.is-active .emr-canvas-template-page__type-count {
  color: var(--el-text-color-regular);
  background: var(--el-bg-color);
}

.emr-canvas-template-page__main-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  padding: 16px 16px 8px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.emr-canvas-template-page__main-head {
  display: flex;
  flex-shrink: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.emr-canvas-template-page__main-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  color: var(--el-text-color-primary);
}

.emr-canvas-template-page__main-desc {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--el-text-color-regular);
}

.emr-canvas-template-page__main-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.emr-canvas-template-page__main-card :deep(.table-layout) {
  flex: 1;
  min-height: 0;
}
</style>
