<!--
  重点药品使用统计：就诊类型 × 重点药类型矩阵（人次 + 金额 + 占比）；组内合计；可导出。
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

/** 组内合计行药品类型码（与后端 SUMMARY_KEY_DRUG_TYPE_TOTAL 对齐）。 */
const KEY_DRUG_TYPE_TOTAL = 'TOTAL';

interface QueryState {
  dateFrom: string;
  dateTo: string;
  encounterClassCodes: string[];
  keyDrugTypeCodes: string[];
  medicinePropertyCodes: string[];
}

const PAGE_TITLE = '重点药品使用统计';

const encounterClassOptions = ref<ApiOptionItem[]>([]);
const keyDrugTypeOptions = ref<ApiOptionItem[]>([]);
const medicinePropertyOptions = ref<ApiOptionItem[]>([]);
const summaryRows = ref<KeyMedicationUsageSummaryRowDto[]>([]);
const totalEncounterNetCount = ref(0);
const totalSaleAmount = ref<number | string>(0);

const queryParams = reactive<QueryState>({
  dateFrom: '',
  dateTo: '',
  encounterClassCodes: [],
  keyDrugTypeCodes: [],
  medicinePropertyCodes: [],
});

const dateRangeShortcuts = buildReportDateRangeShortcuts();

/** 查询序号：日期/筛选快速切换时丢弃过期汇总，避免乱序回写与重复报错。 */
let querySeq = 0;

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

/** 页脚：行数 / 人次合计 / 金额合计（合计仅为类型行，不含组内 TOTAL）。 */
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

/** 是否已从服务端写入默认日期窗（避免 init 晚到覆盖用户已选区间）。 */
let defaultDatesApplied = false;

const { run: runInit, loading: initLoading } = useRequest(loadKeyMedicationUsageReportPageInitApi, {
  manual: true,
  onSuccess: (data) => {
    encounterClassOptions.value = optionDtosToSelectItems(data?.encounterClassOptions);
    keyDrugTypeOptions.value = optionDtosToSelectItems(data?.keyDrugTypeOptions);
    medicinePropertyOptions.value = optionDtosToSelectItems(data?.medicinePropertyOptions);
    // 仅首次 init 写默认窗；用户改日期后不再被 init 回写
    if (!defaultDatesApplied) {
      const from = normalizeReportDateRangeYmd(data?.defaultDateFrom);
      const to = normalizeReportDateRangeYmd(data?.defaultDateTo);
      if (from) {
        queryParams.dateFrom = from;
      }
      if (to) {
        queryParams.dateTo = to;
      }
      defaultDatesApplied = true;
    }
  },
});

const { run: runSummarize, loading: summarizeLoading } = useRequest(
  summarizeKeyMedicationUsageReportApi,
  {
    manual: true,
    showErrorMessage: false,
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

/** 可打印列目录（含药品类型与人次/金额占比）。 */
const printColumnCatalog = computed<ReportPrintColumnDef[]>(() => [
  { field: 'encounterClassDisplay', title: '就诊类型', align: 'center' },
  { field: 'keyDrugTypeDisplay', title: '药品类型', align: 'center' },
  { field: 'encounterNetCount', title: '人次', align: 'right' },
  { field: 'saleAmountTotal', title: '总金额', align: 'right' },
  { field: 'usageRatioPercent', title: '人次占比', align: 'center' },
  { field: 'saleAmountRatioPercent', title: '金额占比', align: 'center' },
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
      usageRatioPercent: (value) => formatRatioCell(value),
      saleAmountRatioPercent: (value) => formatRatioCell(value),
    }),
  );
}

const { printFieldDialogRef, printSubmitting, handleOpenPrintFields, handlePrintCurrentPage } =
  useReportListPrint({
    getReportCode: () => 'report-key-medication-usage',
    getColumnCatalog: () => printColumnCatalog.value,
    getReportTitle: () => PAGE_TITLE,
    getPrintRows: () => mapCurrentPrintRows(),
    fetchAllPrintRows: async () => ({ rows: mapCurrentPrintRows(), truncated: false }),
    hasPrintableData: () => summaryRows.value.length > 0,
  });

const tableLoading = computed(
  () => initLoading.value || summarizeLoading.value || printSubmitting.value,
);

/**
 * 组装当前查询请求体（空多选/空状态不传，表示全部）。
 */
function buildQueryBody() {
  const body: {
    dateFrom: string;
    dateTo: string;
    encounterClassCodes?: string[];
    keyDrugTypeCodes?: string[];
    medicinePropertyCodes?: string[];
  } = {
    dateFrom: queryParams.dateFrom,
    dateTo: queryParams.dateTo,
  };
  // 未选就诊类型 = 后端按全部 AMB+IMP
  if (queryParams.encounterClassCodes.length > 0) {
    body.encounterClassCodes = [...queryParams.encounterClassCodes];
  }
  // 未选重点药类型 = 不按四旗收窄
  if (queryParams.keyDrugTypeCodes.length > 0) {
    body.keyDrugTypeCodes = [...queryParams.keyDrugTypeCodes];
  }
  // 未选药品性质 = 不按麻/精收窄；两框皆空 = 矩阵七行
  if (queryParams.medicinePropertyCodes.length > 0) {
    body.medicinePropertyCodes = [...queryParams.medicinePropertyCodes];
  }
  return body;
}

/**
 * 校验日期后触发汇总。
 */
