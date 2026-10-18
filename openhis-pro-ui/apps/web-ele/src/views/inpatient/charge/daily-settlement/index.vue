<script setup lang="ts">
import type {
  InpatientCashierSettlementHistoryItemDto,
  InpatientDailySettlementChannelRowDto,
  InpatientDailySettlementPatientSummaryDto,
  InpatientDailySettlementSummaryDto,
  LoadInpatientDailySettlementPageInitResponse,
  PreviewInpatientDailySettlementResponse,
} from '#/api/inpatient/daily-settlement';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';
import { formatDateTime } from '@zxplat/utils';

import dayjs from 'dayjs';
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDatePicker,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTooltip,
} from 'element-plus';

import {
  exportInpatientDailySettlementPatientSummariesApi,
  loadInpatientCashierSettlementVoucherApi,
  loadInpatientDailySettlementPageInitApi,
  normalizePreviewInpatientDailySettlementResponse,
  pageInpatientCashierSettlementHistoryApi,
  previewInpatientDailySettlementApi,
  submitInpatientDailySettlementApi,
  withdrawInpatientDailySettlementApi,
} from '#/api/inpatient/daily-settlement';
import DailySettlementChsReconPanel from '#/components/charge/daily-settlement-chs-recon-panel.vue';
import DailySettlementHandoverDialog from '#/components/charge/daily-settlement-handover-dialog.vue';
import DailySettlementSummaryPanel from '#/components/charge/daily-settlement-summary-panel.vue';
import { usePrintClientWarmup } from '#/composables/use-print-client-warmup';
import { useRequest } from '#/hooks/use-request';
import { useAuthStore } from '#/store/auth';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { buildDefaultDateTimeRangeShortcuts } from '#/utils/format/date-picker-shortcuts';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { printDailySettlement } from '#/utils/print/print-daily-settlement';

defineOptions({
  name: 'ChargeIpDailySettlement',
});

usePrintClientWarmup();

/** 费用性质「全部」（仅展示用，请求时不传） */
const FILTER_ALL_VALUE = '__ALL__';

interface SummaryItem {
  label: string;
  value: string;
}

interface QueryFormState {
  dateRange: [string, string];
  expenseProperty: string;
  /** 多选收款人软筛；空数组表示不按人过滤 */
  cashierIds: string[];
}

const authStore = useAuthStore();

/** 待审交账状态码（撤回仅允许此态） */
const SETTLEMENT_STATUS_SUBMITTED = 'SUBMITTED';

const todayStart = dayjs().startOf('day').format('YYYY-MM-DD HH:mm:ss');
const nowEnd = () => dayjs().format('YYYY-MM-DD HH:mm:ss');
/** 日结时间查询：展开面板默认快捷时间范围（含时分秒） */
const dateTimeRangeShortcuts = buildDefaultDateTimeRangeShortcuts();
const queryForm = reactive<QueryFormState>({
  dateRange: [todayStart, nowEnd()],
  expenseProperty: FILTER_ALL_VALUE,
  cashierIds: [],
});

/** 侧栏医保对账日：取日结窗起点自然日 */
const chsReconDate = computed(() => {
  const start = queryForm.dateRange?.[0];
  if (!start) {
    return undefined;
  }
  return dayjs(start).format('YYYY-MM-DD');
});

const pageInit = ref<LoadInpatientDailySettlementPageInitResponse | null>(null);
const previewResult = ref<PreviewInpatientDailySettlementResponse | null>(null);
const hasQueried = ref(false);
const printSubmitting = ref(false);
/** 结果区 Tab：日结合计 / 患者分项（只读统计，不影响交账） */
const resultTab = ref<'summary' | 'patient'>('summary');
const handoverDialogVisible = ref(false);
const historyDialogVisible = ref(false);
const historyItems = ref<InpatientCashierSettlementHistoryItemDto[]>([]);
const historyLoading = ref(false);
const historyReprintLoadingId = ref('');
const historyWithdrawLoadingId = ref('');

const { run: runInit, loading: initLoading } = useRequest(loadInpatientDailySettlementPageInitApi, {
  manual: true,
  showErrorMessage: true,
});
const { run: runPreview, loading: queryLoading } = useRequest(previewInpatientDailySettlementApi, {
  manual: true,
});
const { run: runSubmit, loading: submitLoading } = useRequest(submitInpatientDailySettlementApi, {
  manual: true,
});
const { run: runWithdraw, loading: withdrawLoading } = useRequest(
  withdrawInpatientDailySettlementApi,
  {
    manual: true,
    onSuccess: () => {
      ElMessage.success('撤回成功');
    },
  },
);

const expensePropertyOptions = computed<ApiOptionItem[]>(() => {
  const options = pageInit.value?.expensePropertyOptions ?? [];
  // 与门诊日结同构：前端加「全部」
  return [{ label: '全部', value: FILTER_ALL_VALUE }, ...options];
});

