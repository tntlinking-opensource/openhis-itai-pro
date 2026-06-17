<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { PrintDailySummaryItemDto, PrintEventLogItemDto } from '#/api/report/report-print-log';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import {
  queryPrintDailySummaryApi,
  queryPrintEventLogPageApi,
} from '#/api/report/report-print-log';
import { useRequest } from '#/hooks/use-request';

import {
  formatDateRangeTodayMinus,
  formatPrintEventType,
  formatPrintStatus,
  PRINT_EVENT_TYPE_OPTIONS,
  PRINT_STATUS_OPTIONS,
  PRINT_TARGET_TYPE_OPTIONS,
  printStatusTagType,
} from './print-log.constants';

function formatBizRef(bizRef?: Record<string, unknown>) {
  if (!bizRef || Object.keys(bizRef).length === 0) {
    return '-';
  }
  return JSON.stringify(bizRef, null, 2);
}

defineOptions({ name: 'ReportPrintLog' });

const mainTab = ref<'detail' | 'summary'>('detail');
const detailList = ref<PrintEventLogItemDto[]>([]);
const summaryList = ref<PrintDailySummaryItemDto[]>([]);
const detailTotal = ref(0);
const summaryTotal = ref(0);
const detailVisible = ref(false);
const detailRow = ref<PrintEventLogItemDto | null>(null);

const defaultRange = formatDateRangeTodayMinus(7);

const detailQuery = reactive<{
  pageNo: number;
  pageSize: number;
  keyword?: string;
  eventType?: string;
  status?: string;
  targetCode?: string;
  targetType?: string;
  createdFrom?: string;
  createdTo?: string;
}>({
  pageNo: 1,
  pageSize: 20,
  createdFrom: defaultRange.from,
  createdTo: defaultRange.to,
});

const summaryQuery = reactive<{
  pageNo: number;
  pageSize: number;
  keyword?: string;
  eventType?: string;
  targetCode?: string;
  statDateFrom?: string;
  statDateTo?: string;
}>({
  pageNo: 1,
  pageSize: 20,
  statDateFrom: defaultRange.from,
  statDateTo: defaultRange.to,
});

const { run: runDetail, loading: detailLoading } = useRequest(queryPrintEventLogPageApi, {
  manual: true,
});
const { run: runSummary, loading: summaryLoading } = useRequest(queryPrintDailySummaryApi, {
  manual: true,
});

const tableLoading = computed(() =>
  mainTab.value === 'detail' ? detailLoading.value : summaryLoading.value,
);

const detailFormItems = computed<FormItemConfig[]>(() => [
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '操作员 / 模板 / jobId / 错误',
  },
  {
    label: '事件',
    prop: 'eventType',
    type: 'select',
    options: [{ label: '全部', value: '' }, ...PRINT_EVENT_TYPE_OPTIONS],
    clearable: true,
  },
  {
    label: '状态',
    prop: 'status',
    type: 'select',
    options: [{ label: '全部', value: '' }, ...PRINT_STATUS_OPTIONS],
    clearable: true,
  },
  {
    label: '目标类型',
    prop: 'targetType',
    type: 'select',
    options: [{ label: '全部', value: '' }, ...PRINT_TARGET_TYPE_OPTIONS],
    clearable: true,
  },
  {
    label: '模板编码',
    prop: 'targetCode',
    type: 'input',
    placeholder: 'templateCode',
  },
  {
    label: '开始日期',
    prop: 'createdFrom',
    type: 'date',
    valueFormat: 'YYYY-MM-DD',
  },
  {
    label: '结束日期',
    prop: 'createdTo',
    type: 'date',
    valueFormat: 'YYYY-MM-DD',
  },
]);

