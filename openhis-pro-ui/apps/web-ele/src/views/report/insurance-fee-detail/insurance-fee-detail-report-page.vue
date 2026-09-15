<!--
  门诊/住院医保费用明细共用页（AS-RPT-012 / AS-RPT-013）。
  主类型 T2；查询即搜；金额列右对齐。
-->
<script setup lang="ts">
import type {
  InsuranceFeeDetailFooterDto,
  InsuranceFeeDetailReportScope,
  InsuranceFeeDetailRowDto,
  LoadInsuranceFeeDetailReportPageInitResponse,
} from '#/api/report/insurance-fee-detail';
import type { ApiOptionItem } from '#/types/api-response';
import type { ReportPrintColumnDef } from '#/utils/print/print-report-list';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Download, Printer, Refresh, Search, Setting } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCheckbox,
  ElDatePicker,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTableColumn,
} from 'element-plus';

import {
  CONTRACT_CATEGORY_MUNICIPAL,
  CONTRACT_CATEGORY_PROVINCIAL,
  exportInsuranceFeeDetailReportApi,
  INSUTYPE_GROUP_ALL,
  loadInsuranceFeeDetailReportPageInitApi,
  pageInsuranceFeeDetailReportApi,
} from '#/api/report/insurance-fee-detail';
import { useRequest } from '#/hooks/use-request';
import { buildExcelExportColumns } from '#/utils/export/build-excel-export-columns';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  mapReportPrintRow,
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATETIMERANGE,
  REPORT_TABLE_AMOUNT_CELL,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPageFooter,
  ReportPrintFieldDialog,
  ReportQueryBar,
  reportPrintFieldsOf,
  ReportTableScroll,
  useReportListPrint,
} from '#/views/report/shared';
import {
  buildReportDateTimeRangeShortcuts,
  normalizeReportDateTime,
  toReportApiDateTimeRangePrecise,
} from '#/views/report/shared/report-date-range-shortcuts';
import { optionDtosToSelectItems } from '#/views/report/shared/report-format';

defineOptions({ name: 'InsuranceFeeDetailReportPage' });

interface Props {
  /** 门诊或住院场景 */
  scope: InsuranceFeeDetailReportScope;
}

interface QueryFormState {
  pageNo: number;
  pageSize: number;
  dateFrom: string;
  dateTo: string;
  contractCategoryCodes: string[];
  workInjuryOnly: boolean;
  insutypeGroup: string;
  organizationId?: string;
  setlIdKeyword?: string;
  mdtrtIdKeyword?: string;
  busNoKeyword?: string;
  patientNameKeyword?: string;
}

const props = defineProps<Props>();

const pageInit = ref<LoadInsuranceFeeDetailReportPageInitResponse | null>(null);
const tableData = ref<InsuranceFeeDetailRowDto[]>([]);
const total = ref(0);
const footer = ref<InsuranceFeeDetailFooterDto | undefined>();
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);
/** init 回填日期/下拉期间忽略 @change，避免进页连打两次查询。 */
const filtersHydrating = ref(false);

const queryParams = reactive<QueryFormState>({
  pageNo: 1,
  pageSize: 20,
  dateFrom: '',
  dateTo: '',
  contractCategoryCodes: [CONTRACT_CATEGORY_PROVINCIAL, CONTRACT_CATEGORY_MUNICIPAL],
  workInjuryOnly: false,
  insutypeGroup: INSUTYPE_GROUP_ALL,
  organizationId: undefined,
  setlIdKeyword: undefined,
  mdtrtIdKeyword: undefined,
  busNoKeyword: undefined,
  patientNameKeyword: undefined,
});

const pageTitle = computed(() =>
  props.scope === 'outpatient' ? '门诊医保费用明细' : '住院医保费用明细',
);

const busNoLabel = computed(() => (props.scope === 'outpatient' ? '门诊号' : '住院号'));

const organizationLabel = computed(() => (props.scope === 'outpatient' ? '就诊科室' : '入院科室'));

const defaultExportFileName = computed(() =>
  props.scope === 'outpatient' ? '门诊医保费用明细.xlsx' : '住院医保费用明细.xlsx',
);

