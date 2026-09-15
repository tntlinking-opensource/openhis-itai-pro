<!--
  门诊处方抽查：西药及中成药 / 中药饮片两套列；开方日期窗查询与导出。
  主类型 T2；查询即搜；金额/数量列右对齐。
-->
<script setup lang="ts">
import type {
  LoadPrescriptionSpotCheckReportPageInitResponse,
  PrescriptionSpotCheckHerbalRowDto,
  PrescriptionSpotCheckWesternRowDto,
} from '#/api/report/prescription-spot-check';
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
  exportPrescriptionSpotCheckReportApi,
  loadPrescriptionSpotCheckReportPageInitApi,
  pagePrescriptionSpotCheckReportApi,
  REPORT_TYPE_HERBAL,
  REPORT_TYPE_WESTERN_PATENT,
} from '#/api/report/prescription-spot-check';
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

defineOptions({ name: 'PrescriptionSpotCheckReportPage' });

interface QueryFormState {
  pageNo: number;
  pageSize: number;
  reportType: string;
  openedDateFrom: string;
  openedDateTo: string;
}

/** 表格行：西药或饮片字段并集。 */
type SpotCheckTableRow = PrescriptionSpotCheckWesternRowDto | PrescriptionSpotCheckHerbalRowDto;

const PAGE_TITLE = '处方抽查';

const pageInit = ref<LoadPrescriptionSpotCheckReportPageInitResponse | null>(null);
const tableData = ref<SpotCheckTableRow[]>([]);
const total = ref(0);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const queryParams = reactive<QueryFormState>({
  pageNo: 1,
  pageSize: 20,
  reportType: REPORT_TYPE_WESTERN_PATENT,
  openedDateFrom: '',
  openedDateTo: '',
});

const reportTypeOptions = computed<ApiOptionItem[]>(() => pageInit.value?.reportTypeOptions ?? []);

const isHerbal = computed(() => queryParams.reportType === REPORT_TYPE_HERBAL);

const dateRangeShortcuts = buildReportDateRangeShortcuts();

