<!-- 科室入库单（只读壳单台账）：工作站 + 侧栏卡片队列 + 右侧明细。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import {
  loadStockInFormInitApi,
  mapDepartmentStockInInitToCabinetRows,
  mapDepartmentStockInInitWarehouseToCabinetRows,
  pageDepartmentStockInWorkbenchListApi,
} from '#/api/inventory/department/stock-in';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  mapInitOptionDtoToApiItems,
  normalizeSidebarLocationFilterValue,
  resolveDefaultAllLocationsFilterValue,
} from '#/utils/inventory/inventory-workbench-list';
import {
  normalizePharmacyStockInStatusSort,
  pharmacyStockInStatusLabel,
} from '#/utils/inventory/pharmacy-stock-in-doc-actions';

import DepartmentStockInAsidePane from './department-stock-in-aside-pane.vue';
import DepartmentStockInMainPane from './department-stock-in-main-pane.vue';

defineOptions({ name: 'InventoryDepartmentStockInPage' });

const route = useRoute();

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

const tableForm = ref<{
  purposeLocationId?: number | string;
  sourceLocationId?: number | string;
}>({
  purposeLocationId: INV_ALL_LOCATIONS_FILTER_ID,
  sourceLocationId: INV_ALL_LOCATIONS_FILTER_ID,
});

const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const warehouseCabinetOptions = ref<Record<string, unknown>[]>([]);
const warehouseOptions = ref<ApiOptionItem[]>([]);
const pharmacyOptions = ref<ApiOptionItem[]>([]);
const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);

function toQueueRow(r: Record<string, unknown>): Record<string, unknown> {
  return {
    ...r,
    supplyBusNo: r.busNo,
    statusEnum_enumText: pharmacyStockInStatusLabel(
      normalizePharmacyStockInStatusSort(r.statusEnum as number | string | undefined),
    ),
  };
}

function resolveBusNo(row?: null | Record<string, unknown>): string | undefined {
  if (!row) return undefined;
  for (const k of ['busNo', 'supplyBusNo']) {
    const raw = row[k];
    if (raw === undefined || raw === null) continue;
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return undefined;
}

const selectedBusNo = computed(() => resolveBusNo(selectedOrder.value) ?? '');

async function init() {
  try {
    const res = await loadStockInFormInitApi({});
    const data = res.data;
    pharmacyOptions.value = mapInitOptionDtoToApiItems(data?.departmentPharmacyLocationOptions);
    warehouseOptions.value = mapInitOptionDtoToApiItems(data?.warehouseLocationOptions);
    cabinetListOptions.value = mapDepartmentStockInInitToCabinetRows(
      data?.departmentPharmacyLocationOptions,
    );
    warehouseCabinetOptions.value = mapDepartmentStockInInitWarehouseToCabinetRows(
      data?.warehouseLocationOptions,
    );
    tableForm.value.purposeLocationId = resolveDefaultAllLocationsFilterValue(
      cabinetListOptions.value,
    );
    tableForm.value.sourceLocationId = resolveDefaultAllLocationsFilterValue(
      warehouseCabinetOptions.value,
    );
  } catch {
    pharmacyOptions.value = [];
    warehouseOptions.value = [];
    cabinetListOptions.value = [];
    warehouseCabinetOptions.value = [];
  }
}

async function getList() {
  const dr = listDateRange.value;
  try {
    const { records } = await pageDepartmentStockInWorkbenchListApi({
      purposeLocationId: normalizeSidebarLocationFilterValue(tableForm.value.purposeLocationId),
      sourceLocationId: normalizeSidebarLocationFilterValue(tableForm.value.sourceLocationId),
      keyword: queryParams.value.keyword?.trim() || undefined,
      applyTimeBegin: dr?.[0],
      applyTimeEnd: dr?.[1],
    });
    orderList.value = records.map((r) => toQueueRow(r));
  } catch {
    orderList.value = [];
  }
}

function handleOrderClick(order: Record<string, unknown>) {
  selectedOrder.value = order;
}

watch(
  () => route.query,
  async (q) => {
    const busNo = q.busNo;
    if (!busNo || Array.isArray(busNo)) return;
    if (cabinetListOptions.value.length === 0) await init();
    queryParams.value.keyword = String(busNo);
    await getList();
    const row = orderList.value.find((item) => resolveBusNo(item) === String(busNo));
    if (row) selectedOrder.value = row;
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
    aside-width="280px"
    queue-title=""
    aside-dense
    :show-aside-filter="false"
    :show-toolbar="false"
  >
    <template #aside-queue>
      <DepartmentStockInAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:purpose-location-id="tableForm.purposeLocationId"
        v-model:source-location-id="tableForm.sourceLocationId"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :selected-order="selectedOrder"
        :warehouse-list-options="warehouseCabinetOptions"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <DepartmentStockInMainPane
      class="min-h-0 flex-1"
      :bus-no="selectedBusNo"
      :reset-key="mainPaneResetKey"
      :pharmacy-options="pharmacyOptions"
      :warehouse-options="warehouseOptions"
    />
  </WarehouseDocWorkstation>
</template>
