<!-- 药房退库单：工作站 + 侧栏卡片队列 + 右侧明细。 -->
<script lang="ts" setup>
import type {
  PharmacyLocationOptionDto,
  WarehouseLocationOptionDto,
} from '#/api/inventory/pharmacy/return-to-warehouse';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@vben/utils';

import {
  loadReturnToWarehouseFormInitApi,
  mapReturnToWarehouseInitToCabinetRows,
  pageReturnToWarehouseWorkbenchListApi,
} from '#/api/inventory/pharmacy/return-to-warehouse';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  enrichStockOutQueueCardRow,
  normalizeSidebarLocationFilterValue,
  pickWorkbenchOrderAfterSave,
  resolveDefaultAllLocationsFilterValue,
} from '#/utils/inventory/inventory-workbench-list';

import ReturnToWarehouseAsidePane from './return-to-warehouse-aside-pane.vue';
import ReturnToWarehouseMainPane from './return-to-warehouse-main-pane.vue';

defineOptions({ name: 'InventoryPharmacyReturnToWarehousePage' });

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

const tableForm = ref<{ applyTime?: string; sourceLocationId?: number | string }>({
  sourceLocationId: INV_ALL_LOCATIONS_FILTER_ID,
});

const pharmacyRawOptions = ref<PharmacyLocationOptionDto[]>([]);
const warehouseRawOptions = ref<WarehouseLocationOptionDto[]>([]);
const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);
const mainPaneRef = ref<InstanceType<typeof ReturnToWarehouseMainPane> | null>(null);
const showWelcomeEmpty = computed(() => selectedOrder.value === null);

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
  for (const k of ['busNo', 'supplyBusNo', 'orderBusNo']) {
    const raw = row[k];
    if (raw === undefined || raw === null) continue;
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return undefined;
}

const selectedBusNo = computed(() => resolveBusNo(selectedOrder.value) ?? '');
const isDraftOrder = computed(() => Boolean(selectedOrder.value?._draft));

function toQueueRow(r: Record<string, unknown>): Record<string, unknown> {
  return enrichStockOutQueueCardRow(r, {
    sourceOptions: pharmacyRawOptions.value,
    purposeOptions: warehouseRawOptions.value,
  });
}

async function init() {
  try {
    const res = await loadReturnToWarehouseFormInitApi();
    const data = res.data;
    pharmacyRawOptions.value = data?.pharmacyLocationOptions ?? [];
    warehouseRawOptions.value = data?.warehouseLocationOptions ?? [];
    cabinetListOptions.value = mapReturnToWarehouseInitToCabinetRows(pharmacyRawOptions.value);
    if (
      tableForm.value.sourceLocationId === undefined ||
      tableForm.value.sourceLocationId === null ||
      tableForm.value.sourceLocationId === 0
    ) {
      tableForm.value.sourceLocationId = resolveDefaultAllLocationsFilterValue(
        cabinetListOptions.value,
      );
    }
  } catch {
    pharmacyRawOptions.value = [];
    warehouseRawOptions.value = [];
    cabinetListOptions.value = [];
  }
}

async function getList() {
  const draftRows = orderList.value.filter((r) => r._draft);
  const dr = listDateRange.value;
  try {
    const { records } = await pageReturnToWarehouseWorkbenchListApi({
      sourceLocationId: normalizeSidebarLocationFilterValue(tableForm.value.sourceLocationId),
      keyword: queryParams.value.keyword?.trim() || undefined,
      applyTimeBegin: dr?.[0],
      applyTimeEnd: dr?.[1],
    });
    orderList.value = [
      ...draftRows,
      ...records.map((r) => toQueueRow(r as Record<string, unknown>)),
    ];
  } catch {
    orderList.value = [...draftRows];
  }
}

async function handleCreateFromUpstream(row: Record<string, unknown>) {
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  tableForm.value.applyTime = formatDateTime(new Date());
  mainPaneResetKey.value += 1;
  await nextTick();
  await mainPaneRef.value?.applyLinkableSourceToDraft(row);
}

function handleOrderClick(order: Record<string, unknown>) {
  selectedOrder.value = order;
  if (isDraftOrderRow(order)) {
    // 切回草稿由主区 sync 恢复缓存；勿 bump resetKey，否则会冲掉恢复内容
    return;
  }
}

async function handleDocumentSaved(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  await getList();
  const busNo = payload.busNo?.trim();
  if (!busNo) return;
  selectedOrder.value = pickWorkbenchOrderAfterSave(orderList.value, busNo, resolveBusNo);
}

async function handleDocumentRemoved() {
  if (selectedOrder.value?._draft) {
    orderList.value = orderList.value.filter((r) => r !== selectedOrder.value);
    selectedOrder.value = null;
    mainPaneResetKey.value += 1;
    return;
  }
  orderList.value = orderList.value.filter((r) => !r._draft);
  selectedOrder.value = null;
  mainPaneResetKey.value += 1;
  await getList();
}

watch(
  () => route.query,
  async (newQuery) => {
    const busNo = newQuery.busNo;
    if (!busNo || Array.isArray(busNo)) return;
    if (cabinetListOptions.value.length === 0) {
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
      <ReturnToWarehouseAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:source-location-id="tableForm.sourceLocationId"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :selected-order="selectedOrder"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <ReturnToWarehouseMainPane
      ref="mainPaneRef"
      class="min-h-0 flex-1"
      :apply-time="tableForm.applyTime"
      :bus-no="selectedBusNo"
      :is-draft-order="isDraftOrder"
      :reset-key="mainPaneResetKey"
      :show-welcome-empty="showWelcomeEmpty"
      :pharmacy-options="pharmacyRawOptions"
      :warehouse-options="warehouseRawOptions"
      @create-from-upstream="handleCreateFromUpstream"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
