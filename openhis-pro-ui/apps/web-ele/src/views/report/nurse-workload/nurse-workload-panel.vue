<!--
  护士工作量统计：每位护士一行信息条，行内 chip 展示处置与耗材，无表头。
-->
<script setup lang="ts">
import type {
  EncounterScope,
  NurseConsumableDetailRowDto,
  NurseConsumableSummaryRowDto,
  NurseExecutionSummaryRowDto,
  NurseWorkloadQueryFilter,
} from '#/api/report/nurse-workload';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElEmpty,
  ElFormItem,
  ElMessage,
  ElRadioButton,
  ElRadioGroup,
} from 'element-plus';

import {
  ENCOUNTER_SCOPE_INPATIENT,
  ENCOUNTER_SCOPE_OUTPATIENT,
  loadNurseWorkloadFormInitApi,
  pageNurseConsumableWorkloadDetailApi,
  summarizeNurseConsumableWorkloadApi,
  summarizeNurseExecutionWorkloadApi,
} from '#/api/report/nurse-workload';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { optionDtosToSelectItems } from '#/views/report/shared/report-format';
import { buildReportDateRangeShortcuts } from '#/views/report/shared/report-date-range-shortcuts';
import {
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATERANGE,
  ReportQueryBar,
} from '#/views/report/shared';

import {
  aggregateConsumableDetailRows,
  buildExecutionItemChips,
  type NurseConsumableItemChip,
  type NurseExecutionItemChip,
} from './nurse-workload-card-data';
import NurseWorkloadNurseCard from './nurse-workload-nurse-card.vue';
import NurseWorkloadNurseDetailDialog from './nurse-workload-nurse-detail-dialog.vue';
import NurseWorkloadTotalsBar from './nurse-workload-totals-bar.vue';

defineOptions({ name: 'NurseWorkloadPanel' });

interface Props {
  embedded?: boolean;
}

interface QueryState {
  encounterScope: EncounterScope;
  dateFrom: string;
  dateTo: string;
}

interface NurseWorkloadCardView {
  consumableItemChips: NurseConsumableItemChip[];
  consumableItemCount: number;
  consumableSummaryRows: NurseConsumableSummaryRowDto[];
  consumableTotalAmount: number | string;
  consumableTotalQuantity: number | string;
  executionChips: NurseExecutionItemChip[];
  executionSummaryRows: NurseExecutionSummaryRowDto[];
  executionTotalAmount: number | string;
  executionTotalCount: number;
  practitionerId: string;
  practitionerName: string;
}

const CONSUMABLE_DETAIL_PAGE_SIZE = 100;
const CONSUMABLE_DETAIL_MAX_PAGES = 20;

const props = withDefaults(defineProps<Props>(), {
  embedded: false,
});

const NURSE_SUMMARY_CONCURRENCY = 3;

const encounterScopeOptions = ref<ApiOptionItem[]>([]);
const nurseOptions = ref<ApiOptionItem[]>([]);
const nurseCards = ref<NurseWorkloadCardView[]>([]);
const overallExecutionTotal = ref(0);
const overallConsumableTotal = ref<number | string>(0);
const overallExecutionAmount = ref<number | string>(0);
const overallConsumableAmount = ref<number | string>(0);
const cardsLoading = ref(false);
const cardsLoadProgress = ref('');
const detailDialogVisible = ref(false);
const selectedNurse = ref<NurseWorkloadCardView | null>(null);
const isInitializing = ref(true);

const queryParams = reactive<QueryState>({
  encounterScope: ENCOUNTER_SCOPE_OUTPATIENT,
  dateFrom: '',
  dateTo: '',
});

/** 是否已应用过后端返回的默认就诊范围（仅首次初始化）。 */
const hasAppliedInitialScope = ref(false);

const { run: runInit, loading: initLoading } = useRequest(loadNurseWorkloadFormInitApi, {
  manual: true,
  onSuccess: (data) => {
    encounterScopeOptions.value = optionDtosToSelectItems(data?.encounterScopeOptions);
    nurseOptions.value = optionDtosToSelectItems(data?.nurseOptions);
    if (!hasAppliedInitialScope.value) {
      queryParams.encounterScope =
        (data?.defaultEncounterScope as EncounterScope) ?? ENCOUNTER_SCOPE_OUTPATIENT;
      hasAppliedInitialScope.value = true;
    }
    queryParams.dateFrom = data?.defaultDateFrom ?? queryParams.dateFrom;
    queryParams.dateTo = data?.defaultDateTo ?? queryParams.dateTo;
  },
});

