<!-- 药库进货单：采购到货入库登记；工作站 + 侧栏卡片队列 + 右侧明细。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import { ElMessage } from 'element-plus';

import {
  initPurchaseInboundApi,
  pagePurchaseInboundDetailApi,
  pagePurchaseInboundListApi,
} from '#/api/warehouse/purchase-inbound';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import { isInboundReceiptDocumentEditableRaw } from '#/utils/inventory/inbound-receipt-status';

import PurchaseInboundAsidePane from './purchase-inbound-aside-pane.vue';
import PurchaseInboundMainPane from './purchase-inbound-main-pane.vue';

defineOptions({
  name: 'InventoryWarehousePurchaseInbound',
});

/**
 * createInboundReceipt / updateInboundReceipt 必填经手人 {@code practitionerId}（医务人员主键）。
 * TODO: 改为单头选项绑定；当前为联调占位。
 */
const INBOUND_RECEIPT_PRACTITIONER_ID_STUB = '1';

const route = useRoute();
const userStore = useUserStore();

function resolveApplicantId(): number | string | undefined {
  const u = userStore.userInfo as null | Record<string, any>;
  if (!u) return undefined;
  const ext = u.extraProperties ?? {};
  return u.practitionerId ?? ext.PractitionerId ?? ext.practitionerId;
}

