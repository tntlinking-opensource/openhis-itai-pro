<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { DeviceCatalogInitResponse, DeviceCatalogItem } from '#/api';

import { computed, nextTick, onActivated, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElDialog, ElMessage, ElMessageBox, ElProgress } from 'element-plus';

import {
  batchStartDeviceCatalogApi,
  batchStopDeviceCatalogApi,
  deleteDeviceCatalogApi,
  downloadDeviceCatalogImportErrorFileApi,
  downloadDeviceCatalogImportTemplateApi,
  exportDeviceCatalogApi,
  getImportDeviceCatalogProgressApi,
  initDeviceCatalogApi,
  pageDeviceCatalogApi,
  startImportDeviceCatalogApi,
} from '#/api';
import type { DeviceCatalogImportProgressResult } from '#/api';
import { PublicationStatusCode } from '#/constants/domain/publication-status';
import StatusEnumTag from '#/components/status-enum-tag.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { DEVICE_CATALOG_EXPORT_COLUMNS } from '#/utils/export/catalog-excel-export-columns';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';
import CatalogLotPriceDialog from '../catalog/catalog-lot-price-dialog.vue';
import { resolveCatalogRecordNavigation } from '../catalog/catalog-record-nav';
import DeviceCatalogEditDialog from './[id]/index.vue';

function isDevicePublicationActive(row: DeviceCatalogItem): boolean {
  const status = row.statusEnum;
  if (
    status === PublicationStatusCode.INACTIVE ||
    status === PublicationStatusCode.DISCARDED ||
    status === 3 ||
    status === '3'
  ) {
    return false;
  }
  return (
    status === PublicationStatusCode.ACTIVE ||
    status === 1 ||
    status === '1' ||
    status === 2 ||
    status === '2'
  );
}

const queryParams = ref({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  /** 耗材分类筛选；空字符串表示「全部」 */
  categoryCode: '',
});

const tableData = ref<DeviceCatalogItem[]>([]);
const loading = ref(false);
const total = ref(0);
const selectedRows = ref<DeviceCatalogItem[]>([]);
const tableLayoutRef = ref<InstanceType<typeof TableLayout> | null>(null);

const initOptions = ref<DeviceCatalogInitResponse>({} as DeviceCatalogInitResponse);
const catalogEditOpen = ref(false);
const catalogEditId = ref<string>('');

/** 当前页耗材 id，供编辑弹窗上下翻 */
const catalogNavIds = computed(() =>
  tableData.value.map((row) => String(row.id ?? '').trim()).filter(Boolean),
);

const catalogNavLoading = ref(false);

const lotPriceOpen = ref(false);
const lotPriceItemId = ref('');
const lotPriceItemName = ref('');

const importFileInputRef = ref<HTMLInputElement | null>(null);
const importLoading = ref(false);
const exportLoading = ref(false);
const importProgressVisible = ref(false);
const importProgress = ref<DeviceCatalogImportProgressResult | null>(null);
const PROGRESS_POLL_INTERVAL_MS = 500;

const importProgressPercent = computed(() => {
  const p = importProgress.value?.percent;
  if (typeof p === 'number' && Number.isFinite(p)) {
    return Math.min(100, Math.max(0, p));
  }
  return 0;
});

const importProgressText = computed(() => {
  const p = importProgress.value;
  if (!p) {
    return '';
  }
  if (p.message) {
    return p.message;
  }
  return `${p.processed ?? 0}/${p.total ?? 0}`;
});

const importProgressBarStatus = computed(() => {
  const status = importProgress.value?.status;
  if (status === 'SUCCEEDED') {
    return 'success';
  }
  if (status === 'FAILED') {
    return 'exception';
  }
  return undefined;
});

function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    setTimeout(resolve, ms);
  });
}

function isTooManyRequestsError(error: unknown): boolean {
  const err = error as { response?: { status?: number }; status?: number };
  return err?.response?.status === 429 || err?.status === 429;
}

function readFileAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result || '');
      const comma = result.indexOf(',');
      if (comma >= 0) {
        resolve(result.slice(comma + 1));
        return;
      }
      resolve(result);
    };
    reader.onerror = () => reject(reader.error || new Error('读取文件失败'));
    reader.readAsDataURL(file);
  });
}