const summaryFormItems = computed<FormItemConfig[]>(() => [
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '操作员 / 模板编码',
  },
  {
    label: '事件',
    prop: 'eventType',
    type: 'select',
    options: [{ label: '全部', value: '' }, ...PRINT_EVENT_TYPE_OPTIONS],
    clearable: true,
  },
  {
    label: '模板编码',
    prop: 'targetCode',
    type: 'input',
  },
  {
    label: '汇总起',
    prop: 'statDateFrom',
    type: 'date',
    valueFormat: 'YYYY-MM-DD',
  },
  {
    label: '汇总止',
    prop: 'statDateTo',
    type: 'date',
    valueFormat: 'YYYY-MM-DD',
  },
]);

const detailColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  { prop: 'createdAt', label: '时间', width: 168, showOverflowTooltip: true },
  { prop: 'operatorName', label: '操作员', width: 96, showOverflowTooltip: true },
  { label: '事件', width: 108, slot: 'eventType' },
  { prop: 'targetCode', label: '模板/目标', minWidth: 180, showOverflowTooltip: true },
  { prop: 'copies', label: '份数', width: 64, align: 'center' },
  { label: '状态', width: 72, slot: 'status', align: 'center' },
  { prop: 'printerName', label: '打印机', minWidth: 120, showOverflowTooltip: true },
  { prop: 'errorMessage', label: '错误摘要', minWidth: 140, showOverflowTooltip: true },
  { label: '操作', width: 72, slot: 'actions', fixed: 'right' },
];

const summaryColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  { prop: 'statDate', label: '日期', width: 108 },
  { prop: 'operatorName', label: '操作员', width: 96, showOverflowTooltip: true },
  { label: '事件', width: 108, slot: 'eventType' },
  { prop: 'targetCode', label: '模板/目标', minWidth: 180, showOverflowTooltip: true },
  { prop: 'successCount', label: '成功', width: 72, align: 'center' },
  { prop: 'failCount', label: '失败', width: 72, align: 'center' },
  { prop: 'totalCopies', label: '份数合计', width: 88, align: 'center' },
  { prop: 'lastEventAt', label: '末次时间', width: 168, showOverflowTooltip: true },
];

function stripEmpty<T extends Record<string, unknown>>(params: T): Partial<T> {
  const out: Partial<T> = { ...params };
  for (const key of Object.keys(out)) {
    const v = out[key as keyof T];
    if (v === undefined || v === '') {
      delete out[key as keyof T];
    }
  }
  return out;
}

async function loadDetail() {
  try {
    const res = await runDetail(stripEmpty({ ...detailQuery }));
    detailList.value = res?.content ?? [];
    detailTotal.value = Number(res?.totalCount ?? 0);
  } catch {
    detailList.value = [];
    detailTotal.value = 0;
  }
}

async function loadSummary() {
  try {
    const res = await runSummary(stripEmpty({ ...summaryQuery }));
    summaryList.value = res?.content ?? [];
    summaryTotal.value = Number(res?.totalCount ?? 0);
  } catch {
    summaryList.value = [];
    summaryTotal.value = 0;
  }
}

function loadCurrentTab() {
  if (mainTab.value === 'detail') {
    void loadDetail();
  } else {
    void loadSummary();
  }
}

function handleDetailSearch() {
  detailQuery.pageNo = 1;
  void loadDetail();
}

function handleDetailReset() {
  const range = formatDateRangeTodayMinus(7);
  detailQuery.keyword = undefined;
  detailQuery.eventType = undefined;
  detailQuery.status = undefined;
  detailQuery.targetCode = undefined;
  detailQuery.targetType = undefined;
  detailQuery.createdFrom = range.from;
  detailQuery.createdTo = range.to;
  detailQuery.pageNo = 1;
  void loadDetail();
}

function handleSummarySearch() {
  summaryQuery.pageNo = 1;
  void loadSummary();
}

function handleSummaryReset() {
  const range = formatDateRangeTodayMinus(7);
  summaryQuery.keyword = undefined;
  summaryQuery.eventType = undefined;
  summaryQuery.targetCode = undefined;
  summaryQuery.statDateFrom = range.from;
  summaryQuery.statDateTo = range.to;
  summaryQuery.pageNo = 1;
  void loadSummary();
}

