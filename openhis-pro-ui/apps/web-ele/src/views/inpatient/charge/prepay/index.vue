<script setup lang="ts">
import type { TableColumn } from '@zxplat-core/table-layout-ui';

import type { PrepayPaymentRecordDto, PrepayQueueItemDto } from '#/api/inpatient-prepay';
import type { BillingPatientCardPatient } from '#/components/charge/billing-patient-card.types';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useUserStore } from '@zxplat/stores';

import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTag,
} from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import RegisterStyleTableLayout from '#/components/layouts/register-style-table-layout.vue';
import {
  httpCollectPrepayAdvanceDeposit,
  httpListPrepayPaymentRecords,
  httpLoadInpatientPrepayPageInit,
  httpPagePrepayPatientQueue,
  httpRefundPrepayAdvanceDeposit,
} from '#/api/inpatient-prepay';
import { isScanPayChannel, useUnionPayScanPay } from '#/composables/payment/useUnionPayScanPay';
import {
  findBpcSuccessOrder,
  submitPrepayScanPayment,
  toClientScanFromBpcOrder,
} from '#/utils/payment/submit-scan-payment';
import { settleWithScanRecovery } from '#/utils/payment/scan-settle-recovery';
import { httpGetBpcOrdersByPayment } from '#/api/payment/union-pay-bpc';
import {
  clearCachedClientRefundResult,
  submitScanRefund,
} from '#/utils/payment/submit-scan-refund';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import { formatMoneyDisplay, formatMoneyYuanWithSymbol } from '#/utils/format/money-scale';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import {
  formatInpatientAccountBalancePrimaryText,
  formatInpatientAccountBalanceSecondaryText,
  resolveInpatientAccountBalanceLabel,
} from '#/utils/inpatient/account-balance-display';
import {
  buildPrepayChangeSourceFromCollect,
  buildPrepayChangeSourceFromRecord,
  printInpatientPrepayChange,
} from '#/utils/print/print-inpatient-prepay-change';
import {
  buildPrepayReceiptSourceFromCollect,
  buildPrepayReceiptSourceFromRecord,
  printInpatientPrepayReceipt,
} from '#/utils/print/print-inpatient-prepay-receipt';
import { scheduleBackgroundPrint } from '#/utils/print/runtime';
import { INPATIENT_PREPAY_CHANGE_SLIP_ENABLED } from '#/views/inpatient/charge/prepay/inpatient-prepay-print.config';


import InpatientQueuePanel from './components/inpatient-queue-panel.vue';
import {
  optionDtosToPayChannelOptions,
  type PayChannelOption,
} from '#/utils/payment/payment-channel-options';
import {
  DEFAULT_PAY_CHANNEL_CODE,
  PAY_CHANNEL_OPTIONS,
  PREPAY_ALLOWED_ENCOUNTER_STATUSES,
  PREPAY_STAY_SCOPE,
  type PrepayStayScope,
} from './inpatient-prepay.constants';
import './inpatient-prepay-panel.css';

defineOptions({
  name: 'ChargeIpPrepay',
});

const userStore = useUserStore();

const queueFilter = reactive({
  admissionDate: '',
  keyword: '',
  stayScope: PREPAY_STAY_SCOPE.IN_HOSPITAL as PrepayStayScope,
});

const queuePager = reactive({
  pageNo: 1,
  pageSize: 50,
});

/**
 * 预交金办理表单：组合收款用多渠道明细行，合计即收费总额。
 */
const prepayForm = reactive({
  /** 支付分配行（款员拆分渠道） */
  paymentDetailRows: [
    { payChannelCode: DEFAULT_PAY_CHANNEL_CODE, amount: 0 },
  ] as Array<{ amount: number; payChannelCode: string }>,
  remark: '',
  scanCode: '',
});

/**
 * 组合收款合计金额（展示与提交用）。
 */
const prepayCollectTotalAmount = computed(() => {
  let sum = 0;
  for (const row of prepayForm.paymentDetailRows) {
    const amount = Number(row?.amount);
    // 仅累加正数行
    if (Number.isFinite(amount) && amount > 0) {
      sum += amount;
    }
  }
  return Number(sum.toFixed(2));
});

/**
 * 明细中是否包含扫码渠道（展示付款码）。
 */
const hasScanPayInCollectRows = computed(() =>
  prepayForm.paymentDetailRows.some((row) => isScanPayChannel(row.payChannelCode)),
);

/**
 * 唯一扫码渠道行金额；多条扫码时返回 null（提交前拦截）。
 */
const exclusiveScanPayRowAmount = computed(() => {
  const scanRows = prepayForm.paymentDetailRows.filter((row) =>
    isScanPayChannel(row.payChannelCode),
  );
  // 无扫码行
  if (scanRows.length === 0) {
    return null;
  }
  // 多于一条扫码不允许
  if (scanRows.length > 1) {
    return null;
  }
  const amount = Number(scanRows[0]?.amount);
  if (!Number.isFinite(amount) || amount <= 0) {
    return null;
  }
  return amount;
});

const refundForm = reactive({
  amount: '',
  payChannelCode: DEFAULT_PAY_CHANNEL_CODE,
  remark: '',
  originalPaymentId: '',
});

const scanStatusText = ref('');
const payChannelOptions = ref<PayChannelOption[]>([...PAY_CHANNEL_OPTIONS]);

