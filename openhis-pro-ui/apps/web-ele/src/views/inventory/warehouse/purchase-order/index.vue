<!-- 药库订货单：工作站 + 侧栏卡片队列 + 明细；布局与操作同构药库进货单。 -->
<script lang="ts" setup>
import type {
  GetPurchaseOrderDetailResponse,
  LinkableDeptPurchaseRequestListRowDto,
  PurchaseOrderListRowDto,
} from '#/api/inventory/warehouse/purchase-order';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useUserStore } from '@zxplat/stores';
import { formatDateTime } from '@zxplat/utils';

import { ElMessage } from 'element-plus';

import {
  filterPurchaseOrderWarehouseOptions,
  getPurchaseOrderDetailApi,
  loadPurchaseOrderFormInitApi,
  mapPurchaseOrderInitToApiOptions,
  mapPurchaseOrderInitToCabinetRows,
  pagePurchaseOrderWorkbenchListApi,
  WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID,
} from '#/api/inventory/warehouse/purchase-order';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';
import { isInboundReceiptDocumentEditableRaw } from '#/utils/inventory/inbound-receipt-status';
import { inboundReceiptStatusLabel } from '#/utils/inventory/inbound-receipt-status';
import { pickWorkbenchOrderAfterSave } from '#/utils/inventory/inventory-workbench-list';

import PurchaseOrderAsidePane from './purchase-order-aside-pane.vue';
import PurchaseOrderMainPane from './purchase-order-main-pane.vue';

defineOptions({
  name: 'InventoryWarehousePurchaseOrderPage',
});

const route = useRoute();
const userStore = useUserStore();

const queryParams = ref<{ keyword?: string }>({ keyword: '' });

function createDefaultMonthRange(): [string, string] {
  const d = new Date();
  const y = d.getFullYear();
  const m = d.getMonth();
  const pad = (n: number) => String(n).padStart(2, '0');
  return [`${y}-${pad(m + 1)}-01`, `${y}-${pad(m + 1)}-${pad(d.getDate())}`];
}

const listDateRange = ref<[string, string] | null>(createDefaultMonthRange());

const listKeywordModel = computed({
  get: () => queryParams.value.keyword ?? '',
  set: (v: string) => {
    queryParams.value.keyword = v;
  },
});

const tableForm = ref<{
  locationId?: number | string;
  applyTime?: string;
}>({
  /** 左侧药库默认「全部库房」，与 init 首项 value=0 对齐 */
  locationId: WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID,
});

const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const warehouseHeadOptions = ref<Record<string, unknown>[]>([]);
const departmentPharmacyOptions = ref<Record<string, unknown>[]>([]);
const practitionerOption = ref<ApiOptionItem[]>([]);
const supplierOption = ref<ApiOptionItem[]>([]);
const itemTableTypeOptions = ref<ApiOptionItem[]>([]);

const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const orderDetail = ref<GetPurchaseOrderDetailResponse | null>(null);
const mainPaneResetKey = ref(0);
const mainPaneRef = ref<InstanceType<typeof PurchaseOrderMainPane> | null>(null);
const isAddDisabled = ref(false);

const selectedBusNo = computed(() => {
  const o = selectedOrder.value;
  if (!o || o._draft) return '';
  return resolveOrderBusNo(o) ?? '';
});

const isDraftOrder = computed(() => Boolean(selectedOrder.value?._draft));

const activeOrderStatusEnum = computed(() => {
  if (selectedOrder.value?._draft) return undefined;
  return orderDetail.value?.statusEnum ?? selectedOrder.value?.statusEnum;
});

const orderDocumentEditable = computed(() => {
  if (selectedOrder.value?._draft) return true;
  return isInboundReceiptDocumentEditableRaw(activeOrderStatusEnum.value);
});

const practitionerId = computed(
  () =>
    resolveLoginPractitionerIdForRequest(userStore.userInfo as null | Record<string, unknown>) ??
    practitionerOption.value[0]?.value,
);

function isDraftOrderRow(row?: null | Record<string, unknown>): boolean {
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
  };
}

