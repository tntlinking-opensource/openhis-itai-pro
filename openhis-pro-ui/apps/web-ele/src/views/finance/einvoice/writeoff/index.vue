<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { BossDocumentDetailDto, BossDocumentListItemDto } from '#/api/einvoice-boss/document';
import type { PageWriteOffDocumentsRequest, WriteOffScope } from '#/api/einvoice-boss/writeoff';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useAccess } from '@vben/access';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElRadioButton,
  ElRadioGroup,
  ElTag,
} from 'element-plus';

import {
  getWriteOffDetailApi,
  manualWriteOffAndReissueApi,
  manualWriteOffApi,
  pageWriteOffDocumentsApi,
} from '#/api/einvoice-boss/writeoff';
import { useRequest } from '#/hooks/use-request';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

defineOptions({
  name: 'EinvoiceWriteOff',
});

const { hasAccessByCodes } = useAccess();
const canOperate = computed(() => hasAccessByCodes(['einvoice-boss.writeoff.operate']));

const scope = ref<WriteOffScope>('CANDIDATE');
const tableData = ref<BossDocumentListItemDto[]>([]);
const total = ref(0);
const keywordTruncated = ref(false);

const queryParams = reactive<PageWriteOffDocumentsRequest>({
  pageNo: 1,
  pageSize: 20,
});

const drawerOpen = ref(false);
const drawerDetail = ref<BossDocumentDetailDto | null>(null);
const writeOffReason = ref('手动冲红');

const { run: runList, loading: listLoading } = useRequest(pageWriteOffDocumentsApi, {
  manual: true,
});
const { run: runDetail, loading: detailLoading } = useRequest(getWriteOffDetailApi, {
  manual: true,
});
const { run: runWriteOff, loading: writeOffLoading } = useRequest(manualWriteOffApi, {
  manual: true,
});
const { run: runWriteOffAndReissue, loading: reissueLoading } = useRequest(
  manualWriteOffAndReissueApi,
  {
    manual: true,
  },
);

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '日期',
    prop: 'dateRange',
    type: 'daterange',
    startPlaceholder: '开始日期',
    endPlaceholder: '结束日期',
  },
  { label: '患者', prop: 'keyword', type: 'input', placeholder: '姓名或就诊号' },
  { label: '票号', prop: 'billNo', type: 'input', placeholder: '蓝票号/红票号' },
  {
    label: '业务线',
    prop: 'issueKind',
    type: 'select',
    clearable: true,
    options: [
      { label: '挂号', value: 'REG' },
      { label: '门诊', value: 'AMB' },
      { label: '住院', value: 'IMP' },
    ],
  },
]);

const tableColumns = computed<TableColumn[]>(() => {
  const cols: TableColumn[] = [
    { prop: 'patientName', label: '患者', minWidth: 100, fixed: 'left' },
    { prop: 'issueKindLabel', label: '业务线', width: 80 },
    { prop: 'paymentAmt', label: '支付金额', width: 100, slot: 'paymentAmt' },
    { prop: 'bossBillNo', label: '蓝票号', minWidth: 120, showOverflowTooltip: true },
    { prop: 'invoiceStatus', label: '发票状态', width: 100, slot: 'invoiceStatus' },
    { prop: 'writeOffStatus', label: '冲红状态', width: 110, slot: 'writeOffStatus' },
  ];
  if (scope.value === 'HISTORY') {
    cols.push(
      { prop: 'writeOffBillNo', label: '红票号', minWidth: 120, showOverflowTooltip: true },
      { prop: 'writeOffAt', label: '冲红时间', minWidth: 150, slot: 'writeOffAt' },
    );
  } else {
    cols.push({ prop: 'issuedAt', label: '开票时间', minWidth: 150, slot: 'issuedAt' });
  }
  cols.push({
    prop: 'action',
    label: '操作',
    fixed: 'right',
    width: 220,
    align: 'center',
    slot: 'action',
  });
  return cols;
});

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

function invoiceStatusType(status?: string) {
  switch (status) {
    case 'ISSUED':
      return 'success';
    case 'CANCELLED':
      return 'info';
    default:
      return 'warning';
  }
}

function writeOffStatusLabel(row: BossDocumentListItemDto) {
  return row.writeOffStatusLabel || (row.writeOffStatus ? row.writeOffStatus : '-');
}

