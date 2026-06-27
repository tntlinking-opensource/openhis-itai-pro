<!--
  药库库存明细（现库）：T2 查询 + T3 行内停供/恢复；契约 AS-INV-WHS-004-SDD。
  状态分层 — 业务：筛选、rows、汇总、追溯弹窗；视图：分页；临时：无。
-->
<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  ExportInventoryDetailCurrentResponse,
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

import {
  exportInventoryDetailCurrentApi,
  loadInventoryDetailFormInitApi,
  pageInventoryDetailCurrentApi,
  resumeInventoryItemsApi,
  retireInventoryItemsApi,
  sumInventoryDetailCurrentApi,
} from '#/api/inventory/warehouse/inventory-detail';
import AppDialog from '#/components/app-dialog.vue';
import { PublicationStatusCode } from '#/constants/domain/publication-status';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'InventoryWarehouseInventoryDetailPage' });

/** inventory_range 术语概念码（与后端 InventoryWarehouseDetailConstants 对齐）。 */
const INVENTORY_RANGE_ZERO_CODE = '2';
const INVENTORY_RANGE_GT_ZERO_CODE = '3';

/** 与 IF-02 对齐的筛选 + 分页；`zeroStockFilter` 为术语码，请求前映射为 `zeroStockOnly`。 */
interface InventoryDetailFormState {
  pageNo: number;
  pageSize: number;
  keyword?: string;
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

const queryParams = reactive<InventoryDetailFormState>({
  pageNo: 1,
  pageSize: 10,
  zeroStockFilter: INVENTORY_RANGE_GT_ZERO_CODE,
});

// OptionDto → 表格筛选用 ApiOptionItem（value 一律 string，医保等级在请求体中再转 Integer）。
function optionDtosToSelectItems(list?: OptionDto[]): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}

/** 数量：后端已拼接展示串，前端直接显示。 */
function fmtDisplayQuantityCell(row: InventoryDetailListRowDto): string {
  return String(row.displayQuantityText ?? '').trim();
}

/** 单价 + 单位名（元/单位）。 */
function fmtUnitPriceCell(price: unknown, unitName?: string): string {
  const amt = fmtAmt(price);
  const unit = String(unitName ?? '').trim();
  if (!amt) return '';
  if (!unit) return amt;
  return `${amt} 元/${unit}`;
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

// 组装 IF-02 分页请求体。
function buildPageRequest() {
  const z = mapInventoryRangeToZeroStockOnly(queryParams.zeroStockFilter);
  const ed = queryParams.expireWithinDays?.trim();
  const expireWithinDays = ed && /^\d+$/.test(ed) ? Number.parseInt(ed, 10) : undefined;
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    keyword: queryParams.keyword?.trim() || undefined,
    medicationCategoryCodes: emptyToUndef(queryParams.medicationCategoryCodes),
    deviceCategoryCodes: emptyToUndef(queryParams.deviceCategoryCodes),
    inventoryStatusEnums: emptyToUndef(queryParams.inventoryStatusEnums),
    chrgitmLvList: toChrgitmLvInts(queryParams.chrgitmLvList),
    zeroStockOnly: z,
    expireWithinDays,
  };
}

// 组装 IF-03/06 同筛选请求体（无分页键）。
function buildSumExportFilter(): SumInventoryDetailCurrentRequest {
  const p = buildPageRequest();
  return {
    keyword: p.keyword,
    medicationCategoryCodes: p.medicationCategoryCodes,
    deviceCategoryCodes: p.deviceCategoryCodes,
    chrgitmLvList: p.chrgitmLvList,
    zeroStockOnly: p.zeroStockOnly,
    expireWithinDays: p.expireWithinDays,
    inventoryStatusEnums: p.inventoryStatusEnums,
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
  },
});

const { run: runPage, loading: pageLoading } = useRequest(pageInventoryDetailCurrentApi, {
  manual: true,
  onSuccess: (data) => {
    tableData.value = data?.rows ?? [];
    total.value = Number(data?.totalElements ?? 0);
  },
});

