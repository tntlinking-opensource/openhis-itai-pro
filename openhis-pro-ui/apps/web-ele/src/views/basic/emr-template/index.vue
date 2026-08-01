<script setup lang="ts">
import type { FormItemConfig, FormItemOption, TableColumn } from '@vben-core/table-layout-ui';

import type { DocumentTypeDto } from '#/api/emr/document-type';
import type { QueryTemplateParams, TemplateDto } from '#/api/emr/template';

import { computed, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTabPane, ElTabs, ElTag } from 'element-plus';

import { deleteTemplateApi, getTemplateListApi } from '#/api/emr/template';
import { useRequest } from '#/hooks/use-request';

import DocumentTypePanel from './document-type-panel.vue';
import EditDialog from './edit-dialog.vue';
import {
  EMR_TEMPLATE_ENCOUNTER_CLASS_OPTIONS,
  EMR_TEMPLATE_SCOPE_LEVEL_OPTIONS,
  formatEncounterClassLabel,
  formatTemplateScopeTag,
} from './emr-template-scope.constants';
import MetadataFieldTab from './metadata-field-tab.vue';
import { purgeLegacyEmrTemplateCaches } from './purge-legacy-emr-template-cache';

defineOptions({
  name: 'EmrTemplate',
});

/** 主区 Tab：病历模板 | 元数据字段 */
const mainTab = ref<'metadata' | 'template'>('template');

/** 当前页模板列表 */
const templateList = ref<TemplateDto[]>([]);
/** 分页总条数 */
const total = ref(0);

/** 文书类型字典：顶部筛选 + 弹窗下拉共用 */
const documentTypeList = ref<DocumentTypeDto[]>([]);

/** 左侧文书类型面板 */
const documentTypePanelRef = ref<InstanceType<typeof DocumentTypePanel> | null>(null);

/** 新建 / 编辑 / 查看 / 复制 弹窗 */
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
  },
});

/** 与 TableLayout 查询区、分页绑定的参数 */
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
});

/** 顶部筛选「文书类型」：首项为「全部」 */
const docTypeFilterOptions = computed<FormItemOption[]>(() => [
  { label: '全部', value: '' },
  ...documentTypeList.value
    .filter((d) => d.code)
    .map((d) => ({
      label: d.name ?? String(d.code),
      value: String(d.code),
    })),
]);

/** 弹窗内文书类型下拉（不含「全部」） */
const docTypeSelectOptions = computed<FormItemOption[]>(() =>
  documentTypeList.value
    .filter((d) => d.code)
    .map((d) => ({
      label: d.name ?? String(d.code),
      value: String(d.code),
    })),
);

/** 顶部查询表单项配置 */
const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '名称 / 编码',
  },
  {
    label: '文书类型',
    prop: 'documentTypeCode',
    type: 'select',
    options: docTypeFilterOptions.value,
    clearable: true,
    filterable: true,
    multiple: false,
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
    options: [{ label: '全部', value: '' }, ...EMR_TEMPLATE_SCOPE_LEVEL_OPTIONS],
    clearable: true,
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

/** 表格列与操作列 slot */
const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 60, align: 'center' },
  {
    prop: 'name',
    label: '模板名称',
    align: 'left',
    minWidth: 160,
    showOverflowTooltip: true,
  },
  {
    prop: 'code',
    label: '模板编码',
    align: 'center',
    width: 140,
    showOverflowTooltip: true,
  },
  {
    label: '文书类型',
    align: 'center',
    width: 120,
    slot: 'documentType',
    showOverflowTooltip: true,
  },
  {
    label: '适用就诊',
    align: 'center',
    width: 88,
    slot: 'encounterClass',
  },
  {
    label: '使用范围',
    align: 'center',
    width: 88,
    slot: 'scopeLevel',
  },
  {
    label: '状态',
    prop: 'isEnabled',
    align: 'center',
    width: 88,
    slot: 'enabled',
  },
  {
    prop: 'updatedAt',
    label: '更新时间',
    align: 'center',
    width: 180,
  },
  { align: 'left', fixed: 'right', label: '操作', slot: 'actions', width: 260 },
];

