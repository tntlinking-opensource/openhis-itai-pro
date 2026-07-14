<script setup lang="ts">
import { computed, ref } from 'vue';

import { ElButton } from 'element-plus';

import WorkInjury2301ApprovalDialog from './work-injury-2301-approval-dialog.vue';

defineOptions({ name: 'WorkInjuryDoctorApprovalAction' });

const props = defineProps<{
  patient: {
    encounterId?: string | number;
    workInjuryEncounter?: boolean | null;
    workInjuryQualificationValid?: boolean | null;
    workInjuryNeeds2301Upload?: boolean | null;
    workInjuryContractId?: null | number | string;
    workInjuryApprovalStatusLabel?: null | string;
  } | null;
}>();

const emit = defineEmits<{
  completed: [];
}>();

const dialogRef = ref<InstanceType<typeof WorkInjury2301ApprovalDialog> | null>(null);

const visible = computed(() => {
  const p = props.patient;
  if (!p?.workInjuryEncounter) {
    return false;
  }
  if (p.workInjuryQualificationValid === true) {
    return false;
  }
  return p.workInjuryNeeds2301Upload !== false;
});

const statusHint = computed(() => {
  const label = (props.patient?.workInjuryApprovalStatusLabel ?? '').trim();
  return label || '待审批上传';
});

function openDialog() {
  dialogRef.value?.openDialog();
}
</script>

<template>
  <template v-if="visible && patient?.encounterId">
    <ElButton type="warning" link @click="openDialog"> 工伤审批（{{ statusHint }}） </ElButton>
    <WorkInjury2301ApprovalDialog
      ref="dialogRef"
      :encounter-id="patient.encounterId"
      :contract-id="patient.workInjuryContractId ?? undefined"
      @completed="emit('completed')"
    />
  </template>
</template>
