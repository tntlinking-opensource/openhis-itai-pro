<script setup lang="ts">
import { computed, ref } from 'vue';

import { ElButton } from 'element-plus';

import WorkInjury2301ApprovalDialog from './work-injury-2301-approval-dialog.vue';

defineOptions({ name: 'WorkInjury2301RegisterAction' });

const props = defineProps<{
  encounterId?: string | number;
  contractId?: string | number;
  /** 确认框底栏等紧凑场景使用 link 按钮。 */
  link?: boolean;
}>();

const emit = defineEmits<{
  completed: [];
}>();

const dialogRef = ref<InstanceType<typeof WorkInjury2301ApprovalDialog> | null>(null);

const ready = computed(
  () =>
    props.encounterId != null &&
    props.encounterId !== '' &&
    props.contractId != null &&
    props.contractId !== '',
);

function openDialog() {
  dialogRef.value?.openDialog();
}

defineExpose({ openDialog });
</script>

<template>
  <template v-if="ready">
    <ElButton :type="'warning'" :link="link" @click="openDialog"> 工伤审批上传（2301） </ElButton>
    <WorkInjury2301ApprovalDialog
      ref="dialogRef"
      :encounter-id="encounterId!"
      :contract-id="contractId"
      @completed="emit('completed')"
    />
  </template>
</template>
