<!--
  追溯码查询报表：只读列表 + 流水 Drawer + Excel 导出；支持 deep link（traceNo + openDetail）。
-->
<script setup lang="ts">
import type {
  PageTraceNumberReportRequest,
  TraceNumberReportQueryScope,
  TraceNumberReportRowDto,
} from '#/api/report/trace-number-report';
import type { ApiOptionItem } from '#/types/api-response';
import type { ReportPrintColumnDef } from '#/utils/print/print-report-list';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Download, Printer, Refresh, Search, Setting } from '@element-plus/icons-vue';
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
  exportTraceNumberReportApi,
  loadTraceNumberReportFormInitApi,
  pageTraceNumberReportApi,
  TRACE_QUERY_SCOPE_HOSPITAL,
  TRACE_QUERY_SCOPE_LOCATION,
} from '#/api/report/trace-number-report';
import TraceCodeTooltip from '#/components/trace/trace-code-tooltip.vue';
import { useRequest } from '#/hooks/use-request';
import { buildExcelExportColumns } from '#/utils/export/build-excel-export-columns';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATERANGE,
  REPORT_TABLE_NUM_CELL,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPageFooter,
  ReportPrintFieldDialog,
  ReportQueryBar,
  ReportTableScroll,
  mapReportPrintRow,
  reportPrintFieldsOf,
  useReportListPrint,
} from '#/views/report/shared';
import {
  buildReportDateRangeShortcuts,
  normalizeReportDateRangeYmd,
  toReportApiDateTimeRange,
} from '#/views/report/shared/report-date-range-shortcuts';
import { fmtReportNumber, optionDtosToSelectItems } from '#/views/report/shared/report-format';

import TraceNumberDetailDrawer from './components/trace-number-detail-drawer.vue';

defineOptions({ name: 'TraceNumberReportPage' });

interface QueryState {
  pageNo: number;
  pageSize: number;
  queryScope: TraceNumberReportQueryScope;
  locationId?: string;
  inHospitalStatusEnum?: string;
  traceNoKeyword?: string;
  medicationKeyword?: string;
  lotNumberKeyword?: string;
  flowTimeStart?: string;
  flowTimeEnd?: string;
  deepLinkTraceNo?: string;
  deepLinkOpenDetail?: boolean;
}

const PAGE_TITLE = '追溯码查询';
/** 打印字段偏好键（本机记住）。 */
const PRINT_REPORT_CODE = 'report-trace-number';

/** 追溯码可打印列目录（不含操作列；库房列始终可选）。 */
const PRINT_COLUMNS: ReportPrintColumnDef[] = [
  { field: 'traceNo', title: '追溯码', align: 'center', widthWeight: 1.5 },
  { field: 'currentLocationNames', title: '当前所在库房', align: 'center', widthWeight: 1.2 },
  { field: 'medicationCode', title: '编码', align: 'center', widthWeight: 0.9 },
  { field: 'medicationName', title: '药品名称', align: 'center', widthWeight: 1.3 },
  { field: 'specificationText', title: '规格', align: 'center', widthWeight: 1.1 },
  { field: 'lotNumber', title: '批号', align: 'center', widthWeight: 0.9 },
  { field: 'inHospitalStatusLabel', title: '在院状态', align: 'center', widthWeight: 0.9 },
  { field: 'remainingSplitQuantity', title: '拆零剩余', align: 'right', widthWeight: 0.9 },
  { field: 'traceUnitCode', title: '追溯单位', align: 'center', widthWeight: 0.8 },
  { field: 'lastFlowAt', title: '最近流水', align: 'center', widthWeight: 1.3 },
];

const route = useRoute();
const router = useRouter();

const tableData = ref<TraceNumberReportRowDto[]>([]);
const total = ref(0);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);
const detailDrawerRef = ref<InstanceType<typeof TraceNumberDetailDrawer> | null>(null);

const queryScopeOptions = ref<ApiOptionItem[]>([]);
const inHospitalStatusOptions = ref<ApiOptionItem[]>([]);
const locationOptions = ref<ApiOptionItem[]>([]);

const queryParams = reactive<QueryState>({
  pageNo: 1,
  pageSize: 20,
  queryScope: TRACE_QUERY_SCOPE_HOSPITAL,
});