const {
  scanPayEnabled,
  hasScanPayChannel,
  effectivePayChannelOptions,
  refreshUnionPayCapabilities,
} = useUnionPayScanPay(() => payChannelOptions.value);

const recordTableQueryParams = reactive({
  pageNo: 1,
  pageSize: 20,
});

const queueData = ref<PrepayQueueItemDto[]>([]);
const prepayRecordData = ref<PrepayPaymentRecordDto[]>([]);
const selectedEncounterId = ref('');
const queueLoading = ref(false);
const tableLoading = ref(false);
const collectSubmitting = ref(false);
const refundSubmitting = ref(false);
const refundDialogVisible = ref(false);

const quickAmounts = [100, 200, 500, 1000, 2000];

const recordTableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 60, align: 'center', headerAlign: 'center' },
  { prop: 'tradeTime', label: '收费时间', minWidth: 170, align: 'center', headerAlign: 'center' },
  { prop: 'bizTypeText', label: '业务类型', minWidth: 110, align: 'center', headerAlign: 'center' },
  {
    prop: 'amount',
    label: '金额(元)',
    minWidth: 110,
    slot: 'amount',
    align: 'right',
    headerAlign: 'center',
  },
  {
    prop: 'payMethodText',
    label: '支付方式',
    minWidth: 110,
    align: 'center',
    headerAlign: 'center',
  },
  { prop: 'operatorName', label: '操作员', minWidth: 100, align: 'center', headerAlign: 'center' },
  { prop: 'ticketNo', label: '票据号', minWidth: 150, align: 'center', headerAlign: 'center' },
  {
    prop: 'statusEnumText',
    label: '状态',
    minWidth: 90,
    slot: 'status',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'action',
    label: '操作',
    width: 200,
    slot: 'action',
    align: 'center',
    headerAlign: 'center',
    fixed: 'right',
  },
];

const draftSettlePaymentId = ref('');
const reprintPaymentId = ref('');
const reprintChangePaymentId = ref('');
const printLatestLoading = ref(false);
const printLatestChangeLoading = ref(false);

const currentPatient = computed(
  () => queueData.value.find((item) => item.encounterId === selectedEncounterId.value) ?? null,
);

const queueCardList = computed<BillingPatientCardPatient[]>(() =>
  queueData.value.map((row) => ({
    ageText: row.ageText,
    doctorName: '',
    gender: row.genderEnumText ?? '',
    id: row.encounterId,
    inpatientNo: row.encounterBusNo,
    insuranceType: row.contractDisplay ?? '',
    medicalRecordNo: row.encounterBusNo,
    patientName: row.patientName,
    visitDepartment: [row.organizationName, row.bedName ? `${row.bedName}床` : '']
      .filter(Boolean)
      .join(' / '),
    visitTime: row.startTime ?? '',
    visitType: 'inpatient',
    accountBalanceSummary: formatInpatientAccountBalancePrimaryText(row),
    accountBalanceSub: formatInpatientAccountBalanceSecondaryText(row),
    lowBalanceWarning: row.lowBalanceWarning === true,
  })),
);

const canOperatePrepay = computed(() => {
  const status = currentPatient.value?.statusEnum ?? '';
  return (PREPAY_ALLOWED_ENCOUNTER_STATUSES as readonly string[]).includes(status);
});

const currentPatientStatusTagType = computed(() => {
  if (!currentPatient.value) return 'info';
  if (currentPatient.value.lowBalanceWarning) return 'danger';
  return 'success';
});

/** 库内可退余额（退费上限） */
function resolveRefundableBalance(row: PrepayQueueItemDto | null): number {
  if (!row) return 0;
  const raw = row.prepayNetBalance ?? row.accountBalance ?? 0;
  const num = Number(raw);
  return Number.isFinite(num) ? num : 0;
}

/** 将金额格式化为展示文案 */
function formatCurrencyAmount(value: number | string | undefined): string {
  if (value === undefined || value === null || value === '') return '-';
  const num = Number(value);
  if (!Number.isFinite(num)) return String(value);
  return formatMoneyYuanWithSymbol(num);
}

/** 预交金流水金额：退预交金（UN_PAY）前加负号 */
function formatPrepayRecordAmount(row: PrepayPaymentRecordDto): string {
  const num = Math.abs(Number(row.amount));
  if (!Number.isFinite(num)) return '¥-';
  const isRefund = row.paymentEnum === 'UN_PAY';
  return isRefund ? `¥-${formatMoneyDisplay(num)}` : formatMoneyYuanWithSymbol(num);
}

/** 可原路退的收取流水选项 */
const collectPaymentOptions = computed(() =>
  prepayRecordData.value
    .filter(
      (row) =>
        row.statusEnum === 'SUCCESS' &&
        row.paymentEnum !== 'UN_PAY' &&
        String(row.paymentId ?? '').trim(),
    )
    .map((row) => ({
      value: String(row.paymentId),
      label: `${row.tradeTime} · ${formatPrepayRecordAmount(row)} · ${row.payMethodText || '—'}`,
    })),
);

