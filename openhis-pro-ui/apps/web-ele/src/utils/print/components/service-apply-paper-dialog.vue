<!--
  门诊申请单打印前选择纸张：A4 竖版 / A5 竖版。
-->
<script setup lang="ts">
import type { ServiceApplyPaperSize } from '../service-apply-paper-preference';

import { ref, watch } from 'vue';

import { ElButton, ElDialog, ElRadio, ElRadioGroup } from 'element-plus';

import {
  DEFAULT_SERVICE_APPLY_PAPER_SIZE,
  normalizeServiceApplyPaperSize,
  SERVICE_APPLY_PAPER_SIZE,
  SERVICE_APPLY_PAPER_SIZE_LABELS,
} from '../service-apply-paper-preference';

defineOptions({ name: 'ServiceApplyPaperDialog' });

interface Props {
  confirmText?: string;
  initialPaperSize?: ServiceApplyPaperSize;
  title?: string;
  visible?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  confirmText: '确定',
  initialPaperSize: DEFAULT_SERVICE_APPLY_PAPER_SIZE,
  title: '选择申请单纸张',
  visible: true,
});

const emit = defineEmits<{
  cancel: [];
  confirm: [payload: { paperSize: ServiceApplyPaperSize }];
}>();

const dialogVisible = ref(props.visible);
const paperSize = ref<ServiceApplyPaperSize>(
  normalizeServiceApplyPaperSize(props.initialPaperSize),
);

/** 同步外部初始值 */
function syncFormFromProps(): void {
  paperSize.value = normalizeServiceApplyPaperSize(props.initialPaperSize);
}

function handleCancel(): void {
  dialogVisible.value = false;
  emit('cancel');
}

function handleConfirm(): void {
  dialogVisible.value = false;
  emit('confirm', { paperSize: paperSize.value });
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
    width="420px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @close="handleCancel"
  >
    <ElRadioGroup v-model="paperSize" class="service-apply-paper-options">
      <ElRadio :value="SERVICE_APPLY_PAPER_SIZE.a4" border>
        {{ SERVICE_APPLY_PAPER_SIZE_LABELS[SERVICE_APPLY_PAPER_SIZE.a4] }}
        <span class="hint">210×297mm 竖版</span>
      </ElRadio>
      <ElRadio :value="SERVICE_APPLY_PAPER_SIZE.a5" border>
        {{ SERVICE_APPLY_PAPER_SIZE_LABELS[SERVICE_APPLY_PAPER_SIZE.a5] }}
        <span class="hint">148×210mm 竖版</span>
      </ElRadio>
    </ElRadioGroup>
    <template #footer>
      <ElButton @click="handleCancel">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">{{ confirmText }}</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.service-apply-paper-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.service-apply-paper-options :deep(.el-radio) {
  width: 100%;
  margin-right: 0;
  height: auto;
  padding: 12px 16px;
}

.hint {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
