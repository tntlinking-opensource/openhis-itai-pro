<script lang="ts" setup>
import type { FormItemConfig, TableColumn, TreeNodeData } from '@vben-core/table-layout-ui';

import type { DiagnosisCatalogInitResponse, DiagnosisCatalogItem } from '#/api';

import { computed, nextTick, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage } from 'element-plus';

import type { ClinicalV2ImportProgressResult } from '#/api';

import {
  batchDisableDiagnosisCatalogApi,
  batchEnableDiagnosisCatalogApi,
  deleteDiagnosisCatalogApi,
  getImportClinicalV2CatalogProgressApi,
  getImportYbConditionCatalogProgressApi,
  initDiagnosisCatalogApi,
  pageDiagnosisCatalogApi,
  startImportClinicalV2CatalogApi,
  startImportYbConditionCatalogApi,
} from '#/api';
import StatusEnumTag from '#/components/status-enum-tag.vue';
import { PublicationStatusCode } from '#/constants/domain/publication-status';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';
import EditDialog from './edit-dialog.vue';

/** 国家临床版 2.0 目录来源编码 */
const SOURCE_CLINICAL_V2 = 'clinical_v2';

/** 支持从医保导入的目录来源（有对应 13xx 下载） */
const YB_IMPORTABLE_SOURCES = new Set([
  'general_dx',
  'chronic_spec',
  'disease_pkg',
  'paid_dx',
  'day_surgery_dx',
  'tumor_morph',
  'tcm_dx',
  'tcm_syndrome',
]);

/** File → 纯 base64（去掉 data URL 前缀） */
function readFileAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.addEventListener('load', () => {
      const result = String(reader.result ?? '');
      const comma = result.indexOf(',');
      if (comma >= 0) {
        resolve(result.slice(comma + 1));
        return;
      }
      resolve(result);
    });
    reader.addEventListener('error', () => reject(reader.error ?? new Error('读取文件失败')));
    reader.readAsDataURL(file);
  });
}

function sleep(ms: number) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

async function pollClinicalV2ImportUntilSettled(
  jobId: string,
): Promise<ClinicalV2ImportProgressResult> {
  const maxAttempts = 600;
  for (let i = 0; i < maxAttempts; i += 1) {
    const progress = await getImportClinicalV2CatalogProgressApi(jobId);
    if (progress.status === 'SUCCEEDED' || progress.status === 'FAILED') {
      return progress;
    }
    await sleep(1000);
  }
  throw new Error('导入超时：后台任务仍在执行，请稍后刷新列表确认');
}

async function pollYbConditionImportUntilSettled(
  jobId: string,
): Promise<ClinicalV2ImportProgressResult> {
  const maxAttempts = 600;
  for (let i = 0; i < maxAttempts; i += 1) {
    const progress = await getImportYbConditionCatalogProgressApi(jobId);
    if (progress.status === 'SUCCEEDED' || progress.status === 'FAILED') {
      return progress;
    }
    await sleep(1000);
  }
  throw new Error('导入超时：后台任务仍在执行，请稍后刷新列表确认');
}

const queryParams = ref({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  sourceEnum: undefined as string | undefined,
});

const tableData = ref<DiagnosisCatalogItem[]>([]);
const loading = ref(false);
const total = ref(0);
const selectedRows = ref<DiagnosisCatalogItem[]>([]);
const tableLayoutRef = ref<InstanceType<typeof TableLayout> | null>(null);
const editDialogVisible = ref(false);
const editFormData = ref<Partial<DiagnosisCatalogItem>>({});
const importLoading = ref(false);
const importFileInputRef = ref<HTMLInputElement | null>(null);
const importProgressText = ref('');

// 初始化数据（分类、状态等）
const diagnosisCategoryList = ref<Array<{ label: string; value: number | string }>>([]);
const statusFlagOptions = ref<Array<{ label: string; value: number | string }>>([]);
const ybDiagTypes = ref<Array<{ label: string; value: number | string }>>([]);

