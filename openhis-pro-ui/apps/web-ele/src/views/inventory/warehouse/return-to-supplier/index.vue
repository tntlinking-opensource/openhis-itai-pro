<!-- 药库退货单（向供应商退货）：工作站 + 侧栏卡片队列 + 右侧明细。 -->
<script lang="ts" setup>
import type {
  SupplierOptionDto,
  WarehouseOptionDto,
} from '#/api/inventory/warehouse/return-to-supplier';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@vben/utils';

import {
  loadReturnToSupplierFormInitApi,
  pageReturnToSuppliersApi,
  WHS_RT_TYPE_ENUM,
} from '#/api/inventory/warehouse/return-to-supplier';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';

import ReturnToSupplierAsidePane from './return-to-supplier-aside-pane.vue';
import ReturnToSupplierMainPane from './return-to-supplier-main-pane.vue';

defineOptions({
  name: 'InventoryWarehouseReturnToSupplierPage',
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

const tableForm = ref<{
  applyTime?: string;
  sourceLocationId?: number | string;
}>({
  sourceLocationId: undefined,
});

const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const warehouseRawOptions = ref<WarehouseOptionDto[]>([]);
const supplierRawOptions = ref<SupplierOptionDto[]>([]);
const supplierOptionItems = ref<ApiOptionItem[]>([]);

const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);
const isAddDisabled = ref(false);

const LIST_PAGE_SIZE = 200;

function warehouseOptionsToCabinetRows(list: WarehouseOptionDto[]): Record<string, unknown>[] {
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

function mapSupplierOptions(list: SupplierOptionDto[]): ApiOptionItem[] {
  return list.map((s) => ({
    label: String(s.supplierName ?? s.supplierId ?? ''),
    value: String(s.supplierId ?? ''),
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
    supplierId_dictText: '',
    typeEnum_enumText: '',
    applyTime: formatDateTime(new Date()),
    applicantId_dictText: '',
    totalAmount: undefined,
    sourceLocationId: tableForm.value.sourceLocationId,
  };
}

function resolveReturnToSupplierBusNo(row?: null | Record<string, unknown>): string | undefined {
  if (!row) return undefined;
  for (const k of ['busNo', 'supplyBusNo', 'orderBusNo']) {
    const raw = row[k];
    if (raw === undefined || raw === null) continue;
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return undefined;
}

const selectedBusNo = computed(() => resolveReturnToSupplierBusNo(selectedOrder.value) ?? '');

const isDraftOrder = computed(() => Boolean(selectedOrder.value?._draft));

async function init() {
  try {
    const res = await loadReturnToSupplierFormInitApi();
    const data = res.data;
    warehouseRawOptions.value = data?.warehouseOptions ?? [];
    supplierRawOptions.value = data?.supplierOptions ?? [];
    supplierOptionItems.value = mapSupplierOptions(supplierRawOptions.value);
    cabinetListOptions.value = warehouseOptionsToCabinetRows(warehouseRawOptions.value);
    if (tableForm.value.sourceLocationId === undefined && cabinetListOptions.value.length > 0) {
      const first = cabinetListOptions.value[0] as Record<string, unknown>;
      tableForm.value.sourceLocationId = (first.id ?? first.value) as number | string | undefined;
    }
  } catch {
    warehouseRawOptions.value = [];
    supplierRawOptions.value = [];
    supplierOptionItems.value = [];
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
    const res = await pageReturnToSuppliersApi({
      sourceLocationId: Number(sid),
      typeEnum: WHS_RT_TYPE_ENUM,
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
  const row = orderList.value.find((item) => resolveReturnToSupplierBusNo(item) === busNo);
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
    if (tableForm.value.sourceLocationId === undefined && cabinetListOptions.value.length === 0) {
      await init();
    }
    queryParams.value.keyword = String(busNo);
    await getList();
    const row = orderList.value.find(
      (item) => resolveReturnToSupplierBusNo(item) === String(busNo),
    );
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
      <ReturnToSupplierAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:source-location-id="tableForm.sourceLocationId"
        :add-disabled="isAddDisabled"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :selected-order="selectedOrder"
        @add="handleAdd"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <ReturnToSupplierMainPane
      class="min-h-0 flex-1"
      :apply-time="tableForm.applyTime"
      :bus-no="selectedBusNo"
      :is-draft-order="isDraftOrder"
      :reset-key="mainPaneResetKey"
      :sidebar-source-location-id="tableForm.sourceLocationId"
      :supplier-options="supplierOptionItems"
      :warehouse-options="warehouseRawOptions"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
