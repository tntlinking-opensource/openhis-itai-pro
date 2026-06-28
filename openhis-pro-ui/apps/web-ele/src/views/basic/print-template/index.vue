<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { PrtTemplateListItem, QueryPrtTemplatePageParams } from '#/api/print/print-template';

import { computed, onActivated, onMounted, ref, useTemplateRef } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElSpace,
  ElTag,
} from 'element-plus';

import {
  createBlankDraftApi,
  deleteTemplateApi,
  ensureWritableDraftApi,
  getByBusNoApi,
  publishTemplateApi,
  queryPrintTemplatePageApi,
  saveDraftApi,
} from '#/api/print/print-template';
import JsonEditorDialog from '#/hiprint/components/JsonEditorDialog.vue';
import PreviewDialog from '#/hiprint/components/PreviewDialog.vue';
import { buildPreviewPrintData } from '#/hiprint/utils/print-data';
import { validatePrintJson } from '#/hiprint/utils/print-json';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { PRINT_SCENARIO_ITEMS } from '#/utils/print/constants/print-scenario-labels';

defineOptions({ name: 'BasicPrintTemplateIndex' });

const router = useRouter();

const STATUS_OPTIONS = [
  { label: '全部（不含归档）', value: '' },
  { label: '草稿', value: 'draft' },
  { label: '已发布', value: 'published' },
  { label: '已归档', value: 'archived' },
];

const queryParams = ref<QueryPrtTemplatePageParams>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  status: '',
});

const tableData = ref<PrtTemplateListItem[]>([]);
const loading = ref(false);
const total = ref(0);

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  { prop: 'busNo', label: '模板编号', minWidth: 140, showOverflowTooltip: true },
  {
    prop: 'scenarioCode',
    label: '场景码',
    minWidth: 160,
    showOverflowTooltip: true,
    slot: 'scenarioCode',
  },
  { prop: 'name', label: '名称', minWidth: 140, showOverflowTooltip: true },
  { prop: 'status', label: '状态', width: 100, align: 'center', slot: 'status' },
  { prop: 'version', label: '发布版本', width: 88, align: 'center', slot: 'version' },
  { prop: 'schemaVersion', label: '结构版本', width: 90, align: 'center' },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 440,
    align: 'center',
  },
];

const scenarioPresetOptions = PRINT_SCENARIO_ITEMS.map((item) => ({
  label: `${item.label} (${item.scenarioCode})`,
  value: item.scenarioCode,
}));

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '编号 / 场景码 / 名称',
    clearable: true,
    width: '220px',
  },
  {
    type: 'select',
    prop: 'status',
    label: '状态',
    clearable: true,
    width: '160px',
    options: STATUS_OPTIONS.map((o) => ({ label: o.label, value: o.value })),
  },
]);

function statusTagType(status?: string): 'info' | 'success' | 'warning' {
  switch (status) {
    case 'draft': {
      return 'warning';
    }
    case 'published': {
      return 'success';
    }
    default: {
      return 'info';
    }
  }
}

function statusLabel(status?: string, hasDraft?: boolean, version?: number): string {
  switch (status) {
    case 'archived': {
      return '已归档';
    }
    case 'draft': {
      if (version !== null && version !== undefined && version > 0) {
        return `草稿中（基于 v${version}）`;
      }
      return '草稿';
    }
    case 'published': {
      return `已发布 v${version ?? '-'}`;
    }
    default: {
      return status ?? '-';
    }
  }
}

async function loadData() {
  loading.value = true;
  try {
    const payload = { ...queryParams.value };
    if (!payload.status) {
      delete payload.status;
    }
    if (!payload.keyword) {
      delete payload.keyword;
    }
    const res = await queryPrintTemplatePageApi(payload);
    const { content, totalCount } = parsePagedRowsFromApiData<PrtTemplateListItem>(res);
    tableData.value = content;
    total.value = totalCount;
  } catch {
    ElMessage.error('查询失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function handleQuery(params: Record<string, unknown>) {
  queryParams.value = { ...queryParams.value, ...params } as QueryPrtTemplatePageParams;
  loadData();
}

function handleReset() {
  queryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    status: '',
  };
  loadData();
}

function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  loadData();
}

const createVisible = ref(false);
const createForm = ref({ name: '', scenarioCode: '' });
const createLoading = ref(false);

const scenarioVisible = ref(false);
const scenarioLoading = ref(false);
const scenarioForm = ref({ busNo: '', name: '', scenarioCode: '' });

const jsonEditorRef = useTemplateRef<InstanceType<typeof JsonEditorDialog>>('jsonEditorRef');
const previewRef = useTemplateRef<InstanceType<typeof PreviewDialog>>('previewRef');
const jsonEditLoadingBusNo = ref<null | string>(null);
const previewLoadingBusNo = ref<null | string>(null);
const jsonSaveLoading = ref(false);
/** 当前 JSON 编辑上下文（保存草稿时带回 name / samplePrintData） */
const editingDraftMeta = ref<null | {
  busNo: string;
  id?: string;
  name?: string;
  samplePrintData?: Record<string, unknown>;
  scenarioCode?: string;
}>(null);

