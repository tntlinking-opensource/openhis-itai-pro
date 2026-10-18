<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type {
  BossCompensationRowDto,
  BossPendingCompensationStatsDto,
  CompensationExceptionType,
  EinvoiceConfirmAction,
} from '#/api/einvoice-boss/compensation';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@zxplat/common-ui';
import { useAccess } from '@zxplat/access';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import {
  ElButton,
  ElAlert,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElMessage,
  ElMessageBox,
  ElTag,
} from 'element-plus';

import {
  batchCompensateRequeueApi,
  batchIssueNowApi,
  compensateRequeueApi,
  issueNowApi,
  listPendingCompensationApi,
  recoverByBusNoApi,
} from '#/api/einvoice-boss/compensation';
import EinvoiceConfirmDialog from '#/components/einvoice/EinvoiceConfirmDialog.vue';
import { refreshEinvoiceCompensationMenuBadge } from '#/composables/use-einvoice-compensation-badge';
import { useRequest } from '#/hooks/use-request';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

defineOptions({
  name: 'EinvoiceCompensation',
});

const { hasAccessByCodes } = useAccess();
const router = useRouter();
const canOperate = computed(() => hasAccessByCodes(['einvoice-boss.compensation.operate']));
const canAdjust = computed(() => hasAccessByCodes(['einvoice-boss.document.adjust']));

const tableData = ref<BossCompensationRowDto[]>([]);
const stats = ref<BossPendingCompensationStatsDto>({});
const total = ref(0);

const queryParams = reactive<{
  keyword?: string;
  exceptionType?: CompensationExceptionType | '';
  issueKind?: string;
  pageNo: number;
  pageSize: number;
}>({
  pageNo: 1,
  pageSize: 20,
});

const confirmOpen = ref(false);
const confirmAction = ref<EinvoiceConfirmAction | null>(null);
const activeRow = ref<BossCompensationRowDto | null>(null);
const selectedRows = ref<BossCompensationRowDto[]>([]);

const { run: runList, loading: listLoading } = useRequest(listPendingCompensationApi, {
  manual: true,
});
const { run: runRequeue, loading: requeueLoading } = useRequest(compensateRequeueApi, {
  manual: true,
});
const { run: runIssueNow, loading: issueNowLoading } = useRequest(issueNowApi, { manual: true });
const { run: runRecover, loading: recoverLoading } = useRequest(recoverByBusNoApi, {
  manual: true,
});
const { run: runBatchRequeue, loading: batchRequeueLoading } = useRequest(
  batchCompensateRequeueApi,
  {
    manual: true,
  },
);
const { run: runBatchIssue, loading: batchIssueLoading } = useRequest(batchIssueNowApi, {
  manual: true,
});

const operating = computed(
  () =>
    listLoading.value ||
    requeueLoading.value ||
    issueNowLoading.value ||
    recoverLoading.value ||
    batchRequeueLoading.value ||
    batchIssueLoading.value,
);

const selectedCount = computed(() => selectedRows.value.length);
const batchRequeueCount = computed(
  () => selectedRows.value.filter((row) => row.canRequeue && row.invoiceId).length,
);
const batchIssueCount = computed(
  () => selectedRows.value.filter((row) => row.canIssueNow && row.invoiceId).length,
);

const scanTruncated = computed(() => Boolean(stats.value.scanTruncated));

const statsSummary = computed(() => {
  const s = stats.value;
  return [
    { label: '待处理', value: s.pendingCount ?? 0 },
    { label: '事件丢失', value: s.lostCount ?? 0 },
    { label: '金额待调整', value: s.amountCount ?? 0 },
    { label: '开票失败', value: s.failedCount ?? 0 },
    { label: '超时待查', value: s.staleCount ?? 0 },
  ];
});

const formItems = computed<FormItemConfig[]>(() => [
  { label: '患者/就诊号', prop: 'keyword', type: 'input', placeholder: '姓名或就诊号' },
  {
    label: '异常类型',
    prop: 'exceptionType',
    type: 'select',
    clearable: true,
    options: [
      { label: '事件丢失', value: 'LOST' },
      { label: '金额待调整', value: 'AMOUNT' },
      { label: '开票失败', value: 'FAILED' },
      { label: '超时待查', value: 'STALE' },
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
    ],
  },
]);

