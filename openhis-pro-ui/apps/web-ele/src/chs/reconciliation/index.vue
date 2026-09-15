<script setup lang="ts">
import type {
  ClearAmountSummary,
  LocalClearApplyRecord,
  LocalReconDailyRecord,
  LocalReconDetailRecord,
} from '#/chs/api';
import type { WorkInjuryReconSettlementRow } from '#/chs/api';
import type { ChargeType } from '#/api/core/common';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElCol,
  ElDatePicker,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRow,
  ElSelect,
  ElStep,
  ElSteps,
  ElTabPane,
  ElTabs,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  applyJilinClearApi,
  listLocalClearAppliesApi,
  listLocalReconDailyRecordsApi,
  listLocalReconDetailRecordsApi,
  confirmWorkInjuryReconDailyBalancedApi,
  listWorkInjuryReconSettlementsApi,
  revokeJilinClearApi,
  saveWorkInjuryReconDailyPlatformApi,
  startWorkInjuryReconApi,
  mergeClearAmountSummaries,
  summarizeClearAmountsApi,
  summarizeWorkInjuryReconByDayRangeApi,
} from '#/chs/api';
import {
  formatClrStas,
  formatClrType,
  formatInsutype,
  formatStmtRslt,
  isStmtBalanced,
  YB_CLR_TYPE_OPTIONS,
} from '#/chs/constants/reconciliation';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
import { useRequest } from '#/hooks/use-request';
import { continueWorkInjurySession } from '#/chs/work-injury/run-work-injury-phase';
import {
  invokeWorkInjuryPlugin,
  isWorkInjuryPluginOnline,
  WorkInjuryPluginError,
} from '#/chs/work-injury/work-injury-plugin';
import { ensureWorkInjurySignIn } from '#/chs/work-injury/ensure-work-injury-sign-in';
import { normalizeWorkInjuryWinOutput } from '#/chs/work-injury/probe-work-injury-filing';
import {
  httpWorkInjuryModuleCapability,
  httpWorkInjurySubmitInvokeResult,
} from '#/chs/work-injury/work-injury.http';
import {
  buildDefaultDateRangeShortcuts,
  DATE_PICKER_SHORTCUTS_BOTTOM_POPPER_CLASS,
} from '#/utils/format/date-picker-shortcuts';
import { isWorkInjuryContractCategory } from '#/views/outpatient/charge/register/services/charge-type-category';
import { isWorkInjuryUiEnabled } from '#/utils/project-optional-modules';
import AppDialog from '#/components/app-dialog.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import DailyLedgerList from '#/chs/reconciliation/components/daily-ledger-list.vue';
import {
  RECON_STATUS,
  RECON_STATUS_LABEL,
  type ReconStatusCode,
} from '#/chs/reconciliation/components/daily-ledger-types';
import {
  formatClearOrgCode,
  formatClearOrgOptionLabel,
} from '#/chs/reconciliation/format-clear-org';

defineOptions({ name: 'ChsReconciliationPage' });

const activeTab = ref<'clear' | 'daily' | 'workInjury'>('daily');
/** 月清算子分段：申请主路径 / 记录与撤销 */
const clearSubTab = ref<'apply' | 'records'>('apply');
/** 日对账本地记录默认收起，避免抢主表视线 */
const dailyLocalExpanded = ref(false);
const contractBusNo = ref<string>();
const chargeTypeOptions = ref<ChargeType[]>([]);

const dateFieldProps = {
  type: 'daterange' as const,
  valueFormat: 'YYYY-MM-DD',
  rangeSeparator: '至',
  startPlaceholder: '开始',
  endPlaceholder: '结束',
  clearable: true,
  shortcuts: buildDefaultDateRangeShortcuts(),
  popperClass: DATE_PICKER_SHORTCUTS_BOTTOM_POPPER_CLASS,
};

function formatMoney(value?: number | string | null): string {
  if (value == null || value === '') return '—';
  const n = Number(value);
  return Number.isFinite(n) ? n.toFixed(2) : '—';
}

/** 记录表表头一字排开，禁止折行 */
const clearRecordHeaderCellStyle = {
  whiteSpace: 'nowrap',
  wordBreak: 'keep-all',
} as const;

/** 记录页上下表同宽：表头按 16px 加粗一字不折 */
const CLEAR_RECORD_COL = {
  evtId: 248,
  clrType: 104,
  org: 104,
  stas: 88,
  medfee: 124,
  fund: 100,
  cash: 112,
  acct: 88,
  time: 176,
  action: 76,
} as const;

/** 清算记录时间展示为 YYYY-MM-DD HH:mm:ss */
function formatClearRecordTime(value?: string | null): string {
  if (!value) return '—';
  const text = String(value).replace('T', ' ').trim();
  return text.slice(0, 19) || '—';
}

/** 取记录对账日期（开始日优先） */
function recordReconDate(row: LocalReconDailyRecord): string {
  return (row.stmtBegndate || row.stmtEnddate || '').slice(0, 10);
}

/** 记录是否总账不平（按 stmtRslt code） */
function isRecordUnbalanced(row: LocalReconDailyRecord): boolean {
  return !isStmtBalanced(row.stmtRslt);
}

const detailDrawerVisible = ref(false);
const detailDrawerTitle = ref('明细核对');
const detailDrawerMeta = ref('');
const detailDrawerRecords = ref<LocalReconDetailRecord[]>([]);

/** 从本地日账打开关联的明细对账流水（真实 listLocal） */
function openDetailDrawer(row: LocalReconDailyRecord) {
  const dateText = recordReconDate(row) || '—';
  detailDrawerMeta.value = [
    formatInsutype(row.insutype),
    formatClrType(row.clrType),
    dateText,
    row.stmtRsltDscr || '',
  ]
    .filter(Boolean)
    .join(' · ');
  detailDrawerTitle.value = isRecordUnbalanced(row) ? '明细核对（有差异）' : '明细核对';
  // 按对账日 + 清算类别匹配已落库的 3202 流水
  detailDrawerRecords.value = detailLocalRecords.value.filter((item) => {
    const begin = (item.stmtBegndate || '').slice(0, 10);
    const end = (item.stmtEnddate || begin).slice(0, 10);
    if (!begin || dateText === '—') return false;
    if (begin > dateText || end < dateText) return false;
    if (row.clrType && item.clrType && item.clrType !== row.clrType) return false;
    return true;
  });
  detailDrawerVisible.value = true;
}

// —— 日对账本地记录 ——
const dailyLocalRecords = ref<LocalReconDailyRecord[]>([]);
const detailLocalRecords = ref<LocalReconDetailRecord[]>([]);
/** 与左侧对账区间对齐，供本地记录按月过滤 */
const dailyReconRange = ref<[string, string]>();
const dailyReconDate = ref<string>();

/** 同一对账维度只保留最新一条「平」记录，避免重复对账刷屏 */
function pickLatestBalancedPerDim(rows: LocalReconDailyRecord[]): LocalReconDailyRecord[] {
  const best = new Map<string, LocalReconDailyRecord>();
  for (const row of rows) {
    if (!isStmtBalanced(row.stmtRslt)) continue;
    const begin = (row.stmtBegndate || '').slice(0, 10);
    const end = (row.stmtEnddate || begin).slice(0, 10);
    const key = `${begin}|${end}|${row.insutype || ''}|${row.clrType || ''}`;
    const prev = best.get(key);
    if (!prev || String(row.createdAt || '') > String(prev.createdAt || '')) {
      best.set(key, row);
    }
  }
  return [...best.values()].sort((a, b) => {
    const byDay = (a.stmtBegndate || '').localeCompare(b.stmtBegndate || '');
    if (byDay !== 0) return byDay;
    return (a.insutype || '').localeCompare(b.insutype || '');
  });
}
function pad2(n: number) {
  return String(n).padStart(2, '0');
}

