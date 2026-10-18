<!-- 药房盘点单：工作站 + 侧栏卡片队列 + 右侧明细；布局对齐进货单。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';
import type { PharmacyStocktakeLocationOption } from '#/api/inventory/pharmacy/stocktake';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@zxplat/utils';

import {
  INV_ALL_LOCATIONS_FILTER_ID,
  pickWorkbenchOrderAfterSave,
  resolveDefaultAllLocationsFilterValue,
} from '#/utils/inventory/inventory-workbench-list';
import {
  initPharmacyStocktakeWorkbenchApi,
  pagePharmacyStocktakeWorkbenchListApi,
} from '#/api/inventory/pharmacy/stocktake';
import {
  clearInvDocClientDraftCachesForScene,
  clearInvDocDraftCache,
  INV_DOC_DRAFT_SCENE,
  mergeInvDocDraftCardsIntoQueue,
} from '#/utils/inventory/inv-doc-draft-cache';

import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import { useFromFinanceApproval } from '#/composables/use-from-finance-approval';
import StocktakeAsidePane from '@inventory/components/stocktake/stocktake-aside-pane.vue';
import StocktakeMainPane from '@inventory/components/stocktake/stocktake-main-pane.vue';

defineOptions({
  name: 'InventoryPharmacyStocktakePage',
});

const STOCKTAKE_DRAFT_SCENE = INV_DOC_DRAFT_SCENE.pharmacyStocktake;

const { fromFinanceApproval } = useFromFinanceApproval();

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

const tableForm = ref<{ purposeLocationId?: number | string }>({
  purposeLocationId: INV_ALL_LOCATIONS_FILTER_ID,
});

const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const locationRawOptions = ref<PharmacyStocktakeLocationOption[]>([]);
const itemTableTypeOptions = ref<ApiOptionItem[]>([]);

const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);
const isAddDisabled = ref(false);

function isDraftOrderRow(row?: null | Record<string, unknown>): boolean {
  return Boolean(row?._draft);
}

/** 新建或按已有 clientDraftId 恢复草稿卡片 */
function createDraftOrderRow(
  clientDraftId?: string,
  cardMeta?: Record<string, unknown>,
): Record<string, unknown> {
  const id =
    String(clientDraftId ?? '').trim() ||
    `draft:${Date.now()}:${Math.random().toString(36).slice(2, 9)}`;
  return {
    _draft: true,
    _clientDraftId: id,
    statusEnum_enumText: String(cardMeta?.statusEnum_enumText ?? '编辑中'),
    applyTime: String(cardMeta?.applyTime ?? formatDateTime(new Date())),
    applicantId_dictText: String(cardMeta?.applicantId_dictText ?? ''),
    totalAmount: cardMeta?.totalAmount,
  };
}

/** 丢弃侧栏上现有草稿卡片时同步清 session 缓存 */
function discardSidebarDraftCaches(): void {
  for (const row of orderList.value.filter((r) => r._draft)) {
    const id = String(row._clientDraftId ?? '').trim();
    if (id) clearInvDocDraftCache(STOCKTAKE_DRAFT_SCENE, id);
  }
  clearInvDocClientDraftCachesForScene(STOCKTAKE_DRAFT_SCENE);
}

/** 进入页时把 session 草稿卡片合并进队列，并选中最近一条 */
function rehydrateDraftCardsFromSession(): void {
  const merged = mergeInvDocDraftCardsIntoQueue({
    scene: STOCKTAKE_DRAFT_SCENE,
    orderList: orderList.value,
    buildDraftRow: (clientDraftId, cardMeta) => createDraftOrderRow(clientDraftId, cardMeta),
  });
  orderList.value = merged.orderList;
  if (!selectedOrder.value && merged.inserted[0]) {
    selectedOrder.value = merged.inserted[0];
    isAddDisabled.value = true;
  }
}

function resolveStocktakeBusNo(row?: null | Record<string, unknown>): string | undefined {
  if (!row) return undefined;
  for (const k of ['busNo', 'supplyBusNo']) {
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
  return resolveStocktakeBusNo(o) ?? '';
});

