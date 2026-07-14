<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { BpcOrderListItemDto, PageBpcOrdersRequest } from '#/api/payment/union-pay-bpc';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElMessage,
  ElTag,
} from 'element-plus';

import { isUnionPayWinReady } from '#/api/local/openhisWin';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import {
  httpPageBpcOrders,
  httpQueryBpcOrder,
  httpRetrySettleFromBpcOrder,
} from '#/api/payment/union-pay-bpc';
import { useRequest } from '#/hooks/use-request';

defineOptions({
  name: 'UnionPayBpcOrders',
});

function defaultDateRange(): string[] {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - 6);
  const fmt = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  return [fmt(start), fmt(end)];
}

const tableData = ref<BpcOrderListItemDto[]>([]);
const total = ref(0);
const drawerOpen = ref(false);
const drawerRow = ref<BpcOrderListItemDto | null>(null);
const drawerSnapshot = ref<Awaited<ReturnType<typeof httpQueryBpcOrder>> | null>(null);
const settleCheck = ref<Awaited<ReturnType<typeof httpRetrySettleFromBpcOrder>> | null>(null);
const winReady = ref(false);
const posQueryLoading = ref(false);
const settleCheckLoading = ref(false);

const queryParams = reactive<PageBpcOrdersRequest & { dateRange?: string[] }>({
  pageNo: 1,
  pageSize: 20,
  dateRange: defaultDateRange(),
});

const { run: runList, loading: listLoading } = useRequest(httpPageBpcOrders, { manual: true });
const { run: runSnapshot, loading: snapshotLoading } = useRequest(httpQueryBpcOrder, {
  manual: true,
});

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '交易日期',
    prop: 'dateRange',
    type: 'daterange',
    startPlaceholder: '开始日期',
    endPlaceholder: '结束日期',
  },
  { label: '支付单号', prop: 'paymentId', type: 'input', placeholder: 'paymentId' },
  { label: '商户流水', prop: 'merTradeNo', type: 'input', placeholder: 'merTradeNo' },
  { label: '银联流水', prop: 'tradeNo', type: 'input', placeholder: 'tradeNo' },
  {
    label: '状态',
    prop: 'status',
    type: 'select',
    clearable: true,
    options: [
      { label: '待支付', value: 'PENDING' },
      { label: '成功', value: 'SUCCESS' },
      { label: '失败', value: 'FAILED' },
      { label: '已退货', value: 'REFUNDED' },
    ],
  },
  {
    label: '渠道',
    prop: 'payType',
    type: 'select',
    clearable: true,
    options: [
      { label: '微信', value: 'WEIX' },
      { label: '支付宝', value: 'ZFBA' },
      { label: '银联', value: 'UPAY' },
    ],
  },
  {
    label: '交易类型',
    prop: 'tranType',
    type: 'select',
    clearable: true,
    options: [
      { label: '消费 C', value: 'C' },
      { label: '查单 G', value: 'G' },
      { label: '退货 R', value: 'R' },
    ],
  },
  {
    label: '待补结算',
    prop: 'pendingSettleOnly',
    type: 'select',
    clearable: true,
    options: [{ label: '仅待补结算', value: true }],
  },
]);

const tableColumns: TableColumn[] = [
  { prop: 'createdAt', label: '创建时间', minWidth: 160, slot: 'createdAt' },
  { prop: 'merTradeNo', label: '商户流水号', minWidth: 180, showOverflowTooltip: true },
  { prop: 'tradeNo', label: '银联交易号', minWidth: 180, showOverflowTooltip: true },
  { prop: 'paymentId', label: '支付单号', width: 120 },
  { prop: 'txnAmtFen', label: '金额(元)', width: 100, slot: 'amount' },
  { prop: 'payType', label: '渠道', width: 90, slot: 'payType' },
  { prop: 'tranType', label: '类型', width: 72, slot: 'tranType' },
  { prop: 'status', label: 'BPC状态', width: 96, slot: 'status' },
  { prop: 'paymentStatus', label: 'HIS支付', width: 96, slot: 'paymentStatus' },
  { prop: 'pendingSettle', label: '补结算', width: 88, slot: 'pendingSettle' },
  { prop: 'action', label: '操作', fixed: 'right', width: 100, align: 'center', slot: 'action' },
];

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}

function formatAmountYuan(fen?: number) {
  if (fen == null || Number.isNaN(fen)) {
    return '-';
  }
  return formatMoneyDisplay(fen / 100);
}

function statusLabel(status?: string) {
  switch (status) {
    case 'PENDING':
      return '待支付';
    case 'SUCCESS':
      return '成功';
    case 'FAILED':
      return '失败';
    case 'REFUNDED':
      return '已退货';
    default:
      return status || '-';
  }
}

function statusTagType(status?: string) {
  switch (status) {
    case 'SUCCESS':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'REFUNDED':
      return 'info';
    case 'PENDING':
      return 'warning';
    default:
      return 'info';
  }
}

function payTypeLabel(payType?: string) {
  switch (payType) {
    case 'WEIX':
      return '微信';
    case 'ZFBA':
      return '支付宝';
    case 'UPAY':
      return '银联';
    default:
      return payType || '-';
  }
}

