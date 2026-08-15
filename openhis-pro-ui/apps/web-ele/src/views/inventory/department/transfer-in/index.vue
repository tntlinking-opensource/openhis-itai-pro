<!-- 科室调拨入库（只读壳单）：工作站 + 侧栏 + 右侧明细。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useUserStore } from '@vben/stores';

import {
  loadTransferInFormInitApi,
  mapDepartmentTransferInInitToCabinetRows,
  pageDepartmentTransferInWorkbenchListApi,
} from '#/api/inventory/department/transfer-in';
import TransferAsidePane from '@inventory/components/transfer/transfer-aside-pane.vue';
import TransferMainPane from '@inventory/components/transfer/transfer-main-pane.vue';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  enrichStockOutQueueCardRow,
  mapInitOptionDtoToApiItems,
  mapInventoryPractitionerInitOptions,
  INV_ITEM_TABLE_TYPE_DEVICE,
  normalizeInventoryItemTableTypeOptions,
  normalizeSidebarLocationFilterValue,
  resolveDefaultAllLocationsFilterValue,
} from '#/utils/inventory/inventory-workbench-list';

defineOptions({ name: 'InventoryDepartmentTransferInPage' });

const route = useRoute();
const userStore = useUserStore();

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
const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const warehouseOptions = ref<ApiOptionItem[]>([]);
const pharmacyOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const itemTableTypeOptions = ref<ApiOptionItem[]>([]);
const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);

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

function toQueueRow(r: Record<string, unknown>): Record<string, unknown> {
  return enrichStockOutQueueCardRow(r, {
    sourceOptions: pharmacyOptions.value,
    purposeOptions: pharmacyOptions.value,
  });
}

async function init() {
  try {
    const res = await loadTransferInFormInitApi();
    const data = res.data;
    pharmacyOptions.value = mapInitOptionDtoToApiItems(data?.pharmacyLocationOptions);
    warehouseOptions.value = mapInitOptionDtoToApiItems(data?.warehouseLocationOptions);
    practitionerOptions.value = mapInventoryPractitionerInitOptions(
      [data?.pharmacistOptions, data?.warehouseKeeperOptions],
      userStore.userInfo as null | Record<string, unknown>,
    );
    itemTableTypeOptions.value = normalizeInventoryItemTableTypeOptions(
      data?.inboundItemTableTypeOptions,
      INV_ITEM_TABLE_TYPE_DEVICE,
    );
    cabinetListOptions.value = mapDepartmentTransferInInitToCabinetRows(
      data?.pharmacyLocationOptions,
    );
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
    pharmacyOptions.value = [];
    warehouseOptions.value = [];
    practitionerOptions.value = [];
    itemTableTypeOptions.value = [];
    cabinetListOptions.value = [];
  }
}

async function getList() {
  const dr = listDateRange.value;
  try {
    const { records } = await pageDepartmentTransferInWorkbenchListApi({
      purposeLocationId: normalizeSidebarLocationFilterValue(tableForm.value.purposeLocationId),
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
    queue-title=""
    aside-dense
    :show-aside-filter="false"
    :show-toolbar="false"
  >
    <template #aside-queue>
      <TransferAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:filter-location-id="tableForm.purposeLocationId"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :selected-order="selectedOrder"
        :show-add="false"
        filter-location-label="调入科室药房"
        queue-title="历史调拨入库单"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>
    <TransferMainPane
      :queue-card-row="selectedOrder"
      class="min-h-0 flex-1"
      domain="department"
      variant="in"
      :bus-no="selectedBusNo"
      :reset-key="mainPaneResetKey"
      :warehouse-options="warehouseOptions"
      :pharmacy-options="pharmacyOptions"
      :practitioner-options="practitionerOptions"
      :item-table-type-options="itemTableTypeOptions"
      head-section-title="科室调拨入库单"
    />
  </WarehouseDocWorkstation>
</template>
