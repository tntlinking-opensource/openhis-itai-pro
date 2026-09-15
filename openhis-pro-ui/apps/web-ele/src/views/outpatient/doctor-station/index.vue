<script setup lang="ts">
import type { Component } from 'vue';

import type { DoctorStationPatient } from './doctorPatientStore';
import type {
  ServiceApplyKind,
  ServiceApplyPatientSummary,
} from './service-apply/service-apply.types';

import type { EnsureClinicalReceptionFn } from '#/components/doctor-station';
import type { PatientInfoBarPayload } from '#/components/outpatient/patient-card.types';

import { computed, nextTick, onMounted, provide, ref } from 'vue';

import { useUserStore } from '@vben/stores';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';
import { storeToRefs } from 'pinia';

import PatientAllergyHistoryDialog from '#/components/clinical/patient-allergy-history-dialog.vue';

import {
  httpCancelOutpatientReception,
  httpCompleteOutpatientEncounter,
  httpPageOutpatientInfoForDoctorStation,
  httpStartOutpatientReception,
} from '#/api/doctor-station/encounter.http';
import {
  DiagnosisList,
  ElepAdvice,
  ENSURE_CLINICAL_RECEPTION_KEY,
  ReportQuery,
  TcmAdvice,
} from '#/components/doctor-station';
import CommonDiagnosisMaintainDialog from '#/components/doctor-station/diagnosis/components/common-diagnosis-maintain-dialog.vue';
import { COMMON_DIAGNOSIS_MAINTAIN_VERSION_KEY } from '#/components/doctor-station/diagnosis/common-diagnosis-maintain.context';
import DoctorHeader from '#/components/outpatient/doctor-header.vue';
import EmrTemplateMaintainDialog from '#/components/outpatient/emr-template-maintain-dialog.vue';
import MedicalRecordForm from '#/components/outpatient/medical-record-form.vue';
import PatientInfoBar from '#/components/outpatient/patient-info-bar.vue';
import { EMR_TEMPLATE_STATION_KIND } from '#/views/basic/emr-template/emr-template-scope.constants';
import { confirmEmrDiscardUnsaved } from '#/emrEditor';
import { clearEncounterDiagnosisSession } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-session';
import { useAuthStore } from '#/store';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { resolveRequestErrorMessage } from '#/utils/http/resolve-request-error-message';
import {
  formatLocalDateYmd,
  getLocalDayRangeDateTimeStrings,
} from '#/utils/format/date-range-quick';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { printDoctorStationPrescriptions } from '#/utils/print/print-doctor-station-prescription';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

import { AdviceList } from './advice';
import PrescriptionSheetDialog from './components/prescription-sheet-dialog.vue';
import DoctorPatientList from './doctor-patient-list.vue';
import { useDoctorPatientStore } from './doctorPatientStore';
import InpatientAdmissionDialog from './inpatient-admission/inpatient-admission-dialog.vue';
import RefundApplicationDialog from './refund/refund-application-dialog.vue';
import ServiceApplyTab from './service-apply/index.vue';
import ServiceApplyDialog from './service-apply/service-apply-dialog.vue';
import {
  readDoctorOrderTabPreference,
  writeDoctorOrderTabPreference,
} from './services/order-tab-preference.service';

defineOptions({
  name: 'Doctor',
});

const QUEUE_IN_PROGRESS = 'IN_PROGRESS';

/**
 * IF-DOR-01 单页条数。
 * 待诊/在诊/完诊共用同一次四态并集结果再本地筛。
 */
const DOCTOR_STATION_QUEUE_PAGE_SIZE = 150;

/** 患者队列 Tab 内：全部 / 待诊 / 在诊 / 完诊状态筛选 */
type DoctorWaitingQueueStatusFilter = 'all' | 'completed' | 'in-progress' | 'waiting';

const WAITING_STATUS_CODES = new Set(['PLANNED', 'REGISTERED', 'TRIAGED']);
const IN_PROGRESS_STATUS_CODES = new Set(['IN_PROGRESS', 'ON_HOLD']);
const COMPLETED_STATUS_CODES = new Set(['COMPLETED', 'DISCHARGED']);

/**
 * 双击需调接诊写接口的状态（与后端契约对齐）。
 * 门诊诊毕为 `DISCHARGED`（非财务态 `COMPLETED`）；漏判会导致只切 UI 不写库、状态仍显示完诊。
 */
const RECEPTION_WRITE_STATUSES = new Set([
  'DISCHARGED',
  'ON_HOLD',
  'PLANNED',
  'REGISTERED',
  'TRIAGED',
]);

/** 与 `QUEUE_IN_PROGRESS` 比较用，避免接口返回大小写或数字枚举。 */
function normalizeQueueStatusEnum(value: DoctorStationPatient['statusEnum']): string {
  if (value === null || value === undefined || value === '') return '';
  return String(value).trim().toUpperCase();
}

/** 患者队列侧栏：按全部 / 待诊 / 在诊 / 完诊筛选 */
function matchDoctorQueueStatusFilter(
  statusEnum: DoctorStationPatient['statusEnum'],
  filter: DoctorWaitingQueueStatusFilter,
): boolean {
  if (filter === 'all') return true;
  const code = normalizeQueueStatusEnum(statusEnum);
  if (filter === 'waiting') return WAITING_STATUS_CODES.has(code);
  if (filter === 'in-progress') return IN_PROGRESS_STATUS_CODES.has(code);
  if (filter === 'completed') return COMPLETED_STATUS_CODES.has(code);
  return true;
}