const { run: runOverallExecutionSummary, loading: overallExecutionLoading } = useRequest(
  summarizeNurseExecutionWorkloadApi,
  {
    manual: true,
    onSuccess: (data) => {
      overallExecutionTotal.value = Number(data?.totalExecutionCount ?? 0);
      overallExecutionAmount.value = data?.totalAmount ?? 0;
    },
  },
);

const { run: runOverallConsumableSummary, loading: overallConsumableLoading } = useRequest(
  summarizeNurseConsumableWorkloadApi,
  {
    manual: true,
    onSuccess: (data) => {
      overallConsumableTotal.value = data?.totalQuantity ?? 0;
      overallConsumableAmount.value = data?.totalAmount ?? 0;
    },
  },
);

const nurseCountLabel = computed(() => nurseCards.value.length);

const isListLoading = computed(
  () =>
    initLoading.value ||
    overallExecutionLoading.value ||
    overallConsumableLoading.value ||
    cardsLoading.value,
);

const dateRangeShortcuts = buildReportDateRangeShortcuts();

const dateRange = computed<[string, string] | undefined>({
  get() {
    if (!queryParams.dateFrom || !queryParams.dateTo) {
      return undefined;
    }
    return [queryParams.dateFrom, queryParams.dateTo];
  },
  set(value) {
    queryParams.dateFrom = value?.[0] ?? '';
    queryParams.dateTo = value?.[1] ?? '';
  },
});

const baseQueryFilter = computed<NurseWorkloadQueryFilter>(() => ({
  encounterScope: queryParams.encounterScope,
  dateFrom: queryParams.dateFrom,
  dateTo: queryParams.dateTo,
}));

/** 组装查询筛选（不含护士）。 */
function buildQueryFilter(): NurseWorkloadQueryFilter {
  return { ...baseQueryFilter.value };
}

/** 限制并发数批量执行异步任务。 */
async function mapWithConcurrency<T, R>(
  items: T[],
  concurrency: number,
  mapper: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
  if (items.length === 0) {
    return [];
  }
  const results = new Array<R>(items.length);
  let cursor = 0;
  async function worker() {
    while (cursor < items.length) {
      const index = cursor;
      cursor += 1;
      results[index] = await mapper(items[index]!, index);
    }
  }
  const workerCount = Math.min(concurrency, items.length);
  await Promise.all(Array.from({ length: workerCount }, () => worker()));
  return results;
}

/** 判断全院合计是否存在有效数据。 */
function hasOverallWorkloadData(executionTotal: number, consumableTotal: number | string) {
  return executionTotal !== 0 || Number(consumableTotal) !== 0;
}

/** 拉取护士耗材明细分页并合并（用于卡片按品名聚合）。 */
async function fetchAllConsumableDetailRows(filter: NurseWorkloadQueryFilter) {
  const aggregated: NurseConsumableDetailRowDto[] = [];
  let pageNo = 1;
  let total = 0;
  while (pageNo <= CONSUMABLE_DETAIL_MAX_PAGES) {
    const data = await pageNurseConsumableWorkloadDetailApi({
      ...filter,
      pageNo,
      pageSize: CONSUMABLE_DETAIL_PAGE_SIZE,
    });
    const rows = data?.rows ?? [];
    total = Number(data?.total ?? 0);
    aggregated.push(...rows);
    if (aggregated.length >= total || rows.length < CONSUMABLE_DETAIL_PAGE_SIZE) {
      break;
    }
    pageNo += 1;
  }
  return aggregated;
}

/** 用护士下拉生成零值行（无数据时仍展示全部护士）。 */
function buildEmptyNurseCards() {
  return nurseOptions.value.map((nurse) => ({
    practitionerId: nurse.value,
    practitionerName: nurse.label,
    executionTotalCount: 0,
    executionTotalAmount: 0,
    executionSummaryRows: [],
    executionChips: [],
    consumableTotalQuantity: 0,
    consumableTotalAmount: 0,
    consumableSummaryRows: [],
    consumableItemChips: [],
    consumableItemCount: 0,
  }));
}

