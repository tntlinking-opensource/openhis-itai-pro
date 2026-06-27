<script setup lang="ts">
import type { OptionDto } from '#/api/inpatient-registration';
import type {
  ConfirmInpatientSettleResponse,
  InpatientSettleChargeLineDto,
  InpatientSettleQueueItemDto,
  LoadInpatientSettleSummaryResponse,
  LoadSettledInpatientPaymentViewResponse,
  PreSettleInpatientChargesResponse,
  SettledPaymentBriefDto,
} from '#/api/inpatient-settle';

import { computed, onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import dayjs from 'dayjs';
import {
  ElButton,
  ElCard,
  ElMessage,
  ElMessageBox,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
  ElTooltip,
} from 'element-plus';

import {
  httpReverseInpatientSettle,
  httpConfirmInpatientSettle,
  httpLoadInpatientSettlePageInit,
  httpLoadInpatientSettleSummary,
  httpLoadSettledInpatientPaymentView,
  httpPageInpatientSettleQueue,
  httpPreSettleInpatientCharges,
  httpQueryInpatientSettleCharges,
} from '#/api/inpatient-settle';
import AppDialog from '#/components/app-dialog.vue';
import EinvoiceBillAmountMismatchBanner from '#/components/einvoice/EinvoiceBillAmountMismatchBanner.vue';
import EinvoiceIssueStatusPanel from '#/components/einvoice/EinvoiceIssueStatusPanel.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import SelectTable from '#/components/select-table.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { optionDtosToPayChannelOptions } from '#/utils/payment/payment-channel-options';
import { isScanPayChannel, useUnionPayScanPay } from '#/composables/payment/useUnionPayScanPay';
import { submitScanPayment } from '#/utils/payment/submit-scan-payment';
import { settleWithScanRecovery } from '#/utils/payment/scan-settle-recovery';

import {
  FALLBACK_DEFAULT_PAY_CHANNEL,
  FALLBACK_PAY_CHANNEL_OPTIONS,
  INPATIENT_SETTLE_MODE,
  SETTLE_PRESENTATION_MODE,
  SETTLE_QUEUE_TAB,
} from './inpatient-settle.constants';
import InpatientSettleDialog from './components/inpatient-settle-dialog.vue';
import {
  isInpatientInsuranceRegistration,
  isZeroAmountDischargeFullSettle,
  resolveInpatientSettleInsuranceContext,
  resolveInpatientSettleMode,
  verifyInpatientInsuranceCard,
} from './use-inpatient-settle-insurance';

defineOptions({
  name: 'ChargeIpSettle',
});

interface FeeCategoryRow {
  amount: string;
  category: string;
  insuranceAmount: string;
  ratio: string;
  selfAmount: string;
}

interface FeeDetailRow {
  amount: string;
  category: string;
  chargeItemId: string;
  insuranceFlag: string;
  itemName: string;
  orderNo: string;
  price: string;
  priceQuantity: string;
  quantity: string;
  selected: boolean;
  spec: string;
}

interface InsuranceInfoRow {
  field: string;
  value: string;
}

interface ReliefRow {
  amount: string;
  approver: string;
  recordDate: string;
  type: string;
}

interface SettlementHistoryRow {
  detailRows: FeeDetailRow[];
  operatorName: string;
  paymentId: string;
  paymentMethod: string;
  recordType?: string;
  reimbursementAmount: string;
  reverseDisabledReason?: string;
  reverseEnabled?: boolean;
  selfPayAmount: string;
  settleTime: string;
  settleType: string;
  settlementNo: string;
  status: string;
  ticketNo: string;
  totalAmount: string;
}

/** 展示用患者快照（字段名与原原型一致，数据来自接口） */
interface SettlePatientView {
  admissionDate: string;
  age: string;
  bedNo: string;
  department: string;
  doctorName: string;
  encounterId: string;
  feeCategoryRows: FeeCategoryRow[];
  feeDetailRows: FeeDetailRow[];
  gender: string;
  inpatientNo: string;
  insuranceInFee: string;
  insuranceOutFee: string;
  insuranceType: string;
  name: string;
  operatorName: string;
  patientId: string;
  paymentTime: string;
  prepayTotal: string;
  accountBalanceSummary: string;
  accountBalanceSub?: string;
  reimbursementAmount: string;
  refundOrSupplement: string;
  reliefRows: ReliefRow[];
  settlementHistoryRows: SettlementHistoryRow[];
  settlementNo: string;
  settlementStatus: string;
  settleDate: string;
  selfPayAmount: string;
  totalFee: string;
  wardName: string;
}

const PAY_LABEL_TO_CODE: Record<string, string> = {
  现金: 'CASH',
  微信: 'WECHAT',
  银行卡: 'BANK_CARD',
};

const activeTab = ref('category-summary');
const selectedEncounterId = ref('');
const selectedPatientLabel = ref('');
const settleQueueTab = ref<string>(SETTLE_QUEUE_TAB.PENDING_SETTLE);
const settleQueueTabOptions = ref<OptionDto[]>([]);
const settledPaymentView = ref<LoadSettledInpatientPaymentViewResponse | null>(null);
const lastSettlePaymentId = ref('');

/** 当前是否为已结算队列页签 */
const isSettledQueueTab = computed(() => settleQueueTab.value === SETTLE_QUEUE_TAB.SETTLED);

/** 页签选项：优先用初始化接口，否则本地兜底 */
const settleQueueTabRadioOptions = computed(() => {
  if (settleQueueTabOptions.value.length > 0) {
    return settleQueueTabOptions.value;
  }
  return [
    { id: '1', value: SETTLE_QUEUE_TAB.PENDING_SETTLE, label: '待结算' },
    { id: '2', value: SETTLE_QUEUE_TAB.SETTLED, label: '已结算' },
  ];
});

/** 结算患者下拉表格列（待结算与已结算展示字段不同） */
const settlePatientSelectColumns = computed(() => {
  const base = [
    { prop: 'encounterBusNo', label: '住院号', width: 130 },
    { prop: 'patientName', label: '姓名', width: 100 },
    { prop: 'genderEnumText', label: '性别', width: 72 },
    { prop: 'ageText', label: '年龄', width: 64 },
    { prop: 'organizationName', label: '科室', width: 120 },
    { prop: 'wardName', label: '病区', width: 100 },
    { prop: 'bedName', label: '床号', width: 80 },
    { prop: 'statusEnumText', label: '就诊状态', width: 100 },
  ];
  if (isSettledQueueTab.value) {
    return [...base, { prop: 'lastSettleTime', label: '最近结算时间', width: 160 }];
  }
  return [
    ...base,
    { prop: 'accountBalanceSummaryText', label: '账户余额', width: 140 },
    { prop: 'pendingSettlementTotal', label: '待结算合计', width: 110 },
  ];
});

const paymentMethod = ref<'微信' | '现金' | '银行卡'>('微信');
const payChannelCodeByLabel = ref<Record<string, string>>({ ...PAY_LABEL_TO_CODE });

const queueRecords = ref<InpatientSettleQueueItemDto[]>([]);
const queueRowByEncounterId = ref<Record<string, InpatientSettleQueueItemDto>>({});
const summary = ref<LoadInpatientSettleSummaryResponse | null>(null);
const chargeLines = ref<InpatientSettleChargeLineDto[]>([]);
const settlementHistoryRows = ref<SettlementHistoryRow[]>([]);
/** 历史结算净额（待结算页签亦展示；与 settledPaymentView 解耦） */
const settlementHistoryNetTotal = ref<number | null>(null);

const preSettleSnapshot = ref<null | PreSettleInpatientChargesResponse>(null);
const confirmSettleLoading = ref(false);
const preSettleLoading = ref(false);
const reverseSettleLoading = ref(false);
const settleDialogVisible = ref(false);
const settlePayChannelCode = ref(FALLBACK_DEFAULT_PAY_CHANNEL);
const settleScanCode = ref('');
const settleScanStatusText = ref('');
const paymentChannelSelectOptions = ref([...FALLBACK_PAY_CHANNEL_OPTIONS]);

const { scanPayEnabled, effectivePayChannelOptions, refreshUnionPayCapabilities } =
  useUnionPayScanPay(() => paymentChannelSelectOptions.value);
const lockedChargeItemIds = ref<string[]>([]);
const workspaceLoading = ref(false);

const settlementDetailVisible = ref(false);
const selectedSettlementHistory = ref<null | SettlementHistoryRow>(null);
const historyDetailRows = ref<FeeDetailRow[]>([]);
/** 程序内切换页签（如出院全结后）时跳过 watch 清空选中 */
const skipQueueTabWatchReset = ref(false);

const emptyPatientView = (): SettlePatientView => ({
  admissionDate: '—',
  age: '—',
  bedNo: '—',
  department: '—',
  doctorName: '—',
  encounterId: '',
  feeCategoryRows: [],
  feeDetailRows: [],
  gender: '—',
  inpatientNo: '—',
  insuranceInFee: '—',
  insuranceOutFee: '—',
  insuranceType: '—',
  name: '—',
  operatorName: '—',
  patientId: '',
  paymentTime: '—',
  prepayTotal: '—',
  accountBalanceSummary: '—',
  reimbursementAmount: '—',
  refundOrSupplement: '—',
  reliefRows: [],
  settlementHistoryRows: [],
  settlementNo: '—',
  settlementStatus: '—',
  settleDate: '—',
  selfPayAmount: '—',
  totalFee: '—',
  wardName: '—',
});

const currentQueueRow = computed(() => {
  const encounterId = selectedEncounterId.value;
  if (!encounterId) {
    return null;
  }
  return (
    queueRowByEncounterId.value[encounterId] ??
    queueRecords.value.find((r) => r.encounterId === encounterId) ??
    null
  );
});

/** 解析当前选中患者的 encounterId / patientId（摘要优先于队列行） */
function resolveActiveSettlePatientIds() {
  const encounterId = preserveApiIdForRequest(
    summary.value?.encounterId ?? selectedEncounterId.value,
  );
  const patientId = preserveApiIdForRequest(
    summary.value?.patientId ?? currentQueueRow.value?.patientId,
  );
  return { encounterId, patientId };
}

/** 下拉展示文案：姓名（住院号） */
function formatSettlePatientLabel(row: InpatientSettleQueueItemDto) {
  return `${row.patientName}（${row.encounterBusNo}）`;
}

/** 缓存队列行并同步下拉展示文案 */
function cacheQueueRow(row: InpatientSettleQueueItemDto) {
  queueRowByEncounterId.value = {
    ...queueRowByEncounterId.value,
    [row.encounterId]: row,
  };
  if (!queueRecords.value.some((item) => item.encounterId === row.encounterId)) {
    queueRecords.value = [row, ...queueRecords.value];
  }
  selectedPatientLabel.value = formatSettlePatientLabel(row);
}

/** 格式化为带 ¥ 的展示金额 */
function formatCurrencyDisplay(value: null | number | string | undefined) {
  const n = Number(value ?? 0);
  if (!Number.isFinite(n)) {
    return '—';
  }
  return `¥ ${n.toFixed(2)}`;
}

/** 历史结算表格金额：数值后拼「元」 */
function formatHistoryAmountDisplay(value: null | number | string | undefined) {
  const n = Number(value ?? 0);
  if (!Number.isFinite(n)) {
    return '—';
  }
  return `${n.toFixed(2)}元`;
}

/** 历史结算表格时间：居中展示，精确到分钟 */
function formatHistorySettleTimeDisplay(value: null | string | undefined) {
  if (!value || value === '—') {
    return '—';
  }
  const parsed = dayjs(value);
  if (!parsed.isValid()) {
    return value;
  }
  return parsed.format('YYYY-MM-DD HH:mm');
}

/** 格式化为差额提示文案 */
function formatBalanceHintDisplay(hint: null | number | string | undefined) {
  const n = Number(hint ?? 0);
  if (!Number.isFinite(n) || n === 0) {
    return '—';
  }
  if (n > 0) {
    return `应退 ¥ ${n.toFixed(2)}`;
  }
  return `应补 ¥ ${Math.abs(n).toFixed(2)}`;
}

/** 费用明细行转表格展示结构 */
function mapChargeLineToFeeDetailRow(line: InpatientSettleChargeLineDto): FeeDetailRow {
  const qty = line.quantityValue ?? '';
  const unit = line.quantityUnit ?? '';
  return {
    chargeItemId: line.chargeItemId,
    orderNo: line.sourceText ?? '—',
    itemName: line.itemName ?? '—',
    category: line.categoryDisplay ?? '—',
    spec: line.specText ?? '—',
    price: formatCurrencyDisplay(line.unitPrice),
    quantity: `${qty}${unit}`,
    priceQuantity: `${formatCurrencyDisplay(line.unitPrice)} × ${qty}${unit}`,
    amount: formatCurrencyDisplay(line.lineAmount),
    insuranceFlag: '—',
    selected: Boolean(line.selected),
  };
}

/** 大类汇总转表格展示结构 */
function mapCategoryRows(
  categories: { amount: number | string; categoryDisplay: string; lineCount: number }[],
  pendingTotal: number | string,
): FeeCategoryRow[] {
  const total = Number(pendingTotal ?? 0);
  return categories.map((row) => {
    const amount = Number(row.amount ?? 0);
    const ratio =
      total > 0 && Number.isFinite(amount) ? `${((amount / total) * 100).toFixed(2)}%` : '—';
    return {
      category: row.categoryDisplay,
      amount: formatCurrencyDisplay(row.amount),
      insuranceAmount: '—',
      selfAmount: formatCurrencyDisplay(row.amount),
      ratio,
    };
  });
}

/** 全部待结算明细 ID */
const allPendingChargeItemIds = computed(() => chargeLines.value.map((line) => line.chargeItemId));

/** 当前结算模式（出院全结 / 中途结算） */
const currentSettleMode = computed(() => {
  const snapMode = preSettleSnapshot.value?.settleMode;
  if (snapMode) {
    return snapMode;
  }
  const status = summary.value?.statusEnum ?? currentQueueRow.value?.statusEnum;
  return resolveInpatientSettleMode(
    status,
    selectedChargeItemIds.value,
    allPendingChargeItemIds.value,
  );
});

const currentSettleModeLabel = computed(() => {
  if (currentSettleMode.value === INPATIENT_SETTLE_MODE.DISCHARGE_FULL) {
    return '出院全结';
  }
  if (currentSettleMode.value === INPATIENT_SETTLE_MODE.MID_SETTLE) {
    return '中途结算';
  }
  return '—';
});

const isInsuranceRegistration = computed(() =>
  isInpatientInsuranceRegistration(currentQueueRow.value),
);

/** 解析本次渠道实付（少补/医保现金）；预交金充足时为 0。 */
function resolveChannelPayableAmount() {
  const snap = preSettleSnapshot.value;
  if (snap?.insurancePreSettled && snap.displayAmount != null) {
    return Number(snap.displayAmount);
  }
  if (snap?.displayAmount != null) {
    return Number(snap.displayAmount);
  }
  const hint = Number(summary.value?.balanceHint ?? 0);
  if (Number.isFinite(hint) && hint < 0) {
    return Math.abs(hint);
  }
  return 0;
}

/** 患者实付金额：预结算后以 displayAmount（少补）为准 */
const payableAmount = computed(() => {
  if (preSettleSnapshot.value) {
    return resolveChannelPayableAmount();
  }
  const hint = Number(summary.value?.balanceHint ?? 0);
  if (Number.isFinite(hint) && hint < 0) {
    return Math.abs(hint);
  }
  if (Number.isFinite(hint) && hint >= 0) {
    return 0;
  }
  return Number(summary.value?.selectedTotal ?? 0);
});

/** 医保报销合计（预结算后展示） */
const insuranceReimbursementAmount = computed(() => {
  const snap = preSettleSnapshot.value;
  if (!snap?.insurancePreSettled) {
    return null;
  }
  const fund = Number(snap.ybFundPaySumamt ?? 0);
  const acct = Number(snap.ybAcctPay ?? 0);
  const total = fund + acct;
  return Number.isFinite(total) ? total : null;
});

const settlementInsuranceBreakdown = computed(() => {
  const snap = preSettleSnapshot.value;
  if (!snap?.insurancePreSettled) {
    return [];
  }
  return [
    { key: 'medfee', label: '医疗费总额', value: snap.ybMedfeeSumamt ?? 0 },
    { key: 'fund', label: '基金支付', value: snap.ybFundPaySumamt ?? 0 },
    { key: 'acct', label: '个人账户', value: snap.ybAcctPay ?? 0 },
    { key: 'cash', label: '个人现金', value: snap.ybPsnCashPay ?? 0 },
  ];
});

const settlePayChannelOptions = computed(() => effectivePayChannelOptions.value);

const selectedSettleChargeLines = computed(() =>
  chargeLines.value.filter((line) => lockedChargeItemIds.value.includes(line.chargeItemId)),
);

const categoryDisplayRows = ref<FeeCategoryRow[]>([]);

/** 当前选中费用明细 ID（默认勾选待结算行） */
const selectedChargeItemIds = computed(() =>
  chargeLines.value.filter((line) => line.selected).map((line) => line.chargeItemId),
);

const billAmountMismatchContext = computed(() => {
  if (isSettledQueueTab.value && settledPaymentView.value) {
    const lines = settledPaymentView.value.lines ?? [];
    return {
      lineAmounts: lines.map((line) => line.lineAmount),
      paymentAmount: settledPaymentView.value.receivableTotal,
      paymentId: settledPaymentView.value.activePaymentId,
    };
  }
  const selectedLines = chargeLines.value.filter((line) => line.selected);
  const lineAmounts = selectedLines.map((line) => line.lineAmount);
  let paymentAmount = summary.value?.selectedTotal;
  const snap = preSettleSnapshot.value;
  if (snap?.insurancePreSettled && snap.displayAmount != null) {
    paymentAmount = snap.displayAmount;
  } else if (snap?.displayAmount != null) {
    paymentAmount = snap.displayAmount;
  } else if (Number(summary.value?.balanceHint ?? 0) < 0) {
    paymentAmount = Math.abs(Number(summary.value?.balanceHint ?? 0));
  } else if (snap?.receivableTotal != null) {
    paymentAmount = 0;
  }
  return {
    lineAmounts,
    paymentAmount,
    paymentId: snap?.paymentDraftId,
  };
});

/** 当前患者展示快照（绑定原模板字段） */
const currentPatientDisplay = computed<SettlePatientView>(() => {
  const row = currentQueueRow.value;
  const s = summary.value;
  const settled = settledPaymentView.value;
  if (!row && !s && !settled) {
    return emptyPatientView();
  }
  const admission = row?.startTime ? dayjs(row.startTime).format('YYYY-MM-DD') : '—';
  const settledMode = isSettledQueueTab.value && settled;
  const settleTimeText = settled?.settleTime
    ? dayjs(settled.settleTime).format('YYYY-MM-DD HH:mm:ss')
    : dayjs().format('YYYY-MM-DD HH:mm:ss');
  return {
    encounterId: row?.encounterId ?? s?.encounterId ?? settled?.encounterId ?? '',
    patientId: row?.patientId ?? s?.patientId ?? '',
    inpatientNo: s?.encounterBusNo ?? row?.encounterBusNo ?? '—',
    name: s?.patientName ?? row?.patientName ?? '—',
    gender: row?.genderEnumText ?? '—',
    age: row?.ageText ?? '—',
    department: s?.organizationName ?? row?.organizationName ?? '—',
    wardName: s?.wardName ?? row?.wardName ?? '—',
    bedNo: s?.bedName ?? row?.bedName ?? '—',
    admissionDate: admission,
    insuranceType: row?.contractDisplay ?? '—',
    doctorName: '—',
    totalFee: settledMode
      ? formatCurrencyDisplay(settled.receivableTotal)
      : formatCurrencyDisplay(s?.pendingSettlementTotal),
    reimbursementAmount:
      insuranceReimbursementAmount.value != null
        ? formatCurrencyDisplay(insuranceReimbursementAmount.value)
        : preSettleSnapshot.value?.insurancePreSettled
          ? formatCurrencyDisplay(
              Number(preSettleSnapshot.value.ybFundPaySumamt ?? 0) +
                Number(preSettleSnapshot.value.ybAcctPay ?? 0),
            )
          : '—',
    selfPayAmount: preSettleSnapshot.value?.insurancePreSettled
      ? formatCurrencyDisplay(
          preSettleSnapshot.value.displayAmount ?? preSettleSnapshot.value.ybPsnCashPay,
        )
      : preSettleSnapshot.value?.displayAmount != null
        ? formatCurrencyDisplay(preSettleSnapshot.value.displayAmount)
        : settledMode
          ? formatCurrencyDisplay(settled.receivableTotal)
          : Number(s?.balanceHint ?? 0) < 0
            ? formatBalanceHintDisplay(s?.balanceHint)
            : formatCurrencyDisplay(0),
    prepayTotal: settledMode ? '—' : formatCurrencyDisplay(s?.prepayNetBalance),
    refundOrSupplement: settledMode ? '—' : formatBalanceHintDisplay(s?.balanceHint),
    accountBalanceSummary: settledMode ? '—' : (s?.accountBalanceSummaryText ?? '—'),
    accountBalanceSub: settledMode ? undefined : s?.accountBalanceSubText,
    insuranceInFee: '—',
    insuranceOutFee: '—',
    settlementNo: settledMode
      ? (settled.activePaymentId ?? '—')
      : (preSettleSnapshot.value?.paymentDraftId ?? '—'),
    settlementStatus: settledMode ? '已结清' : '待结算',
    paymentTime: settleTimeText,
    settleDate:
      settledMode && settled.settleTime
        ? dayjs(settled.settleTime).format('YYYY-MM-DD')
        : dayjs().format('YYYY-MM-DD'),
    operatorName: settledMode ? (settled.operatorName ?? '—') : '—',
    feeCategoryRows: categoryDisplayRows.value,
    feeDetailRows: chargeLines.value.map((line) => mapChargeLineToFeeDetailRow(line)),
    reliefRows: [],
    settlementHistoryRows: settlementHistoryRows.value,
  };
});

const coreSettlementSummary = computed(() => {
  const rows = [
    { label: '住院总费用', value: currentPatientDisplay.value.totalFee },
    { label: '医保报销金额', value: currentPatientDisplay.value.reimbursementAmount },
    { label: '个人自费金额', value: currentPatientDisplay.value.selfPayAmount },
    { label: '预交押金总额', value: currentPatientDisplay.value.prepayTotal },
    { label: '应补金额 / 应退金额', value: currentPatientDisplay.value.refundOrSupplement },
  ];
  if (!isSettledQueueTab.value && currentPatientDisplay.value.accountBalanceSummary !== '—') {
    rows.push({
      label: '账户余额',
      value: currentPatientDisplay.value.accountBalanceSummary,
    });
    const sub = currentPatientDisplay.value.accountBalanceSub;
    if (sub) {
      rows.push({ label: '说明', value: sub });
    }
  }
  return rows;
});

const insuranceKeyInfoRows = computed<InsuranceInfoRow[]>(() => {
  const snap = preSettleSnapshot.value;
  if (snap?.insurancePreSettled) {
    return [
      { field: '结算模式', value: currentSettleModeLabel.value },
      { field: '医疗费总额', value: formatCurrencyDisplay(snap.ybMedfeeSumamt) },
      { field: '基金支付', value: formatCurrencyDisplay(snap.ybFundPaySumamt) },
      { field: '个人账户支付', value: formatCurrencyDisplay(snap.ybAcctPay) },
      { field: '个人现金支付', value: formatCurrencyDisplay(snap.ybPsnCashPay) },
      { field: '预结算快照 ID', value: snap.ybPreSettleId ?? '—' },
    ];
  }
  return [
    { field: '结算模式', value: currentSettleModeLabel.value },
    { field: '医保内费用', value: currentPatientDisplay.value.insuranceInFee },
    { field: '医保外自费项目合计', value: currentPatientDisplay.value.insuranceOutFee },
    { field: '费用性质', value: currentPatientDisplay.value.insuranceType },
  ];
});

/** 解析底部支付方式对应的渠道 code */
function resolvePayChannelCode() {
  const fromLabel = payChannelCodeByLabel.value[paymentMethod.value];
  if (fromLabel) {
    return fromLabel;
  }
  return PAY_LABEL_TO_CODE[paymentMethod.value] ?? FALLBACK_DEFAULT_PAY_CHANNEL;
}

/** 加载页初始化（支付渠道、队列页签等） */
async function loadPageInit() {
  try {
    const init = await httpLoadInpatientSettlePageInit();
    settleQueueTabOptions.value = init.settleQueueTabOptions ?? [];
    settleQueueTab.value = init.defaultSettleQueueTab || SETTLE_QUEUE_TAB.PENDING_SETTLE;
    paymentChannelSelectOptions.value = optionDtosToPayChannelOptions(init.paymentChannelOptions);
    const map: Record<string, string> = { ...PAY_LABEL_TO_CODE };
    for (const opt of paymentChannelSelectOptions.value) {
      map[opt.label] = opt.value;
    }
    payChannelCodeByLabel.value = map;
  } catch {
    ElMessage.error('结算页初始化失败');
  }
  await refreshUnionPayCapabilities();
}

/** 清空当前选中患者与工作区展示 */
function resetSettleWorkspace() {
  summary.value = null;
  settledPaymentView.value = null;
  chargeLines.value = [];
  categoryDisplayRows.value = [];
  settlementHistoryRows.value = [];
  settlementHistoryNetTotal.value = null;
  preSettleSnapshot.value = null;
}

/** 住院结算搜索患者列表（队列分页） */
async function querySettlePatientOptions({
  keyword = '',
  pageNo = 1,
  pageSize = 20,
}: {
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  const kw = keyword.trim();
  const queueRes = await httpPageInpatientSettleQueue({
    keyword: kw || undefined,
    settleQueueTab: settleQueueTab.value,
    pageNo,
    pageSize,
  });
  const parsed = parsePagedRowsFromApiData<InpatientSettleQueueItemDto>(queueRes);
  const content = parsed.content;
  const totalCount = parsed.totalCount;

  const next = { ...queueRowByEncounterId.value };
  for (const row of content) {
    next[row.encounterId] = row;
  }
  queueRowByEncounterId.value = next;
  queueRecords.value = content;
  return {
    content,
    totalElements: totalCount,
  };
}

/** 选中下拉行后按页签刷新工作区 */
async function handleSettlePatientSelect(value: string | string[]) {
  const encounterId = Array.isArray(value) ? (value[0] ?? '') : value;
  const row = queueRowByEncounterId.value[encounterId];
  if (row) {
    cacheQueueRow(row);
  }
  selectedEncounterId.value = encounterId;
  if (!encounterId) {
    resetSettleWorkspace();
    return;
  }
  if (!row?.patientId) {
    ElMessage.warning('患者信息不完整，请重新选择');
    return;
  }
  await reloadWorkspace();
}

/** 输入框失焦：按关键字检索并加载工作区 */
async function handleSettlePatientSearchBlur(keyword: string) {
  const kw = keyword.trim();
  if (!kw || selectedEncounterId.value) {
    return;
  }
  workspaceLoading.value = true;
  try {
    const { content } = await querySettlePatientOptions({ keyword: kw, pageNo: 1, pageSize: 1 });
    const row = content[0];
    if (!row) {
      ElMessage.warning('未找到患者');
      return;
    }
    cacheQueueRow(row);
    selectedEncounterId.value = row.encounterId;
    await reloadWorkspace();
  } finally {
    workspaceLoading.value = false;
  }
}

/** 加载待结算摘要（IF-IP-STL-03；仅待结算页签） */
async function loadSummary(ids?: { encounterId: string; patientId: string }) {
  const encounterId = preserveApiIdForRequest(
    ids?.encounterId ?? summary.value?.encounterId ?? selectedEncounterId.value,
  );
  const patientId = preserveApiIdForRequest(
    ids?.patientId ?? summary.value?.patientId ?? currentQueueRow.value?.patientId,
  );
  if (!encounterId || !patientId) {
    summary.value = null;
    return;
  }
  summary.value = await httpLoadInpatientSettleSummary({
    encounterId,
    patientId,
    selectedChargeItemIds:
      selectedChargeItemIds.value.length > 0 ? selectedChargeItemIds.value : undefined,
  });
}

/** 加载待结算费用与大类汇总（IF-IP-STL-04） */
async function loadPendingCharges() {
  const { encounterId, patientId } = resolveActiveSettlePatientIds();
  if (!encounterId || !patientId) {
    chargeLines.value = [];
    categoryDisplayRows.value = [];
    return;
  }
  const detailRes = await httpQueryInpatientSettleCharges({
    encounterId,
    patientId,
    presentationMode: SETTLE_PRESENTATION_MODE.DETAIL,
  });
  chargeLines.value = detailRes.lines ?? [];
  const summaryRes = await httpQueryInpatientSettleCharges({
    encounterId,
    patientId,
    presentationMode: SETTLE_PRESENTATION_MODE.SUMMARY,
  });
  categoryDisplayRows.value = mapCategoryRows(
    summaryRes.categories ?? [],
    detailRes.pendingSettlementTotal,
  );
  await loadSummary();
}

/** 加载住院结算/反结历史（IF-IP-STL-07；待结算与已结算页签共用） */
async function loadSettlementHistory(ids?: { encounterId: string; patientId: string }) {
  const encounterId = preserveApiIdForRequest(ids?.encounterId ?? selectedEncounterId.value);
  const patientId = preserveApiIdForRequest(ids?.patientId ?? currentQueueRow.value?.patientId);
  if (!encounterId || !patientId) {
    settlementHistoryRows.value = [];
    settlementHistoryNetTotal.value = null;
    return;
  }
  try {
    const historyRes = await httpLoadSettledInpatientPaymentView({
      encounterId,
      patientId,
      presentationMode: SETTLE_PRESENTATION_MODE.SUMMARY,
    });
    settlementHistoryRows.value = (historyRes.payments ?? []).map((p) =>
      mapPaymentToHistoryRow(p, historyRes),
    );
    settlementHistoryNetTotal.value = historyRes.netSettleTotal ?? null;
  } catch {
    // 首次待结算、尚无流水时静默为空，不打断主工作区
    settlementHistoryRows.value = [];
    settlementHistoryNetTotal.value = null;
  }
}

/** 加载已结算单查阅（IF-IP-STL-07；仅已结算页签） */
async function loadSettledWorkspace(ids?: { encounterId: string; patientId: string }) {
  const encounterId = preserveApiIdForRequest(ids?.encounterId ?? selectedEncounterId.value);
  const patientId = preserveApiIdForRequest(ids?.patientId ?? currentQueueRow.value?.patientId);
  if (!encounterId || !patientId) {
    settledPaymentView.value = null;
    chargeLines.value = [];
    categoryDisplayRows.value = [];
    settlementHistoryRows.value = [];
    return;
  }
  const detailRes = await httpLoadSettledInpatientPaymentView({
    encounterId,
    patientId,
    presentationMode: SETTLE_PRESENTATION_MODE.DETAIL,
  });
  settledPaymentView.value = detailRes;
  summary.value = null;
  chargeLines.value = detailRes.lines ?? [];
  const summaryRes = await httpLoadSettledInpatientPaymentView({
    encounterId,
    patientId,
    presentationMode: SETTLE_PRESENTATION_MODE.SUMMARY,
  });
  categoryDisplayRows.value = mapCategoryRows(
    summaryRes.categories ?? [],
    detailRes.receivableTotal ?? summaryRes.receivableTotal ?? 0,
  );
  settlementHistoryRows.value = (detailRes.payments ?? []).map((p) =>
    mapPaymentToHistoryRow(p, detailRes),
  );
  settlementHistoryNetTotal.value = detailRes.netSettleTotal ?? null;
}

/** 待结算页签工作区：费用 + 摘要 + 历史流水只读 */
async function loadPendingWorkspace() {
  settledPaymentView.value = null;
  await loadPendingCharges();
  await loadSettlementHistory();
}

/** 支付流水转历史结算表格行 */
function mapPaymentToHistoryRow(
  payment: SettledPaymentBriefDto,
  view: { operatorName?: string },
): SettlementHistoryRow {
  const signedAmount = payment.signedAmount ?? payment.receivableTotal;
  const isReverse = payment.recordType === 'REVERSE';
  let status = '已结清';
  if (payment.statusEnum === 'REFUNDED') {
    if (isReverse) {
      status = '反结';
    } else {
      status = '已反结';
    }
  }
  return {
    paymentId: payment.paymentId,
    settlementNo: payment.paymentId,
    settleTime: payment.settleTime ? formatHistorySettleTimeDisplay(payment.settleTime) : '—',
    settleType: isReverse ? '反结' : '结算',
    totalAmount: formatHistoryAmountDisplay(signedAmount),
    reimbursementAmount: '—',
    selfPayAmount: formatHistoryAmountDisplay(signedAmount),
    paymentMethod: payment.payMethodText ?? '—',
    operatorName: view.operatorName ?? '—',
    ticketNo: '—',
    status,
    recordType: payment.recordType,
    reverseEnabled: payment.reverseEnabled,
    reverseDisabledReason: payment.reverseDisabledReason,
    detailRows: [],
  };
}

/** 历史结算净额展示（已结算走全量视图，待结算走独立历史净额） */
const settledNetTotalDisplay = computed(() => {
  const net = isSettledQueueTab.value
    ? settledPaymentView.value?.netSettleTotal
    : settlementHistoryNetTotal.value;
  if (net === undefined || net === null) {
    return '—';
  }
  return formatCurrencyDisplay(net);
});

/** 选中患者后按页签刷新右侧工作区 */
async function reloadWorkspace() {
  if (!selectedEncounterId.value) {
    return;
  }
  workspaceLoading.value = true;
  try {
    preSettleSnapshot.value = null;
    if (isSettledQueueTab.value) {
      await loadSettledWorkspace();
    } else {
      await loadPendingWorkspace();
    }
  } finally {
    workspaceLoading.value = false;
  }
}

/** 顶部工具栏占位动作 */
function handleToolbarAction(actionLabel: string) {
  if (actionLabel === '刷新费用') {
    void reloadWorkspace().then(() => ElMessage.success('费用已刷新'));
    return;
  }
  if (actionLabel === '医保校验') {
    const contractId = preserveApiIdForRequest(currentQueueRow.value?.contractId);
    if (!contractId) {
      ElMessage.warning('当前患者非医保登记或缺少合同信息');
      return;
    }
    void verifyInpatientInsuranceCard(contractId);
    return;
  }
  ElMessage.success(`${actionLabel}已触发`);
}

/** 底部结算 */
async function handleFooterAction(actionLabel: string) {
  if (actionLabel === '确认结算') {
    await handleOpenSettleDialog();
  }
}

/** 对指定历史结算单执行反结（IF-IP-RVS-01） */
async function handleReverseInpatientSettle(row: SettlementHistoryRow) {
  if (!isSettledQueueTab.value) {
    return;
  }
  if (!row.reverseEnabled) {
    ElMessage.warning(row.reverseDisabledReason ?? '当前结算单不可反结');
    return;
  }
  const { encounterId, patientId } = resolveActiveSettlePatientIds();
  if (!encounterId || !patientId) {
    ElMessage.warning('请先搜索并选择患者');
    return;
  }
  const paymentId = preserveApiIdForRequest(row.paymentId);
  if (!paymentId) {
    ElMessage.warning('未找到可反结的结算单');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '反结后将生成一条负向记录，本批费用恢复待结算。是否继续？',
      '住院反结',
      { type: 'warning', confirmButtonText: '确认反结', cancelButtonText: '返回' },
    );
  } catch {
    return;
  }
  reverseSettleLoading.value = true;
  try {
    const result = await httpReverseInpatientSettle({
      paymentId,
      encounterId,
      patientId,
    });
    const tips: string[] = ['住院反结成功'];
    if (result.refundPaymentId) {
      tips.push(`逆向单：${result.refundPaymentId}`);
    }
    ElMessage.success(tips.join('；'));
    const keepEncounterId = selectedEncounterId.value;
    const keepRow = currentQueueRow.value;
    skipQueueTabWatchReset.value = true;
    settleQueueTab.value = SETTLE_QUEUE_TAB.PENDING_SETTLE;
    if (keepEncounterId && keepRow) {
      selectedEncounterId.value = keepEncounterId;
      cacheQueueRow(keepRow);
    }
    if (selectedEncounterId.value) {
      await reloadWorkspace();
    }
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '住院反结失败';
    ElMessage.error(message);
  } finally {
    reverseSettleLoading.value = false;
  }
}

/** 预结算并打开确认弹窗 */
async function handleOpenSettleDialog() {
  const { encounterId, patientId } = resolveActiveSettlePatientIds();
  if (!encounterId || !patientId) {
    ElMessage.warning('请先搜索并选择患者');
    return;
  }
  const chargeItemIds = selectedChargeItemIds.value;
  const encounterStatus = summary.value?.statusEnum ?? currentQueueRow.value?.statusEnum;
  const zeroAmountDischargeFull = isZeroAmountDischargeFullSettle(
    encounterStatus,
    chargeItemIds,
    allPendingChargeItemIds.value,
  );
  if (chargeItemIds.length === 0 && !zeroAmountDischargeFull) {
    ElMessage.warning('请选择待结算费用');
    return;
  }
  const settleMode = resolveInpatientSettleMode(
    encounterStatus,
    chargeItemIds,
    allPendingChargeItemIds.value,
  );
  if (!settleMode) {
    ElMessage.warning('请先完成护士出院或改为中途结算');
    return;
  }
  preSettleLoading.value = true;
  try {
    let insurancePayload: Record<string, string | undefined> = {};
    if (isInsuranceRegistration.value) {
      const contractId = preserveApiIdForRequest(currentQueueRow.value?.contractId);
      if (!contractId) {
        ElMessage.warning('医保患者缺少合同信息，请检查住院登记');
        return;
      }
      try {
        const insuranceCtx = await resolveInpatientSettleInsuranceContext(contractId);
        if (insuranceCtx) {
          insurancePayload = {
            contractId: insuranceCtx.contractId,
            insuplcAdmdvs: insuranceCtx.insuplcAdmdvs,
            insutype: insuranceCtx.insutype,
            psnNo: insuranceCtx.psnNo,
            ybCardInfo: insuranceCtx.ybCardInfo,
            ybMediumType: insuranceCtx.ybMediumType,
          };
        } else {
          ElMessage.warning(
            '本机 OpenHIS.Win 未启动，已跳过医保读卡；自费结算可继续，医保结算请先启动插件',
          );
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : '医保读卡失败';
        ElMessage.error(message);
        return;
      }
    }
    const pre = await httpPreSettleInpatientCharges({
      encounterId,
      patientId,
      chargeItemIds,
      settleMode,
      ...insurancePayload,
    });
    preSettleSnapshot.value = pre;
    lockedChargeItemIds.value = [...chargeItemIds];
    settlePayChannelCode.value = resolvePayChannelCode();
    settleDialogVisible.value = true;
  } finally {
    preSettleLoading.value = false;
  }
}

/** 正式结算成功后的页面刷新与提示 */
async function finalizeAfterSettleSuccess(confirmRes: ConfirmInpatientSettleResponse) {
  if (confirmRes.paymentId != null) {
    lastSettlePaymentId.value = String(confirmRes.paymentId);
  }
  if (confirmRes.insuranceSettled && confirmRes.ybSetlId) {
    ElMessage.success(`住院结算成功（医保结算号：${confirmRes.ybSetlId}）`);
  } else {
    ElMessage.success('住院结算成功');
  }
  preSettleSnapshot.value = null;
  lockedChargeItemIds.value = [];
  if (confirmRes.dischargeFullSettle) {
    const keepEncounterId = selectedEncounterId.value;
    const keepRow = currentQueueRow.value;
    skipQueueTabWatchReset.value = true;
    settleQueueTab.value = SETTLE_QUEUE_TAB.SETTLED;
    if (keepEncounterId && keepRow) {
      selectedEncounterId.value = keepEncounterId;
      cacheQueueRow(keepRow);
    }
  }
  if (selectedEncounterId.value) {
    await reloadWorkspace();
  }
}

function handleBillBannerFocusAdjust() {
  activeTab.value = 'fee-detail';
}

/** 弹窗内确认正式结算 */
async function handleConfirmSettleFromDialog() {
  const snap = preSettleSnapshot.value;
  const { encounterId, patientId } = resolveActiveSettlePatientIds();
  if (!snap || !encounterId || !patientId) {
    return;
  }
  const chargeItemIds = lockedChargeItemIds.value;
  const zeroAmountDischargeFull =
    chargeItemIds.length === 0 &&
    snap.settleMode === INPATIENT_SETTLE_MODE.DISCHARGE_FULL &&
    Number(snap.receivableTotal ?? 0) === 0;
  if (chargeItemIds.length === 0 && !zeroAmountDischargeFull) {
    ElMessage.warning('请选择待结算费用');
    return;
  }
  const payAmount = resolveChannelPayableAmount();
  if (
    isScanPayChannel(settlePayChannelCode.value) &&
    payAmount > 0 &&
    !settleScanCode.value.trim()
  ) {
    ElMessage.warning('请输入付款码');
    return;
  }
  confirmSettleLoading.value = true;
  settleScanStatusText.value = '';
  try {
    let clientScanResult;
    if (isScanPayChannel(settlePayChannelCode.value) && payAmount > 0) {
      if (!scanPayEnabled.value) {
        ElMessage.error('请启动本地客户端（OpenHIS.Win）并确认银联组件已安装');
        return;
      }
      settleScanStatusText.value = '等待支付结果…';
      const scanOutcome = await submitScanPayment({
        paymentDraftId: snap.paymentDraftId,
        amount: payAmount,
        scanCode: settleScanCode.value,
      });
      clientScanResult = scanOutcome.clientScanResult;
    }

    const confirmRes = clientScanResult
      ? await settleWithScanRecovery(clientScanResult, () =>
          httpConfirmInpatientSettle({
            billDate: dayjs().format('YYYY-MM-DD HH:mm:ss'),
            encounterId,
            patientId,
            paymentDraftId: snap.paymentDraftId,
            chargeItemIds,
            ybPreSettleId: snap.ybPreSettleId,
            clientScanResult,
            paymentDetails:
              payAmount > 0
                ? [
                    {
                      amount: payAmount,
                      payChannelCode: settlePayChannelCode.value || resolvePayChannelCode(),
                    },
                  ]
                : [],
          }),
        )
      : await httpConfirmInpatientSettle({
          billDate: dayjs().format('YYYY-MM-DD HH:mm:ss'),
          encounterId,
          patientId,
          paymentDraftId: snap.paymentDraftId,
          chargeItemIds,
          ybPreSettleId: snap.ybPreSettleId,
          paymentDetails:
            payAmount > 0
              ? [
                  {
                    amount: payAmount,
                    payChannelCode: settlePayChannelCode.value || resolvePayChannelCode(),
                  },
                ]
              : [],
        });
    settleDialogVisible.value = false;
    settleScanCode.value = '';
    await finalizeAfterSettleSuccess(confirmRes);
  } catch (error) {
    const message = error instanceof Error ? error.message : '住院结算失败';
    ElMessage.error(message);
  } finally {
    confirmSettleLoading.value = false;
  }
}

function settlementHistoryStatusTagType(status: string) {
  if (status === '已结清') {
    return 'success';
  }
  if (status === '待补缴') {
    return 'warning';
  }
  if (status === '反结' || status === '已反结') {
    return 'info';
  }
  return 'info';
}

/** 打开历史结算记录的费用明细弹窗 */
async function handleOpenSettlementDetail(row: SettlementHistoryRow) {
  selectedSettlementHistory.value = row;
  const { encounterId, patientId } = resolveActiveSettlePatientIds();
  if (encounterId && patientId && row.paymentId) {
    const res = await httpLoadSettledInpatientPaymentView({
      encounterId,
      patientId,
      paymentId: preserveApiIdForRequest(row.paymentId),
      presentationMode: SETTLE_PRESENTATION_MODE.DETAIL,
    });
    historyDetailRows.value = (res.lines ?? []).map((line) => mapChargeLineToFeeDetailRow(line));
  } else {
    historyDetailRows.value = row.detailRows;
  }
  settlementDetailVisible.value = true;
}

/** 关闭历史结算明细弹窗 */
function handleCloseSettlementDetail() {
  settlementDetailVisible.value = false;
  selectedSettlementHistory.value = null;
  historyDetailRows.value = [];
}

onMounted(async () => {
  await loadPageInit();
});

/** 切换队列页签时清空选中并复位工作区（程序内切换可跳过） */
watch(settleQueueTab, () => {
  if (skipQueueTabWatchReset.value) {
    skipQueueTabWatchReset.value = false;
    return;
  }
  selectedEncounterId.value = '';
  selectedPatientLabel.value = '';
  resetSettleWorkspace();
});
</script>

<template>
  <Page
    class="settle-page !mt-0 bg-[var(--el-fill-color-light)]"
    content-class="!p-0"
    v-loading="workspaceLoading"
  >
    <div class="flex h-full min-h-0 flex-col gap-3 overflow-hidden p-2">
      <ElCard shadow="never" class="settle-card" body-class="!p-3">
        <div class="settle-toolbar flex flex-wrap items-center justify-between gap-3">
          <div class="settle-toolbar__main flex min-w-0 flex-1 items-center gap-3">
            <ElRadioGroup v-model="settleQueueTab" class="settle-queue-tab flex-shrink-0">
              <ElRadioButton
                v-for="opt in settleQueueTabRadioOptions"
                :key="opt.value"
                :value="opt.value"
              >
                {{ opt.label }}
              </ElRadioButton>
            </ElRadioGroup>
            <SelectTable
              class="settle-patient-select min-w-0 flex-1"
              :placeholder="
                isSettledQueueTab ? '请输入住院号/姓名搜索已结算患者' : '请输入住院号/姓名搜索患者'
              "
              :columns="settlePatientSelectColumns"
              :remote-method="querySettlePatientOptions"
              value-key="encounterId"
              label-key="patientName"
              :selected-label="selectedPatientLabel"
              v-model="selectedEncounterId"
              @change="handleSettlePatientSelect"
              @blur="handleSettlePatientSearchBlur"
            />
          </div>
          <div class="flex flex-shrink-0 items-center gap-2">
            <ElButton plain @click="handleToolbarAction('刷新费用')">刷新费用</ElButton>
            <ElButton type="primary" @click="handleToolbarAction('医保校验')">医保校验</ElButton>
          </div>
        </div>

        <div class="patient-info-bar mt-2">
          <div class="patient-info-row">
            <div class="patient-info-item">
              <span class="patient-info-label">住院号</span>
              <span class="patient-info-value">{{ currentPatientDisplay.inpatientNo }}</span>
            </div>
            <div class="patient-info-item">
              <span class="patient-info-label">姓名</span>
              <span class="patient-info-value">{{ currentPatientDisplay.name }}</span>
            </div>
            <div class="patient-info-item">
              <span class="patient-info-label">性别/年龄</span>
              <span class="patient-info-value"
                >{{ currentPatientDisplay.gender }}/{{ currentPatientDisplay.age }}</span
              >
            </div>
            <div class="patient-info-item">
              <span class="patient-info-label">床位</span>
              <span class="patient-info-value">{{ currentPatientDisplay.bedNo }}</span>
            </div>
            <div class="patient-info-item">
              <span class="patient-info-label">科室</span>
              <span class="patient-info-value">{{ currentPatientDisplay.department }}</span>
            </div>
            <div class="patient-info-item">
              <span class="patient-info-label">病区</span>
              <span class="patient-info-value">{{ currentPatientDisplay.wardName }}</span>
            </div>
            <div class="patient-info-item">
              <span class="patient-info-label">入院日期</span>
              <span class="patient-info-value">{{ currentPatientDisplay.admissionDate }}</span>
            </div>
            <div class="patient-info-item">
              <span class="patient-info-label">医保类型</span>
              <span class="patient-info-value">{{ currentPatientDisplay.insuranceType }}</span>
            </div>
            <div
              v-if="
                !isSettledQueueTab && (currentSettleModeLabel !== '—' || isInsuranceRegistration)
              "
              class="patient-info-item patient-info-item--tags"
            >
              <span class="patient-info-label">标识</span>
              <div class="patient-info-tags">
                <ElTag
                  v-if="!isSettledQueueTab && currentSettleModeLabel !== '—'"
                  type="warning"
                  effect="plain"
                  size="small"
                >
                  {{ currentSettleModeLabel }}
                </ElTag>
                <ElTag
                  v-if="isInsuranceRegistration && !isSettledQueueTab"
                  type="success"
                  effect="plain"
                  size="small"
                >
                  医保
                </ElTag>
              </div>
            </div>
            <div class="patient-info-item">
              <span class="patient-info-label">主治医生</span>
              <span class="patient-info-value">{{ currentPatientDisplay.doctorName }}</span>
            </div>
          </div>
        </div>
      </ElCard>

      <div class="settle-main-layout min-h-0 flex-1">
        <div class="settle-main-left flex min-h-0 flex-col gap-3">
          <ElCard shadow="never" class="settle-card" body-class="!p-3">
            <SectionTitle title="费用合计" />
            <EinvoiceBillAmountMismatchBanner
              :line-amounts="billAmountMismatchContext.lineAmounts"
              :payment-amount="billAmountMismatchContext.paymentAmount"
              :payment-id="billAmountMismatchContext.paymentId"
              @focus-adjust="handleBillBannerFocusAdjust"
            />
            <EinvoiceIssueStatusPanel
              v-if="lastSettlePaymentId"
              :payment-id="lastSettlePaymentId"
            />
            <div class="mt-2">
              <div class="summary-grid summary-grid--five">
                <div v-for="item in coreSettlementSummary" :key="item.label" class="summary-item">
                  <div class="summary-item__label">{{ item.label }}</div>
                  <div class="summary-item__value">{{ item.value }}</div>
                </div>
              </div>
            </div>
          </ElCard>

          <ElCard
            shadow="never"
            class="settle-card min-h-0 flex-1 overflow-hidden"
            body-class="settle-fee-card-body h-full !p-0"
          >
            <div class="settle-detail-panel settle-detail-panel--fee">
              <div class="px-3 pt-3">
                <SectionTitle title="费用明细" />
              </div>

              <ElTabs v-model="activeTab" class="settle-detail-tabs min-h-0 flex-1 px-4">
                <ElTabPane label="费用大类明细" name="category-summary">
                  <ElTable
                    :data="currentPatientDisplay.feeCategoryRows"
                    stripe
                    border
                    height="100%"
                  >
                    <ElTableColumn type="index" label="序号" width="60" align="center" />
                    <ElTableColumn prop="category" label="费用大类" min-width="180" />
                    <ElTableColumn prop="amount" label="费用金额" min-width="130" align="right" />
                    <ElTableColumn
                      prop="insuranceAmount"
                      label="医保内金额"
                      min-width="130"
                      align="right"
                    />
                    <ElTableColumn
                      prop="selfAmount"
                      label="自费金额"
                      min-width="130"
                      align="right"
                    />
                    <ElTableColumn prop="ratio" label="占比" min-width="90" align="center" />
                  </ElTable>
                </ElTabPane>

                <ElTabPane label="费用明细清单" name="fee-detail">
                  <ElTable
                    :data="currentPatientDisplay.feeDetailRows"
                    stripe
                    border
                    height="100%"
                    row-key="chargeItemId"
                  >
                    <ElTableColumn type="index" label="序号" width="60" align="center" />
                    <ElTableColumn prop="itemName" label="项目名称" min-width="220" />
                    <ElTableColumn prop="category" label="费用类别" min-width="140" />
                    <ElTableColumn
                      prop="priceQuantity"
                      label="单价/数量"
                      min-width="140"
                      align="right"
                    />
                    <ElTableColumn prop="amount" label="金额" min-width="110" align="right" />
                    <ElTableColumn
                      prop="insuranceFlag"
                      label="医保属性"
                      min-width="120"
                      align="center"
                    />
                  </ElTable>
                </ElTabPane>

                <ElTabPane label="医保结算信息" name="insurance-detail">
                  <ElTable :data="insuranceKeyInfoRows" stripe border height="100%">
                    <ElTableColumn type="index" label="序号" width="60" align="center" />
                    <ElTableColumn prop="field" label="医保字段" min-width="220" />
                    <ElTableColumn prop="value" label="字段值" min-width="260" />
                  </ElTable>
                </ElTabPane>

                <ElTabPane label="减免 / 补助记录" name="relief-record">
                  <ElTable :data="currentPatientDisplay.reliefRows" stripe border height="100%">
                    <ElTableColumn type="index" label="序号" width="60" align="center" />
                    <ElTableColumn prop="type" label="类型" min-width="180" />
                    <ElTableColumn prop="amount" label="金额" min-width="140" align="right" />
                    <ElTableColumn prop="recordDate" label="记录时间" min-width="180" />
                    <ElTableColumn prop="approver" label="审核人 / 来源" min-width="180" />
                  </ElTable>
                </ElTabPane>
              </ElTabs>
            </div>
          </ElCard>
        </div>

        <ElCard
          shadow="never"
          class="settle-card settle-main-right min-h-0 overflow-hidden"
          body-class="settle-history-card-body h-full !p-0"
        >
          <div class="settle-detail-panel settle-detail-panel--history">
            <div class="flex items-center justify-between gap-2 px-3 pt-3">
              <SectionTitle title="历史结算记录" />
              <span class="text-xs text-[var(--el-text-color-secondary)]"
                >共 {{ currentPatientDisplay.settlementHistoryRows.length }} 条 · 净额
                {{ settledNetTotalDisplay }}</span
              >
            </div>
            <div class="settle-history-table-wrap p-3 pt-2">
              <ElTable
                :data="currentPatientDisplay.settlementHistoryRows"
                stripe
                border
                height="100%"
                highlight-current-row
              >
                <ElTableColumn type="index" label="序号" width="56" align="center" />
                <ElTableColumn label="单据号" min-width="130" align="center">
                  <template #default>—</template>
                </ElTableColumn>
                <ElTableColumn prop="settleTime" label="结算时间" min-width="140" align="center" />
                <ElTableColumn prop="settleType" label="结算类型" width="90" align="center" />
                <ElTableColumn prop="totalAmount" label="结算金额" width="88" align="right" />
                <ElTableColumn
                  prop="reimbursementAmount"
                  label="医保报销"
                  width="88"
                  align="right"
                />
                <ElTableColumn prop="selfPayAmount" label="个人自付" width="88" align="right" />
                <ElTableColumn prop="paymentMethod" label="支付方式" width="90" align="center" />
                <ElTableColumn prop="operatorName" label="操作员" width="90" align="center" />
                <ElTableColumn prop="status" label="状态" width="90" align="center">
                  <template #default="{ row }">
                    <ElTag
                      :type="settlementHistoryStatusTagType(row.status)"
                      effect="plain"
                      size="small"
                    >
                      {{ row.status }}
                    </ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="操作" width="100" fixed="right" align="center">
                  <template #default="{ row }">
                    <ElButton type="primary" link @click="handleOpenSettlementDetail(row)">
                      明细
                    </ElButton>
                    <ElTooltip
                      v-if="isSettledQueueTab"
                      :content="row.reverseDisabledReason ?? '反结'"
                      :disabled="Boolean(row.reverseEnabled)"
                    >
                      <ElButton
                        type="warning"
                        link
                        :disabled="!row.reverseEnabled"
                        :loading="reverseSettleLoading"
                        @click="handleReverseInpatientSettle(row)"
                      >
                        反结
                      </ElButton>
                    </ElTooltip>
                  </template>
                </ElTableColumn>
              </ElTable>
            </div>
          </div>
        </ElCard>
      </div>

      <ElCard shadow="never" class="settle-card" body-class="!p-3">
        <div class="payment-footer-layout">
          <div class="payment-footer-left">
            <SectionTitle title="结算支付信息" />
            <div class="payment-footer-amount-line">
              <span class="payment-footer-amount-label">本次患者实付：</span>
              <strong class="settle-payable-amount">
                ¥{{ Number.isFinite(payableAmount) ? payableAmount.toFixed(2) : '0.00' }}
              </strong>
            </div>
            <p
              v-if="isInsuranceRegistration"
              class="payment-footer-hint text-xs text-[var(--el-text-color-secondary)]"
            >
              医保登记患者，确认结算时将自动读卡预结算
            </p>
          </div>

          <div class="payment-footer-meta">
            <div class="payment-footer-meta-line">
              <span class="payment-footer-meta-label">实际支付方式：</span>
              <ElRadioGroup v-model="paymentMethod" class="payment-footer-pay-method">
                <ElRadio label="现金" value="现金" />
                <ElRadio label="微信" value="微信" />
                <ElRadio label="银行卡" value="银行卡" />
              </ElRadioGroup>
            </div>
            <div class="payment-footer-meta-line">
              <span class="payment-footer-meta-label">结算时间：</span>
              <span class="payment-footer-meta-value">{{ currentPatientDisplay.paymentTime }}</span>
              <span class="payment-footer-meta-label payment-footer-meta-label--gap"
                >结算状态：</span
              >
              <ElTag
                :type="currentPatientDisplay.settlementStatus === '已结清' ? 'success' : 'warning'"
                effect="plain"
                size="small"
              >
                {{ currentPatientDisplay.settlementStatus }}
              </ElTag>
            </div>
          </div>

          <div class="payment-footer-actions">
            <ElButton
              v-if="!isSettledQueueTab"
              type="primary"
              :loading="preSettleLoading || confirmSettleLoading"
              @click="handleFooterAction('确认结算')"
            >
              结算
            </ElButton>
          </div>
        </div>
      </ElCard>
    </div>

    <AppDialog
      v-model="settlementDetailVisible"
      :title="`结算明细 · ${selectedSettlementHistory?.settlementNo ?? ''}`"
      width="960px"
      destroy-on-close
      @closed="handleCloseSettlementDetail"
    >
      <template v-if="selectedSettlementHistory">
        <div
          class="settle-history-detail-summary mb-3 grid gap-x-4 gap-y-2 text-sm text-[var(--el-text-color-primary)]"
        >
          <div>
            <span class="text-[var(--el-text-color-secondary)]">结算时间：</span>
            {{ selectedSettlementHistory.settleTime }}
          </div>
          <div>
            <span class="text-[var(--el-text-color-secondary)]">结算类型：</span>
            {{ selectedSettlementHistory.settleType }}
          </div>
          <div>
            <span class="text-[var(--el-text-color-secondary)]">结算金额：</span>
            {{ selectedSettlementHistory.totalAmount }}
          </div>
          <div>
            <span class="text-[var(--el-text-color-secondary)]">医保报销：</span>
            {{ selectedSettlementHistory.reimbursementAmount }}
          </div>
          <div>
            <span class="text-[var(--el-text-color-secondary)]">个人自付：</span>
            {{ selectedSettlementHistory.selfPayAmount }}
          </div>
          <div>
            <span class="text-[var(--el-text-color-secondary)]">支付方式：</span>
            {{ selectedSettlementHistory.paymentMethod }}
          </div>
          <div>
            <span class="text-[var(--el-text-color-secondary)]">操作员：</span>
            {{ selectedSettlementHistory.operatorName }}
          </div>
          <div>
            <span class="text-[var(--el-text-color-secondary)]">票据号：</span>
            {{ selectedSettlementHistory.ticketNo }}
          </div>
          <div>
            <span class="text-[var(--el-text-color-secondary)]">状态：</span>
            <ElTag
              :type="settlementHistoryStatusTagType(selectedSettlementHistory.status)"
              effect="plain"
              size="small"
            >
              {{ selectedSettlementHistory.status }}
            </ElTag>
          </div>
        </div>
        <ElTable :data="historyDetailRows" stripe border max-height="420">
          <ElTableColumn type="index" label="序号" width="60" align="center" />
          <ElTableColumn prop="orderNo" label="医嘱号" min-width="140" show-overflow-tooltip />
          <ElTableColumn prop="itemName" label="项目名称" min-width="200" show-overflow-tooltip />
          <ElTableColumn prop="category" label="费用类别" min-width="120" show-overflow-tooltip />
          <ElTableColumn prop="spec" label="规格" min-width="100" show-overflow-tooltip />
          <ElTableColumn prop="price" label="单价" min-width="100" align="right" />
          <ElTableColumn prop="quantity" label="数量" width="80" align="center" />
          <ElTableColumn prop="amount" label="金额" min-width="110" align="right" />
          <ElTableColumn prop="insuranceFlag" label="医保属性" width="100" align="center" />
        </ElTable>
      </template>
      <template #footer>
        <ElButton @click="handleCloseSettlementDetail">关闭</ElButton>
      </template>
    </AppDialog>

    <InpatientSettleDialog
      v-model="settleDialogVisible"
      v-model:pay-channel-code="settlePayChannelCode"
      v-model:scan-code="settleScanCode"
      :charge-lines="selectedSettleChargeLines"
      :loading="preSettleLoading"
      :confirm-loading="confirmSettleLoading"
      :receivable-total="preSettleSnapshot?.receivableTotal ?? 0"
      :display-amount="preSettleSnapshot?.displayAmount ?? 0"
      :preview-text="preSettleSnapshot?.previewText"
      :insurance-pre-settled="preSettleSnapshot?.insurancePreSettled"
      :insurance-breakdown-items="settlementInsuranceBreakdown"
      :settle-mode-label="currentSettleModeLabel"
      :pay-channel-options="settlePayChannelOptions"
      :scan-pay-enabled="scanPayEnabled"
      :scan-status-text="settleScanStatusText"
      @confirm="handleConfirmSettleFromDialog"
    />
  </Page>
</template>

<style scoped>
.settle-page {
  background: transparent !important;
  box-shadow: none;
}

.settle-page :deep(.el-card__body) {
  height: 100%;
}

.settle-page :deep(.settle-card.el-card) {
  border: none;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 2px 8px rgb(0 0 0 / 6%);
}

.settle-page :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.settle-page :deep(.settle-detail-tabs .el-tabs__item.is-active) {
  color: var(--el-color-primary);
  font-weight: 600;
}

.settle-page :deep(.settle-detail-tabs .el-tabs__active-bar) {
  height: 3px;
  border-radius: 2px;
}

.settle-page :deep(.el-table th.el-table__cell) {
  background: #f5f7fa;
}

.settle-page :deep(.el-select),
.settle-page :deep(.el-date-editor) {
  width: 100%;
}

.settle-toolbar__main {
  min-width: 280px;
}

.settle-patient-select {
  min-width: 200px;
  max-width: 560px;
}

.settle-page :deep(.settle-patient-select .el-input__wrapper) {
  border-radius: 8px;
}

.settle-queue-tab {
  display: inline-flex;
  flex-wrap: nowrap;
  gap: 8px;
}

.settle-page :deep(.settle-queue-tab .el-radio-button .el-radio-button__inner) {
  padding: 6px 16px;
  border: 1px solid var(--el-border-color-lighter) !important;
  border-radius: 16px !important;
  box-shadow: none !important;
}

.settle-page :deep(.settle-queue-tab .el-radio-button.is-active .el-radio-button__inner) {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary) !important;
  background: #fff;
}

.patient-info-bar {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-light);
  padding: 8px 12px;
}

