<!--
  库房日结：选库房即可（药/耗由服务端按现库/快照 item_table 解析）；主表 + 展开流水。
-->
<script setup lang="ts">
import type {
  DailySettlementColumnDto,
  DailySettlementItemTable,
  DailySettlementListRowDto,
  PageDailySettlementRequest,
} from '#/api/report/daily-settlement';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Download, Refresh, Search } from '@element-plus/icons-vue';
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
  exportDailySettlementApi,
  ITEM_TABLE_DEVICE,
  ITEM_TABLE_MEDICATION,
  loadDailySettlementFormInitApi,
  pageDailySettlementApi,
} from '#/api/report/daily-settlement';
import { useRequest } from '#/hooks/use-request';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
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
  ReportQueryBar,
  ReportTableScroll,
} from '#/views/report/shared';
import {
  fmtReportNumber,
  backupBusNoOptionsToSelectItems,
  optionDtosToSelectItems,
} from '#/views/report/shared/report-format';

import DailySettlementFlowExpandPanel from './daily-settlement-flow-expand-panel.vue';
import { formatMinUnitQuantity } from './settlement-flow-rows';

defineOptions({ name: 'DailySettlementReportPage' });

interface SettlementTableColumn {
  prop: string;
  label: string;
  minWidth: number;
  fixed?: 'left' | 'right';
  align?: 'center' | 'left' | 'right';
  headerAlign?: 'center' | 'left' | 'right';
  className?: string;
  labelClassName?: string;
  formatter?: (row: DailySettlementListRowDto) => string;
}

/** 表头对齐 class（覆盖报表表默认居中）。 */
const HEADER_ALIGN_CLASS = {
  left: 'daily-settlement-header-left',
  center: 'daily-settlement-header-center',
  right: 'daily-settlement-header-right',
} as const;

/** 期初/期末区块分隔：期末首列浅蓝竖线。 */
const PERIOD_END_SPLIT_CLASS = 'daily-settlement-col--end-split';

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

const PAGE_TITLE = '库房日结';

/** 页内展示的账平核对公式（与后端 BR-INV-SET-011/012/013 一致，仅进价侧）。 */
const BALANCE_FORMULA_LINES = [
  '数量口径：一律以拆零最小单位计（与库存明细「库存(最小)」一致）',
  '期初/期末盘点单据：单号日 D 为零点备份（建议每日 00:05），数量为 (D−1) 日终库存；期间含期初盘点日全天',
  '推算期末数量 = 期初数量 − 发药 + 退药 + 入库/调入/退库/退领 + 盘点盈 − 出库/调出/退供应商/领用 − 盘点亏 + 损益调整',
  '盈亏数 = 实际期末数量 − 推算期末数量',
  '推算期末进价总额 = 期初进价总额 ± 期间各业务进价额（符号与数量侧一致）',
  '盈亏差 = 实际期末进价总额 − 推算期末进价总额',
  '是否账平：盈亏数 = 0 且 盈亏差 = 0',
  '展开行：随「隐藏全零行」联动；选「否」时列出全部适用业务（含 0）',
];

const tableData = ref<DailySettlementListRowDto[]>([]);
const total = ref(0);
const columnMeta = ref<DailySettlementColumnDto[]>([]);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const warehouseOptions = ref<ApiOptionItem[]>([]);
const backupBusNoOptions = ref<ApiOptionItem[]>([]);
/** 最近一次查询服务端解析的物品主表（用于列头文案）。 */
const resolvedItemTable = ref<DailySettlementItemTable>(ITEM_TABLE_MEDICATION);

const queryParams = reactive<QueryState>({
  pageNo: 1,
  pageSize: 10,
  isBus: 'true',
});

const tableLoading = computed(() => initLoading.value || pageLoading.value || exportLoading.value);

/** 与查询区「隐藏全零行」一致：是则展开子表也过滤全 0 业务行。 */
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

/** 主表列：品种信息 + 期初/期末/推算/盈亏/账平（仅进价侧；数量=最小单位）。 */
const mainTableColumns = computed<SettlementTableColumn[]>(() => {
  const productNameLabel =
    resolvedItemTable.value === ITEM_TABLE_MEDICATION ? '药品名称' : '项目名称';
  const formatQty = (row: DailySettlementListRowDto, field: keyof DailySettlementListRowDto) =>
    formatMinUnitQuantity(row[field], row.minUnitCodeLabel);
  return [
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
      prop: 'beginCostPackageUnitPrice',
      label: '期初单价',
      minWidth: 88,
      align: 'right',
      headerAlign: 'right',
      className: REPORT_TABLE_COL_GROUP_START,
      labelClassName: REPORT_TABLE_COL_GROUP_START,
      formatter: (row) => fmtReportNumber(row.beginCostPackageUnitPrice, 2),
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
      formatter: (row) => fmtReportNumber(row.beginCostAmount, 2),
    },
    {
      prop: 'endCostPackageUnitPrice',
      label: '期末单价',
      minWidth: 88,
      align: 'right',
      headerAlign: 'right',
      className: PERIOD_END_SPLIT_CLASS,
      labelClassName: PERIOD_END_SPLIT_CLASS,
      formatter: (row) => fmtReportNumber(row.endCostPackageUnitPrice, 2),
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
      formatter: (row) => fmtReportNumber(row.endCostAmount, 2),
    },
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
      formatter: (row) => fmtReportNumber(row.computedEndCostAmount, 2),
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
      formatter: (row) => fmtReportNumber(row.costAmountDiff, 2),
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
  ];
});

