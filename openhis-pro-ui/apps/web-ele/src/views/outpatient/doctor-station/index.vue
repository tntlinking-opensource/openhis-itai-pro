<script setup lang="ts">
import type { Component } from 'vue';

import type { DoctorStationPatient } from './doctorPatientStore';

import type { PatientInfoBarPayload } from '#/components/outpatient/patient-card.types';

import { computed, nextTick, onMounted, provide, ref } from 'vue';

import { useUserStore } from '@vben/stores';

import { ElButton, ElMessage } from 'element-plus';
import { storeToRefs } from 'pinia';

import {
  httpPageOutpatientInfoForDoctorStation,
  httpStartOutpatientReception,
} from '#/api/doctor-station/encounter.http';
import DiagnosisForm from '#/components/outpatient/diagnosis-form.vue';
import DoctorHeader from '#/components/outpatient/doctor-header.vue';
import MedicalRecordForm from '#/components/outpatient/medical-record-form.vue';
import PatientInfoBar from '#/components/outpatient/patient-info-bar.vue';
import { useAuthStore } from '#/store';
import { formatLocalDateYmd, getLocalDayRangeDateTimeStrings } from '#/utils/date-range-quick';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import {
  DiagnosisList,
  ENSURE_CLINICAL_RECEPTION_KEY,
  TcmAdvice,
  type EnsureClinicalReceptionFn,
} from '#/components/doctor-station';
import { confirmEmrDiscardUnsaved } from '#/emrEditor';

import { AdviceList } from './advice';
import DoctorPatientList from './doctor-patient-list.vue';
import { useDoctorPatientStore } from './doctorPatientStore';
import InpatientAdmissionDialog from './inpatient-admission/inpatient-admission-dialog.vue';
import RefundApplicationDialog from './refund/refund-application-dialog.vue';

defineOptions({
  name: 'Doctor',
});

const QUEUE_PLANNED = 'PLANNED';
const QUEUE_IN_PROGRESS = 'IN_PROGRESS';

/**
 * 双击需调接诊写接口的状态（与后端契约对齐；计划队列分页行常见为 `PLANNED`，勿漏判导致只走前端无写库）。
 */
const RECEPTION_WRITE_STATUSES = new Set(['COMPLETED', 'ON_HOLD', 'PLANNED', 'REGISTERED']);

/** 与 `QUEUE_IN_PROGRESS` 比较用，避免接口返回大小写或数字枚举。 */
function normalizeQueueStatusEnum(value: DoctorStationPatient['statusEnum']): string {
  if (value === null || value === undefined || value === '') return '';
  return String(value).trim().toUpperCase();
}

const authStore = useAuthStore();
const userStore = useUserStore();
const doctorPatientStore = useDoctorPatientStore();
const { selectedPatient, currentPatientInfo } = storeToRefs(doctorPatientStore);

/** 在诊列表（IF-DOR-01 / BR-DOR-001）过滤用：须与接诊写命令中的医师主键一致 */
function resolveStaffPractitionerIdForInProgressQueue(): string {
  const u = userStore.userInfo;
  const raw = u?.practitionerId;
  if (raw === null || raw === undefined || raw === '') {
    return '308791351298756608';
  }
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    return String(Math.trunc(raw));
  }
  const s = String(raw).trim();
  return s || '308791351298756608';
}

/**
 * 接诊写库后从「在诊」队列分页拉当前就诊行，拿到读模型投影的最新 statusEnum / statusEnumText 等。
 */
async function fetchPatientRowFromInProgressQueue(
  encounterId: string,
): Promise<DoctorStationPatient | null> {
  const organizationId = Number(authStore.getSelectedOrganization());
  if (!organizationId || Number.isNaN(organizationId)) return null;
  const { startDate, endDate } = getLocalDayRangeDateTimeStrings(listDateStr.value);
  try {
    const res = await httpPageOutpatientInfoForDoctorStation({
      organizationId,
      queueType: QUEUE_IN_PROGRESS,
      encounterStatus: QUEUE_IN_PROGRESS,
      staffPractitionerId: resolveStaffPractitionerIdForInProgressQueue(),
      startDate,
      endDate,
      pageNo: 1,
      pageSize: 50,
    });
    const id = encounterId.trim();
    const { content } = parsePagedRowsFromApiData<DoctorStationPatient>(res);
    const hit = content.find((r) => String(r.encounterId ?? '').trim() === id);
    return hit ?? null;
  } catch {
    return null;
  }
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
  TCM: '中医处方',
  PRESCRIPTION: '电子处方',
  REPORT_QUERY: '报告查询',
} as const;

type DoctorTab = (typeof DOCTOR_TABS)[keyof typeof DOCTOR_TABS];

