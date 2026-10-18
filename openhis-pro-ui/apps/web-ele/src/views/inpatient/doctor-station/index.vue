<script setup lang="ts">
import type { Component } from 'vue';

import type { InpatientDoctorStationPatient } from './doctorPatientStore';

import type { EnsureClinicalReceptionFn } from '#/components/doctor-station';
import type {
  ServiceApplyKind,
  ServiceApplyPatientSummary,
} from '#/views/outpatient/doctor-station/service-apply/service-apply.types';

import {
  computed,
  defineAsyncComponent,
  nextTick,
  onDeactivated,
  onMounted,
  onUnmounted,
  provide,
  ref,
  watch,
} from 'vue';
import { useRouter } from 'vue-router';

import { useUserStore } from '@zxplat/stores';

import { ElButton, ElMessage } from 'element-plus';
import { storeToRefs } from 'pinia';

import PatientAllergyHistoryDialog from '#/components/clinical/patient-allergy-history-dialog.vue';

import {
  DiagnosisList,
  ENSURE_CLINICAL_RECEPTION_KEY,
  ReportQuery,
  TcmAdvice,
} from '#/components/doctor-station';
import CommonDiagnosisMaintainDialog from '#/components/doctor-station/diagnosis/components/common-diagnosis-maintain-dialog.vue';
import { COMMON_DIAGNOSIS_MAINTAIN_VERSION_KEY } from '#/components/doctor-station/diagnosis/common-diagnosis-maintain.context';
import DoctorHeader from '#/components/outpatient/doctor-header.vue';
import MedicalRecordForm from '#/components/outpatient/medical-record-form.vue';
import PatientInfoBar from '#/components/outpatient/patient-info-bar.vue';
import { useEncounterFeeSummary } from '#/components/outpatient/use-encounter-fee-summary';
import { confirmEmrDiscardUnsaved } from '#/emrEditor';
import { useAuthStore } from '#/store';
import { isEncounterClinicalOrderMutationBlocked } from '#/utils/inpatient/encounter-clinical-order-gate';
import {
  inferServiceApplyKindFromAdviceRow,
  type ServiceApplyDocumentGroup,
} from '#/views/outpatient/doctor-station/service-apply/service-apply-doc-groups';
import ServiceApplyDialog from '#/views/outpatient/doctor-station/service-apply/service-apply-dialog.vue';

import { InpatientAdviceList } from './advice';
import InpatientSurgeryAnesthApplyTab from './surgery-anesth/surgery-anesth-apply-tab.vue';
import { clearInpatientAdviceClipboard } from './advice/services/inpatient-advice-clipboard';
import InpatientDischargeAdviceDialog from './components/inpatient-discharge-advice-dialog.vue';
import InpatientNursingAdviceDialog from './components/inpatient-nursing-advice-dialog.vue';
import PatientLargeScreen from './components/patient-large-screen.vue';
import DoctorPatientList from './doctor-patient-list.vue';
import { useInpatientDoctorPatientStore } from './doctorPatientStore';
import { mapDoctorPatientToNurseModulePatient } from './map-doctor-patient-to-nurse-module-patient';
import { useInpatientDoctorPatientQueue } from './use-inpatient-doctor-patient-queue';
import type { AuditViolation } from '#/chs/api/detail-audit-try';
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
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { tryCrbSsoOnDoctorStationEnter } from '#/utils/crb/try-crb-sso-on-doctor-station';
import { INPATIENT_DOCTOR_SESSION_MAX } from './session/inpatient-doctor-session.constants';
import InpatientDoctorSessionTabs from './session/inpatient-doctor-session-tabs.vue';
import { useInpatientDoctorSessionStore } from './session/inpatient-doctor-session-store';
import { resolveDoctorSessionEncounterId } from './session/map-inpatient-doctor-session';
import { INPATIENT_DOCTOR_MULTI_SESSION_ENABLED } from '#/views/inpatient/doctor-station/inpatient-doctor-multi-session.config';
import { DOCTOR_STATION_LARGE_SCREEN_DEFAULT_OPEN } from '#/views/inpatient/doctor-station/patient-large-screen-default.config';
import { INPATIENT_SURGERY_ANESTH_ENABLED } from '#/views/inpatient/surgery-anesth/inpatient-surgery-anesth.config';
import '#/views/inpatient/doctor-station/inpatient-doctor-station-session.css';

defineOptions({
  name: 'InpatientDoctorStation',
});

// 标签枚举（住院侧不含门诊「电子处方」；体温单/护理记录/费用明细复用护士站模块）
const DOCTOR_TABS = {
  MEDICAL_RECORD: '住院病历',
  DIAGNOSIS: '诊断录入',
  ORDER: '开立医嘱',
  SURGERY: '手术',
  ANESTHESIA: '麻醉',
  TCM: '中医处方',
  REPORT_QUERY: '报告查询',
  TEMPERATURE_SHEET: '体温单',
  NURSING_RECORD: '护理记录',
  COST_DETAILS: '费用明细',
} as const;

