<!--
  门诊挂号明细报表：汇总/明细双 Tab；只读查询与导出（AS-RPT-007）。
-->
<script setup lang="ts">
import type {
  OutpatientRegistrationDetailRowDto,
  OutpatientRegistrationDetailSummaryRowDto,
  PresentationModeCode,
} from '#/api/report/outpatient-registration-detail';
import type { ApiOptionItem } from '#/types/api-response';
import type { ReportPrintColumnDef } from '#/utils/print/print-report-list';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Download, Printer, Refresh, Search, Setting } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  exportOutpatientRegistrationDetailReportApi,
  loadOutpatientRegistrationDetailReportPageInitApi,
  pageOutpatientRegistrationDetailReportDetailApi,
  PRESENTATION_MODE_DETAIL,
  PRESENTATION_MODE_SUMMARY,
  SUMMARY_VIEW_DEPARTMENT,
  SUMMARY_VIEW_DOCTOR,
  summarizeOutpatientRegistrationDetailReportApi,
} from '#/api/report/outpatient-registration-detail';
import { useRequest } from '#/hooks/use-request';
import { buildExcelExportColumns } from '#/utils/export/build-excel-export-columns';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  isReportRefundBySignedFields,
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
  resolveReportRefundRowClassName,
  useReportListPrint,
} from '#/views/report/shared';
import {
  buildReportDateRangeShortcuts,
  normalizeReportDateRangeYmd,
} from '#/views/report/shared/report-date-range-shortcuts';
import { optionDtosToSelectItems } from '#/views/report/shared/report-format';

defineOptions({ name: 'OutpatientRegistrationDetailReport' });

interface QueryState {
  pageNo: number;
  pageSize: number;
  dateFrom: string;
  dateTo: string;
  summaryView: string;
  organizationId?: string;
  practitionerId?: string;
  keyword?: string;
  /** 就诊状态 code；空=全部。 */
  statusEnum?: string;
  presentationMode: PresentationModeCode;
}

const PAGE_TITLE = '门诊挂号明细';

const summaryViewOptions = ref<ApiOptionItem[]>([]);
const organizationOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const statusOptions = ref<ApiOptionItem[]>([]);
const summaryRows = ref<OutpatientRegistrationDetailSummaryRowDto[]>([]);
const detailRows = ref<OutpatientRegistrationDetailRowDto[]>([]);
const total = ref(0);
const totalNetRegistrationCount = ref(0);
const totalRegistrationFeeAmount = ref<number | string>(0);
/** 明细页脚：挂号笔数（排除待挂号/未收费取消）。 */
const registrationCount = ref(0);
/** 明细页脚：已退笔数。 */
const cancelledCount = ref(0);
/** 明细页脚：有效笔数。 */
const activeCount = ref(0);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const queryParams = reactive<QueryState>({
  pageNo: 1,
  pageSize: 20,
  dateFrom: '',
  dateTo: '',
  summaryView: SUMMARY_VIEW_DEPARTMENT,
  presentationMode: PRESENTATION_MODE_DETAIL,
});

const isSummaryTab = computed(() => queryParams.presentationMode === PRESENTATION_MODE_SUMMARY);

const isDoctorView = computed(() => queryParams.summaryView === SUMMARY_VIEW_DOCTOR);

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

const footerSummaryItems = computed(() => {
  if (isSummaryTab.value) {
    return [
      { label: '汇总行数', value: summaryRows.value.length },
      {
        label: '净挂号笔数合计',
        value: totalNetRegistrationCount.value,
      },
      {
        label: '挂号费合计',
        value: formatMoneyCell(totalRegistrationFeeAmount.value),
        primary: true,
        prefix: '¥',
      },
    ];
  }
  return [
    { label: '挂号', value: registrationCount.value },
    { label: '有效', value: activeCount.value },
    { label: '已退', value: cancelledCount.value },
  ];
});

/** ReportQueryBar 需要 Record 形态的 model。 */
const queryBarModel = computed(() => queryParams as Record<string, unknown>);

const { run: runInit, loading: initLoading } = useRequest(
  loadOutpatientRegistrationDetailReportPageInitApi,
  {
    manual: true,
    onSuccess: (data) => {
      summaryViewOptions.value = optionDtosToSelectItems(data?.summaryViewOptions);
      organizationOptions.value = optionDtosToSelectItems(data?.organizationOptions);
      practitionerOptions.value = optionDtosToSelectItems(data?.practitionerOptions);
      statusOptions.value = optionDtosToSelectItems(data?.statusOptions);
      queryParams.dateFrom =
        normalizeReportDateRangeYmd(data?.defaultDateFrom) || queryParams.dateFrom;
      queryParams.dateTo = normalizeReportDateRangeYmd(data?.defaultDateTo) || queryParams.dateTo;
      if (data?.defaultPageSize && data.defaultPageSize > 0) {
        queryParams.pageSize = data.defaultPageSize;
      }
      const defaultView = String(
        data?.defaultSummaryView ?? summaryViewOptions.value[0]?.value ?? '',
      ).trim();
      if (defaultView) {
        queryParams.summaryView = defaultView;
      }
    },
  },
);

