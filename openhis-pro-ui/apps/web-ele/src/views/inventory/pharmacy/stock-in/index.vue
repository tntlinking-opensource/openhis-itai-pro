<!-- 药房入库单：工作站 + 侧栏卡片队列 + 右侧明细；布局与药库出库单对齐。 -->
<script lang="ts" setup>
import type { WarehouseOptionDto } from '#/api/inventory/pharmacy/stock-in';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@vben/utils';

import {
  initPharmacyStockInWorkbenchApi,
  pagePharmacyStockInWorkbenchListApi,
} from '#/api/inventory/pharmacy/stock-in';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';

import PharmacyStockInAsidePane from './pharmacy-stock-in-aside-pane.vue';
import PharmacyStockInMainPane from './pharmacy-stock-in-main-pane.vue';

defineOptions({
  name: 'InventoryPharmacyStockInPage',
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

const queryParams = ref<{
  keyword?: string;
}>({
  keyword: '',
});

const listDateRange = ref<[string, string] | null>(createDefaultMonthRange());

const listKeywordModel = computed({
  get: () => queryParams.value.keyword ?? '',
  set: (v: string) => {
    queryParams.value.keyword = v;
  },
});

const tableForm = ref<{
  purposeLocationId?: number | string;
  sourceLocationId?: number | string;
}>({
  purposeLocationId: undefined,
  sourceLocationId: undefined,
});

const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const pharmacyCabinetListOptions = ref<Record<string, unknown>[]>([]);
const warehouseRawOptions = ref<WarehouseOptionDto[]>([]);
const pharmacyRawOptions = ref<WarehouseOptionDto[]>([]);

const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);

const mainPaneResetKey = ref(0);

const itemTableTypeOptions = ref<ApiOptionItem[]>([]);

const isAddDisabled = ref(false);

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
    purposeLocationId: tableForm.value.purposeLocationId,
  };
}

/** 侧栏卡片库房一行：草稿用当前筛选的源药库→目的药房名称（与出库单卡片同构）。 */
function resolvePharmacyStockInQueueWarehouseLine(
  row: Record<string, unknown>,
): string | undefined {
  if (!row._draft) {
    return undefined;
  }
  const sid = row.sourceLocationId ?? tableForm.value.sourceLocationId;
  const pid = row.purposeLocationId ?? tableForm.value.purposeLocationId;
  const wh = cabinetListOptions.value;
  const ph = pharmacyCabinetListOptions.value;
  const labelOf = (id: unknown, lists: Record<string, unknown>[][]): string => {
    if (id === undefined || id === null || id === '') return '';
    const idStr = String(id);
    for (const list of lists) {
      for (const o of list) {
        const v = o.id ?? o.value;
        if (String(v) === idStr) return String(o.name ?? o.label ?? '').trim();
      }
    }
    return '';
  };
  const sn = labelOf(sid, [wh]);
  const pn = labelOf(pid, [ph]);
  if (sn && pn && sn !== pn) return `${sn} → ${pn}`;
  if (sn) return sn;
  if (pn) return pn;
  return undefined;
}

/** 从队列行解析入库单号。 */
function resolvePharmacyStockInBusNo(row?: null | Record<string, unknown>): string | undefined {
  if (!row) return undefined;
  for (const k of ['busNo', 'supplyBusNo', 'orderBusNo']) {
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
  return resolvePharmacyStockInBusNo(o) ?? '';
});

const isDraftOrder = computed(() => Boolean(selectedOrder.value?._draft));

