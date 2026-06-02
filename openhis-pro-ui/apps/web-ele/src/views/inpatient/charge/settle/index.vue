<script setup lang="ts">
import type { OptionDto } from '#/api/inpatient-registration';
import type {
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
  ElRadio,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import {
  httpConfirmInpatientSettle,
  httpLoadInpatientSettlePageInit,
  httpLoadInpatientSettleSummary,
  httpLoadSettledInpatientPaymentView,
  httpPageInpatientSettleQueue,
  httpPreSettleInpatientCharges,
  httpQueryInpatientSettleCharges,
} from '#/api/inpatient-settle';
import AppDialog from '#/components/app-dialog.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import SelectTable from '#/components/select-table.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

import {
  FALLBACK_DEFAULT_PAY_CHANNEL,
  SETTLE_PRESENTATION_MODE,
  SETTLE_QUEUE_TAB,
} from './inpatient-settle.constants';

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
  reimbursementAmount: string;
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
  return [...base, { prop: 'pendingSettlementTotal', label: '待结算合计', width: 110 }];
});

const paymentMethod = ref<'微信' | '现金' | '银行卡'>('微信');
const payChannelCodeByLabel = ref<Record<string, string>>({ ...PAY_LABEL_TO_CODE });

const queueRecords = ref<InpatientSettleQueueItemDto[]>([]);
const queueRowByEncounterId = ref<Record<string, InpatientSettleQueueItemDto>>({});
const summary = ref<LoadInpatientSettleSummaryResponse | null>(null);
const chargeLines = ref<InpatientSettleChargeLineDto[]>([]);
const settlementHistoryRows = ref<SettlementHistoryRow[]>([]);

const preSettleSnapshot = ref<null | PreSettleInpatientChargesResponse>(null);
const confirmSettleLoading = ref(false);
const workspaceLoading = ref(false);

const settlementDetailVisible = ref(false);
const selectedSettlementHistory = ref<null | SettlementHistoryRow>(null);
const historyDetailRows = ref<FeeDetailRow[]>([]);
/** 程序内切换页签（如出院全结后）时跳过 watch 清空选中 */
const skipQueueTabWatchReset = ref(false);

const printActions = ['打印住院收费票据', '打印费用总清单', '打印医保结算单'];

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

/** 当前选中费用明细 ID（默认勾选待结算行） */
const selectedChargeItemIds = computed(() =>
  chargeLines.value.filter((line) => line.selected).map((line) => line.chargeItemId),
);

const categoryDisplayRows = ref<FeeCategoryRow[]>([]);

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
    reimbursementAmount: '—',
    selfPayAmount: settledMode
      ? formatCurrencyDisplay(settled.receivableTotal)
      : formatCurrencyDisplay(s?.selectedTotal),
    prepayTotal: settledMode ? '—' : formatCurrencyDisplay(s?.prepayNetBalance),
    refundOrSupplement: settledMode ? '—' : formatBalanceHintDisplay(s?.balanceHint),
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

const coreSettlementSummary = computed(() => [
  { label: '住院总费用', value: currentPatientDisplay.value.totalFee },
  { label: '医保报销金额', value: currentPatientDisplay.value.reimbursementAmount },
  { label: '个人自费金额', value: currentPatientDisplay.value.selfPayAmount },
  { label: '预交押金总额', value: currentPatientDisplay.value.prepayTotal },
  { label: '应补金额 / 应退金额', value: currentPatientDisplay.value.refundOrSupplement },
]);

const insuranceKeyInfoRows = computed<InsuranceInfoRow[]>(() => [
  { field: '医保内费用', value: currentPatientDisplay.value.insuranceInFee },
  { field: '医保外自费项目合计', value: currentPatientDisplay.value.insuranceOutFee },
  { field: '本次医保结算单号', value: currentPatientDisplay.value.settlementNo },
]);

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
    const map: Record<string, string> = { ...PAY_LABEL_TO_CODE };
    for (const opt of init.paymentChannelOptions ?? []) {
      if (opt.label && opt.value) {
        map[opt.label] = opt.value;
      }
    }
    payChannelCodeByLabel.value = map;
  } catch {
    ElMessage.error('结算页初始化失败');
  }
}

/** 清空当前选中患者与工作区展示 */
function resetSettleWorkspace() {
  summary.value = null;
  settledPaymentView.value = null;
  chargeLines.value = [];
  categoryDisplayRows.value = [];
  settlementHistoryRows.value = [];
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
}

/** 待结算页签工作区：费用 + 摘要 */
async function loadPendingWorkspace() {
  settledPaymentView.value = null;
  await loadPendingCharges();
  // 待结算就诊未 COMPLETED，不走 IF-IP-STL-07
  settlementHistoryRows.value = [];
}

