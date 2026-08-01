<!--
  门诊/住院费用明细报表：汇总/明细双 Tab；只读查询与导出（AS-RPT-008 / AS-RPT-009）。
-->
<script setup lang="ts">
import type { FeeDetailTableRow } from './flatten-encounter-blocks';

import type {
  FeeDetailAmountSummaryDto,
  FeeDetailReportScope,
  FeeDetailReportSummaryFooterDto,
  FeeDetailReportSummaryRowDto,
  LoadFeeDetailReportPageInitResponse,
  PresentationModeCode,
} from '#/api/report/fee-detail';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Download, Refresh, Search } from '@element-plus/icons-vue';
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

import dayjs from 'dayjs';

import {
  exportFeeDetailReportApi,
  loadFeeDetailReportPageInitApi,
  pageFeeDetailReportApi,
  PRESENTATION_MODE_DETAIL,
  PRESENTATION_MODE_SUMMARY,
  summarizeFeeDetailReportApi,
} from '#/api/report/fee-detail';
import { useRequest } from '#/hooks/use-request';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatMoneyYuan } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
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

import { FEE_DETAIL_SUMMARY_DISPLAY, SUMMARY_FOOTER_DISPLAY } from './constants';
import { resolveFeeDetailEncounterSpanMethod } from './encounter-column-merge';
import { flattenEncounterBlocksToTableRows } from './flatten-encounter-blocks';

defineOptions({ name: 'FeeDetailReportPage' });

interface Props {
  /** 门诊或住院费用明细报表场景 */
  scope: FeeDetailReportScope;
}

interface QueryFormState {
  pageNo: number;
  pageSize: number;
  dateFrom: string;
  dateTo: string;
  tradeDirection: string;
  registrationDepartmentId?: string;
  feeCategoryCodes?: string[];
  subCategoryCodes?: string[];
  patientKeyword?: string;
  productNameKeyword?: string;
  productCodeKeyword?: string;
  cashierId?: string;
  feeNatureContractNo?: string;
  presentationMode: PresentationModeCode;
}

const props = defineProps<Props>();

const pageInit = ref<LoadFeeDetailReportPageInitResponse | null>(null);
const summaryRows = ref<FeeDetailReportSummaryRowDto[]>([]);
const summaryFooter = ref<FeeDetailReportSummaryFooterDto | undefined>();
const tableData = ref<FeeDetailTableRow[]>([]);
const globalSummary = ref<FeeDetailAmountSummaryDto | undefined>();
const total = ref(0);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const queryParams = reactive<QueryFormState>({
  pageNo: 1,
  pageSize: 10,
  dateFrom: '',
  dateTo: '',
  tradeDirection: 'ALL',
  registrationDepartmentId: undefined,
  feeCategoryCodes: undefined,
  subCategoryCodes: undefined,
  patientKeyword: undefined,
  productNameKeyword: undefined,
  productCodeKeyword: undefined,
  cashierId: undefined,
  feeNatureContractNo: undefined,
  /** 进入页默认明细 Tab（门/住院统一）。 */
  presentationMode: PRESENTATION_MODE_DETAIL,
});

const pageTitle = computed(() => (props.scope === 'outpatient' ? '门诊费用明细' : '住院费用明细'));

const defaultExportFileName = computed(() =>
  props.scope === 'outpatient' ? '门诊费用明细.xlsx' : '住院费用明细.xlsx',
);

const encounterNoLabel = computed(() => (props.scope === 'outpatient' ? '门诊号' : '住院号'));

const departmentLabel = computed(() => (props.scope === 'outpatient' ? '挂号科室' : '住院科室'));

const patientKeywordPlaceholder = computed(() =>
  props.scope === 'outpatient' ? '姓名 / 门诊号' : '姓名 / 住院号',
);

const isSummaryTab = computed(() => queryParams.presentationMode === PRESENTATION_MODE_SUMMARY);

const dateRangeShortcuts = buildReportDateRangeShortcuts();

/** 结算日期与 daterange 双向绑定。 */
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

const tradeDirectionOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.tradeDirectionOptions ?? [],
);

const registrationDepartmentOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.registrationDepartmentOptions ?? [],
);

const feeCategoryOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.feeCategoryOptions ?? [],
);

const cashierOptions = computed<ApiOptionItem[]>(() => pageInit.value?.cashierOptions ?? []);

const feeNatureOptions = computed<ApiOptionItem[]>(() => pageInit.value?.feeNatureOptions ?? []);

