<script setup lang="ts">
import type { OptionDto } from '#/api/inpatient-registration';
import type {
  ConfirmInpatientSettleResponse,
  InpatientPrepayRefundPlanItemDto,
  InpatientSettleChargeLineDto,
  InpatientSettleQueueItemDto,
  LoadInpatientSettleSummaryResponse,
  LoadSettledInpatientPaymentViewResponse,
  PreSettleInpatientChargesResponse,
  SettledPaymentBriefDto,
} from '#/api/inpatient-settle';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { Page } from '@zxplat/common-ui';

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
  httpRecallInpatientDischarge,
  httpConfirmInpatientSettle,
  httpDiagnoseInpatientPlatformVisit,
  httpRepairInpatientPlatformVisit,
  httpCompleteInpatientDischargePrepayRefund,
  httpLoadInpatientSettlePageInit,
  httpLoadInpatientSettleSummary,
  httpLoadSettledInpatientPaymentView,
  httpPageInpatientSettleQueue,
  httpPreSettleInpatientCharges,
  httpQueryInpatientSettleCharges,
} from '#/api/inpatient-settle';
import type { AlignInpatientPlatformVisitResponse } from '#/api/inpatient-settle';
import AppDialog from '#/components/app-dialog.vue';
import EinvoiceBillAmountMismatchBanner from '#/components/einvoice/EinvoiceBillAmountMismatchBanner.vue';
import EinvoiceIssueStatusPanel from '#/components/einvoice/EinvoiceIssueStatusPanel.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import SelectTable from '#/components/select-table.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { isExpectedEmptyResponseError } from '#/utils/http/expected-empty-response-errors';
import { formatMoneyDisplay, formatMoneyYuan } from '#/utils/format/money-scale';
import { decimalAdd, decimalDiv, decimalMul } from '#/utils/format/decimal-util';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { optionDtosToPayChannelOptions } from '#/utils/payment/payment-channel-options';
import { isScanPayChannel, useUnionPayScanPay } from '#/composables/payment/useUnionPayScanPay';
import { submitScanPayment } from '#/utils/payment/submit-scan-payment';
import { settleWithScanRecovery } from '#/utils/payment/scan-settle-recovery';
import {
  clearCachedClientRefundResult,
  submitScanRefund,
} from '#/utils/payment/submit-scan-refund';

import {
  FALLBACK_DEFAULT_PAY_CHANNEL,
  FALLBACK_PAY_CHANNEL_OPTIONS,
  INPATIENT_SETTLE_MODE,
  SETTLE_PRESENTATION_MODE,
  SETTLE_QUEUE_TAB,
} from './inpatient-settle.constants';
import {
  buildClinicalPreSettleInsuranceBreakdown,
  resolveClinicalPreSettleChannelPayable,
  resolveClinicalPreSettlePersonalCashPayable,
  resolveClinicalPreSettlePrepayOffset,
  uploadInpatientFeeDetailsWithProgress,
} from '#/chs/composables/use-inpatient-clinical-pre-settle';

import InpatientSettleDialog from './components/inpatient-settle-dialog.vue';
import {
  getInpatientContractBusNo,
  loadInpatientRegisterContractMeta,
  type InpatientRegisterContractMeta,
} from '../register/services/inpatient-register-contract-meta';
import {
  chooseInpatientSettleInsuranceMedium,
  inpatientSettleReadCredentialFailHint,
  inpatientSettleReadCredentialHint,
  inpatientSettleReadCredentialLabel,
  isInpatientInsuranceRegistration,
  isZeroAmountDischargeFullSettle,
  inpatientSettleBlockedMessage,
  resolveInpatientSettleInsuranceContext,
  resolveInpatientSettleMode,
  verifyInpatientInsuranceCard,
  type InpatientSettleInsuranceMedium,
} from './use-inpatient-settle-insurance';
import {
  INPATIENT_SETTLE_FOOTER_ACTION,
  INPATIENT_SETTLE_TOOLBAR_ACTION,
  INPATIENT_SETTLE_TOOLBAR_ACTION_LABEL,
  type InpatientSettleFooterActionCode,
  type InpatientSettleToolbarActionCode,
} from './settle-toolbar-action';
import {
  INPATIENT_SETTLE_INSURANCE_SHEET_HOSPITAL_CODE,
  INPATIENT_SETTLE_INSURANCE_SHEET_HOSPITAL_LEVEL,
  INPATIENT_SETTLE_INSURANCE_SHEET_PRINT_ENABLED,
} from '#/views/inpatient/charge/settle/inpatient-settle-print.config';
import { printInpatientInsuranceSettlement } from '#/utils/print/print-inpatient-insurance-settlement';
import { NATY_OPTIONS } from '#/views/mrms/mrms.constants';
import DetailAuditRemindDialog from '#/chs/components/detail-audit-remind-dialog.vue';
import InsuranceProgressDialog from '#/chs/components/insurance-progress-dialog.vue';
import {
  runIpDetailAuditGate,
  type DetailAuditRemindDialogExpose,
} from '#/chs/composables/use-detail-audit-gate';
import { useInsuranceProgressDialog } from '#/chs/composables/use-insurance-progress-dialog';

defineOptions({
  name: 'ChargeIpSettle',
});

const detailAuditDialogRef = ref<DetailAuditRemindDialogExpose | null>(null);
const {
  activeIndex: settleProgressActiveIndex,
  countCurrent: settleProgressCountCurrent,
  countLabelText: settleProgressCountLabelText,
  countTotal: settleProgressCountTotal,
  detail: settleProgressDetail,
  progress: settleProgress,
  steps: settleProgressSteps,
  title: settleProgressTitle,
  visible: settleProgressVisible,
} = useInsuranceProgressDialog();

/** 第二阶段原路退半成功上下文（关弹窗后仍可重试） */
const pendingPrepayRefundContext = ref<{
  confirmRes: ConfirmInpatientSettleResponse;
  encounterId: string;
  patientId: string;
  payAmount: number;
  payChannelCode: string;
  plan: InpatientPrepayRefundPlanItemDto[];
} | null>(null);

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
  /** 医保基金分摊（对齐门诊「本次谁付的」） */
  payCompositionRows: Array<{ key: string; label: string; value: string }>;
  paymentId: string;
  paymentMethod: string;
  recallEnabled?: boolean;
  recordType?: string;
  reimbursementAmount: string;
  reverseDisabledReason?: string;
  reverseEnabled?: boolean;
  selfPayAmount: string;
  settleTime: string;
  settleType: string;
  settlementNo: string;
  status: string;
  statusCode: SettlementHistoryStatusCode;
  ticketNo: string;
  totalAmount: string;
}

type SettlementHistoryStatusCode = 'REVERSED' | 'REVERSE_RECORD' | 'SETTLED';

const SETTLEMENT_HISTORY_STATUS_LABEL: Record<SettlementHistoryStatusCode, string> = {
  SETTLED: '已结清',
  REVERSED: '已反结',
  REVERSE_RECORD: '反结',
};

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
  settlementStatusCode: 'PENDING' | 'SETTLED';
  settleDate: string;
  selfPayAmount: string;
  totalFee: string;
  wardName: string;
}

const paymentChannelCode = ref(FALLBACK_DEFAULT_PAY_CHANNEL);
/** 医保结算凭证：身份证 / 社保卡 / 医保码（对齐门诊收费） */
const insuranceMedium = ref<InpatientSettleInsuranceMedium>('yb-card');
const insuranceMediumOptions: { label: string; value: InpatientSettleInsuranceMedium }[] = [
  { label: '身份证', value: 'id' },
  { label: '社保卡', value: 'yb-card' },
  { label: '医保码', value: 'yb-ec' },
];
const contractMeta = ref<InpatientRegisterContractMeta>({
  contractCategoryById: {},
  contractInsuranceProfileById: {},
});
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

const activeTab = ref('category-summary');
const queueRecords = ref<InpatientSettleQueueItemDto[]>([]);
const queueRowByEncounterId = ref<Record<string, InpatientSettleQueueItemDto>>({});
const summary = ref<LoadInpatientSettleSummaryResponse | null>(null);
const chargeLines = ref<InpatientSettleChargeLineDto[]>([]);
const settlementHistoryRows = ref<SettlementHistoryRow[]>([]);
/** 已结算页签：当前选中历史结算单 paymentId（驱动左侧费用快照回显） */
const selectedSettledPaymentId = ref('');
const settlementHistoryTableRef = ref<InstanceType<typeof ElTable> | null>(null);
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

const {
  scanPayEnabled,
  hasScanPayChannel,
  effectivePayChannelOptions,
  refreshUnionPayCapabilities,
} = useUnionPayScanPay(() => paymentChannelSelectOptions.value);
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
  settlementStatusCode: 'PENDING',
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
  return `¥ ${formatMoneyDisplay(n)}`;
}

