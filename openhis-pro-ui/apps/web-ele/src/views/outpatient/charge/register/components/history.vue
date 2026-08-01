<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { RegisterHistoryTableRow } from '../services';

import type { DateRangeQuickSpec } from '#/utils/format/date-range-quick';

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

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
import { buildDefaultDateRangeShortcuts } from '#/utils/format/date-picker-shortcuts';
import { getDateRangeQuick } from '#/utils/format/date-range-quick';
import { formatMoneyDisplay, formatMoneyYuanWithSymbol } from '#/utils/format/money-scale';
import { FALLBACK_DEFAULT_PAY_CHANNEL } from '#/utils/payment/payment-channel-options';
import { settleWithScanRecovery } from '#/utils/payment/scan-settle-recovery';
import { submitScanPayment } from '#/utils/payment/submit-scan-payment';
import {
  clearCachedClientRefundResult,
  submitScanRefund,
} from '#/utils/payment/submit-scan-refund';
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

const props = withDefaults(
  defineProps<{
    dateRange?: [string, string];
    /** full：日期+关键字；keyword-only：仅关键字；hidden：无工具栏筛选 */
    toolbarMode?: 'full' | 'hidden' | 'keyword-only';
  }>(),
  {
    dateRange: undefined,
    toolbarMode: 'full',
  },
);

const keywordModel = defineModel<string>('keyword', { default: '' });

const emit = defineEmits<{
  recordsChanged: [];
}>();

const loading = ref(false);
const tableAreaRef = ref<HTMLElement | null>(null);
const tableAreaHeight = ref<number>();

let tableAreaResizeObserver: null | ResizeObserver = null;

/** 收费 / 退号 / 取消挂号共用一个核对弹窗 */
const historyActionDialogVisible = ref(false);
/** charge=续付；void=未收费取消；cancel=已挂号退号 */
const historyActionMode = ref<'cancel' | 'charge' | 'void'>('charge');
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
const scanCodeInputRef = ref<{ focus?: () => void } | null>(null);

const {
  scanPayEnabled,
  hasScanPayChannel,
  effectivePayChannelOptions,
  ensurePayChannelsLoaded,
  refreshPayChannelsForDialog,
  refreshUnionPayCapabilities,
} = useRegisterPayChannels();

const showDateToolbar = computed(() => props.toolbarMode === 'full');
const showKeywordToolbar = computed(
  () => props.toolbarMode === 'full' || props.toolbarMode === 'keyword-only',
);

const showScanPaySection = computed(
  () =>
    historyActionMode.value === 'charge' &&
    hasScanPayChannel.value &&
    isScanPayChannel(payChannelCode.value) &&
    Boolean(historyActionRow.value) &&
    historyPayableCashAmount(historyActionRow.value!) > 0.001,
);

function focusScanCodeInput() {
  void nextTick(() => {
    requestAnimationFrame(() => {
      scanCodeInputRef.value?.focus?.();
    });
  });
}

function onHistoryActionDialogOpened() {
  if (showScanPaySection.value) {
    focusScanCodeInput();
  }
}

watch(payChannelCode, (code) => {
  if (
    historyActionDialogVisible.value &&
    historyActionMode.value === 'charge' &&
    isScanPayChannel(code)
  ) {
    focusScanCodeInput();
  }
});

const historyActionTitle = computed(() => {
  if (historyActionMode.value === 'charge') {
    return '确认收费';
  }
  if (historyActionMode.value === 'cancel') {
    return '确认退号';
  }
  if (historyActionMode.value === 'void') {
    return '确认取消';
  }
  return '';
});
const tableData = ref<RegisterHistoryTableRow[]>([]);
const total = ref(0);
/** 与工具栏、分页绑定的查询条件（日期、已提交 keyword、分页） */
const queryParams = ref({
  pageNo: 1,
  pageSize: 20,
  dateRange: getDateRangeQuick({ strategy: 'calendar-day' }) as [string, string],
  keyword: '',
});
/** 区间查询：展开面板默认快捷时间范围 */
const dateRangeShortcuts = buildDefaultDateRangeShortcuts();
/** 已生效的检索关键字；输入框草稿不得直接参与分页，避免未回车就带条件查空 */
const committedKeyword = ref('');
/** 防止分页/筛选并发时旧请求回写空表 */
let loadHistorySeq = 0;