/** 项目类别：合并药品/诊疗/耗材 fin_type 字典（按 value 去重）。 */
const subCategoryOptions = computed<ApiOptionItem[]>(() => {
  const init = pageInit.value;
  if (!init) {
    return [];
  }
  const merged: ApiOptionItem[] = [
    ...(init.medicationSubCategoryOptions ?? []),
    ...(init.activitySubCategoryOptions ?? []),
    ...(init.deviceSubCategoryOptions ?? []),
  ];
  const seen = new Set<string>();
  // 按 value 去重，避免三类字典重叠导致重复选项。
  return merged.filter((item) => {
    const value = String(item.value ?? '');
    if (!value || seen.has(value)) {
      return false;
    }
    seen.add(value);
    return true;
  });
});

const showFeeCategoryFilter = computed(() => feeCategoryOptions.value.length > 0);

/** ReportQueryBar 需要 Record 形态的 model。 */
const queryBarModel = computed(() => queryParams as Record<string, unknown>);

const footerSummaryItems = computed(() => {
  if (isSummaryTab.value) {
    const footer = summaryFooter.value;
    if (!footer) {
      return [{ label: '汇总行数', value: summaryRows.value.length }];
    }
    return SUMMARY_FOOTER_DISPLAY.map((item) => {
      const raw = footer[item.key];
      if (item.kind === 'count') {
        return {
          label: item.label,
          value: raw === undefined || raw === null || raw === '' ? '—' : String(raw),
          primary: item.key === 'lineCountTotal',
        };
      }
      return {
        label: item.label,
        value: formatMoneyCell(raw),
        primary: item.key === 'saleAmountTotal',
        prefix: '¥',
      };
    });
  }
  const summary = globalSummary.value;
  if (!summary) {
    return [{ label: '明细行数', value: total.value }];
  }
  return FEE_DETAIL_SUMMARY_DISPLAY.map((item) => ({
    label: item.label,
    value: formatMoneyCell(summary[item.key]),
  }));
});

const { run: runInit, loading: initLoading } = useRequest(
  () => loadFeeDetailReportPageInitApi(props.scope),
  { manual: true },
);

const { run: runSummarize, loading: summarizeLoading } = useRequest(
  (body) => summarizeFeeDetailReportApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      summaryRows.value = data?.rows ?? [];
      summaryFooter.value = data?.footer;
      total.value = summaryRows.value.length;
    },
  },
);

const { run: runPage, loading: pageLoading } = useRequest(
  (body) => pageFeeDetailReportApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      tableData.value = flattenEncounterBlocksToTableRows(data.content ?? []);
      total.value = data.totalCount ?? 0;
      globalSummary.value = data.summary;
    },
  },
);

const { run: runExport, loading: exportLoading } = useRequest(
  (body) => exportFeeDetailReportApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      downloadBinaryExportFile(data, defaultExportFileName.value);
      const rowCount = data.rowCount ?? data.exportedRowCount ?? 0;
      if (data.truncated) {
        ElMessage.warning('导出数据已触达行数上限，部分记录未包含在文件中');
      } else {
        ElMessage.success(`导出成功，共 ${rowCount} 行`);
      }
    },
  },
);

const tableLoading = computed(
  () => initLoading.value || summarizeLoading.value || pageLoading.value,
);

/** 解析默认 Tab：门/住院费用明细均固定明细（忽略后端 defaultActiveTab）。 */
function resolveDefaultPresentationMode(
  _init: LoadFeeDetailReportPageInitResponse | null | undefined,
): PresentationModeCode {
  return PRESENTATION_MODE_DETAIL;
}

/** 下拉选项转为 ElSelect 可用结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 校验日期区间并组装查询/导出请求体。 */
function buildFilterPayload() {
  const dateFrom = normalizeReportDateRangeYmd(queryParams.dateFrom);
  const dateTo = normalizeReportDateRangeYmd(queryParams.dateTo);
  if (!dateFrom || !dateTo) {
    ElMessage.warning('请选择结算日期');
    return null;
  }
  if (dayjs(dateFrom).isAfter(dayjs(dateTo))) {
    ElMessage.warning('开始日期不能晚于结束日期');
    return null;
  }
  return {
    dateFrom,
    dateTo,
    // 汇总 Tab 不传收退方向，由后端强制净视角。
    tradeDirection: isSummaryTab.value ? undefined : queryParams.tradeDirection || 'ALL',
    registrationDepartmentId: preserveApiIdForRequest(queryParams.registrationDepartmentId),
    feeCategoryCodes: queryParams.feeCategoryCodes?.length
      ? queryParams.feeCategoryCodes
      : undefined,
    subCategoryCodes: queryParams.subCategoryCodes?.length
      ? queryParams.subCategoryCodes
      : undefined,
    patientKeyword: queryParams.patientKeyword?.trim() || undefined,
    productNameKeyword: queryParams.productNameKeyword?.trim() || undefined,
    productCodeKeyword: queryParams.productCodeKeyword?.trim() || undefined,
    cashierId: preserveApiIdForRequest(queryParams.cashierId),
    feeNatureContractNo: queryParams.feeNatureContractNo || undefined,
  };
}

