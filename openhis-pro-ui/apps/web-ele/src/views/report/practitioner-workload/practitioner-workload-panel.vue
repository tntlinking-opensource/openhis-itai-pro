<!--
  科室/医生工作量统计：默认展示科室内全部医生卡片；点击卡片查看明细；底部整体汇总。
-->
<script setup lang="ts">
import type {
  EncounterScope,
  PractitionerWorkloadFinTypeRowDto,
  PractitionerWorkloadQueryFilter,
  PractitionerWorkloadRegistrationRowDto,
  PractitionerWorkloadSummaryTotalsDto,
} from '#/api/report/practitioner-workload';
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
  loadPractitionerWorkloadPageInitApi,
  queryPractitionerWorkloadSummaryApi,
} from '#/api/report/practitioner-workload';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { optionDtosToSelectItems } from '#/views/report/shared/report-format';
import { buildReportDateRangeShortcuts } from '#/views/report/shared/report-date-range-shortcuts';
import {
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATERANGE,
  ReportQueryBar,
} from '#/views/report/shared';

import PractitionerWorkloadDoctorCard from './practitioner-workload-doctor-card.vue';
import PractitionerWorkloadDoctorDetailDialog from './practitioner-workload-doctor-detail-dialog.vue';
import PractitionerWorkloadTotalsBar from './practitioner-workload-totals-bar.vue';

defineOptions({ name: 'PractitionerWorkloadPanel' });

interface Props {
  /** 嵌入首页时压缩外边距。 */
  embedded?: boolean;
}

interface QueryState {
  encounterScope: EncounterScope;
  dateFrom: string;
  dateTo: string;
  organizationId?: string;
}

interface DoctorWorkloadCardView {
  finTypeRows: PractitionerWorkloadFinTypeRowDto[];
  practitionerId: string;
  practitionerName: string;
  registrationRows: PractitionerWorkloadRegistrationRowDto[];
  summaryTotals: PractitionerWorkloadSummaryTotalsDto;
}

const props = withDefaults(defineProps<Props>(), {
  embedded: false,
});

const DOCTOR_SUMMARY_CONCURRENCY = 3;

const encounterScopeOptions = ref<ApiOptionItem[]>([]);
const organizationOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const doctorCards = ref<DoctorWorkloadCardView[]>([]);
const summaryTotals = ref<PractitionerWorkloadSummaryTotalsDto>({});
const cardsLoading = ref(false);
const cardsLoadProgress = ref('');
const detailDialogVisible = ref(false);
const selectedDoctor = ref<DoctorWorkloadCardView | null>(null);

const queryParams = reactive<QueryState>({
  encounterScope: ENCOUNTER_SCOPE_OUTPATIENT,
  dateFrom: '',
  dateTo: '',
});

const ALL_ORG_VALUE = '__ALL__';

/** 是否已应用过后端返回的默认就诊范围（仅首次初始化）。 */
const hasAppliedInitialScope = ref(false);

/** 挂载初始化中：跳过科室/范围 watch，避免与 onMounted 竞态双发。 */
const isInitializing = ref(true);

const { run: runInit, loading: initLoading } = useRequest(loadPractitionerWorkloadPageInitApi, {
  manual: true,
  onSuccess: (data) => {
    encounterScopeOptions.value = optionDtosToSelectItems(data?.encounterScopeOptions);
    organizationOptions.value = optionDtosToSelectItems(data?.organizationOptions);
    practitionerOptions.value = optionDtosToSelectItems(data?.practitionerOptions);
    if (!hasAppliedInitialScope.value) {
      queryParams.encounterScope =
        (data?.defaultEncounterScope as EncounterScope) ?? ENCOUNTER_SCOPE_OUTPATIENT;
      hasAppliedInitialScope.value = true;
    }
    queryParams.dateFrom = data?.defaultDateFrom ?? queryParams.dateFrom;
    queryParams.dateTo = data?.defaultDateTo ?? queryParams.dateTo;
  },
});

