<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { RegisterHistoryTableRow } from '../services';

import type { DateRangeQuickSpec } from '#/utils/format/date-range-quick';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { TableLayout } from '@vben-core/table-layout-ui';

import { Refresh } from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElInput,
  ElMessage,
  ElRadioButton,
  ElRadioGroup,
  ElTag,
} from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';
import { isScanPayChannel } from '#/composables/payment/useUnionPayScanPay';
import { getDateRangeQuick } from '#/utils/format/date-range-quick';
import { settleWithScanRecovery } from '#/utils/payment/scan-settle-recovery';
import { submitScanPayment } from '#/utils/payment/submit-scan-payment';
import { submitScanRefund } from '#/utils/payment/submit-scan-refund';
import { tagTypeByCode } from '#/utils/format/status-tag';
import {
  printRegistrationReceipt,
  schedulePrintRegistrationReceipt,
} from '#/utils/print/print-registration-receipt';

import { useRegisterPayChannels } from '../composables/use-register-pay-channels';

import {
  cancelPlannedRegisterEncounter,
  cancelRegisterEncounter,
  DEFAULT_REGISTER_PAY_CHANNEL_CODE,
  ENCOUNTER_CANCELABLE_STATUS_CODES,
  ENCOUNTER_STATUS_CODE,
  ENCOUNTER_STATUS_EL_TAG_MAP,
  pageRegisterHistoryForTable,
  payRegisterFee,
  refreshRegisterFeeInsurancePreSettle,
} from '../services';
import { formatInsuplcAdmdvsDisplay } from '../services/charge-type-category';
import { readYbEcVoucherSnapshot } from '../services/read-yb-ec-voucher';
import {
  formatInsuranceCredentialExpiredHint,
  isInsuranceCredentialExpiredError,
} from '../services/register-insurance-credential';
import { applyRefreshToHistoryRow } from '../services/register-insurance-refresh.utils';

defineOptions({
  name: 'RegisterHistory',
});

const emit = defineEmits<{
  recordsChanged: [];
}>();

const loading = ref(false);
const tableAreaRef = ref<HTMLElement | null>(null);
const tableAreaHeight = ref<number>();

let tableAreaResizeObserver: null | ResizeObserver = null;

/** 收费 / 退号共用一个核对弹窗 */
const historyActionDialogVisible = ref(false);
const historyActionMode = ref<'cancel' | 'charge' | 'yb-cancel'>('charge');
const historyActionRow = ref<null | RegisterHistoryTableRow>(null);
const actionDialogLoading = ref(false);
const historyRefreshLoading = ref(false);
const historyCredentialExpiredHint = ref(false);
/** 补打挂号小票 loading（按 encounterId 区分行） */
const reprintLoading = ref(false);
const reprintEncounterId = ref<null | string>(null);

const payChannelCode = ref(DEFAULT_REGISTER_PAY_CHANNEL_CODE);
const scanCode = ref('');
const scanStatusText = ref('');

const { scanPayEnabled, effectivePayChannelOptions, ensurePayChannelsLoaded } =
  useRegisterPayChannels();

const showScanPaySection = computed(
  () =>
    historyActionMode.value === 'charge' &&
    scanPayEnabled.value &&
    isScanPayChannel(payChannelCode.value),
);

const historyActionTitle = computed(() => {
  if (historyActionMode.value === 'charge') {
    return '确认收费';
  }
  if (historyActionMode.value === 'cancel') {
    return '确认退号';
  }
  if (historyActionMode.value === 'yb-cancel') {
    return '确认医保退号';
  }
  return '';
});
const tableData = ref<RegisterHistoryTableRow[]>([]);
const total = ref(0);
/** 与工具栏、分页绑定的查询条件（日期、keyword、分页） */
const queryParams = ref({
  pageNo: 1,
  pageSize: 20,
  dateRange: getDateRangeQuick({ strategy: 'calendar-day' }) as [string, string],
  keyword: '',
});

