<!-- 药库退货单（向供应商退货）：工作站 + 侧栏卡片队列 + 右侧明细。 -->
<script lang="ts" setup>
import type {
  LinkableGoodsReceiptListRowDto,
  SupplierOptionDto,
  WarehouseOptionDto,
} from '#/api/inventory/warehouse/return-to-supplier';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@vben/utils';

import {
  loadReturnToSupplierFormInitApi,
  mapReturnToSupplierInitToCabinetRows,
  pageReturnToSupplierWorkbenchListApi,
} from '#/api/inventory/warehouse/return-to-supplier';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import { useFromFinanceApproval } from '#/composables/use-from-finance-approval';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  normalizeSidebarLocationFilterValue,
  pickWorkbenchOrderAfterSave,
  resolveDefaultAllLocationsFilterValue,
} from '#/utils/inventory/inventory-workbench-list';

import {
  clearInvDocClientDraftCachesForScene,
  clearInvDocDraftCache,
  INV_DOC_DRAFT_SCENE,
  mergeInvDocDraftCardsIntoQueue,
} from '#/utils/inventory/inv-doc-draft-cache';

import ReturnToSupplierAsidePane from './return-to-supplier-aside-pane.vue';
import ReturnToSupplierMainPane from './return-to-supplier-main-pane.vue';

defineOptions({
  name: 'InventoryWarehouseReturnToSupplierPage',
});

const RETURN_TO_SUPPLIER_DRAFT_SCENE = INV_DOC_DRAFT_SCENE.warehouseReturnToSupplier;

const route = useRoute();
const { fromFinanceApproval } = useFromFinanceApproval();

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

const tableForm = ref<{
  applyTime?: string;
  sourceLocationId?: number | string;
}>({
  sourceLocationId: INV_ALL_LOCATIONS_FILTER_ID,
});

const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const warehouseRawOptions = ref<WarehouseOptionDto[]>([]);
const supplierRawOptions = ref<SupplierOptionDto[]>([]);
const supplierOptionItems = ref<ApiOptionItem[]>([]);
const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);
const mainPaneRef = ref<InstanceType<typeof ReturnToSupplierMainPane> | null>(null);

const showWelcomeEmpty = computed(() => selectedOrder.value === null);

function mapSupplierOptions(list: SupplierOptionDto[]): ApiOptionItem[] {
  return list.map((s) => ({
    label: String(s.supplierName ?? s.supplierId ?? ''),
    value: String(s.supplierId ?? ''),
  }));
}

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
    supplierId_dictText: String(cardMeta?.supplierId_dictText ?? ''),
    typeEnum_enumText: String(cardMeta?.typeEnum_enumText ?? ''),
    applyTime: String(cardMeta?.applyTime ?? formatDateTime(new Date())),
    applicantId_dictText: String(cardMeta?.applicantId_dictText ?? ''),
    totalAmount: cardMeta?.totalAmount,
  };
}

/** 丢弃侧栏上现有草稿卡片时同步清 session 缓存 */
function discardSidebarDraftCaches(): void {
  for (const row of orderList.value.filter((r) => r._draft)) {
    const id = String(row._clientDraftId ?? '').trim();
    if (id) clearInvDocDraftCache(RETURN_TO_SUPPLIER_DRAFT_SCENE, id);
  }
  clearInvDocClientDraftCachesForScene(RETURN_TO_SUPPLIER_DRAFT_SCENE);
}

