<!--
  处方点评记录：筛选 + 工作表同列列表 + 当前页导出；详情复用门诊处方查看抽屉（仅处方信息）。
  主类型：T2 纯展示查询列表；本页无点评/撤销写入口。
-->
<script setup lang="ts">
import type {
  PagePrescriptionReviewsRequest,
  PrescriptionReviewListRow,
} from '#/api/pharmacy/prescription-review';
import type { OutpatientPrescriptionSummaryRow } from '#/api/report/outpatient-prescription-view';
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
  ElSelect,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  exportPrescriptionReviewsApi,
  loadPrescriptionReviewPageInitApi,
  pagePrescriptionReviewsApi,
} from '#/api/pharmacy/prescription-review';
import { useRequest } from '#/hooks/use-request';
import { buildExcelExportColumns } from '#/utils/export/build-excel-export-columns';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import OutpatientPrescriptionViewDetailDrawer from '#/views/report/outpatient-prescription-view/components/outpatient-prescription-view-detail-drawer.vue';
import {
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATERANGE,
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

defineOptions({ name: 'PrescriptionReviewReportPage' });

interface QueryState {
  pageNo: number;
  pageSize: number;
  reviewedDateFrom?: string;
  reviewedDateTo?: string;
  openedDateFrom?: string;
  openedDateTo?: string;
  /** 筛选项 value 为 "true"/"false" 字符串；空=不限 */
  reasonableValue?: string;
  /** 重点药类型 KeyDrugType code；空=不限 */
  keyDrugType?: string;
  /** 药品性质 KeyDrugType code；空=不限 */
  medicinePropertyCode?: string;
  /** 处方号或点评业务号关键字 */
  keyword?: string;
}

const PAGE_TITLE = '处方点评记录';

/**
 * 导出列与工作表数据列一致（无序号；field 落在后端 @Excel 白名单）。
 */
const EXPORT_COLUMNS = [
  { field: 'prescriptionNo', title: '处方编号' },
  { field: 'patientAgeText', title: '年龄(岁)' },
  { field: 'diagnosisName', title: '诊断' },
  { field: 'drugVarietyCount', title: '药品品种' },
  { field: 'antibioticFlag', title: '抗菌药(0/1)' },
  { field: 'injectFlag', title: '注射剂(0/1)' },
  { field: 'basicDrugVarietyCount', title: '国家基本药物品种数' },
  { field: 'genericNameCount', title: '药品通用名数' },
  { field: 'prescriptionAmount', title: '处方金额(元)' },
  { field: 'orderingPractitionerName', title: '处方医师' },
  { field: 'reasonableFlag', title: '是否合理(0/1)' },
  { field: 'reason', title: '存在问题' },
];

const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);
const detailDrawerRef = ref<InstanceType<typeof OutpatientPrescriptionViewDetailDrawer> | null>(
  null,
);
const tableData = ref<PrescriptionReviewListRow[]>([]);
const total = ref(0);

const reasonableOptions = ref<ApiOptionItem[]>([]);
const keyDrugTypeOptions = ref<ApiOptionItem[]>([]);
const medicinePropertyOptions = ref<ApiOptionItem[]>([]);

const queryParams = reactive<QueryState>({
  pageNo: 1,
  pageSize: 50,
});

const tableLoading = computed(() => initLoading.value || pageLoading.value || exportLoading.value);

const dateRangeShortcuts = buildReportDateRangeShortcuts();

/** 点评日与 daterange 双向绑定。 */
const reviewedDateRange = computed({
  get() {
    const start = normalizeReportDateRangeYmd(queryParams.reviewedDateFrom);
    const end = normalizeReportDateRangeYmd(queryParams.reviewedDateTo);
    if (start && end) {
      return [start, end] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.reviewedDateFrom = value?.[0];
    queryParams.reviewedDateTo = value?.[1];
  },
});

/** 开方日与 daterange 双向绑定。 */
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
    queryParams.openedDateFrom = value?.[0];
    queryParams.openedDateTo = value?.[1];
  },
});

/** 下拉选项转为 ElSelect 结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 行键。 */
function rowKey(row: PrescriptionReviewListRow) {
  return String(row.id ?? row.prescriptionNo ?? '');
}

/** 0/1 标志展示；空为 —。 */
function formatZeroOne(value?: number | null) {
  if (value === 0 || value === 1) {
    return String(value);
  }
  return '—';
}

/** 金额展示。 */
function formatAmount(value?: number | string | null) {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return num.toFixed(2);
}

