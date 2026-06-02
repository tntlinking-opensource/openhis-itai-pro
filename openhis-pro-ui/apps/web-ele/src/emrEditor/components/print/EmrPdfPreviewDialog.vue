<script setup lang="ts">
import { computed, toValue } from 'vue';

import { ElButton, ElDialog, ElMessage } from 'element-plus';

defineOptions({
  name: 'EmrPdfPreviewDialog',
});

const props = withDefaults(
  defineProps<{
    visible?: boolean;
    loading?: boolean;
    silentPrinting?: boolean;
    pdfBlobUrl?: string;
    previewStale?: boolean;
    previewTruncated?: boolean;
    pageCount?: number;
    renderedAtLabel?: string;
    errorMessage?: string;
    title?: string;
  }>(),
  {
    visible: false,
    loading: false,
    silentPrinting: false,
    pdfBlobUrl: '',
    previewStale: false,
    previewTruncated: false,
    renderedAtLabel: '',
    errorMessage: '',
    title: '打印预览',
  },
);

const emit = defineEmits<{
  'update:visible': [boolean];
  refresh: [];
  openNewTab: [];
  exportPdf: [];
  print: [];
  silentPrint: [];
}>();

const resolvedLoading = computed(() => Boolean(toValue(props.loading)));
const resolvedSilentPrinting = computed(() => Boolean(toValue(props.silentPrinting)));
const printActionLoading = computed(() => resolvedLoading.value || resolvedSilentPrinting.value);
const resolvedPdfUrl = computed(() => String(toValue(props.pdfBlobUrl) ?? '').trim());

const statusHint = computed(() => {
  const parts: string[] = [];
  if (props.previewStale) parts.push('内容已修改，请刷新预览');
  if (props.renderedAtLabel) parts.push(`同步于 ${props.renderedAtLabel}`);
  if (props.pageCount != null) parts.push(`共 ${props.pageCount} 页`);
  if (props.previewTruncated) parts.push('（预览已截断）');
  return parts.join(' · ');
});

function handleClose() {
  emit('update:visible', false);
}

function handleRefresh() {
  emit('refresh');
}

function handleOpenNewTab() {
  if (!resolvedPdfUrl.value) {
    ElMessage.warning('请先生成预览');
    return;
  }
  emit('openNewTab');
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="title"
    width="92%"
    top="4vh"
    class="emr-pdf-preview-dialog"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @closed="handleClose"
  >
    <div class="emr-pdf-preview-dialog__toolbar">
      <ElButton type="primary" :loading="resolvedLoading" @click="handleRefresh">刷新预览</ElButton>
      <ElButton :disabled="!resolvedPdfUrl || resolvedLoading" @click="handleOpenNewTab">
        新标签打开
      </ElButton>
      <ElButton :disabled="resolvedLoading" @click="emit('exportPdf')">导出 PDF</ElButton>
      <ElButton :disabled="printActionLoading" @click="emit('print')">打印</ElButton>
      <ElButton
        :disabled="printActionLoading || !resolvedPdfUrl"
        :loading="resolvedSilentPrinting"
        @click="emit('silentPrint')"
      >
        静默打印(试)
      </ElButton>
      <span v-if="statusHint" class="emr-pdf-preview-dialog__hint">{{ statusHint }}</span>
      <span v-if="errorMessage" class="emr-pdf-preview-dialog__error">{{ errorMessage }}</span>
    </div>

    <div v-loading="resolvedLoading" class="emr-pdf-preview-dialog__body">
      <iframe
        v-if="resolvedPdfUrl"
        :key="resolvedPdfUrl"
        :src="resolvedPdfUrl"
        class="emr-pdf-preview-dialog__iframe"
        title="PDF 预览"
      />
      <div v-else-if="!resolvedLoading" class="emr-pdf-preview-dialog__empty">
        点击「刷新预览」生成 PDF
      </div>
    </div>
  </ElDialog>
</template>

<style scoped lang="scss">
.emr-pdf-preview-dialog__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.emr-pdf-preview-dialog__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.emr-pdf-preview-dialog__error {
  font-size: 12px;
  color: var(--el-color-danger);
}

.emr-pdf-preview-dialog__body {
  min-height: 70vh;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  overflow: hidden;
  background: #e5e7eb;
}

.emr-pdf-preview-dialog__iframe {
  display: block;
  width: 100%;
  height: 70vh;
  border: none;
  background: #fff;
}

.emr-pdf-preview-dialog__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
</style>