function tranTypeLabel(tranType?: string) {
  switch (tranType) {
    case 'C':
      return '消费';
    case 'G':
      return '查单';
    case 'R':
      return '退货';
    default:
      return tranType || '-';
  }
}

function paymentStatusLabel(status?: string) {
  switch (status) {
    case 'DRAFT':
      return '草稿';
    case 'SUCCESS':
      return '已结算';
    case 'FAILED':
      return '失败';
    default:
      return status || '-';
  }
}

function paymentStatusTagType(status?: string) {
  switch (status) {
    case 'SUCCESS':
      return 'success';
    case 'DRAFT':
      return 'warning';
    case 'FAILED':
      return 'danger';
    default:
      return 'info';
  }
}

async function getList(params?: PageBpcOrdersRequest & { dateRange?: string[] }) {
  const p = { ...queryParams, ...(params ?? {}) };
  const result = await runList({
    dateFrom: p.dateRange?.[0],
    dateTo: p.dateRange?.[1],
    pendingSettleOnly: p.pendingSettleOnly === true ? true : undefined,
    paymentId: p.paymentId ? String(p.paymentId).trim() || undefined : undefined,
    merTradeNo: p.merTradeNo?.trim() || undefined,
    tradeNo: p.tradeNo?.trim() || undefined,
    status: p.status || undefined,
    payType: p.payType || undefined,
    tranType: p.tranType || undefined,
    pageNo: p.pageNo,
    pageSize: p.pageSize,
  });
  tableData.value = result?.rows ?? [];
  total.value = Number(result?.total ?? 0);
}

function handleQuery(params: PageBpcOrdersRequest & { dateRange?: string[] }) {
  Object.assign(queryParams, params);
  void getList(params);
}

function handleReset() {
  queryParams.paymentId = undefined;
  queryParams.merTradeNo = '';
  queryParams.tradeNo = '';
  queryParams.status = '';
  queryParams.payType = '';
  queryParams.tranType = '';
  queryParams.pendingSettleOnly = undefined;
  queryParams.dateRange = defaultDateRange();
  queryParams.pageNo = 1;
  void getList();
}

function handlePagination(pagination: { pageNo?: number; pageSize?: number }) {
  if (pagination.pageNo) {
    queryParams.pageNo = pagination.pageNo;
  }
  if (pagination.pageSize) {
    queryParams.pageSize = pagination.pageSize;
  }
  void getList();
}

async function openDetail(row: BpcOrderListItemDto) {
  drawerRow.value = row;
  drawerSnapshot.value = null;
  settleCheck.value = null;
  drawerOpen.value = true;
  if (row.merTradeNo || row.tradeNo || row.paymentId) {
    drawerSnapshot.value = await runSnapshot({
      merTradeNo: row.merTradeNo,
      tradeNo: row.tradeNo,
      paymentId: row.paymentId,
    });
  }
  if (row.pendingSettle && row.paymentId && row.merTradeNo) {
    settleCheckLoading.value = true;
    try {
      settleCheck.value = await httpRetrySettleFromBpcOrder({
        paymentId: row.paymentId,
        merTradeNo: row.merTradeNo,
      });
    } catch {
      settleCheck.value = null;
    } finally {
      settleCheckLoading.value = false;
    }
  }
}

async function refreshWhaleSnapshot() {
  const row = drawerRow.value;
  if (!row) {
    return;
  }
  drawerSnapshot.value = await runSnapshot({
    merTradeNo: row.merTradeNo,
    tradeNo: row.tradeNo,
    paymentId: row.paymentId,
  });
  ElMessage.success('已刷新本地订单快照');
}

