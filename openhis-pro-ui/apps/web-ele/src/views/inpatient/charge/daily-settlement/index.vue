<script setup lang="ts">
import type {
  InpatientDailySettlementChannelRowDto,
  InpatientDailySettlementSummaryDto,
  LoadInpatientDailySettlementPageInitResponse,
  PreviewInpatientDailySettlementResponse,
} from '#/api/inpatient/daily-settlement';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import dayjs from 'dayjs';
import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTooltip,
} from 'element-plus';

import {
  INPATIENT_DAILY_SETTLEMENT_SUMMARY_DISPLAY,
  loadInpatientDailySettlementPageInitApi,
  normalizePreviewInpatientDailySettlementResponse,
  previewInpatientDailySettlementApi,
  resolveDefaultCashierIdFromPageInit,
  submitInpatientDailySettlementApi,
} from '#/api/inpatient/daily-settlement';
import { useRequest } from '#/hooks/use-request';
import { useAuthStore } from '#/store/auth';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import { useUserStore } from '@vben/stores';
import DailySettlementHandoverDialog from '#/components/charge/daily-settlement-handover-dialog.vue';

defineOptions({
  name: 'ChargeIpDailySettlement',
});

interface SummaryItem {
  label: string;
  value: string;
}

interface QueryFormState {
  dateRange: [string, string];
  expenseProperty: string;
  cashierId: string;
}

interface ReconciliationFormState {
  settlementOrg: string;
  settlementDateRange: [string, string];
  settlementType: string;
  chargeTotal: string;
  fundTotal: string;
  personalPayTotal: string;
  cashPayTotal: string;
}

const authStore = useAuthStore();
const userStore = useUserStore();

const today = dayjs().format('YYYY-MM-DD');
const queryForm = reactive<QueryFormState>({
  dateRange: [today, today],
  expenseProperty: '',
  cashierId: '',
});

const pageInit = ref<LoadInpatientDailySettlementPageInitResponse | null>(null);
const previewResult = ref<PreviewInpatientDailySettlementResponse | null>(null);
const hasQueried = ref(false);
const handoverDialogVisible = ref(false);

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

const reconciliationForm = reactive<ReconciliationFormState>({
  settlementOrg: 'jilin',
  settlementDateRange: ['2026-03-01', '2026-03-31'],
  settlementType: 'hospital',
  chargeTotal: '0',
  fundTotal: '0',
  personalPayTotal: '0',
  cashPayTotal: '0',
});

const expensePropertyOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.expensePropertyOptions ?? [],
);

const cashierOptions = computed<ApiOptionItem[]>(() => pageInit.value?.cashierOptions ?? []);

const financialDelegateEnabled = computed(() => pageInit.value?.financialDelegateEnabled === true);

const selectedCashierLabel = computed(() => {
  const matched = cashierOptions.value.find((item) => item.value === queryForm.cashierId);
  return matched?.label?.trim() || '—';
});

