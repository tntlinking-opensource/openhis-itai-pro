<script setup lang="ts">
import type { ServiceApplyPrintMeta } from '#/utils/print/print-service-apply';
import type { ServiceApplyPatientSummary } from '../service-apply/service-apply.types';

import { ref } from 'vue';

import AdviceWorkspace from './components/advice-workspace.vue';

defineOptions({ name: 'Advice' });

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    patientId?: string;
    serviceApplyPatient?: ServiceApplyPatientSummary;
    serviceApplyPrintMeta?: ServiceApplyPrintMeta;
  }>(),
  { encounterId: '', patientId: '' },
);

const workspaceRef = ref<InstanceType<typeof AdviceWorkspace> | null>(null);

defineExpose({
  loadAdviceInit: () => workspaceRef.value?.loadAdviceInit(),
  reload: () => workspaceRef.value?.reload(),
});
</script>

<template>
  <AdviceWorkspace ref="workspaceRef" v-bind="props" />
</template>
