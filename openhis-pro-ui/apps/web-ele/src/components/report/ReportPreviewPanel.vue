<script setup lang="ts">
import type { PrintElementDefDto } from '#/api/report/report-design';
import type { LayoutBox, LayoutItem, LayoutPage } from '#/types/report-layout';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { ElCheckbox, ElEmpty } from 'element-plus';

import OverlayBoxGuide from '#/components/report/designer/OverlayBoxGuide.vue';
import OverlayMmRuler from '#/components/report/designer/OverlayMmRuler.vue';
import ReportPdfStageCanvas from '#/components/report/ReportPdfStageCanvas.vue';
import { pageSizeMm, computeEffectiveBox, hasBoxGeometry } from '#/utils/report/overlay-layout';
import type { PreviewLayoutMetaDto } from '#/utils/report/report-preview';
import {
  hasUserGuideImageUrl,
  isOverlayPreview,
  pageAspectRatio,
  resolveGuideImageUrl,
} from '#/utils/report/report-preview';

defineOptions({ name: 'ReportPreviewPanel' });

const props = withDefaults(
  defineProps<{
    pdfBlob?: Blob | null;
    previewMeta?: PreviewLayoutMetaDto | null;
    loading?: boolean;
    showGuideToggle?: boolean;
    /** 设计器主区域：占满父容器并按可用空间缩放预览舞台 */
    fillContainer?: boolean;
    /** 设计器 overlay 模式：在预览上绘制元素落格框线 */
    overlayLayoutItems?: LayoutItem[];
    overlayElements?: PrintElementDefDto[];
    overlayPage?: LayoutPage | null;
    selectedElementCode?: string | null;
    showBoxGuide?: boolean;
    overlayEditable?: boolean;
    printOffsetMm?: number[];
    overlaySampleData?: Record<string, unknown> | null;
  }>(),
  {
    pdfBlob: null,
    previewMeta: null,
    loading: false,
    showGuideToggle: true,
    fillContainer: false,
    overlayLayoutItems: () => [],
    overlayElements: () => [],
    overlayPage: null,
    selectedElementCode: null,
    showBoxGuide: true,
    overlayEditable: false,
    printOffsetMm: undefined,
    overlaySampleData: null,
  },
);

const emit = defineEmits<{
  overlaySelect: [elementCode: string];
  overlayBoxChange: [payload: { elementCode: string; box: LayoutBox }];
}>();

const showGuide = ref(true);
const showBoxGuideLocal = ref(true);
const pdfPageSizeMm = ref<{ widthMm: number; heightMm: number } | null>(null);
const guideLoadFailed = ref(false);

const overlayMode = computed(
  () => isOverlayPreview(props.previewMeta) || props.overlayPage?.layoutMode === 'overlay',
);
const aspectRatio = computed(() => {
  if (pdfPageSizeMm.value) {
    return pdfPageSizeMm.value.widthMm / pdfPageSizeMm.value.heightMm;
  }
  const pageSize = props.previewMeta?.pageSize ?? props.overlayPage?.size;
  const orientation = props.previewMeta?.orientation ?? props.overlayPage?.orientation;
  if (props.previewMeta?.pageSize?.includes('×') && props.previewMeta.pageSize.includes('mm')) {
    const matched = props.previewMeta.pageSize.match(/^(\d+(?:\.\d+)?)×(\d+(?:\.\d+)?)mm$/);
    if (matched) {
      return Number(matched[1]) / Number(matched[2]);
    }
  }
  return pageAspectRatio(pageSize, orientation, props.overlayPage ?? undefined);
});
const overlayPageSizeOptions = computed(() =>
  pdfPageSizeMm.value ? { pageSizeOverride: pdfPageSizeMm.value } : undefined,
);
const guideImageUrlRaw = computed(
  () => props.previewMeta?.guideImageUrl ?? props.overlayPage?.overlay?.guideImageUrl,
);
const guideConfigured = computed(() => hasUserGuideImageUrl(guideImageUrlRaw.value));
const guideSrc = computed(() => {
  if (!overlayMode.value || !showGuide.value || guideLoadFailed.value || !guideConfigured.value) {
    return '';
  }
  return resolveGuideImageUrl(guideImageUrlRaw.value);
});
const showGuideHint = computed(
  () => overlayMode.value && showGuide.value && !guideConfigured.value,
);
const showGuideLoadError = computed(
  () => overlayMode.value && showGuide.value && guideConfigured.value && guideLoadFailed.value,
);
const showBoxGuideLayer = computed(
  () =>
    overlayMode.value &&
    props.showBoxGuide &&
    showBoxGuideLocal.value &&
    (props.overlayLayoutItems?.length ?? 0) > 0,
);
const showMmRulers = computed(() => overlayMode.value && props.overlayEditable);
const overlayPageMm = computed(() => pageSizeMm(props.overlayPage, overlayPageSizeOptions.value));
const selectedRulerHighlight = computed(() => {
  const code = props.selectedElementCode;
  if (!code) {
    return null;
  }
  const item = props.overlayLayoutItems?.find((i) => i.elementCode === code);
  if (!item) {
    return null;
  }
  const def = props.overlayElements?.find((e) => e.elementCode === code);
  const box = computeEffectiveBox(item, def, props.printOffsetMm);
  if (!box || !hasBoxGeometry(box)) {
    return null;
  }
  return {
    xStart: box.xMm ?? 0,
    xEnd: (box.xMm ?? 0) + (box.widthMm ?? 0),
    yStart: box.yMm ?? 0,
    yEnd: (box.yMm ?? 0) + (box.heightMm ?? 0),
  };
});