const canSubmitSettlement = computed(() => {
  const count = previewResult.value?.paymentCount ?? 0;
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

// 将 summary 各字段映射为展示项。
function formatSummaryFieldValue(
  key: (typeof INPATIENT_DAILY_SETTLEMENT_SUMMARY_DISPLAY)[number]['key'],
  summary: InpatientDailySettlementSummaryDto,
  unit: string,
): string {
  const raw = summary[key];
  if (raw === undefined || raw === null) {
    return unit === '人' ? '0 人' : `0.00 ${unit}`;
  }
  if (unit === '人') {
    return `${raw} ${unit}`;
  }
  return `${raw} ${unit}`;
}

const summaryDisplayColumns = computed<SummaryItem[][]>(() => {
  const summary = resolvedSummary.value;
  if (!summary) {
    return [];
  }
  const items: SummaryItem[] = INPATIENT_DAILY_SETTLEMENT_SUMMARY_DISPLAY.map(
    ({ key, label, unit }) => ({
      label,
      value: formatSummaryFieldValue(key, summary, unit),
    }),
  );
  return chunkItems(items, 4);
});

const hasSummaryDisplay = computed(() => summaryDisplayColumns.value.length > 0);

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

// 组装预览请求体。
function buildPreviewRequest() {
  const [dateFrom, dateTo] = queryForm.dateRange;
  return {
    dateFrom,
    dateTo,
    cashierId: preserveApiIdForRequest(queryForm.cashierId),
    expenseProperty: queryForm.expenseProperty?.trim() || undefined,
  };
}

// 加载页初始化数据并回填默认筛选项。
async function loadPageInit() {
  const data = await runInit();
  pageInit.value = data;
  if (data.defaultDateFrom && data.defaultDateTo) {
    queryForm.dateRange = [data.defaultDateFrom, data.defaultDateTo];
  }
  if (data.expensePropertyOptions?.length) {
    const firstValue = data.expensePropertyOptions[0]?.value;
    if (firstValue) {
      queryForm.expenseProperty = firstValue;
    }
  }
  const defaultCashierId = resolveDefaultCashierIdFromPageInit(
    data,
    resolveLoginPractitionerIdForRequest(userStore.userInfo),
  );
  if (defaultCashierId) {
    queryForm.cashierId = defaultCashierId;
  }
}

// 按当前筛选条件查询未交账汇总。
async function handleSearch() {
  const [dateFrom, dateTo] = queryForm.dateRange;
  if (!dateFrom || !dateTo) {
    ElMessage.warning('请选择查询日期');
    return;
  }
  if (!queryForm.expenseProperty?.trim()) {
    ElMessage.warning('请选择费用性质');
    return;
  }
  if (!queryForm.cashierId?.trim()) {
    ElMessage.warning('请选择收款人');
    return;
  }
  try {
    const raw = await runPreview(buildPreviewRequest());
    previewResult.value = normalizePreviewInpatientDailySettlementResponse(
      raw as PreviewInpatientDailySettlementResponse & InpatientDailySettlementSummaryDto,
    );
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
    ElMessage.warning('请选择查询日期');
    return;
  }
  const orgId = preserveApiIdForRequest(authStore.getSelectedOrganization());
  if (!orgId) {
    ElMessage.warning('请先选择交账科室');
    return;
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
  const orgId = preserveApiIdForRequest(authStore.getSelectedOrganization());
  if (!dateFrom || !dateTo || !orgId || paymentIds.length === 0) {
    return;
  }
  try {
    const result = await runSubmit({
      dateFrom,
      dateTo,
      cashierId: preserveApiIdForRequest(queryForm.cashierId),
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

// 清空医保对账输入区（占位）。
function handleClearReconciliation() {
  reconciliationForm.chargeTotal = '0';
  reconciliationForm.fundTotal = '0';
  reconciliationForm.personalPayTotal = '0';
  reconciliationForm.cashPayTotal = '0';
}

// 提交医保对账清算（占位，后续接入）。
function handleSubmitReconciliation() {
  // 仅保留页面结构
}

onMounted(async () => {
  try {
    await loadPageInit();
    await handleSearch();
  } catch {
    // init 失败时不自动查询
  }
});

// 切换费用性质后自动刷新展示汇总。
watch(
  () => queryForm.expenseProperty,
  () => {
    if (!hasQueried.value || initLoading.value) {
      return;
    }
    void handleSearch();
  },
);
</script>

<template>
  <Page class="min-h-0 bg-[var(--el-fill-color-light)] p-2">
    <div class="grid h-full min-h-0 grid-cols-[2fr_1fr] gap-2">
      <div class="flex min-h-0 flex-col gap-2">
        <ElCard shadow="never">
          <ElForm
            :inline="true"
            :model="queryForm"
            class="daily-settlement-query-form flex flex-nowrap items-center overflow-x-auto"
          >
            <ElFormItem label="查询日期">
              <ElDatePicker
                v-model="queryForm.dateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                class="!w-[360px]"
              />
            </ElFormItem>
            <ElFormItem label="费用性质">
              <ElSelect v-model="queryForm.expenseProperty" class="!w-[120px]">
                <ElOption
                  v-for="item in expensePropertyOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="经办人">
              <ElSelect
                v-model="queryForm.cashierId"
                placeholder="经办人"
                class="!w-[140px]"
                :disabled="!financialDelegateEnabled && cashierOptions.length <= 1"
              >
                <ElOption
                  v-for="item in cashierOptions"
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

        <ElCard shadow="never" class="daily-settlement-card min-h-0 flex-1">
          <div class="flex h-full min-h-0 flex-col gap-4">
            <h3
              class="text-center text-xl font-semibold tracking-wide text-[var(--el-text-color-primary)]"
            >
              住院收费日结单
            </h3>

            <div
              class="grid grid-cols-5 gap-4 border-b border-[var(--el-border-color)] pb-3 text-sm"
            >
              <div>
                <span class="text-[var(--el-text-color-regular)]">经办人姓名：</span>
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
                    <span class="text-[var(--el-text-color-regular)]">{{ item.label }}：</span>
                    <span class="font-medium">{{ item.value }}</span>
                  </div>
                </div>
              </div>
            </template>

            <template v-if="hasQueried && hasSummaryDisplay">
              <div class="flex flex-col gap-3">
                <div
                  v-for="(row, rowIndex) in summaryDisplayColumns"
                  :key="`summary-${rowIndex}`"
                  class="grid grid-cols-4 gap-x-6 gap-y-2 text-sm"
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

            <ElEmpty
              v-if="hasQueried && !queryLoading && !hasFinTypeDisplay && !hasSummaryDisplay"
              description="当前日期范围内暂无收费汇总"
              class="py-8"
            />
          </div>
        </ElCard>
      </div>

      <ElCard shadow="never" class="daily-settlement-card h-full min-h-0">
        <div class="flex h-full min-h-0 flex-col">
          <div
            class="mb-3 border-b border-[var(--el-border-color)] pb-2 text-sm font-medium text-[var(--el-text-color-primary)]"
          >
            医保对账
          </div>
          <ElForm :model="reconciliationForm" label-width="108px" class="grid grid-cols-1 gap-2">
            <ElFormItem label="结算统办机构">
              <ElSelect v-model="reconciliationForm.settlementOrg" class="w-full">
                <ElOption label="吉林省医保" value="jilin" />
                <ElOption label="长春市医保" value="changchun" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="结算日期">
              <ElDatePicker
                v-model="reconciliationForm.settlementDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                class="w-full"
              />
            </ElFormItem>
            <ElFormItem label="清算类型">
              <ElSelect v-model="reconciliationForm.settlementType" class="w-full">
                <ElOption label="门诊" value="clinic" />
                <ElOption label="住院" value="hospital" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="医疗费总额">
              <ElInput v-model="reconciliationForm.chargeTotal" />
            </ElFormItem>
            <ElFormItem label="基金支付总额">
              <ElInput v-model="reconciliationForm.fundTotal" />
            </ElFormItem>
            <ElFormItem label="个人账户支付总额">
              <ElInput v-model="reconciliationForm.personalPayTotal" />
            </ElFormItem>
            <ElFormItem label="现金支付总额">
              <ElInput v-model="reconciliationForm.cashPayTotal" />
            </ElFormItem>
          </ElForm>
          <div
            class="mt-auto flex items-center justify-end gap-2 border-t border-[var(--el-border-color)] pt-3"
          >
            <ElButton @click="handleClearReconciliation">清算</ElButton>
            <ElButton type="primary" @click="handleSubmitReconciliation">手动清算</ElButton>
          </div>
        </div>
      </ElCard>
    </div>

    <DailySettlementHandoverDialog
      v-model:visible="handoverDialogVisible"
      :items="previewResult?.items ?? []"
      :handover-rows="previewResult?.handoverRows"
      :handover-summary="previewResult?.handoverSummary"
      :summary-display="INPATIENT_DAILY_SETTLEMENT_SUMMARY_DISPLAY"
      :zero-net-income-hint="isZeroNetIncome"
      :loading="submitLoading"
      @confirm="handleHandoverConfirm"
    />
  </Page>
</template>

<style scoped>
.daily-settlement-card :deep(.el-card__body) {
  padding: 12px;
}

.daily-settlement-query-form :deep(.el-form-item) {
  margin-right: 12px;
  margin-bottom: 0;
}
</style>