function formatYmd(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

/** 默认对账日：昨天（与左侧日对账列表一致） */
function defaultDailyReconDate(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return formatYmd(d);
}

/** 默认清算区间：上个月 1 日～月末 */
function defaultLastMonthRange(): [string, string] {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const end = new Date(now.getFullYear(), now.getMonth(), 0);
  return [formatYmd(start), formatYmd(end)];
}

/** 默认清算月：上个月 yyyyMM（状态查询用） */
function defaultLastMonthYm(): string {
  const now = new Date();
  const d = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  return `${d.getFullYear()}${pad2(d.getMonth() + 1)}`;
}

// —— 月清算 ——
const clearDateRange = ref<[string, string]>(defaultLastMonthRange());
const clearClrType = ref('11');
const clearSummary = ref<ClearAmountSummary | null>(null);
const clearStatusYm = ref(defaultLastMonthYm());
const clearLocalRecords = ref<LocalClearApplyRecord[]>([]);
const clearAmountForm = ref({
  medfeeSumamt: undefined as number | undefined,
  fundAppySum: undefined as number | undefined,
  cashPayamt: undefined as number | undefined,
  acctPay: undefined as number | undefined,
});

/** 快捷切回上个月区间 */
function setClearDateLastMonth() {
  clearDateRange.value = defaultLastMonthRange();
  clearStatusYm.value = defaultLastMonthYm();
}

/** 从清算机构（契约 busNo）解析医保清算经办机构编码 */
function requireClrOptins(): string {
  const code = formatClearOrgCode(contractBusNo.value);
  if (!code) {
    throw new Error('请选择清算机构');
  }
  return code;
}

/** 清算状态 Tag 类型 */
function clearStasTagType(stas?: string): 'info' | 'success' | 'warning' {
  if (stas === '10') return 'warning';
  if (stas === '20') return 'success';
  return 'info';
}

/** 事件号排序：无号的排后面 */
function compareClearEvtId(a?: string | null, b?: string | null): number {
  const left = (a ?? '').trim();
  const right = (b ?? '').trim();
  if (!left && !right) return 0;
  if (!left) return 1;
  if (!right) return -1;
  return left.localeCompare(right);
}

/** 本地申请所属清算月 yyyyMM（用开始日，不用申请时间） */
function localRecordClearYm(row: LocalClearApplyRecord): string {
  return (row.begndate || '').replaceAll('-', '').slice(0, 6);
}

/** 清算机构编码转省市医保名称 */
function formatProvCityInsurance(clrOptins?: string | null): string {
  const code = formatClearOrgCode(clrOptins);
  if (!code) return '—';
  const hit = chargeTypeOptions.value.find((item) => formatClearOrgCode(item.busNo) === code);
  const name = (hit?.label ?? '').trim();
  return name || code;
}

/** 本地表：按清算月过滤后按事件号排序 */
const linkedClearLocalRecords = computed(() => {
  const ym = (clearStatusYm.value ?? '').trim();
  const rows = clearLocalRecords.value.filter((row) => {
    if (!ym) return true;
    return localRecordClearYm(row) === ym;
  });
  return rows.sort((a, b) => compareClearEvtId(a.clrAppyEvtId, b.clrAppyEvtId));
});

interface WiChargeTypeOption {
  contractId: string;
  label: string;
  busNo: string;
}

const wiModuleEnabled = ref(false);
const wiContractOptions = ref<WiChargeTypeOption[]>([]);
const wiContractId = ref<string>();
/** 总额对账日期区间，默认当天（可查当日已结费用） */
const wiDateRange = ref<[string, string]>([formatYmd(new Date()), formatYmd(new Date())]);
const wiSocialPoolCode = ref<string>('220199');
/** 可选 1320 平台口径（单选，不再三口径连打） */
const wi1320Scope = ref<'LOCAL' | 'QGYD' | 'SNYD'>('LOCAL');
/** 对账确认月：默认上月 yyyyMM（平台不允许确认未结束月份） */
const wiMonth = ref<string | undefined>(defaultLastMonthYm());
const wiHisLoading = ref(false);
const wi1320Loading = ref(false);
const wi1322Loading = ref(false);
const wiLastSummary = ref<string>();
/** 工伤三步：1 结算统计 → 2 总额对账 → 3 对账确认 */
const wiStep = ref<1 | 2 | 3>(1);
const wiHisAmounts = ref({
  medfeeSumamt: 0,
  hifpPay: 0,
  psnCashPay: 0,
  setlCount: 0,
});
/** 收银侧汇总（来自 payment 明细，非平台 1320） */
const wiPaymentAmounts = ref({
  medfeeSumamt: undefined as number | undefined,
  hifpPay: undefined as number | undefined,
  psnCashPay: undefined as number | undefined,
});

/** 工伤对账表格行：按日一行；结算 ↔ 收银勾稽；可选平台 1320 回执 */
interface WiReconTableRow {
  date: string;
  isTotal?: boolean;
  status: ReconStatusCode;
  medfeeSumamt: number;
  hifpPay: number;
  psnCashPay: number;
  setlCount: number;
  paymentMedfee?: number;
  paymentHifp?: number;
  paymentCash?: number;
  /** 平台 1320 回执（仅参考，不作为账平依据） */
  platformMedfee?: number;
  platformHifp?: number;
  platformCash?: number;
  unbalancedCount?: number;
}

const wiDailyRows = ref<WiReconTableRow[]>([]);
const wiReconcilingDate = ref<string>();
const wiDetailDialogVisible = ref(false);
const wiDetailDay = ref<string>();
const wiDetailRows = ref<WorkInjuryReconSettlementRow[]>([]);
const wiDetailLoading = ref(false);
const wiDetail1321Loading = ref(false);

const wiTotalRow = computed<WiReconTableRow | undefined>(() => {
  if (wiDailyRows.value.length === 0) return undefined;
  const totalStatus: ReconStatusCode = !wiPaymentReady.value
    ? RECON_STATUS.PENDING
    : wiAmountsBalanced.value
      ? RECON_STATUS.BALANCED
      : RECON_STATUS.UNBALANCED;
  const platformReady = wiDailyRows.value.some((row) => row.platformMedfee != null);
  return {
    date: '总计',
    isTotal: true,
    status: totalStatus,
    medfeeSumamt: wiHisAmounts.value.medfeeSumamt,
    hifpPay: wiHisAmounts.value.hifpPay,
    psnCashPay: wiHisAmounts.value.psnCashPay,
    setlCount: wiHisAmounts.value.setlCount,
    paymentMedfee: wiPaymentAmounts.value.medfeeSumamt,
    paymentHifp: wiPaymentAmounts.value.hifpPay,
    paymentCash: wiPaymentAmounts.value.psnCashPay,
    platformMedfee: platformReady
      ? Number(
          wiDailyRows.value
            .reduce((sum, row) => sum + Number(row.platformMedfee ?? 0), 0)
            .toFixed(2),
        )
      : undefined,
    platformHifp: platformReady
      ? Number(
          wiDailyRows.value.reduce((sum, row) => sum + Number(row.platformHifp ?? 0), 0).toFixed(2),
        )
      : undefined,
    platformCash: platformReady
      ? Number(
          wiDailyRows.value.reduce((sum, row) => sum + Number(row.platformCash ?? 0), 0).toFixed(2),
        )
      : undefined,
  };
});

const wiHasPlatformColumns = computed(() =>
  wiDailyRows.value.some(
    (row) => row.platformMedfee != null || row.platformHifp != null || row.platformCash != null,
  ),
);

const wiTableData = computed<WiReconTableRow[]>(() => wiDailyRows.value);

function daysBetween(begin: string, end: string): string[] {
  const days: string[] = [];
  const cursor = new Date(`${begin}T00:00:00`);
  const last = new Date(`${end}T00:00:00`);
  while (cursor <= last) {
    days.push(formatYmd(cursor));
    cursor.setDate(cursor.getDate() + 1);
  }
  return days;
}

function wiTableRowClassName({ row }: { row: WiReconTableRow }): string {
  return row.isTotal ? 'wi-recon-table__total-row' : '';
}

function wiDiffColor(diff?: number): string | undefined {
  if (diff == null) return undefined;
  return diff === 0 ? 'var(--el-color-success)' : 'var(--el-color-danger)';
}

function wiStatusTagType(status: ReconStatusCode): 'danger' | 'info' | 'success' {
  if (status === RECON_STATUS.BALANCED) return 'success';
  if (status === RECON_STATUS.UNBALANCED) return 'danger';
  return 'info';
}

function computeWiRowStatus(row: WiReconTableRow): ReconStatusCode {
  if (row.paymentMedfee == null || row.paymentHifp == null || row.paymentCash == null) {
    return RECON_STATUS.PENDING;
  }
  if ((row.unbalancedCount ?? 0) > 0) {
    return RECON_STATUS.UNBALANCED;
  }
  const diffs = [
    amountDiff(row.medfeeSumamt, row.paymentMedfee),
    amountDiff(row.hifpPay, row.paymentHifp),
    amountDiff(row.psnCashPay, row.paymentCash),
  ];
  const amountOk = diffs.every((diff) => diff != null && diff === 0);
  if (!amountOk) {
    return RECON_STATUS.UNBALANCED;
  }
  // 无笔数：直接视为平；有笔数：金额勾稽平后仍须明细确认账平
  return (row.setlCount ?? 0) === 0 ? RECON_STATUS.BALANCED : RECON_STATUS.PENDING;
}

/** 结算统计回填：优先用库中 confirmed / localStatus，金额仍用实时汇总。 */
function resolveWiStatusFromPersist(
  summary:
    | {
        localStatus?: string;
        confirmed?: boolean;
      }
    | undefined,
  next: WiReconTableRow,
): ReconStatusCode {
  if ((next.unbalancedCount ?? 0) > 0 || summary?.localStatus === 'unbalanced') {
    return RECON_STATUS.UNBALANCED;
  }
  if (summary?.confirmed === true || summary?.localStatus === 'balanced') {
    return RECON_STATUS.BALANCED;
  }
  return computeWiRowStatus(next);
}

/** 1320 对账口径：本市 / 省内异地 / 全国异地（V2.5 §5.3.2.1），由页面单选 */
type Wi1320ReconScope = 'LOCAL' | 'QGYD' | 'SNYD';

const WI1320_SCOPE_OPTIONS: Array<{ value: Wi1320ReconScope; label: string }> = [
  { value: 'LOCAL', label: '本市' },
  { value: 'SNYD', label: '省内异地' },
  { value: 'QGYD', label: '全国异地' },
];

function wi1320ScopeLabel(scope: Wi1320ReconScope = wi1320Scope.value): string {
  return WI1320_SCOPE_OPTIONS.find((item) => item.value === scope)?.label ?? scope;
}

function buildWiFormContextForDay(day: string, scope: Wi1320ReconScope): Record<string, unknown> {
  const ymd = day.replaceAll('-', '');
  const ctx: Record<string, unknown> = {
    stmtBegindate: ymd,
    stmtEnddate: ymd,
    refdSetlFlag: '1',
    reconScope: scope,
  };
  // 本市口径才传统筹区；异地口径不强制
  if (scope === 'LOCAL') {
    ctx.socialPoolCode = (wiSocialPoolCode.value ?? '').trim() || '220199';
  }
  return ctx;
}

function extractWi1320Payload(output: Record<string, unknown>): Record<string, unknown> {
  const root = normalizeWorkInjuryWinOutput(output);
  const nested = root.output;
  if (nested && typeof nested === 'object' && !Array.isArray(nested)) {
    return nested as Record<string, unknown>;
  }
  return root;
}

function apply1320OutputToRow(
  _row: WiReconTableRow,
  output: Record<string, unknown>,
): { medfee: number; hifp: number; cash: number } {
  const payload = extractWi1320Payload(output);
  return {
    medfee: parseWiAmount(payload, 'medfee_sumamt') ?? 0,
    hifp: parseWiAmount(payload, 'hifp_pay') ?? 0,
    cash: parseWiAmount(payload, 'psn_cash_pay') ?? 0,
  };
}

function syncWiPaymentTotalsFromDaily() {
  const daily = wiDailyRows.value;
  if (daily.length === 0) {
    resetWiPaymentAmounts();
    return;
  }
  const allReady = daily.every(
    (row) => row.paymentMedfee != null && row.paymentHifp != null && row.paymentCash != null,
  );
  if (!allReady) {
    resetWiPaymentAmounts();
    return;
  }
  wiPaymentAmounts.value = {
    medfeeSumamt: Number(
      daily.reduce((sum, row) => sum + Number(row.paymentMedfee ?? 0), 0).toFixed(2),
    ),
    hifpPay: Number(daily.reduce((sum, row) => sum + Number(row.paymentHifp ?? 0), 0).toFixed(2)),
    psnCashPay: Number(
      daily.reduce((sum, row) => sum + Number(row.paymentCash ?? 0), 0).toFixed(2),
    ),
  };
}

function updateWiStepAfterRecon() {
  wiStep.value = wiAmountsBalanced.value ? 3 : 2;
}

/** 1320/1322 前强制 9001 签到，避免 Win 进程内缓存过期 sign_no。 */
async function ensureWiReconSignIn(): Promise<void> {
  if (!(await isWorkInjuryPluginOnline())) {
    throw new WorkInjuryPluginError('请启动 OpenHIS.Win 并启用工伤模块');
  }
  await ensureWorkInjurySignIn();
}

async function invokeWorkInjury1320Core(
  formContext: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  return invokeWorkInjuryReconCore(formContext, 'RECON', '1320');
}

async function invokeWorkInjury1321Core(
  row: WorkInjuryReconSettlementRow,
  formContext: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  return invokeWorkInjuryReconCore(formContext, 'RECON_DETAIL', '1321', row.encounterId);
}

async function invokeWorkInjuryReconCore(
  formContext: Record<string, unknown>,
  phase: 'RECON' | 'RECON_DETAIL' | 'RECON_CONFIRM',
  expectedInfno: string,
  encounterId?: string | number,
): Promise<Record<string, unknown>> {
  const opt = requireWiContract();
  const prep = await startWorkInjuryReconApi({
    contractId: opt.contractId,
    encounterId,
    formContext,
    phase,
  });
  if (prep.nextAction !== 'INVOKE_WIN' || prep.infno !== expectedInfno) {
    throw new Error(`${expectedInfno} prepare 异常：infno=${prep.infno ?? '(空)'}`);
  }
  if (!prep.sessionId || !prep.stepToken) {
    throw new Error(`${expectedInfno} 缺少 sessionId/stepToken`);
  }
  const winRes = await invokeWorkInjuryPlugin(prep);
  const output = normalizeWorkInjuryWinOutput(winRes.output);
  await httpWorkInjurySubmitInvokeResult({
    sessionId: prep.sessionId,
    stepToken: prep.stepToken,
    success: winRes.success !== false,
    infcode: winRes.infcode != null ? String(winRes.infcode) : '0',
    errMsg: winRes.errMsg,
    output,
    winMsgid: winRes.msgid,
    winRawInput: winRes.rawInput,
    context: formContext,
  });
  if (winRes.success === false || (winRes.infcode != null && String(winRes.infcode) !== '0')) {
    throw new Error(winRes.errMsg || `${expectedInfno} 失败`);
  }
  return output;
}

async function reconcileWiDayRow(row: WiReconTableRow): Promise<WiReconTableRow> {
  const output = await invokeWorkInjury1320Core(
    buildWiFormContextForDay(row.date, wi1320Scope.value),
  );
  const platform = apply1320OutputToRow(row, output);
  return {
    ...row,
    platformMedfee: platform.medfee,
    platformHifp: platform.hifp,
    platformCash: platform.cash,
  };
}

function applyPlatformToDailyRow(next: WiReconTableRow) {
  wiDailyRows.value = wiDailyRows.value.map((item) =>
    item.date === next.date ? { ...item, ...next } : item,
  );
}

async function persistWiPlatformRow(next: WiReconTableRow) {
  const opt = requireWiContract();
  await saveWorkInjuryReconDailyPlatformApi({
    contractId: opt.contractId,
    contractBusNo: opt.busNo,
    day: next.date,
    platformMedfee: next.platformMedfee,
    platformHifp: next.platformHifp,
    platformCash: next.platformCash,
    reconScope: wi1320Scope.value,
  });
}

async function handleWiReconcileRow(row: WiReconTableRow) {
  if (row.isTotal || wiStep.value < 2) return;
  wiReconcilingDate.value = row.date;
  try {
    await ensureWiReconSignIn();
    const next = await reconcileWiDayRow(row);
    applyPlatformToDailyRow(next);
    await persistWiPlatformRow(next);
    ElMessage.info(
      `${row.date} 平台1320（${wi1320ScopeLabel()}）：医疗费 ${formatMoney(next.platformMedfee)} / 统筹 ${formatMoney(next.platformHifp)} / 现金 ${formatMoney(next.platformCash)}`,
    );
  } catch (error) {
    if (error instanceof WorkInjuryPluginError) {
      ElMessage.error(error.message || '对账失败');
    } else if (error instanceof Error && !('response' in error)) {
      ElMessage.error(error.message || '对账失败');
    }
  } finally {
    wiReconcilingDate.value = undefined;
  }
}

function buildWi1321FormContext(row: WorkInjuryReconSettlementRow): Record<string, unknown> {
  const billNo = (row.mdtrtId ?? row.setlId ?? '').trim();
  return {
    iptOtpNo: (row.iptOtpNo ?? '').trim(),
    // 手册示例可用 "0"；试传单据号（mdtrt_id）排查平台查不到数据的情况
    medrcdno: billNo || '0',
    socialPoolCode: (wiSocialPoolCode.value ?? '').trim() || '220199',
  };
}

function apply1321OutputToDetailRow(
  row: WorkInjuryReconSettlementRow,
  output: Record<string, unknown>,
): WorkInjuryReconSettlementRow {
  const payload = extractWi1320Payload(output);
  const hisMed = Number(row.medfeeSumamt ?? 0);
  const hisHifp = Number(row.fundPaySumamt ?? 0);
  const hisCash = Number(row.psnCashPay ?? 0);
  const platformMedfee = parseWiAmount(payload, 'medfee_sumamt') ?? 0;
  const platformHifp = parseWiAmount(payload, 'hifp_pay') ?? 0;
  const platformCash = parseWiAmount(payload, 'psn_cash_pay') ?? 0;
  const balanced =
    amountDiff(hisMed, platformMedfee) === 0 &&
    amountDiff(hisHifp, platformHifp) === 0 &&
    amountDiff(hisCash, platformCash) === 0;
  return {
    ...row,
    platformMedfee,
    platformHifp,
    platformCash,
    detailStatus: balanced ? 'balanced' : 'unbalanced',
    detailErrMsg: undefined,
  };
}

async function loadWiDetailSettlements(day: string) {
  const opt = requireWiContract();
  wiDetailLoading.value = true;
  try {
    const rows = await listWorkInjuryReconSettlementsApi({
      contractId: opt.contractId,
      contractBusNo: opt.busNo,
      day,
    });
    wiDetailRows.value = rows.map((row) => enrichWiDetailLocalStatus(row));
  } catch (error: unknown) {
    wiDetailRows.value = [];
    const axiosErr = error as {
      response?: { data?: { message?: string; errorCode?: string } };
      message?: string;
    };
    const apiMsg = axiosErr.response?.data?.message;
    const hint =
      axiosErr.response?.data?.errorCode === 'NOT_FOUND'
        ? '接口未部署：请重启后端（含 listReconSettlementsByDay）'
        : apiMsg || (error instanceof Error ? error.message : '');
    ElMessage.error(hint || '加载结算明细失败');
  } finally {
    wiDetailLoading.value = false;
  }
}

/** 按明细回写当日日表结算/收银汇总（重新拉取后保持一致）。 */
function syncWiDayRowFromDetails(day: string) {
  const details = wiDetailRows.value;
  let medfee = 0;
  let fund = 0;
  let cash = 0;
  let paymentMedfee = 0;
  let paymentHifp = 0;
  let paymentCash = 0;
  let unbalancedCount = 0;
  let setlCount = 0;
  for (const row of details) {
    // 已撤销不计入日汇总（与后端 DayBucket 口径一致；明细仍展示）
    if (isWiDetailRevoked(row)) {
      continue;
    }
    setlCount += 1;
    medfee += Number(row.medfeeSumamt ?? 0);
    fund += Number(row.fundPaySumamt ?? 0);
    cash += Number(row.psnCashPay ?? 0);
    paymentMedfee += Number(row.paymentMedfee ?? 0);
    paymentHifp += Number(row.paymentHifp ?? 0);
    paymentCash += Number(row.paymentCash ?? 0);
    if (wiDetailRowHasProblem(row)) {
      unbalancedCount += 1;
    }
  }
  wiDailyRows.value = wiDailyRows.value.map((row) => {
    if (row.date !== day) return row;
    const next: WiReconTableRow = {
      ...row,
      medfeeSumamt: Number(medfee.toFixed(2)),
      hifpPay: Number(fund.toFixed(2)),
      psnCashPay: Number(cash.toFixed(2)),
      setlCount,
      paymentMedfee: Number(paymentMedfee.toFixed(2)),
      paymentHifp: Number(paymentHifp.toFixed(2)),
      paymentCash: Number(paymentCash.toFixed(2)),
      unbalancedCount,
    };
    // 已确认账平的日期保持平；否则按最新勾稽重算
    if (row.status === RECON_STATUS.BALANCED && unbalancedCount === 0) {
      return { ...next, status: RECON_STATUS.BALANCED };
    }
    return { ...next, status: computeWiRowStatus(next) };
  });
  wiHisAmounts.value = {
    medfeeSumamt: Number(
      wiDailyRows.value.reduce((sum, row) => sum + row.medfeeSumamt, 0).toFixed(2),
    ),
    hifpPay: Number(wiDailyRows.value.reduce((sum, row) => sum + row.hifpPay, 0).toFixed(2)),
    psnCashPay: Number(wiDailyRows.value.reduce((sum, row) => sum + row.psnCashPay, 0).toFixed(2)),
    setlCount: wiDailyRows.value.reduce((sum, row) => sum + row.setlCount, 0),
  };
  syncWiPaymentTotalsFromDaily();
  updateWiStepAfterRecon();
}

async function reloadWiDetailSettlements() {
  const day = wiDetailDay.value;
  if (!day) return;
  await loadWiDetailSettlements(day);
  syncWiDayRowFromDetails(day);
  ElMessage.success(`已重新拉取 ${day} 本地明细（${wiDetailRows.value.length} 笔）`);
}

async function openWiDetailDialog(row: WiReconTableRow) {
  if (row.isTotal) return;
  wiDetailDay.value = row.date;
  wiDetailDialogVisible.value = true;
  await loadWiDetailSettlements(row.date);
}

/** 可重新拉取平台 1321 的行：有流水号且非撤销（含已本地勾稽平，支持重拉）。 */
function wiDetail1321Targets(): WorkInjuryReconSettlementRow[] {
  return wiDetailRows.value.filter(
    (item) => Boolean((item.iptOtpNo ?? '').trim()) && !isWiDetailRevoked(item),
  );
}

async function runWiDetail1321All() {
  const targets = wiDetail1321Targets();
  if (targets.length === 0) {
    ElMessage.info('无可拉取的流水（缺少 ipt_otp_no 或均为已撤销）');
    return;
  }
  try {
    await ensureWiReconSignIn();
    wiDetail1321Loading.value = true;
    let failed = 0;
    for (const item of targets) {
      try {
        const output = await invokeWorkInjury1321Core(item, buildWi1321FormContext(item));
        const withPlatform = apply1321OutputToDetailRow(item, output);
        const next = {
          ...enrichWiDetailLocalStatus({
            ...withPlatform,
            detailStatus: undefined,
            detailErrMsg: undefined,
          }),
          platformMedfee: withPlatform.platformMedfee,
          platformHifp: withPlatform.platformHifp,
          platformCash: withPlatform.platformCash,
        };
        wiDetailRows.value = wiDetailRows.value.map((row) =>
          row.settlementRecordId === item.settlementRecordId ? next : row,
        );
      } catch (error) {
        failed += 1;
        wiDetailRows.value = wiDetailRows.value.map((row) =>
          row.settlementRecordId === item.settlementRecordId
            ? {
                ...row,
                detailStatus: 'error',
                detailErrMsg: error instanceof Error ? error.message : '1321 失败',
              }
            : row,
        );
      }
    }
    if (failed > 0) {
      ElMessage.warning(`重新拉取平台1321完成，${failed} 笔失败`);
    } else {
      ElMessage.success(`重新拉取平台1321完成（${targets.length} 笔）`);
    }
  } catch (error) {
    if (error instanceof WorkInjuryPluginError) {
      ElMessage.error(error.message || '1321 明细对账失败');
    } else if (error instanceof Error) {
      ElMessage.error(error.message || '1321 明细对账失败');
    }
  } finally {
    wiDetail1321Loading.value = false;
  }
}

function isWiDetailRevoked(row: WorkInjuryReconSettlementRow): boolean {
  return String(row.status ?? '').toUpperCase() === 'REVOKED';
}

/** 明细核对问题：有效结算须 2207 已入账且 settlement↔payment 勾稽平；已撤销不阻断。 */
function wiDetailRowHasProblem(row: WorkInjuryReconSettlementRow): boolean {
  if (isWiDetailRevoked(row)) return false;
  if (!row.posted2207) return true;
  if (row.paymentId == null || row.paymentId === '') return true;
  if (row.paymentMedfee == null || row.paymentHifp == null || row.paymentCash == null) return true;
  return (
    amountDiff(Number(row.medfeeSumamt ?? 0), Number(row.paymentMedfee)) !== 0 ||
    amountDiff(Number(row.fundPaySumamt ?? 0), Number(row.paymentHifp)) !== 0 ||
    amountDiff(Number(row.psnCashPay ?? 0), Number(row.paymentCash)) !== 0
  );
}

const wiDetailCanMarkBalanced = computed(
  () => !wiDetailLoading.value && wiDetailRows.value.every((row) => !wiDetailRowHasProblem(row)),
);

function wiDetail2207Label(row: WorkInjuryReconSettlementRow): string {
  if (isWiDetailRevoked(row)) return '已撤销';
  return row.posted2207 ? '已入账' : '未入账';
}

function wiDetail2207Tag(
  row: WorkInjuryReconSettlementRow,
): 'danger' | 'info' | 'success' | 'warning' {
  if (isWiDetailRevoked(row)) return 'info';
  return row.posted2207 ? 'success' : 'warning';
}

function enrichWiDetailLocalStatus(
  row: WorkInjuryReconSettlementRow,
): WorkInjuryReconSettlementRow {
  if (row.detailStatus === 'error') return row;
  if (isWiDetailRevoked(row)) {
    return { ...row, detailStatus: 'pending' };
  }
  if (!row.posted2207) {
    return { ...row, detailStatus: 'pending' };
  }
  if (row.paymentId == null || row.paymentId === '') {
    return { ...row, detailStatus: 'unbalanced', detailErrMsg: '无收银' };
  }
  if (row.paymentMedfee == null || row.paymentHifp == null || row.paymentCash == null) {
    return { ...row, detailStatus: 'unbalanced', detailErrMsg: '无收银明细' };
  }
  const balanced =
    amountDiff(Number(row.medfeeSumamt ?? 0), Number(row.paymentMedfee)) === 0 &&
    amountDiff(Number(row.fundPaySumamt ?? 0), Number(row.paymentHifp)) === 0 &&
    amountDiff(Number(row.psnCashPay ?? 0), Number(row.paymentCash)) === 0;
  return {
    ...row,
    detailStatus: balanced ? 'balanced' : 'unbalanced',
    detailErrMsg: balanced ? undefined : '结算与收银差额',
  };
}

function wiDetailStatusLabel(row: WorkInjuryReconSettlementRow): string {
  if (isWiDetailRevoked(row)) return '已撤销';
  if (!row.posted2207) return '2207未入账';
  if (row.paymentId == null || row.paymentId === '') return '无收银';
  if (row.detailStatus === 'balanced') return '平';
  if (row.detailStatus === 'unbalanced') return '不平';
  if (row.detailStatus === 'error') return '失败';
  return '待核对';
}

function wiDetailStatusTag(
  row: WorkInjuryReconSettlementRow,
): 'danger' | 'info' | 'success' | 'warning' {
  if (isWiDetailRevoked(row)) return 'info';
  if (!row.posted2207) return 'warning';
  if (row.paymentId == null || row.paymentId === '') return 'danger';
  if (row.detailStatus === 'balanced') return 'success';
  if (row.detailStatus === 'error' || row.detailStatus === 'unbalanced') return 'danger';
  return 'info';
}

/** 明细无问题 → 当日账平（以结算↔收银勾稽为准），并落库。 */
async function confirmWiDetailBalanced() {
  const day = wiDetailDay.value;
  if (!day) return;
  if (!wiDetailCanMarkBalanced.value) {
    ElMessage.warning('仍有未入账或结算↔收银不平的有效结算，请先排查后再确认账平');
    return;
  }
  try {
    const opt = requireWiContract();
    await confirmWorkInjuryReconDailyBalancedApi({
      contractId: opt.contractId,
      contractBusNo: opt.busNo,
      day,
    });
    wiDailyRows.value = wiDailyRows.value.map((row) =>
      row.date === day ? { ...row, unbalancedCount: 0, status: RECON_STATUS.BALANCED } : row,
    );
    syncWiPaymentTotalsFromDaily();
    updateWiStepAfterRecon();
    wiDetailDialogVisible.value = false;
    wiLastSummary.value = `${day} 明细核对无问题，已记账平`;
    ElMessage.success(wiLastSummary.value);
  } catch (error) {
    const message = error instanceof Error ? error.message : '确认账平失败';
    ElMessage.error(message);
  }
}

function filterWorkInjuryChargeTypes(
  items: Array<{ value?: string; label?: string; busNo?: string; categoryEnum?: string }>,
): WiChargeTypeOption[] {
  return items
    .filter(
      (item) =>
        Boolean(item.value?.trim()) &&
        Boolean(item.busNo?.trim()) &&
        (isWorkInjuryContractCategory(item.categoryEnum) || item.busNo!.trim().endsWith('-GS')),
    )
    .map((item) => ({
      contractId: String(item.value).trim(),
      label: (item.label ?? item.busNo ?? item.value ?? '').trim(),
      busNo: (item.busNo ?? '').trim(),
    }));
}

function requireWiContract(): WiChargeTypeOption {
  const cid = wiContractId.value?.trim();
  if (!cid) {
    throw new Error('请选择工伤费用性质');
  }
  const opt = wiContractOptions.value.find((item) => item.contractId === cid);
  if (!opt) {
    throw new Error('工伤费用性质无效，请重新选择');
  }
  return opt;
}

function requireWiDateRange(): [string, string] {
  if (!wiDateRange.value?.[0] || !wiDateRange.value?.[1]) {
    throw new Error('请选择对账日期');
  }
  return wiDateRange.value;
}

function buildWiFormContext(includeMonth: boolean): Record<string, unknown> {
  const [begin, end] = requireWiDateRange();
  const ctx: Record<string, unknown> = {
    socialPoolCode: (wiSocialPoolCode.value ?? '').trim() || '220199',
    stmtBegindate: begin.replaceAll('-', ''),
    stmtEnddate: end.replaceAll('-', ''),
  };
  if (includeMonth) {
    let month = (wiMonth.value ?? '').trim();
    if (!month) {
      month = defaultLastMonthYm();
      wiMonth.value = month;
    }
    ctx.month = month;
  }
  return ctx;
}

function currentYm(): string {
  const now = new Date();
  return `${now.getFullYear()}${pad2(now.getMonth() + 1)}`;
}

function isCurrentOrFutureMonth(ym: string): boolean {
  return ym >= currentYm();
}

function parseWiAmount(
  output: Record<string, unknown> | undefined,
  key: string,
): number | undefined {
  if (!output) return undefined;
  const camelKey = key.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase());
  for (const candidate of [key, camelKey]) {
    const raw = output[candidate];
    if (raw == null || raw === '') continue;
    const n = Number(raw);
    if (Number.isFinite(n)) return n;
  }
  return undefined;
}

