<!--
  住院药房日结（T3）：菜单由后端驱动时可注册路径 /inpatient/pharmacy/daily-settlement
-->
<script setup lang="ts">
import type { PharmacyDailySettlementDispenseItemDto } from '#/api/outpatient/pharmacy-daily-settlement';
import type {
  PharmacyDailySettlementHistoryItemDto,
  PreviewInpatientPharmacyDailySettlementResponse,
} from '#/api/inpatient/pharmacy-daily-settlement';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import dayjs from 'dayjs';
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  loadInpatientPharmacyDailySettlementPageInitApi,
  normalizePreviewInpatientPharmacyDailySettlementResponse,
  pageInpatientPharmacyDailySettlementHistoryApi,
  previewInpatientPharmacyDailySettlementApi,
  submitInpatientPharmacyDailySettlementApi,
  withdrawInpatientPharmacyDailySettlementApi,
} from '#/api/inpatient/pharmacy-daily-settlement';
import { useRequest } from '#/hooks/use-request';
import { downloadPharmacyDailySettlementExcel } from '#/utils/export/pharmacy-daily-settlement-excel';
import { formatPurchaseMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { printPharmacyDailySettlement } from '#/utils/print/print-pharmacy-daily-settlement';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';
import {
  REPORT_TABLE_AMOUNT_CELL,
  REPORT_TABLE_NUM_CELL,
  ReportElTable,
} from '#/views/report/shared';

defineOptions({
  name: 'InpatientPharmacyDailySettlement',
});

const SETTLEMENT_STATUS_SUBMITTED = 'SUBMITTED';

interface QueryFormState {
  dateRange: [string, string];
  locationId: string;
  /** 发药人多选（与门诊日结收款人同构） */
  practitionerIds: string[];
}

const todayStart = dayjs().startOf('day').format('YYYY-MM-DD HH:mm:ss');
const nowEnd = () => dayjs().format('YYYY-MM-DD HH:mm:ss');

const queryForm = reactive<QueryFormState>({
  dateRange: [todayStart, nowEnd()],
  locationId: '',
  practitionerIds: [],
});

const locationOptions = ref<ApiOptionItem[]>([]);
/** 发药人下拉：查询后由本窗发药人回填，可改选再查 */
const practitionerOptions = ref<ApiOptionItem[]>([]);
const previewResult = ref<PreviewInpatientPharmacyDailySettlementResponse | null>(null);
const hasQueried = ref(false);
const submitDialogVisible = ref(false);
const selectedDispenseIds = ref<string[]>([]);
/** 弹窗当前勾选行（用于合计）。 */
const selectedDispenseRows = ref<PharmacyDailySettlementDispenseItemDto[]>([]);
/** 交账明细弹窗表格（用于打开时默认勾选，避免 selection-change 空数组把确认按钮灰掉）。 */
const dispenseTableRef = ref<{
  clearSelection: () => void;
  toggleRowSelection: (row: PharmacyDailySettlementDispenseItemDto, selected?: boolean) => void;
} | null>(null);
const historyDialogVisible = ref(false);
const historyItems = ref<PharmacyDailySettlementHistoryItemDto[]>([]);
const historyLoading = ref(false);
const historyWithdrawLoadingId = ref('');
const printSubmitting = ref(false);

const { run: runInit, loading: initLoading } = useRequest(
  loadInpatientPharmacyDailySettlementPageInitApi,
  { manual: true },
);
const { run: runPreview, loading: queryLoading } = useRequest(
  previewInpatientPharmacyDailySettlementApi,
  { manual: true },
);
const { run: runSubmit, loading: submitLoading } = useRequest(
  submitInpatientPharmacyDailySettlementApi,
  { manual: true },
);
const { run: runWithdraw, loading: withdrawLoading } = useRequest(
  withdrawInpatientPharmacyDailySettlementApi,
  {
    manual: true,
    onSuccess: () => {
      ElMessage.success('撤回成功');
    },
  },
);

const hasGapHardBlock = computed(() =>
  Boolean(previewResult.value?.gapHardBlockEarliestAt?.trim()),
);

const gapHardBlockMessage = computed(
  () =>
    previewResult.value?.gapHardBlockMessage?.trim() ||
    (hasGapHardBlock.value ? '存在漏交硬栏，请调整日结时间起点后再提交' : ''),
);

const gapSoftWarnCount = computed(() => previewResult.value?.gapSoftWarnCount ?? 0);

/** 取可比较金额；非法值按 0。 */
function toComparableAmount(value: unknown): number {
  const num = Number(value);
  return Number.isFinite(num) ? num : 0;
}

/** 按售价总额倒序（金额大在上）。 */
function sortBySaleAmountDesc<T extends { saleAmount?: string | number }>(rows: T[]): T[] {
  return [...rows].sort(
    (a, b) => toComparableAmount(b.saleAmount) - toComparableAmount(a.saleAmount),
  );
}

/** 大类汇总卡片数据（售价总额倒序）。 */
const categoryRows = computed(() => sortBySaleAmountDesc(previewResult.value?.categoryRows ?? []));

/** 品规汇总表数据（售价总额倒序）。 */
const productRows = computed(() => sortBySaleAmountDesc(previewResult.value?.productRows ?? []));

/** 当前选中药房名称。 */
const selectedPharmacyLabel = computed(() => {
  const id = preserveApiIdForRequest(queryForm.locationId);
  if (!id) {
    return '';
  }
  const hit = locationOptions.value.find((item) => preserveApiIdForRequest(item.value) === id);
  return hit?.label?.trim() || '';
});

/** 发药人展示文案（空选=全部）。 */
const selectedDispenserNamesText = computed(() => {
  if (!queryForm.practitionerIds.length) {
    return '全部';
  }
  // 按已选 id 取下拉文案
  const labels: string[] = [];
  for (const id of queryForm.practitionerIds) {
    const key = preserveApiIdForRequest(id);
    const hit = practitionerOptions.value.find(
      (item) => preserveApiIdForRequest(item.value) === key,
    );
    const label = hit?.label?.trim();
    if (label) {
      labels.push(label);
    }
  }
  return labels.length ? labels.join('、') : '全部';
});

/** 日结时间窗展示。 */
const settlementPeriodText = computed(() => {
  const [from, to] = queryForm.dateRange;
  if (!from || !to) {
    return '';
  }
  return `${from} ~ ${to}`;
});

const canSubmit = computed(() => {
  const count =
    previewResult.value?.pendingCount ??
    previewResult.value?.unsettledCount ??
    previewResult.value?.items?.length ??
    0;
  // 漏交硬栏仅提示 + 提交 Gate；不禁用「确认交账」入口与弹窗勾选
  return hasQueried.value && count > 0 && !queryLoading.value;
});

/** 弹窗已选数量合计。 */
const selectedQuantityTotal = computed(() => formatSelectedAmountSum('dispenseQuantity'));

/** 弹窗已选进价合计。 */
const selectedPurchaseTotal = computed(() => formatSelectedAmountSum('purchaseAmount'));

/** 弹窗已选售价合计。 */
const selectedSaleTotal = computed(() => formatSelectedAmountSum('saleAmount'));

/** 汇总弹窗勾选行的数值字段。 */
function formatSelectedAmountSum(
  field: 'dispenseQuantity' | 'purchaseAmount' | 'saleAmount',
): string {
  let total = 0;
  for (const row of selectedDispenseRows.value) {
    const raw = row[field];
    const num = typeof raw === 'number' ? raw : Number(raw);
    if (Number.isFinite(num)) {
      total += num;
    }
  }
  if (field === 'purchaseAmount') {
    return formatPurchaseMoneyDisplay(total) || '0.00';
  }
  return total.toFixed(2);
}

/** 组装预览请求体（发药人多选软筛）。 */
function buildPreviewRequest() {
  const [dateFrom, dateTo] = queryForm.dateRange;
  const locationId = preserveApiIdForRequest(queryForm.locationId);
  // 已选发药人主键（空数组表示不按人过滤）
  const practitionerIds = queryForm.practitionerIds
    .map((id) => preserveApiIdForRequest(id))
    .filter((id): id is string => Boolean(id));
  return {
    dateFrom,
    dateTo,
    locationId: locationId ?? '',
    practitionerIds: practitionerIds.length ? practitionerIds : undefined,
  };
}

/** 用查询结果合并窗内发药人到下拉（不覆盖已选手动项）。 */
function syncPractitionerOptionsFromPreview(
  data: PreviewInpatientPharmacyDailySettlementResponse | null,
) {
  const fromWindow = data?.practitionerOptions ?? [];
  if (!fromWindow.length) {
    return;
  }
  // 合并窗内人员到下拉（药师岗 + 本窗实际发药人）
  const byValue = new Map<string, ApiOptionItem>();
  for (const item of practitionerOptions.value) {
    const key = preserveApiIdForRequest(item.value);
    if (key) {
      byValue.set(key, { ...item, value: key });
    }
  }
  for (const item of fromWindow) {
    const key = preserveApiIdForRequest(item.value);
    if (key) {
      byValue.set(key, { ...item, value: key });
    }
  }
  practitionerOptions.value = [...byValue.values()];
}

/** 加载页初始化数据。 */
async function loadPageInit() {
  const data = await runInit();
  locationOptions.value = data?.pharmacyOptions ?? [];
  // 药师岗池进下拉；人选非必选（空=不按人过滤）
  practitionerOptions.value = data?.practitionerOptions ?? [];
  queryForm.practitionerIds = [];
  if (data?.defaultDateFrom) {
    queryForm.dateRange = [
      dayjs(data.defaultDateFrom).startOf('day').format('YYYY-MM-DD HH:mm:ss'),
      nowEnd(),
    ];
  }
  const firstLocation = preserveApiIdForRequest(locationOptions.value[0]?.value);
  if (firstLocation) {
    queryForm.locationId = firstLocation;
  }
}

/** 按当前筛选查询汇总。 */
async function handleSearch() {
  const [dateFrom, dateTo] = queryForm.dateRange;
  if (!dateFrom || !dateTo) {
    ElMessage.warning('请选择日结时间');
    return;
  }
  if (!preserveApiIdForRequest(queryForm.locationId)) {
    ElMessage.warning('请选择药房');
    return;
  }
  try {
    const data = normalizePreviewInpatientPharmacyDailySettlementResponse(
      await runPreview(buildPreviewRequest()),
    );
    previewResult.value = data;
    syncPractitionerOptionsFromPreview(data);
    hasQueried.value = true;
  } catch {
    previewResult.value = null;
    hasQueried.value = true;
  }
}

/** 打开提交弹窗并默认全选待交明细。 */
function handleOpenSubmit() {
  if (hasGapHardBlock.value) {
    // 仍打开弹窗供勾选核对；最终提交由后端漏交 Gate 拒绝
    ElMessage.warning(gapHardBlockMessage.value || '存在漏交硬栏，请调整日结时间起点后再提交');
  }
  const items = previewResult.value?.items ?? [];
  if (!items.length) {
    ElMessage.warning('当前没有可交账明细');
    return;
  }
  submitDialogVisible.value = true;
  void nextTick(() => {
    const table = dispenseTableRef.value;
    if (!table) {
      return;
    }
    // 表格挂载后显式全选，避免仅写 selectedDispenseIds 而勾选框未同步
    table.clearSelection();
    for (const item of items) {
      table.toggleRowSelection(item, true);
    }
  });
}

/** 确认提交勾选的发药明细。 */
async function handleConfirmSubmit() {
  const [dateFrom, dateTo] = queryForm.dateRange;
  const locationId = preserveApiIdForRequest(queryForm.locationId);
  if (!dateFrom || !dateTo || !locationId || selectedDispenseIds.value.length === 0) {
    return;
  }
  try {
    const result = await runSubmit({
      dateFrom,
      dateTo,
      locationId,
      selectedDispenseIds: selectedDispenseIds.value,
    });
    submitDialogVisible.value = false;
    ElMessage.success(`交账成功，单号 ${result.busNo ?? '—'}`);
    await handleSearch();
  } catch {
    // useRequest 已提示
  }
}

/** 格式化历史时间展示。 */
function formatHistoryTime(time?: string): string {
  if (!time?.trim()) {
    return '—';
  }
  return formatDateTime(time);
}

/** 历史行药房展示。 */
function formatHistoryScope(row: PharmacyDailySettlementHistoryItemDto): string {
  const locationName = row.locationName?.trim();
  if (locationName) {
    return locationName;
  }
  return '—';
}

/** 金额/数量两位小数展示（空或非法为 —；售价/数量）。 */
function formatDisplayAmount(value?: string | number): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return num.toFixed(2);
}