const tableColumns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    width: 60,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'encounterBusNo',
    label: '就诊号',
    width: 140,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'patientName',
    label: '患者信息',
    minWidth: 140,
    align: 'center',
    headerAlign: 'center',
    formatter: (row) => formatPatientInfoCell(row as RegisterHistoryTableRow),
  },
  {
    prop: 'contactPhone',
    label: '联系电话',
    width: 100,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'organizationName',
    label: '科室',
    width: 90,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'doctorName',
    label: '医生',
    width: 80,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'contractName',
    label: '费用性质',
    width: 100,
    align: 'center',
    headerAlign: 'center',
    formatter: (_row, _column, cellValue) => String(cellValue ?? '').trim() || '—',
  },
  {
    prop: 'insuplcAdmdvs',
    label: '参保地',
    width: 90,
    align: 'center',
    headerAlign: 'center',
    formatter: (row, _column, cellValue) =>
      formatInsuplcAdmdvsDisplay(
        cellValue as string | undefined,
        (row as RegisterHistoryTableRow).insuplcAdmdvsName,
      ),
  },
  {
    prop: 'serviceTypeName',
    label: '挂号类型',
    width: 100,
    slot: 'registerType',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'amount',
    label: '挂号费',
    width: 80,
    align: 'right',
    headerAlign: 'center',
    formatter: (_row, _column, cellValue) => {
      const s = formatAmountTwoDecimals(cellValue);
      return s === '—' ? '—' : `${s} 元`;
    },
  },
  {
    prop: 'startTime',
    label: '挂号时间',
    width: 150,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'statusText',
    label: '状态',
    width: 80,
    slot: 'status',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    width: 240,
    slot: 'action',
    align: 'center',
    headerAlign: 'center',
  },
];

/**
 * 按当前日期范围、关键字与分页拉取就诊记录表格数据。
 */
const handleQuery = async () => {
  loading.value = true;
  try {
    const [start, end] = queryParams.value.dateRange;
    const { list, total: totalCount } = await pageRegisterHistoryForTable({
      dateFrom: start ?? '',
      dateTo: end ?? '',
      keyword: queryParams.value.keyword,
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
    });
    tableData.value = list;
    total.value = totalCount;
  } finally {
    loading.value = false;
  }
};

/**
 * 列表「患者信息」列：姓名/性别（接口无年龄字段，仅拼性别文案）。
 */
function formatPatientInfoCell(row: RegisterHistoryTableRow): string {
  const name = String(row.patientName ?? '').trim();
  const gender = String(row.patientGenderText ?? '').trim();
  if (!name && !gender) {
    return '—';
  }
  if (!gender) {
    return name || '—';
  }
  if (!name) {
    return gender;
  }
  return `${name}/${gender}`;
}

/**
 * 金额四舍五入保留两位小数（列表费用列、弹窗等共用）。
 */
function formatAmountTwoDecimals(value: unknown): string {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return '—';
  }
  return (Math.round(n * 100) / 100).toFixed(2);
}

/**
 * 核对弹窗内空值展示。
 */
function historyDialogCell(value: string | undefined): string {
  const t = String(value ?? '').trim();
  return t || '—';
}

/**
 * 核对弹窗内挂号费金额（带 ¥）。
 */
function historyDialogAmountYuan(row: RegisterHistoryTableRow): string {
  const n = Number(row.amount);
  if (!Number.isFinite(n)) {
    return '—';
  }
  const s = (Math.round(n * 100) / 100).toFixed(2);
  return `¥${s}`;
}

/**
 * 核对弹窗关闭后清空行与提交态。
 */
function onHistoryActionDialogClosed() {
  historyActionRow.value = null;
  actionDialogLoading.value = false;
  historyRefreshLoading.value = false;
  historyCredentialExpiredHint.value = false;
  payChannelCode.value = DEFAULT_REGISTER_PAY_CHANNEL_CODE;
  scanCode.value = '';
  scanStatusText.value = '';
}

function resolvePayError(error: unknown): { code?: string; message: string } {
  if (error && typeof error === 'object') {
    const row = error as {
      code?: string;
      message?: string;
      response?: { data?: { code?: string; message?: string } };
    };
    const data = row.response?.data;
    return {
      code: data?.code ?? row.code,
      message: String(data?.message ?? row.message ?? ''),
    };
  }
  return { message: String(error ?? '') };
}