/** 当前关键字结果集内、按状态筛选后的患者队列行 */
function filterWaitingPatientsByStatus(
  rows: DoctorStationPatient[],
  filter: DoctorWaitingQueueStatusFilter,
): DoctorStationPatient[] {
  return rows.filter((p) => matchDoctorQueueStatusFilter(p.statusEnum, filter));
}

/**
 * 按已记录的当前接诊 encounterId，从队列结果集刷新 `currentPatients`（0～1 人）。
 * 禁止用「左侧任意选中」填充，避免未接诊误入当前接诊。
 */
function refreshCurrentPatientsFromQueue(rows: DoctorStationPatient[]): void {
  const id = String(currentPatients.value[0]?.encounterId ?? '').trim();
  if (!id) {
    currentPatients.value = [];
    return;
  }
  const fresh = rows.find((p) => String(p.encounterId ?? '').trim() === id);
  if (fresh && IN_PROGRESS_STATUS_CODES.has(normalizeQueueStatusEnum(fresh.statusEnum))) {
    currentPatients.value = [fresh];
    return;
  }
  currentPatients.value = [];
}

/**
 * 患者队列拉取后：当前为「待诊」筛选且无待诊行时，自动切到「在诊」（仅在有在诊行时切换）。
 * 不改变「当前接诊 / 患者队列」顶栏；当前接诊仅由接诊流程程序切入。
 */
function maybeAutoSwitchQueueStatusFilter(
  rows: DoctorStationPatient[],
  options?: { skip?: boolean },
): void {
  if (options?.skip) return;
  if (patientQueue.value !== 'waiting') return;
  if (queueStatusFilter.value !== 'waiting') return;
  if (filterWaitingPatientsByStatus(rows, 'waiting').length > 0) return;
  if (filterWaitingPatientsByStatus(rows, 'in-progress').length === 0) return;
  queueStatusFilter.value = 'in-progress';
}

const authStore = useAuthStore();
const userStore = useUserStore();
const doctorPatientStore = useDoctorPatientStore();
const { selectedPatient, currentPatientInfo } = storeToRefs(doctorPatientStore);

/** 医生站列表/接诊写库用：当前登录医师主键（须与挂号/接诊参与者 practitionerId 一致，禁用 number 防雪花精度丢失） */
function resolveStaffPractitionerIdForInProgressQueue(): string | undefined {
  const raw = userStore.userInfo?.practitionerId;
  if (raw === null || raw === undefined || raw === '') {
    return undefined;
  }
  const normalized = String(raw).trim();
  if (!normalized) {
    return undefined;
  }
  return normalized;
}

/** 接诊分支用状态：与头信息条同源（同一就诊时取条上 `statusEnum`，否则取列表行）。 */
function statusEnumForReceptionDecision(
  row: DoctorStationPatient,
  info: PatientInfoBarPayload,
): string {
  const id = (row.encounterId ?? '').trim();
  const infoId = (info.encounterId ?? '').trim();
  const raw = id && id === infoId ? info.statusEnum : row.statusEnum;
  return normalizeQueueStatusEnum(raw);
}

// 标签枚举（不要直接用中文做 key）
const DOCTOR_TABS = {
  MEDICAL_RECORD: '门诊病历',
  DIAGNOSIS: '诊断录入',
  ORDER: '开立医嘱',
  SERVICE_APPLY: '申请单',
  TCM: '中医处方',
  PRESCRIPTION: '电子处方',
  REPORT_QUERY: '报告查询',
} as const;

type DoctorTab = (typeof DOCTOR_TABS)[keyof typeof DOCTOR_TABS];

/** 页签展示列表。电子处方未完成，暂不露出入口，组件与映射仍保留。 */
const visibleDoctorTabs = computed(() =>
  Object.values(DOCTOR_TABS).filter((tab) => tab !== DOCTOR_TABS.PRESCRIPTION),
);

// 当前激活的功能标签
const activeTab = ref<DoctorTab>(DOCTOR_TABS.MEDICAL_RECORD);
const patientQueue = ref<'current' | 'waiting'>('waiting');
/** 患者队列 Tab：待诊 / 在诊 / 完诊筛选，默认待诊 */
const queueStatusFilter = ref<DoctorWaitingQueueStatusFilter>('waiting');

/** 侧栏列表查询日 */
const listDateStr = ref(formatLocalDateYmd());
/** 待诊列表关键字（仅患者队列请求携带） */
const listKeyword = ref('');
const waitingPatients = ref<DoctorStationPatient[]>([]);
const currentPatients = ref<DoctorStationPatient[]>([]);
const listLoading = ref(false);
/** 正在提交接诊，避免双击等重复触发 */
const acceptingEncounterId = ref<null | string>(null);
/** 正在提交完诊，避免重复触发 */
const completingEncounterId = ref<null | string>(null);
/** 正在提交取消接诊，避免重复触发 */
const cancellingReceptionEncounterId = ref<null | string>(null);

const filteredPatients = computed(() => {
  if (patientQueue.value === 'current') return currentPatients.value;
  return filterWaitingPatientsByStatus(waitingPatients.value, queueStatusFilter.value);
});