/** 按护士并行拉汇总，填充卡片数据；单个护士失败不影响其它卡片。 */
async function loadNurseCards(filter: NurseWorkloadQueryFilter) {
  const nurses = nurseOptions.value;
  if (nurses.length === 0) {
    nurseCards.value = [];
    return;
  }
  cardsLoading.value = true;
  cardsLoadProgress.value = `0 / ${nurses.length}`;
  let failedCount = 0;
  try {
    let finished = 0;
    const cards = await mapWithConcurrency(nurses, NURSE_SUMMARY_CONCURRENCY, async (nurse) => {
      try {
        const nurseFilter = {
          ...filter,
          practitionerId: preserveApiIdForRequest(nurse.value),
        };
        const [executionData, consumableData, consumableDetailRows] = await Promise.all([
          summarizeNurseExecutionWorkloadApi(nurseFilter),
          summarizeNurseConsumableWorkloadApi(nurseFilter),
          fetchAllConsumableDetailRows(nurseFilter),
        ]);
        const executionChips = buildExecutionItemChips(executionData?.rows ?? []);
        const consumableItemChips = aggregateConsumableDetailRows(consumableDetailRows);
        return {
          practitionerId: nurse.value,
          practitionerName: nurse.label,
          executionTotalCount: Number(executionData?.totalExecutionCount ?? 0),
          executionTotalAmount: executionData?.totalAmount ?? 0,
          executionSummaryRows: executionData?.rows ?? [],
          executionChips,
          consumableTotalQuantity: consumableData?.totalQuantity ?? 0,
          consumableTotalAmount: consumableData?.totalAmount ?? 0,
          consumableSummaryRows: consumableData?.rows ?? [],
          consumableItemChips,
          consumableItemCount: consumableItemChips.length,
        };
      } catch {
        failedCount += 1;
        return null;
      } finally {
        finished += 1;
        cardsLoadProgress.value = `${finished} / ${nurses.length}`;
      }
    });
    const validCards = cards.filter((card): card is NurseWorkloadCardView => card !== null);
    if (failedCount > 0) {
      ElMessage.warning(`${failedCount} 位护士统计加载失败，已跳过`);
    }
    // 有数据的护士排前，其余按姓名
    nurseCards.value = validCards.sort((left, right) => {
      const leftHasData = left.executionTotalCount > 0 || left.consumableItemCount > 0;
      const rightHasData = right.executionTotalCount > 0 || right.consumableItemCount > 0;
      if (leftHasData !== rightHasData) {
        if (leftHasData) {
          return -1;
        }
        return 1;
      }
      if (right.executionTotalCount !== left.executionTotalCount) {
        return right.executionTotalCount - left.executionTotalCount;
      }
      return left.practitionerName.localeCompare(right.practitionerName, 'zh-CN');
    });
  } finally {
    cardsLoading.value = false;
    cardsLoadProgress.value = '';
  }
}

/** 查询全院合计与各护士卡片。 */
async function handleQuery() {
  if (!queryParams.dateFrom || !queryParams.dateTo) {
    ElMessage.warning('请选择统计日期');
    return;
  }
  const filter = buildQueryFilter();
  nurseCards.value = [];
  await Promise.all([runOverallExecutionSummary(filter), runOverallConsumableSummary(filter)]);
  if (!hasOverallWorkloadData(overallExecutionTotal.value, overallConsumableTotal.value)) {
    nurseCards.value = buildEmptyNurseCards();
    return;
  }
  await loadNurseCards(filter);
}

/** 打开指定护士明细弹窗。 */
function openNurseDetail(card: NurseWorkloadCardView) {
  selectedNurse.value = card;
  detailDialogVisible.value = true;
}

watch(
  () => queryParams.encounterScope,
  () => {
    if (isInitializing.value) {
      return;
    }
    void handleQuery();
  },
);

onMounted(async () => {
  await runInit();
  isInitializing.value = false;
  if (queryParams.dateFrom && queryParams.dateTo) {
    await handleQuery();
  }
});
</script>