watch(
  () => props.pdfBlob,
  (blob) => {
    pdfPageSizeMm.value = null;
    if (!blob) {
      return;
    }
  },
  { immediate: true },
);

function onPdfPageSizeMm(size: { widthMm: number; heightMm: number }) {
  pdfPageSizeMm.value = size;
}

watch(
  () => [props.previewMeta?.guideImageUrl, props.overlayPage?.overlay?.guideImageUrl] as const,
  () => {
    guideLoadFailed.value = false;
  },
);

onBeforeUnmount(() => {
  pdfPageSizeMm.value = null;
});

function onGuideError() {
  guideLoadFailed.value = true;
}
</script>

<template>
  <div class="report-preview-panel" :class="{ 'is-fill': fillContainer }">
    <div v-if="overlayMode && showGuideToggle" class="report-preview-panel__toolbar">
      <ElCheckbox v-if="guideConfigured" v-model="showGuide">显示定位纸</ElCheckbox>
      <ElCheckbox v-if="overlayLayoutItems?.length" v-model="showBoxGuideLocal">
        显示落格框线
      </ElCheckbox>
      <span v-if="showGuideLoadError" class="report-preview-panel__warn"
        >定位纸加载失败，请重新上传</span
      >
    </div>

    <div
      class="report-preview-panel__stage-host"
      :class="{ 'is-loading': loading, 'has-mm-rulers': showMmRulers }"
    >
      <div
        class="report-preview-panel__stage-wrap"
        :class="{ 'has-mm-rulers': showMmRulers }"
        :style="{ '--stage-aspect': String(aspectRatio) }"
      >
        <template v-if="showMmRulers">
          <div class="report-preview-panel__ruler-corner" aria-hidden="true">
            <span class="report-preview-panel__ruler-unit">mm</span>
          </div>
          <OverlayMmRuler
            class="report-preview-panel__ruler-top"
            axis="horizontal"
            :length-mm="overlayPageMm.widthMm"
            :highlight-start="selectedRulerHighlight?.xStart"
            :highlight-end="selectedRulerHighlight?.xEnd"
          />
          <OverlayMmRuler
            class="report-preview-panel__ruler-left"
            axis="vertical"
            :length-mm="overlayPageMm.heightMm"
            :highlight-start="selectedRulerHighlight?.yStart"
            :highlight-end="selectedRulerHighlight?.yEnd"
          />
        </template>
        <div
          class="report-preview-panel__stage"
          :style="{
            aspectRatio: fillContainer ? undefined : String(aspectRatio),
          }"
        >
          <ReportPdfStageCanvas
            v-if="pdfBlob"
            :pdf-blob="pdfBlob"
            @page-size-mm="onPdfPageSizeMm"
          />
          <img
            v-if="guideSrc"
            class="report-preview-panel__guide"
            :src="guideSrc"
            alt=""
            @error="onGuideError"
          />
          <p v-if="showGuideHint" class="report-preview-panel__guide-hint">
            未上传定位纸，请在左侧「页面设置」上传预印纸扫描图（仅设计预览，不进正式 PDF）
          </p>
          <OverlayBoxGuide
            v-if="showBoxGuideLayer"
            :items="overlayLayoutItems"
            :elements="overlayElements"
            :page="overlayPage ?? undefined"
            :selected-element-code="selectedElementCode"
            :editable="overlayEditable"
            :print-offset-mm="printOffsetMm"
            :page-size-options="overlayPageSizeOptions"
            :sample-data="overlaySampleData"
            @select="(code) => emit('overlaySelect', code)"
            @box-change="(payload) => emit('overlayBoxChange', payload)"
          />
          <ElEmpty v-if="!pdfBlob && !showBoxGuideLayer" description="暂无预览" />
        </div>
      </div>
    </div>
    <p v-if="overlayMode" class="report-preview-panel__hint">
      <template v-if="overlayEditable">
        <template v-if="!pdfBlob">
          落格框可拖拽调整；点击顶栏「预览」弹出 PDF 浏览窗口，「试打」输出到打印机。
        </template>
        <template v-else>
          拖拽框线移动落格，选中项右下角圆点可改大小；定位纸与框线不进正式 PDF。
        </template>
      </template>
      <template v-else> 定位纸与框线仅预览叠图，正式 PDF 与打印不含底图与框线。 </template>
    </p>
  </div>
