<script setup lang="ts">
import { ref } from 'vue';

import { ElButton } from 'element-plus';

import WorkInjuryFilingDetailDialog from './work-injury-filing-detail-dialog.vue';

defineOptions({ name: 'WorkInjuryFilingDetailAction' });

const props = defineProps<{
  encounterId?: string | number;
  contractId?: string | number;
  /** 标题旁紧凑场景使用 link。 */
  link?: boolean;
}>();

const emit = defineEmits<{
  completed: [];
}>();

const dialogRef = ref<InstanceType<typeof WorkInjuryFilingDetailDialog> | null>(null);

function openDialog(options?: { autoGuideApproval?: boolean }) {
  dialogRef.value?.openDialog(options);
}

/** 无有效审批时引导：打开详情并自动进入 2301 / 自动绑定。 */
function openApprovalGuide() {
  openDialog({ autoGuideApproval: true });
}

defineExpose({ openDialog, openApprovalGuide });
</script>

<template>
  <ElButton type="primary" :link="link" @click="openDialog()">工伤备案详情</ElButton>
  <WorkInjuryFilingDetailDialog
    ref="dialogRef"
    :encounter-id="props.encounterId"
    :contract-id="props.contractId"
    @completed="emit('completed')"
  />
</template>
