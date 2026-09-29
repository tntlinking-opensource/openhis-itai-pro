<script setup lang="ts">
import type {
  DailySettlementChannelRowDto,
  DailySettlementSummaryDto,
  LoadOutpatientDailySettlementPageInitResponse,
  OutpatientCashierSettlementHistoryItemDto,
  PreviewOutpatientDailySettlementResponse,
} from '#/api/outpatient/daily-settlement';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onActivated, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

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
  ElTable,
  ElTableColumn,
  ElTooltip,
} from 'element-plus';

import {
  loadOutpatientCashierSettlementVoucherApi,
  loadOutpatientDailySettlementPageInitApi,
  normalizePreviewOutpatientDailySettlementResponse,
  pageOutpatientCashierSettlementHistoryApi,
  previewOutpatientDailySettlementApi,
  submitOutpatientDailySettlementApi,
  withdrawOutpatientDailySettlementApi,
} from '#/api/outpatient/daily-settlement';
import DailySettlementHandoverDialog from '#/components/charge/daily-settlement-handover-dialog.vue';
import DailySettlementSummaryPanel from '#/components/charge/daily-settlement-summary-panel.vue';
import { usePrintClientWarmup } from '#/composables/use-print-client-warmup';
import { useRequest } from '#/hooks/use-request';
import { useAuthStore } from '#/store/auth';
import { buildDefaultDateTimeRangeShortcuts } from '#/utils/format/date-picker-shortcuts';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { printDailySettlement } from '#/utils/print/print-daily-settlement';

defineOptions({
  name: 'ChargeOpDailySettlement',
});

usePrintClientWarmup();

/** 费用性质「全部」选项值（请求时转为空筛选） */
const FILTER_ALL_VALUE = '__ALL__';
/** 待审交账状态码（撤回仅允许此态） */
const SETTLEMENT_STATUS_SUBMITTED = 'SUBMITTED';

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

/** 当天 00:00:00（每次调用现算，避免跨日后仍用首次进入的起点） */
const resolveTodayStart = () => dayjs().startOf('day').format('YYYY-MM-DD HH:mm:ss');
/** 当前时刻（每次调用现算） */
const nowEnd = () => dayjs().format('YYYY-MM-DD HH:mm:ss');
/** 日结时间查询：展开面板默认快捷时间范围（含时分秒） */
const dateTimeRangeShortcuts = buildDefaultDateTimeRangeShortcuts();
const queryForm = reactive<QueryFormState>({
  dateRange: [resolveTodayStart(), nowEnd()],
  expenseProperty: FILTER_ALL_VALUE,
  cashierIds: [],
});
/** 首屏 onMounted 完成前跳过 onActivated，避免 keep-alive 下重复 init/查询 */
const pageReady = ref(false);
/**
 * 用户是否在日期控件上手动改过日结时间。
 * 为 true 时「查询」不再把结束时刻刷成当前，避免冲掉今天内人工选的时段。
 */
const dateRangeManuallyEdited = ref(false);

const pageInit = ref<LoadOutpatientDailySettlementPageInitResponse | null>(null);
const previewResult = ref<null | PreviewOutpatientDailySettlementResponse>(null);
const hasQueried = ref(false);
const printSubmitting = ref(false);
const handoverDialogVisible = ref(false);
const historyDialogVisible = ref(false);
const historyItems = ref<OutpatientCashierSettlementHistoryItemDto[]>([]);
const historyLoading = ref(false);
const historyReprintLoadingId = ref<string>('');
const historyWithdrawLoadingId = ref<string>('');

const { run: runInit, loading: initLoading } = useRequest(
  loadOutpatientDailySettlementPageInitApi,
  {
    manual: true,
    showErrorMessage: true,
  },
);
const { run: runPreview, loading: queryLoading } = useRequest(previewOutpatientDailySettlementApi, {
  manual: true,
});
const { run: runSubmit, loading: submitLoading } = useRequest(submitOutpatientDailySettlementApi, {
  manual: true,
});
const { run: runWithdraw, loading: withdrawLoading } = useRequest(
  withdrawOutpatientDailySettlementApi,
  {
    manual: true,
    onSuccess: () => {
      ElMessage.success('撤回成功');
    },
  },
);