/** 历史结算表格金额：数值后拼「元」 */
function formatHistoryAmountDisplay(value: null | number | string | undefined) {
  const n = Number(value ?? 0);
  if (!Number.isFinite(n)) {
    return '—';
  }
  return formatMoneyYuan(n);
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

/**
 * 摘要/预结「应付 / 原路退」卡：认 C 与应退，禁止 balanceHint。
 */
function formatDepositSettlePayableCardHint(source: {
  channelPayable?: null | number | string;
  depositOffsetAmount?: null | number | string;
  prepayFullRefund?: boolean | null;
  prepayRefundAmount?: null | number | string;
}): string {
  const parts: string[] = [];
  const channel = Number(source.channelPayable ?? NaN);
  if (Number.isFinite(channel) && channel > 0) {
    parts.push(`应付 ¥ ${formatMoneyDisplay(channel)}`);
  }
  const refund = Number(source.prepayRefundAmount ?? NaN);
  if (Number.isFinite(refund) && refund > 0) {
    const full = source.prepayFullRefund === true;
    parts.push(
      full
        ? `全额退现金 ¥ ${formatMoneyDisplay(refund)}`
        : `退现金 ¥ ${formatMoneyDisplay(refund)}`,
    );
  }
  if (parts.length > 0) {
    return parts.join(' · ');
  }
  const offset = Number(source.depositOffsetAmount ?? 0);
  if (Number.isFinite(offset) && offset > 0) {
    return '预交金冲抵，无需收退';
  }
  if (Number.isFinite(channel) && channel === 0) {
    return '无需收退';
  }
  return '—';
}

/** 预交押金：D≥0 展示金额，禁止正余额显示「—」。 */
function formatPrepayDepositDisplay(value: null | number | string | undefined) {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return '—';
  }
  return `¥ ${formatMoneyDisplay(n)}`;
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
    insuranceFlag: line.chrgitmLvText?.trim() || '—',
    selected: Boolean(line.selected),
  };
}

function resolveActiveSettledPaymentBrief(
  view: LoadSettledInpatientPaymentViewResponse | null | undefined,
) {
  if (!view?.activePaymentId) {
    return undefined;
  }
  const activeId = String(view.activePaymentId);
  return view.payments?.find((payment) => String(payment.paymentId) === activeId);
}

/** 大类医保内/自费分摊：用结算回执符合范围、全自费+先行自付按大类金额占比拆分 */
interface CategoryInsuranceSplitSource {
  fulamtOwnpayAmt?: null | number | string;
  inscpScpAmt?: null | number | string;
  medfeeSumamt?: null | number | string;
  preselfpayAmt?: null | number | string;
}

/** 大类汇总转表格展示结构 */
function mapCategoryRows(
  categories: {
    amount: number | string;
    categoryCode?: string;
    categoryDisplay?: string;
    insuranceAmount?: null | number | string;
    lineCount: number;
    selfAmount?: null | number | string;
  }[],
  pendingTotal: number | string,
  insuranceSplit?: CategoryInsuranceSplitSource | null,
): FeeCategoryRow[] {
  const total = Number(pendingTotal ?? 0);
  const medfee = Number(insuranceSplit?.medfeeSumamt ?? 0);
  const inscp = Number(insuranceSplit?.inscpScpAmt ?? 0);
  const ownSelf = decimalAdd(
    Number(insuranceSplit?.fulamtOwnpayAmt ?? 0),
    Number(insuranceSplit?.preselfpayAmt ?? 0),
  );
  const canSplit = Number.isFinite(medfee) && medfee > 0.000_001;
  return categories.map((row) => {
    const amount = Number(row.amount ?? 0);
    // 占总费用比：大类费用金额 / 住院总费用（非大类内医保/自费比）
    const ratio =
      total > 0 && Number.isFinite(amount) ? `${((amount / total) * 100).toFixed(2)}%` : '—';
    let insuranceRaw: null | number | string | undefined = row.insuranceAmount;
    let selfRaw: null | number | string | undefined = row.selfAmount;
    // 接口未给行级分摊时：按医疗费占比拆「符合范围 / 全自费+先行自付」（对齐结算图一口径）
    if (
      canSplit &&
      Number.isFinite(amount) &&
      (insuranceRaw == null || insuranceRaw === '') &&
      (selfRaw == null || selfRaw === '')
    ) {
      const share = decimalDiv(amount, medfee, 8);
      insuranceRaw = decimalMul(share, inscp, 2);
      selfRaw = decimalMul(share, ownSelf, 2);
    }
    const hasInsuranceSplit =
      insuranceRaw != null && insuranceRaw !== '' && Number.isFinite(Number(insuranceRaw));
    const hasSelfSplit = selfRaw != null && selfRaw !== '' && Number.isFinite(Number(selfRaw));
    return {
      category: row.categoryDisplay?.trim() || row.categoryCode || '—',
      amount: formatCurrencyDisplay(row.amount),
      insuranceAmount: hasInsuranceSplit ? formatCurrencyDisplay(insuranceRaw) : '—',
      selfAmount: hasSelfSplit ? formatCurrencyDisplay(selfRaw) : '—',
      ratio,
    };
  });
}

/** 从已结算支付摘要取大类分摊基数（图一：符合范围 / 全自费 / 先行自付） */
function resolveCategoryInsuranceSplitFromBrief(
  payment: SettledPaymentBriefDto | null | undefined,
): CategoryInsuranceSplitSource | null {
  if (!payment?.insuranceSettled) {
    return null;
  }
  return {
    medfeeSumamt: payment.ybMedfeeSumamt,
    inscpScpAmt: payment.ybInscpScpAmt,
    fulamtOwnpayAmt: payment.ybFulamtOwnpayAmt,
    preselfpayAmt: payment.ybPreselfpayAmt,
  };
}

/** 全部待结算明细 ID */
const allPendingChargeItemIds = computed(() => chargeLines.value.map((line) => line.chargeItemId));

/** 当前结算模式（出院全结 / 中途结算） */
const currentSettleMode = computed(() => {
  const snapMode = preSettleSnapshot.value?.settleMode;
  if (snapMode) {
    return snapMode;
  }
  const settledModeCode = resolveActiveSettledPaymentBrief(settledPaymentView.value)?.settleMode;
  if (settledModeCode) {
    return settledModeCode;
  }
  const status = summary.value?.statusEnum ?? currentQueueRow.value?.statusEnum;
  return resolveInpatientSettleMode(
    status,
    selectedChargeItemIds.value,
    allPendingChargeItemIds.value,
  );
});

const currentSettleModeLabel = computed(() => {
  const mode = currentSettleMode.value;
  if (mode === INPATIENT_SETTLE_MODE.DISCHARGE_FULL) {
    return '出院全结';
  }
  if (mode === INPATIENT_SETTLE_MODE.MID_SETTLE) {
    return '中途结算';
  }
  return '—';
});

/** 结算按钮禁用原因（待出院 / 未护士出院 / 未全选） */
const settleActionBlockedMessage = computed(() => {
  const status = summary.value?.statusEnum ?? currentQueueRow.value?.statusEnum;
  return inpatientSettleBlockedMessage(
    status,
    selectedChargeItemIds.value,
    allPendingChargeItemIds.value,
  );
});

const settleActionDisabled = computed(() => Boolean(settleActionBlockedMessage.value));

const isInsuranceRegistration = computed(() =>
  isInpatientInsuranceRegistration(currentQueueRow.value, contractMeta.value.contractCategoryById),
);

/** 解析本次渠道实付 C；预结算 displayAmount 优先，摘要回落 channelPayable。 */
function resolveChannelPayableAmount() {
  const snap = preSettleSnapshot.value;
  if (snap) {
    return resolveClinicalPreSettleChannelPayable(snap);
  }
  const fromSummary = Number(summary.value?.channelPayable ?? NaN);
  if (Number.isFinite(fromSummary) && fromSummary >= 0) {
    return fromSummary;
  }
  return 0;
}

/** 本次预交金冲抵额 O（自费=医保同一套）。 */
const settlePrepayOffsetAmount = computed(() =>
  resolveClinicalPreSettlePrepayOffset(preSettleSnapshot.value),
);

