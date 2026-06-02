<!--
  库房单据工作站：单据级底部操作按钮组（占位事件名，由业务页监听）。
-->
<script setup lang="ts">
import { Check, RefreshLeft } from '@element-plus/icons-vue';
import { ElButton } from 'element-plus';

import InvActionSep from './inv-action-sep.vue';

defineOptions({ name: 'InvDocActions' });

withDefaults(
  defineProps<{
    approveDisabled?: boolean;
    rejectDisabled?: boolean;
    removeDisabled?: boolean;
    saveAndSubmitDisabled?: boolean;
    saveDisabled?: boolean;
    submitApprovalDisabled?: boolean;
    withdrawDisabled?: boolean;
  }>(),
  {
    approveDisabled: false,
    rejectDisabled: false,
    removeDisabled: false,
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
</script>

<template>
  <ElButton type="primary" :disabled="saveDisabled" @click="emit('save')">保存单据</ElButton>
  <ElButton type="primary" plain :disabled="saveAndSubmitDisabled" @click="emit('saveAndSubmit')">
    保存并提交审批
  </ElButton>
  <ElButton type="danger" plain :disabled="removeDisabled" @click="emit('removeDoc')">
    删除单据
  </ElButton>
  <InvActionSep />
  <ElButton type="primary" plain :disabled="submitApprovalDisabled" @click="emit('submitApproval')">
    提交审批
  </ElButton>
  <ElButton :icon="RefreshLeft" :disabled="withdrawDisabled" @click="emit('withdraw')">
    撤回
  </ElButton>
  <InvActionSep />
  <ElButton type="success" :icon="Check" :disabled="approveDisabled" @click="emit('approve')">
    审批
  </ElButton>
  <ElButton type="warning" :disabled="rejectDisabled" @click="emit('reject')">驳回</ElButton>
</template>
