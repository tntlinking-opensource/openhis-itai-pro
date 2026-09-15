<!--
  药品结余留用：批次必选 + 签发时间窗；门诊/住院合表明细与导出。
  主类型 T2；查询即搜；金额/数量列右对齐。
-->
<script setup lang="ts">
import type {
  LoadSurplusRetentionReportPageInitResponse,
  SurplusRetentionReportRowDto,
} from '#/api/report/surplus-retention';
import type { ApiOptionItem } from '#/types/api-response';
import type { ReportPrintColumnDef } from '#/utils/print/print-report-list';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Download, Refresh, Search } from '@element-plus/icons-vue';
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
  exportSurplusRetentionReportApi,
  loadSurplusRetentionReportPageInitApi,
  pageSurplusRetentionReportApi,
} from '#/api/report/surplus-retention';
import { useRequest } from '#/hooks/use-request';
import { buildExcelExportColumns } from '#/utils/export/build-excel-export-columns';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import {
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATERANGE,
  REPORT_TABLE_AMOUNT_CELL,
  REPORT_TABLE_NUM_CELL,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPageFooter,
  ReportQueryBar,
  ReportTableScroll,
} from '#/views/report/shared';
import {
  buildReportDateRangeShortcuts,
  normalizeReportDateRangeYmd,
} from '#/views/report/shared/report-date-range-shortcuts';
import { optionDtosToSelectItems } from '#/views/report/shared/report-format';

defineOptions({ name: 'SurplusRetentionReportPage' });

interface QueryFormState {
  pageNo: number;
  pageSize: number;
  surplusRetentionBatchCode: string;
  authoredDateFrom: string;
  authoredDateTo: string;
}

const PAGE_TITLE = '药品结余留用';

const pageInit = ref<LoadSurplusRetentionReportPageInitResponse | null>(null);
const tableData = ref<SurplusRetentionReportRowDto[]>([]);
const total = ref(0);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const queryParams = reactive<QueryFormState>({
  pageNo: 1,
  pageSize: 20,
  surplusRetentionBatchCode: '',
  authoredDateFrom: '',
  authoredDateTo: '',
});

const batchOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.surplusRetentionBatchCodeOptions ?? [],
);

const dateRangeShortcuts = buildReportDateRangeShortcuts();

