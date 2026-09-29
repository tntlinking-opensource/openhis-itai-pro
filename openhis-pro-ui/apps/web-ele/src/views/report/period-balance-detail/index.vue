<!--
  库房期初期末明细：选库房即可（药/耗由服务端按现库/快照 item_table 解析）；主表 + 展开流水。
-->
<script setup lang="ts">
import type {
  PeriodBalanceDetailColumnDto,
  PeriodBalanceDetailItemTable,
  PeriodBalanceDetailListRowDto,
  PagePeriodBalanceDetailRequest,
} from '#/api/report/period-balance-detail';
import type { ApiOptionItem } from '#/types/api-response';
import type { ReportPrintColumnDef } from '#/utils/print/print-report-list';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Download, Printer, Refresh, Search, Setting } from '@element-plus/icons-vue';
import {
  ElButton,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTableColumn,
} from 'element-plus';

import {
  exportPeriodBalanceDetailApi,
  ITEM_TABLE_DEVICE,
  ITEM_TABLE_MEDICATION,
  loadPeriodBalanceDetailFormInitApi,
  pagePeriodBalanceDetailApi,
} from '#/api/report/period-balance-detail';
import { useRequest } from '#/hooks/use-request';
import { buildExcelExportColumns } from '#/utils/export/build-excel-export-columns';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatPurchaseMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  REPORT_QUERY_CONTROL,
  REPORT_TABLE_AMOUNT_CELL,
  REPORT_TABLE_COL_GROUP_START,
  REPORT_TABLE_NUM_CELL,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPageFooter,
  ReportPrintFieldDialog,
  ReportQueryBar,
  ReportTableScroll,
  mapReportPrintRow,
  collectPagedReportRows,
  reportPrintFieldsOf,
  useReportListPrint,
} from '#/views/report/shared';
import {
  fmtReportPurchaseMoney,
  backupBusNoOptionsToSelectItems,
  optionDtosToSelectItems,
} from '#/views/report/shared/report-format';

import PeriodBalanceDetailFlowExpandPanel from './period-balance-detail-flow-expand-panel.vue';
import { buildPeriodBusinessSummary, formatMinUnitQuantity } from './settlement-flow-rows';

defineOptions({ name: 'PeriodBalanceDetailReportPage' });

interface SettlementTableColumn {
  prop: string;
  label: string;
  minWidth: number;
  fixed?: 'left' | 'right';
  align?: 'center' | 'left' | 'right';
  headerAlign?: 'center' | 'left' | 'right';
  className?: string;
  labelClassName?: string;
  formatter?: (row: PeriodBalanceDetailListRowDto) => string;
}

/** 表头对齐 class（覆盖报表表默认居中）。 */
const HEADER_ALIGN_CLASS = {
  left: 'period-balance-detail-header-left',
  center: 'period-balance-detail-header-center',
  right: 'period-balance-detail-header-right',
} as const;

/** 期初/期末区块分隔：期末首列浅蓝竖线。 */
const PERIOD_END_SPLIT_CLASS = 'period-balance-detail-col--end-split';

function buildColumnClassName(column: SettlementTableColumn): string | undefined {
  const parts = [column.className].filter(Boolean);
  if (column.align === 'right') {
    parts.push(REPORT_TABLE_NUM_CELL);
  }
  return parts.length > 0 ? parts.join(' ') : undefined;
}

function buildLabelClassName(column: SettlementTableColumn): string | undefined {
  const headerAlign = column.headerAlign ?? column.align ?? 'center';
  const parts = [HEADER_ALIGN_CLASS[headerAlign], column.labelClassName].filter(Boolean);
  return parts.length > 0 ? parts.join(' ') : undefined;
}

interface QueryState {
  pageNo: number;
  pageSize: number;
  locationId?: string;
  startBackupBusNo?: string;
  endBackupBusNo?: string;
  keyword?: string;
  isBus?: boolean | string;
  isBalance?: boolean | null | string;
}

