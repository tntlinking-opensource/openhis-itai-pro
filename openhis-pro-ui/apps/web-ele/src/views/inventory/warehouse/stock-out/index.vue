<!-- 药库出库单：工作站 + 侧栏卡片队列 + 右侧明细；布局对齐进货单。 -->
<script lang="ts" setup>
import type { PharmacyLocationOptionDto } from '#/api/inventory/pharmacy/requisition';
import type {
  StockOutFormInitOptionDto,
  StockOutListRowDto,
  WarehouseOptionDto,
} from '#/api/inventory/warehouse/stock-out';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@vben/utils';

import { initWarehouseStockOutApi, pageWarehouseStockOutListApi } from '#/api/warehouse/stock-out';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  enrichStockOutQueueCardRow,
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

import StockOutAsidePane from './stock-out-aside-pane.vue';
import StockOutMainPane from './stock-out-main-pane.vue';

defineOptions({
  name: 'InventoryWarehouseStockOutPage',
});

const STOCK_OUT_DRAFT_SCENE = INV_DOC_DRAFT_SCENE.warehouseStockOut;
/**
 * createStockOut / updateStockOut 必填经手人 {@code practitionerId}（医务人员主键）。
 * TODO: 改为单头选项绑定；当前为联调占位（与进货单同口径）。
 */
const STOCK_OUT_PRACTITIONER_ID_STUB = '1';

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
  applyTime?: string;
  purposeLocationId?: number | string;
  sourceLocationId?: number | string;
}>({
  purposeLocationId: INV_ALL_LOCATIONS_FILTER_ID,
  sourceLocationId: INV_ALL_LOCATIONS_FILTER_ID,
});

const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const pharmacyCabinetListOptions = ref<Record<string, unknown>[]>([]);
const warehouseRawOptions = ref<WarehouseOptionDto[]>([]);
const pharmacyRawOptions = ref<PharmacyLocationOptionDto[]>([]);

function mapInitItemTableTypes(opts: StockOutFormInitOptionDto[]): ApiOptionItem[] {
  return opts.map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
    sort:
      o.sort !== undefined && o.sort !== '' && Number.isFinite(Number(o.sort))
        ? Number(o.sort)
        : undefined,
  }));
}

const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);

const mainPaneResetKey = ref(0);
const mainPaneRef = ref<InstanceType<typeof StockOutMainPane> | null>(null);

/** 与进货单 init 的「项目类型」同构，传入主区选品 productTableName */
const itemTableTypeOptions = ref<ApiOptionItem[]>([]);

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
    if (id) clearInvDocDraftCache(STOCK_OUT_DRAFT_SCENE, id);
  }
  clearInvDocClientDraftCachesForScene(STOCK_OUT_DRAFT_SCENE);
}

/** 进入页时把 session 草稿卡片合并进队列，并选中最近一条 */
function rehydrateDraftCardsFromSession(): void {
  const merged = mergeInvDocDraftCardsIntoQueue({
    scene: STOCK_OUT_DRAFT_SCENE,
    orderList: orderList.value,
    buildDraftRow: (clientDraftId, cardMeta) => createDraftOrderRow(clientDraftId, cardMeta),
  });
  orderList.value = merged.orderList;
  if (!selectedOrder.value && merged.inserted[0]) {
    selectedOrder.value = merged.inserted[0];
    isAddDisabled.value = true;
  }
}

/** 侧栏卡片库房一行：草稿用当前筛选的源药库→目的药房名称（与进货单卡片「库房」展示对齐）。 */
function resolveStockOutQueueWarehouseLine(row: Record<string, unknown>): string | undefined {
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

/** 从队列行解析出库单号。 */
function resolveStockOutBusNo(row?: null | Record<string, unknown>): string | undefined {
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
  return resolveStockOutBusNo(o) ?? '';
});

const isDraftOrder = computed(() => Boolean(selectedOrder.value?._draft));

/** 当前侧栏草稿的稳定客户端 id */
const selectedClientDraftId = computed(() => {
  const o = selectedOrder.value;
  if (!o?._draft) return '';
  return String(o._clientDraftId ?? '').trim();
});

/** 初始化：药库出库 init 同时返回药库位、药房位及项目类型/状态等下拉。 */
async function init() {
  try {
    const wh = await initWarehouseStockOutApi();
    cabinetListOptions.value = wh.cabinetListOptions ?? [];
    warehouseRawOptions.value = wh.warehouseOptions ?? [];
    pharmacyRawOptions.value = wh.pharmacyLocationOptions ?? [];
    pharmacyCabinetListOptions.value = wh.pharmacyCabinetListOptions ?? [];
    itemTableTypeOptions.value = mapInitItemTableTypes(wh.inboundItemTableTypeOptions ?? []);
    tableForm.value.sourceLocationId = resolveDefaultAllLocationsFilterValue(
      cabinetListOptions.value,
    );
    tableForm.value.purposeLocationId = resolveDefaultAllLocationsFilterValue(
      pharmacyCabinetListOptions.value,
    );
  } catch {
    itemTableTypeOptions.value = [];
    cabinetListOptions.value = [];
    pharmacyCabinetListOptions.value = [];
    warehouseRawOptions.value = [];
    pharmacyRawOptions.value = [];
  }
}