function amountDiff(his?: number, platform?: number): number | undefined {
  if (his == null || platform == null) return undefined;
  return Number((his - platform).toFixed(2));
}

const wiMedfeeDiff = computed(() =>
  amountDiff(wiHisAmounts.value.medfeeSumamt, wiPaymentAmounts.value.medfeeSumamt),
);
const wiFundDiff = computed(() =>
  amountDiff(wiHisAmounts.value.hifpPay, wiPaymentAmounts.value.hifpPay),
);
const wiCashDiff = computed(() =>
  amountDiff(wiHisAmounts.value.psnCashPay, wiPaymentAmounts.value.psnCashPay),
);

const wiPaymentReady = computed(
  () =>
    wiPaymentAmounts.value.medfeeSumamt != null &&
    wiPaymentAmounts.value.hifpPay != null &&
    wiPaymentAmounts.value.psnCashPay != null,
);

const wiAmountsBalanced = computed(() => {
  if (!wiPaymentReady.value) return false;
  return wiDailyRows.value.every((row) => row.status === RECON_STATUS.BALANCED);
});

function resetWiPaymentAmounts() {
  wiPaymentAmounts.value = {
    medfeeSumamt: undefined,
    hifpPay: undefined,
    psnCashPay: undefined,
  };
}

/** 从对账日期区间回填确认月（取结束日所在月，若当月则默认上月） */
function syncWiMonthFromDateRange() {
  const end = wiDateRange.value?.[1];
  if (!end) return;
  const ym = end.replaceAll('-', '').slice(0, 6);
  wiMonth.value = isCurrentOrFutureMonth(ym) ? defaultLastMonthYm() : ym;
}