const PAGE_TITLE = '库房期初期末明细';

/** 页内展示的账平核对公式（与后端 BR-INV-SET-011/012/013 一致，仅进价侧）。 */
const BALANCE_FORMULA_LINES = [
  '数量口径：一律以拆零最小单位计（与库存明细「库存(最小)」一致）',
  '期初/期末日终备份：单号日 D 为零点备份（建议每日 00:05），数量为 (D−1) 日终库存；期间含期初备份日全天；期末不选=查到当前实时库存',
  '默认「仅显示有业务」：列表只含期间发生过入库/出库/发药等动账（或调价）的品种，分页总数与列表一致',
  '推算期末数量 = 期初数量 − 发药 + 退药 + 入库/调入/退库/退领 + 盘点盈 − 出库/调出/退供应商/领用 − 盘点亏 + 损益调整',
  '盈亏数 = 实际期末数量 − 推算期末数量',
  '推算期末进价总额 = 期初进价总额 ± 期间各业务进价额（符号与数量侧一致）+ 调价进价盈亏',
  '盈亏差 = 实际期末进价总额 − 推算期末进价总额',
  '是否账平：盈亏数 = 0 且 盈亏差 = 0',
  '主表「期间业务」为摘要，展开行可看数量/金额；「仅显示有业务=否」时按全目录翻页（含无动账品种）',
];

const tableData = ref<PeriodBalanceDetailListRowDto[]>([]);
const total = ref(0);
const columnMeta = ref<PeriodBalanceDetailColumnDto[]>([]);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const warehouseOptions = ref<ApiOptionItem[]>([]);
const backupBusNoOptions = ref<ApiOptionItem[]>([]);
/** 最近一次查询服务端解析的物品主表（用于列头文案）。 */
const resolvedItemTable = ref<PeriodBalanceDetailItemTable>(ITEM_TABLE_MEDICATION);

const queryParams = reactive<QueryState>({
  pageNo: 1,
  pageSize: 20,
  isBus: 'true',
});

/** 主表可打印列目录（调价列始终入目录，无值可空）。 */
const printColumnCatalog = computed<ReportPrintColumnDef[]>(() => [
  { field: 'itemNo', title: '编码', align: 'center' },
  { field: 'productName', title: '名称', align: 'center' },
  { field: 'itemName', title: '商品名', align: 'center' },
  { field: 'specText', title: '规格', align: 'center' },
  { field: 'periodBusinessSummary', title: '期间业务', align: 'center' },
  { field: 'beginCostPackageUnitPrice', title: '期初单价', align: 'right' },
  { field: 'beginQuantity', title: '期初数量', align: 'right' },
  { field: 'beginCostAmount', title: '期初总额', align: 'right' },
  { field: 'endCostPackageUnitPrice', title: '期末单价', align: 'right' },
  { field: 'endQuantity', title: '期末数量', align: 'right' },
  { field: 'endCostAmount', title: '期末总额', align: 'right' },
  { field: 'priceAdjustmentCostAmount', title: '调价', align: 'right' },
  { field: 'computedEndQuantity', title: '推算数量', align: 'right' },
  { field: 'computedEndCostAmount', title: '推算总额', align: 'right' },
  { field: 'quantityDiff', title: '盈亏数', align: 'right' },
  { field: 'costAmountDiff', title: '盈亏差', align: 'right' },
  { field: 'isBalance', title: '是否账平', align: 'center' },
]);