/** 签发日期与 daterange 双向绑定。 */
const authoredDateRange = computed({
  get() {
    const start = normalizeReportDateRangeYmd(queryParams.authoredDateFrom);
    const end = normalizeReportDateRangeYmd(queryParams.authoredDateTo);
    if (start && end) {
      return [start, end] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.authoredDateFrom = value?.[0] ?? '';
    queryParams.authoredDateTo = value?.[1] ?? '';
  },
});

const queryBarModel = computed(() => queryParams as Record<string, unknown>);

/** 导出列目录（含 * 与括号单位，对齐监管模板 / 后端 @Excel；列表展示见下方 ElTableColumn）。 */
const exportColumnCatalog: ReportPrintColumnDef[] = [
  { field: 'outpatientInpatientNo', title: '*门诊住院号', align: 'center' },
  { field: 'patientName', title: '患者姓名', align: 'center' },
  { field: 'insuredRegion', title: '*参保地区', align: 'center' },
  { field: 'insuredType', title: '*参保类型', align: 'center' },
  { field: 'admissionTime', title: '*入院时间', align: 'center' },
  { field: 'dischargeTime', title: '*出院时间', align: 'center' },
  { field: 'prescriptionDate', title: '*处方日期', align: 'center' },
  { field: 'drugGenericName', title: '*药品通用名', align: 'center' },
  { field: 'productName', title: '*产品名称', align: 'center' },
  { field: 'drugCategoryText', title: '药品类别', align: 'center' },
  { field: 'doseFormText', title: '*剂型', align: 'center' },
  { field: 'specification', title: '*规格', align: 'center' },
  { field: 'packageUnitText', title: '*包装', align: 'center' },
  { field: 'pricingUnitText', title: '*计价单位', align: 'center' },
  { field: 'selectedProductText', title: '*是否中选产品', align: 'center' },
  { field: 'manufacturerName', title: '*生产企业', align: 'center' },
  { field: 'unitPrice', title: '*单价（元）', align: 'right' },
  { field: 'usageQuantity', title: '*使用数量（片袋支）', align: 'right' },
  { field: 'usageAmount', title: '*使用总金额（元）', align: 'right' },
];

const { run: runInit, loading: initLoading } = useRequest(loadSurplusRetentionReportPageInitApi, {
  manual: true,
  onSuccess: (data) => {
    pageInit.value = {
      ...data,
      surplusRetentionBatchCodeOptions: optionDtosToSelectItems(
        data?.surplusRetentionBatchCodeOptions,
      ),
    };
    queryParams.authoredDateFrom =
      normalizeReportDateRangeYmd(data?.defaultAuthoredDateFrom) || queryParams.authoredDateFrom;
    queryParams.authoredDateTo =
      normalizeReportDateRangeYmd(data?.defaultAuthoredDateTo) || queryParams.authoredDateTo;
    if (data?.defaultPageSize && data.defaultPageSize > 0) {
      queryParams.pageSize = data.defaultPageSize;
    }
    // 批次未选时不自动套第一项：须用户明确选择（BR-RPT-SRB-001）
  },
});

const { run: runPage, loading: pageLoading } = useRequest(pageSurplusRetentionReportApi, {
  manual: true,
  onSuccess: (data) => {
    tableData.value = data?.content ?? [];
    total.value = Number(data?.totalCount ?? 0);
  },
});

const { run: runExport, loading: exportLoading } = useRequest(exportSurplusRetentionReportApi, {
  manual: true,
  onSuccess: (data) => {
    downloadBinaryExportFile(data, '药品结余留用.xlsx');
    const rowCount = data.rowCount ?? data.exportedRowCount ?? 0;
    // 触顶截断：与 AS §7 / BR-RPT-SRB-006 一致，须提示用户
    if (data.truncated) {
      ElMessage.warning(`导出已截断，仅导出前 ${rowCount} 行（已达上限）`);
      return;
    }
    ElMessage.success(`导出成功，共 ${rowCount} 行`);
  },
});

const tableLoading = computed(() => initLoading.value || pageLoading.value || exportLoading.value);

/**
 * 组装查询/导出筛选体。
 */
function buildFilterBody() {
  return {
    surplusRetentionBatchCode: String(queryParams.surplusRetentionBatchCode ?? '').trim(),
    authoredDateFrom: normalizeReportDateRangeYmd(queryParams.authoredDateFrom) || '',
    authoredDateTo: normalizeReportDateRangeYmd(queryParams.authoredDateTo) || '',
  };
}

/**
 * 批次下拉变更：清空时清表；有值则按当前窗查询。
 */
function handleBatchChange() {
  if (!String(queryParams.surplusRetentionBatchCode ?? '').trim()) {
    tableData.value = [];
    total.value = 0;
    return;
  }
  handleQuery();
}

/**
 * 校验批次与签发窗后查询第一页。
 */
function handleQuery() {
  if (!String(queryParams.surplusRetentionBatchCode ?? '').trim()) {
    ElMessage.warning('请选择结余留用批次号');
    return;
  }
  if (!queryParams.authoredDateFrom || !queryParams.authoredDateTo) {
    ElMessage.warning('请选择签发时间');
    return;
  }
  queryParams.pageNo = 1;
  void loadPage();
}

/**
 * 拉取当前页明细。
 */
async function loadPage() {
  await runPage({
    ...buildFilterBody(),
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
  });
  await nextTick();
  tableScrollRef.value?.scrollToTop?.();
}

/**
 * 重置为 init 默认签发窗并清空批次，不自动查询。
 */
async function handleReset() {
  queryParams.pageNo = 1;
  queryParams.surplusRetentionBatchCode = '';
  tableData.value = [];
  total.value = 0;
  await runInit();
}

/**
 * 导出当前可见列。
 */
function handleExport() {
  if (!String(queryParams.surplusRetentionBatchCode ?? '').trim()) {
    ElMessage.warning('请选择结余留用批次号');
    return;
  }
  if (!queryParams.authoredDateFrom || !queryParams.authoredDateTo) {
    ElMessage.warning('请选择签发时间');
    return;
  }
  const exportColumns = buildExcelExportColumns(exportColumnCatalog);
  void runExport({
    ...buildFilterBody(),
    exportColumns,
  });
}

/**
 * 翻页。
 */
function handlePageChange(pageNo: number) {
  queryParams.pageNo = pageNo;
  void loadPage();
}

/**
 * 改页大小回第一页。
 */
function handleSizeChange(pageSize: number) {
  queryParams.pageSize = pageSize;
  queryParams.pageNo = 1;
  void loadPage();
}

/**
 * 金额单元格展示。
 */
function formatMoneyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return formatMoneyDisplay(value);
}

/**
 * 数量单元格展示。
 */
function formatQtyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return String(value);
}

/**
 * 文本空值占位。
 */
function formatTextCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return String(value);
}

/**
 * 行稳定主键（门诊住院号 + 处方日期 + 品名 + 下标）。
 */
function resolveRowKey(row: SurplusRetentionReportRowDto, index: number) {
  return `${row.outpatientInpatientNo ?? ''}-${row.prescriptionDate ?? ''}-${row.productName ?? ''}-${index}`;
}

