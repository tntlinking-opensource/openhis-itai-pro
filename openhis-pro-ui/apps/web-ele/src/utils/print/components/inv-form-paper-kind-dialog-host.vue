<script setup lang="ts">
import type { InvFormPaperKind, InvFormTripleSpec } from '../form-print-preference';

import { ref } from 'vue';

import { INV_FORM_PAPER_KIND, INV_FORM_TRIPLE_SPEC } from '../form-print-preference';

import InvFormPaperKindDialog from './inv-form-paper-kind-dialog.vue';

defineOptions({ name: 'InvFormPaperKindDialogHost' });

interface Props {
  confirmText?: string;
  initialPaperKind?: InvFormPaperKind;
  initialTripleSpec?: InvFormTripleSpec;
  title?: string;
}

withDefaults(defineProps<Props>(), {
  confirmText: '确定',
  initialPaperKind: INV_FORM_PAPER_KIND.triple,
  initialTripleSpec: INV_FORM_TRIPLE_SPEC.third,
  title: '选择纸张',
});

const emit = defineEmits<{
  cancel: [];
  confirm: [payload: { paperKind: InvFormPaperKind; tripleSpec: InvFormTripleSpec }];
}>();

const visible = ref(true);

/** 用户确认所选纸张 */
function handleConfirm(payload: {
  paperKind: InvFormPaperKind;
  tripleSpec: InvFormTripleSpec;
}): void {
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
    :initial-triple-spec="initialTripleSpec"
    @confirm="handleConfirm"
    @cancel="handleCancel"
  />
</template>