/** 将主表一行映射为打印单元格（复用页内数量/金额格式化）。 */
function mapPeriodBalancePrintRow(row: PeriodBalanceDetailListRowDto): Record<string, string> {
  const fields = reportPrintFieldsOf(printColumnCatalog.value);
  return mapReportPrintRow(row as unknown as Record<string, unknown>, fields, {
    periodBusinessSummary: () => buildPeriodBusinessSummary(row, columnMeta.value),
    beginCostPackageUnitPrice: () =>
      formatPurchaseMoneyDisplay(row.beginCostPackageUnitPrice) || '',
    beginQuantity: () => formatMinUnitQuantity(row.beginQuantity, row.minUnitCodeLabel),
    beginCostAmount: () => fmtReportPurchaseMoney(row.beginCostAmount),
    endCostPackageUnitPrice: () => formatPurchaseMoneyDisplay(row.endCostPackageUnitPrice) || '',
    endQuantity: () => formatMinUnitQuantity(row.endQuantity, row.minUnitCodeLabel),
    endCostAmount: () => fmtReportPurchaseMoney(row.endCostAmount),
    priceAdjustmentCostAmount: () => {
      if (!rowHasNonZeroPriceAdjustment(row)) {
        return '';
      }
      return fmtReportPurchaseMoney(row.priceAdjustmentCostAmount);
    },
    computedEndQuantity: () => formatMinUnitQuantity(row.computedEndQuantity, row.minUnitCodeLabel),
    computedEndCostAmount: () => fmtReportPurchaseMoney(row.computedEndCostAmount),
    quantityDiff: () => formatMinUnitQuantity(row.quantityDiff, row.minUnitCodeLabel),
    costAmountDiff: () => fmtReportPurchaseMoney(row.costAmountDiff),
    isBalance: () => (row.isBalance ? '是' : '否'),
  });
}

const { printFieldDialogRef, printSubmitting, handleOpenPrintFields, handlePrintCurrentPage } =
  useReportListPrint({
    getReportCode: () => 'report-period-balance-detail',
    getColumnCatalog: () => printColumnCatalog.value,
    getReportTitle: () => PAGE_TITLE,
    getPrintRows: () => tableData.value.map((row) => mapPeriodBalancePrintRow(row)),
    fetchAllPrintRows: async () => {
      const collected = await collectPagedReportRows({
        fetchPage: async (pageNo, pageSize) => {
          const page = await pagePeriodBalanceDetailApi({
            ...buildPageRequest(),
            pageNo,
            pageSize,
          });
          return { rows: page.rows ?? [], total: Number(page.total ?? 0) };
        },
      });
      return {
        rows: collected.rows.map((row) => mapPeriodBalancePrintRow(row)),
        truncated: collected.truncated,
      };
    },
    hasPrintableData: () => total.value > 0 || tableData.value.length > 0,
  });

const tableLoading = computed(
  () => initLoading.value || pageLoading.value || exportLoading.value || printSubmitting.value,
);

/** 与查询区「仅显示有业务」一致：是则展开子表也过滤全 0 业务行。 */
const hideFlowZeroRows = computed(() => {
  const raw = queryParams.isBus;
  return !(raw === false || raw === 'false');
});

/** 下拉选项转为 ElSelect 可用结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 主表行：是否存在非零调价进价盈亏（决定是否渲染「调价」列）。 */
function rowHasNonZeroPriceAdjustment(
  row: PeriodBalanceDetailListRowDto | null | undefined,
): boolean {
  const raw = row?.priceAdjustmentCostAmount;
  if (raw === null || raw === undefined || raw === '') {
    return false;
  }
  const num = Number(raw);
  return Number.isFinite(num) && num !== 0;
}