function resolveOrderBusNo(row?: null | Record<string, unknown>): string | undefined {
  if (!row) return undefined;
  for (const k of ['supplyBusNo', 'busNo', 'orderBusNo', 'purchaseBusNo']) {
    const raw = row[k];
    if (raw === undefined || raw === null) continue;
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return undefined;
}

function toQueueRow(r: PurchaseOrderListRowDto): Record<string, unknown> {
  return {
    ...r,
    supplyBusNo: r.busNo,
    statusEnum_enumText: r.statusEnumText ?? inboundReceiptStatusLabel(r.statusEnum),
    supplierId_dictText: r.supplierName ?? '',
    purposeLocationId_dictText: r.purposeLocationName ?? '',
    totalPrice: r.totalAmount,
  };
}

async function init() {
  try {
    const res = await loadPurchaseOrderFormInitApi({});
    const data = res.data;
    const warehouseOpts = data?.warehouseLocationOptions ?? [];
    cabinetListOptions.value = mapPurchaseOrderInitToCabinetRows(warehouseOpts);
    warehouseHeadOptions.value = mapPurchaseOrderInitToCabinetRows(
      filterPurchaseOrderWarehouseOptions(warehouseOpts),
    );
    departmentPharmacyOptions.value = mapPurchaseOrderInitToCabinetRows(
      data?.departmentPharmacyLocationOptions ?? [],
    );
    supplierOption.value = mapPurchaseOrderInitToApiOptions(data?.supplierOptions);
    practitionerOption.value = mapPurchaseOrderInitToApiOptions(data?.warehouseKeeperOptions);
    itemTableTypeOptions.value = mapPurchaseOrderInitToApiOptions(
      data?.purchaseItemTableTypeOptions,
    );
    // 与 init 首项 OptionDto.value（"0"/全部）类型对齐，避免下拉显示裸数字 0
    const allValue = cabinetListOptions.value[0]?.value ?? cabinetListOptions.value[0]?.id;
    if (
      tableForm.value.locationId === undefined ||
      tableForm.value.locationId === null ||
      String(tableForm.value.locationId).trim() === '' ||
      tableForm.value.locationId === 0
    ) {
      tableForm.value.locationId =
        allValue === undefined || allValue === null
          ? WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID
          : (allValue as number | string);
    }
  } catch {
    cabinetListOptions.value = [];
    warehouseHeadOptions.value = [];
    departmentPharmacyOptions.value = [];
    supplierOption.value = [];
    practitionerOption.value = [];
    itemTableTypeOptions.value = [];
  }
}

async function getList() {
  const draftRows = orderList.value.filter((r) => r._draft);
  const dr = listDateRange.value;
  try {
    const { records } = await pagePurchaseOrderWorkbenchListApi({
      purposeLocationId: tableForm.value.locationId,
      keyword: queryParams.value.keyword?.trim() || undefined,
      applyTimeBegin: dr?.[0],
      applyTimeEnd: dr?.[1],
      pageNo: 1,
      pageSize: 100,
    });
    orderList.value = [...draftRows, ...records.map((r) => toQueueRow(r))];
  } catch {
    orderList.value = [...draftRows];
  }
}

function resetDetailState() {
  orderDetail.value = null;
}

function handleAdd() {
  resetDetailState();
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  mainPaneResetKey.value += 1;
  tableForm.value.applyTime = formatDateTime(new Date());
  void nextTick(() => {
    isAddDisabled.value = true;
  });
}

/** 选用申请单且当前非草稿：插入草稿并带入明细。 */
async function handleCreateFromUpstream(row: LinkableDeptPurchaseRequestListRowDto) {
  resetDetailState();
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  mainPaneResetKey.value += 1;
  tableForm.value.applyTime = formatDateTime(new Date());
  await nextTick();
  isAddDisabled.value = true;
  await mainPaneRef.value?.applyLinkableDeptPurchaseRequestToDraft(row);
}

async function handleEdit(order?: null | Record<string, unknown>) {
  const row = order ?? selectedOrder.value;
  if (isDraftOrderRow(row)) return;

  resetDetailState();
  const busNo = resolveOrderBusNo(row);
  if (!busNo) {
    ElMessage.error('请选择要编辑的单据');
    return;
  }

  try {
    const res = await getPurchaseOrderDetailApi({ busNo });
    orderDetail.value = res.data ?? null;
  } catch {
    ElMessage.error('加载订货单详情失败');
    return;
  }

  isAddDisabled.value = false;
}

function handleOrderClick(order: Record<string, unknown>) {
  selectedOrder.value = order;
  if (isDraftOrderRow(order)) {
    // 切回草稿由主区 sync 恢复缓存；勿 bump resetKey，否则会冲掉恢复内容
    return;
  }
  void handleEdit(order);
}

function handleLocationChange(locationId?: number | string) {
  const v = locationId;
  tableForm.value.locationId =
    v === undefined || v === null || String(v).trim() === ''
      ? WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID
      : v;
  void getList();
}

async function handleDocumentSaved(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  await getList();
  const busNo = payload.busNo?.trim();
  if (!busNo) return;
  selectedOrder.value = pickWorkbenchOrderAfterSave(orderList.value, busNo, resolveOrderBusNo);
  await handleEdit(selectedOrder.value);
}

async function handleDocumentChanged(payload: { busNo?: string }) {
  await handleDocumentSaved(payload);
}

async function handleDocumentRemoved() {
  if (selectedOrder.value?._draft) {
    orderList.value = orderList.value.filter((r) => r !== selectedOrder.value);
    selectedOrder.value = null;
    resetDetailState();
    mainPaneResetKey.value += 1;
    isAddDisabled.value = false;
    return;
  }
  orderList.value = orderList.value.filter((r) => !r._draft);
  resetDetailState();
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
    if (cabinetListOptions.value.length === 0) {
      await init();
    }
    queryParams.value.keyword = String(busNo);
    await getList();
    const row = orderList.value.find((item) => resolveOrderBusNo(item) === String(busNo));
    if (!row) return;
    selectedOrder.value = row;
    await handleEdit(row);
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
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <PurchaseOrderMainPane
      :queue-card-row="selectedOrder"
      ref="mainPaneRef"
      class="min-h-0 flex-1"
      :apply-time="tableForm.applyTime"
      :bus-no="selectedBusNo"
      :document-editable="orderDocumentEditable"
      :is-draft-order="isDraftOrder"
      :item-table-type-options="itemTableTypeOptions"
      :order-detail="orderDetail"
      :practitioner-id="practitionerId"
      :reset-key="mainPaneResetKey"
      :supplier-options="supplierOption"
      :warehouse-options="warehouseHeadOptions"
      :department-pharmacy-options="departmentPharmacyOptions"
      @create-from-upstream="handleCreateFromUpstream"
      @document-changed="handleDocumentChanged"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
