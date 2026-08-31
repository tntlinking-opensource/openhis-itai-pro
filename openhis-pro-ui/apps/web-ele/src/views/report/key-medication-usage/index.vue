<!--
  重点药品使用统计：就诊类型 × 重点药类型矩阵汇总（人次 + 金额）；可导出。
-->
<script setup lang="ts">
import type { KeyMedicationUsageSummaryRowDto } from '#/api/report/key-medication-usage';
import type { ApiOptionItem } from '#/types/api-response';
import type { ReportPrintColumnDef } from '#/utils/print/print-report-list';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Download, Printer, Refresh, Search, Setting } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElFormItem,
  ElMessage,
  ElOption,
  ElSelect,
  ElTableColumn,
} from 'element-plus';

import {
  exportKeyMedicationUsageReportApi,
  loadKeyMedicationUsageReportPageInitApi,
  summarizeKeyMedicationUsageReportApi,
} from '#/api/report/key-medication-usage';
import { useRequest } from '#/hooks/use-request';
import { buildExcelExportColumns } from '#/utils/export/build-excel-export-columns';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import {
  mapReportPrintRow,
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATERANGE,
  REPORT_TABLE_AMOUNT_CELL,
  REPORT_TABLE_NUM_CELL,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPageFooter,
  ReportPrintFieldDialog,
  ReportQueryBar,
  ReportTableScroll,
  reportPrintFieldsOf,
  useReportListPrint,
} from '#/views/report/shared';
import {
  buildReportDateRangeShortcuts,
  normalizeReportDateRangeYmd,
} from '#/views/report/shared/report-date-range-shortcuts';
import { optionDtosToSelectItems } from '#/views/report/shared/report-format';

defineOptions({ name: 'KeyMedicationUsageReport' });

interface QueryState {
  dateFrom: string;
  dateTo: string;
  encounterClassCodes: string[];
  keyDrugTypeCodes: string[];
}

const PAGE_TITLE = '重点药品使用统计';

const encounterClassOptions = ref<ApiOptionItem[]>([]);
const keyDrugTypeOptions = ref<ApiOptionItem[]>([]);
const summaryRows = ref<KeyMedicationUsageSummaryRowDto[]>([]);
const totalEncounterNetCount = ref(0);
const totalSaleAmount = ref<number | string>(0);

const queryParams = reactive<QueryState>({
  dateFrom: '',
  dateTo: '',
  encounterClassCodes: [],
  keyDrugTypeCodes: [],
});

const dateRangeShortcuts = buildReportDateRangeShortcuts();

/** 业务日期与 daterange 双向绑定。 */
const businessDateRange = computed({
  get() {
    const start = normalizeReportDateRangeYmd(queryParams.dateFrom);
    const end = normalizeReportDateRangeYmd(queryParams.dateTo);
    if (start && end) {
      return [start, end] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.dateFrom = value?.[0] ?? '';
    queryParams.dateTo = value?.[1] ?? '';
  },
});

/** ReportQueryBar 需要 Record 形态的 model。 */
const queryBarModel = computed(() => queryParams as Record<string, unknown>);

/** 页脚：行数 / 人次合计 / 金额合计。 */
const footerSummaryItems = computed(() => [
  { label: '汇总行数', value: summaryRows.value.length },
  { label: '人次合计', value: totalEncounterNetCount.value },
  {
    label: '金额合计',
    value: formatMoneyCell(totalSaleAmount.value),
    primary: true,
    prefix: '¥',
  },
]);

const { run: runInit, loading: initLoading } = useRequest(loadKeyMedicationUsageReportPageInitApi, {
  manual: true,
  onSuccess: (data) => {
    encounterClassOptions.value = optionDtosToSelectItems(data?.encounterClassOptions);
    keyDrugTypeOptions.value = optionDtosToSelectItems(data?.keyDrugTypeOptions);
    queryParams.dateFrom =
      normalizeReportDateRangeYmd(data?.defaultDateFrom) || queryParams.dateFrom;
    queryParams.dateTo = normalizeReportDateRangeYmd(data?.defaultDateTo) || queryParams.dateTo;
  },
});

const { run: runSummarize, loading: summarizeLoading } = useRequest(
  summarizeKeyMedicationUsageReportApi,
  {
    manual: true,
    onSuccess: (data) => {
      summaryRows.value = data?.rows ?? [];
      totalEncounterNetCount.value = Number(data?.totalEncounterNetCount ?? 0);
      totalSaleAmount.value = data?.totalSaleAmount ?? 0;
    },
  },
);

const { run: runExport, loading: exportLoading } = useRequest(exportKeyMedicationUsageReportApi, {
  manual: true,
  onSuccess: (data) => {
    downloadBinaryExportFile(data, '重点药品使用统计.xlsx');
    const rowCount = data.rowCount ?? data.exportedRowCount ?? 0;
    ElMessage.success(`导出成功，共 ${rowCount} 行`);
  },
});

/** 可打印列目录。 */
const printColumnCatalog = computed<ReportPrintColumnDef[]>(() => [
  { field: 'encounterClassDisplay', title: '就诊类型', align: 'center' },
  { field: 'keyDrugTypeDisplay', title: '药品类型', align: 'center' },
  { field: 'encounterNetCount', title: '人次', align: 'right' },
  { field: 'saleAmountTotal', title: '总金额', align: 'right' },
]);

/**
 * 将表格行映射为打印单元格文案。
 */
function mapCurrentPrintRows(): Record<string, string>[] {
  const fields = reportPrintFieldsOf(printColumnCatalog.value);
  return summaryRows.value.map((row) =>
    mapReportPrintRow(row as unknown as Record<string, unknown>, fields, {
      encounterNetCount: (value) => formatCountCell(value),
      saleAmountTotal: (value) => formatMoneyCell(value),
    }),
  );
}

const { printFieldDialogRef, printSubmitting, handleOpenPrintFields, handlePrintCurrentPage } =
  useReportListPrint({
    getReportCode: () => 'report-key-medication-usage',
    getColumnCatalog: () => printColumnCatalog.value,
    getReportTitle: () => PAGE_TITLE,
    getPrintRows: () => mapCurrentPrintRows(),
  });

const tableLoading = computed(
  () => initLoading.value || summarizeLoading.value || printSubmitting.value,
);

/**
 * 组装当前查询请求体（空多选不传，表示全部）。
 */
function buildQueryBody() {
  const body: {
    dateFrom: string;
    dateTo: string;
    encounterClassCodes?: string[];
    keyDrugTypeCodes?: string[];
  } = {
    dateFrom: queryParams.dateFrom,
    dateTo: queryParams.dateTo,
  };
  // 未选就诊类型 = 后端按全部 AMB+IMP
  if (queryParams.encounterClassCodes.length > 0) {
    body.encounterClassCodes = [...queryParams.encounterClassCodes];
  }
  // 未选药品类型 = 后端按全部三类重点药
  if (queryParams.keyDrugTypeCodes.length > 0) {
    body.keyDrugTypeCodes = [...queryParams.keyDrugTypeCodes];
  }
  return body;
}

/**
 * 校验日期后触发汇总。
 */
function handleQuery() {
  if (!queryParams.dateFrom || !queryParams.dateTo) {
    ElMessage.warning('请选择有效查询日期');
    return;
  }
  void runSummarize(buildQueryBody());
}

/**
 * 重置为默认当日与空筛选并查询。
 */
async function handleReset() {
  queryParams.encounterClassCodes = [];
  queryParams.keyDrugTypeCodes = [];
  await runInit();
  handleQuery();
}

/**
 * 导出当前筛选矩阵（列清单与页面可见列一致）。
 */
function handleExport() {
  if (!queryParams.dateFrom || !queryParams.dateTo) {
    ElMessage.warning('请选择有效查询日期');
    return;
  }
  const exportColumns = buildExcelExportColumns(printColumnCatalog.value);
  void runExport({
    ...buildQueryBody(),
    exportColumns,
  });
}

/**
 * 金额单元格：空占位。
 */
function formatMoneyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return formatMoneyDisplay(value);
}

