<!--
  药库库存明细：现库 T2+T3（停供/恢复）；选备份单号时只读查静表快照。
  状态分层 — 业务：筛选（含 backupBusNo）、rows、汇总；视图：分页；临时：追溯弹窗。
-->
<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  InventoryDetailListRowDto,
  OptionDto,
  SumInventoryDetailCurrentRequest,
} from '#/api/inventory/warehouse/inventory-detail';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { Download } from '@element-plus/icons-vue';
import { ElButton, ElInput, ElMessage, ElMessageBox } from 'element-plus';

import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';

import {
  exportInventoryDetailBackupApi,
  exportInventoryDetailCurrentApi,
  fmtPackageUnitPrice,
  listInventoryDetailBackupBusNoApi,
  loadInventoryDetailFormInitApi,
  pageInventoryDetailBackupApi,
  pageInventoryDetailCurrentApi,
  resumeInventoryItemsApi,
  retireInventoryItemsApi,
  sumInventoryDetailBackupApi,
  sumInventoryDetailCurrentApi,
} from '#/api/inventory/warehouse/inventory-detail';
import AppDialog from '#/components/app-dialog.vue';
import { PublicationStatusCode } from '#/constants/domain/publication-status';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'InventoryWarehouseInventoryDetailPage' });

/** inventory_range 术语概念码（与后端 InventoryWarehouseDetailConstants 对齐）。 */
const INVENTORY_RANGE_ZERO_CODE = '2';
const INVENTORY_RANGE_GT_ZERO_CODE = '3';

/** 筛选 + 分页；有 backupBusNo 时走备份读，否则走现库。 */
interface InventoryDetailFormState {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  /** 备份批次单号；有值查备份静表，空则查现库 */
  backupBusNo?: string;
  /** 库房主键多选（Long 以 string 传递） */
  warehouseIds?: string[];
  /** 供应商主键多选 */
  supplierIds?: string[];
  medicationCategoryCodes?: string[];
  deviceCategoryCodes?: string[];
  inventoryStatusEnums?: string[];
  chrgitmLvList?: string[];
  /** inventory_range 概念码：2=数量等于0，3=数量大于0 */
  zeroStockFilter?: typeof INVENTORY_RANGE_ZERO_CODE | typeof INVENTORY_RANGE_GT_ZERO_CODE;
  expireWithinDays?: string;
}

const tableData = ref<InventoryDetailListRowDto[]>([]);
const total = ref(0);
const sumLineCostTotal = ref('');
const sumLinePriceTotal = ref('');
const sumTruncated = ref(false);

const chrgitmLvOptions = ref<ApiOptionItem[]>([]);
const inventoryStatusOptions = ref<ApiOptionItem[]>([]);
const medicationCategoryOptions = ref<ApiOptionItem[]>([]);
const deviceCategoryOptions = ref<ApiOptionItem[]>([]);
const inventoryRangeOptions = ref<ApiOptionItem[]>([]);
const warehouseOptions = ref<ApiOptionItem[]>([]);
const supplierOptions = ref<ApiOptionItem[]>([]);
const backupBusNoOptions = ref<ApiOptionItem[]>([]);

const queryParams = reactive<InventoryDetailFormState>({
  pageNo: 1,
  pageSize: 10,
  zeroStockFilter: INVENTORY_RANGE_GT_ZERO_CODE,
});

/** 是否按备份单号查静表（有单号即备份读；未选则现库）。 */
const isBackupQuery = computed(() => Boolean(queryParams.backupBusNo?.trim()));

// OptionDto → 表格筛选用 ApiOptionItem（value 一律 string，医保等级在请求体中再转 Integer）。
function optionDtosToSelectItems(list?: OptionDto[]): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}

/** 单价 + 单位名（元/单位）。 */
function fmtUnitPriceCell(price: unknown, unitName?: string): string {
  return fmtPackageUnitPrice(price as string | number | null | undefined, unitName);
}

