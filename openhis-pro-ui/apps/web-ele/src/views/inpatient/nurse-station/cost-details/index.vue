<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';

import type {
  NurseStationCostDetailLineDto,
  NurseStationCostDetailPresentationMode,
  NurseStationCostDetailPresentationOptionDto,
  NurseStationCostDetailStatusFilter,
  NurseStationCostDetailTotalsDto,
  NurseStationCostSummaryLineDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-cost-detail.types';

import { computed, onMounted, ref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';

import {
  httpLoadNurseStationCostDetailPageInit,
  httpQueryNurseStationInpatientCostDetail,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-cost-detail.http';
import InpatientPatientInfoHeader from '#/components/inpatient/patient-info-header.vue';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import CostDetailPanel from './components/cost-detail-panel.vue';
import {
  buildNurseStationStickyPatientInfoFromModulePatient,
  buildNurseStationStickyPatientInfoPlaceholder,
} from '../components/build-nurse-station-sticky-patient-info';
import {
  isCostDetailPresentationMode,
  isCostDetailStatusFilter,
} from './services/map-nurse-station-cost-detail';

type CostDetailQuickDay = 'today' | 'yesterday';

interface Props {
  patients?: NurseStationModulePatient[];
}

defineOptions({
  name: 'InpatientNurseStationCostDetailsModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

const DEFAULT_PRESENTATION_OPTIONS: NurseStationCostDetailPresentationOptionDto[] = [
  { code: 'DETAIL', display: '明细' },
  { code: 'SUMMARY', display: '汇总' },
];

const DEFAULT_STATUS_FILTER_OPTIONS: NurseStationCostDetailPresentationOptionDto[] = [
  { code: 'PENDING_SETTLEMENT', display: '待结算' },
  { code: 'SETTLED', display: '已结算' },
];

const DEFAULT_STATUS_FILTER: NurseStationCostDetailStatusFilter = 'PENDING_SETTLEMENT';

const costDetailQuickDay = ref<CostDetailQuickDay>('today');
const costDetailDateRange = ref<string[]>([]);
const costDetailStatusEnum = ref<NurseStationCostDetailStatusFilter>(DEFAULT_STATUS_FILTER);
const presentationMode = ref<NurseStationCostDetailPresentationMode>('DETAIL');
const presentationOptions = ref<NurseStationCostDetailPresentationOptionDto[]>(
  DEFAULT_PRESENTATION_OPTIONS,
);
const statusFilterOptions = ref<NurseStationCostDetailPresentationOptionDto[]>(
  DEFAULT_STATUS_FILTER_OPTIONS,
);
const detailLines = ref<NurseStationCostDetailLineDto[]>([]);
const summaryLines = ref<NurseStationCostSummaryLineDto[]>([]);
const totals = ref<NurseStationCostDetailTotalsDto | null>(null);
const pageInitialized = ref(false);

const initRequest = useRequest(httpLoadNurseStationCostDetailPageInit, { manual: true });
const queryRequest = useRequest(httpQueryNurseStationInpatientCostDetail, { manual: true });

/** 费用明细要求左侧患者单选。 */
const canOperateCostDetail = computed(() => props.patients.length === 1);

/** 当前选中就诊与患者主键（单选场景）。 */
const selectedPatientContext = computed(() => {
  if (!canOperateCostDetail.value) {
    return null;
  }
  const patient = props.patients[0];
  if (!patient) {
    return null;
  }
  const encounterId = preserveApiIdForRequest(patient.id);
  const patientId = preserveApiIdForRequest(patient.patientId ?? patient.id);
  if (!encounterId || !patientId) {
    return null;
  }
  return { encounterId, patientId, patient };
});

/** 页面顶部吸顶患者信息。 */
const stickyPatientInfo = computed(() => {
  if (props.patients.length === 0) {
    return null;
  }
  if (!canOperateCostDetail.value) {
    return buildNurseStationStickyPatientInfoPlaceholder({
      diagnosis: `当前已选 ${props.patients.length} 位患者（请单选后查看费用明细）`,
      name: '请选择单一患者',
    });
  }
  const patient = props.patients[0];
  if (!patient) {
    return null;
  }
  return buildNurseStationStickyPatientInfoFromModulePatient(patient);
});

/** 将服务端默认日期与费用状态筛选写入查询条件。 */
function applyPageInitDefaults(dateFrom?: string, dateTo?: string, defaultStatusEnum?: string) {
  const from = dateFrom?.trim();
  const to = dateTo?.trim();
  if (from && to) {
    costDetailDateRange.value = [from, to];
  } else {
    const today = dayjs().format('YYYY-MM-DD');
    costDetailDateRange.value = [today, today];
  }
  const statusCode = defaultStatusEnum?.trim();
  if (isCostDetailStatusFilter(statusCode)) {
    costDetailStatusEnum.value = statusCode;
  } else {
    costDetailStatusEnum.value = DEFAULT_STATUS_FILTER;
  }
}

/** 加载费用明细页默认配置。 */
async function loadPageInit() {
  const data = await initRequest.run();
  presentationOptions.value =
    data.presentationOptions?.length > 0 ? data.presentationOptions : DEFAULT_PRESENTATION_OPTIONS;
  statusFilterOptions.value =
    data.statusFilterOptions?.length > 0 ? data.statusFilterOptions : DEFAULT_STATUS_FILTER_OPTIONS;
  applyPageInitDefaults(data.dateFrom, data.dateTo, data.defaultStatusEnum);
  pageInitialized.value = true;
}

/** 按快捷日切换日期区间。 */
function handleCostDetailQuickDayChange(day: CostDetailQuickDay) {
  costDetailQuickDay.value = day;
  const targetDay = day === 'today' ? dayjs() : dayjs().subtract(1, 'day');
  const dateText = targetDay.format('YYYY-MM-DD');
  costDetailDateRange.value = [dateText, dateText];
}

/** 切换明细/汇总展示模式后重新查询。 */
function handlePresentationModeChange(mode: NurseStationCostDetailPresentationMode) {
  if (presentationMode.value === mode) {
    return;
  }
  presentationMode.value = mode;
  void handleCostDetailQuery();
}

/** 重置筛选条件为今日待结算。 */
function handleCostDetailReset() {
  handleCostDetailQuickDayChange('today');
  costDetailStatusEnum.value = DEFAULT_STATUS_FILTER;
}

/** 校验查询入参是否完整。 */
function resolveQueryPayload() {
  const context = selectedPatientContext.value;
  if (!context) {
    ElMessage.warning('费用明细仅支持单选患者，请在左侧只勾选 1 位患者');
    return null;
  }
  const [dateFrom, dateTo] = costDetailDateRange.value;
  if (!dateFrom || !dateTo) {
    ElMessage.warning('请选择有效的查询日期');
    return null;
  }
  if (dayjs(dateFrom).isAfter(dayjs(dateTo))) {
    ElMessage.warning('请选择有效的查询日期');
    return null;
  }
  if (!isCostDetailPresentationMode(presentationMode.value)) {
    ElMessage.warning('查询参数错误，请刷新页面');
    return null;
  }
  if (!isCostDetailStatusFilter(costDetailStatusEnum.value)) {
    ElMessage.warning('查询参数错误，请刷新页面');
    return null;
  }
  return {
    encounterId: context.encounterId,
    patientId: context.patientId,
    dateFrom,
    dateTo,
    presentationMode: presentationMode.value,
    statusEnum: costDetailStatusEnum.value,
  };
}

/** 查询住院费用明细或汇总。 */
async function handleCostDetailQuery() {
  if (!pageInitialized.value) {
    await loadPageInit();
  }
  const payload = resolveQueryPayload();
  if (!payload) {
    return;
  }
  const data = await queryRequest.run(payload);
  detailLines.value = data.detailLines ?? [];
  summaryLines.value = data.summaryLines ?? [];
  totals.value = data.totals ?? null;
  if (isCostDetailPresentationMode(data.presentationMode)) {
    presentationMode.value = data.presentationMode;
  }
}

/** 左侧患者切换为单选时自动刷新费用数据。 */
watch(
  () => selectedPatientContext.value?.encounterId,
  (encounterId, prevEncounterId) => {
    if (!encounterId) {
      detailLines.value = [];
      summaryLines.value = [];
      totals.value = null;
      return;
    }
    if (encounterId === prevEncounterId) {
      return;
    }
    void handleCostDetailQuery();
  },
);

onMounted(async () => {
  await loadPageInit();
  if (selectedPatientContext.value) {
    await handleCostDetailQuery();
  }
});
</script>

<template>
  <div class="cost-details-page flex min-h-0 flex-1 flex-col">
    <InpatientPatientInfoHeader :patient-info="stickyPatientInfo" />
    <CostDetailPanel
      v-model:date-range="costDetailDateRange"
      v-model:status-enum="costDetailStatusEnum"
      :detail-lines="detailLines"
      :loading="queryRequest.loading.value || initRequest.loading.value"
      :presentation-mode="presentationMode"
      :presentation-options="presentationOptions"
      :quick-day="costDetailQuickDay"
      :status-filter-options="statusFilterOptions"
      :summary-lines="summaryLines"
      :totals="totals"
      @on-presentation-mode-change="handlePresentationModeChange"
      @on-query="handleCostDetailQuery"
      @on-quick-day-change="handleCostDetailQuickDayChange"
      @on-reset="handleCostDetailReset"
    />
  </div>
</template>

<style scoped>
.cost-details-page {
  background: #f3f5f8;
  gap: 6px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 0 2px 6px;
}

.cost-details-page :deep(.inpatient-patient-info-header) {
  margin-bottom: 0;
}
</style>
