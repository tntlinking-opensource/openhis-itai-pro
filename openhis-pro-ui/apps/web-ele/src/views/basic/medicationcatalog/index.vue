<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { MedicationCatalogInitResponse, MedicationCatalogItem } from '#/api';

import { computed, onActivated, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElDialog, ElMessage, ElMessageBox, ElProgress, ElTag } from 'element-plus';

import {
  deleteMedicationCatalogApi,
  downloadMedicationCatalogImportErrorFileApi,
  downloadMedicationCatalogImportTemplateApi,
  exportMedicationCatalogApi,
  getImportMedicationCatalogProgressApi,
  medicationCatalogInitApi,
  pageMedicationCatalogApi,
  startImportMedicationCatalogApi,
  startMedicationCatalogApi,
  stopMedicationCatalogApi,
} from '#/api';
import type { MedicationCatalogImportProgressResult } from '#/api';
import { PublicationStatusCode } from '#/constants/domain/publication-status';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { MEDICATION_CATALOG_EXPORT_COLUMNS } from '#/utils/export/catalog-excel-export-columns';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatMoneyUnitPriceDisplay } from '#/utils/format/money-scale';

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';
import CatalogLotPriceDialog from '../catalog/catalog-lot-price-dialog.vue';
import { resolveCatalogRecordNavigation } from '../catalog/catalog-record-nav';
import MedicationCatalogEditDialog from './medication-catalog-edit-dialog.vue';

const route = useRoute();
const router = useRouter();

function isMedicationPublicationActive(row: MedicationCatalogItem): boolean {
  return row.statusEnum === PublicationStatusCode.ACTIVE;
}

/** 后端启用接口仅允许 INACTIVE 停用态 */
function isMedicationPublicationRetired(row: MedicationCatalogItem): boolean {
  return row.statusEnum === PublicationStatusCode.INACTIVE;
}

const queryParams = ref({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  /** 药品分类筛选；空字符串表示「全部」 */
  categoryCode: '',
  /** 重点药类型；空字符串表示「全部」 */
  keyDrugType: '' as string,
  /** 启用状态；空字符串表示「全部」 */
  statusEnum: '' as string,
});

const tableData = ref<MedicationCatalogItem[]>([]);

const loading = ref(false);
const total = ref(0);
const initOptions = ref<MedicationCatalogInitResponse>({} as MedicationCatalogInitResponse);

const catalogEditOpen = ref(false);
const catalogEditMedId = ref<string>('');

/** 当前页药品 id，供编辑弹窗上下翻 */
const catalogNavIds = computed(() =>
  tableData.value.map((row) => String(row.medicationDefId ?? '').trim()).filter(Boolean),
);

const catalogNavLoading = ref(false);

const lotPriceOpen = ref(false);
const lotPriceItemId = ref('');
const lotPriceItemName = ref('');
const lotPriceCategoryCode = ref('');

const importFileInputRef = ref<HTMLInputElement | null>(null);
const importLoading = ref(false);
const exportLoading = ref(false);
const importProgressVisible = ref(false);
const importProgress = ref<MedicationCatalogImportProgressResult | null>(null);

const PROGRESS_POLL_INTERVAL_MS = 500;

const importProgressPercent = computed(() => {
  const p = importProgress.value?.percent;
  if (typeof p === 'number' && Number.isFinite(p)) {
    return Math.min(100, Math.max(0, Math.round(p)));
  }
  return 0;
});

const importProgressText = computed(() => {
  const p = importProgress.value;
  if (!p) {
    return '';
  }
  if (p.total != null && p.total > 0 && p.processed != null) {
    return `${p.processed}/${p.total} · ${p.message || '导入中…'}`;
  }
  return p.message || '导入中…';
});

const importProgressBarStatus = computed(() => {
  const status = importProgress.value?.status;
  if (status === 'FAILED') {
    return 'exception';
  }
  if (status === 'SUCCEEDED') {
    return 'success';
  }
  return undefined;
});

function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    setTimeout(resolve, ms);
  });
}

function isTooManyRequestsError(error: unknown): boolean {
  const err = error as { response?: { status?: number }; code?: number };
  return err.response?.status === 429 || err.code === 429;
}