/** 款员下拉：页初始化为 ROLE_CASHIER 岗池，查询后再并入本窗有流水的人 */
const cashierOptions = ref<ApiOptionItem[]>([]);

/** 当前下拉选项中的全部收款人 id（保序） */
function listCashierOptionIds(): string[] {
  return cashierOptions.value
    .map((item) => preserveApiIdForRequest(item.value))
    .filter((value): value is string => Boolean(value));
}

/** 是否勾选了当前选项列表中的全部人（仅展示用，请求仍传明确 id） */
const isAllSelectedCashiers = computed(() => {
  const selected = queryForm.cashierIds;
  const allValues = listCashierOptionIds();
  return allValues.length > 0 && allValues.every((value) => selected.includes(value));
});

const selectedCashierLabel = computed(() => {
  const selected = queryForm.cashierIds;
  if (!selected.length) {
    return '—';
  }
  if (isAllSelectedCashiers.value) {
    return (
      selected
        .map((id) => cashierOptions.value.find((item) => item.value === id)?.label?.trim())
        .filter((label): label is string => Boolean(label))
        .join('、') || '—'
    );
  }
  const labels = selected
    .map((id) => cashierOptions.value.find((item) => item.value === id)?.label?.trim())
    .filter((label): label is string => Boolean(label));
  return labels.length ? labels.join('、') : '—';
});

const hasGapHardBlock = computed(() =>
  Boolean(previewResult.value?.gapHardBlockEarliestAt?.trim()),
);

const gapHardBlockMessage = computed(
  () =>
    previewResult.value?.gapHardBlockMessage?.trim() ||
    (hasGapHardBlock.value ? '存在漏交硬栏，请调整日结时间起点后再提交' : ''),
);

const gapSoftWarnCount = computed(() => previewResult.value?.gapSoftWarnCount ?? 0);

const canSubmitSettlement = computed(() => {
  const count = previewResult.value?.paymentCount ?? 0;
  // 漏交硬栏仅提示 + 提交 Gate；不禁用「确认交账」入口与弹窗勾选
  return hasQueried.value && count > 0 && !queryLoading.value && !submitLoading.value;
});

// 将渠道行与汇总字段拆成多列网格，便于日结单式展示。
function chunkItems<T>(items: T[], size: number): T[][] {
  const rows: T[][] = [];
  for (let i = 0; i < items.length; i += size) {
    rows.push(items.slice(i, i + size));
  }
  return rows;
}

// 格式化上半区 fin_type 行。
function formatChannelRow(row: InpatientDailySettlementChannelRowDto): SummaryItem {
  const amount = row.amount ?? '0.00';
  const unit = row.unit ?? '元';
  return {
    label: row.category?.trim() || '—',
    value: `${amount} ${unit}`,
  };
}

const resolvedSummary = computed<InpatientDailySettlementSummaryDto | undefined>(
  () => previewResult.value?.summary,
);

const channelSummaryColumns = computed<SummaryItem[][]>(() => {
  const rows = previewResult.value?.rows ?? [];
  const items = rows.map((row) => formatChannelRow(row));
  return chunkItems(items, 4);
});

const hasFinTypeDisplay = computed(
  () => hasQueried.value && channelSummaryColumns.value.length > 0,
);

const hasSummaryDisplay = computed(() => {
  const summary = resolvedSummary.value;
  if (!summary) {
    return false;
  }
  return Boolean(
    summary.totalIncome !== undefined ||
      summary.expenseNatureSummaries?.length ||
      summary.summaryRows?.length,
  );
});

/** 患者分项块（与合计同源；只读） */
const patientSummaries = computed<InpatientDailySettlementPatientSummaryDto[]>(
  () => previewResult.value?.patientSummaries ?? [],
);

const hasPatientSummaryDisplay = computed(
  () => hasQueried.value && patientSummaries.value.length > 0,
);

/** 当前 Tab 可打印：合计看 summary，患者分项看 patientSummaries */
const canPrintSettlement = computed(() => {
  if (queryLoading.value || printSubmitting.value) {
    return false;
  }
  if (resultTab.value === 'patient') {
    return hasPatientSummaryDisplay.value;
  }
  return hasSummaryDisplay.value;
});

/** 组装患者分项块标题：姓名 + 住院号 + 病房-病床（不带患者序号） */
function formatPatientBlockTitle(block: InpatientDailySettlementPatientSummaryDto): string {
  const name = block.patientName?.trim() || '未知患者';
  const busNo = block.encounterBusNo?.trim() || '—';
  const ward = block.wardName?.trim() || '';
  const bed = block.bedName?.trim() || '';
  // 病房-病床：有病床则拼接；仅病房则只展示病房
  let location = '';
  if (ward && bed) {
    location = `${ward}-${bed}`;
  } else if (ward) {
    location = ward;
  } else if (bed) {
    location = bed;
  }
  if (location) {
    return `${name}　住院号：${busNo}　${location}`;
  }
  return `${name}　住院号：${busNo}`;
}

