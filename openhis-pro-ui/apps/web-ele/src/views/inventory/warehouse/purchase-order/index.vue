<!-- 药库订货单：工作站 + 侧栏队列 + 明细；对接 inventoryWarehousePurchaseOrder。 -->
<script lang="ts" setup>
import type {
  SupplierOptionDto,
  WarehouseDocumentDetailRowDto,
  WarehouseOptionDto,
} from '#/api/inventory/warehouse/purchase-order';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref } from 'vue';

import { formatDateTime } from '@vben/utils';

import { ElMessage } from 'element-plus';

import {
  loadPurchaseOrderFormInitApi,
  pagePurchaseOrdersApi,
  WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID,
  WHS_PO_TYPE_ENUM,
} from '#/api/inventory/warehouse/purchase-order';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import PurchaseOrderAsidePane from './purchase-order-aside-pane.vue';
import PurchaseOrderMainPane from './purchase-order-main-pane.vue';

defineOptions({
  name: 'InventoryWarehousePurchaseOrderPage',
});

/** 列表行 statusEnum → 卡片文案（与读模型 SupplyStatus.sortOrder 对齐） */
const STATUS_LABEL: Record<number, string> = {
  1: '待审核',
  2: '审核中',
  3: '同意',
  4: '驳回',
  9: '已撤回',
  10: '待复审',
  11: '已失效',
  12: '已发送',
};

function statusEnumText(sort?: number): string {
  if (sort === undefined || sort === null) return '';
  return STATUS_LABEL[sort] ?? String(sort);
}

const queryParams = ref<{
  keyword?: string;
}>({
  keyword: '',
});

function createDefaultMonthRange(): [string, string] {
  const d = new Date();
  const y = d.getFullYear();
  const m = d.getMonth();
  const pad = (n: number) => String(n).padStart(2, '0');
  const start = `${y}-${pad(m + 1)}-01`;
  const end = `${y}-${pad(m + 1)}-${pad(d.getDate())}`;
  return [start, end];
}

const listDateRange = ref<[string, string] | null>(createDefaultMonthRange());

const listKeywordModel = computed({
  get: () => queryParams.value.keyword ?? '',
  set: (v: string) => {
    queryParams.value.keyword = v;
  },
});

interface OrderTableForm {
  locationId?: number | string;
}

const tableForm = ref<OrderTableForm>({
  locationId: undefined,
});

const warehouseOptions = ref<WarehouseOptionDto[]>([]);
const supplierOptions = ref<SupplierOptionDto[]>([]);
const allWarehousesOption = ref<null | WarehouseOptionDto>(null);

const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const practitionerOption = ref<ApiOptionItem[]>([]);

const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);

const isAddDisabled = ref(false);

/** 左侧列表 purposeLocationId：0=全部药库 */
const numericListPurposeId = computed(() => {
  const v = tableForm.value.locationId;
  if (v === undefined || v === null || v === '') {
    return WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID;
  }
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) ? n : WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID;
});

/** 新增草稿时传给右侧的默认目的药库 */
const initialDraftPurposeId = computed((): number | undefined => {
  const id = numericListPurposeId.value;
  if (id === WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID) {
    return warehouseOptions.value[0]?.locationId;
  }
  return id;
});

const selectedBusNo = computed(() => {
  const o = selectedOrder.value;
  if (!o || o._draft) return '';
  return resolveOrderBusNo(o) ?? '';
});

const isMainDraft = computed(() => Boolean(selectedOrder.value?._draft));

function isDraftOrder(row?: null | Record<string, unknown>): boolean {
  return Boolean(row?._draft);
}

function createDraftOrderRow(): Record<string, unknown> {
  return {
    _draft: true,
    _clientDraftId: `draft:${Date.now()}:${Math.random().toString(36).slice(2, 9)}`,
    statusEnum_enumText: '编辑中',
    supplierId_dictText: '',
    applyTime: formatDateTime(new Date()),
    applicantId_dictText: '',
    totalPrice: undefined,
    purposeLocationId: tableForm.value.locationId,
  };
}