type DoctorTab = (typeof DOCTOR_TABS)[keyof typeof DOCTOR_TABS];

/** 页签：红旗院点去掉手术、麻醉 */
const doctorTabs = computed(() => {
  const tabs = Object.values(DOCTOR_TABS);
  if (INPATIENT_SURGERY_ANESTH_ENABLED) {
    return tabs;
  }
  return tabs.filter((tab) => tab !== DOCTOR_TABS.SURGERY && tab !== DOCTOR_TABS.ANESTHESIA);
});

/** 复用护士站模块的 Tab（入参为 patients[]） */
const NURSE_REUSED_TABS = new Set<DoctorTab>([
  DOCTOR_TABS.TEMPERATURE_SHEET,
  DOCTOR_TABS.NURSING_RECORD,
  DOCTOR_TABS.COST_DETAILS,
]);

const NurseStationTemperatureSheet = defineAsyncComponent(
  () => import('#/views/inpatient/nurse-station/temperature-sheet/index.vue'),
);
const NurseStationNursingRecord = defineAsyncComponent(
  () => import('#/views/inpatient/nurse-station/nursing-record/index.vue'),
);
const NurseStationCostDetails = defineAsyncComponent(
  () => import('#/views/inpatient/nurse-station/cost-details/index.vue'),
);

const authStore = useAuthStore();
const userStore = useUserStore();
const router = useRouter();
const doctorPatientStore = useInpatientDoctorPatientStore();
const sessionStore = useInpatientDoctorSessionStore();
const { selectedPatient, currentPatientInfo } = storeToRefs(doctorPatientStore);
const { sessions, activeEncounterId } = storeToRefs(sessionStore);
/** 国基 true：多人页签；红旗/产品 false：同时只看一人 */
const multiSessionEnabled = INPATIENT_DOCTOR_MULTI_SESSION_ENABLED;
const feeEncounterId = computed(() => String(selectedPatient.value?.encounterId ?? '').trim());
const feePatientId = computed(() => String(selectedPatient.value?.patientId ?? '').trim());
const { feeSummary, refreshEncounterFee } = useEncounterFeeSummary({
  encounterId: feeEncounterId,
  patientId: feePatientId,
  scope: 'inpatient',
});

const activeTab = ref<DoctorTab>(DOCTOR_TABS.MEDICAL_RECORD);
const sessionSwitching = ref(false);
let sessionSwitchTimer: ReturnType<typeof setTimeout> | undefined;
/** 进站是否默认展开患者大屏（国基 true / 红旗与产品 false，见 patient-large-screen-default.config） */
const largeScreenVisible = ref(DOCTOR_STATION_LARGE_SCREEN_DEFAULT_OPEN);
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
const clinicalPreSettleAuditViolations = ref<AuditViolation[]>([]);
const clinicalPreSettleAuditNote = ref('');

const { assertOrganizationSelected, inHospitalPatients, listKeyword, listLoading, loadQueue } =
  useInpatientDoctorPatientQueue();

const doctorDisplayName = computed(() => {
  const u = userStore.userInfo;
  if (!u) return '—';
  return u.realName || u.name || u.userName || u.username || '—';
});

/** 待结算/已结算/已离院：禁用出院、护理等开立类入口（查看不受影响） */
const adviceWriteDisabled = computed(() =>
  isEncounterClinicalOrderMutationBlocked(selectedPatient.value?.statusEnum),
);

/** 病历编辑器元数据：性别优先读模型文本字段；病房优先 houseName；床号随当前就诊带出 */
function buildEmrPatientInfo(p: InpatientDoctorStationPatient | null | undefined) {
  if (!p) return undefined;
  const ageRaw = (p.ageText ?? '').trim();
  const age = ageRaw.replace(/岁\s*$/u, '');
  const wardName = (p.houseName ?? '').trim() || (p.wardName ?? '').trim();
  const bedRaw = (p.bedName ?? '').trim();
  // 召回/清床展示「原房-床（未占床）」时页眉只带中间段
  const unoccupiedMatch = /^原(.+)（未占床）$/.exec(bedRaw);
  const bedNo = (unoccupiedMatch?.[1] ?? bedRaw).trim();
  return {
    name: p.patientName ?? '',
    gender: (p.genderEnumText ?? '').trim(),
    age,
    phone: (p.phone ?? '').trim(),
    insurance: (p.contractDisplay ?? '').trim(),
    medicalRecordNo: (p.encounterBusNo ?? '').trim(),
    idCard: (p.idCard ?? '').trim(),
    deptName: (p.organizationName ?? '').trim(),
    wardName,
    bedNo,
  };
}

const emrPatientInfo = computed(() => buildEmrPatientInfo(selectedPatient.value));

function emrBindingsFor(patient: InpatientDoctorStationPatient) {
  return {
    encounterId: patient.encounterId ?? '',
    patientId: patient.patientId ?? '',
    stationKind: 'INPATIENT' as const,
    archiveEnabled: false,
    hostOwnsUnsavedGuard: multiSessionEnabled,
    patientInfo: buildEmrPatientInfo(patient),
  };
}

