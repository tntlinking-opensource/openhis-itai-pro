<!--
  处方点评记录：筛选 + 点评字段列表 + 导出；详情复用门诊处方查看抽屉（仅处方信息）。
  主类型：T2 纯展示查询列表（导出/查看）；点评与撤销仅在「门诊处方查看」页操作。
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
  statusEnum?: string;
  reviewedDateFrom?: string;
  reviewedDateTo?: string;
  /** 筛选项 value 为 "true"/"false" 字符串；空=不限 */
  reasonableValue?: string;
  prescriptionNoKeyword?: string;
  busNoKeyword?: string;
}

const PAGE_TITLE = '处方点评记录';

/** 导出列与列表展示列一致（field 须落在后端 @Excel 白名单）。 */
const EXPORT_COLUMNS = [
  { field: 'prescriptionNo', title: '处方号' },
  { field: 'busNo', title: '业务号' },
  { field: 'statusText', title: '点评状态' },
  { field: 'reasonableText', title: '是否合理' },
  { field: 'reason', title: '原因' },
  { field: 'remark', title: '备注' },
  { field: 'reviewerName', title: '点评人' },
  { field: 'reviewedAt', title: '点评时间' },
];

const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);
const detailDrawerRef = ref<InstanceType<typeof OutpatientPrescriptionViewDetailDrawer> | null>(
  null,
);
const tableData = ref<PrescriptionReviewListRow[]>([]);
const total = ref(0);

const statusOptions = ref<ApiOptionItem[]>([]);
const reasonableOptions = ref<ApiOptionItem[]>([]);

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

/** 下拉选项转为 ElSelect 结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 点评时间展示。 */
function formatDateTime(value?: string) {
  const text = String(value ?? '').trim();
  if (!text) {
    return '—';
  }
  return text.length >= 19 ? text.slice(0, 19) : text;
}

/** 行键。 */
function rowKey(row: PrescriptionReviewListRow) {
  return String(row.id ?? row.prescriptionNo ?? '');
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
    statusEnum: queryParams.statusEnum || undefined,
    reviewedDateFrom: queryParams.reviewedDateFrom || undefined,
    reviewedDateTo: queryParams.reviewedDateTo || undefined,
    reasonableFlag: resolveReasonableFlag(),
    prescriptionNoKeyword: queryParams.prescriptionNoKeyword?.trim() || undefined,
    busNoKeyword: queryParams.busNoKeyword?.trim() || undefined,
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
    statusOptions.value = optionDtosToSelectItems(data?.statusOptions);
    reasonableOptions.value = optionDtosToSelectItems(data?.reasonableOptions);
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
  queryParams.statusEnum = undefined;
  queryParams.reasonableValue = undefined;
  queryParams.prescriptionNoKeyword = undefined;
  queryParams.busNoKeyword = undefined;
  queryParams.pageNo = 1;
  queryParams.pageSize = 50;
  await runInit();
  await getList();
}

/** 导出与列表同列。 */
async function handleExport() {
  const exportColumns = buildExcelExportColumns(EXPORT_COLUMNS);
  const data = await runExport({
    ...buildFilterBody(),
    exportColumns,
  });
  if (!data?.body?.trim() && !data?.fileBytes?.trim()) {
    ElMessage.warning('导出内容为空');
    return;
  }
  downloadBinaryExportFile(data, data.fileName ?? '处方点评记录.xlsx');
  if (data.truncated) {
    ElMessage.warning('导出数据已触达行数上限，部分记录未包含在文件中');
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

/** 按点评时间向前扩两年作开方查询窗，避免开方日早于点评日筛丢。 */
function resolveDetailOpenedDateWindow(row: PrescriptionReviewListRow) {
  const today = new Date();
  const to = normalizeReportDateRangeYmd(today.toISOString().slice(0, 10)) || '';
  const reviewedRaw = String(row.reviewedAt ?? '').trim();
  const anchor = reviewedRaw ? new Date(reviewedRaw.replace(' ', 'T')) : today;
  const fromDate = Number.isNaN(anchor.getTime()) ? today : anchor;
  fromDate.setFullYear(fromDate.getFullYear() - 2);
  const from = normalizeReportDateRangeYmd(fromDate.toISOString().slice(0, 10)) || to;
  return { from, to };
}

/**
 * 打开处方详情：复用门诊处方查看能力；按处方号精确筛，详情区仅展示处方信息。
 */
function handleOpenDetail(row: PrescriptionReviewListRow) {
  const rxNo = String(row.prescriptionNo ?? '').trim();
  if (!rxNo) {
    ElMessage.warning('缺少处方号');
    return;
  }
  const { from, to } = resolveDetailOpenedDateWindow(row);
  // 方头最小摘要；明细加载后抽屉会用首行回填患者等字段
  const summary: OutpatientPrescriptionSummaryRow = {
    prescriptionNo: rxNo,
    lineCount: 0,
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
            <ElFormItem label="点评状态">
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
            <ElFormItem label="处方号">
              <ElInput
                v-model="queryParams.prescriptionNoKeyword"
                clearable
                placeholder="模糊"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="业务号">
              <ElInput
                v-model="queryParams.busNoKeyword"
                clearable
                placeholder="模糊"
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
                label="处方号"
                min-width="132"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="busNo"
                label="业务号"
                min-width="132"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="statusText"
                label="点评状态"
                min-width="88"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  <ElTag v-if="row.statusText" type="success" effect="light">
                    {{ row.statusText }}
                  </ElTag>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="reasonableText"
                label="是否合理"
                min-width="88"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  <ElTag
                    v-if="row.reasonableText"
                    :type="row.reasonableFlag === false ? 'danger' : 'success'"
                    effect="light"
                  >
                    {{ row.reasonableText }}
                  </ElTag>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="reason"
                label="原因"
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
                prop="remark"
                label="备注"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.remark || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="reviewerName"
                label="点评人"
                min-width="88"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.reviewerName || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="reviewedAt"
                label="点评时间"
                min-width="154"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatDateTime(row.reviewedAt) }}
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
          :page-sizes="[20, 50, 100, 200]"
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