<template>
  <div class="nurse-workload-panel" :class="{ 'nurse-workload-panel--embedded': props.embedded }">
    <h2 v-if="!props.embedded" class="workload-panel__heading">护士工作量统计</h2>

    <ReportQueryBar :model="queryParams as Record<string, unknown>">
      <template #fields>
        <ElFormItem label="就诊范围">
          <ElRadioGroup v-model="queryParams.encounterScope">
            <ElRadioButton v-for="opt in encounterScopeOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </ElRadioButton>
            <ElRadioButton
              v-if="encounterScopeOptions.length === 0"
              :value="ENCOUNTER_SCOPE_OUTPATIENT"
            >
              门诊
            </ElRadioButton>
            <ElRadioButton
              v-if="encounterScopeOptions.length === 0"
              :value="ENCOUNTER_SCOPE_INPATIENT"
            >
              住院
            </ElRadioButton>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="统计日期">
          <ElDatePicker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clearable="false"
            :shortcuts="dateRangeShortcuts"
            :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATERANGE]"
            @change="handleQuery"
          />
        </ElFormItem>
      </template>
      <template #actions>
        <ElButton
          type="primary"
          :icon="Search"
          :loading="
            initLoading || overallExecutionLoading || overallConsumableLoading || cardsLoading
          "
          @click="handleQuery"
        >
          查询
        </ElButton>
      </template>
    </ReportQueryBar>

    <NurseWorkloadTotalsBar
      :execution-total-count="overallExecutionTotal"
      :consumable-total-quantity="overallConsumableTotal"
      :execution-total-amount="overallExecutionAmount"
      :consumable-total-amount="overallConsumableAmount"
      :nurse-count="nurseCountLabel"
    />

    <div class="nurse-workload-panel__list-wrap">
      <div class="nurse-workload-panel__list-head">
        <span class="workload-panel__heading">护士工作量明细</span>
        <span v-if="cardsLoadProgress" class="nurse-workload-panel__progress">
          加载中 {{ cardsLoadProgress }}
        </span>
      </div>

      <div v-loading="isListLoading" class="nurse-workload-panel__list-body">
        <ElEmpty v-if="!isListLoading && nurseCards.length === 0" description="当前暂无护士人员" />
        <NurseWorkloadNurseCard
          v-for="card in nurseCards"
          :key="card.practitionerId"
          :practitioner-name="card.practitionerName"
          :execution-total-count="card.executionTotalCount"
          :execution-total-amount="card.executionTotalAmount"
          :execution-chips="card.executionChips"
          :consumable-item-count="card.consumableItemCount"
          :consumable-total-amount="card.consumableTotalAmount"
          :consumable-item-chips="card.consumableItemChips"
          @view-detail="openNurseDetail(card)"
        />
      </div>
    </div>

    <NurseWorkloadNurseDetailDialog
      v-if="selectedNurse"
      v-model="detailDialogVisible"
      :base-filter="baseQueryFilter"
      :practitioner-id="selectedNurse.practitionerId"
      :practitioner-name="selectedNurse.practitionerName"
      :execution-total-count="selectedNurse.executionTotalCount"
      :execution-total-amount="selectedNurse.executionTotalAmount"
      :execution-summary-rows="selectedNurse.executionSummaryRows"
      :consumable-total-quantity="selectedNurse.consumableTotalQuantity"
      :consumable-total-amount="selectedNurse.consumableTotalAmount"
      :consumable-summary-rows="selectedNurse.consumableSummaryRows"
      :consumable-item-chips="selectedNurse.consumableItemChips"
      :consumable-item-count="selectedNurse.consumableItemCount"
    />
  </div>
</template>

<style scoped>
.nurse-workload-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: #fff;
}

.nurse-workload-panel--embedded {
  height: 100%;
  min-height: 0;
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  gap: 12px;
}

.workload-panel__heading {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--el-text-color-primary);
}

.nurse-workload-panel__list-wrap {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.nurse-workload-panel__list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
  padding-bottom: 8px;
}

.nurse-workload-panel__list-body {
  position: relative;
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  overflow: auto;
}

.nurse-workload-panel--embedded .nurse-workload-panel__list-body {
  min-height: 0;
  max-height: none;
}

.nurse-workload-panel__progress {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