function isEmrPaneActive(patient: InpatientDoctorStationPatient) {
  return (
    activeTab.value === DOCTOR_TABS.MEDICAL_RECORD &&
    resolveDoctorSessionEncounterId(patient) ===
      resolveDoctorSessionEncounterId(selectedPatient.value)
  );
}

const medicalRecordData = ref({});

const tabComponents = computed<Record<DoctorTab, Component>>(() => ({
  [DOCTOR_TABS.MEDICAL_RECORD]: MedicalRecordForm,
  [DOCTOR_TABS.DIAGNOSIS]: DiagnosisList,
  [DOCTOR_TABS.ORDER]: InpatientAdviceList,
  [DOCTOR_TABS.SURGERY]: InpatientSurgeryAnesthApplyTab,
  [DOCTOR_TABS.ANESTHESIA]: InpatientSurgeryAnesthApplyTab,
  [DOCTOR_TABS.TCM]: TcmAdvice,
  [DOCTOR_TABS.REPORT_QUERY]: ReportQuery,
  [DOCTOR_TABS.TEMPERATURE_SHEET]: NurseStationTemperatureSheet,
  [DOCTOR_TABS.NURSING_RECORD]: NurseStationNursingRecord,
  [DOCTOR_TABS.COST_DETAILS]: NurseStationCostDetails,
}));

/** 诊断/医嘱/病历等 Tab 注入就诊上下文；护士复用 Tab 传 patients[] */
const doctorStationContextBindings = computed(() => {
  const tab = activeTab.value;
  if (NURSE_REUSED_TABS.has(tab)) {
    const patient = selectedPatient.value;
    const patients = patient ? [mapDoctorPatientToNurseModulePatient(patient)] : [];
    // 体温单/护理记录只读；费用明细隐藏模块内患者头（外层已有）
    if (tab === DOCTOR_TABS.TEMPERATURE_SHEET || tab === DOCTOR_TABS.NURSING_RECORD) {
      return { patients, readonly: true };
    }
    return { patients, hidePatientHeader: true };
  }
  const base = {
    encounterId: selectedPatient.value?.encounterId ?? '',
    patientId: selectedPatient.value?.patientId ?? '',
  };
  if (tab === DOCTOR_TABS.MEDICAL_RECORD) {
    // 住院文书归档改在病案首页「病历」；医生站仅书写/保存
    return {
      ...base,
      stationKind: 'INPATIENT' as const,
      archiveEnabled: false,
      hostOwnsUnsavedGuard: multiSessionEnabled,
    };
  }
  if (
    tab !== DOCTOR_TABS.DIAGNOSIS &&
    tab !== DOCTOR_TABS.ORDER &&
    tab !== DOCTOR_TABS.SURGERY &&
    tab !== DOCTOR_TABS.ANESTHESIA &&
    tab !== DOCTOR_TABS.TCM &&
    tab !== DOCTOR_TABS.REPORT_QUERY
  ) {
    return {};
  }
  if (tab === DOCTOR_TABS.DIAGNOSIS) {
    return {
      ...base,
      patientServiceTypeName: selectedPatient.value?.organizationName ?? '',
      stationScope: 'inpatient' as const,
    };
  }
  if (tab === DOCTOR_TABS.SURGERY || tab === DOCTOR_TABS.ANESTHESIA) {
    const patient = selectedPatient.value;
    return {
      ...base,
      formKind: tab === DOCTOR_TABS.SURGERY ? 'SURGERY' : 'ANESTHESIA',
      writeDisabled: adviceWriteDisabled.value,
      admissionTime: String(patient?.startTime ?? patient?.admissionTime ?? '').trim(),
      dischargeTime: String(patient?.endTime ?? '').trim(),
    };
  }
  if (tab === DOCTOR_TABS.TCM) {
    return { ...base, stationScope: 'inpatient' as const };
  }
  if (tab === DOCTOR_TABS.REPORT_QUERY) {
    return { ...base, stationScope: 'inpatient' as const };
  }
  return base;
});

/** 同步选中患者：多开只回写已打开页签；单人刷新后尽量保留原选中行 */
async function syncSelectedPatientAfterQueueLoad(
  rows: InpatientDoctorStationPatient[],
  skipDefaultSelection?: boolean,
) {
  if (multiSessionEnabled) {
    sessionStore.syncFromQueue(rows);
    if (sessionStore.activeSession) {
      applyActiveSession(sessionStore.activeSession);
    }
    return;
  }
  if (skipDefaultSelection) {
    return;
  }
  const selId = (selectedPatient.value?.encounterId ?? '').trim();
  const keep = selId && rows.some((row) => row.encounterId.trim() === selId);
  if (keep && selectedPatient.value) {
    const fresh = rows.find((row) => row.encounterId.trim() === selId);
    await applySelectedPatientChange(fresh ?? selectedPatient.value);
    return;
  }
  await applySelectedPatientChange(rows[0] ?? null);
}

