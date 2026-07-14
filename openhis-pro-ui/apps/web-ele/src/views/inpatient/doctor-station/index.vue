<script setup lang="ts">
import type { Component } from 'vue';

import type { InpatientDoctorStationPatient } from './doctorPatientStore';
import type {
  ServiceApplyKind,
  ServiceApplyPatientSummary,
} from '#/views/outpatient/doctor-station/service-apply/service-apply.types';

import type { EnsureClinicalReceptionFn } from '#/components/doctor-station';

import { computed, nextTick, onDeactivated, onMounted, provide, ref } from 'vue';

import { useUserStore } from '@vben/stores';
import { ElButton, ElMessage } from 'element-plus';
import { storeToRefs } from 'pinia';

import {
  DiagnosisList,
  ENSURE_CLINICAL_RECEPTION_KEY,
  TcmAdvice,
} from '#/components/doctor-station';
import DiagnosisForm from '#/components/outpatient/diagnosis-form.vue';
import DoctorHeader from '#/components/outpatient/doctor-header.vue';
import MedicalRecordForm from '#/components/outpatient/medical-record-form.vue';
import PatientInfoBar from '#/components/outpatient/patient-info-bar.vue';
import { confirmEmrDiscardUnsaved } from '#/emrEditor';
import { useAuthStore } from '#/store';
import { isEncounterClinicalOrderMutationBlocked } from '#/utils/inpatient/encounter-clinical-order-gate';

import { InpatientAdviceList } from './advice';
import InpatientDischargeAdviceDialog from './components/inpatient-discharge-advice-dialog.vue';
import InpatientNursingAdviceDialog from './components/inpatient-nursing-advice-dialog.vue';
import DoctorPatientList from './doctor-patient-list.vue';
import { useInpatientDoctorPatientStore } from './doctorPatientStore';
import { useInpatientDoctorPatientQueue } from './use-inpatient-doctor-patient-queue';
import ServiceApplyDialog from '#/views/outpatient/doctor-station/service-apply/service-apply-dialog.vue';

defineOptions({
  name: 'InpatientDoctorStation',
});

// 标签枚举（与门诊医生站同构，住院侧文案区分）
const DOCTOR_TABS = {
  MEDICAL_RECORD: '住院病历',
  DIAGNOSIS: '诊断录入',
  ORDER: '开立医嘱',
  TCM: '中医处方',
  PRESCRIPTION: '电子处方',
  REPORT_QUERY: '报告查询',
} as const;

type DoctorTab = (typeof DOCTOR_TABS)[keyof typeof DOCTOR_TABS];

const authStore = useAuthStore();
const userStore = useUserStore();
const doctorPatientStore = useInpatientDoctorPatientStore();
const { selectedPatient, currentPatientInfo } = storeToRefs(doctorPatientStore);

const activeTab = ref<DoctorTab>(DOCTOR_TABS.MEDICAL_RECORD);

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

/** 病历编辑器元数据：性别优先读模型文本字段 */
const emrPatientInfo = computed(() => {
  const p = selectedPatient.value;
  if (!p) return undefined;
  const ageRaw = (p.ageText ?? '').trim();
  const age = ageRaw.replace(/岁\s*$/u, '');
  return {
    name: p.patientName ?? '',
    gender: (p.genderEnumText ?? '').trim(),
    age,
    phone: '',
    medicalRecordNo: (p.encounterBusNo ?? '').trim(),
    idCard: '',
  };
});

const medicalRecordData = ref({});

const tabComponents = computed<Record<DoctorTab, Component>>(() => ({
  [DOCTOR_TABS.MEDICAL_RECORD]: MedicalRecordForm,
  [DOCTOR_TABS.DIAGNOSIS]: DiagnosisList,
  [DOCTOR_TABS.ORDER]: InpatientAdviceList,
  [DOCTOR_TABS.TCM]: TcmAdvice,
  [DOCTOR_TABS.PRESCRIPTION]: DiagnosisForm,
  [DOCTOR_TABS.REPORT_QUERY]: DiagnosisForm,
}));

/** 诊断/医嘱/病历等 Tab 注入就诊上下文 */
const doctorStationContextBindings = computed(() => {
  const tab = activeTab.value;
  const base = {
    encounterId: selectedPatient.value?.encounterId ?? '',
    patientId: selectedPatient.value?.patientId ?? '',
  };
  if (tab === DOCTOR_TABS.MEDICAL_RECORD) {
    return { ...base, stationKind: 'INPATIENT' as const };
  }
  if (tab !== DOCTOR_TABS.DIAGNOSIS && tab !== DOCTOR_TABS.ORDER && tab !== DOCTOR_TABS.TCM) {
    return {};
  }
  if (tab === DOCTOR_TABS.DIAGNOSIS) {
    return {
      ...base,
      patientServiceTypeName: selectedPatient.value?.organizationName ?? '',
      stationScope: 'inpatient' as const,
    };
  }
  if (tab === DOCTOR_TABS.TCM) {
    return { ...base, stationScope: 'inpatient' as const };
  }
  return base;
});

/** 同步选中患者：刷新后尽量保留原选中行，否则选第一条 */
async function syncSelectedPatientAfterQueueLoad(
  rows: InpatientDoctorStationPatient[],
  skipDefaultSelection?: boolean,
) {
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
    doctorPatientStore.setSelectedPatient(null);
  }
}

async function guardEmrUnsavedBeforeLeaveMedicalRecord(): Promise<boolean> {
  return confirmEmrDiscardUnsaved();
}