const headerDateRangeText = computed(() => {
  const summaryRange = resolvedSummary.value?.dateRange;
  if (summaryRange) {
    return String(summaryRange);
  }
  const [from, to] = queryForm.dateRange;
  return from && to ? (from === to ? from : `${from} ~ ${to}`) : '—';
});

const paymentCountText = computed(() => {
  if (!hasQueried.value) {
    return '—';
  }
  return String(previewResult.value?.paymentCount ?? 0);
});

/** 总收入是否为 0（优先 handoverSummary，含 0 元收费、一收一退抵净场景）。 */
const isZeroNetIncome = computed(() => {
  const incomeText =
    previewResult.value?.handoverSummary?.totalIncome ?? resolvedSummary.value?.totalIncome;
  if (!incomeText) {
    return false;
  }
  const income = Number.parseFloat(incomeText);
  return !Number.isNaN(income) && income === 0;
});

/** 将后端默认日结时间规范为 YYYY-MM-DD HH:mm:ss（结束默认当前时刻）。 */
function normalizeDateTimeBound(raw: null | string | undefined, bound: 'start' | 'end') {
  const text = String(raw ?? '').trim();
  if (!text) {
    return bound === 'start' ? todayStart : nowEnd();
  }
  const parsed = dayjs(text);
  if (!parsed.isValid()) {
    return bound === 'start' ? todayStart : nowEnd();
  }
  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) {
    if (bound === 'start') {
      return parsed.startOf('day').format('YYYY-MM-DD HH:mm:ss');
    }
    return parsed.isSame(dayjs(), 'day')
      ? nowEnd()
      : parsed.endOf('day').format('YYYY-MM-DD HH:mm:ss');
  }
  return parsed.format('YYYY-MM-DD HH:mm:ss');
}

/** 解析当前交账科室 orgId。 */
function resolveOrgIdForRequest(): string | undefined {
  return preserveApiIdForRequest(authStore.getSelectedOrganization());
}

// 组装收款人筛选入参。
function buildCashierFilterPayload() {
  const cashierIds = queryForm.cashierIds
    .map((id) => preserveApiIdForRequest(id))
    .filter((id): id is string => Boolean(id));
  return {
    cashierIds: cashierIds.length ? cashierIds : undefined,
  };
}

// 组装预览请求体（与门诊日结同构：费用性质「全部」不传）。
function buildPreviewRequest() {
  const [dateFrom, dateTo] = queryForm.dateRange;
  const orgId = resolveOrgIdForRequest();
  return {
    dateFrom,
    dateTo,
    orgId: orgId ?? '',
    ...buildCashierFilterPayload(),
    expenseProperty:
      queryForm.expenseProperty === FILTER_ALL_VALUE
        ? undefined
        : queryForm.expenseProperty?.trim() || undefined,
  };
}

// 加载页初始化数据并回填默认筛选项。
async function loadPageInit() {
  const data = await runInit();
  pageInit.value = data;
  queryForm.dateRange = [normalizeDateTimeBound(data.defaultDateFrom, 'start'), nowEnd()];
  // 与门诊日结同构：费用性质默认「全部」
  queryForm.expenseProperty = FILTER_ALL_VALUE;
  // 款员岗池先入下拉；查询后再并入本窗有流水的人
  cashierOptions.value = normalizeCashierOptions(data.cashierOptions);
  queryForm.cashierIds = [];
}

/** 收款人变更触发查询时，跳过程序化回填引起的二次查询 */
const skipCashierChangeSearch = ref(false);

/**
 * 款员岗池与本窗有流水的人合并入下拉；空选且本窗有人时自动带出本窗的人。
 */
function syncCashierOptionsFromPreview(data: PreviewInpatientDailySettlementResponse | null) {
  const merged = new Map<string, ApiOptionItem>();
  for (const item of normalizeCashierOptions(pageInit.value?.cashierOptions)) {
    merged.set(item.value, item);
  }
  const fromWindow = data?.practitionerOptions ?? [];
  const windowIds: string[] = [];
  for (const item of normalizeCashierOptions(fromWindow)) {
    if (!merged.has(item.value)) {
      merged.set(item.value, item);
    }
    windowIds.push(item.value);
  }
  cashierOptions.value = [...merged.values()];
  const allowed = new Set(cashierOptions.value.map((item) => item.value));
  // 已选中若不在下拉则剔除；空选且本窗有人时自动带出本窗的人，不把整个款员岗池选上
  if (queryForm.cashierIds.length) {
    const retained = queryForm.cashierIds.filter((id) => allowed.has(id));
    if (retained.length !== queryForm.cashierIds.length) {
      skipCashierChangeSearch.value = true;
      queryForm.cashierIds = retained;
    }
  } else if (windowIds.length) {
    skipCashierChangeSearch.value = true;
    queryForm.cashierIds = windowIds;
  }
}