const workInjuryEnabled = computed(() => Boolean(pageInit.value?.workInjuryEnabled));

const organizationOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.organizationOptions ?? [],
);

const contractCategoryOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.contractCategoryOptions ?? [],
);

const insutypeGroupOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.insutypeGroupOptions ?? [],
);

const dateTimeRangeShortcuts = buildReportDateTimeRangeShortcuts();
const dateTimeDefaultTime: [Date, Date] = [
  new Date(2000, 0, 1, 0, 0, 0),
  new Date(2000, 0, 1, 23, 59, 59),
];

/** 结算时间与 datetimerange 双向绑定。 */
const businessDateRange = computed({
  get() {
    const start = normalizeReportDateTime(queryParams.dateFrom, 'start');
    const end = normalizeReportDateTime(queryParams.dateTo, 'end');
    if (start && end) {
      return [start, end] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.dateFrom = value?.[0] ? normalizeReportDateTime(value[0], 'start') || '' : '';
    queryParams.dateTo = value?.[1] ? normalizeReportDateTime(value[1], 'end') || '' : '';
  },
});

const queryBarModel = computed(() => queryParams as Record<string, unknown>);

const footerSummaryItems = computed(() => [
  { label: '笔数', value: footer.value?.lineCount ?? 0 },
  {
    label: '医疗费总额',
    value: formatMoneyCell(footer.value?.medfeeSumamtTotal),
    prefix: '¥',
  },
  {
    label: '基金支付',
    value: formatMoneyCell(footer.value?.fundPaySumamtTotal),
    prefix: '¥',
  },
  {
    label: '个账支付',
    value: formatMoneyCell(footer.value?.acctPayTotal),
    prefix: '¥',
  },
  {
    label: '现金支付',
    value: formatMoneyCell(footer.value?.psnCashPayTotal),
    primary: true,
    prefix: '¥',
  },
]);

const { run: runInit, loading: initLoading } = useRequest(
  () => loadInsuranceFeeDetailReportPageInitApi(props.scope),
  {
    manual: true,
    onSuccess: (data) => {
      pageInit.value = {
        ...data,
        organizationOptions: optionDtosToSelectItems(data?.organizationOptions),
        contractCategoryOptions: optionDtosToSelectItems(data?.contractCategoryOptions),
        insutypeGroupOptions: optionDtosToSelectItems(data?.insutypeGroupOptions),
      };
      queryParams.dateFrom =
        normalizeReportDateTime(data?.defaultDateFrom, 'start') || queryParams.dateFrom;
      queryParams.dateTo =
        normalizeReportDateTime(data?.defaultDateTo, 'end') || queryParams.dateTo;
      if (data?.defaultPageSize && data.defaultPageSize > 0) {
        queryParams.pageSize = data.defaultPageSize;
      }
    },
  },
);

const { run: runPage, loading: pageLoading } = useRequest(
  (body: Parameters<typeof pageInsuranceFeeDetailReportApi>[1]) =>
    pageInsuranceFeeDetailReportApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      tableData.value = data?.content ?? [];
      total.value = Number(data?.totalCount ?? 0);
      footer.value = data?.footer;
    },
  },
);

const { run: runExport, loading: exportLoading } = useRequest(
  (body: Parameters<typeof exportInsuranceFeeDetailReportApi>[1]) =>
    exportInsuranceFeeDetailReportApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      downloadBinaryExportFile(data, defaultExportFileName.value);
      const rowCount = data.rowCount ?? data.exportedRowCount ?? 0;
      ElMessage.success(`导出成功，共 ${rowCount} 行`);
    },
  },
);

const tableLoading = computed(
  () => initLoading.value || pageLoading.value || exportLoading.value || printSubmitting.value,
);

