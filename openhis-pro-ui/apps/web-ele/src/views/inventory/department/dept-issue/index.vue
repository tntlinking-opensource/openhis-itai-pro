<!-- 科室领用单：工作站 + 侧栏卡片队列 + 右侧明细。 -->
<script lang="ts" setup>
import type { DeptPharmacyOptionDto } from '#/api/inventory/department/dept-issue';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useUserStore } from '@zxplat/stores';
import { formatDateTime } from '@zxplat/utils';

import {
  loadDeptIssueFormInitApi,
  mapDeptIssueInitOptionsToApiItems,
  mapDeptIssueInitToCabinetRows,
  pageDeptIssueWorkbenchListApi,
} from '#/api/inventory/department/dept-issue';
import {
  clearInvDocClientDraftCachesForScene,
  clearInvDocDraftCache,
  INV_DOC_DRAFT_SCENE,
  mergeInvDocDraftCardsIntoQueue,
} from '#/utils/inventory/inv-doc-draft-cache';

import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  INV_ITEM_TABLE_TYPE_DEVICE,
  mapInventoryPractitionerInitOptions,
  normalizeInventoryItemTableTypeOptions,
  normalizeSidebarLocationFilterValue,
  pickWorkbenchOrderAfterSave,
  resolveDefaultAllLocationsFilterValue,
} from '#/utils/inventory/inventory-workbench-list';
import {
  normalizeStockOutStatusSort,
  stockOutStatusLabel,
} from '#/utils/inventory/stock-out-doc-actions';

import DeptIssueAsidePane from './dept-issue-aside-pane.vue';
import DeptIssueMainPane from './dept-issue-main-pane.vue';

defineOptions({ name: 'InventoryDepartmentDeptIssuePage' });

const DEPT_ISSUE_DRAFT_SCENE = INV_DOC_DRAFT_SCENE.deptIssue;

const route = useRoute();
const userStore = useUserStore();

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

const tableForm = ref<{ purposeLocationId?: number | string }>({
  purposeLocationId: INV_ALL_LOCATIONS_FILTER_ID,
});

const cabinetListOptions = ref<Record<string, unknown>[]>([]);
const deptPharmacyOptions = ref<
  (ApiOptionItem & { organizationId?: string; organizationName?: string })[]
>([]);
const organizationOptions = ref<ApiOptionItem[]>([]);
const productTableTypeOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);
const isAddDisabled = ref(false);

function mapDeptPharmacyHeadOptions(
  list?: DeptPharmacyOptionDto[],
): (ApiOptionItem & { organizationId?: string; organizationName?: string })[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? o.organizationName ?? ''),
    value: String(o.value ?? ''),
    organizationId: o.organizationId,
    organizationName: o.organizationName,
  }));
}

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
    const res = await loadDeptIssueFormInitApi({});
    const data = res.data;
    deptPharmacyOptions.value = mapDeptPharmacyHeadOptions(data?.departmentPharmacyOptions);
    organizationOptions.value = mapDeptIssueInitOptionsToApiItems(data?.organizationOptions);
    productTableTypeOptions.value = normalizeInventoryItemTableTypeOptions(
      mapDeptIssueInitOptionsToApiItems(data?.productTableOptions),
      INV_ITEM_TABLE_TYPE_DEVICE,
    );
    practitionerOptions.value = mapInventoryPractitionerInitOptions(
      [data?.pharmacistOptions],
      userStore.userInfo,
    );
    cabinetListOptions.value = mapDeptIssueInitToCabinetRows(data?.departmentPharmacyOptions);
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
    deptPharmacyOptions.value = [];
    organizationOptions.value = [];
    productTableTypeOptions.value = [];
    practitionerOptions.value = [];
    cabinetListOptions.value = [];
  }
}

async function getList() {
  const draftRows = orderList.value.filter((r) => r._draft);
  const dr = listDateRange.value;
  try {
    const { records } = await pageDeptIssueWorkbenchListApi({
      purposeLocationId: normalizeSidebarLocationFilterValue(tableForm.value.purposeLocationId),
      keyword: queryParams.value.keyword?.trim() || undefined,
      applyTimeBegin: dr?.[0],
      applyTimeEnd: dr?.[1],
    });
    orderList.value = [
      ...draftRows,
      ...records.map((r) => ({
        ...r,
        supplyBusNo: r.busNo,
        statusEnum_enumText: stockOutStatusLabel(normalizeStockOutStatusSort(r.statusEnum)),
      })),
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
    if (id) clearInvDocDraftCache(DEPT_ISSUE_DRAFT_SCENE, id);
  }
  clearInvDocClientDraftCachesForScene(DEPT_ISSUE_DRAFT_SCENE);
}

/** 进入页时把 session 草稿卡片合并进队列，并选中最近一条 */
function rehydrateDraftCardsFromSession(): void {
  const merged = mergeInvDocDraftCardsIntoQueue({
    scene: DEPT_ISSUE_DRAFT_SCENE,
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
    if (id) clearInvDocDraftCache(DEPT_ISSUE_DRAFT_SCENE, id);
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
    if (cabinetListOptions.value.length === 0) await init();
    // 外链 busNo 只用于回选单据，勿写入侧栏检索；否则错误/残留单号会把列表滤空
    await getList();
    selectedOrder.value = pickWorkbenchOrderAfterSave(orderList.value, String(busNo), resolveBusNo);
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
      <DeptIssueAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:location-id="tableForm.purposeLocationId"
        :add-disabled="isAddDisabled"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :selected-order="selectedOrder"
        @add="handleAdd"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <DeptIssueMainPane
      :queue-card-row="selectedOrder"
      class="min-h-0 flex-1"
      :bus-no="selectedBusNo"
      :client-draft-id="selectedClientDraftId"
      :dept-pharmacy-options="deptPharmacyOptions"
      :organization-options="organizationOptions"
      :is-draft-order="isDraftOrder"
      :practitioner-options="practitionerOptions"
      :product-table-type-options="productTableTypeOptions"
      :reset-key="mainPaneResetKey"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
