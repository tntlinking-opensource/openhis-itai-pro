<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';

import { computed } from 'vue';

import { ElEmpty } from 'element-plus';

import MedicalRecordForm from '#/components/outpatient/medical-record-form.vue';
import InpatientPatientInfoHeader from '#/components/inpatient/patient-info-header.vue';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  buildNurseStationStickyPatientInfoFromModulePatient,
  buildNurseStationStickyPatientInfoPlaceholder,
} from '../components/build-nurse-station-sticky-patient-info';

interface Props {
  patients?: NurseStationModulePatient[];
}

defineOptions({
  name: 'InpatientNurseStationMedicalRecordReviewModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

/** 病历调阅仅支持左侧单选患者 */
const canOperate = computed(() => props.patients.length === 1);

const selectedPatient = computed(() => (canOperate.value ? props.patients[0] : undefined));

/** 就诊 ID（护士站患者行 id 即 encounterId） */
const encounterId = computed(() => preserveApiIdForRequest(selectedPatient.value?.id) ?? '');

/** 患者主索引 ID */
const patientId = computed(() => preserveApiIdForRequest(selectedPatient.value?.patientId) ?? '');

/** 病历编辑器元数据条（与医生站住院病历对齐字段） */
const emrPatientInfo = computed(() => {
  const patient = selectedPatient.value;
  if (!patient) {
    return undefined;
  }
  return {
    name: patient.name ?? '',
    gender: (patient.gender ?? '').trim(),
    age: patient.age == null ? '' : String(patient.age),
    phone: '',
    insurance: (patient.feeType ?? '').trim(),
    medicalRecordNo: (patient.encounterBusNo ?? '').trim(),
    idCard: '',
  };
});

const stickyPatientInfo = computed(() => {
  if (props.patients.length === 0) {
    return null;
  }
  if (!canOperate.value) {
    return buildNurseStationStickyPatientInfoPlaceholder({
      diagnosis: `当前已选 ${props.patients.length} 位患者（请单选后调阅病历）`,
      name: '请选择单一患者',
    });
  }
  const patient = props.patients[0];
  if (!patient) {
    return null;
  }
  return buildNurseStationStickyPatientInfoFromModulePatient(patient);
});
</script>

<template>
  <div class="medical-record-review-page flex min-h-0 flex-1 flex-col">
    <InpatientPatientInfoHeader :patient-info="stickyPatientInfo" />

    <section
      v-if="!canOperate"
      class="flex min-h-0 flex-1 items-center justify-center rounded-md bg-white"
    >
      <ElEmpty description="请在左侧患者列表单选一位患者" />
    </section>

    <section
      v-else
      class="review-panel flex min-h-0 flex-1 flex-col overflow-hidden rounded-md border border-solid border-[var(--el-border-color-lighter)] bg-white"
    >
      <MedicalRecordForm
        class="min-h-0 flex-1"
        station-kind="INPATIENT"
        force-read-only
        restrict-history-to-inpatient
        :patient-id="patientId"
        :encounter-id="encounterId"
        :patient-info="emrPatientInfo"
      />
    </section>
  </div>
</template>

<style scoped>
.medical-record-review-page {
  background: #f3f5f8;
  gap: 6px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 0 2px 6px;
}

.medical-record-review-page :deep(.inpatient-patient-info-header) {
  margin-bottom: 0;
}

.review-panel {
  min-height: 0;
}
</style>