/** 拉取在院患者队列（IF-IP-DOR-01） */
async function loadInpatientDoctorStationList(options?: { skipDefaultSelection?: boolean }) {
  if (!assertOrganizationSelected(() => Number(authStore.getSelectedOrganization()))) {
    if (multiSessionEnabled) {
      sessionStore.closeAll();
    }
    doctorPatientStore.setSelectedPatient(null);
    return;
  }
  try {
    const result = await loadQueue(options);
    if (!result?.rows) {
      return;
    }
    await syncSelectedPatientAfterQueueLoad(result.rows, options?.skipDefaultSelection);
  } catch {
    inHospitalPatients.value = [];
    if (multiSessionEnabled) {
      sessionStore.closeAll();
    }
    doctorPatientStore.setSelectedPatient(null);
  }
}

const sessionPanelKey = computed(() => {
  const encounterId = resolveDoctorSessionEncounterId(selectedPatient.value);
  return `${encounterId}:${activeTab.value}`;
});

/** 记住当前患者停在哪个功能页 */
function applyFunctionTab(tab: DoctorTab) {
  activeTab.value = tab;
  const encounterId = resolveDoctorSessionEncounterId(selectedPatient.value);
  if (encounterId) {
    sessionStore.setSessionTab(encounterId, tab);
  }
}

function flashSessionSwitch() {
  sessionSwitching.value = true;
  if (sessionSwitchTimer) {
    clearTimeout(sessionSwitchTimer);
  }
  sessionSwitchTimer = setTimeout(() => {
    sessionSwitching.value = false;
    sessionSwitchTimer = undefined;
    currentTabPanel()?.restoreViewport?.();
  }, 280);
}

/** 打开/激活患者会话；已打开的人各自保留病历和功能页 */
function applyActiveSession(
  next: InpatientDoctorStationPatient | null,
  tab?: DoctorTab,
): boolean {
  const fromId = resolveDoctorSessionEncounterId(selectedPatient.value);
  if (!next) {
    doctorPatientStore.setSelectedPatient(null);
    activeTab.value = DOCTOR_TABS.MEDICAL_RECORD;
    return true;
  }
  const result = sessionStore.openSessions([next]);
  if (result.blockedByCap > 0 && !sessionStore.isOpened(next.encounterId)) {
    ElMessage.warning(`最多同时打开 ${INPATIENT_DOCTOR_SESSION_MAX} 位患者，请先关闭页签`);
    return false;
  }
  const current = sessionStore.activeSession ?? next;
  const toId = resolveDoctorSessionEncounterId(current);
  if (fromId && toId && fromId !== toId) {
    flashSessionSwitch();
  }
  doctorPatientStore.setSelectedPatient(current);
  const encounterId = resolveDoctorSessionEncounterId(current);
  if (tab) {
    applyFunctionTab(tab);
    return true;
  }
  const remembered = sessionStore.getSessionTab(encounterId);
  if (remembered) {
    activeTab.value = remembered as DoctorTab;
    return true;
  }
  sessionStore.setSessionTab(encounterId, activeTab.value);
  return true;
}

async function guardEmrUnsavedBeforeLeaveMedicalRecord(): Promise<boolean> {
  return confirmEmrDiscardUnsaved();
}

/** 单人工作台：换人/离病历前确认未保存 */
async function applySelectedPatientChange(
  next: InpatientDoctorStationPatient | null,
): Promise<boolean> {
  const prevId = (selectedPatient.value?.encounterId ?? '').trim();
  const nextId = (next?.encounterId ?? '').trim();
  if (prevId && prevId !== nextId) {
    const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
    if (!ok) return false;
  }
  doctorPatientStore.setSelectedPatient(next);
  return true;
}

/** 切换功能页；多开时各患者自己记住 */
const handleTabChange = async (tab: string) => {
  if (multiSessionEnabled) {
    applyFunctionTab(tab as DoctorTab);
    return;
  }
  const next = tab as DoctorTab;
  if (activeTab.value === DOCTOR_TABS.MEDICAL_RECORD && next !== DOCTOR_TABS.MEDICAL_RECORD) {
    const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
    if (!ok) return;
  }
  activeTab.value = next;
};

/** 侧栏点选患者：多开加页签，单人直接切换 */
const handlePatientSelect = async (patient: InpatientDoctorStationPatient) => {
  if (multiSessionEnabled) {
    applyActiveSession(patient);
    return;
  }
  await applySelectedPatientChange(patient);
};

const patientListRef = ref<{ locatePatient: (encounterId: string) => void } | null>(null);

function locateQueuePatient(patient: InpatientDoctorStationPatient | null | undefined) {
  const encounterId = resolveDoctorSessionEncounterId(patient);
  if (!encounterId) return;
  nextTick(() => {
    patientListRef.value?.locatePatient(encounterId);
  });
}