/** 切换选中患者前做 EMR 未保存确认 */
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

/** 切换顶部 Tab；医嘱页签激活时触发子页初始化 */
const handleTabChange = async (tab: string) => {
  const next = tab as DoctorTab;
  if (activeTab.value === DOCTOR_TABS.MEDICAL_RECORD && next !== DOCTOR_TABS.MEDICAL_RECORD) {
    const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
    if (!ok) return;
  }
  activeTab.value = next;
};

/** 侧栏点选患者 */
const handlePatientSelect = async (patient: InpatientDoctorStationPatient) => {
  await applySelectedPatientChange(patient);
};

/** 关键字检索后刷新队列 */
const handlePatientSearch = async (keyword: string) => {
  if (!assertOrganizationSelected(() => Number(authStore.getSelectedOrganization()))) {
    await applySelectedPatientChange(null);
    return;
  }
  listKeyword.value = keyword;
  void loadInpatientDoctorStationList();
};

/** 侧栏刷新 */
const handleRefresh = () => {
  void loadInpatientDoctorStationList({ skipDefaultSelection: true });
};

const tabPanelRef = ref<null | { reload?: () => Promise<void> | void }>(null);
const dischargeDialogVisible = ref(false);
const nursingDialogVisible = ref(false);

onMounted(() => {
  void loadInpatientDoctorStationList();
});

/** 离开页面（含 Tab 缓存切换）时关闭出院弹窗，避免遮罩残留 */
onDeactivated(() => {
  dischargeDialogVisible.value = false;
  nursingDialogVisible.value = false;
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
  const inst = tabPanelRef.value;
  if (inst && typeof inst.reload === 'function') {
    await inst.reload();
    return;
  }
  void loadInpatientDoctorStationList({ skipDefaultSelection: true });
}

const handleQuickAction = async (
  action: 'allergy' | 'exam' | 'lab' | 'pathology' | 'report' | 'surgery' | 'transfusion',
) => {
  if (!ensurePatientSelected()) return;
  const isWriteQuickAction =
    action === 'exam' ||
    action === 'lab' ||
    action === 'transfusion' ||
    action === 'pathology' ||
    action === 'surgery';
  if (isWriteQuickAction && adviceWriteDisabled.value) return;
  if (action === 'report') {
    if (activeTab.value === DOCTOR_TABS.MEDICAL_RECORD) {
      const ok = await guardEmrUnsavedBeforeLeaveMedicalRecord();
      if (!ok) return;
    }
    activeTab.value = DOCTOR_TABS.REPORT_QUERY;
    return;
  }
  if (action === 'lab' || action === 'exam') {
    openServiceApplyDialog(action);
    return;
  }
  const actionTextMap: Record<Exclude<typeof action, 'exam' | 'lab' | 'report'>, string> = {
    transfusion: '输血申请',
    pathology: '病理申请',
    surgery: '手术申请',
    allergy: '过敏史',
  };
  ElMessage.info(`${actionTextMap[action]}功能待实现`);
};

const serviceApplyDialogVisible = ref(false);
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
    feeNatureText: (info.serviceTypeName ?? '').trim() || '住院',
  };
});

/** 打开检验/检查申请弹窗 */
function openServiceApplyDialog(kind: ServiceApplyKind) {
  serviceApplyKind.value = kind;
  serviceApplyDialogVisible.value = true;
}

/** 申请单签发成功后刷新当前「开立医嘱」列表 */
async function handleServiceApplySigned() {
  if (activeTab.value !== DOCTOR_TABS.ORDER) return;
  await nextTick();
  const inst = tabPanelRef.value as undefined | { reload?: () => Promise<void> };
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
  const inst = tabPanelRef.value;
  if (inst && typeof inst.reload === 'function') {
    await inst.reload();
  }
}

async function handleNursingAdviceSuccess() {
  void loadInpatientDoctorStationList({ skipDefaultSelection: true });
  const inst = tabPanelRef.value;
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
  <div class="ip-doctor-station flex h-full min-h-0 flex-row pt-2">
    <DoctorPatientList
      :patients="inHospitalPatients"
      :loading="listLoading"
      :selected-patient-id="selectedPatient?.encounterId"
      @select="handlePatientSelect"
      @search="handlePatientSearch"
      @refresh="handleRefresh"
    />

    <div class="bg-background-page ml-[2px] flex min-h-0 flex-1 flex-col overflow-hidden">
      <PatientInfoBar :patient="currentPatientInfo" :doctor-name="doctorDisplayName" />
      <div class="flex min-h-0 flex-1 flex-col">
        <DoctorHeader
          :patient="currentPatientInfo"
          :doctor-name="doctorDisplayName"
          :active-tab="activeTab"
          :tabs="Object.values(DOCTOR_TABS)"
          @tab-change="handleTabChange"
        >
          <template #actions>
            <ElButton type="primary" link @click="handleToolbarRefresh">刷新</ElButton>
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
                @quick-action="handleQuickAction"
                @update="handleFormUpdate"
              />
            </KeepAlive>
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

    <ServiceApplyDialog
      v-model="serviceApplyDialogVisible"
      station-scope="inpatient"
      :apply-kind="serviceApplyKind"
      :encounter-id="selectedPatient?.encounterId ?? ''"
      :patient-id="selectedPatient?.patientId ?? ''"
      :patient="serviceApplyPatientSummary"
      @signed="handleServiceApplySigned"
    />
  </div>
</template>