const tableColumns: TableColumn[] = [
  {
    type: 'selection',
    width: 48,
    fixed: 'left',
    selectable: (row: BossCompensationRowDto) => !!(row.canRequeue || row.canIssueNow),
  },
  { prop: 'patientName', label: '患者', minWidth: 100, fixed: 'left' },
  { prop: 'encounterBusNo', label: '就诊号', minWidth: 140, showOverflowTooltip: true },
  { prop: 'issueKindLabel', label: '业务线', width: 80 },
  { prop: 'totalAmt', label: '开票金额', width: 100, slot: 'totalAmt' },
  { prop: 'busNo', label: 'busNo', minWidth: 160, showOverflowTooltip: true },
  { prop: 'exceptionTypeLabel', label: '异常类型', width: 110, slot: 'exceptionType' },
  { prop: 'waitingMinutes', label: '等待时长', width: 100, slot: 'waitingMinutes' },
  { prop: 'action', label: '操作', fixed: 'right', width: 120, align: 'center', slot: 'action' },
];

function exceptionTagType(type?: CompensationExceptionType) {
  switch (type) {
    case 'LOST':
      return 'danger';
    case 'AMOUNT':
      return 'warning';
    case 'FAILED':
      return 'danger';
    case 'STALE':
      return 'info';
    default:
      return 'info';
  }
}

async function getList(params?: typeof queryParams) {
  const p = params ?? queryParams;
  const result = await runList({
    keyword: p.keyword?.trim() || undefined,
    exceptionType: p.exceptionType || undefined,
    issueKind: p.issueKind || undefined,
    pageNo: p.pageNo,
    pageSize: p.pageSize,
  });
  tableData.value = result?.rows ?? [];
  stats.value = result?.stats ?? {};
  total.value = Number(result?.total ?? 0);
  await refreshEinvoiceCompensationMenuBadge(stats.value.pendingCount);
}

function handleQuery(params: typeof queryParams) {
  Object.assign(queryParams, params);
  void getList(params);
}

function handleReset() {
  queryParams.keyword = '';
  queryParams.exceptionType = '';
  queryParams.issueKind = '';
  queryParams.pageNo = 1;
  void getList();
}