/** 下拉值统一成字符串主键，避免 Long 精度丢失 */
function normalizeCashierOptions(source: ApiOptionItem[] | undefined): ApiOptionItem[] {
  const options: ApiOptionItem[] = [];
  for (const item of source ?? []) {
    const key = preserveApiIdForRequest(item.value);
    if (!key) {
      continue;
    }
    options.push({ ...item, value: key });
  }
  return options;
}

/** 改日期：清空已选收款人后再查，便于空选路径自动带出本窗收费人 */
function handleDateRangeChange() {
  queryForm.cashierIds = [];
  void handleSearch();
}

/** 收款人下拉变更：空选亦可查；程序化带出时不重查 */
function handleCashierIdsChange() {
  if (skipCashierChangeSearch.value) {
    skipCashierChangeSearch.value = false;
    return;
  }
  void handleSearch();
}

// 按当前筛选条件查询未交账汇总。
async function handleSearch() {
  const [dateFrom, dateTo] = queryForm.dateRange;
  if (!dateFrom || !dateTo) {
    ElMessage.warning('请选择日结时间');
    return;
  }
  if (!resolveOrgIdForRequest()) {
    ElMessage.warning('请先选择交账科室');
    return;
  }
  try {
    const request = buildPreviewRequest();
    const raw = await runPreview(request);
    const normalized = normalizePreviewInpatientDailySettlementResponse(
      raw as PreviewInpatientDailySettlementResponse & InpatientDailySettlementSummaryDto,
    );
    previewResult.value = normalized;
    // 按时间窗带出本窗收费人，供后续可选软筛
    syncCashierOptionsFromPreview(normalized);
    hasQueried.value = true;
  } catch {
    previewResult.value = null;
    hasQueried.value = true;
  }
}

// 打开交账明细弹窗（默认全选待交账流水）。
function handleSubmitSettlement() {
  const [dateFrom, dateTo] = queryForm.dateRange;
  if (!dateFrom || !dateTo) {
    ElMessage.warning('请选择日结时间');
    return;
  }
  if (!resolveOrgIdForRequest()) {
    ElMessage.warning('请先选择交账科室');
    return;
  }
  if (hasGapHardBlock.value) {
    // 仍打开弹窗供勾选核对；最终提交由后端漏交 Gate 拒绝并带回文案
    ElMessage.warning(gapHardBlockMessage.value || '存在漏交硬栏，请调整日结时间起点后再提交');
  }
  if ((previewResult.value?.paymentCount ?? 0) <= 0) {
    ElMessage.warning('当前没有可交账流水');
    return;
  }
  if (!previewResult.value?.items?.length) {
    ElMessage.warning('待交账明细为空，请重新查询');
    return;
  }
  handoverDialogVisible.value = true;
}

// 弹窗确认后提交勾选 paymentIds。
async function handleHandoverConfirm(paymentIds: string[]) {
  const [dateFrom, dateTo] = queryForm.dateRange;
  const orgId = resolveOrgIdForRequest();
  if (!dateFrom || !dateTo || !orgId || paymentIds.length === 0) {
    return;
  }
  try {
    const result = await runSubmit({
      dateFrom,
      dateTo,
      ...buildCashierFilterPayload(),
      orgId,
      paymentIds,
    });
    handoverDialogVisible.value = false;
    ElMessage.success(`交账成功，单号 ${result.busNo ?? '—'}`);
    await handleSearch();
  } catch {
    // 错误提示由 useRequest 拦截器处理
  }
}

/** 解析交账历史查询时刻窗（按 submitted_at；非法则回落当天）。 */
function resolveHistorySubmittedRange(): { submittedFrom: string; submittedTo: string } {
  const fallbackFrom = dayjs().startOf('day').format('YYYY-MM-DD HH:mm:ss');
  const fallbackTo = dayjs().format('YYYY-MM-DD HH:mm:ss');
  const range = queryForm.dateRange;
  if (!Array.isArray(range) || !range[0] || !range[1]) {
    return { submittedFrom: fallbackFrom, submittedTo: fallbackTo };
  }
  const from = dayjs(range[0]);
  const to = dayjs(range[1]);
  if (!from.isValid() || !to.isValid() || to.isBefore(from)) {
    return { submittedFrom: fallbackFrom, submittedTo: fallbackTo };
  }
  let submittedTo = to.format('YYYY-MM-DD HH:mm:ss');
  // 历史按交账提交时刻；业务窗止时若早于当前，会把今日已交单筛掉
  if (to.isBefore(dayjs())) {
    submittedTo = fallbackTo;
  }
  return {
    submittedFrom: from.format('YYYY-MM-DD HH:mm:ss'),
    submittedTo,
  };
}

