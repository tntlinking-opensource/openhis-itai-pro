<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  BossDocumentDetailDto,
  BossDocumentListItemDto,
  PageDocumentsRequest,
} from '#/api/einvoice-boss/document';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { useAccess } from '@vben/access';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElAlert,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElImage,
  ElMessage,
  ElTag,
  ElTimeline,
  ElTimelineItem,
} from 'element-plus';

import {
  getDocumentByIdApi,
  getAccountStatusApi,
  pageDocumentsApi,
} from '#/api/einvoice-boss/document';
import { useRequest } from '#/hooks/use-request';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

defineOptions({
  name: 'EinvoiceList',
});

const router = useRouter();
const { hasAccessByCodes } = useAccess();
const canAdjust = computed(() => hasAccessByCodes(['einvoice-boss.document.adjust']));
const canAccount = computed(() => hasAccessByCodes(['einvoice-boss.account.view']));

const tableData = ref<BossDocumentListItemDto[]>([]);
const total = ref(0);
const keywordTruncated = ref(false);
const drawerOpen = ref(false);
const drawerDetail = ref<BossDocumentDetailDto | null>(null);
const accountStatus = ref<Awaited<ReturnType<typeof getAccountStatusApi>> | null>(null);

const queryParams = reactive<PageDocumentsRequest>({
  pageNo: 1,
  pageSize: 20,
});

const { run: runList, loading: listLoading } = useRequest(pageDocumentsApi, { manual: true });
const { run: runDetail, loading: detailLoading } = useRequest(getDocumentByIdApi, { manual: true });
const { run: runAccountStatus, loading: accountLoading } = useRequest(getAccountStatusApi, {
  manual: true,
});

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '日期',
    prop: 'dateRange',
    type: 'daterange',
    startPlaceholder: '开始日期',
    endPlaceholder: '结束日期',
  },
  { label: '患者', prop: 'keyword', type: 'input', placeholder: '姓名或就诊号' },
  { label: '票号', prop: 'billNo', type: 'input', placeholder: 'billNo' },
  {
    label: '状态',
    prop: 'invoiceStatus',
    type: 'select',
    clearable: true,
    options: [
      { label: '草稿', value: 'DRAFT' },
      { label: '已开具', value: 'ISSUED' },
      { label: '已作废', value: 'CANCELLED' },
    ],
  },
  {
    label: '业务线',
    prop: 'issueKind',
    type: 'select',
    clearable: true,
    options: [
      { label: '挂号', value: 'REG' },
      { label: '门诊', value: 'AMB' },
      { label: '住院', value: 'IMP' },
      { label: '通用票', value: 'COM' },
    ],
  },
]);

const tableColumns: TableColumn[] = [
  { prop: 'patientName', label: '患者', minWidth: 100, fixed: 'left' },
  { prop: 'issueKindLabel', label: '业务线', width: 80 },
  { prop: 'paymentAmt', label: '支付金额', width: 100, slot: 'paymentAmt' },
  { prop: 'bossBillNo', label: '票号', minWidth: 120, showOverflowTooltip: true },
  { prop: 'invoiceStatus', label: '发票状态', width: 100, slot: 'invoiceStatus' },
  { prop: 'writeOffStatus', label: '冲红状态', width: 110, slot: 'writeOffStatus' },
  { prop: 'issuedAt', label: '开票时间', minWidth: 150, slot: 'issuedAt' },
  { prop: 'action', label: '操作', fixed: 'right', width: 220, align: 'center', slot: 'action' },
];

function invoiceStatusLabel(status?: string) {
  switch (status) {
    case 'ISSUED':
      return '已开具';
    case 'CANCELLED':
      return '已作废';
    default:
      return '草稿';
  }
}

function writeOffStatusLabel(row: BossDocumentListItemDto) {
  if (row.invoiceStatus !== 'CANCELLED') {
    return '-';
  }
  return row.writeOffStatusLabel || '待确认';
}

function writeOffStatusType(row: { invoiceStatus?: string; writeOffStatus?: string }) {
  switch (row.writeOffStatus) {
    case 'SUCCESS':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'SKIPPED':
      return 'info';
    default:
      return row.invoiceStatus === 'CANCELLED' ? 'warning' : 'info';
  }
}

function invoiceStatusType(status?: string) {
  switch (status) {
    case 'ISSUED':
      return 'success';
    case 'CANCELLED':
      return 'info';
    default:
      return 'info';
  }
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 16);
}

