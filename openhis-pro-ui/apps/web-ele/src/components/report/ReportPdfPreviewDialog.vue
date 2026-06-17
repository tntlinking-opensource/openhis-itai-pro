<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { ElButton, ElDialog, ElMessage } from 'element-plus';

import { pdfBlobToObjectUrl, revokePdfObjectUrl } from '#/utils/report/pdf-blob';

defineOptions({ name: 'ReportPdfPreviewDialog' });

const props = withDefaults(
  defineProps<{
    modelValue?: boolean;
    loading?: boolean;
    pdfBlob?: Blob | null;
    title?: string;
  }>(),
  {
    modelValue: false,
    loading: false,
    pdfBlob: null,
    title: 'PDF 预览',
  },
);

const emit = defineEmits<{
  'update:modelValue': [boolean];
  refresh: [];
}>();

const pdfBlobUrl = ref('');
let lastPdfUrl = '';

const resolvedPdfUrl = computed(() => pdfBlobUrl.value.trim());

function syncPdfUrl(blob: Blob | null | undefined) {
  revokePdfObjectUrl(lastPdfUrl);
  lastPdfUrl = '';
  pdfBlobUrl.value = '';
  if (!blob || blob.size <= 0) {
    return;
  }
  const url = pdfBlobToObjectUrl(blob);
  lastPdfUrl = url;
  pdfBlobUrl.value = url;
}

watch(
  () => props.pdfBlob,
  (blob) => {
    syncPdfUrl(blob);
  },
  { immediate: true },
);

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      return;
    }
    syncPdfUrl(props.pdfBlob);
  },
);

function handleClose() {
  emit('update:modelValue', false);
}

function handleOpenNewTab() {
  if (!resolvedPdfUrl.value) {
    ElMessage.warning('请先生成预览');
    return;
  }
  window.open(resolvedPdfUrl.value, '_blank', 'noopener,noreferrer');
}

function handleRefresh() {
  emit('refresh');
}

onBeforeUnmount(() => {
  revokePdfObjectUrl(lastPdfUrl);
  lastPdfUrl = '';
  pdfBlobUrl.value = '';
});
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="title"
    width="92%"
    top="4vh"
    class="report-pdf-preview-dialog"
    destroy-on-close
    align-center
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClose"
  >
    <div class="report-pdf-preview-dialog__toolbar">
      <ElButton type="primary" :loading="loading" @click="handleRefresh">刷新预览</ElButton>
      <ElButton :disabled="!resolvedPdfUrl || loading" @click="handleOpenNewTab">
        新标签打开
      </ElButton>
    </div>

    <div v-loading="loading" class="report-pdf-preview-dialog__body">
      <iframe
        v-if="resolvedPdfUrl"
        :key="resolvedPdfUrl"
        :src="resolvedPdfUrl"
        class="report-pdf-preview-dialog__iframe"
        title="PDF 预览"
      />
      <div v-else-if="!loading" class="report-pdf-preview-dialog__empty">暂无 PDF 预览</div>
    </div>
  </ElDialog>
</template>

<style scoped>
.report-pdf-preview-dialog__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.report-pdf-preview-dialog__body {
  min-height: 70vh;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  overflow: hidden;
  background: #e5e7eb;
}

.report-pdf-preview-dialog__iframe {
  display: block;
  width: 100%;
  height: 70vh;
  border: none;
  background: #fff;
}

.report-pdf-preview-dialog__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
</style>