/** 可打印/导出列（业务号标题随门诊/住院变化）。 */
const printColumnCatalog = computed<ReportPrintColumnDef[]>(() => [
  { field: 'patientName', title: '患者姓名', align: 'center' },
  { field: 'organizationName', title: organizationLabel.value, align: 'center' },
  { field: 'busNo', title: busNoLabel.value, align: 'center' },
  { field: 'mdtrtId', title: '就医登记号', align: 'center' },
  { field: 'setlId', title: '结算ID', align: 'center' },
  { field: 'setlTime', title: '结算时间', align: 'center' },
  { field: 'medfeeSumamt', title: '医疗费总额', align: 'right' },
  { field: 'fundPaySumamt', title: '基金支付总额', align: 'right' },
  { field: 'acctPay', title: '个人账户支付', align: 'right' },
  { field: 'psnCashPay', title: '现金支付', align: 'right' },
]);

/**
 * 将当前页映射为打印单元格。
 */
function mapCurrentPrintRows(): Record<string, string>[] {
  const fields = reportPrintFieldsOf(printColumnCatalog.value);
  return tableData.value.map((row) =>
    mapReportPrintRow(row as unknown as Record<string, unknown>, fields, {
      medfeeSumamt: (value) => formatMoneyCell(value),
      fundPaySumamt: (value) => formatMoneyCell(value),
      acctPay: (value) => formatMoneyCell(value),
      psnCashPay: (value) => formatMoneyCell(value),
    }),
  );
}

const { printFieldDialogRef, printSubmitting, handleOpenPrintFields, handlePrintCurrentPage } =
  useReportListPrint({
    getReportCode: () =>
      props.scope === 'outpatient'
        ? 'report-insurance-fee-detail-op'
        : 'report-insurance-fee-detail-ip',
    getColumnCatalog: () => printColumnCatalog.value,
    getReportTitle: () => pageTitle.value,
    getPrintRows: () => mapCurrentPrintRows(),
  });

/**
 * 组装查询/导出请求体（空关键字不传；工伤与省/市互斥由 UI 保证）。
 */
function buildFilterBody() {
  // toReportApiDateTimeRangePrecise 回传 start/end（不是 dateFrom/dateTo）
  const range = toReportApiDateTimeRangePrecise(queryParams.dateFrom, queryParams.dateTo);
  const body: Record<string, unknown> = {
    dateFrom: range.start,
    dateTo: range.end,
    workInjuryOnly: queryParams.workInjuryOnly,
    insutypeGroup: queryParams.insutypeGroup || INSUTYPE_GROUP_ALL,
  };
  // 工伤：不传省/市；否则传多选类别
  if (queryParams.workInjuryOnly) {
    body.workInjuryOnly = true;
  } else if (queryParams.contractCategoryCodes.length > 0) {
    body.contractCategoryCodes = [...queryParams.contractCategoryCodes];
  }
  const orgId = preserveApiIdForRequest(queryParams.organizationId);
  if (orgId) {
    body.organizationId = orgId;
  }
  const setlId = queryParams.setlIdKeyword?.trim();
  if (setlId) {
    body.setlIdKeyword = setlId;
  }
  const mdtrtId = queryParams.mdtrtIdKeyword?.trim();
  if (mdtrtId) {
    body.mdtrtIdKeyword = mdtrtId;
  }
  const busNo = queryParams.busNoKeyword?.trim();
  if (busNo) {
    body.busNoKeyword = busNo;
  }
  const patientName = queryParams.patientNameKeyword?.trim();
  if (patientName) {
    body.patientNameKeyword = patientName;
  }
  return body;
}

/**
 * 校验时间窗与费用类别后查询第一页。
 */
function handleQuery() {
  // init 回填中：不查，等 onMounted / 重置完成后再主动查
  if (filtersHydrating.value) {
    return;
  }
  if (!queryParams.dateFrom || !queryParams.dateTo) {
    ElMessage.warning('请选择有效结算时间');
    return;
  }
  if (!queryParams.workInjuryOnly && queryParams.contractCategoryCodes.length === 0) {
    ElMessage.warning('请选择省医保/市医保，或勾选工伤');
    return;
  }
  queryParams.pageNo = 1;
  void loadPage();
}

/**
 * 拉取当前页。
 */
async function loadPage() {
  await runPage({
    ...buildFilterBody(),
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
  } as Parameters<typeof pageInsuranceFeeDetailReportApi>[1]);
  await nextTick();
  tableScrollRef.value?.scrollToTop?.();
}