/** 是否合理筛选项 → boolean | undefined。 */
function resolveReasonableFlag(): boolean | undefined {
  const raw = String(queryParams.reasonableValue ?? '').trim();
  if (raw === 'true') {
    return true;
  }
  if (raw === 'false') {
    return false;
  }
  return undefined;
}

/** 组装筛选请求体（不含分页）。 */
function buildFilterBody(): Omit<PagePrescriptionReviewsRequest, 'pageNo' | 'pageSize'> {
  return {
    reviewedDateFrom: queryParams.reviewedDateFrom || undefined,
    reviewedDateTo: queryParams.reviewedDateTo || undefined,
    openedDateFrom: queryParams.openedDateFrom || undefined,
    openedDateTo: queryParams.openedDateTo || undefined,
    reasonableFlag: resolveReasonableFlag(),
    keyDrugType: queryParams.keyDrugType || undefined,
    medicinePropertyCode: queryParams.medicinePropertyCode || undefined,
    keyword: queryParams.keyword?.trim() || undefined,
  };
}

/** 组装分页请求体。 */
function buildPageRequest(): PagePrescriptionReviewsRequest {
  return {
    ...buildFilterBody(),
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
  };
}

const { run: runInit, loading: initLoading } = useRequest(loadPrescriptionReviewPageInitApi, {
  manual: true,
  onSuccess: (data) => {
    queryParams.reviewedDateFrom =
      normalizeReportDateRangeYmd(data?.defaultReviewedDateFrom) || queryParams.reviewedDateFrom;
    queryParams.reviewedDateTo =
      normalizeReportDateRangeYmd(data?.defaultReviewedDateTo) || queryParams.reviewedDateTo;
    // 本页仅有效已点评行，不再展示点评状态筛
    reasonableOptions.value = optionDtosToSelectItems(data?.reasonableOptions);
    keyDrugTypeOptions.value = optionDtosToSelectItems(data?.keyDrugTypeOptions);
    medicinePropertyOptions.value = optionDtosToSelectItems(data?.medicinePropertyOptions);
  },
});

const { run: runPage, loading: pageLoading } = useRequest(pagePrescriptionReviewsApi, {
  manual: true,
  onSuccess: (data) => {
    tableData.value = data?.records ?? [];
    total.value = Number(data?.total ?? 0);
  },
});

const { run: runExport, loading: exportLoading } = useRequest(exportPrescriptionReviewsApi, {
  manual: true,
});

