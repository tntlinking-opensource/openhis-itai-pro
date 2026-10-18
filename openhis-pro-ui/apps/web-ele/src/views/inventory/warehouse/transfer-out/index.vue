<!-- 药库调拨出库：工作站 + 侧栏 + 右侧明细。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@zxplat/utils';

import {
  loadTransferOutFormInitApi,
  mapTransferOutInitToCabinetRows,
  pageTransferOutWorkbenchListApi,
  WHS_TRANSFER_OUT_TYPE_ENUM,
} from '#/api/inventory/warehouse/transfer-out';
import {
  clearInvDocClientDraftCachesForScene,
  clearInvDocDraftCache,
  INV_DOC_DRAFT_SCENE,
  mergeInvDocDraftCardsIntoQueue,
} from '#/utils/inventory/inv-doc-draft-cache';

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

const TRANSFER_OUT_DRAFT_SCENE = INV_DOC_DRAFT_SCENE.warehouseTransferOut;

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

/** 当前侧栏草稿的稳定客户端 id */
const selectedClientDraftId = computed(() => {
  const o = selectedOrder.value;
  if (!o?._draft) return '';
  return String(o._clientDraftId ?? '').trim();
});

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
    if (id) clearInvDocDraftCache(TRANSFER_OUT_DRAFT_SCENE, id);
  }
  clearInvDocClientDraftCachesForScene(TRANSFER_OUT_DRAFT_SCENE);
}

/** 进入页时把 session 草稿卡片合并进队列，并选中最近一条 */
function rehydrateDraftCardsFromSession(): void {
  const merged = mergeInvDocDraftCardsIntoQueue({
    scene: TRANSFER_OUT_DRAFT_SCENE,
    orderList: orderList.value,
    buildDraftRow: (clientDraftId, cardMeta) => createDraftOrderRow(clientDraftId, cardMeta),
  });
  orderList.value = merged.orderList;
  if (!selectedOrder.value && merged.inserted[0]) {
    selectedOrder.value = merged.inserted[0];
    isAddDisabled.value = true;
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
    const id = String(selectedOrder.value._clientDraftId ?? '').trim();
    if (id) clearInvDocDraftCache(TRANSFER_OUT_DRAFT_SCENE, id);
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
  rehydrateDraftCardsFromSession();
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
      :queue-card-row="selectedOrder"
      class="min-h-0 flex-1"
      variant="out"
      :bus-no="selectedBusNo"
      :client-draft-id="selectedClientDraftId"
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
