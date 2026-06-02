<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  DocumentManagementPageQuery,
  WarehouseTypeOptionItem,
} from '#/api/warehouse/document-management.types';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElTag } from 'element-plus';

import {
  documentManagementInitApi,
  documentManagementPageApi,
} from '#/api/warehouse/document-management';

defineOptions({
  name: 'WarehouseDocumentApproval',
});

/** 单据类型 → Tag 样式 */
const TYPE_ENUM_TAG: Record<number, { type: string }> = {
  13: { type: 'primary' },
  1: { type: 'success' },
  5: { type: 'danger' },
  7: { type: 'warning' },
  9: { type: 'success' },
  6: { type: 'info' },
  4: { type: 'warning' },
};

/** 状态 → Tag 样式 */
const STATUS_ENUM_TAG: Record<number, { type: string }> = {
  1: { type: 'warning' },
  2: { type: 'primary' },
  3: { type: 'success' },
  4: { type: 'danger' },
  9: { type: 'info' },
  10: { type: 'warning' },
};

/** 单据类型 → 详情路由（与历史 path 保持一致） */
const TYPE_ENUM_VIEW_ROUTES: Record<number, string> = {
  13: '/medicineStorage/purchaseOrder/',
  1: '/medicineStorage/stockInOrder/',
  5: '/medicineStorage/returnOrder/',
  7: '/medicineStorage/stockOutOrder/',
  9: '/medicineStorage/returnToWarehouseOrder/',
  6: '/medicineStorage/profitLossOrder/',
  4: '/medicineStorage/stocktakingOrder/',
};

const router = useRouter();

const queryParams = reactive({
  pageNo: 1,
  pageSize: 50,
  locationTypeEnum: null as null | number | string,
  typeEnum: null as null | number | string,
  locationId: null as null | number | string,
  statusEnum: null as null | number | string,
  /** TableLayout QuickDateRange：字符串区间 */
  applyTimeRange: [] as string[],
  approvalTimeRange: [] as string[],
});

const warehouseTypeOption = ref<WarehouseTypeOptionItem[]>([]);
const supplyStatusOptions = ref<{ label: string; value: number | string }[]>([]);
const orderList = ref<Record<string, unknown>[]>([]);
const total = ref(0);
const loading = ref(false);

const currentWarehouseContext = computed(() => {
  const type = queryParams.locationTypeEnum;
  if (!type || warehouseTypeOption.value.length === 0) {
    return { orderOption: [] as WarehouseTypeOptionItem['orderOption'], locationOption: [] };
  }
  const current = warehouseTypeOption.value.find((item) => item.value === type);
  return {
    orderOption: current?.orderOption ?? [],
    locationOption: current?.locationOption ?? [],
  };
});

const formItems = computed<FormItemConfig[]>(() => {
  const locType = queryParams.locationTypeEnum;
  return [
    {
      label: '库房类型',
      prop: 'locationTypeEnum',
      type: 'select',
      multiple: false,
      clearable: true,
      filterable: true,
      placeholder: '请选择库房类型',
      width: '240px',
      options: warehouseTypeOption.value.map((item) => ({
        label: item.label,
        value: item.value,
      })),
    },
    {
      label: '库房',
      prop: 'locationId',
      type: 'select',
      multiple: false,
      clearable: true,
      filterable: true,
      placeholder: '请选择库房',
      disabled: !locType,
      width: '240px',
      options: currentWarehouseContext.value.locationOption.map((item) => ({
        label: item.name,
        value: item.id,
      })),
    },
    {
      label: '单据类型',
      prop: 'typeEnum',
      type: 'select',
      multiple: false,
      clearable: true,
      filterable: true,
      placeholder: '请选择单据类型',
      disabled: !locType,
      width: '240px',
      options: currentWarehouseContext.value.orderOption.map((item) => ({
        label: item.label,
        value: item.value,
      })),
    },
    {
      label: '单据状态',
      prop: 'statusEnum',
      type: 'select',
      multiple: false,
      clearable: true,
      placeholder: '请选择单据状态',
      width: '240px',
      options: supplyStatusOptions.value || [],
    },
    {
      label: '制单日期',
      prop: 'applyTimeRange',
      type: 'daterange',
      startPlaceholder: '开始日期',
      endPlaceholder: '结束日期',
      valueFormat: 'YYYY-MM-DD',
      clearable: true,
    },
    {
      label: '审核日期',
      prop: 'approvalTimeRange',
      type: 'daterange',
      startPlaceholder: '开始日期',
      endPlaceholder: '结束日期',
      valueFormat: 'YYYY-MM-DD',
      clearable: true,
    },
  ];
});