async function handleHistoryRescanRefresh() {
  const row = historyActionRow.value;
  if (!row?.paymentDraftId || !row.insurancePreSettled) {
    return;
  }
  historyRefreshLoading.value = true;
  try {
    const snapshot = await readYbEcVoucherSnapshot();
    const credential = snapshot?.insuranceCredential;
    if (!credential?.mdtrtCertNo) {
      return;
    }
    const refresh = await refreshRegisterFeeInsurancePreSettle({
      paymentDraftId: row.paymentDraftId,
      ybMediumType: credential.mdtrtCertType,
      ybCardInfo: credential.mdtrtCertNo,
    });
    historyActionRow.value = applyRefreshToHistoryRow(row, refresh);
    historyCredentialExpiredHint.value = false;
    ElMessage.success('已刷新医保预结算');
  } finally {
    historyRefreshLoading.value = false;
  }
}

/**
 * 是否可补打挂号小票（已产生成功支付流水）。
 */
function canReprintReceipt(row: RegisterHistoryTableRow): boolean {
  return Boolean(String(row.paymentId ?? '').trim());
}

/** 医保挂号且待收费：仅展示「医保退号」，不展示「退号」。 */
function canShowYbCancel(row: RegisterHistoryTableRow): boolean {
  if (row.statusEnum !== ENCOUNTER_STATUS_CODE.PLANNED) {
    return false;
  }
  return Boolean(row.insurancePlatformRegistered || row.insurancePreSettled);
}

/** 已挂号 / 已分诊 / 在诊：展示窗口「退号」。 */
function canShowCancel(row: RegisterHistoryTableRow): boolean {
  return ENCOUNTER_CANCELABLE_STATUS_CODES.has(row.statusEnum);
}

/**
 * 补打挂号小票。
 */
async function handleReprintReceipt(row: RegisterHistoryTableRow) {
  const paymentId = String(row.paymentId ?? '').trim();
  const encounterId = String(row.encounterId ?? '').trim();
  if (!paymentId) {
    ElMessage.warning('暂无支付单号，无法补打挂号小票');
    return;
  }
  reprintEncounterId.value = encounterId || null;
  reprintLoading.value = true;
  try {
    await printRegistrationReceipt({
      paymentId,
      encounterId: encounterId || undefined,
      jobName: '挂号小票（补打）',
    });
  } finally {
    reprintLoading.value = false;
    reprintEncounterId.value = null;
  }
}

/**
 * 打开收费核对弹窗（正式结算前由用户确认）。
 */
function handleCharge(row: RegisterHistoryTableRow) {
  const draftId = String(row.paymentDraftId ?? '').trim();
  if (!draftId) {
    ElMessage.warning('当前记录无支付草稿，无法收费');
    return;
  }
  historyActionMode.value = 'charge';
  historyActionRow.value = row;
  payChannelCode.value = DEFAULT_REGISTER_PAY_CHANNEL_CODE;
  scanCode.value = '';
  scanStatusText.value = '';
  historyCredentialExpiredHint.value = false;
  historyActionDialogVisible.value = true;
}

/**
 * 弹窗内确认收费：提交挂号费正式结算后刷新列表。
 */
async function executeChargeRegister() {
  const row = historyActionRow.value;
  if (!row || historyActionMode.value !== 'charge') {
    return;
  }
  const draftId = String(row.paymentDraftId ?? '').trim();
  if (!draftId) {
    return;
  }
  if (isScanPayChannel(payChannelCode.value) && !scanCode.value.trim()) {
    ElMessage.warning('请输入付款码');
    return;
  }
  actionDialogLoading.value = true;
  scanStatusText.value = '';
  try {
    let clientScanResult;
    if (isScanPayChannel(payChannelCode.value)) {
      if (!scanPayEnabled.value) {
        ElMessage.error('请启动本地客户端（OpenHIS.Win）并确认银联组件已安装');
        return;
      }
      scanStatusText.value = '等待支付结果…';
      const scanOutcome = await submitScanPayment({
        paymentDraftId: draftId,
        amount: historyPayableCashAmount(row),
        scanCode: scanCode.value,
      });
      clientScanResult = scanOutcome.clientScanResult;
    }

    const cashPay = historyPayableCashAmount(row);
    const payDetails =
      cashPay > 0.001
        ? [
            {
              payAmount: cashPay,
              payChannelCode: payChannelCode.value,
            },
          ]
        : [];

    const payResult = await (clientScanResult
      ? settleWithScanRecovery(clientScanResult, () =>
          payRegisterFee({
            paymentDetails: payDetails,
            paymentDraftId: draftId,
            clientScanResult,
          }),
        )
      : payRegisterFee({
          paymentDetails: payDetails,
          paymentDraftId: draftId,
        }));
    historyActionDialogVisible.value = false;
    historyActionRow.value = null;
    ElMessage.success('收费成功');
    await handleQuery();
    emit('recordsChanged');
    schedulePrintRegistrationReceipt({
      encounterId: payResult?.encounterId ?? row.encounterId,
      paymentId: payResult?.paymentId,
      jobName: '挂号小票',
    });
  } catch (error) {
    const { code, message } = resolvePayError(error);
    if (isInsuranceCredentialExpiredError(code, message)) {
      historyCredentialExpiredHint.value = true;
      ElMessage.warning(formatInsuranceCredentialExpiredHint());
    }
  } finally {
    actionDialogLoading.value = false;
  }
}

