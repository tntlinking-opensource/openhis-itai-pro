<!--
  审批调价单：展示摘要并填写审批意见。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { ElButton, ElDialog, ElForm, ElFormItem, ElInput } from 'element-plus';

defineOptions({ name: 'PriceAdjustmentApproveDialog' });

const props = withDefaults(
  defineProps<{
    approveMode?: 'approve' | 'reject';
    busNo?: string;
    lineCount?: number;
    summaryText?: string;
    visible: boolean;
  }>(),
  {
    approveMode: 'approve',
    busNo: '',
    lineCount: 0,
    summaryText: '',
  },
);

const emit = defineEmits<{
  confirm: [comment: string];
  'update:visible': [value: boolean];
}>();

const comment = ref('');

const dialogTitle = computed(() =>
  props.approveMode === 'reject' ? `审批拒绝 · ${props.busNo}` : `审批通过 · ${props.busNo}`,
);

const commentRequired = computed(() => props.approveMode === 'reject');

/** 弹窗打开时清空意见 */
watch(
  () => props.visible,
  (open) => {
    if (open) comment.value = '';
  },
);

/** 关闭弹窗 */
function handleClose() {
  emit('update:visible', false);
}

/** 校验意见后提交审批结果 */
function handleConfirm() {
  const text = comment.value.trim();
  if (commentRequired.value && text === '') return;
  emit('confirm', text);
  emit('update:visible', false);
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="dialogTitle"
    width="480px"
    destroy-on-close
    @close="handleClose"
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="approve-summary mb-4 text-sm text-[var(--el-text-color-regular)]">
      <p>明细 {{ lineCount }} 行</p>
      <p v-if="summaryText">{{ summaryText }}</p>
    </div>
    <ElForm label-width="80px" @submit.prevent="handleConfirm">
      <ElFormItem label="审批意见" :required="commentRequired">
        <ElInput
          v-model="comment"
          type="textarea"
          :rows="3"
          :placeholder="commentRequired ? '拒绝时必填' : '选填'"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton :type="approveMode === 'reject' ? 'warning' : 'success'" @click="handleConfirm">
        确认
      </ElButton>
    </template>
  </ElDialog>
</template>