/** 「患者队列」角标：仅统计展示为「待诊」的行（与 encounterQueueStatusDisplayText 的 REGISTERED 一致） */
const waitingPatientCount = computed(
  () =>
    waitingPatients.value.filter((p) => normalizeQueueStatusEnum(p.statusEnum) === 'REGISTERED')
      .length,
);

/** 吸底统计：当前列表结果集内待诊人数 */
const waitingStatusStatCount = computed(
  () =>
    waitingPatients.value.filter((p) => matchDoctorQueueStatusFilter(p.statusEnum, 'waiting'))
      .length,
);

/** 吸底统计：当前列表结果集内已接诊（在诊/暂离）人数 */
const receivedStatusStatCount = computed(
  () =>
    waitingPatients.value.filter((p) => matchDoctorQueueStatusFilter(p.statusEnum, 'in-progress'))
      .length,
);

/** 与侧栏「病历号」一致：就诊业务号优先，否则 encounterId */
function resolveOutpatientEmrMedicalRecordNo(p: DoctorStationPatient): string {
  const bus = (p.encounterBusNo ?? '').trim();
  if (bus) return bus;
  return (p.encounterId ?? '').trim();
}

/** 病历编辑器元数据仍使用 name/gender/age 等扁平字段 */
const emrPatientInfo = computed(() => {
  const p = selectedPatient.value;
  if (!p) return undefined;
  const ageRaw = (p.ageText ?? '').trim();
  const age = ageRaw.replace(/岁\s*$/u, '');
  return {
    name: p.patientName ?? '',
    gender: (p.genderEnumText ?? '').trim(),
    age,
    phone: p.phone ?? '',
    insurance: (p.contractDisplay ?? '').trim(),
    medicalRecordNo: resolveOutpatientEmrMedicalRecordNo(p),
    idCard: p.idCard ?? '',
  };
});

const doctorDisplayName = computed(() => {
  const u = userStore.userInfo;
  if (!u) return '—';
  return u.realName || u.name || u.userName || u.username || '—';
});

// 病历表单数据
const medicalRecordData = ref({});

const tabComponents = computed<Record<DoctorTab, Component>>(() => {
  return {
    [DOCTOR_TABS.MEDICAL_RECORD]: MedicalRecordForm,
    [DOCTOR_TABS.DIAGNOSIS]: DiagnosisList,
    [DOCTOR_TABS.ORDER]: AdviceList,
    [DOCTOR_TABS.SERVICE_APPLY]: ServiceApplyTab,
    [DOCTOR_TABS.TCM]: TcmAdvice,
    [DOCTOR_TABS.PRESCRIPTION]: ElepAdvice,
    [DOCTOR_TABS.REPORT_QUERY]: ReportQuery,
  };
});

/** 诊断/医嘱/病历等 Tab 注入就诊上下文 */
const doctorStationContextBindings = computed(() => {
  const encounterId = selectedPatient.value?.encounterId ?? '';
  const patientId = selectedPatient.value?.patientId ?? '';
  if (
    activeTab.value === DOCTOR_TABS.DIAGNOSIS ||
    activeTab.value === DOCTOR_TABS.ORDER ||
    activeTab.value === DOCTOR_TABS.TCM ||
    activeTab.value === DOCTOR_TABS.PRESCRIPTION
  ) {
    return { encounterId, patientId };
  }
  if (activeTab.value === DOCTOR_TABS.REPORT_QUERY) {
    return { encounterId, patientId, stationScope: 'outpatient' as const };
  }
  if (activeTab.value === DOCTOR_TABS.SERVICE_APPLY) {
    return {
      encounterId,
      patientId,
      serviceApplyPatient: serviceApplyPatientSummary.value,
      serviceApplyPrintMeta: serviceApplyPrintMeta.value,
    };
  }
  if (activeTab.value === DOCTOR_TABS.MEDICAL_RECORD) {
    return { encounterId, patientId, stationKind: 'OUTPATIENT' as const };
  }
  return {};
});

