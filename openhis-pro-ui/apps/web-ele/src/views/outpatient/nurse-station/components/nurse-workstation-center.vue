<script setup lang="ts">
import type { NurseSkinTestRowDraft } from '../services/map-nurse-skin-test';
import type { NurseAdviceExecuteRow, NursePatient } from './types';

import type {
  OutpatientSkinTestEnumOptionDto,
  OutpatientSkinTestRowDto,
} from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';
import type { PatientInfoBarPayload } from '#/components/outpatient/patient-card.types';

import { ref } from 'vue';

import { ElTabPane, ElTabs } from 'element-plus';

import NurseExecuteCenter from './nurse-execute-center.vue';
import NurseSkinTestCenter from './nurse-skin-test-center.vue';

defineOptions({
  name: 'NurseWorkstationCenter',
});

defineProps<Props>();

const emit = defineEmits<{
  cancelExecute: [row: NurseAdviceExecuteRow];
  deleteSkinTestRow: [allergyRecordId: string];
  execute: [];
  refreshSkinTest: [];
  saveSkinTestRow: [draft: NurseSkinTestRowDraft];
  selectionChange: [keys: string[]];
}>();

const activeTab = defineModel<'execute' | 'skintest'>('activeTab', { default: 'execute' });

interface Props {
  executeItems: NurseAdviceExecuteRow[];
  executeTableLoading: boolean;
  currentPatient: null | NursePatient;
  patient: PatientInfoBarPayload;
  doctorName: string;
  canExecuteSelected: boolean;
  skinTestRows: OutpatientSkinTestRowDto[];
  skinTestTableLoading: boolean;
  clinicalStatusOptions: OutpatientSkinTestEnumOptionDto[];
  verificationStatusOptions: OutpatientSkinTestEnumOptionDto[];
  criticalityOptions: OutpatientSkinTestEnumOptionDto[];
  severityOptions: OutpatientSkinTestEnumOptionDto[];
  practitionerOptions: { label: string; value: string }[];
  defaultPractitionerId?: string;
  savingSkinTestServiceRequestId?: string;
  deletingSkinTestAllergyRecordId?: string;
}

const executeCenterRef = ref<InstanceType<typeof NurseExecuteCenter> | null>(null);

defineExpose({
  clearTableSelection: () => executeCenterRef.value?.clearTableSelection(),
  setTableSelectionForKeys: (keys: string[]) =>
    executeCenterRef.value?.setTableSelectionForKeys(keys),
});
</script>

<template>
  <div class="flex h-full min-h-0 min-w-0 flex-col">
    <div class="bg-background shrink-0 px-2 pt-1.5">
      <ElTabs v-model="activeTab" class="nurse-workstation-tabs">
        <ElTabPane label="执行" name="execute" />
        <ElTabPane label="皮试" name="skintest" />
      </ElTabs>
    </div>
    <div class="relative min-h-0 min-w-0 flex-1 overflow-hidden">
      <NurseExecuteCenter
        v-show="activeTab === 'execute'"
        ref="executeCenterRef"
        class="absolute inset-0 flex min-h-0 min-w-0 flex-col"
        :execute-items="executeItems"
        :execute-table-loading="executeTableLoading"
        :current-patient="currentPatient"
        :patient="patient"
        :doctor-name="doctorName"
        :can-execute-selected="canExecuteSelected"
        @selection-change="emit('selectionChange', $event)"
        @execute="emit('execute')"
        @cancel-execute="emit('cancelExecute', $event)"
      />
      <NurseSkinTestCenter
        v-show="activeTab === 'skintest'"
        class="absolute inset-0 flex min-h-0 min-w-0 flex-col"
        :skin-test-rows="skinTestRows"
        :skin-test-table-loading="skinTestTableLoading"
        :current-patient="currentPatient"
        :patient="patient"
        :doctor-name="doctorName"
        :clinical-status-options="clinicalStatusOptions"
        :verification-status-options="verificationStatusOptions"
        :criticality-options="criticalityOptions"
        :severity-options="severityOptions"
        :practitioner-options="practitionerOptions"
        :default-practitioner-id="defaultPractitionerId"
        :saving-service-request-id="savingSkinTestServiceRequestId"
        :deleting-allergy-record-id="deletingSkinTestAllergyRecordId"
        @refresh="emit('refreshSkinTest')"
        @save-row="emit('saveSkinTestRow', $event)"
        @delete-row="emit('deleteSkinTestRow', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.nurse-workstation-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.nurse-workstation-tabs :deep(.el-tabs__content) {
  display: none;
}
</style>
