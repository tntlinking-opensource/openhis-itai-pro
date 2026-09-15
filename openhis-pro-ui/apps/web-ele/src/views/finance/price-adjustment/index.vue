<!-- 调价管理：工作站 + 侧栏卡片队列 + 右侧明细（同构药库进货单）。 -->
<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { formatDateTime } from '@vben/utils';

import { ElMessage } from 'element-plus';

import {
  getPriceAdjustmentDetailApi,
  initPriceAdjustmentPageApi,
  pagePriceAdjustmentListApi,
} from '#/api/finance/price-adjustment-page';
import WarehouseDocWorkstation from '#/components/warehouse/warehouse-doc-workstation.vue';
import { useFromFinanceApproval } from '#/composables/use-from-finance-approval';

import PriceAdjustmentAsidePane from './price-adjustment-aside-pane.vue';
import PriceAdjustmentMainPane from './price-adjustment-main-pane.vue';
import type { PriceAdjustmentHeadForm, PriceAdjustmentLineRow } from './types';
import { isPriceAdjustmentDocumentEditable } from './utils/price-adjustment-status';

defineOptions({
  name: 'PriceAdjustment',
});

const route = useRoute();
const { fromFinanceApproval } = useFromFinanceApproval();

const queryParams = ref<{ keyword?: string }>({
  keyword: '',
});

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

const tableForm = ref<Partial<PriceAdjustmentHeadForm>>({
  adjustmentReason: '',
  adjustmentTime: formatDateTime(new Date()),
});

const tableData = ref<PriceAdjustmentLineRow[]>([]);
const auditForm = ref<Record<string, unknown>>({});

const adjustmentReasonOptions = ref<ApiOptionItem[]>([]);
const itemTableTabOptions = ref<ApiOptionItem[]>([]);

const orderList = ref<Record<string, unknown>[]>([]);
const selectedOrder = ref<null | Record<string, unknown>>(null);
const mainPaneResetKey = ref(0);
const isAddDisabled = ref(false);

function isDraftOrderRow(row?: null | Record<string, unknown>): boolean {
  return Boolean(row?._draft);
}

/** 本地未保存草稿行 */
function createDraftOrderRow(): Record<string, unknown> {
  return {
    _draft: true,
    _clientDraftId: `draft:${Date.now()}:${Math.random().toString(36).slice(2, 9)}`,
    statusEnum_enumText: '编辑中',
    adjustmentTime: formatDateTime(new Date()),
    applyTime: formatDateTime(new Date()),
  };
}

function resolveBusNo(row?: null | Record<string, unknown>): string {
  return String(row?.busNo ?? '').trim();
}

const selectedBusNo = computed(() => resolveBusNo(selectedOrder.value));
const isDraftOrder = computed(() => Boolean(selectedOrder.value?._draft));

const activeStatusEnum = computed(() => {
  if (selectedOrder.value?._draft) return undefined;
  if (auditForm.value.statusEnum) return auditForm.value.statusEnum;
  return selectedOrder.value?.statusEnum;
});

const documentEditable = computed(() => {
  if (fromFinanceApproval.value) return false;
  if (selectedOrder.value?._draft) return true;
  return isPriceAdjustmentDocumentEditable(activeStatusEnum.value);
});

/** 页初始化：下拉选项 */
async function initPage() {
  try {
    const data = await initPriceAdjustmentPageApi();
    adjustmentReasonOptions.value = data.adjustmentReasonOptions ?? [];
    itemTableTabOptions.value = data.itemTableTabOptions ?? [];
    if (!tableForm.value.adjustmentReason && adjustmentReasonOptions.value.length > 0) {
      tableForm.value.adjustmentReason = String(adjustmentReasonOptions.value[0]?.value ?? '');
    }
  } catch {
    adjustmentReasonOptions.value = [];
    itemTableTabOptions.value = [];
  }
}

/** 左侧列表：保留本地草稿 + 服务端分页（操作科室由后端会话解析） */
async function getList() {
  const dr = listDateRange.value;
  const draftRows = orderList.value.filter((r) => r._draft);
  try {
    const data = await pagePriceAdjustmentListApi({
      keyword: queryParams.value.keyword,
      submitTimeFrom: dr?.[0] ? `${dr[0]}T00:00:00` : undefined,
      submitTimeTo: dr?.[1] ? `${dr[1]}T23:59:59` : undefined,
    });
    orderList.value = [...draftRows, ...(data.records ?? [])];
  } catch {
    orderList.value = [...draftRows];
  }
}