function resolveOrderBusNo(row?: null | Record<string, unknown>): string | undefined {
  if (!row) return undefined;
  const candidates = [row.supplyBusNo, row.busNo, row.orderBusNo, row.purchaseBusNo];
  for (const raw of candidates) {
    if (raw === undefined || raw === null) continue;
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return undefined;
}

/** 供应商名称：下拉字典 */
function supplierLabel(id?: number): string {
  if (id === undefined || id === null) return '';
  const hit = supplierOptions.value.find((s) => s.supplierId === id);
  return String(hit?.supplierName ?? '').trim();
}

/** 药库名称：下拉字典 */
function warehouseLabel(id?: number): string {
  if (id === undefined || id === null) return '';
  const hit = warehouseOptions.value.find((w) => w.locationId === id);
  return String(hit?.displayName ?? '').trim();
}

/** 将接口列表行转为卡片队列展示结构 */
function toQueueRow(r: WarehouseDocumentDetailRowDto): Record<string, unknown> {
  return {
    ...r,
    statusEnum_enumText: statusEnumText(r.statusEnum),
    supplierId_dictText: supplierLabel(r.supplierId),
    purposeLocationId_dictText: warehouseLabel(r.purposeLocationId),
  };
}

/** 加载药库/供应商下拉 */
async function loadInit() {
  try {
    const res = await loadPurchaseOrderFormInitApi({});
    const data = res.data;
    warehouseOptions.value = data?.warehouseOptions ?? [];
    supplierOptions.value = data?.supplierOptions ?? [];
    allWarehousesOption.value = data?.allWarehousesOption ?? null;

    const opts: Record<string, unknown>[] = [];
    const all = allWarehousesOption.value;
    if (all?.locationId !== undefined && all.locationId !== null) {
      opts.push({
        id: all.locationId,
        name: all.displayName ?? '全部药库',
      });
    }
    for (const w of warehouseOptions.value) {
      if (w.locationId !== undefined && w.locationId !== null) {
        opts.push({ id: w.locationId, name: w.displayName ?? String(w.locationId) });
      }
    }
    cabinetListOptions.value = opts;

    const allId = all?.locationId;
    if (allId !== undefined && allId !== null && tableForm.value.locationId === undefined) {
      tableForm.value.locationId = allId;
    } else if (tableForm.value.locationId === undefined && warehouseOptions.value.length > 0) {
      tableForm.value.locationId = warehouseOptions.value[0]?.locationId;
    }
  } catch {
    warehouseOptions.value = [];
    supplierOptions.value = [];
    allWarehousesOption.value = null;
    cabinetListOptions.value = [];
  }
}

/** 拉取左侧订货单分页并与本地草稿合并 */
async function fetchOrderList() {
  const wid = numericListPurposeId.value;
  try {
    const dr = listDateRange.value;
    const res = await pagePurchaseOrdersApi({
      purposeLocationId: wid,
      keyword: queryParams.value.keyword?.trim() || undefined,
      applyTimeFrom: dr?.[0],
      applyTimeTo: dr?.[1],
      pageNo: 1,
      pageSize: 100,
      typeEnum: WHS_PO_TYPE_ENUM,
    });
    const { content } = parsePagedRowsFromApiData<WarehouseDocumentDetailRowDto>(res.data);
    const records = content.map((r) => toQueueRow(r));
    const draftRows = orderList.value.filter((r) => r._draft);
    orderList.value = [...draftRows, ...records];
  } catch {
    const draftRows = orderList.value.filter((r) => r._draft);
    orderList.value = draftRows;
  }
}

function handleAdd() {
  const lid = numericListPurposeId.value;
  if (lid === WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID) {
    ElMessage.warning('当前为「全部药库」筛选，请先选择具体药库后再新增');
    return;
  }
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  mainPaneResetKey.value += 1;
  void nextTick(() => {
    isAddDisabled.value = true;
  });
}

function handleOrderClick(order: Record<string, unknown>) {
  selectedOrder.value = order;
  if (isDraftOrder(order)) {
    mainPaneResetKey.value += 1;
    return;
  }
  isAddDisabled.value = false;
}

function handleLocationChange() {
  void fetchOrderList();
}

/** 右侧保存/审批等写操作后刷新左侧并选中对应单据号 */
async function handleDocChanged(payload?: { busNo?: string }) {
  isAddDisabled.value = false;
  const keeperNo = payload?.busNo;
  orderList.value = orderList.value.filter((r) => !r._draft);
  await fetchOrderList();
  if (keeperNo) {
    const row = orderList.value.find((r) => resolveOrderBusNo(r) === keeperNo);
    selectedOrder.value =
      row ??
      ({
        busNo: keeperNo,
        statusEnum: 1,
        statusEnum_enumText: statusEnumText(1),
      } as Record<string, unknown>);
  } else {
    selectedOrder.value = orderList.value[0] ?? null;
  }
}

onMounted(async () => {
  await loadInit();
  await fetchOrderList();
  if (orderList.value.length > 0 && !selectedOrder.value) {
    const first = orderList.value[0];
    if (first && !first._draft) {
      selectedOrder.value = first;
    }
  }
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
      <PurchaseOrderAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:location-id="tableForm.locationId"
        :add-disabled="isAddDisabled"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :practitioner-option="practitionerOption"
        :selected-order="selectedOrder"
        :show-supplier-in-queue="true"
        @add="handleAdd"
        @location-change="handleLocationChange"
        @search="fetchOrderList"
        @select="handleOrderClick"
      />
    </template>

    <PurchaseOrderMainPane
      class="min-h-0 flex-1"
      :bus-no="selectedBusNo"
      :reset-key="mainPaneResetKey"
      :is-new-draft="isMainDraft"
      :initial-purpose-location-id="initialDraftPurposeId"
      :supplier-options="supplierOptions"
      :warehouse-options="warehouseOptions"
      :list-purpose-location-id="numericListPurposeId"
      @doc-changed="handleDocChanged"
    />
  </WarehouseDocWorkstation>
</template>