const isLocationScope = computed(() => queryParams.queryScope === TRACE_QUERY_SCOPE_LOCATION);

const dateRangeShortcuts = buildReportDateRangeShortcuts();

/** 流水时间与 daterange 双向绑定（界面精确到天，请求时补全日起止）。 */
const flowDateRange = computed({
  get() {
    const start = normalizeReportDateRangeYmd(queryParams.flowTimeStart);
    const end = normalizeReportDateRangeYmd(queryParams.flowTimeEnd);
    if (start && end) {
      return [start, end] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.flowTimeStart = value?.[0];
    queryParams.flowTimeEnd = value?.[1];
  },
});

/** 下拉选项转为 ElSelect 可用结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 从路由 query 解析 deep link 参数。 */
function applyRouteQueryToParams() {
  const traceNo = String(route.query.traceNo ?? '').trim();
  const openDetailRaw = route.query.openDetail;
  const openDetail = openDetailRaw === true || openDetailRaw === 'true' || openDetailRaw === '1';
  const queryScopeRaw = String(route.query.queryScope ?? '').trim();
  const locationId = String(route.query.locationId ?? '').trim();

  if (
    queryScopeRaw === TRACE_QUERY_SCOPE_LOCATION ||
    queryScopeRaw === TRACE_QUERY_SCOPE_HOSPITAL
  ) {
    queryParams.queryScope = queryScopeRaw;
  }
  if (locationId) {
    queryParams.locationId = locationId;
  }
  if (traceNo) {
    queryParams.traceNoKeyword = traceNo;
    queryParams.deepLinkTraceNo = traceNo;
    queryParams.deepLinkOpenDetail = openDetail;
  }
}

/** 组装分页/导出请求体。 */
function buildPageRequest(): PageTraceNumberReportRequest {
  const locationId = isLocationScope.value
    ? preserveApiIdForRequest(queryParams.locationId ?? '')
    : undefined;
  const flowRange = toReportApiDateTimeRange(queryParams.flowTimeStart, queryParams.flowTimeEnd);
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    queryScope: queryParams.queryScope,
    locationId: locationId || undefined,
    inHospitalStatusEnum: queryParams.inHospitalStatusEnum || undefined,
    traceNoKeyword: queryParams.traceNoKeyword?.trim() || undefined,
    medicationKeyword: queryParams.medicationKeyword?.trim() || undefined,
    lotNumberKeyword: queryParams.lotNumberKeyword?.trim() || undefined,
    flowTimeStart: flowRange.start,
    flowTimeEnd: flowRange.end,
    traceNo: queryParams.deepLinkTraceNo || undefined,
    openDetail: queryParams.deepLinkOpenDetail || undefined,
  };
}

/** 列表加载成功后处理 deep link 打开详情。 */
function handleDeepLinkAfterList(data?: { openDetail?: boolean; prefillTraceNo?: string }) {
  const traceNo = String(data?.prefillTraceNo ?? queryParams.deepLinkTraceNo ?? '').trim();
  const shouldOpen = data?.openDetail === true || queryParams.deepLinkOpenDetail === true;
  if (!shouldOpen || !traceNo) {
    return;
  }
  void detailDrawerRef.value?.open(traceNo);
  queryParams.deepLinkOpenDetail = false;
}

/** 格式化最近流水时间。 */
function formatDateTime(value?: string) {
  const text = String(value ?? '').trim();
  if (!text) {
    return '—';
  }
  return text.length >= 19 ? text.slice(0, 19) : text;
}

/** 数量展示：整数、无小数；空为 —。 */
function formatQuantity(value?: number | string) {
  const text = fmtReportNumber(value, 0);
  return text || '—';
}

/** 文本空则 —。 */
function formatText(value?: null | string) {
  const text = String(value ?? '').trim();
  return text || '—';
}

