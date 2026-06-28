<!--
  调价明细：T2 只读查询列表，行展开展示批号盈亏明细。
  状态分层 — 业务：生效时间窗、项目类型筛选、列表行；视图：分页、展开行。
-->
<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  InitPriceAdjustmentDetailResponse,
  PagePriceAdjustmentApprovedLineParams,
  PriceAdjustmentApprovedLineListRowDto,
} from '#/api/finance/price-adjustment-detail';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  initPriceAdjustmentDetailApi,
  pagePriceAdjustmentApprovedLineApi,
} from '#/api/finance/price-adjustment-detail';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import PriceAdjustmentApprovedLineExpandPanel from './components/price-adjustment-approved-line-expand-panel.vue';
import PriceAdjustmentImpactAmount from './components/price-adjustment-impact-amount.vue';

defineOptions({ name: 'FinancePriceAdjustmentDetail' });

const ALL_ITEM_TABLE_TAB = '';

interface QueryFormState {
  pageNo: number;
  pageSize: number;
  approveTimeRange: [string, string];
  itemTable?: string;
  itemNameKeyword?: string;
  itemCodeKeyword?: string;
  busNo?: string;
}

const pageInit = ref<InitPriceAdjustmentDetailResponse | null>(null);
const tableData = ref<PriceAdjustmentApprovedLineListRowDto[]>([]);
const total = ref(0);

const queryParams = reactive<QueryFormState>({
  pageNo: 1,
  pageSize: 20,
  approveTimeRange: ['', ''],
  itemTable: ALL_ITEM_TABLE_TAB,
  itemNameKeyword: undefined,
  itemCodeKeyword: undefined,
  busNo: undefined,
});

const { run: runInit, loading: initLoading } = useRequest(initPriceAdjustmentDetailApi, {
  manual: true,
  onSuccess: (data) => {
    pageInit.value = data ?? null;
    if (data?.defaultApproveTimeFrom && data?.defaultApproveTimeTo) {
      queryParams.approveTimeRange = [data.defaultApproveTimeFrom, data.defaultApproveTimeTo];
    }
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

const loading = computed(() => initLoading.value || pageLoading.value);

const itemTableTabOptions = computed<ApiOptionItem[]>(() => {
  const fromInit = (pageInit.value?.itemTableTabOptions ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
  return [{ label: '全部', value: ALL_ITEM_TABLE_TAB }, ...fromInit];
});

// 输入框回车触发查询。
function handleKeywordEnter(event: KeyboardEvent) {
  if (event.key !== 'Enter') {
    return;
  }
  queryParams.pageNo = 1;
  void getList();
}

const keywordInputProps = {
  onKeyup: handleKeywordEnter,
};

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '生效时间',
    prop: 'approveTimeRange',
    type: 'datetimerange',
    valueFormat: 'YYYY-MM-DD HH:mm:ss',
  },
  {
    label: '项目类型',
    prop: 'itemTable',
    type: 'select',
    multiple: false,
    clearable: true,
    options: itemTableTabOptions.value,
  },
  {
    label: '项目名称',
    prop: 'itemNameKeyword',
    type: 'input',
    placeholder: '模糊匹配',
    inputProps: keywordInputProps,
  },
  {
    label: '院内编码',
    prop: 'itemCodeKeyword',
    type: 'input',
    placeholder: '模糊匹配',
    inputProps: keywordInputProps,
  },
  {
    label: '调价单号',
    prop: 'busNo',
    type: 'input',
    placeholder: '模糊匹配',
    inputProps: keywordInputProps,
  },
]);

const tableColumns: TableColumn[] = [
  { type: 'expand', width: 48, slot: 'rowExpand', align: 'center', headerAlign: 'center' },
  { type: 'index', label: '序号', width: 60, align: 'center', headerAlign: 'center' },
  {
    prop: 'busNo',
    label: '调价单号',
    minWidth: 140,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'approveTime',
    label: '生效时间',
    width: 170,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'itemTableText',
    label: '项目类型',
    width: 100,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'itemName',
    label: '项目名称',
    minWidth: 160,
    align: 'left',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'itemCode',
    label: '院内编码',
    width: 120,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'adjustmentStyleText',
    label: '展示口径',
    width: 100,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'adjustmentReasonText',
    label: '调价原因',
    width: 120,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'totalImpact',
    label: '盈亏合计',
    width: 130,
    align: 'right',
    headerAlign: 'center',
    slot: 'totalImpact',
  },
  {
    prop: 'operatorName',
    label: '制单人',
    width: 100,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'approverName',
    label: '审批人',
    width: 100,
    align: 'center',
    headerAlign: 'center',
  },
];

// 组装分页查询请求体。
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

// 按当前筛选与分页拉取已生效调价明细。
async function getList() {
  const body = buildPageRequest();
  if (!body) {
    return;
  }
  await runPage(body);
}

// 顶部查询：回到第一页。
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

// 重置筛选为 init 默认时间窗，保留分页大小。
function handleReset() {
  const init = pageInit.value;
  queryParams.approveTimeRange =
    init?.defaultApproveTimeFrom && init?.defaultApproveTimeTo
      ? [init.defaultApproveTimeFrom, init.defaultApproveTimeTo]
      : queryParams.approveTimeRange;
  queryParams.itemNameKeyword = undefined;
  queryParams.itemCodeKeyword = undefined;
  queryParams.busNo = undefined;
  queryParams.itemTable = ALL_ITEM_TABLE_TAB;
  queryParams.pageNo = 1;
  void getList();
}

// 分页变更后重新查询。
function handlePagination(pagination: { page: number; limit: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  void getList();
}

onMounted(async () => {
  await runInit();
  await getList();
});
</script>

<template>
  <Page auto-content-height>
    <div class="box-border flex min-h-0 flex-1 flex-col gap-2 overflow-hidden p-2">
      <TableLayout
        class="price-adjustment-detail-table"
        :table-data="tableData"
        :loading="loading"
        :total="total"
        :query-params="queryParams as Record<string, unknown>"
        :form-items="formItems"
        :table-columns="tableColumns"
        :show-top-query="true"
        :show-pagination="true"
        row-key="id"
        border
        stripe
        @query="handleQuery"
        @reset="handleReset"
        @pagination="handlePagination"
      >
        <template #rowExpand="{ row }">
          <PriceAdjustmentApprovedLineExpandPanel :line-id="row.id" />
        </template>

        <template #totalImpact="{ row }">
          <PriceAdjustmentImpactAmount :value="row.totalImpact" />
        </template>
      </TableLayout>
    </div>
  </Page>
</template>

<style scoped lang="scss">
.price-adjustment-detail-table {
  :deep(.vxe-body--expanded-cell) {
    padding: 0;
    background: var(--el-bg-color);
  }

  :deep(.vxe-body--expanded-column) {
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  :deep(.row--stripe .vxe-body--expanded-cell) {
    background: var(--el-bg-color);
  }
}
</style>
