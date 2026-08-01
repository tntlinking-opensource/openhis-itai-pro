<!-- 退货入库单：只读壳单台账（退库 agree 自动生成）。 -->
<script lang="ts" setup>
import type { LocationOptionDto } from '#/api/inventory/warehouse/return-stock-in';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import {
  loadReturnStockInFormInitApi,
  mapReturnStockInInitToCabinetRows,
  pageReturnStockInWorkbenchListApi,
} from '#/api/inventory/warehouse/return-stock-in';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import {
  enrichStockOutQueueCardRow,
  INV_ALL_LOCATIONS_FILTER_ID,
  resolveDefaultAllLocationsFilterValue,
} from '#/utils/inventory/inventory-workbench-list';

import ReturnStockInAsidePane from './return-stock-in-aside-pane.vue';
import ReturnStockInMainPane from './return-stock-in-main-pane.vue';

defineOptions({ name: 'InventoryWarehouseReturnStockInPage' });

const route = useRoute();

/** 侧栏默认日期：当月 1 号至当天 */
function createDefaultMonthRange(): [string, string] {
  const d = new Date();
  const y = d.getFullYear();
  const m = d.getMonth();
  const pad = (n: number) => String(n).padStart(2, '0');
  return [`${y}-${pad(m + 1)}-01`, `${y}-${pad(m + 1)}-${pad(d.getDate())}`];
}

const queryParams = ref<{ keyword?: string }>({ keyword: '' });
const listDateRange = ref<[string, string] | null>(createDefaultMonthRange());
const listKeywordModel = computed({
  get: () => queryParams.value.keyword ?? '',
  set: (v: string) => {
    queryParams.value.keyword = v;
  },
});

const tableForm = ref<{ purposeLocationId?: number | string }>({
  purposeLocationId: INV_ALL_LOCATIONS_FILTER_ID,
});

const warehouseRawOptions = ref<LocationOptionDto[]>([]);
const departmentRawOptions = ref<LocationOptionDto[]>([]);
const pharmacyRawOptions = ref<LocationOptionDto[]>([]);
const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);
const LIST_PAGE_SIZE = 200;

/** 从队列行解析业务单号 */
function resolveBusNo(row?: null | Record<string, unknown>): string | undefined {
  if (!row) return undefined;
  for (const k of ['busNo', 'supplyBusNo', 'orderBusNo']) {
    const raw = row[k];
    if (raw === undefined || raw === null) continue;
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return undefined;
}

const selectedBusNo = computed(() => resolveBusNo(selectedOrder.value) ?? '');
/** 壳单禁止草稿新建 */
const isDraftOrder = computed(() => false);

function toQueueRow(r: Record<string, unknown>): Record<string, unknown> {
  return enrichStockOutQueueCardRow(r, {
    sourceOptions: pharmacyRawOptions.value,
    purposeOptions: warehouseRawOptions.value,
  });
}

/** 加载药库/药房/科室选项；侧栏默认「全部库房」 */
async function init() {
  try {
    const res = await loadReturnStockInFormInitApi();
    const data = res.data;
    warehouseRawOptions.value = data?.warehouseOptions ?? [];
    departmentRawOptions.value = data?.departmentOptions ?? [];
    pharmacyRawOptions.value = data?.pharmacyOptions ?? [];
    cabinetListOptions.value = mapReturnStockInInitToCabinetRows(warehouseRawOptions.value);
    if (
      tableForm.value.purposeLocationId === undefined ||
      tableForm.value.purposeLocationId === null ||
      String(tableForm.value.purposeLocationId).trim() === '' ||
      tableForm.value.purposeLocationId === 0
    ) {
      tableForm.value.purposeLocationId = resolveDefaultAllLocationsFilterValue(
        cabinetListOptions.value,
      );
    }
  } catch {
    warehouseRawOptions.value = [];
    departmentRawOptions.value = [];
    pharmacyRawOptions.value = [];
    cabinetListOptions.value = [];
  }
}

/** 刷新历史退货入库单队列（库位 0 = 全部可见药库） */
async function getList() {
  const dr = listDateRange.value;
  try {
    const { records } = await pageReturnStockInWorkbenchListApi({
      purposeLocationId: tableForm.value.purposeLocationId,
      keyword: queryParams.value.keyword?.trim() || undefined,
      applyTimeBegin: dr?.[0],
      applyTimeEnd: dr?.[1],
      pageNo: 1,
      pageSize: LIST_PAGE_SIZE,
    });
    orderList.value = records.map((r) => toQueueRow(r as Record<string, unknown>));
  } catch {
    orderList.value = [];
  }
}

/** 选中队列卡片 */
function handleOrderClick(order: Record<string, unknown>) {
  selectedOrder.value = order;
}

watch(
  () => route.query,
  async (newQuery) => {
    const busNo = newQuery.busNo;
    if (!busNo || Array.isArray(busNo)) return;
    if (tableForm.value.purposeLocationId === undefined && cabinetListOptions.value.length === 0) {
      await init();
    }
    queryParams.value.keyword = String(busNo);
    await getList();
    const row = orderList.value.find((item) => resolveBusNo(item) === String(busNo));
    if (!row) return;
    selectedOrder.value = row;
  },
  { immediate: true },
);

onMounted(async () => {
  await init();
  await getList();
});
</script>

<template>
  <WarehouseDocWorkstation
    queue-title=""
    aside-dense
    :show-aside-filter="false"
    :show-toolbar="false"
  >
    <template #aside-queue>
      <ReturnStockInAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:purpose-location-id="tableForm.purposeLocationId"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :selected-order="selectedOrder"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <ReturnStockInMainPane
      class="min-h-0 flex-1"
      :bus-no="selectedBusNo"
      :is-draft-order="isDraftOrder"
      :reset-key="mainPaneResetKey"
      :sidebar-purpose-location-id="tableForm.purposeLocationId"
      :warehouse-options="warehouseRawOptions"
      :department-options="departmentRawOptions"
      :pharmacy-options="pharmacyRawOptions"
    />
  </WarehouseDocWorkstation>
</template>
