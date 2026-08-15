<!--
  门诊/住院耗材使用报表：汇总/明细双 Tab；只读查询与导出（AS-RPT-005 / AS-RPT-006）。
-->
<script setup lang="ts">
import type {
  ConsumableUsageDetailRowDto,
  ConsumableUsageExportTab,
  ConsumableUsageReportScope,
  ConsumableUsageSummaryRowDto,
} from '#/api/report/consumable-usage';
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

import {
  EXPORT_TAB_DETAIL,
  EXPORT_TAB_SUMMARY,
  exportConsumableUsageReportApi,
  loadConsumableUsageReportPageInitApi,
  pageConsumableUsageReportDetailApi,
  SOURCE_CODE_ALL,
  SOURCE_CODE_DEPT_ISSUE,
  SOURCE_CODE_PATIENT_DISPENSE,
  SUMMARY_DIMENSION_LOCATION,
  summarizeConsumableUsageReportApi,
} from '#/api/report/consumable-usage';
import { useRequest } from '#/hooks/use-request';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
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
import { fmtReportNumber, optionDtosToSelectItems } from '#/views/report/shared/report-format';

defineOptions({ name: 'ConsumableUsageReportPage' });

interface Props {
  /** 门诊或住院耗材使用报表场景 */
  scope: ConsumableUsageReportScope;
}

interface QueryState {
  pageNo: number;
  pageSize: number;
  dateFrom: string;
  dateTo: string;
  summaryDimension: string;
  sourceCode: string;
  locationId?: string;
  exportTabCode: ConsumableUsageExportTab;
  feeNatureContractNo?: string;
  recordTypeCode?: string;
  deviceCategoryCodes: string[];
  performerPractitionerId?: string;
  deviceKeyword: string;
  patientKeyword: string;
  /** 患者=开立科室；领用=领用科室 */
  organizationId?: string;
}

const props = defineProps<Props>();

const summaryDimensionOptions = ref<ApiOptionItem[]>([]);
const sourceCodeOptions = ref<ApiOptionItem[]>([]);
const locationOptions = ref<ApiOptionItem[]>([]);
const feeNatureOptions = ref<ApiOptionItem[]>([]);
const organizationOptions = ref<ApiOptionItem[]>([]);
const categoryOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const recordTypeOptions = ref<ApiOptionItem[]>([]);
const summaryRows = ref<ConsumableUsageSummaryRowDto[]>([]);
const detailRows = ref<ConsumableUsageDetailRowDto[]>([]);
const total = ref(0);
const totalNetQuantity = ref<number | string>('');
const purchaseNetTotalAmount = ref<number | string>('');
const saleNetTotalAmount = ref<number | string>('');
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const queryParams = reactive<QueryState>({
  pageNo: 1,
  pageSize: 20,
  dateFrom: '',
  dateTo: '',
  summaryDimension: SUMMARY_DIMENSION_LOCATION,
  sourceCode: SOURCE_CODE_PATIENT_DISPENSE,
  exportTabCode: EXPORT_TAB_DETAIL,
  feeNatureContractNo: undefined,
  deviceCategoryCodes: [],
  deviceKeyword: '',
  patientKeyword: '',
  organizationId: undefined,
});

const pageTitle = computed(() =>
  props.scope === 'outpatient' ? '门诊耗材使用明细' : '住院耗材使用明细',
);

const defaultExportFileName = computed(() =>
  props.scope === 'outpatient' ? '门诊耗材使用明细.xlsx' : '住院耗材使用明细.xlsx',
);

const isSummaryTab = computed(() => queryParams.exportTabCode === EXPORT_TAB_SUMMARY);

const encounterNoLabel = computed(() => (props.scope === 'outpatient' ? '门诊号' : '住院号'));

/** 来源=领用时筛领用科室，否则开立科室。 */
const organizationFilterLabel = computed(() =>
  queryParams.sourceCode === SOURCE_CODE_DEPT_ISSUE ? '领用科室' : '开立科室',
);

/** 明细科室列随来源切换文案。 */
const departmentColumnLabel = computed(() =>
  queryParams.sourceCode === SOURCE_CODE_DEPT_ISSUE ? '领用科室' : '开立科室',
);

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
        label: '净数量合计',
        value: fmtReportNumber(totalNetQuantity.value, 2) || '—',
      },
      {
        label: '进价合计',
        value: formatMoneyCell(purchaseNetTotalAmount.value),
        prefix: '¥',
      },
      {
        label: '售价合计',
        value: formatMoneyCell(saleNetTotalAmount.value),
        primary: true,
        prefix: '¥',
      },
    ];
  }
  return [{ label: '明细行数', value: total.value }];
});