const { run: runSummarize, loading: summarizeLoading } = useRequest(
  summarizeOutpatientRegistrationDetailReportApi,
  {
    manual: true,
    onSuccess: (data) => {
      summaryRows.value = data?.rows ?? [];
      totalNetRegistrationCount.value = Number(data?.totalNetRegistrationCount ?? 0);
      totalRegistrationFeeAmount.value = data?.totalRegistrationFeeAmount ?? 0;
      total.value = summaryRows.value.length;
      registrationCount.value = 0;
      cancelledCount.value = 0;
      activeCount.value = 0;
    },
  },
);

const { run: runPageDetail, loading: detailLoading } = useRequest(
  pageOutpatientRegistrationDetailReportDetailApi,
  {
    manual: true,
    onSuccess: (data) => {
      detailRows.value = data?.content ?? [];
      total.value = Number(data?.totalCount ?? 0);
      totalNetRegistrationCount.value = 0;
      totalRegistrationFeeAmount.value = 0;
      const footer = data?.footer;
      registrationCount.value = Number(footer?.registrationCount ?? 0);
      cancelledCount.value = Number(footer?.cancelledCount ?? 0);
      activeCount.value = Number(footer?.activeCount ?? 0);
    },
  },
);

const { run: runExport, loading: exportLoading } = useRequest(
  exportOutpatientRegistrationDetailReportApi,
  {
    manual: true,
    onSuccess: (data) => {
      downloadBinaryExportFile(data, '门诊挂号明细.xlsx');
      const rowCount = data.rowCount ?? data.exportedRowCount ?? 0;
      if (data.truncated) {
        ElMessage.warning('导出数据已触达行数上限，部分记录未包含在文件中');
      } else {
        ElMessage.success(`导出成功，共 ${rowCount} 行`);
      }
    },
  },
);

/** 当前 Tab / 汇总视角对应的可打印列目录。 */
const printColumnCatalog = computed<ReportPrintColumnDef[]>(() => {
  if (isSummaryTab.value) {
    const cols: ReportPrintColumnDef[] = [];
    if (isDoctorView.value) {
      cols.push({ field: 'practitionerName', title: '接诊医生', align: 'center' });
    }
    cols.push(
      { field: 'organizationName', title: '科室', align: 'center' },
      { field: 'serviceTypeName', title: '号源类型', align: 'center' },
      { field: 'netRegistrationCount', title: '净挂号笔数', align: 'right' },
      { field: 'registrationFeeAmount', title: '挂号费合计', align: 'right' },
    );
    return cols;
  }
  return [
    { field: 'patientName', title: '患者姓名', align: 'center' },
    { field: 'busNo', title: '门诊号', align: 'center' },
    { field: 'organizationName', title: '挂号科室', align: 'center' },
    { field: 'admitterDoctorName', title: '接诊医生', align: 'center' },
    { field: 'serviceTypeName', title: '号源类型', align: 'center' },
    { field: 'statusEnum_enumText', title: '状态', align: 'center' },
    { field: 'registrationTime', title: '挂号时间', align: 'center' },
    { field: 'feeNatureDisplay', title: '费用性质', align: 'center' },
    { field: 'insuranceTypeDisplay', title: '医保类型', align: 'center' },
    { field: 'registrationFeeAmount', title: '挂号费', align: 'right' },
  ];
});

/** 打印偏好键：明细固定；汇总按科室/医生视角分键（列不同）。 */
function resolveReportPrintCode() {
  if (isSummaryTab.value) {
    const view = queryParams.summaryView || SUMMARY_VIEW_DEPARTMENT;
    return `report-outpatient-registration-summary-${view}`;
  }
  return 'report-outpatient-registration-detail';
}

/** 将当前 Tab 表格行映射为打印单元格文案。 */
function mapCurrentPrintRows(): Record<string, string>[] {
  const fields = reportPrintFieldsOf(printColumnCatalog.value);
  if (isSummaryTab.value) {
    return summaryRows.value.map((row) =>
      mapReportPrintRow(row as unknown as Record<string, unknown>, fields, {
        netRegistrationCount: (value) => formatCountCell(value),
        registrationFeeAmount: (value) => formatMoneyCell(value),
      }),
    );
  }
  return detailRows.value.map((row) =>
    mapReportPrintRow(row as unknown as Record<string, unknown>, fields, {
      statusEnum_enumText: (_value, raw) => String(raw.statusEnum_enumText ?? raw.statusEnum ?? ''),
      registrationFeeAmount: (value) => formatMoneyCell(value),
    }),
  );
}