const isDraftOrder = computed(() => Boolean(selectedOrder.value?._draft));

/** 当前侧栏草稿的稳定客户端 id */
const selectedClientDraftId = computed(() => {
  const o = selectedOrder.value;
  if (!o?._draft) return '';
  return String(o._clientDraftId ?? '').trim();
});

async function init() {
  try {
    const data = await initPharmacyStocktakeWorkbenchApi();
    cabinetListOptions.value = data.cabinetListOptions ?? [];
    locationRawOptions.value = data.locationRawOptions ?? [];
    itemTableTypeOptions.value = data.itemTableTypeOptions ?? [];
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
    cabinetListOptions.value = [];
    locationRawOptions.value = [];
    itemTableTypeOptions.value = [];
  }
}

async function getList() {
  const dr = listDateRange.value;
  const draftRows = orderList.value.filter((r) => r._draft);
  try {
    const data = await pagePharmacyStocktakeWorkbenchListApi({
      keyword: queryParams.value.keyword,
      purposeLocationId: tableForm.value.purposeLocationId,
      ...(dr?.[0] ? { applyTimeBegin: dr[0] } : {}),
      ...(dr?.[1] ? { applyTimeEnd: dr[1] } : {}),
    });
    orderList.value = [...draftRows, ...data.records];
  } catch {
    orderList.value = [...draftRows];
  }
}

function handleAdd() {
  discardSidebarDraftCaches();
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
    // 切回草稿由主区 sync 恢复缓存；勿 bump resetKey，否则会冲掉恢复内容
    return;
  }
  isAddDisabled.value = false;
}

function handleLocationChange(locationId?: number | string) {
  tableForm.value.purposeLocationId = locationId;
  void getList();
}

async function handleDocumentSaved(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  await getList();
  const busNo = payload.busNo?.trim();
  if (!busNo) return;
  selectedOrder.value = pickWorkbenchOrderAfterSave(orderList.value, busNo, resolveStocktakeBusNo);
  isAddDisabled.value = false;
}

async function handleDocumentRemoved() {
  if (selectedOrder.value?._draft) {
    const id = String(selectedOrder.value._clientDraftId ?? '').trim();
    if (id) clearInvDocDraftCache(STOCKTAKE_DRAFT_SCENE, id);
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
    const busNoText = String(busNo).trim();
    if (!busNoText) return;
    if (tableForm.value.purposeLocationId === undefined && cabinetListOptions.value.length === 0) {
      await init();
    }
    // 深链有单号：直接打开，不经侧栏日期/列表过滤
    selectedOrder.value = { busNo: busNoText, supplyBusNo: busNoText };
    isAddDisabled.value = false;
  },
  { immediate: true },
);

onMounted(async () => {
  await init();
  await getList();
  rehydrateDraftCardsFromSession();
});
</script>

<template>
  <WarehouseDocWorkstation
    queue-title=""
    aside-dense
    :show-aside="!fromFinanceApproval"
    :show-aside-filter="false"
    :show-toolbar="false"
  >
    <template #aside-queue>
      <StocktakeAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:location-id="tableForm.purposeLocationId"
        :add-disabled="isAddDisabled"
        :cabinet-list-options="cabinetListOptions"
        location-label="盘点药房"
        :orders="orderList"
        :selected-order="selectedOrder"
        @add="handleAdd"
        @location-change="handleLocationChange"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <StocktakeMainPane
      :queue-card-row="selectedOrder"
      class="min-h-0 flex-1"
      variant="pharmacy"
      :bus-no="selectedBusNo"
      :client-draft-id="selectedClientDraftId"
      :is-draft-order="isDraftOrder"
      :item-table-type-options="itemTableTypeOptions"
      :location-options="locationRawOptions"
      location-field-label="盘点药房"
      head-section-title="药房盘点单"
      :reset-key="mainPaneResetKey"
      :sidebar-purpose-location-id="tableForm.purposeLocationId"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