/** 进价侧金额：中药可至三位；大类行可传 categoryCode。 */
function formatPurchaseAmount(value?: string | number, categoryCode?: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  return formatPurchaseMoneyDisplay(value, categoryCode) || '—';
}

/** 进价单价展示（与进价金额同一口径）。 */
function formatPurchaseUnitPrice(value?: string | number): string {
  return formatPurchaseAmount(value);
}

/** 历史金额两位小数展示。 */
function formatHistoryAmount(value?: string | number): string {
  return formatDisplayAmount(value);
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

/** 打开交账历史（按交账提交时刻窗查询）。 */
async function openHistoryDialog() {
  historyDialogVisible.value = true;
  historyLoading.value = true;
  try {
    const { submittedFrom, submittedTo } = resolveHistorySubmittedRange();
    const data = await pageInpatientPharmacyDailySettlementHistoryApi({
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

/** 历史行是否可撤回。 */
function canWithdrawHistoryRow(row: PharmacyDailySettlementHistoryItemDto): boolean {
  const status = row.statusEnum?.trim();
  return !status || status === SETTLEMENT_STATUS_SUBMITTED;
}

/** 撤回历史交账。 */
async function handleWithdrawHistory(row: PharmacyDailySettlementHistoryItemDto) {
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

/** 弹窗勾选变更：同步待提交的 dispenseId 列表与勾选行。 */
function handleDispenseSelectionChange(rows: PharmacyDailySettlementDispenseItemDto[]) {
  selectedDispenseRows.value = rows;
  selectedDispenseIds.value = rows
    .map((row) => preserveApiIdForRequest(row.dispenseId))
    .filter((id): id is string => Boolean(id));
}

/** 打印当前预览的药房日结汇总。 */
async function handlePrintSettlement() {
  if (!hasQueried.value) {
    ElMessage.warning('请先查询日结汇总后再打印');
    return;
  }
  if (!categoryRows.value.length && !productRows.value.length) {
    ElMessage.warning('当前时间范围内暂无汇总，无法打印');
    return;
  }
  printSubmitting.value = true;
  try {
    await printPharmacyDailySettlement({
      jobName: '住院药房日结单',
      bizRef: {
        locationId: preserveApiIdForRequest(queryForm.locationId) ?? '',
        dateFrom: queryForm.dateRange[0] ?? '',
        dateTo: queryForm.dateRange[1] ?? '',
        practitionerIds: queryForm.practitionerIds
          .map((id) => preserveApiIdForRequest(id))
          .filter((id): id is string => Boolean(id)),
      },
      source: {
        hospitalName: resolvePrintHospitalName(),
        documentTitle: '住院药房日结单',
        pharmacyName: selectedPharmacyLabel.value,
        settlementPeriodText: settlementPeriodText.value,
        dispenserNamesText: selectedDispenserNamesText.value,
        pendingCount: previewResult.value?.pendingCount ?? 0,
        totalIncome: previewResult.value?.totalIncome,
        categoryRows: categoryRows.value,
        productRows: productRows.value,
      },
    });
  } finally {
    printSubmitting.value = false;
  }
}

/** 导出当前预览的药房日结 Excel（与交账审批药房日结同口径）。 */
function handleExportSettlement() {
  if (!hasQueried.value) {
    ElMessage.warning('请先查询日结汇总后再导出');
    return;
  }
  if (!categoryRows.value.length && !productRows.value.length) {
    ElMessage.warning('当前时间范围内暂无汇总，无法导出');
    return;
  }
  downloadPharmacyDailySettlementExcel({
    documentTitle: '住院药房日结',
    pharmacyName: selectedPharmacyLabel.value,
    settlementPeriodText: settlementPeriodText.value,
    dispenserNamesText: selectedDispenserNamesText.value,
    pendingCount: previewResult.value?.pendingCount ?? 0,
    totalIncome: previewResult.value?.totalIncome,
    categoryRows: categoryRows.value,
    productRows: productRows.value,
  });
  ElMessage.success('导出成功');
}

watch(submitDialogVisible, (visible) => {
  if (!visible) {
    selectedDispenseIds.value = [];
    selectedDispenseRows.value = [];
  }
});

onMounted(async () => {
  try {
    await loadPageInit();
    if (queryForm.locationId) {
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
    content-class="!overflow-hidden flex h-full min-h-0 flex-col"
    class="bg-[var(--el-fill-color-light)]"
  >
    <div class="flex h-full min-h-0 flex-col gap-2">
      <ElCard shadow="never" body-style="padding: 8px 12px;">
        <ElForm
          :inline="true"
          :model="queryForm"
          class="pharmacy-daily-settlement-query-form flex flex-nowrap items-center overflow-x-auto"
        >
          <ElFormItem label="药房">
            <ElSelect
              v-model="queryForm.locationId"
              filterable
              placeholder="请选择药房"
              class="!w-[200px]"
              @change="handleSearch"
            >
              <ElOption
                v-for="item in locationOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="日结时间">
            <ElDatePicker
              v-model="queryForm.dateRange"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm:ss"
              start-placeholder="开始"
              end-placeholder="结束"
              :clearable="false"
              class="!w-[360px]"
              @change="handleSearch"
            />
          </ElFormItem>
          <ElFormItem label="发药人">
            <ElSelect
              v-model="queryForm.practitionerIds"
              multiple
              collapse-tags
              collapse-tags-tooltip
              clearable
              filterable
              placeholder="可选；空=不按人"
              class="!w-[220px]"
              @change="handleSearch"
            >
              <ElOption
                v-for="item in practitionerOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <div class="ml-auto flex shrink-0 items-center gap-2">
            <ElButton type="primary" :loading="queryLoading || initLoading" @click="handleSearch">
              查询
            </ElButton>
            <ElButton plain :loading="printSubmitting" @click="handlePrintSettlement"
              >打印</ElButton
            >
            <ElButton plain @click="handleExportSettlement">导出 Excel</ElButton>
            <ElButton plain :loading="historyLoading" @click="openHistoryDialog">交账历史</ElButton>
            <ElButton
              type="primary"
              plain
              :loading="submitLoading"
              :disabled="!canSubmit"
              @click="handleOpenSubmit"
            >
              确认交账
            </ElButton>
          </div>
        </ElForm>
      </ElCard>

      <ElCard shadow="never" class="pharmacy-daily-settlement-card min-h-0 flex-1">
        <div class="flex h-full min-h-0 flex-col gap-3">
          <ElAlert
            v-if="hasQueried && hasGapHardBlock"
            type="error"
            :closable="false"
            show-icon
            class="shrink-0"
            :title="gapHardBlockMessage"
          />
          <ElAlert
            v-if="hasQueried && gapSoftWarnCount > 0"
            type="warning"
            :closable="false"
            show-icon
            class="shrink-0"
            :title="`截止后仍有 ${gapSoftWarnCount} 笔未交账（仅提醒）`"
          />

          <div class="flex shrink-0 flex-wrap gap-x-6 gap-y-1 text-sm">
            <div>
              <span class="text-[var(--el-text-color-regular)]">待交笔数：</span>
              {{ hasQueried ? (previewResult?.pendingCount ?? 0) : '—' }}
            </div>
            <div>
              <span class="text-[var(--el-text-color-regular)]">售价合计：</span>
              {{ previewResult?.totalIncome ?? '—' }}
            </div>
          </div>

          <!-- 上：大类汇总卡片；下：品规汇总报表表 -->
          <div class="flex min-h-0 flex-1 flex-col gap-3">
            <div class="shrink-0">
              <h4 class="pharmacy-daily-settlement-section-title">大类汇总</h4>
              <div v-loading="queryLoading" class="pharmacy-category-summary">
                <div
                  v-for="(row, index) in categoryRows"
                  :key="row.categoryCode ?? `category-${index}`"
                  class="pharmacy-category-summary__card"
                >
                  <div class="pharmacy-category-summary__name">
                    {{ row.categoryLabel?.trim() || '—' }}
                  </div>
                  <div class="pharmacy-category-summary__metrics">
                    <div class="pharmacy-category-summary__metric">
                      <span class="pharmacy-category-summary__label">进价总额</span>
                      <span class="pharmacy-category-summary__value">
                        {{ formatPurchaseAmount(row.purchaseAmount, row.categoryCode) }}
                      </span>
                    </div>
                    <div class="pharmacy-category-summary__metric">
                      <span class="pharmacy-category-summary__label">售价总额</span>
                      <span
                        class="pharmacy-category-summary__value pharmacy-category-summary__value--primary"
                      >
                        {{ formatDisplayAmount(row.saleAmount) }}
                      </span>
                    </div>
                  </div>
                </div>
                <div
                  v-if="hasQueried && !queryLoading && !categoryRows.length"
                  class="pharmacy-category-summary__empty text-sm text-[var(--el-text-color-secondary)]"
                >
                  暂无大类汇总
                </div>
              </div>
            </div>

            <div class="flex min-h-0 flex-1 flex-col">
              <h4 class="pharmacy-daily-settlement-section-title">品规汇总</h4>
              <div class="min-h-0 flex-1">
                <ReportElTable :loading="queryLoading" :data="productRows" height="100%">
                  <ElTableColumn
                    prop="busNo"
                    label="药品编码"
                    width="120"
                    align="center"
                    header-align="center"
                    show-overflow-tooltip
                  />
                  <ElTableColumn
                    prop="medicationName"
                    label="药品"
                    min-width="160"
                    align="center"
                    header-align="center"
                    show-overflow-tooltip
                  />
                  <ElTableColumn
                    prop="specification"
                    label="规格"
                    width="120"
                    align="center"
                    header-align="center"
                    show-overflow-tooltip
                  />
                  <ElTableColumn
                    prop="manufacturer"
                    label="厂家"
                    min-width="120"
                    align="center"
                    header-align="center"
                    show-overflow-tooltip
                  />
                  <ElTableColumn
                    prop="netQuantity"
                    label="净数量"
                    width="100"
                    align="right"
                    header-align="right"
                    :class-name="REPORT_TABLE_NUM_CELL"
                  >
                    <template #default="{ row }">
                      {{ formatDisplayAmount(row.netQuantity) }}
                    </template>
                  </ElTableColumn>
                  <ElTableColumn
                    prop="purchaseUnitPrice"
                    label="进价单价"
                    width="110"
                    align="right"
                    header-align="right"
                    :class-name="REPORT_TABLE_AMOUNT_CELL"
                  >
                    <template #default="{ row }">
                      {{ formatPurchaseUnitPrice(row.purchaseUnitPrice) }}
                    </template>
                  </ElTableColumn>
                  <ElTableColumn
                    prop="saleUnitPrice"
                    label="售价单价"
                    width="110"
                    align="right"
                    header-align="right"
                    :class-name="REPORT_TABLE_AMOUNT_CELL"
                  >
                    <template #default="{ row }">
                      {{ formatDisplayAmount(row.saleUnitPrice) }}
                    </template>
                  </ElTableColumn>
                  <ElTableColumn
                    prop="purchaseAmount"
                    label="进价总额"
                    width="110"
                    align="right"
                    header-align="right"
                    :class-name="REPORT_TABLE_AMOUNT_CELL"
                  >
                    <template #default="{ row }">
                      {{ formatPurchaseAmount(row.purchaseAmount) }}
                    </template>
                  </ElTableColumn>
                  <ElTableColumn
                    prop="saleAmount"
                    label="售价总额"
                    width="110"
                    align="right"
                    header-align="right"
                    :class-name="REPORT_TABLE_AMOUNT_CELL"
                  >
                    <template #default="{ row }">
                      {{ formatDisplayAmount(row.saleAmount) }}
                    </template>
                  </ElTableColumn>
                </ReportElTable>
              </div>
            </div>
          </div>
        </div>
      </ElCard>
    </div>

    <ElDialog v-model="submitDialogVisible" title="确认交账明细" width="1100px">
      <ElTable
        ref="dispenseTableRef"
        :data="previewResult?.items ?? []"
        border
        stripe
        size="small"
        max-height="420"
        row-key="dispenseId"
        @selection-change="handleDispenseSelectionChange"
      >
        <ElTableColumn type="selection" width="48" />
        <ElTableColumn prop="busNo" label="单号" min-width="120" show-overflow-tooltip />
        <ElTableColumn prop="dispenseTime" label="发药时间" width="160" align="center" />
        <ElTableColumn prop="statusEnumText" label="状态" width="80" align="center" />
        <ElTableColumn prop="dispenseEnumText" label="类型" width="70" align="center" />
        <ElTableColumn prop="medicationName" label="药品名" min-width="140" show-overflow-tooltip />
        <ElTableColumn
          prop="specification"
          label="规格"
          width="100"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="manufacturer"
          label="厂家"
          min-width="110"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn prop="dispenseQuantity" label="数量" width="80" align="right" />
        <ElTableColumn prop="unitText" label="单位" width="60" align="center" />
        <ElTableColumn prop="purchaseAmount" label="进价" width="90" align="right">
          <template #default="{ row }">
            {{ formatPurchaseAmount(row.purchaseAmount) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="saleAmount" label="售价/小计" width="100" align="right">
          <template #default="{ row }">
            {{ formatDisplayAmount(row.saleAmount) }}
          </template>
        </ElTableColumn>
      </ElTable>
      <div class="mt-2 flex flex-wrap gap-x-6 text-sm text-[var(--el-text-color-regular)]">
        <span>已选 {{ selectedDispenseIds.length }} 笔</span>
        <span>数量合计：{{ selectedQuantityTotal }}</span>
        <span>进价合计：{{ selectedPurchaseTotal }}</span>
        <span>售价合计：{{ selectedSaleTotal }}</span>
      </div>
      <template #footer>
        <ElButton @click="submitDialogVisible = false">取消</ElButton>
        <ElButton
          type="primary"
          :loading="submitLoading"
          :disabled="selectedDispenseIds.length === 0"
          @click="handleConfirmSubmit"
        >
          确认交账（{{ selectedDispenseIds.length }} 笔）
        </ElButton>
      </template>
    </ElDialog>

    <ElDialog
      v-model="historyDialogVisible"
      title="交账历史"
      width="92%"
      top="4vh"
      destroy-on-close
      class="pharmacy-daily-settlement-history-dialog"
    >
      <ElTable v-loading="historyLoading" :data="historyItems" border stripe height="560">
        <ElTableColumn
          prop="busNo"
          label="交账单号"
          min-width="170"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn label="药房" min-width="140" align="center" show-overflow-tooltip>
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
        <ElTableColumn label="售价合计" width="110" align="right" header-align="right">
          <template #default="{ row }">
            {{ formatHistoryAmount(row.totalIncome) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="statusEnumText" label="状态" width="100" align="center" />
        <ElTableColumn label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
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
.pharmacy-daily-settlement-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 12px;
}

.pharmacy-daily-settlement-query-form :deep(.el-form-item) {
  margin-right: 12px;
  margin-bottom: 0;
}

.pharmacy-daily-settlement-history-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}

.pharmacy-daily-settlement-section-title {
  position: relative;
  padding-left: 10px;
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.pharmacy-daily-settlement-section-title::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--el-color-primary);
  content: '';
  transform: translateY(-50%);
}

.pharmacy-category-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  min-height: 64px;
}

.pharmacy-category-summary__card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 180px;
  padding: 10px 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.pharmacy-category-summary__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.pharmacy-category-summary__metrics {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.pharmacy-category-summary__metric {
  display: flex;
  gap: 8px;
  justify-content: space-between;
  align-items: baseline;
  font-size: 13px;
}

.pharmacy-category-summary__label {
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.pharmacy-category-summary__value {
  font-variant-numeric: tabular-nums;
  font-weight: 500;
  color: var(--el-text-color-primary);
  text-align: right;
}

.pharmacy-category-summary__value--primary {
  font-weight: 600;
  color: var(--el-color-primary);
}

.pharmacy-category-summary__empty {
  display: flex;
  align-items: center;
  min-height: 48px;
  padding: 0 4px;
}
</style>