/** 开方日期与 daterange 双向绑定。 */
const openedDateRange = computed({
  get() {
    const start = normalizeReportDateRangeYmd(queryParams.openedDateFrom);
    const end = normalizeReportDateRangeYmd(queryParams.openedDateTo);
    if (start && end) {
      return [start, end] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.openedDateFrom = value?.[0] ?? '';
    queryParams.openedDateTo = value?.[1] ?? '';
  },
});

const queryBarModel = computed(() => queryParams as Record<string, unknown>);

/** 西药及中成药可见列（金额/剂量/发药数量右对齐）。 */
const westernColumnCatalog: ReportPrintColumnDef[] = [
  { field: 'orgName', title: '医疗机构名称', align: 'center' },
  { field: 'orgLicenseNo', title: '医疗机构执业许可证登记号', align: 'center' },
  { field: 'prescriptionNo', title: '处方编号', align: 'center' },
  { field: 'prescriptionAmount', title: '处方金额（元）', align: 'right' },
  { field: 'patientBusNo', title: '患者唯一标识', align: 'center' },
  { field: 'genderText', title: '性别', align: 'center' },
  { field: 'birthDate', title: '出生日期', align: 'center' },
  { field: 'openedAt', title: '处方开立日期', align: 'center' },
  { field: 'visitTypeText', title: '患者就诊类型', align: 'center' },
  { field: 'deptName', title: '处方开立科室名称', align: 'center' },
  { field: 'deptYbNo', title: '处方开立科室代码', align: 'center' },
  { field: 'practitionerYbNo', title: '处方开立医师代码', align: 'center' },
  { field: 'diagnosisName', title: '诊断名称', align: 'center' },
  { field: 'diagnosisCode', title: '诊断编码', align: 'center' },
  { field: 'drugBusNo', title: '药品编码', align: 'center' },
  { field: 'drugYbNo', title: '药品医保信息业务编码', align: 'center' },
  { field: 'drugYbName', title: '药品名称（医保名）', align: 'center' },
  { field: 'doseFormText', title: '剂型', align: 'center' },
  { field: 'specification', title: '规格', align: 'center' },
  { field: 'packagingText', title: '包装', align: 'center' },
  { field: 'methodText', title: '给药途径', align: 'center' },
  { field: 'rateText', title: '频次', align: 'center' },
  { field: 'doseQuantity', title: '单次剂量', align: 'right' },
  { field: 'doseUnitText', title: '单次剂量单位', align: 'center' },
  { field: 'dispenseQuantity', title: '发药数量', align: 'right' },
  { field: 'dispenseUnitText', title: '发药数量单位', align: 'center' },
  { field: 'drugOrder', title: '药品顺序', align: 'center' },
];

/** 中药饮片可见列（金额/剂数/每剂用量右对齐）。 */
const herbalColumnCatalog: ReportPrintColumnDef[] = [
  { field: 'orgName', title: '医疗机构名称', align: 'center' },
  { field: 'orgLicenseNo', title: '医疗机构执业许可证登记号', align: 'center' },
  { field: 'prescriptionNo', title: '处方编号', align: 'center' },
  { field: 'prescriptionAmount', title: '处方金额（元）', align: 'right' },
  { field: 'patientBusNo', title: '患者唯一标识', align: 'center' },
  { field: 'genderText', title: '性别', align: 'center' },
  { field: 'birthDate', title: '出生日期', align: 'center' },
  { field: 'openedAt', title: '处方开立日期', align: 'center' },
  { field: 'visitTypeText', title: '患者就诊类型', align: 'center' },
  { field: 'deptName', title: '处方开立科室名称', align: 'center' },
  { field: 'deptYbNo', title: '处方开立科室代码', align: 'center' },
  { field: 'practitionerYbNo', title: '处方开立医师代码', align: 'center' },
  { field: 'tcmDiseaseName', title: '门（急）诊中医疾病诊断名称', align: 'center' },
  { field: 'tcmDiseaseCode', title: '编码', align: 'center' },
  { field: 'tcmSyndromeName', title: '证候名称', align: 'center' },
  { field: 'tcmSyndromeCode', title: '证候编码', align: 'center' },
  { field: 'drugName', title: '药品名称', align: 'center' },
  { field: 'drugBusNo', title: '药品编码', align: 'center' },
  { field: 'drugYbNo', title: '药品医保信息业务编码', align: 'center' },
  { field: 'doseCount', title: '剂数', align: 'right' },
  { field: 'prepareMethodText', title: '制备方法', align: 'center' },
  { field: 'methodText', title: '给药途径', align: 'center' },
  { field: 'dosePerUnit', title: '每剂用量', align: 'right' },
  { field: 'doseUnitText', title: '每剂用量单位', align: 'center' },
  { field: 'footnoteText', title: '脚注', align: 'center' },
  { field: 'drugOrder', title: '药品顺序', align: 'center' },
];

/** 当前类型导出列目录。 */
const exportColumnCatalog = computed(() =>
  isHerbal.value ? herbalColumnCatalog : westernColumnCatalog,
);

const defaultExportFileName = computed(() =>
  isHerbal.value ? '门诊处方抽查-中药饮片.xlsx' : '门诊处方抽查-西药及中成药.xlsx',
);

const { run: runInit, loading: initLoading } = useRequest(
  loadPrescriptionSpotCheckReportPageInitApi,
  {
    manual: true,
    onSuccess: (data) => {
      pageInit.value = {
        ...data,
        reportTypeOptions: optionDtosToSelectItems(data?.reportTypeOptions),
      };
      const defaultType = String(data?.defaultReportType ?? '').trim();
      if (defaultType === REPORT_TYPE_HERBAL || defaultType === REPORT_TYPE_WESTERN_PATENT) {
        queryParams.reportType = defaultType;
      } else {
        queryParams.reportType = REPORT_TYPE_WESTERN_PATENT;
      }
      queryParams.openedDateFrom =
        normalizeReportDateRangeYmd(data?.defaultOpenedDateFrom) || queryParams.openedDateFrom;
      queryParams.openedDateTo =
        normalizeReportDateRangeYmd(data?.defaultOpenedDateTo) || queryParams.openedDateTo;
      if (data?.defaultPageSize && data.defaultPageSize > 0) {
        queryParams.pageSize = data.defaultPageSize;
      }
    },
  },
);

const { run: runPage, loading: pageLoading } = useRequest(pagePrescriptionSpotCheckReportApi, {
  manual: true,
  onSuccess: (data) => {
    // 按类型取互斥明细；content 后端留空
    if (data?.reportType === REPORT_TYPE_HERBAL || queryParams.reportType === REPORT_TYPE_HERBAL) {
      tableData.value = data?.herbalContent ?? [];
    } else {
      tableData.value = data?.westernContent ?? [];
    }
    total.value = Number(data?.totalCount ?? 0);
  },
});

const { run: runExport, loading: exportLoading } = useRequest(
  exportPrescriptionSpotCheckReportApi,
  {
    manual: true,
    onSuccess: (data) => {
      downloadBinaryExportFile(data, defaultExportFileName.value);
      const rowCount = data.rowCount ?? data.exportedRowCount ?? 0;
      ElMessage.success(`导出成功，共 ${rowCount} 行`);
    },
  },
);

const tableLoading = computed(() => initLoading.value || pageLoading.value || exportLoading.value);

/**
 * 组装查询/导出筛选体。
 */
function buildFilterBody() {
  return {
    reportType: queryParams.reportType,
    openedDateFrom: normalizeReportDateRangeYmd(queryParams.openedDateFrom) || '',
    openedDateTo: normalizeReportDateRangeYmd(queryParams.openedDateTo) || '',
  };
}

/**
 * 校验类型与开方日期后查询第一页。
 */
function handleQuery() {
  if (!queryParams.reportType) {
    ElMessage.warning('请选择报表类型');
    return;
  }
  if (!queryParams.openedDateFrom || !queryParams.openedDateTo) {
    ElMessage.warning('请选择开方日期');
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
 * 重置为 init 默认并查询。
 */
async function handleReset() {
  queryParams.pageNo = 1;
  await runInit();
  handleQuery();
}

/**
 * 导出当前类型可见列。
 */
function handleExport() {
  if (!queryParams.reportType) {
    ElMessage.warning('请选择报表类型');
    return;
  }
  if (!queryParams.openedDateFrom || !queryParams.openedDateTo) {
    ElMessage.warning('请选择开方日期');
    return;
  }
  const exportColumns = buildExcelExportColumns(exportColumnCatalog.value);
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
 * 行稳定主键（处方号 + 药品顺序 + 药品编码）。
 */
function resolveRowKey(row: SpotCheckTableRow, index: number) {
  return `${row.prescriptionNo ?? ''}-${row.drugOrder ?? ''}-${row.drugBusNo ?? ''}-${index}`;
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
            <ElFormItem label="报表类型" required>
              <ElSelect
                v-model="queryParams.reportType"
                :clearable="false"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in reportTypeOptions"
                  :key="String(item.value)"
                  :label="item.label"
                  :value="String(item.value)"
                />
                <!-- init 未回填时兜底两项，避免空下拉 -->
                <template v-if="reportTypeOptions.length === 0">
                  <ElOption label="西药及中成药" :value="REPORT_TYPE_WESTERN_PATENT" />
                  <ElOption label="中药饮片" :value="REPORT_TYPE_HERBAL" />
                </template>
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="开方日期" required>
              <ElDatePicker
                v-model="openedDateRange"
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
          <template #default="{ tableHeight }">
            <ReportElTable
              :data="tableData"
              :loading="tableLoading"
              :height="tableHeight"
              :row-key="resolveRowKey"
              border
              stripe
            >
              <!-- 西药及中成药列 -->
              <template v-if="!isHerbal">
                <ElTableColumn
                  prop="orgName"
                  label="医疗机构名称"
                  align="center"
                  header-align="center"
                  min-width="140"
                />
                <ElTableColumn
                  prop="orgLicenseNo"
                  label="医疗机构执业许可证登记号"
                  align="center"
                  header-align="center"
                  min-width="180"
                />
                <ElTableColumn
                  prop="prescriptionNo"
                  label="处方编号"
                  align="center"
                  header-align="center"
                  min-width="140"
                />
                <ElTableColumn
                  prop="prescriptionAmount"
                  label="处方金额（元）"
                  align="right"
                  header-align="right"
                  min-width="120"
                >
                  <template #default="{ row }">
                    <span :class="REPORT_TABLE_AMOUNT_CELL">
                      {{ formatMoneyCell(row.prescriptionAmount) }}
                    </span>
                  </template>
                </ElTableColumn>
                <ElTableColumn
                  prop="patientBusNo"
                  label="患者唯一标识"
                  align="center"
                  header-align="center"
                  min-width="120"
                />
                <ElTableColumn
                  prop="genderText"
                  label="性别"
                  align="center"
                  header-align="center"
                  min-width="70"
                />
                <ElTableColumn
                  prop="birthDate"
                  label="出生日期"
                  align="center"
                  header-align="center"
                  min-width="110"
                />
                <ElTableColumn
                  prop="openedAt"
                  label="处方开立日期"
                  align="center"
                  header-align="center"
                  min-width="160"
                />
                <ElTableColumn
                  prop="visitTypeText"
                  label="患者就诊类型"
                  align="center"
                  header-align="center"
                  min-width="110"
                />
                <ElTableColumn
                  prop="deptName"
                  label="处方开立科室名称"
                  align="center"
                  header-align="center"
                  min-width="130"
                />
                <ElTableColumn
                  prop="deptYbNo"
                  label="处方开立科室代码"
                  align="center"
                  header-align="center"
                  min-width="120"
                />
                <ElTableColumn
                  prop="practitionerYbNo"
                  label="处方开立医师代码"
                  align="center"
                  header-align="center"
                  min-width="130"
                />
                <ElTableColumn
                  prop="diagnosisName"
                  label="诊断名称"
                  align="center"
                  header-align="center"
                  min-width="140"
                />
                <ElTableColumn
                  prop="diagnosisCode"
                  label="诊断编码"
                  align="center"
                  header-align="center"
                  min-width="120"
                />
                <ElTableColumn
                  prop="drugBusNo"
                  label="药品编码"
                  align="center"
                  header-align="center"
                  min-width="120"
                />
                <ElTableColumn
                  prop="drugYbNo"
                  label="药品医保信息业务编码"
                  align="center"
                  header-align="center"
                  min-width="160"
                />
                <ElTableColumn
                  prop="drugYbName"
                  label="药品名称（医保名）"
                  align="center"
                  header-align="center"
                  min-width="140"
                />
                <ElTableColumn
                  prop="doseFormText"
                  label="剂型"
                  align="center"
                  header-align="center"
                  min-width="90"
                />
                <ElTableColumn
                  prop="specification"
                  label="规格"
                  align="center"
                  header-align="center"
                  min-width="100"
                />
                <ElTableColumn
                  prop="packagingText"
                  label="包装"
                  align="center"
                  header-align="center"
                  min-width="100"
                />
                <ElTableColumn
                  prop="methodText"
                  label="给药途径"
                  align="center"
                  header-align="center"
                  min-width="100"
                />
                <ElTableColumn
                  prop="rateText"
                  label="频次"
                  align="center"
                  header-align="center"
                  min-width="90"
                />
                <ElTableColumn
                  prop="doseQuantity"
                  label="单次剂量"
                  align="right"
                  header-align="right"
                  min-width="100"
                >
                  <template #default="{ row }">
                    <span :class="REPORT_TABLE_NUM_CELL">
                      {{ formatQtyCell(row.doseQuantity) }}
                    </span>
                  </template>
                </ElTableColumn>
                <ElTableColumn
                  prop="doseUnitText"
                  label="单次剂量单位"
                  align="center"
                  header-align="center"
                  min-width="110"
                />
                <ElTableColumn
                  prop="dispenseQuantity"
                  label="发药数量"
                  align="right"
                  header-align="right"
                  min-width="100"
                >
                  <template #default="{ row }">
                    <span :class="REPORT_TABLE_NUM_CELL">
                      {{ formatQtyCell(row.dispenseQuantity) }}
                    </span>
                  </template>
                </ElTableColumn>
                <ElTableColumn
                  prop="dispenseUnitText"
                  label="发药数量单位"
                  align="center"
                  header-align="center"
                  min-width="110"
                />
                <ElTableColumn
                  prop="drugOrder"
                  label="药品顺序"
                  align="center"
                  header-align="center"
                  min-width="90"
                />
              </template>

              <!-- 中药饮片列 -->
              <template v-else>
                <ElTableColumn
                  prop="orgName"
                  label="医疗机构名称"
                  align="center"
                  header-align="center"
                  min-width="140"
                />
                <ElTableColumn
                  prop="orgLicenseNo"
                  label="医疗机构执业许可证登记号"
                  align="center"
                  header-align="center"
                  min-width="180"
                />
                <ElTableColumn
                  prop="prescriptionNo"
                  label="处方编号"
                  align="center"
                  header-align="center"
                  min-width="140"
                />
                <ElTableColumn
                  prop="prescriptionAmount"
                  label="处方金额（元）"
                  align="right"
                  header-align="right"
                  min-width="120"
                >
                  <template #default="{ row }">
                    <span :class="REPORT_TABLE_AMOUNT_CELL">
                      {{ formatMoneyCell(row.prescriptionAmount) }}
                    </span>
                  </template>
                </ElTableColumn>
                <ElTableColumn
                  prop="patientBusNo"
                  label="患者唯一标识"
                  align="center"
                  header-align="center"
                  min-width="120"
                />
                <ElTableColumn
                  prop="genderText"
                  label="性别"
                  align="center"
                  header-align="center"
                  min-width="70"
                />
                <ElTableColumn
                  prop="birthDate"
                  label="出生日期"
                  align="center"
                  header-align="center"
                  min-width="110"
                />
                <ElTableColumn
                  prop="openedAt"
                  label="处方开立日期"
                  align="center"
                  header-align="center"
                  min-width="160"
                />
                <ElTableColumn
                  prop="visitTypeText"
                  label="患者就诊类型"
                  align="center"
                  header-align="center"
                  min-width="110"
                />
                <ElTableColumn
                  prop="deptName"
                  label="处方开立科室名称"
                  align="center"
                  header-align="center"
                  min-width="130"
                />
                <ElTableColumn
                  prop="deptYbNo"
                  label="处方开立科室代码"
                  align="center"
                  header-align="center"
                  min-width="120"
                />
                <ElTableColumn
                  prop="practitionerYbNo"
                  label="处方开立医师代码"
                  align="center"
                  header-align="center"
                  min-width="130"
                />
                <ElTableColumn
                  prop="tcmDiseaseName"
                  label="门（急）诊中医疾病诊断名称"
                  align="center"
                  header-align="center"
                  min-width="180"
                />
                <ElTableColumn
                  prop="tcmDiseaseCode"
                  label="编码"
                  align="center"
                  header-align="center"
                  min-width="110"
                />
                <ElTableColumn
                  prop="tcmSyndromeName"
                  label="证候名称"
                  align="center"
                  header-align="center"
                  min-width="120"
                />
                <ElTableColumn
                  prop="tcmSyndromeCode"
                  label="证候编码"
                  align="center"
                  header-align="center"
                  min-width="110"
                />
                <ElTableColumn
                  prop="drugName"
                  label="药品名称"
                  align="center"
                  header-align="center"
                  min-width="120"
                />
                <ElTableColumn
                  prop="drugBusNo"
                  label="药品编码"
                  align="center"
                  header-align="center"
                  min-width="120"
                />
                <ElTableColumn
                  prop="drugYbNo"
                  label="药品医保信息业务编码"
                  align="center"
                  header-align="center"
                  min-width="160"
                />
                <ElTableColumn
                  prop="doseCount"
                  label="剂数"
                  align="right"
                  header-align="right"
                  min-width="80"
                >
                  <template #default="{ row }">
                    <span :class="REPORT_TABLE_NUM_CELL">
                      {{ formatQtyCell(row.doseCount) }}
                    </span>
                  </template>
                </ElTableColumn>
                <ElTableColumn
                  prop="prepareMethodText"
                  label="制备方法"
                  align="center"
                  header-align="center"
                  min-width="100"
                />
                <ElTableColumn
                  prop="methodText"
                  label="给药途径"
                  align="center"
                  header-align="center"
                  min-width="100"
                />
                <ElTableColumn
                  prop="dosePerUnit"
                  label="每剂用量"
                  align="right"
                  header-align="right"
                  min-width="100"
                >
                  <template #default="{ row }">
                    <span :class="REPORT_TABLE_NUM_CELL">
                      {{ formatQtyCell(row.dosePerUnit) }}
                    </span>
                  </template>
                </ElTableColumn>
                <ElTableColumn
                  prop="doseUnitText"
                  label="每剂用量单位"
                  align="center"
                  header-align="center"
                  min-width="110"
                />
                <ElTableColumn
                  prop="footnoteText"
                  label="脚注"
                  align="center"
                  header-align="center"
                  min-width="100"
                />
                <ElTableColumn
                  prop="drugOrder"
                  label="药品顺序"
                  align="center"
                  header-align="center"
                  min-width="90"
                />
              </template>
            </ReportElTable>
          </template>
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
