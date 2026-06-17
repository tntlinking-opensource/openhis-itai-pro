<script setup lang="ts">
import type { PreviewLayoutMetaDto } from '#/utils/report/report-preview';

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { CopyDocument, FullScreen, Minus } from '@element-plus/icons-vue';
import { ElButton, ElIcon } from 'element-plus';

import ReportPreviewPanel from '#/components/report/ReportPreviewPanel.vue';

defineOptions({ name: 'FloatingReportPreviewPanel' });

const props = withDefaults(
  defineProps<{
    pdfBlob?: Blob | null;
    previewMeta?: PreviewLayoutMetaDto | null;
    loading?: boolean;
  }>(),
  {
    pdfBlob: null,
    previewMeta: null,
    loading: false,
  },
);

type PanelState = 'normal' | 'minimized' | 'maximized';

const anchorRef = ref<HTMLElement | null>(null);
const panelState = ref<PanelState>('normal');
const position = ref({ x: 12, y: 12 });
const panelSize = ref({ width: 340, height: 440 });
const dragging = ref(false);
const resizing = ref(false);

const PANEL_HEADER_HEIGHT = 40;
const PANEL_MINIMIZED_WIDTH = 200;
const PANEL_MIN_WIDTH = 280;
const PANEL_MIN_HEIGHT = 320;
const PANEL_MAX_WIDTH = 960;
const PANEL_MAX_HEIGHT = 900;

const dragOffset = { x: 0, y: 0 };
const resizeOrigin = { x: 0, y: 0, width: 0, height: 0 };

const isMaximized = computed(() => panelState.value === 'maximized');
const isMinimized = computed(() => panelState.value === 'minimized');

const panelStyle = computed(() => {
  if (isMaximized.value) {
    return {
      left: '8px',
      top: '8px',
      right: '8px',
      bottom: '8px',
      width: 'auto',
      height: 'auto',
    };
  }
  if (isMinimized.value) {
    return {
      left: `${position.value.x}px`,
      top: `${position.value.y}px`,
      width: `${PANEL_MINIMIZED_WIDTH}px`,
      height: `${PANEL_HEADER_HEIGHT}px`,
    };
  }
  return {
    left: `${position.value.x}px`,
    top: `${position.value.y}px`,
    width: `${panelSize.value.width}px`,
    height: `${panelSize.value.height}px`,
  };
});

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function getPanelSize() {
  if (isMinimized.value) {
    return { width: PANEL_MINIMIZED_WIDTH, height: PANEL_HEADER_HEIGHT };
  }
  return { ...panelSize.value };
}

function getMaxPanelSize() {
  const anchor = anchorRef.value;
  if (!anchor) {
    return { width: PANEL_MAX_WIDTH, height: PANEL_MAX_HEIGHT };
  }
  return {
    width: Math.min(
      PANEL_MAX_WIDTH,
      Math.max(PANEL_MIN_WIDTH, anchor.clientWidth - position.value.x - 8),
    ),
    height: Math.min(
      PANEL_MAX_HEIGHT,
      Math.max(PANEL_MIN_HEIGHT, anchor.clientHeight - position.value.y - 8),
    ),
  };
}

function placeDefaultPosition() {
  const anchor = anchorRef.value;
  if (!anchor) {
    return;
  }
  const { width, height } = getPanelSize();
  position.value = {
    x: Math.max(8, anchor.clientWidth - width - 12),
    y: Math.max(8, anchor.clientHeight - height - 12),
  };
}

function clampPosition() {
  const anchor = anchorRef.value;
  if (!anchor || isMaximized.value) {
    return;
  }
  const { width, height } = getPanelSize();
  position.value = {
    x: clamp(0, position.value.x, Math.max(0, anchor.clientWidth - width)),
    y: clamp(0, position.value.y, Math.max(0, anchor.clientHeight - height)),
  };
}

function clampPanelSize() {
  const max = getMaxPanelSize();
  panelSize.value = {
    width: clamp(PANEL_MIN_WIDTH, panelSize.value.width, max.width),
    height: clamp(PANEL_MIN_HEIGHT, panelSize.value.height, max.height),
  };
  clampPosition();
}

function onHeaderMouseDown(event: MouseEvent) {
  if (isMaximized.value) {
    return;
  }
  if ((event.target as HTMLElement).closest('button')) {
    return;
  }
  const anchor = anchorRef.value;
  if (!anchor) {
    return;
  }
  dragging.value = true;
  const rect = anchor.getBoundingClientRect();
  dragOffset.x = event.clientX - rect.left - position.value.x;
  dragOffset.y = event.clientY - rect.top - position.value.y;
  document.addEventListener('mousemove', onDocumentMouseMove);
  document.addEventListener('mouseup', onDocumentMouseUp);
  event.preventDefault();
}