const tableColumns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    width: 56,
    align: 'center',
  },
  {
    prop: 'supplyBusNo',
    label: '订单编号',
    align: 'center',
    minWidth: 180,
    showOverflowTooltip: true,
  },
  {
    prop: 'applicantId_dictText',
    label: '申请人',
    align: 'center',
    width: 100,
    showOverflowTooltip: true,
  },
  {
    prop: 'applyTime',
    label: '申请时间',
    align: 'center',
    width: 168,
    showOverflowTooltip: true,
  },
  {
    prop: 'approvalTime',
    label: '批准时间',
    align: 'center',
    width: 168,
    showOverflowTooltip: true,
  },
  {
    prop: 'approverId_dictText',
    label: '批准人',
    align: 'center',
    width: 100,
    showOverflowTooltip: true,
  },
  {
    prop: 'locationId_dictText',
    label: '仓库',
    align: 'center',
    minWidth: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'statusEnum_enumText',
    label: '状态',
    align: 'center',
    width: 100,
    slot: 'statusEnum',
  },
  {
    prop: 'supplierId_dictText',
    label: '供应商',
    align: 'center',
    minWidth: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'typeEnum_enumText',
    label: '单据类型',
    align: 'center',
    width: 110,
    slot: 'typeEnum',
  },
  {
    label: '操作',
    width: 100,
    align: 'center',
    fixed: 'right',
    slot: 'actions',
  },
];

function isViewableType(type: unknown): boolean {
  const n = Number(type);
  return !Number.isNaN(n) && n in TYPE_ENUM_VIEW_ROUTES;
}

function tagTypeForStatus(status: unknown): 'danger' | 'info' | 'primary' | 'success' | 'warning' {
  const t = STATUS_ENUM_TAG[Number(status)]?.type;
  if (t === 'danger' || t === 'warning' || t === 'success' || t === 'primary') return t;
  return 'info';
}

function tagTypeForDocType(
  typeEnum: unknown,
): 'danger' | 'info' | 'primary' | 'success' | 'warning' {
  const t = TYPE_ENUM_TAG[Number(typeEnum)]?.type;
  if (t === 'danger' || t === 'warning' || t === 'success' || t === 'primary') return t;
  return 'info';
}

function formatRangeBoundary(range: string[] | undefined, index: number): null | string {
  const raw = range?.[index];
  if (raw === undefined || raw === null || raw === '') return null;
  const s = String(raw).slice(0, 10);
  return /^\d{4}-\d{2}-\d{2}$/.test(s) ? s : null;
}

function buildListParams(): DocumentManagementPageQuery {
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    locationTypeEnum: queryParams.locationTypeEnum,
    typeEnum: queryParams.typeEnum,
    locationId: queryParams.locationId,
    statusEnum: queryParams.statusEnum,
    applyTimeSTime: formatRangeBoundary(queryParams.applyTimeRange, 0),
    applyTimeETime: formatRangeBoundary(queryParams.applyTimeRange, 1),
    approvalTimeSTime: formatRangeBoundary(queryParams.approvalTimeRange, 0),
    approvalTimeETime: formatRangeBoundary(queryParams.approvalTimeRange, 1),
  };
}

function handleTableQuery(params: Record<string, unknown>) {
  queryParams.pageNo = (params.pageNo as number) ?? 1;
  getList();
}

function handleReset() {
  queryParams.pageNo = 1;
  queryParams.pageSize = 50;
  queryParams.locationTypeEnum = null;
  queryParams.typeEnum = null;
  queryParams.locationId = null;
  queryParams.statusEnum = null;
  queryParams.applyTimeRange = [];
  queryParams.approvalTimeRange = [];
  getList();
}

function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  getList();
}

async function initOptions() {
  try {
    const data = await documentManagementInitApi();
    warehouseTypeOption.value = data.warehouseTypeOption;
    supplyStatusOptions.value = data.supplyStatusOptions;
  } catch (error) {
    console.error('初始化失败:', error);
    warehouseTypeOption.value = [];
    supplyStatusOptions.value = [];
  }
}

async function getList() {
  loading.value = true;
  try {
    const params = buildListParams();
    const data = await documentManagementPageApi(params);
    orderList.value = (data.records || []) as Record<string, unknown>[];
    total.value = Number(data.total) || 0;
  } catch (error) {
    console.error('获取列表失败:', error);
  } finally {
    loading.value = false;
  }
}

function handleView(row: Record<string, unknown>) {
  const typeEnum = row.typeEnum;
  const path = TYPE_ENUM_VIEW_ROUTES[Number(typeEnum)];
  if (!path) {
    console.warn('未知的单据类型:', typeEnum);
    return;
  }
  router.push({
    path,
    query: { busNo: String(row.supplyBusNo ?? '') },
  });
}

watch(
  () => queryParams.locationTypeEnum,
  (newVal, oldVal) => {
    if (newVal !== oldVal) {
      queryParams.typeEnum = null;
      queryParams.locationId = null;
    }
  },
);

onMounted(async () => {
  await initOptions();
  getList();
});
</script>

<template>
  <Page>
    <TableLayout
      :table-data="orderList"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      row-key="supplyBusNo"
      :show-top-query="true"
      :show-pagination="true"
      :highlight-current-row="true"
      @query="handleTableQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #statusEnum="{ row }">
        <ElTag :type="tagTypeForStatus(row.statusEnum)">
          {{ row.statusEnum_enumText }}
        </ElTag>
      </template>
      <template #typeEnum="{ row }">
        <ElTag :type="tagTypeForDocType(row.typeEnum)">
          {{ row.typeEnum_enumText }}
        </ElTag>
      </template>
      <template #actions="{ row }">
        <ElButton v-if="isViewableType(row.typeEnum)" link type="primary" @click="handleView(row)">
          查看
        </ElButton>
      </template>
    </TableLayout>
  </Page>
</template>
