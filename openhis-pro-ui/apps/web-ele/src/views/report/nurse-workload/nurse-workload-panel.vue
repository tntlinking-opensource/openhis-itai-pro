<!--
  护士工作量统计：每位护士一行信息条，行内 chip 展示处置与耗材，无表头。
-->
<script setup lang="ts">
import type {
  EncounterScope,
  NurseConsumableSummaryRowDto,
  NurseExecutionSummaryRowDto,
  NurseWorkloadNurseCardDto,
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
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import {
  ENCOUNTER_SCOPE_INPATIENT,
  ENCOUNTER_SCOPE_OUTPATIENT,
  listNurseWorkloadNurseCardsApi,
  loadNurseWorkloadFormInitApi,
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
  /** 开立科室；UI 用 ALL_ORG_VALUE 表示全部。 */
  organizationId?: string;
}

interface NurseWorkloadCardView {
  consumableItemChips: NurseConsumableItemChip[];
  consumableItemCount: number;
  consumableSaleAmountTotal: number | string;
  consumablePurchaseAmountTotal?: number | string | null;
  consumableSummaryRows: NurseConsumableSummaryRowDto[];
  consumableTotalQuantity: number | string;
  executionChips: NurseExecutionItemChip[];
  executionSaleAmountTotal: number | string;
  executionPurchaseAmountTotal?: number | string | null;
  executionSummaryRows: NurseExecutionSummaryRowDto[];
  executionTotalCount: number;
  practitionerId: string;
  practitionerName: string;
}

const props = withDefaults(defineProps<Props>(), {
  embedded: false,
});

const encounterScopeOptions = ref<ApiOptionItem[]>([]);
const organizationOptions = ref<ApiOptionItem[]>([]);
const nurseOptions = ref<ApiOptionItem[]>([]);
const nurseCards = ref<NurseWorkloadCardView[]>([]);
const overallExecutionTotal = ref(0);
const overallConsumableTotal = ref<number | string>(0);
const overallExecutionSaleAmount = ref<number | string>(0);
const overallExecutionPurchaseAmount = ref<number | string | null>(null);
const overallConsumableSaleAmount = ref<number | string>(0);
const overallConsumablePurchaseAmount = ref<number | string | null>(null);
const cardsLoading = ref(false);
const detailDialogVisible = ref(false);
const selectedNurse = ref<NurseWorkloadCardView | null>(null);
const isInitializing = ref(true);

const queryParams = reactive<QueryState>({
  encounterScope: ENCOUNTER_SCOPE_OUTPATIENT,
  dateFrom: '',
  dateTo: '',
});

/** 开立科室「全部」哨兵值（与医生工作量同构）。 */
const ALL_ORG_VALUE = '__ALL__';

/** 是否已应用过后端返回的默认就诊范围（仅首次初始化）。 */
const hasAppliedInitialScope = ref(false);

const { run: runInit, loading: initLoading } = useRequest(loadNurseWorkloadFormInitApi, {
  manual: true,
  onSuccess: (data) => {
    encounterScopeOptions.value = optionDtosToSelectItems(data?.encounterScopeOptions);
    organizationOptions.value = optionDtosToSelectItems(data?.organizationOptions);
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
      overallExecutionSaleAmount.value = data?.totalSaleAmount ?? 0;
      overallExecutionPurchaseAmount.value = data?.totalPurchaseAmount ?? null;
    },
  },
);

const { run: runOverallConsumableSummary, loading: overallConsumableLoading } = useRequest(
  summarizeNurseConsumableWorkloadApi,
  {
    manual: true,
    onSuccess: (data) => {
      overallConsumableTotal.value = data?.totalQuantity ?? 0;
      overallConsumableSaleAmount.value = data?.totalSaleAmount ?? 0;
      overallConsumablePurchaseAmount.value = data?.totalPurchaseAmount ?? null;
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

const baseQueryFilter = computed<NurseWorkloadQueryFilter>(() => {
  const orgId = queryParams.organizationId;
  // 「全部」或空值不传开立科室，避免哨兵串进后端 Long
  const resolvedOrgId =
    orgId && orgId !== ALL_ORG_VALUE ? preserveApiIdForRequest(orgId) : undefined;
  return {
    encounterScope: queryParams.encounterScope,
    dateFrom: queryParams.dateFrom,
    dateTo: queryParams.dateTo,
    requestingOrgId: resolvedOrgId,
    organizationId: resolvedOrgId,
  };
});

/** 组装查询筛选（不含护士）。 */
function buildQueryFilter(): NurseWorkloadQueryFilter {
  return { ...baseQueryFilter.value };
}

/**
 * 重新加载下拉（切换门诊/住院时清空已选开立科室）。
 * @param clearOrganization 是否重置科室为「全部」
 */
async function reloadInitOptions(clearOrganization = false) {
  if (clearOrganization) {
    queryParams.organizationId = ALL_ORG_VALUE;
  }
  await runInit({
    encounterScope: queryParams.encounterScope,
  });
}

/** 开立科室变更：clearable 清空时回落「全部」再查询。 */
function handleOrganizationChange(value?: string) {
  if (!value) {
    queryParams.organizationId = ALL_ORG_VALUE;
  }
  void handleQuery();
}

/** 判断全院合计是否存在有效数据。 */
function hasOverallWorkloadData(executionTotal: number, consumableTotal: number | string) {
  return executionTotal !== 0 || Number(consumableTotal) !== 0;
}

/** 用护士下拉生成零值行（无数据时仍展示全部护士）。 */
function buildEmptyNurseCards() {
  return nurseOptions.value.map((nurse) => ({
    practitionerId: nurse.value,
    practitionerName: nurse.label,
    executionTotalCount: 0,
    executionSaleAmountTotal: 0,
    executionPurchaseAmountTotal: null,
    executionSummaryRows: [],
    executionChips: [],
    consumableTotalQuantity: 0,
    consumableSaleAmountTotal: 0,
    consumablePurchaseAmountTotal: null,
    consumableSummaryRows: [],
    consumableItemChips: [],
    consumableItemCount: 0,
  }));
}

/** 将后端卡片 DTO 映射为面板视图（补齐姓名与 chip）。 */
function mapNurseCardView(
  card: NurseWorkloadNurseCardDto,
  practitionerName: string,
): NurseWorkloadCardView {
  const executionSummaryRows = card.executionSummaryRows ?? [];
  const consumableItemChips: NurseConsumableItemChip[] = (card.consumableItemChips ?? []).map(
    (chip) => ({
      key: chip.key ?? '',
      label: chip.label ?? '未知耗材',
      quantity: Number(chip.quantity ?? 0),
    }),
  );
  return {
    practitionerId: preserveApiIdForRequest(card.practitionerId),
    practitionerName,
    executionTotalCount: Number(card.executionTotalCount ?? 0),
    executionSaleAmountTotal: card.executionSaleAmountTotal ?? 0,
    executionPurchaseAmountTotal: card.executionPurchaseAmountTotal ?? null,
    executionSummaryRows,
    executionChips: buildExecutionItemChips(executionSummaryRows),
    consumableTotalQuantity: card.consumableTotalQuantity ?? 0,
    consumableSaleAmountTotal: card.consumableSaleAmountTotal ?? 0,
    consumablePurchaseAmountTotal: card.consumablePurchaseAmountTotal ?? null,
    consumableSummaryRows: card.consumableSummaryRows ?? [],
    consumableItemChips,
    consumableItemCount: consumableItemChips.length,
  };
}

/**
 * 一次批量拉护士卡片；与下拉并集合并零值行。
 * 禁止前端翻页全量明细后再分桶。
 */
async function loadNurseCards(filter: NurseWorkloadQueryFilter) {
  const nurses = nurseOptions.value;
  if (nurses.length === 0) {
    nurseCards.value = [];
    return;
  }
  cardsLoading.value = true;
  try {
    const data = await listNurseWorkloadNurseCardsApi(filter);
    // 有数据的护士 id → 卡片
    const cardByNurseId = new Map<string, NurseWorkloadCardView>();
    for (const card of data?.cards ?? []) {
      const nurseId = preserveApiIdForRequest(card.practitionerId);
      if (!nurseId) {
        continue;
      }
      const nurseName = nurses.find((nurse) => String(nurse.value) === nurseId)?.label ?? nurseId;
      cardByNurseId.set(nurseId, mapNurseCardView(card, nurseName));
    }
    // 下拉全员展示：无命中补零值卡
    const cards: NurseWorkloadCardView[] = nurses.map((nurse) => {
      const nurseKey = String(nurse.value);
      return (
        cardByNurseId.get(nurseKey) ?? {
          practitionerId: nurse.value,
          practitionerName: nurse.label,
          executionTotalCount: 0,
          executionSaleAmountTotal: 0,
          executionPurchaseAmountTotal: null,
          executionSummaryRows: [],
          executionChips: [],
          consumableTotalQuantity: 0,
          consumableSaleAmountTotal: 0,
          consumablePurchaseAmountTotal: null,
          consumableSummaryRows: [],
          consumableItemChips: [],
          consumableItemCount: 0,
        }
      );
    });
    // 有数据的护士排前，其余按姓名
    nurseCards.value = cards.sort((left, right) => {
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
    // 切换门诊/住院：科室下拉口径变化，重置为「全部」后重查
    void reloadInitOptions(true).then(() => handleQuery());
  },
);

onMounted(async () => {
  queryParams.organizationId = ALL_ORG_VALUE;
  await reloadInitOptions(false);
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
        <ElFormItem label="开立科室">
          <ElSelect
            v-model="queryParams.organizationId"
            clearable
            filterable
            placeholder="全部"
            :class="REPORT_QUERY_CONTROL"
            @change="handleOrganizationChange"
          >
            <ElOption label="全部" :value="ALL_ORG_VALUE" />
            <ElOption
              v-for="opt in organizationOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
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
      :execution-total-sale-amount="overallExecutionSaleAmount"
      :execution-total-purchase-amount="overallExecutionPurchaseAmount"
      :consumable-total-sale-amount="overallConsumableSaleAmount"
      :consumable-total-purchase-amount="overallConsumablePurchaseAmount"
      :nurse-count="nurseCountLabel"
    />

    <div class="nurse-workload-panel__list-wrap">
      <div class="nurse-workload-panel__list-head">
        <span class="workload-panel__heading">护士工作量明细</span>
      </div>

      <div v-loading="isListLoading" class="nurse-workload-panel__list-body">
        <ElEmpty v-if="!isListLoading && nurseCards.length === 0" description="当前暂无护士人员" />
        <NurseWorkloadNurseCard
          v-for="card in nurseCards"
          :key="card.practitionerId"
          :practitioner-name="card.practitionerName"
          :execution-total-count="card.executionTotalCount"
          :execution-sale-amount-total="card.executionSaleAmountTotal"
          :execution-chips="card.executionChips"
          :consumable-item-count="card.consumableItemCount"
          :consumable-sale-amount-total="card.consumableSaleAmountTotal"
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
      :execution-sale-amount-total="selectedNurse.executionSaleAmountTotal"
      :execution-purchase-amount-total="selectedNurse.executionPurchaseAmountTotal"
      :execution-summary-rows="selectedNurse.executionSummaryRows"
      :consumable-total-quantity="selectedNurse.consumableTotalQuantity"
      :consumable-sale-amount-total="selectedNurse.consumableSaleAmountTotal"
      :consumable-purchase-amount-total="selectedNurse.consumablePurchaseAmountTotal"
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
</style>
