<!--
  药库库存明细：现库 T2+T3（停供/恢复）；选备份单号时只读查静表快照。
  状态分层 — 业务：筛选（含 backupBusNo）、rows、汇总；视图：分页。
-->
<script setup lang="ts">
import type {
  InventoryDetailListRowDto,
  OptionDto,
  SumInventoryDetailCurrentRequest,
} from '#/api/inventory/warehouse/inventory-detail';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { formatDate } from '@vben/utils';

import { Download, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTableColumn,
} from 'element-plus';

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
  INVENTORY_WARNING_MODE_EXPIRE,
  INVENTORY_WARNING_MODE_LOW_STOCK,
  type InventoryWarningMode,
} from '#/api/inventory/warehouse/inventory-detail';
import TraceCodeListDialog from '#/components/trace/trace-code-list-dialog.vue';
import { PublicationStatusCode } from '#/constants/domain/publication-status';
import { useRequest } from '#/hooks/use-request';
import {
  REPORT_QUERY_CONTROL,
  REPORT_TABLE_NUM_CELL,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPageFooter,
  ReportQueryBar,
  ReportTableScroll,
} from '#/views/report/shared';

defineOptions({ name: 'InventoryWarehouseInventoryDetailPage' });

/** inventory_range 术语概念码（与后端 InventoryWarehouseDetailConstants 对齐）。 */
const INVENTORY_RANGE_ZERO_CODE = '2';
const INVENTORY_RANGE_GT_ZERO_CODE = '3';
const DEFAULT_EXPIRE_WARNING_DAYS = 180;
const PAGE_TITLE = '药库库存明细';

/** 筛选 + 分页；有 backupBusNo 时走备份读，否则走现库。 */
interface InventoryDetailFormState {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  backupBusNo?: string;
  warehouseIds?: string[];
  supplierIds?: string[];
  medicationCategoryCodes?: string[];
  deviceCategoryCodes?: string[];
  inventoryStatusEnums?: string[];
  chrgitmLvList?: string[];
  zeroStockFilter?: typeof INVENTORY_RANGE_ZERO_CODE | typeof INVENTORY_RANGE_GT_ZERO_CODE;
  expireWithinDays?: string;
  warningMode?: InventoryWarningMode;
}

const tableData = ref<InventoryDetailListRowDto[]>([]);
const total = ref(0);
const sumLineCostTotal = ref('');
const sumLinePriceTotal = ref('');
const sumTruncated = ref(false);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const chrgitmLvOptions = ref<ApiOptionItem[]>([]);
const inventoryStatusOptions = ref<ApiOptionItem[]>([]);
const medicationCategoryOptions = ref<ApiOptionItem[]>([]);
const deviceCategoryOptions = ref<ApiOptionItem[]>([]);
const inventoryRangeOptions = ref<ApiOptionItem[]>([]);
const warehouseOptions = ref<ApiOptionItem[]>([]);
const supplierOptions = ref<ApiOptionItem[]>([]);
const backupBusNoOptions = ref<ApiOptionItem[]>([]);
/** 系统设置：效期预警天数（剩余天数 ≤ 该值视为临期）。 */
const expireWarningDays = ref(DEFAULT_EXPIRE_WARNING_DAYS);

const queryParams = reactive<InventoryDetailFormState>({
  pageNo: 1,
  pageSize: 15,
  zeroStockFilter: INVENTORY_RANGE_GT_ZERO_CODE,
});

const route = useRoute();
const router = useRouter();

const footerSummaryItems = computed(() => [
  { label: '进价合计', value: sumLineCostTotal.value },
  { label: '售价合计', value: sumLinePriceTotal.value, primary: true },
]);

/** 是否按备份单号查静表（有单号即备份读；未选则现库）。 */
const isBackupQuery = computed(() => Boolean(queryParams.backupBusNo?.trim()));

const tableLoading = computed(
  () =>
    initLoading.value ||
    busNoLoading.value ||
    pageLoading.value ||
    backupPageLoading.value ||
    sumLoading.value ||
    backupSumLoading.value ||
    exportLoading.value ||
    backupExportLoading.value ||
    retireLoading.value ||
    resumeLoading.value,
);

