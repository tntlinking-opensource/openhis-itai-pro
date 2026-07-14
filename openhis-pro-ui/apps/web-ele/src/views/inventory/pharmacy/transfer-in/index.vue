<!-- 药房调拨入库：工作站 + 侧栏 + 右侧明细。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import {
  loadTransferInFormInitApi,
  mapPharmacyTransferInInitToCabinetRows,
  pageTransferInWorkbenchListApi,
  PHA_TRANSFER_IN_TYPE_ENUM,
} from '#/api/inventory/pharmacy/transfer-in';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  mapInitOptionDtoToApiItems,
  normalizeSidebarLocationFilterValue,
  resolveDefaultAllLocationsFilterValue,
} from '#/utils/inventory/inventory-workbench-list';
import TransferAsidePane from './transfer-in-aside-pane.vue';
import TransferMainPane from './transfer-in-main-pane.vue';

defineOptions({ name: 'InventoryPharmacyTransferInPage' });

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

const tableForm = ref<{ purposeLocationId?: number | string }>({
  purposeLocationId: INV_ALL_LOCATIONS_FILTER_ID,
});
const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const warehouseOptions = ref<ApiOptionItem[]>([]);
const pharmacyOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
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
const isDraftOrder = computed(() => false);

async function init() {
  try {
    const res = await loadTransferInFormInitApi();
    const data = res.data;
    pharmacyOptions.value = mapOptions(data?.pharmacyLocationOptions);
    warehouseOptions.value = mapOptions(data?.warehouseLocationOptions);
    practitionerOptions.value = mapOptions(data?.pharmacistOptions ?? data?.warehouseKeeperOptions);
    cabinetListOptions.value = mapPharmacyTransferInInitToCabinetRows(
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
    cabinetListOptions.value = [];
  }
}

async function getList() {
  const dr = listDateRange.value;
  try {
    const { records } = await pageTransferInWorkbenchListApi({
      purposeLocationId: normalizeSidebarLocationFilterValue(tableForm.value.purposeLocationId),
      typeEnum: PHA_TRANSFER_IN_TYPE_ENUM,
      keyword: queryParams.value.keyword?.trim() || undefined,
      applyTimeBegin: dr?.[0],
      applyTimeEnd: dr?.[1],
    });
    orderList.value = records as Record<string, unknown>[];
  } catch {
    orderList.value = [];
  }
}

function handleOrderClick(order: Record<string, unknown>) {
  selectedOrder.value = order;
}

async function handleDocumentRemoved() {
  selectedOrder.value = null;
  mainPaneResetKey.value += 1;
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
        v-model:filter-location-id="tableForm.purposeLocationId"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :selected-order="selectedOrder"
        :show-add="false"
        filter-location-label="调入药房"
        queue-title="历史调拨入库单"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>
    <TransferMainPane
      class="min-h-0 flex-1"
      domain="pharmacy"
      variant="in"
      :bus-no="selectedBusNo"
      :is-draft-order="isDraftOrder"
      :reset-key="mainPaneResetKey"
      :warehouse-options="warehouseOptions"
      :pharmacy-options="pharmacyOptions"
      :practitioner-options="practitionerOptions"
      head-section-title="药房调拨入库单"
      @document-removed="handleDocumentRemoved"
    />
  </WarehouseDocWorkstation>
</template>