// 金额/数量类展示：空则空白，否则本地化数字。
function fmtAmt(v: unknown): string {
  if (v === null || v === undefined || v === '') return '';
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return '';
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
}

// 非空数组才下发，避免传空数组与后端默认不一致。
function emptyToUndef(arr?: string[]): string[] | undefined {
  return arr && arr.length > 0 ? arr : undefined;
}

// 医保等级多选 string → IF-02 的 List<Integer>。
function toChrgitmLvInts(codes?: string[]): number[] | undefined {
  if (!codes || codes.length === 0) return undefined;
  const nums = codes.map((c) => Number.parseInt(String(c), 10)).filter((n) => !Number.isNaN(n));
  return nums.length > 0 ? nums : undefined;
}

// inventory_range 术语码 → IF-02 的 zeroStockOnly（2=等于0，3=大于0；未识别时默认大于0）。
function normalizeInventoryRangeCode(code?: string | string[]): string | undefined {
  const raw = Array.isArray(code) ? code[0] : code;
  const normalized = String(raw ?? '').trim();
  return normalized || undefined;
}

function mapInventoryRangeToZeroStockOnly(code?: string | string[]): boolean {
  const normalized = normalizeInventoryRangeCode(code);
  if (normalized === INVENTORY_RANGE_ZERO_CODE) return true;
  return false;
}

// 组装现库分页请求体。
function buildPageRequest() {
  const z = mapInventoryRangeToZeroStockOnly(queryParams.zeroStockFilter);
  const ed = queryParams.expireWithinDays?.trim();
  const expireWithinDays = ed && /^\d+$/.test(ed) ? Number.parseInt(ed, 10) : undefined;
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    keyword: queryParams.keyword?.trim() || undefined,
    warehouseIds: emptyToUndef(queryParams.warehouseIds),
    supplierIds: emptyToUndef(queryParams.supplierIds),
    medicationCategoryCodes: emptyToUndef(queryParams.medicationCategoryCodes),
    deviceCategoryCodes: emptyToUndef(queryParams.deviceCategoryCodes),
    inventoryStatusEnums: emptyToUndef(queryParams.inventoryStatusEnums),
    chrgitmLvList: toChrgitmLvInts(queryParams.chrgitmLvList),
    zeroStockOnly: z,
    expireWithinDays,
  };
}

// 组装现库汇总/导出同筛选请求体（无分页键）。
function buildSumExportFilter(): SumInventoryDetailCurrentRequest {
  const p = buildPageRequest();
  return {
    keyword: p.keyword,
    warehouseIds: p.warehouseIds,
    supplierIds: p.supplierIds,
    medicationCategoryCodes: p.medicationCategoryCodes,
    deviceCategoryCodes: p.deviceCategoryCodes,
    chrgitmLvList: p.chrgitmLvList,
    zeroStockOnly: p.zeroStockOnly,
    expireWithinDays: p.expireWithinDays,
    inventoryStatusEnums: p.inventoryStatusEnums,
  };
}

// 组装备份分页请求体（关键字不含批号，由后端约束）。
function buildBackupPageRequest() {
  const base = buildPageRequest();
  const busNo = queryParams.backupBusNo?.trim();
  return {
    ...base,
    backupBusNo: busNo || undefined,
  };
}

// 组装备份汇总/导出同筛选请求体。
function buildBackupSumExportFilter() {
  const base = buildSumExportFilter();
  const busNo = queryParams.backupBusNo?.trim();
  return {
    ...base,
    backupBusNo: busNo || undefined,
  };
}

const { run: runInit, loading: initLoading } = useRequest(loadInventoryDetailFormInitApi, {
  manual: true,
  onSuccess: (data) => {
    const d = data ?? {};
    chrgitmLvOptions.value = optionDtosToSelectItems(d.chrgitmLvOptions);
    inventoryStatusOptions.value = optionDtosToSelectItems(d.inventoryStatusOptions);
    medicationCategoryOptions.value = optionDtosToSelectItems(d.medicationCategoryOptions);
    deviceCategoryOptions.value = optionDtosToSelectItems(d.deviceCategoryOptions);
    inventoryRangeOptions.value = optionDtosToSelectItems(d.inventoryRangeOptions);
    warehouseOptions.value = optionDtosToSelectItems(d.warehouseOptions);
    supplierOptions.value = optionDtosToSelectItems(d.supplierOptions);
  },
});