/** File → 纯 base64（去掉 data URL 前缀），供导入接口 fileBytes 使用 */
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
    initOptions.value?.medicationCategoryCodeOptions as CategoryOptionItem[] | undefined,
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
    slot: 'name',
    minWidth: 160,
    showOverflowTooltip: true,
  },
  {
    prop: 'specification',
    label: '规格',
    width: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'purchaseUnitRatio',
    label: '采购拆零比例',
    width: 120,
    align: 'right',
    headerAlign: 'right',
  },
  {
    prop: 'statusEnum',
    label: '状态',
    slot: 'statusEnum',
    width: 90,
    align: 'center',
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

/**
 * 列表价格列：销售参考价固定两位，附包装单位文案。
 *
 * @param price 销售参考价
 * @param unitText 包装单位展示名
 */
function formatRetailPriceWithUnit(price: unknown, unitText: unknown): string {
  if (price === null || price === undefined || price === '') {
    return '';
  }
  const formatted = formatMoneyUnitPriceDisplay(price, 2);
  if (!formatted) {
    return String(price);
  }
  const unit = typeof unitText === 'string' && unitText.trim() ? unitText.trim() : '';
  return unit ? `${formatted}/${unit}` : formatted;
}

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入药品名称或编码',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'categoryCode',
    label: '药品分类',
    placeholder: '请选择药品分类',
    clearable: true,
    filterable: true,
    /** TableLayout FormItem 默认 multiple=true，单选分类须显式关闭 */
    multiple: false,
    options: categoryCodeOptions.value,
  },
  {
    type: 'select',
    prop: 'keyDrugType',
    label: '重点药类型',
    placeholder: '全部',
    clearable: true,
    multiple: false,
    options: [
      { label: '全部', value: '' },
      { label: '基药', value: 'BASIC' },
      { label: '国家重点监控药品', value: 'NATIONAL_MONITOR' },
      { label: '集采药品', value: 'CENTRAL_PURCHASE' },
      { label: '抗生素', value: 'ANTIBIOTIC' },
    ],
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
]);