const { printFieldDialogRef, printSubmitting, handleOpenPrintFields, handlePrintCurrentPage } =
  useReportListPrint({
    getReportCode: () => resolveReportPrintCode(),
    getColumnCatalog: () => printColumnCatalog.value,
    getReportTitle: () => PAGE_TITLE,
    getPrintRows: () => mapCurrentPrintRows(),
  });

const tableLoading = computed(
  () => initLoading.value || summarizeLoading.value || detailLoading.value || printSubmitting.value,
);

/** 下拉选项转为 ElSelect 结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 金额单元格：空占位。 */
function formatMoneyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return formatMoneyDisplay(value);
}

/** 数量单元格。 */
function formatCountCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return String(value);
}

/** 汇总行是否净退号冲减（净笔数/金额为负）。 */
function isRegistrationSummaryRefundRow(row: OutpatientRegistrationDetailSummaryRowDto): boolean {
  return isReportRefundBySignedFields(row as Record<string, unknown>, [
    'netRegistrationCount',
    'registrationFeeAmount',
  ]);
}

/** 明细行是否已退号。 */
function isRegistrationDetailRefundRow(row: OutpatientRegistrationDetailRowDto): boolean {
  const code = String(row.statusEnum ?? '').trim();
  return code === 'CANCELLED' || code === 'ENTERED_IN_ERROR';
}

/** 汇总退冲减整行标红。 */
function resolveSummaryRowClassName({
  row,
}: {
  row: OutpatientRegistrationDetailSummaryRowDto;
}): string {
  return resolveReportRefundRowClassName(isRegistrationSummaryRefundRow(row));
}

/** 明细退号整行标红。 */
function resolveDetailRowClassName({ row }: { row: OutpatientRegistrationDetailRowDto }): string {
  return resolveReportRefundRowClassName(isRegistrationDetailRefundRow(row));
}

/** 就诊状态 Tag（按 EncounterStatus code，不用展示文案分支）。 */
function resolveStatusTagType(statusEnum?: string): 'danger' | 'info' | 'success' {
  const code = String(statusEnum ?? '').trim();
  if (code === 'CANCELLED' || code === 'ENTERED_IN_ERROR') {
    return 'danger';
  }
  if (code === 'COMPLETED' || code === 'DISCHARGED') {
    return 'success';
  }
  return 'info';
}

/** 汇总行稳定 key。 */
function resolveSummaryRowKey(row: OutpatientRegistrationDetailSummaryRowDto) {
  if (row.dimensionKey) {
    return row.dimensionKey;
  }
  return [
    row.summaryDimension ?? '',
    row.organizationId ?? '',
    row.practitionerId ?? '',
    row.serviceTypeId ?? '',
  ].join('|');
}

/** 明细行稳定 key。 */
function resolveDetailRowKey(row: OutpatientRegistrationDetailRowDto) {
  return String(row.encounterId ?? `${row.busNo ?? ''}|${row.registrationTime ?? ''}`);
}

/** 校验日期并组装筛选体（Long 主键保持 string）。 */
function buildFilterPayload() {
  const dateFrom = normalizeReportDateRangeYmd(queryParams.dateFrom);
  const dateTo = normalizeReportDateRangeYmd(queryParams.dateTo);
  if (!dateFrom || !dateTo) {
    ElMessage.warning('请选择挂号业务日期');
    return null;
  }
  const keyword = String(queryParams.keyword ?? '').trim();
  const statusEnum = String(queryParams.statusEnum ?? '').trim();
  return {
    dateFrom,
    dateTo,
    organizationId: preserveApiIdForRequest(queryParams.organizationId),
    practitionerId: preserveApiIdForRequest(queryParams.practitionerId),
    keyword: keyword || undefined,
    statusEnum: statusEnum || undefined,
  };
}

/** 拉取当前 Tab 数据。 */
async function getList() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  if (isSummaryTab.value) {
    await runSummarize({
      ...filters,
      summaryView: queryParams.summaryView || SUMMARY_VIEW_DEPARTMENT,
    });
  } else {
    await runPageDetail({
      ...filters,
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
    });
  }
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 搜索：回到第一页再查。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 切换汇总/明细 Tab 后立即查询。 */
function handleTabChange() {
  queryParams.pageNo = 1;
  void getList();
}

/** 导出当前 Tab（列清单与页面可见列一致）。 */
async function handleExport() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  const exportColumns = buildExcelExportColumns(printColumnCatalog.value);
  await runExport({
    ...filters,
    presentationMode: queryParams.presentationMode,
    summaryView: isSummaryTab.value
      ? queryParams.summaryView || SUMMARY_VIEW_DEPARTMENT
      : undefined,
    exportColumns,
  });
}