const { run: runOverallSummary, loading: summaryLoading } = useRequest(
  queryPractitionerWorkloadSummaryApi,
  {
    manual: true,
    onSuccess: (data) => {
      summaryTotals.value = data?.summaryTotals ?? {};
    },
  },
);

defineExpose({
  summaryTotals,
});

const doctorCountLabel = computed(() => doctorCards.value.length);

const isListLoading = computed(
  () => initLoading.value || summaryLoading.value || cardsLoading.value,
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

const baseQueryFilter = computed<PractitionerWorkloadQueryFilter>(() => {
  const orgId = queryParams.organizationId;
  return {
    encounterScope: queryParams.encounterScope,
    dateFrom: queryParams.dateFrom,
    dateTo: queryParams.dateTo,
    organizationId: orgId && orgId !== ALL_ORG_VALUE ? preserveApiIdForRequest(orgId) : undefined,
  };
});

/** 组装查询筛选（不含医生）。 */
function buildQueryFilter(): PractitionerWorkloadQueryFilter {
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

/** 按医生并行拉汇总，填充卡片数据；单个医生失败不影响其它卡片。 */
async function loadDoctorCards(filter: PractitionerWorkloadQueryFilter) {
  const doctors = practitionerOptions.value;
  if (doctors.length === 0) {
    doctorCards.value = [];
    return;
  }
  cardsLoading.value = true;
  cardsLoadProgress.value = `0 / ${doctors.length}`;
  let failedCount = 0;
  try {
    let finished = 0;
    const cards = await mapWithConcurrency(doctors, DOCTOR_SUMMARY_CONCURRENCY, async (doctor) => {
      try {
        const data = await queryPractitionerWorkloadSummaryApi({
          ...filter,
          practitionerId: preserveApiIdForRequest(doctor.value),
        });
        return {
          practitionerId: doctor.value,
          practitionerName: doctor.label,
          summaryTotals: data?.summaryTotals ?? {},
          finTypeRows: data?.finTypeRows ?? [],
          registrationRows: data?.registrationRows ?? [],
        };
      } catch {
        failedCount += 1;
        return null;
      } finally {
        finished += 1;
        cardsLoadProgress.value = `${finished} / ${doctors.length}`;
      }
    });
    const validCards = cards.filter((card): card is DoctorWorkloadCardView => card !== null);
    if (failedCount > 0) {
      ElMessage.warning(`${failedCount} 位医生统计加载失败，已跳过`);
    }
    // 开单金额高的医生排前，便于快速浏览
    doctorCards.value = validCards.sort((left, right) => {
      const leftAmount = Number(left.summaryTotals.receivableAmountTotal ?? 0);
      const rightAmount = Number(right.summaryTotals.receivableAmountTotal ?? 0);
      if (rightAmount !== leftAmount) {
        return rightAmount - leftAmount;
      }
      return left.practitionerName.localeCompare(right.practitionerName, 'zh-CN');
    });
  } finally {
    cardsLoading.value = false;
    cardsLoadProgress.value = '';
  }
}

/** 重新加载科室/医生下拉（切换门诊住院时清空已选科室）。 */
async function reloadInitOptions(clearOrganization = false) {
  if (clearOrganization) {
    queryParams.organizationId = ALL_ORG_VALUE;
  }
  const orgId =
    queryParams.organizationId && queryParams.organizationId !== ALL_ORG_VALUE
      ? preserveApiIdForRequest(queryParams.organizationId)
      : undefined;
  await runInit({
    encounterScope: queryParams.encounterScope,
    organizationId: orgId,
  });
}

/** 判断整体合计是否存在有效数据。 */
function hasOverallWorkloadData(totals: PractitionerWorkloadSummaryTotalsDto) {
  return (
    Number(totals.encounterCountTotal ?? 0) !== 0 ||
    Number(totals.prescriptionCountTotal ?? 0) !== 0 ||
    Number(totals.activityItemCountTotal ?? 0) !== 0 ||
    Number(totals.receivableAmountTotal ?? 0) !== 0 ||
    Number(totals.registrationNetCountTotal ?? 0) !== 0
  );
}

/** 用医生下拉生成零值卡片（当日无结算数据时避免 N 次无效请求）。 */
function buildEmptyDoctorCards() {
  return practitionerOptions.value.map((doctor) => ({
    practitionerId: doctor.value,
    practitionerName: doctor.label,
    summaryTotals: {},
    finTypeRows: [],
    registrationRows: [],
  }));
}

/** 查询整体汇总与各医生卡片。 */
async function handleQuery() {
  if (!queryParams.dateFrom || !queryParams.dateTo) {
    ElMessage.warning('请选择统计日期');
    return;
  }
  const filter = buildQueryFilter();
  doctorCards.value = [];
  await runOverallSummary(filter);
  if (!hasOverallWorkloadData(summaryTotals.value)) {
    doctorCards.value = buildEmptyDoctorCards();
    return;
  }
  await loadDoctorCards(filter);
}

/** 打开指定医生明细弹窗。 */
function openDoctorDetail(card: DoctorWorkloadCardView) {
  selectedDoctor.value = card;
  detailDialogVisible.value = true;
}

watch(
  () => queryParams.encounterScope,
  () => {
    if (isInitializing.value) {
      return;
    }
    void reloadInitOptions(true).then(() => handleQuery());
  },
);

watch(
  () => queryParams.organizationId,
  (next) => {
    if (isInitializing.value) {
      return;
    }
    // clearable 清空时回落到「全部」，避免 null 请求
    if (!next) {
      queryParams.organizationId = ALL_ORG_VALUE;
      return;
    }
    void reloadInitOptions(false).then(() => handleQuery());
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
  <div
    class="practitioner-workload-panel"
    :class="{ 'practitioner-workload-panel--embedded': props.embedded }"
  >
    <h2 v-if="!props.embedded" class="workload-panel__heading">科室医生工作量统计</h2>

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
        <ElFormItem label="科室">
          <ElSelect
            v-model="queryParams.organizationId"
            clearable
            filterable
            placeholder="全部"
            :class="REPORT_QUERY_CONTROL"
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
          :loading="initLoading || summaryLoading || cardsLoading"
          @click="handleQuery"
        >
          查询
        </ElButton>
      </template>
    </ReportQueryBar>

    <PractitionerWorkloadTotalsBar
      :summary-totals="summaryTotals"
      :doctor-count="doctorCountLabel"
    />

    <div class="practitioner-workload-panel__list-wrap">
      <div class="practitioner-workload-panel__list-head">
        <span class="workload-panel__heading">医生工作量明细</span>
        <span v-if="cardsLoadProgress" class="practitioner-workload-panel__progress">
          加载中 {{ cardsLoadProgress }}
        </span>
      </div>

      <div v-loading="isListLoading" class="practitioner-workload-panel__cards">
        <ElEmpty
          v-if="!isListLoading && doctorCards.length === 0"
          description="当前科室暂无医生或暂无数据"
        />
        <PractitionerWorkloadDoctorCard
          v-for="card in doctorCards"
          :key="card.practitionerId"
          :practitioner-name="card.practitionerName"
          :summary-totals="card.summaryTotals"
          @click="openDoctorDetail(card)"
        />
      </div>
    </div>

    <PractitionerWorkloadDoctorDetailDialog
      v-if="selectedDoctor"
      v-model="detailDialogVisible"
      :base-filter="baseQueryFilter"
      :practitioner-id="selectedDoctor.practitionerId"
      :practitioner-name="selectedDoctor.practitionerName"
      :summary-totals="selectedDoctor.summaryTotals"
      :fin-type-rows="selectedDoctor.finTypeRows"
      :registration-rows="selectedDoctor.registrationRows"
    />
  </div>
</template>

<style scoped>
.practitioner-workload-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: #fff;
}

.practitioner-workload-panel--embedded {
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

.practitioner-workload-panel__list-wrap {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.practitioner-workload-panel__list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
  padding-bottom: 8px;
}

.practitioner-workload-panel__progress {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.practitioner-workload-panel__cards {
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  align-content: start;
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.practitioner-workload-panel--embedded .practitioner-workload-panel__cards {
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
}
</style>
