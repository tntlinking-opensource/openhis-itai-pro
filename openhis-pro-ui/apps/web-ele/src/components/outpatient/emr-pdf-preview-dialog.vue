<script setup lang="ts">
import type { EmrPrintPaperPayload } from '#/api/emr/document';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { ElButton, ElDialog, ElMessage, ElOption, ElSelect, useZIndex } from 'element-plus';

import {
  pdfBase64ToBlob,
  pdfBase64ToObjectUrl,
  revokePdfObjectUrl,
} from '#/emrEditor/utils/emr-pdf-blob';
import {
  buildEmrPrintPaperPayload,
  EMR_PRINT_PAPER_ORIENTATION_OPTIONS,
  EMR_PRINT_PAPER_SIZE_OPTIONS,
  type EmrPrintPaperOrientation,
  type EmrPrintPaperSizeKey,
} from '#/emrEditor/components/simple-editor/emr-print-config';

defineOptions({ name: 'EmrPdfPreviewDialog' });

const props = withDefaults(
  defineProps<{
    modelValue?: boolean;
    pdfBase64?: string;
    pageCount?: number;
    title?: string;
    /** 打开后自动唤起系统打印框（正式「打印」） */
    autoPrint?: boolean;
    /** 换纸重出 PDF 时禁用下拉 */
    loading?: boolean;
  }>(),
  {
    modelValue: false,
    pdfBase64: '',
    pageCount: undefined,
    title: '打印预览',
    autoPrint: false,
    loading: false,
  },
);

const emit = defineEmits<{
  'update:modelValue': [boolean];
  /** 用户改纸张后，由宿主按同一份正文重出 PDF */
  'paper-change': [paper: EmrPrintPaperPayload];
}>();

const iframeRef = ref<HTMLIFrameElement | null>(null);
const objectUrl = ref('');
const { nextZIndex } = useZIndex();
const dialogZIndex = ref<number | undefined>();
const paperSizeKey = ref<EmrPrintPaperSizeKey>('A4');
const paperOrientation = ref<EmrPrintPaperOrientation>('portrait');

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      dialogZIndex.value = nextZIndex();
      paperSizeKey.value = 'A4';
      paperOrientation.value = 'portrait';
    }
  },
);

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
});

/** 释放当前预览 URL，避免 blob 泄漏 */
function revokeCurrent() {
  revokePdfObjectUrl(objectUrl.value);
  objectUrl.value = '';
}

watch(
  () => [props.modelValue, props.pdfBase64] as const,
  ([open, b64]) => {
    revokeCurrent();
    if (!open || !b64?.trim()) return;
    objectUrl.value = pdfBase64ToObjectUrl(b64);
  },
  { immediate: true },
);

/** 把当前下拉选项收成后端 paper，通知宿主重出 PDF */
function emitPaperChange() {
  emit('paper-change', buildEmrPrintPaperPayload(paperSizeKey.value, paperOrientation.value));
}

/** 用户改纸型 */
function onPaperSizeChange(key: EmrPrintPaperSizeKey) {
  paperSizeKey.value = key;
  emitPaperChange();
}

/** 用户改纵向/横向 */
function onPaperOrientationChange(next: EmrPrintPaperOrientation) {
  paperOrientation.value = next;
  emitPaperChange();
}

function onIframeLoad() {
  if (!props.autoPrint) return;
  try {
    iframeRef.value?.contentWindow?.focus();
    iframeRef.value?.contentWindow?.print();
  } catch {
    ElMessage.warning('无法自动调起打印，请使用下方「打印」按钮');
  }
}

function handlePrint() {
  const win = iframeRef.value?.contentWindow;
  if (!win) {
    ElMessage.warning('PDF 尚未加载完成');
    return;
  }
  try {
    win.focus();
    win.print();
  } catch {
    ElMessage.error('调起打印失败');
  }
}

function handleDownload() {
  const b64 = props.pdfBase64?.trim();
  if (!b64) {
    ElMessage.warning('没有可下载的 PDF');
    return;
  }
  const blob = pdfBase64ToBlob(b64);
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${(props.title || '病历').replaceAll(/[\\/:*?"<>|]/g, '_')}.pdf`;
  a.click();
  URL.revokeObjectURL(url);
}

onBeforeUnmount(() => {
  revokeCurrent();
});
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="title"
    width="920px"
    top="4vh"
    append-to-body
    :z-index="dialogZIndex"
    destroy-on-close
    class="emr-pdf-preview-dialog"
  >
    <div class="mb-2 flex flex-wrap items-center gap-2">
      <span class="text-xs" style="color: var(--el-text-color-regular)">纸张</span>
      <ElSelect
        :model-value="paperSizeKey"
        size="small"
        style="width: 168px"
        :disabled="loading"
        @change="onPaperSizeChange"
      >
        <ElOption
          v-for="item in EMR_PRINT_PAPER_SIZE_OPTIONS"
          :key="item.key"
          :label="item.label"
          :value="item.key"
        />
      </ElSelect>
      <ElSelect
        :model-value="paperOrientation"
        size="small"
        style="width: 96px"
        :disabled="loading"
        @change="onPaperOrientationChange"
      >
        <ElOption
          v-for="item in EMR_PRINT_PAPER_ORIENTATION_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </ElSelect>
      <ElButton type="primary" :disabled="loading" @click="handlePrint">打印</ElButton>
      <ElButton :disabled="loading" @click="handleDownload">下载 PDF</ElButton>
      <span v-if="pageCount" class="text-xs" style="color: var(--el-text-color-secondary)">
        共 {{ pageCount }} 页
      </span>
    </div>
    <div v-loading="loading">
      <iframe
        v-if="objectUrl"
        ref="iframeRef"
        :src="objectUrl"
        class="emr-pdf-preview-dialog__frame"
        title="病历 PDF 预览"
        @load="onIframeLoad"
      />
      <div v-else class="py-10 text-center text-sm" style="color: var(--el-text-color-secondary)">
        暂无预览内容
      </div>
    </div>
  </ElDialog>
</template>

<style scoped>
.emr-pdf-preview-dialog__frame {
  display: block;
  width: 100%;
  height: 72vh;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: #f5f5f5;
}
</style>
