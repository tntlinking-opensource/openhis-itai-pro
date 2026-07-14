<!--
  追溯码查询报表：只读列表 + 详情 Drawer + Excel 导出；支持 deep link（traceNo + openDetail）。
-->
<script setup lang="ts">
import type {
  PageTraceNumberReportRequest,
  TraceNumberReportQueryScope,
  TraceNumberReportRowDto,
} from '#/api/report/trace-number-report';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

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
  exportTraceNumberReportApi,
  loadTraceNumberReportFormInitApi,
  pageTraceNumberReportApi,
  TRACE_QUERY_SCOPE_HOSPITAL,
  TRACE_QUERY_SCOPE_LOCATION,
} from '#/api/report/trace-number-report';
import TraceCodeTooltip from '#/components/trace/trace-code-tooltip.vue';
import { useRequest } from '#/hooks/use-request';
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
  ReportQueryBar,
  ReportTableScroll,
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
  locationStockStatusEnum?: string;
  traceNoKeyword?: string;
  medicationKeyword?: string;
  lotNumberKeyword?: string;
  flowTimeStart?: string;
  flowTimeEnd?: string;
  deepLinkTraceNo?: string;
  deepLinkOpenDetail?: boolean;
}

const PAGE_TITLE = '追溯码查询';

const route = useRoute();
const router = useRouter();

const tableData = ref<TraceNumberReportRowDto[]>([]);
const total = ref(0);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);
const detailDrawerRef = ref<InstanceType<typeof TraceNumberDetailDrawer> | null>(null);

const queryScopeOptions = ref<ApiOptionItem[]>([]);
const inHospitalStatusOptions = ref<ApiOptionItem[]>([]);
const locationStockStatusOptions = ref<ApiOptionItem[]>([]);
const locationOptions = ref<ApiOptionItem[]>([]);

const queryParams = reactive<QueryState>({
  pageNo: 1,
  pageSize: 20,
  queryScope: TRACE_QUERY_SCOPE_HOSPITAL,
});

const isLocationScope = computed(() => queryParams.queryScope === TRACE_QUERY_SCOPE_LOCATION);

const tableLoading = computed(() => initLoading.value || pageLoading.value || exportLoading.value);

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
    locationStockStatusEnum: isLocationScope.value
      ? queryParams.locationStockStatusEnum || undefined
      : undefined,
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

const { run: runInit, loading: initLoading } = useRequest(loadTraceNumberReportFormInitApi, {
  manual: true,
  onSuccess: (data) => {
    queryScopeOptions.value = optionDtosToSelectItems(data?.queryScopeOptions);
    inHospitalStatusOptions.value = optionDtosToSelectItems(data?.inHospitalStatusOptions);
    locationStockStatusOptions.value = optionDtosToSelectItems(data?.locationStockStatusOptions);
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

/** 拉取追溯码分页列表。 */
async function getList() {
  if (isLocationScope.value && !queryParams.locationId) {
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

/** 重置筛选并回到第一页。 */
function handleReset() {
  queryParams.locationId = undefined;
  queryParams.inHospitalStatusEnum = undefined;
  queryParams.locationStockStatusEnum = undefined;
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

/** 导出 Excel。 */
async function handleExportExcel() {
  if (isLocationScope.value && !queryParams.locationId) {
    ElMessage.warning('请选择库房');
    return;
  }
  const pageReq = buildPageRequest();
  const { pageNo: _pageNo, pageSize: _pageSize, ...filter } = pageReq;
  const data = await runExport(filter);
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

/** 点击行打开详情 Drawer。 */
function handleOpenDetail(row: TraceNumberReportRowDto) {
  const traceNo = String(row.traceNo ?? '').trim();
  if (!traceNo) {
    return;
  }
  void detailDrawerRef.value?.open(traceNo);
}

/** 全院范围时清空库房与本库态筛选。 */
watch(
  () => queryParams.queryScope,
  (scope) => {
    if (scope !== TRACE_QUERY_SCOPE_LOCATION) {
      queryParams.locationId = undefined;
      queryParams.locationStockStatusEnum = undefined;
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
              <ElSelect v-model="queryParams.queryScope" filterable :class="REPORT_QUERY_CONTROL">
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
              >
                <ElOption
                  v-for="item in toSelectOptions(inHospitalStatusOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem v-if="isLocationScope" label="本库状态">
              <ElSelect
                v-model="queryParams.locationStockStatusEnum"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
              >
                <ElOption
                  v-for="item in toSelectOptions(locationStockStatusOptions)"
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
              />
            </ElFormItem>
            <ElFormItem label="药品">
              <ElInput
                v-model="queryParams.medicationKeyword"
                clearable
                placeholder="名称/编码/拼音/五笔"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="批号">
              <ElInput
                v-model="queryParams.lotNumberKeyword"
                clearable
                placeholder="批号关键字"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
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
              <ElTableColumn type="index" label="序号" width="56" align="center" />
              <ElTableColumn prop="traceNo" label="追溯码" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <TraceCodeTooltip :value="row.traceNo" />
                </template>
              </ElTableColumn>
              <ElTableColumn prop="medicationCode" label="编码" width="100" show-overflow-tooltip />
              <ElTableColumn
                prop="medicationName"
                label="药品名称"
                min-width="140"
                show-overflow-tooltip
              />
              <ElTableColumn prop="lotNumber" label="批号" width="100" show-overflow-tooltip />
              <ElTableColumn
                prop="inHospitalStatusLabel"
                label="在院状态"
                width="96"
                align="center"
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
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ fmtReportNumber(row.remainingSplitQuantity, 2) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="traceUnitCode"
                label="追溯单位"
                width="88"
                show-overflow-tooltip
              />
              <ElTableColumn
                v-if="isLocationScope"
                prop="locationStockStatusLabel"
                label="本库状态"
                width="96"
                align="center"
              >
                <template #default="{ row }">
                  <ElTag v-if="row.locationStockStatusLabel" effect="light">
                    {{ row.locationStockStatusLabel }}
                  </ElTag>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="isLocationScope"
                prop="locationNetSplitQuantity"
                label="本库净量"
                width="96"
                align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ fmtReportNumber(row.locationNetSplitQuantity, 2) }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="lastFlowAt" label="最近流水" width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ formatDateTime(row.lastFlowAt) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="72" fixed="right" align="center">
                <template #default="{ row }">
                  <ElButton link type="primary" @click.stop="handleOpenDetail(row)">详情</ElButton>
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
  </Page>
</template>
