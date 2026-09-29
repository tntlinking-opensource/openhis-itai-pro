<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { ServiceCatalogInitResponse, ServiceCatalogItem } from '#/api';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElDialog, ElMessage, ElMessageBox, ElProgress, ElTag } from 'element-plus';

import {
  deleteServiceCatalogApi,
  downloadServiceCatalogImportErrorFileApi,
  downloadServiceCatalogImportTemplateApi,
  exportServiceCatalogApi,
  getImportServiceCatalogProgressApi,
  getServiceCatalogApi,
  pageServiceCatalogApi,
  serviceCatalogInitApi,
  startImportServiceCatalogApi,
  startServiceCatalogApi,
  stopServiceCatalogApi,
} from '#/api';
import type { ServiceCatalogImportProgressResult } from '#/api';
import StatusEnumTag from '#/components/status-enum-tag.vue';
import { PublicationStatusCode } from '#/constants/domain/publication-status';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { SERVICE_CATALOG_EXPORT_COLUMNS } from '#/utils/export/catalog-excel-export-columns';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';
import { resolveCatalogRecordNavigation } from '../catalog/catalog-record-nav';
import {
  BUILT_IN_ACTIVITY_EDIT_HINT,
  isBuiltInActivityName,
} from '#/constants/domain/built-in-activity-definition';
import EditDialog from './edit-dialog.vue';

const route = useRoute();
const router = useRouter();

const queryParams = ref({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  /** 目录分类筛选；空字符串表示「全部」 */
  categoryCode: '',
  /** 启用状态；空字符串表示「全部」 */
  statusEnum: '' as string,
  /** 绑定子项；空字符串表示「全部」；'true'/'false' 提交时转 Boolean */
  childrenFlag: '' as string,
});

const tableData = ref<ServiceCatalogItem[]>([]);
const loading = ref(false);
const total = ref(0);

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
    minWidth: 140,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'name',
    label: '项目名',
    slot: 'name',
    minWidth: 220,
    showOverflowTooltip: true,
  },
  {
    prop: 'statusEnum',
    label: '状态',
    width: 100,
    align: 'center',
    headerAlign: 'center',
    slot: 'statusEnum',
  },
  {
    prop: 'categoryCode_dictText',
    label: '分类',
    minWidth: 120,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'childrenFlag',
    label: '绑定子项',
    width: 100,
    align: 'center',
    headerAlign: 'center',
    slot: 'childrenFlag',
  },
  {
    prop: 'retailPrice',
    label: '售价',
    minWidth: 100,
    align: 'right',
    headerAlign: 'right',
    slot: 'retailPrice',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 180,
    align: 'center',
    headerAlign: 'center',
  },
];

function formatRetailPrice(price: unknown): string {
  if (price === null || price === undefined || price === '') {
    return '';
  }
  const num = Number(price);
  return Number.isFinite(num) ? formatMoneyDisplay(num) : String(price);
}

/**
 * 列表/详情 `childrenFlag` 是否为「已绑定子项」
 *（列表 Boolean；详情 0/1，均按真值判定）。
 */
function isChildrenBound(flag: unknown): boolean {
  return flag === true || flag === 1 || flag === '1';
}

/** 列表行是否为产品内置诊疗（禁止删/停用） */
function isBuiltInServiceRow(row: ServiceCatalogItem | null | undefined): boolean {
  return isBuiltInActivityName(row?.name);
}

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入诊疗名称或编码',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'categoryCode',
    label: '目录分类',
    placeholder: '请选择目录分类',
    clearable: true,
    filterable: true,
    /** TableLayout FormItem 默认 multiple=true，单选分类须显式关闭 */
    multiple: false,
    options: categoryCodeOptions.value,
  },
  {
    type: 'select',
    prop: 'statusEnum',
    label: '启用状态',
    placeholder: '全部',
    clearable: true,
    multiple: false,
    options: [
      { label: '全部', value: '' },
      { label: '已启用', value: PublicationStatusCode.ACTIVE },
      { label: '已停用', value: PublicationStatusCode.INACTIVE },
    ],
  },
  {
    type: 'select',
    prop: 'childrenFlag',
    label: '绑定子项',
    placeholder: '全部',
    clearable: true,
    multiple: false,
    options: [
      { label: '全部', value: '' },
      { label: '是', value: 'true' },
      { label: '否', value: 'false' },
    ],
  },
]);