async function openHistoryDialog() {
  historyDialogVisible.value = true;
  historyLoading.value = true;
  try {
    const { submittedFrom, submittedTo } = resolveHistorySubmittedRange();
    const data = await pageInpatientCashierSettlementHistoryApi({
      submittedFrom,
      submittedTo,
      pageIndex: 0,
      pageSize: 50,
    });
    historyItems.value = data?.items ?? [];
  } catch {
    historyItems.value = [];
  } finally {
    historyLoading.value = false;
  }
}

/** 格式化历史时间展示。 */
function formatHistoryTime(time?: string): string {
  if (!time?.trim()) {
    return '—';
  }
  return formatDateTime(time);
}

/** 历史行药房/科室展示：药房优先。 */
function formatHistoryScope(row: InpatientCashierSettlementHistoryItemDto): string {
  const locationName = row.locationName?.trim();
  if (locationName) {
    return locationName;
  }
  const orgName = row.orgName?.trim();
  if (orgName) {
    return orgName;
  }
  return '—';
}

/** 历史金额两位小数展示。 */
function formatHistoryAmount(value?: string | number): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return num.toFixed(2);
}

/** 历史行是否可尝试撤回。 */
function canWithdrawHistoryRow(row: InpatientCashierSettlementHistoryItemDto): boolean {
  const status = row.statusEnum?.trim();
  return !status || status === SETTLEMENT_STATUS_SUBMITTED;
}

