<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';

import * as pdfjsLib from 'pdfjs-dist';
import pdfjsWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

defineOptions({ name: 'ReportPdfStageCanvas' });

const props = withDefaults(
  defineProps<{
    pdfBlob?: Blob | null;
    /** 1-based page index */
    pageNumber?: number;
  }>(),
  {
    pdfBlob: null,
    pageNumber: 1,
  },
);

const emit = defineEmits<{
  pageSizeMm: [size: { widthMm: number; heightMm: number }];
  renderError: [message: string];
}>();

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfjsWorker;

const canvasRef = ref<HTMLCanvasElement | null>(null);
const renderToken = ref(0);

const PT_TO_MM = 25.4 / 72;

function ptToMm(pt: number): number {
  return Math.round(pt * PT_TO_MM * 100) / 100;
}

async function renderPdf(blob: Blob, pageNumber: number) {
  const token = ++renderToken.value;
  const canvas = canvasRef.value;
  if (!canvas) {
    return;
  }
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return;
  }
  try {
    const data = await blob.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({ data }).promise;
    const safePage = Math.min(Math.max(pageNumber, 1), pdf.numPages);
    const page = await pdf.getPage(safePage);
    if (token !== renderToken.value) {
      return;
    }
    const viewport = page.getViewport({ scale: 1 });
    canvas.width = Math.max(1, Math.floor(viewport.width));
    canvas.height = Math.max(1, Math.floor(viewport.height));
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    await page.render({ canvas, canvasContext: ctx, viewport }).promise;
    if (token !== renderToken.value) {
      return;
    }
    emit('pageSizeMm', {
      widthMm: ptToMm(viewport.width),
      heightMm: ptToMm(viewport.height),
    });
  } catch (error) {
    if (token !== renderToken.value) {
      return;
    }
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    emit('renderError', error instanceof Error ? error.message : 'PDF 渲染失败');
  }
}

watch(
  () => [props.pdfBlob, props.pageNumber] as const,
  ([blob, pageNumber]) => {
    if (!blob) {
      renderToken.value += 1;
      const canvas = canvasRef.value;
      if (canvas) {
        const ctx = canvas.getContext('2d');
        ctx?.clearRect(0, 0, canvas.width, canvas.height);
        canvas.width = 0;
        canvas.height = 0;
      }
      return;
    }
    void renderPdf(blob, pageNumber ?? 1);
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  renderToken.value += 1;
});
</script>

<template>
  <canvas ref="canvasRef" class="report-pdf-stage-canvas" aria-label="打印预览 PDF" />
</template>

<style scoped>
.report-pdf-stage-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border: none;
  z-index: 1;
  display: block;
}
</style>
