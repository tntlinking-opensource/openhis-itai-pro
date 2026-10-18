<!--
  调价明细：T2 只读查询列表，行展开展示批号盈亏明细。
  筛选默认仅前端：生效时间当月（与财务审批一致）；init 只取 Tab/分页等选项。
  状态分层 — 业务：生效时间窗、项目类型筛选、列表行；视图：分页、展开行。
-->
<script setup lang="ts">
import type {
  InitPriceAdjustmentDetailResponse,
  PagePriceAdjustmentApprovedLineParams,
  PriceAdjustmentApprovedLineListRowDto,
} from '#/api/finance/price-adjustment-detail';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTableColumn,
} from 'element-plus';

import {
  initPriceAdjustmentDetailApi,
  pagePriceAdjustmentApprovedLineApi,
} from '#/api/finance/price-adjustment-detail';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
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

import PriceAdjustmentApprovedLineExpandPanel from './components/price-adjustment-approved-line-expand-panel.vue';
import PriceAdjustmentImpactAmount from './components/price-adjustment-impact-amount.vue';

defineOptions({ name: 'FinancePriceAdjustmentDetail' });

const ALL_ITEM_TABLE_TAB = '';
const PAGE_TITLE = '调价明细';

interface QueryFormState {
  pageNo: number;
  pageSize: number;
  approveTimeRange: [string, string];
  itemTable?: string;
  itemNameKeyword?: string;
  itemCodeKeyword?: string;
  busNo?: string;
}

/** 当前自然月起止（与财务审批同口径，带时分秒）。 */
function createCurrentMonthRange(): [string, string] {
  const d = new Date();
  const y = d.getFullYear();
  const m = d.getMonth();
  const pad = (n: number) => String(n).padStart(2, '0');
  const lastDay = new Date(y, m + 1, 0).getDate();
  return [
    `${y}-${pad(m + 1)}-01 00:00:00`,
    `${y}-${pad(m + 1)}-${pad(lastDay)} 23:59:59`,
  ];
}

const [defaultFrom, defaultTo] = createCurrentMonthRange();

const pageInit = ref<InitPriceAdjustmentDetailResponse | null>(null);
const tableData = ref<PriceAdjustmentApprovedLineListRowDto[]>([]);
const total = ref(0);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const queryParams = reactive<QueryFormState>({
  pageNo: 1,
  pageSize: 20,
  approveTimeRange: [defaultFrom, defaultTo],
  itemTable: ALL_ITEM_TABLE_TAB,
  itemNameKeyword: undefined,
  itemCodeKeyword: undefined,
  busNo: undefined,
});

const { run: runInit, loading: initLoading } = useRequest(initPriceAdjustmentDetailApi, {
  manual: true,
  onSuccess: (data) => {
    pageInit.value = data ?? null;
    // 生效时间默认由前端自带，不吃 init.defaultApproveTime*
    if (data?.defaultPageSize) {
      queryParams.pageSize = data.defaultPageSize;
    }
  },
});

const { run: runPage, loading: pageLoading } = useRequest(pagePriceAdjustmentApprovedLineApi, {
  manual: true,
  onSuccess: (data) => {
    const parsed = parsePagedRowsFromApiData<PriceAdjustmentApprovedLineListRowDto>(data);
    tableData.value = parsed.content;
    total.value = parsed.totalCount;
    if (typeof parsed.pageNo === 'number') {
      queryParams.pageNo = parsed.pageNo;
    }
    if (typeof parsed.pageSize === 'number') {
      queryParams.pageSize = parsed.pageSize;
    }
  },
});

const tableLoading = computed(() => initLoading.value || pageLoading.value);