const keywordPlaceholder = computed(() => {
  if (isBackupQuery.value) {
    return '名称、编码、商品名、拼音、五笔';
  }
  return '名称、编码、商品名、拼音、五笔、批号';
});

/** 解析路由中的预警模式参数。 */
function parseRouteWarningMode(raw: unknown): InventoryWarningMode | undefined {
  const value = Array.isArray(raw) ? raw[0] : raw;
  const normalized = String(value ?? '').trim();
  if (
    normalized === INVENTORY_WARNING_MODE_EXPIRE ||
    normalized === INVENTORY_WARNING_MODE_LOW_STOCK
  ) {
    return normalized;
  }
  return undefined;
}

/** 从路由 query 写回预警模式。 */
function applyRouteWarningMode() {
  queryParams.warningMode = parseRouteWarningMode(route.query.warningMode);
}

/** OptionDto → 表格筛选用 ApiOptionItem（value 一律 string）。 */
function optionDtosToSelectItems(list?: OptionDto[]): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}

/** 下拉选项转为 ElSelect 可用结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 单价 + 单位名（元/单位）。 */
function fmtUnitPriceCell(price: unknown, unitName?: string): string {
  return fmtPackageUnitPrice(price as string | number | null | undefined, unitName);
}

/** 金额/数量类展示：空则空白，否则本地化数字。 */
function fmtAmt(v: unknown): string {
  if (v === null || v === undefined || v === '') return '';
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return '';
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
}

/** 日期展示：去掉时分秒，仅保留 YYYY-MM-DD。 */
function fmtDateOnly(v: unknown): string {
  if (v === null || v === undefined || v === '') return '';
  const formatted = formatDate(v as Date | number | string, 'YYYY-MM-DD');
  return formatted.length > 10 ? formatted.slice(0, 10) : formatted;
}

type InventoryDetailExpiryStatus = 'expired' | 'nearExpiry' | '';

type InventoryDetailExpiryRowClass =
  | 'inventory-detail-row--danger'
  | 'inventory-detail-row--warning'
  | '';

/** 解析行效期状态：过期 / 临期 / 正常。 */
function resolveInventoryDetailExpiryStatus(
  row: InventoryDetailListRowDto,
): InventoryDetailExpiryStatus {
  const remaining = row.remainingDays;
  if (remaining === null || remaining === undefined) {
    return '';
  }
  if (remaining < 0) {
    return 'expired';
  }
  if (remaining <= expireWarningDays.value) {
    return 'nearExpiry';
  }
  return '';
}

/** 项目名称后展示的效期标签文案。 */
function resolveInventoryDetailExpiryLabel(row: InventoryDetailListRowDto): string {
  const status = resolveInventoryDetailExpiryStatus(row);
  if (status === 'expired') {
    return '过期';
  }
  if (status === 'nearExpiry') {
    return '临期';
  }
  return '';
}

/** 按剩余效期天数着色：过期 danger，临期（≤预警天数）warning。 */
function resolveInventoryDetailRowClassName({
  row,
}: {
  row: InventoryDetailListRowDto;
}): InventoryDetailExpiryRowClass {
  const status = resolveInventoryDetailExpiryStatus(row);
  if (status === 'expired') {
    return 'inventory-detail-row--danger';
  }
  if (status === 'nearExpiry') {
    return 'inventory-detail-row--warning';
  }
  return '';
}

/** 非空数组才下发，避免传空数组与后端默认不一致。 */
function emptyToUndef(arr?: string[]): string[] | undefined {
  return arr && arr.length > 0 ? arr : undefined;
}

/** 医保等级多选 string → List<Integer>。 */
function toChrgitmLvInts(codes?: string[]): number[] | undefined {
  if (!codes || codes.length === 0) return undefined;
  const nums = codes.map((c) => Number.parseInt(String(c), 10)).filter((n) => !Number.isNaN(n));
  return nums.length > 0 ? nums : undefined;
}

/** inventory_range 术语码归一化。 */
function normalizeInventoryRangeCode(code?: string | string[]): string | undefined {
  const raw = Array.isArray(code) ? code[0] : code;
  const normalized = String(raw ?? '').trim();
  return normalized || undefined;
}