const { run: runInit, loading: initLoading } = useRequest(
  () => loadConsumableUsageReportPageInitApi(props.scope),
  {
    manual: true,
    onSuccess: (data) => {
      summaryDimensionOptions.value = optionDtosToSelectItems(data?.summaryDimensionOptions);
      // 去掉遗留 ALL，默认患者使用
      sourceCodeOptions.value = optionDtosToSelectItems(data?.sourceCodeOptions).filter(
        (item) => String(item.value) !== SOURCE_CODE_ALL,
      );
      locationOptions.value = optionDtosToSelectItems(data?.locationOptions);
      feeNatureOptions.value = optionDtosToSelectItems(data?.feeNatureOptions);
      organizationOptions.value = optionDtosToSelectItems(data?.organizationOptions);
      categoryOptions.value = optionDtosToSelectItems(data?.categoryOptions);
      practitionerOptions.value = optionDtosToSelectItems(data?.practitionerOptions);
      recordTypeOptions.value = optionDtosToSelectItems(data?.recordTypeOptions);
      queryParams.dateFrom =
        normalizeReportDateRangeYmd(data?.defaultDateFrom) || queryParams.dateFrom;
      queryParams.dateTo = normalizeReportDateRangeYmd(data?.defaultDateTo) || queryParams.dateTo;
      const firstDim = summaryDimensionOptions.value[0]?.value;
      if (firstDim) {
        queryParams.summaryDimension = String(firstDim);
      }
      const firstSource = sourceCodeOptions.value[0]?.value;
      if (firstSource) {
        queryParams.sourceCode = String(firstSource);
      } else {
        queryParams.sourceCode = SOURCE_CODE_PATIENT_DISPENSE;
      }
      const firstRecordType = recordTypeOptions.value[0]?.value;
      if (firstRecordType) {
        queryParams.recordTypeCode = String(firstRecordType);
      }
    },
  },
);

const { run: runSummarize, loading: summarizeLoading } = useRequest(
  (body) => summarizeConsumableUsageReportApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      summaryRows.value = data?.rows ?? [];
      totalNetQuantity.value = data?.totalNetQuantity ?? '';
      purchaseNetTotalAmount.value = data?.purchaseNetTotalAmount ?? '';
      saleNetTotalAmount.value = data?.saleNetTotalAmount ?? '';
      total.value = summaryRows.value.length;
    },
  },
);

const { run: runPageDetail, loading: detailLoading } = useRequest(
  (body) => pageConsumableUsageReportDetailApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      detailRows.value = data?.records ?? [];
      total.value = Number(data?.total ?? 0);
      // 明细分页不返回合计；页脚数量用 total，金额/数量合计置空避免误导
      totalNetQuantity.value = '';
      purchaseNetTotalAmount.value = '';
      saleNetTotalAmount.value = '';
    },
  },
);

const { run: runExport, loading: exportLoading } = useRequest(
  (body) => exportConsumableUsageReportApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      downloadBinaryExportFile(data, defaultExportFileName.value);
      if (data.truncated) {
        ElMessage.warning('导出数据已触达行数上限，部分记录未包含在文件中');
      } else {
        ElMessage.success(`导出成功，共 ${data.exportedRowCount ?? 0} 行`);
      }
    },
  },
);

const tableLoading = computed(
  () => initLoading.value || summarizeLoading.value || detailLoading.value,
);

/** 下拉选项转为 ElSelect 结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 金额单元格：空占位，其余按展示刻度。 */
function formatMoneyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return formatMoneyDisplay(value);
}

/** 数量单元格。 */
function formatQtyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return fmtReportNumber(value, 2) || String(value);
}

/** 数量 + 单位。 */
function formatQtyWithUnit(quantity: unknown, unit?: string): string {
  const qty = formatQtyCell(quantity);
  const unitText = String(unit ?? '').trim();
  if (qty === '—') {
    return unitText || '—';
  }
  return unitText ? `${qty}${unitText}` : qty;
}

/** 负数量/金额标红。 */
function resolveSignedNumberClass(value: unknown): string {
  const num = Number(value);
  if (Number.isFinite(num) && num < 0) {
    return 'text-[var(--el-color-danger)]';
  }
  return '';
}