async function loadWorkInjuryReconInit() {
  try {
    if (!isWorkInjuryUiEnabled()) {
      wiModuleEnabled.value = false;
    } else {
      const cap = await httpWorkInjuryModuleCapability();
      wiModuleEnabled.value = cap?.enabled === true;
    }
  } catch {
    wiModuleEnabled.value = false;
  }
  if (!wiModuleEnabled.value) {
    wiContractOptions.value = [];
    if (activeTab.value === 'workInjury') {
      activeTab.value = 'daily';
    }
    return;
  }
  const list = await getChargeTypeListApi();
  wiContractOptions.value = filterWorkInjuryChargeTypes(list);
  if (!wiContractId.value && wiContractOptions.value.length === 1) {
    wiContractId.value = wiContractOptions.value[0]!.contractId;
  }
}

/** 工伤「结算统计」：按日汇总 settlement ↔ payment */
async function runWorkInjuryHisStat() {
  try {
    const opt = requireWiContract();
    const [begin, end] = requireWiDateRange();
    wiHisLoading.value = true;
    const days = daysBetween(begin, end);
    const daySummaries = await summarizeWorkInjuryReconByDayRangeApi({
      contractId: opt.contractId,
      contractBusNo: opt.busNo,
      begndate: begin,
      enddate: end,
    });
    const byDay = new Map(daySummaries.map((row) => [row.day, row]));
    wiDailyRows.value = days.map((day) => {
      const summary = byDay.get(day);
      const next: WiReconTableRow = {
        date: day,
        status: RECON_STATUS.PENDING,
        medfeeSumamt: Number(summary?.medfeeSumamt ?? 0),
        hifpPay: Number(summary?.fundPaySumamt ?? 0),
        psnCashPay: Number(summary?.psnCashPay ?? 0),
        setlCount: Number(summary?.setlCount ?? 0),
        paymentMedfee: Number(summary?.paymentMedfee ?? 0),
        paymentHifp: Number(summary?.paymentHifp ?? 0),
        paymentCash: Number(summary?.paymentCash ?? 0),
        unbalancedCount: Number(summary?.unbalancedCount ?? 0),
        platformMedfee:
          summary?.platformMedfee != null ? Number(summary.platformMedfee) : undefined,
        platformHifp: summary?.platformHifp != null ? Number(summary.platformHifp) : undefined,
        platformCash: summary?.platformCash != null ? Number(summary.platformCash) : undefined,
      };
      return { ...next, status: resolveWiStatusFromPersist(summary, next) };
    });
    wiHisAmounts.value = {
      medfeeSumamt: Number(
        wiDailyRows.value.reduce((sum, row) => sum + row.medfeeSumamt, 0).toFixed(2),
      ),
      hifpPay: Number(wiDailyRows.value.reduce((sum, row) => sum + row.hifpPay, 0).toFixed(2)),
      psnCashPay: Number(
        wiDailyRows.value.reduce((sum, row) => sum + row.psnCashPay, 0).toFixed(2),
      ),
      setlCount: wiDailyRows.value.reduce((sum, row) => sum + row.setlCount, 0),
    };
    syncWiPaymentTotalsFromDaily();
    updateWiStepAfterRecon();
    if (wiStep.value < 2) wiStep.value = 2;
    syncWiMonthFromDateRange();
    const unbalancedDays = wiDailyRows.value.filter(
      (row) => row.status === RECON_STATUS.UNBALANCED,
    ).length;
    const pendingDays = wiDailyRows.value.filter(
      (row) => row.status === RECON_STATUS.PENDING,
    ).length;
    wiLastSummary.value =
      unbalancedDays > 0
        ? `结算↔收银统计完成：${days.length} 天 · ${wiHisAmounts.value.setlCount} 笔 · ${unbalancedDays} 天不平，请点「明细」排查`
        : pendingDays > 0
          ? `结算↔收银统计完成：${days.length} 天 · ${wiHisAmounts.value.setlCount} 笔 · 请逐日「明细」确认账平`
          : `结算↔收银统计完成：${days.length} 天 · 共 ${wiHisAmounts.value.setlCount} 笔 · 可对账确认`;
    ElMessage.success(wiLastSummary.value);
  } catch (error) {
    const message = error instanceof Error ? error.message : '本地结算统计失败';
    ElMessage.error(message);
  } finally {
    wiHisLoading.value = false;
  }
}

/** 批量 1320：优先只重拉本地勾稽不平的日；若无不平则补拉尚未有平台回执的日。 */
function resolveWi1320BatchTargets(): WiReconTableRow[] {
  const rows = wiDailyRows.value.filter((row) => !row.isTotal);
  const unbalanced = rows.filter((row) => row.status === RECON_STATUS.UNBALANCED);
  if (unbalanced.length > 0) {
    return unbalanced;
  }
  return rows.filter((row) => row.platformMedfee == null);
}

