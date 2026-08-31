<!--
  库房单据工作站：单据级底部操作按钮组。
  财务审批族传 require-finance-approval-role：
  - 普通进入：隐藏审批/驳回，保留制单按钮；
  - 财务审批深链：仅展示审批/驳回，隐藏制单按钮（角色由后端 Gate 校验，与交账审批同口径）。
  打印 / 打印预览在有单据时可用（printDisabled 控制）。
-->
<script setup lang="ts">
import { computed } from 'vue';

import { Check, Printer, RefreshLeft, View } from '@element-plus/icons-vue';
import { ElButton } from 'element-plus';

import { useFromFinanceApproval } from '#/composables/use-from-finance-approval';

import InvActionSep from './inv-action-sep.vue';

defineOptions({ name: 'InvDocActions' });

const props = withDefaults(
  defineProps<{
    approveDisabled?: boolean;
    printDisabled?: boolean;
    rejectDisabled?: boolean;
    removeDisabled?: boolean;
    /**
     * 财务审批族单据为 true：原业务页默认隐藏审批/驳回；
     * 财务审批深链时仅展示审驳（有无 ROLE_FINANCE_APPROVER 由后端校验）。
     */
    requireFinanceApprovalRole?: boolean;
    saveAndSubmitDisabled?: boolean;
    saveDisabled?: boolean;
    submitApprovalDisabled?: boolean;
    withdrawDisabled?: boolean;
  }>(),
  {
    approveDisabled: false,
    printDisabled: true,
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
  print: [payload: { preview?: boolean }];
  reject: [];
  removeDoc: [];
  save: [];
  saveAndSubmit: [];
  submitApproval: [];
  withdraw: [];
}>();

const { fromFinanceApproval } = useFromFinanceApproval();

/** 财务审批只读审驳入口（深链即可展示；角色由后端 Gate 校验） */
const financeApprovalReviewMode = computed(
  () => props.requireFinanceApprovalRole && fromFinanceApproval.value,
);

/** 是否展示审批/驳回 */
const showApproveRejectActions = computed(() => {
  if (!props.requireFinanceApprovalRole) {
    return true;
  }
  return financeApprovalReviewMode.value;
});

/** 是否展示制单类按钮（保存/删除/提交/撤回） */
const showDocumentManageActions = computed(() => !financeApprovalReviewMode.value);
</script>

<template>
  <template v-if="showDocumentManageActions">
    <ElButton type="primary" :disabled="saveDisabled" @click="emit('save')">保存单据</ElButton>
    <ElButton type="danger" plain :disabled="removeDisabled" @click="emit('removeDoc')">
      删除单据
    </ElButton>
    <InvActionSep />
    <ElButton
      type="primary"
      plain
      :disabled="submitApprovalDisabled"
      @click="emit('submitApproval')"
    >
      提交审批
    </ElButton>
    <ElButton type="primary" plain :disabled="saveAndSubmitDisabled" @click="emit('saveAndSubmit')">
      保存并提交审批
    </ElButton>
    <ElButton :icon="RefreshLeft" :disabled="withdrawDisabled" @click="emit('withdraw')">
      撤回
    </ElButton>
  </template>
  <template v-if="showApproveRejectActions">
    <InvActionSep v-if="showDocumentManageActions" />
    <ElButton type="success" :icon="Check" :disabled="approveDisabled" @click="emit('approve')">
      审批
    </ElButton>
    <ElButton type="warning" :disabled="rejectDisabled" @click="emit('reject')">驳回</ElButton>
  </template>
  <InvActionSep />
  <ElButton :icon="View" :disabled="printDisabled" @click="emit('print', { preview: true })">
    打印预览
  </ElButton>
  <ElButton :icon="Printer" :disabled="printDisabled" @click="emit('print', { preview: false })">
    打印
  </ElButton>
</template>