/** 拉取当前 Tab 数据。 */
async function getList() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  if (isSummaryTab.value) {
    await runSummarize(filters);
  } else {
    await runPage({
      ...filters,
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
    });
  }
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 搜索按钮：回到第一页再查。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 切换汇总/明细 Tab 后立即查询。 */
function handleTabChange() {
  queryParams.pageNo = 1;
  void getList();
}

/** 重置筛选项为初始化默认值。 */
function handleReset() {
  const init = pageInit.value;
  queryParams.dateFrom =
    normalizeReportDateRangeYmd(init?.defaultDateFrom) || dayjs().format('YYYY-MM-DD');
  queryParams.dateTo =
    normalizeReportDateRangeYmd(init?.defaultDateTo) || dayjs().format('YYYY-MM-DD');
  queryParams.tradeDirection = 'ALL';
  if (init?.tradeDirectionOptions?.length) {
    const hasAll = init.tradeDirectionOptions.some((item) => item.value === 'ALL');
    queryParams.tradeDirection = hasAll
      ? 'ALL'
      : String(init.tradeDirectionOptions[0]?.value ?? 'ALL');
  }
  queryParams.registrationDepartmentId = undefined;
  queryParams.feeCategoryCodes = undefined;
  queryParams.subCategoryCodes = undefined;
  queryParams.patientKeyword = undefined;
  queryParams.productNameKeyword = undefined;
  queryParams.productCodeKeyword = undefined;
  queryParams.cashierId = undefined;
  queryParams.feeNatureContractNo = undefined;
  queryParams.presentationMode = resolveDefaultPresentationMode(init);
  queryParams.pageNo = 1;
  queryParams.pageSize = init?.defaultPageSize ?? 10;
  void getList();
}

/** 导出当前 Tab。 */
async function handleExport() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  await runExport({
    ...filters,
    presentationMode: queryParams.presentationMode,
  });
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

/** 数量展示：空值占位。 */
function formatQuantity(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return String(value);
}

/** 数量 + 单位合并，如「3盒」。 */
function formatQuantityWithUnit(quantity: unknown, unit: unknown): string {
  const qty = formatQuantity(quantity);
  const unitText = unit === undefined || unit === null ? '' : String(unit).trim();
  if (qty === '—' && !unitText) {
    return '—';
  }
  if (qty === '—') {
    return unitText || '—';
  }
  return unitText ? `${qty}${unitText}` : qty;
}

/** 金额展示：2 位小数 + 元（BR-MON-001）。 */
function formatMoneyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return formatMoneyYuan(value);
}

/** 判断金额是否为负数（退费行）。 */
function isNegativeAmount(value: unknown): boolean {
  if (value === undefined || value === null || value === '') {
    return false;
  }
  const num = Number(value);
  return Number.isFinite(num) && num < 0;
}

/** 金额列样式：负数标红。 */
function resolveAmountClass(value: unknown): string {
  return isNegativeAmount(value) ? 'text-[var(--el-color-danger)]' : '';
}

/** 收退方向标签类型。 */
function resolveTradeDirectionTagType(tradeDirection?: string): 'danger' | 'info' | 'success' {
  if (tradeDirection === 'REFUND') {
    return 'danger';
  }
  if (tradeDirection === 'CHARGE') {
    return 'success';
  }
  return 'info';
}

/** 收退方向文案。 */
function resolveTradeDirectionLabel(tradeDirection?: string): string {
  if (tradeDirection === 'REFUND') {
    return '退费';
  }
  if (tradeDirection === 'CHARGE') {
    return '收费';
  }
  return tradeDirection?.trim() || '—';
}

/** 行样式：按就诊块统一底色（避免合并列与斑马纹错位），小计行单独强调。 */
function resolveRowClassName({ row }: { row: FeeDetailTableRow }): string {
  const blockIndex = row.encounterBlockIndex ?? 0;
  const toneClass = blockIndex % 2 === 0 ? 'fee-detail-block--even' : 'fee-detail-block--odd';
  if (row.rowType === 'subtotal') {
    return `${toneClass} fee-detail-subtotal-row`;
  }
  return toneClass;
}

