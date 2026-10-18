<!-- 科室退领单：工作站 + 侧栏卡片队列 + 右侧明细。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useUserStore } from '@zxplat/stores';
import { formatDateTime } from '@zxplat/utils';

import {
  loadDeptIssueReturnFormInitApi,
  mapDeptIssueReturnInitToCabinetRows,
  pageDeptIssueReturnWorkbenchListApi,
} from '#/api/inventory/department/dept-issue-return';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  mapInitOptionDtoToApiItems,
  mapInventoryPractitionerInitOptions,
  normalizeSidebarLocationFilterValue,
  pickWorkbenchOrderAfterSave,
  resolveDefaultAllLocationsFilterValue,
} from '#/utils/inventory/inventory-workbench-list';
import {
  normalizeStockOutStatusSort,
  stockOutStatusLabel,
} from '#/utils/inventory/stock-out-doc-actions';

import {
  clearInvDocClientDraftCachesForScene,
  clearInvDocDraftCache,
  INV_DOC_DRAFT_SCENE,
  mergeInvDocDraftCardsIntoQueue,
} from '#/utils/inventory/inv-doc-draft-cache';

import DeptIssueReturnAsidePane from './dept-issue-return-aside-pane.vue';
import DeptIssueReturnMainPane from './dept-issue-return-main-pane.vue';

defineOptions({ name: 'InventoryDepartmentDeptIssueReturnPage' });

const DEPT_ISSUE_RETURN_DRAFT_SCENE = INV_DOC_DRAFT_SCENE.deptIssueReturn;

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
const deptPharmacyOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);
const mainPaneRef = ref<InstanceType<typeof DeptIssueReturnMainPane> | null>(null);
const showWelcomeEmpty = computed(() => selectedOrder.value === null);

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
    if (id) clearInvDocDraftCache(DEPT_ISSUE_RETURN_DRAFT_SCENE, id);
  }
  clearInvDocClientDraftCachesForScene(DEPT_ISSUE_RETURN_DRAFT_SCENE);
}

/** 进入页时把 session 草稿卡片合并进队列，并选中最近一条 */
function rehydrateDraftCardsFromSession(): void {
  const merged = mergeInvDocDraftCardsIntoQueue({
    scene: DEPT_ISSUE_RETURN_DRAFT_SCENE,
    orderList: orderList.value,
    buildDraftRow: (clientDraftId, cardMeta) => createDraftOrderRow(clientDraftId, cardMeta),
  });
  orderList.value = merged.orderList;
  if (!selectedOrder.value && merged.inserted[0]) {
    selectedOrder.value = merged.inserted[0];
  }
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
    const res = await loadDeptIssueReturnFormInitApi({});
    const data = res.data;
    deptPharmacyOptions.value = mapInitOptionDtoToApiItems(data?.departmentPharmacies);
    practitionerOptions.value = mapInventoryPractitionerInitOptions(
      [data?.pharmacistOptions, data?.pharmacists],
      userStore.userInfo,
    );
    cabinetListOptions.value = mapDeptIssueReturnInitToCabinetRows(data?.departmentPharmacies);
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
    practitionerOptions.value = [];
    cabinetListOptions.value = [];
  }
}

async function getList() {
  const draftRows = orderList.value.filter((r) => r._draft);
  const dr = listDateRange.value;
  try {
    const { records } = await pageDeptIssueReturnWorkbenchListApi({
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

/** 新增：先选可退领用单，再落本地草稿（不建空白单）。 */
/** 选用领用单且当前非草稿：插入草稿并带入可退明细。 */
async function handleCreateFromUpstream(row: Record<string, unknown>) {
  discardSidebarDraftCaches();
  const draft = createDraftOrderRow();
  orderList.value = [draft, ...orderList.value.filter((r) => !r._draft)];
  selectedOrder.value = draft;
  mainPaneResetKey.value += 1;
  await nextTick();
  await mainPaneRef.value?.applyLinkableIssueToDraft(row);
}

function handleOrderClick(order: Record<string, unknown>) {
  selectedOrder.value = order;
  if (order._draft) {
    // 切回草稿由主区 sync 恢复缓存；勿 bump resetKey，否则会冲掉恢复内容
    return;
  }
}

async function handleDocumentSaved(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  await getList();
  const busNo = payload.busNo?.trim();
  if (!busNo) return;
  selectedOrder.value = pickWorkbenchOrderAfterSave(orderList.value, busNo, resolveBusNo);
}

async function handleDocumentRemoved() {
  if (selectedOrder.value?._draft) {
    const id = String(selectedOrder.value._clientDraftId ?? '').trim();
    if (id) clearInvDocDraftCache(DEPT_ISSUE_RETURN_DRAFT_SCENE, id);
    orderList.value = orderList.value.filter((r) => r !== selectedOrder.value);
    selectedOrder.value = null;
    mainPaneResetKey.value += 1;
    return;
  }
  selectedOrder.value = null;
  mainPaneResetKey.value += 1;
  await getList();
}

watch(
  () => route.query,
  async (q) => {
    const busNo = q.busNo;
    if (!busNo || Array.isArray(busNo)) return;
    if (cabinetListOptions.value.length === 0) await init();
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
      <DeptIssueReturnAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        v-model:location-id="tableForm.purposeLocationId"
        :cabinet-list-options="cabinetListOptions"
        :orders="orderList"
        :selected-order="selectedOrder"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <DeptIssueReturnMainPane
      :queue-card-row="selectedOrder"
      ref="mainPaneRef"
      class="min-h-0 flex-1"
      :bus-no="selectedBusNo"
      :client-draft-id="selectedClientDraftId"
      :dept-pharmacy-options="deptPharmacyOptions"
      :is-draft-order="isDraftOrder"
      :practitioner-options="practitionerOptions"
      :reset-key="mainPaneResetKey"
      :show-welcome-empty="showWelcomeEmpty"
      @create-from-upstream="handleCreateFromUpstream"
      @document-removed="handleDocumentRemoved"
      @document-saved="handleDocumentSaved"
    />
  </WarehouseDocWorkstation>
</template>