/** 可选：批量调平台 1320（仅参考；已平且已有回执的不重复拉） */
async function runWorkInjury1320() {
  try {
    if (wiDailyRows.value.length === 0) {
      ElMessage.warning('请先完成结算统计');
      return;
    }
    const targets = resolveWi1320BatchTargets();
    if (targets.length === 0) {
      ElMessage.info('没有需要重拉的日期（无不平，且平台回执已齐）');
      return;
    }
    await ensureWiReconSignIn();
    wi1320Loading.value = true;
    wiLastSummary.value = undefined;
    let failed = 0;
    const snippets: string[] = [];
    for (const row of targets) {
      try {
        const next = await reconcileWiDayRow(row);
        applyPlatformToDailyRow(next);
        await persistWiPlatformRow(next);
        snippets.push(
          `${row.date} 医疗费 ${formatMoney(next.platformMedfee)} / 统筹 ${formatMoney(next.platformHifp)}`,
        );
      } catch {
        failed += 1;
      }
    }
    if (failed > 0) {
      wiLastSummary.value = `平台1320（${wi1320ScopeLabel()}）重拉结束：${targets.length} 天中失败 ${failed} 天`;
      ElMessage.warning(wiLastSummary.value);
    } else {
      wiLastSummary.value = `平台1320（${wi1320ScopeLabel()}）重拉完成 ${targets.length} 天：${snippets.slice(0, 3).join('；')}${snippets.length > 3 ? '…' : ''}`;
      ElMessage.info(wiLastSummary.value);
    }
  } catch (error) {
    if (error instanceof WorkInjuryPluginError) {
      ElMessage.error(error.message || '总额对账失败');
    } else if (error instanceof Error && !('response' in error)) {
      ElMessage.error(error.message || '总额对账失败');
    }
  } finally {
    wi1320Loading.value = false;
  }
}

/** 老 HIS「对账确认」：仅 1322；当月未结束时前端拦截 */
async function runWorkInjury1322() {
  try {
    const opt = requireWiContract();
    const formContext = buildWiFormContext(true);
    const month = String(formContext.month);
    if (isCurrentOrFutureMonth(month)) {
      await ElMessageBox.alert(
        `${month} 月份尚未结束，平台不允许对账确认。请改选已结束月份（默认上月）。`,
        '无法确认',
        { type: 'warning' },
      );
      return;
    }
    if (wiPaymentAmounts.value.medfeeSumamt == null || wiPaymentAmounts.value.hifpPay == null) {
      await ElMessageBox.alert('请先逐日「明细」核对并确认账平后再对账确认。', '无法确认', {
        type: 'warning',
      });
      return;
    }
    const diffs = [wiMedfeeDiff.value, wiFundDiff.value, wiCashDiff.value];
    if (diffs.some((d) => d != null && d !== 0)) {
      await ElMessageBox.alert('仍有日期未账平，请先逐日「明细」核对并确认账平。', '无法确认', {
        type: 'warning',
      });
      return;
    }
    await ensureWiReconSignIn();
    wi1322Loading.value = true;
    const prep = await startWorkInjuryReconApi({
      contractId: opt.contractId,
      formContext,
      phase: 'RECON_CONFIRM',
    });
    const result = await continueWorkInjurySession(prep, 'RECON_CONFIRM', {
      contractId: opt.contractId,
      context: formContext,
    });
    wiLastSummary.value = result.summary ?? `对账确认完成（月份 ${month}）`;
    ElMessage.success(wiLastSummary.value);
  } catch (error) {
    if (error instanceof WorkInjuryPluginError) {
      ElMessage.error(error.message || '对账确认失败');
    } else if (error instanceof Error && !('response' in error)) {
      ElMessage.error(error.message || '对账确认失败');
    }
  } finally {
    wi1322Loading.value = false;
  }
}

function requireDateRange(range?: [string, string] | null, label = '日期范围'): [string, string] {
  if (!range?.[0] || !range?.[1]) {
    throw new Error(`请选择${label}`);
  }
  return range;
}

function applySummaryToClearForm(summary: ClearAmountSummary) {
  clearAmountForm.value = {
    medfeeSumamt: Number(summary.medfeeSumamt ?? 0),
    fundAppySum: Number(summary.fundAppySum ?? 0),
    cashPayamt: Number(summary.cashPayamt ?? 0),
    acctPay: Number(summary.acctPay ?? 0),
  };
}

function requireContractBusNo(): string {
  const busNo = contractBusNo.value?.trim();
  if (!busNo) {
    throw new Error('请先选择清算机构');
  }
  return busNo;
}

async function withSignIn<T>(runner: () => Promise<T>): Promise<T> {
  await ensureInsuranceSignIn(requireContractBusNo(), { preferServerSignIn: true });
  return runner();
}

const { run: loadChargeTypes, loading: loadingChargeTypes } = useRequest(async () => {
  const list = await getChargeTypeListApi();
  const eligible = filterInsuranceChargeTypes(list);
  chargeTypeOptions.value = eligible;
  contractBusNo.value = resolveDefaultInsuranceBusNo(eligible, contractBusNo.value);
});

/** 起止日之间的 yyyyMM 去重列表 */
function monthsBetween(begin: string, end: string): string[] {
  const months: string[] = [];
  const cursor = new Date(`${begin}T00:00:00`);
  const last = new Date(`${end}T00:00:00`);
  cursor.setDate(1);
  last.setDate(1);
  while (cursor <= last) {
    months.push(`${cursor.getFullYear()}${pad2(cursor.getMonth() + 1)}`);
    cursor.setMonth(cursor.getMonth() + 1);
  }
  return months.length > 0 ? months : [begin.replaceAll('-', '').slice(0, 6)];
}

const { run: loadDailyLocalRecords, loading: loadingDailyRecords } = useRequest(async () => {
  const begin = (
    dailyReconRange.value?.[0] ||
    dailyReconDate.value ||
    defaultDailyReconDate()
  ).trim();
  const end = (dailyReconRange.value?.[1] || begin).trim();
  const busNo = requireContractBusNo();
  const batches = await Promise.all(
    monthsBetween(begin, end).map((monthFilter) =>
      listLocalReconDailyRecordsApi({
        contractBusNo: busNo,
        monthFilter,
      }),
    ),
  );
  const byId = new Map<string, LocalReconDailyRecord>();
  for (const batch of batches) {
    for (const row of batch) {
      byId.set(String(row.id ?? `${row.stmtBegndate}-${row.insutype}-${row.clrType}`), row);
    }
  }
  dailyLocalRecords.value = pickLatestBalancedPerDim([...byId.values()]);
});

const { run: loadDetailLocalRecords } = useRequest(async () => {
  const begin = (
    dailyReconRange.value?.[0] ||
    dailyReconDate.value ||
    defaultDailyReconDate()
  ).trim();
  const end = (dailyReconRange.value?.[1] || begin).trim();
  const busNo = requireContractBusNo();
  const batches = await Promise.all(
    monthsBetween(begin, end).map((monthFilter) =>
      listLocalReconDetailRecordsApi({
        contractBusNo: busNo,
        monthFilter,
      }),
    ),
  );
  const byId = new Map<string, LocalReconDetailRecord>();
  for (const batch of batches) {
    for (const row of batch) {
      byId.set(String(row.id ?? `${row.stmtBegndate}-${row.clrType}-${row.resultFileQuryNo}`), row);
    }
  }
  detailLocalRecords.value = [...byId.values()];
});

/** 左侧对账区间变更后刷新右侧本地记录（按涉及月份合并） */
function onDailyReconDateChange(date: string) {
  const next = date?.trim();
  if (!next || next === dailyReconDate.value) {
    return;
  }
  dailyReconDate.value = next;
  if (!contractBusNo.value) {
    return;
  }
  void Promise.all([loadDailyLocalRecords(), loadDetailLocalRecords()]);
}

function onDailyReconRangeChange(begin: string, end: string) {
  const b = begin?.trim();
  const e = end?.trim();
  if (!b || !e) return;
  const prev = dailyReconRange.value;
  if (prev?.[0] === b && prev?.[1] === e) return;
  dailyReconRange.value = [b, e];
  dailyReconDate.value = e;
  if (!contractBusNo.value) {
    return;
  }
  void Promise.all([loadDailyLocalRecords(), loadDetailLocalRecords()]);
}

/** 收集医保契约 busNo（去重），用于本地清算流水全量查询 */
function insuranceContractBusNos(): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  // 遍历费用性质下拉，收集全部医保契约（不按当前选中项过滤）
  for (const item of chargeTypeOptions.value) {
    const busNo = item.busNo?.trim();
    if (!busNo || seen.has(busNo)) continue;
    seen.add(busNo);
    result.push(busNo);
  }
  if (result.length === 0) {
    const current = contractBusNo.value?.trim();
    if (current) result.push(current);
  }
  return result;
}

const { run: loadClearLocalRecords, loading: loadingClearRecords } = useRequest(async () => {
  const busNos = insuranceContractBusNos();
  if (busNos.length === 0) {
    throw new Error('请先选择清算机构');
  }
  const batches = await Promise.all(
    busNos.map((busNo) => listLocalClearAppliesApi({ contractBusNo: busNo })),
  );
  const byId = new Map<string, LocalClearApplyRecord>();
  // 合并各契约返回的申请流水，按本地 id 去重
  for (const batch of batches) {
    for (const row of batch) {
      byId.set(String(row.id ?? `${row.clrAppyEvtId}-${row.begndate}-${row.clrType}`), row);
    }
  }
  clearLocalRecords.value = [...byId.values()];
});

const { run: summarizeClearAmounts, loading: summarizingClear } = useRequest(async () => {
  const [begndate, enddate] = requireDateRange(clearDateRange.value, '清算月份');
  const rows = await withSignIn(() =>
    summarizeClearAmountsApi({
      contractBusNo: requireContractBusNo(),
      begndate,
      enddate,
      // 必须按业务类型过滤，避免门诊误加住院
      clrType: clearClrType.value,
    }),
  );
  const summary = mergeClearAmountSummaries(rows);
  clearSummary.value = summary;
  applySummaryToClearForm(summary);
  ElMessage.success(
    `已汇总本地结算 ${summary.setlCount ?? 0} 笔（${rows.length} 个险种/业务类型）`,
  );
});

const { run: applyClear, loading: applyingClear } = useRequest(async () => {
  const [begndate, enddate] = requireDateRange(clearDateRange.value, '清算月份');
  const clrOptins = requireClrOptins();
  if (clearAmountForm.value.medfeeSumamt == null) {
    await summarizeClearAmounts();
  }
  /** 金额统一两位，避免 JS 浮点尾巴进平台 */
  const round2 = (v?: number) => (v == null ? undefined : Math.round(Number(v) * 100) / 100);
  const medfee = round2(clearAmountForm.value.medfeeSumamt);
  const result = await withSignIn(() =>
    applyJilinClearApi({
      contractBusNo: requireContractBusNo(),
      clrType: clearClrType.value,
      clrOptins,
      begndate,
      enddate,
      medfeeSumamt: medfee,
      // 平台仍要 med_sumfee；与医疗费同值静默提交，页面不单独填认可费用
      medSumfee: medfee,
      fundAppySum: round2(clearAmountForm.value.fundAppySum),
      cashPayamt: round2(clearAmountForm.value.cashPayamt),
      acctPay: round2(clearAmountForm.value.acctPay),
    }),
  );
  await ElMessageBox.alert(`清算申请已提交，事件 ID：${result.clrAppyEvtId ?? '—'}`, '清算申请');
  await loadClearLocalRecords();
});

async function handleRevokeClear(row: LocalClearApplyRecord) {
  const evtId = row.clrAppyEvtId?.trim();
  const optins = (row.clrOptins ?? formatClearOrgCode(contractBusNo.value))?.trim();
  if (!evtId || !optins) {
    ElMessage.warning('缺少清算事件 ID 或清算机构');
    return;
  }
  await ElMessageBox.confirm(`确认撤销清算申请 ${evtId}？`, '撤销清算', { type: 'warning' });
  await withSignIn(() =>
    revokeJilinClearApi({
      contractBusNo: requireContractBusNo(),
      clrAppyEvtId: evtId,
      clrOptins: optins,
    }),
  );
  ElMessage.success('撤销成功');
  await loadClearLocalRecords();
}