/** 主表列：品种信息 + 期初/期末/推算/盈亏/账平（仅进价侧；数量=最小单位）。 */
const mainTableColumns = computed<SettlementTableColumn[]>(() => {
  const productNameLabel =
    resolvedItemTable.value === ITEM_TABLE_MEDICATION ? '药品名称' : '项目名称';
  /** 按库存明细「库存(最小)」口径格式化数量单元格。 */
  const formatQty = (
    row: PeriodBalanceDetailListRowDto,
    field: keyof PeriodBalanceDetailListRowDto,
  ) => formatMinUnitQuantity(row[field], row.minUnitCodeLabel);
  /** 当前页是否存在非零调价进价盈亏（有则展示「调价」列）。 */
  const showPriceAdjustmentColumn = tableData.value.some((row) =>
    rowHasNonZeroPriceAdjustment(row),
  );
  const columns: SettlementTableColumn[] = [
    {
      prop: 'itemNo',
      label: '编码',
      minWidth: 80,
      fixed: 'left',
      align: 'center',
      headerAlign: 'center',
    },
    {
      prop: 'productName',
      label: productNameLabel,
      minWidth: 110,
      fixed: 'left',
      align: 'left',
      headerAlign: 'center',
    },
    {
      prop: 'itemName',
      label: '商品名',
      minWidth: 96,
      fixed: 'left',
      align: 'left',
      headerAlign: 'center',
    },
    {
      prop: 'specText',
      label: '规格',
      minWidth: 80,
      fixed: 'left',
      align: 'center',
      headerAlign: 'center',
    },
    {
      prop: 'periodBusinessSummary',
      label: '期间业务',
      minWidth: 160,
      align: 'center',
      headerAlign: 'center',
      formatter: (row) => buildPeriodBusinessSummary(row, columnMeta.value),
    },
    {
      prop: 'beginCostPackageUnitPrice',
      label: '期初单价',
      minWidth: 88,
      align: 'right',
      headerAlign: 'right',
      className: REPORT_TABLE_COL_GROUP_START,
      labelClassName: REPORT_TABLE_COL_GROUP_START,
      formatter: (row) => formatPurchaseMoneyDisplay(row.beginCostPackageUnitPrice) || '—',
    },
    {
      prop: 'beginQuantity',
      label: '期初数量',
      minWidth: 120,
      align: 'right',
      headerAlign: 'right',
      formatter: (row) => formatQty(row, 'beginQuantity'),
    },
    {
      prop: 'beginCostAmount',
      label: '期初总额',
      minWidth: 88,
      align: 'right',
      headerAlign: 'right',
      className: REPORT_TABLE_AMOUNT_CELL,
      formatter: (row) => fmtReportPurchaseMoney(row.beginCostAmount) || '—',
    },
    {
      prop: 'endCostPackageUnitPrice',
      label: '期末单价',
      minWidth: 88,
      align: 'right',
      headerAlign: 'right',
      className: PERIOD_END_SPLIT_CLASS,
      labelClassName: PERIOD_END_SPLIT_CLASS,
      formatter: (row) => formatPurchaseMoneyDisplay(row.endCostPackageUnitPrice) || '—',
    },
    {
      prop: 'endQuantity',
      label: '期末数量',
      minWidth: 120,
      align: 'right',
      headerAlign: 'right',
      formatter: (row) => formatQty(row, 'endQuantity'),
    },
    {
      prop: 'endCostAmount',
      label: '期末总额',
      minWidth: 88,
      align: 'right',
      headerAlign: 'right',
      className: REPORT_TABLE_AMOUNT_CELL,
      formatter: (row) => fmtReportPurchaseMoney(row.endCostAmount) || '—',
    },
  ];
  if (showPriceAdjustmentColumn) {
    columns.push({
      prop: 'priceAdjustmentCostAmount',
      label: '调价',
      minWidth: 88,
      align: 'right',
      headerAlign: 'right',
      className: REPORT_TABLE_AMOUNT_CELL,
      formatter: (row) => {
        // 无调价或为零：占位，避免与「有调价」行混淆
        if (!rowHasNonZeroPriceAdjustment(row)) {
          return '—';
        }
        return fmtReportPurchaseMoney(row.priceAdjustmentCostAmount) || '—';
      },
    });
  }
  columns.push(
    {
      prop: 'computedEndQuantity',
      label: '推算数量',
      minWidth: 120,
      align: 'right',
      headerAlign: 'right',
      className: REPORT_TABLE_COL_GROUP_START,
      labelClassName: REPORT_TABLE_COL_GROUP_START,
      formatter: (row) => formatQty(row, 'computedEndQuantity'),
    },
    {
      prop: 'computedEndCostAmount',
      label: '推算总额',
      minWidth: 88,
      align: 'right',
      headerAlign: 'right',
      className: REPORT_TABLE_AMOUNT_CELL,
      formatter: (row) => fmtReportPurchaseMoney(row.computedEndCostAmount) || '—',
    },
    {
      prop: 'quantityDiff',
      label: '盈亏数',
      minWidth: 120,
      align: 'right',
      headerAlign: 'right',
      className: REPORT_TABLE_COL_GROUP_START,
      labelClassName: REPORT_TABLE_COL_GROUP_START,
      formatter: (row) => formatMinUnitQuantity(row.quantityDiff, row.minUnitCodeLabel),
    },
    {
      prop: 'costAmountDiff',
      label: '盈亏差',
      minWidth: 88,
      align: 'right',
      headerAlign: 'right',
      className: REPORT_TABLE_AMOUNT_CELL,
      formatter: (row) => fmtReportPurchaseMoney(row.costAmountDiff) || '—',
    },
    {
      prop: 'isBalance',
      label: '是否账平',
      minWidth: 76,
      fixed: 'right',
      align: 'center',
      headerAlign: 'center',
      formatter: (row) => (row.isBalance ? '是' : '否'),
    },
  );
  return columns;
});