const expensePropertyOptions = computed<ApiOptionItem[]>(() => {
  const options = pageInit.value?.expensePropertyOptions ?? [];
  return [{ label: '全部', value: FILTER_ALL_VALUE }, ...options];
});

/** 款员下拉：页初始化为 ROLE_CASHIER 岗池，查询后再并入本窗有流水的人 */
const cashierOptions = ref<ApiOptionItem[]>([]);

/** 当前下拉选项中的全部收款人 id（保序） */
function listCashierOptionIds(): string[] {
  return cashierOptions.value.map((item) => preserveApiIdForRequest(item.value)).filter(Boolean);
}

/** 是否勾选了当前选项列表中的全部人（仅展示用，请求仍传明确 id） */
const isAllSelectedCashiers = computed(() => {
  const selected = queryForm.cashierIds;
  const allValues = listCashierOptionIds();
  return allValues.length > 0 && allValues.every((value) => selected.includes(value));
});

const selectedCashierLabel = computed(() => {
  const selected = queryForm.cashierIds;
  if (selected.length === 0) {
    return '—';
  }
  if (isAllSelectedCashiers.value) {
    return (
      selected
        .map((id) => cashierOptions.value.find((item) => item.value === id)?.label?.trim())
        .filter(Boolean)
        .join('、') || '—'
    );
  }
  const labels = selected
    .map((id) => cashierOptions.value.find((item) => item.value === id)?.label?.trim())
    .filter(Boolean);
  return labels.length > 0 ? labels.join('、') : '—';
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
  // 漏交硬栏仅提示 + 提交 Gate；不禁用「确认交账」入口与弹窗勾选（恢复点开弹窗→勾选→确认）
  return hasQueried.value && count > 0 && !queryLoading.value && !submitLoading.value;
});

const canPrintSettlement = computed(
  () => hasSummaryDisplay.value && !queryLoading.value && !printSubmitting.value,
);

// 将渠道行与汇总字段拆成多列网格，便于日结单式展示（非表格）。
function chunkItems<T>(items: T[], size: number): T[][] {
  const rows: T[][] = [];
  for (let i = 0; i < items.length; i += size) {
    rows.push(items.slice(i, i + size));
  }
  return rows;
}

// 格式化渠道汇总行：后端返回 category + amount + unit。
function formatChannelRow(row: DailySettlementChannelRowDto): SummaryItem {
  const amount = row.amount ?? '0.00';
  const unit = row.unit ?? '元';
  return {
    label: row.category?.trim() || '—',
    value: `${amount} ${unit}`,
  };
}

