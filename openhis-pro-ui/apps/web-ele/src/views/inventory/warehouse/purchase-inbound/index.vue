<!-- 药库进货单：采购到货入库登记；工作站 + 侧栏卡片队列 + 右侧明细。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';
import type { LinkablePurchaseOrderListRowDto } from '#/api/inventory/warehouse/inbound-receipt';

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
import { useFromFinanceApproval } from '#/composables/use-from-finance-approval';
import { calcWarehouseSplitLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';
import {
  inboundReceiptStatusLabel,
  isInboundReceiptDocumentEditableRaw,
} from '#/utils/inventory/inbound-receipt-status';
import {
  clearInvDocClientDraftCachesForScene,
  clearInvDocDraftCache,
  INV_DOC_DRAFT_SCENE,
  mergeInvDocDraftCardsIntoQueue,
} from '#/utils/inventory/inv-doc-draft-cache';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  pickWorkbenchOrderAfterSave,
  resolveDefaultAllLocationsFilterValue,
} from '#/utils/inventory/inventory-workbench-list';

import PurchaseInboundAsidePane from './purchase-inbound-aside-pane.vue';
import PurchaseInboundMainPane from './purchase-inbound-main-pane.vue';

defineOptions({
  name: 'InventoryWarehousePurchaseInbound',
});

const INBOUND_DRAFT_SCENE = INV_DOC_DRAFT_SCENE.warehousePurchaseInbound;

/**
 * createInboundReceipt / updateInboundReceipt 必填经手人 {@code practitionerId}（医务人员主键）。
 * TODO: 改为单头选项绑定；当前为联调占位。
 */
const INBOUND_RECEIPT_PRACTITIONER_ID_STUB = '1';

const route = useRoute();
const userStore = useUserStore();
const { fromFinanceApproval } = useFromFinanceApproval();

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
  locationId: INV_ALL_LOCATIONS_FILTER_ID,
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
const isAddDisabled = ref(false);

const mainPaneResetKey = ref(0);
const mainPaneRef = ref<InstanceType<typeof PurchaseInboundMainPane> | null>(null);

const showWelcomeEmpty = computed(() => selectedOrder.value === null);

function isDraftOrderRow(row?: null | Record<string, any>): boolean {
  return Boolean(row?._draft);
}

/** 新建或按已有 clientDraftId 恢复草稿卡片 */
function createDraftOrderRow(
  clientDraftId?: string,
  cardMeta?: Record<string, unknown>,
): Record<string, any> {
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

/** 丢弃侧栏上现有草稿卡片时同步清 localStorage 缓存 */
function discardSidebarDraftCaches(): void {
  for (const row of orderList.value.filter((r) => r._draft)) {
    const id = String(row._clientDraftId ?? '').trim();
    if (id) clearInvDocDraftCache(INBOUND_DRAFT_SCENE, id);
  }
  clearInvDocClientDraftCachesForScene(INBOUND_DRAFT_SCENE);
}

/** 进入页时把 localStorage 草稿卡片合并进队列，并选中最近一条 */
function rehydrateDraftCardsFromSession(): void {
  const merged = mergeInvDocDraftCardsIntoQueue({
    scene: INBOUND_DRAFT_SCENE,
    orderList: orderList.value,
    buildDraftRow: (clientDraftId, cardMeta) => createDraftOrderRow(clientDraftId, cardMeta),
  });
  orderList.value = merged.orderList;
  if (!selectedOrder.value && merged.inserted[0]) {
    selectedOrder.value = merged.inserted[0];
    isAddDisabled.value = true;
  }
}

const PURCHASE_ORDER_DETAIL_PAGE_SIZE = 2000;

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

/** 当前侧栏草稿的稳定客户端 id */
const selectedClientDraftId = computed(() => {
  const o = selectedOrder.value;
  if (!o?._draft) return '';
  return String(o._clientDraftId ?? '').trim();
});

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

/** 待审核/驳回/已撤回可改单；财务审批深链强制只读 */
const inboundDocumentEditable = computed(() => {
  if (fromFinanceApproval.value) return false;
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
    if (
      tableForm.value.locationId === undefined ||
      tableForm.value.locationId === null ||
      String(tableForm.value.locationId).trim() === '' ||
      tableForm.value.locationId === 0
    ) {
      tableForm.value.locationId = resolveDefaultAllLocationsFilterValue(cabinetListOptions.value);
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
      statusEnum_enumText: r.statusEnumText ?? inboundReceiptStatusLabel(r.statusEnum),
      supplierId_dictText: r.supplierName ?? r.supplierId_dictText ?? '',
      rejectReason: r.rejectReason ?? r.reason ?? '',
    }));
    orderList.value = [...draftRows, ...mapped];
  } catch {
    orderList.value = [...draftRows];
  }
}