function handleActivateSession(patient: InpatientDoctorStationPatient) {
  applyActiveSession(patient);
  locateQueuePatient(patient);
}

function handleCloseSession(patient: InpatientDoctorStationPatient) {
  sessionStore.closeSession(resolveDoctorSessionEncounterId(patient));
  applyActiveSession(sessionStore.activeSession);
}

function handleCloseOtherSessions(patient: InpatientDoctorStationPatient) {
  sessionStore.closeOthers(resolveDoctorSessionEncounterId(patient));
  applyActiveSession(sessionStore.activeSession);
}

function handleCloseAllSessions() {
  sessionStore.closeAll();
  applyActiveSession(null);
}

/** 展开患者一览大屏（铺满医生站工作区） */
function openPatientLargeScreen() {
  largeScreenVisible.value = true;
}

/** 大屏进入工作台：选中患者并切到指定功能页 */
async function leaveLargeScreenToPatient(
  patient: InpatientDoctorStationPatient,
  tab: DoctorTab,
) {
  if (multiSessionEnabled) {
    const ok = applyActiveSession(patient, tab);
    if (!ok) return;
    largeScreenVisible.value = false;
    return;
  }
  const prevId = (selectedPatient.value?.encounterId ?? '').trim();
  const nextId = (patient.encounterId ?? '').trim();
  const patientChanged = prevId !== nextId;
  if (patientChanged) {
    const ok = await applySelectedPatientChange(patient);
    if (!ok) return;
  } else if (activeTab.value === DOCTOR_TABS.MEDICAL_RECORD && tab !== DOCTOR_TABS.MEDICAL_RECORD) {
    const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
    if (!ok) return;
  }
  activeTab.value = tab;
  largeScreenVisible.value = false;
}

/** 大屏点卡：选中并进入住院病历 */
async function handleLargeScreenSelect(patient: InpatientDoctorStationPatient) {
  await leaveLargeScreenToPatient(patient, DOCTOR_TABS.MEDICAL_RECORD);
}

/** 大屏快捷入口：页签切换，或跳转病案首页 */
async function handleLargeScreenShortcut(payload: {
  patient: InpatientDoctorStationPatient;
  tab: DoctorTab | '病案首页';
}) {
  if (payload.tab === '病案首页') {
    const ok = multiSessionEnabled
      ? applyActiveSession(payload.patient)
      : await applySelectedPatientChange(payload.patient);
    if (!ok) return;
    const encounterId = preserveApiIdForRequest(payload.patient.encounterId);
    if (!encounterId) {
      ElMessage.warning('缺少就诊主键，无法打开病案首页');
      return;
    }
    largeScreenVisible.value = false;
    void router.push({
      name: 'MrmsCaseRecordHomepageDetail',
      query: { encounterId },
    });
    return;
  }
  await leaveLargeScreenToPatient(payload.patient, payload.tab);
}

/** 关键字检索后刷新队列 */
const handlePatientSearch = async (keyword: string) => {
  if (!assertOrganizationSelected(() => Number(authStore.getSelectedOrganization()))) {
    if (multiSessionEnabled) {
      applyActiveSession(null);
    } else {
      await applySelectedPatientChange(null);
    }
    return;
  }
  listKeyword.value = keyword;
  void loadInpatientDoctorStationList(
    multiSessionEnabled ? { skipDefaultSelection: true } : undefined,
  );
};

/** 侧栏刷新 */
const handleRefresh = () => {
  void loadInpatientDoctorStationList({ skipDefaultSelection: true });
};

type StationTabPanelExpose = {
  reload?: () => Promise<void> | void;
  restoreViewport?: () => void;
};

const otherTabPanelRef = ref<StationTabPanelExpose | null>(null);
const emrFormRefMap = new Map<string, StationTabPanelExpose>();

function setEmrFormRef(
  patient: InpatientDoctorStationPatient,
  el: Element | StationTabPanelExpose | null,
) {
  const key = resolveDoctorSessionEncounterId(patient);
  if (!key) return;
  if (el && typeof el === 'object' && 'reload' in el) {
    emrFormRefMap.set(key, el);
    return;
  }
  emrFormRefMap.delete(key);
}

const tabPanelRef = ref<StationTabPanelExpose | null>(null);

function currentTabPanel(): StationTabPanelExpose | null {
  if (!multiSessionEnabled) {
    return tabPanelRef.value;
  }
  if (activeTab.value === DOCTOR_TABS.MEDICAL_RECORD) {
    const key = resolveDoctorSessionEncounterId(selectedPatient.value);
    return emrFormRefMap.get(key) ?? null;
  }
  return otherTabPanelRef.value;
}
const dischargeDialogVisible = ref(false);
const nursingDialogVisible = ref(false);

onMounted(() => {
  void loadInpatientDoctorStationList(
    multiSessionEnabled ? { skipDefaultSelection: true } : undefined,
  );
  // 进入住院医生站后静默本机传染病前置 SSO（失败不挡；同会话只尝试一次）
  void tryCrbSsoOnDoctorStationEnter();
});