/** 患者渠道实付：预结算后以 displayAmount=C 为准；摘要用 channelPayable。 */
const payableAmount = computed(() => resolveChannelPayableAmount());

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
  // 对齐门诊确认收费：统筹/其他/个账/起付线/公务员/现金 + 预交金冲抵
  return buildClinicalPreSettleInsuranceBreakdown(preSettleSnapshot.value).filter((item) => {
    const n = Number(item.value ?? 0);
    return Number.isFinite(n) && Math.abs(n) >= 0.000_001;
  });
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
  // 勾稽对照：费用行 vs 渠道实付 C（非个人现金 P、非旧 balanceHint）
  let paymentAmount: null | number | string | undefined = summary.value?.channelPayable;
  const snap = preSettleSnapshot.value;
  if (snap?.displayAmount != null) {
    paymentAmount = snap.displayAmount;
  } else if (snap?.receivableTotal != null && Number(snap.displayAmount ?? 0) === 0) {
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
    // 主治医生：队列/摘要同源 attendingDoctorName（后端展示参与者口径）
    doctorName: (s?.attendingDoctorName ?? row?.attendingDoctorName ?? '').trim() || '—',
    totalFee: settledMode
      ? formatCurrencyDisplay(
          (() => {
            const brief = resolveActiveSettledPaymentBrief(settled);
            const medfee = Number(brief?.ybMedfeeSumamt ?? 0);
            // 已结算医保：总费用对齐图一医疗费总额，避免误用个人现金 display
            if (brief?.insuranceSettled && Number.isFinite(medfee) && medfee > 0) {
              return brief.ybMedfeeSumamt;
            }
            if (Number(settled.receivableTotal ?? 0) > 0) {
              return settled.receivableTotal;
            }
            return brief?.ybMedfeeSumamt ?? settled.receivableTotal;
          })(),
        )
      : formatCurrencyDisplay(s?.pendingSettlementTotal),
    reimbursementAmount:
      insuranceReimbursementAmount.value != null
        ? formatCurrencyDisplay(insuranceReimbursementAmount.value)
        : preSettleSnapshot.value?.insurancePreSettled
          ? formatCurrencyDisplay(
              Number(preSettleSnapshot.value.ybFundPaySumamt ?? 0) +
                Number(preSettleSnapshot.value.ybAcctPay ?? 0),
            )
          : settledMode && resolveActiveSettledPaymentBrief(settled)?.insuranceSettled
            ? formatCurrencyDisplay(
                Number(resolveActiveSettledPaymentBrief(settled)?.ybFundPaySumamt ?? 0) +
                  Number(resolveActiveSettledPaymentBrief(settled)?.ybAcctPay ?? 0),
              )
            : '—',
    selfPayAmount: (() => {
      // 已结算：个人现金认 P（医保优先 ybPsnCashPay，否则 personalCashPayable/signed）
      if (settledMode) {
        const brief = resolveActiveSettledPaymentBrief(settled);
        if (brief?.insuranceSettled) {
          return formatCurrencyDisplay(brief.ybPsnCashPay);
        }
        if (brief?.personalCashPayable != null && brief.personalCashPayable !== '') {
          return formatCurrencyDisplay(brief.personalCashPayable);
        }
        return formatCurrencyDisplay(settled.receivableTotal);
      }
      // 预结算后：个人自费金额 = P（非渠道实付 C）
      const personalFromSnap = resolveClinicalPreSettlePersonalCashPayable(preSettleSnapshot.value);
      if (personalFromSnap != null) {
        return formatCurrencyDisplay(personalFromSnap);
      }
      if (isInsuranceRegistration.value) {
        return '—';
      }
      if (s?.personalCashPayable != null && s.personalCashPayable !== '') {
        return formatCurrencyDisplay(s.personalCashPayable);
      }
      return formatCurrencyDisplay(s?.selectedTotal);
    })(),
    prepayTotal: settledMode ? '—' : formatPrepayDepositDisplay(s?.prepayNetBalance),
    refundOrSupplement: (() => {
      if (settledMode) {
        const brief = resolveActiveSettledPaymentBrief(settled);
        if (brief?.insuranceSettled) {
          return `个人现金应付 ¥ ${formatMoneyDisplay(brief.ybPsnCashPay)}`;
        }
        return formatDepositSettlePayableCardHint({
          channelPayable: brief?.channelPayable,
          depositOffsetAmount: brief?.depositOffsetAmount,
        });
      }
      const snap = preSettleSnapshot.value;
      if (snap) {
        return formatDepositSettlePayableCardHint({
          channelPayable: snap.displayAmount,
          depositOffsetAmount: snap.depositOffsetAmount,
          prepayFullRefund: snap.prepayFullRefund,
          prepayRefundAmount: snap.prepayRefundAmount,
        });
      }
      if (isInsuranceRegistration.value) {
        return '—';
      }
      return formatDepositSettlePayableCardHint({
        channelPayable: s?.channelPayable,
        depositOffsetAmount: s?.depositOffsetAmount,
        prepayFullRefund: s?.prepayFullRefund,
        prepayRefundAmount: s?.prepayRefundAmount,
      });
    })(),
    accountBalanceSummary: settledMode
      ? '—'
      : isInsuranceRegistration.value && !preSettleSnapshot.value?.insurancePreSettled
        ? formatPrepayDepositDisplay(s?.prepayNetBalance)
        : (s?.accountBalanceSummaryText ?? '—'),
    accountBalanceSub: settledMode
      ? undefined
      : isInsuranceRegistration.value && !preSettleSnapshot.value?.insurancePreSettled
        ? '医保预结算后按冲抵/补收/原路退展示'
        : s?.accountBalanceSubText,
    insuranceInFee: (() => {
      if (settledMode) {
        const brief = resolveActiveSettledPaymentBrief(settled);
        if (brief?.insuranceSettled && brief.ybInscpScpAmt != null) {
          return formatCurrencyDisplay(brief.ybInscpScpAmt);
        }
      }
      if (
        preSettleSnapshot.value?.insurancePreSettled &&
        preSettleSnapshot.value.ybInscpScpAmt != null
      ) {
        return formatCurrencyDisplay(preSettleSnapshot.value.ybInscpScpAmt);
      }
      return '—';
    })(),
    insuranceOutFee: (() => {
      if (settledMode) {
        const brief = resolveActiveSettledPaymentBrief(settled);
        if (brief?.insuranceSettled && brief.ybFulamtOwnpayAmt != null) {
          return formatCurrencyDisplay(brief.ybFulamtOwnpayAmt);
        }
      }
      if (
        preSettleSnapshot.value?.insurancePreSettled &&
        preSettleSnapshot.value.ybFulamtOwnpayAmt != null
      ) {
        return formatCurrencyDisplay(preSettleSnapshot.value.ybFulamtOwnpayAmt);
      }
      return '—';
    })(),
    settlementNo: settledMode
      ? (resolveActiveSettledPaymentBrief(settled)?.paymentNo ??
        resolveActiveSettledPaymentBrief(settled)?.paymentId ??
        '—')
      : (preSettleSnapshot.value?.paymentDraftId ?? '—'),
    settlementStatus: settledMode ? '已结清' : '待结算',
    settlementStatusCode: settledMode ? 'SETTLED' : 'PENDING',
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
    { label: '应付个人现金 / 预交金原路退', value: currentPatientDisplay.value.refundOrSupplement },
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
      { field: '基金支付合计', value: formatCurrencyDisplay(snap.ybFundPaySumamt) },
      { field: '基本医保统筹', value: formatCurrencyDisplay(snap.ybHifpPay) },
      { field: '其他支出', value: formatCurrencyDisplay(snap.ybOthPay) },
      { field: '个人账户支付', value: formatCurrencyDisplay(snap.ybAcctPay) },
      { field: '起付线', value: formatCurrencyDisplay(snap.ybActPayDedc) },
      { field: '公务员医疗补助', value: formatCurrencyDisplay(snap.ybCvlservPay) },
      { field: '个人现金支付', value: formatCurrencyDisplay(snap.ybPsnCashPay) },
      { field: '预结算快照 ID', value: snap.ybPreSettleId ?? '—' },
    ];
  }
  const settledBrief = resolveActiveSettledPaymentBrief(settledPaymentView.value);
  if (isSettledQueueTab.value && settledBrief?.insuranceSettled) {
    return [
      { field: '结算模式', value: currentSettleModeLabel.value },
      { field: '医疗费总额', value: formatCurrencyDisplay(settledBrief.ybMedfeeSumamt) },
      { field: '基金支付合计', value: formatCurrencyDisplay(settledBrief.ybFundPaySumamt) },
      { field: '基本医保统筹', value: formatCurrencyDisplay(settledBrief.ybHifpPay) },
      { field: '公务员医疗补助', value: formatCurrencyDisplay(settledBrief.ybCvlservPay) },
      { field: '其他支出', value: formatCurrencyDisplay(settledBrief.ybOthPay) },
      { field: '个人账户支付', value: formatCurrencyDisplay(settledBrief.ybAcctPay) },
      { field: '个人现金支付', value: formatCurrencyDisplay(settledBrief.ybPsnCashPay) },
      { field: '全自费', value: formatCurrencyDisplay(settledBrief.ybFulamtOwnpayAmt) },
      { field: '先行自付', value: formatCurrencyDisplay(settledBrief.ybPreselfpayAmt) },
      { field: '符合范围金额', value: formatCurrencyDisplay(settledBrief.ybInscpScpAmt) },
      { field: '起付线', value: formatCurrencyDisplay(settledBrief.ybActPayDedc) },
    ];
  }
  return [
    { field: '结算模式', value: currentSettleModeLabel.value },
    { field: '医保内费用', value: currentPatientDisplay.value.insuranceInFee },
    { field: '医保外自费项目合计', value: currentPatientDisplay.value.insuranceOutFee },
    { field: '费用性质', value: currentPatientDisplay.value.insuranceType },
  ];
});

/** 解析底部支付方式渠道 code */
function resolvePayChannelCode() {
  return paymentChannelCode.value || FALLBACK_DEFAULT_PAY_CHANNEL;
}