/** 重置为初始化默认值。 */
async function handleReset() {
  queryParams.pageNo = 1;
  queryParams.organizationId = undefined;
  queryParams.practitionerId = undefined;
  queryParams.keyword = undefined;
  queryParams.statusEnum = undefined;
  queryParams.presentationMode = PRESENTATION_MODE_DETAIL;
  await runInit();
  void getList();
}

/** 切换每页条数（仅明细）。 */
function handlePageSizeChange(size: number) {
  queryParams.pageSize = size;
  queryParams.pageNo = 1;
  void getList();
}

/** 切换页码（仅明细）。 */
function handleCurrentPageChange(page: number) {
  queryParams.pageNo = page;
  void getList();
}

onMounted(async () => {
  try {
    await runInit();
    await getList();
  } catch {
    // init 失败时不自动查询
  }
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryBarModel">
          <template #fields>
            <ElFormItem label="挂号日期">
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
            <ElFormItem label="挂号科室">
              <ElSelect
                v-model="queryParams.organizationId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(organizationOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="接诊医生">
              <ElSelect
                v-model="queryParams.practitionerId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(practitionerOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="状态">
              <ElSelect
                v-model="queryParams.statusEnum"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(statusOptions)"
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
                placeholder="患者姓名/门诊号"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem v-if="isSummaryTab" label="汇总视角">
              <ElSelect
                v-model="queryParams.summaryView"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(summaryViewOptions)"
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
        <ReportListToolbar
          :title="`${PAGE_TITLE} · ${isSummaryTab ? '汇总' : '明细'}`"
          :total="isSummaryTab ? summaryRows.length : total"
        >
          <template #actions>
            <ElRadioGroup v-model="queryParams.presentationMode" @change="handleTabChange">
              <ElRadioButton :value="PRESENTATION_MODE_SUMMARY">汇总</ElRadioButton>
              <ElRadioButton :value="PRESENTATION_MODE_DETAIL">明细</ElRadioButton>
            </ElRadioGroup>
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
            <ElButton plain :icon="Download" :loading="exportLoading" @click="handleExport">
              导出
            </ElButton>
          </template>
        </ReportListToolbar>
      </template>

      <template #table>
        <ReportTableScroll ref="tableScrollRef">
          <template #default="{ tableHeight }">
            <ReportElTable
              v-if="isSummaryTab"
              :loading="tableLoading"
              :data="summaryRows"
              :height="tableHeight"
              :row-key="resolveSummaryRowKey"
              :row-class-name="resolveSummaryRowClassName"
            >
              <ElTableColumn
                type="index"
                label="序号"
                width="56"
                align="center"
                header-align="center"
              />
              <ElTableColumn
                v-if="!isDoctorView"
                prop="organizationName"
                label="科室"
                min-width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                v-if="isDoctorView"
                prop="practitionerName"
                label="接诊医生"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                v-if="isDoctorView"
                prop="organizationName"
                label="科室"
                min-width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="serviceTypeName"
                label="号源类型"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="净挂号笔数"
                width="120"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ formatCountCell(row.netRegistrationCount) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="挂号费合计"
                width="120"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.registrationFeeAmount) }}
                </template>
              </ElTableColumn>
            </ReportElTable>

            <ReportElTable
              v-else
              :loading="tableLoading"
              :data="detailRows"
              :height="tableHeight"
              :row-key="resolveDetailRowKey"
              :row-class-name="resolveDetailRowClassName"
            >
              <ElTableColumn
                prop="patientName"
                label="患者姓名"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="busNo"
                label="门诊号"
                min-width="130"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="organizationName"
                label="挂号科室"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="admitterDoctorName"
                label="接诊医生"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="serviceTypeName"
                label="号源类型"
                min-width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn label="状态" width="100" align="center" header-align="center">
                <template #default="{ row }">
                  <ElTag :type="resolveStatusTagType(row.statusEnum)" effect="light">
                    {{ row.statusEnum_enumText || row.statusEnum || '—' }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="registrationTime"
                label="挂号时间"
                width="170"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="feeNatureDisplay"
                label="费用性质"
                min-width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="insuranceTypeDisplay"
                label="医保类型"
                min-width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="挂号费"
                width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.registrationFeeAmount) }}
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
          :page-size="isSummaryTab ? Math.max(summaryRows.length, 1) : queryParams.pageSize"
          :total="isSummaryTab ? summaryRows.length : total"
          :layout="isSummaryTab ? 'total' : 'sizes, prev, pager, next, jumper'"
          @size-change="handlePageSizeChange"
          @current-change="handleCurrentPageChange"
        />
      </template>
    </ReportListPageLayout>

    <ReportPrintFieldDialog
      ref="printFieldDialogRef"
      :report-code="resolveReportPrintCode()"
      :column-catalog="printColumnCatalog"
    />
  </Page>
</template>