/** 离开住院医生站：关弹窗，并清掉跨患者医嘱剪贴板 */
function leaveInpatientDoctorStation() {
  dischargeDialogVisible.value = false;
  nursingDialogVisible.value = false;
  clearInpatientAdviceClipboard();
}

onDeactivated(() => {
  leaveInpatientDoctorStation();
});

onUnmounted(() => {
  if (sessionSwitchTimer) {
    clearTimeout(sessionSwitchTimer);
    sessionSwitchTimer = undefined;
  }
  leaveInpatientDoctorStation();
});

/** 住院侧无门诊接诊写接口：已选患者即可继续临床操作 */
const ensureReceptionForClinicalWork: EnsureClinicalReceptionFn = async () => {
  if (!selectedPatient.value) {
    ElMessage.warning('请先选择患者');
    return false;
  }
  return true;
};

provide(ENSURE_CLINICAL_RECEPTION_KEY, ensureReceptionForClinicalWork);

/** 常用诊断维护变更计数：诊断 Tab 注入后刷新快捷树 */
const commonDxMaintainVersion = ref(0);
provide(COMMON_DIAGNOSIS_MAINTAIN_VERSION_KEY, commonDxMaintainVersion);

const commonDxMaintainDialogVisible = ref(false);

/** 打开诊断维护（个人/科室常用） */
function handleMaintainCommonDiagnosis() {
  commonDxMaintainDialogVisible.value = true;
}

/** 维护弹窗变更后递增版本，驱动诊断快捷树刷新 */
function handleCommonDiagnosisMaintainChanged() {
  commonDxMaintainVersion.value += 1;
}

const handleFormUpdate = (data: Record<string, unknown>) => {
  medicalRecordData.value = data;
};

/** 未选患者时拦截工具栏操作 */
const ensurePatientSelected = () => {
  if (!selectedPatient.value) {
    ElMessage.warning('请先选择患者');
    return false;
  }
  return true;
};

/** 工具栏刷新：优先调用当前 Tab 的 reload */
async function handleToolbarRefresh() {
  void refreshEncounterFee();
  const inst = currentTabPanel();
  if (inst && typeof inst.reload === 'function') {
    await inst.reload();
    return;
  }
  void loadInpatientDoctorStationList({ skipDefaultSelection: true });
}

watch(activeTab, () => {
  void refreshEncounterFee();
});

/** 完整预结算：不读卡，3101→2301→2303 仅展示金额，不确认落账 */
async function handleClinicalPreSettle() {
  const p = selectedPatient.value;
  if (!p) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const encounterId = preserveApiIdForRequest(p.encounterId);
  const patientId = preserveApiIdForRequest(p.patientId);
  if (!encounterId || !patientId) {
    ElMessage.warning('患者就诊信息不完整');
    return;
  }
  clinicalPreSettleLoading.value = true;
  let openPreviewAfterProgress = false;
  try {
    const result = await runInpatientClinicalPreSettle({
      params: {
        encounterId,
        patientId,
        encounterBusNo: p.encounterBusNo,
        statusEnum: p.statusEnum,
        contractDisplay: p.contractDisplay,
        insutype: p.insutype,
      },
      dialogRef: detailAuditDialogRef,
      progress: clinicalPreSettleProgress,
    });
    if (!result) {
      return;
    }
    clinicalPreSettleSnapshot.value = result.snapshot;
    clinicalPreSettleChargeLines.value = result.chargeLines;
    clinicalPreSettleAuditViolations.value = result.auditViolations;
    clinicalPreSettleAuditNote.value = result.auditRemindNote;
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

const handleQuickAction = async (
  action:
    | 'allergy'
    | 'anesthesia'
    | 'entrust'
    | 'exam'
    | 'lab'
    | 'pathology'
    | 'report'
    | 'surgery'
    | 'transfusion'
    | 'treatment',
) => {
  if (!ensurePatientSelected()) return;
  const isWriteQuickAction =
    action === 'exam' ||
    action === 'lab' ||
    action === 'treatment' ||
    action === 'transfusion' ||
    action === 'pathology' ||
    action === 'surgery' ||
    action === 'anesthesia';
  if (isWriteQuickAction && adviceWriteDisabled.value) return;
  if (action === 'report') {
    if (multiSessionEnabled) {
      applyFunctionTab(DOCTOR_TABS.REPORT_QUERY);
      return;
    }
    if (activeTab.value === DOCTOR_TABS.MEDICAL_RECORD) {
      const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
      if (!ok) return;
    }
    activeTab.value = DOCTOR_TABS.REPORT_QUERY;
    return;
  }
  if (action === 'lab' || action === 'exam' || action === 'treatment') {
    openServiceApplyDialog(action);
    return;
  }
  // 嘱托由开立医嘱工作区本地处理，一般不会冒泡到此
  if (action === 'entrust') {
    return;
  }
  if (action === 'allergy') {
    allergyDialogVisible.value = true;
    return;
  }
  if (action === 'surgery' || action === 'anesthesia') {
    const nextTab = action === 'surgery' ? DOCTOR_TABS.SURGERY : DOCTOR_TABS.ANESTHESIA;
    if (multiSessionEnabled) {
      applyFunctionTab(nextTab);
      return;
    }
    if (activeTab.value === DOCTOR_TABS.MEDICAL_RECORD) {
      const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
      if (!ok) return;
    }
    activeTab.value = nextTab;
    return;
  }
  const actionTextMap: Record<
    Exclude<
      typeof action,
      'allergy' | 'anesthesia' | 'entrust' | 'exam' | 'lab' | 'report' | 'surgery' | 'treatment'
    >,
    string
  > = {
    transfusion: '输血申请',
    pathology: '病理申请',
  };
  ElMessage.info(`${actionTextMap[action]}功能待实现`);
};

const serviceApplyDialogVisible = ref(false);
const allergyDialogVisible = ref(false);
const serviceApplyKind = ref<ServiceApplyKind>('lab');
const serviceApplyEditGroup = ref<null | ServiceApplyDocumentGroup>(null);

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
    feeNatureText: (info.serviceTypeName ?? '').trim() || '住院',
  };
});