/** 渲染主表单元格文本。 */
function formatCell(row: PeriodBalanceDetailListRowDto, column: SettlementTableColumn) {
  if (column.formatter) {
    return column.formatter(row);
  }
  const value = row[column.prop as keyof PeriodBalanceDetailListRowDto];
  return value === null || value === undefined || value === '' ? '—' : String(value);
}

const { run: runInit, loading: initLoading } = useRequest(loadPeriodBalanceDetailFormInitApi, {
  manual: true,
  onSuccess: (data) => {
    warehouseOptions.value = optionDtosToSelectItems(data?.warehouseOptions);
    backupBusNoOptions.value = backupBusNoOptionsToSelectItems(data?.backupBusNoOptions);
  },
});

const { run: runPage, loading: pageLoading } = useRequest(pagePeriodBalanceDetailApi, {
  manual: true,
  onSuccess: (data) => {
    tableData.value = data?.rows ?? [];
    total.value = Number(data?.total ?? 0);
    columnMeta.value = data?.columns ?? [];
    if (data?.itemTable === ITEM_TABLE_DEVICE || data?.itemTable === ITEM_TABLE_MEDICATION) {
      resolvedItemTable.value = data.itemTable;
    }
  },
});

const { run: runExport, loading: exportLoading } = useRequest(exportPeriodBalanceDetailApi, {
  manual: true,
});

/** 组装分页/导出请求体。 */
function buildPageRequest(): PagePeriodBalanceDetailRequest {
  const isBalanceRaw = queryParams.isBalance;
  let isBalance: boolean | null | undefined;
  if (isBalanceRaw === true || isBalanceRaw === 'true') {
    isBalance = true;
  } else if (isBalanceRaw === false || isBalanceRaw === 'false') {
    isBalance = false;
  }
  const isBusRaw = queryParams.isBus;
  const isBus = !(isBusRaw === false || isBusRaw === 'false');
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    locationId: preserveApiIdForRequest(queryParams.locationId ?? '') ?? '',
    startBackupBusNo: queryParams.startBackupBusNo?.trim() ?? '',
    endBackupBusNo: queryParams.endBackupBusNo?.trim() || undefined,
    keyword: queryParams.keyword?.trim() || undefined,
    isBus,
    isBalance,
  };
}

