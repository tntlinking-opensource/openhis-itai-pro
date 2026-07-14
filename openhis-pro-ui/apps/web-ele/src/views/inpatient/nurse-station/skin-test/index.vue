<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';

import type {
  NurseStationSkinTestEnumOptionDto,
  NurseStationSkinTestRowDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-skin-test.types';

import { computed, onMounted, ref, watch } from 'vue';

import { useUserStore } from '@vben/stores';
import { ElEmpty, ElMessage, ElMessageBox } from 'element-plus';

import {
  httpDeleteNurseStationSkinTestRecord,
  httpListNurseStationSkinTestRows,
  httpLoadNurseStationSkinTestInit,
  httpSaveNurseStationSkinTestRecord,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-skin-test.http';
import InpatientPatientInfoHeader from '#/components/inpatient/patient-info-header.vue';
import { useRequest } from '#/hooks/use-request';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import {
  ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE,
  isEncounterClinicalOrderMutationBlocked,
} from '#/utils/inpatient/encounter-clinical-order-gate';

import SkinTestPanel from './components/skin-test-panel.vue';
import {
  buildNurseStationStickyPatientInfoFromModulePatient,
  buildNurseStationStickyPatientInfoPlaceholder,
} from '../components/build-nurse-station-sticky-patient-info';
import {
  mapInpatientSkinTestDraftToSaveRequest,
  type InpatientSkinTestRowDraft,
} from './services/map-nurse-station-skin-test';

interface Props {
  patients?: NurseStationModulePatient[];
}

defineOptions({
  name: 'InpatientNurseStationSkinTestModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

const userStore = useUserStore();

const skinTestRows = ref<NurseStationSkinTestRowDto[]>([]);
const clinicalStatusOptions = ref<NurseStationSkinTestEnumOptionDto[]>([]);
const verificationStatusOptions = ref<NurseStationSkinTestEnumOptionDto[]>([]);
const criticalityOptions = ref<NurseStationSkinTestEnumOptionDto[]>([]);
const severityOptions = ref<NurseStationSkinTestEnumOptionDto[]>([]);
const practitionerOptions = ref<{ label: string; value: string }[]>([]);
const pageInitialized = ref(false);
const savingMedicationRequestId = ref('');
const deletingAllergyRecordId = ref('');

const initRequest = useRequest(httpLoadNurseStationSkinTestInit, { manual: true });
const listRequest = useRequest(httpListNurseStationSkinTestRows, { manual: true });

/** 皮试仅支持左侧单选患者 */
const canOperateSkinTest = computed(() => props.patients.length === 1);
/** 就诊已出院/待结算/已结算：禁止皮试划价写操作 */
const isEncounterChargeMutationBlocked = computed(() =>
  canOperateSkinTest.value
    ? isEncounterClinicalOrderMutationBlocked(props.patients[0]?.statusEnum)
    : false,
);
const canWriteSkinTest = computed(
  () => canOperateSkinTest.value && !isEncounterChargeMutationBlocked.value,
);

/** 当前选中就诊上下文 */
const selectedPatientContext = computed(() => {
  if (!canOperateSkinTest.value) {
    return null;
  }
  const patient = props.patients[0];
  if (!patient) {
    return null;
  }
  const encounterId = preserveApiIdForRequest(patient.id);
  const patientId = preserveApiIdForRequest(patient.patientId ?? patient.id);
  const accountId = preserveApiIdForRequest(patient.accountId);
  if (!encounterId || !patientId) {
    return null;
  }
  return { encounterId, patientId, accountId: accountId ?? '', patient };
});

const defaultPractitionerId = computed(
  () => resolveLoginPractitionerIdForRequest(userStore.userInfo) ?? '',
);

/** 顶部吸顶患者信息 */
const stickyPatientInfo = computed(() => {
  if (props.patients.length === 0) {
    return null;
  }
  if (!canOperateSkinTest.value) {
    return buildNurseStationStickyPatientInfoPlaceholder({
      diagnosis: `当前已选 ${props.patients.length} 位患者（请单选后登记皮试）`,
      name: '请选择单一患者',
    });
  }
  const patient = props.patients[0];
  if (!patient) {
    return null;
  }
  return buildNurseStationStickyPatientInfoFromModulePatient(patient);
});

/** 加载皮试枚举与护士候选 */
async function loadPageInit() {
  const initRes = await initRequest.run();
  clinicalStatusOptions.value = initRes.clinicalStatusOptions ?? [];
  verificationStatusOptions.value = initRes.verificationStatusOptions ?? [];
  criticalityOptions.value = initRes.criticalityOptions ?? [];
  severityOptions.value = initRes.severityOptions ?? [];
  practitionerOptions.value = (initRes.nurseOptions ?? [])
    .map((opt) => ({
      value: preserveApiIdForRequest(opt.value) ?? '',
      label: (opt.label ?? '').trim() || String(opt.value ?? ''),
    }))
    .filter((opt) => opt.value.length > 0);
  pageInitialized.value = true;
}

/** 按就诊加载皮试列表 */
async function loadSkinTestRows() {
  if (!pageInitialized.value) {
    await loadPageInit();
  }
  const context = selectedPatientContext.value;
  if (!context) {
    skinTestRows.value = [];
    return;
  }
  const res = await listRequest.run({
    encounterId: context.encounterId,
    patientId: context.patientId,
  });
  skinTestRows.value = res.rows ?? [];
}

/** 保存单行皮试登记 */
async function handleSaveSkinTestRow(draft: InpatientSkinTestRowDraft) {
  const context = selectedPatientContext.value;
  if (!context) {
    ElMessage.warning('皮试登记仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  if (!canWriteSkinTest.value) {
    ElMessage.warning(ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE);
    return;
  }
  if (!context.accountId) {
    ElMessage.warning('患者账户无效，请刷新患者列表后重试');
    return;
  }
  const body = mapInpatientSkinTestDraftToSaveRequest(
    { ...draft, accountId: context.accountId },
    context.encounterId,
  );
  savingMedicationRequestId.value = body.medicationRequestId;
  try {
    const res = await httpSaveNurseStationSkinTestRecord(body);
    if (res.firstTimePriced === true) {
      ElMessage.success('皮试结果已保存，并已自动生成皮试操作费（待结算）');
    } else {
      ElMessage.success('皮试结果已保存');
    }
    await loadSkinTestRows();
  } finally {
    savingMedicationRequestId.value = '';
  }
}

/** 删除误录皮试记录 */
async function handleDeleteSkinTestRow(allergyRecordId: string) {
  const context = selectedPatientContext.value;
  const recordId = String(allergyRecordId ?? '').trim();
  if (!context || !recordId) {
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该误录的皮试结果？已划价记录不可删除。', '删除皮试记录', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  deletingAllergyRecordId.value = recordId;
  try {
    await httpDeleteNurseStationSkinTestRecord({
      encounterId: context.encounterId,
      allergyRecordId: recordId,
    });
    ElMessage.success('皮试记录已删除');
    await loadSkinTestRows();
  } finally {
    deletingAllergyRecordId.value = '';
  }
}

watch(
  () => selectedPatientContext.value?.encounterId,
  (encounterId, prevEncounterId) => {
    if (!encounterId) {
      skinTestRows.value = [];
      return;
    }
    if (encounterId === prevEncounterId) {
      return;
    }
    void loadSkinTestRows();
  },
);

onMounted(async () => {
  await loadPageInit();
  if (selectedPatientContext.value) {
    await loadSkinTestRows();
  }
});
</script>

<template>
  <div class="skin-test-module flex h-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <InpatientPatientInfoHeader :patient-info="stickyPatientInfo" />
    <SkinTestPanel
      v-if="canOperateSkinTest && selectedPatientContext"
      :account-id="selectedPatientContext.accountId"
      :rows="skinTestRows"
      :loading="listRequest.loading.value || initRequest.loading.value"
      :clinical-status-options="clinicalStatusOptions"
      :verification-status-options="verificationStatusOptions"
      :criticality-options="criticalityOptions"
      :severity-options="severityOptions"
      :practitioner-options="practitionerOptions"
      :default-practitioner-id="defaultPractitionerId"
      :saving-medication-request-id="savingMedicationRequestId"
      :deleting-allergy-record-id="deletingAllergyRecordId"
      @refresh="loadSkinTestRows"
      @save-row="handleSaveSkinTestRow"
      @delete-row="handleDeleteSkinTestRow"
    />
    <div v-else class="skin-test-module__placeholder bg-background flex min-h-0 flex-1 flex-col">
      <div class="skin-test-center__empty flex flex-1 items-center justify-center">
        <ElEmpty
          :description="
            patients.length === 0
              ? '请在左侧选择患者'
              : '皮试登记仅支持单选患者，请在左侧只勾选 1 位患者'
          "
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.skin-test-module :deep(.inpatient-patient-info-header) {
  flex-shrink: 0;
  margin-bottom: 0;
}
</style>