/** 初始化：药房入库 init 返回药库位、药房位及项目类型等下拉。 */
async function init() {
  try {
    const wh = await initPharmacyStockInWorkbenchApi();
    cabinetListOptions.value = wh.cabinetListOptions ?? [];
    warehouseRawOptions.value = wh.warehouseRawOptions ?? [];
    pharmacyRawOptions.value = wh.pharmacyRawOptions ?? [];
    pharmacyCabinetListOptions.value = wh.pharmacyCabinetListOptions ?? [];
    itemTableTypeOptions.value = wh.itemTableTypeOptions ?? [];
    if (tableForm.value.sourceLocationId === undefined && cabinetListOptions.value.length > 0) {
      const first = cabinetListOptions.value[0] as Record<string, unknown>;
      tableForm.value.sourceLocationId = (first.id ?? first.value) as number | string | undefined;
    }
    if (
      tableForm.value.purposeLocationId === undefined &&
      pharmacyCabinetListOptions.value.length > 0
    ) {
      const first = pharmacyCabinetListOptions.value[0] as Record<string, unknown>;
      tableForm.value.purposeLocationId = (first.id ?? first.value) as number | string | undefined;
    }
  } catch {
    itemTableTypeOptions.value = [];
    cabinetListOptions.value = [];
    pharmacyCabinetListOptions.value = [];
    warehouseRawOptions.value = [];
    pharmacyRawOptions.value = [];
  }
}

/** 拉取左侧入库单列表并与本地草稿合并。 */
async function getList() {
  const dr = listDateRange.value;
  const draftRows = orderList.value.filter((r) => r._draft);
  try {
    const data = await pagePharmacyStockInWorkbenchListApi({
      keyword: queryParams.value.keyword,
      purposeLocationId: tableForm.value.purposeLocationId,
      sourceLocationId: tableForm.value.sourceLocationId,
      ...(dr?.[0] ? { applyTimeBegin: dr[0] } : {}),
      ...(dr?.[1] ? { applyTimeEnd: dr[1] } : {}),
    });
    const records = data.records ?? [];
    orderList.value = [...draftRows, ...records];
  } catch {
    orderList.value = [...draftRows];
  }
}

/** 新增草稿：插入队列并清空右侧待录入。 */
function handleAdd() {
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  mainPaneResetKey.value += 1;
  nextTick(() => {
    isAddDisabled.value = true;
  });
}

/** 点击队列卡片：草稿仅切状态；已存单据拉详情。 */
function handleOrderClick(order: Record<string, unknown>) {
  selectedOrder.value = order;
  if (isDraftOrderRow(order)) {
    mainPaneResetKey.value += 1;
    return;
  }
  isAddDisabled.value = false;
}

/** 保存成功：清草稿、刷新并回选当前单号。 */
async function handleDocumentSaved(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  await getList();
  const busNo = payload.busNo?.trim();
  if (!busNo) return;
  const row = orderList.value.find((item) => resolvePharmacyStockInBusNo(item) === busNo);
  if (!row) return;
  selectedOrder.value = row;
  isAddDisabled.value = false;
}

/** 删除成功：回到空选并刷新列表。 */
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
    if (
      (tableForm.value.sourceLocationId === undefined ||
        tableForm.value.purposeLocationId === undefined) &&
      cabinetListOptions.value.length === 0
    ) {
      await init();
    }
    queryParams.value.keyword = busNo;
    await getList();
    const row = orderList.value.find((item) => resolvePharmacyStockInBusNo(item) === String(busNo));
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
      <PharmacyStockInAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:purpose-location-id="tableForm.purposeLocationId"
        v-model:source-location-id="tableForm.sourceLocationId"
        :add-disabled="isAddDisabled"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :pharmacy-list-options="pharmacyCabinetListOptions"
        :resolve-queue-warehouse-line="resolvePharmacyStockInQueueWarehouseLine"
        :selected-order="selectedOrder"
        @add="handleAdd"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <PharmacyStockInMainPane
      class="min-h-0 flex-1"
      :bus-no="selectedBusNo"
      :is-draft-order="isDraftOrder"
      :item-table-type-options="itemTableTypeOptions"
      :pharmacy-options="pharmacyRawOptions"
      :reset-key="mainPaneResetKey"
      :sidebar-purpose-location-id="tableForm.purposeLocationId"
      :sidebar-source-location-id="tableForm.sourceLocationId"
      :warehouse-options="warehouseRawOptions"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
