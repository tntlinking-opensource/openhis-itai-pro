<!-- 基础数据 · 库存期初：工作站 + 侧栏卡片队列 + 右侧明细。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@zxplat/utils';

import {
  INV_ALL_LOCATIONS_FILTER_ID,
  INV_ITEM_TABLE_TYPE_DEVICE,
  INV_ITEM_TABLE_TYPE_MEDICATION,
  isSpecificInventoryLocationId,
  normalizeSidebarLocationFilterValue,
  pickWorkbenchOrderAfterSave,
  resolveDefaultAllLocationsFilterValue,
} from '#/utils/inventory/inventory-workbench-list';
import {
  loadInventoryOpeningFormInitApi,
  mapInventoryOpeningInitOptionsToApiItems,
  mapInventoryOpeningInitToCabinetRows,
  mapInventoryOpeningLocationOptions,
  pageInventoryOpeningWorkbenchListApi,
} from '#/api/basic/inventory-opening';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import InventoryOpeningAsidePane from './inventory-opening-aside-pane.vue';
import InventoryOpeningMainPane from './inventory-opening-main-pane.vue';

defineOptions({ name: 'BasicInventoryOpeningPage' });

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

const tableForm = ref<{ locationId?: number | string }>({
  locationId: INV_ALL_LOCATIONS_FILTER_ID,
});
const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const locationOptions = ref<Array<ApiOptionItem & { formEnum?: string; openingClosed?: boolean }>>(
  [],
);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const supplierOptions = ref<ApiOptionItem[]>([]);
const itemTableTypeOptions = ref<ApiOptionItem[]>([
  { label: '药品', value: INV_ITEM_TABLE_TYPE_MEDICATION },
  { label: '耗材', value: INV_ITEM_TABLE_TYPE_DEVICE },
]);
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
const isDraftOrder = computed(() => Boolean(selectedOrder.value?._draft));

/** 草稿单头库房：仅具体库位；禁止侧栏「全部」的 0 */
const draftHeadLocationId = computed(() => {
  if (!isDraftOrder.value) return undefined;
  const raw = selectedOrder.value?.locationId ?? selectedOrder.value?.purposeLocationId;
  return isSpecificInventoryLocationId(raw) ? String(raw) : undefined;
});

function findLocationMeta(
  locationId?: number | string,
): (ApiOptionItem & { formEnum?: string; openingClosed?: boolean }) | undefined {
  const key = String(locationId ?? '').trim();
  if (!key || key === '0') return undefined;
  return locationOptions.value.find((o) => String(o.value) === key);
}

const filterLocationClosed = computed(() => {
  const id = normalizeSidebarLocationFilterValue(tableForm.value.locationId);
  if (id === 0 || id === undefined || id === null) return false;
  return Boolean(findLocationMeta(id)?.openingClosed);
});

async function init() {
  try {
    const res = await loadInventoryOpeningFormInitApi();
    const data = res.data;
    locationOptions.value = mapInventoryOpeningLocationOptions(data);
    practitionerOptions.value = mapInventoryOpeningInitOptionsToApiItems(data?.practitionerOptions);
    supplierOptions.value = mapInventoryOpeningInitOptionsToApiItems(data?.supplierOptions);
    cabinetListOptions.value = mapInventoryOpeningInitToCabinetRows(data);
    if (
      tableForm.value.locationId === undefined ||
      tableForm.value.locationId === null ||
      String(tableForm.value.locationId).trim() === '' ||
      tableForm.value.locationId === 0
    ) {
      tableForm.value.locationId = resolveDefaultAllLocationsFilterValue(cabinetListOptions.value);
    }
  } catch {
    locationOptions.value = [];
    practitionerOptions.value = [];
    supplierOptions.value = [];
    cabinetListOptions.value = [];
  }
}

async function getList() {
  const dr = listDateRange.value;
  const draftRows = orderList.value.filter((r) => r._draft);
  try {
    const { records } = await pageInventoryOpeningWorkbenchListApi({
      locationId: normalizeSidebarLocationFilterValue(tableForm.value.locationId),
      keyword: queryParams.value.keyword?.trim() || undefined,
      applyTimeBegin: dr?.[0],
      applyTimeEnd: dr?.[1],
    });
    orderList.value = [...draftRows, ...(records as Record<string, unknown>[])];
  } catch {
    orderList.value = [...draftRows];
  }
}

function handleAdd() {
  if (filterLocationClosed.value) {
    return;
  }
  const draft = createDraftOrderRow();
  const filterId = normalizeSidebarLocationFilterValue(tableForm.value.locationId);
  // 侧栏「全部库房」(0) 不得写入单头目标库房
  if (isSpecificInventoryLocationId(filterId)) {
    const locKey = String(filterId);
    draft.locationId = locKey;
    draft.purposeLocationId = locKey;
    const meta = findLocationMeta(locKey);
    if (meta) {
      draft.purposeLocationId_dictText = meta.label;
      draft.locationName = meta.label;
    }
  }
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
    return;
  }
  isAddDisabled.value = false;
}

async function handleDocumentSaved(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  await getList();
  const busNo = payload.busNo?.trim();
  if (!busNo) return;
  selectedOrder.value = pickWorkbenchOrderAfterSave(orderList.value, busNo, resolveBusNo);
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

async function handleLocationsChanged() {
  await init();
  await getList();
}

watch(
  () => route.query,
  async (newQuery) => {
    const busNo = newQuery.busNo;
    if (!busNo || Array.isArray(busNo)) return;
    if (tableForm.value.locationId === undefined && cabinetListOptions.value.length === 0) {
      await init();
    }
    queryParams.value.keyword = String(busNo);
    await getList();
    const row = orderList.value.find((item) => resolveBusNo(item) === String(busNo));
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
      <InventoryOpeningAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:location-id="tableForm.locationId"
        :add-disabled="isAddDisabled || filterLocationClosed"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :practitioner-option="practitionerOptions"
        :selected-order="selectedOrder"
        location-label="库房"
        queue-title="历史期初单"
        @add="handleAdd"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <InventoryOpeningMainPane
      class="min-h-0 flex-1"
      :bus-no="selectedBusNo"
      :is-draft-order="isDraftOrder"
      :reset-key="mainPaneResetKey"
      :location-options="locationOptions"
      :practitioner-options="practitionerOptions"
      :supplier-options="supplierOptions"
      :item-table-type-options="itemTableTypeOptions"
      :draft-location-id="draftHeadLocationId"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
      @locations-changed="handleLocationsChanged"
    />
  </WarehouseDocWorkstation>
</template>
