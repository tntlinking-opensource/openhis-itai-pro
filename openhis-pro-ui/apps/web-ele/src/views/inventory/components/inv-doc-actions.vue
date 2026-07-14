<!--
  库房单据工作站：单据级底部操作按钮组（占位事件名，由业务页监听）。
  动账类页面传 require-finance-approval-role：审批/驳回仅在后端 canFinanceApprove 为 true 时展示。
-->
<script setup lang="ts">
import { computed } from 'vue';

import { Check, RefreshLeft } from '@element-plus/icons-vue';
import { ElButton } from 'element-plus';

import { useFinanceApprovalAccess } from '#/composables/use-finance-approval-access';

import InvActionSep from './inv-action-sep.vue';

defineOptions({ name: 'InvDocActions' });

const props = withDefaults(
  defineProps<{
    approveDisabled?: boolean;
    rejectDisabled?: boolean;
    removeDisabled?: boolean;
    /** 动账类页面为 true：审批按钮跟随后端 canFinanceApprove */
    requireFinanceApprovalRole?: boolean;
    saveAndSubmitDisabled?: boolean;
    saveDisabled?: boolean;
    submitApprovalDisabled?: boolean;
    withdrawDisabled?: boolean;
  }>(),
  {
    approveDisabled: false,
    rejectDisabled: false,
    removeDisabled: false,
    requireFinanceApprovalRole: false,
    saveAndSubmitDisabled: false,
    saveDisabled: false,
    submitApprovalDisabled: false,
    withdrawDisabled: false,
  },
);

const emit = defineEmits<{
  approve: [];
  reject: [];
  removeDoc: [];
  save: [];
  saveAndSubmit: [];
  submitApproval: [];
  withdraw: [];
}>();

const { canFinanceApprove } = useFinanceApprovalAccess();

const showApproveRejectActions = computed(
  () => !props.requireFinanceApprovalRole || canFinanceApprove.value,
);
</script>

<template>
  <ElButton type="primary" :disabled="saveDisabled" @click="emit('save')">保存单据</ElButton>
  <ElButton type="danger" plain :disabled="removeDisabled" @click="emit('removeDoc')">
    删除单据
  </ElButton>
  <InvActionSep />
  <ElButton type="primary" plain :disabled="submitApprovalDisabled" @click="emit('submitApproval')">
    提交审批
  </ElButton>
  <ElButton type="primary" plain :disabled="saveAndSubmitDisabled" @click="emit('saveAndSubmit')">
    保存并提交审批
  </ElButton>
  <ElButton :icon="RefreshLeft" :disabled="withdrawDisabled" @click="emit('withdraw')">
    撤回
  </ElButton>
  <template v-if="showApproveRejectActions">
    <InvActionSep />
    <ElButton type="success" :icon="Check" :disabled="approveDisabled" @click="emit('approve')">
      审批
    </ElButton>
    <ElButton type="warning" :disabled="rejectDisabled" @click="emit('reject')">驳回</ElButton>
  </template>
</template>