/**
 * 重置筛选为 init 默认并查询。
 */
async function handleReset() {
  filtersHydrating.value = true;
  queryParams.contractCategoryCodes = [CONTRACT_CATEGORY_PROVINCIAL, CONTRACT_CATEGORY_MUNICIPAL];
  queryParams.workInjuryOnly = false;
  queryParams.insutypeGroup = INSUTYPE_GROUP_ALL;
  queryParams.organizationId = undefined;
  queryParams.setlIdKeyword = undefined;
  queryParams.mdtrtIdKeyword = undefined;
  queryParams.busNoKeyword = undefined;
  queryParams.patientNameKeyword = undefined;
  queryParams.pageNo = 1;
  try {
    await runInit();
  } finally {
    filtersHydrating.value = false;
  }
  handleQuery();
}

/**
 * 导出当前筛选（列标题随门诊/住院业务号文案）。
 */
function handleExport() {
  if (!queryParams.dateFrom || !queryParams.dateTo) {
    ElMessage.warning('请选择有效结算时间');
    return;
  }
  if (!queryParams.workInjuryOnly && queryParams.contractCategoryCodes.length === 0) {
    ElMessage.warning('请选择省医保/市医保，或勾选工伤');
    return;
  }
  const exportColumns = buildExcelExportColumns(printColumnCatalog.value);
  void runExport({
    ...buildFilterBody(),
    exportColumns,
  } as Parameters<typeof exportInsuranceFeeDetailReportApi>[1]);
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
 * 勾选工伤：清空省/市并查询。
 */
function handleWorkInjuryChange(checked: boolean | string | number) {
  const on = Boolean(checked);
  queryParams.workInjuryOnly = on;
  // 工伤与省/市互斥：开启工伤时清空类别多选
  if (on) {
    queryParams.contractCategoryCodes = [];
  } else if (queryParams.contractCategoryCodes.length === 0) {
    queryParams.contractCategoryCodes = [CONTRACT_CATEGORY_PROVINCIAL, CONTRACT_CATEGORY_MUNICIPAL];
  }
  handleQuery();
}

/**
 * 省/市多选变更：有选中时关闭工伤。
 */
function handleContractCategoryChange(codes: string[]) {
  queryParams.contractCategoryCodes = codes ?? [];
  // 选了省/市则关闭工伤
  if (queryParams.contractCategoryCodes.length > 0) {
    queryParams.workInjuryOnly = false;
  }
  handleQuery();
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
 * 行稳定主键。
 */
function resolveRowKey(row: InsuranceFeeDetailRowDto) {
  return String(row.settlementRecordId ?? `${row.setlId ?? ''}-${row.mdtrtId ?? ''}`);
}

onMounted(async () => {
  filtersHydrating.value = true;
  try {
    await runInit();
  } finally {
    filtersHydrating.value = false;
  }
  handleQuery();
});
</script>

<template>
  <Page auto-content-height>
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryBarModel">
          <template #fields>
            <ElFormItem label="结算时间" required>
              <ElDatePicker
                v-model="businessDateRange"
                type="datetimerange"
                value-format="YYYY-MM-DD HH:mm:ss"
                :default-time="dateTimeDefaultTime"
                :shortcuts="dateTimeRangeShortcuts"
                :clearable="false"
                :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATETIMERANGE]"
                @change="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="费用类别">
              <ElSelect
                v-model="queryParams.contractCategoryCodes"
                multiple
                clearable
                collapse-tags
                collapse-tags-tooltip
                placeholder="省/市医保"
                :disabled="queryParams.workInjuryOnly"
                :class="REPORT_QUERY_CONTROL"
                @change="handleContractCategoryChange"
              >
                <ElOption
                  v-for="item in contractCategoryOptions"
                  :key="String(item.value)"
                  :label="item.label"
                  :value="String(item.value)"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem v-if="workInjuryEnabled" label="工伤">
              <ElCheckbox
                :model-value="queryParams.workInjuryOnly"
                @change="handleWorkInjuryChange"
              >
                仅工伤
              </ElCheckbox>
            </ElFormItem>
            <ElFormItem label="职工/居民">
              <ElSelect
                v-model="queryParams.insutypeGroup"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in insutypeGroupOptions"
                  :key="String(item.value)"
                  :label="item.label"
                  :value="String(item.value)"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem :label="organizationLabel">
              <ElSelect
                v-model="queryParams.organizationId"
                clearable
                filterable
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in organizationOptions"
                  :key="String(item.value)"
                  :label="item.label"
                  :value="String(item.value)"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="结算ID">
              <ElInput
                v-model="queryParams.setlIdKeyword"
                clearable
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="就医登记号">
              <ElInput
                v-model="queryParams.mdtrtIdKeyword"
                clearable
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem :label="busNoLabel">
              <ElInput
                v-model="queryParams.busNoKeyword"
                clearable
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="患者姓名">
              <ElInput
                v-model="queryParams.patientNameKeyword"
                clearable
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
          </template>
        </ReportQueryBar>
      </template>

      <template #toolbar>
        <ReportListToolbar :title="pageTitle" :total="total">
          <template #actions>
            <ElButton :icon="Search" type="primary" @click="handleQuery">查询</ElButton>
            <ElButton :icon="Refresh" @click="handleReset">重置</ElButton>
            <ElButton :icon="Download" :loading="exportLoading" @click="handleExport">
              导出
            </ElButton>
            <ElButton :icon="Printer" :loading="printSubmitting" @click="handlePrintCurrentPage">
              打印
            </ElButton>
            <ElButton :icon="Setting" @click="handleOpenPrintFields">打印字段</ElButton>
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
              prop="patientName"
              label="患者姓名"
              align="center"
              header-align="center"
              min-width="90"
              show-overflow-tooltip
            />
            <ElTableColumn
              prop="organizationName"
              :label="organizationLabel"
              align="center"
              header-align="center"
              min-width="100"
              show-overflow-tooltip
            />
            <ElTableColumn
              prop="busNo"
              :label="busNoLabel"
              align="center"
              header-align="center"
              min-width="140"
              show-overflow-tooltip
            />
            <ElTableColumn
              prop="mdtrtId"
              label="就医登记号"
              align="center"
              header-align="center"
              min-width="180"
              show-overflow-tooltip
            />
            <ElTableColumn
              prop="setlId"
              label="结算ID"
              align="center"
              header-align="center"
              min-width="180"
              show-overflow-tooltip
            />
            <ElTableColumn
              prop="setlTime"
              label="结算时间"
              align="center"
              header-align="center"
              min-width="160"
              show-overflow-tooltip
            />
            <ElTableColumn
              prop="medfeeSumamt"
              label="医疗费总额"
              align="right"
              header-align="right"
              min-width="110"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <span :class="REPORT_TABLE_AMOUNT_CELL">
                  {{ formatMoneyCell(row.medfeeSumamt) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="fundPaySumamt"
              label="基金支付总额"
              align="right"
              header-align="right"
              min-width="110"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <span :class="REPORT_TABLE_AMOUNT_CELL">
                  {{ formatMoneyCell(row.fundPaySumamt) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="acctPay"
              label="个人账户支付"
              align="right"
              header-align="right"
              min-width="110"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <span :class="REPORT_TABLE_AMOUNT_CELL">
                  {{ formatMoneyCell(row.acctPay) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="psnCashPay"
              label="现金支付"
              align="right"
              header-align="right"
              min-width="110"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <span :class="REPORT_TABLE_AMOUNT_CELL">
                  {{ formatMoneyCell(row.psnCashPay) }}
                </span>
              </template>
            </ElTableColumn>
          </ReportElTable>
        </ReportTableScroll>
      </template>

      <template #footer>
        <ReportPageFooter
          :summary-items="footerSummaryItems"
          :current-page="queryParams.pageNo"
          :page-size="queryParams.pageSize"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </template>
    </ReportListPageLayout>

    <ReportPrintFieldDialog ref="printFieldDialogRef" />
  </Page>
</template>