/** 支付流水转历史结算表格行 */
function mapPaymentToHistoryRow(
  payment: SettledPaymentBriefDto,
  view: { operatorName?: string },
): SettlementHistoryRow {
  return {
    paymentId: payment.paymentId,
    settlementNo: payment.paymentId,
    settleTime: payment.settleTime ?? '—',
    settleType: '—',
    totalAmount: formatCurrencyDisplay(payment.receivableTotal),
    reimbursementAmount: '—',
    selfPayAmount: formatCurrencyDisplay(payment.receivableTotal),
    paymentMethod: payment.payMethodText ?? '—',
    operatorName: view.operatorName ?? '—',
    ticketNo: '—',
    status: '已结清',
    detailRows: [],
  };
}

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
    ElMessage.info('医保校验本期未接入');
    return;
  }
  ElMessage.success(`${actionLabel}已触发`);
}

/** 明细表勾选变更后重算待结算摘要 */
function handleFeeDetailSelectionChange(rows: FeeDetailRow[]) {
  if (isSettledQueueTab.value) {
    return;
  }
  const idSet = new Set(rows.map((r) => r.chargeItemId));
  chargeLines.value = chargeLines.value.map((line) => ({
    ...line,
    selected: idSet.has(line.chargeItemId),
  }));
  void loadSummary();
}

/** 底部结算与打印 */
async function handleFooterAction(actionLabel: string) {
  if (actionLabel === '确认结算') {
    await handleConfirmSettle();
    return;
  }
  ElMessage.info(`${actionLabel}功能待接入`);
}