function openCreate() {
  createForm.value = { name: '', scenarioCode: '' };
  createVisible.value = true;
}

async function submitCreate() {
  const name = createForm.value.name?.trim();
  const scenarioCode = createForm.value.scenarioCode?.trim();
  createLoading.value = true;
  try {
    const dto = await createBlankDraftApi({
      ...(name ? { name } : {}),
      ...(scenarioCode ? { scenarioCode } : {}),
    });
    createVisible.value = false;
    ElMessage.success('已创建空白草稿');
    const busNo = dto.busNo?.trim();
    if (busNo) {
      router.push({ path: '/basic/print-design', query: { busNo } });
      return;
    }
    await loadData();
  } catch {
    ElMessage.error('创建失败');
  } finally {
    createLoading.value = false;
  }
}

async function handlePublish(row: PrtTemplateListItem) {
  if (!row.busNo || !row.hasDraft) {
    ElMessage.warning('无草稿可发布，请先设计并保存');
    return;
  }
  try {
    await publishTemplateApi(row.busNo);
    ElMessage.success('发布成功');
    await loadData();
  } catch {
    ElMessage.error('发布失败');
  }
}

/** 进入设计器：只读加载已发布/草稿内容，保存时才 fork 草稿 */
function goDesign(row: PrtTemplateListItem): void {
  const busNo = row.busNo?.trim();
  if (!busNo) {
    return;
  }
  if (row.status === 'archived') {
    ElMessage.warning('已归档模板不可编辑');
    return;
  }
  router.push({ path: '/basic/print-design', query: { busNo } });
}

/** 拉取已发布/当前生效模板并打开预览（只读，不 fork 草稿） */
async function handlePreview(row: PrtTemplateListItem): Promise<void> {
  const busNo = row.busNo?.trim();
  if (!busNo) {
    return;
  }
  previewLoadingBusNo.value = busNo;
  try {
    const dto = await getByBusNoApi(busNo);
    const printJson = dto.PrintJson ?? { schemaVersion: 1, engine: 'Print', panels: [] };
    const printData = buildPreviewPrintData(printJson, { samplePrintData: dto.samplePrintData });
    await previewRef.value?.show(printJson, printData);
  } catch {
    ElMessage.error('预览加载失败');
  } finally {
    previewLoadingBusNo.value = null;
  }
}