/** 打开检验/检查/治疗申请弹窗（新建） */
function openServiceApplyDialog(kind: ServiceApplyKind) {
  serviceApplyEditGroup.value = null;
  serviceApplyKind.value = kind;
  serviceApplyDialogVisible.value = true;
}

/** 双击草稿申请单：弹窗编辑（已签发须先撤回） */
function handleEditServiceApply(group: ServiceApplyDocumentGroup) {
  const head = group.lines[0];
  const kind = head ? (inferServiceApplyKindFromAdviceRow(head) ?? 'lab') : 'lab';
  serviceApplyKind.value = kind;
  // 先写入回显组再打开，避免 watch 打开瞬间无 editDocumentGroup
  serviceApplyEditGroup.value = group;
  serviceApplyDialogVisible.value = true;
}

/** 申请单弹窗关闭后清空编辑回显，避免下次新建串数据 */
watch(serviceApplyDialogVisible, (open) => {
  if (!open) {
    serviceApplyEditGroup.value = null;
  }
});

/** 申请单暂存/签发成功后刷新当前「开立医嘱」列表 */
async function handleServiceApplyPersisted() {
  if (activeTab.value !== DOCTOR_TABS.ORDER) return;
  await nextTick();
  const inst = currentTabPanel();
  if (inst && typeof inst.reload === 'function') {
    await inst.reload();
  }
}

/** 打开出院医嘱表单弹窗 */
const handleCompleteVisit = () => {
  if (!ensurePatientSelected()) return;
  dischargeDialogVisible.value = true;
};

/** 出院医嘱开立成功后刷新患者队列与医嘱列表 */
async function handleDischargeAdviceSuccess() {
  void loadInpatientDoctorStationList({ skipDefaultSelection: true });
  const inst = currentTabPanel();
  if (inst && typeof inst.reload === 'function') {
    await inst.reload();
  }
}

async function handleNursingAdviceSuccess() {
  void loadInpatientDoctorStationList({ skipDefaultSelection: true });
  const inst = currentTabPanel();
  if (inst && typeof inst.reload === 'function') {
    await inst.reload();
  }
}

const handleNursing = () => {
  if (!ensurePatientSelected()) return;
  nursingDialogVisible.value = true;
};
</script>