/** 拉取库房期初期末明细分页。 */
async function getList(options?: { quietMissingRequired?: boolean }) {
  if (!queryParams.locationId) {
    tableData.value = [];
    total.value = 0;
    if (!options?.quietMissingRequired) {
      ElMessage.warning('请选择库房');
    }
    return;
  }
  if (!queryParams.startBackupBusNo?.trim()) {
    tableData.value = [];
    total.value = 0;
    if (!options?.quietMissingRequired) {
      ElMessage.warning('请选择期初日终备份');
    }
    return;
  }
  await runPage(buildPageRequest());
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 查询区变更：缺必填时静默清空，避免清空库房反复弹 toast。 */
function handleFilterChange() {
  queryParams.pageNo = 1;
  void getList({ quietMissingRequired: true });
}

/** 查询按钮：回到第一页，缺必填时提示。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 重置筛选并回到第一页（保留库房/期初，二者为必填）。 */
function handleReset() {
  queryParams.keyword = undefined;
  queryParams.endBackupBusNo = undefined;
  queryParams.isBus = 'true';
  queryParams.isBalance = undefined;
  queryParams.pageNo = 1;
  void getList();
}

/** 导出 Excel（固定列 + 当前动态期间业务列）。 */
async function handleExportExcel() {
  const pageReq = buildPageRequest();
  // 固定打印列 + 服务端下发的期间业务动态列
  const dynamicCols = (columnMeta.value ?? []).map((col) => ({
    field: String(col.columnCode ?? ''),
    title: String(col.columnTitle ?? col.columnCode ?? ''),
  }));
  const exportColumns = buildExcelExportColumns([...printColumnCatalog.value, ...dynamicCols]);
  const data = await runExport({
    locationId: pageReq.locationId,
    startBackupBusNo: pageReq.startBackupBusNo,
    endBackupBusNo: pageReq.endBackupBusNo,
    keyword: pageReq.keyword,
    isBus: pageReq.isBus,
    isBalance: pageReq.isBalance,
    exportColumns,
  });
  if (!data?.fileBytes?.trim()) {
    ElMessage.warning('导出内容为空');
    return;
  }
  downloadBinaryExportFile(data, data.fileName ?? '库房期初期末明细.xlsx');
  if (data.truncated) {
    ElMessage.warning('导出数据已触达行数上限');
  } else {
    ElMessage.success(`导出成功，共 ${data.exportedRowCount ?? 0} 行`);
  }
}

/** 切换每页条数。 */
function handlePageSizeChange(size: number) {
  queryParams.pageSize = size;
  queryParams.pageNo = 1;
  void getList();
}

/** 切换页码。 */
function handleCurrentPageChange(page: number) {
  queryParams.pageNo = page;
  void getList();
}

onMounted(async () => {
  await runInit();
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar
          :model="queryParams as Record<string, unknown>"
          class="period-balance-detail-query-bar"
          label-width="112px"
        >
          <template #fields>
            <ElFormItem label="库房">
              <ElSelect
                v-model="queryParams.locationId"
                clearable
                filterable
                placeholder="请选择库房"
                :class="REPORT_QUERY_CONTROL"
                @change="handleFilterChange"
              >
                <ElOption
                  v-for="item in toSelectOptions(warehouseOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="期初日终备份">
              <ElSelect
                v-model="queryParams.startBackupBusNo"
                clearable
                filterable
                placeholder="必选，=前一日日终库存"
                class="period-balance-detail-query-control--backup"
                @change="handleFilterChange"
              >
                <ElOption
                  v-for="item in toSelectOptions(backupBusNoOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="期末日终备份">
              <ElSelect
                v-model="queryParams.endBackupBusNo"
                clearable
                filterable
                placeholder="可选，空=实时库存"
                class="period-balance-detail-query-control--backup"
                @change="handleFilterChange"
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
                placeholder="编码/名称"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleFilterChange"
                @clear="handleFilterChange"
              />
            </ElFormItem>
            <ElFormItem label="仅显示有业务">
              <ElSelect
                v-model="queryParams.isBus"
                :class="REPORT_QUERY_CONTROL"
                @change="handleFilterChange"
              >
                <ElOption label="是（推荐）" value="true" />
                <ElOption label="否（全目录）" value="false" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="账平筛选">
              <ElSelect
                v-model="queryParams.isBalance"
                clearable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleFilterChange"
              >
                <ElOption label="全部" value="" />
                <ElOption label="仅账平" value="true" />
                <ElOption label="仅不平" value="false" />
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
              plain
              :icon="Setting"
              :disabled="printSubmitting"
              @click="handleOpenPrintFields"
            >
              打印设置
            </ElButton>
            <ElButton
              type="primary"
              plain
              :icon="Printer"
              :loading="printSubmitting"
              @click="handlePrintCurrentPage"
            >
              打印
            </ElButton>
            <ElButton
              type="primary"
              plain
              :icon="Download"
              :loading="exportLoading"
              @click="handleExportExcel"
            >
              导出 Excel
            </ElButton>
          </template>
        </ReportListToolbar>
        <div class="period-balance-detail-formula">
          <div class="period-balance-detail-formula__title">账平核对公式</div>
          <p
            v-for="(line, index) in BALANCE_FORMULA_LINES"
            :key="index"
            class="period-balance-detail-formula__line"
          >
            {{ line }}
          </p>
        </div>
      </template>

      <template #table>
        <ReportTableScroll ref="tableScrollRef">
          <template #default="{ tableHeight }">
            <ReportElTable
              class="period-balance-detail-table"
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              row-key="itemId"
            >
              <ElTableColumn type="expand" width="48" align="center">
                <template #default="{ row }">
                  <PeriodBalanceDetailFlowExpandPanel
                    :row="row as PeriodBalanceDetailListRowDto"
                    :columns="columnMeta"
                    :hide-zero-rows="hideFlowZeroRows"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-for="column in mainTableColumns"
                :key="column.prop"
                :prop="column.prop"
                :label="column.label"
                :min-width="column.minWidth"
                :fixed="column.fixed"
                :align="column.align"
                :header-align="column.headerAlign ?? column.align ?? 'center'"
                :class-name="buildColumnClassName(column)"
                :label-class-name="buildLabelClassName(column)"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatCell(row as PeriodBalanceDetailListRowDto, column) }}
                </template>
              </ElTableColumn>
            </ReportElTable>
          </template>
        </ReportTableScroll>
      </template>

      <template #footer>
        <ReportPageFooter
          :current-page="queryParams.pageNo"
          :page-size="queryParams.pageSize"
          :total="total"
          @size-change="handlePageSizeChange"
          @current-change="handleCurrentPageChange"
        />
      </template>
    </ReportListPageLayout>

    <ReportPrintFieldDialog
      ref="printFieldDialogRef"
      report-code="report-period-balance-detail"
      :column-catalog="printColumnCatalog"
      show-print-scope
    />
  </Page>
</template>

<style scoped lang="scss">
.period-balance-detail-query-bar {
  :deep(.el-form-item__label) {
    white-space: nowrap;
  }
}

.period-balance-detail-formula {
  flex-shrink: 0;
  padding: 6px 10px;
  margin-bottom: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  background: var(--el-color-info-light-9);
  border: 1px solid var(--el-color-info-light-5);
  border-radius: 4px;
}

.period-balance-detail-formula__title {
  margin-bottom: 4px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.period-balance-detail-formula__line {
  margin: 0;
}

.period-balance-detail-table {
  :deep(th.period-balance-detail-header-left .cell) {
    text-align: left;
  }

  :deep(th.period-balance-detail-header-center .cell) {
    text-align: center;
  }

  :deep(th.period-balance-detail-header-right .cell) {
    text-align: right;
  }

  :deep(th.period-balance-detail-col--end-split),
  :deep(td.period-balance-detail-col--end-split) {
    border-left: 2px solid var(--el-color-primary-light-5) !important;
  }

  :deep(th.period-balance-detail-col--end-split) {
    background: var(--el-color-primary-light-9) !important;
  }

  :deep(.el-table__expanded-cell) {
    padding: 0 !important;
  }
}
</style>