/** 最近一笔成功收取流水（供扫码原路退选原支付单） */
function resolveLatestCollectPaymentId(records: PrepayPaymentRecordDto[]): string | undefined {
  const collectRows = records.filter(
    (row) =>
      row.statusEnum === 'SUCCESS' &&
      row.paymentEnum !== 'UN_PAY' &&
      String(row.paymentId ?? '').trim(),
  );
  if (collectRows.length === 0) {
    return undefined;
  }
  const sorted = [...collectRows].sort((a, b) =>
    String(b.tradeTime ?? '').localeCompare(String(a.tradeTime ?? '')),
  );
  return String(sorted[0]?.paymentId ?? '').trim() || undefined;
}

/** 解析金额输入为正数，非法时返回 null */
function parsePositiveAmount(raw: string): null | number {
  const num = Number(String(raw ?? '').trim());
  if (!Number.isFinite(num) || !(num > 0)) return null;
  return num;
}

/** 收退成功后刷新队列（同步展示文案与可退余额） */
async function refreshQueueAfterBalanceChange() {
  await queryQueueData();
}

/** 根据左侧筛选条件刷新患者队列 */
async function queryQueueData() {
  queueLoading.value = true;
  try {
    const result = await httpPagePrepayPatientQueue({
      keyword: queueFilter.keyword.trim() || undefined,
      admissionDate: queueFilter.admissionDate.trim() || undefined,
      stayScope: queueFilter.stayScope,
      pageNo: queuePager.pageNo,
      pageSize: queuePager.pageSize,
    });
    queueData.value = result.content ?? [];
    if (!selectedEncounterId.value && queueData.value.length > 0) {
      selectedEncounterId.value = queueData.value[0]?.encounterId ?? '';
    }
    if (!queueData.value.some((item) => item.encounterId === selectedEncounterId.value)) {
      selectedEncounterId.value = queueData.value[0]?.encounterId ?? '';
    }
  } finally {
    queueLoading.value = false;
  }
}

/** 解析当前选中就诊对应的患者 ID（队列卡片优先） */
function resolveSelectedPatientId(): string | undefined {
  const row = queueData.value.find((item) => item.encounterId === selectedEncounterId.value);
  return preserveApiIdForRequest(row?.patientId ?? currentPatient.value?.patientId);
}

/** 按当前就诊刷新预交金流水 */
async function queryPrepayRecords() {
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  if (!encounterId) {
    prepayRecordData.value = [];
    return;
  }
  const patientId = resolveSelectedPatientId();
  tableLoading.value = true;
  try {
    const result = await httpListPrepayPaymentRecords({
      encounterId,
      patientId,
    });
    prepayRecordData.value = result.records ?? [];
  } finally {
    tableLoading.value = false;
  }
}

/** 左侧切换患者并触发右侧数据联动 */
function handleChoosePatient(patient: BillingPatientCardPatient) {
  selectedEncounterId.value = patient.id;
}

/** 刷新左侧患者队列 */
async function handleRefreshQueue() {
  await queryQueueData();
  ElMessage.success('患者列表已刷新');
}

/**
 * 快捷金额写入首行支付明细（组合收款起点）。
 *
 * @param amount 快捷金额
 */
function handleApplyQuickAmount(amount: number) {
  // 无明细行时补一行再写入
  if (prepayForm.paymentDetailRows.length === 0) {
    prepayForm.paymentDetailRows.push({
      payChannelCode: DEFAULT_PAY_CHANNEL_CODE,
      amount,
    });
    return;
  }
  prepayForm.paymentDetailRows[0]!.amount = amount;
}

/** 新增一条支付渠道明细行。 */
function addPaymentDetailRow() {
  prepayForm.paymentDetailRows.push({
    payChannelCode: DEFAULT_PAY_CHANNEL_CODE,
    amount: 0,
  });
}

/**
 * 删除一条支付渠道明细行（至少保留一行）。
 *
 * @param index 行下标
 */
function removePaymentDetailRow(index: number) {
  // 至少保留一行，避免提交时空明细
  if (prepayForm.paymentDetailRows.length <= 1) {
    return;
  }
  prepayForm.paymentDetailRows.splice(index, 1);
}

/**
 * 组装提交用支付明细（金额 > 0 且渠道非空）。
 *
 * @returns 明细数组；无有效行时 undefined
 */
function buildCollectPaymentDetailsForSubmit() {
  const rows = prepayForm.paymentDetailRows
    .filter((row) => row && Number(row.amount) > 0 && String(row.payChannelCode || '').trim())
    .map((row) => ({
      payChannelCode: String(row.payChannelCode).trim(),
      amount: Number(row.amount),
    }));
  if (rows.length === 0) {
    return undefined;
  }
  return rows;
}

/**
 * 重置办理区明细与备注（收取成功后）。
 */
function resetCollectForm() {
  prepayForm.paymentDetailRows = [
    { payChannelCode: DEFAULT_PAY_CHANNEL_CODE, amount: 0 },
  ];
  prepayForm.remark = '';
  prepayForm.scanCode = '';
}