const tableColumns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    minWidth: 56,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'encounterBusNo',
    label: '就诊号',
    minWidth: 70,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'patientName',
    label: '患者信息',
    minWidth: 60,
    align: 'center',
    headerAlign: 'center',
    formatter: (row) => formatPatientInfoCell(row as RegisterHistoryTableRow),
  },
  {
    prop: 'contactPhone',
    label: '联系电话',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'organizationName',
    label: '科室',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'doctorName',
    label: '医生',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'contractName',
    label: '费用性质',
    align: 'center',
    headerAlign: 'center',
    formatter: (_row, _column, cellValue) => String(cellValue ?? '').trim() || '—',
  },
  {
    prop: 'insuplcAdmdvs',
    label: '参保地',
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
    slot: 'registerType',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'amount',
    label: '挂号费',
    align: 'right',
    headerAlign: 'center',
    formatter: (_row, _column, cellValue) => {
      const s = formatAmountTwoDecimals(cellValue);
      return s === '—' ? '—' : `${s} 元`;
    },
  },
  {
    prop: 'cashierName',
    label: '收费人',
    align: 'center',
    headerAlign: 'center',
    formatter: (_row, _column, cellValue) => String(cellValue ?? '').trim() || '—',
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
    slot: 'status',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    // 收费 + 补打 + 取消/退号；固定 width 避免吃掉多余横向空间
    width: 140,
    slot: 'action',
    align: 'center',
    headerAlign: 'center',
  },
];

/**
 * 按当前日期范围、已提交关键字与分页拉取就诊记录表格数据。
 */
const handleQuery = async () => {
  const seq = ++loadHistorySeq;
  loading.value = true;
  try {
    const [start, end] = queryParams.value.dateRange;
    const pageNo = Number(queryParams.value.pageNo) || 1;
    const pageSize = Number(queryParams.value.pageSize) || 20;
    queryParams.value.pageNo = pageNo;
    queryParams.value.pageSize = pageSize;
    const { list, total: totalCount } = await pageRegisterHistoryForTable({
      dateFrom: start ?? '',
      dateTo: end ?? '',
      keyword: committedKeyword.value,
      pageNo,
      pageSize,
    });
    if (seq !== loadHistorySeq) {
      return;
    }
    tableData.value = list;
    total.value = totalCount;
  } finally {
    if (seq === loadHistorySeq) {
      loading.value = false;
    }
  }
};

watch(
  () => props.dateRange,
  (range) => {
    if (!range) {
      return;
    }
    const [start, end] = range;
    const [currentStart, currentEnd] = queryParams.value.dateRange;
    if (start === currentStart && end === currentEnd) {
      return;
    }
    queryParams.value.dateRange = range;
    queryParams.value.pageNo = 1;
    void handleQuery();
  },
);

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
  return formatMoneyDisplay(n);
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
  return formatMoneyYuanWithSymbol(n);
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

/** HTTP 业务错误已由 request 拦截器提示，避免重复弹窗。 */
function isRequestLayerError(error: unknown): boolean {
  return Boolean(error && typeof error === 'object' && 'response' in error);
}