/** 拉取患者队列分页（不传 encounterStatus，后端按 BR-DOR-009 返回挂当前医生号的各状态就诊；在诊 Tab 仍由本地维护）。 */
async function loadDoctorStationLists(options?: {
  skipDefaultSelection?: boolean;
}): Promise<DoctorStationPatient[]> {
  const organizationId = Number(authStore.getSelectedOrganization());
  if (!organizationId || Number.isNaN(organizationId)) {
    ElMessage.warning('请先选择科室');
    waitingPatients.value = [];
    currentPatients.value = [];
    doctorPatientStore.setSelectedPatient(null);
    return [];
  }

  const staffPractitionerId = resolveStaffPractitionerIdForInProgressQueue();
  if (!staffPractitionerId) {
    ElMessage.warning('当前账号未关联医师身份，无法加载患者队列');
    waitingPatients.value = [];
    currentPatients.value = [];
    doctorPatientStore.setSelectedPatient(null);
    return [];
  }

  const { startDate, endDate } = getLocalDayRangeDateTimeStrings(listDateStr.value);

  listLoading.value = true;
  try {
    const waitingRes = await httpPageOutpatientInfoForDoctorStation({
      organizationId,
      staffPractitionerId,
      keyword: listKeyword.value.trim() || undefined,
      startDate,
      endDate,
      pageNo: 1,
      pageSize: DOCTOR_STATION_QUEUE_PAGE_SIZE,
    });

    const { content: waitingRows } = parsePagedRowsFromApiData<DoctorStationPatient>(waitingRes);
    waitingPatients.value = waitingRows;
    refreshCurrentPatientsFromQueue(waitingRows);

    maybeAutoSwitchQueueStatusFilter(waitingRows, { skip: options?.skipDefaultSelection });

    if (options?.skipDefaultSelection) return waitingRows;

    if (patientQueue.value === 'current') {
      if (currentPatients.value.length > 0) {
        await applySelectedPatientChange(currentPatients.value[0] ?? null);
        return waitingRows;
      }
      // 当前接诊人已失效（完诊/不在队列）→ 退回患者队列
      patientQueue.value = 'waiting';
    }

    const selId = (selectedPatient.value?.encounterId ?? '').trim();
    if (selId) {
      const visible = filterWaitingPatientsByStatus(waitingRows, queueStatusFilter.value);
      const fresh = visible.find((p) => String(p.encounterId ?? '').trim() === selId);
      if (fresh) {
        await applySelectedPatientChange(fresh);
        return waitingRows;
      }
    }
    const visible = filterWaitingPatientsByStatus(waitingRows, queueStatusFilter.value);
    await applySelectedPatientChange(visible[0] ?? null);
    return waitingRows;
  } catch {
    waitingPatients.value = [];
    currentPatients.value = [];
    return [];
  } finally {
    listLoading.value = false;
  }
}

/** 离开病历 Tab 或换患者前确认未保存（病历组件 KeepAlive 内注册守卫） */
async function guardEmrUnsavedBeforeLeaveMedicalRecord(): Promise<boolean> {
  return confirmEmrDiscardUnsaved();
}

/** 切换选中患者前做 EMR 未保存确认；返回 false 表示取消切换 */
async function applySelectedPatientChange(next: DoctorStationPatient | null): Promise<boolean> {
  const prevId = (selectedPatient.value?.encounterId ?? '').trim();
  const nextId = (next?.encounterId ?? '').trim();
  if (prevId && prevId !== nextId) {
    const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
    if (!ok) return false;
    // 换就诊清空医嘱/申请单共用的诊断会话记忆
    clearEncounterDiagnosisSession();
  }
  doctorPatientStore.setSelectedPatient(next);
  return true;
}

// 事件处理
const handleTabChange = async (tab: string) => {
  const next = tab as DoctorTab;
  if (activeTab.value === DOCTOR_TABS.MEDICAL_RECORD && next !== DOCTOR_TABS.MEDICAL_RECORD) {
    const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
    if (!ok) return;
  }
  activeTab.value = next;
  // 只记开药相关页签：西药医生常停在开立医嘱，中药医生常停在中医处方
  if (next === DOCTOR_TABS.ORDER || next === DOCTOR_TABS.TCM) {
    writeDoctorOrderTabPreference(next);
  }
};

const handlePatientSelect = async (patient: DoctorStationPatient) => {
  await applySelectedPatientChange(patient);
};

/**
 * 侧栏顶栏切换：仅允许手切回「患者队列」。
 * 「当前接诊」Radio 已 disabled，禁止手点进入；进入仅由接诊流程写 `patientQueue`。
 */
const handleQueueChange = async (queue: 'away' | 'completed' | 'current' | 'waiting') => {
  if (queue !== 'waiting') return;

  patientQueue.value = 'waiting';

  const selId = (selectedPatient.value?.encounterId ?? '').trim();
  if (selId) {
    const visible = filterWaitingPatientsByStatus(waitingPatients.value, queueStatusFilter.value);
    const stillSelected = visible.some((p) => String(p.encounterId ?? '').trim() === selId);
    if (stillSelected) return;
  }
  const visible = filterWaitingPatientsByStatus(waitingPatients.value, queueStatusFilter.value);
  if (visible.length > 0) {
    await applySelectedPatientChange(visible[0] ?? null);
  }
};

// 将关键字写回后重新拉取待诊列表（在诊 Tab 不传搜索）。
const handlePatientSearch = (keyword: string) => {
  listKeyword.value = keyword;
  loadDoctorStationLists();
};

const handleDateChange = (_date: string) => {
  loadDoctorStationLists();
};

const handleRefresh = () => {
  loadDoctorStationLists();
};

onMounted(() => {
  loadDoctorStationLists();
});

const handleAction = (_action: string) => {
  // TODO: 实现各种操作
};

const serviceApplyDialogVisible = ref(false);
const allergyDialogVisible = ref(false);
const serviceApplyKind = ref<ServiceApplyKind>('lab');

/** 检验/检查申请弹窗顶部患者摘要 */
const serviceApplyPatientSummary = computed<ServiceApplyPatientSummary>(() => {
  const info = currentPatientInfo.value;
  const gender = (info.genderEnumText ?? '').trim() || '—';
  const ageRaw = (info.ageText ?? '').trim();
  const age = ageRaw ? (ageRaw.includes('岁') ? ageRaw : `${ageRaw}岁`) : '—';
  return {
    patientName: (info.patientName ?? '').trim() || '—',
    genderText: gender,
    ageText: age,
    encounterNo: (info.medicalRecordNo ?? info.encounterId ?? '').trim() || '—',
    feeNatureText: (info.serviceTypeName ?? '').trim() || '普通门诊',
  };
});