/** 分类筛选归一：空/数组转可提交的 string（对齐耗材目录） */
function normalizeCategoryCodeForApi(raw: unknown): string | undefined {
  if (raw === undefined || raw === null) {
    return undefined;
  }
  // multi-select 误开时取首项，避免 Array.toString() 拼出 "1,2"
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

/** 重点药类型筛选归一：空/全部 → undefined；否则传 KeyDrugType code */
function normalizeKeyDrugTypeForApi(raw: unknown): string | undefined {
  if (raw === undefined || raw === null) {
    return undefined;
  }
  const s = String(raw).trim();
  if (s === '' || s === '全部') {
    return undefined;
  }
  return s;
}

/** 发布状态筛选归一：空/全部 → undefined */
function normalizeStatusEnumForApi(raw: unknown): string | undefined {
  if (raw === undefined || raw === null) {
    return undefined;
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
    const response = await pageMedicationCatalogApi({
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      keyword: queryParams.value.keyword || undefined,
      categoryCode: normalizeCategoryCodeForApi(queryParams.value.categoryCode),
      keyDrugType: normalizeKeyDrugTypeForApi(queryParams.value.keyDrugType),
      statusEnum: normalizeStatusEnumForApi(queryParams.value.statusEnum),
    });

    const parsed = parsePagedRowsFromApiData<MedicationCatalogItem>(response.data);
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
    keyDrugType: '',
  };
  handleQuery(queryParams.value);
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  handleQuery(queryParams.value);
};

const selectedRows = ref<MedicationCatalogItem[]>([]);

const handleSelectionChange = (selection: MedicationCatalogItem[]) => {
  selectedRows.value = selection;
};

/** 打开新增/编辑弹窗（不走路由） */
function openCatalogEditor(medicationDefId: string) {
  if (medicationDefId !== 'new' && !medicationDefId) {
    ElMessage.warning('缺少药品ID');
    return;
  }
  catalogEditMedId.value = medicationDefId;
  catalogEditOpen.value = true;
}

/** 弹窗上一个/下一个：同页切 id，边界则翻页后再开相邻记录 */
async function handleCatalogNavigate(direction: -1 | 1) {
  const plan = resolveCatalogRecordNavigation({
    recordIds: catalogNavIds.value,
    currentId: catalogEditMedId.value,
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

/** 下载药品目录导入模板（v1.6） */
const handleDownloadImportTemplate = async () => {
  try {
    const data = await downloadMedicationCatalogImportTemplateApi();
    if (!data?.fileBytes?.trim()) {
      ElMessage.warning('模板内容为空，请稍后重试');
      return;
    }
    downloadBinaryExportFile(data, data.fileName ?? '药品目录导入模板_v1.6.xlsx');
    ElMessage.success('模板已下载');
  } catch (error) {
    console.error('下载导入模板失败:', error);
    ElMessage.error('下载导入模板失败，请稍后重试');
  }
};

/**
 * 按当前筛选导出药品目录（与导入模板同列；超上限时后端 truncated）
 */
const handleExportCatalog = async () => {
  exportLoading.value = true;
  try {
    const result = await exportMedicationCatalogApi({
      keyword: queryParams.value.keyword || undefined,
      categoryCode: queryParams.value.categoryCode || undefined,
      keyDrugType: normalizeKeyDrugTypeForApi(queryParams.value.keyDrugType),
      statusEnum: normalizeStatusEnumForApi(queryParams.value.statusEnum),
      exportColumns: MEDICATION_CATALOG_EXPORT_COLUMNS,
    });
    if (!result?.fileBytes?.trim()) {
      ElMessage.warning('导出内容为空');
      return;
    }
    downloadBinaryExportFile(result, result.fileName ?? '药品目录导出.xlsx');
    if (result.truncated) {
      ElMessage.warning(
        `已导出 ${result.exportedRowCount ?? 0} 条（已达上限，结果已截断，请缩小筛选后再导）`,
      );
      return;
    }
    ElMessage.success(`已导出 ${result.exportedRowCount ?? 0} 条`);
  } catch (error) {
    console.error('导出药品目录失败:', error);
    ElMessage.error('导出失败，请稍后重试');
  } finally {
    exportLoading.value = false;
  }
};

const handleImportClick = () => {
  importFileInputRef.value?.click();
};

async function pollImportUntilSettled(
  jobId: string,
): Promise<MedicationCatalogImportProgressResult> {
  const maxRounds = 3600;
  for (let i = 0; i < maxRounds; i += 1) {
    try {
      const progress = await getImportMedicationCatalogProgressApi(jobId);
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

async function handleImportFailure(progress: MedicationCatalogImportProgressResult) {
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
      const fileResult = await downloadMedicationCatalogImportErrorFileApi(token);
      if (!fileResult?.fileBytes?.trim()) {
        ElMessage.warning('错误标注文件为空或已过期');
        return;
      }
      downloadBinaryExportFile(fileResult, fileResult.fileName ?? '药品目录导入_错误标注.xlsx');
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
  const lowerName = file.name.toLowerCase();
  if (!lowerName.endsWith('.xlsx')) {
    ElMessage.warning('请上传 .xlsx 格式的导入模板');
    return;
  }
  importLoading.value = true;
  importProgressVisible.value = true;
  importProgress.value = { status: 'PENDING', percent: 0, message: '准备上传…' };
  try {
    const fileBytes = await readFileAsBase64(file);
    const started = await startImportMedicationCatalogApi({
      fileBytes,
      fileName: file.name,
    });
    const jobId = started?.jobId;
    if (!jobId) {
      throw new Error('启动导入失败：未返回任务号');
    }
    const progress = await pollImportUntilSettled(String(jobId));
    if (progress.status === 'SUCCEEDED') {
      ElMessage.success(`导入成功 ${progress.successCount ?? 0} 条`);
      await handleQuery(queryParams.value);
      importProgressVisible.value = false;
      return;
    }
    await handleImportFailure(progress);
  } catch (error: unknown) {
    console.error('导入失败:', error);
    const err = error as { message?: string };
    ElMessage.error(err.message || '导入失败，请稍后重试');
  } finally {
    importLoading.value = false;
    if (importProgress.value?.status === 'SUCCEEDED') {
      importProgress.value = null;
    }
  }
};

const handleRowClick = (row: MedicationCatalogItem) => {
  openCatalogEditor(String(row.medicationDefId || ''));
};

/** 弹窗保存成功后刷新当前表格 */
function handleCatalogFormSuccess() {
  handleQuery(queryParams.value);
}

/** 打开批号价格查看弹窗 */
function handleViewLotPrices(row: MedicationCatalogItem) {
  const id = String(row.medicationDefId || '');
  if (!id) {
    ElMessage.warning('缺少药品ID');
    return;
  }
  lotPriceItemId.value = id;
  lotPriceItemName.value = typeof row.name === 'string' ? row.name : '';
  lotPriceCategoryCode.value = String(row.categoryCode ?? '').trim();
  lotPriceOpen.value = true;
}

const handleStart = async (row: MedicationCatalogItem) => {
  if (!row.medicationDefId) {
    ElMessage.warning('缺少药品ID');
    return;
  }
  try {
    await startMedicationCatalogApi([String(row.medicationDefId)]);
    ElMessage.success('启用成功');
    handleQuery(queryParams.value);
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleStop = async (row: MedicationCatalogItem) => {
  if (!row.medicationDefId) {
    ElMessage.warning('缺少药品ID');
    return;
  }
  try {
    await stopMedicationCatalogApi([String(row.medicationDefId)]);
    ElMessage.success('停用成功');
    handleQuery(queryParams.value);
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleBatchStart = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要启用的药品');
    return;
  }

  const ids = selectedRows.value
    .filter((row) => isMedicationPublicationRetired(row) && row.medicationDefId)
    .map((row) => String(row.medicationDefId));

  if (ids.length === 0) {
    ElMessage.warning('所选药品中没有处于「停用」状态的可启用项');
    return;
  }

  try {
    await startMedicationCatalogApi(ids);
    ElMessage.success(`成功启用 ${ids.length} 个药品`);
    handleQuery(queryParams.value);
    selectedRows.value = [];
  } catch (error) {
    console.error('批量启用失败:', error);
    ElMessage.error('批量启用失败，请稍后重试');
  }
};

const handleBatchStop = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要停用的药品');
    return;
  }

  const ids = selectedRows.value
    .filter((row) => isMedicationPublicationActive(row) && row.medicationDefId)
    .map((row) => String(row.medicationDefId));

  if (ids.length === 0) {
    ElMessage.warning('所选药品中没有处于「已启用」状态的可停用项');
    return;
  }

  try {
    await stopMedicationCatalogApi(ids);
    ElMessage.success(`成功停用 ${ids.length} 个药品`);
    handleQuery(queryParams.value);
    selectedRows.value = [];
  } catch (error) {
    console.error('批量停用失败:', error);
    ElMessage.error('批量停用失败，请稍后重试');
  }
};

const doDeleteByIds = async (ids: string[]) => {
  if (ids.length === 0) {
    ElMessage.warning('请先选择要删除的药品');
    return;
  }
  await ElMessageBox.confirm(`确定要删除选中的 ${ids.length} 条药品吗？删除后无法恢复！`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  });
  await deleteMedicationCatalogApi(ids);
  ElMessage.success('删除成功');
  await handleQuery(queryParams.value);
  selectedRows.value = [];
};

const handleDelete = async () => {
  const ids = selectedRows.value
    .map((row) => row.medicationDefId)
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

const handleRowDelete = async (row: MedicationCatalogItem) => {
  if (!row.medicationDefId) {
    ElMessage.warning('缺少药品ID');
    return;
  }
  try {
    await doDeleteByIds([String(row.medicationDefId)]);
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      ElMessage.error(error?.msg || error?.message || '删除失败，请稍后重试');
    }
  }
};

const getStatusTagType = (statusEnum: unknown): 'danger' | 'info' | 'success' | 'warning' => {
  if (statusEnum === PublicationStatusCode.ACTIVE) {
    return 'success';
  }
  if (statusEnum === PublicationStatusCode.INACTIVE) {
    return 'warning';
  }
  if (statusEnum === PublicationStatusCode.DISCARDED) {
    return 'danger';
  }
  return 'info';
};

const initPage = async () => {
  try {
    const response = await medicationCatalogInitApi();
    const data: MedicationCatalogInitResponse =
      response.data || ({} as MedicationCatalogInitResponse);
    if (data) {
      initOptions.value = data;
    }
    handleQuery(queryParams.value);
  } catch (error) {
    console.error('初始化失败:', error);
  }
};

onActivated(() => {
  const needRefresh = sessionStorage.getItem('medicationCatalogRefresh');
  if (needRefresh === 'true') {
    sessionStorage.removeItem('medicationCatalogRefresh');
    handleQuery(queryParams.value);
  }
});

/** 目录检查等引导跳转：按 openId 打开编辑，或按 keyword 预填检索 */
async function applyRouteDeepLink() {
  const openId = String(route.query.openId ?? '').trim();
  const keyword = String(route.query.keyword ?? '').trim();
  if (keyword) {
    queryParams.value.keyword = keyword;
  }
  if (openId) {
    openCatalogEditor(openId);
  }
  if (openId || keyword) {
    const nextQuery = { ...route.query };
    delete nextQuery.openId;
    delete nextQuery.keyword;
    void router.replace({ path: route.path, query: nextQuery });
  }
  if (keyword && !openId) {
    handleQuery(queryParams.value);
  }
}

watch(
  () => [route.query.openId, route.query.keyword],
  () => {
    if (String(route.query.openId ?? '').trim() || String(route.query.keyword ?? '').trim()) {
      void applyRouteDeepLink();
    }
  },
);

initPage();
void applyRouteDeepLink();
</script>

<template>
  <Page>
    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :max-height="BASIC_TABLE_MAX_HEIGHT"
      :show-top-query="true"
      :show-pagination="true"
      row-key="medicationDefId"
      border
      stripe
      size="default"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
    >
      <template #toolbar-actions>
        <div class="flex flex-wrap items-center gap-2 [&_.el-button]:!m-0">
          <ElButton type="primary" @click="handleAdd">新增</ElButton>
          <ElButton :loading="importLoading" @click="handleDownloadImportTemplate">
            下载导入模板
          </ElButton>
          <ElButton :loading="importLoading" type="primary" plain @click="handleImportClick">
            导入
          </ElButton>
          <ElButton :loading="exportLoading" @click="handleExportCatalog">导出</ElButton>
          <ElButton type="success" @click="handleBatchStart">批量启用</ElButton>
          <ElButton type="warning" @click="handleBatchStop">批量停用</ElButton>
          <ElButton type="danger" @click="handleDelete">删除</ElButton>
        </div>
        <input
          ref="importFileInputRef"
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          class="sr-only"
          tabindex="-1"
          aria-hidden="true"
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
        <ElTag
          :type="getStatusTagType(row.statusEnum)"
          :effect="row.statusEnum === PublicationStatusCode.DISCARDED ? 'plain' : 'dark'"
        >
          {{ row.statusEnum_enumText || '未知' }}
        </ElTag>
      </template>

      <template #retailPrice="{ row }">
        {{ formatRetailPriceWithUnit(row.retailPrice, row.packageUnitCode_dictText) }}
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-1">
          <ElButton type="primary" link @click="handleRowClick(row)">编辑</ElButton>
          <ElButton type="primary" link @click="handleViewLotPrices(row)">价格</ElButton>
          <ElButton
            v-if="isMedicationPublicationActive(row)"
            type="warning"
            link
            @click="handleStop(row)"
          >
            停用
          </ElButton>
          <ElButton
            v-else-if="isMedicationPublicationRetired(row)"
            type="success"
            link
            @click="handleStart(row)"
          >
            启用
          </ElButton>
          <ElButton type="danger" link @click="handleRowDelete(row)">删除</ElButton>
        </div>
      </template>
    </TableLayout>

    <MedicationCatalogEditDialog
      v-model="catalogEditOpen"
      :medication-def-id="catalogEditMedId"
      :init-options-source="initOptions"
      :record-ids="catalogNavIds"
      :page-no="queryParams.pageNo"
      :page-size="queryParams.pageSize"
      :list-total="total"
      :navigating="catalogNavLoading"
      @navigate="handleCatalogNavigate"
      @success="handleCatalogFormSuccess"
    />

    <CatalogLotPriceDialog
      v-model="lotPriceOpen"
      catalog-type="medication"
      :item-id="lotPriceItemId"
      :item-name="lotPriceItemName"
      :category-code="lotPriceCategoryCode"
    />

    <ElDialog
      v-model="importProgressVisible"
      title="药品目录导入"
      width="480px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
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
          class="max-h-40 overflow-y-auto text-sm text-red-600"
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