/** 打开退预交金弹窗 */
function handleOpenRefundDialog() {
  if (!selectedEncounterId.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (!canOperatePrepay.value) {
    ElMessage.warning('当前就诊状态不可办理预交金退费');
    return;
  }
  refundForm.amount = '';
  refundForm.payChannelCode = DEFAULT_PAY_CHANNEL_CODE;
  refundForm.remark = '';
  refundForm.originalPaymentId = resolveLatestCollectPaymentId(prepayRecordData.value) ?? '';
  refundDialogVisible.value = true;
}

/** 草稿行是否可补结算（银联已扣、院内未落账） */
function canCompleteDraftSettlement(row: PrepayPaymentRecordDto): boolean {
  return (
    row.statusEnum === 'DRAFT' &&
    row.paymentEnum !== 'UN_PAY' &&
    String(row.paymentId ?? '').trim() !== ''
  );
}

/** 成功收取流水可补打预交金收据 */
function canReprintPrepayReceipt(row: PrepayPaymentRecordDto): boolean {
  return (
    row.statusEnum === 'SUCCESS' &&
    row.paymentEnum !== 'UN_PAY' &&
    String(row.paymentId ?? '').trim() !== ''
  );
}

/** 成功收取或退费流水可补打退补单 */
function canReprintPrepayChange(row: PrepayPaymentRecordDto): boolean {
  return (
    INPATIENT_PREPAY_CHANGE_SLIP_ENABLED &&
    row.statusEnum === 'SUCCESS' &&
    String(row.paymentId ?? '').trim() !== ''
  );
}

/** 当前患者是否有可打印的成功收取流水（收据） */
const canPrintLatestPrepayReceipt = computed(() =>
  prepayRecordData.value.some((row) => canReprintPrepayReceipt(row)),
);

/** 当前患者是否有可打印的成功流水（退补单，含退费） */
const canPrintLatestPrepayChange = computed(
  () =>
    INPATIENT_PREPAY_CHANGE_SLIP_ENABLED &&
    prepayRecordData.value.some((row) => canReprintPrepayChange(row)),
);

/** 最近一笔成功收取流水（供工具栏打印预交金收据） */
function resolveLatestCollectRecord(
  records: PrepayPaymentRecordDto[],
): PrepayPaymentRecordDto | undefined {
  const collectRows = records.filter((row) => canReprintPrepayReceipt(row));
  if (collectRows.length === 0) {
    return undefined;
  }
  const sorted = [...collectRows].sort((a, b) =>
    String(b.tradeTime ?? '').localeCompare(String(a.tradeTime ?? '')),
  );
  return sorted[0];
}

/** 最近一笔可打退补单的流水（收取或退费） */
function resolveLatestChangeRecord(
  records: PrepayPaymentRecordDto[],
): PrepayPaymentRecordDto | undefined {
  const rows = records.filter((row) => canReprintPrepayChange(row));
  if (rows.length === 0) {
    return undefined;
  }
  const sorted = [...rows].sort((a, b) =>
    String(b.tradeTime ?? '').localeCompare(String(a.tradeTime ?? '')),
  );
  return sorted[0];
}

/**
 * 交易成功后异步打印：收据与退补单排队依次打，避免并发抢打印机把第二张吞掉。
 * 退费不打收据，只打退补单。
 */
function schedulePrepaySlipAfterTrade(params: {
  changeKind: 'collect' | 'refund';
  amount: number | string;
  payChannelCode: string;
  scanPayType?: string;
  encounterId?: null | string;
  paymentId?: null | string;
}) {
  scheduleBackgroundPrint(async () => {
    if (params.changeKind === 'collect') {
      await printInpatientPrepayReceipt({
        source: buildPrepayReceiptSourceFromCollect({
          patient: currentPatient.value,
          amount: params.amount,
          payChannelCode: params.payChannelCode,
          scanPayType: params.scanPayType,
        }),
        encounterId: params.encounterId,
        paymentId: params.paymentId,
        jobName: '住院预交金收据',
        background: true,
      });
    }
    if (!INPATIENT_PREPAY_CHANGE_SLIP_ENABLED) {
      return;
    }
    await printInpatientPrepayChange({
      source: buildPrepayChangeSourceFromCollect({
        patient: currentPatient.value,
        amount: params.amount,
        payChannelCode: params.payChannelCode,
        scanPayType: params.scanPayType,
        changeKind: params.changeKind,
      }),
      encounterId: params.encounterId,
      paymentId: params.paymentId,
      jobName: '住院预交金退补单',
      background: true,
    });
  });
}

/**
 * 对半成功草稿补结算：复用 BPC SUCCESS 凭证，不重复扫码扣款。
 *
 * @param row 预交金流水草稿行
 */
async function handleCompleteDraftSettlement(row: PrepayPaymentRecordDto) {
  const paymentDraftId = preserveApiIdForRequest(row.paymentId);
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  const patientId = preserveApiIdForRequest(currentPatient.value?.patientId);
  const amountNum = Number(row.amount);
  if (!paymentDraftId || !encounterId || !patientId) {
    ElMessage.warning('缺少就诊或支付草稿信息，无法补结算');
    return;
  }
  if (!Number.isFinite(amountNum) || amountNum <= 0) {
    ElMessage.warning('草稿金额无效');
    return;
  }
  if (!canOperatePrepay.value) {
    ElMessage.warning('当前就诊状态不可收取预交金');
    return;
  }
  draftSettlePaymentId.value = String(paymentDraftId);
  try {
    const orders = (await httpGetBpcOrdersByPayment(paymentDraftId)) ?? [];
    const successOrder = findBpcSuccessOrder(orders);
    if (!successOrder) {
      ElMessage.error('未找到银联成功扣款凭证，请联系信息科，勿重复扫码');
      return;
    }
    const clientScanResult = toClientScanFromBpcOrder(successOrder);
    const collectResult = await settleWithScanRecovery(
      clientScanResult,
      () =>
        httpCollectPrepayAdvanceDeposit({
          encounterId,
          patientId,
          amount: amountNum,
          paymentDraftId,
          paymentDetails: [{ payChannelCode: 'SCAN_PAY', amount: amountNum }],
          clientScanResult,
          operatorId: resolveLoginPractitionerIdForRequest(userStore.userInfo),
        }),
      {
        paymentDraftId,
        refundAmountYuan: amountNum,
      },
    );
    await refreshQueueAfterBalanceChange();
    await queryPrepayRecords();
    ElMessage.success('补结算成功');
    schedulePrepaySlipAfterTrade({
      changeKind: 'collect',
      amount: amountNum,
      payChannelCode: 'SCAN_PAY',
      scanPayType: clientScanResult.payType,
      encounterId,
      paymentId: collectResult?.paymentId ?? paymentDraftId,
    });
  } finally {
    draftSettlePaymentId.value = '';
  }
}

/** 补打预交金收据（按成功收取流水） */
async function handleReprintPrepayReceipt(row: PrepayPaymentRecordDto) {
  if (!canReprintPrepayReceipt(row)) {
    return;
  }
  if (!currentPatient.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const paymentId = String(row.paymentId ?? '').trim();
  reprintPaymentId.value = paymentId;
  try {
    await printInpatientPrepayReceipt({
      source: buildPrepayReceiptSourceFromRecord({
        patient: currentPatient.value,
        record: row,
      }),
      encounterId: selectedEncounterId.value,
      paymentId,
      jobName: '住院预交金收据（补打）',
    });
  } finally {
    reprintPaymentId.value = '';
  }
}

/** 补打退补单（收取为补，退费为退） */
async function handleReprintPrepayChange(row: PrepayPaymentRecordDto) {
  if (!canReprintPrepayChange(row)) {
    return;
  }
  if (!currentPatient.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const paymentId = String(row.paymentId ?? '').trim();
  reprintChangePaymentId.value = paymentId;
  try {
    await printInpatientPrepayChange({
      source: buildPrepayChangeSourceFromRecord({
        patient: currentPatient.value,
        record: row,
      }),
      encounterId: selectedEncounterId.value,
      paymentId,
      jobName: '住院预交金退补单（补打）',
    });
  } finally {
    reprintChangePaymentId.value = '';
  }
}

/** 工具栏：打印最近一笔预交金收据 */
async function handlePrintLatestPrepayReceipt() {
  if (!currentPatient.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const record = resolveLatestCollectRecord(prepayRecordData.value);
  if (!record) {
    ElMessage.warning('暂无成功收取流水，无法打印收据');
    return;
  }
  printLatestLoading.value = true;
  try {
    await printInpatientPrepayReceipt({
      source: buildPrepayReceiptSourceFromRecord({
        patient: currentPatient.value,
        record,
      }),
      encounterId: selectedEncounterId.value,
      paymentId: record.paymentId,
      jobName: '住院预交金收据',
    });
  } finally {
    printLatestLoading.value = false;
  }
}

/** 工具栏：打印最近一笔退补单 */
async function handlePrintLatestPrepayChange() {
  if (!INPATIENT_PREPAY_CHANGE_SLIP_ENABLED) {
    return;
  }
  if (!currentPatient.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const record = resolveLatestChangeRecord(prepayRecordData.value);
  if (!record) {
    ElMessage.warning('暂无成功流水，无法打印退补单');
    return;
  }
  printLatestChangeLoading.value = true;
  try {
    await printInpatientPrepayChange({
      source: buildPrepayChangeSourceFromRecord({
        patient: currentPatient.value,
        record,
      }),
      encounterId: selectedEncounterId.value,
      paymentId: record.paymentId,
      jobName: '住院预交金退补单',
    });
  } finally {
    printLatestChangeLoading.value = false;
  }
}

/** 提交收取预交金并刷新余额与流水（支持组合收款，一渠道一流水）。 */
async function handleCollectPrepay() {
  const paymentDetails = buildCollectPaymentDetailsForSubmit();
  const amount = paymentDetails ? Number(prepayCollectTotalAmount.value) : null;
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  const patientId = preserveApiIdForRequest(currentPatient.value?.patientId);
  if (!encounterId || !patientId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (!canOperatePrepay.value) {
    ElMessage.warning('当前就诊状态不可收取预交金');
    return;
  }
  if (!paymentDetails || amount === null || !(amount > 0)) {
    ElMessage.warning('请填写至少一条支付明细金额');
    return;
  }
  const scanRows = paymentDetails.filter((row) => isScanPayChannel(row.payChannelCode));
  // 扫码渠道至多一条，与后端门禁一致
  if (scanRows.length > 1) {
    ElMessage.warning('扫码支付每次仅支持一条明细');
    return;
  }
  const scanRow = scanRows[0];
  if (scanRow && !prepayForm.scanCode.trim()) {
    ElMessage.warning('请输入付款码');
    return;
  }
  collectSubmitting.value = true;
  scanStatusText.value = '';
  try {
    let paymentDraftId: string | undefined;
    let clientScanResult;
    // 组合收款中仅扫码行走银联预下单；金额用该行而非合计
    if (scanRow) {
      await refreshUnionPayCapabilities();
      if (!scanPayEnabled.value) {
        ElMessage.error('请启动本地客户端（Zxplat.Win）并确认银联组件已安装');
        return;
      }
      scanStatusText.value = '等待支付结果…';
      const scanOutcome = await submitPrepayScanPayment({
        encounterId,
        patientId,
        amount: scanRow.amount,
        scanCode: prepayForm.scanCode,
        payChannelCode: scanRow.payChannelCode,
        operatorId: resolveLoginPractitionerIdForRequest(userStore.userInfo),
        remark: prepayForm.remark.trim() || undefined,
      });
      paymentDraftId = scanOutcome.paymentDraftId;
      clientScanResult = scanOutcome.clientScanResult;
    }

    const collectBody = {
      encounterId,
      patientId,
      amount,
      paymentDetails,
      ...(paymentDraftId ? { paymentDraftId } : {}),
      operatorId: resolveLoginPractitionerIdForRequest(userStore.userInfo),
      remark: prepayForm.remark.trim() || undefined,
    };

    let collectResult: Awaited<ReturnType<typeof httpCollectPrepayAdvanceDeposit>> | undefined;
    if (clientScanResult) {
      collectResult = await settleWithScanRecovery(
        clientScanResult,
        () => httpCollectPrepayAdvanceDeposit({ ...collectBody, clientScanResult }),
        {
          paymentDraftId,
          // 银联原路退按扫码行金额，非组合合计
          refundAmountYuan: scanRow?.amount ?? amount,
        },
      );
    } else {
      collectResult = await httpCollectPrepayAdvanceDeposit(collectBody);
    }
    await refreshQueueAfterBalanceChange();
    resetCollectForm();
    await queryPrepayRecords();
    ElMessage.success('预交金收取成功');
    // 打印取最后一笔（扫码行或末条渠道）；组合收款多票可后续补打
    const printChannel =
      scanRow?.payChannelCode ?? paymentDetails[paymentDetails.length - 1]?.payChannelCode ?? '';
    const printAmount = scanRow?.amount ?? paymentDetails[paymentDetails.length - 1]?.amount ?? amount;
    schedulePrepaySlipAfterTrade({
      changeKind: 'collect',
      amount: printAmount,
      payChannelCode: printChannel,
      scanPayType: clientScanResult?.payType,
      encounterId,
      paymentId: collectResult?.paymentId,
    });
  } finally {
    collectSubmitting.value = false;
  }
}

/** 提交退预交金并刷新余额与流水 */
async function handleConfirmRefund() {
  const refundAmount = parsePositiveAmount(refundForm.amount);
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  const patientId = preserveApiIdForRequest(currentPatient.value?.patientId);
  if (!encounterId || !patientId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (refundAmount === null) {
    ElMessage.warning('请输入正确的退费金额');
    return;
  }
  const refundable = resolveRefundableBalance(currentPatient.value);
  if (refundAmount > refundable) {
    ElMessage.warning('退费金额不能大于可退余额');
    return;
  }
  refundSubmitting.value = true;
  try {
    const originalPaymentId = String(refundForm.originalPaymentId ?? '').trim();
    let clientRefundResult;
    if (originalPaymentId) {
      try {
        clientRefundResult =
          (await submitScanRefund({
            originalPaymentId,
            refundAmountYuan: refundAmount,
          })) ?? undefined;
      } catch (error) {
        const message = error instanceof Error ? error.message : '退费前银联校验失败，请稍后重试';
        ElMessage.error(message);
        return;
      }
    }

    const refundResult = await httpRefundPrepayAdvanceDeposit({
      encounterId,
      patientId,
      refundAmount,
      paymentDetails: [
        {
          payChannelCode: refundForm.payChannelCode,
          amount: refundAmount,
        },
      ],
      ...(originalPaymentId && clientRefundResult ? { originalPaymentId, clientRefundResult } : {}),
      operatorId: resolveLoginPractitionerIdForRequest(userStore.userInfo),
      remark: refundForm.remark.trim() || undefined,
    });
    if (originalPaymentId) {
      clearCachedClientRefundResult(originalPaymentId);
    }
    await refreshQueueAfterBalanceChange();
    refundDialogVisible.value = false;
    await queryPrepayRecords();
    ElMessage.success('预交金退费成功');
    schedulePrepaySlipAfterTrade({
      changeKind: 'refund',
      amount: refundAmount,
      payChannelCode: refundForm.payChannelCode,
      encounterId,
      paymentId: refundResult?.paymentId,
    });
  } finally {
    refundSubmitting.value = false;
  }
}

watch(
  () => [selectedEncounterId.value, queueData.value] as const,
  () => {
    void queryPrepayRecords();
  },
);

watch(
  () => [queueFilter.keyword, queueFilter.admissionDate, queueFilter.stayScope],
  () => {
    queuePager.pageNo = 1;
    queryQueueData();
  },
);

onMounted(async () => {
  const initPromise = (async () => {
    try {
      const init = await httpLoadInpatientPrepayPageInit({});
      payChannelOptions.value = optionDtosToPayChannelOptions(init.paymentChannelOptions);
    } catch {
      payChannelOptions.value = [...PAY_CHANNEL_OPTIONS];
    }
  })();
  const queuePromise = queryQueueData();
  const unionPayPromise = refreshUnionPayCapabilities();
  await Promise.all([initPromise, queuePromise, unionPayPromise]);
  await queryPrepayRecords();
});
</script>

<template>
  <div class="flex h-full min-h-0 flex-col overflow-hidden">
    <StationTriPaneLayout
      :show-right="false"
      :show-footer="false"
      class="charge-inpatient-prepay-workbench min-h-0 flex-1"
      left-track="minmax(300px, 0.34fr)"
      center-track="minmax(0, 1fr)"
    >
      <template #left>
        <InpatientQueuePanel
          v-model:keyword="queueFilter.keyword"
          v-model:date="queueFilter.admissionDate"
          v-model:stay-scope="queueFilter.stayScope"
          :list="queueCardList"
          :selected-id="selectedEncounterId"
          :loading="queueLoading"
          @select="handleChoosePatient"
          @refresh="handleRefreshQueue"
        />
      </template>

      <template #center>
        <ElCard
          shadow="never"
          class="flex h-full min-h-0 flex-col overflow-hidden rounded-lg"
          body-class="flex-1 min-h-0 overflow-hidden !p-2"
        >
          <div class="flex h-full min-h-0 flex-col gap-3 overflow-hidden">
            <SectionTitle title="患者信息" />
            <div class="patient-info-bar">
              <div class="patient-info-grid">
                <div class="patient-info-item">
                  <span class="patient-info-label">住院号</span>
                  <span class="patient-info-value">{{
                    currentPatient?.encounterBusNo || '-'
                  }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">姓名</span>
                  <span class="patient-info-value">{{ currentPatient?.patientName || '-' }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">科室</span>
                  <span class="patient-info-value">{{
                    currentPatient?.organizationName || '-'
                  }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">病区</span>
                  <span class="patient-info-value">{{ currentPatient?.wardName || '-' }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">床号</span>
                  <span class="patient-info-value">{{ currentPatient?.bedName || '-' }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">{{
                    resolveInpatientAccountBalanceLabel(currentPatient)
                  }}</span>
                  <span
                    class="patient-info-value"
                    :class="currentPatient?.lowBalanceWarning ? 'text-red-600' : 'text-blue-600'"
                  >
                    {{ formatInpatientAccountBalancePrimaryText(currentPatient) }}
                    <span
                      v-if="formatInpatientAccountBalanceSecondaryText(currentPatient)"
                      class="ml-1 text-xs text-[var(--el-text-color-secondary)]"
                    >
                      {{ formatInpatientAccountBalanceSecondaryText(currentPatient) }}
                    </span>
                  </span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">就诊状态</span>
                  <ElTag :type="currentPatientStatusTagType" size="small">
                    {{ currentPatient?.statusEnumText || '未选择' }}
                  </ElTag>
                  <ElTag
                    v-if="currentPatient?.lowBalanceWarning"
                    type="danger"
                    size="small"
                    class="ml-1"
                  >
                    欠费预警
                  </ElTag>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">费用性质</span>
                  <span class="patient-info-value">{{
                    currentPatient?.contractDisplay || '-'
                  }}</span>
                </div>
              </div>
            </div>

            <div class="prepay-toolbar">
              <div class="flex items-center gap-2">
                <SectionTitle title="预交金办理" />
                <div class="text-xs text-[var(--el-text-color-regular)]">快捷金额</div>
                <ElButton
                  v-for="item in quickAmounts"
                  :key="item"
                  size="small"
                  plain
                  :disabled="!canOperatePrepay"
                  @click="handleApplyQuickAmount(item)"
                >
                  {{ item }}
                </ElButton>
              </div>
              <div class="flex items-center gap-2">
                <ElButton
                  type="primary"
                  :loading="collectSubmitting"
                  :disabled="!canOperatePrepay"
                  @click="handleCollectPrepay"
                >
                  收取预交金
                </ElButton>
                <ElButton plain :disabled="!canOperatePrepay" @click="handleOpenRefundDialog">
                  退预交金
                </ElButton>
                <ElButton
                  plain
                  :disabled="!canPrintLatestPrepayReceipt"
                  :loading="printLatestLoading"
                  @click="handlePrintLatestPrepayReceipt"
                >
                  收据
                </ElButton>
                <ElButton
                  v-if="INPATIENT_PREPAY_CHANGE_SLIP_ENABLED"
                  plain
                  :disabled="!canPrintLatestPrepayChange"
                  :loading="printLatestChangeLoading"
                  @click="handlePrintLatestPrepayChange"
                >
                  退补单
                </ElButton>
              </div>
            </div>

            <ElForm :model="prepayForm" class="prepay-form">
              <ElFormItem label="支付明细" required>
                <div class="prepay-pay-rows">
                  <div
                    v-for="(row, index) in prepayForm.paymentDetailRows"
                    :key="`prepay-pay-row-${index}`"
                    class="prepay-pay-row"
                  >
                    <ElSelect
                      v-model="row.payChannelCode"
                      class="prepay-pay-row__channel"
                      filterable
                      :disabled="!canOperatePrepay"
                    >
                      <ElOption
                        v-for="item in effectivePayChannelOptions"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </ElSelect>
                    <ElInputNumber
                      v-model="row.amount"
                      :precision="2"
                      :step="100"
                      :min="0"
                      controls-position="right"
                      :disabled="!canOperatePrepay"
                    />
                    <ElButton
                      link
                      type="danger"
                      :disabled="!canOperatePrepay || prepayForm.paymentDetailRows.length <= 1"
                      @click="removePaymentDetailRow(index)"
                    >
                      删除
                    </ElButton>
                  </div>
                  <div class="prepay-pay-rows__footer">
                    <ElButton
                      link
                      type="primary"
                      :disabled="!canOperatePrepay"
                      @click="addPaymentDetailRow"
                    >
                      添加支付方式
                    </ElButton>
                    <span class="prepay-pay-rows__total">
                      合计 {{ formatMoneyDisplay(prepayCollectTotalAmount) }} 元
                    </span>
                  </div>
                </div>
              </ElFormItem>
              <ElFormItem
                v-if="hasScanPayChannel && hasScanPayInCollectRows"
                label="付款码"
                required
              >
                <ElInput
                  v-model="prepayForm.scanCode"
                  placeholder="请扫码或输入付款码（仅扫码行金额）"
                  class="w-80"
                  clearable
                  :disabled="!canOperatePrepay"
                />
                <p v-if="scanStatusText" class="mt-1 text-[13px] text-blue-600">
                  {{ scanStatusText }}
                </p>
                <p
                  v-if="exclusiveScanPayRowAmount == null && hasScanPayInCollectRows"
                  class="mt-1 text-[12px] text-[var(--el-color-warning)]"
                >
                  扫码支付每次仅支持一条明细，且金额须大于 0
                </p>
              </ElFormItem>
              <ElFormItem label="备注">
                <ElInput
                  v-model="prepayForm.remark"
                  placeholder="可选填写"
                  class="w-80"
                  clearable
                  :disabled="!canOperatePrepay"
                />
              </ElFormItem>
            </ElForm>

            <div class="min-h-0 flex-1 overflow-hidden">
              <SectionTitle class="mb-2" title="预交金流水" />
              <RegisterStyleTableLayout
                class="!p-0"
                :table-data="prepayRecordData"
                :loading="tableLoading"
                :total="prepayRecordData.length"
                :query-params="recordTableQueryParams"
                :table-columns="recordTableColumns"
                :show-top-query="false"
                :show-pagination="false"
              >
                <template #amount="{ row }">
                  <span :class="row.paymentEnum === 'UN_PAY' ? 'text-orange-600' : ''">
                    {{ formatPrepayRecordAmount(row) }}
                  </span>
                </template>
                <template #status="{ row }">
                  <ElTag :type="row.statusEnum === 'SUCCESS' ? 'success' : 'info'" effect="light">
                    {{ row.statusEnumText }}
                  </ElTag>
                </template>
                <template #action="{ row }">
                  <div class="flex flex-wrap items-center justify-center gap-1">
                    <ElButton
                      v-if="canCompleteDraftSettlement(row)"
                      type="primary"
                      link
                      :loading="draftSettlePaymentId === String(row.paymentId)"
                      :disabled="!canOperatePrepay || collectSubmitting"
                      @click="handleCompleteDraftSettlement(row)"
                    >
                      补结算
                    </ElButton>
                    <ElButton
                      v-if="canReprintPrepayReceipt(row)"
                      type="primary"
                      link
                      :loading="reprintPaymentId === String(row.paymentId)"
                      @click="handleReprintPrepayReceipt(row)"
                    >
                      收据
                    </ElButton>
                    <ElButton
                      v-if="canReprintPrepayChange(row)"
                      type="primary"
                      link
                      :loading="reprintChangePaymentId === String(row.paymentId)"
                      @click="handleReprintPrepayChange(row)"
                    >
                      退补单
                    </ElButton>
                    <span
                      v-if="
                        !canCompleteDraftSettlement(row) &&
                        !canReprintPrepayReceipt(row) &&
                        !canReprintPrepayChange(row)
                      "
                      class="text-[var(--el-text-color-placeholder)]"
                    >
                      —
                    </span>
                  </div>
                </template>
              </RegisterStyleTableLayout>
            </div>
          </div>
        </ElCard>
      </template>
    </StationTriPaneLayout>

    <AppDialog v-model="refundDialogVisible" title="退预交金" width="520px">
      <ElForm :model="refundForm" label-width="96px">
        <ElFormItem label="退费金额" required>
          <ElInput v-model="refundForm.amount" placeholder="请输入退费金额" clearable />
        </ElFormItem>
        <ElFormItem v-if="collectPaymentOptions.length > 0" label="原收取流水">
          <ElSelect
            v-model="refundForm.originalPaymentId"
            placeholder="扫码原路退时选择原支付单"
            clearable
            class="w-full"
          >
            <ElOption
              v-for="item in collectPaymentOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
          <p class="mt-1 text-[12px] text-[var(--el-text-color-secondary)]">
            若原收取为扫码支付，请选择对应流水以发起银联退货
          </p>
        </ElFormItem>
        <ElFormItem label="退现渠道" required>
          <ElRadioGroup v-model="refundForm.payChannelCode">
            <ElRadio v-for="item in payChannelOptions" :key="item.value" :label="item.value">
              {{ item.label }}
            </ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="refundForm.remark" placeholder="可选填写" clearable />
        </ElFormItem>
        <ElFormItem label="可退余额">
          <span class="text-[var(--el-text-color-primary)]">
            {{ formatCurrencyAmount(resolveRefundableBalance(currentPatient)) }}
          </span>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="refundDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="refundSubmitting" @click="handleConfirmRefund">
          确认退费
        </ElButton>
      </template>
    </AppDialog>
  </div>
</template>
