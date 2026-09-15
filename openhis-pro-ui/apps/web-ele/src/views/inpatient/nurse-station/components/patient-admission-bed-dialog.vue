<script setup lang="ts">
import type {
  AdmissionVitalSignSubmitDto,
  OptionDto,
  PatientDetailDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station.types';
import type { AdmissionBedOperationIntent } from '../services/admission-bed-operation';
import type { NurseStationModulePatient } from './nurse-station-module-patient.types';
import type { PatientItem } from '../types';

import { computed, reactive, ref, watch } from 'vue';

import dayjs from 'dayjs';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import {
  httpGetPatientDetail,
  httpLoadAdmissionParticipantInit,
  httpSavePatientInfo,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station.http';
import { httpExecuteBedOperation } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.http';
import AppDialog from '#/components/app-dialog.vue';
import PatientAllergyHistoryPanel from '#/components/clinical/patient-allergy-history-panel.vue';
import { useRequest } from '#/hooks/use-request';
import { formatMoneyYuan } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { isStaleCrossRoleError } from '#/utils/http/stale-cross-role-error';
import { isEncounterClinicalOrderMutationBlocked } from '#/utils/inpatient/encounter-clinical-order-gate';
import { normalizeAdmCondTermCode } from '#/utils/terminology/resolve-adm-cond-code-label';
import { normalizeTerminologyOptions } from '#/utils/terminology/resolve-terminology-option-label';
import { DEFAULT_ADM_COND_CODE_GENERAL } from '#/views/inpatient/charge/register/inpatient-register.constants';

import {
  admissionBedConfirmLabel,
  admissionBedDialogTitle,
  buildExecuteBedOperationFromIntent,
} from '../services/admission-bed-operation';
import {
  bedOperationSuccessMessage,
  ENCOUNTER_STATUS_REGISTERED,
} from '../services/execute-bed-operation';
import {
  buildParticipantSubmitRows,
  mapParticipantsToFormIds,
  mergeParticipantsIntoOptionLists,
} from '../services/map-patient-detail-participants';
import { loadPrintPendingRows } from '../print-center/load-print-pending';
import { printInpatientPrintCenter } from '../print-center/print-center-print';
import {
  formatNurseStationAccountBalance,
  resolveInpatientAccountBalanceLabel,
} from './nurse-station-module-patient.types';

defineOptions({
  name: 'InpatientNurseStationPatientAdmissionBedDialog',
});

interface Props {
  /** 列表行摘要（详情加载前展示） */
  patient: PatientItem | null;
  /** 拖放/选床后的待确认写操作；无则仅维护患者信息 */
  bedOperation: AdmissionBedOperationIntent | null;
  regionNameById: Record<string, string>;
  wardNameById: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), {
  patient: null,
  bedOperation: null,
  regionNameById: () => ({}),
  wardNameById: () => ({}),
});

const visible = defineModel<boolean>({ default: false });

const emit = defineEmits<{
  saved: [];
}>();

const detail = ref<PatientDetailDto | null>(null);
const doctorOptions = ref<OptionDto[]>([]);
const primaryNurseOptions = ref<OptionDto[]>([]);
const admCondCodeOptions = ref<OptionDto[]>([]);
/** 打开弹窗时回填的入院体征快照；用于判断后续保存是否需要再写体征 */
const loadedAdmissionVitals = ref<AdmissionVitalSignSubmitDto | undefined>();

const vitalForm = reactive({
  heightCm: '',
  weightKg: '',
  temperature: '',
  heartRate: '',
  pulse: '',
  bloodPressureHigh: '',
  bloodPressureLow: '',
});

const admissionForm = reactive({
  inpatientDoctorId: '',
  attendingDoctorId: '',
  chiefDoctorId: '',
  primaryNurseId: '',
  priorityEnum: DEFAULT_ADM_COND_CODE_GENERAL,
  admissionTimeDisplay: '',
});

const detailRequest = useRequest(httpGetPatientDetail, { manual: true });
const saveRequest = useRequest(httpSavePatientInfo, { manual: true });
const bedOperationRequest = useRequest(httpExecuteBedOperation, {
  manual: true,
  refreshOnStale: () => {
    emit('saved');
  },
});

const dialogTitle = computed(() => admissionBedDialogTitle(props.bedOperation));
const confirmButtonLabel = computed(() => admissionBedConfirmLabel(props.bedOperation));

const dialogLoading = computed(
  () =>
    detailRequest.loading.value || saveRequest.loading.value || bedOperationRequest.loading.value,
);

/** 打印腕带中 */
const printingWristband = ref(false);
/** 打印床头卡中 */
const printingBedCard = ref(false);

const encounterId = computed(() => props.patient?.id ?? '');

/** 过敏史写接口用患者主键（详情优先，列表摘要兜底） */
const allergyPatientId = computed(
  () => preserveApiIdForRequest(detail.value?.patientId ?? props.patient?.patientId) ?? '',
);

/** 过敏史写接口用就诊主键 */
const allergyEncounterId = computed(() => preserveApiIdForRequest(encounterId.value) ?? '');

/** 出院/待结算/已结算时过敏史只读 */
const allergyReadOnly = computed(() =>
  isEncounterClinicalOrderMutationBlocked(detail.value?.statusEnum ?? props.patient?.statusEnum),
);

const allergyHistoryPanelRef = ref<{ persist: (silent?: boolean) => Promise<void> } | null>(null);

/** 体征卡保存时顺带提交过敏史文本 */
async function persistAllergyHistory() {
  if (allergyReadOnly.value) {
    return;
  }
  await allergyHistoryPanelRef.value?.persist(true);
}

/** 患者姓名 */
const patientName = computed(
  () => (detail.value?.patientName ?? props.patient?.name ?? '—').trim() || '—',
);

/** 头像占位字（姓名首字） */
const patientAvatarText = computed(() => {
  const name = patientName.value;
  if (!name || name === '—') {
    return '?';
  }
  return name.slice(0, 1);
});

/** 性别展示 */
const genderDisplay = computed(
  () => (detail.value?.genderEnumText ?? props.patient?.gender ?? '—').trim() || '—',
);

/** 年龄展示 */
const ageDisplay = computed(() => {
  const ageText = (detail.value?.ageText ?? '').trim();
  if (ageText) {
    return ageText.includes('岁') ? ageText : `${ageText}岁`;
  }
  const age = props.patient?.age;
  return age != null && age > 0 ? `${age}岁` : '—';
});

/** 联系电话 */
const phoneDisplay = computed(
  () => (detail.value?.patientPhone ?? props.patient?.patientPhone ?? '').trim() || '—',
);

/** 住院号 */
const inpatientNoDisplay = computed(
  () => (detail.value?.encounterBusNo ?? props.patient?.encounterBusNo ?? '').trim() || '—',
);

/** 病人院内编码 */
const patientBusNoDisplay = computed(
  () => (detail.value?.patientBusNo ?? props.patient?.patientBusNo ?? '').trim() || '—',
);

/** 入院途径 */
const inWayDisplay = computed(
  () => (detail.value?.inWayCodeText ?? props.patient?.inWayCodeText ?? '').trim() || '—',
);

/** 身份证号 */
const idCardNoDisplay = computed(
  () => (detail.value?.idCardNo ?? props.patient?.idCardNo ?? '').trim() || '—',
);

/** 住址 */
const addressDisplay = computed(
  () => (detail.value?.address ?? props.patient?.address ?? '').trim() || '—',
);

/** 入院状态（病情展示） */
const admissionStatusDisplay = computed(
  () => (detail.value?.priorityEnumText ?? props.patient?.priorityEnumText ?? '').trim() || '—',
);

/** 入院科室（机构名） */
const organizationNameDisplay = computed(
  () => (detail.value?.organizationName ?? props.patient?.organizationName ?? '').trim() || '—',
);

/** 入院病区名称 */
const wardNameDisplay = computed(() => {
  const op = props.bedOperation;
  if (op) {
    return op.targetRegionName;
  }
  return (
    (detail.value?.wardName ?? props.regionNameById[props.patient?.regionId ?? ''] ?? '').trim() ||
    '—'
  );
});

/** 主治医生 */
const attendingDoctorDisplay = computed(
  () =>
    (detail.value?.attendingDoctorName ?? props.patient?.attendingDoctorName ?? '').trim() || '—',
);

/** 医保人员编号 */
const insuranceNoDisplay = computed(
  () => (detail.value?.insuranceNo ?? props.patient?.insuranceNo ?? '').trim() || '—',
);

/** 医保类型（费用性质） */
const feeTypeLabel = computed(
  () => (detail.value?.contractDisplay ?? props.patient?.feeType ?? '—').trim() || '—',
);

/** 居民医疗类型 */
const medTypeDisplay = computed(
  () => (detail.value?.medTypeDisplay ?? props.patient?.medTypeDisplay ?? '').trim() || '—',
);

/** 险种类别展示（与大屏同口径） */
const insutypeDisplay = computed(() => {
  const code = (detail.value?.insutype ?? props.patient?.insutype ?? '').trim();
  if (!code) {
    return '—';
  }
  if (code === '1') {
    return '1-医疗保险';
  }
  return code;
});

/** 入院时间展示 */
const admissionAtDisplay = computed(() => {
  const raw = (
    detail.value?.admissionTime ??
    detail.value?.startTime ??
    props.patient?.admissionTime ??
    props.patient?.startTime ??
    ''
  ).trim();
  if (!raw) {
    return '—';
  }
  const parsed = dayjs(raw);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : raw;
});

/** 住院天数：即进不计出（入院日计、出院日不计）；在院含当日 */
const inpatientDaysDisplay = computed(() => {
  const admissionRaw = (
    detail.value?.admissionTime ??
    detail.value?.startTime ??
    props.patient?.admissionTime ??
    props.patient?.startTime ??
    ''
  ).trim();
  if (!admissionRaw) {
    return '—';
  }
  const admission = dayjs(admissionRaw);
  if (!admission.isValid()) {
    return '—';
  }
  const endRaw = (detail.value?.endTime ?? props.patient?.endTime ?? '').trim();
  let hasDischarge = false;
  let end = dayjs();
  if (endRaw && dayjs(endRaw).isValid()) {
    hasDischarge = true;
    end = dayjs(endRaw);
  }
  const diff = end.startOf('day').diff(admission.startOf('day'), 'day');
  // 已出院：即进不计出
  if (hasDischarge) {
    return `${Math.max(1, diff)}天`;
  }
  // 在院：含当日
  return `${Math.max(1, diff + 1)}天`;
});

/** 预缴额 */
const paidAmountDisplay = computed(() => {
  const paid = detail.value?.paidAmount ?? props.patient?.paidAmount;
  if (paid == null || paid === '') {
    return '—';
  }
  const num = Number(paid);
  if (Number.isFinite(num)) {
    return formatMoneyYuan(num);
  }
  return String(paid);
});

/** 账户余额标签（可用余额/账户余额） */
const balanceFieldLabel = computed(() =>
  resolveInpatientAccountBalanceLabel({
    accountBalanceDisplayMode:
      detail.value?.accountBalanceDisplayMode ?? props.patient?.accountBalanceDisplayMode,
  }),
);

/** 账户余额金额 */
const balanceAmountDisplay = computed(() =>
  formatNurseStationAccountBalance({
    accountBalance: detail.value?.accountBalance ?? props.patient?.accountBalance,
    accountBalanceSummaryText:
      detail.value?.accountBalanceSummaryText ?? props.patient?.accountBalanceSummaryText,
    availableBalance: detail.value?.availableBalance ?? props.patient?.availableBalance,
  }),
);

/** 住院诊断 */
const diagnosisDisplay = computed(() => {
  const list = detail.value?.diagnosisList ?? props.patient?.diagnosisList;
  if (list?.length) {
    return list.join('；');
  }
  return (detail.value?.diagnosisSummary ?? props.patient?.diagnosis ?? '—').trim() || '—';
});

/** 护理级别（读模型 nursingLevelName） */
const nursingLevelLabel = computed(
  () => (detail.value?.nursingLevelName ?? props.patient?.level ?? '').trim() || '',
);

/** 是否已分床 */
const hasAssignedBed = computed(() => {
  const bed = (detail.value?.bedName ?? props.patient?.bedNo ?? '').trim();
  return Boolean(bed);
});

/** 床位标签文案 */
const bedBadgeText = computed(() => {
  const bed = (detail.value?.bedName ?? props.patient?.bedNo ?? '').trim();
  if (!bed) {
    return '未分床';
  }
  const ward = (
    detail.value?.wardName ??
    props.regionNameById[props.patient?.regionId ?? ''] ??
    ''
  ).trim();
  return ward ? `${ward} · ${bed}` : bed;
});

/** 所在位置：病区 | 病房 | 床位 */
const locationDisplay = computed(() => {
  const op = props.bedOperation;
  if (op) {
    return `${op.targetRegionName} | ${op.targetHouseName} | ${op.targetBedName}`;
  }
  const ward =
    (detail.value?.wardName ?? props.regionNameById[props.patient?.regionId ?? ''] ?? '').trim() ||
    '—';
  const house =
    (detail.value?.houseName ?? props.wardNameById[props.patient?.wardId ?? ''] ?? '').trim() ||
    '—';
  const bed = (detail.value?.bedName ?? props.patient?.bedNo ?? '').trim() || '未分床';
  return `${ward} | ${house} | ${bed}`;
});

/** 互换时展示对手信息 */
const swapPartnerHint = computed(() => {
  const op = props.bedOperation;
  if (op?.type !== 'swap' || !op.swapPartnerName) {
    return '';
  }
  return `将与「${op.swapPartnerName}」（床位 ${op.targetBedName}）互换`;
});

/** 患者信息区展示字段（字段口径对齐大屏患者卡） */
const patientInfoFields = computed(() => [
  { label: '住院号', value: inpatientNoDisplay.value },
  { label: '入院途径', value: inWayDisplay.value },
  { label: '病人ID', value: patientBusNoDisplay.value },
  { label: '电话', value: phoneDisplay.value },
  { label: '身份证号', value: idCardNoDisplay.value },
  { label: '护理级别', value: nursingLevelLabel.value || '—' },
  { label: '入院状态', value: admissionStatusDisplay.value },
  { label: '入院科室', value: organizationNameDisplay.value },
  { label: '入院病区', value: wardNameDisplay.value },
  { label: '主治医生', value: attendingDoctorDisplay.value },
  { label: '医保号', value: insuranceNoDisplay.value },
  { label: '医保类型', value: feeTypeLabel.value },
  { label: '居民医疗类型', value: medTypeDisplay.value },
  { label: '险种类别', value: insutypeDisplay.value },
  { label: '入院时间', value: admissionAtDisplay.value },
  { label: '住院天数', value: inpatientDaysDisplay.value },
  { label: balanceFieldLabel.value, value: balanceAmountDisplay.value },
  { label: '预缴', value: paidAmountDisplay.value },
  { label: '所在位置', value: locationDisplay.value, span: 3 },
  { label: '地址', value: addressDisplay.value, span: 3 },
  { label: '入院诊断', value: diagnosisDisplay.value, span: 3 },
]);

/** 入院科室（病区）只读 */
const admissionDepartment = computed(() => {
  const op = props.bedOperation;
  if (op) {
    return op.targetRegionName;
  }
  return (
    (detail.value?.wardName ?? props.regionNameById[props.patient?.regionId ?? ''] ?? '—').trim() ||
    '—'
  );
});

/** 入院病区只读 */
const admissionWard = computed(() => {
  const op = props.bedOperation;
  if (op) {
    return op.targetHouseName;
  }
  return (
    (detail.value?.houseName ?? props.wardNameById[props.patient?.wardId ?? ''] ?? '—').trim() ||
    '—'
  );
});

/** 入住床位只读 */
const assignedBed = computed(() => {
  const op = props.bedOperation;
  if (op) {
    return op.targetBedName;
  }
  return (detail.value?.bedName ?? props.patient?.bedNo ?? '未分床').trim() || '未分床';
});

/** 入科时间回显：待入科取当前精确时分秒；已入科读后端 start_time/admissionTime */
function resolveAdmissionTimeDisplay(row: PatientDetailDto): string {
  const status = (row.statusEnum ?? '').trim();
  if (status === ENCOUNTER_STATUS_REGISTERED) {
    return dayjs().format('YYYY-MM-DD HH:mm:ss');
  }
  const admissionRaw = (row.admissionTime ?? row.startTime ?? '').trim();
  if (!admissionRaw) {
    return dayjs().format('YYYY-MM-DD HH:mm:ss');
  }
  const parsed = dayjs(admissionRaw);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : admissionRaw;
}

/** 重置体征与入院表单 */
function resetForms() {
  vitalForm.heightCm = '';
  vitalForm.weightKg = '';
  vitalForm.temperature = '';
  vitalForm.heartRate = '';
  vitalForm.pulse = '';
  vitalForm.bloodPressureHigh = '';
  vitalForm.bloodPressureLow = '';
  loadedAdmissionVitals.value = undefined;
  admissionForm.inpatientDoctorId = '';
  admissionForm.attendingDoctorId = '';
  admissionForm.chiefDoctorId = '';
  admissionForm.primaryNurseId = '';
  admissionForm.priorityEnum = DEFAULT_ADM_COND_CODE_GENERAL;
  admissionForm.admissionTimeDisplay = '';
}

/** 归一化体征块字段，便于比较是否改动 */
function normalizeAdmissionVitalSigns(
  vitals?: AdmissionVitalSignSubmitDto,
): Required<
  Pick<
    AdmissionVitalSignSubmitDto,
    | 'heightCm'
    | 'weightKg'
    | 'temperature'
    | 'heartRate'
    | 'pulse'
    | 'bloodPressureHigh'
    | 'bloodPressureLow'
  >
> {
  const pulseOrHeartRate = (vitals?.pulse ?? '').trim() || (vitals?.heartRate ?? '').trim();
  return {
    heightCm: (vitals?.heightCm ?? '').trim(),
    weightKg: (vitals?.weightKg ?? '').trim(),
    temperature: (vitals?.temperature ?? '').trim(),
    heartRate: pulseOrHeartRate,
    pulse: pulseOrHeartRate,
    bloodPressureHigh: (vitals?.bloodPressureHigh ?? '').trim(),
    bloodPressureLow: (vitals?.bloodPressureLow ?? '').trim(),
  };
}

/** 体征块是否至少有一项非空 */
function hasAnyAdmissionVitalValue(vitals?: AdmissionVitalSignSubmitDto): boolean {
  const normalized = normalizeAdmissionVitalSigns(vitals);
  return Object.values(normalized).some((value) => Boolean(value));
}

/** 当前表单相对打开时快照是否有改动 */
function isAdmissionVitalsChangedFromLoaded(current?: AdmissionVitalSignSubmitDto): boolean {
  const next = normalizeAdmissionVitalSigns(current);
  const prev = normalizeAdmissionVitalSigns(loadedAdmissionVitals.value);
  return (
    next.heightCm !== prev.heightCm ||
    next.weightKg !== prev.weightKg ||
    next.temperature !== prev.temperature ||
    next.pulse !== prev.pulse ||
    next.bloodPressureHigh !== prev.bloodPressureHigh ||
    next.bloodPressureLow !== prev.bloodPressureLow
  );
}

/** 体征表单 → 提交块；全空时返回 undefined */
function buildAdmissionVitalSignsFromForm(): AdmissionVitalSignSubmitDto | undefined {
  const normalized = normalizeAdmissionVitalSigns({
    heightCm: vitalForm.heightCm,
    weightKg: vitalForm.weightKg,
    temperature: vitalForm.temperature,
    heartRate: vitalForm.pulse,
    pulse: vitalForm.pulse,
    bloodPressureHigh: vitalForm.bloodPressureHigh,
    bloodPressureLow: vitalForm.bloodPressureLow,
  });
  if (!hasAnyAdmissionVitalValue(normalized)) {
    return undefined;
  }
  return {
    heightCm: normalized.heightCm || undefined,
    weightKg: normalized.weightKg || undefined,
    temperature: normalized.temperature || undefined,
    heartRate: normalized.heartRate || undefined,
    pulse: normalized.pulse || undefined,
    bloodPressureHigh: normalized.bloodPressureHigh || undefined,
    bloodPressureLow: normalized.bloodPressureLow || undefined,
  };
}

/**
 * 组装本次是否提交入院体征：
 * 已有入院体征且未改动 → 不提交（后续保存只改医护，不再写/新增体征）；
 * 首次填写或确有改动 → 提交（后端按入科时点 upsert，不新增第二份）。
 */
function resolveAdmissionVitalSignsForSave(): AdmissionVitalSignSubmitDto | undefined {
  const payload = buildAdmissionVitalSignsFromForm();
  if (!payload) {
    return undefined;
  }
  if (
    hasAnyAdmissionVitalValue(loadedAdmissionVitals.value) &&
    !isAdmissionVitalsChangedFromLoaded(payload)
  ) {
    return undefined;
  }
  return payload;
}

/** 详情回填块 → 体征表单 */
function applyAdmissionVitalSignsToForm(vitals?: AdmissionVitalSignSubmitDto) {
  const normalized = normalizeAdmissionVitalSigns(vitals);
  vitalForm.heightCm = normalized.heightCm;
  vitalForm.weightKg = normalized.weightKg;
  vitalForm.temperature = normalized.temperature;
  vitalForm.pulse = normalized.pulse;
  vitalForm.heartRate = normalized.heartRate;
  vitalForm.bloodPressureHigh = normalized.bloodPressureHigh;
  vitalForm.bloodPressureLow = normalized.bloodPressureLow;
  // 快照用归一化结果，比较时与表单口径一致
  loadedAdmissionVitals.value = hasAnyAdmissionVitalValue(normalized)
    ? {
        heightCm: normalized.heightCm || undefined,
        weightKg: normalized.weightKg || undefined,
        temperature: normalized.temperature || undefined,
        heartRate: normalized.heartRate || undefined,
        pulse: normalized.pulse || undefined,
        bloodPressureHigh: normalized.bloodPressureHigh || undefined,
        bloodPressureLow: normalized.bloodPressureLow || undefined,
      }
    : undefined;
}

/** 关闭弹窗 */
function closeDialog() {
  visible.value = false;
}

/** 弹窗患者 → 打印模块入参（腕带/床头卡字段来自列表摘要 + 详情兜底） */
function mapDialogPatientToModulePatient(item: PatientItem): NurseStationModulePatient {
  const d = detail.value;
  return {
    id: item.id,
    patientId: item.patientId ?? d?.patientId,
    encounterBusNo: item.encounterBusNo ?? d?.encounterBusNo,
    admissionAt: item.admissionTime ?? item.startTime ?? d?.admissionTime,
    inWardAt: item.startTime ?? d?.startTime,
    dischargeAt: item.endTime,
    bedNo: (d?.bedName ?? item.bedNo ?? '').trim(),
    name: (d?.patientName ?? item.name ?? '').trim(),
    gender: (d?.genderEnumText ?? item.gender ?? '').trim(),
    age: item.age,
    nurseLevel: item.level,
    feeType: (d?.contractDisplay ?? item.feeType ?? '').trim() || undefined,
    attendingDoctorName:
      (d?.attendingDoctorName ?? item.attendingDoctorName ?? '').trim() || undefined,
    diagnosis: item.diagnosis || d?.diagnosisSummary,
    // 入院科室：详情优先，勿漏传到腕带/床头卡打印
    organizationName: (d?.organizationName ?? item.organizationName ?? '').trim() || undefined,
    organizationId: (d?.organizationId ?? item.organizationId ?? '').trim() || undefined,
    regionName: props.regionNameById[item.regionId] || d?.wardName || '-',
    wardName: props.wardNameById[item.wardId] || d?.houseName || '-',
    pendingAudit: item.pendingAudit,
    pendingExecute: item.pendingExecute,
    accountId: item.accountId,
    accountBalance: item.accountBalance,
    accountBalanceDisplayMode: item.accountBalanceDisplayMode,
    accountBalanceSummaryText: item.accountBalanceSummaryText,
    accountBalanceSubText: item.accountBalanceSubText,
    availableBalance: item.availableBalance,
    pendingSettlementTotal: item.pendingSettlementTotal,
    lowBalanceWarning: item.lowBalanceWarning,
    riskTags: item.riskText ? [item.riskText] : [],
    statusEnum: item.statusEnum,
  };
}

/** 打印当前弹窗患者腕带 */
async function handlePrintWristband() {
  const row = props.patient;
  if (!row) {
    ElMessage.warning('患者信息未加载');
    return;
  }
  if (printingWristband.value) {
    return;
  }
  printingWristband.value = true;
  try {
    const patients = [mapDialogPatientToModulePatient(row)];
    const today = dayjs().format('YYYY-MM-DD');
    const selectedRows = await loadPrintPendingRows({
      docType: 'wristband',
      patients,
      dateFrom: today,
      dateTo: today,
      executionDeadline: `${today} 23:59:59`,
    });
    if (selectedRows.length <= 0) {
      ElMessage.warning('暂无「腕带」可打印数据');
      return;
    }
    const result = await printInpatientPrintCenter({
      docType: 'wristband',
      patients,
      selectedRows,
      dateFrom: today,
      dateTo: today,
      executionDeadline: `${today} 23:59:59`,
    });
    if (result.ok) {
      ElMessage.success(`已发送打印：腕带，共 ${result.count} 份`);
    }
  } finally {
    printingWristband.value = false;
  }
}

/** 打印当前弹窗患者床头卡 */
async function handlePrintBedCard() {
  const row = props.patient;
  if (!row) {
    ElMessage.warning('患者信息未加载');
    return;
  }
  if (printingBedCard.value) {
    return;
  }
  printingBedCard.value = true;
  try {
    const patients = [mapDialogPatientToModulePatient(row)];
    const today = dayjs().format('YYYY-MM-DD');
    const selectedRows = await loadPrintPendingRows({
      docType: 'bed_card',
      patients,
      dateFrom: today,
      dateTo: today,
      executionDeadline: `${today} 23:59:59`,
    });
    if (selectedRows.length <= 0) {
      ElMessage.warning('暂无「床头卡」可打印数据');
      return;
    }
    const result = await printInpatientPrintCenter({
      docType: 'bed_card',
      patients,
      selectedRows,
      dateFrom: today,
      dateTo: today,
      executionDeadline: `${today} 23:59:59`,
    });
    if (result.ok) {
      ElMessage.success(`已发送打印：床头卡，共 ${result.count} 份`);
    }
  } finally {
    printingBedCard.value = false;
  }
}

/** 解析当前表单适用的入院病区位置 id */
function resolveWardLocationId(): string {
  const op = props.bedOperation;
  if (op?.targetWardLocationId) {
    return op.targetWardLocationId.trim();
  }
  const fromDetail = (detail.value?.wardId ?? '').trim();
  if (fromDetail) {
    return fromDetail;
  }
  return (props.patient?.regionId ?? '').trim();
}

/** 加载住院医师与责任护士下拉 */
async function loadParticipantOptions() {
  const orgId = detail.value?.organizationId;
  const wardLocationId = resolveWardLocationId();
  const res = await httpLoadAdmissionParticipantInit({
    ...(orgId ? { organizationId: String(orgId) } : {}),
    ...(wardLocationId ? { wardLocationId } : {}),
  });
  const merged = mergeParticipantsIntoOptionLists(
    res.doctorOptions ?? [],
    res.primaryNurseOptions ?? [],
    detail.value?.participants,
  );
  doctorOptions.value = merged.doctorOptions;
  primaryNurseOptions.value = merged.primaryNurseOptions;
  admCondCodeOptions.value = normalizeTerminologyOptions(res.admCondCodeOptions);
}

/** 打开时拉取患者详情并回填表单 */
async function loadDetailAndForm() {
  const id = encounterId.value;
  if (!id) {
    return;
  }
  resetForms();
  detail.value = null;
  const res = await detailRequest.run({ encounterId: id });
  const row = res.detail;
  if (!row) {
    ElMessage.warning('未获取到患者详情');
    return;
  }
  detail.value = row;
  // 待入科首次分床：办理入院 / 门诊转住院带来的医生默认回显到住院医生（必填）
  const bringDoctorToInpatientDoctor =
    (row.statusEnum ?? '').trim() === ENCOUNTER_STATUS_REGISTERED;
  const participantIds = mapParticipantsToFormIds(row.participants, {
    bringDoctorToInpatientDoctor,
  });
  admissionForm.inpatientDoctorId = participantIds.inpatientDoctorId;
  admissionForm.attendingDoctorId = participantIds.attendingDoctorId;
  admissionForm.chiefDoctorId = participantIds.chiefDoctorId;
  admissionForm.primaryNurseId = participantIds.primaryNurseId;
  admissionForm.priorityEnum =
    normalizeAdmCondTermCode(row.priorityEnum) || DEFAULT_ADM_COND_CODE_GENERAL;
  applyAdmissionVitalSignsToForm(row.admissionVitalSigns);
  admissionForm.admissionTimeDisplay = resolveAdmissionTimeDisplay(row);
  await loadParticipantOptions();
}

/** 校验入院信息必填项 */
function validateAdmissionForm(): boolean {
  if (!admissionForm.inpatientDoctorId) {
    ElMessage.warning('请选择住院医生');
    return false;
  }
  if (!admissionForm.primaryNurseId) {
    ElMessage.warning('请选择责任护士');
    return false;
  }
  return true;
}

/**
 * 保存体征卡表单（医护/病情 + 入院体征）；不触发分床，保存后不关窗。
 * 有体征时须已入科（有 start_time），否则体温单锚点无法落库。
 */
async function handleSaveVitals() {
  const id = encounterId.value;
  if (!id) {
    return;
  }
  if (!validateAdmissionForm()) {
    return;
  }
  // 已有入院体征且未改动时不提交，避免后续保存重复写/新增
  const vitalPayload = resolveAdmissionVitalSignsForSave();
  // 未入科无 start_time 时后端跳过写体温单，避免假成功
  const inWardAt = (
    detail.value?.startTime ??
    detail.value?.admissionTime ??
    props.patient?.startTime ??
    props.patient?.admissionTime ??
    ''
  ).trim();
  if (vitalPayload && !inWardAt) {
    ElMessage.warning('请先完成入科分床，体征将按入科时间写入体温单');
    return;
  }
  const participants = buildParticipantSubmitRows({
    inpatientDoctorId: admissionForm.inpatientDoctorId,
    attendingDoctorId: admissionForm.attendingDoctorId,
    chiefDoctorId: admissionForm.chiefDoctorId,
    primaryNurseId: admissionForm.primaryNurseId,
  });
  try {
    await saveRequest.run({
      encounterId: id,
      priorityEnum: admissionForm.priorityEnum || undefined,
      participants: participants.length > 0 ? participants : undefined,
      admissionVitalSigns: vitalPayload,
    });
    await persistAllergyHistory();
    if (vitalPayload) {
      ElMessage.success('已保存并带入体温单');
    } else {
      ElMessage.success('入院信息已保存');
    }
    emit('saved');
    // 回读详情，保证医护与体征以服务端为准
    await loadDetailAndForm();
  } catch (error) {
    if (isStaleCrossRoleError(error)) {
      emit('saved');
    }
  }
}

/** 确认：保存医护信息后按需提交分床/换床/互换 */
async function handleAdmit() {
  const id = encounterId.value;
  if (!id) {
    return;
  }
  if (!validateAdmissionForm()) {
    return;
  }
  const participants = buildParticipantSubmitRows({
    inpatientDoctorId: admissionForm.inpatientDoctorId,
    attendingDoctorId: admissionForm.attendingDoctorId,
    chiefDoctorId: admissionForm.chiefDoctorId,
    primaryNurseId: admissionForm.primaryNurseId,
  });
  const op = props.bedOperation;
  const assignWithBed = op?.type === 'assign';
  // 首次分床仍可随身征；维护路径：未改动则不重复提交入院体征
  const vitalPayload = assignWithBed
    ? buildAdmissionVitalSignsFromForm()
    : resolveAdmissionVitalSignsForSave();
  try {
    await saveRequest.run({
      encounterId: id,
      priorityEnum: admissionForm.priorityEnum || undefined,
      participants: participants.length > 0 ? participants : undefined,
      admissionVitalSigns: assignWithBed ? undefined : vitalPayload,
    });
    await persistAllergyHistory();
    if (op) {
      const execBody = buildExecuteBedOperationFromIntent(
        op,
        id,
        participants,
        admissionForm.priorityEnum || undefined,
        assignWithBed ? vitalPayload : undefined,
      );
      await bedOperationRequest.run(execBody);
      ElMessage.success(
        bedOperationSuccessMessage(execBody.operationType, patientName.value, op.targetBedName),
      );
    } else {
      ElMessage.success('患者信息已保存');
    }
    emit('saved');
    closeDialog();
  } catch (error) {
    if (isStaleCrossRoleError(error)) {
      emit('saved');
      closeDialog();
    }
  }
}

watch(visible, (open) => {
  if (open) {
    void loadDetailAndForm();
  } else {
    detail.value = null;
    resetForms();
  }
});
</script>

<template>
  <AppDialog
    v-model="visible"
    :title="dialogTitle"
    width="920px"
    destroy-on-close
    :close-on-click-modal="false"
    class="patient-admission-bed-dialog"
  >
    <div v-loading="dialogLoading" class="admission-dialog">
      <section class="patient-info-card">
        <div class="patient-info-card__accent" aria-hidden="true"></div>
        <div class="patient-info-card__body">
          <div class="patient-info-card__hero">
            <div class="patient-info-card__avatar">{{ patientAvatarText }}</div>
            <div class="patient-info-card__identity">
              <div class="patient-info-card__name-row">
                <span class="patient-info-card__name">{{ patientName }}</span>
                <span class="patient-info-card__demographics">
                  {{ genderDisplay }} · {{ ageDisplay }}
                </span>
              </div>
              <div class="patient-info-card__tags">
                <ElTag size="small" :type="hasAssignedBed ? 'primary' : 'warning'" effect="light">
                  {{ bedBadgeText }}
                </ElTag>
                <ElTag size="small" type="info" effect="plain">{{ feeTypeLabel }}</ElTag>
                <ElTag v-if="nursingLevelLabel" size="small" type="success" effect="plain">
                  {{ nursingLevelLabel }}
                </ElTag>
              </div>
            </div>
          </div>
          <div v-if="swapPartnerHint" class="patient-info-card__swap-hint">
            {{ swapPartnerHint }}
          </div>
          <div class="patient-info-card__grid">
            <div
              v-for="item in patientInfoFields"
              :key="item.label"
              class="patient-info-card__field"
              :class="{
                'patient-info-card__field--span2': item.span === 2,
                'patient-info-card__field--span3': item.span === 3,
              }"
            >
              <span class="patient-info-card__label">{{ item.label }}</span>
              <span class="patient-info-card__value" :title="item.value">{{ item.value }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="admission-dialog__section">
        <div class="admission-dialog__section-title">入院体征</div>
        <ElForm label-width="72px" class="admission-dialog__form-grid">
          <div class="admission-dialog__row">
            <ElFormItem label="身高">
              <ElInput v-model="vitalForm.heightCm" placeholder="请输入">
                <template #suffix>cm</template>
              </ElInput>
            </ElFormItem>
            <ElFormItem label="体重">
              <ElInput v-model="vitalForm.weightKg" placeholder="请输入">
                <template #suffix>kg</template>
              </ElInput>
            </ElFormItem>
            <ElFormItem label="体温">
              <ElInput v-model="vitalForm.temperature" placeholder="请输入">
                <template #suffix>°C</template>
              </ElInput>
            </ElFormItem>
          </div>
          <div class="admission-dialog__row">
            <ElFormItem label="脉搏">
              <ElInput v-model="vitalForm.pulse" placeholder="请输入">
                <template #suffix>次/分</template>
              </ElInput>
            </ElFormItem>
            <ElFormItem label="血压">
              <div class="admission-dialog__bp">
                <ElInput v-model="vitalForm.bloodPressureHigh" placeholder="请输入" />
                <span class="admission-dialog__bp-sep">~</span>
                <ElInput v-model="vitalForm.bloodPressureLow" placeholder="请输入" />
                <span class="admission-dialog__bp-unit">mmHg</span>
              </div>
            </ElFormItem>
          </div>
          <div class="admission-dialog__row admission-dialog__row--full">
            <ElFormItem label="过敏史">
              <PatientAllergyHistoryPanel
                v-if="allergyPatientId && allergyEncounterId"
                ref="allergyHistoryPanelRef"
                :patient-id="allergyPatientId"
                :encounter-id="allergyEncounterId"
                default-verification-status="UNCONFIRMED"
                :read-only="allergyReadOnly"
              />
              <ElInput v-else disabled placeholder="正在加载患者信息…" />
            </ElFormItem>
          </div>
        </ElForm>
      </section>

      <section class="admission-dialog__section">
        <div class="admission-dialog__section-title">入院信息</div>
        <ElForm label-width="88px" class="admission-dialog__form-grid">
          <div class="admission-dialog__row">
            <ElFormItem label="入院科室">
              <ElInput :model-value="admissionDepartment" disabled />
            </ElFormItem>
            <ElFormItem label="入院病区">
              <ElInput :model-value="admissionWard" disabled />
            </ElFormItem>
            <ElFormItem label="入住床位">
              <ElInput :model-value="assignedBed" disabled />
            </ElFormItem>
          </div>
          <div class="admission-dialog__row">
            <ElFormItem label="住院医生" required>
              <ElSelect
                v-model="admissionForm.inpatientDoctorId"
                filterable
                clearable
                placeholder="请选择住院医生"
                class="w-full"
              >
                <ElOption
                  v-for="opt in doctorOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="主治医生">
              <ElSelect
                v-model="admissionForm.attendingDoctorId"
                filterable
                clearable
                placeholder="请选择主治医生"
                class="w-full"
              >
                <ElOption
                  v-for="opt in doctorOptions"
                  :key="`att-${opt.value}`"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="主任医生">
              <ElSelect
                v-model="admissionForm.chiefDoctorId"
                filterable
                clearable
                placeholder="请选择主任医生"
                class="w-full"
              >
                <ElOption
                  v-for="opt in doctorOptions"
                  :key="`chief-${opt.value}`"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </ElFormItem>
          </div>
          <div class="admission-dialog__row">
            <ElFormItem label="入科时间">
              <ElInput v-model="admissionForm.admissionTimeDisplay" disabled />
            </ElFormItem>
            <ElFormItem label="责任护士" required>
              <ElSelect
                v-model="admissionForm.primaryNurseId"
                filterable
                clearable
                placeholder="请选择责任护士"
                class="w-full"
              >
                <ElOption
                  v-for="opt in primaryNurseOptions"
                  :key="`nurse-${opt.value}`"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="患者病情">
              <ElSelect
                v-model="admissionForm.priorityEnum"
                clearable
                placeholder="请选择患者病情"
                class="w-full"
              >
                <ElOption
                  v-for="opt in admCondCodeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
          </div>
        </ElForm>
      </section>
    </div>

    <template #footer>
      <div class="admission-dialog__footer">
        <ElButton
          plain
          :loading="printingWristband"
          :disabled="!patient"
          @click="handlePrintWristband"
        >
          打腕带
        </ElButton>
        <ElButton plain :loading="printingBedCard" :disabled="!patient" @click="handlePrintBedCard">
          打床头卡
        </ElButton>
        <div class="admission-dialog__footer-actions">
          <ElButton @click="closeDialog">取消</ElButton>
          <ElButton :loading="saveRequest.loading.value" @click="handleSaveVitals">保存</ElButton>
          <ElButton type="primary" :loading="dialogLoading" @click="handleAdmit">
            {{ confirmButtonLabel }}
          </ElButton>
        </div>
      </div>
    </template>
  </AppDialog>
</template>

<style scoped>
.patient-info-card {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  display: flex;
  margin-bottom: 10px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgb(15 23 42 / 6%);
}
.patient-info-card__accent {
  background: var(--el-color-primary);
  flex-shrink: 0;
  width: 3px;
}
.patient-info-card__body {
  flex: 1;
  min-width: 0;
  padding: 8px 12px;
}
.patient-info-card__hero {
  align-items: center;
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}
.patient-info-card__avatar {
  align-items: center;
  background: linear-gradient(145deg, var(--el-color-primary-light-3), var(--el-color-primary));
  border-radius: 50%;
  color: #fff;
  display: flex;
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 600;
  height: 32px;
  justify-content: center;
  width: 32px;
}
.patient-info-card__identity {
  flex: 1;
  min-width: 0;
}
.patient-info-card__name-row {
  align-items: baseline;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 2px;
}
.patient-info-card__name {
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.2;
}
.patient-info-card__demographics {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.patient-info-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.patient-info-card__tags :deep(.el-tag) {
  height: 20px;
  line-height: 18px;
  padding: 0 6px;
}
.patient-info-card__swap-hint {
  background: var(--el-color-warning-light-9);
  border-radius: 4px;
  color: var(--el-color-warning-dark-2);
  font-size: 12px;
  line-height: 1.35;
  margin-bottom: 6px;
  padding: 4px 8px;
}
.patient-info-card__grid {
  border-top: 1px dashed var(--el-border-color-lighter);
  display: grid;
  gap: 2px 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding-top: 6px;
}
.patient-info-card__field {
  align-items: baseline;
  display: flex;
  gap: 4px;
  min-width: 0;
  line-height: 1.35;
}
.patient-info-card__field--span2 {
  grid-column: span 2;
}
.patient-info-card__field--span3 {
  grid-column: span 3;
}
.patient-info-card__label {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  font-size: 12px;
}
.patient-info-card__label::after {
  content: '：';
}
.patient-info-card__value {
  color: var(--el-text-color-primary);
  flex: 1;
  font-size: 12px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.patient-info-card__field--span2 .patient-info-card__value,
.patient-info-card__field--span3 .patient-info-card__value {
  white-space: normal;
}
.admission-dialog__section + .admission-dialog__section {
  margin-top: 8px;
}
.admission-dialog__section-title {
  border-left: 3px solid var(--el-color-primary);
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
  margin-bottom: 12px;
  padding-left: 8px;
}
.admission-dialog__row {
  display: grid;
  gap: 0 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.admission-dialog__row--full {
  grid-template-columns: minmax(0, 1fr);
}
.admission-dialog__bp {
  align-items: center;
  display: flex;
  gap: 6px;
  width: 100%;
}
.admission-dialog__bp-sep {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}
.admission-dialog__bp-unit {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  font-size: 12px;
}
.admission-dialog__footer {
  align-items: center;
  display: flex;
  justify-content: space-between;
  width: 100%;
}
.admission-dialog__footer-actions {
  display: flex;
  gap: 8px;
}
:deep(.patient-admission-bed-dialog .el-dialog__body) {
  padding-top: 8px;
}
</style>