async function queryFromPos() {
  const row = drawerRow.value;
  if (!row?.merTradeNo) {
    return;
  }
  if (!winReady.value) {
    ElMessage.warning('本机未检测到 OpenHIS.Win 银联组件，无法 POS 查单');
    return;
  }
  posQueryLoading.value = true;
  try {
    const result = await queryOrderApi({
      merTradeNo: row.merTradeNo,
      tradeNo: row.tradeNo,
    });
    ElMessage.success(
      result.success
        ? `POS 查单：${result.respMsg || '成功'}`
        : `POS 查单：${result.respMsg || '失败'}`,
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : 'POS 查单失败';
    ElMessage.error(message);
  } finally {
    posQueryLoading.value = false;
  }
}

async function copyText(text?: string) {
  const value = String(text ?? '').trim();
  if (!value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(value);
    ElMessage.success('已复制');
  } catch {
    ElMessage.warning('复制失败');
  }
}

onMounted(() => {
  void (async () => {
    winReady.value = await isUnionPayWinReady();
    await getList();
  })();
});
</script>

<template>
  <Page auto-content-height>
    <TableLayout
      v-model:query-params="queryParams"
      :table-columns="tableColumns"
      :table-data="tableData"
      :total="total"
      :loading="listLoading"
      :form-items="formItems"
      row-key="orderId"
      show-index
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #createdAt="{ row }">
        {{ formatDateTime(row.createdAt) }}
      </template>
      <template #amount="{ row }">
        {{ formatAmountYuan(row.txnAmtFen) }}
      </template>
      <template #payType="{ row }">
        {{ payTypeLabel(row.payType) }}
      </template>
      <template #tranType="{ row }">
        {{ tranTypeLabel(row.tranType) }}
      </template>
      <template #status="{ row }">
        <ElTag :type="statusTagType(row.status)" size="small">
          {{ statusLabel(row.status) }}
        </ElTag>
      </template>
      <template #paymentStatus="{ row }">
        <ElTag :type="paymentStatusTagType(row.paymentStatus)" size="small">
          {{ paymentStatusLabel(row.paymentStatus) }}
        </ElTag>
      </template>
      <template #pendingSettle="{ row }">
        <ElTag v-if="row.pendingSettle" type="warning" size="small">待补</ElTag>
        <span v-else>-</span>
      </template>
      <template #action="{ row }">
        <ElButton link type="primary" @click="openDetail(row)">详情</ElButton>
      </template>
    </TableLayout>

    <ElDrawer v-model="drawerOpen" title="银联订单详情" size="480px">
      <template v-if="drawerRow">
        <ElDescriptions :column="1" border size="small" class="mb-4">
          <ElDescriptionsItem label="订单 ID">{{ drawerRow.orderId ?? '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="支付单号">
            <span>{{ drawerRow.paymentId ?? '-' }}</span>
            <ElButton
              v-if="drawerRow.paymentId"
              link
              type="primary"
              class="ml-2"
              @click="copyText(String(drawerRow.paymentId))"
            >
              复制
            </ElButton>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="商户流水号">
            <span>{{ drawerRow.merTradeNo ?? '-' }}</span>
            <ElButton
              v-if="drawerRow.merTradeNo"
              link
              type="primary"
              class="ml-2"
              @click="copyText(drawerRow.merTradeNo)"
            >
              复制
            </ElButton>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="银联交易号">
            <span>{{ drawerRow.tradeNo ?? '-' }}</span>
            <ElButton
              v-if="drawerRow.tradeNo"
              link
              type="primary"
              class="ml-2"
              @click="copyText(drawerRow.tradeNo)"
            >
              复制
            </ElButton>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="金额(元)">
            {{ formatAmountYuan(drawerRow.txnAmtFen) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="渠道">{{
            payTypeLabel(drawerRow.payType)
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="交易类型">{{
            tranTypeLabel(drawerRow.tranType)
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="BPC 状态">
            <ElTag :type="statusTagType(drawerRow.status)" size="small">
              {{ statusLabel(drawerRow.status) }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="HIS 支付状态">
            <ElTag :type="paymentStatusTagType(drawerRow.paymentStatus)" size="small">
              {{ paymentStatusLabel(drawerRow.paymentStatus) }}
            </ElTag>
            <ElTag v-if="drawerRow.pendingSettle" type="warning" size="small" class="ml-2">
              待补结算
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="终端号">{{ drawerRow.posNo ?? '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="响应码">{{ drawerRow.respCode ?? '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="响应信息">{{ drawerRow.respMsg ?? '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="创建时间">{{
            formatDateTime(drawerRow.createdAt)
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="更新时间">{{
            formatDateTime(drawerRow.updatedAt)
          }}</ElDescriptionsItem>
        </ElDescriptions>

        <div
          v-if="drawerRow.pendingSettle"
          class="mb-4 rounded border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900"
        >
          <div class="mb-1 font-medium">半成功：银联已扣款，HIS 尚未结算</div>
          <div v-if="settleCheckLoading">正在校验是否可补结算…</div>
          <template v-else-if="settleCheck">
            <div>
              {{ settleCheck.message || (settleCheck.eligible ? '可补结算' : '不可补结算') }}
            </div>
          </template>
          <p class="mt-2 text-xs">
            补结算需在<strong>原收银窗口</strong>触发（重传
            clientScanResult），财务页仅查询与对账，请勿对已成功 merTradeNo 再次发起 consumer-scan。
          </p>
        </div>

        <div v-if="drawerSnapshot" class="mb-4 rounded border border-dashed p-3 text-sm">
          <div class="mb-2 font-medium">Whale 本地快照</div>
          <div>状态：{{ statusLabel(drawerSnapshot.status) }}</div>
          <div>银联号：{{ drawerSnapshot.tradeNo ?? '-' }}</div>
          <div>金额(元)：{{ formatAmountYuan(drawerSnapshot.txnAmtFen) }}</div>
        </div>

        <div class="flex flex-wrap gap-2">
          <ElButton :loading="snapshotLoading" @click="refreshWhaleSnapshot">刷新本地快照</ElButton>
          <ElButton :loading="posQueryLoading" type="primary" plain @click="queryFromPos">
            POS 查单（本机 Win）
          </ElButton>
        </div>
        <p v-if="!winReady" class="mt-3 text-xs text-gray-500">
          POS 查单需在本机启动 OpenHIS.Win；财务 PC 未安装时可仅用上方列表与 Whale 快照对账。
        </p>
      </template>
    </ElDrawer>
  </Page>
</template>