function writeOffStatusType(row: { writeOffStatus?: string }) {
  switch (row.writeOffStatus) {
    case 'SUCCESS':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'SKIPPED':
      return 'info';
    case 'PROCESSING':
      return 'warning';
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

function canManualWriteOff(row: BossDocumentListItemDto) {
  if (!canOperate.value || !row.documentId) {
    return false;
  }
  if (row.writeOffStatus === 'SUCCESS') {
    return false;
  }
  return row.invoiceStatus === 'ISSUED' || row.writeOffStatus === 'FAILED';
}

async function getList(params?: PageWriteOffDocumentsRequest & { dateRange?: string[] }) {
  const p = { ...queryParams, ...(params ?? {}) };
  const result = await runList({
    keyword: p.keyword?.trim() || undefined,
    billNo: p.billNo?.trim() || undefined,
    issueKind: p.issueKind || undefined,
    dateFrom: p.dateRange?.[0],
    dateTo: p.dateRange?.[1],
    scope: scope.value,
    pageNo: p.pageNo,
    pageSize: p.pageSize,
  });
  tableData.value = result?.rows ?? [];
  total.value = Number(result?.total ?? 0);
  keywordTruncated.value = Boolean(result?.keywordTruncated);
}

function handleQuery(params: PageWriteOffDocumentsRequest & { dateRange?: string[] }) {
  Object.assign(queryParams, params);
  void getList(params);
}

function handleReset() {
  queryParams.keyword = '';
  queryParams.billNo = '';
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

async function handleScopeChange() {
  queryParams.pageNo = 1;
  await getList();
}

async function openDetail(row: BossDocumentListItemDto) {
  if (!row.documentId) {
    return;
  }
  drawerOpen.value = true;
  const data = await runDetail(row.documentId);
  drawerDetail.value = data ?? null;
}

async function handleWriteOff(row: BossDocumentListItemDto) {
  await confirmWriteOff(row, false);
}

async function handleWriteOffAndReissue(row: BossDocumentListItemDto) {
  await confirmWriteOff(row, true);
}

async function confirmWriteOff(row: BossDocumentListItemDto, reissue: boolean) {
  if (!row.documentId) {
    return;
  }
  writeOffReason.value = reissue ? '明细纠错，冲销重开' : '手动冲红';
  try {
    const { value } = await ElMessageBox.prompt(
      reissue ? '将冲红后按最新明细重开蓝票，请输入原因' : '请输入冲红原因',
      reissue ? '确认冲销重开' : '确认手动冲红',
      {
        confirmButtonText: reissue ? '冲销重开' : '冲红',
        cancelButtonText: '取消',
        inputValue: writeOffReason.value,
        inputPlaceholder: '冲红原因',
        type: 'warning',
        beforeClose: (action, instance, done) => {
          if (action === 'confirm' && !instance.inputValue?.trim()) {
            ElMessage.warning('请填写冲红原因');
            return;
          }
          done();
        },
      },
    );
    const reason = (value || '').trim() || writeOffReason.value;
    const result = reissue
      ? await runWriteOffAndReissue({ documentId: row.documentId, reason })
      : await runWriteOff({ documentId: row.documentId, reason });
    if (result?.success) {
      ElMessage.success(
        reissue
          ? result.message || '冲销重开已提交'
          : result.writeOffBillNo
            ? `冲红成功，红票号 ${result.writeOffBillNo}`
            : result.message || '冲红成功',
      );
      await getList();
    } else {
      ElMessage.error(result?.message || (reissue ? '冲销重开失败' : '冲红失败'));
    }
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  void getList();
});
</script>

<template>
  <Page auto-content-height>
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <ElRadioGroup v-model="scope" @change="handleScopeChange">
        <ElRadioButton value="CANDIDATE">待冲红</ElRadioButton>
        <ElRadioButton value="HISTORY">冲红记录</ElRadioButton>
      </ElRadioGroup>
      <span class="text-sm text-[var(--el-text-color-secondary)]">
        {{
          scope === 'CANDIDATE'
            ? '已开具蓝票且尚未冲红成功的票据；冲红失败可重试'
            : '已作废/已冲红票据，可查看红票信息'
        }}
      </span>
    </div>

    <ElAlert
      v-if="keywordTruncated"
      class="mb-3"
      title="扫描命中上限，列表可能不完整，请缩小筛选范围"
      type="warning"
      show-icon
      :closable="false"
    />

    <TableLayout
      v-model:query-params="queryParams"
      :table-columns="tableColumns"
      :table-data="tableData"
      :form-items="formItems"
      :loading="listLoading || writeOffLoading || reissueLoading"
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
          v-if="row.writeOffStatus || row.invoiceStatus === 'CANCELLED'"
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
      <template #writeOffAt="{ row }">
        {{ formatDateTime(row.writeOffAt) }}
      </template>
      <template #action="{ row }">
        <ElButton v-if="canManualWriteOff(row)" link type="danger" @click="handleWriteOff(row)">
          冲红
        </ElButton>
        <ElButton
          v-if="canManualWriteOff(row)"
          link
          type="warning"
          @click="handleWriteOffAndReissue(row)"
        >
          冲销重开
        </ElButton>
        <ElButton link type="primary" @click="openDetail(row)">查看</ElButton>
      </template>
      <template #empty>
        <div class="py-10 text-center text-sm text-[var(--el-text-color-secondary)]">
          <p v-if="scope === 'CANDIDATE'">暂无待冲红票据</p>
          <p v-else>暂无冲红记录</p>
        </div>
      </template>
    </TableLayout>

    <ElDrawer v-model="drawerOpen" title="冲红详情" size="480px">
      <div v-loading="detailLoading">
        <template v-if="drawerDetail">
          <ElDescriptions :column="1" border size="small" class="mb-4">
            <ElDescriptionsItem label="患者">{{
              drawerDetail.patientName || '-'
            }}</ElDescriptionsItem>
            <ElDescriptionsItem label="就诊号">
              {{ drawerDetail.encounterBusNo || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="busNo">{{ drawerDetail.busNo || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="蓝票号">
              {{ drawerDetail.bossBillNo || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="蓝票批次">
              {{ drawerDetail.bossBatchCode || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="开具时间">
              {{ formatDateTime(drawerDetail.issuedAt) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="冲红状态">
              <ElTag :type="writeOffStatusType(drawerDetail)" size="small">
                {{ drawerDetail.writeOffStatusLabel || drawerDetail.writeOffStatus || '待确认' }}
              </ElTag>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="红票号">
              {{ drawerDetail.writeOffBillNo || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="红票批次">
              {{ drawerDetail.writeOffBatchCode || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="冲红时间">
              {{ formatDateTime(drawerDetail.writeOffAt) }}
            </ElDescriptionsItem>
          </ElDescriptions>

          <div
            v-if="drawerDetail.writeOffMessage"
            class="mb-4 text-sm text-[var(--el-color-danger)]"
          >
            冲红说明：{{ drawerDetail.writeOffMessage }}
          </div>

          <div v-if="drawerDetail.pictureUrl || drawerDetail.pictureNetUrl" class="mb-4 text-sm">
            <div class="mb-2 font-medium">票面链接</div>
            <div v-if="drawerDetail.pictureUrl">
              内网：
              <a :href="drawerDetail.pictureUrl" target="_blank" rel="noopener">打开</a>
            </div>
            <div v-if="drawerDetail.pictureNetUrl">
              外网：
              <a :href="drawerDetail.pictureNetUrl" target="_blank" rel="noopener">打开</a>
            </div>
          </div>

          <div
            v-if="
              canOperate &&
              drawerDetail.documentId &&
              drawerDetail.writeOffStatus !== 'SUCCESS' &&
              (drawerDetail.invoiceStatus === 'ISSUED' || drawerDetail.writeOffStatus === 'FAILED')
            "
            class="mt-4"
          >
            <ElInput
              v-model="writeOffReason"
              type="textarea"
              :rows="2"
              placeholder="冲红原因"
              class="mb-2"
            />
            <ElButton
              type="danger"
              :loading="writeOffLoading"
              @click="
                handleWriteOff({
                  documentId: drawerDetail.documentId,
                  invoiceStatus: drawerDetail.invoiceStatus,
                  writeOffStatus: drawerDetail.writeOffStatus,
                })
              "
            >
              重新冲红
            </ElButton>
          </div>
        </template>
      </div>
    </ElDrawer>
  </Page>
</template>
