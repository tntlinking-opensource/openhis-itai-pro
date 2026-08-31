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

import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElButton, ElMessage } from 'element-plus';

import {
  httpLoadNurseStationCostDetailPageInit,
  httpQueryNurseStationInpatientCostDetail,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-cost-detail.http';
import type {
  InpatientSettleChargeLineDto,
  PreSettleInpatientChargesResponse,
} from '#/api/inpatient-settle';
import DetailAuditRemindDialog from '#/chs/components/detail-audit-remind-dialog.vue';
import InpatientClinicalPreSettlePreviewDialog from '#/chs/components/inpatient-clinical-pre-settle-preview-dialog.vue';
import InsuranceProgressDialog from '#/chs/components/insurance-progress-dialog.vue';
import type { DetailAuditRemindDialogExpose } from '#/chs/composables/use-detail-audit-gate';
import {
  buildClinicalPreSettleInsuranceBreakdown,
  resolveClinicalPreSettlePrepayOffset,
  runInpatientClinicalPreSettle,
} from '#/chs/composables/use-inpatient-clinical-pre-settle';
import { useInsuranceProgressDialog } from '#/chs/composables/use-insurance-progress-dialog';
import InpatientPatientInfoHeader from '#/components/inpatient/patient-info-header.vue';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import CostDetailPanel from './components/cost-detail-panel.vue';
import {
  buildNurseStationStickyPatientInfoFromModulePatient,
  buildNurseStationStickyPatientInfoPlaceholder,
} from '../components/build-nurse-station-sticky-patient-info';
import { loadPrintPendingRows } from '../print-center/load-print-pending';
import { printInpatientPrintCenter } from '../print-center/print-center-print';
import {
  isCostDetailPresentationMode,
  isCostDetailStatusFilter,
} from './services/map-nurse-station-cost-detail';

/** 快捷日；空串表示用户自选区间，打印/回页时不得覆盖 dateRange */
type CostDetailQuickDay = '' | 'today' | 'yesterday';

interface Props {
  patients?: NurseStationModulePatient[];
  /** 隐藏顶部吸顶患者信息（医生站外层已有患者栏时用） */
  hidePatientHeader?: boolean;
  /**
   * 外部首选日期起（yyyy-MM-dd）；病案等场景按入院日打开。
   * 有值时覆盖页 init 默认日期。
   */
  preferredDateFrom?: string;
  /** 外部首选日期止（yyyy-MM-dd）；出院日或今日。 */
  preferredDateTo?: string;
  /** 外部首选费用状态；已结算患者建议传 SETTLED。 */
  preferredStatusEnum?: NurseStationCostDetailStatusFilter;
  /** 隐藏「完整预结算」工具条（病案费用清单等只读场景） */
  hideClinicalPreSettle?: boolean;
}

defineOptions({
  name: 'InpatientNurseStationCostDetailsModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
  hidePatientHeader: false,
  preferredDateFrom: undefined,
  preferredDateTo: undefined,
  preferredStatusEnum: undefined,
  hideClinicalPreSettle: false,
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

const costDetailQuickDay = ref<CostDetailQuickDay>('');
const costDetailDateRange = ref<string[]>([]);
const costDetailKeyword = ref('');
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
/** 费用清单打印中 */
const printingFeeList = ref(false);
/** 费用明细单打印中 */
const printingFeeDetail = ref(false);

const detailAuditDialogRef = ref<DetailAuditRemindDialogExpose | null>(null);
const clinicalPreSettleLoading = ref(false);
const {
  activeIndex: clinicalPreSettleProgressActiveIndex,
  countCurrent: clinicalPreSettleProgressCountCurrent,
  countTotal: clinicalPreSettleProgressCountTotal,
  detail: clinicalPreSettleProgressDetail,
  progress: clinicalPreSettleProgress,
  steps: clinicalPreSettleProgressSteps,
  title: clinicalPreSettleProgressTitle,
  visible: clinicalPreSettleProgressVisible,
} = useInsuranceProgressDialog();
const clinicalPreSettleVisible = ref(false);
const clinicalPreSettleSnapshot = ref<PreSettleInpatientChargesResponse | null>(null);
const clinicalPreSettleChargeLines = ref<InpatientSettleChargeLineDto[]>([]);

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

/**
 * 按当前 dateRange 回写快捷日高亮：仅单日且等于今日/昨日时点亮，否则清空。
 * 避免入院～出院等自选区间仍挂着「今日」，导致打印前被强行盖回当天。
 */
function syncQuickDayFromDateRange() {
  const [from, to] = costDetailDateRange.value;
  if (!from || !to || from !== to) {
    costDetailQuickDay.value = '';
    return;
  }
  const today = dayjs().format('YYYY-MM-DD');
  const yesterday = dayjs().subtract(1, 'day').format('YYYY-MM-DD');
  if (from === today) {
    costDetailQuickDay.value = 'today';
    return;
  }
  if (from === yesterday) {
    costDetailQuickDay.value = 'yesterday';
    return;
  }
  costDetailQuickDay.value = '';
}

/** 将服务端默认日期与费用状态筛选写入查询条件。 */
function applyPageInitDefaults(dateFrom?: string, dateTo?: string, defaultStatusEnum?: string) {
  const preferredFrom = props.preferredDateFrom?.trim().slice(0, 10);
  const preferredTo = props.preferredDateTo?.trim().slice(0, 10);
  // 病案等外嵌场景：优先入院～出院日，避免默认「今日」查空
  if (preferredFrom && preferredTo) {
    costDetailDateRange.value = [preferredFrom, preferredTo];
  } else {
    const from = dateFrom?.trim();
    const to = dateTo?.trim();
    if (from && to) {
      costDetailDateRange.value = [from, to];
    } else {
      const today = dayjs().format('YYYY-MM-DD');
      costDetailDateRange.value = [today, today];
    }
  }
  syncQuickDayFromDateRange();
  const preferredStatus = props.preferredStatusEnum?.trim();
  if (isCostDetailStatusFilter(preferredStatus)) {
    costDetailStatusEnum.value = preferredStatus;
    return;
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
  if (day !== 'today' && day !== 'yesterday') {
    return;
  }
  costDetailQuickDay.value = day;
  const targetDay = day === 'today' ? dayjs() : dayjs().subtract(1, 'day');
  const dateText = targetDay.format('YYYY-MM-DD');
  costDetailDateRange.value = [dateText, dateText];
}

/** 用户改日期区间：清掉「今日/昨日」高亮，打印按所选区间走。 */
function handleCostDetailDateRangeChange(value: string[] | null) {
  costDetailDateRange.value = Array.isArray(value) ? value : [];
  syncQuickDayFromDateRange();
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
  costDetailKeyword.value = '';
}

/**
 * 打印前对齐日期：仅快捷「今日/昨日」按当前日历重算；
 * 自选区间（quickDay 为空）原样保留，禁止盖回当天。
 */
function syncCostDetailQuickDayRangeForPrint() {
  if (costDetailQuickDay.value === 'today' || costDetailQuickDay.value === 'yesterday') {
    handleCostDetailQuickDayChange(costDetailQuickDay.value);
  }
}

/** 解析当前可打印的日期区间；非法则返回 null */
function resolvePrintDateRange(): null | { dateFrom: string; dateTo: string } {
  syncCostDetailQuickDayRangeForPrint();
  const [dateFrom, dateTo] = costDetailDateRange.value;
  if (!dateFrom || !dateTo) {
    ElMessage.warning('请选择有效的查询日期');
    return null;
  }
  if (dayjs(dateFrom).isAfter(dayjs(dateTo))) {
    ElMessage.warning('请选择有效的查询日期');
    return null;
  }
  return { dateFrom, dateTo };
}

/** 按当前日期区间打印住院费用清单 */
async function handlePrintFeeList() {
  if (!canOperateCostDetail.value || props.patients.length !== 1) {
    ElMessage.warning('费用清单仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  const range = resolvePrintDateRange();
  if (!range) {
    return;
  }
  const { dateFrom, dateTo } = range;
  if (printingFeeList.value) {
    return;
  }
  printingFeeList.value = true;
  try {
    // 打印前先刷屏内明细，再按同条件拉最新待打/打印数据（避免挂起页脏数据）
    await handleCostDetailQuery();
    const selectedRows = await loadPrintPendingRows({
      docType: 'fee_list',
      patients: props.patients,
      dateFrom,
      dateTo,
      executionDeadline: `${dateTo} 23:59:59`,
      costStatusEnum: costDetailStatusEnum.value,
    });
    if (selectedRows.length <= 0) {
      ElMessage.warning('暂无「费用清单」可打印数据');
      return;
    }
    const result = await printInpatientPrintCenter({
      docType: 'fee_list',
      patients: props.patients,
      selectedRows,
      dateFrom,
      dateTo,
      executionDeadline: `${dateTo} 23:59:59`,
      costStatusEnum: costDetailStatusEnum.value,
    });
    if (result.ok) {
      ElMessage.success(`已发送打印：费用清单，共 ${result.count} 份`);
    }
  } finally {
    printingFeeList.value = false;
  }
}

/** 按当前日期区间打印住院费用明细单（DETAIL 流水，按分类分组，无分类合计） */
async function handlePrintFeeDetail() {
  if (!canOperateCostDetail.value || props.patients.length !== 1) {
    ElMessage.warning('费用明细单仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  const range = resolvePrintDateRange();
  if (!range) {
    return;
  }
  const { dateFrom, dateTo } = range;
  if (printingFeeDetail.value) {
    return;
  }
  printingFeeDetail.value = true;
  try {
    await handleCostDetailQuery();
    const selectedRows = await loadPrintPendingRows({
      docType: 'fee_detail',
      patients: props.patients,
      dateFrom,
      dateTo,
      executionDeadline: `${dateTo} 23:59:59`,
      costStatusEnum: costDetailStatusEnum.value,
    });
    if (selectedRows.length <= 0) {
      ElMessage.warning('暂无「费用明细单」可打印数据');
      return;
    }
    const result = await printInpatientPrintCenter({
      docType: 'fee_detail',
      patients: props.patients,
      selectedRows,
      dateFrom,
      dateTo,
      executionDeadline: `${dateTo} 23:59:59`,
      costStatusEnum: costDetailStatusEnum.value,
    });
    if (result.ok) {
      ElMessage.success(`已发送打印：费用明细单，共 ${result.count} 份`);
    }
  } finally {
    printingFeeDetail.value = false;
  }
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
  // 关键字 trim 后空串不传，避免后端无意义过滤
  const keyword = costDetailKeyword.value.trim();
  return {
    encounterId: context.encounterId,
    patientId: context.patientId,
    dateFrom,
    dateTo,
    presentationMode: presentationMode.value,
    statusEnum: costDetailStatusEnum.value,
    keyword: keyword || undefined,
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
    // 挂载首帧由 onMounted 查询，避免与 watch 重复弹错
    if (prevEncounterId === undefined) {
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
  document.addEventListener('visibilitychange', handleCostDetailVisibilityChange);
});

onUnmounted(() => {
  document.removeEventListener('visibilitychange', handleCostDetailVisibilityChange);
});

/** 页签重新可见时：快捷日对齐日历并静默重查，降低挂起页脏数据概率 */
function handleCostDetailVisibilityChange() {
  if (document.visibilityState !== 'visible') {
    return;
  }
  if (!pageInitialized.value || !selectedPatientContext.value) {
    return;
  }
  if (costDetailQuickDay.value === 'today' || costDetailQuickDay.value === 'yesterday') {
    handleCostDetailQuickDayChange(costDetailQuickDay.value);
  }
  void handleCostDetailQuery();
}

/** 完整预结算：3101→2303 仅展示金额 */
async function handleClinicalPreSettle() {
  const context = selectedPatientContext.value;
  if (!context) {
    ElMessage.warning('费用明细仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  clinicalPreSettleLoading.value = true;
  let openPreviewAfterProgress = false;
  try {
    const result = await runInpatientClinicalPreSettle({
      params: {
        encounterId: context.encounterId,
        patientId: context.patientId,
        encounterBusNo: context.patient.encounterBusNo,
        statusEnum: context.patient.statusEnum,
        contractDisplay: context.patient.feeType,
      },
      dialogRef: detailAuditDialogRef,
      progress: clinicalPreSettleProgress,
    });
    if (!result) {
      return;
    }
    clinicalPreSettleSnapshot.value = result.snapshot;
    clinicalPreSettleChargeLines.value = result.chargeLines;
    openPreviewAfterProgress = true;
  } catch (error) {
    const message = error instanceof Error ? error.message : '完整预结算失败';
    ElMessage.error(message);
  } finally {
    clinicalPreSettleLoading.value = false;
  }
  if (openPreviewAfterProgress) {
    await nextTick();
    clinicalPreSettleVisible.value = true;
  }
}

const clinicalPreSettleInsuranceBreakdown = computed(() =>
  buildClinicalPreSettleInsuranceBreakdown(clinicalPreSettleSnapshot.value),
);

/** 本次预交金冲抵额 O（后端 depositOffsetAmount）。 */
const clinicalPreSettlePrepayOffset = computed(() =>
  resolveClinicalPreSettlePrepayOffset(clinicalPreSettleSnapshot.value),
);
</script>

<template>
  <div class="cost-details-page flex min-h-0 flex-1 flex-col">
    <InpatientPatientInfoHeader v-if="!hidePatientHeader" :patient-info="stickyPatientInfo" />
    <div v-if="!hideClinicalPreSettle" class="cost-details-toolbar">
      <ElButton
        type="primary"
        :disabled="!canOperateCostDetail"
        :loading="clinicalPreSettleLoading"
        @click="handleClinicalPreSettle"
      >
        完整预结算
      </ElButton>
      <span class="cost-details-toolbar__hint">
        可随时试算医保金额（3101→2303），不落账；正式结算仍须护士出院后到收费处办理
      </span>
    </div>
    <CostDetailPanel
      v-model:keyword="costDetailKeyword"
      v-model:status-enum="costDetailStatusEnum"
      :date-range="costDetailDateRange"
      :detail-lines="detailLines"
      :loading="queryRequest.loading.value || initRequest.loading.value"
      :printing="printingFeeList"
      :printing-detail="printingFeeDetail"
      :presentation-mode="presentationMode"
      :presentation-options="presentationOptions"
      :quick-day="costDetailQuickDay"
      :status-filter-options="statusFilterOptions"
      :summary-lines="summaryLines"
      :totals="totals"
      @update:date-range="handleCostDetailDateRangeChange"
      @on-presentation-mode-change="handlePresentationModeChange"
      @on-print="handlePrintFeeList"
      @on-print-detail="handlePrintFeeDetail"
      @on-query="handleCostDetailQuery"
      @on-quick-day-change="handleCostDetailQuickDayChange"
      @on-reset="handleCostDetailReset"
    />

    <DetailAuditRemindDialog ref="detailAuditDialogRef" warn-type="1" />
    <InsuranceProgressDialog
      v-model="clinicalPreSettleProgressVisible"
      :title="clinicalPreSettleProgressTitle"
      :steps="clinicalPreSettleProgressSteps"
      :active-index="clinicalPreSettleProgressActiveIndex"
      :detail="clinicalPreSettleProgressDetail"
      :count-current="clinicalPreSettleProgressCountCurrent"
      :count-total="clinicalPreSettleProgressCountTotal"
    />
    <InpatientClinicalPreSettlePreviewDialog
      v-model="clinicalPreSettleVisible"
      :display-amount="clinicalPreSettleSnapshot?.displayAmount ?? 0"
      :receivable-total="clinicalPreSettleSnapshot?.receivableTotal ?? 0"
      :prepay-net-balance="clinicalPreSettleSnapshot?.prepayNetBalance"
      :prepay-offset-amount="clinicalPreSettlePrepayOffset"
      :prepay-refund-amount="clinicalPreSettleSnapshot?.prepayRefundAmount"
      :prepay-full-refund="Boolean(clinicalPreSettleSnapshot?.prepayFullRefund)"
      :preview-text="clinicalPreSettleSnapshot?.previewText"
      :insurance-pre-settled="clinicalPreSettleSnapshot?.insurancePreSettled"
      :insurance-breakdown-items="clinicalPreSettleInsuranceBreakdown"
      :charge-lines="clinicalPreSettleChargeLines"
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

.cost-details-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 8px 12px 0;
}

.cost-details-toolbar__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