const { run: runInit, loading: initLoading } = useRequest(loadTraceNumberReportFormInitApi, {
  manual: true,
  onSuccess: (data) => {
    queryScopeOptions.value = optionDtosToSelectItems(data?.queryScopeOptions);
    inHospitalStatusOptions.value = optionDtosToSelectItems(data?.inHospitalStatusOptions);
    locationOptions.value = optionDtosToSelectItems(data?.locationOptions);
    if (!queryParams.queryScope && queryScopeOptions.value[0]?.value) {
      queryParams.queryScope = queryScopeOptions.value[0].value as TraceNumberReportQueryScope;
    }
  },
});

const { run: runPage, loading: pageLoading } = useRequest(pageTraceNumberReportApi, {
  manual: true,
  onSuccess: (data) => {
    tableData.value = data?.records ?? [];
    total.value = Number(data?.total ?? 0);
    handleDeepLinkAfterList(data);
  },
});

const { run: runExport, loading: exportLoading } = useRequest(exportTraceNumberReportApi, {
  manual: true,
});

const { printFieldDialogRef, printSubmitting, handleOpenPrintFields, handlePrintCurrentPage } =
  useReportListPrint({
    getReportCode: () => PRINT_REPORT_CODE,
    getColumnCatalog: () => PRINT_COLUMNS,
    getReportTitle: () => PAGE_TITLE,
    getPrintRows: () =>
      tableData.value.map((r) =>
        mapReportPrintRow(
          r as unknown as Record<string, unknown>,
          reportPrintFieldsOf(PRINT_COLUMNS),
          {
            traceNo: (v) => formatText(v as null | string),
            currentLocationNames: (v) => formatText(v as null | string),
            medicationCode: (v) => formatText(v as null | string),
            medicationName: (v) => formatText(v as null | string),
            specificationText: (v) => formatText(v as null | string),
            lotNumber: (v) => formatText(v as null | string),
            inHospitalStatusLabel: (v) => formatText(v as null | string),
            remainingSplitQuantity: (v) => formatQuantity(v as number | string | undefined),
            traceUnitCode: (v) => formatText(v as null | string),
            lastFlowAt: (v) => formatDateTime(v as string | undefined),
          },
        ),
      ),
  });

const tableLoading = computed(
  () => initLoading.value || pageLoading.value || exportLoading.value || printSubmitting.value,
);