// 当前激活的功能标签
const activeTab = ref<DoctorTab>(DOCTOR_TABS.MEDICAL_RECORD);
const patientQueue = ref<'current' | 'waiting'>('waiting');

/** 侧栏列表查询日 */
const listDateStr = ref(formatLocalDateYmd());
/** 待诊列表关键字（仅患者队列请求携带） */
const listKeyword = ref('');
const waitingPatients = ref<DoctorStationPatient[]>([]);
const currentPatients = ref<DoctorStationPatient[]>([]);
const listLoading = ref(false);
/** 正在提交接诊，避免双击等重复触发 */
const acceptingEncounterId = ref<null | string>(null);

const filteredPatients = computed(() =>
  patientQueue.value === 'current' ? currentPatients.value : waitingPatients.value,
);

/** 「患者队列」角标：仅统计展示为「待诊」的行（与 encounterQueueStatusDisplayText 的 REGISTERED 一致） */
const waitingPatientCount = computed(
  () =>
    waitingPatients.value.filter((p) => normalizeQueueStatusEnum(p.statusEnum) === 'REGISTERED')
      .length,
);

function genderEnumToLabelForEmr(v: null | number | undefined): string {
  if (v === 1) return '男';
  if (v === 2) return '女';
  return '';
}

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
    gender: genderEnumToLabelForEmr(p.genderEnum),
    age,
    phone: p.phone ?? '',
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
    [DOCTOR_TABS.TCM]: TcmAdvice,
    [DOCTOR_TABS.PRESCRIPTION]: DiagnosisForm,
    [DOCTOR_TABS.REPORT_QUERY]: DiagnosisForm,
  };
});

/** 诊断/医嘱/病历等 Tab 注入就诊上下文 */
const doctorStationContextBindings = computed(() => {
  const encounterId = selectedPatient.value?.encounterId ?? '';
  const patientId = selectedPatient.value?.patientId ?? '';
  if (activeTab.value === DOCTOR_TABS.DIAGNOSIS || activeTab.value === DOCTOR_TABS.ORDER) {
    return {
      encounterId,
      patientId,
      patientServiceTypeName: selectedPatient.value?.serviceTypeName ?? '',
    };
  }
  if (activeTab.value === DOCTOR_TABS.MEDICAL_RECORD) {
    return { encounterId, patientId, stationKind: 'OUTPATIENT' as const };
  }
  return {};
});