async function handleQuery() {
  if (!queryParams.dateFrom || !queryParams.dateTo) {
    ElMessage.warning('请选择有效查询日期');
    return;
  }
  const seq = ++querySeq;
  try {
    const data = await runSummarize(buildQueryBody());
    if (seq !== querySeq) {
      return;
    }
    summaryRows.value = data?.rows ?? [];
    totalEncounterNetCount.value = Number(data?.totalEncounterNetCount ?? 0);
    totalSaleAmount.value = data?.totalSaleAmount ?? 0;
  } catch (error) {
    if (seq !== querySeq) {
      return;
    }
    const msg =
      (error as { message?: string; msg?: string }).message ||
      (error as { message?: string; msg?: string }).msg ||
      '查询失败，请稍后重试';
    ElMessage.error(msg);
  }
}

/**
 * 重置为默认近一个月与空筛选并查询。
 */
async function handleReset() {
  queryParams.encounterClassCodes = [];
  queryParams.keyDrugTypeCodes = [];
  queryParams.medicinePropertyCodes = [];
  defaultDatesApplied = false;
  await runInit();
  handleQuery();
}

/**
 * 导出当前筛选汇总（列清单与页面可见列一致）。
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
 * 占比单元格（百分比文案；空为 —）。
 */
function formatRatioCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return `${value}%`;
}

/**
 * 汇总行稳定主键（就诊类型 + 药品类型；合计行为 TOTAL）。
 */
function resolveSummaryRowKey(row: KeyMedicationUsageSummaryRowDto) {
  return `${row.encounterClassCode ?? ''}-${row.keyDrugTypeCode ?? ''}`;
}

/**
 * 行样式：合计行加粗底；就诊类型切换时加分区顶边。
 */
function resolveRowClassName({
  row,
  rowIndex,
}: {
  row: KeyMedicationUsageSummaryRowDto;
  rowIndex: number;
}): string {
  const classes: string[] = [];
  // 组内合计行：加粗/底色
  if (row.keyDrugTypeCode === KEY_DRUG_TYPE_TOTAL) {
    classes.push('is-total-row');
  }
  // 门诊块 → 住院块：首行加粗顶边分隔
  if (rowIndex > 0) {
    const prev = summaryRows.value[rowIndex - 1];
    if (prev && row.encounterClassCode && prev.encounterClassCode !== row.encounterClassCode) {
      classes.push('is-section-start');
    }
  }
  return classes.join(' ');
}

/**
 * 同就诊类型块内合并「就诊类型」列，便于视觉分组。
 */
function handleEncounterClassSpanMethod({
  row,
  column,
  rowIndex,
}: {
  row: KeyMedicationUsageSummaryRowDto;
  column: { property?: string };
  rowIndex: number;
}): [number, number] {
  if (column.property !== 'encounterClassDisplay') {
    return [1, 1];
  }
  const code = row.encounterClassCode ?? '';
  // 非组首行：被上方 rowspan 吞掉
  if (rowIndex > 0) {
    const prev = summaryRows.value[rowIndex - 1];
    if (prev && (prev.encounterClassCode ?? '') === code) {
      return [0, 0];
    }
  }
  // 组首：向下数连续同就诊类型行数
  let rowspan = 1;
  for (let i = rowIndex + 1; i < summaryRows.value.length; i += 1) {
    if ((summaryRows.value[i]?.encounterClassCode ?? '') === code) {
      rowspan += 1;
    } else {
      break;
    }
  }
  return [rowspan, 1];
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
              >
                <ElOption
                  v-for="item in encounterClassOptions"
                  :key="String(item.value)"
                  :label="String(item.label ?? '')"
                  :value="String(item.value ?? '')"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="重点药类型">
              <ElSelect
                v-model="queryParams.keyDrugTypeCodes"
                multiple
                clearable
                collapse-tags
                collapse-tags-tooltip
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
              >
                <ElOption
                  v-for="item in keyDrugTypeOptions"
                  :key="String(item.value)"
                  :label="String(item.label ?? '')"
                  :value="String(item.value ?? '')"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="药品性质">
              <ElSelect
                v-model="queryParams.medicinePropertyCodes"
                multiple
                clearable
                collapse-tags
                collapse-tags-tooltip
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
              >
                <ElOption
                  v-for="item in medicinePropertyOptions"
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
            :row-class-name="resolveRowClassName"
            :span-method="handleEncounterClassSpanMethod"
            class="key-medication-usage-table"
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
              min-width="120"
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
            <ElTableColumn
              prop="usageRatioPercent"
              label="人次占比"
              align="center"
              header-align="center"
              min-width="110"
            >
              <template #default="{ row }">
                {{ formatRatioCell(row.usageRatioPercent) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="saleAmountRatioPercent"
              label="金额占比"
              align="center"
              header-align="center"
              min-width="110"
            >
              <template #default="{ row }">
                {{ formatRatioCell(row.saleAmountRatioPercent) }}
              </template>
            </ElTableColumn>
          </ReportElTable>
        </ReportTableScroll>
      </template>

      <template #footer>
        <ReportPageFooter :items="footerSummaryItems" />
      </template>
    </ReportListPageLayout>

    <ReportPrintFieldDialog
      ref="printFieldDialogRef"
      report-code="report-key-medication-usage"
      :column-catalog="printColumnCatalog"
      show-print-scope
    />
  </Page>
</template>

<style scoped>
/* 住院块首行：粗顶边分隔门诊/住院 */
:deep(.key-medication-usage-table .is-section-start > td) {
  border-top: 3px solid var(--el-border-color-darker);
}

/* 组内合计行：加粗 + 浅底 */
:deep(.key-medication-usage-table .is-total-row > td) {
  font-weight: 600;
  background-color: var(--el-fill-color-light);
}
</style>