const itemTableTabOptions = computed<ApiOptionItem[]>(() => {
  const fromInit = (pageInit.value?.itemTableTabOptions ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
  return [{ label: '全部', value: ALL_ITEM_TABLE_TAB }, ...fromInit];
});

/** 下拉选项转为 ElSelect 可用结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 生效时间与 daterange 双向绑定。 */
const approveTimeRange = computed({
  get() {
    const [from, to] = queryParams.approveTimeRange;
    if (from && to) {
      return [from, to] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.approveTimeRange = value ? [value[0], value[1]] : ['', ''];
  },
});

/** 组装分页查询请求体。 */
function buildPageRequest(): PagePriceAdjustmentApprovedLineParams | null {
  const [from, to] = queryParams.approveTimeRange ?? [];
  if (!from?.trim() || !to?.trim()) {
    return null;
  }
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    approveTimeFrom: from.trim(),
    approveTimeTo: to.trim(),
    itemTable: queryParams.itemTable?.trim() || undefined,
    itemNameKeyword: queryParams.itemNameKeyword?.trim() || undefined,
    itemCodeKeyword: queryParams.itemCodeKeyword?.trim() || undefined,
    busNo: queryParams.busNo?.trim() || undefined,
  };
}

/** 按当前筛选与分页拉取已生效调价明细。 */
async function getList() {
  const body = buildPageRequest();
  if (!body) {
    ElMessage.warning('请选择生效时间');
    return;
  }
  await runPage(body);
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 顶部查询：回到第一页。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 重置为前端默认（生效时间当月），保留分页大小。 */
function handleReset() {
  const [from, to] = createCurrentMonthRange();
  queryParams.approveTimeRange = [from, to];
  queryParams.itemNameKeyword = undefined;
  queryParams.itemCodeKeyword = undefined;
  queryParams.busNo = undefined;
  queryParams.itemTable = ALL_ITEM_TABLE_TAB;
  queryParams.pageNo = 1;
  void getList();
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
            <ElFormItem label="生效时间">
              <ElDatePicker
                v-model="approveTimeRange"
                type="datetimerange"
                value-format="YYYY-MM-DD HH:mm:ss"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                :clearable="false"
                :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATERANGE]"
                @change="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="项目类型">
              <ElSelect
                v-model="queryParams.itemTable"
                clearable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(itemTableTabOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="项目名称">
              <ElInput
                v-model="queryParams.itemNameKeyword"
                clearable
                placeholder="模糊匹配"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="院内编码">
              <ElInput
                v-model="queryParams.itemCodeKeyword"
                clearable
                placeholder="模糊匹配"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="调价单号">
              <ElInput
                v-model="queryParams.busNo"
                clearable
                placeholder="模糊匹配"
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
        <ReportListToolbar :title="`${PAGE_TITLE} · 明细列表`" :total="total" />
      </template>

      <template #table>
        <ReportTableScroll ref="tableScrollRef">
          <template #default="{ tableHeight }">
            <ReportElTable
              class="price-adjustment-detail-table"
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              row-key="id"
            >
              <ElTableColumn type="expand" width="48" align="center">
                <template #default="{ row }">
                  <PriceAdjustmentApprovedLineExpandPanel :line-id="row.id" />
                </template>
              </ElTableColumn>
              <ElTableColumn type="index" label="序号" width="60" align="center" />
              <ElTableColumn
                prop="busNo"
                label="调价单号"
                min-width="140"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn prop="approveTime" label="生效时间" width="170" align="center" />
              <ElTableColumn prop="itemTableText" label="项目类型" width="100" align="center" />
              <ElTableColumn
                prop="itemName"
                label="项目名称"
                min-width="160"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="itemCode"
                label="院内编码"
                width="120"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="adjustmentStyleText"
                label="展示口径"
                width="100"
                align="center"
              />
              <ElTableColumn
                prop="adjustmentReasonText"
                label="调价原因"
                width="120"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="盈亏合计"
                prop="totalImpact"
                width="130"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <PriceAdjustmentImpactAmount :value="row.totalImpact" />
                </template>
              </ElTableColumn>
              <ElTableColumn prop="operatorName" label="制单人" width="100" align="center" />
              <ElTableColumn prop="approverName" label="审批人" width="100" align="center" />
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
  </Page>
</template>

<style scoped lang="scss">
.price-adjustment-detail-table {
  :deep(.el-table__expanded-cell) {
    padding: 0;
    background: var(--el-bg-color);
  }

  :deep(.el-table__expanded-cell:hover) {
    background: var(--el-bg-color) !important;
  }
}
</style>