interface CategoryOptionItem {
  children?: CategoryOptionItem[];
  info?: string;
  label?: string;
  name?: string;
  text?: string;
  value: number | string;
}

/** 将树形分类选项展平为下拉框选项（保留层级缩进） */
function flattenCategorySelectOptions(
  options: CategoryOptionItem[] | null | undefined,
  depth = 0,
): Array<{ label: string; value: string }> {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  const prefix = depth > 0 ? `${'　'.repeat(depth)}` : '';
  const result: Array<{ label: string; value: string }> = [];
  for (const item of options) {
    const label = item.label || item.info || item.text || item.name || String(item.value);
    result.push({ label: `${prefix}${label}`, value: String(item.value) });
    if (item.children?.length) {
      result.push(...flattenCategorySelectOptions(item.children, depth + 1));
    }
  }
  return result;
}

const categoryCodeOptions = computed(() =>
  flattenCategorySelectOptions(
    initOptions.value?.deviceCategories as CategoryOptionItem[] | undefined,
  ),
);

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
    align: 'center',
  },
  {
    prop: 'busNo',
    label: '编码',
    width: 150,
    showOverflowTooltip: true,
  },
  {
    prop: 'name',
    label: '项目名',
    minWidth: 160,
    showOverflowTooltip: true,
    slot: 'name',
  },
  {
    prop: 'specification',
    label: '规格',
    width: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'statusEnum',
    label: '状态',
    width: 90,
    align: 'center',
    slot: 'statusEnum',
  },
  {
    prop: 'categoryCode_dictText',
    label: '分类',
    width: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'manufacturerText',
    label: '生产厂家',
    minWidth: 160,
    showOverflowTooltip: true,
  },
  {
    prop: 'retailPrice',
    label: '价格',
    width: 120,
    align: 'right',
    headerAlign: 'right',
    slot: 'retailPrice',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 260,
    align: 'center',
    headerAlign: 'center',
  },
];

function formatRetailPriceWithUnit(price: unknown, unitText: unknown): string {
  if (price === null || price === undefined || price === '') {
    return '';
  }
  const num = Number(price);
  const formatted = Number.isFinite(num) ? formatMoneyDisplay(num) : String(price);
  const unit = typeof unitText === 'string' && unitText.trim() ? unitText.trim() : '';
  return unit ? `${formatted}/${unit}` : formatted;
}

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入耗材名称或编码',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'categoryCode',
    label: '耗材分类',
    placeholder: '请选择耗材分类',
    clearable: true,
    filterable: true,
    /** TableLayout FormItem 默认 multiple=true，单选分类须显式关闭 */
    multiple: false,
    options: categoryCodeOptions.value,
  },
]);

function normalizeCategoryCodeForApi(raw: unknown): string | undefined {
  if (raw === undefined || raw === null) {
    return undefined;
  }
  if (Array.isArray(raw)) {
    if (raw.length === 0) {
      return undefined;
    }
    const first = String(raw[0] ?? '').trim();
    return first === '' ? undefined : first;
  }
  const s = String(raw).trim();
  return s === '' ? undefined : s;
}

const handleQuery = async (params: Record<string, unknown>) => {
  queryParams.value = {
    ...queryParams.value,
    ...params,
  };
  loading.value = true;
  try {
    const response = await pageDeviceCatalogApi({
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      keyword: queryParams.value.keyword || undefined,
      categoryCode: normalizeCategoryCodeForApi(queryParams.value.categoryCode),
    });

    const parsed = parsePagedRowsFromApiData<DeviceCatalogItem>(response.data);
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
    categoryCode: '',
  };
  handleQuery(queryParams.value);
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  handleQuery(queryParams.value);
};

const handleSelectionChange = (selection: DeviceCatalogItem[]) => {
  selectedRows.value = selection;
};

const handleSortChange = (_sortInfo: { column: unknown; order: string; prop: string }) => {};

function openCatalogEditor(id: string) {
  catalogEditId.value = id;
  catalogEditOpen.value = true;
}