const { run: runListBackupBusNo, loading: busNoLoading } = useRequest(
  listInventoryDetailBackupBusNoApi,
  {
    manual: true,
    onSuccess: (data) => {
      backupBusNoOptions.value = (data?.backupBusNoList ?? []).map((busNo) => ({
        label: busNo,
        value: busNo,
      }));
    },
  },
);

const { run: runPage, loading: pageLoading } = useRequest(pageInventoryDetailCurrentApi, {
  manual: true,
  onSuccess: (data) => {
    tableData.value = data?.rows ?? [];
    total.value = Number(data?.totalElements ?? 0);
  },
});

const { run: runBackupPage, loading: backupPageLoading } = useRequest(
  pageInventoryDetailBackupApi,
  {
    manual: true,
    onSuccess: (data) => {
      tableData.value = data?.rows ?? [];
      total.value = Number(data?.totalElements ?? 0);
    },
  },
);

const { run: runSum, loading: sumLoading } = useRequest(sumInventoryDetailCurrentApi, {
  manual: true,
  onSuccess: (data) => {
    sumLineCostTotal.value = fmtAmt(data?.sumLineCostTotal);
    sumLinePriceTotal.value = fmtAmt(data?.sumLinePriceTotal);
    sumTruncated.value = Boolean(data?.truncated);
  },
});

const { run: runBackupSum, loading: backupSumLoading } = useRequest(sumInventoryDetailBackupApi, {
  manual: true,
  onSuccess: (data) => {
    sumLineCostTotal.value = fmtAmt(data?.sumLineCostTotal);
    sumLinePriceTotal.value = fmtAmt(data?.sumLinePriceTotal);
    sumTruncated.value = Boolean(data?.truncated);
  },
});

const { run: runExport, loading: exportLoading } = useRequest(exportInventoryDetailCurrentApi, {
  manual: true,
  showErrorMessage: true,
});

const { run: runBackupExport, loading: backupExportLoading } = useRequest(
  exportInventoryDetailBackupApi,
  {
    manual: true,
    showErrorMessage: true,
  },
);

const { run: runRetire, loading: retireLoading } = useRequest(retireInventoryItemsApi, {
  manual: true,
  onSuccess: (data) => {
    ElMessage.success(`停供成功，更新 ${data?.affectedRows ?? 0} 条`);
    getList();
  },
});

const { run: runResume, loading: resumeLoading } = useRequest(resumeInventoryItemsApi, {
  manual: true,
  onSuccess: (data) => {
    ElMessage.success(`已恢复供应，更新 ${data?.affectedRows ?? 0} 条`);
    getList();
  },
});

const traceDialogVisible = ref(false);
const traceDialogTitle = ref('追溯码');
const traceDialogText = ref('');