/** 来源 Tag 样式。 */
function resolveSourceTagType(sourceCode?: string): 'info' | 'success' | 'warning' {
  if (sourceCode === 'DEPT_ISSUE') {
    return 'warning';
  }
  if (sourceCode === 'PATIENT_DISPENSE') {
    return 'success';
  }
  return 'info';
}

/** 明细行稳定 key（两路并集）。 */
function resolveDetailRowKey(row: ConsumableUsageDetailRowDto) {
  return [
    row.sourceCode ?? '',
    row.dispenseId ?? '',
    row.supplyRequestId ?? '',
    row.deviceDefId ?? '',
    row.businessTime ?? '',
  ].join('|');
}

/** 汇总行 key。 */
function resolveSummaryRowKey(row: ConsumableUsageSummaryRowDto) {
  if (row.dimensionKey) {
    return row.dimensionKey;
  }
  return [
    row.summaryDimension ?? '',
    row.patientId ?? '',
    row.organizationId ?? '',
    row.deviceDefId ?? '',
  ].join('|');
}

/** 校验日期并组装筛选体。 */
function buildFilterPayload() {
  const dateFrom = normalizeReportDateRangeYmd(queryParams.dateFrom);
  const dateTo = normalizeReportDateRangeYmd(queryParams.dateTo);
  if (!dateFrom || !dateTo) {
    ElMessage.warning('请选择业务日期');
    return null;
  }
  return {
    dateFrom,
    dateTo,
    sourceCode: queryParams.sourceCode || SOURCE_CODE_PATIENT_DISPENSE,
    locationId: preserveApiIdForRequest(queryParams.locationId),
    feeNatureContractNo: queryParams.feeNatureContractNo || undefined,
    recordTypeCode:
      queryParams.recordTypeCode ||
      (recordTypeOptions.value[0]?.value ? String(recordTypeOptions.value[0].value) : undefined),
    deviceCategoryCodes:
      queryParams.deviceCategoryCodes.length > 0 ? [...queryParams.deviceCategoryCodes] : undefined,
    performerPractitionerId: preserveApiIdForRequest(queryParams.performerPractitionerId),
    deviceKeyword: queryParams.deviceKeyword.trim() || undefined,
    patientKeyword: queryParams.patientKeyword.trim() || undefined,
    organizationId: preserveApiIdForRequest(queryParams.organizationId),
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
      summaryDimension: queryParams.summaryDimension || SUMMARY_DIMENSION_LOCATION,
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

/** 记录类型变更：清空时回落到「全部」，避免 UI 空白而后端仍按 ALL。 */
function handleRecordTypeChange(value: string | null | undefined) {
  if (!value) {
    const allCode = recordTypeOptions.value[0]?.value;
    queryParams.recordTypeCode = allCode ? String(allCode) : undefined;
  }
  handleQuery();
}

/** 来源切换：清空科室筛，避免开立/领用串用。 */
function handleSourceChange() {
  queryParams.organizationId = undefined;
  handleQuery();
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

/** 导出当前 Tab。 */
async function handleExport() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  await runExport({
    ...filters,
    exportTabCode: queryParams.exportTabCode,
    summaryDimension: isSummaryTab.value
      ? queryParams.summaryDimension || SUMMARY_DIMENSION_LOCATION
      : undefined,
  });
}

/** 重置为初始化默认值。 */
async function handleReset() {
  queryParams.pageNo = 1;
  queryParams.pageSize = 20;
  queryParams.summaryDimension = String(
    summaryDimensionOptions.value[0]?.value ?? SUMMARY_DIMENSION_LOCATION,
  );
  queryParams.sourceCode = String(
    sourceCodeOptions.value[0]?.value ?? SOURCE_CODE_PATIENT_DISPENSE,
  );
  queryParams.locationId = undefined;
  queryParams.feeNatureContractNo = undefined;
  queryParams.recordTypeCode = undefined;
  queryParams.deviceCategoryCodes = [];
  queryParams.performerPractitionerId = undefined;
  queryParams.deviceKeyword = '';
  queryParams.patientKeyword = '';
  queryParams.organizationId = undefined;
  queryParams.exportTabCode = EXPORT_TAB_DETAIL;
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
        <ReportQueryBar :model="queryParams as Record<string, unknown>">
          <template #fields>
            <ElFormItem label="业务日期">
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
            <ElFormItem label="来源">
              <ElSelect
                v-model="queryParams.sourceCode"
                :class="REPORT_QUERY_CONTROL"
                @change="handleSourceChange"
              >
                <ElOption
                  v-for="item in toSelectOptions(sourceCodeOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem :label="organizationFilterLabel">
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
            <ElFormItem label="记录类型">
              <ElSelect
                v-model="queryParams.recordTypeCode"
                clearable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleRecordTypeChange"
              >
                <ElOption
                  v-for="item in toSelectOptions(recordTypeOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="耗材房">
              <ElSelect
                v-model="queryParams.locationId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(locationOptions)"
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
                collapse-tags-tooltip
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(categoryOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="发耗材人">
              <ElSelect
                v-model="queryParams.performerPractitionerId"
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
            <ElFormItem label="耗材">
              <ElInput
                v-model="queryParams.deviceKeyword"
                clearable
                placeholder="名称/拼音/编码"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="患者">
              <ElInput
                v-model="queryParams.patientKeyword"
                clearable
                :placeholder="props.scope === 'outpatient' ? '姓名/门诊号等' : '姓名/住院号等'"
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
            <ElFormItem v-if="isSummaryTab" label="汇总维">
              <ElSelect
                v-model="queryParams.summaryDimension"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(summaryDimensionOptions)"
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
            <ElRadioGroup v-model="queryParams.exportTabCode" @change="handleTabChange">
              <ElRadioButton :value="EXPORT_TAB_SUMMARY">汇总</ElRadioButton>
              <ElRadioButton :value="EXPORT_TAB_DETAIL">明细</ElRadioButton>
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
                type="index"
                label="序号"
                width="56"
                align="center"
                header-align="center"
              />
              <ElTableColumn
                prop="dimensionName"
                label="维度"
                min-width="160"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="净数量"
                width="120"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveSignedNumberClass(row.netQuantity)">
                    {{ formatQtyCell(row.netQuantity) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="进价净额"
                width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveSignedNumberClass(row.purchaseNetAmount)">
                    {{ formatMoneyCell(row.purchaseNetAmount) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="售价净额"
                width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveSignedNumberClass(row.saleNetAmount)">
                    {{ formatMoneyCell(row.saleNetAmount) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="lineCount"
                label="明细行数"
                width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              />
            </ReportElTable>

            <ReportElTable
              v-else
              :loading="tableLoading"
              :data="detailRows"
              :height="tableHeight"
              :row-key="resolveDetailRowKey"
            >
              <ElTableColumn label="来源" width="100" align="center" header-align="center">
                <template #default="{ row }">
                  <ElTag :type="resolveSourceTagType(row.sourceCode)" effect="light">
                    {{ row.sourceCode_dictText || row.sourceCode || '—' }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="deviceName"
                label="耗材名称"
                min-width="160"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="specText"
                label="规格"
                width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="patientName"
                label="患者"
                width="90"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="encounterBusNo"
                :label="encounterNoLabel"
                width="130"
                align="center"
                header-align="center"
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
              <template v-if="scope === 'inpatient'">
                <ElTableColumn
                  prop="wardName"
                  label="病区"
                  width="100"
                  align="center"
                  header-align="center"
                  show-overflow-tooltip
                />
                <ElTableColumn
                  prop="bedName"
                  label="床号"
                  width="72"
                  align="center"
                  header-align="center"
                  show-overflow-tooltip
                />
              </template>
              <ElTableColumn
                prop="organizationName"
                :label="departmentColumnLabel"
                width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="locationName"
                label="耗材房"
                width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="statusOrTypeEnum_enumText"
                label="状态/类型"
                width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="数量"
                width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveSignedNumberClass(row.quantity)">
                    {{ formatQtyWithUnit(row.quantity, row.unitCode_dictText) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="进价单价"
                width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.purchaseUnitPrice) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="进价总价"
                width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveSignedNumberClass(row.purchaseAmount)">
                    {{ formatMoneyCell(row.purchaseAmount) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="售价单价"
                width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.saleUnitPrice) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="售价总价"
                width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveSignedNumberClass(row.saleAmount)">
                    {{ formatMoneyCell(row.saleAmount) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="lotNumber"
                label="批号"
                width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="performerName"
                label="执行人"
                width="90"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="busNo"
                label="业务单号"
                width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="businessTime"
                label="业务时间"
                width="170"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
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