/** 渲染主表单元格文本。 */
function formatCell(row: DailySettlementListRowDto, column: SettlementTableColumn) {
  if (column.formatter) {
    return column.formatter(row);
  }
  const value = row[column.prop as keyof DailySettlementListRowDto];
  return value === null || value === undefined || value === '' ? '—' : String(value);
}

const { run: runInit, loading: initLoading } = useRequest(loadDailySettlementFormInitApi, {
  manual: true,
  onSuccess: (data) => {
    warehouseOptions.value = optionDtosToSelectItems(data?.warehouseOptions);
    backupBusNoOptions.value = backupBusNoOptionsToSelectItems(data?.backupBusNoOptions);
  },
});

const { run: runPage, loading: pageLoading } = useRequest(pageDailySettlementApi, {
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

const { run: runExport, loading: exportLoading } = useRequest(exportDailySettlementApi, {
  manual: true,
});

/** 组装分页/导出请求体。 */
function buildPageRequest(): PageDailySettlementRequest {
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

/** 拉取日结分页。 */
async function getList() {
  if (!queryParams.locationId) {
    ElMessage.warning('请选择库房');
    return;
  }
  if (!queryParams.startBackupBusNo?.trim()) {
    ElMessage.warning('请选择期初盘点单据');
    return;
  }
  await runPage(buildPageRequest());
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 查询回到第一页。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 重置筛选并回到第一页。 */
function handleReset() {
  queryParams.keyword = undefined;
  queryParams.endBackupBusNo = undefined;
  queryParams.isBus = 'true';
  queryParams.isBalance = undefined;
  queryParams.pageNo = 1;
  void getList();
}

/** 导出 Excel。 */
async function handleExportExcel() {
  const pageReq = buildPageRequest();
  const data = await runExport({
    locationId: pageReq.locationId,
    startBackupBusNo: pageReq.startBackupBusNo,
    endBackupBusNo: pageReq.endBackupBusNo,
    keyword: pageReq.keyword,
    isBus: pageReq.isBus,
    isBalance: pageReq.isBalance,
  });
  if (!data?.fileBytes?.trim()) {
    ElMessage.warning('导出内容为空');
    return;
  }
  downloadBinaryExportFile(data, data.fileName ?? '库房日结.xlsx');
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
          class="daily-settlement-query-bar"
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
              >
                <ElOption
                  v-for="item in toSelectOptions(warehouseOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="期初盘点单据">
              <ElSelect
                v-model="queryParams.startBackupBusNo"
                clearable
                filterable
                placeholder="请选择期初"
                class="daily-settlement-query-control--backup"
              >
                <ElOption
                  v-for="item in toSelectOptions(backupBusNoOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="期末盘点单据">
              <ElSelect
                v-model="queryParams.endBackupBusNo"
                clearable
                filterable
                placeholder="请选择期末"
                class="daily-settlement-query-control--backup"
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
                @keyup.enter="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="隐藏全零行">
              <ElSelect v-model="queryParams.isBus" :class="REPORT_QUERY_CONTROL">
                <ElOption label="是" value="true" />
                <ElOption label="否" value="false" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="账平筛选">
              <ElSelect
                v-model="queryParams.isBalance"
                clearable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
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
        <div class="daily-settlement-formula">
          <div class="daily-settlement-formula__title">账平核对公式</div>
          <p
            v-for="(line, index) in BALANCE_FORMULA_LINES"
            :key="index"
            class="daily-settlement-formula__line"
          >
            {{ line }}
          </p>
        </div>
      </template>

      <template #table>
        <ReportTableScroll ref="tableScrollRef">
          <template #default="{ tableHeight }">
            <ReportElTable
              class="daily-settlement-table"
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              row-key="itemId"
            >
              <ElTableColumn type="expand" width="48" align="center">
                <template #default="{ row }">
                  <DailySettlementFlowExpandPanel
                    :row="row as DailySettlementListRowDto"
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
                  {{ formatCell(row as DailySettlementListRowDto, column) }}
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
  </Page>
</template>

<style scoped lang="scss">
.daily-settlement-query-bar {
  :deep(.el-form-item__label) {
    white-space: nowrap;
  }
}

.daily-settlement-formula {
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

.daily-settlement-formula__title {
  margin-bottom: 4px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.daily-settlement-formula__line {
  margin: 0;
}

.daily-settlement-table {
  :deep(th.daily-settlement-header-left .cell) {
    text-align: left;
  }

  :deep(th.daily-settlement-header-center .cell) {
    text-align: center;
  }

  :deep(th.daily-settlement-header-right .cell) {
    text-align: right;
  }

  :deep(th.daily-settlement-col--end-split),
  :deep(td.daily-settlement-col--end-split) {
    border-left: 2px solid var(--el-color-primary-light-5) !important;
  }

  :deep(th.daily-settlement-col--end-split) {
    background: var(--el-color-primary-light-9) !important;
  }

  :deep(.el-table__expanded-cell) {
    padding: 0 !important;
  }
}
</style>