/** 弹窗上一个/下一个：同页切 id，边界则翻页后再开相邻记录 */
async function handleCatalogNavigate(direction: -1 | 1) {
  const plan = resolveCatalogRecordNavigation({
    recordIds: catalogNavIds.value,
    currentId: catalogEditId.value,
    direction,
    pageNo: queryParams.value.pageNo,
    pageSize: queryParams.value.pageSize,
    listTotal: total.value,
  });
  if (plan.kind === 'none') {
    return;
  }
  if (plan.kind === 'same-page') {
    openCatalogEditor(plan.id);
    return;
  }
  catalogNavLoading.value = true;
  try {
    queryParams.value.pageNo = plan.targetPageNo;
    await handleQuery(queryParams.value);
    const ids = catalogNavIds.value;
    const targetId = plan.pick === 'first' ? ids[0] : ids[ids.length - 1];
    if (!targetId) {
      ElMessage.warning(direction === 1 ? '下一页没有可编辑记录' : '上一页没有可编辑记录');
      return;
    }
    openCatalogEditor(targetId);
  } finally {
    catalogNavLoading.value = false;
  }
}

const handleAdd = () => {
  openCatalogEditor('new');
};

const handleDownloadImportTemplate = async () => {
  try {
    const data = await downloadDeviceCatalogImportTemplateApi();
    if (!data?.fileBytes?.trim()) {
      ElMessage.warning('模板内容为空，请稍后重试');
      return;
    }
    downloadBinaryExportFile(data, data.fileName ?? '耗材目录导入模板_v1.2.xlsx');
    ElMessage.success('模板已下载');
  } catch (error) {
    console.error('下载导入模板失败:', error);
    ElMessage.error('下载导入模板失败，请稍后重试');
  }
};

/**
 * 按当前筛选导出耗材目录（与导入模板同列；超上限时后端 truncated）
 */
const handleExportCatalog = async () => {
  exportLoading.value = true;
  try {
    const result = await exportDeviceCatalogApi({
      keyword: queryParams.value.keyword || undefined,
      categoryCode: normalizeCategoryCodeForApi(queryParams.value.categoryCode),
      exportColumns: DEVICE_CATALOG_EXPORT_COLUMNS,
    });
    if (!result?.fileBytes?.trim()) {
      ElMessage.warning('导出内容为空');
      return;
    }
    downloadBinaryExportFile(result, result.fileName ?? '耗材目录导出.xlsx');
    if (result.truncated) {
      ElMessage.warning(
        `已导出 ${result.exportedRowCount ?? 0} 条（已达上限，结果已截断，请缩小筛选后再导）`,
      );
      return;
    }
    ElMessage.success(`已导出 ${result.exportedRowCount ?? 0} 条`);
  } catch (error) {
    console.error('导出耗材目录失败:', error);
    ElMessage.error('导出失败，请稍后重试');
  } finally {
    exportLoading.value = false;
  }
};

const handleImportClick = () => {
  importFileInputRef.value?.click();
};

async function pollImportUntilSettled(jobId: string): Promise<DeviceCatalogImportProgressResult> {
  const maxRounds = 3600;
  for (let i = 0; i < maxRounds; i += 1) {
    try {
      const progress = await getImportDeviceCatalogProgressApi(jobId);
      importProgress.value = progress;
      const status = progress.status || '';
      if (status === 'SUCCEEDED' || status === 'FAILED') {
        return progress;
      }
    } catch (error) {
      if (isTooManyRequestsError(error)) {
        await sleep(PROGRESS_POLL_INTERVAL_MS * 2);
        continue;
      }
      throw error;
    }
    await sleep(PROGRESS_POLL_INTERVAL_MS);
  }
  throw new Error('导入超时，请稍后刷新列表确认是否已写入');
}

async function handleImportFailure(progress: DeviceCatalogImportProgressResult) {
  const message = progress.message || '导入失败';
  const token = progress.errorFileToken;
  if (token) {
    try {
      await ElMessageBox.confirm(
        `${message}\n可下载带「错误说明」列的原表，修改后整文件重传。`,
        '导入失败',
        {
          confirmButtonText: '下载错误标注表',
          cancelButtonText: '关闭',
          type: 'warning',
        },
      );
      const fileResult = await downloadDeviceCatalogImportErrorFileApi(token);
      if (!fileResult?.fileBytes?.trim()) {
        ElMessage.warning('错误标注文件为空或已过期');
        return;
      }
      downloadBinaryExportFile(fileResult, fileResult.fileName ?? '耗材目录导入_错误标注.xlsx');
    } catch (boxError) {
      if (boxError !== 'cancel') {
        console.error('下载错误标注表失败:', boxError);
      }
    }
    return;
  }
  ElMessage.error(message);
}

const handleImportFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) {
    return;
  }
  const lower = file.name.toLowerCase();
  if (!lower.endsWith('.xlsx')) {
    ElMessage.warning('请选择 .xlsx 格式的导入模板文件');
    return;
  }
  importLoading.value = true;
  importProgressVisible.value = true;
  importProgress.value = { status: 'PENDING', percent: 0, message: '准备上传…' };
  try {
    const fileBytes = await readFileAsBase64(file);
    const started = await startImportDeviceCatalogApi({
      fileBytes,
      fileName: file.name,
    });
    const jobId = started.jobId;
    if (!jobId) {
      throw new Error('未返回导入任务 ID');
    }
    const progress = await pollImportUntilSettled(String(jobId));
    if (progress.status === 'SUCCEEDED') {
      ElMessage.success(progress.message || `导入成功，共 ${progress.successCount ?? 0} 条`);
      await handleQuery(queryParams.value);
      importProgressVisible.value = false;
      return;
    }
    await handleImportFailure(progress);
  } catch (error) {
    console.error('导入耗材目录失败:', error);
    ElMessage.error((error as Error)?.message || '导入失败，请稍后重试');
  } finally {
    importLoading.value = false;
    if (importProgress.value?.status === 'SUCCEEDED') {
      importProgress.value = null;
    }
  }
};

const clearSelection = async () => {
  selectedRows.value = [];
  await nextTick();
  const gridApi = tableLayoutRef.value?.gridApi;
  if (gridApi) {
    const grid = gridApi.grid || gridApi;
    if (typeof grid.clearCheckboxRow === 'function') {
      grid.clearCheckboxRow();
    }
  }
};

const doDeleteByIds = async (ids: string[]) => {
  if (ids.length === 0) {
    ElMessage.warning('请先选择要删除的数据');
    return;
  }
  await ElMessageBox.confirm(`确定要删除选中的 ${ids.length} 条耗材吗？删除后无法恢复！`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  });
  await deleteDeviceCatalogApi(ids);
  ElMessage.success('删除成功');
  await handleQuery(queryParams.value);
  await clearSelection();
};

const handleDelete = async () => {
  const ids = selectedRows.value
    .map((item) => item.id)
    .filter(Boolean)
    .map(String);
  try {
    await doDeleteByIds(ids);
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      ElMessage.error(error?.msg || error?.message || '删除失败，请稍后重试');
    }
  }
};