/** 预结算并正式结算 */
async function handleConfirmSettle() {
  const { encounterId, patientId } = resolveActiveSettlePatientIds();
  if (!encounterId || !patientId) {
    ElMessage.warning('请先搜索并选择患者');
    return;
  }
  const chargeItemIds = selectedChargeItemIds.value;
  if (chargeItemIds.length === 0) {
    ElMessage.warning('请选择待结算费用');
    return;
  }
  confirmSettleLoading.value = true;
  try {
    const pre = await httpPreSettleInpatientCharges({
      encounterId,
      patientId,
      chargeItemIds,
    });
    preSettleSnapshot.value = pre;
    const confirmRes = await httpConfirmInpatientSettle({
      billDate: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      encounterId,
      patientId,
      paymentDraftId: pre.paymentDraftId,
      chargeItemIds,
      paymentDetails: [
        {
          amount: pre.receivableTotal,
          payChannelCode: resolvePayChannelCode(),
        },
      ],
    });
    ElMessage.success('住院结算成功');
    preSettleSnapshot.value = null;
    // 出院全结后就诊变 COMPLETED，须切到已结算页签才能继续查阅
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
  <Page class="settle-page bg-[var(--el-fill-color-light)] p-3" v-loading="workspaceLoading">
    <div class="flex h-full min-h-0 flex-col gap-3 overflow-hidden">
      <ElCard shadow="never" body-class="!p-3">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="flex min-w-0 flex-col gap-2">
            <ElRadioGroup v-model="settleQueueTab" class="settle-queue-tab">
              <ElRadio
                v-for="opt in settleQueueTabRadioOptions"
                :key="opt.value"
                :value="opt.value"
              >
                {{ opt.label }}
              </ElRadio>
            </ElRadioGroup>
            <SelectTable
              class="settle-patient-select"
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
          <div class="flex items-center gap-2">
            <ElButton plain @click="handleToolbarAction('刷新费用')">刷新费用</ElButton>
            <ElButton plain @click="handleToolbarAction('医保校验')">医保校验</ElButton>
          </div>
        </div>

        <div
          class="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-[var(--el-text-color-primary)]"
        >
          <span><strong>住院号：</strong>{{ currentPatientDisplay.inpatientNo }}</span>
          <span><strong>姓名：</strong>{{ currentPatientDisplay.name }}</span>
          <span
            ><strong>性别/年龄：</strong>{{ currentPatientDisplay.gender }}/{{
              currentPatientDisplay.age
            }}</span
          >
          <span><strong>床位：</strong>{{ currentPatientDisplay.bedNo }}</span>
          <span><strong>科室：</strong>{{ currentPatientDisplay.department }}</span>
          <span><strong>病区：</strong>{{ currentPatientDisplay.wardName }}</span>
          <span><strong>入院日期：</strong>{{ currentPatientDisplay.admissionDate }}</span>
          <span><strong>医保类型：</strong>{{ currentPatientDisplay.insuranceType }}</span>
          <span><strong>主治医生：</strong>{{ currentPatientDisplay.doctorName }}</span>
        </div>
      </ElCard>

      <div class="settle-main-layout min-h-0 flex-1">
        <div class="settle-main-left flex min-h-0 flex-col gap-3">
          <ElCard shadow="never" body-class="!p-3">
            <SectionTitle title="费用合计" />
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
            class="min-h-0 flex-1 overflow-hidden"
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
                    @selection-change="handleFeeDetailSelectionChange"
                  >
                    <ElTableColumn
                      v-if="!isSettledQueueTab"
                      type="selection"
                      width="48"
                      align="center"
                    />
                    <ElTableColumn type="index" label="序号" width="60" align="center" />
                    <ElTableColumn prop="orderNo" label="医嘱号" min-width="150" />
                    <ElTableColumn prop="itemName" label="项目名称" min-width="220" />
                    <ElTableColumn prop="category" label="费用类别" min-width="140" />
                    <ElTableColumn prop="spec" label="规格" min-width="120" />
                    <ElTableColumn prop="price" label="单价" min-width="100" align="right" />
                    <ElTableColumn prop="quantity" label="数量" min-width="80" align="center" />
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
          class="settle-main-right min-h-0 overflow-hidden"
          body-class="settle-history-card-body h-full !p-0"
        >
          <div class="settle-detail-panel settle-detail-panel--history">
            <div class="flex items-center justify-between gap-2 px-3 pt-3">
              <SectionTitle title="历史结算记录" />
              <span class="text-xs text-[var(--el-text-color-secondary)]"
                >共 {{ currentPatientDisplay.settlementHistoryRows.length }} 条</span
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
                <ElTableColumn prop="settlementNo" label="结算单号" min-width="150" />
                <ElTableColumn prop="settleTime" label="结算时间" min-width="160" />
                <ElTableColumn prop="settleType" label="结算类型" width="100" align="center" />
                <ElTableColumn prop="totalAmount" label="结算金额" min-width="120" align="right" />
                <ElTableColumn
                  prop="reimbursementAmount"
                  label="医保报销"
                  min-width="120"
                  align="right"
                />
                <ElTableColumn
                  prop="selfPayAmount"
                  label="个人自付"
                  min-width="110"
                  align="right"
                />
                <ElTableColumn prop="paymentMethod" label="支付方式" width="90" align="center" />
                <ElTableColumn prop="operatorName" label="操作员" width="100" align="center" />
                <ElTableColumn prop="ticketNo" label="票据号" min-width="140" />
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
                <ElTableColumn label="明细" width="72" fixed="right" align="center">
                  <template #default="{ row }">
                    <ElButton type="primary" link @click="handleOpenSettlementDetail(row)">
                      明细
                    </ElButton>
                  </template>
                </ElTableColumn>
              </ElTable>
            </div>
          </div>
        </ElCard>
      </div>

      <ElCard shadow="never" body-class="!p-3">
        <div class="payment-footer-layout">
          <div class="payment-footer-main">
            <SectionTitle title="结算支付信息" />
            <div class="payment-footer__line">
              <span class="payment-footer__label">实际支付方式：</span>
              <ElRadioGroup v-model="paymentMethod">
                <ElRadio label="现金" value="现金" />
                <ElRadio label="微信" value="微信" />
                <ElRadio label="银行卡" value="银行卡" />
              </ElRadioGroup>
            </div>
            <div class="payment-footer__line">
              <span class="payment-footer__label">结算时间：</span>
              <span>{{ currentPatientDisplay.paymentTime }}</span>
              <span class="payment-footer__label ml-4">操作员：</span>
              <span>{{ currentPatientDisplay.operatorName }}</span>
              <span class="payment-footer__label ml-4">结算状态：</span>
              <ElTag
                :type="currentPatientDisplay.settlementStatus === '已结清' ? 'success' : 'warning'"
                effect="plain"
              >
                {{ currentPatientDisplay.settlementStatus }}
              </ElTag>
            </div>
          </div>

          <div class="payment-footer-actions">
            <ElButton
              v-for="action in printActions"
              :key="action"
              plain
              @click="handleFooterAction(action)"
            >
              {{ action }}
            </ElButton>
            <ElButton
              v-if="!isSettledQueueTab"
              type="primary"
              :loading="confirmSettleLoading"
              @click="handleFooterAction('确认结算')"
            >
              确认结算
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
  </Page>
</template>

<style scoped>
.settle-page :deep(.el-card__body) {
  height: 100%;
}

.settle-page :deep(.el-card) {
  border-radius: 4px;
  border-color: #dcdfe6;
  box-shadow: none;
}

.settle-page :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.settle-page :deep(.el-table th.el-table__cell) {
  background: #f5f7fa;
}

.settle-page :deep(.el-select),
.settle-page :deep(.el-date-editor) {
  width: 100%;
}

.settle-patient-select {
  width: 420px;
  min-width: 280px;
}

.settle-queue-tab {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
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
  grid-template-columns: minmax(0, 55fr) minmax(0, 45fr);
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
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: #fff;
  padding: 10px;
}

.summary-item__label {
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 600;
}

.summary-item__value {
  margin-top: 8px;
  color: var(--el-color-primary);
  font-size: 20px;
  font-weight: 600;
}

.settle-history-detail-summary {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.payment-footer-layout {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: space-between;
}

.payment-footer-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
}

.payment-footer__line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.payment-footer__label {
  color: var(--el-text-color-secondary);
}

.payment-footer-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
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
}
</style>
