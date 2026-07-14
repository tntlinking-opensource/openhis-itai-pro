<script setup lang="ts">
import type { ServiceApplyPrintMeta } from '#/utils/print/print-service-apply';

import type { ServiceApplyKind, ServiceApplyPatientSummary } from './service-apply.types';

import { ref } from 'vue';

import ServiceApplyWorkspace from './service-apply-workspace.vue';

defineOptions({ name: 'ServiceApplyTab' });

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    patientId?: string;
    serviceApplyPatient?: ServiceApplyPatientSummary;
    serviceApplyPrintMeta?: ServiceApplyPrintMeta;
  }>(),
  { encounterId: '', patientId: '' },
);

const emit = defineEmits<{
  'open-apply': [kind: ServiceApplyKind];
}>();

const workspaceRef = ref<InstanceType<typeof ServiceApplyWorkspace> | null>(null);

defineExpose({
  reload: () => workspaceRef.value?.reload(),
});
</script>

<template>
  <ServiceApplyWorkspace
    ref="workspaceRef"
    v-bind="props"
    @open-apply="emit('open-apply', $event)"
  />
</template>