const queryParams = ref<{
  keyword?: string;
  locationId?: number | string;
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

const tableForm = ref<Record<string, any>>({
  locationId: undefined,
  busNo: undefined,
  applyTime: undefined,
  applicantId: undefined,
  reason: undefined,
  categoryEnum: undefined,
  supplierId: undefined,
});

const tableData = ref<Record<string, any>[]>([]);

const auditForm = ref<Record<string, any>>({
  approvalTime: undefined,
  statusEnum: undefined,
  statusEnum_enumText: undefined,
  approverId: undefined,
});

const cabinetListOptions = ref<Record<string, any>[]>([]);
const practitionerOption = ref<ApiOptionItem[]>([]);
const supplierOption = ref<ApiOptionItem[]>([]);
const supplyStatusOptions = ref<ApiOptionItem[]>([]);
const supplyTypeOptions = ref<ApiOptionItem[]>([]);
const supplyCategoryOptions = ref<ApiOptionItem[]>([]);
const inboundItemTableTypeOptions = ref<ApiOptionItem[]>([]);

const orderList = ref<Record<string, any>[]>([]);
const selectedOrder = ref<null | Record<string, any>>(null);

const mainPaneResetKey = ref(0);

function isDraftOrderRow(row?: null | Record<string, any>): boolean {
  return Boolean(row?._draft);
}

function createDraftOrderRow(): Record<string, any> {
  return {
    _draft: true,
    _clientDraftId: `draft:${Date.now()}:${Math.random().toString(36).slice(2, 9)}`,
    statusEnum_enumText: '编辑中',
    supplierId_dictText: '',
    typeEnum_enumText: '',
    applyTime: formatDateTime(new Date()),
    applicantId_dictText: '',
    totalAmount: undefined,
  };
}

const PURCHASE_ORDER_DETAIL_PAGE_SIZE = 2000;

const isAddDisabled = ref(false);

function resolvePurchaseOrderBusNo(row?: null | Record<string, any>): string | undefined {
  if (!row) return undefined;
  const candidates = [row.supplyBusNo, row.busNo, row.orderBusNo, row.purchaseBusNo];
  for (const raw of candidates) {
    if (raw === undefined || raw === null) continue;
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return undefined;
}

const selectedBusNo = computed(() => {
  const o = selectedOrder.value;
  if (!o) return '';
  return resolvePurchaseOrderBusNo(o) ?? '';
});

const isDraftOrder = computed(() => Boolean(selectedOrder.value?._draft));

/** 详情头或列表行上的状态（加载中空表时退回列表行） */
const activeInboundStatusEnum = computed(() => {
  if (selectedOrder.value?._draft) return undefined;
  const head = tableData.value[0];
  if (
    head &&
    head.statusEnum !== undefined &&
    head.statusEnum !== null &&
    String(head.statusEnum).trim() !== ''
  ) {
    return head.statusEnum;
  }
  const sel = selectedOrder.value;
  if (
    sel &&
    sel.statusEnum !== undefined &&
    sel.statusEnum !== null &&
    String(sel.statusEnum).trim() !== ''
  ) {
    return sel.statusEnum;
  }
  return undefined;
});

/** 仅待审核、驳回可改单（本地草稿始终可编辑） */
const inboundDocumentEditable = computed(() => {
  if (selectedOrder.value?._draft) return true;
  return isInboundReceiptDocumentEditableRaw(activeInboundStatusEnum.value);
});

async function init() {
  try {
    const data = await initPurchaseInboundApi();
    cabinetListOptions.value = data.cabinetListOptions ?? [];
    practitionerOption.value = data.practitionerOption ?? [];
    supplierOption.value = data.supplierOption ?? [];
    supplyStatusOptions.value = data.supplyStatusOptions ?? [];
    supplyTypeOptions.value = data.supplyTypeOptions ?? [];
    supplyCategoryOptions.value = data.supplyCategoryOptions ?? [];
    inboundItemTableTypeOptions.value = data.inboundItemTableTypeOptions ?? [];
    if (tableForm.value.locationId === undefined && cabinetListOptions.value.length > 0) {
      const first = cabinetListOptions.value[0] as Record<string, unknown>;
      tableForm.value.locationId = (first.id ?? first.value) as number | string | undefined;
    }
  } catch {
    cabinetListOptions.value = [];
    practitionerOption.value = [];
    supplierOption.value = [];
    supplyStatusOptions.value = [];
    supplyTypeOptions.value = [];
    supplyCategoryOptions.value = [];
    inboundItemTableTypeOptions.value = [];
  }
}

async function getList() {
  const dr = listDateRange.value;
  const draftRows = orderList.value.filter((r) => r._draft);
  try {
    const data = await pagePurchaseInboundListApi({
      keyword: queryParams.value.keyword,
      locationId: tableForm.value.locationId,
      ...(dr?.[0] ? { applyTimeBegin: dr[0] } : {}),
      ...(dr?.[1] ? { applyTimeEnd: dr[1] } : {}),
    });
    const records = data.records ?? [];
    const mapped = records.map((r) => ({
      ...r,
      supplyBusNo: resolvePurchaseOrderBusNo(r),
    }));
    orderList.value = [...draftRows, ...mapped];
  } catch {
    orderList.value = [...draftRows];
  }
}

function handleAdd() {
  resetFormAndTableData();
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  mainPaneResetKey.value += 1;

  tableForm.value = {
    applicantId: resolveApplicantId(),
    applyTime: formatDateTime(new Date()),
    locationId: tableForm.value.locationId,
  };
  nextTick(() => {
    isAddDisabled.value = true;
  });
}

function resetFormAndTableData() {
  tableData.value = [];
  auditForm.value = {};
}

async function handleEdit(order?: null | Record<string, any>) {
  const row = order ?? selectedOrder.value;
  if (isDraftOrderRow(row)) return;

  resetFormAndTableData();
  const busNo = resolvePurchaseOrderBusNo(row);
  if (!busNo) {
    ElMessage.error('请选择要编辑的单据');
    return;
  }

  try {
    const detail = await pagePurchaseInboundDetailApi({
      busNo,
      pageNo: 1,
      pageSize: PURCHASE_ORDER_DETAIL_PAGE_SIZE,
    });
    buildEditForm(detail.records ?? []);
  } catch {
    ElMessage.error('加载进货单详情失败');
    return;
  }

  isAddDisabled.value = false;
}

function buildUnitList(item: Record<string, any>) {
  (item.unitList || []).forEach((unit: Record<string, any>) => {
    const isMaxUnit = unit.value === item.maxUnitCode;
    const isMinUnit = unit.value === item.minUnitCode;

    unit.priceMask = isMaxUnit
      ? item.price
      : item.price / Number.parseInt(String(item.partPercent), 10);
    unit.retailPriceMask = isMaxUnit
      ? item.retailPrice
      : item.retailPrice / Number.parseInt(String(item.partPercent), 10);
    unit.totalPriceMask = (
      Number.parseFloat(item.price) * Number.parseFloat(item.itemQuantity)
    ).toFixed(2);
    unit.totalRetailPriceMask = (
      Number.parseFloat(item.retailPrice) * Number.parseFloat(item.itemQuantity)
    ).toFixed(2);
    unit.specificationInventoryOriginal = item.specificationInventory;

    if (isMaxUnit) {
      unit.specificationInventoryMax = Math.floor(
        item.specificationInventory / Number.parseInt(String(item.partPercent), 10),
      );
      unit.specificationInventoryMaxUnit =
        unit.specificationInventoryMax > 0 ? item.maxUnitCode_dictText : '';
      unit.specificationInventoryMin =
        item.specificationInventory % Number.parseInt(String(item.partPercent), 10);
      unit.specificationInventoryMinUnit =
        unit.specificationInventoryMin > 0 ? item.minUnitCode_dictText : '';
    } else if (isMinUnit) {
      unit.specificationInventoryMax = item.specificationInventory;
      unit.specificationInventoryMaxUnit =
        unit.specificationInventoryMax > 0 ? item.minUnitCode_dictText : '';
      unit.specificationInventoryMin = 0;
      unit.specificationInventoryMinUnit =
        unit.specificationInventoryMin > 0 ? item.minUnitCode_dictText : '';
    } else {
      unit.specificationInventoryMax = item.specificationInventory;
      unit.specificationInventoryMaxUnit =
        unit.specificationInventoryMax > 0 ? item.minUnitCode_dictText : '';
      unit.specificationInventoryMin = 0;
      unit.specificationInventoryMinUnit =
        unit.specificationInventoryMin > 0 ? item.minUnitCode_dictText : '';
    }
  });
}

function buildEditForm(data: Record<string, any>[]) {
  const head = data[0];
  if (!head) return;

  tableForm.value = {
    busNo: head.busNo ?? head.supplyBusNo,
    applyTime: head.applyTime,
    supplierId: head.supplierId,
    applicantId: head.applicantId,
    categoryEnum: head.categoryEnum,
    reason: head.reason,
    locationId: head.purposeLocationId ?? head.locationId,
  };
  data.forEach((item) => {
    item.isEditing = false;
    item.isViewing = true;
    buildUnitList(item);
  });
  tableData.value = data;
  auditForm.value = {
    approvalTime: head.approvalTime,
    statusEnum: head.statusEnum,
    statusEnum_enumText: head.statusEnum_enumText,
    approverId: head.approverId,
  };
}

function handleOrderClick(order: Record<string, any>) {
  selectedOrder.value = order;
  if (isDraftOrderRow(order)) {
    resetFormAndTableData();
    mainPaneResetKey.value += 1;
    return;
  }
  void handleEdit(order);
}

function handleLocationChange(locationId?: number | string) {
  queryParams.value.locationId = locationId;
  void getList();
}

async function handleInboundDocumentSaved(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  await getList();
  const busNo = payload.busNo?.trim();
  if (!busNo) return;
  const row = orderList.value.find((item) => resolvePurchaseOrderBusNo(item) === busNo);
  if (!row) return;
  selectedOrder.value = row;
  await handleEdit(row);
}

async function handleInboundDocumentChanged(payload: { busNo?: string }) {
  await handleInboundDocumentSaved(payload);
}

async function handleInboundDocumentRemoved() {
  if (selectedOrder.value?._draft) {
    orderList.value = orderList.value.filter((r) => r !== selectedOrder.value);
    selectedOrder.value = null;
    resetFormAndTableData();
    mainPaneResetKey.value += 1;
    isAddDisabled.value = false;
    return;
  }
  orderList.value = orderList.value.filter((r) => !r._draft);
  resetFormAndTableData();
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
    if (tableForm.value.locationId === undefined || cabinetListOptions.value.length === 0) {
      await init();
    }
    queryParams.value.keyword = busNo;
    await getList();
    const row = orderList.value.find((item) => resolvePurchaseOrderBusNo(item) === String(busNo));
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
    aside-width="280px"
    queue-title=""
    aside-dense
    :show-aside-filter="false"
    :show-toolbar="false"
  >
    <template #aside-queue>
      <PurchaseInboundAsidePane
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

    <PurchaseInboundMainPane
      class="min-h-0 flex-1"
      :apply-time="tableForm.applyTime"
      :bus-no="selectedBusNo"
      :practitioner-id="INBOUND_RECEIPT_PRACTITIONER_ID_STUB"
      :document-editable="inboundDocumentEditable"
      :inbound-detail-rows="tableData"
      :is-draft-order="isDraftOrder"
      :item-table-type-options="inboundItemTableTypeOptions"
      :purpose-location-id="tableForm.locationId"
      :reset-key="mainPaneResetKey"
      :source-location-id="tableForm.locationId"
      :supplier-options="supplierOption"
      :warehouse-options="cabinetListOptions"
      @document-changed="handleInboundDocumentChanged"
      @document-removed="handleInboundDocumentRemoved"
      @document-saved="handleInboundDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