/** 同一业务号下合并就诊头列（业务号/患者/科室/日期）。 */
function handleEncounterSpanMethod(params: {
  column: { property?: string };
  row: FeeDetailTableRow;
}) {
  return resolveFeeDetailEncounterSpanMethod({
    row: params.row,
    column: params.column,
  });
}

/** 汇总行稳定 key。 */
function resolveSummaryRowKey(row: FeeDetailReportSummaryRowDto) {
  return String(row.finTypeCode ?? row.finTypeDisplay ?? '');
}

/** 加载页初始化下拉与默认日期。 */
async function loadPageInit() {
  const data = await runInit();
  pageInit.value = data;
  queryParams.dateFrom =
    normalizeReportDateRangeYmd(data.defaultDateFrom) || dayjs().format('YYYY-MM-DD');
  queryParams.dateTo =
    normalizeReportDateRangeYmd(data.defaultDateTo) || dayjs().format('YYYY-MM-DD');
  queryParams.pageSize = data.defaultPageSize ?? 10;
  queryParams.presentationMode = resolveDefaultPresentationMode(data);
  if (data.tradeDirectionOptions?.length) {
    const hasAll = data.tradeDirectionOptions.some((item) => item.value === 'ALL');
    queryParams.tradeDirection = hasAll
      ? 'ALL'
      : String(data.tradeDirectionOptions[0]?.value ?? 'ALL');
  }
  queryParams.cashierId = undefined;
}

