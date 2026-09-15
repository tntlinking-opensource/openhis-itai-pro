<script setup lang="ts">
import type { InpatientAdviceQuickAction } from './components/inpatient-advice-quick-action-bar.vue';

import type { ServiceApplyDocumentGroup } from '#/views/outpatient/doctor-station/service-apply/service-apply-doc-groups';

import { ref } from 'vue';

import InpatientAdviceWorkspace from './components/inpatient-advice-workspace.vue';

defineOptions({ name: 'InpatientDoctorStationAdviceList' });

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    /** 外部强制只读（病案菜单编辑门禁） */
    forceWriteDisabled?: boolean;
    patientId?: string;
    /**
     * 开立时间默认排序；病案首页临床侧栏传 asc。
     */
    defaultTimeSortOrder?: 'asc' | 'desc';
    /** 纳入中药饮片并按方合并（病案首页医嘱侧栏） */
    includeTcmPrescriptions?: boolean;
  }>(),
  {
    encounterId: '',
    forceWriteDisabled: false,
    patientId: '',
    defaultTimeSortOrder: undefined,
    includeTcmPrescriptions: false,
  },
);

const emit = defineEmits<{
  editServiceApply: [group: ServiceApplyDocumentGroup];
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
      @edit-service-apply="emit('editServiceApply', $event)"
    />
  </div>
</template>