const handleEnable = async () => {
  const ids = selectedRows.value.map((item) => item.id).filter(Boolean);
  if (ids.length === 0) {
    ElMessage.warning('请先选择要启用的数据');
    return;
  }
  try {
    await batchStartDeviceCatalogApi(ids.map(String));
    ElMessage.success('启用成功');
    await handleQuery(queryParams.value);
    await clearSelection();
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
    await batchStopDeviceCatalogApi(ids.map(String));
    ElMessage.success('停用成功');
    await handleQuery(queryParams.value);
    await clearSelection();
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleStart = async (row: DeviceCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await batchStartDeviceCatalogApi([String(row.id)]);
    ElMessage.success('启用成功');
    await handleQuery(queryParams.value);
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleStop = async (row: DeviceCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await batchStopDeviceCatalogApi([String(row.id)]);
    ElMessage.success('停用成功');
    await handleQuery(queryParams.value);
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleRowClick = (row: DeviceCatalogItem) => {
  openCatalogEditor(String(row.id || ''));
};

/** 打开批号价格查看弹窗 */
function handleViewLotPrices(row: DeviceCatalogItem) {
  const id = String(row.id || '');
  if (!id) {
    ElMessage.warning('缺少耗材ID');
    return;
  }
  lotPriceItemId.value = id;
  lotPriceItemName.value = typeof row.name === 'string' ? row.name : '';
  lotPriceOpen.value = true;
}

const handleRowDelete = async (row: DeviceCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await doDeleteByIds([String(row.id)]);
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      ElMessage.error(error?.msg || error?.message || '删除失败，请稍后重试');
    }
  }
};

const initPage = async () => {
  try {
    const res = await initDeviceCatalogApi();
    const data: DeviceCatalogInitResponse =
      ((res as unknown as { data?: DeviceCatalogInitResponse })
        ?.data as DeviceCatalogInitResponse) ||
      (res as unknown as DeviceCatalogInitResponse) ||
      ({} as DeviceCatalogInitResponse);
    initOptions.value = data;
    await handleQuery(queryParams.value);
  } catch (error) {
    console.error('加载初始化数据失败:', error);
    await handleQuery(queryParams.value);
  }
};

onActivated(() => {
  const needRefresh = sessionStorage.getItem('deviceCatalogRefresh');
  if (needRefresh === 'true') {
    sessionStorage.removeItem('deviceCatalogRefresh');
    handleQuery(queryParams.value);
  }
});

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
      :show-pagination="true"
      row-key="id"
      border
      stripe
      size="default"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
        <ElButton :loading="importLoading" @click="handleDownloadImportTemplate">
          下载导入模板
        </ElButton>
        <ElButton :loading="importLoading" type="primary" plain @click="handleImportClick">
          导入
        </ElButton>
        <ElButton :loading="exportLoading" @click="handleExportCatalog">导出</ElButton>
        <ElButton type="success" @click="handleEnable">批量启用</ElButton>
        <ElButton type="warning" @click="handleDisable">批量停用</ElButton>
        <ElButton type="danger" @click="handleDelete">删除</ElButton>
        <input
          ref="importFileInputRef"
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          class="hidden"
          @change="handleImportFileChange"
        />
      </template>

      <template #name="{ row }">
        <a
          href="javascript:void(0)"
          class="text-primary cursor-pointer hover:underline"
          @click="handleRowClick(row)"
        >
          {{ row.name }}
        </a>
      </template>

      <template #statusEnum="{ row }">
        <StatusEnumTag :status-enum="row.statusEnum" :status-text="row.statusEnum_enumText" />
      </template>

      <template #retailPrice="{ row }">
        {{ formatRetailPriceWithUnit(row.retailPrice, row.packageUnitCode_dictText) }}
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-1">
          <ElButton type="primary" link @click="handleRowClick(row)">编辑</ElButton>
          <ElButton type="primary" link @click="handleViewLotPrices(row)">价格</ElButton>
          <ElButton
            v-if="!isDevicePublicationActive(row)"
            type="success"
            link
            @click="handleStart(row)"
          >
            启用
          </ElButton>
          <ElButton v-else type="warning" link @click="handleStop(row)">停用</ElButton>
          <ElButton type="danger" link @click="handleRowDelete(row)">删除</ElButton>
        </div>
      </template>
    </TableLayout>

    <DeviceCatalogEditDialog
      v-model="catalogEditOpen"
      :catalog-id="catalogEditId"
      :init-options-source="initOptions"
      :record-ids="catalogNavIds"
      :page-no="queryParams.pageNo"
      :page-size="queryParams.pageSize"
      :list-total="total"
      :navigating="catalogNavLoading"
      @navigate="handleCatalogNavigate"
      @success="handleQuery(queryParams)"
    />

    <CatalogLotPriceDialog
      v-model="lotPriceOpen"
      catalog-type="device"
      :item-id="lotPriceItemId"
      :item-name="lotPriceItemName"
    />

    <ElDialog
      v-model="importProgressVisible"
      title="耗材目录导入"
      width="480px"
      :close-on-click-modal="false"
      :close-on-press-escape="!importLoading"
      :show-close="!importLoading"
    >
      <div class="space-y-3">
        <ElProgress
          :percentage="importProgressPercent"
          :status="importProgressBarStatus"
          :stroke-width="16"
          striped
          striped-flow
        />
        <p class="text-sm text-gray-600">{{ importProgressText }}</p>
        <ul
          v-if="importProgress?.errorMessages?.length"
          class="max-h-40 list-disc overflow-y-auto pl-5 text-sm text-red-600"
        >
          <li v-for="(line, idx) in importProgress.errorMessages" :key="idx">
            {{ line }}
          </li>
        </ul>
      </div>
      <template #footer>
        <ElButton v-if="!importLoading" type="primary" @click="importProgressVisible = false">
          关闭
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