/** 拉取点评分页。 */
async function getList() {
  await runPage(buildPageRequest());
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 查询回到第一页。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 重置筛选并重新初始化默认日期后查询。 */
async function handleReset() {
  queryParams.reasonableValue = undefined;
  queryParams.keyDrugType = undefined;
  queryParams.medicinePropertyCode = undefined;
  queryParams.keyword = undefined;
  queryParams.openedDateFrom = undefined;
  queryParams.openedDateTo = undefined;
  queryParams.pageNo = 1;
  queryParams.pageSize = 50;
  await runInit();
  await getList();
}

/** 导出当前页（条数=pageSize）。 */
async function handleExport() {
  const exportColumns = buildExcelExportColumns(EXPORT_COLUMNS);
  const data = await runExport({
    ...buildPageRequest(),
    exportColumns,
  });
  if (!data?.body?.trim() && !data?.fileBytes?.trim()) {
    ElMessage.warning('导出内容为空');
    return;
  }
  downloadBinaryExportFile(data, data.fileName ?? '处方点评记录.xlsx');
  if (data.truncated) {
    ElMessage.warning('当前仅为分页导出，尚有未导出页');
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

/**
 * 详情开方日窗：优先点评筛选日，否则近两年至今天，避免筛丢。
 */
function resolveDetailOpenedDateWindow() {
  const today = new Date();
  const to =
    normalizeReportDateRangeYmd(queryParams.reviewedDateTo) ||
    normalizeReportDateRangeYmd(today.toISOString().slice(0, 10)) ||
    '';
  const fromFilter = normalizeReportDateRangeYmd(queryParams.reviewedDateFrom);
  if (fromFilter) {
    return { from: fromFilter, to };
  }
  const fromDate = new Date(today);
  fromDate.setFullYear(fromDate.getFullYear() - 2);
  const from = normalizeReportDateRangeYmd(fromDate.toISOString().slice(0, 10)) || to;
  return { from, to };
}

/**
 * 打开处方详情：点评信息由列表行带入抽屉上半区；处方信息仍走门诊处方查看重载。
 */
function handleOpenDetail(row: PrescriptionReviewListRow) {
  const rxNo = String(row.prescriptionNo ?? '').trim();
  if (!rxNo) {
    ElMessage.warning('缺少处方号');
    return;
  }
  const { from, to } = resolveDetailOpenedDateWindow();
  // 方头最小摘要 + 点评字段；明细加载后抽屉会用首行回填患者等（不覆盖点评）
  const summary: OutpatientPrescriptionSummaryRow = {
    prescriptionNo: rxNo,
    lineCount: 0,
    reviewId: row.id,
    reviewBusNo: row.busNo,
    reviewStatusEnum: row.statusEnum || 'REVIEWED',
    reviewStatusText: row.statusText || '已点评',
    reasonableFlag: row.reasonableFlag,
    reasonableText: row.reasonableText,
    reviewReason: row.reason,
    reviewRemark: row.remark,
    reviewerName: row.reviewerName,
    reviewedAt: row.reviewedAt,
  };
  void detailDrawerRef.value?.open({
    summary,
    openedDateFrom: from,
    openedDateTo: to,
  });
}

onMounted(async () => {
  await runInit();
  await getList();
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryParams as Record<string, unknown>">
          <template #fields>
            <ElFormItem label="点评时间">
              <ElDatePicker
                v-model="reviewedDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                :shortcuts="dateRangeShortcuts"
                clearable
                :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATERANGE]"
                @change="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="开方时间">
              <ElDatePicker
                v-model="openedDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                :shortcuts="dateRangeShortcuts"
                clearable
                :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATERANGE]"
                @change="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="是否合理">
              <ElSelect
                v-model="queryParams.reasonableValue"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(reasonableOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="重点药类型">
              <ElSelect
                v-model="queryParams.keyDrugType"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(keyDrugTypeOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="药品性质">
              <ElSelect
                v-model="queryParams.medicinePropertyCode"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(medicinePropertyOptions)"
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
                placeholder="处方号/业务号"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
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
        <ReportListToolbar :title="`${PAGE_TITLE} · 点评列表`" :total="total">
          <template #actions>
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
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              :row-key="rowKey"
              class="prv-review-table"
              @row-click="handleOpenDetail"
            >
              <ElTableColumn
                prop="prescriptionNo"
                label="处方编号"
                min-width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="patientAgeText"
                label="年龄(岁)"
                min-width="80"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  {{ row.patientAgeText || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="diagnosisName"
                label="诊断"
                min-width="160"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.diagnosisName || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="drugVarietyCount"
                label="药品品种"
                min-width="88"
                align="right"
                header-align="center"
              >
                <template #default="{ row }">
                  {{ row.drugVarietyCount ?? '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="antibioticFlag"
                label="抗菌药(0/1)"
                min-width="100"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  {{ formatZeroOne(row.antibioticFlag) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="injectFlag"
                label="注射剂(0/1)"
                min-width="100"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  {{ formatZeroOne(row.injectFlag) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="basicDrugVarietyCount"
                label="国家基本药物品种数"
                min-width="140"
                align="right"
                header-align="center"
              >
                <template #default="{ row }">
                  {{ row.basicDrugVarietyCount ?? '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="genericNameCount"
                label="药品通用名数"
                min-width="112"
                align="right"
                header-align="center"
              >
                <template #default="{ row }">
                  {{ row.genericNameCount ?? '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="prescriptionAmount"
                label="处方金额(元)"
                min-width="112"
                align="right"
                header-align="center"
              >
                <template #default="{ row }">
                  {{ formatAmount(row.prescriptionAmount) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="orderingPractitionerName"
                label="处方医师"
                min-width="96"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.orderingPractitionerName || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="reasonableFlag"
                label="是否合理(0/1)"
                min-width="112"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  <ElTag v-if="row.reasonableFlag === true" type="success" effect="light">
                    1
                  </ElTag>
                  <ElTag v-else-if="row.reasonableFlag === false" type="danger" effect="light">
                    0
                  </ElTag>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="reason"
                label="存在问题"
                min-width="160"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.reason || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="操作"
                min-width="72"
                align="center"
                header-align="center"
                fixed="right"
              >
                <template #default="{ row }">
                  <ElButton link type="primary" @click.stop="handleOpenDetail(row)">
                    详情
                  </ElButton>
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
          :page-sizes="[10, 20, 50, 100, 200]"
          @size-change="handlePageSizeChange"
          @current-change="handleCurrentPageChange"
        />
      </template>
    </ReportListPageLayout>

    <OutpatientPrescriptionViewDetailDrawer ref="detailDrawerRef" />
  </Page>
</template>

<style scoped>
:deep(.prv-review-table .el-table__body tr) {
  cursor: pointer;
}
</style>