/** 拉取左侧出库单列表并与本地草稿合并。 */
async function getList() {
  const dr = listDateRange.value;
  const draftRows = orderList.value.filter((r) => r._draft);
  try {
    const data = await pageWarehouseStockOutListApi({
      keyword: queryParams.value.keyword,
      purposeLocationId: normalizeSidebarLocationFilterValue(tableForm.value.purposeLocationId),
      sourceLocationId: normalizeSidebarLocationFilterValue(tableForm.value.sourceLocationId),
      ...(dr?.[0] ? { applyTimeBegin: dr[0] } : {}),
      ...(dr?.[1] ? { applyTimeEnd: dr[1] } : {}),
    });
    const records = (data.records ?? []).map((r) =>
      enrichStockOutQueueCardRow(r as Record<string, unknown>, {
        sourceOptions: cabinetListOptions.value as {
          locationId?: unknown;
          displayName?: string;
          value?: unknown;
          label?: string;
        }[],
        purposeOptions: pharmacyCabinetListOptions.value as {
          locationId?: unknown;
          displayName?: string;
          value?: unknown;
          label?: string;
        }[],
      }),
    );
    orderList.value = [...draftRows, ...records];
  } catch {
    orderList.value = [...draftRows];
  }
}

/** 新增草稿：插入队列并清空右侧待录入。 */
function handleAdd() {
  discardSidebarDraftCaches();
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  tableForm.value.applyTime = formatDateTime(new Date());
  mainPaneResetKey.value += 1;
  nextTick(() => {
    isAddDisabled.value = true;
  });
}

/** 点击队列卡片：草稿仅切状态；已存单据拉详情。 */
function handleOrderClick(order: Record<string, unknown>) {
  selectedOrder.value = order;
  if (isDraftOrderRow(order)) {
    // 切回草稿由主区 sync 恢复缓存；勿 bump resetKey，否则会冲掉恢复内容
    return;
  }
  isAddDisabled.value = false;
}

/** 同步草稿卡片与侧栏筛选项上的源/目的库位（关联请领带入后刷新左侧展示）。 */
function syncDraftHeadContext(payload: {
  originalBusNo?: string;
  purposeLocationId?: number | string;
  sourceLocationId?: number | string;
}) {
  const draft = selectedOrder.value;
  if (!draft?._draft) return;
  if (payload.sourceLocationId !== undefined && payload.sourceLocationId !== null) {
    draft.sourceLocationId = payload.sourceLocationId;
    tableForm.value.sourceLocationId = payload.sourceLocationId;
  }
  if (payload.purposeLocationId !== undefined && payload.purposeLocationId !== null) {
    draft.purposeLocationId = payload.purposeLocationId;
    tableForm.value.purposeLocationId = payload.purposeLocationId;
  }
  if (payload.originalBusNo?.trim()) {
    draft.originalBusNo = payload.originalBusNo.trim();
  }
}

/** 从请领单新建：左侧插入草稿并带入明细。 */
async function handleCreateFromRequisition(row: StockOutListRowDto) {
  discardSidebarDraftCaches();
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  tableForm.value.applyTime = formatDateTime(new Date());
  mainPaneResetKey.value += 1;
  await nextTick();
  isAddDisabled.value = true;
  await mainPaneRef.value?.applyLinkablePharmacyRequisitionToDraft(row);
}

function handleDraftHeadSynced(payload: {
  originalBusNo?: string;
  purposeLocationId?: number | string;
  sourceLocationId?: number | string;
}) {
  syncDraftHeadContext(payload);
}

/** 保存成功：清草稿、刷新并回选当前单号。 */
async function handleDocumentSaved(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  await getList();
  const busNo = payload.busNo?.trim();
  if (!busNo) return;
  selectedOrder.value = pickWorkbenchOrderAfterSave(orderList.value, busNo, resolveStockOutBusNo);
  isAddDisabled.value = false;
}

/** 删除成功：回到空选并刷新列表。 */
async function handleDocumentRemoved() {
  if (selectedOrder.value?._draft) {
    const id = String(selectedOrder.value._clientDraftId ?? '').trim();
    if (id) clearInvDocDraftCache(STOCK_OUT_DRAFT_SCENE, id);
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
    const row = orderList.value.find((item) => resolveStockOutBusNo(item) === String(busNo));
    if (!row) return;
    selectedOrder.value = row;
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
    :show-aside-filter="false"
    :show-toolbar="false"
  >
    <template #aside-queue>
      <StockOutAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:purpose-location-id="tableForm.purposeLocationId"
        v-model:source-location-id="tableForm.sourceLocationId"
        :add-disabled="isAddDisabled"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :pharmacy-list-options="pharmacyCabinetListOptions"
        :resolve-queue-warehouse-line="resolveStockOutQueueWarehouseLine"
        :selected-order="selectedOrder"
        @add="handleAdd"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <StockOutMainPane
      :queue-card-row="selectedOrder"
      ref="mainPaneRef"
      class="min-h-0 flex-1"
      :apply-time="tableForm.applyTime"
      :bus-no="selectedBusNo"
      :client-draft-id="selectedClientDraftId"
      :practitioner-id="STOCK_OUT_PRACTITIONER_ID_STUB"
      :is-draft-order="isDraftOrder"
      :item-table-type-options="itemTableTypeOptions"
      :pharmacy-options="pharmacyRawOptions"
      :reset-key="mainPaneResetKey"
      :sidebar-purpose-location-id="tableForm.purposeLocationId"
      :sidebar-source-location-id="tableForm.sourceLocationId"
      :warehouse-options="warehouseRawOptions"
      @create-from-requisition="handleCreateFromRequisition"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
      @draft-head-synced="handleDraftHeadSynced"
    />
  </WarehouseDocWorkstation>
</template>