/** 日对账契约变更后刷新本地对账记录 */
function onDailyContractChange() {
  if (!contractBusNo.value) return;
  return Promise.all([loadDailyLocalRecords(), loadDetailLocalRecords()]);
}

/** 月清算机构变更后刷新本地清算记录 */
function onClearContractChange() {
  if (!contractBusNo.value) return;
  return loadClearLocalRecords();
}

onMounted(async () => {
  await loadChargeTypes();
  await loadWorkInjuryReconInit();
  if (contractBusNo.value) {
    await Promise.all([loadDailyLocalRecords(), loadDetailLocalRecords(), loadClearLocalRecords()]);
  }
});
</script>

<template>
  <Page auto-content-height content-class="recon-page">
    <div class="recon-page__body flex flex-col gap-3">
      <ElTabs v-model="activeTab" class="recon-tabs min-h-0 flex-1">
        <!-- ========== 日对账 ========== -->
        <ElTabPane label="日对账" name="daily" lazy>
          <div class="recon-a11y">
            <ElRow :gutter="12">
              <ElCol :xs="24" :lg="dailyLocalExpanded ? 15 : 24">
                <div class="mb-3">
                  <div class="mb-2 flex items-center justify-between gap-2">
                    <SectionTitle title="对总账" class="recon-section-title !mb-0" />
                    <ElButton link type="primary" @click="dailyLocalExpanded = !dailyLocalExpanded">
                      {{
                        dailyLocalExpanded
                          ? '收起本地记录'
                          : `本地记录（最新已平 ${dailyLocalRecords.length}）`
                      }}
                    </ElButton>
                  </div>
                  <div class="recon-panel">
                    <DailyLedgerList
                      v-model:contract-bus-no="contractBusNo"
                      :charge-type-options="chargeTypeOptions"
                      :loading-charge-types="loadingChargeTypes"
                      @contract-change="onDailyContractChange"
                      @reconciled="onDailyContractChange"
                      @recon-range-change="onDailyReconRangeChange"
                    />
                  </div>
                </div>
              </ElCol>

              <ElCol v-if="dailyLocalExpanded" :xs="24" :lg="9">
                <div class="mb-3">
                  <SectionTitle title="本地记录" class="recon-section-title mb-2" />
                  <div v-loading="loadingDailyRecords" class="recon-panel recon-local-records">
                    <div
                      class="recon-local-records__filter mb-2 text-sm"
                      style="color: var(--el-text-color-regular)"
                    >
                      仅展示各维度最新一条已平记录
                    </div>
                    <ElEmpty
                      v-if="dailyLocalRecords.length === 0"
                      description="暂无已平对账记录"
                      :image-size="64"
                    />
                    <div v-else class="flex flex-col gap-2">
                      <div
                        v-for="row in dailyLocalRecords"
                        :key="String(row.id ?? `${row.stmtBegndate}-${row.insutype}`)"
                        class="recon-local-record-item"
                      >
                        <div class="flex items-start justify-between gap-2">
                          <div class="min-w-0 flex-1">
                            <div
                              class="recon-local-record-title"
                              style="color: var(--el-text-color-primary)"
                            >
                              {{ row.stmtBegndate || '—' }}
                              <span v-if="row.stmtEnddate && row.stmtEnddate !== row.stmtBegndate">
                                至 {{ row.stmtEnddate }}
                              </span>
                            </div>
                            <div
                              class="recon-local-record-sub mt-0.5"
                              style="color: var(--el-text-color-regular)"
                            >
                              {{ formatInsutype(row.insutype) }}
                              <span style="color: var(--el-text-color-secondary)">
                                · {{ formatClrType(row.clrType) }}
                              </span>
                            </div>
                          </div>
                          <ElTag type="success" size="default" effect="light">
                            {{ formatStmtRslt(row.stmtRslt) }}
                          </ElTag>
                        </div>
                        <div class="recon-local-record-amounts mt-1.5">
                          <div class="recon-local-record-amt">
                            <span class="recon-local-record-amt__label">医疗费</span>
                            <span class="recon-local-record-amt__value">{{
                              formatMoney(row.medfeeSumamt)
                            }}</span>
                          </div>
                          <div class="recon-local-record-amt">
                            <span class="recon-local-record-amt__label">基金</span>
                            <span class="recon-local-record-amt__value">{{
                              formatMoney(row.fundPaySumamt)
                            }}</span>
                          </div>
                          <div class="recon-local-record-amt">
                            <span class="recon-local-record-amt__label">个账</span>
                            <span class="recon-local-record-amt__value">{{
                              formatMoney(row.acctPay)
                            }}</span>
                          </div>
                          <div class="recon-local-record-amt">
                            <span class="recon-local-record-amt__label">笔数</span>
                            <span class="recon-local-record-amt__value">{{
                              row.fixmedinsSetlCnt ?? '—'
                            }}</span>
                          </div>
                        </div>
                        <div
                          v-if="row.stmtRsltDscr"
                          class="recon-local-record-sub mt-1.5 leading-relaxed"
                          style="color: var(--el-text-color-secondary)"
                        >
                          {{ row.stmtRsltDscr }}
                        </div>
                        <div
                          v-else
                          class="recon-local-record-sub mt-1.5"
                          style="color: var(--el-color-success)"
                        >
                          对总账已平
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </ElCol>
            </ElRow>
          </div>
        </ElTabPane>

        <!-- ========== 月清算 ========== -->
        <ElTabPane label="月清算" name="clear" lazy>
          <div class="recon-a11y">
            <ElTabs v-model="clearSubTab" class="recon-sub-tabs">
              <ElTabPane label="清算申请" name="apply">
                <div class="recon-panel">
                  <ElForm inline label-width="88px" class="recon-form recon-clear-filters mb-3">
                    <ElFormItem label="清算机构" required>
                      <ElSelect
                        v-model="contractBusNo"
                        class="recon-filter-control recon-filter-control--org"
                        filterable
                        placeholder="请选择清算机构"
                        :loading="loadingChargeTypes"
                        @change="onClearContractChange"
                      >
                        <ElOption
                          v-for="item in chargeTypeOptions"
                          :key="item.busNo"
                          :label="formatClearOrgOptionLabel(item.label, item.busNo)"
                          :value="item.busNo"
                        />
                      </ElSelect>
                    </ElFormItem>
                    <ElFormItem label="业务类型" required>
                      <ElSelect
                        v-model="clearClrType"
                        class="recon-filter-control recon-filter-control--type"
                      >
                        <ElOption
                          v-for="item in YB_CLR_TYPE_OPTIONS"
                          :key="item.value"
                          :label="item.label"
                          :value="item.value"
                        />
                      </ElSelect>
                    </ElFormItem>
                    <ElFormItem label="清算区间" required>
                      <div class="flex items-center gap-2">
                        <ElDatePicker
                          v-model="clearDateRange"
                          v-bind="dateFieldProps"
                          class="recon-filter-control recon-filter-control--range"
                        />
                        <ElButton @click="setClearDateLastMonth">上月</ElButton>
                      </div>
                    </ElFormItem>
                  </ElForm>

                  <div class="recon-amount-panel mb-3">
                    <div class="mb-2 font-medium" style="color: var(--el-text-color-primary)">
                      清算金额
                    </div>
                    <ElForm label-width="108px" class="recon-form" size="large">
                      <ElRow :gutter="16">
                        <ElCol :xs="24" :sm="12" :lg="8">
                          <ElFormItem label="医疗费总额" class="!mb-2">
                            <ElInputNumber
                              v-model="clearAmountForm.medfeeSumamt"
                              :min="0"
                              :precision="2"
                              controls-position="right"
                              class="!w-full"
                            />
                          </ElFormItem>
                        </ElCol>
                        <ElCol :xs="24" :sm="12" :lg="8">
                          <ElFormItem label="基金申报" class="!mb-2">
                            <ElInputNumber
                              v-model="clearAmountForm.fundAppySum"
                              :min="0"
                              :precision="2"
                              controls-position="right"
                              class="!w-full"
                            />
                          </ElFormItem>
                        </ElCol>
                        <ElCol :xs="24" :sm="12" :lg="8">
                          <ElFormItem label="现金支付" class="!mb-2">
                            <ElInputNumber
                              v-model="clearAmountForm.cashPayamt"
                              :min="0"
                              :precision="2"
                              controls-position="right"
                              class="!w-full"
                            />
                          </ElFormItem>
                        </ElCol>
                        <ElCol :xs="24" :sm="12" :lg="8">
                          <ElFormItem label="个账支出" class="!mb-2">
                            <ElInputNumber
                              v-model="clearAmountForm.acctPay"
                              :min="0"
                              :precision="2"
                              controls-position="right"
                              class="!w-full"
                            />
                          </ElFormItem>
                        </ElCol>
                      </ElRow>
                    </ElForm>
                  </div>

                  <div class="flex flex-wrap gap-3">
                    <ElButton
                      size="large"
                      :loading="summarizingClear"
                      @click="summarizeClearAmounts"
                    >
                      汇总本地金额
                    </ElButton>
                    <ElButton
                      size="large"
                      type="primary"
                      :loading="applyingClear || summarizingClear"
                      @click="applyClear"
                    >
                      发起清算
                    </ElButton>
                  </div>
                  <p v-if="clearSummary" class="recon-hint-text mt-3">
                    已汇总本地结算 {{ clearSummary.setlCount ?? 0 }} 笔，核对金额后点「发起清算」
                  </p>
                </div>
              </ElTabPane>

              <ElTabPane label="记录与撤销" name="records">
                <div v-loading="loadingClearRecords" class="recon-panel">
                  <div class="mb-2 flex items-start justify-between gap-2">
                    <div class="min-w-0">
                      <SectionTitle title="本地清算记录" class="recon-section-title !mb-0" />
                      <p class="recon-hint-text mt-1">
                        本院发起清算时上报并冻住的金额，可按清算月筛选、撤销已申请记录
                      </p>
                    </div>
                    <div class="flex shrink-0 items-center gap-2">
                      <ElDatePicker
                        v-model="clearStatusYm"
                        type="month"
                        value-format="YYYYMM"
                        placeholder="清算月"
                        class="!w-40"
                        clearable
                      />
                      <ElButton :loading="loadingClearRecords" @click="loadClearLocalRecords">
                        刷新
                      </ElButton>
                    </div>
                  </div>
                  <ElTable
                    class="recon-clear-record-table"
                    :data="linkedClearLocalRecords"
                    border
                    stripe
                    size="default"
                    max-height="560"
                    row-key="clrAppyEvtId"
                    empty-text="暂无该月清算申请记录"
                    :header-cell-style="clearRecordHeaderCellStyle"
                  >
                    <ElTableColumn
                      prop="clrAppyEvtId"
                      label="事件号"
                      :width="CLEAR_RECORD_COL.evtId"
                      show-overflow-tooltip
                    />
                    <ElTableColumn
                      label="业务类型"
                      :width="CLEAR_RECORD_COL.clrType"
                      align="center"
                    >
                      <template #default="{ row }">
                        {{ formatClrType(row.clrType) }}
                      </template>
                    </ElTableColumn>
                    <ElTableColumn label="省市医保" :width="CLEAR_RECORD_COL.org" align="center">
                      <template #default="{ row }">
                        {{ formatProvCityInsurance(row.clrOptins) }}
                      </template>
                    </ElTableColumn>
                    <ElTableColumn label="状态" :width="CLEAR_RECORD_COL.stas" align="center">
                      <template #default="{ row }">
                        <ElTag :type="clearStasTagType(row.clrStas)" size="small" effect="light">
                          {{ formatClrStas(row.clrStas) }}
                        </ElTag>
                      </template>
                    </ElTableColumn>
                    <ElTableColumn
                      label="医疗费总额"
                      :min-width="CLEAR_RECORD_COL.medfee"
                      align="right"
                    >
                      <template #default="{ row }">
                        {{ formatMoney(row.medfeeSumamt) }}
                      </template>
                    </ElTableColumn>
                    <ElTableColumn label="基金" :min-width="CLEAR_RECORD_COL.fund" align="right">
                      <template #default="{ row }">
                        {{ formatMoney(row.fundAppySum) }}
                      </template>
                    </ElTableColumn>
                    <ElTableColumn
                      label="医保现金"
                      :min-width="CLEAR_RECORD_COL.cash"
                      align="right"
                    >
                      <template #default="{ row }">
                        {{ formatMoney(row.cashPayamt) }}
                      </template>
                    </ElTableColumn>
                    <ElTableColumn label="个账" :min-width="CLEAR_RECORD_COL.acct" align="right">
                      <template #default="{ row }">
                        {{ formatMoney(row.acctPay) }}
                      </template>
                    </ElTableColumn>
                    <ElTableColumn
                      label="申请时间"
                      :min-width="CLEAR_RECORD_COL.time"
                      align="center"
                      show-overflow-tooltip
                    >
                      <template #default="{ row }">
                        {{ formatClearRecordTime(row.createdAt) }}
                      </template>
                    </ElTableColumn>
                    <ElTableColumn
                      label="操作"
                      :width="CLEAR_RECORD_COL.action"
                      fixed="right"
                      align="center"
                    >
                      <template #default="{ row }">
                        <ElButton
                          v-if="row.clrStas === '10'"
                          link
                          type="danger"
                          @click="handleRevokeClear(row)"
                        >
                          撤销
                        </ElButton>
                        <span v-else style="color: var(--el-text-color-placeholder)">—</span>
                      </template>
                    </ElTableColumn>
                  </ElTable>
                </div>
              </ElTabPane>
            </ElTabs>
          </div>
        </ElTabPane>

        <!-- ========== 工伤（结算统计 → 明细勾稽 → 对账确认） ========== -->
        <ElTabPane v-if="wiModuleEnabled" label="工伤对账" name="workInjury" lazy>
          <ElCard shadow="never" class="wi-recon-card recon-a11y">
            <template #header>
              <div class="flex flex-col gap-2">
                <span class="wi-recon-title">工伤保险对账</span>
                <ElSteps :active="wiStep - 1" finish-status="success" align-center>
                  <ElStep title="结算统计" description="汇总结算与收银金额" />
                  <ElStep title="按行明细" description="逐日点「明细」勾稽并确认账平" />
                  <ElStep title="对账确认" description="可选 1322 确认已结束月" />
                </ElSteps>
              </div>
            </template>

            <ElForm label-width="132px" class="recon-form wi-recon-form" size="large">
              <ElRow :gutter="20">
                <ElCol :xs="24" :sm="12" :lg="8">
                  <ElFormItem label="工伤费用性质" required>
                    <ElSelect
                      v-model="wiContractId"
                      class="!w-full"
                      filterable
                      placeholder="请选择"
                    >
                      <ElOption
                        v-for="item in wiContractOptions"
                        :key="item.contractId"
                        :label="`${item.label}（${item.busNo}）`"
                        :value="item.contractId"
                      />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol :xs="24" :sm="12" :lg="8">
                  <ElFormItem label="对账日期" required>
                    <ElDatePicker v-model="wiDateRange" v-bind="dateFieldProps" class="!w-full" />
                  </ElFormItem>
                </ElCol>
                <ElCol :xs="24" :sm="12" :lg="8">
                  <ElFormItem label="统筹区编码">
                    <ElInput
                      v-model="wiSocialPoolCode"
                      placeholder="220199"
                      :disabled="wi1320Scope !== 'LOCAL'"
                    />
                  </ElFormItem>
                </ElCol>
                <ElCol :xs="24" :sm="12" :lg="8">
                  <ElFormItem label="1320口径">
                    <ElSelect v-model="wi1320Scope" class="!w-full">
                      <ElOption
                        v-for="item in WI1320_SCOPE_OPTIONS"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol v-if="wiStep >= 3" :xs="24" :sm="12" :lg="8">
                  <ElFormItem label="确认对账月" required>
                    <ElDatePicker
                      v-model="wiMonth"
                      type="month"
                      value-format="YYYYMM"
                      placeholder="默认上月"
                      class="!w-full"
                    />
                  </ElFormItem>
                </ElCol>
              </ElRow>

              <div class="mb-3 flex flex-wrap gap-3">
                <ElButton size="large" :loading="wiHisLoading" @click="runWorkInjuryHisStat">
                  1. 结算统计
                </ElButton>
                <ElButton
                  size="large"
                  type="success"
                  :loading="wi1322Loading"
                  :disabled="wiStep < 3 || !wiAmountsBalanced"
                  @click="runWorkInjury1322"
                >
                  2. 对账确认
                </ElButton>
                <ElButton
                  size="large"
                  :loading="wi1320Loading"
                  :disabled="wiStep < 2 || wiDailyRows.length === 0"
                  @click="runWorkInjury1320"
                >
                  可选·重拉平台1320（不平）
                </ElButton>
              </div>
            </ElForm>

            <ElTable
              :data="wiTableData"
              border
              stripe
              max-height="480"
              class="wi-recon-table mt-1"
              :row-class-name="wiTableRowClassName"
            >
              <ElTableColumn prop="date" label="对账日期" min-width="140" align="center" />
              <ElTableColumn label="结算医疗费" align="right" min-width="120">
                <template #default="{ row }">{{ formatMoney(row.medfeeSumamt) }}</template>
              </ElTableColumn>
              <ElTableColumn label="结算统筹" align="right" min-width="112">
                <template #default="{ row }">{{ formatMoney(row.hifpPay) }}</template>
              </ElTableColumn>
              <ElTableColumn label="结算现金" align="right" min-width="112">
                <template #default="{ row }">{{ formatMoney(row.psnCashPay) }}</template>
              </ElTableColumn>
              <ElTableColumn label="笔数" align="center" min-width="72">
                <template #default="{ row }">{{ row.setlCount ?? 0 }}</template>
              </ElTableColumn>
              <ElTableColumn v-if="wiStep >= 2" label="收银医疗费" align="right" min-width="128">
                <template #default="{ row }">
                  <template v-if="row.paymentMedfee != null">
                    <div>{{ formatMoney(row.paymentMedfee) }}</div>
                    <div
                      class="wi-recon-table__diff"
                      :style="{
                        color: wiDiffColor(amountDiff(row.medfeeSumamt, row.paymentMedfee)),
                      }"
                    >
                      差 {{ formatMoney(amountDiff(row.medfeeSumamt, row.paymentMedfee)) }}
                    </div>
                  </template>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn v-if="wiStep >= 2" label="收银统筹" align="right" min-width="120">
                <template #default="{ row }">
                  <template v-if="row.paymentHifp != null">
                    <div>{{ formatMoney(row.paymentHifp) }}</div>
                    <div
                      class="wi-recon-table__diff"
                      :style="{ color: wiDiffColor(amountDiff(row.hifpPay, row.paymentHifp)) }"
                    >
                      差 {{ formatMoney(amountDiff(row.hifpPay, row.paymentHifp)) }}
                    </div>
                  </template>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn v-if="wiStep >= 2" label="收银现金" align="right" min-width="120">
                <template #default="{ row }">
                  <template v-if="row.paymentCash != null">
                    <div>{{ formatMoney(row.paymentCash) }}</div>
                    <div
                      class="wi-recon-table__diff"
                      :style="{
                        color: wiDiffColor(amountDiff(row.psnCashPay, row.paymentCash)),
                      }"
                    >
                      差 {{ formatMoney(amountDiff(row.psnCashPay, row.paymentCash)) }}
                    </div>
                  </template>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn v-if="wiStep >= 2" label="平台医疗费" align="right" min-width="128">
                <template #default="{ row }">
                  <template v-if="row.platformMedfee != null">
                    <div>{{ formatMoney(row.platformMedfee) }}</div>
                    <div
                      class="wi-recon-table__diff"
                      :style="{
                        color: wiDiffColor(amountDiff(row.medfeeSumamt, row.platformMedfee)),
                      }"
                    >
                      差 {{ formatMoney(amountDiff(row.medfeeSumamt, row.platformMedfee)) }}
                    </div>
                  </template>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn v-if="wiStep >= 2" label="平台统筹" align="right" min-width="120">
                <template #default="{ row }">
                  <template v-if="row.platformHifp != null">
                    <div>{{ formatMoney(row.platformHifp) }}</div>
                    <div
                      class="wi-recon-table__diff"
                      :style="{ color: wiDiffColor(amountDiff(row.hifpPay, row.platformHifp)) }"
                    >
                      差 {{ formatMoney(amountDiff(row.hifpPay, row.platformHifp)) }}
                    </div>
                  </template>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn v-if="wiStep >= 2" label="平台现金" align="right" min-width="120">
                <template #default="{ row }">
                  <template v-if="row.platformCash != null">
                    <div>{{ formatMoney(row.platformCash) }}</div>
                    <div
                      class="wi-recon-table__diff"
                      :style="{
                        color: wiDiffColor(amountDiff(row.psnCashPay, row.platformCash)),
                      }"
                    >
                      差 {{ formatMoney(amountDiff(row.psnCashPay, row.platformCash)) }}
                    </div>
                  </template>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="wiStep >= 2"
                label="平不平"
                align="center"
                min-width="88"
                fixed="right"
              >
                <template #default="{ row }">
                  <ElTag
                    v-if="row.status"
                    :type="wiStatusTagType(row.status)"
                    effect="light"
                    size="small"
                  >
                    {{ RECON_STATUS_LABEL[row.status] }}
                  </ElTag>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="wiStep >= 2"
                label="操作"
                align="center"
                min-width="140"
                fixed="right"
              >
                <template #default="{ row }">
                  <template v-if="!row.isTotal">
                    <ElButton type="primary" link @click="openWiDetailDialog(row)"> 明细 </ElButton>
                    <ElButton
                      type="info"
                      link
                      :loading="wiReconcilingDate === row.date"
                      :disabled="wi1320Loading && wiReconcilingDate !== row.date"
                      @click="handleWiReconcileRow(row)"
                    >
                      1320
                    </ElButton>
                  </template>
                </template>
              </ElTableColumn>
            </ElTable>

            <div v-if="wiTotalRow" class="wi-recon-total-bar mt-2">
              <span class="wi-recon-total-bar__label">总计</span>
              <span>结算 医疗费 {{ formatMoney(wiTotalRow.medfeeSumamt) }}</span>
              <span>统筹 {{ formatMoney(wiTotalRow.hifpPay) }}</span>
              <span>现金 {{ formatMoney(wiTotalRow.psnCashPay) }}</span>
              <span>{{ wiTotalRow.setlCount ?? 0 }} 笔</span>
              <template v-if="wiStep >= 2 && wiTotalRow.paymentMedfee != null">
                <span class="wi-recon-total-bar__sep">|</span>
                <span>收银 医疗费 {{ formatMoney(wiTotalRow.paymentMedfee) }}</span>
                <span>统筹 {{ formatMoney(wiTotalRow.paymentHifp) }}</span>
                <span>现金 {{ formatMoney(wiTotalRow.paymentCash) }}</span>
                <ElTag
                  :type="wiStatusTagType(wiTotalRow.status)"
                  effect="light"
                  size="small"
                  class="ml-1"
                >
                  {{ RECON_STATUS_LABEL[wiTotalRow.status] }}
                </ElTag>
              </template>
              <template
                v-if="wiStep >= 2 && wiHasPlatformColumns && wiTotalRow.platformMedfee != null"
              >
                <span class="wi-recon-total-bar__sep">|</span>
                <span>平台 医疗费 {{ formatMoney(wiTotalRow.platformMedfee) }}</span>
                <span>统筹 {{ formatMoney(wiTotalRow.platformHifp) }}</span>
                <span>现金 {{ formatMoney(wiTotalRow.platformCash) }}</span>
              </template>
            </div>

            <div class="wi-recon-footer mt-3">
              本地笔数：{{ wiHisAmounts.setlCount }}
              <template v-if="wiStep === 1"> · 请先「结算统计」</template>
              <template v-else-if="wiStep === 2 && !wiPaymentReady">
                · 请先「结算统计」拉取收银汇总
              </template>
              <template v-else-if="wiStep === 2 && !wiAmountsBalanced">
                · 请逐日点「明细」核对结算↔收银；无问题则「确认账平」
              </template>
              <template v-else-if="wiStep >= 3">
                · 明细已账平，选已结束月份后「对账确认」
              </template>
            </div>

            <ElAlert
              v-if="wiLastSummary"
              class="wi-recon-alert mt-4"
              :type="wiAmountsBalanced || wiStep === 1 ? 'success' : 'warning'"
              :closable="false"
              show-icon
              :title="wiLastSummary"
            />

            <ElDialog
              v-model="wiDetailDialogVisible"
              :title="`明细核对 · ${wiDetailDay ?? ''}`"
              width="1280px"
              destroy-on-close
            >
              <p class="mb-3 text-sm" style="color: var(--el-text-color-secondary)">
                核对本地工伤结算与收银明细：有效结算须 2207
                已入账且金额勾稽平；无收银视为问题。已撤销不计入问题。可随时重新拉取本地明细或平台
                1321。
              </p>
              <div class="mb-3 flex flex-wrap gap-2">
                <ElButton
                  type="success"
                  :disabled="!wiDetailCanMarkBalanced"
                  @click="confirmWiDetailBalanced"
                >
                  确认账平
                </ElButton>
                <ElButton :loading="wiDetailLoading" @click="reloadWiDetailSettlements">
                  重新拉取本地明细
                </ElButton>
                <ElButton
                  :loading="wiDetail1321Loading"
                  :disabled="wiDetailLoading || wiDetailRows.length === 0"
                  @click="runWiDetail1321All"
                >
                  重新拉取平台1321
                </ElButton>
              </div>
              <ElTable
                v-loading="wiDetailLoading"
                :data="wiDetailRows"
                border
                stripe
                size="small"
                max-height="420"
              >
                <ElTableColumn
                  prop="iptOtpNo"
                  label="流水号"
                  min-width="140"
                  show-overflow-tooltip
                />
                <ElTableColumn prop="psnName" label="姓名" width="80" />
                <ElTableColumn label="结算医疗费" align="right" width="100">
                  <template #default="{ row }">{{ formatMoney(row.medfeeSumamt) }}</template>
                </ElTableColumn>
                <ElTableColumn label="结算统筹" align="right" width="92">
                  <template #default="{ row }">{{ formatMoney(row.fundPaySumamt) }}</template>
                </ElTableColumn>
                <ElTableColumn label="结算现金" align="right" width="92">
                  <template #default="{ row }">{{ formatMoney(row.psnCashPay) }}</template>
                </ElTableColumn>
                <ElTableColumn label="收银医疗费" align="right" width="100">
                  <template #default="{ row }">
                    <template v-if="row.paymentId == null || row.paymentId === ''">无收银</template>
                    <template v-else>{{ formatMoney(row.paymentMedfee) }}</template>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="收银统筹" align="right" width="92">
                  <template #default="{ row }">
                    {{
                      row.paymentId == null || row.paymentId === ''
                        ? '—'
                        : formatMoney(row.paymentHifp)
                    }}
                  </template>
                </ElTableColumn>
                <ElTableColumn label="收银现金" align="right" width="92">
                  <template #default="{ row }">
                    {{
                      row.paymentId == null || row.paymentId === ''
                        ? '—'
                        : formatMoney(row.paymentCash)
                    }}
                  </template>
                </ElTableColumn>
                <ElTableColumn label="平台医疗费" align="right" width="100">
                  <template #default="{ row }">
                    {{ row.platformMedfee != null ? formatMoney(row.platformMedfee) : '—' }}
                  </template>
                </ElTableColumn>
                <ElTableColumn label="平台统筹" align="right" width="92">
                  <template #default="{ row }">
                    {{ row.platformHifp != null ? formatMoney(row.platformHifp) : '—' }}
                  </template>
                </ElTableColumn>
                <ElTableColumn label="平台现金" align="right" width="92">
                  <template #default="{ row }">
                    {{ row.platformCash != null ? formatMoney(row.platformCash) : '—' }}
                  </template>
                </ElTableColumn>
                <ElTableColumn label="2207" width="84" align="center">
                  <template #default="{ row }">
                    <ElTag :type="wiDetail2207Tag(row)" size="small" effect="light">
                      {{ wiDetail2207Label(row) }}
                    </ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="勾稽" width="88" align="center">
                  <template #default="{ row }">
                    <ElTag :type="wiDetailStatusTag(row)" size="small" effect="light">
                      {{ wiDetailStatusLabel(row) }}
                    </ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn
                  prop="mdtrtId"
                  label="单据号"
                  min-width="110"
                  show-overflow-tooltip
                />
              </ElTable>
              <ElEmpty
                v-if="!wiDetailLoading && wiDetailRows.length === 0"
                description="当日无工伤结算回执，可直接确认账平"
              />
            </ElDialog>
          </ElCard>
        </ElTabPane>
      </ElTabs>
    </div>

    <AppDialog v-model="detailDrawerVisible" :title="detailDrawerTitle" width="720px">
      <template #default>
        <p v-if="detailDrawerMeta" class="mb-3 text-sm" style="color: var(--el-text-color-regular)">
          {{ detailDrawerMeta }}
        </p>
        <ElTable
          v-if="detailDrawerRecords.length"
          :data="detailDrawerRecords"
          border
          stripe
          size="small"
        >
          <ElTableColumn prop="stmtBegndate" label="开始日" width="110" />
          <ElTableColumn prop="stmtEnddate" label="结束日" width="110" />
          <ElTableColumn label="业务类型" width="88" align="center">
            <template #default="{ row }">
              {{ formatClrType(row.clrType) }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="rowCount" label="明细笔数" width="88" align="right" />
          <ElTableColumn prop="mismatchCount" label="差异笔数" width="88" align="right" />
          <ElTableColumn
            prop="resultFileQuryNo"
            label="结果文件号"
            min-width="140"
            show-overflow-tooltip
          />
        </ElTable>
        <ElEmpty
          v-else
          description="暂无关联的明细对账流水，请在左侧发起「对明细账」"
          :image-size="72"
          class="!py-4"
        />
      </template>
      <template #footer>
        <ElButton type="primary" @click="detailDrawerVisible = false"> 关闭 </ElButton>
      </template>
    </AppDialog>
  </Page>
</template>

<style scoped>
.recon-section-title {
  --section-title-font-size: 16px;
  --section-title-bar-height: 14px;
}

.recon-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
  padding: 12px;
}