/** 进入页时把 session 草稿卡片合并进队列，并选中最近一条 */
function rehydrateDraftCardsFromSession(): void {
  const merged = mergeInvDocDraftCardsIntoQueue({
    scene: RETURN_TO_SUPPLIER_DRAFT_SCENE,
    orderList: orderList.value,
    buildDraftRow: (clientDraftId, cardMeta) => createDraftOrderRow(clientDraftId, cardMeta),
  });
  orderList.value = merged.orderList;
  if (!selectedOrder.value && merged.inserted[0]) {
    selectedOrder.value = merged.inserted[0];
  }
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

/** 当前侧栏草稿的稳定客户端 id */
const selectedClientDraftId = computed(() => {
  const o = selectedOrder.value;
  if (!o?._draft) return '';
  return String(o._clientDraftId ?? '').trim();
});

async function init() {
  try {
    const res = await loadReturnToSupplierFormInitApi();
    const data = res.data;
    warehouseRawOptions.value = data?.warehouseOptions ?? [];
    supplierRawOptions.value = data?.supplierOptions ?? [];
    supplierOptionItems.value = mapSupplierOptions(supplierRawOptions.value);
    cabinetListOptions.value = mapReturnToSupplierInitToCabinetRows(warehouseRawOptions.value);
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
    warehouseRawOptions.value = [];
    supplierRawOptions.value = [];
    supplierOptionItems.value = [];
    cabinetListOptions.value = [];
  }
}

async function getList() {
  const draftRows = orderList.value.filter((r) => r._draft);
  const dr = listDateRange.value;
  try {
    const { records } = await pageReturnToSupplierWorkbenchListApi({
      sourceLocationId: normalizeSidebarLocationFilterValue(tableForm.value.sourceLocationId),
      keyword: queryParams.value.keyword?.trim() || undefined,
      applyTimeBegin: dr?.[0],
      applyTimeEnd: dr?.[1],
    });
    orderList.value = [...draftRows, ...(records as Record<string, unknown>[])];
  } catch {
    orderList.value = [...draftRows];
  }
}

async function handleCreateFromUpstream(row: LinkableGoodsReceiptListRowDto) {
  discardSidebarDraftCaches();
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  tableForm.value.applyTime = formatDateTime(new Date());
  mainPaneResetKey.value += 1;
  await nextTick();
  await mainPaneRef.value?.applyLinkableInboundToDraft(row);
}

function handleOrderClick(order: Record<string, unknown>) {
  selectedOrder.value = order;
  if (isDraftOrderRow(order)) {
    // 切回草稿由主区 sync 恢复缓存；勿 bump resetKey，否则会冲掉恢复内容
    return;
  }
}

async function handleDocumentSaved(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  await getList();
  const busNo = payload.busNo?.trim();
  if (!busNo) return;
  selectedOrder.value = pickWorkbenchOrderAfterSave(
    orderList.value,
    busNo,
    resolveReturnToSupplierBusNo,
  );
}

async function handleDocumentRemoved() {
  if (selectedOrder.value?._draft) {
    const id = String(selectedOrder.value._clientDraftId ?? '').trim();
    if (id) clearInvDocDraftCache(RETURN_TO_SUPPLIER_DRAFT_SCENE, id);
    orderList.value = orderList.value.filter((r) => r !== selectedOrder.value);
    selectedOrder.value = null;
    mainPaneResetKey.value += 1;
    return;
  }
  orderList.value = orderList.value.filter((r) => !r._draft);
  selectedOrder.value = null;
  mainPaneResetKey.value += 1;
  await getList();
}

watch(
  () => route.query,
  async (newQuery) => {
    const busNo = newQuery.busNo;
    if (!busNo || Array.isArray(busNo)) return;
    const busNoText = String(busNo).trim();
    if (!busNoText) return;
    if (cabinetListOptions.value.length === 0) {
      await init();
    }
    // 深链有单号：直接打开，不经侧栏日期/列表过滤
    selectedOrder.value = { busNo: busNoText, supplyBusNo: busNoText };
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
      <ReturnToSupplierAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:source-location-id="tableForm.sourceLocationId"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :selected-order="selectedOrder"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <ReturnToSupplierMainPane
      :queue-card-row="selectedOrder"
      ref="mainPaneRef"
      class="min-h-0 flex-1"
      :apply-time="tableForm.applyTime"
      :bus-no="selectedBusNo"
      :client-draft-id="selectedClientDraftId"
      :is-draft-order="isDraftOrder"
      :reset-key="mainPaneResetKey"
      :show-welcome-empty="showWelcomeEmpty"
      :supplier-options="supplierOptionItems"
      :warehouse-options="warehouseRawOptions"
      @create-from-upstream="handleCreateFromUpstream"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