function handleDetailPagination(pagination: { limit: number; page: number }) {
  detailQuery.pageNo = pagination.page;
  detailQuery.pageSize = pagination.limit;
  void loadDetail();
}

function handleSummaryPagination(pagination: { limit: number; page: number }) {
  summaryQuery.pageNo = pagination.page;
  summaryQuery.pageSize = pagination.limit;
  void loadSummary();
}

function openDetail(row: PrintEventLogItemDto) {
  detailRow.value = row;
  detailVisible.value = true;
}

watch(mainTab, () => {
  loadCurrentTab();
});

onMounted(() => {
  loadCurrentTab();
});
</script>

<template>
  <Page auto-content-height>
    <ElTabs v-model="mainTab" class="print-log-page">
      <ElTabPane label="事件明细" name="detail">
        <TableLayout
          v-model:query-params="detailQuery"
          :form-items="detailFormItems"
          :table-columns="detailColumns"
          :table-data="detailList"
          :total="detailTotal"
          :loading="tableLoading"
          row-key="id"
          @search="handleDetailSearch"
          @reset="handleDetailReset"
          @pagination="handleDetailPagination"
        >
          <template #eventType="{ row }">
            {{ formatPrintEventType(row.eventType) }}
          </template>
          <template #status="{ row }">
            <ElTag size="small" :type="printStatusTagType(row.status)">
              {{ formatPrintStatus(row.status) }}
            </ElTag>
          </template>
          <template #actions="{ row }">
            <ElButton link type="primary" size="small" @click="openDetail(row)">详情</ElButton>
          </template>
        </TableLayout>
      </ElTabPane>

      <ElTabPane label="日汇总" name="summary">
        <TableLayout
          v-model:query-params="summaryQuery"
          :form-items="summaryFormItems"
          :table-columns="summaryColumns"
          :table-data="summaryList"
          :total="summaryTotal"
          :loading="tableLoading"
          @search="handleSummarySearch"
          @reset="handleSummaryReset"
          @pagination="handleSummaryPagination"
        >
          <template #eventType="{ row }">
            {{ formatPrintEventType(row.eventType) }}
          </template>
        </TableLayout>
      </ElTabPane>
    </ElTabs>

    <ElDialog v-model="detailVisible" title="打印事件详情" width="640px" destroy-on-close>
      <ElDescriptions v-if="detailRow" :column="1" border size="small">
        <ElDescriptionsItem label="ID">{{ detailRow.id }}</ElDescriptionsItem>
        <ElDescriptionsItem label="时间">{{ detailRow.createdAt }}</ElDescriptionsItem>
        <ElDescriptionsItem label="操作员">
          {{ detailRow.operatorName ?? detailRow.operatorId ?? '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="事件">
          {{ formatPrintEventType(detailRow.eventType) }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="目标">
          {{ detailRow.targetType }} / {{ detailRow.targetCode }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="状态">
          <ElTag size="small" :type="printStatusTagType(detailRow.status)">
            {{ formatPrintStatus(detailRow.status) }}
          </ElTag>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="份数">{{ detailRow.copies ?? 0 }}</ElDescriptionsItem>
        <ElDescriptionsItem label="打印机">{{ detailRow.printerName ?? '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="clientJobId">
          {{ detailRow.clientJobId ?? '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="parentEventId">
          {{ detailRow.parentEventId ?? '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="contentHash">
          {{ detailRow.contentHash ?? '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="错误码">{{ detailRow.errorCode ?? '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="错误信息">
          {{ detailRow.errorMessage ?? '-' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="bizRef">
          <pre class="print-log-page__json">{{ formatBizRef(detailRow.bizRef) }}</pre>
        </ElDescriptionsItem>
      </ElDescriptions>
    </ElDialog>
  </Page>
</template>

<style scoped>
.print-log-page {
  height: 100%;
}

.print-log-page__json {
  margin: 0;
  max-height: 160px;
  overflow: auto;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
