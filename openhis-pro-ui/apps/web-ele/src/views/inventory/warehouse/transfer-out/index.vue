<!-- 药库调拨出库：工作站 + 侧栏 + 右侧明细。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@vben/utils';

import {
  loadTransferOutFormInitApi,
  mapTransferOutInitToCabinetRows,
  pageTransferOutWorkbenchListApi,
  WHS_TRANSFER_OUT_TYPE_ENUM,
} from '#/api/inventory/warehouse/transfer-out';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  enrichStockOutQueueCardRow,
  mapInitOptionDtoToApiItems,
  normalizeSidebarLocationFilterValue,
  pickWorkbenchOrderAfterSave,
  resolveDefaultAllLocationsFilterValue,
} from '#/utils/inventory/inventory-workbench-list';
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
  return mapInitOptionDtoToApiItems(list);
}

const queryParams = ref<{ keyword?: string }>({ keyword: '' });
const listDateRange = ref<[string, string] | null>(createDefaultMonthRange());
const listKeywordModel = computed({
  get: () => queryParams.value.keyword ?? '',
  set: (v: string) => {
    queryParams.value.keyword = v;
  },
});

const tableForm = ref<{ sourceLocationId?: number | string }>({
  sourceLocationId: INV_ALL_LOCATIONS_FILTER_ID,
});
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
    cabinetListOptions.value = mapTransferOutInitToCabinetRows(data?.warehouseLocationOptions);
    if (
      tableForm.value.sourceLocationId === undefined ||
      tableForm.value.sourceLocationId === null ||
      String(tableForm.value.sourceLocationId).trim() === '' ||
      tableForm.value.sourceLocationId === 0
    ) {
      tableForm.value.sourceLocationId = resolveDefaultAllLocationsFilterValue(
        cabinetListOptions.value,
      );
    }
  } catch {
    pharmacyOptions.value = [];
    warehouseOptions.value = [];
    practitionerOptions.value = [];
    cabinetListOptions.value = [];
  }
}

async function getList() {
  const draftRows = orderList.value.filter((r) => r._draft);
  const dr = listDateRange.value;
  try {
    const { records } = await pageTransferOutWorkbenchListApi({
      sourceLocationId: normalizeSidebarLocationFilterValue(tableForm.value.sourceLocationId),
      typeEnum: WHS_TRANSFER_OUT_TYPE_ENUM,
      keyword: queryParams.value.keyword?.trim() || undefined,
      applyTimeBegin: dr?.[0],
      applyTimeEnd: dr?.[1],
    });
    // 侧栏卡片：制单人/审批人/金额/库位名与出库单队列映射同构
    const locOpts = cabinetListOptions.value.map((o) => ({
      locationId: o.id ?? o.value,
      displayName: String(o.name ?? o.label ?? ''),
    }));
    orderList.value = [
      ...draftRows,
      ...records.map((r) =>
        enrichStockOutQueueCardRow(r as Record<string, unknown>, {
          sourceOptions: locOpts,
          purposeOptions: locOpts,
        }),
      ),
    ];
  } catch {
    orderList.value = [...draftRows];
  }
}

function handleAdd() {
  const draft = {
    _draft: true,
    statusEnum_enumText: '编辑中',
    applyTime: formatDateTime(new Date()),
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
      :warehouse-options="warehouseOptions"
      :pharmacy-options="pharmacyOptions"
      :practitioner-options="practitionerOptions"
      head-section-title="药库调拨出库单"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