/** 分类筛选归一：空/数组转可提交的 string（对齐耗材目录） */
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

/** 发布状态筛选归一：空/全部 → undefined */
function normalizeStatusEnumForApi(raw: unknown): string | undefined {
  if (raw === undefined || raw === null) {
    return undefined;
  }
  const s = String(raw).trim();
  return s === '' ? undefined : s;
}

/** 绑定子项筛选归一：空 → undefined；'true'/'false'/布尔 → boolean */
function normalizeChildrenFlagForApi(raw: unknown): boolean | undefined {
  if (raw === undefined || raw === null || raw === '') {
    return undefined;
  }
  if (raw === true || raw === 1 || raw === '1' || raw === 'true') {
    return true;
  }
  if (raw === false || raw === 0 || raw === '0' || raw === 'false') {
    return false;
  }
  return undefined;
}

const handleQuery = async (params: Record<string, unknown>) => {
  queryParams.value = {
    ...queryParams.value,
    ...params,
  };
  loading.value = true;
  try {
    const response = await pageServiceCatalogApi({
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      keyword: queryParams.value.keyword || undefined,
      categoryCode: normalizeCategoryCodeForApi(queryParams.value.categoryCode),
      statusEnum: normalizeStatusEnumForApi(queryParams.value.statusEnum),
      childrenFlag: normalizeChildrenFlagForApi(queryParams.value.childrenFlag),
      // 目录维护须见明细子项（组套选品等默认隐藏）
      includeReferencedPackageChildren: true,
    });

    const parsed = parsePagedRowsFromApiData<ServiceCatalogItem>(response.data);
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
    statusEnum: '',
    childrenFlag: '',
  };
  handleQuery(queryParams.value);
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  handleQuery(queryParams.value);
};

const selectedRows = ref<ServiceCatalogItem[]>([]);
const tableLayoutRef = ref<InstanceType<typeof TableLayout> | null>(null);

const importFileInputRef = ref<HTMLInputElement | null>(null);
const importLoading = ref(false);
const exportLoading = ref(false);
const importProgressVisible = ref(false);
const importProgress = ref<ServiceCatalogImportProgressResult | null>(null);
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

const handleSelectionChange = (selection: ServiceCatalogItem[]) => {
  selectedRows.value = selection;
};

const handleSortChange = (_sortInfo: { column: unknown; order: string; prop: string }) => {};

const handleAdd = () => {
  editFormData.value = {};
  editDialogVisible.value = true;
};

const handleDownloadImportTemplate = async () => {
  try {
    const data = await downloadServiceCatalogImportTemplateApi();
    if (!data?.fileBytes?.trim()) {
      ElMessage.warning('模板内容为空，请稍后重试');
      return;
    }
    downloadBinaryExportFile(data, data.fileName ?? '诊疗目录导入模板_v1.0.xlsx');
    ElMessage.success('模板已下载');
  } catch (error) {
    console.error('下载导入模板失败:', error);
    ElMessage.error('下载导入模板失败，请稍后重试');
  }
};

/**
 * 按当前筛选导出诊疗目录（与导入模板同列；超上限时后端 truncated）
 */
const handleExportCatalog = async () => {
  exportLoading.value = true;
  try {
    const result = await exportServiceCatalogApi({
      keyword: queryParams.value.keyword || undefined,
      categoryCode: queryParams.value.categoryCode || undefined,
      statusEnum: normalizeStatusEnumForApi(queryParams.value.statusEnum),
      childrenFlag: normalizeChildrenFlagForApi(queryParams.value.childrenFlag),
      exportColumns: SERVICE_CATALOG_EXPORT_COLUMNS,
    });
    if (!result?.fileBytes?.trim()) {
      ElMessage.warning('导出内容为空');
      return;
    }
    downloadBinaryExportFile(result, result.fileName ?? '诊疗目录导出.xlsx');
    if (result.truncated) {
      ElMessage.warning(
        `已导出 ${result.exportedRowCount ?? 0} 条（已达上限，结果已截断，请缩小筛选后再导）`,
      );
      return;
    }
    ElMessage.success(`已导出 ${result.exportedRowCount ?? 0} 条`);
  } catch (error) {
    console.error('导出诊疗目录失败:', error);
    ElMessage.error('导出失败，请稍后重试');
  } finally {
    exportLoading.value = false;
  }
};