onMounted(async () => {
  await runInit();
  // 批次必选：初始化后不自动查，避免空批次打后端
});
</script>

<template>
  <Page auto-content-height>
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryBarModel" label-width="128px">
          <template #fields>
            <ElFormItem label="结余留用批次号" required>
              <ElSelect
                v-model="queryParams.surplusRetentionBatchCode"
                clearable
                filterable
                placeholder="请选择"
                :class="REPORT_QUERY_CONTROL"
                @change="handleBatchChange"
              >
                <ElOption
                  v-for="item in batchOptions"
                  :key="String(item.value)"
                  :label="item.label"
                  :value="String(item.value)"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="签发时间" required>
              <ElDatePicker
                v-model="authoredDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                :shortcuts="dateRangeShortcuts"
                :clearable="false"
                :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATERANGE]"
                @change="handleQuery"
              />
            </ElFormItem>
          </template>
        </ReportQueryBar>
      </template>

      <template #toolbar>
        <ReportListToolbar :title="PAGE_TITLE" :total="total">
          <template #actions>
            <ElButton :icon="Search" type="primary" @click="handleQuery">查询</ElButton>
            <ElButton :icon="Refresh" @click="handleReset">重置</ElButton>
            <ElButton :icon="Download" :loading="exportLoading" @click="handleExport">
              导出
            </ElButton>
          </template>
        </ReportListToolbar>
      </template>

      <template #table>
        <ReportTableScroll ref="tableScrollRef">
          <ReportElTable
            :data="tableData"
            :loading="tableLoading"
            :row-key="resolveRowKey"
            border
            stripe
          >
            <ElTableColumn
              prop="outpatientInpatientNo"
              label="门诊住院号"
              align="center"
              header-align="center"
              min-width="120"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.outpatientInpatientNo) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="patientName"
              label="患者姓名"
              align="center"
              header-align="center"
              min-width="100"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.patientName) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="insuredRegion"
              label="参保地区"
              align="center"
              header-align="center"
              min-width="100"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.insuredRegion) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="insuredType"
              label="参保类型"
              align="center"
              header-align="center"
              min-width="100"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.insuredType) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="admissionTime"
              label="入院时间"
              align="center"
              header-align="center"
              min-width="160"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.admissionTime) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="dischargeTime"
              label="出院时间"
              align="center"
              header-align="center"
              min-width="160"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.dischargeTime) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="prescriptionDate"
              label="处方日期"
              align="center"
              header-align="center"
              min-width="160"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.prescriptionDate) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="drugGenericName"
              label="药品通用名"
              align="center"
              header-align="center"
              min-width="140"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.drugGenericName) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="productName"
              label="产品名称"
              align="center"
              header-align="center"
              min-width="140"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.productName) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="drugCategoryText"
              label="药品类别"
              align="center"
              header-align="center"
              min-width="100"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.drugCategoryText) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="doseFormText"
              label="剂型"
              align="center"
              header-align="center"
              min-width="90"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.doseFormText) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="specification"
              label="规格"
              align="center"
              header-align="center"
              min-width="110"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.specification) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="packageUnitText"
              label="包装"
              align="center"
              header-align="center"
              min-width="80"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.packageUnitText) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="pricingUnitText"
              label="计价单位"
              align="center"
              header-align="center"
              min-width="90"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.pricingUnitText) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="selectedProductText"
              label="是否中选产品"
              align="center"
              header-align="center"
              min-width="110"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.selectedProductText) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="manufacturerName"
              label="生产企业"
              align="center"
              header-align="center"
              min-width="140"
            >
              <template #default="{ row }">
                {{ formatTextCell(row.manufacturerName) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="unitPrice"
              label="单价"
              align="right"
              header-align="right"
              min-width="100"
            >
              <template #default="{ row }">
                <span :class="REPORT_TABLE_AMOUNT_CELL">
                  {{ formatMoneyCell(row.unitPrice) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="usageQuantity"
              label="使用数量"
              align="right"
              header-align="right"
              min-width="100"
            >
              <template #default="{ row }">
                <span :class="REPORT_TABLE_NUM_CELL">
                  {{ formatQtyCell(row.usageQuantity) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="usageAmount"
              label="使用总金额"
              align="right"
              header-align="right"
              min-width="110"
            >
              <template #default="{ row }">
                <span :class="REPORT_TABLE_AMOUNT_CELL">
                  {{ formatMoneyCell(row.usageAmount) }}
                </span>
              </template>
            </ElTableColumn>
          </ReportElTable>
        </ReportTableScroll>
      </template>

      <template #footer>
        <ReportPageFooter
          :current-page="queryParams.pageNo"
          :page-size="queryParams.pageSize"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </template>
    </ReportListPageLayout>
  </Page>
</template>