.recon-local-records {
  max-height: 560px;
  overflow: auto;
}

.recon-local-record-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  padding: 10px 12px;
  background: var(--el-fill-color-blank);
}

.recon-local-record-amounts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 12px;
}

.recon-local-record-amt {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  line-height: 1.4;
}

.recon-local-record-amt__label {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

.recon-local-record-amt__value {
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.recon-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.recon-amount-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-light);
  padding: 12px 12px 4px;
}

.recon-form :deep(.el-form-item__label) {
  color: var(--el-text-color-regular);
  white-space: nowrap;
  line-height: 32px;
}

.recon-clear-filters {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  width: 100%;
  overflow: visible;
}

.recon-clear-filters :deep(.el-form-item) {
  flex-shrink: 0;
  margin-right: 12px;
  margin-bottom: 8px;
}

.recon-clear-filters :deep(.el-form-item__label) {
  width: 80px !important;
  min-width: 80px;
  height: 32px;
  line-height: 32px;
  white-space: nowrap !important;
  word-break: keep-all;
}

/* inline 并排布局，勿再 margin-left 叠宽 */
.recon-clear-filters :deep(.el-form-item__content) {
  margin-left: 0 !important;
}

.recon-filter-control--org {
  width: 220px;
}

.recon-filter-control--type {
  width: 120px;
}

