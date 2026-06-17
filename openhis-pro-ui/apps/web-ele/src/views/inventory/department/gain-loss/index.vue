<!-- 科室损益单：工作站 + 侧栏卡片队列 + 右侧明细。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@vben/utils';

import {
  loadDeptGainLossFormInitApi,
  mapDeptGainLossInitOptionsToApiItems,
  pageDeptGainLosssApi,
} from '#/api/inventory/department/gain-loss';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import GainLossAsidePane from './gain-loss-aside-pane.vue';
import GainLossMainPane from './gain-loss-main-pane.vue';

defineOptions({ name: 'InventoryDepartmentGainLossPage' });

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

const tableForm = ref<{ purposeLocationId?: number | string }>({ purposeLocationId: undefined });
const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const locationOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const itemTableTypeOptions = ref<ApiOptionItem[]>([]);
const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);
const isAddDisabled = ref(false);
const LIST_PAGE_SIZE = 200;

function optionsToCabinetRows(list: ApiOptionItem[]): Record<string, unknown>[] {
  return list.map((w) => ({
    id: w.value,
    value: w.value,
    name: w.label,
    label: w.label,
  }));
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
    const res = await loadDeptGainLossFormInitApi();
    const data = res.data;
    locationOptions.value = mapDeptGainLossInitOptionsToApiItems(data?.deptLocationOptions);
    practitionerOptions.value = mapDeptGainLossInitOptionsToApiItems(
      data?.nurseOptions ?? data?.warehouseKeeperOptions,
    );
    itemTableTypeOptions.value = mapDeptGainLossInitOptionsToApiItems(
      data?.inboundItemTableTypeOptions,
    );
    cabinetListOptions.value = optionsToCabinetRows(locationOptions.value);
    if (tableForm.value.purposeLocationId === undefined && cabinetListOptions.value.length > 0) {
      const first = cabinetListOptions.value[0] as Record<string, unknown>;
      tableForm.value.purposeLocationId = (first.id ?? first.value) as number | string | undefined;
    }
  } catch {
    locationOptions.value = [];
    practitionerOptions.value = [];
    itemTableTypeOptions.value = [];
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
    const res = await pageDeptGainLosssApi({
      purposeLocationId: pid,
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

async function handleDocumentSaved(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  await getList();
  const busNo = payload.busNo?.trim();
  if (!busNo) return;
  const row = orderList.value.find((item) => resolveBusNo(item) === busNo);
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
      <GainLossAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:location-id="tableForm.purposeLocationId"
        :add-disabled="isAddDisabled"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :selected-order="selectedOrder"
        location-label="科室"
        queue-title="历史损益单"
        @add="handleAdd"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <GainLossMainPane
      class="min-h-0 flex-1"
      variant="department"
      :bus-no="selectedBusNo"
      :is-draft-order="isDraftOrder"
      :reset-key="mainPaneResetKey"
      :sidebar-purpose-location-id="tableForm.purposeLocationId"
      :location-options="locationOptions"
      :practitioner-options="practitionerOptions"
      :item-table-type-options="itemTableTypeOptions"
      location-field-label="科室"
      head-section-title="科室损益单"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