/** 检验/检查申请单打印抬头 */
const serviceApplyPrintMeta = computed(() => ({
  hospitalName: resolvePrintHospitalName(),
  departmentName: (selectedPatient.value?.organizationName ?? '').trim(),
  orderingPractitionerName: doctorDisplayName.value === '—' ? '' : doctorDisplayName.value.trim(),
}));

/** 打开检验/检查/治疗申请弹窗（打开前自动接诊） */
async function openServiceApplyDialog(kind: ServiceApplyKind) {
  const ok = await ensureReceptionForClinicalWork();
  if (!ok) return;
  serviceApplyKind.value = kind;
  serviceApplyDialogVisible.value = true;
}

/** 申请单暂存/签发成功后刷新当前「申请单」列表 */
async function handleServiceApplyPersisted() {
  if (activeTab.value !== DOCTOR_TABS.SERVICE_APPLY) return;
  await nextTick();
  const inst = tabPanelRef.value as undefined | { reload?: () => Promise<void> };
  if (inst && typeof inst.reload === 'function') {
    await inst.reload();
  }
}

const handleQuickAction = async (
  action:
    | 'allergy'
    | 'exam'
    | 'lab'
    | 'pathology'
    | 'report'
    | 'surgery'
    | 'transfusion'
    | 'treatment',
) => {
  if (!ensurePatientSelected()) return;
  if (action === 'report') {
    if (activeTab.value === DOCTOR_TABS.MEDICAL_RECORD) {
      const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
      if (!ok) return;
    }
    activeTab.value = DOCTOR_TABS.REPORT_QUERY;
    return;
  }
  if (action === 'lab' || action === 'exam' || action === 'treatment') {
    activeTab.value = DOCTOR_TABS.SERVICE_APPLY;
    await openServiceApplyDialog(action);
    return;
  }
  if (action === 'allergy') {
    allergyDialogVisible.value = true;
    return;
  }
  const actionTextMap: Record<
    Exclude<typeof action, 'allergy' | 'exam' | 'lab' | 'report' | 'treatment'>,
    string
  > = {
    transfusion: '输血申请',
    pathology: '病理申请',
    surgery: '手术申请',
  };
  ElMessage.info(`${actionTextMap[action]}功能待实现`);
};

/**
 * 接诊或切在诊：与双击卡片同源；返回是否成功（含「已接诊仅切 UI」）。
 * 临床写操作 / 打开申请单弹窗等通过 `ensureReceptionForClinicalWork` 间接调用。
 */
async function ensureReceptionForPatient(patient: DoctorStationPatient): Promise<boolean> {
  if (acceptingEncounterId.value !== null) {
    ElMessage.warning('接诊处理中，请稍候');
    return false;
  }

  const status = statusEnumForReceptionDecision(patient, currentPatientInfo.value);

  // IN_PROGRESS 等不在写接口集合内的状态：不调接诊写接口，但仍须切「当前接诊」并选中（禁止在此处 return 掉切换）。
  if (!RECEPTION_WRITE_STATUSES.has(status)) {
    patientQueue.value = 'current';
    currentPatients.value = [patient];
    doctorPatientStore.setSelectedPatient(patient);
    return true;
  }

  const me = userStore.userInfo;
  if (!me) {
    ElMessage.warning('请先登录后再接诊');
    return false;
  }

  acceptingEncounterId.value = patient.encounterId;
  try {
    const doctorPractitionerId = resolveStaffPractitionerIdForInProgressQueue();
    if (!doctorPractitionerId) {
      ElMessage.warning('当前账号未关联医师身份，无法接诊');
      return false;
    }
    await httpStartOutpatientReception({
      encounterId: patient.encounterId,
      doctorId: doctorPractitionerId,
      operatorId: doctorPractitionerId,
      directFromPlanned: true,
    });
    ElMessage.success('接诊成功');
    const waitingRows = await loadDoctorStationLists({ skipDefaultSelection: true });
    patientQueue.value = 'current';
    const encounterKey = patient.encounterId.trim();
    const fresh = waitingRows.find((row) => String(row.encounterId ?? '').trim() === encounterKey);
    const inProgressPatient: DoctorStationPatient =
      fresh ??
      ({
        ...patient,
        statusEnum: QUEUE_IN_PROGRESS,
      } as DoctorStationPatient);
    currentPatients.value = [inProgressPatient];
    doctorPatientStore.setSelectedPatient(inProgressPatient);
    return true;
  } catch (error) {
    ElMessage.error(resolveRequestErrorMessage(error, '接诊失败，请稍后重试'));
    return false;
  } finally {
    acceptingEncounterId.value = null;
  }
}

/** 接诊前是否已有在诊/暂离患者（决定双击接诊后右侧页签：无 → 病历，有 → 开药偏好页签） */
function hasInProgressConsultationBeforeAccept(): boolean {
  if (currentPatients.value.length > 0) return true;
  return waitingPatients.value.some((p) =>
    IN_PROGRESS_STATUS_CODES.has(normalizeQueueStatusEnum(p.statusEnum)),
  );
}

/** 二次接诊落点：读本机开药偏好（开立医嘱 / 中医处方） */
function resolvePostAcceptOrderTab(): DoctorTab {
  const pref = readDoctorOrderTabPreference();
  if (pref === DOCTOR_TABS.TCM) {
    return DOCTOR_TABS.TCM;
  }
  return DOCTOR_TABS.ORDER;
}