/** 分页变更（TableLayout 契约为 page/limit） */
function handlePagination(pagination: { page: number; limit: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  void getList();
}

function handleSelectionChange(rows: BossCompensationRowDto[]) {
  selectedRows.value = rows;
}

async function handleBatchRequeue() {
  const invoiceIds = selectedRows.value
    .filter((row) => row.canRequeue && row.invoiceId)
    .map((row) => row.invoiceId!);
  if (invoiceIds.length === 0) {
    ElMessage.warning('请选择可重新入队的记录');
    return;
  }
  await ElMessageBox.confirm(
    `将对 ${invoiceIds.length} 笔待补偿票据重新入队，是否继续？`,
    '批量重新入队',
    { type: 'warning' },
  );
  const batchResult = await runBatchRequeue(invoiceIds);
  ElMessage.success(
    `成功 ${batchResult?.successCount ?? 0} 笔，失败 ${batchResult?.failCount ?? 0} 笔`,
  );
  selectedRows.value = [];
  await getList();
}

async function handleBatchIssue() {
  const invoiceIds = selectedRows.value
    .filter((row) => row.canIssueNow && row.invoiceId)
    .map((row) => row.invoiceId!);
  if (invoiceIds.length === 0) {
    ElMessage.warning('请选择可立即开具的记录');
    return;
  }
  await ElMessageBox.confirm(
    `将向博思平台批量发起 ${invoiceIds.length} 笔开具，请确认金额已核对。是否继续？`,
    '批量立即开具',
    { type: 'warning' },
  );
  const batchResult = await runBatchIssue(invoiceIds);
  ElMessage.success(
    `成功 ${batchResult?.successCount ?? 0} 笔，失败 ${batchResult?.failCount ?? 0} 笔`,
  );
  selectedRows.value = [];
  await getList();
}

function openConfirm(action: EinvoiceConfirmAction, row: BossCompensationRowDto) {
  activeRow.value = row;
  confirmAction.value = action;
  confirmOpen.value = true;
}

function goAdjustAmount(row: BossCompensationRowDto) {
  if (!row.documentId) {
    ElMessage.warning('暂无电子发票单据，请先重新入队');
    return;
  }
  router.push(`/finance/einvoice/document/${row.documentId}/amount`);
}

async function handleConfirm() {
  const row = activeRow.value;
  const action = confirmAction.value;
  if (!row?.invoiceId || !action) {
    confirmOpen.value = false;
    return;
  }
  try {
    if (action === 'requeue') {
      await runRequeue(row.invoiceId);
      ElMessage.success('操作已提交');
    } else if (action === 'issueNow') {
      await runIssueNow(row.invoiceId);
      ElMessage.success('操作已提交');
    } else if (action === 'recoverByBusNo') {
      const result = await runRecover(row.invoiceId);
      if (result?.billNo) {
        ElMessage.success(`已查回票面，票号 ${result.billNo}`);
      } else {
        ElMessage.success('查票完成，博思侧暂无开票记录');
      }
    }
    confirmOpen.value = false;
    await getList();
  } catch (error: unknown) {
    const message = (error as { message?: string })?.message;
    if (message) {
      ElMessage.error(message);
    }
  }
}

onMounted(() => {
  void getList();
});
</script>

<template>
  <Page auto-content-height>
    <ElAlert
      v-if="scanTruncated"
      class="mb-3"
      title="待补偿扫描命中上限，统计与列表可能不完整，请缩小筛选范围"
      type="warning"
      show-icon
      :closable="false"
    />
    <div class="mb-3 flex flex-wrap gap-4 text-sm text-[var(--el-text-color-regular)]">
      <span v-for="item in statsSummary" :key="item.label">
        {{ item.label }}
        <strong class="ml-1 text-[var(--el-text-color-primary)]">{{ item.value }}</strong>
      </span>
    </div>

    <TableLayout
      v-model:query-params="queryParams"
      :table-columns="tableColumns"
      :table-data="tableData"
      :form-items="formItems"
      :loading="operating"
      :total="total"
      show-pagination
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
    >
      <template v-if="canOperate" #toolbar-actions>
        <span v-if="selectedCount > 0" class="mr-2 text-sm text-[var(--el-text-color-regular)]">
          已选 {{ selectedCount }} 笔
        </span>
        <ElButton
          :disabled="batchRequeueCount === 0"
          :loading="batchRequeueLoading"
          @click="handleBatchRequeue"
        >
          批量重新入队
        </ElButton>
        <ElButton
          type="primary"
          :disabled="batchIssueCount === 0"
          :loading="batchIssueLoading"
          @click="handleBatchIssue"
        >
          批量立即开具
        </ElButton>
      </template>
      <template #totalAmt="{ row }">
        {{ row.totalAmt != null ? formatMoneyDisplay(row.totalAmt) : '-' }}
      </template>
      <template #exceptionType="{ row }">
        <ElTag :type="exceptionTagType(row.exceptionType)" size="small">
          {{ row.exceptionTypeLabel || row.exceptionType }}
        </ElTag>
      </template>
      <template #waitingMinutes="{ row }"> {{ row.waitingMinutes ?? 0 }} 分钟 </template>
      <template #action="{ row }">
        <ElDropdown v-if="canOperate" trigger="click">
          <ElButton link type="primary">操作</ElButton>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem v-if="row.canAdjustAmount && canAdjust" @click="goAdjustAmount(row)">
                调整金额
              </ElDropdownItem>
              <ElDropdownItem v-if="row.canRequeue" @click="openConfirm('requeue', row)">
                重新入队
              </ElDropdownItem>
              <ElDropdownItem v-if="row.canIssueNow" @click="openConfirm('issueNow', row)">
                立即开具
              </ElDropdownItem>
              <ElDropdownItem
                v-if="row.canRecoverByBusNo"
                @click="openConfirm('recoverByBusNo', row)"
              >
                流水号查票
              </ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>
        <span v-else class="text-[var(--el-text-color-placeholder)]">无权限</span>
      </template>
      <template #empty>
        <div class="py-10 text-center text-sm text-[var(--el-text-color-secondary)]">
          <p>暂无待补偿开票记录</p>
          <p class="mt-1">后台开票正常时，患者可在收费窗扫码取票。</p>
        </div>
      </template>
    </TableLayout>

    <EinvoiceConfirmDialog
      v-model:open="confirmOpen"
      :action="confirmAction"
      :bus-no="activeRow?.busNo"
      @confirm="handleConfirm"
    />
  </Page>
</template>
