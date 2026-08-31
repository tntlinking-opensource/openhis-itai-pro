<!--
  库房单据打印前选择纸张：三联单（针式连续纸）或 A4。
-->
<script setup lang="ts">
import type { InvFormPaperKind } from '../form-print-preference';

import { ref, watch } from 'vue';

import { ElButton, ElDialog } from 'element-plus';

import { INV_FORM_PAPER_KIND, normalizeInvFormPaperKind } from '../form-print-preference';
import InvFormPaperKindCards from './inv-form-paper-kind-cards.vue';

defineOptions({ name: 'InvFormPaperKindDialog' });

interface Props {
  confirmText?: string;
  initialPaperKind?: InvFormPaperKind;
  title?: string;
  visible?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  confirmText: '确定',
  initialPaperKind: INV_FORM_PAPER_KIND.triple,
  title: '选择纸张',
  visible: true,
});

const emit = defineEmits<{
  cancel: [];
  confirm: [payload: { paperKind: InvFormPaperKind }];
}>();

const dialogVisible = ref(props.visible);
const paperKind = ref<InvFormPaperKind>(normalizeInvFormPaperKind(props.initialPaperKind));

/** 把外部初始纸张同步到表单 */
function syncFormFromProps(): void {
  paperKind.value = normalizeInvFormPaperKind(props.initialPaperKind);
}

/** 关闭并取消 */
function handleCancel(): void {
  dialogVisible.value = false;
  emit('cancel');
}

/** 提交所选纸张 */
function handleConfirm(): void {
  dialogVisible.value = false;
  emit('confirm', { paperKind: paperKind.value });
}

watch(
  () => props.visible,
  (value) => {
    dialogVisible.value = value;
    if (value) {
      syncFormFromProps();
    }
  },
);
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="title"
    width="480px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @close="handleCancel"
  >
    <InvFormPaperKindCards v-model="paperKind" />
    <template #footer>
      <ElButton @click="handleCancel">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">{{ confirmText }}</ElButton>
    </template>
  </ElDialog>
</template>