/** inventory_range 术语码 → zeroStockOnly。 */
function mapInventoryRangeToZeroStockOnly(code?: string | string[]): boolean {
  const normalized = normalizeInventoryRangeCode(code);
  if (normalized === INVENTORY_RANGE_ZERO_CODE) return true;
  return false;
}

/** 组装现库分页请求体。 */
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
    warningMode: queryParams.warningMode,
  };
}

/** 组装现库汇总/导出同筛选请求体（无分页键）。 */
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
    warningMode: p.warningMode,
  };
}

/** 组装备份分页请求体。 */
function buildBackupPageRequest() {
  const base = buildPageRequest();
  const busNo = queryParams.backupBusNo?.trim();
  return {
    ...base,
    backupBusNo: busNo || undefined,
  };
}

/** 组装备份汇总/导出同筛选请求体。 */
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
    const warningDays = Number(d.expireWarningDays);
    expireWarningDays.value =
      Number.isFinite(warningDays) && warningDays > 0 ? warningDays : DEFAULT_EXPIRE_WARNING_DAYS;
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
    void getList();
  },
});

const { run: runResume, loading: resumeLoading } = useRequest(resumeInventoryItemsApi, {
  manual: true,
  onSuccess: (data) => {
    ElMessage.success(`已恢复供应，更新 ${data?.affectedRows ?? 0} 条`);
    void getList();
  },
});

const traceCodeListDialogRef = ref<InstanceType<typeof TraceCodeListDialog> | null>(null);

/** 同步拉分页与底部汇总（有备份单号走静表，否则现库）。 */
async function getList() {
  if (isBackupQuery.value) {
    const pageBody = buildBackupPageRequest();
    const sumBody = buildBackupSumExportFilter();
    await Promise.all([runBackupPage(pageBody), runBackupSum(sumBody)]);
  } else {
    const pageBody = buildPageRequest();
    const sumBody = buildSumExportFilter();
    await Promise.all([runPage(pageBody), runSum(sumBody)]);
  }
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 顶部查询：回到第一页。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 清空筛选并回到第一页。 */
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
  queryParams.warningMode = undefined;
  queryParams.pageNo = 1;
  void router.replace({ path: route.path, query: {} });
  void getList();
}

/** 按当前筛选导出 Excel（有备份单号则导备份）。 */
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

/** 切换每页条数。 */
function handlePageSizeChange(limit: number) {
  queryParams.pageSize = limit;
  queryParams.pageNo = 1;
  void getList();
}

/** 切换页码。 */
function handleCurrentPageChange(page: number) {
  queryParams.pageNo = page;
  void getList();
}

/** 是否可停供。 */
function canRetireInventoryRow(row: InventoryDetailListRowDto): boolean {
  return row.inventoryStatusEnum === PublicationStatusCode.ACTIVE;
}

/** 是否可恢复供应。 */
function canResumeInventoryRow(row: InventoryDetailListRowDto): boolean {
  return row.inventoryStatusEnum === PublicationStatusCode.INACTIVE;
}

/** 打开本库追溯码列表弹窗。 */
function handleOpenTrace(row: InventoryDetailListRowDto) {
  void traceCodeListDialogRef.value?.loadForInventoryRow({
    locationId: row.warehouseId ?? '',
    itemId: row.itemId ?? '',
    lotNumber: row.lotNumber ?? '',
    productName: row.name,
  });
}

/** 单行停供。 */
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
    // 用户取消确认框时不提示
  }
}

/** 单行恢复供应。 */
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
    // 用户取消确认框时不提示
  }
}

onMounted(() => {
  applyRouteWarningMode();
  runInit();
  runListBackupBusNo({});
  void getList();
});