const { run: runSum, loading: sumLoading } = useRequest(sumInventoryDetailCurrentApi, {
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

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '关键字',
    prop: 'keyword',
    type: 'input',
    placeholder: '名称、编码、拼音、五笔、批号',
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

const tableColumns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    width: 56,
    align: 'center',
    headerAlign: 'center',
    fixed: 'left',
  },
  {
    prop: 'name',
    label: '名称',
    minWidth: 160,
    align: 'left',
    headerAlign: 'center',
    showOverflowTooltip: true,
    fixed: 'left',
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
    prop: 'displayQuantityText',
    label: '库存数量',
    minWidth: 120,
    align: 'right',
    headerAlign: 'center',
    formatter: (row) => fmtDisplayQuantityCell(row as InventoryDetailListRowDto),
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
    prop: 'supplierName',
    label: '供应商',
    minWidth: 140,
    align: 'left',
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
    prop: 'inventoryStatusText',
    label: '供应状态',
    width: 100,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'chrgitmLvText',
    label: '医保等级',
    width: 100,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'medicationCategoryText',
    label: '药品分类',
    width: 100,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'deviceCategoryText',
    label: '耗材分类',
    width: 100,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'displayCostUnitPrice',
    label: '进价',
    width: 130,
    align: 'right',
    headerAlign: 'center',
    formatter: (row) =>
      fmtUnitPriceCell(
        (row as InventoryDetailListRowDto).displayCostUnitPrice,
        (row as InventoryDetailListRowDto).displayCostUnitName,
      ),
  },
  {
    prop: 'displayPriceUnitPrice',
    label: '售价',
    width: 130,
    align: 'right',
    headerAlign: 'center',
    formatter: (row) =>
      fmtUnitPriceCell(
        (row as InventoryDetailListRowDto).displayPriceUnitPrice,
        (row as InventoryDetailListRowDto).displayPriceUnitName,
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
  {
    label: '操作',
    width: 220,
    align: 'center',
    headerAlign: 'center',
    fixed: 'right',
    slot: 'rowActions',
  },
];

// 同步拉分页与底部汇总（IF-02 + IF-03）。
async function getList() {
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
  queryParams.expireWithinDays = undefined;
  queryParams.medicationCategoryCodes = undefined;
  queryParams.deviceCategoryCodes = undefined;
  queryParams.inventoryStatusEnums = undefined;
  queryParams.chrgitmLvList = undefined;
  queryParams.zeroStockFilter = INVENTORY_RANGE_GT_ZERO_CODE;
  queryParams.pageNo = 1;
  getList();
}

// 将导出字节写入本地下载（body 支持 Base64 或 number[]）。
function triggerFileDownload(data: ExportInventoryDetailCurrentResponse) {
  const raw = data?.body;
  if (raw === null || raw === undefined) {
    ElMessage.warning('导出内容为空');
    return;
  }
  let bytes: Uint8Array;
  if (typeof raw === 'string') {
    const bin = atob(raw);
    bytes = Uint8Array.from({ length: bin.length }, (_, i) => bin.codePointAt(i) ?? 0);
  } else if (Array.isArray(raw)) {
    bytes = new Uint8Array(raw);
  } else {
    ElMessage.warning('导出数据格式异常');
    return;
  }
  const blob = new Blob([bytes as unknown as BlobPart], {
    type: data.contentType ?? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = data.fileName ?? 'inventory-detail.xlsx';
  a.click();
  URL.revokeObjectURL(url);
  if (data.truncated === true) {
    ElMessage.warning('导出已按行数上限截断');
  }
}

// IF-06：按当前筛选导出 Excel。
async function handleExportExcel() {
  const body = buildSumExportFilter();
  const data = await runExport(body);
  triggerFileDownload(data);
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

// 打开追溯码弹窗：无 traceNo 时提示待后端字段。
function handleOpenTrace(row: InventoryDetailListRowDto) {
  const t = row.traceNo?.trim();
  if (!t) {
    ElMessage.info('当前行未下发追溯码（traceNo），请在后端 DTO 补字段后展示');
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
          pageLoading ||
          sumLoading ||
          exportLoading ||
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
            :loading="exportLoading"
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