function resetFormAndTableData() {
  tableData.value = [];
  auditForm.value = {};
  tableForm.value = {
    adjustmentReason: String(adjustmentReasonOptions.value[0]?.value ?? ''),
    adjustmentTime: formatDateTime(new Date()),
  };
}

/** 加载已持久化单据详情 */
async function handleEdit(order?: null | Record<string, unknown>) {
  const row = order ?? selectedOrder.value;
  if (isDraftOrderRow(row)) return;

  resetFormAndTableData();
  const busNo = resolveBusNo(row);
  if (!busNo) {
    ElMessage.error('请选择要编辑的单据');
    return;
  }
  try {
    const detail = await getPriceAdjustmentDetailApi(busNo);
    tableForm.value = { ...detail.head };
    tableData.value = detail.lines.map((line) => ({ ...line }));
    auditForm.value = { ...detail.audit };
  } catch {
    ElMessage.error('加载调价单详情失败');
    return;
  }
  isAddDisabled.value = false;
}

function handleAdd() {
  resetFormAndTableData();
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
  if (isDraftOrderRow(order)) {
    resetFormAndTableData();
    mainPaneResetKey.value += 1;
    return;
  }
  void handleEdit(order);
}

/** 保存/提交/审批后刷新列表并回选 */
async function handleDocumentChanged(payload: { busNo?: string }) {
  orderList.value = orderList.value.filter((r) => !r._draft);
  isAddDisabled.value = false;
  await getList();
  const busNo = payload.busNo?.trim();
  if (!busNo) {
    selectedOrder.value = null;
    resetFormAndTableData();
    mainPaneResetKey.value += 1;
    return;
  }
  const row = orderList.value.find((item) => resolveBusNo(item) === busNo);
  if (!row) return;
  selectedOrder.value = row;
  await handleEdit(row);
  mainPaneResetKey.value += 1;
}

/** 删除本地草稿或撤销后清空选中 */
async function handleDocumentRemoved() {
  if (selectedOrder.value?._draft) {
    orderList.value = orderList.value.filter((r) => r !== selectedOrder.value);
    selectedOrder.value = null;
    resetFormAndTableData();
    mainPaneResetKey.value += 1;
    isAddDisabled.value = false;
    return;
  }
  selectedOrder.value = null;
  resetFormAndTableData();
  mainPaneResetKey.value += 1;
  isAddDisabled.value = false;
  await getList();
}

/** 支持财务审批等入口以 ?busNo= 深链定位单据 */
watch(
  () => route.query,
  async (newQuery) => {
    const busNo = newQuery.busNo;
    if (!busNo || Array.isArray(busNo)) return;
    const busNoText = String(busNo).trim();
    if (!busNoText) return;
    if (adjustmentReasonOptions.value.length === 0) {
      await initPage();
    }
    // 深链有单号：直接按 busNo 拉详情，不经侧栏日期/列表过滤
    const row = { busNo: busNoText };
    selectedOrder.value = row;
    isAddDisabled.value = false;
    await handleEdit(row);
  },
  { immediate: true },
);

onMounted(async () => {
  await initPage();
  await getList();
});
</script>

<template>
  <WarehouseDocWorkstation
    aside-width="280px"
    queue-title=""
    aside-dense
    :show-aside="!fromFinanceApproval"
    :show-aside-filter="false"
    :show-toolbar="false"
  >
    <template #aside-queue>
      <PriceAdjustmentAsidePane
        v-model:date-range="listDateRange"
        v-model:keyword="listKeywordModel"
        :add-disabled="isAddDisabled"
        :orders="orderList"
        :selected-order="selectedOrder"
        @add="handleAdd"
        @search="getList"
        @select="handleOrderClick"
      />
    </template>

    <PriceAdjustmentMainPane
      class="min-h-0 flex-1"
      :adjustment-detail-rows="tableData"
      :adjustment-reason-options="adjustmentReasonOptions"
      :audit-form="auditForm"
      :bus-no="selectedBusNo"
      :document-editable="documentEditable"
      :head-meta="tableForm"
      :is-draft-order="isDraftOrder"
      :item-table-tab-options="itemTableTabOptions"
      :reset-key="mainPaneResetKey"
      @document-changed="handleDocumentChanged"
      @document-removed="handleDocumentRemoved"
    />
  </WarehouseDocWorkstation>
</template>