/** 当前选中患者：临床写操作前自动接诊 / 切在诊（供子页 inject）。 */
const ensureReceptionForClinicalWork: EnsureClinicalReceptionFn = async () => {
  const p = selectedPatient.value;
  if (!p) {
    ElMessage.warning('请先选择患者');
    return false;
  }
  return ensureReceptionForPatient(p);
};

provide(ENSURE_CLINICAL_RECEPTION_KEY, ensureReceptionForClinicalWork);

/** 常用诊断维护变更计数：诊断 Tab 注入后刷新快捷树 */
const commonDxMaintainVersion = ref(0);
provide(COMMON_DIAGNOSIS_MAINTAIN_VERSION_KEY, commonDxMaintainVersion);

// 双击待诊卡片：RECEPTION_WRITE_STATUSES 内调接诊写接口；其余不调接口，仅切在诊并选中。
const handlePatientAccept = async (patient: DoctorStationPatient) => {
  if (acceptingEncounterId.value !== null) return;
  const prevId = (selectedPatient.value?.encounterId ?? '').trim();
  const nextId = (patient.encounterId ?? '').trim();
  if (prevId && nextId && prevId !== nextId) {
    const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
    if (!ok) return;
  }
  const navigateToOrder = hasInProgressConsultationBeforeAccept();
  const accepted = await ensureReceptionForPatient(patient);
  if (!accepted) return;
  // 首诊进病历；后续接诊按该医生上次开药页签（西药/中药）跳转
  activeTab.value = navigateToOrder ? resolvePostAcceptOrderTab() : DOCTOR_TABS.MEDICAL_RECORD;
};

const handleFormUpdate = (data: Record<string, unknown>) => {
  medicalRecordData.value = data;
};

const ensurePatientSelected = () => {
  if (!selectedPatient.value) {
    ElMessage.warning('请先选择患者');
    return false;
  }
  return true;
};

/** 主内容区当前页签若暴露 `reload`（如诊断录入），则与激活页签共用同一套拉数逻辑 */
const tabPanelRef = ref<null | { reload?: () => Promise<void> | void }>(null);

async function handleToolbarRefresh() {
  const inst = tabPanelRef.value;
  if (inst && typeof inst.reload === 'function') {
    await inst.reload();
  }
}