const handleImportClick = () => {
  importFileInputRef.value?.click();
};

async function pollImportUntilSettled(jobId: string): Promise<ServiceCatalogImportProgressResult> {
  const maxRounds = 3600;
  for (let i = 0; i < maxRounds; i += 1) {
    try {
      const progress = await getImportServiceCatalogProgressApi(jobId);
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

async function handleImportFailure(progress: ServiceCatalogImportProgressResult) {
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
      const fileResult = await downloadServiceCatalogImportErrorFileApi(token);
      if (!fileResult?.fileBytes?.trim()) {
        ElMessage.warning('错误标注文件为空或已过期');
        return;
      }
      downloadBinaryExportFile(fileResult, fileResult.fileName ?? '诊疗目录导入_错误标注.xlsx');
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
    const started = await startImportServiceCatalogApi({
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
    console.error('导入诊疗目录失败:', error);
    ElMessage.error((error as Error)?.message || '导入失败，请稍后重试');
  } finally {
    importLoading.value = false;
    if (importProgress.value?.status === 'SUCCEEDED') {
      importProgress.value = null;
    }
  }
};

const handleEnable = async () => {
  if (selectedRows.value.every((row) => row.statusEnum === 2)) {
    ElMessage.warning('请先选择要启用的数据');
    return;
  }

  try {
    const ids = selectedRows.value.map((row) => row.id).filter(Boolean);
    if (ids.length === 0) {
      ElMessage.warning('所选数据中没有有效的ID');
      return;
    }

    await startServiceCatalogApi(ids as string[]);
    ElMessage.success('启用成功');
    selectedRows.value = [];
    await handleQuery(queryParams.value);
    await nextTick();
    const gridApi = tableLayoutRef.value?.gridApi;
    if (gridApi) {
      const grid = gridApi.grid || gridApi;
      grid.clearCheckboxRow();
    }
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleDisable = async () => {
  if (selectedRows.value.every((row) => row.statusEnum === 3)) {
    ElMessage.warning('请先选择要禁用的数据');
    return;
  }

  // 内置项不可停用：从批量中剔除并提示
  const builtInNames = selectedRows.value
    .filter((row) => isBuiltInServiceRow(row))
    .map((row) => String(row.name ?? '').trim())
    .filter(Boolean);
  if (builtInNames.length > 0) {
    ElMessage.warning(`${BUILT_IN_ACTIVITY_EDIT_HINT}：${builtInNames.join('、')}`);
  }
  const disableTargets = selectedRows.value.filter((row) => !isBuiltInServiceRow(row));
  if (disableTargets.length === 0) {
    return;
  }

  try {
    const ids = disableTargets.map((row) => row.id).filter(Boolean);
    if (ids.length === 0) {
      ElMessage.warning('所选数据中没有有效的ID');
      return;
    }

    await stopServiceCatalogApi(ids as string[]);
    ElMessage.success('禁用成功');
    selectedRows.value = [];
    await handleQuery(queryParams.value);
    await nextTick();
    const gridApi = tableLayoutRef.value?.gridApi;
    if (gridApi) {
      const grid = gridApi.grid || gridApi;
      grid.clearCheckboxRow();
    }
    nextTick(() => {
      const gridApi = tableLayoutRef.value?.gridApi;
      if (gridApi && typeof gridApi.clearCheckboxRow === 'function') {
        gridApi.clearCheckboxRow();
      } else if (gridApi && typeof gridApi.clearCheckboxReserve === 'function') {
        gridApi.clearCheckboxReserve();
      }
    });
  } catch (error) {
    console.error('禁用失败:', error);
    ElMessage.error('禁用失败，请稍后重试');
  }
};

const handleStart = async (row: ServiceCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await startServiceCatalogApi([String(row.id)]);
    ElMessage.success('启用成功');
    handleQuery(queryParams.value);
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleStop = async (row: ServiceCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  // Gate：内置诊疗禁止停用
  if (isBuiltInServiceRow(row)) {
    ElMessage.warning(BUILT_IN_ACTIVITY_EDIT_HINT);
    return;
  }
  try {
    await stopServiceCatalogApi([String(row.id)]);
    ElMessage.success('停用成功');
    handleQuery(queryParams.value);
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const doDeleteByIds = async (ids: string[]) => {
  if (ids.length === 0) {
    ElMessage.warning('请先选择要删除的数据');
    return;
  }
  await ElMessageBox.confirm(
    `确定要删除选中的 ${ids.length} 条诊疗项目吗？删除后无法恢复！`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    },
  );
  await deleteServiceCatalogApi(ids);
  ElMessage.success('删除成功');
  await handleQuery(queryParams.value);
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

const handleDelete = async () => {
  // 内置项不可删：从批量中剔除并提示
  const builtInNames = selectedRows.value
    .filter((row) => isBuiltInServiceRow(row))
    .map((row) => String(row.name ?? '').trim())
    .filter(Boolean);
  if (builtInNames.length > 0) {
    ElMessage.warning(`${BUILT_IN_ACTIVITY_EDIT_HINT}：${builtInNames.join('、')}`);
  }
  const ids = selectedRows.value
    .filter((row) => !isBuiltInServiceRow(row))
    .map((row) => row.id)
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

const handleRowDelete = async (row: ServiceCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  // Gate：内置诊疗禁止删除
  if (isBuiltInServiceRow(row)) {
    ElMessage.warning(BUILT_IN_ACTIVITY_EDIT_HINT);
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

const handleEdit = (row: ServiceCatalogItem) => {
  editFormData.value = { ...row };
  editDialogVisible.value = true;
};

/** 弹窗内按列表顺序切换到指定诊疗 */
function openServiceEditorById(id: string) {
  const row = tableData.value.find((item) => String(item.id ?? '') === id);
  if (!row) {
    ElMessage.warning('未找到对应记录');
    return;
  }
  editFormData.value = { ...row };
  editDialogVisible.value = true;
}

/** 弹窗上一个/下一个：同页切记录，边界则翻页后再开相邻记录 */
async function handleNavigateRecord(direction: -1 | 1) {
  const plan = resolveCatalogRecordNavigation({
    recordIds: catalogNavIds.value,
    currentId: editFormData.value?.id,
    direction,
    pageNo: queryParams.value.pageNo,
    pageSize: queryParams.value.pageSize,
    listTotal: total.value,
  });
  if (plan.kind === 'none') {
    return;
  }
  if (plan.kind === 'same-page') {
    openServiceEditorById(plan.id);
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
    openServiceEditorById(targetId);
  } finally {
    catalogNavLoading.value = false;
  }
}

const editDialogVisible = ref(false);
const editFormData = ref<Partial<ServiceCatalogItem>>({});

/** 当前页诊疗 id，供编辑弹窗上下翻 */
const catalogNavIds = computed(() =>
  tableData.value.map((row) => String(row.id ?? '').trim()).filter(Boolean),
);

const catalogNavLoading = ref(false);

// 初始化选项数据
const initOptions = ref<ServiceCatalogInitResponse>({} as ServiceCatalogInitResponse);

/** 将 init 接口分类选项转为下拉框格式 */
function convertCategoryOptions(options: unknown): Array<{ label: string; value: string }> {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options
    .filter(
      (item): item is { label: string; value: number | string } =>
        typeof item === 'object' &&
        item !== null &&
        'label' in item &&
        'value' in item &&
        (typeof item.value === 'string' || typeof item.value === 'number'),
    )
    .map((item) => ({
      label: item.label,
      value: String(item.value),
    }));
}

const categoryCodeOptions = computed(() =>
  convertCategoryOptions(initOptions.value?.diagnosisCategoryOptions),
);

const loadInitOptions = async () => {
  try {
    const response = await serviceCatalogInitApi();
    const data: ServiceCatalogInitResponse = response.data || ({} as ServiceCatalogInitResponse);
    if (data) {
      initOptions.value = data;
    }
  } catch (error) {
    console.error('获取初始化选项失败:', error);
    ElMessage.error('获取初始化选项失败，请稍后重试');
  }
};

const handleEditSuccess = () => {
  editFormData.value = {};
  handleQuery(queryParams.value);
};

/** 收费页等引导跳转：按 openId 打开编辑，或按 keyword 预填检索 */
async function applyRouteDeepLink() {
  const openId = String(route.query.openId ?? '').trim();
  const keyword = String(route.query.keyword ?? '').trim();
  if (keyword) {
    queryParams.value.keyword = keyword;
  }
  if (!openId && !keyword) {
    return;
  }
  if (openId) {
    try {
      const response = await getServiceCatalogApi({ id: openId });
      const item = (response.data ?? response) as ServiceCatalogItem;
      if (item?.id) {
        handleEdit(item);
        const nextQuery = { ...route.query };
        delete nextQuery.openId;
        delete nextQuery.keyword;
        void router.replace({ path: route.path, query: nextQuery });
        return;
      }
    } catch (error) {
      console.warn('按 openId 打开诊疗目录失败，回落关键词检索', error);
    }
  }
  await handleQuery(queryParams.value);
  if (openId || keyword) {
    const nextQuery = { ...route.query };
    delete nextQuery.openId;
    delete nextQuery.keyword;
    void router.replace({ path: route.path, query: nextQuery });
  }
}

const initPage = async () => {
  await loadInitOptions();
  const hasDeepLink = Boolean(
    String(route.query.openId ?? '').trim() || String(route.query.keyword ?? '').trim(),
  );
  if (hasDeepLink) {
    await applyRouteDeepLink();
    return;
  }
  await handleQuery(queryParams.value);
};

onMounted(() => {
  void initPage();
});

watch(
  () => [route.query.openId, route.query.keyword],
  () => {
    if (String(route.query.openId ?? '').trim() || String(route.query.keyword ?? '').trim()) {
      void applyRouteDeepLink();
    }
  },
);
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
          @click="handleEdit(row)"
        >
          {{ row.name }}
        </a>
      </template>

      <template #statusEnum="{ row }">
        <StatusEnumTag :status-enum="row.statusEnum" :status-text="row.statusEnum_enumText" />
      </template>

      <template #childrenFlag="{ row }">
        <ElTag :type="isChildrenBound(row.childrenFlag) ? 'success' : 'info'" size="small">
          {{ isChildrenBound(row.childrenFlag) ? '是' : '否' }}
        </ElTag>
      </template>

      <template #retailPrice="{ row }">
        {{ formatRetailPrice(row.retailPrice) }}
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-1">
          <ElButton type="primary" link @click="handleEdit(row)">编辑</ElButton>
          <ElButton v-if="row.statusEnum !== 2" type="success" link @click="handleStart(row)">
            启用
          </ElButton>
          <ElButton
            v-else-if="row.statusEnum === 2 && !isBuiltInServiceRow(row)"
            type="warning"
            link
            @click="handleStop(row)"
          >
            停用
          </ElButton>
          <ElButton
            v-if="!isBuiltInServiceRow(row)"
            type="danger"
            link
            @click="handleRowDelete(row)"
          >
            删除
          </ElButton>
        </div>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="editDialogVisible"
      :form-data="editFormData"
      :init-options="initOptions"
      :record-ids="catalogNavIds"
      :page-no="queryParams.pageNo"
      :page-size="queryParams.pageSize"
      :list-total="total"
      :navigating="catalogNavLoading"
      @navigate="handleNavigateRecord"
      @success="handleEditSuccess"
    />

    <ElDialog
      v-model="importProgressVisible"
      title="诊疗目录导入"
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