async function getList(params?: PageDocumentsRequest & { dateRange?: string[] }) {
  const p = { ...queryParams, ...(params ?? {}) };
  const result = await runList({
    keyword: p.keyword?.trim() || undefined,
    billNo: p.billNo?.trim() || undefined,
    invoiceStatus: p.invoiceStatus || undefined,
    issueKind: p.issueKind || undefined,
    dateFrom: p.dateRange?.[0],
    dateTo: p.dateRange?.[1],
    pageNo: p.pageNo,
    pageSize: p.pageSize,
  });
  tableData.value = result?.rows ?? [];
  total.value = Number(result?.total ?? 0);
  keywordTruncated.value = Boolean(result?.keywordTruncated);
}

function handleQuery(params: PageDocumentsRequest & { dateRange?: string[] }) {
  Object.assign(queryParams, params);
  void getList(params);
}

function handleReset() {
  queryParams.keyword = '';
  queryParams.billNo = '';
  queryParams.invoiceStatus = '';
  queryParams.issueKind = '';
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

async function openIntranetDetail(row: BossDocumentListItemDto) {
  if (!row.documentId) {
    return;
  }
  const data = await runDetail(row.documentId);
  const url = data?.pictureUrl?.trim();
  if (!url) {
    ElMessage.warning(
      data?.invoiceStatus === 'ISSUED' ? '暂无内网票面链接，请稍后在补偿页查票' : '票据尚未开具',
    );
    return;
  }
  window.open(url, '_blank', 'noopener,noreferrer');
}

async function openQrDrawer(row: BossDocumentListItemDto) {
  if (!row.documentId) {
    return;
  }
  drawerOpen.value = true;
  accountStatus.value = null;
  const data = await runDetail(row.documentId);
  drawerDetail.value = data ?? null;
}

async function queryAccountStatus() {
  if (!drawerDetail.value?.documentId) {
    return;
  }
  accountStatus.value = await runAccountStatus(drawerDetail.value.documentId);
}

function goAdjust(row: BossDocumentListItemDto) {
  if (!row.documentId) {
    return;
  }
  router.push(`/finance/einvoice/document/${row.documentId}/amount`);
}

const qrSrc = computed(() => {
  const code = drawerDetail.value?.billQrCode?.trim();
  if (!code) {
    return '';
  }
  if (code.startsWith('data:') || code.startsWith('http')) {
    return code;
  }
  return `data:image/png;base64,${code}`;
});

onMounted(() => {
  void getList();
});
</script>

<template>
  <Page auto-content-height>
    <ElAlert
      v-if="keywordTruncated"
      class="mb-3"
      title="关键词匹配结果过多，仅展示部分记录，请缩小筛选范围"
      type="warning"
      show-icon
      :closable="false"
    />
    <TableLayout
      v-model:query-params="queryParams"
      :table-columns="tableColumns"
      :table-data="tableData"
      :form-items="formItems"
      :loading="listLoading"
      :total="total"
      show-pagination
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #paymentAmt="{ row }">
        {{ row.paymentAmt != null ? formatMoneyDisplay(row.paymentAmt) : '-' }}
      </template>
      <template #invoiceStatus="{ row }">
        <ElTag :type="invoiceStatusType(row.invoiceStatus)" size="small">
          {{ invoiceStatusLabel(row.invoiceStatus) }}
        </ElTag>
      </template>
      <template #writeOffStatus="{ row }">
        <ElTag
          v-if="row.invoiceStatus === 'CANCELLED'"
          :type="writeOffStatusType(row)"
          size="small"
        >
          {{ writeOffStatusLabel(row) }}
        </ElTag>
        <span v-else class="text-[var(--el-text-color-placeholder)]">-</span>
      </template>
      <template #issuedAt="{ row }">
        {{ formatDateTime(row.issuedAt) }}
      </template>
      <template #action="{ row }">
        <ElButton
          v-if="row.invoiceStatus === 'ISSUED'"
          link
          type="primary"
          @click="openIntranetDetail(row)"
        >
          详情
        </ElButton>
        <ElButton
          v-if="row.invoiceStatus === 'ISSUED'"
          link
          type="primary"
          @click="openQrDrawer(row)"
        >
          QR
        </ElButton>
        <ElButton
          v-if="row.invoiceStatus === 'CANCELLED'"
          link
          type="primary"
          @click="openQrDrawer(row)"
        >
          冲红详情
        </ElButton>
        <ElButton
          v-if="canAdjust && row.invoiceStatus === 'DRAFT'"
          link
          type="warning"
          @click="goAdjust(row)"
        >
          调额
        </ElButton>
      </template>
    </TableLayout>

    <ElDrawer
      v-model="drawerOpen"
      :title="drawerDetail?.invoiceStatus === 'CANCELLED' ? '冲红详情' : '电子发票二维码'"
      size="480px"
    >
      <div v-loading="detailLoading">
        <template v-if="drawerDetail">
          <ElDescriptions :column="1" border size="small" class="mb-4">
            <ElDescriptionsItem label="患者">{{ drawerDetail.patientName }}</ElDescriptionsItem>
            <ElDescriptionsItem label="就诊号">{{
              drawerDetail.encounterBusNo
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="busNo">{{ drawerDetail.busNo }}</ElDescriptionsItem>
            <ElDescriptionsItem label="票号">{{
              drawerDetail.bossBillNo || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="批次号">{{
              drawerDetail.bossBatchCode || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="开具时间">
              {{ formatDateTime(drawerDetail.issuedAt) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem v-if="drawerDetail.invoiceStatus === 'CANCELLED'" label="冲红状态">
              <ElTag :type="writeOffStatusType(drawerDetail)" size="small">
                {{ drawerDetail.writeOffStatusLabel || '待确认' }}
              </ElTag>
            </ElDescriptionsItem>
            <ElDescriptionsItem v-if="drawerDetail.writeOffBillNo" label="红票号">
              {{ drawerDetail.writeOffBillNo }}
            </ElDescriptionsItem>
            <ElDescriptionsItem v-if="drawerDetail.writeOffAt" label="冲红时间">
              {{ formatDateTime(drawerDetail.writeOffAt) }}
            </ElDescriptionsItem>
          </ElDescriptions>

          <div
            v-if="drawerDetail.invoiceStatus === 'CANCELLED' && drawerDetail.writeOffMessage"
            class="mb-4 text-sm text-[var(--el-color-danger)]"
          >
            冲红说明：{{ drawerDetail.writeOffMessage }}
          </div>

          <div v-if="drawerDetail.pictureUrl || drawerDetail.pictureNetUrl" class="mb-4 text-sm">
            <div v-if="drawerDetail.pictureUrl">
              内网：<a :href="drawerDetail.pictureUrl" target="_blank" rel="noopener">{{
                drawerDetail.pictureUrl
              }}</a>
            </div>
            <div v-if="drawerDetail.pictureNetUrl">
              外网：<a :href="drawerDetail.pictureNetUrl" target="_blank" rel="noopener">{{
                drawerDetail.pictureNetUrl
              }}</a>
            </div>
          </div>

          <div class="mb-4">
            <div class="mb-2 text-sm font-medium">二维码</div>
            <ElImage
              v-if="drawerDetail.invoiceStatus === 'ISSUED' && qrSrc"
              :src="qrSrc"
              fit="contain"
              class="h-40 w-40"
            />
            <div v-else class="text-sm text-[var(--el-text-color-placeholder)]">开票中</div>
          </div>

          <div v-if="drawerDetail.task" class="mb-4 text-sm">
            <div class="mb-1 font-medium">任务</div>
            <div>状态：{{ drawerDetail.task.status }}</div>
            <div v-if="drawerDetail.task.lastErrorMsg">
              错误：{{ drawerDetail.task.lastErrorMsg }}
            </div>
            <div>重试：{{ drawerDetail.task.attemptCount ?? 0 }}</div>
          </div>

          <div v-if="canAccount && drawerDetail.invoiceStatus === 'ISSUED'" class="mb-4">
            <ElButton size="small" :loading="accountLoading" @click="queryAccountStatus">
              查询入账状态
            </ElButton>
            <div v-if="accountStatus" class="mt-2 text-sm">
              <div>
                入账状态：{{
                  accountStatus.accountStatusLabel || accountStatus.accountStatus || '-'
                }}
              </div>
              <div>入账日期：{{ accountStatus.accountDate || '-' }}</div>
            </div>
          </div>

          <div v-if="drawerDetail.adjustLogs?.length" class="text-sm">
            <div class="mb-2 font-medium">调额记录</div>
            <ElTimeline>
              <ElTimelineItem
                v-for="log in drawerDetail.adjustLogs"
                :key="log.id"
                :timestamp="formatDateTime(log.createdAt)"
              >
                {{ formatMoneyDisplay(log.beforeTotal) }} →
                {{ formatMoneyDisplay(log.afterTotal) }}：{{ log.reason }}
              </ElTimelineItem>
            </ElTimeline>
          </div>
        </template>
      </div>
    </ElDrawer>
  </Page>
</template>