/**
 * 查询当前页列表。
 * 去掉值为 undefined / 空串的字段再请求，避免后端把「空字符串」当成有效筛选条件。
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

/** 清空筛选条件并重新查询 */
function handleReset() {
  queryParams.keyword = undefined;
  queryParams.documentTypeCode = undefined;
  queryParams.scopeLevel = undefined;
  queryParams.encounterClassCode = undefined;
  queryParams.enabled = undefined;
  getList();
}

/** 翻页或改每页条数 */
function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  getList();
}

/** 空白新建模板 */
function openCreate() {
  editDialogRef.value?.open({
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

/**
 * 列表复制：不调后端 copy 接口；子组件拉取正文后切换为新建并预填名称编码。
 */
function handleCopyRow(row: TemplateDto) {
  editDialogRef.value?.open({
    documentTypeOptions: docTypeSelectOptions.value,
    mode: 'copy',
    row,
  });
}

/** 二次确认后删除 */
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

/** 从左侧面板同步启用文书类型到筛选/弹窗下拉 */
function syncDocumentTypeOptions() {
  const options = documentTypePanelRef.value?.getEnabledOptions() ?? [];
  documentTypeList.value = options.map((opt) => ({
    code: String(opt.value),
    name: opt.label,
    isEnabled: true,
  }));
  editDialogRef.value?.setDocumentTypeOptions(options);
}

/** 左侧文书类型变更后刷新下拉选项 */
function handleDocumentTypesChange() {
  syncDocumentTypeOptions();
}

/** 点击左侧文书类型行：联动右侧模板筛选 */
function handleDocumentTypeSelect(code: string | undefined) {
  queryParams.documentTypeCode = code;
  queryParams.pageNo = 1;
  getList();
}

/** 列表行是否启用 */
function isRowEnabled(row: TemplateDto) {
  return row.isEnabled !== false && row.enabled !== false;
}

/** 文书类型展示名 */
function formatDocumentType(row: TemplateDto) {
  return row.documentTypeName?.trim() || row.documentTypeCode?.trim() || '—';
}

purgeLegacyEmrTemplateCaches();
getList();
</script>

<template>
  <Page auto-content-height>
    <div class="emr-template-page">
      <DocumentTypePanel
        v-show="mainTab === 'template'"
        ref="documentTypePanelRef"
        @change="handleDocumentTypesChange"
        @select="handleDocumentTypeSelect"
      />
      <div class="emr-template-page__main">
        <ElTabs v-model="mainTab" class="emr-template-page__tabs">
          <ElTabPane label="病历模板" name="template">
            <TableLayout
              :form-items="formItems"
              :loading="tableLoading"
              :query-params="queryParams"
              :show-pagination="true"
              :show-top-query="true"
              :table-columns="tableColumns"
              :table-data="templateList"
              :total="total"
              @pagination="handlePagination"
              @query="getList"
              @reset="handleReset"
            >
              <template #documentType="{ row }">
                {{ formatDocumentType(row) }}
              </template>
              <template #encounterClass="{ row }">
                {{
                  row.encounterClassName || formatEncounterClassLabel(row.encounterClassCode) || '—'
                }}
              </template>
              <template #scopeLevel="{ row }">
                {{ row.scopeLevelName || formatTemplateScopeTag(row.scopeLevel) || '—' }}
              </template>
              <template #enabled="{ row }">
                <ElTag :type="isRowEnabled(row) ? 'success' : 'info'" size="small">
                  {{ isRowEnabled(row) ? '启用' : '停用' }}
                </ElTag>
              </template>
              <template #actions="{ row }">
                <ElButton link type="primary" @click="openView(row)">查看</ElButton>
                <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
                <ElButton link type="primary" @click="handleCopyRow(row)">复制</ElButton>
                <ElButton link type="danger" @click="handleDelete(row)">删除</ElButton>
              </template>
              <template #toolbar-actions>
                <ElButton type="primary" @click="openCreate">新建模板</ElButton>
              </template>
            </TableLayout>
          </ElTabPane>
          <ElTabPane label="元数据字段" name="metadata" lazy>
            <MetadataFieldTab />
          </ElTabPane>
        </ElTabs>
      </div>
    </div>
    <EditDialog ref="editDialogRef" @success="getList" />
  </Page>
</template>

<style scoped>
.emr-template-page {
  display: flex;
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.emr-template-page__main {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.emr-template-page__tabs {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.emr-template-page__tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
}

.emr-template-page__tabs :deep(.el-tab-pane) {
  height: 100%;
}
</style>