/** 完诊：校验状态、确认后调用 IF-DOR-04 并刷新患者队列 */
async function handleCompleteVisit(): Promise<void> {
  if (!ensurePatientSelected()) return;
  if (completingEncounterId.value !== null) {
    ElMessage.warning('完诊处理中，请稍候');
    return;
  }

  const patient = selectedPatient.value;
  if (!patient) return;

  const status = statusEnumForReceptionDecision(patient, currentPatientInfo.value);
  if (WAITING_STATUS_CODES.has(status)) {
    ElMessage.warning('当前患者处于待诊状态，不能完诊');
    return;
  }
  if (COMPLETED_STATUS_CODES.has(status)) {
    ElMessage.warning('当前患者已完诊');
    return;
  }
  if (!IN_PROGRESS_STATUS_CODES.has(status)) {
    ElMessage.warning('当前状态不允许完诊');
    return;
  }

  const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
  if (!ok) return;

  const patientName = (patient.patientName ?? '').trim() || '当前患者';
  try {
    await ElMessageBox.confirm(
      `完诊后患者将进入「完诊」列表，也可在完诊列表中重新接诊。确定完成患者【${patientName}】的就诊？`,
      '完诊确认',
      { type: 'warning', confirmButtonText: '确定完诊', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }

  const operatorId = resolveStaffPractitionerIdForInProgressQueue();
  if (!operatorId) {
    ElMessage.warning('当前账号未关联医师身份，无法完诊');
    return;
  }

  const encounterId = (patient.encounterId ?? '').trim();
  if (!encounterId) {
    ElMessage.warning('缺少就诊标识，无法完诊');
    return;
  }

  completingEncounterId.value = encounterId;
  try {
    await httpCompleteOutpatientEncounter({
      encounterId,
      operatorId,
    });
    ElMessage.success('完诊成功');
    doctorPatientStore.setSelectedPatient(null);
    currentPatients.value = [];
    patientQueue.value = 'waiting';
    await loadDoctorStationLists();
  } catch (error) {
    ElMessage.error(resolveRequestErrorMessage(error, '完诊失败，请稍后重试'));
  } finally {
    completingEncounterId.value = null;
  }
}

/**
 * 取消接诊：在诊/暂离/完诊回退待诊，表示医生同意窗口退号。
 * 须先删净本诊病历/诊断/医嘱；待诊不可取消；成功后清空当前接诊并刷新队列。
 */
async function handleCancelReception(): Promise<void> {
  if (!ensurePatientSelected()) return;
  if (cancellingReceptionEncounterId.value !== null) {
    ElMessage.warning('取消接诊处理中，请稍候');
    return;
  }

  const patient = selectedPatient.value;
  if (!patient) return;

  const status = statusEnumForReceptionDecision(patient, currentPatientInfo.value);
  // 仅已接诊后的状态可取消（与后端 assertCancelReceptionAllowed 对齐）。
  if (WAITING_STATUS_CODES.has(status)) {
    ElMessage.warning('当前患者尚未接诊，无需取消接诊');
    return;
  }
  if (!IN_PROGRESS_STATUS_CODES.has(status) && !COMPLETED_STATUS_CODES.has(status)) {
    ElMessage.warning('当前状态不允许取消接诊');
    return;
  }

  const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
  if (!ok) return;

  const patientName = (patient.patientName ?? '').trim() || '当前患者';
  try {
    await ElMessageBox.confirm(
      `取消接诊前须已删除本诊全部病历、诊断与医嘱；确认后患者将回到「待诊」，收费窗口方可退号。确定取消患者【${patientName}】的接诊？`,
      '取消接诊确认',
      { type: 'warning', confirmButtonText: '确定取消接诊', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }

  const operatorId = resolveStaffPractitionerIdForInProgressQueue();
  if (!operatorId) {
    ElMessage.warning('当前账号未关联医师身份，无法取消接诊');
    return;
  }

  const encounterId = (patient.encounterId ?? '').trim();
  if (!encounterId) {
    ElMessage.warning('缺少就诊标识，无法取消接诊');
    return;
  }

  cancellingReceptionEncounterId.value = encounterId;
  try {
    await httpCancelOutpatientReception({
      encounterId,
      operatorId,
    });
    ElMessage.success('已取消接诊');
    doctorPatientStore.setSelectedPatient(null);
    currentPatients.value = [];
    patientQueue.value = 'waiting';
    await loadDoctorStationLists();
  } catch (error) {
    ElMessage.error(resolveRequestErrorMessage(error, '取消接诊失败，请稍后重试'));
  } finally {
    cancellingReceptionEncounterId.value = null;
  }
}

const handleTemporaryLeave = () => {
  if (!ensurePatientSelected()) return;
  ElMessage.info('暂离功能待实现');
};

const refundDialogVisible = ref(false);
const admissionDialogVisible = ref(false);
const prescriptionSheetDialogVisible = ref(false);
const commonDxMaintainDialogVisible = ref(false);
const emrTemplateMaintainDialogVisible = ref(false);

/** 打开病历模板维护（个人/科室） */
function handleMaintainEmrTemplate() {
  emrTemplateMaintainDialogVisible.value = true;
}

/** 模板维护变更后：若当前在病历 Tab 则刷新侧栏模板树 */
function handleEmrTemplateMaintainChanged() {
  if (activeTab.value !== DOCTOR_TABS.MEDICAL_RECORD) return;
  const inst = tabPanelRef.value as null | { refreshTemplateTree?: () => unknown };
  if (inst && typeof inst.refreshTemplateTree === 'function') {
    void inst.refreshTemplateTree();
  }
}

/** 打开诊断维护（个人/科室常用） */
function handleMaintainCommonDiagnosis() {
  commonDxMaintainDialogVisible.value = true;
}

/** 维护弹窗变更后递增版本，驱动诊断快捷树刷新 */
function handleCommonDiagnosisMaintainChanged() {
  commonDxMaintainVersion.value += 1;
}
const prescriptionPrintSubmitting = ref(false);

/** 打开申请退费弹窗（须已选就诊；打开前自动接诊） */
const handleRefund = async () => {
  if (!ensurePatientSelected()) return;
  const ok = await ensureReceptionForClinicalWork();
  if (!ok) return;
  refundDialogVisible.value = true;
};

/** 退费申请提交成功：刷新侧栏队列；当前在开立医嘱时同步刷新医嘱列表（收费/退费状态可能已变） */
const handleRefundSubmitted = async () => {
  void loadDoctorStationLists({ skipDefaultSelection: true });
  if (activeTab.value !== DOCTOR_TABS.ORDER) return;
  await nextTick();
  const inst = tabPanelRef.value;
  if (inst && typeof inst.reload === 'function') {
    await inst.reload();
  }
};

/** 打开处方单查看弹窗（仅展示已签发药品） */
const handlePrescription = () => {
  if (!ensurePatientSelected()) return;
  const patientId = (selectedPatient.value?.patientId ?? '').trim();
  if (!patientId) {
    ElMessage.warning('缺少患者标识，无法查看处方单');
    return;
  }
  prescriptionSheetDialogVisible.value = true;
};

/** 打印当前就诊已签发中西药处方（直接用处方单笺面数据，不依赖收费） */
const handlePrintPrescription = async () => {
  if (!ensurePatientSelected()) return;
  const encounterId = preserveApiIdForRequest(selectedPatient.value?.encounterId);
  const patientId = preserveApiIdForRequest(selectedPatient.value?.patientId);
  const patient = selectedPatient.value;
  if (!encounterId || !patientId || !patient) {
    ElMessage.warning('缺少就诊或患者信息，无法打印处方');
    return;
  }
  prescriptionPrintSubmitting.value = true;
  try {
    const result = await printDoctorStationPrescriptions({
      encounterId,
      patientId,
      patient,
      prescriptionScopeTabCode: 'CURRENT_ENCOUNTER',
      jobName: '门诊医生站处方',
    });
    if (!result.ok) {
      ElMessage.warning('暂无已签发的处方可打印');
      return;
    }
    ElMessage.success(`已发送 ${result.count} 张处方单打印`);
  } catch {
    ElMessage.error('处方打印失败');
  } finally {
    prescriptionPrintSubmitting.value = false;
  }
};

/** 打开办理住院弹窗（须已选就诊；打开前自动接诊） */
const handleAdmission = async () => {
  if (!ensurePatientSelected()) return;
  const ok = await ensureReceptionForClinicalWork();
  if (!ok) return;
  admissionDialogVisible.value = true;
};
</script>

<template>
  <div class="flex h-full flex-row pt-2">
    <DoctorPatientList
      v-model:filter-date="listDateStr"
      v-model:queue-status-filter="queueStatusFilter"
      :patients="filteredPatients"
      :current-queue="patientQueue"
      :waiting-count="waitingPatientCount"
      :waiting-status-stat-count="waitingStatusStatCount"
      :received-status-stat-count="receivedStatusStatCount"
      :loading="listLoading"
      :reception-locked="acceptingEncounterId !== null"
      :selected-patient-id="selectedPatient?.encounterId"
      @accept="handlePatientAccept"
      @queue-change="handleQueueChange"
      @quick-action="handleQuickAction"
      @select="handlePatientSelect"
      @search="handlePatientSearch"
      @date-change="handleDateChange"
      @refresh="handleRefresh"
    />

    <!-- 主内容区 -->
    <div class="bg-background-page ml-[2px] flex min-h-0 flex-1 flex-col overflow-hidden">
      <PatientInfoBar :patient="currentPatientInfo" :doctor-name="doctorDisplayName" />
      <div class="flex min-h-0 flex-1 flex-col">
        <DoctorHeader
          :patient="currentPatientInfo"
          :doctor-name="doctorDisplayName"
          :active-tab="activeTab"
          :tabs="visibleDoctorTabs"
          @tab-change="handleTabChange"
          @action="handleAction"
        >
          <template #actions>
            <ElButton type="primary" link @click="handleToolbarRefresh">刷新</ElButton>
            <ElButton type="primary" link @click="handleCompleteVisit">完诊</ElButton>
            <ElButton type="primary" link @click="handleCancelReception">取消接诊</ElButton>
            <ElButton type="primary" link @click="handleTemporaryLeave"> 暂离 </ElButton>
            <ElButton type="primary" link @click="handleRefund">退费</ElButton>
            <ElButton type="primary" link @click="handlePrescription"> 处方单 </ElButton>
            <ElButton
              type="primary"
              link
              :loading="prescriptionPrintSubmitting"
              @click="handlePrintPrescription"
            >
              打印处方
            </ElButton>
            <ElButton type="primary" link @click="handleMaintainEmrTemplate"> 病历模板 </ElButton>
            <ElButton type="primary" link @click="handleMaintainCommonDiagnosis">
              诊断维护
            </ElButton>
            <ElButton type="primary" link @click="handleAdmission">办理住院</ElButton>
          </template>
        </DoctorHeader>
        <div class="flex min-h-0 flex-1 flex-col gap-0">
          <div
            class="border-border-base bg-background-card ml-0 flex min-h-0 flex-1 flex-col overflow-hidden rounded-none border border-t-0 shadow-sm"
          >
            <KeepAlive>
              <component
                :is="tabComponents[activeTab]"
                ref="tabPanelRef"
                :key="activeTab"
                v-if="tabComponents[activeTab]"
                class="h-full min-h-0 overflow-hidden"
                :patient-info="emrPatientInfo"
                :form-data="medicalRecordData"
                v-bind="doctorStationContextBindings"
                @update="handleFormUpdate"
                @open-apply="openServiceApplyDialog"
              />
            </KeepAlive>
          </div>
        </div>
      </div>
    </div>

    <RefundApplicationDialog
      v-model="refundDialogVisible"
      :encounter-id="selectedPatient?.encounterId ?? ''"
      :patient-name="selectedPatient?.patientName ?? ''"
      @submitted="handleRefundSubmitted"
    />

    <InpatientAdmissionDialog
      v-model="admissionDialogVisible"
      :amb-encounter-id="selectedPatient?.encounterId ?? ''"
      :patient-id="selectedPatient?.patientId ?? ''"
      :patient="selectedPatient"
    />

    <PrescriptionSheetDialog
      v-model:visible="prescriptionSheetDialogVisible"
      :encounter-id="selectedPatient?.encounterId ?? ''"
      :patient-id="selectedPatient?.patientId ?? ''"
      :patient="selectedPatient"
    />

    <PatientAllergyHistoryDialog
      v-model="allergyDialogVisible"
      :patient-id="selectedPatient?.patientId ?? ''"
      :encounter-id="selectedPatient?.encounterId ?? ''"
      default-verification-status="CONFIRMED"
    />

    <ServiceApplyDialog
      v-model="serviceApplyDialogVisible"
      :apply-kind="serviceApplyKind"
      :encounter-id="selectedPatient?.encounterId ?? ''"
      :patient-id="selectedPatient?.patientId ?? ''"
      :patient="serviceApplyPatientSummary"
      :print-meta="serviceApplyPrintMeta"
      @saved="handleServiceApplyPersisted"
    />

    <CommonDiagnosisMaintainDialog
      v-model="commonDxMaintainDialogVisible"
      station-scope="outpatient"
      @changed="handleCommonDiagnosisMaintainChanged"
    />

    <EmrTemplateMaintainDialog
      v-model="emrTemplateMaintainDialogVisible"
      :station-kind="EMR_TEMPLATE_STATION_KIND.OUTPATIENT"
      @changed="handleEmrTemplateMaintainChanged"
    />
  </div>
</template>
