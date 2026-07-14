<!-- 退库入库单：工作站 + 侧栏卡片队列 + 右侧明细。 -->
<script lang="ts" setup>
import type { LocationOptionDto } from '#/api/inventory/warehouse/return-stock-in';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@vben/utils';

import {
  loadReturnStockInFormInitApi,
  pageReturnStockInsApi,
  WHS_RSI_TYPE_ENUM,
} from '#/api/inventory/warehouse/return-stock-in';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import {
  pickWorkbenchOrderAfterSave,
  preserveLocationIdForRequest,
} from '#/utils/inventory/inventory-workbench-list';

import ReturnStockInAsidePane from './return-stock-in-aside-pane.vue';
import ReturnStockInMainPane from './return-stock-in-main-pane.vue';

defineOptions({ name: 'InventoryWarehouseReturnStockInPage' });

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

const tableForm = ref<{ applyTime?: string; purposeLocationId?: number | string }>({
  purposeLocationId: undefined,
});

const warehouseRawOptions = ref<LocationOptionDto[]>([]);
const departmentRawOptions = ref<LocationOptionDto[]>([]);
const pharmacyRawOptions = ref<LocationOptionDto[]>([]);
const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);
const isAddDisabled = ref(false);
const LIST_PAGE_SIZE = 200;

function locationOptionsToCabinetRows(list: LocationOptionDto[]): Record<string, unknown>[] {
  return list.map((w) => {
    const id = w.locationId;
    return {
      id,
      value: id,
      name: w.displayName ?? String(id ?? ''),
      label: w.displayName ?? String(id ?? ''),
    };
  });
}

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

async function init() {
  try {
    const res = await loadReturnStockInFormInitApi();
    const data = res.data;
    warehouseRawOptions.value = data?.warehouseOptions ?? [];
    departmentRawOptions.value = data?.departmentOptions ?? [];
    pharmacyRawOptions.value = data?.pharmacyOptions ?? [];
    cabinetListOptions.value = locationOptionsToCabinetRows(warehouseRawOptions.value);
    if (tableForm.value.purposeLocationId === undefined && cabinetListOptions.value.length > 0) {
      const first = cabinetListOptions.value[0] as Record<string, unknown>;
      tableForm.value.purposeLocationId = (first.id ?? first.value) as number | string | undefined;
    }
  } catch {
    warehouseRawOptions.value = [];
    departmentRawOptions.value = [];
    pharmacyRawOptions.value = [];
    cabinetListOptions.value = [];
  }
}

async function getList() {
  const pid = tableForm.value.purposeLocationId;
  const draftRows = orderList.value.filter((r) => r._draft);
  if (pid === undefined || pid === null || String(pid).trim() === '') {
    orderList.value = [...draftRows];
    return;
  }
  const dr = listDateRange.value;
  try {
    const res = await pageReturnStockInsApi({
      purposeLocationId: preserveLocationIdForRequest(pid),
      typeEnum: WHS_RSI_TYPE_ENUM,
      keyword: queryParams.value.keyword?.trim() || undefined,
      pageNo: 1,
      pageSize: LIST_PAGE_SIZE,
      ...(dr?.[0] ? { applyTimeFrom: dr[0] } : {}),
      ...(dr?.[1] ? { applyTimeTo: dr[1] } : {}),
    });
    orderList.value = [...draftRows, ...(res.data?.records ?? [])];
  } catch {
    orderList.value = [...draftRows];
  }
}

function handleAdd() {
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  tableForm.value.applyTime = formatDateTime(new Date());
  mainPaneResetKey.value += 1;
  nextTick(() => {
    isAddDisabled.value = true;
  });
}

function handleOrderClick(order: Record<string, unknown>) {
  selectedOrder.value = order;
  if (isDraftOrderRow(order)) {
    // 切回草稿由主区 sync 恢复缓存；勿 bump resetKey，否则会冲掉恢复内容
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
      <ReturnStockInAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:purpose-location-id="tableForm.purposeLocationId"
        :add-disabled="isAddDisabled"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :selected-order="selectedOrder"
        @add="handleAdd"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <ReturnStockInMainPane
      class="min-h-0 flex-1"
      :apply-time="tableForm.applyTime"
      :bus-no="selectedBusNo"
      :is-draft-order="isDraftOrder"
      :reset-key="mainPaneResetKey"
      :sidebar-purpose-location-id="tableForm.purposeLocationId"
      :warehouse-options="warehouseRawOptions"
      :department-options="departmentRawOptions"
      :pharmacy-options="pharmacyRawOptions"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