onMounted(async () => {
  try {
    await loadPageInit();
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
            <ElFormItem label="结算日期">
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
            <ElFormItem v-if="!isSummaryTab" label="收退方向">
              <ElRadioGroup
                v-model="queryParams.tradeDirection"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElRadioButton
                  v-for="item in toSelectOptions(tradeDirectionOptions)"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </ElRadioButton>
              </ElRadioGroup>
            </ElFormItem>
            <ElFormItem :label="departmentLabel">
              <ElSelect
                v-model="queryParams.registrationDepartmentId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(registrationDepartmentOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem v-if="showFeeCategoryFilter" label="费用大类">
              <ElSelect
                v-model="queryParams.feeCategoryCodes"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(feeCategoryOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="项目类别">
              <ElSelect
                v-model="queryParams.subCategoryCodes"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(subCategoryOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="患者">
              <ElInput
                v-model="queryParams.patientKeyword"
                clearable
                :placeholder="patientKeywordPlaceholder"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="费用性质">
              <ElSelect
                v-model="queryParams.feeNatureContractNo"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(feeNatureOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="项目名称">
              <ElInput
                v-model="queryParams.productNameKeyword"
                clearable
                placeholder="项目名称"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="项目编码">
              <ElInput
                v-model="queryParams.productCodeKeyword"
                clearable
                placeholder="项目编码"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="收费员">
              <ElSelect
                v-model="queryParams.cashierId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(cashierOptions)"
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
          :title="`${pageTitle} · ${isSummaryTab ? '汇总' : '明细'}`"
          :total="isSummaryTab ? summaryRows.length : total"
        >
          <template #actions>
            <ElRadioGroup v-model="queryParams.presentationMode" @change="handleTabChange">
              <ElRadioButton :value="PRESENTATION_MODE_SUMMARY">汇总</ElRadioButton>
              <ElRadioButton :value="PRESENTATION_MODE_DETAIL">明细</ElRadioButton>
            </ElRadioGroup>
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
            >
              <ElTableColumn
                prop="finTypeDisplay"
                label="费用类型"
                min-width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="lineCount"
                label="行数"
                min-width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              />
              <ElTableColumn
                label="进价合计"
                prop="purchaseAmountTotal"
                width="120"
                align="right"
                header-align="right"
                :class-name="`${REPORT_TABLE_NUM_CELL} ${REPORT_TABLE_AMOUNT_CELL}`"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.purchaseAmountTotal)">
                    {{ formatMoneyCell(row.purchaseAmountTotal) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="售价合计"
                prop="saleAmountTotal"
                width="120"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.saleAmountTotal)">
                    {{ formatMoneyCell(row.saleAmountTotal) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="实收合计"
                prop="actualReceivedAmountTotal"
                width="120"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.actualReceivedAmountTotal)">
                    {{ formatMoneyCell(row.actualReceivedAmountTotal) }}
                  </span>
                </template>
              </ElTableColumn>
            </ReportElTable>

            <ReportElTable
              v-else
              class="fee-detail-report-table"
              :stripe="false"
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              row-key="rowKey"
              :row-class-name="resolveRowClassName"
              :span-method="handleEncounterSpanMethod"
            >
              <ElTableColumn
                prop="busNo"
                :label="encounterNoLabel"
                width="120"
                align="center"
                header-align="center"
                fixed="left"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="patientName"
                label="患者"
                width="90"
                align="center"
                header-align="center"
                fixed="left"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="registrationDepartmentName"
                :label="departmentLabel"
                width="100"
                align="center"
                header-align="center"
                fixed="left"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="encounterDate"
                label="就诊日期"
                width="110"
                align="center"
                header-align="center"
                fixed="left"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="feeNatureDisplay"
                label="费用性质"
                width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="medTypeDisplay"
                label="医疗类别"
                width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="subCategoryDisplay"
                label="项目类别"
                width="88"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="productName"
                label="项目名称"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="productCode"
                label="项目编码"
                width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="数量"
                width="88"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ formatQuantityWithUnit(row.quantityValue, row.quantityUnitDisplay) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="orderingDoctorName"
                label="开单医生"
                width="88"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="cashierName"
                label="收费人"
                width="88"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="billDate"
                label="结算时间"
                width="160"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="tradeDirection"
                label="来源"
                width="72"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  <template v-if="row.rowType === 'subtotal'">—</template>
                  <ElTag
                    v-else
                    :type="resolveTradeDirectionTagType(row.tradeDirection as string)"
                    effect="light"
                  >
                    {{ resolveTradeDirectionLabel(row.tradeDirection as string) }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="insuranceLevelDisplay"
                label="医保等级"
                width="88"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="进价单价"
                prop="purchaseUnitPrice"
                width="96"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.purchaseUnitPrice)">
                    {{ formatMoneyCell(row.purchaseUnitPrice) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="进价总价"
                prop="purchaseAmount"
                width="96"
                align="right"
                header-align="right"
                :class-name="`${REPORT_TABLE_NUM_CELL} ${REPORT_TABLE_AMOUNT_CELL}`"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.purchaseAmount)">
                    {{ formatMoneyCell(row.purchaseAmount) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="售价单价"
                prop="saleUnitPrice"
                width="96"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.saleUnitPrice)">
                    {{ formatMoneyCell(row.saleUnitPrice) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="售价总价"
                prop="saleAmount"
                width="96"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.saleAmount)">
                    {{ formatMoneyCell(row.saleAmount) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="实收金额"
                prop="actualReceivedAmount"
                width="96"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.actualReceivedAmount)">
                    {{ formatMoneyCell(row.actualReceivedAmount) }}
                  </span>
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
  </Page>
</template>

<style scoped lang="scss">
.fee-detail-report-table {
  /* 关闭默认斑马纹，改按就诊块着色，合并列与明细列底色一致。 */
  :deep(.el-table__body tr.fee-detail-block--even > td.el-table__cell),
  :deep(.el-table__body tr.fee-detail-block--even > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.fee-detail-block--even > td.el-table-fixed-column--right) {
    background-color: var(--el-bg-color);
  }

  :deep(.el-table__body tr.fee-detail-block--odd > td.el-table__cell),
  :deep(.el-table__body tr.fee-detail-block--odd > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.fee-detail-block--odd > td.el-table-fixed-column--right) {
    background-color: var(--el-fill-color-lighter);
  }

  :deep(.el-table__body tr.fee-detail-subtotal-row > td.el-table__cell),
  :deep(.el-table__body tr.fee-detail-subtotal-row > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.fee-detail-subtotal-row > td.el-table-fixed-column--right) {
    font-weight: 500;
    color: var(--el-text-color-primary);
    background-color: var(--el-fill-color-light) !important;
    border-top: 1px solid var(--el-border-color);
  }

  /* 禁用 hover 变色，保持就诊块底色。 */
  :deep(.el-table__body tr.fee-detail-block--even:hover > td.el-table__cell),
  :deep(.el-table__body tr.fee-detail-block--even:hover > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.fee-detail-block--even:hover > td.el-table-fixed-column--right) {
    background-color: var(--el-bg-color) !important;
  }

  :deep(.el-table__body tr.fee-detail-block--odd:hover > td.el-table__cell),
  :deep(.el-table__body tr.fee-detail-block--odd:hover > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.fee-detail-block--odd:hover > td.el-table-fixed-column--right) {
    background-color: var(--el-fill-color-lighter) !important;
  }

  :deep(.el-table__body tr.fee-detail-subtotal-row:hover > td.el-table__cell),
  :deep(.el-table__body tr.fee-detail-subtotal-row:hover > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.fee-detail-subtotal-row:hover > td.el-table-fixed-column--right) {
    background-color: var(--el-fill-color-light) !important;
  }
}
</style>
