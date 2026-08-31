<script setup lang="ts">
import type { InvFormPaperKind } from '../form-print-preference';

import { ref } from 'vue';

import { INV_FORM_PAPER_KIND } from '../form-print-preference';

import InvFormPaperKindDialog from './inv-form-paper-kind-dialog.vue';

defineOptions({ name: 'InvFormPaperKindDialogHost' });

interface Props {
  confirmText?: string;
  initialPaperKind?: InvFormPaperKind;
  title?: string;
}

withDefaults(defineProps<Props>(), {
  confirmText: '确定',
  initialPaperKind: INV_FORM_PAPER_KIND.triple,
  title: '选择纸张',
});

const emit = defineEmits<{
  cancel: [];
  confirm: [payload: { paperKind: InvFormPaperKind }];
}>();

const visible = ref(true);

/** 用户确认所选纸张 */
function handleConfirm(payload: { paperKind: InvFormPaperKind }): void {
  visible.value = false;
  emit('confirm', payload);
}

/** 用户取消 */
function handleCancel(): void {
  visible.value = false;
  emit('cancel');
}
</script>

<template>
  <InvFormPaperKindDialog
    :visible="visible"
    :title="title"
    :confirm-text="confirmText"
    :initial-paper-kind="initialPaperKind"
    @confirm="handleConfirm"
    @cancel="handleCancel"
  />
</template>
