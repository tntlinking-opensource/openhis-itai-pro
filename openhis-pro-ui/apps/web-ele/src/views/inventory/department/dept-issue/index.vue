<!-- 科室领用单：工作站 + 侧栏卡片队列 + 右侧明细。 -->
<script lang="ts" setup>
import type { DeptPharmacyOptionDto } from '#/api/inventory/department/dept-issue';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import {
  loadDeptIssueFormInitApi,
  mapDeptIssueInitOptionsToApiItems,
  mapDeptIssueInitToCabinetRows,
  pageDeptIssueWorkbenchListApi,
} from '#/api/inventory/department/dept-issue';
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

function handleAdd() {
  const draft = {
    _draft: true,
    statusEnum_enumText: '编辑中',
    applyTime: formatDateTime(new Date()),
  };
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
      class="min-h-0 flex-1"
      :bus-no="selectedBusNo"
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