function onResizeMouseDown(event: MouseEvent) {
  if (isMaximized.value || isMinimized.value) {
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  resizing.value = true;
  resizeOrigin.x = event.clientX;
  resizeOrigin.y = event.clientY;
  resizeOrigin.width = panelSize.value.width;
  resizeOrigin.height = panelSize.value.height;
  document.addEventListener('mousemove', onDocumentMouseMove);
  document.addEventListener('mouseup', onDocumentMouseUp);
}

function onDocumentMouseMove(event: MouseEvent) {
  if (resizing.value) {
    const max = getMaxPanelSize();
    panelSize.value = {
      width: clamp(
        PANEL_MIN_WIDTH,
        resizeOrigin.width + (event.clientX - resizeOrigin.x),
        max.width,
      ),
      height: clamp(
        PANEL_MIN_HEIGHT,
        resizeOrigin.height + (event.clientY - resizeOrigin.y),
        max.height,
      ),
    };
    clampPosition();
    return;
  }
  if (!dragging.value) {
    return;
  }
  const anchor = anchorRef.value;
  if (!anchor) {
    return;
  }
  const rect = anchor.getBoundingClientRect();
  const { width, height } = getPanelSize();
  position.value = {
    x: clamp(0, event.clientX - rect.left - dragOffset.x, Math.max(0, rect.width - width)),
    y: clamp(0, event.clientY - rect.top - dragOffset.y, Math.max(0, rect.height - height)),
  };
}

function onDocumentMouseUp() {
  dragging.value = false;
  resizing.value = false;
  document.removeEventListener('mousemove', onDocumentMouseMove);
  document.removeEventListener('mouseup', onDocumentMouseUp);
}

function toggleMinimize() {
  if (isMinimized.value) {
    panelState.value = 'normal';
    clampPanelSize();
    return;
  }
  clampPosition();
  panelState.value = 'minimized';
}

function toggleMaximize() {
  if (isMaximized.value) {
    panelState.value = 'normal';
    clampPanelSize();
    return;
  }
  clampPosition();
  panelState.value = 'maximized';
}

function onAnchorResize() {
  if (panelState.value === 'normal') {
    clampPanelSize();
    return;
  }
  if (panelState.value === 'minimized') {
    clampPosition();
  }
}

let resizeObserver: ResizeObserver | undefined;

onMounted(() => {
  placeDefaultPosition();
  if (anchorRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(onAnchorResize);
    resizeObserver.observe(anchorRef.value);
  }
});

onBeforeUnmount(() => {
  onDocumentMouseUp();
  resizeObserver?.disconnect();
});

watch(
  () => props.pdfBlob,
  () => {
    if (isMinimized.value) {
      panelState.value = 'normal';
      clampPanelSize();
    }
  },
);
</script>

<template>
  <div ref="anchorRef" class="floating-report-preview">
    <div
      class="floating-report-preview__panel"
      :class="{
        'is-maximized': isMaximized,
        'is-minimized': isMinimized,
        'is-dragging': dragging,
        'is-resizing': resizing,
      }"
      :style="panelStyle"
    >
      <header
        class="floating-report-preview__header"
        :class="{ 'is-draggable': !isMaximized }"
        @mousedown="onHeaderMouseDown"
      >
        <span class="floating-report-preview__title">PDF 预览</span>
        <span v-if="loading" class="floating-report-preview__status">生成中…</span>
        <div class="floating-report-preview__actions">
          <ElButton
            link
            type="primary"
            :title="isMinimized ? '展开' : '缩小'"
            @click="toggleMinimize"
          >
            <ElIcon><Minus /></ElIcon>
          </ElButton>
          <ElButton
            link
            type="primary"
            :title="isMaximized ? '还原' : '最大化'"
            @click="toggleMaximize"
          >
            <ElIcon>
              <CopyDocument v-if="isMaximized" />
              <FullScreen v-else />
            </ElIcon>
          </ElButton>
        </div>
      </header>
      <div v-show="!isMinimized" class="floating-report-preview__body">
        <ReportPreviewPanel
          :pdf-blob="pdfBlob"
          :preview-meta="previewMeta"
          :loading="loading"
          :show-guide-toggle="false"
        />
      </div>
      <div
        v-if="!isMaximized && !isMinimized"
        class="floating-report-preview__resize-handle"
        title="拖动调整大小"
        @mousedown="onResizeMouseDown"
      />
    </div>
  </div>
</template>

<style scoped>
.floating-report-preview {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 20;
}

.floating-report-preview__panel {
  position: absolute;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  pointer-events: auto;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgb(0 0 0 / 12%);
}

.floating-report-preview__panel.is-dragging,
.floating-report-preview__panel.is-resizing {
  user-select: none;
  opacity: 0.96;
}

.floating-report-preview__panel.is-maximized {
  border-radius: 6px;
}

.floating-report-preview__header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 8px 0 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}

.floating-report-preview__header.is-draggable {
  cursor: move;
}

.floating-report-preview__title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
}

.floating-report-preview__status {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.floating-report-preview__actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 2px;
}

.floating-report-preview__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px;
}

.floating-report-preview__body :deep(.report-preview-panel__stage-host) {
  flex: 1;
  min-height: 0;
}

.floating-report-preview__body :deep(.report-preview-panel__stage) {
  width: 100%;
  height: 100%;
  max-width: none;
  min-height: 240px;
  aspect-ratio: unset;
}

.floating-report-preview__panel.is-minimized .floating-report-preview__header {
  border-bottom: none;
}

.floating-report-preview__resize-handle {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 16px;
  height: 16px;
  cursor: nwse-resize;
  background: linear-gradient(
    135deg,
    transparent 0 45%,
    var(--el-border-color) 45% 50%,
    transparent 50% 60%,
    var(--el-border-color) 60% 65%,
    transparent 65%
  );
}

.floating-report-preview__resize-handle:hover {
  background: linear-gradient(
    135deg,
    transparent 0 45%,
    var(--el-color-primary) 45% 50%,
    transparent 50% 60%,
    var(--el-color-primary) 60% 65%,
    transparent 65%
  );
}
</style>