/** 加载页初始化（支付渠道、队列页签等） */
async function loadPageInit() {
  try {
    const init = await httpLoadInpatientSettlePageInit();
    settleQueueTabOptions.value = init.settleQueueTabOptions ?? [];
    settleQueueTab.value = init.defaultSettleQueueTab || SETTLE_QUEUE_TAB.PENDING_SETTLE;
    paymentChannelSelectOptions.value = optionDtosToPayChannelOptions(init.paymentChannelOptions);
    if (paymentChannelSelectOptions.value.length > 0) {
      paymentChannelCode.value =
        paymentChannelSelectOptions.value[0]?.value ?? FALLBACK_DEFAULT_PAY_CHANNEL;
    }
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
  selectedSettledPaymentId.value = '';
  preSettleSnapshot.value = null;
}

/** 已结算队列按住院号去重（保留最近结算时间较新的一行） */
function dedupeSettledQueueByEncounterBusNo(rows: InpatientSettleQueueItemDto[]) {
  const byBusNo = new Map<string, InpatientSettleQueueItemDto>();
  for (const row of rows) {
    const key = (row.encounterBusNo ?? row.encounterId ?? '').trim();
    if (!key) {
      continue;
    }
    const existing = byBusNo.get(key);
    if (!existing) {
      byBusNo.set(key, row);
      continue;
    }
    const candidateTime = row.lastSettleTime ? dayjs(row.lastSettleTime).valueOf() : 0;
    const incumbentTime = existing.lastSettleTime ? dayjs(existing.lastSettleTime).valueOf() : 0;
    if (candidateTime > incumbentTime) {
      byBusNo.set(key, row);
      continue;
    }
    if (candidateTime === incumbentTime && String(row.encounterId) > String(existing.encounterId)) {
      byBusNo.set(key, row);
    }
  }
  return [...byBusNo.values()];
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
  let content = parsed.content;
  if (isSettledQueueTab.value) {
    content = dedupeSettledQueueByEncounterBusNo(content);
  }
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

/** 清空历史结算区（尚无流水时的正常空态） */
function clearSettlementHistoryDisplay() {
  settlementHistoryRows.value = [];
  settlementHistoryNetTotal.value = null;
}

/** 加载住院结算/反结历史（IF-IP-STL-07；待结算与已结算页签共用） */
async function loadSettlementHistory(ids?: { encounterId: string; patientId: string }) {
  const encounterId = preserveApiIdForRequest(ids?.encounterId ?? selectedEncounterId.value);
  const patientId = preserveApiIdForRequest(ids?.patientId ?? currentQueueRow.value?.patientId);
  if (!encounterId || !patientId) {
    clearSettlementHistoryDisplay();
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
  } catch (error) {
    if (isExpectedEmptyResponseError(error)) {
      clearSettlementHistoryDisplay();
      return;
    }
    throw error;
  }
}

/** 加载已结算单查阅（IF-IP-STL-07；仅已结算页签） */
async function loadSettledWorkspace(
  ids?: { encounterId: string; patientId: string },
  paymentId?: string,
) {
  const encounterId = preserveApiIdForRequest(ids?.encounterId ?? selectedEncounterId.value);
  const patientId = preserveApiIdForRequest(ids?.patientId ?? currentQueueRow.value?.patientId);
  if (!encounterId || !patientId) {
    settledPaymentView.value = null;
    chargeLines.value = [];
    categoryDisplayRows.value = [];
    settlementHistoryRows.value = [];
    selectedSettledPaymentId.value = '';
    return;
  }
  const resolvedPaymentId = preserveApiIdForRequest(paymentId ?? selectedSettledPaymentId.value);
  const requestBase = {
    encounterId,
    patientId,
    ...(resolvedPaymentId ? { paymentId: resolvedPaymentId } : {}),
  };
  try {
    const detailRes = await httpLoadSettledInpatientPaymentView({
      ...requestBase,
      presentationMode: SETTLE_PRESENTATION_MODE.DETAIL,
    });
    settledPaymentView.value = detailRes;
    selectedSettledPaymentId.value = detailRes.activePaymentId
      ? String(detailRes.activePaymentId)
      : '';
    summary.value = null;
    chargeLines.value = detailRes.lines ?? [];
    const summaryRes = await httpLoadSettledInpatientPaymentView({
      ...requestBase,
      presentationMode: SETTLE_PRESENTATION_MODE.SUMMARY,
    });
    categoryDisplayRows.value = mapCategoryRows(
      summaryRes.categories ?? [],
      detailRes.receivableTotal ?? summaryRes.receivableTotal ?? 0,
      resolveCategoryInsuranceSplitFromBrief(resolveActiveSettledPaymentBrief(detailRes)),
    );
    settlementHistoryRows.value = (detailRes.payments ?? []).map((p) =>
      mapPaymentToHistoryRow(p, detailRes),
    );
    settlementHistoryNetTotal.value = detailRes.netSettleTotal ?? null;
    await syncSettlementHistoryTableCurrentRow();
  } catch (error) {
    if (isExpectedEmptyResponseError(error)) {
      settledPaymentView.value = null;
      chargeLines.value = [];
      categoryDisplayRows.value = [];
      selectedSettledPaymentId.value = '';
      clearSettlementHistoryDisplay();
      return;
    }
    throw error;
  }
}

/** 历史表高亮行与 activePaymentId 对齐 */
async function syncSettlementHistoryTableCurrentRow() {
  await nextTick();
  const table = settlementHistoryTableRef.value;
  if (!table || !selectedSettledPaymentId.value) {
    return;
  }
  const currentRow = settlementHistoryRows.value.find(
    (row) => String(row.paymentId) === selectedSettledPaymentId.value,
  );
  if (currentRow) {
    table.setCurrentRow(currentRow);
  }
}

/** 选中历史结算行：按该笔 paymentId 回显左侧费用快照 */
async function handleSettlementHistoryRowSelect(row: SettlementHistoryRow | null | undefined) {
  if (!isSettledQueueTab.value || !row?.paymentId) {
    return;
  }
  const paymentId = preserveApiIdForRequest(row.paymentId);
  if (!paymentId || paymentId === selectedSettledPaymentId.value) {
    return;
  }
  workspaceLoading.value = true;
  try {
    await loadSettledWorkspace(undefined, paymentId);
  } finally {
    workspaceLoading.value = false;
  }
}

/** 待结算页签工作区：费用 + 摘要 + 历史流水只读 */
async function loadPendingWorkspace() {
  settledPaymentView.value = null;
  await loadPendingCharges();
  await loadSettlementHistory();
}

function resolveSettlementHistoryStatusCode(
  payment: SettledPaymentBriefDto,
): SettlementHistoryStatusCode {
  const isReverse = payment.recordType === 'REVERSE';
  if (payment.statusEnum === 'REFUNDED') {
    return isReverse ? 'REVERSE_RECORD' : 'REVERSED';
  }
  return 'SETTLED';
}

/** 从已结算支付摘要组装「本次谁付的」分项（非零才展示） */
function buildPayCompositionFromBrief(
  payment: SettledPaymentBriefDto,
): Array<{ key: string; label: string; value: string }> {
  const rows: Array<{ key: string; label: string; value: string }> = [];
  const pushIf = (key: string, label: string, raw: null | number | string | undefined) => {
    const n = Number(raw ?? 0);
    if (!Number.isFinite(n) || Math.abs(n) < 0.000_001) {
      return;
    }
    rows.push({ key, label, value: formatHistoryAmountDisplay(n) });
  };
  if (payment.insuranceSettled) {
    pushIf('hifpPay', '基本医保统筹', payment.ybHifpPay);
    pushIf('othPay', '其他支出', payment.ybOthPay);
    pushIf('cvlservPay', '公务员医疗补助', payment.ybCvlservPay);
    pushIf('actPayDedc', '起付线', payment.ybActPayDedc);
    pushIf('acctPay', '个人账户', payment.ybAcctPay);
    pushIf('psnCashPay', '个人现金', payment.ybPsnCashPay);
    pushIf('fundPaySumamt', '基金支付合计', payment.ybFundPaySumamt);
  } else {
    pushIf('personalCash', '个人现金应付', payment.personalCashPayable ?? payment.signedAmount);
  }
  pushIf('depositOffset', '预交金冲抵', payment.depositOffsetAmount);
  pushIf('channelPayable', '渠道实收', payment.channelPayable);
  return rows;
}

/** 支付流水转历史结算表格行 */
function mapPaymentToHistoryRow(
  payment: SettledPaymentBriefDto,
  view: { operatorName?: string },
): SettlementHistoryRow {
  const signedAmount = payment.signedAmount ?? payment.receivableTotal;
  const signedNum = Number(signedAmount ?? 0);
  const medfee = Number(payment.ybMedfeeSumamt ?? 0);
  // 医保结算：结算金额优先医疗费总额（图一口径），避免 display=0 显示成 0.00
  const totalSource =
    payment.insuranceSettled && Number.isFinite(medfee) && medfee > 0
      ? medfee
      : Number.isFinite(signedNum) && Math.abs(signedNum) >= 0.000_001
        ? signedAmount
        : Number.isFinite(medfee) && medfee > 0
          ? medfee
          : signedAmount;
  const isReverse = payment.recordType === 'REVERSE';
  const statusCode = resolveSettlementHistoryStatusCode(payment);
  const fund = Number(payment.ybFundPaySumamt ?? 0);
  const acct = Number(payment.ybAcctPay ?? 0);
  const cash = Number(payment.ybPsnCashPay ?? 0);
  return {
    paymentId: payment.paymentId,
    settlementNo: payment.paymentNo ?? payment.paymentId,
    settleTime: payment.settleTime ? formatHistorySettleTimeDisplay(payment.settleTime) : '—',
    settleType: isReverse ? '反结' : '结算',
    totalAmount: formatHistoryAmountDisplay(totalSource),
    reimbursementAmount: payment.insuranceSettled ? formatHistoryAmountDisplay(fund + acct) : '—',
    selfPayAmount: payment.insuranceSettled
      ? formatHistoryAmountDisplay(cash)
      : formatHistoryAmountDisplay(payment.personalCashPayable ?? signedAmount),
    paymentMethod: payment.payMethodText ?? '—',
    operatorName: view.operatorName ?? '—',
    ticketNo: '—',
    status: SETTLEMENT_HISTORY_STATUS_LABEL[statusCode],
    statusCode,
    recordType: payment.recordType,
    reverseEnabled: payment.reverseEnabled,
    reverseDisabledReason: payment.reverseDisabledReason,
    recallEnabled: payment.recallEnabled,
    payCompositionRows: buildPayCompositionFromBrief(payment),
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
async function handleToolbarAction(actionCode: InpatientSettleToolbarActionCode) {
  if (actionCode === INPATIENT_SETTLE_TOOLBAR_ACTION.REFRESH_FEES) {
    void reloadWorkspace().then(() => ElMessage.success('费用已刷新'));
    return;
  }
  if (actionCode === INPATIENT_SETTLE_TOOLBAR_ACTION.VERIFY_INSURANCE) {
    const contractId = preserveApiIdForRequest(currentQueueRow.value?.contractId);
    if (!contractId) {
      ElMessage.warning('当前患者非医保登记或缺少合同信息');
      return;
    }
    void verifyInpatientInsuranceCard(contractId);
    return;
  }
  if (actionCode === INPATIENT_SETTLE_TOOLBAR_ACTION.ALIGN_PLATFORM_VISIT) {
    await handleAlignPlatformVisit();
    return;
  }
  if (actionCode === INPATIENT_SETTLE_TOOLBAR_ACTION.PRINT_INSURANCE_SETTLEMENT) {
    await handlePrintInsuranceSettlement();
    return;
  }
  ElMessage.success(`${INPATIENT_SETTLE_TOOLBAR_ACTION_LABEL[actionCode] ?? actionCode}已触发`);
}

/** 民族编码 → 票面文案（病案 NATY + 常见国标码兜底） */
function resolveNationText(nationalityCode: null | string | undefined): string {
  const code = String(nationalityCode ?? '').trim();
  if (!code) {
    return '';
  }
  const fromOptions = NATY_OPTIONS.find((opt) => String(opt.value) === code)?.label?.trim();
  if (fromOptions) {
    return fromOptions;
  }
  const fallback: Record<string, string> = {
    '01': '汉族',
    '02': '蒙古族',
    '03': '回族',
    '04': '藏族',
    '05': '维吾尔族',
    '06': '苗族',
    '07': '彝族',
    '08': '壮族',
    '09': '布依族',
    '10': '朝鲜族',
    '11': '满族',
    '99': '其他',
  };
  return fallback[code] || code;
}

/** 已结算医保单：打印医保结算单（版式走库模板） */
async function handlePrintInsuranceSettlement() {
  if (!INPATIENT_SETTLE_INSURANCE_SHEET_PRINT_ENABLED) {
    return;
  }
  if (!isSettledQueueTab.value) {
    ElMessage.warning('请切换到已结算页签后再打印医保结算单');
    return;
  }
  // Pre：再拉一次已结算查阅，带上票面基础信息（身份证/诊断/出院等）
  try {
    await reloadWorkspace();
  } catch {
    // 刷新失败仍尝试用当前缓存打印
  }
  const brief = resolveActiveSettledPaymentBrief(settledPaymentView.value);
  if (!brief?.insuranceSettled) {
    ElMessage.warning('当前结算单未关联医保回执，无法打印医保结算单');
    return;
  }
  const display = currentPatientDisplay.value;
  const view = settledPaymentView.value;
  const admissionDateText =
    view?.admissionDate || (display.admissionDate === '—' ? '' : display.admissionDate);
  await printInpatientInsuranceSettlement({
    encounterId: display.encounterId,
    paymentId: brief.paymentId,
    source: {
      patientName: display.name === '—' ? '' : display.name,
      genderText: display.gender === '—' ? '' : display.gender,
      ageText: display.age === '—' ? '' : display.age,
      nationText: resolveNationText(view?.nationalityCode),
      idCardNo: view?.idCardNo ?? '',
      inpatientNo: display.inpatientNo === '—' ? '' : display.inpatientNo,
      admissionDateText: admissionDateText || '',
      dischargeDateText: view?.dischargeDate ?? '',
      dischargeDeptText: display.department === '—' ? '' : display.department,
      doctorName: display.doctorName === '—' ? '' : display.doctorName,
      doctorCode: view?.doctorCode ?? '',
      stayDaysText: view?.stayDaysText ?? '',
      visitCountText: view?.visitCountText ?? '',
      admissionDiagnosisText: view?.admissionDiagnosisText ?? '',
      dischargeDiagnosisText: view?.dischargeDiagnosisText ?? '',
      employerName: view?.employerName ?? '',
      hospitalLevelText: INPATIENT_SETTLE_INSURANCE_SHEET_HOSPITAL_LEVEL,
      hospitalCode: INPATIENT_SETTLE_INSURANCE_SHEET_HOSPITAL_CODE,
      operatorName: view?.operatorName ?? '',
      settleDateText: brief.ybSetlTime || brief.settleTime || '',
      processDateText: brief.ybSetlTime || brief.settleTime || '',
      psnNo: brief.ybPsnNo ?? '',
      medType: brief.ybMedType ?? undefined,
      insutype: brief.ybInsutype ?? undefined,
      psnType: brief.ybPsnType ?? undefined,
      insuplcAdmdvs: brief.ybClrOptins ?? '',
      medfeeSumamt: brief.ybMedfeeSumamt,
      inscpScpAmt: brief.ybInscpScpAmt,
      actPayDedc: brief.ybActPayDedc,
      preselfpayAmt: brief.ybPreselfpayAmt,
      fulamtOwnpayAmt: brief.ybFulamtOwnpayAmt,
      overlmtSelfpay: brief.ybOverlmtSelfpay,
      poolPropSelfpay: brief.ybPoolPropSelfpay,
      hifpPay: brief.ybHifpPay,
      mafPay: brief.ybMafPay,
      hifobPay: brief.ybHifobPay,
      hifesPay: brief.ybHifesPay,
      hifmiPay: brief.ybHifmiPay,
      acctPay: brief.ybAcctPay,
      cvlservPay: brief.ybCvlservPay,
      psnCashPay: brief.ybPsnCashPay,
      psnPartAmt: brief.ybPsnPartAmt,
      fundPaySumamt: brief.ybFundPaySumamt,
      othPay: brief.ybOthPay,
      acctMulaidPay: brief.ybAcctMulaidPay,
      balc: brief.ybBalc,
      patientSignText: '',
    },
  });
}

/** 平台在院对齐：先诊断，必要时一键修复 */
async function handleAlignPlatformVisit() {
  if (!isInsuranceRegistration.value) {
    ElMessage.warning('当前非医保登记患者');
    return;
  }
  const { encounterId, patientId } = resolveActiveSettlePatientIds();
  const contractId = preserveApiIdForRequest(currentQueueRow.value?.contractId);
  if (!encounterId || !patientId || !contractId) {
    ElMessage.warning('请先选择医保患者');
    return;
  }
  let medium = insuranceMedium.value;
  if (!medium) {
    const chosen = await chooseInpatientSettleInsuranceMedium();
    if (!chosen) {
      return;
    }
    medium = chosen;
    insuranceMedium.value = chosen;
  }
  try {
    const insuranceCtx = await resolveInpatientSettleInsuranceContext(contractId, medium);
    if (!insuranceCtx?.psnNo) {
      ElMessage.warning(inpatientSettleReadCredentialFailHint(medium));
      return;
    }
    const payload = {
      encounterId,
      patientId,
      contractId,
      psnNo: insuranceCtx.psnNo,
      insuplcAdmdvs: insuranceCtx.insuplcAdmdvs,
    };
    const diagnosed = await httpDiagnoseInpatientPlatformVisit(payload);
    await showPlatformVisitAlignResult(diagnosed, false);
    const code = String(diagnosed.mismatchCode ?? 'OK');
    if (code === 'OK' || code === 'NO_LOCAL_MAP' || code === 'PLATFORM_ORPHAN_INHOSP') {
      return;
    }
    try {
      await ElMessageBox.confirm(
        `${diagnosed.advice ?? '存在平台在院不一致'}。是否执行一键修复？`,
        '平台在院对齐',
        { type: 'warning', confirmButtonText: '修复', cancelButtonText: '取消' },
      );
    } catch {
      return;
    }
    const repaired = await httpRepairInpatientPlatformVisit(payload);
    await showPlatformVisitAlignResult(repaired, true);
  } catch (error) {
    const message = error instanceof Error ? error.message : '平台在院对齐失败';
    ElMessage.error(message);
  }
}

async function showPlatformVisitAlignResult(
  result: AlignInpatientPlatformVisitResponse,
  afterRepair: boolean,
) {
  const platformIds = (result.platformInHospitalMdtrtIds ?? []).join('、') || '（无）';
  const lines = [
    `不一致码：${result.mismatchCode ?? '—'}`,
    `本地 mdtrt：${result.localMdtrtId ?? '（无）'}`,
    `平台在院：${platformIds}`,
    `建议：${result.advice ?? '—'}`,
  ];
  if (afterRepair) {
    lines.push(`修复：${result.repairSummary ?? (result.repaired ? '已执行' : '未执行')}`);
  }
  await ElMessageBox.alert(lines.join('\n'), afterRepair ? '平台在院对齐结果' : '平台在院诊断', {
    confirmButtonText: '知道了',
  });
}

/** 底部结算 */
async function handleFooterAction(actionCode: InpatientSettleFooterActionCode) {
  if (actionCode === INPATIENT_SETTLE_FOOTER_ACTION.CONFIRM_SETTLE) {
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
      '反结后将生成一条负向支付记录，本批费用将复制为新的待结算明细（原已结算记录保留可查）。是否继续？',
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

/** 对已离院患者执行出院召回（IF-IP-RCL-01；内嵌反结） */
async function handleRecallInpatientDischarge(row: SettlementHistoryRow) {
  if (!isSettledQueueTab.value) {
    return;
  }
  if (!row.recallEnabled) {
    ElMessage.warning('当前结算单不可召回');
    return;
  }
  const { encounterId, patientId } = resolveActiveSettlePatientIds();
  if (!encounterId || !patientId) {
    ElMessage.warning('请先搜索并选择患者');
    return;
  }
  const paymentId = preserveApiIdForRequest(row.paymentId);
  try {
    await ElMessageBox.confirm(
      '召回将撤销出院结算并使患者回到已入科（未占床）。需医护配合调整费用后重新办理出院再结算。是否继续？',
      '出院召回',
      { type: 'warning', confirmButtonText: '确认召回', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  reverseSettleLoading.value = true;
  try {
    const result = await httpRecallInpatientDischarge({
      encounterId,
      patientId,
      paymentId: paymentId || undefined,
    });
    const tips: string[] = ['出院召回成功，患者已回已入科（未占床）'];
    if (result.refundPaymentId) {
      tips.push(`逆向单：${result.refundPaymentId}`);
    }
    ElMessage.success(tips.join('；'));
    await reloadWorkspace();
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '出院召回失败';
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
  const blockedMessage = inpatientSettleBlockedMessage(
    encounterStatus,
    chargeItemIds,
    allPendingChargeItemIds.value,
  );
  if (blockedMessage) {
    ElMessage.warning(blockedMessage);
    return;
  }
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
    ElMessage.warning('请先完成护士出院后再结算');
    return;
  }
  preSettleLoading.value = true;
  let progressOpened = false;
  try {
    let insurancePayload: Record<string, string | undefined> = {};
    if (isInsuranceRegistration.value) {
      const contractId = preserveApiIdForRequest(currentQueueRow.value?.contractId);
      if (!contractId) {
        ElMessage.warning('医保患者缺少合同信息，请检查住院登记');
        return;
      }
      // 页脚已选介质优先；未选时弹窗选择（对齐门诊收费）
      let medium = insuranceMedium.value;
      if (!medium) {
        const chosen = await chooseInpatientSettleInsuranceMedium();
        if (!chosen) {
          return;
        }
        medium = chosen;
        insuranceMedium.value = chosen;
      }
      const readCredentialLabel = inpatientSettleReadCredentialLabel(medium);
      settleProgress.open(
        '住院预结算处理中',
        [
          { key: 'read-card', label: readCredentialLabel },
          { key: 'person-1101', label: '核验参保人员信息（1101）' },
          { key: 'detail-audit', label: '医保事前提醒（3101）' },
          { key: 'fee-upload', label: '费用明细上传（2301）' },
          { key: 'pre-settle', label: '医保预结算通信（2303）' },
          { key: 'open-dialog', label: '打开结算确认' },
        ],
        '正在准备…',
      );
      progressOpened = true;
      await nextTick();
      try {
        const insuranceCtx = await resolveInpatientSettleInsuranceContext(contractId, medium, {
          onPhase: (phase) => {
            if (phase === 'plugin' || phase === 'read-card') {
              settleProgress.setStep(0, inpatientSettleReadCredentialHint(medium));
              return;
            }
            settleProgress.setStep(1, '正在核验参保人员信息（1101）…');
          },
        });
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
          ElMessage.warning(inpatientSettleReadCredentialFailHint(medium));
          return;
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : '医保读卡失败';
        ElMessage.error(message);
        return;
      }
      try {
        settleProgress.setStep(2, '医保事前提醒分析中（3101），请稍候…');
        const contractBusNo = getInpatientContractBusNo(contractId, contractMeta.value);
        const { continued } = await runIpDetailAuditGate({
          params: {
            encounterId,
            chargeItemIds,
            settleMode: settleMode || undefined,
            contractId,
            contractBusNo,
            trigScen: '9',
            psnNo: insurancePayload.psnNo,
            insuplcAdmdvs: insurancePayload.insuplcAdmdvs,
            insutype: insurancePayload.insutype,
          },
          dialogRef: detailAuditDialogRef,
        });
        if (!continued) {
          ElMessage.info('已按事前提醒返回修改，未继续预结算');
          return;
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : '事前提醒失败';
        ElMessage.error(message);
        return;
      }
      try {
        await uploadInpatientFeeDetailsWithProgress({
          payload: {
            encounterId,
            patientId,
            chargeItemIds,
            settleMode,
            ...insurancePayload,
          },
          progress: settleProgress,
          stepIndex: 3,
        });
      } catch (error) {
        const message = error instanceof Error ? error.message : '费用明细上传失败';
        ElMessage.error(message);
        return;
      }
      settleProgress.clearCount();
      settleProgress.setStep(4, '医保预结算通信中（2303），请稍候…');
    }
    const pre = await httpPreSettleInpatientCharges({
      encounterId,
      patientId,
      chargeItemIds,
      settleMode,
      ...insurancePayload,
    });
    if (progressOpened) {
      settleProgress.setStep(5, '预结算完成，即将打开确认窗口…');
    }
    preSettleSnapshot.value = pre;
    lockedChargeItemIds.value = [...chargeItemIds];
    settlePayChannelCode.value = resolvePayChannelCode();
    settleDialogVisible.value = true;
  } finally {
    if (progressOpened) {
      settleProgress.close();
    }
    preSettleLoading.value = false;
  }
}

/** 按场景组装正式结算进度步骤 */
function buildFormalSettleProgressSteps(
  snap: PreSettleInpatientChargesResponse,
  payAmount: number,
): Array<{ key: string; label: string }> {
  const steps: Array<{ key: string; label: string }> = [];
  if (snap.insurancePreSettled) {
    if (payAmount > 0) {
      steps.push({ key: 'collect-cash', label: '收个人现金' });
    }
    steps.push(
      { key: 'platform-discharge', label: '平台出院办理（2402）' },
      { key: 'confirm-2304', label: '医保正式结算（2304）' },
      { key: 'local-settle', label: '院内落账' },
    );
    const planLen = (snap.prepayRefundPlan ?? []).length;
    if (planLen > 0) {
      steps.push({ key: 'prepay-refund', label: '预交金原路退' });
    }
    steps.push({ key: 'financial-complete', label: '财务完结' });
    return steps;
  }
  if (payAmount > 0) {
    steps.push({ key: 'collect-cash', label: '收补交' });
  }
  steps.push(
    { key: 'local-settle', label: '院内落账与多退' },
    { key: 'financial-complete', label: '财务完结' },
  );
  return steps;
}

function settleProgressStepIndex(key: string): number {
  return settleProgressSteps.value.findIndex((step) => step.key === key);
}

function goSettleProgressStep(key: string, detail: string) {
  const idx = settleProgressStepIndex(key);
  if (idx >= 0) {
    settleProgress.setStep(idx, detail);
  }
}

function resolvePayChannelLabel(code: string): string {
  const hit = paymentChannelSelectOptions.value.find((item) => item.value === code);
  return hit?.label || code || '—';
}

function formatPrepayRefundSummary(plan: InpatientPrepayRefundPlanItemDto[]): string {
  if (!plan.length) {
    return '无预交金退回';
  }
  let total = 0;
  const channelMap = new Map<string, number>();
  for (const item of plan) {
    const amt = Number(item.amount ?? 0);
    if (!Number.isFinite(amt) || amt <= 0) {
      continue;
    }
    total += amt;
    const code = String(item.payChannelCode ?? '').trim() || '未知渠道';
    channelMap.set(code, (channelMap.get(code) ?? 0) + amt);
  }
  if (total <= 0) {
    return '无预交金退回';
  }
  const channelText = [...channelMap.entries()]
    .map(([code, amt]) => `${resolvePayChannelLabel(code)} ¥${formatMoneyDisplay(amt)}`)
    .join('；');
  return `合计 ¥${formatMoneyDisplay(total)}${channelText ? `（${channelText}）` : ''}`;
}

/** 结算成功摘要弹窗 */
async function showSettleSuccessSummary(options: {
  confirmRes: ConfirmInpatientSettleResponse;
  payAmount: number;
  payChannelCode: string;
  prepayRefundPlan?: InpatientPrepayRefundPlanItemDto[];
}) {
  const cashLine =
    options.payAmount > 0
      ? `个人现金实收：¥${formatMoneyDisplay(options.payAmount)}（${resolvePayChannelLabel(options.payChannelCode)}）`
      : '个人现金实收：无';
  const refundLine = `预交金退回：${formatPrepayRefundSummary(options.prepayRefundPlan ?? [])}`;
  const lines = [cashLine, refundLine];
  if (options.confirmRes.insuranceSettled && options.confirmRes.ybSetlId) {
    lines.push(`医保结算号：${options.confirmRes.ybSetlId}`);
  }
  const safeLines = lines.map((line) =>
    line.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;'),
  );
  await ElMessageBox.alert(
    `<div style="line-height:1.75">${safeLines.map((line) => `<div>${line}</div>`).join('')}</div>`,
    '结算成功',
    {
      confirmButtonText: '知道了',
      type: 'success',
      dangerouslyUseHTMLString: true,
    },
  );
}

/**
 * 第二阶段：Win 原路退 + complete API。
 * @returns true 已退完；false 半成功（用户选稍后或仍失败）
 */
async function runPendingPrepayRefundStage(options: {
  encounterId: string;
  patientId: string;
  plan: InpatientPrepayRefundPlanItemDto[];
  progressOpened: boolean;
}): Promise<{
  completed: boolean;
  confirmRes?: ConfirmInpatientSettleResponse;
}> {
  const pendingItems = options.plan.filter((item) => {
    const originalPaymentId = String(item.originalPaymentId ?? '').trim();
    const refundAmountYuan = Number(item.amount ?? 0);
    return (
      !!originalPaymentId &&
      item.requiresClientRefund &&
      Number.isFinite(refundAmountYuan) &&
      refundAmountYuan > 0
    );
  });
  const total = pendingItems.length;

  const executeOnce = async (): Promise<ConfirmInpatientSettleResponse> => {
    const clientRefundResults: Array<{
      clientRefundResult: NonNullable<Awaited<ReturnType<typeof submitScanRefund>>>;
      originalPaymentId: string;
    }> = [];
    for (let i = 0; i < pendingItems.length; i += 1) {
      const item = pendingItems[i]!;
      const originalPaymentId = String(item.originalPaymentId ?? '').trim();
      const refundAmountYuan = Number(item.amount ?? 0);
      settleScanStatusText.value = `预交金原路退第 ${i + 1}/${total} 笔…`;
      if (options.progressOpened) {
        settleProgress.setCount(i + 1, total, `原路退 ${i + 1}/${total}`);
        goSettleProgressStep('prepay-refund', `预交金原路退第 ${i + 1}/${total} 笔，请稍候…`);
      }
      const clientRefundResult = await submitScanRefund({
        originalPaymentId,
        refundAmountYuan,
        originalPayChannelCodes: item.payChannelCode ? [item.payChannelCode] : undefined,
      });
      if (clientRefundResult) {
        clientRefundResults.push({ originalPaymentId, clientRefundResult });
      }
    }
    if (options.progressOpened) {
      settleProgress.clearCount();
      goSettleProgressStep('financial-complete', '财务完结处理中…');
    }
    const confirmRes = await httpCompleteInpatientDischargePrepayRefund({
      encounterId: options.encounterId,
      patientId: options.patientId,
      ...(clientRefundResults.length > 0 ? { clientRefundResults } : {}),
    });
    for (const item of clientRefundResults) {
      clearCachedClientRefundResult(item.originalPaymentId);
    }
    return confirmRes;
  };

  // 失败后可重试原路退，或选稍后处理退出
  for (;;) {
    try {
      const confirmRes = await executeOnce();
      return { completed: true, confirmRes };
    } catch (error) {
      const message =
        error instanceof Error ? error.message : '结算已成功，但预交金原路退失败，请稍后重试';
      ElMessage.error(message);
      if (options.progressOpened) {
        settleProgress.clearCount();
      }
      try {
        await ElMessageBox.confirm(
          '费用已结算，预交金尚未退完。可重试原路退，或稍后再试。',
          '预交金待退完',
          {
            confirmButtonText: '重试原路退',
            cancelButtonText: '稍后处理',
            type: 'warning',
            distinguishCancelAndClose: true,
          },
        );
      } catch {
        return { completed: false };
      }
    }
  }
}

/** 正式结算成功后的页面刷新与提示 */
async function finalizeAfterSettleSuccess(confirmRes: ConfirmInpatientSettleResponse) {
  if (confirmRes.paymentId != null) {
    lastSettlePaymentId.value = String(confirmRes.paymentId);
  }
  preSettleSnapshot.value = null;
  lockedChargeItemIds.value = [];
  pendingPrepayRefundContext.value = null;
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
  const payChannelCode = settlePayChannelCode.value || resolvePayChannelCode();
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
  let confirmProgressOpened = false;
  const progressSteps = buildFormalSettleProgressSteps(snap, payAmount);
  const hasCollectCashStep = progressSteps.some((step) => step.key === 'collect-cash');

  try {
    settleProgress.open(
      '住院正式结算处理中',
      progressSteps,
      hasCollectCashStep
        ? snap.insurancePreSettled
          ? '正在收取个人现金…'
          : '正在收取补交…'
        : snap.insurancePreSettled
          ? '正在办理平台出院并正式结算…'
          : '正在院内落账…',
    );
    confirmProgressOpened = true;
    await nextTick();

    let clientScanResult;
    if (hasCollectCashStep) {
      goSettleProgressStep(
        'collect-cash',
        snap.insurancePreSettled ? '收取个人现金中…' : '收取补交中…',
      );
      if (isScanPayChannel(settlePayChannelCode.value) && payAmount > 0) {
        await refreshUnionPayCapabilities();
        if (!scanPayEnabled.value) {
          ElMessage.error('请启动本地客户端（Zxplat.Win）并确认银联组件已安装');
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
    }

    if (snap.insurancePreSettled) {
      goSettleProgressStep('platform-discharge', '平台出院办理中（2402），请稍候…');
      // 同 HTTP 内 2402→2304：请求发出时切到 2304 文案占位（非实时）
      goSettleProgressStep('confirm-2304', '医保正式结算通信中（2304），请稍候…');
    } else {
      goSettleProgressStep('local-settle', '院内落账处理中…');
    }

    const paymentDetails =
      payAmount > 0
        ? [
            {
              amount: payAmount,
              payChannelCode,
            },
          ]
        : [];
    const confirmBody = {
      billDate: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      encounterId,
      patientId,
      paymentDraftId: snap.paymentDraftId,
      chargeItemIds,
      ybPreSettleId: snap.ybPreSettleId,
      paymentDetails,
      ...(clientScanResult ? { clientScanResult } : {}),
    };

    let confirmRes = clientScanResult
      ? await settleWithScanRecovery(
          clientScanResult,
          () => httpConfirmInpatientSettle(confirmBody),
          {
            paymentDraftId: snap.paymentDraftId,
            refundAmountYuan: payAmount,
          },
        )
      : await httpConfirmInpatientSettle(confirmBody);

    const refundPlan = confirmRes?.prepayRefundPlan ?? snap.prepayRefundPlan ?? [];

    if (confirmRes?.prepayRefundPending) {
      // 若预结算未预留原路退步，动态补上（避免步骤列表缺项）；勿 reopen，以免进度归零
      if (settleProgressStepIndex('prepay-refund') < 0) {
        const insertAt = Math.max(settleProgressSteps.value.length - 1, 0);
        settleProgressSteps.value.splice(insertAt, 0, {
          key: 'prepay-refund',
          label: '预交金原路退',
        });
      }
      goSettleProgressStep('local-settle', '院内落账完成，准备原路退…');
      goSettleProgressStep('prepay-refund', '预交金原路退货中…');

      pendingPrepayRefundContext.value = {
        confirmRes,
        encounterId,
        patientId,
        payAmount,
        payChannelCode,
        plan: refundPlan,
      };

      const stage = await runPendingPrepayRefundStage({
        encounterId,
        patientId,
        plan: refundPlan,
        progressOpened: confirmProgressOpened,
      });

      if (!stage.completed || !stage.confirmRes) {
        settleDialogVisible.value = false;
        settleScanCode.value = '';
        ElMessage.warning({
          message: '费用已结算，预交金待退完。可点击「重试原路退」继续处理。',
          duration: 6000,
        });
        if (selectedEncounterId.value) {
          await reloadWorkspace();
        }
        return;
      }
      confirmRes = stage.confirmRes;
      pendingPrepayRefundContext.value = null;
    } else if (snap.insurancePreSettled) {
      goSettleProgressStep('local-settle', '院内落账完成…');
    }

    goSettleProgressStep('financial-complete', '财务完结完成');
    settleDialogVisible.value = false;
    settleScanCode.value = '';

    await showSettleSuccessSummary({
      confirmRes,
      payAmount,
      payChannelCode,
      prepayRefundPlan: refundPlan,
    });
    await finalizeAfterSettleSuccess(confirmRes);
  } catch (error) {
    const message = error instanceof Error ? error.message : '住院结算失败';
    ElMessage.error(message);
  } finally {
    if (confirmProgressOpened) {
      settleProgress.close();
    }
    confirmSettleLoading.value = false;
  }
}

function handleBillBannerFocusAdjust() {
  activeTab.value = 'fee-detail';
}

/** 稍后处理后，基于缓存上下文重试原路退 */
async function retryPendingPrepayRefundFromContext() {
  const ctx = pendingPrepayRefundContext.value;
  if (!ctx) {
    ElMessage.warning('暂无待退的预交金原路退任务');
    return;
  }
  confirmSettleLoading.value = true;
  let progressOpened = false;
  try {
    settleProgress.open(
      '预交金原路退处理中',
      [
        { key: 'prepay-refund', label: '预交金原路退' },
        { key: 'financial-complete', label: '财务完结' },
      ],
      '预交金原路退货中…',
    );
    progressOpened = true;
    await nextTick();
    const stage = await runPendingPrepayRefundStage({
      encounterId: ctx.encounterId,
      patientId: ctx.patientId,
      plan: ctx.plan,
      progressOpened,
    });
    if (!stage.completed || !stage.confirmRes) {
      ElMessage.warning('预交金仍未退完，请稍后重试');
      return;
    }
    pendingPrepayRefundContext.value = null;
    await showSettleSuccessSummary({
      confirmRes: stage.confirmRes,
      payAmount: ctx.payAmount,
      payChannelCode: ctx.payChannelCode,
      prepayRefundPlan: ctx.plan,
    });
    await finalizeAfterSettleSuccess(stage.confirmRes);
  } catch (error) {
    const message = error instanceof Error ? error.message : '预交金原路退失败';
    ElMessage.error(message);
  } finally {
    if (progressOpened) {
      settleProgress.close();
    }
    confirmSettleLoading.value = false;
  }
}

function settlementHistoryStatusTagType(statusCode: SettlementHistoryStatusCode) {
  if (statusCode === 'SETTLED') {
    return 'success';
  }
  if (statusCode === 'REVERSE_RECORD') {
    return 'info';
  }
  if (statusCode === 'REVERSED') {
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
    // 用本次查阅回填的医保分项刷新摘要（避免列表缓存缺基金）
    const brief = (res.payments ?? []).find(
      (payment) => String(payment.paymentId) === String(row.paymentId),
    );
    if (brief) {
      selectedSettlementHistory.value = mapPaymentToHistoryRow(brief, res);
    }
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
  try {
    contractMeta.value = await loadInpatientRegisterContractMeta();
  } catch {
    contractMeta.value = { contractCategoryById: {}, contractInsuranceProfileById: {} };
  }
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
            <ElButton
              plain
              @click="handleToolbarAction(INPATIENT_SETTLE_TOOLBAR_ACTION.REFRESH_FEES)"
            >
              {{ INPATIENT_SETTLE_TOOLBAR_ACTION_LABEL.REFRESH_FEES }}
            </ElButton>
            <ElButton
              type="primary"
              @click="handleToolbarAction(INPATIENT_SETTLE_TOOLBAR_ACTION.VERIFY_INSURANCE)"
            >
              {{ INPATIENT_SETTLE_TOOLBAR_ACTION_LABEL.VERIFY_INSURANCE }}
            </ElButton>
            <ElButton
              type="warning"
              plain
              @click="handleToolbarAction(INPATIENT_SETTLE_TOOLBAR_ACTION.ALIGN_PLATFORM_VISIT)"
            >
              {{ INPATIENT_SETTLE_TOOLBAR_ACTION_LABEL.ALIGN_PLATFORM_VISIT }}
            </ElButton>
            <ElButton
              v-if="INPATIENT_SETTLE_INSURANCE_SHEET_PRINT_ENABLED"
              type="success"
              plain
              @click="handleToolbarAction(INPATIENT_SETTLE_TOOLBAR_ACTION.PRINT_INSURANCE_SETTLEMENT)"
            >
              {{ INPATIENT_SETTLE_TOOLBAR_ACTION_LABEL.PRINT_INSURANCE_SETTLEMENT }}
            </ElButton>
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
                    <ElTableColumn prop="ratio" label="占总费用比" min-width="110" align="center" />
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
                ref="settlementHistoryTableRef"
                :data="currentPatientDisplay.settlementHistoryRows"
                row-key="paymentId"
                stripe
                border
                height="100%"
                highlight-current-row
                @current-change="handleSettlementHistoryRowSelect"
              >
                <ElTableColumn type="index" label="序号" width="56" align="center" />
                <ElTableColumn
                  prop="settlementNo"
                  label="单据号"
                  min-width="168"
                  align="center"
                  show-overflow-tooltip
                />
                <ElTableColumn prop="settleTime" label="结算时间" min-width="140" align="center" />
                <ElTableColumn prop="settleType" label="结算类型" width="90" align="center" />
                <ElTableColumn prop="totalAmount" label="结算金额" width="100" align="right" />
                <ElTableColumn
                  prop="reimbursementAmount"
                  label="医保报销"
                  width="100"
                  align="right"
                />
                <ElTableColumn prop="selfPayAmount" label="个人自付" width="100" align="right" />
                <ElTableColumn
                  prop="paymentMethod"
                  label="支付方式"
                  min-width="120"
                  align="center"
                  show-overflow-tooltip
                />
                <ElTableColumn prop="operatorName" label="操作员" width="90" align="center" />
                <ElTableColumn prop="status" label="状态" width="90" align="center">
                  <template #default="{ row }">
                    <ElTag
                      :type="settlementHistoryStatusTagType(row.statusCode)"
                      effect="plain"
                      size="small"
                    >
                      {{ row.status }}
                    </ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="操作" width="140" fixed="right" align="center">
                  <template #default="{ row }">
                    <ElButton type="primary" link @click="handleOpenSettlementDetail(row)">
                      明细
                    </ElButton>
                    <ElButton
                      v-if="isSettledQueueTab && row.recallEnabled"
                      type="warning"
                      link
                      :loading="reverseSettleLoading"
                      @click="handleRecallInpatientDischarge(row)"
                    >
                      召回
                    </ElButton>
                    <ElTooltip
                      v-else-if="isSettledQueueTab"
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
              <span class="payment-footer-amount-label">应付个人现金：</span>
              <strong class="settle-payable-amount">
                ¥{{ Number.isFinite(payableAmount) ? formatMoneyDisplay(payableAmount) : '0.00' }}
              </strong>
            </div>
            <p
              v-if="isInsuranceRegistration"
              class="payment-footer-hint text-xs text-[var(--el-text-color-secondary)]"
            >
              医保登记患者：先选凭证预结算（不办理平台出院）；确认结算成功后再出院办理。个人现金优先冲抵预交金，不足再补交
            </p>
          </div>

          <div class="payment-footer-meta">
            <div v-if="isInsuranceRegistration" class="payment-footer-meta-line">
              <span class="payment-footer-meta-label">医保凭证：</span>
              <ElRadioGroup v-model="insuranceMedium" class="payment-footer-pay-method">
                <ElRadio v-for="opt in insuranceMediumOptions" :key="opt.value" :label="opt.value">
                  {{ opt.label }}
                </ElRadio>
              </ElRadioGroup>
            </div>
            <div v-else class="payment-footer-meta-line">
              <span class="payment-footer-meta-label">实际支付方式：</span>
              <ElRadioGroup v-model="paymentChannelCode" class="payment-footer-pay-method">
                <ElRadio
                  v-for="opt in paymentChannelSelectOptions"
                  :key="opt.value"
                  :label="opt.value"
                >
                  {{ opt.label }}
                </ElRadio>
              </ElRadioGroup>
            </div>
            <div class="payment-footer-meta-line">
              <span class="payment-footer-meta-label">结算时间：</span>
              <span class="payment-footer-meta-value">{{ currentPatientDisplay.paymentTime }}</span>
              <span class="payment-footer-meta-label payment-footer-meta-label--gap"
                >结算状态：</span
              >
              <ElTag
                :type="
                  currentPatientDisplay.settlementStatusCode === 'SETTLED' ? 'success' : 'warning'
                "
                effect="plain"
                size="small"
              >
                {{ currentPatientDisplay.settlementStatus }}
              </ElTag>
            </div>
          </div>

          <div class="payment-footer-actions">
            <ElButton
              v-if="pendingPrepayRefundContext"
              type="warning"
              :loading="confirmSettleLoading"
              @click="retryPendingPrepayRefundFromContext"
            >
              重试原路退
            </ElButton>
            <ElButton
              v-if="!isSettledQueueTab"
              type="primary"
              size="large"
              class="payment-footer-settle-btn !h-11 !min-w-[128px] shrink-0 !px-8 !text-base !font-bold"
              :disabled="settleActionDisabled"
              :title="settleActionBlockedMessage ?? undefined"
              :loading="preSettleLoading || confirmSettleLoading"
              @click="handleFooterAction(INPATIENT_SETTLE_FOOTER_ACTION.CONFIRM_SETTLE)"
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
              :type="settlementHistoryStatusTagType(selectedSettlementHistory.statusCode)"
              effect="plain"
              size="small"
            >
              {{ selectedSettlementHistory.status }}
            </ElTag>
          </div>
        </div>
        <div v-if="selectedSettlementHistory.payCompositionRows.length > 0" class="mb-3">
          <SectionTitle title="本次谁付的" />
          <div
            class="mt-2 grid grid-cols-2 gap-x-4 gap-y-2 text-sm text-[var(--el-text-color-primary)] sm:grid-cols-3"
          >
            <div v-for="item in selectedSettlementHistory.payCompositionRows" :key="item.key">
              <span class="text-[var(--el-text-color-secondary)]">{{ item.label }}：</span>
              ¥{{ item.value }}
            </div>
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
      :prepay-net-balance="preSettleSnapshot?.prepayNetBalance ?? summary?.prepayNetBalance"
      :prepay-offset-amount="settlePrepayOffsetAmount"
      :prepay-refund-amount="preSettleSnapshot?.prepayRefundAmount"
      :prepay-full-refund="Boolean(preSettleSnapshot?.prepayFullRefund)"
      :preview-text="preSettleSnapshot?.previewText"
      :insurance-pre-settled="preSettleSnapshot?.insurancePreSettled"
      :insurance-breakdown-items="settlementInsuranceBreakdown"
      :settle-mode-label="currentSettleModeLabel"
      :pay-channel-options="settlePayChannelOptions"
      :scan-pay-enabled="hasScanPayChannel"
      :scan-status-text="settleScanStatusText"
      @confirm="handleConfirmSettleFromDialog"
    />

    <DetailAuditRemindDialog ref="detailAuditDialogRef" warn-type="1" />
    <InsuranceProgressDialog
      v-model="settleProgressVisible"
      :title="settleProgressTitle"
      :steps="settleProgressSteps"
      :active-index="settleProgressActiveIndex"
      :detail="settleProgressDetail"
      :count-current="settleProgressCountCurrent"
      :count-total="settleProgressCountTotal"
      :count-label-text="settleProgressCountLabelText"
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

/* 底栏主操作「结算」：加大字号/高度，收费员一眼能点到 */
.settle-page :deep(.payment-footer-settle-btn.el-button) {
  min-width: 128px;
  height: 44px;
  padding: 0 32px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.12em;
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