<template>
  <div class="ip-doctor-station relative flex h-full min-h-0 flex-row overflow-hidden">
    <DoctorPatientList
      ref="patientListRef"
      :patients="inHospitalPatients"
      :loading="listLoading"
      :selected-patient-id="selectedPatient?.encounterId"
      @select="handlePatientSelect"
      @search="handlePatientSearch"
      @refresh="handleRefresh"
      @open-large-screen="openPatientLargeScreen"
    />

    <PatientLargeScreen
      v-model="largeScreenVisible"
      :patients="inHospitalPatients"
      :selected-encounter-id="selectedPatient?.encounterId"
      :loading="listLoading"
      :keyword="listKeyword"
      :current-doctor-name="doctorDisplayName"
      @select="handleLargeScreenSelect"
      @shortcut="handleLargeScreenShortcut"
      @search="handlePatientSearch"
      @refresh="handleRefresh"
    />

    <div class="bg-background-page ml-[2px] flex min-h-0 flex-1 flex-col overflow-hidden">
      <InpatientDoctorSessionTabs
        v-if="multiSessionEnabled"
        :sessions="sessions"
        :active-encounter-id="activeEncounterId"
        @activate="handleActivateSession"
        @close="handleCloseSession"
        @close-others="handleCloseOtherSessions"
        @close-all="handleCloseAllSessions"
      />
      <div
        v-loading="multiSessionEnabled && sessionSwitching"
        class="flex min-h-0 flex-1 flex-col overflow-hidden"
      >
      <PatientInfoBar
        :patient="currentPatientInfo"
        :doctor-name="doctorDisplayName"
        :fee-total-text="feeSummary?.totalText ?? ''"
      />
      <div class="flex min-h-0 flex-1 flex-col">
        <DoctorHeader
          :patient="currentPatientInfo"
          :doctor-name="doctorDisplayName"
          :active-tab="activeTab"
          :tabs="doctorTabs"
          @tab-change="handleTabChange"
        >
          <template #actions>
            <ElButton type="primary" link @click="openPatientLargeScreen">患者一览</ElButton>
            <ElButton type="primary" link @click="handleToolbarRefresh">刷新</ElButton>
            <ElButton
              type="primary"
              link
              :loading="clinicalPreSettleLoading"
              @click="handleClinicalPreSettle"
            >
              完整预结算
            </ElButton>
            <ElButton
              type="primary"
              link
              :disabled="adviceWriteDisabled"
              @click="handleCompleteVisit"
            >
              出院
            </ElButton>
            <ElButton type="primary" link :disabled="adviceWriteDisabled" @click="handleNursing">
              护理
            </ElButton>
            <ElButton type="primary" link @click="handleMaintainCommonDiagnosis">
              诊断维护
            </ElButton>
          </template>
        </DoctorHeader>
        <div class="flex min-h-0 flex-1 flex-col gap-0">
          <div
            class="border-border-base bg-background-card ml-0 flex min-h-0 flex-1 flex-col overflow-hidden rounded-none border border-t-0 shadow-sm"
          >
            <div v-if="multiSessionEnabled" class="ip-station-panels">
              <div
                v-for="session in sessions"
                :key="`emr:${resolveDoctorSessionEncounterId(session)}`"
                class="ip-station-panels__emr"
                :class="{ 'is-active': isEmrPaneActive(session) }"
              >
                <MedicalRecordForm
                  :ref="(el) => setEmrFormRef(session, el as never)"
                  class="h-full min-h-0 overflow-hidden"
                  :form-data="medicalRecordData"
                  v-bind="emrBindingsFor(session)"
                  @quick-action="handleQuickAction"
                  @edit-service-apply="handleEditServiceApply"
                  @update="handleFormUpdate"
                />
              </div>
              <KeepAlive :max="24">
                <component
                  :is="tabComponents[activeTab]"
                  ref="otherTabPanelRef"
                  :key="sessionPanelKey"
                  v-if="activeTab !== DOCTOR_TABS.MEDICAL_RECORD && tabComponents[activeTab]"
                  class="h-full min-h-0 overflow-hidden"
                  :patient-info="emrPatientInfo"
                  :form-data="medicalRecordData"
                  v-bind="doctorStationContextBindings"
                  @quick-action="handleQuickAction"
                  @edit-service-apply="handleEditServiceApply"
                  @update="handleFormUpdate"
                />
              </KeepAlive>
            </div>
            <KeepAlive v-else>
              <component
                :is="tabComponents[activeTab]"
                ref="tabPanelRef"
                :key="activeTab"
                v-if="tabComponents[activeTab]"
                class="h-full min-h-0 overflow-hidden"
                :patient-info="emrPatientInfo"
                :form-data="medicalRecordData"
                v-bind="doctorStationContextBindings"
                @quick-action="handleQuickAction"
                @edit-service-apply="handleEditServiceApply"
                @update="handleFormUpdate"
              />
            </KeepAlive>
          </div>
        </div>
      </div>
      </div>
    </div>

    <InpatientDischargeAdviceDialog
      v-model="dischargeDialogVisible"
      :patient="selectedPatient"
      @success="handleDischargeAdviceSuccess"
    />

    <InpatientNursingAdviceDialog
      v-model="nursingDialogVisible"
      :patient="selectedPatient"
      @success="handleNursingAdviceSuccess"
    />

    <PatientAllergyHistoryDialog
      v-model="allergyDialogVisible"
      :patient-id="selectedPatient?.patientId ?? ''"
      :encounter-id="selectedPatient?.encounterId ?? ''"
      default-verification-status="CONFIRMED"
      :read-only="adviceWriteDisabled"
    />

    <ServiceApplyDialog
      v-model="serviceApplyDialogVisible"
      station-scope="inpatient"
      shell="dialog"
      :apply-kind="serviceApplyKind"
      :edit-document-group="serviceApplyEditGroup"
      :encounter-id="selectedPatient?.encounterId ?? ''"
      :patient-id="selectedPatient?.patientId ?? ''"
      :patient="serviceApplyPatientSummary"
      @saved="handleServiceApplyPersisted"
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
      :audit-violations="clinicalPreSettleAuditViolations"
      :audit-remind-note="clinicalPreSettleAuditNote"
    />

    <CommonDiagnosisMaintainDialog
      v-model="commonDxMaintainDialogVisible"
      station-scope="inpatient"
      @changed="handleCommonDiagnosisMaintainChanged"
    />
  </div>
</template>