/**
 * 数量单元格。
 */
function formatCountCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return String(value);
}

/**
 * 汇总行稳定主键（就诊类型 × 药品类型）。
 */
function resolveSummaryRowKey(row: KeyMedicationUsageSummaryRowDto) {
  return `${row.encounterClassCode ?? ''}_${row.keyDrugTypeCode ?? ''}`;
}

onMounted(async () => {
  await runInit();
  handleQuery();
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryBarModel">
          <template #fields>
            <ElFormItem label="日期">
              <ElDatePicker
                v-model="businessDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                :clearable="false"
                :shortcuts="dateRangeShortcuts"
                :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATERANGE]"
                @change="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="就诊类型">
              <ElSelect
                v-model="queryParams.encounterClassCodes"
                multiple
                clearable
                collapse-tags
                collapse-tags-tooltip
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in encounterClassOptions"
                  :key="String(item.value)"
                  :label="String(item.label ?? '')"
                  :value="String(item.value ?? '')"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="药品类型">
              <ElSelect
                v-model="queryParams.keyDrugTypeCodes"
                multiple
                clearable
                collapse-tags
                collapse-tags-tooltip
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in keyDrugTypeOptions"
                  :key="String(item.value)"
                  :label="String(item.label ?? '')"
                  :value="String(item.value ?? '')"
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
        <ReportListToolbar :title="PAGE_TITLE" :total="summaryRows.length">
          <template #actions>
            <ElButton
              :icon="Download"
              :loading="exportLoading"
              type="primary"
              @click="handleExport"
            >
              导出
            </ElButton>
            <ElButton :icon="Setting" @click="handleOpenPrintFields">打印字段</ElButton>
            <ElButton :icon="Printer" :loading="printSubmitting" @click="handlePrintCurrentPage">
              打印
            </ElButton>
          </template>
        </ReportListToolbar>
      </template>

      <template #table>
        <ReportTableScroll>
          <ReportElTable
            v-loading="tableLoading"
            :data="summaryRows"
            :row-key="resolveSummaryRowKey"
          >
            <ElTableColumn
              prop="encounterClassDisplay"
              label="就诊类型"
              align="center"
              header-align="center"
              min-width="100"
            />
            <ElTableColumn
              prop="keyDrugTypeDisplay"
              label="药品类型"
              align="center"
              header-align="center"
              min-width="160"
            />
            <ElTableColumn
              prop="encounterNetCount"
              label="人次"
              align="right"
              header-align="right"
              min-width="100"
              :class-name="REPORT_TABLE_NUM_CELL"
            >
              <template #default="{ row }">
                {{ formatCountCell(row.encounterNetCount) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="saleAmountTotal"
              label="总金额"
              align="right"
              header-align="right"
              min-width="120"
              :class-name="REPORT_TABLE_AMOUNT_CELL"
            >
              <template #default="{ row }">
                {{ formatMoneyCell(row.saleAmountTotal) }}
              </template>
            </ElTableColumn>
          </ReportElTable>
        </ReportTableScroll>
      </template>

      <template #footer>
        <ReportPageFooter :items="footerSummaryItems" />
      </template>
    </ReportListPageLayout>

    <ReportPrintFieldDialog ref="printFieldDialogRef" />
  </Page>
</template>