function historyPayableCashAmount(row: RegisterHistoryTableRow): number {
  if (row.insurancePreSettled) {
    const cash = Number(row.ybPsnCashPay ?? 0);
    return Number.isFinite(cash) ? cash : 0;
  }
  const amount = Number(row.amount ?? 0);
  return Number.isFinite(amount) ? amount : 0;
}

/**
 * 打开退号核对弹窗。
 */
function handleCancel(row: RegisterHistoryTableRow) {
  historyActionMode.value = 'cancel';
  historyActionRow.value = row;
  historyActionDialogVisible.value = true;
}

/**
 * 打开医保退号核对弹窗（PLANNED + 已 2201）。
 */
function handleYbCancel(row: RegisterHistoryTableRow) {
  historyActionMode.value = 'yb-cancel';
  historyActionRow.value = row;
  historyActionDialogVisible.value = true;
}

/**
 * 弹窗内确认医保退号。
 */
async function executeYbCancelRegister() {
  const row = historyActionRow.value;
  if (!row || historyActionMode.value !== 'yb-cancel') {
    return;
  }
  actionDialogLoading.value = true;
  try {
    await cancelPlannedRegisterEncounter({
      cancelReason: '窗口医保退号',
      encounterId: row.encounterId,
    });
    historyActionDialogVisible.value = false;
    historyActionRow.value = null;
    ElMessage.success('医保退号成功');
    await handleQuery();
    emit('recordsChanged');
  } finally {
    actionDialogLoading.value = false;
  }
}

/**
 * 弹窗内确认退号：调用接口成功后刷新列表。
 */
async function executeCancelRegister() {
  const row = historyActionRow.value;
  if (!row || historyActionMode.value !== 'cancel') {
    return;
  }
  actionDialogLoading.value = true;
  try {
    let clientRefundResult;
    const paymentId = String(row.paymentId ?? '').trim();
    if (paymentId) {
      try {
        clientRefundResult =
          (await submitScanRefund({
            originalPaymentId: paymentId,
            refundAmountYuan: row.amount,
          })) ?? undefined;
      } catch (error) {
        const message = error instanceof Error ? error.message : '退费前银联校验失败，请稍后重试';
        ElMessage.error(message);
        return;
      }
    }

    const result = await cancelRegisterEncounter({
      cancelReason: '窗口退号',
      encounterId: row.encounterId,
      ...(clientRefundResult ? { clientRefundResult } : {}),
    });
    historyActionDialogVisible.value = false;
    historyActionRow.value = null;
    ElMessage.success('退号成功');
    if (result.appointmentReleaseMessage) {
      ElMessage.info(result.appointmentReleaseMessage);
    } else if (row.hasLinkedAppointment) {
      ElMessage.info('关联预约已按院点策略释放');
    }
    await handleQuery();
    emit('recordsChanged');
  } finally {
    actionDialogLoading.value = false;
  }
}

/**
 * 表格分页变更：写回页码与每页条数后重新查询。
 */
const handlePagination = ({ page, limit }: { limit: number; page: number }) => {
  queryParams.value.pageNo = page;
  queryParams.value.pageSize = limit;
  handleQuery();
};