/** 撤回待审交账历史行。 */
async function handleWithdrawHistory(row: InpatientCashierSettlementHistoryItemDto) {
  const settlementId = preserveApiIdForRequest(row.settlementId);
  if (!settlementId) {
    return;
  }
  try {
    await ElMessageBox.confirm(`确认撤回交账单 ${row.busNo ?? settlementId}？`, '撤回交账', {
      type: 'warning',
      confirmButtonText: '撤回',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  historyWithdrawLoadingId.value = settlementId;
  try {
    await runWithdraw({ settlementId });
    await openHistoryDialog();
    await handleSearch();
  } finally {
    historyWithdrawLoadingId.value = '';
  }
}

/** 重打历史交账原单（合计）；后端按挂载 payment 流水现算凭证。 */
async function handleReprintHistory(row: InpatientCashierSettlementHistoryItemDto) {
  await reprintHistoryVoucher(row, 'summary');
}

/** 重打历史交账患者分项；与合计同源挂载现算。 */
async function handleReprintHistoryPatient(row: InpatientCashierSettlementHistoryItemDto) {
  await reprintHistoryVoucher(row, 'patient');
}

/**
 * 加载挂载凭证并按模式打印合计或患者分项。
 *
 * @param row 历史行
 * @param mode summary=日结合计单；patient=患者分项单
 */
async function reprintHistoryVoucher(
  row: InpatientCashierSettlementHistoryItemDto,
  mode: 'patient' | 'summary',
) {
  const settlementId = preserveApiIdForRequest(row.settlementId);
  if (!settlementId) {
    return;
  }
  historyReprintLoadingId.value = settlementId;
  try {
    const voucher = await loadInpatientCashierSettlementVoucherApi(settlementId);
    const mountedVoucher = voucher.mountedVoucher;
    if (mode === 'patient') {
      const patients =
        (mountedVoucher?.patientSummaries as
          | InpatientDailySettlementPatientSummaryDto[]
          | undefined) ?? [];
      if (!patients.length) {
        ElMessage.info('该交账单暂无患者分项可重打');
        return;
      }
      await printDailySettlement({
        jobName: `住院交账患者分项-${voucher.busNo ?? settlementId}`,
        bizRef: {
          settlementId,
          busNo: String(voucher.busNo ?? '').trim() || undefined,
          printMode: 'patient',
        },
        source: {
          hospitalName: '',
          documentTitle: '住院收费日结单（患者分项）',
          cashierName: mountedVoucher?.cashierName ?? '',
          settlementPeriodText: mountedVoucher?.settlementPeriodText ?? '',
          summary: mountedVoucher?.summary as InpatientDailySettlementSummaryDto | undefined,
          patientSummaries: patients,
          printMode: 'patient',
          discountFeatureEnabled: Boolean(pageInit.value?.discountFeatureEnabled),
        },
      });
      return;
    }
    if (!mountedVoucher?.summary) {
      ElMessage.info('凭证汇总不可用，明细需按当时挂载 payment 流水现算');
      return;
    }
    await printDailySettlement({
      jobName: `住院交账原单-${voucher.busNo ?? settlementId}`,
      bizRef: {
        settlementId,
        busNo: String(voucher.busNo ?? '').trim() || undefined,
      },
      source: {
        hospitalName: '',
        documentTitle: '住院收费日结单',
        cashierName: mountedVoucher.cashierName ?? '',
        settlementPeriodText: mountedVoucher.settlementPeriodText ?? '',
        rows: mountedVoucher.rows as InpatientDailySettlementChannelRowDto[] | undefined,
        summary: mountedVoucher.summary as InpatientDailySettlementSummaryDto,
        printMode: 'summary',
        discountFeatureEnabled: Boolean(pageInit.value?.discountFeatureEnabled),
      },
    });
  } catch {
    ElMessage.info('加载凭证失败，明细需按当时挂载 payment 流水现算');
  } finally {
    historyReprintLoadingId.value = '';
  }
}

/**
 * 导出患者分项 Excel（后端 POI 真 xlsx；与门诊费用明细同栈；不影响交账）。
 */
async function handleExportPatientSummaries() {
  if (!hasQueried.value) {
    ElMessage.warning('请先查询日结汇总后再导出');
    return;
  }
  if (!patientSummaries.value.length) {
    ElMessage.warning('暂无患者分项可导出');
    return;
  }
  const body = buildPreviewRequest();
  if (!body.orgId) {
    ElMessage.warning('请先选择交账科室');
    return;
  }
  try {
    const data = await exportInpatientDailySettlementPatientSummariesApi(body);
    downloadBinaryExportFile(data, '住院日结患者分项.xlsx');
    ElMessage.success(`导出成功，共 ${data.rowCount ?? 0} 行`);
  } catch {
    ElMessage.error('导出失败，请稍后重试');
  }
}

/** 打印当前 Tab：合计单或患者分项单（互不影响交账）。 */
async function handlePrintSettlement() {
  if (resultTab.value === 'patient') {
    await handlePrintPatientSettlement();
    return;
  }
  if (!previewResult.value?.summary) {
    ElMessage.warning('请先查询日结汇总后再打印');
    return;
  }
  printSubmitting.value = true;
  try {
    const [dateFrom, dateTo] = queryForm.dateRange;
    await printDailySettlement({
      jobName: '住院收费日结单',
      bizRef: {
        orgId: resolveOrgIdForRequest() ?? '',
        dateFrom: dateFrom ?? '',
        dateTo: dateTo ?? '',
        cashierIds: queryForm.cashierIds
          .map((id) => preserveApiIdForRequest(id))
          .filter((id): id is string => Boolean(id)),
      },
      source: {
        hospitalName: '',
        documentTitle: '住院收费日结单',
        cashierName: selectedCashierLabel.value === '—' ? '' : selectedCashierLabel.value,
        settlementPeriodText: headerDateRangeText.value === '—' ? '' : headerDateRangeText.value,
        rows: previewResult.value.rows,
        summary: previewResult.value.summary,
        printMode: 'summary',
        discountFeatureEnabled: Boolean(pageInit.value?.discountFeatureEnabled),
      },
    });
  } finally {
    printSubmitting.value = false;
  }
}

/** 打印患者分项日结单（与合计同源、单独一单）。 */
async function handlePrintPatientSettlement() {
  if (!patientSummaries.value.length) {
    ElMessage.warning('暂无患者分项可打印');
    return;
  }
  printSubmitting.value = true;
  try {
    const [dateFrom, dateTo] = queryForm.dateRange;
    await printDailySettlement({
      jobName: '住院收费日结单（患者分项）',
      bizRef: {
        orgId: resolveOrgIdForRequest() ?? '',
        dateFrom: dateFrom ?? '',
        dateTo: dateTo ?? '',
        cashierIds: queryForm.cashierIds
          .map((id) => preserveApiIdForRequest(id))
          .filter((id): id is string => Boolean(id)),
        printMode: 'patient',
      },
      source: {
        hospitalName: '',
        documentTitle: '住院收费日结单（患者分项）',
        cashierName: selectedCashierLabel.value === '—' ? '' : selectedCashierLabel.value,
        settlementPeriodText: headerDateRangeText.value === '—' ? '' : headerDateRangeText.value,
        summary: previewResult.value?.summary,
        patientSummaries: patientSummaries.value,
        printMode: 'patient',
        discountFeatureEnabled: Boolean(pageInit.value?.discountFeatureEnabled),
      },
    });
  } finally {
    printSubmitting.value = false;
  }
}

onMounted(async () => {
  try {
    await loadPageInit();
    // 有交账科室则自动查一次：空选=不按人，随后按窗内有流水人填充下拉
    if (resolveOrgIdForRequest()) {
      await handleSearch();
    }
  } catch {
    // init 失败时不自动查询
  }
});
</script>

<template>
  <Page
    auto-content-height
    class="daily-settlement-page min-h-0 bg-[var(--el-fill-color-light)] p-2"
    content-class="!flex !h-full !min-h-0 !min-w-0 !flex-col !overflow-hidden !p-0"
  >
    <div class="daily-settlement-layout grid h-full min-h-0 flex-1 grid-cols-[2fr_1fr] gap-2">
      <div class="flex min-h-0 min-w-0 flex-col gap-2 overflow-hidden">
        <ElCard shadow="never" class="shrink-0">
          <ElForm
            :inline="true"
            :model="queryForm"
            class="daily-settlement-query-form flex flex-nowrap items-center overflow-x-auto"
          >
            <ElFormItem label="日结时间">
              <ElDatePicker
                v-model="queryForm.dateRange"
                type="datetimerange"
                value-format="YYYY-MM-DD HH:mm:ss"
                format="YYYY-MM-DD HH:mm:ss"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                :clearable="false"
                class="!w-[400px]"
                :shortcuts="dateTimeRangeShortcuts"
                @change="handleDateRangeChange"
              />
            </ElFormItem>
            <ElFormItem label="费用性质">
              <ElSelect
                v-model="queryForm.expenseProperty"
                placeholder="请选择费用性质"
                class="!w-[140px]"
                filterable
                @change="handleSearch"
              >
                <ElOption
                  v-for="item in expensePropertyOptions"
                  :key="`expense-${item.value}`"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="款员">
              <ElSelect
                v-model="queryForm.cashierIds"
                multiple
                clearable
                placeholder="不选=全部"
                class="!w-auto !min-w-[220px]"
                filterable
                @change="handleCashierIdsChange"
              >
                <ElOption
                  v-for="item in cashierOptions"
                  :key="`cashier-${item.value}`"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <div class="ml-auto flex shrink-0 items-center gap-2">
              <ElButton type="primary" :loading="queryLoading || initLoading" @click="handleSearch">
                查询
              </ElButton>
              <ElButton plain :loading="historyLoading" @click="openHistoryDialog">
                交账历史
              </ElButton>
              <ElButton
                plain
                :loading="printSubmitting"
                :disabled="!canPrintSettlement"
                @click="handlePrintSettlement"
              >
                {{ resultTab === 'patient' ? '打印患者分项' : '打印日结单' }}
              </ElButton>
              <ElButton plain :disabled="!hasPatientSummaryDisplay" @click="handleExportPatientSummaries">
                导出患者分项
              </ElButton>
              <ElButton
                type="primary"
                plain
                :loading="submitLoading"
                :disabled="!canSubmitSettlement"
                @click="handleSubmitSettlement"
              >
                确认交账
              </ElButton>
            </div>
          </ElForm>
        </ElCard>

        <ElCard shadow="never" class="daily-settlement-card min-h-0 flex-1 overflow-hidden">
          <div class="flex h-full min-h-0 flex-col gap-4 overflow-y-auto">
            <h3
              class="text-center text-xl font-semibold tracking-wide text-[var(--el-text-color-primary)]"
            >
              住院收费日结单
            </h3>

            <ElAlert
              v-if="hasQueried && hasGapHardBlock"
              type="error"
              :closable="false"
              show-icon
              class="mb-2"
              :title="gapHardBlockMessage"
            />
            <ElAlert
              v-if="hasQueried && gapSoftWarnCount > 0"
              type="warning"
              :closable="false"
              show-icon
              class="mb-2"
              :title="`截止后仍有 ${gapSoftWarnCount} 笔未交账（仅提醒，不拦截提交）`"
            />

            <div
              class="grid grid-cols-4 gap-4 border-b border-[var(--el-border-color)] pb-3 text-sm"
            >
              <div>
                <span class="text-[var(--el-text-color-regular)]">款员：</span>
                {{ selectedCashierLabel }}
              </div>
              <div>
                <ElTooltip
                  content="按 payment 流水条数计，含 0 元收费与一收一退抵净流水，须归档后才会从待交账列表消失"
                  placement="top"
                >
                  <span class="cursor-help text-[var(--el-text-color-regular)]"
                    >待交账流水笔数：</span
                  >
                </ElTooltip>
                {{ paymentCountText }}
              </div>
              <div class="col-span-2">
                <span class="text-[var(--el-text-color-regular)]">时间：</span>
                {{ headerDateRangeText }}
              </div>
            </div>

            <template v-if="hasQueried">
              <!-- 不设 flex-1：由上方 overflow-y-auto 容器统滚，避免 ElTabs 撑高后裁切翻不动 -->
              <ElTabs v-model="resultTab" class="daily-settlement-result-tabs mt-2 shrink-0">
                <ElTabPane label="日结合计" name="summary">
                  <template v-if="hasFinTypeDisplay">
                    <div class="flex flex-col gap-3 border-b border-[var(--el-border-color)] pb-4">
                      <div
                        v-for="(row, rowIndex) in channelSummaryColumns"
                        :key="`fin-type-${rowIndex}`"
                        class="grid grid-cols-4 gap-x-6 gap-y-2 text-sm"
                      >
                        <div
                          v-for="item in row"
                          :key="item.label"
                          class="flex justify-between gap-2 text-[var(--el-text-color-primary)]"
                        >
                          <span class="text-[var(--el-text-color-regular)]"
                            >{{ item.label }}：</span
                          >
                          <span class="font-medium">{{ item.value }}</span>
                        </div>
                      </div>
                    </div>
                  </template>

                  <template v-if="hasSummaryDisplay">
                    <DailySettlementSummaryPanel
                      :summary="resolvedSummary"
                      :discount-feature-enabled="Boolean(pageInit?.discountFeatureEnabled)"
                    />
                  </template>

                  <ElEmpty
                    v-if="!queryLoading && !hasFinTypeDisplay && !hasSummaryDisplay"
                    description="当前日结时间范围内暂无收费汇总"
                    class="py-8"
                  />
                </ElTabPane>

                <ElTabPane label="患者分项" name="patient">
                  <div v-if="hasPatientSummaryDisplay" class="flex flex-col gap-3 pb-4 pr-1">
                    <!-- 一患者一卡：标题条 + 汇总体，块间分隔清晰 -->
                    <div
                      v-for="block in patientSummaries"
                      :key="`patient-${block.seqNo}-${block.encounterId ?? block.patientId ?? ''}`"
                      class="overflow-hidden rounded-lg border border-solid border-[var(--el-border-color)] bg-[var(--el-bg-color)]"
                    >
                      <div
                        class="border-b border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-fill-color-light)] px-3 py-2 text-sm font-medium text-[var(--el-text-color-primary)]"
                      >
                        {{ formatPatientBlockTitle(block) }}
                      </div>
                      <div class="px-3 py-3">
                        <DailySettlementSummaryPanel
                          :summary="block.summary"
                          omit-retail-upload-diff
                          :discount-feature-enabled="Boolean(pageInit?.discountFeatureEnabled)"
                        />
                      </div>
                    </div>
                  </div>
                  <ElEmpty
                    v-else-if="!queryLoading"
                    description="当前日结时间范围内暂无患者分项"
                    class="py-8"
                  />
                </ElTabPane>
              </ElTabs>
            </template>

            <ElEmpty
              v-if="!hasQueried && !queryLoading"
              description="请选择日结时间后查询"
              class="py-8"
            />
          </div>
        </ElCard>
      </div>

      <DailySettlementChsReconPanel :recon-date="chsReconDate" clr-type="21" />
    </div>

    <DailySettlementHandoverDialog
      v-model:visible="handoverDialogVisible"
      :items="previewResult?.items ?? []"
      :handover-rows="previewResult?.handoverRows"
      :handover-summary="previewResult?.handoverSummary"
      :zero-net-income-hint="isZeroNetIncome"
      :loading="submitLoading"
      @confirm="handleHandoverConfirm"
    />

    <ElDialog
      v-model="historyDialogVisible"
      title="交账历史（重打原单）"
      width="92%"
      top="4vh"
      destroy-on-close
      class="daily-settlement-history-dialog"
    >
      <ElTable v-loading="historyLoading" :data="historyItems" border stripe height="560">
        <ElTableColumn
          prop="busNo"
          label="交账单号"
          min-width="170"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn label="科室" min-width="120" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatHistoryScope(row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="业务窗" min-width="300" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatHistoryTime(row.dateFrom) }} ~ {{ formatHistoryTime(row.dateTo) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="submitterName" label="交账人" width="100" align="center">
          <template #default="{ row }">
            {{ row.submitterName?.trim() || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="交账时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatHistoryTime(row.submittedAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="approverName" label="审批人" width="100" align="center">
          <template #default="{ row }">
            {{ row.approverName?.trim() || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="审批时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatHistoryTime(row.approvedAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="paymentCount"
          label="笔数"
          width="80"
          align="right"
          header-align="right"
        />
        <ElTableColumn label="金额" width="110" align="right" header-align="right">
          <template #default="{ row }">
            {{ formatHistoryAmount(row.totalIncome) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="statusEnumText" label="状态" width="100" align="center" />
        <ElTableColumn label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              :loading="historyReprintLoadingId === String(row.settlementId ?? '')"
              @click="handleReprintHistory(row)"
            >
              重打合计
            </ElButton>
            <ElButton
              link
              type="primary"
              :loading="historyReprintLoadingId === String(row.settlementId ?? '')"
              @click="handleReprintHistoryPatient(row)"
            >
              重打患者分项
            </ElButton>
            <ElButton
              v-if="canWithdrawHistoryRow(row)"
              link
              type="warning"
              :loading="
                historyWithdrawLoadingId === String(row.settlementId ?? '') || withdrawLoading
              "
              @click="handleWithdrawHistory(row)"
            >
              撤回
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElDialog>
  </Page>
</template>

<style scoped>
.daily-settlement-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 12px;
  overflow: hidden;
}

.daily-settlement-query-form :deep(.el-form-item) {
  margin-right: 12px;
  margin-bottom: 0;
}

.daily-settlement-history-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}
</style>