function resolveLocalActionErrorMessage(error: unknown, fallback: string): string {
  if (isRequestLayerError(error)) {
    return '';
  }
  if (error instanceof Error) {
    return error.message.trim() || fallback;
  }
  if (error && typeof error === 'object') {
    const maybeMessage = (error as { message?: unknown }).message;
    if (typeof maybeMessage === 'string' && maybeMessage.trim()) {
      return maybeMessage.trim();
    }
  }
  if (typeof error === 'string' && error.trim()) {
    return error.trim();
  }
  return fallback;
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

/**
 * 待挂号（未收费）展示「取消」。
 * 自费与医保预结算均走 cancelPlannedRegistration（后端按需 2205/2202）。
 */
function canShowVoid(row: RegisterHistoryTableRow): boolean {
  return String(row.statusEnum ?? '').trim() === ENCOUNTER_STATUS_CODE.PLANNED;
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
async function handleCharge(row: RegisterHistoryTableRow) {
  const draftId = String(row.paymentDraftId ?? '').trim();
  if (!draftId) {
    ElMessage.warning('当前记录无支付草稿，无法收费');
    return;
  }
  historyActionMode.value = 'charge';
  historyActionRow.value = row;
  const cashPay = historyPayableCashAmount(row);
  payChannelCode.value =
    cashPay > 0.001 ? DEFAULT_REGISTER_PAY_CHANNEL_CODE : FALLBACK_DEFAULT_PAY_CHANNEL;
  scanCode.value = '';
  scanStatusText.value = '';
  historyCredentialExpiredHint.value = false;
  await refreshPayChannelsForDialog();
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
  const cashPay = historyPayableCashAmount(row);
  const needScanPay = cashPay > 0.001 && isScanPayChannel(payChannelCode.value);
  if (needScanPay && !scanCode.value.trim()) {
    ElMessage.warning('请输入付款码');
    return;
  }
  actionDialogLoading.value = true;
  scanStatusText.value = '';
  try {
    let clientScanResult;
    if (needScanPay) {
      await refreshUnionPayCapabilities();
      if (!scanPayEnabled.value) {
        ElMessage.error('请启动本地客户端（OpenHIS.Win）并确认银联组件已安装');
        return;
      }
      scanStatusText.value = '等待支付结果…';
      const scanOutcome = await submitScanPayment({
        paymentDraftId: draftId,
        amount: cashPay,
        scanCode: scanCode.value,
      });
      clientScanResult = scanOutcome.clientScanResult;
    }
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
 * 打开取消核对弹窗（PLANNED 未收费）。
 */
function handleVoid(row: RegisterHistoryTableRow) {
  historyActionMode.value = 'void';
  historyActionRow.value = row;
  historyActionDialogVisible.value = true;
}

/**
 * 弹窗内确认取消：调用 cancelPlannedRegistration（未上平台纯本地；有平台按需 2205/2202）。
 */
async function executeVoidRegister() {
  const row = historyActionRow.value;
  if (!row || historyActionMode.value !== 'void') {
    return;
  }
  actionDialogLoading.value = true;
  try {
    await cancelPlannedRegisterEncounter({
      cancelReason: '窗口取消挂号',
      encounterId: row.encounterId,
    });
    historyActionDialogVisible.value = false;
    historyActionRow.value = null;
    ElMessage.success('取消成功');
    await handleQuery();
    emit('recordsChanged');
  } catch (error) {
    const message = resolveLocalActionErrorMessage(error, '取消失败，请稍后重试');
    if (message) {
      ElMessage.error(message);
    }
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
    if (paymentId) {
      clearCachedClientRefundResult(paymentId);
    }
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
  } catch (error) {
    const message = resolveLocalActionErrorMessage(error, '退号失败，请稍后重试');
    if (message) {
      ElMessage.error(message);
    }
  } finally {
    actionDialogLoading.value = false;
  }
}

/**
 * 表格分页变更：写回页码与每页条数后重新查询（沿用已提交关键字）。
 */
const handlePagination = ({ page, limit }: { limit: number; page: number }) => {
  queryParams.value.pageNo = Number(page) || 1;
  queryParams.value.pageSize = Number(limit) || queryParams.value.pageSize || 20;
  void handleQuery();
};

/**
 * 关键字搜索：提交输入草稿后回到第一页再查。
 */
const handleKeywordSearch = async () => {
  const draft =
    props.toolbarMode === 'hidden'
      ? String(keywordModel.value ?? '')
      : String(queryParams.value.keyword ?? '');
  committedKeyword.value = draft.trim();
  queryParams.value.keyword = committedKeyword.value;
  queryParams.value.pageNo = 1;
  await handleQuery();
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

/** 进入页面默认按父级日期（缺省今天）拉一次列表（不等待本地 Win / 支付渠道 init） */
onMounted(() => {
  if (typeof ResizeObserver !== 'undefined' && tableAreaRef.value) {
    tableAreaResizeObserver = new ResizeObserver(() => {
      const height = tableAreaRef.value?.clientHeight ?? 0;
      tableAreaHeight.value = height > 0 ? height : undefined;
    });
    tableAreaResizeObserver.observe(tableAreaRef.value);
  }

  if (props.dateRange) {
    queryParams.value.dateRange = props.dateRange;
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
  search: handleKeywordSearch,
});
</script>

<template>
  <div
    ref="tableAreaRef"
    class="register-history flex h-full min-h-0 flex-1 flex-col overflow-hidden"
    :class="{ 'register-history--toolbar-hidden': props.toolbarMode === 'hidden' }"
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
      :border="false"
      :stripe="false"
      size="small"
      @pagination="handlePagination"
    >
      <template v-if="showDateToolbar || showKeywordToolbar" #toolbar-actions>
        <div v-if="showDateToolbar || showKeywordToolbar" class="flex w-full items-center gap-2">
          <SelectFuzzyInput
            v-if="showKeywordToolbar"
            v-model="queryParams.keyword"
            placeholder="患者姓名/就诊号"
            @search="handleKeywordSearch"
          />
          <template v-if="showDateToolbar">
            <ElDatePicker
              v-model="queryParams.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 260px; max-width: 260px; flex: 0 0 260px"
              :shortcuts="dateRangeShortcuts"
              @change="handleDateRangeChange"
            />
            <ElButton @click="applyDateRangeQuick({ strategy: 'calendar-day' })">今天</ElButton>
            <ElButton @click="applyDateRangeQuick({ strategy: 'calendar-week' })">本周</ElButton>
            <ElButton @click="applyDateRangeQuick({ strategy: 'calendar-month' })">本月</ElButton>
          </template>
        </div>
      </template>
      <template v-if="showDateToolbar || showKeywordToolbar" #toolbar-tools>
        <ElButton :icon="Refresh" circle @click="handleKeywordSearch" />
      </template>
      <template #registerType="{ row }">
        <span>{{ row.serviceTypeName }}</span>
      </template>

      <template #status="{ row }">
        <ElTag :type="tagTypeByCode(row.statusEnum, ENCOUNTER_STATUS_EL_TAG_MAP)" effect="light">
          {{ row.statusEnum === ENCOUNTER_STATUS_CODE.CANCELLED ? '退号' : row.statusText }}
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
          <ElButton v-if="canShowVoid(row)" type="danger" link @click="handleVoid(row)">
            取消
          </ElButton>
          <ElButton v-if="canShowCancel(row)" type="danger" link @click="handleCancel(row)">
            退号
          </ElButton>
        </div>
      </template>
    </TableLayout>

    <AppDialog
      v-model="historyActionDialogVisible"
      class="register-history-action-dialog"
      :title="historyActionTitle"
      width="560px"
      append-to-body
      destroy-on-close
      @opened="onHistoryActionDialogOpened"
      @closed="onHistoryActionDialogClosed"
    >
      <div
        v-if="historyActionRow"
        class="register-history-action-dialog__body text-left text-[14px] leading-relaxed"
      >
        <template v-if="historyActionMode === 'charge'">
          <div class="register-history-action-dialog__amount">
            <span class="register-history-action-dialog__amount-label">应收金额</span>
            <span class="register-history-action-dialog__amount-value">{{
              historyDialogAmountYuan({
                ...historyActionRow,
                amount: historyPayableCashAmount(historyActionRow),
              })
            }}</span>
          </div>

          <div class="register-history-action-dialog__meta">
            <div class="register-history-action-dialog__meta-row">
              <span>就诊号</span>
              <span class="break-all">{{
                historyDialogCell(historyActionRow.encounterBusNo)
              }}</span>
            </div>
            <div class="register-history-action-dialog__meta-row">
              <span>患者</span>
              <span class="break-all">{{ historyDialogCell(historyActionRow.patientName) }}</span>
            </div>
            <div class="register-history-action-dialog__meta-row">
              <span>挂号类型</span>
              <span class="break-all">{{
                historyDialogCell(historyActionRow.serviceTypeName)
              }}</span>
            </div>
            <template v-if="historyActionRow.insurancePreSettled">
              <div class="register-history-action-dialog__meta-row">
                <span>基金支付</span>
                <span>{{
                  historyDialogAmountYuan({
                    ...historyActionRow,
                    amount: historyActionRow.ybFundPaySumamt ?? 0,
                  })
                }}</span>
              </div>
              <div class="register-history-action-dialog__meta-row">
                <span>个账支付</span>
                <span>{{
                  historyDialogAmountYuan({
                    ...historyActionRow,
                    amount: historyActionRow.ybAcctPay ?? 0,
                  })
                }}</span>
              </div>
            </template>
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

          <div class="register-history-action-dialog__pay">
            <div class="register-history-action-dialog__section-title">支付方式</div>
            <ElRadioGroup
              v-if="historyPayableCashAmount(historyActionRow) > 0.001"
              v-model="payChannelCode"
              class="register-history-action-dialog__channels"
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
              {{
                historyActionRow.insurancePreSettled
                  ? '医保已全额覆盖，无需额外现金支付'
                  : '本单金额为 0，无需支付'
              }}
            </p>
          </div>

          <div v-if="showScanPaySection" class="register-history-action-dialog__scan">
            <ElInput
              ref="scanCodeInputRef"
              v-model="scanCode"
              size="large"
              clearable
              placeholder="请扫码或输入付款码，回车确认收费"
              @keyup.enter="executeChargeRegister"
            />
            <p v-if="scanStatusText" class="register-history-action-dialog__scan-status">
              {{ scanStatusText }}
            </p>
          </div>
        </template>

        <template v-else-if="historyActionMode === 'void'">
          <div
            class="mb-1 border-0 border-b border-solid border-[var(--el-border-color-lighter)] pb-1 text-[14px] font-semibold text-[var(--el-text-color-primary)]"
          >
            取消说明
          </div>
          <p class="text-[13px] leading-relaxed text-[var(--el-text-color-regular)]">
            确认后将取消本笔未收费挂号并作废支付草稿。若本单已做过挂号费医保预结算，将按需撤销未结算明细与平台挂号（med_type=12）；未上平台则仅本地取消。
          </p>
        </template>

        <template v-else>
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
            v-else-if="historyActionMode === 'void'"
            type="danger"
            :loading="actionDialogLoading"
            @click="executeVoidRegister"
          >
            确认取消
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
.register-history--toolbar-hidden :deep(.vxe-toolbar) {
  display: none;
}

.register-history__table :deep(> .relative) {
  flex: 1;
  min-height: 0;
  height: 100%;
}

.register-history__table :deep(.vxe-grid) {
  --vxe-ui-table-border-color: #f0f4fa;
  --vxe-ui-table-header-background-color: #f7fafc;
  --vxe-ui-font-color: #1a2b3c;
  --vxe-ui-font-size-small: 13px;
  --vxe-ui-table-row-height-small: 34px;
  font-size: 13px;
}

.register-history__table :deep(.vxe-table--header-wrapper) {
  border-bottom: 2px solid #e6edf4;
}

.register-history__table :deep(.vxe-header--column) {
  height: 34px;
  font-size: 13px;
  font-weight: 500;
  line-height: 20px;
  color: #2c4054;
  background: #f7fafc;
}

.register-history__table :deep(.vxe-header--column .vxe-cell) {
  height: 34px;
  padding: 6px 10px;
  line-height: 20px;
}

.register-history__table :deep(.vxe-body--column) {
  height: 34px;
  border-bottom: 1px solid #f0f4fa;
  background-image: none;
}

.register-history__table :deep(.vxe-body--column .vxe-cell) {
  height: 34px;
  padding: 6px 10px;
  line-height: 20px;
  color: #1a2b3c;
  white-space: nowrap;
}

.register-history__table :deep(.vxe-body--row) {
  background: #fff;
}

.register-history__table :deep(.vxe-body--row:hover .vxe-body--column) {
  background: #fafcff;
}

.register-history__table :deep(.vxe-table--border-line) {
  display: none;
}

.register-history__table :deep(.vxe-table--body-wrapper) {
  overflow-x: auto;
}

.register-history__table :deep(.vxe-table--fixed-right-wrapper .vxe-body--column .vxe-cell) {
  overflow: visible;
  text-overflow: clip;
}

.register-history__table :deep(.vxe-toolbar) {
  padding: 0 0 8px;
  background: transparent;
}

.register-history__table :deep(.vxe-pager) {
  padding-top: 12px;
  font-size: 13px;
  color: #4d6278;
  background: transparent;
}

.register-history__table :deep(.vxe-pager .vxe-pager--wrapper) {
  justify-content: space-between;
}

.register-history__table :deep(.vxe-pager .vxe-pager--btn),
.register-history__table :deep(.vxe-pager .vxe-pager--num-btn),
.register-history__table :deep(.vxe-pager .vxe-pager--jump-prev),
.register-history__table :deep(.vxe-pager .vxe-pager--jump-next) {
  min-width: 28px;
  height: 28px;
  color: #4d6278;
  background: transparent;
  border: 1px solid #dce3eb;
  border-radius: 4px;
}

.register-history__table :deep(.vxe-pager .vxe-pager--num-btn.is--active) {
  color: #fff;
  background: #2a7de1;
  border-color: #2a7de1;
}

.register-history__table :deep(.vxe-pager .vxe-pager--sizes .vxe-input) {
  height: 28px;
}

.register-history__table :deep(.vxe-pager .vxe-pager--sizes .vxe-input--inner) {
  font-size: 13px;
  border: 1px solid #dce3eb;
  border-radius: 4px;
}

.register-history__actions {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  white-space: nowrap;
}

.register-history-action-dialog__body {
  max-height: min(460px, 62vh);
  overflow-y: auto;
}

.register-history-action-dialog__amount {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  margin-bottom: 12px;
  background: var(--el-color-primary-light-9);
  border-radius: 8px;
}

.register-history-action-dialog__amount-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.register-history-action-dialog__amount-value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--el-color-primary);
}

.register-history-action-dialog__meta {
  display: grid;
  gap: 6px;
  margin-bottom: 4px;
}

.register-history-action-dialog__meta-row {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 8px;
  font-size: 13px;
}

.register-history-action-dialog__meta-row > span:first-child {
  color: var(--el-text-color-secondary);
}

.register-history-action-dialog__meta-row > span:last-child {
  color: var(--el-text-color-primary);
}

.register-history-action-dialog__pay {
  margin-top: 16px;
}

.register-history-action-dialog__section-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.register-history-action-dialog__channels {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.register-history-action-dialog__scan {
  margin-top: 14px;
}

.register-history-action-dialog__scan-status {
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-color-primary);
}
</style>