/** 删除租户自建模板 */
async function handleDelete(row: PrtTemplateListItem): Promise<void> {
  if (!row.busNo) {
    return;
  }
  if (!row.deletable) {
    ElMessage.warning('出厂模板不可删除');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认删除模板「${row.name || row.busNo}」？删除后不可恢复。`,
      '删除确认',
      { type: 'warning' },
    );
    await deleteTemplateApi(row.busNo);
    ElMessage.success('已删除');
    await loadData();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败');
    }
  }
}

/** 拉取模板内容并打开 PrintJson 编辑器（打开时不 fork，保存时再 fork） */
async function handleEditJson(row: PrtTemplateListItem) {
  const busNo = row.busNo?.trim();
  if (!busNo) {
    return;
  }
  if (row.status === 'archived') {
    ElMessage.warning('已归档模板不可编辑');
    return;
  }
  jsonEditLoadingBusNo.value = busNo;
  try {
    const dto = await getByBusNoApi(busNo);
    editingDraftMeta.value = {
      id: dto.id,
      busNo,
      name: dto.name ?? row.name,
      scenarioCode: dto.scenarioCode ?? row.scenarioCode,
      samplePrintData: dto.samplePrintData,
    };
    jsonEditorRef.value?.open(
      dto.PrintJson ?? { schemaVersion: 1, engine: 'Print', panels: [] },
      `编辑 PrintJson · ${busNo}`,
    );
  } catch {
    ElMessage.error('加载模板失败');
    editingDraftMeta.value = null;
  } finally {
    jsonEditLoadingBusNo.value = null;
  }
}

/** 校验 JSON 后保存草稿（IF-BAS-PRT-14） */
async function handleApplyPrintJson(printJson: Record<string, unknown>) {
  const meta = editingDraftMeta.value;
  if (!meta?.busNo) {
    return;
  }
  try {
    validatePrintJson(printJson);
  } catch (error) {
    const message = error instanceof Error ? error.message : 'PrintJson 校验失败';
    ElMessage.error(message);
    return;
  }
  jsonSaveLoading.value = true;
  try {
    const draft = await ensureWritableDraftApi(meta.busNo);
    await saveDraftApi({
      id: draft.id,
      busNo: meta.busNo,
      name: draft.name ?? meta.name,
      scenarioCode: draft.scenarioCode ?? meta.scenarioCode,
      PrintJson: printJson,
      samplePrintData: meta.samplePrintData ?? draft.samplePrintData,
    });
    ElMessage.success('已保存草稿');
    editingDraftMeta.value = null;
    await loadData();
  } catch {
    ElMessage.error('保存失败');
  } finally {
    jsonSaveLoading.value = false;
  }
}

/** 打开场景码编辑弹窗（只读列表数据，不 fork 草稿） */
function openScenarioDialog(row: PrtTemplateListItem): void {
  const busNo = row.busNo?.trim();
  if (!busNo) {
    return;
  }
  if (row.status === 'archived') {
    ElMessage.warning('已归档模板不可编辑');
    return;
  }
  scenarioForm.value = {
    busNo,
    name: row.name ?? '',
    scenarioCode: row.scenarioCode ?? '',
  };
  scenarioVisible.value = true;
}

/** 保存场景码到草稿（点保存时才 fork） */
async function submitScenarioCode(): Promise<void> {
  const busNo = scenarioForm.value.busNo?.trim();
  if (!busNo) {
    return;
  }
  scenarioLoading.value = true;
  try {
    const draft = await ensureWritableDraftApi(busNo);
    await saveDraftApi({
      id: draft.id,
      busNo,
      name: draft.name ?? scenarioForm.value.name,
      scenarioCode: scenarioForm.value.scenarioCode?.trim() || undefined,
      PrintJson: draft.PrintJson ?? { schemaVersion: 1, engine: 'Print', panels: [] },
      samplePrintData: draft.samplePrintData,
    });
    scenarioVisible.value = false;
    ElMessage.success('场景码已保存');
    await loadData();
  } catch {
    ElMessage.error('保存失败');
  } finally {
    scenarioLoading.value = false;
  }
}

onMounted(() => {
  loadData();
});

onActivated(() => {
  loadData();
});
</script>

<template>
  <Page auto-content-height>
    <TableLayout
      :form-items="formItems"
      :loading="loading"
      :query-params="queryParams"
      :table-columns="tableColumns"
      :table-data="tableData"
      :total="total"
      show-pagination
      @pagination="handlePagination"
      @query="handleQuery"
      @reset="handleReset"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="openCreate">新建模板</ElButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="statusTagType(row.status)" size="small">
          {{ statusLabel(row.status, row.hasDraft, row.version) }}
        </ElTag>
      </template>

      <template #version="{ row }">
        {{ row.version != null ? `v${row.version}` : '-' }}
      </template>

      <template #scenarioCode="{ row }">
        <span>{{ row.scenarioCode || '-' }}</span>
      </template>

      <template #action="{ row }">
        <ElSpace wrap>
          <ElButton
            link
            type="primary"
            :loading="previewLoadingBusNo === row.busNo"
            @click="handlePreview(row)"
          >
            预览
          </ElButton>
          <ElButton
            link
            type="primary"
            :disabled="row.status === 'archived'"
            @click="openScenarioDialog(row)"
          >
            场景码
          </ElButton>
          <ElButton
            link
            type="primary"
            :disabled="row.status === 'archived'"
            @click="goDesign(row)"
          >
            设计
          </ElButton>
          <ElButton
            link
            type="primary"
            :loading="jsonEditLoadingBusNo === row.busNo"
            :disabled="row.status === 'archived' || jsonSaveLoading"
            @click="handleEditJson(row)"
          >
            编辑 JSON
          </ElButton>
          <ElButton link type="warning" :disabled="!row.hasDraft" @click="handlePublish(row)">
            发布
          </ElButton>
          <ElButton link type="danger" :disabled="!row.deletable" @click="handleDelete(row)">
            删除
          </ElButton>
        </ElSpace>
      </template>
    </TableLayout>

    <ElDialog v-model="createVisible" title="新建空白模板" width="480px" destroy-on-close>
      <ElForm label-width="88px">
        <ElFormItem label="名称">
          <ElInput v-model="createForm.name" placeholder="如：门诊处方" />
        </ElFormItem>
        <ElFormItem label="场景码">
          <ElInput
            v-model="createForm.scenarioCode"
            placeholder="如：outpatient.rx.print（可选）"
            clearable
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="createVisible = false">取消</ElButton>
        <ElButton :loading="createLoading" type="primary" @click="submitCreate">确定</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="scenarioVisible" title="编辑场景码" width="520px" destroy-on-close>
      <ElForm label-width="88px">
        <ElFormItem label="模板">
          <span>{{ scenarioForm.name || scenarioForm.busNo }}</span>
        </ElFormItem>
        <ElFormItem label="场景码">
          <ElInput
            v-model="scenarioForm.scenarioCode"
            placeholder="如：outpatient.rx.print，留空表示不关联业务场景"
            clearable
          />
        </ElFormItem>
        <ElFormItem label="常用">
          <ElSpace wrap>
            <ElButton
              v-for="item in scenarioPresetOptions"
              :key="item.value"
              size="small"
              @click="scenarioForm.scenarioCode = item.value"
            >
              {{ item.label }}
            </ElButton>
          </ElSpace>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="scenarioVisible = false">取消</ElButton>
        <ElButton :loading="scenarioLoading" type="primary" @click="submitScenarioCode">
          保存
        </ElButton>
      </template>
    </ElDialog>

    <JsonEditorDialog ref="jsonEditorRef" @apply="handleApplyPrintJson" />
    <PreviewDialog ref="previewRef" />
  </Page>
</template>