const keywordPlaceholder = computed(() => {
  if (isBackupQuery.value) {
    return '名称、编码、商品名、拼音、五笔';
  }
  return '名称、编码、商品名、拼音、五笔、批号';
});

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '备份单号',
    prop: 'backupBusNo',
    type: 'select',
    placeholder: '不选则查现库存',
    multiple: false,
    filterable: true,
    clearable: true,
    collapseTags: false,
    options: backupBusNoOptions.value,
  },
  {
    label: '关键字',
    prop: 'keyword',
    type: 'input',
    placeholder: keywordPlaceholder.value,
  },
  {
    label: '库房',
    prop: 'warehouseIds',
    type: 'select',
    placeholder: '可多选',
    multiple: true,
    collapseTags: true,
    clearable: true,
    options: warehouseOptions.value,
  },
  {
    label: '供应商',
    prop: 'supplierIds',
    type: 'select',
    placeholder: '可多选',
    multiple: true,
    collapseTags: true,
    clearable: true,
    options: supplierOptions.value,
  },
  {
    label: '近效期(天)',
    prop: 'expireWithinDays',
    type: 'input',
    placeholder: '剩余天数 ≤',
  },
  {
    label: '药品类型',
    prop: 'medicationCategoryCodes',
    type: 'select',
    placeholder: '可多选',
    multiple: true,
    collapseTags: true,
    clearable: true,
    options: medicationCategoryOptions.value,
  },
  {
    label: '耗材类型',
    prop: 'deviceCategoryCodes',
    type: 'select',
    placeholder: '可多选',
    multiple: true,
    collapseTags: true,
    clearable: true,
    options: deviceCategoryOptions.value,
  },
  {
    label: '供应状态',
    prop: 'inventoryStatusEnums',
    type: 'select',
    placeholder: '可多选',
    multiple: true,
    collapseTags: true,
    clearable: true,
    options: inventoryStatusOptions.value,
  },
  {
    label: '医保等级',
    prop: 'chrgitmLvList',
    type: 'select',
    placeholder: '可多选',
    multiple: true,
    collapseTags: true,
    clearable: true,
    options: chrgitmLvOptions.value,
  },
  {
    label: '库存范围',
    prop: 'zeroStockFilter',
    type: 'select',
    placeholder: '库存范围',
    multiple: false,
    filterable: false,
    clearable: false,
    options: inventoryRangeOptions.value,
  },
]);

const baseTableColumns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    width: 56,
    align: 'center',
    headerAlign: 'center',
    fixed: 'left',
  },
  {
    prop: 'productBusNo',
    label: '编码',
    width: 110,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
    fixed: 'left',
  },
  {
    prop: 'name',
    label: '项目名称',
    minWidth: 140,
    align: 'left',
    headerAlign: 'center',
    showOverflowTooltip: true,
    fixed: 'left',
  },
  {
    prop: 'merchandiseName',
    label: '商品名称',
    minWidth: 120,
    align: 'left',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'categoryLabel',
    label: '项目类别',
    width: 100,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'lotNumber',
    label: '批号',
    minWidth: 110,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'packageStockText',
    label: '库存(包装)',
    minWidth: 100,
    align: 'right',
    headerAlign: 'center',
  },
  {
    prop: 'splitStockText',
    label: '库存(最小)',
    minWidth: 100,
    align: 'right',
    headerAlign: 'center',
  },
  {
    prop: 'packageUnitRatio',
    label: '拆零换算比',
    width: 80,
    align: 'center',
    headerAlign: 'center',
    formatter: (row) => fmtAmt((row as InventoryDetailListRowDto).packageUnitRatio),
  },
  {
    prop: 'warehouseName',
    label: '库房',
    minWidth: 100,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'totalVolume',
    label: '规格',
    minWidth: 100,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'manufacturer',
    label: '厂家',
    minWidth: 120,
    align: 'left',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'supplierName',
    label: '供应商',
    minWidth: 140,
    align: 'left',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'doseFormLabel',
    label: '剂型',
    width: 90,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'approvalNumber',
    label: '国药准字',
    minWidth: 120,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'ybNo',
    label: '医保码',
    minWidth: 120,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'expirationDate',
    label: '效期',
    width: 120,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'productionDate',
    label: '生产日期',
    width: 120,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'remainingDays',
    label: '剩余天数',
    width: 90,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'inventoryStatusLabel',
    label: '供应状态',
    width: 100,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'chrgitmLvLabel',
    label: '医保等级',
    width: 100,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'lotCostPrice',
    label: '进价',
    width: 130,
    align: 'right',
    headerAlign: 'center',
    formatter: (row) =>
      fmtUnitPriceCell(
        (row as InventoryDetailListRowDto).lotCostPrice,
        (row as InventoryDetailListRowDto).packageUnitName,
      ),
  },
  {
    prop: 'lotSalePrice',
    label: '售价',
    width: 130,
    align: 'right',
    headerAlign: 'center',
    formatter: (row) =>
      fmtUnitPriceCell(
        (row as InventoryDetailListRowDto).lotSalePrice,
        (row as InventoryDetailListRowDto).packageUnitName,
      ),
  },
  {
    prop: 'lineCostTotal',
    label: '进价总额',
    width: 120,
    align: 'right',
    headerAlign: 'center',
    formatter: (_row, _c, cell) => fmtAmt(cell),
  },
  {
    prop: 'linePriceTotal',
    label: '售价总额',
    width: 120,
    align: 'right',
    headerAlign: 'center',
    formatter: (_row, _c, cell) => fmtAmt(cell),
  },
];