</template>

<style scoped>
.report-preview-panel.is-fill {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  min-width: 0;
}

.report-preview-panel__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  flex-shrink: 0;
}

.report-preview-panel.is-fill .report-preview-panel__toolbar {
  padding: 0 4px;
}

.report-preview-panel__warn {
  font-size: 12px;
  color: var(--el-color-warning);
}

.report-preview-panel__stage-host {
  position: relative;
  width: 100%;
}

.report-preview-panel.is-fill .report-preview-panel__stage-host {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  container-type: size;
}

.report-preview-panel__stage-host.is-loading {
  opacity: 0.55;
}

.report-preview-panel__stage-wrap {
  position: relative;
}

.report-preview-panel__stage-wrap.has-mm-rulers {
  --ruler-thickness: 24px;
  display: inline-grid;
  grid-template-columns: var(--ruler-thickness) minmax(0, auto);
  grid-template-rows: var(--ruler-thickness) minmax(0, auto);
  grid-template-areas:
    'corner ruler-top'
    'ruler-left stage';
}

.report-preview-panel__ruler-corner {
  grid-area: corner;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
  border-right: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.report-preview-panel__ruler-unit {
  font-size: 10px;
  line-height: 1;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.report-preview-panel__ruler-top {
  grid-area: ruler-top;
  height: var(--ruler-thickness);
  min-width: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.report-preview-panel__ruler-left {
  grid-area: ruler-left;
  width: var(--ruler-thickness);
  min-height: 0;
  border-right: 1px solid var(--el-border-color-lighter);
}

.report-preview-panel__stage {
  grid-area: stage;
  position: relative;
  width: 100%;
  max-width: 520px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  overflow: hidden;
}

.report-preview-panel.is-fill .report-preview-panel__stage-wrap.has-mm-rulers {
  max-width: 100%;
  max-height: 100%;
}

.report-preview-panel.is-fill
  .report-preview-panel__stage-wrap.has-mm-rulers
  .report-preview-panel__stage {
  width: min(
    calc(100cqw - var(--ruler-thickness)),
    calc((100cqh - var(--ruler-thickness)) * var(--stage-aspect, 0.707))
  );
  height: min(
    calc(100cqh - var(--ruler-thickness)),
    calc((100cqw - var(--ruler-thickness)) / var(--stage-aspect, 0.707))
  );
  max-width: 100%;
  max-height: 100%;
  margin: 0;
  aspect-ratio: unset;
}

.report-preview-panel.is-fill
  .report-preview-panel__stage-wrap:not(.has-mm-rulers)
  .report-preview-panel__stage {
  width: min(100cqw, calc(100cqh * var(--stage-aspect, 0.707)));
  height: min(100cqh, calc(100cqw / var(--stage-aspect, 0.707)));
  max-width: 100%;
  max-height: 100%;
  margin: 0;
  aspect-ratio: unset;
}

.report-preview-panel__stage-wrap:not(.has-mm-rulers) .report-preview-panel__stage {
  grid-area: unset;
}

.report-preview-panel__guide {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: fill;
  opacity: 0.42;
  pointer-events: none;
  z-index: 2;
  mix-blend-mode: multiply;
}

.report-preview-panel__guide-hint {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  text-align: center;
  pointer-events: none;
}

.report-preview-panel__hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
  flex-shrink: 0;
}

.report-preview-panel.is-fill .report-preview-panel__hint {
  padding: 0 4px;
}
</style>
