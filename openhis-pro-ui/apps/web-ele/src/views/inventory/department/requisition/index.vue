<!-- 科室药房请领单：工作站 + 侧栏卡片队列 + 明细。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@zxplat/utils';
import { useUserStore } from '@zxplat/stores';

import {
  loadDepartmentRequisitionFormInitApi,
  mapDepartmentRequisitionInitToCabinetRows,
  mapDepartmentRequisitionListRowToQueueCard,
  pageDepartmentRequisitionWorkbenchListApi,
  type DepartmentRequisitionListRowDto,
} from '#/api/inventory/department/requisition';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  INV_ITEM_TABLE_TYPE_DEVICE,
  mapInitOptionDtoToApiItems,
  mapInventoryPractitionerInitOptions,
  normalizeInventoryItemTableTypeOptions,
  normalizeSidebarLocationFilterValue,
  pickWorkbenchOrderAfterSave,
  resolveDefaultAllLocationsFilterValue,
} from '#/utils/inventory/inventory-workbench-list';
import { stockOutStatusLabel } from '#/utils/inventory/stock-out-doc-actions';

import RequisitionAsidePane from './requisition-aside-pane.vue';
import RequisitionMainPane from './requisition-main-pane.vue';

defineOptions({ name: 'InventoryDepartmentRequisitionPage' });

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

function toQueueRow(r: DepartmentRequisitionListRowDto): Record<string, unknown> {
  return {
    ...mapDepartmentRequisitionListRowToQueueCard(r),
    statusEnum_enumText: stockOutStatusLabel(r.statusEnum as number | string | undefined),
  };
}

async function init() {
  try {
    const res = await loadDepartmentRequisitionFormInitApi({});
    const data = res.data;
    warehouseOptions.value = mapInitOptionDtoToApiItems(data?.warehouseLocationOptions);
    pharmacyOptions.value = mapInitOptionDtoToApiItems(data?.pharmacyLocationOptions);
    cabinetListOptions.value = mapDepartmentRequisitionInitToCabinetRows(
      data?.pharmacyLocationOptions,
    );
    practitionerOptions.value = mapInventoryPractitionerInitOptions(
      [data?.pharmacistOptions, data?.warehouseKeeperOptions],
      userStore.userInfo,
    );
    itemTableTypeOptions.value = normalizeInventoryItemTableTypeOptions(
      mapInitOptionDtoToApiItems(data?.inboundItemTableTypeOptions),
      INV_ITEM_TABLE_TYPE_DEVICE,
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
    warehouseOptions.value = [];
    pharmacyOptions.value = [];
    cabinetListOptions.value = [];
    practitionerOptions.value = [];
    itemTableTypeOptions.value = [];
  }
}

async function getList() {
  const draftRows = orderList.value.filter((r) => r._draft);
  const dr = listDateRange.value;
  try {
    const { records } = await pageDepartmentRequisitionWorkbenchListApi({
      purposeLocationId: normalizeSidebarLocationFilterValue(tableForm.value.purposeLocationId),
      keyword: queryParams.value.keyword?.trim() || undefined,
      applyTimeBegin: dr?.[0],
      applyTimeEnd: dr?.[1],
    });
    orderList.value = [
      ...draftRows,
      ...records.map((r) => toQueueRow(r as DepartmentRequisitionListRowDto)),
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

function handleLocationChange(locationId?: number | string) {
  tableForm.value.purposeLocationId = normalizeSidebarLocationFilterValue(locationId);
  void getList();
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
      <RequisitionAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:location-id="tableForm.purposeLocationId"
        :add-disabled="isAddDisabled"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :selected-order="selectedOrder"
        filter-location-label="科室药房"
        queue-title="历史请领单"
        @add="handleAdd"
        @location-change="handleLocationChange"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <RequisitionMainPane
      :queue-card-row="selectedOrder"
      class="min-h-0 flex-1"
      variant="department"
      :bus-no="selectedBusNo"
      :is-draft-order="isDraftOrder"
      :reset-key="mainPaneResetKey"
      :warehouse-options="warehouseOptions"
      :pharmacy-options="pharmacyOptions"
      :practitioner-options="practitionerOptions"
      :item-table-type-options="itemTableTypeOptions"
      head-section-title="科室药房请领单"
      purpose-location-label="科室药房"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