.recon-filter-control--range {
  width: 280px;
}

.recon-amount-panel :deep(.el-form-item__label) {
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

/* 三 Tab 共用适老化 */
.recon-a11y {
  font-size: 16px;
}

.recon-a11y :deep(.el-button) {
  font-size: 16px;
}

.recon-a11y :deep(.el-form-item__label) {
  font-size: 16px;
}

.recon-a11y :deep(.el-table) {
  font-size: 16px;
}

.recon-a11y :deep(.el-table th.el-table__cell) {
  font-size: 16px;
  font-weight: 600;
}

.recon-sub-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.recon-sub-tabs :deep(.el-tabs__item) {
  font-size: 16px;
  height: 44px;
  line-height: 44px;
}

.recon-hint-text {
  font-size: 15px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

/* 不走 SFC scoped 承重：提示条仅补字号，布局用模板原子类 */
/* 不走 SFC scoped 承重：记录表表头禁止折行 */
.recon-clear-record-table :deep(th.el-table__cell .cell) {
  white-space: nowrap;
  word-break: keep-all;
  line-height: 22px;
}

.recon-local-record-title {
  font-size: 15px;
  font-weight: 600;
}

.recon-local-record-sub {
  font-size: 14px;
}

.recon-a11y .recon-local-record-amt {
  font-size: 14px;
}

/* 工伤对账：适配 1600×900，字号加大便于现场查看 */
.wi-recon-card {
  width: 100%;
  max-width: 1480px;
  font-size: 16px;
}

.wi-recon-title {
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--el-text-color-primary);
}

.wi-recon-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.wi-recon-form :deep(.el-form-item__label) {
  font-size: 16px;
  line-height: 40px;
  height: 40px;
}

.wi-recon-form :deep(.el-input__inner),
.wi-recon-form :deep(.el-select__selected-item),
.wi-recon-form :deep(.el-range-input) {
  font-size: 16px;
}

.wi-recon-form :deep(.el-button) {
  font-size: 16px;
  min-width: 112px;
}

.wi-recon-table {
  width: 100%;
}

.wi-recon-table :deep(.el-table__header th) {
  font-size: 16px;
  font-weight: 600;
}

.wi-recon-table :deep(.el-table__cell) {
  font-size: 17px;
  padding: 14px 0;
}

.wi-recon-table :deep(.wi-recon-table__total-row) {
  font-weight: 600;
  background-color: var(--el-fill-color-light);
}

.wi-recon-total-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  align-items: center;
  padding: 12px 16px;
  font-size: 16px;
  font-weight: 500;
  background-color: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.wi-recon-total-bar__label {
  font-weight: 700;
}

.wi-recon-total-bar__sep {
  color: var(--el-text-color-secondary);
}

.wi-recon-table__diff {
  margin-top: 4px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.3;
}

.wi-recon-footer {
  font-size: 15px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.wi-recon-alert :deep(.el-alert__title) {
  font-size: 16px;
}

.wi-recon-card :deep(.el-step__title) {
  font-size: 15px;
}

.wi-recon-card :deep(.el-step__description) {
  font-size: 13px;
}
</style>