/** 拉取待诊队列分页（在诊列表不再单独请求，由接诊成功后的本地状态维护）。 */
async function loadDoctorStationLists(options?: { skipDefaultSelection?: boolean }) {
  const organizationId = Number(authStore.getSelectedOrganization());
  if (!organizationId || Number.isNaN(organizationId)) {
    ElMessage.warning('请先选择科室');
    waitingPatients.value = [];
    currentPatients.value = [];
    doctorPatientStore.setSelectedPatient(null);
    return;
  }

  const { startDate, endDate } = getLocalDayRangeDateTimeStrings(listDateStr.value);

  listLoading.value = true;
  try {
    const waitingRes = await httpPageOutpatientInfoForDoctorStation({
      organizationId,
      queueType: QUEUE_PLANNED,
      keyword: listKeyword.value.trim() || undefined,
      startDate,
      endDate,
      pageNo: 1,
      pageSize: 50,
    });

    const { content: waitingRows } = parsePagedRowsFromApiData<DoctorStationPatient>(waitingRes);
    waitingPatients.value = waitingRows;
    currentPatients.value = [];

    if (options?.skipDefaultSelection) return;

    if (patientQueue.value === 'waiting') {
      await applySelectedPatientChange(waitingPatients.value[0] ?? null);
      return;
    }

    const sel = selectedPatient.value;
    if (sel?.statusEnum === QUEUE_IN_PROGRESS) {
      currentPatients.value = [sel];
      await applySelectedPatientChange(sel);
    } else {
      await applySelectedPatientChange(null);
    }
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
  if (next === DOCTOR_TABS.ORDER) {
    await nextTick();
    const inst = tabPanelRef.value as undefined | { loadAdviceInit?: () => Promise<void> };
    if (inst && typeof inst.loadAdviceInit === 'function') {
      void inst.loadAdviceInit();
    }
  }
};

const handlePatientSelect = async (patient: DoctorStationPatient) => {
  await applySelectedPatientChange(patient);
};

const handleQueueChange = async (queue: 'away' | 'completed' | 'current' | 'waiting') => {
  if (queue !== 'current' && queue !== 'waiting') return;
  const nextPatients = queue === 'current' ? currentPatients.value : waitingPatients.value;
  const nextPatient = nextPatients[0] ?? null;
  const applied = await applySelectedPatientChange(nextPatient);
  if (!applied) return;
  patientQueue.value = queue;
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

const handleQuickAction = async (
  action: 'allergy' | 'exam' | 'lab' | 'pathology' | 'report' | 'surgery' | 'transfusion',
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
  const actionTextMap: Record<Exclude<typeof action, 'report'>, string> = {
    exam: '检查申请',
    lab: '检验申请',
    transfusion: '输血申请',
    pathology: '病理申请',
    surgery: '手术申请',
    allergy: '过敏史',
  };
  ElMessage.info(`${actionTextMap[action]}功能待实现`);
};

/**
 * 接诊或切在诊：与双击卡片同源；返回是否成功（含「已接诊仅切 UI」）。
 * 新增诊断 / 新增医嘱前通过 `ensureReceptionForClinicalWork` 间接调用。
 */
async function ensureReceptionForPatient(patient: DoctorStationPatient): Promise<boolean> {
  if (acceptingEncounterId.value !== null) {
    ElMessage.warning('接诊处理中，请稍候');
    return false;
  }

  const status = statusEnumForReceptionDecision(patient, currentPatientInfo.value);

  // IN_PROGRESS 等不在写接口集合内的状态：不调接诊写接口，但仍须切「在诊患者」并选中（禁止在此处 return 掉切换）。
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
    await httpStartOutpatientReception({
      encounterId: patient.encounterId,
      doctorId: doctorPractitionerId,
      operatorId: doctorPractitionerId,
      directFromPlanned: true,
    });
    ElMessage.success('接诊成功');
    await loadDoctorStationLists({ skipDefaultSelection: true });
    patientQueue.value = 'current';
    const fresh = await fetchPatientRowFromInProgressQueue(patient.encounterId);
    const inProgressPatient: DoctorStationPatient =
      fresh ??
      ({
        ...patient,
        statusEnum: QUEUE_IN_PROGRESS,
      } as DoctorStationPatient);
    currentPatients.value = [inProgressPatient];
    doctorPatientStore.setSelectedPatient(inProgressPatient);
    return true;
  } catch {
    ElMessage.error('接诊失败，请稍后重试');
    return false;
  } finally {
    acceptingEncounterId.value = null;
  }
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

// 双击待诊卡片：RECEPTION_WRITE_STATUSES 内调接诊写接口；其余不调接口，仅切在诊并选中。
const handlePatientAccept = async (patient: DoctorStationPatient) => {
  if (acceptingEncounterId.value !== null) return;
  const prevId = (selectedPatient.value?.encounterId ?? '').trim();
  const nextId = (patient.encounterId ?? '').trim();
  if (prevId && nextId && prevId !== nextId) {
    const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
    if (!ok) return;
  }
  const accepted = await ensureReceptionForPatient(patient);
  if (!accepted) return;
};

// 预留的历史搜索功能
// const handleHistorySearch = (_keyword: string) => {
//   // TODO: 实现历史搜索
// };

// 预留的历史项选择功能
// const handleHistorySelect = (_item: any) => {
//   // TODO: 实现历史项选择
// };

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

const handleCompleteVisit = () => {
  if (!ensurePatientSelected()) return;
  ElMessage.info('完诊功能待实现');
};

const handleTemporaryLeave = () => {
  if (!ensurePatientSelected()) return;
  ElMessage.info('暂离功能待实现');
};

const refundDialogVisible = ref(false);
const admissionDialogVisible = ref(false);

/** 打开申请退费弹窗（须已选就诊） */
const handleRefund = () => {
  if (!ensurePatientSelected()) return;
  refundDialogVisible.value = true;
};

/** 退费申请提交成功后刷新侧栏队列 */
const handleRefundSubmitted = () => {
  void loadDoctorStationLists({ skipDefaultSelection: true });
};

const handlePrescription = () => {
  if (!ensurePatientSelected()) return;
  ElMessage.info('处方单功能待实现');
};

/** 打开办理住院弹窗（须已选就诊） */
const handleAdmission = () => {
  if (!ensurePatientSelected()) return;
  admissionDialogVisible.value = true;
};
</script>

<template>
  <div class="flex h-full flex-row pt-2">
    <DoctorPatientList
      v-model:filter-date="listDateStr"
      :patients="filteredPatients"
      :current-queue="patientQueue"
      :waiting-count="waitingPatientCount"
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
          :tabs="Object.values(DOCTOR_TABS)"
          @tab-change="handleTabChange"
          @action="handleAction"
        >
          <template #actions>
            <ElButton type="primary" link @click="handleToolbarRefresh">刷新</ElButton>
            <ElButton type="primary" link @click="handleCompleteVisit">完诊</ElButton>
            <ElButton type="primary" link @click="handleTemporaryLeave"> 暂离 </ElButton>
            <ElButton type="primary" link @click="handleRefund">退费</ElButton>
            <ElButton type="primary" link @click="handlePrescription"> 处方单 </ElButton>
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
  </div>
</template>
