<!-- 药库盘库单：工作站 + 侧栏卡片队列 + 右侧明细；布局对齐进货单。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';
import type { WarehouseStocktakeLocationOption } from '#/api/inventory/warehouse/stocktake';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@vben/utils';

import {
  initWarehouseStocktakeWorkbenchApi,
  pageWarehouseStocktakeWorkbenchListApi,
} from '#/api/inventory/warehouse/stocktake';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import StocktakeAsidePane from '@inventory/components/stocktake/stocktake-aside-pane.vue';
import StocktakeMainPane from '@inventory/components/stocktake/stocktake-main-pane.vue';

defineOptions({
  name: 'InventoryWarehouseStocktakePage',
});

const route = useRoute();

function createDefaultMonthRange(): [string, string] {
  const d = new Date();
  const y = d.getFullYear();
  const m = d.getMonth();
  const pad = (n: number) => String(n).padStart(2, '0');
  const start = `${y}-${pad(m + 1)}-01`;
  const end = `${y}-${pad(m + 1)}-${pad(d.getDate())}`;
  return [start, end];
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
  purposeLocationId: undefined,
});

const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const locationRawOptions = ref<WarehouseStocktakeLocationOption[]>([]);
const itemTableTypeOptions = ref<ApiOptionItem[]>([]);

const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);
const isAddDisabled = ref(false);

function isDraftOrderRow(row?: null | Record<string, unknown>): boolean {
  return Boolean(row?._draft);
}

function createDraftOrderRow(): Record<string, unknown> {
  return {
    _draft: true,
    _clientDraftId: `draft:${Date.now()}:${Math.random().toString(36).slice(2, 9)}`,
    statusEnum_enumText: '编辑中',
    applyTime: formatDateTime(new Date()),
    purposeLocationId: tableForm.value.purposeLocationId,
  };
}

function resolveStocktakeBusNo(row?: null | Record<string, unknown>): string | undefined {
  if (!row) return undefined;
  for (const k of ['busNo', 'supplyBusNo']) {
    const raw = row[k];
    if (raw === undefined || raw === null) continue;
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return undefined;
}

const selectedBusNo = computed(() => {
  const o = selectedOrder.value;
  if (!o) return '';
  return resolveStocktakeBusNo(o) ?? '';
});

const isDraftOrder = computed(() => Boolean(selectedOrder.value?._draft));

async function init() {
  try {
    const data = await initWarehouseStocktakeWorkbenchApi();
    cabinetListOptions.value = data.cabinetListOptions ?? [];
    locationRawOptions.value = data.locationRawOptions ?? [];
    itemTableTypeOptions.value = data.itemTableTypeOptions ?? [];
    if (tableForm.value.purposeLocationId === undefined && cabinetListOptions.value.length > 0) {
      const first = cabinetListOptions.value[0] as Record<string, unknown>;
      tableForm.value.purposeLocationId = (first.id ?? first.value) as number | string | undefined;
    }
  } catch {
    cabinetListOptions.value = [];
    locationRawOptions.value = [];
    itemTableTypeOptions.value = [];
  }
}

async function getList() {
  const dr = listDateRange.value;
  const draftRows = orderList.value.filter((r) => r._draft);
  try {
    const data = await pageWarehouseStocktakeWorkbenchListApi({
      keyword: queryParams.value.keyword,
      purposeLocationId: tableForm.value.purposeLocationId,
      ...(dr?.[0] ? { applyTimeBegin: dr[0] } : {}),
      ...(dr?.[1] ? { applyTimeEnd: dr[1] } : {}),
    });
    orderList.value = [...draftRows, ...data.records];
  } catch {
    orderList.value = [...draftRows];
  }
}

function handleAdd() {
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  mainPaneResetKey.value += 1;
  nextTick(() => {
    isAddDisabled.value = true;
  });
}

function handleOrderClick(order: Record<string, unknown>) {
  selectedOrder.value = order;
  if (isDraftOrderRow(order)) {
    mainPaneResetKey.value += 1;
    return;
  }
  isAddDisabled.value = false;
}

function handleLocationChange(locationId?: number | string) {
  tableForm.value.purposeLocationId = locationId;
  void getList();
}

async function handleDocumentSaved(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  await getList();
  const busNo = payload.busNo?.trim();
  if (!busNo) return;
  const row = orderList.value.find((item) => resolveStocktakeBusNo(item) === busNo);
  if (!row) return;
  selectedOrder.value = row;
  isAddDisabled.value = false;
}

async function handleDocumentRemoved() {
  if (selectedOrder.value?._draft) {
    orderList.value = orderList.value.filter((r) => r !== selectedOrder.value);
    selectedOrder.value = null;
    mainPaneResetKey.value += 1;
    isAddDisabled.value = false;
    return;
  }
  orderList.value = orderList.value.filter((r) => !r._draft);
  selectedOrder.value = null;
  mainPaneResetKey.value += 1;
  isAddDisabled.value = false;
  await getList();
}

watch(
  () => route.query,
  async (newQuery) => {
    const busNo = newQuery.busNo;
    if (!busNo || Array.isArray(busNo)) return;
    if (tableForm.value.purposeLocationId === undefined && cabinetListOptions.value.length === 0) {
      await init();
    }
    queryParams.value.keyword = busNo;
    await getList();
    const row = orderList.value.find((item) => resolveStocktakeBusNo(item) === String(busNo));
    if (!row) return;
    selectedOrder.value = row;
    isAddDisabled.value = false;
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
      <StocktakeAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:location-id="tableForm.purposeLocationId"
        :add-disabled="isAddDisabled"
        :cabinet-list-options="cabinetListOptions"
        location-label="盘点药库"
        :orders="orderList"
        :selected-order="selectedOrder"
        @add="handleAdd"
        @location-change="handleLocationChange"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <StocktakeMainPane
      class="min-h-0 flex-1"
      variant="warehouse"
      :bus-no="selectedBusNo"
      :is-draft-order="isDraftOrder"
      :item-table-type-options="itemTableTypeOptions"
      :location-options="locationRawOptions"
      location-field-label="盘点药库"
      head-section-title="药库盘库单"
      :reset-key="mainPaneResetKey"
      :sidebar-purpose-location-id="tableForm.purposeLocationId"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