const actionColumn: TableColumn = {
  label: '操作',
  width: 220,
  align: 'center',
  headerAlign: 'center',
  fixed: 'right',
  slot: 'rowActions',
};

const tableColumns = computed<TableColumn[]>(() => {
  if (isBackupQuery.value) {
    return baseTableColumns;
  }
  return [...baseTableColumns, actionColumn];
});

// 同步拉分页与底部汇总（有备份单号走静表，否则现库）。
async function getList() {
  if (isBackupQuery.value) {
    const pageBody = buildBackupPageRequest();
    const sumBody = buildBackupSumExportFilter();
    await Promise.all([runBackupPage(pageBody), runBackupSum(sumBody)]);
    return;
  }
  const pageBody = buildPageRequest();
  const sumBody = buildSumExportFilter();
  await Promise.all([runPage(pageBody), runSum(sumBody)]);
}

// 顶部查询：回到第一页。
function handleTopQuery() {
  queryParams.pageNo = 1;
  getList();
}

// 清空筛选并回到第一页。
function handleReset() {
  queryParams.keyword = undefined;
  queryParams.backupBusNo = undefined;
  queryParams.warehouseIds = undefined;
  queryParams.supplierIds = undefined;
  queryParams.expireWithinDays = undefined;
  queryParams.medicationCategoryCodes = undefined;
  queryParams.deviceCategoryCodes = undefined;
  queryParams.inventoryStatusEnums = undefined;
  queryParams.chrgitmLvList = undefined;
  queryParams.zeroStockFilter = INVENTORY_RANGE_GT_ZERO_CODE;
  queryParams.pageNo = 1;
  getList();
}

// 按当前筛选导出 Excel（有备份单号则导备份）。
async function handleExportExcel() {
  const defaultFileName = isBackupQuery.value ? '库存明细备份.xlsx' : '库存明细.xlsx';
  const data = isBackupQuery.value
    ? await runBackupExport(buildBackupSumExportFilter())
    : await runExport(buildSumExportFilter());
  if (!data?.body?.trim()) {
    ElMessage.warning('导出内容为空');
    return;
  }
  downloadBinaryExportFile(data, defaultFileName);
  if (data.truncated) {
    ElMessage.warning('导出数据已触达行数上限，部分记录未包含在文件中');
  } else {
    ElMessage.success(`导出成功，共 ${data.exportedRowCount ?? 0} 行`);
  }
}

function handlePageSizeChange(limit: number) {
  queryParams.pageSize = limit;
  queryParams.pageNo = 1;
  getList();
}

function handleCurrentPageChange(page: number) {
  queryParams.pageNo = page;
  getList();
}

function canRetireInventoryRow(row: InventoryDetailListRowDto): boolean {
  return row.inventoryStatusEnum === PublicationStatusCode.ACTIVE;
}

function canResumeInventoryRow(row: InventoryDetailListRowDto): boolean {
  return row.inventoryStatusEnum === PublicationStatusCode.INACTIVE;
}

// 打开追溯码弹窗。
function handleOpenTrace(row: InventoryDetailListRowDto) {
  const t = row.traceNo?.trim();
  if (!t) {
    ElMessage.info('当前行无追溯码');
    return;
  }
  traceDialogTitle.value = `追溯码 · ${row.name ?? ''}`;
  traceDialogText.value = t;
  traceDialogVisible.value = true;
}