/**
 * 关键字搜索：回到第一页再查，避免仍停留在深页导致空表误解。
 */
const handleKeywordSearch = () => {
  queryParams.value.pageNo = 1;
  handleQuery();
};

/**
 * 日期范围变更：同样回到第一页再查（区间变了结果集可能变短）。
 */
const handleDateRangeChange = () => {
  queryParams.value.pageNo = 1;
  handleQuery();
};

/** 快捷筛：入参为 `getDateRangeQuick` 的规则对象，各页按需组合 */
function applyDateRangeQuick(spec: DateRangeQuickSpec) {
  queryParams.value.dateRange = getDateRangeQuick(spec) as [string, string];
  handleDateRangeChange();
}

/** 进入页面默认按「今天」条件拉一次列表（不等待本地 Win / 支付渠道 init） */
onMounted(() => {
  if (typeof ResizeObserver !== 'undefined' && tableAreaRef.value) {
    tableAreaResizeObserver = new ResizeObserver(() => {
      const height = tableAreaRef.value?.clientHeight ?? 0;
      tableAreaHeight.value = height > 0 ? height : undefined;
    });
    tableAreaResizeObserver.observe(tableAreaRef.value);
  }

  void handleQuery();
  void ensurePayChannelsLoaded();
});

onBeforeUnmount(() => {
  tableAreaResizeObserver?.disconnect();
  tableAreaResizeObserver = null;
});

/** 供父页在挂号成功等场景触发右侧列表刷新 */
defineExpose({
  refresh: handleQuery,
});
</script>