watch(
  () => route.query.warningMode,
  () => {
    applyRouteWarningMode();
    queryParams.pageNo = 1;
    void getList();
  },
);
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryParams as Record<string, unknown>">
          <template #fields>
            <ElFormItem label="备份单号">
              <ElSelect
                v-model="queryParams.backupBusNo"
                clearable
                filterable
                placeholder="不选则查现库存"
                :class="REPORT_QUERY_CONTROL"
              >
                <ElOption
                  v-for="item in toSelectOptions(backupBusNoOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="关键字">
              <ElInput
                v-model="queryParams.keyword"
                clearable
                :placeholder="keywordPlaceholder"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="库房">
              <ElSelect
                v-model="queryParams.warehouseIds"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="可多选"
                :class="REPORT_QUERY_CONTROL"
              >
                <ElOption
                  v-for="item in toSelectOptions(warehouseOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="供应商">
              <ElSelect
                v-model="queryParams.supplierIds"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="可多选"
                :class="REPORT_QUERY_CONTROL"
              >
                <ElOption
                  v-for="item in toSelectOptions(supplierOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="近效期(天)">
              <ElInput
                v-model="queryParams.expireWithinDays"
                clearable
                placeholder="剩余天数 ≤"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="药品类型">
              <ElSelect
                v-model="queryParams.medicationCategoryCodes"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="可多选"
                :class="REPORT_QUERY_CONTROL"
              >
                <ElOption
                  v-for="item in toSelectOptions(medicationCategoryOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="耗材类型">
              <ElSelect
                v-model="queryParams.deviceCategoryCodes"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="可多选"
                :class="REPORT_QUERY_CONTROL"
              >
                <ElOption
                  v-for="item in toSelectOptions(deviceCategoryOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="供应状态">
              <ElSelect
                v-model="queryParams.inventoryStatusEnums"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="可多选"
                :class="REPORT_QUERY_CONTROL"
              >
                <ElOption
                  v-for="item in toSelectOptions(inventoryStatusOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="医保等级">
              <ElSelect
                v-model="queryParams.chrgitmLvList"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="可多选"
                :class="REPORT_QUERY_CONTROL"
              >
                <ElOption
                  v-for="item in toSelectOptions(chrgitmLvOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="库存范围">
              <ElSelect v-model="queryParams.zeroStockFilter" :class="REPORT_QUERY_CONTROL">
                <ElOption
                  v-for="item in toSelectOptions(inventoryRangeOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
          </template>
          <template #actions>
            <ElButton type="primary" :icon="Search" :loading="tableLoading" @click="handleQuery">
              搜索
            </ElButton>
            <ElButton :icon="Refresh" :loading="tableLoading" @click="handleReset">重置</ElButton>
          </template>
        </ReportQueryBar>
      </template>

      <template #toolbar>
        <ReportListToolbar :title="`${PAGE_TITLE} · 明细列表`" :total="total">
          <template #actions>
            <ElButton
              type="primary"
              plain
              :icon="Download"
              :loading="exportLoading || backupExportLoading"
              @click="handleExportExcel"
            >
              导出 Excel
            </ElButton>
          </template>
        </ReportListToolbar>
      </template>

      <template #table>
        <ReportTableScroll ref="tableScrollRef">
          <template #default="{ tableHeight }">
            <ReportElTable
              class="inventory-detail-table"
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              row-key="id"
              :row-class-name="resolveInventoryDetailRowClassName"
            >
              <ElTableColumn
                type="index"
                label="序号"
                width="56"
                align="center"
                header-align="center"
                fixed="left"
              />
              <ElTableColumn
                prop="productBusNo"
                label="编码"
                width="110"
                align="center"
                header-align="center"
                fixed="left"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="name"
                label="项目名称"
                min-width="168"
                align="center"
                header-align="center"
                fixed="left"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  <span class="inventory-detail-name-cell">
                    <span>{{ row.name }}</span>
                    <span
                      v-if="resolveInventoryDetailExpiryLabel(row)"
                      class="inventory-detail-expiry-label"
                      :class="
                        resolveInventoryDetailExpiryStatus(row) === 'expired'
                          ? 'inventory-detail-expiry-label--danger'
                          : 'inventory-detail-expiry-label--warning'
                      "
                    >
                      {{ resolveInventoryDetailExpiryLabel(row) }}
                    </span>
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="merchandiseName"
                label="商品名称"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="categoryLabel"
                label="项目类别"
                width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="lotNumber"
                label="批号"
                min-width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="packageStockText"
                label="库存(包装)"
                min-width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              />
              <ElTableColumn
                prop="splitStockText"
                label="库存(最小)"
                min-width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              />
              <ElTableColumn
                prop="warehouseName"
                label="库房"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="totalVolume"
                label="规格"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="manufacturer"
                label="厂家"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="supplierName"
                label="供应商"
                min-width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="doseFormLabel"
                label="剂型"
                width="90"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="expirationDate"
                label="效期"
                width="120"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">{{ fmtDateOnly(row.expirationDate) }}</template>
              </ElTableColumn>
              <ElTableColumn
                prop="productionDate"
                label="生产日期"
                width="120"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">{{ fmtDateOnly(row.productionDate) }}</template>
              </ElTableColumn>
              <ElTableColumn
                prop="remainingDays"
                label="剩余天数"
                width="90"
                align="center"
                header-align="center"
              />
              <ElTableColumn
                prop="inventoryStatusLabel"
                label="供应状态"
                width="100"
                align="center"
                header-align="center"
              />
              <ElTableColumn
                label="进价"
                prop="lotCostPrice"
                width="130"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ fmtUnitPriceCell(row.lotCostPrice, row.packageUnitName) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="售价"
                prop="lotSalePrice"
                width="130"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ fmtUnitPriceCell(row.lotSalePrice, row.packageUnitName) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="进价总额"
                prop="lineCostTotal"
                width="120"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">{{ fmtAmt(row.lineCostTotal) }}</template>
              </ElTableColumn>
              <ElTableColumn
                label="售价总额"
                prop="linePriceTotal"
                width="120"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">{{ fmtAmt(row.linePriceTotal) }}</template>
              </ElTableColumn>
              <ElTableColumn
                v-if="!isBackupQuery"
                label="操作"
                width="160"
                align="center"
                header-align="center"
                fixed="right"
              >
                <template #default="{ row }">
                  <ElButton link type="primary" @click="handleOpenTrace(row)">追溯码</ElButton>
                  <ElButton
                    v-if="canRetireInventoryRow(row)"
                    link
                    type="danger"
                    @click="handleRetireInventoryRow(row)"
                  >
                    停供
                  </ElButton>
                  <ElButton
                    v-if="canResumeInventoryRow(row)"
                    link
                    type="primary"
                    @click="handleResumeInventoryRow(row)"
                  >
                    取消停供
                  </ElButton>
                </template>
              </ElTableColumn>
            </ReportElTable>
          </template>
        </ReportTableScroll>
      </template>

      <template #footer>
        <ReportPageFooter
          :summary-items="footerSummaryItems"
          :current-page="queryParams.pageNo"
          :page-size="queryParams.pageSize"
          :page-sizes="[15, 20, 50, 100]"
          :total="total"
          @size-change="handlePageSizeChange"
          @current-change="handleCurrentPageChange"
        >
          <span v-if="sumTruncated" class="text-sm text-[var(--el-color-warning)]">
            汇总已截断上限
          </span>
        </ReportPageFooter>
      </template>
    </ReportListPageLayout>

    <TraceCodeListDialog ref="traceCodeListDialogRef" />
  </Page>
</template>

<style scoped lang="scss">
.inventory-detail-table {
  :deep(.inventory-detail-row--warning) {
    --el-table-tr-bg-color: var(--el-color-warning-light-7);

    td.el-table__cell {
      background: var(--el-color-warning-light-7) !important;
    }

    &.el-table__row--striped td.el-table__cell,
    td.el-table-fixed-column--left,
    td.el-table-fixed-column--right {
      background: var(--el-color-warning-light-7) !important;
    }
  }

  :deep(.inventory-detail-row--danger) {
    --el-table-tr-bg-color: var(--el-color-danger-light-7);

    td.el-table__cell {
      background: var(--el-color-danger-light-7) !important;
    }

    &.el-table__row--striped td.el-table__cell,
    td.el-table-fixed-column--left,
    td.el-table-fixed-column--right {
      background: var(--el-color-danger-light-7) !important;
    }
  }
}

.inventory-detail-name-cell {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  max-width: 100%;
}

.inventory-detail-expiry-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.inventory-detail-expiry-label--warning {
  color: var(--el-color-warning);
}

.inventory-detail-expiry-label--danger {
  color: var(--el-color-danger);
}
</style>