/** 拉取追溯码分页列表。 */
async function getList() {
  if (isLocationScope.value && !queryParams.locationId) {
    // 指定库房未选库时清空列表，避免残留全院数据导致「当前所在库房」全是 —
    tableData.value = [];
    total.value = 0;
    ElMessage.warning('请选择库房');
    return;
  }
  await runPage(buildPageRequest());
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 查询回到第一页。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 切换查询范围：切到指定库房且未选库时只清空，不弹窗打扰。 */
function handleQueryScopeChange() {
  queryParams.pageNo = 1;
  if (isLocationScope.value && !queryParams.locationId) {
    tableData.value = [];
    total.value = 0;
    return;
  }
  void getList();
}

/** 重置筛选并回到第一页。 */
function handleReset() {
  queryParams.locationId = undefined;
  queryParams.inHospitalStatusEnum = undefined;
  queryParams.traceNoKeyword = undefined;
  queryParams.medicationKeyword = undefined;
  queryParams.lotNumberKeyword = undefined;
  queryParams.flowTimeStart = undefined;
  queryParams.flowTimeEnd = undefined;
  queryParams.deepLinkTraceNo = undefined;
  queryParams.deepLinkOpenDetail = false;
  queryParams.queryScope = TRACE_QUERY_SCOPE_HOSPITAL;
  queryParams.pageNo = 1;
  void router.replace({ path: route.path, query: {} });
  void getList();
}

/** 导出 Excel（列清单与打印列目录一致）。 */
async function handleExportExcel() {
  if (isLocationScope.value && !queryParams.locationId) {
    ElMessage.warning('请选择库房');
    return;
  }
  const pageReq = buildPageRequest();
  const { pageNo: _pageNo, pageSize: _pageSize, ...filter } = pageReq;
  // 页面展示字段 → 导出行 DTO 字段
  const exportColumns = buildExcelExportColumns(PRINT_COLUMNS, {
    fieldAliases: {
      traceUnitCode: 'traceUnitCodeLabel',
      lastFlowAt: 'lastFlowAtLabel',
    },
  });
  const data = await runExport({
    ...filter,
    exportColumns,
  });
  if (!data?.body?.trim()) {
    ElMessage.warning('导出内容为空');
    return;
  }
  downloadBinaryExportFile(data, data.fileName ?? '追溯码查询.xlsx');
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

/** 点击行打开流水 Drawer。 */
function handleOpenDetail(row: TraceNumberReportRowDto) {
  const traceNo = String(row.traceNo ?? '').trim();
  if (!traceNo) {
    return;
  }
  void detailDrawerRef.value?.open(traceNo);
}

/** 全院范围时清空库房筛选。 */
watch(
  () => queryParams.queryScope,
  (scope) => {
    if (scope !== TRACE_QUERY_SCOPE_LOCATION) {
      queryParams.locationId = undefined;
    }
  },
);

onMounted(async () => {
  applyRouteQueryToParams();
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
            <ElFormItem label="查询范围">
              <ElSelect
                v-model="queryParams.queryScope"
                filterable
                :class="REPORT_QUERY_CONTROL"
                @change="handleQueryScopeChange"
              >
                <ElOption
                  v-for="item in toSelectOptions(queryScopeOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem v-if="isLocationScope" label="库房">
              <ElSelect
                v-model="queryParams.locationId"
                clearable
                filterable
                placeholder="请选择库房"
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
            <ElFormItem label="在院状态">
              <ElSelect
                v-model="queryParams.inHospitalStatusEnum"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(inHospitalStatusOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="追溯码">
              <ElInput
                v-model="queryParams.traceNoKeyword"
                clearable
                placeholder="追溯码关键字"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="药品">
              <ElInput
                v-model="queryParams.medicationKeyword"
                clearable
                placeholder="名称/编码/拼音/五笔"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="批号">
              <ElInput
                v-model="queryParams.lotNumberKeyword"
                clearable
                placeholder="批号关键字"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="流水时间">
              <ElDatePicker
                v-model="flowDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                :shortcuts="dateRangeShortcuts"
                :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATERANGE]"
                @change="handleQuery"
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
        <ReportListToolbar :title="`${PAGE_TITLE} · 列表`" :total="total">
          <template #actions>
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
            <ElButton
              type="primary"
              plain
              :icon="Download"
              :loading="exportLoading"
              @click="handleExportExcel"
            >
              导出 Excel
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
              row-key="traceNo"
              highlight-current-row
              @row-click="handleOpenDetail"
            >
              <ElTableColumn
                type="index"
                label="序号"
                width="56"
                align="center"
                header-align="center"
              />
              <ElTableColumn
                prop="traceNo"
                label="追溯码"
                min-width="180"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  <TraceCodeTooltip :value="row.traceNo" />
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="isLocationScope"
                prop="currentLocationNames"
                label="当前所在库房"
                min-width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatText(row.currentLocationNames) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="medicationCode"
                label="编码"
                width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="medicationName"
                label="药品名称"
                min-width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="specificationText"
                label="规格"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatText(row.specificationText) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="lotNumber"
                label="批号"
                width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="inHospitalStatusLabel"
                label="在院状态"
                width="96"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  <ElTag v-if="row.inHospitalStatusLabel" type="info" effect="light">
                    {{ row.inHospitalStatusLabel }}
                  </ElTag>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="remainingSplitQuantity"
                label="拆零剩余"
                width="96"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ formatQuantity(row.remainingSplitQuantity) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="traceUnitCode"
                label="追溯单位"
                width="88"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatText(row.traceUnitCode) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="lastFlowAt"
                label="最近流水"
                width="160"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatDateTime(row.lastFlowAt) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="操作"
                width="72"
                fixed="right"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  <ElButton link type="primary" @click.stop="handleOpenDetail(row)">流水</ElButton>
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
          @size-change="handlePageSizeChange"
          @current-change="handleCurrentPageChange"
        />
      </template>
    </ReportListPageLayout>

    <TraceNumberDetailDrawer ref="detailDrawerRef" />
    <ReportPrintFieldDialog
      ref="printFieldDialogRef"
      :report-code="PRINT_REPORT_CODE"
      :column-catalog="PRINT_COLUMNS"
    />
  </Page>
</template>
