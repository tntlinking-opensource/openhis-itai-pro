<!-- 药库调拨出库：工作站 + 侧栏 + 右侧明细。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@vben/utils';

import {
  loadTransferOutFormInitApi,
  pageTransferOutsApi,
  WHS_TRANSFER_OUT_TYPE_ENUM,
} from '#/api/inventory/warehouse/transfer-out';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import TransferAsidePane from './transfer-out-aside-pane.vue';
import TransferMainPane from './transfer-out-main-pane.vue';

defineOptions({ name: 'InventoryWarehouseTransferOutPage' });

const route = useRoute();

function createDefaultMonthRange(): [string, string] {
  const d = new Date();
  const y = d.getFullYear();
  const m = d.getMonth();
  const pad = (n: number) => String(n).padStart(2, '0');
  return [`${y}-${pad(m + 1)}-01`, `${y}-${pad(m + 1)}-${pad(d.getDate())}`];
}

function mapOptions(list?: { label?: string; value?: string }[]): ApiOptionItem[] {
  return (list ?? []).map((o) => ({ label: String(o.label ?? ''), value: String(o.value ?? '') }));
}

function optionsToCabinetRows(list: ApiOptionItem[]): Record<string, unknown>[] {
  return list.map((w) => ({ id: w.value, value: w.value, name: w.label, label: w.label }));
}

const queryParams = ref<{ keyword?: string }>({ keyword: '' });
const listDateRange = ref<[string, string] | null>(createDefaultMonthRange());
const listKeywordModel = computed({
  get: () => queryParams.value.keyword ?? '',
  set: (v: string) => {
    queryParams.value.keyword = v;
  },
});

const tableForm = ref<{ sourceLocationId?: number | string }>({ sourceLocationId: undefined });
const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const warehouseOptions = ref<ApiOptionItem[]>([]);
const pharmacyOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);
const isAddDisabled = ref(false);

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

async function init() {
  try {
    const res = await loadTransferOutFormInitApi();
    const data = res.data;
    pharmacyOptions.value = mapOptions(data?.pharmacyLocationOptions);
    warehouseOptions.value = mapOptions(data?.warehouseLocationOptions);
    practitionerOptions.value = mapOptions(data?.pharmacistOptions ?? data?.warehouseKeeperOptions);
    cabinetListOptions.value = optionsToCabinetRows(warehouseOptions.value);
    if (tableForm.value.sourceLocationId === undefined && cabinetListOptions.value.length > 0) {
      const first = cabinetListOptions.value[0] as Record<string, unknown>;
      tableForm.value.sourceLocationId = (first.id ?? first.value) as number | string | undefined;
    }
  } catch {
    pharmacyOptions.value = [];
    warehouseOptions.value = [];
    practitionerOptions.value = [];
    cabinetListOptions.value = [];
  }
}

async function getList() {
  const sid = tableForm.value.sourceLocationId;
  const draftRows = orderList.value.filter((r) => r._draft);
  if (sid === undefined || sid === null || String(sid).trim() === '') {
    orderList.value = [...draftRows];
    return;
  }
  const dr = listDateRange.value;
  try {
    const res = await pageTransferOutsApi({
      sourceLocationId: sid,
      typeEnum: WHS_TRANSFER_OUT_TYPE_ENUM,
      keyword: queryParams.value.keyword?.trim() || undefined,
      pageNo: 1,
      pageSize: 200,
      ...(dr?.[0] ? { applyTimeFrom: dr[0] } : {}),
      ...(dr?.[1] ? { applyTimeTo: dr[1] } : {}),
    });
    orderList.value = [...draftRows, ...(res.data?.records ?? [])];
  } catch {
    orderList.value = [...draftRows];
  }
}

function handleAdd() {
  const draft = {
    _draft: true,
    statusEnum_enumText: '编辑中',
    applyTime: formatDateTime(new Date()),
    sourceLocationId: tableForm.value.sourceLocationId,
  };
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  mainPaneResetKey.value += 1;
  nextTick(() => {
    isAddDisabled.value = true;
  });
}

function handleOrderClick(order: Record<string, unknown>) {
  selectedOrder.value = order;
  if (order._draft) {
    mainPaneResetKey.value += 1;
    return;
  }
  isAddDisabled.value = false;
}

async function handleDocumentSaved(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  await getList();
  const busNo = payload.busNo?.trim();
  if (!busNo) return;
  const row = orderList.value.find((item) => resolveBusNo(item) === busNo);
  if (row) selectedOrder.value = row;
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
  selectedOrder.value = null;
  mainPaneResetKey.value += 1;
  isAddDisabled.value = false;
  await getList();
}

watch(
  () => route.query,
  async (q) => {
    const busNo = q.busNo;
    if (!busNo || Array.isArray(busNo)) return;
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
      <TransferAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:filter-location-id="tableForm.sourceLocationId"
        :add-disabled="isAddDisabled"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :selected-order="selectedOrder"
        filter-location-label="调出药库"
        queue-title="历史调拨出库单"
        @add="handleAdd"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>
    <TransferMainPane
      class="min-h-0 flex-1"
      variant="out"
      :bus-no="selectedBusNo"
      :is-draft-order="isDraftOrder"
      :reset-key="mainPaneResetKey"
      :sidebar-filter-location-id="tableForm.sourceLocationId"
      :warehouse-options="warehouseOptions"
      :pharmacy-options="pharmacyOptions"
      :practitioner-options="practitionerOptions"
      head-section-title="调拨出库单"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