async function handleCreateFromUpstream(row: LinkablePurchaseOrderListRowDto) {
  resetFormAndTableData();
  discardSidebarDraftCaches();
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  mainPaneResetKey.value += 1;

  tableForm.value = {
    applicantId: resolveApplicantId(),
    applyTime: formatDateTime(new Date()),
    locationId: tableForm.value.locationId,
  };
  await nextTick();
  isAddDisabled.value = true;
  await mainPaneRef.value?.applyLinkablePurchaseOrderToDraft(row);
}

/** 空白新增进货草稿：可手填选品；订货单为可选关联 */
function handleAdd() {
  resetFormAndTableData();
  discardSidebarDraftCaches();
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  mainPaneResetKey.value += 1;
  tableForm.value = {
    applicantId: resolveApplicantId(),
    applyTime: formatDateTime(new Date()),
    locationId: tableForm.value.locationId,
  };
  void nextTick(() => {
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

  const busNo = resolvePurchaseOrderBusNo(row);
  if (!busNo) {
    ElMessage.error('请选择要编辑的单据');
    return;
  }

  // 勿在拉详情前清空 tableData：提交/撤回后重载会短暂 rows=[]，
  // 主区会把项目类型重置为默认项，与未清完的明细冲突并误报。
  try {
    const detail = await pagePurchaseInboundDetailApi({
      busNo,
      pageNo: 1,
      pageSize: PURCHASE_ORDER_DETAIL_PAGE_SIZE,
    });
    buildEditForm(detail.records ?? []);
    isAddDisabled.value = false;
  } catch {
    ElMessage.error('加载进货单详情失败');
    resetFormAndTableData();
  }
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
    unit.totalPriceMask = formatMoneyDisplay(
      calcWarehouseSplitLineTotal(item.itemQuantity, item.partPercent, item.price),
    );
    unit.totalRetailPriceMask = formatMoneyDisplay(
      calcWarehouseSplitLineTotal(item.itemQuantity, item.partPercent, item.retailPrice),
    );
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

  // 侧栏库房仅筛列表：详情回填勿覆盖；保存/驳回/撤回/切单后仍保持用户所选（默认全部）
  const sidebarLocationId = tableForm.value.locationId;
  tableForm.value = {
    busNo: head.busNo ?? head.supplyBusNo,
    applyTime: head.applyTime,
    supplierId: head.supplierId,
    applicantId: head.applicantId,
    categoryEnum: head.categoryEnum,
    reason: head.reason,
    locationId: sidebarLocationId,
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
    // 切回草稿由主区 sync 恢复缓存；勿 bump resetKey，否则会冲掉恢复内容
    return;
  }
  void handleEdit(order);
}

function handleLocationChange(locationId?: number | string) {
  const v = locationId;
  tableForm.value.locationId =
    v === undefined || v === null || String(v).trim() === '' ? INV_ALL_LOCATIONS_FILTER_ID : v;
  queryParams.value.locationId = tableForm.value.locationId;
  void getList();
}

async function handleInboundDocumentSaved(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  await getList();
  const busNo = payload.busNo?.trim();
  isAddDisabled.value = false;
  if (!busNo) return;
  selectedOrder.value = pickWorkbenchOrderAfterSave(
    orderList.value,
    busNo,
    resolvePurchaseOrderBusNo,
  );
  await handleEdit(selectedOrder.value);
}

async function handleInboundDocumentChanged(payload: { busNo?: string }) {
  await handleInboundDocumentSaved(payload);
}

async function handleInboundDocumentRemoved() {
  if (selectedOrder.value?._draft) {
    const id = String(selectedOrder.value._clientDraftId ?? '').trim();
    if (id) clearInvDocDraftCache(INBOUND_DRAFT_SCENE, id);
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
      :queue-card-row="selectedOrder"
      ref="mainPaneRef"
      class="min-h-0 flex-1"
      :apply-time="tableForm.applyTime"
      :bus-no="selectedBusNo"
      :client-draft-id="selectedClientDraftId"
      :practitioner-id="INBOUND_RECEIPT_PRACTITIONER_ID_STUB"
      :document-editable="inboundDocumentEditable"
      :inbound-detail-rows="tableData"
      :is-draft-order="isDraftOrder"
      :item-table-type-options="inboundItemTableTypeOptions"
      :reset-key="mainPaneResetKey"
      :show-welcome-empty="showWelcomeEmpty"
      :practitioner-options="practitionerOption"
      :supplier-options="supplierOption"
      :warehouse-options="cabinetListOptions"
      @create-blank="handleAdd"
      @create-from-upstream="handleCreateFromUpstream"
      @document-changed="handleInboundDocumentChanged"
      @document-removed="handleInboundDocumentRemoved"
      @document-saved="handleInboundDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
