<script setup lang="ts">
import type {
  ClearAmountSummary,
  ClearStatus3205ARow,
  LocalClearApplyRecord,
  LocalReconDailyRecord,
  LocalReconDetailRecord,
} from '#/chs/api';
import type { ChargeType } from '#/api/core/common';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElCol,
  ElDatePicker,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
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
  queryClearApplyStatusApi,
  revokeJilinClearApi,
  startWorkInjuryReconApi,
  mergeClearAmountSummaries,
  summarizeClearAmountsApi,
} from '#/chs/api';
import {
  formatClrStas,
  formatClrType,
  formatInsutype,
  formatStmtRslt,
  isStmtBalanced,
  YB_CLR_STAS_OPTIONS,
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
import { normalizeWorkInjuryWinOutput } from '#/chs/work-injury/probe-work-injury-filing';
import {
  httpWorkInjuryModuleCapability,
  httpWorkInjurySubmitInvokeResult,
} from '#/chs/work-injury/work-injury.http';
import { isWorkInjuryContractCategory } from '#/views/outpatient/charge/register/services/charge-type-category';
import AppDialog from '#/components/app-dialog.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import DailyLedgerList from '#/chs/reconciliation/components/daily-ledger-list.vue';
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
};

function formatMoney(value?: number | string | null): string {
  if (value == null || value === '') return '—';
  const n = Number(value);
  return Number.isFinite(n) ? n.toFixed(2) : '—';
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
/** 本地记录结果筛选：全部 / 平 / 不平 */
const dailyLocalStmtFilter = ref<'all' | 'balanced' | 'unbalanced'>('all');

const dailyLocalBalancedCount = computed(
  () => dailyLocalRecords.value.filter((row) => !isRecordUnbalanced(row)).length,
);
const dailyLocalUnbalancedCount = computed(
  () => dailyLocalRecords.value.filter((row) => isRecordUnbalanced(row)).length,
);
const filteredDailyLocalRecords = computed(() => {
  const rows = dailyLocalRecords.value;
  if (dailyLocalStmtFilter.value === 'balanced') {
    return rows.filter((row) => !isRecordUnbalanced(row));
  }
  if (dailyLocalStmtFilter.value === 'unbalanced') {
    return rows.filter((row) => isRecordUnbalanced(row));
  }
  return rows;
});

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
const clearStatusRows = ref<ClearStatus3205ARow[]>([]);
const clearStatusStas = ref('10');
const clearStatusYm = ref(defaultLastMonthYm());
const clearLocalRecords = ref<LocalClearApplyRecord[]>([]);
const clearAmountForm = ref({
  medfeeSumamt: undefined as number | undefined,
  medSumfee: undefined as number | undefined,
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
const wiSocialPoolCode = ref<string>('220100');
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
const wiPlatformAmounts = ref({
  medfeeSumamt: undefined as number | undefined,
  hifpPay: undefined as number | undefined,
  psnCashPay: undefined as number | undefined,
});

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
    socialPoolCode: (wiSocialPoolCode.value ?? '').trim() || '220100',
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
  const raw = output[key];
  if (raw == null || raw === '') return undefined;
  const n = Number(raw);
  return Number.isFinite(n) ? n : undefined;
}

function amountDiff(his?: number, platform?: number): number | undefined {
  if (his == null || platform == null) return undefined;
  return Number((his - platform).toFixed(2));
}

const wiMedfeeDiff = computed(() =>
  amountDiff(wiHisAmounts.value.medfeeSumamt, wiPlatformAmounts.value.medfeeSumamt),
);
const wiFundDiff = computed(() =>
  amountDiff(wiHisAmounts.value.hifpPay, wiPlatformAmounts.value.hifpPay),
);
const wiCashDiff = computed(() =>
  amountDiff(wiHisAmounts.value.psnCashPay, wiPlatformAmounts.value.psnCashPay),
);

const wiPlatformReady = computed(
  () => wiPlatformAmounts.value.medfeeSumamt != null && wiPlatformAmounts.value.hifpPay != null,
);

const wiAmountsBalanced = computed(() => {
  if (!wiPlatformReady.value) return false;
  const diffs = [wiMedfeeDiff.value, wiFundDiff.value, wiCashDiff.value];
  return diffs.every((d) => d != null && d === 0);
});

function resetWiPlatformAmounts() {
  wiPlatformAmounts.value = {
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
    const cap = await httpWorkInjuryModuleCapability();
    wiModuleEnabled.value = cap?.enabled === true;
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

/** 老 HIS「结算统计」：汇总本地 WORK_INJURY 结算回执金额 */
async function runWorkInjuryHisStat() {
  try {
    const opt = requireWiContract();
    const [begin, end] = requireWiDateRange();
    wiHisLoading.value = true;
    const rows = await summarizeClearAmountsApi({
      contractBusNo: opt.busNo,
      begndate: begin,
      enddate: end,
    });
    const merged = mergeClearAmountSummaries(rows);
    wiHisAmounts.value = {
      medfeeSumamt: Number(merged.medfeeSumamt ?? 0),
      hifpPay: Number(merged.fundAppySum ?? 0),
      psnCashPay: Number(merged.cashPayamt ?? 0),
      setlCount: Number(merged.setlCount ?? 0),
    };
    resetWiPlatformAmounts();
    wiStep.value = 2;
    syncWiMonthFromDateRange();
    wiLastSummary.value = `本地结算统计完成：共 ${wiHisAmounts.value.setlCount} 笔`;
    ElMessage.success(wiLastSummary.value);
  } catch (error) {
    const message = error instanceof Error ? error.message : '本地结算统计失败';
    ElMessage.error(message);
  } finally {
    wiHisLoading.value = false;
  }
}

/** 老 HIS「总额对账」：仅 1320，展示平台金额供比对 */
async function runWorkInjury1320() {
  try {
    const opt = requireWiContract();
    const formContext = buildWiFormContext(false);
    if (!(await isWorkInjuryPluginOnline())) {
      ElMessage.warning('请启动 OpenHIS.Win 并启用工伤模块');
      return;
    }
    wi1320Loading.value = true;
    wiLastSummary.value = undefined;
    const prep = await startWorkInjuryReconApi({
      contractId: opt.contractId,
      formContext,
      phase: 'RECON',
    });
    if (prep.nextAction !== 'INVOKE_WIN' || prep.infno !== '1320') {
      throw new Error(`总额对账 prepare 异常：infno=${prep.infno ?? '(空)'}`);
    }
    if (!prep.sessionId || !prep.stepToken) {
      throw new Error('总额对账缺少 sessionId/stepToken');
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
      throw new Error(winRes.errMsg || '1320 总额对账失败');
    }
    wiPlatformAmounts.value = {
      medfeeSumamt: parseWiAmount(output, 'medfee_sumamt'),
      hifpPay: parseWiAmount(output, 'hifp_pay'),
      psnCashPay: parseWiAmount(output, 'psn_cash_pay'),
    };
    if (wiAmountsBalanced.value) {
      wiStep.value = 3;
      wiLastSummary.value = '1320 总额对账完成，金额已平，可进行对账确认';
    } else {
      wiStep.value = 2;
      wiLastSummary.value = '1320 总额对账完成，金额不平，请查明差额后再确认';
    }
    ElMessage.success(wiLastSummary.value);
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
    if (wiPlatformAmounts.value.medfeeSumamt == null || wiPlatformAmounts.value.hifpPay == null) {
      await ElMessageBox.alert('请先完成「总额对账(1320)」并确认金额后再对账确认。', '无法确认', {
        type: 'warning',
      });
      return;
    }
    const diffs = [wiMedfeeDiff.value, wiFundDiff.value, wiCashDiff.value];
    if (diffs.some((d) => d != null && d !== 0)) {
      await ElMessageBox.alert(
        'HIS 与平台金额不平，禁止对账确认。请先查明 1320 差额（当前联调常见：平台回 0）。',
        '无法确认',
        { type: 'warning' },
      );
      return;
    }
    if (!(await isWorkInjuryPluginOnline())) {
      ElMessage.warning('请启动 OpenHIS.Win 并启用工伤模块');
      return;
    }
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
    medSumfee: Number(summary.medSumfee ?? summary.medfeeSumamt ?? 0),
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
  await ensureInsuranceSignIn(requireContractBusNo());
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
  dailyLocalRecords.value = [...byId.values()];
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

const { run: loadClearLocalRecords, loading: loadingClearRecords } = useRequest(async () => {
  clearLocalRecords.value = await listLocalClearAppliesApi({
    contractBusNo: requireContractBusNo(),
  });
});

const { run: summarizeClearAmounts, loading: summarizingClear } = useRequest(async () => {
  const [begndate, enddate] = requireDateRange(clearDateRange.value, '清算月份');
  const rows = await withSignIn(() =>
    summarizeClearAmountsApi({ contractBusNo: requireContractBusNo(), begndate, enddate }),
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
  const result = await withSignIn(() =>
    applyJilinClearApi({
      contractBusNo: requireContractBusNo(),
      clrType: clearClrType.value,
      clrOptins,
      begndate,
      enddate,
      ...clearAmountForm.value,
    }),
  );
  await ElMessageBox.alert(`清算申请已提交，事件 ID：${result.clrAppyEvtId ?? '—'}`, '清算申请');
  await loadClearLocalRecords();
});

const { run: queryClearStatus, loading: queryingStatus } = useRequest(async () => {
  const clrOptins = requireClrOptins();
  if (['20', '50'].includes(clearStatusStas.value) && !clearStatusYm.value.trim()) {
    throw new Error('查询已受理/已清算状态需填写清算月份（yyyyMM）');
  }
  clearStatusRows.value = await withSignIn(() =>
    queryClearApplyStatusApi({
      contractBusNo: requireContractBusNo(),
      clrOptins,
      clrStas: clearStatusStas.value,
      clrYm: clearStatusYm.value.trim() || undefined,
    }),
  );
  ElMessage.success(`查询到 ${clearStatusRows.value.length} 条清算状态`);
});

async function handleRevokeClear(row: ClearStatus3205ARow | LocalClearApplyRecord) {
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
                          : `本地记录（${dailyLocalRecords.length} · 平 ${dailyLocalBalancedCount} / 不平 ${dailyLocalUnbalancedCount}）`
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
                      v-if="dailyLocalRecords.length > 0"
                      class="recon-local-records__filter mb-2 flex items-center justify-between gap-2"
                    >
                      <ElRadioGroup v-model="dailyLocalStmtFilter" size="default">
                        <ElRadioButton value="all">
                          全部 {{ dailyLocalRecords.length }}
                        </ElRadioButton>
                        <ElRadioButton value="balanced">
                          平 {{ dailyLocalBalancedCount }}
                        </ElRadioButton>
                        <ElRadioButton value="unbalanced">
                          不平 {{ dailyLocalUnbalancedCount }}
                        </ElRadioButton>
                      </ElRadioGroup>
                    </div>
                    <ElEmpty
                      v-if="dailyLocalRecords.length === 0"
                      description="暂无对账记录"
                      :image-size="64"
                    />
                    <ElEmpty
                      v-else-if="filteredDailyLocalRecords.length === 0"
                      description="当前筛选下无记录"
                      :image-size="64"
                    />
                    <div v-else class="flex flex-col gap-2">
                      <div
                        v-for="row in filteredDailyLocalRecords"
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
                          <ElTag
                            :type="isRecordUnbalanced(row) ? 'danger' : 'success'"
                            size="default"
                            effect="light"
                          >
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
                          :style="{
                            color: isRecordUnbalanced(row)
                              ? 'var(--el-color-danger)'
                              : 'var(--el-text-color-secondary)',
                          }"
                        >
                          {{ row.stmtRsltDscr }}
                        </div>
                        <div
                          v-else-if="!isRecordUnbalanced(row)"
                          class="recon-local-record-sub mt-1.5"
                          style="color: var(--el-color-success)"
                        >
                          对总账已平
                        </div>
                        <div v-if="isRecordUnbalanced(row)" class="mt-1.5 flex justify-end">
                          <ElButton link type="primary" @click="openDetailDrawer(row)">
                            本地明细流水
                          </ElButton>
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
                          <ElFormItem label="认可费用" class="!mb-2">
                            <ElInputNumber
                              v-model="clearAmountForm.medSumfee"
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
                <ElRow :gutter="12">
                  <ElCol :xs="24" :lg="14">
                    <div class="recon-panel mb-3">
                      <div class="mb-2 font-medium" style="color: var(--el-text-color-primary)">
                        平台清算状态（3205A）
                      </div>
                      <ElForm inline label-width="72px" class="recon-form mb-2">
                        <ElFormItem label="状态">
                          <ElSelect v-model="clearStatusStas" class="!w-36">
                            <ElOption
                              v-for="item in YB_CLR_STAS_OPTIONS"
                              :key="item.value"
                              :label="item.label"
                              :value="item.value"
                            />
                          </ElSelect>
                        </ElFormItem>
                        <ElFormItem label="清算月">
                          <ElDatePicker
                            v-model="clearStatusYm"
                            type="month"
                            value-format="YYYYMM"
                            placeholder="选择月份"
                            class="!w-40"
                            clearable
                          />
                        </ElFormItem>
                        <ElFormItem>
                          <ElButton
                            size="large"
                            :loading="queryingStatus"
                            @click="queryClearStatus"
                          >
                            查询
                          </ElButton>
                        </ElFormItem>
                      </ElForm>
                      <ElTable
                        :data="clearStatusRows"
                        border
                        stripe
                        max-height="360"
                        empty-text="暂无查询结果"
                      >
                        <ElTableColumn
                          prop="clrAppyEvtId"
                          label="事件号"
                          min-width="140"
                          show-overflow-tooltip
                        />
                        <ElTableColumn label="状态" width="100">
                          <template #default="{ row }">
                            {{ formatClrStas(row.clrStas) }}
                          </template>
                        </ElTableColumn>
                        <ElTableColumn prop="begndate" label="开始" width="110" />
                        <ElTableColumn prop="enddate" label="结束" width="110" />
                        <ElTableColumn label="医疗费" width="110" align="right">
                          <template #default="{ row }">
                            {{ formatMoney(row.medfeeSumamt) }}
                          </template>
                        </ElTableColumn>
                        <ElTableColumn label="操作" width="88" fixed="right">
                          <template #default="{ row }">
                            <ElButton
                              v-if="row.clrStas === '10'"
                              link
                              type="danger"
                              @click="handleRevokeClear(row)"
                            >
                              撤销
                            </ElButton>
                          </template>
                        </ElTableColumn>
                      </ElTable>
                    </div>
                  </ElCol>
                  <ElCol :xs="24" :lg="10">
                    <div v-loading="loadingClearRecords" class="recon-panel recon-local-records">
                      <div class="mb-2 font-medium" style="color: var(--el-text-color-primary)">
                        本地清算记录
                      </div>
                      <ElEmpty
                        v-if="clearLocalRecords.length === 0"
                        description="暂无清算申请记录"
                        :image-size="64"
                      />
                      <div v-else class="flex flex-col gap-2">
                        <div
                          v-for="row in clearLocalRecords"
                          :key="
                            String(row.id ?? row.clrAppyEvtId ?? `${row.begndate}-${row.clrType}`)
                          "
                          class="recon-local-record-item"
                        >
                          <div class="flex items-start justify-between gap-2">
                            <div class="min-w-0 flex-1">
                              <div
                                class="recon-local-record-title"
                                style="color: var(--el-text-color-primary)"
                              >
                                {{ row.begndate || '—' }}
                                <span v-if="row.enddate && row.enddate !== row.begndate">
                                  至 {{ row.enddate }}
                                </span>
                              </div>
                              <div
                                class="recon-local-record-sub mt-0.5"
                                style="color: var(--el-text-color-regular)"
                              >
                                {{ formatClrType(row.clrType) }}
                              </div>
                            </div>
                            <ElTag
                              :type="clearStasTagType(row.clrStas)"
                              size="default"
                              effect="light"
                            >
                              {{ formatClrStas(row.clrStas) }}
                            </ElTag>
                          </div>
                          <div
                            class="recon-local-record-sub mt-1"
                            style="color: var(--el-text-color-secondary)"
                          >
                            医疗费 {{ formatMoney(row.medfeeSumamt) }}
                          </div>
                          <div v-if="row.clrStas === '10'" class="mt-1.5 flex justify-end">
                            <ElButton link type="danger" @click="handleRevokeClear(row)">
                              撤销
                            </ElButton>
                          </div>
                        </div>
                      </div>
                    </div>
                  </ElCol>
                </ElRow>
              </ElTabPane>
            </ElTabs>
          </div>
        </ElTabPane>

        <!-- ========== 工伤（三步：结算统计 → 总额对账 → 对账确认） ========== -->
        <ElTabPane v-if="wiModuleEnabled" label="工伤对账" name="workInjury" lazy>
          <ElCard shadow="never" class="wi-recon-card recon-a11y">
            <template #header>
              <div class="flex flex-col gap-2">
                <span class="wi-recon-title">工伤保险对账</span>
                <ElSteps :active="wiStep - 1" finish-status="success" align-center>
                  <ElStep title="结算统计" description="汇总本地 HIS 金额" />
                  <ElStep title="总额对账" description="1320 取平台金额比对" />
                  <ElStep title="对账确认" description="1322 确认已结束月" />
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
                    <ElInput v-model="wiSocialPoolCode" placeholder="220100" />
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
                  type="primary"
                  :loading="wi1320Loading"
                  :disabled="wiStep < 2"
                  @click="runWorkInjury1320"
                >
                  2. 总额对账
                </ElButton>
                <ElButton
                  size="large"
                  type="success"
                  :loading="wi1322Loading"
                  :disabled="wiStep < 3 || !wiAmountsBalanced"
                  @click="runWorkInjury1322"
                >
                  3. 对账确认
                </ElButton>
              </div>
            </ElForm>

            <ElTable
              :data="[
                {
                  dim: '医疗费总额',
                  his: wiHisAmounts.medfeeSumamt,
                  platform: wiPlatformAmounts.medfeeSumamt,
                  diff: wiMedfeeDiff,
                },
                {
                  dim: '统筹支付',
                  his: wiHisAmounts.hifpPay,
                  platform: wiPlatformAmounts.hifpPay,
                  diff: wiFundDiff,
                },
                {
                  dim: '现金支付',
                  his: wiHisAmounts.psnCashPay,
                  platform: wiPlatformAmounts.psnCashPay,
                  diff: wiCashDiff,
                },
              ]"
              border
              stripe
              class="wi-recon-table mt-1"
            >
              <ElTableColumn prop="dim" label="项目" min-width="160" />
              <ElTableColumn label="HIS" align="right" min-width="180">
                <template #default="{ row }">{{ formatMoney(row.his) }}</template>
              </ElTableColumn>
              <ElTableColumn label="工伤保险(平台)" align="right" min-width="200">
                <template #default="{ row }">
                  {{ wiStep < 2 ? '—' : formatMoney(row.platform) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="差额(HIS-平台)" align="right" min-width="180">
                <template #default="{ row }">
                  <span
                    :style="{
                      color:
                        row.diff == null
                          ? undefined
                          : row.diff === 0
                            ? 'var(--el-color-success)'
                            : 'var(--el-color-danger)',
                    }"
                  >
                    {{ wiStep < 2 ? '—' : formatMoney(row.diff) }}
                  </span>
                </template>
              </ElTableColumn>
            </ElTable>

            <div class="wi-recon-footer mt-3">
              本地笔数：{{ wiHisAmounts.setlCount }}
              <template v-if="wiStep === 1"> · 请先「结算统计」</template>
              <template v-else-if="wiStep === 2 && !wiPlatformReady">
                · 请「总额对账」取平台金额
              </template>
              <template v-else-if="wiStep === 2 && !wiAmountsBalanced">
                · 金额不平，禁止确认
              </template>
              <template v-else-if="wiStep >= 3"> · 金额已平，选已结束月份后「对账确认」 </template>
            </div>

            <ElAlert
              v-if="wiLastSummary"
              class="wi-recon-alert mt-4"
              :type="wiAmountsBalanced || wiStep === 1 ? 'success' : 'warning'"
              :closable="false"
              show-icon
              :title="wiLastSummary"
            />
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