<template>
  <div
    ref="tableAreaRef"
    class="register-history flex h-full min-h-0 flex-1 flex-col overflow-hidden"
  >
    <TableLayout
      class="register-history__table h-full min-h-0 flex-1 !p-0"
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :table-columns="tableColumns"
      :table-height="tableAreaHeight"
      :show-pagination="true"
      :show-top-query="false"
      border
      stripe
      @pagination="handlePagination"
    >
      <template #toolbar-actions>
        <div class="flex w-full items-center gap-2">
          <SelectFuzzyInput
            v-model="queryParams.keyword"
            placeholder="患者姓名/就诊号"
            @search="handleKeywordSearch"
          />
          <ElDatePicker
            v-model="queryParams.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px; max-width: 260px; flex: 0 0 260px"
            @change="handleDateRangeChange"
          />
          <ElButton @click="applyDateRangeQuick({ strategy: 'calendar-day' })">今天</ElButton>
          <ElButton @click="applyDateRangeQuick({ strategy: 'calendar-week' })">本周</ElButton>
          <ElButton @click="applyDateRangeQuick({ strategy: 'calendar-month' })">本月</ElButton>
        </div>
      </template>
      <template #toolbar-tools>
        <ElButton :icon="Refresh" circle @click="handleKeywordSearch" />
      </template>
      <template #registerType="{ row }">
        <span>{{ row.serviceTypeName }}</span>
      </template>

      <template #status="{ row }">
        <ElTag :type="tagTypeByCode(row.statusEnum, ENCOUNTER_STATUS_EL_TAG_MAP)" effect="light">
          {{ row.statusText }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="register-history__actions">
          <ElButton
            type="primary"
            link
            :disabled="row.statusEnum !== ENCOUNTER_STATUS_CODE.PLANNED"
            @click="handleCharge(row)"
          >
            收费
          </ElButton>
          <ElButton
            type="primary"
            link
            :disabled="!canReprintReceipt(row)"
            :loading="reprintLoading && reprintEncounterId === String(row.encounterId ?? '')"
            @click="handleReprintReceipt(row)"
          >
            补打
          </ElButton>
          <ElButton v-if="canShowYbCancel(row)" type="danger" link @click="handleYbCancel(row)">
            医保退号
          </ElButton>
          <ElButton v-if="canShowCancel(row)" type="danger" link @click="handleCancel(row)">
            退号
          </ElButton>
        </div>
      </template>
    </TableLayout>

    <AppDialog
      v-model="historyActionDialogVisible"
      :title="historyActionTitle"
      width="520px"
      append-to-body
      destroy-on-close
      @closed="onHistoryActionDialogClosed"
    >
      <div
        v-if="historyActionRow"
        class="max-h-[min(400px,58vh)] overflow-y-auto text-left text-[14px] leading-relaxed"
      >
        <div
          class="mb-1 border-0 border-b border-solid border-[var(--el-border-color-lighter)] pb-1 text-[14px] font-semibold text-[var(--el-text-color-primary)]"
        >
          就诊与患者
        </div>
        <div class="grid grid-cols-[96px_1fr] gap-x-2 gap-y-1 py-0.5">
          <span class="text-[var(--el-text-color-regular)]">就诊号</span>
          <span class="break-all text-[var(--el-text-color-primary)]">{{
            historyDialogCell(historyActionRow.encounterBusNo)
          }}</span>
          <span class="text-[var(--el-text-color-regular)]">患者姓名</span>
          <span class="break-all text-[var(--el-text-color-primary)]">{{
            historyDialogCell(historyActionRow.patientName)
          }}</span>
          <span class="text-[var(--el-text-color-regular)]">联系电话</span>
          <span class="break-all text-[var(--el-text-color-primary)]">{{
            historyDialogCell(historyActionRow.contactPhone)
          }}</span>
        </div>

        <template v-if="historyActionMode === 'charge'">
          <div
            class="mb-1 mt-4 border-0 border-b border-solid border-[var(--el-border-color-lighter)] pb-1 text-[14px] font-semibold text-[var(--el-text-color-primary)]"
          >
            费用与草稿
          </div>
          <div class="grid grid-cols-[96px_1fr] gap-x-2 gap-y-1 py-0.5">
            <span class="text-[var(--el-text-color-regular)]">应收金额</span>
            <span class="text-[var(--el-text-color-primary)]">{{
              historyDialogAmountYuan({
                ...historyActionRow,
                amount: historyPayableCashAmount(historyActionRow),
              })
            }}</span>
            <template v-if="historyActionRow.insurancePreSettled">
              <span class="text-[var(--el-text-color-regular)]">基金支付</span>
              <span class="text-[var(--el-text-color-primary)]">{{
                historyDialogAmountYuan({
                  ...historyActionRow,
                  amount: historyActionRow.ybFundPaySumamt ?? 0,
                })
              }}</span>
              <span class="text-[var(--el-text-color-regular)]">个账支付</span>
              <span class="text-[var(--el-text-color-primary)]">{{
                historyDialogAmountYuan({
                  ...historyActionRow,
                  amount: historyActionRow.ybAcctPay ?? 0,
                })
              }}</span>
            </template>
            <span class="text-[var(--el-text-color-regular)]">支付草稿</span>
            <span class="break-all text-[var(--el-text-color-primary)]">{{
              historyDialogCell(historyActionRow.paymentDraftId)
            }}</span>
            <span class="text-[var(--el-text-color-regular)]">挂号类型</span>
            <span class="break-all text-[var(--el-text-color-primary)]">{{
              historyDialogCell(historyActionRow.serviceTypeName)
            }}</span>
          </div>

          <ElAlert
            v-if="historyActionRow.insurancePreSettled && historyCredentialExpiredHint"
            class="mt-3"
            type="warning"
            :closable="false"
            show-icon
            :title="formatInsuranceCredentialExpiredHint()"
          />

          <div v-if="historyActionRow.insurancePreSettled" class="mt-3">
            <ElButton
              type="warning"
              plain
              :loading="historyRefreshLoading"
              :disabled="actionDialogLoading"
              @click="handleHistoryRescanRefresh"
            >
              重新扫医保码并刷新预结算
            </ElButton>
          </div>

          <div class="mt-4">
            <div
              class="mb-1 border-0 border-b border-solid border-[var(--el-border-color-lighter)] pb-1 text-[14px] font-semibold text-[var(--el-text-color-primary)]"
            >
              支付方式
            </div>
            <ElRadioGroup
              v-if="historyPayableCashAmount(historyActionRow) > 0.001"
              v-model="payChannelCode"
              class="flex flex-wrap gap-2"
            >
              <ElRadioButton
                v-for="opt in effectivePayChannelOptions"
                :key="opt.value"
                :value="opt.value"
              >
                {{ opt.label }}
              </ElRadioButton>
            </ElRadioGroup>
            <p v-else class="text-[13px] text-[var(--el-text-color-secondary)]">
              医保已全额覆盖，无需额外现金支付
            </p>
          </div>

          <div v-if="showScanPaySection" class="mt-3">
            <div class="mb-2 text-[14px] font-semibold text-[var(--el-text-color-primary)]">
              扫码支付
            </div>
            <ElInput
              v-model="scanCode"
              placeholder="请扫码或输入付款码"
              clearable
              @keyup.enter="executeChargeRegister"
            />
            <p v-if="scanStatusText" class="mt-2 text-[13px] text-blue-600">{{ scanStatusText }}</p>
          </div>
        </template>

        <template v-else-if="historyActionMode === 'yb-cancel'">
          <div
            class="mb-1 mt-4 border-0 border-b border-solid border-[var(--el-border-color-lighter)] pb-1 text-[14px] font-semibold text-[var(--el-text-color-primary)]"
          >
            医保退号说明
          </div>
          <p class="text-[13px] leading-relaxed text-[var(--el-text-color-regular)]">
            该记录已在医保平台完成挂号登记（2201）且尚未收费，确认后将撤销平台挂号并作废本地待挂号记录。
          </p>
        </template>

        <template v-else>
          <div
            class="mb-1 mt-4 border-0 border-b border-solid border-[var(--el-border-color-lighter)] pb-1 text-[14px] font-semibold text-[var(--el-text-color-primary)]"
          >
            挂号信息
          </div>
          <div class="grid grid-cols-[96px_1fr] gap-x-2 gap-y-1 py-0.5">
            <span class="text-[var(--el-text-color-regular)]">挂号科室</span>
            <span class="break-all text-[var(--el-text-color-primary)]">{{
              historyDialogCell(historyActionRow.organizationName)
            }}</span>
            <span class="text-[var(--el-text-color-regular)]">接诊医生</span>
            <span class="break-all text-[var(--el-text-color-primary)]">{{
              historyDialogCell(historyActionRow.doctorName)
            }}</span>
            <span class="text-[var(--el-text-color-regular)]">挂号类型</span>
            <span class="break-all text-[var(--el-text-color-primary)]">{{
              historyDialogCell(historyActionRow.serviceTypeName)
            }}</span>
            <span class="text-[var(--el-text-color-regular)]">挂号时间</span>
            <span class="text-[var(--el-text-color-primary)]">{{
              historyDialogCell(historyActionRow.startTime)
            }}</span>
            <span class="text-[var(--el-text-color-regular)]">应退金额</span>
            <span class="text-[var(--el-text-color-primary)]">{{
              historyDialogAmountYuan(historyActionRow)
            }}</span>
            <span class="text-[var(--el-text-color-regular)]">当前状态</span>
            <span class="text-[var(--el-text-color-primary)]">{{
              historyDialogCell(historyActionRow.statusText)
            }}</span>
          </div>
        </template>
      </div>

      <template #footer>
        <div class="flex justify-end gap-2">
          <ElButton :disabled="actionDialogLoading" @click="historyActionDialogVisible = false">
            取消
          </ElButton>
          <ElButton
            v-if="historyActionMode === 'charge'"
            type="primary"
            :loading="actionDialogLoading"
            @click="executeChargeRegister"
          >
            确认收费
          </ElButton>
          <ElButton
            v-else-if="historyActionMode === 'yb-cancel'"
            type="danger"
            :loading="actionDialogLoading"
            @click="executeYbCancelRegister"
          >
            确认医保退号
          </ElButton>
          <ElButton
            v-else-if="historyActionMode === 'cancel'"
            type="danger"
            :loading="actionDialogLoading"
            @click="executeCancelRegister"
          >
            确认退号
          </ElButton>
        </div>
      </template>
    </AppDialog>
  </div>
</template>

<style scoped>
.register-history__table :deep(> .relative) {
  flex: 1;
  min-height: 0;
  height: 100%;
}

.register-history__actions {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  white-space: nowrap;
}

.register-history__table :deep(.vxe-table--fixed-right-wrapper .vxe-body--column .vxe-cell) {
  overflow: visible;
  text-overflow: clip;
}
</style>