// 单行停供（IF-08）。
async function handleRetireInventoryRow(row: InventoryDetailListRowDto) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认对「${row.name ?? row.id}」停供？`, '停供', {
      type: 'warning',
      confirmButtonText: '确认停供',
      cancelButtonText: '取消',
    });
    await runRetire({ inventoryItemIds: [row.id] });
  } catch {
    // 用户取消确认框时不提示；接口错误由 useRequest 统一提示。
  }
}

// 单行恢复供应（IF-09）。
async function handleResumeInventoryRow(row: InventoryDetailListRowDto) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认恢复「${row.name ?? row.id}」供应？`, '取消停供', {
      type: 'info',
      confirmButtonText: '确认恢复',
      cancelButtonText: '取消',
    });
    await runResume({ inventoryItemIds: [row.id] });
  } catch {
    // 用户取消确认框时不提示。
  }
}

onMounted(() => {
  runInit();
  runListBackupBusNo({});
  getList();
});
</script>

<template>
  <Page auto-content-height>
    <div class="box-border flex min-h-0 flex-1 flex-col gap-2 overflow-hidden p-2">
      <TableLayout
        :table-data="tableData"
        :loading="
          initLoading ||
          busNoLoading ||
          pageLoading ||
          backupPageLoading ||
          sumLoading ||
          backupSumLoading ||
          exportLoading ||
          backupExportLoading ||
          retireLoading ||
          resumeLoading
        "
        :total="total"
        :query-params="queryParams as Record<string, unknown>"
        :form-items="formItems"
        :table-columns="tableColumns"
        :show-top-query="true"
        :show-pagination="false"
        row-key="id"
        border
        stripe
        @query="handleTopQuery"
        @reset="handleReset"
      >
        <template #toolbar-actions>
          <ElButton
            type="danger"
            plain
            :icon="Download"
            :loading="exportLoading || backupExportLoading"
            @click="handleExportExcel"
          >
            导出Excel
          </ElButton>
        </template>
        <template #rowActions="{ row }">
          <ElButton link type="primary" @click="handleOpenTrace(row as InventoryDetailListRowDto)">
            追溯码
          </ElButton>
          <ElButton
            v-if="canRetireInventoryRow(row as InventoryDetailListRowDto)"
            link
            type="danger"
            @click="handleRetireInventoryRow(row as InventoryDetailListRowDto)"
          >
            停供
          </ElButton>
          <ElButton
            v-if="canResumeInventoryRow(row as InventoryDetailListRowDto)"
            link
            type="primary"
            @click="handleResumeInventoryRow(row as InventoryDetailListRowDto)"
          >
            取消停供
          </ElButton>
        </template>
      </TableLayout>
      <div
        class="flex shrink-0 flex-wrap items-center justify-end gap-x-8 gap-y-2 border-t border-[var(--el-border-color-lighter)] px-2 py-2 text-sm text-[var(--el-text-color-regular)]"
      >
        <span>
          进价合计：
          <span class="text-[var(--el-text-color-primary)]">{{ sumLineCostTotal }}</span>
        </span>
        <span>
          售价合计：
          <span class="text-[var(--el-text-color-primary)]">{{ sumLinePriceTotal }}</span>
        </span>
        <span v-if="sumTruncated" class="text-[var(--el-color-warning)]">（汇总已截断上限）</span>
      </div>
      <div class="flex shrink-0 justify-end px-2 pb-2">
        <ElPagination
          :current-page="queryParams.pageNo"
          :page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handlePageSizeChange"
          @current-change="handleCurrentPageChange"
        />
      </div>
    </div>
    <AppDialog v-model="traceDialogVisible" :title="traceDialogTitle" width="520px" :footer="false">
      <ElInput v-model="traceDialogText" type="textarea" :rows="5" readonly />
    </AppDialog>
  </Page>
</template>