const tableColumns: TableColumn[] = [
  {
    type: 'selection',
    fixed: 'left',
    width: 50,
    align: 'center',
  },
  {
    type: 'index',
    label: '序号',
    width: 60,
  },
  {
    prop: 'conditionCode',
    label: '医保编码',
    width: 150,
    showOverflowTooltip: true,
  },
  {
    prop: 'name',
    label: '医保诊断名称',
    showOverflowTooltip: true,
  },
  {
    prop: 'clinicCode',
    label: '国临码',
    width: 140,
    showOverflowTooltip: true,
  },
  {
    prop: 'ybNo',
    label: '国临诊断名称',
    width: 140,
    showOverflowTooltip: true,
  },
  {
    prop: 'statusEnum',
    label: '状态',
    width: 90,
    slot: 'statusEnum',
  },
  {
    prop: 'description',
    label: '说明',
    showOverflowTooltip: true,
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 150,
  },
];

const formItems: FormItemConfig[] = [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入诊断名称或编码',
    clearable: true,
  },
];

const handleQuery = async (params: Record<string, unknown>) => {
  queryParams.value = {
    ...queryParams.value,
    ...params,
  };
  loading.value = true;
  try {
    const response = await pageDiagnosisCatalogApi({
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      keyword: queryParams.value.keyword || undefined,
      sourceEnum: queryParams.value.sourceEnum || undefined,
    });

    const parsed = parsePagedRowsFromApiData<DiagnosisCatalogItem>(response.data);
    tableData.value = parsed.content;
    total.value = parsed.totalCount;
  } catch (error) {
    console.error('查询失败:', error);
    ElMessage.error('查询失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 10,
    keyword: '',
    sourceEnum: undefined,
  };
  handleQuery(queryParams.value);
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  handleQuery(queryParams.value);
};

// 侧边树数据：疾病分类
const treeData = ref<Array<{ id: number | string; label: string; value: number | string }>>([]);

/** 侧栏树节点点击：按疾病来源分类（sourceEnum）筛选列表 */
const handleSideQuery = (node: TreeNodeData) => {
  const nodeKey = node?.id ?? node?.value;
  if (!node || nodeKey === '__ALL__') {
    queryParams.value.sourceEnum = undefined;
  } else {
    const nodeValue = node.value ?? node.id;
    queryParams.value.sourceEnum =
      nodeValue !== undefined && nodeValue !== null && String(nodeValue).trim() !== ''
        ? String(nodeValue)
        : undefined;
  }
  queryParams.value.pageNo = 1;
  handleQuery(queryParams.value);
};

const handleSelectionChange = (selection: DiagnosisCatalogItem[]) => {
  selectedRows.value = selection;
};

const handleAdd = () => {
  const source = queryParams.value.sourceEnum;
  editFormData.value = source ? { sourceEnum: source } : {};
  editDialogVisible.value = true;
};

const handleEdit = (row: DiagnosisCatalogItem) => {
  editFormData.value = { ...row };
  editDialogVisible.value = true;
};

const handleDelete = async (row?: DiagnosisCatalogItem) => {
  const ids = row && row.id ? [row.id] : selectedRows.value.map((item) => item.id).filter(Boolean);

  if (ids.length === 0) {
    ElMessage.warning('请先选择要删除的数据');
    return;
  }

  try {
    await Promise.all(ids.map((id) => deleteDiagnosisCatalogApi(String(id))));
    ElMessage.success('删除成功');
    selectedRows.value = [];
    await handleQuery(queryParams.value);
    await nextTick();
    const gridApi = tableLayoutRef.value?.gridApi;
    if (gridApi) {
      const grid = gridApi.grid || gridApi;
      if (typeof grid.clearCheckboxRow === 'function') {
        grid.clearCheckboxRow();
      }
    }
  } catch (error) {
    console.error('删除失败:', error);
    ElMessage.error('删除失败，请稍后重试');
  }
};

const handleEnable = async () => {
  const ids = selectedRows.value.map((item) => item.id).filter(Boolean);
  if (ids.length === 0) {
    ElMessage.warning('请先选择要启用的数据');
    return;
  }
  try {
    await batchEnableDiagnosisCatalogApi(ids.map(String));
    ElMessage.success('启用成功');
    selectedRows.value = [];
    await handleQuery(queryParams.value);
    await nextTick();
    const gridApi = tableLayoutRef.value?.gridApi;
    if (gridApi) {
      const grid = gridApi.grid || gridApi;
      if (typeof grid.clearCheckboxRow === 'function') {
        grid.clearCheckboxRow();
      }
    }
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleDisable = async () => {
  const ids = selectedRows.value.map((item) => item.id).filter(Boolean);
  if (ids.length === 0) {
    ElMessage.warning('请先选择要停用的数据');
    return;
  }
  try {
    await batchDisableDiagnosisCatalogApi(ids.map(String));
    ElMessage.success('停用成功');
    selectedRows.value = [];
    await handleQuery(queryParams.value);
    await nextTick();
    const gridApi = tableLayoutRef.value?.gridApi;
    if (gridApi) {
      const grid = gridApi.grid || gridApi;
      if (typeof grid.clearCheckboxRow === 'function') {
        grid.clearCheckboxRow();
      }
    }
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleStart = async (row: DiagnosisCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await batchEnableDiagnosisCatalogApi([String(row.id)]);
    ElMessage.success('启用成功');
    await handleQuery(queryParams.value);
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleStop = async (row: DiagnosisCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await batchDisableDiagnosisCatalogApi([String(row.id)]);
    ElMessage.success('停用成功');
    await handleQuery(queryParams.value);
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleEditSuccess = () => {
  handleQuery(queryParams.value);
};

const showClinicalV2Import = computed(() => queryParams.value.sourceEnum === SOURCE_CLINICAL_V2);

const showYbImport = computed(() => {
  const source = queryParams.value.sourceEnum;
  return !!source && YB_IMPORTABLE_SOURCES.has(source);
});

const handleImportClinicalV2Click = () => {
  importFileInputRef.value?.click();
};

const handleImportYbClick = async () => {
  const sourceEnum = queryParams.value.sourceEnum;
  if (!sourceEnum || !YB_IMPORTABLE_SOURCES.has(sourceEnum)) {
    ElMessage.warning('当前目录来源不支持从医保导入');
    return;
  }
  importLoading.value = true;
  importProgressText.value = '正在启动…';
  try {
    const started = await startImportYbConditionCatalogApi({ sourceEnum });
    const jobId = started?.jobId;
    if (!jobId) {
      throw new Error('启动导入失败：未返回任务号');
    }
    importProgressText.value = '后台导入中…';
    const progress = await pollYbConditionImportUntilSettled(String(jobId));
    importProgressText.value = progress.message || '';
    if (progress.status === 'SUCCEEDED') {
      ElMessage.success(progress.message || `导入成功：共写入 ${progress.successCount ?? 0} 条`);
      await handleQuery(queryParams.value);
      return;
    }
    ElMessage.error(progress.message || '导入失败');
  } catch (error: unknown) {
    console.error('从医保导入诊断目录失败:', error);
    const err = error as { message?: string };
    ElMessage.error(err.message || '导入失败，请稍后重试');
  } finally {
    importLoading.value = false;
    importProgressText.value = '';
  }
};

const handleImportClinicalV2File = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) {
    return;
  }
  const lowerName = file.name.toLowerCase();
  if (!lowerName.endsWith('.xls') && !lowerName.endsWith('.xlsx')) {
    ElMessage.warning('请上传 .xls 或 .xlsx 格式的国家临床版2.0文件');
    return;
  }
  importLoading.value = true;
  importProgressText.value = '正在上传…';
  try {
    const fileBytes = await readFileAsBase64(file);
    const started = await startImportClinicalV2CatalogApi({
      fileBytes,
      fileName: file.name,
    });
    const jobId = started?.jobId;
    if (!jobId) {
      throw new Error('启动导入失败：未返回任务号');
    }
    importProgressText.value = '后台导入中…';
    const progress = await pollClinicalV2ImportUntilSettled(String(jobId));
    importProgressText.value = progress.message || '';
    if (progress.status === 'SUCCEEDED') {
      ElMessage.success(progress.message || `导入成功：共写入 ${progress.successCount ?? 0} 条`);
      await handleQuery(queryParams.value);
      return;
    }
    ElMessage.error(progress.message || '导入失败');
  } catch (error: unknown) {
    console.error('导入国家临床版2.0失败:', error);
    const err = error as { message?: string };
    ElMessage.error(err.message || '导入失败，请稍后重试');
  } finally {
    importLoading.value = false;
    importProgressText.value = '';
  }
};

// 初始化：加载分类等信息 + 首次查询
const initPage = async () => {
  try {
    const res = await initDiagnosisCatalogApi();
    const data: DiagnosisCatalogInitResponse = res.data || ({} as DiagnosisCatalogInitResponse);
    diagnosisCategoryList.value =
      (data.diseaseCategoryList as Array<{ label: string; value: number | string }>) || [];
    statusFlagOptions.value =
      (data.statusFlagOptions as Array<{ label: string; value: number | string }>) || [];
    ybDiagTypes.value =
      (data.ybDiagTypes as Array<{ label: string; value: number | string }>) || [];
    treeData.value = diagnosisCategoryList.value.map((item) => ({
      label: item.label,
      value: item.value,
      id: String(item.value),
    }));
  } catch (error) {
    console.error('初始化失败:', error);
  }
  await handleQuery(queryParams.value);
};

initPage();
</script>

<template>
  <Page>
    <TableLayout
      ref="tableLayoutRef"
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :max-height="BASIC_TABLE_MAX_HEIGHT"
      :show-top-query="true"
      :show-side-query="true"
      :show-pagination="true"
      :sider-data="treeData"
      row-key="id"
      border
      stripe
      size="default"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
      @side-query="handleSideQuery"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
        <ElButton type="success" @click="handleEnable">批量启用</ElButton>
        <ElButton type="warning" @click="handleDisable">批量停用</ElButton>
        <ElButton
          v-if="showClinicalV2Import"
          type="primary"
          plain
          :loading="importLoading"
          @click="handleImportClinicalV2Click"
        >
          {{ importLoading ? importProgressText || '导入中…' : '导入国临2.0' }}
        </ElButton>
        <ElButton
          v-if="showYbImport"
          type="primary"
          plain
          :loading="importLoading"
          @click="handleImportYbClick"
        >
          {{ importLoading ? importProgressText || '导入中…' : '从医保导入' }}
        </ElButton>
        <input
          ref="importFileInputRef"
          type="file"
          accept=".xls,.xlsx,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          style="display: none"
          @change="handleImportClinicalV2File"
        />
      </template>

      <template #name="{ row }">
        <a
          href="javascript:void(0)"
          class="text-primary cursor-pointer hover:underline"
          @click="handleEdit(row)"
        >
          {{ row.name }}
        </a>
      </template>

      <template #statusEnum="{ row }">
        <StatusEnumTag :status-enum="row.statusEnum" :status-text="row.statusEnum_enumText" />
      </template>

      <template #action="{ row }">
        <ElButton type="primary" link @click="handleEdit(row)"> 编辑 </ElButton>
        <ElButton
          v-if="row.statusEnum !== PublicationStatusCode.ACTIVE"
          type="success"
          link
          @click="handleStart(row)"
        >
          启用
        </ElButton>
        <ElButton v-else type="warning" link @click="handleStop(row)"> 停用 </ElButton>
        <ElButton type="danger" link @click="handleDelete(row)"> 删除 </ElButton>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="editDialogVisible"
      :form-data="editFormData"
      @success="handleEditSuccess"
    />
  </Page>
</template>
