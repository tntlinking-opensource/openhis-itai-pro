<script setup lang="ts">
import type { ServiceApplyPaperSize } from '../service-apply-paper-preference';

import { ref } from 'vue';

import { DEFAULT_SERVICE_APPLY_PAPER_SIZE } from '../service-apply-paper-preference';

import ServiceApplyPaperDialog from './service-apply-paper-dialog.vue';

defineOptions({ name: 'ServiceApplyPaperDialogHost' });

interface Props {
  confirmText?: string;
  initialPaperSize?: ServiceApplyPaperSize;
  title?: string;
}

withDefaults(defineProps<Props>(), {
  confirmText: '确定',
  initialPaperSize: DEFAULT_SERVICE_APPLY_PAPER_SIZE,
  title: '选择申请单纸张',
});

const emit = defineEmits<{
  cancel: [];
  confirm: [payload: { paperSize: ServiceApplyPaperSize }];
}>();

const visible = ref(true);

function handleConfirm(payload: { paperSize: ServiceApplyPaperSize }): void {
  visible.value = false;
  emit('confirm', payload);
}

function handleCancel(): void {
  visible.value = false;
  emit('cancel');
}
</script>

<template>
  <ServiceApplyPaperDialog
    :visible="visible"
    :title="title"
    :confirm-text="confirmText"
    :initial-paper-size="initialPaperSize"
    @confirm="handleConfirm"
    @cancel="handleCancel"
  />
</template>