.patient-info-row {
  display: flex;
  flex-wrap: nowrap;
  align-items: stretch;
  gap: 8px;
  width: 65%;
  max-width: 65%;
}

.patient-info-item {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}

.patient-info-item--tags .patient-info-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.patient-info-label {
  color: var(--el-text-color-regular);
  font-size: 13px;
  white-space: nowrap;
}

.patient-info-value {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settle-filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: space-between;
}

.settle-inline-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.settle-inline-field__label {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.settle-main-layout {
  display: grid;
  min-height: 0;
  gap: 12px;
  grid-template-columns: minmax(0, 50fr) minmax(0, 50fr);
}

.settle-main-left {
  min-width: 0;
}

.settle-main-right {
  min-width: 0;
  height: 100%;
}

.settle-page :deep(.settle-fee-card-body),
.settle-page :deep(.settle-history-card-body) {
  display: flex;
  min-height: 0;
  flex-direction: column;
}

.settle-detail-panel {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.settle-detail-panel--fee {
  height: 100%;
}

.settle-detail-panel--history {
  height: 100%;
}

.settle-detail-tabs {
  display: flex;
  min-height: 0;
  flex-direction: column;
}

.settle-page :deep(.settle-detail-tabs .el-tabs__content) {
  flex: 1;
  min-height: 0;
}

.settle-page :deep(.settle-detail-tabs .el-tab-pane) {
  height: 100%;
}

.settle-history-table-wrap {
  display: flex;
  min-height: 0;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  overflow: auto;
}

.summary-grid {
  display: grid;
  gap: 8px;
}

.summary-grid--five {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.summary-item {
  border: 1px solid #eef0f3;
  border-radius: 8px;
  background: #fafbfc;
  padding: 12px 14px;
}

.summary-item__label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 500;
}

.summary-item__value {
  margin-top: 6px;
  color: var(--el-color-primary);
  font-size: 22px;
  font-weight: 700;
}

.payment-footer-layout {
  display: flex;
  align-items: center;
  gap: 24px;
}

.payment-footer-left {
  flex: 0 0 auto;
  min-width: 0;
}

.payment-footer-amount-line {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 4px;
}

.payment-footer-amount-label {
  color: var(--el-text-color-regular);
  font-size: 14px;
  white-space: nowrap;
}

.payment-footer-meta {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  padding: 0 8px;
}

.payment-footer-meta-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 8px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.payment-footer-meta-label {
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.payment-footer-meta-label--gap {
  margin-left: 12px;
}

.payment-footer-meta-value {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.payment-footer-pay-method {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.settle-page :deep(.payment-footer-pay-method .el-radio) {
  margin-right: 8px;
}

.settle-page :deep(.payment-footer-pay-method .el-radio__label) {
  padding-left: 4px;
  font-size: 13px;
}

.payment-footer-hint {
  margin: 4px 0 0;
}

.settle-payable-amount {
  color: var(--el-color-primary);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}

.payment-footer-actions {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.settle-history-detail-summary {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

@media (width <= 1400px) {
  .settle-main-layout {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(0, 1fr) minmax(260px, 0.5fr);
  }

  .summary-grid--five {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .payment-footer-layout {
    align-items: stretch;
    flex-direction: column;
  }

  .payment-footer-meta {
    padding: 0;
  }

  .payment-footer-actions {
    justify-content: flex-start;
  }
}
</style>
