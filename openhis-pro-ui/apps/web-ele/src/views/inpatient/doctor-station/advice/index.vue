<script setup lang="ts">
import type { InpatientAdviceQuickAction } from './components/inpatient-advice-quick-action-bar.vue';

import { ref } from 'vue';

import InpatientAdviceWorkspace from './components/inpatient-advice-workspace.vue';

defineOptions({ name: 'InpatientDoctorStationAdviceList' });

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    patientId?: string;
    patientServiceTypeName?: string;
  }>(),
  { encounterId: '', patientId: '', patientServiceTypeName: '' },
);

const emit = defineEmits<{
  quickAction: [action: InpatientAdviceQuickAction];
}>();

const workspaceRef = ref<InstanceType<typeof InpatientAdviceWorkspace> | null>(null);

defineExpose({
  loadAdviceInit: () => workspaceRef.value?.loadAdviceInit(),
  reload: () => workspaceRef.value?.reload(),
});
</script>

<template>
  <div class="h-full min-h-0">
    <InpatientAdviceWorkspace
      ref="workspaceRef"
      class="h-full"
      v-bind="props"
      @quick-action="emit('quickAction', $event)"
    />
  </div>
</template>