const resolvedSummary = computed<DailySettlementSummaryDto | undefined>(
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
function normalizeDateTimeBound(raw: null | string | undefined, bound: 'end' | 'start') {
  const text = String(raw ?? '').trim();
  if (!text) {
    return bound === 'start' ? resolveTodayStart() : nowEnd();
  }
  const parsed = dayjs(text);
  if (!parsed.isValid()) {
    return bound === 'start' ? resolveTodayStart() : nowEnd();
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

/**
 * 默认进行中日窗：把结束时刻刷新为当前时间。
 * 人工改过日期（含今天内某时段、历史日）时不改，按所选区间查询。
 */
function refreshQueryEndToNow() {
  if (dateRangeManuallyEdited.value) {
    return;
  }
  const [from] = queryForm.dateRange ?? [];
  if (!from) {
    return;
  }
  queryForm.dateRange = [from, nowEnd()];
}

/** 解析当前交账科室 orgId。 */
function resolveOrgIdForRequest(): string | undefined {
  return preserveApiIdForRequest(authStore.getSelectedOrganization());
}

// 组装预览请求体：日结时间、收款人与费用性质展示过滤。
function buildCashierFilterPayload() {
  const cashierIds = queryForm.cashierIds.map((id) => preserveApiIdForRequest(id)).filter(Boolean);
  return {
    cashierIds: cashierIds.length > 0 ? cashierIds : undefined,
  };
}

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
  dateRangeManuallyEdited.value = false;
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
function syncCashierOptionsFromPreview(data: null | PreviewOutpatientDailySettlementResponse) {
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
  if (queryForm.cashierIds.length > 0) {
    const retained = queryForm.cashierIds.filter((id) => allowed.has(id));
    if (retained.length !== queryForm.cashierIds.length) {
      skipCashierChangeSearch.value = true;
      queryForm.cashierIds = retained;
    }
  } else if (windowIds.length > 0) {
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

/** 改日期：标记为人工区间，清空已选收款人后再查，便于空选路径自动带出本窗收费人 */
function handleDateRangeChange() {
  dateRangeManuallyEdited.value = true;
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
    const normalized = normalizePreviewOutpatientDailySettlementResponse(
      raw as DailySettlementSummaryDto & PreviewOutpatientDailySettlementResponse,
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

/** 「查询」按钮：未手改日期时刷结束时刻到当前再查；已手改则严格按所选区间查 */
function handleQueryClick() {
  refreshQueryEndToNow();
  void handleSearch();
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
    const data = await pageOutpatientCashierSettlementHistoryApi({
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
function formatHistoryScope(row: OutpatientCashierSettlementHistoryItemDto): string {
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
function formatHistoryAmount(value?: number | string): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return num.toFixed(2);
}

/** 历史行是否可尝试撤回（状态未知时也展示，由后端校验）。 */
function canWithdrawHistoryRow(row: OutpatientCashierSettlementHistoryItemDto): boolean {
  const status = row.statusEnum?.trim();
  return !status || status === SETTLEMENT_STATUS_SUBMITTED;
}

/** 撤回待审交账历史行。 */
async function handleWithdrawHistory(row: OutpatientCashierSettlementHistoryItemDto) {
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

/** 重打历史交账原单；后端按挂载 payment 流水现算凭证。 */
async function handleReprintHistory(row: OutpatientCashierSettlementHistoryItemDto) {
  const settlementId = preserveApiIdForRequest(row.settlementId);
  if (!settlementId) {
    return;
  }
  historyReprintLoadingId.value = settlementId;
  try {
    const voucher = await loadOutpatientCashierSettlementVoucherApi(settlementId);
    const mountedVoucher = voucher.mountedVoucher;
    if (!mountedVoucher?.summary) {
      ElMessage.info('凭证汇总不可用，明细需按当时挂载 payment 流水现算');
      return;
    }
    await printDailySettlement({
      jobName: `门诊交账原单-${voucher.busNo ?? settlementId}`,
      bizRef: {
        settlementId,
        busNo: String(voucher.busNo ?? '').trim() || undefined,
      },
      source: {
        hospitalName: '',
        documentTitle: '门诊收费日结单',
        cashierName: mountedVoucher.cashierName ?? '',
        settlementPeriodText: mountedVoucher.settlementPeriodText ?? '',
        rows: mountedVoucher.rows as DailySettlementChannelRowDto[] | undefined,
        summary: mountedVoucher.summary as DailySettlementSummaryDto,
      },
    });
  } catch {
    ElMessage.info('加载凭证失败，明细需按当时挂载 payment 流水现算');
  } finally {
    historyReprintLoadingId.value = '';
  }
}

async function handlePrintSettlement() {
  if (!previewResult.value?.summary) {
    ElMessage.warning('请先查询日结汇总后再打印');
    return;
  }
  printSubmitting.value = true;
  try {
    const [dateFrom, dateTo] = queryForm.dateRange;
    await printDailySettlement({
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
        documentTitle: '门诊收费日结单',
        cashierName: selectedCashierLabel.value === '—' ? '' : selectedCashierLabel.value,
        settlementPeriodText: headerDateRangeText.value === '—' ? '' : headerDateRangeText.value,
        rows: previewResult.value.rows,
        summary: resolvedSummary.value,
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
  } finally {
    pageReady.value = true;
  }
});

/** keep-alive 再次进入：恢复默认进行中日窗（截止=当前）并重查 */
onActivated(() => {
  if (!pageReady.value) {
    return;
  }
  dateRangeManuallyEdited.value = false;
  refreshQueryEndToNow();
  if (resolveOrgIdForRequest()) {
    void handleSearch();
  }
});
</script>

<template>
  <Page
    auto-content-height
    class="daily-settlement-page min-h-0 bg-[var(--el-fill-color-light)] p-2"
    content-class="!flex !h-full !min-h-0 !min-w-0 !flex-col !overflow-hidden !p-0"
  >
    <!-- 整页内容约 70% 宽，右侧留白，版心更接近打印稿 -->
    <div
      class="daily-settlement-layout flex h-full min-h-0 w-[70%] min-w-0 max-w-[70%] shrink-0 flex-col gap-2 self-start overflow-hidden"
    >
      <ElCard shadow="never" class="daily-settlement-query-card shrink-0">
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
            <ElButton
              type="primary"
              :loading="queryLoading || initLoading"
              @click="handleQueryClick"
            >
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
              打印日结单
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
        <div class="flex h-full min-h-0 flex-col gap-2.5 overflow-y-auto">
          <h3
            class="text-center text-lg font-semibold leading-tight tracking-wide text-[var(--el-text-color-primary)]"
          >
            门诊收费日结单
          </h3>

          <ElAlert
            v-if="hasQueried && hasGapHardBlock"
            type="error"
            :closable="false"
            show-icon
            class="daily-settlement-alert"
            :title="gapHardBlockMessage"
          />
          <ElAlert
            v-if="hasQueried && gapSoftWarnCount > 0"
            type="warning"
            :closable="false"
            show-icon
            class="daily-settlement-alert"
            :title="`截止后仍有 ${gapSoftWarnCount} 笔未交账（仅提醒，不拦截提交）`"
          />

          <div
            class="grid grid-cols-4 gap-x-4 gap-y-1 border-b border-[var(--el-border-color)] pb-2.5 text-sm leading-snug"
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

          <template v-if="hasFinTypeDisplay">
            <div class="flex flex-col gap-1.5 border-b border-[var(--el-border-color)] pb-2.5">
              <div
                v-for="(row, rowIndex) in channelSummaryColumns"
                :key="`fin-type-${rowIndex}`"
                class="grid grid-cols-4 gap-x-5 gap-y-1 text-sm leading-snug"
              >
                <div
                  v-for="item in row"
                  :key="item.label"
                  class="flex justify-between gap-2 text-[var(--el-text-color-primary)]"
                >
                  <span class="text-[var(--el-text-color-regular)]">{{ item.label }}：</span>
                  <span class="font-medium">{{ item.value }}</span>
                </div>
              </div>
            </div>
          </template>

          <template v-if="hasQueried && hasSummaryDisplay">
            <DailySettlementSummaryPanel :summary="resolvedSummary" />
          </template>

          <ElEmpty
            v-if="hasQueried && !queryLoading && !hasFinTypeDisplay && !hasSummaryDisplay"
            description="当前日结时间范围内暂无收费汇总"
            class="py-6"
          />
        </div>
      </ElCard>
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
        <ElTableColumn label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              :loading="historyReprintLoadingId === String(row.settlementId ?? '')"
              @click="handleReprintHistory(row)"
            >
              重打原单
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
.daily-settlement-query-card :deep(.el-card__body) {
  padding: 10px 14px;
}

.daily-settlement-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 10px 14px;
  overflow: hidden;
}

.daily-settlement-query-form :deep(.el-form-item) {
  margin-right: 12px;
  margin-bottom: 0;
}

.daily-settlement-alert {
  --el-alert-padding: 8px 12px;
}

.daily-settlement-alert :deep(.el-alert__title) {
  font-size: 13px;
  line-height: 1.35;
}

.daily-settlement-history-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}
</style>
