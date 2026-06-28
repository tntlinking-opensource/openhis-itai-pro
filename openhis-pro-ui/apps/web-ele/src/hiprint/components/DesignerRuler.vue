<script lang="ts" setup>
import type { HiprintTemplateInstance } from '../core/types';

import { nextTick, onBeforeUnmount, onMounted, watch } from 'vue';

import { $ } from '../core/bootstrap';
import Ruler from '../lib/ruler';
import { DEFAULT_RULER_STYLE } from '../lib/ruler-types';

defineOptions({ name: 'DesignerRuler' });

const props = withDefaults(defineProps<Props>(), {
  scrollId: 'paperBox',
  xHostId: 'global-ruler-x-host',
  yHostId: 'global-ruler-y-host',
  scale: 1,
  getTemplate: undefined,
});

interface Props {
  scrollId?: string;
  xHostId?: string;
  yHostId?: string;
  scale?: number;
  getTemplate?: () => HiprintTemplateInstance | null | undefined;
}

function getDesignerCssVar(name: string, fallback: string): string {
  const root = document.querySelector('.hiprint-designer-root');
  if (!root) {
    return fallback;
  }
  const value = getComputedStyle(root).getPropertyValue(name).trim();
  return value || fallback;
}

interface SelectedRect {
  height: number;
  id: string;
  left: number;
  top: number;
  width: number;
}

interface HiprintSelectElement {
  id: string;
  options: {
    getRectInfo?: () => { h: number; w: number };
    height: number;
    posLeft?: () => string;
    posTop?: () => string;
    width: number;
  };
}

type HinnnWindow = Window & {
  hinnn?: {
    event: {
      off: (key: string, handler: () => void) => void;
      on: (key: string, handler: () => void) => void;
    };
    pt?: { toPx: (value: string) => number };
  };
};

const rulerId = DEFAULT_RULER_STYLE.rulerId;
const rulerSize = DEFAULT_RULER_STYLE.size;

let scrollLeft = 0;
let scrollTop = 0;
let scrollEl: HTMLElement | null = null;
let xTrackEl: HTMLElement | null = null;
let yTrackEl: HTMLElement | null = null;
let xCanvasEl: HTMLCanvasElement | null = null;
let yCanvasEl: HTMLCanvasElement | null = null;
let xLineEl: HTMLElement | null = null;
let yLineEl: HTMLElement | null = null;
let guideXEl: HTMLElement | null = null;
let guideYEl: HTMLElement | null = null;
let guideXLabelEl: HTMLElement | null = null;
let guideYLabelEl: HTMLElement | null = null;
let xRectBoxEl: HTMLElement | null = null;
let yRectBoxEl: HTMLElement | null = null;
let rulerInstance: null | Ruler = null;
let styleSnapshot = { ...DEFAULT_RULER_STYLE };
let resizeObserver: null | ResizeObserver = null;
let resizeTimer: null | ReturnType<typeof setTimeout> = null;
let updating = false;
let boundTemplateId: null | string = null;
const templateHandlers = {
  onSelect: () => syncSelectedRects(),
  onUpdate: () => {
    void updateRuler(props.scale);
  },
};

/** 与 sv-print ruler.svelte 一致的 padding / 内容尺寸 */
let padLeft = 24;
let padRight = 24;
let padTop = 40;
let padBottom = 40;
let contentWidth = 0;
let contentHeight = 0;

function getHostEl(id: string): HTMLElement | null {
  return document.querySelector(`#${CSS.escape(id)}`);
}

function toPtPx(value: number | string): number {
  if (typeof value === 'number') {
    return value;
  }
  const hinnn = (window as HinnnWindow).hinnn;
  if (hinnn?.pt?.toPx) {
    return hinnn.pt.toPx(String(value));
  }
  return Number.parseFloat(String(value)) || 0;
}

function bindTrackElements(): void {
  const xHost = getHostEl(props.xHostId);
  const yHost = getHostEl(props.yHostId);
  xTrackEl = xHost?.querySelector('.global-ruler-x-track') as HTMLElement | null;
  yTrackEl = yHost?.querySelector('.global-ruler-y-track') as HTMLElement | null;
  xCanvasEl = xTrackEl?.querySelector('canvas') as HTMLCanvasElement | null;
  yCanvasEl = yTrackEl?.querySelector('canvas') as HTMLCanvasElement | null;
  xLineEl = getHostEl('global-ruler-x-line');
  yLineEl = getHostEl('global-ruler-y-line');
  guideXEl = getHostEl('designer-guide-x');
  guideYEl = getHostEl('designer-guide-y');
  guideXLabelEl = getHostEl('designer-guide-x-label');
  guideYLabelEl = getHostEl('designer-guide-y-label');
  xRectBoxEl = getHostEl('global-ruler-x-rect-box');
  yRectBoxEl = getHostEl('global-ruler-y-rect-box');
}

function hideRulerGuides(): void {
  for (const el of [xLineEl, yLineEl, guideXEl, guideYEl, guideXLabelEl, guideYLabelEl]) {
    if (el) {
      el.style.display = 'none';
    }
  }
}

function showGuideX(offsetX: number): void {
  const visibleX = offsetX - scrollLeft;
  const designerX = rulerSize + visibleX;
  const mm = Math.round((offsetX + styleSnapshot.startX) / styleSnapshot.scale / styleSnapshot.gap);

  if (xLineEl) {
    xLineEl.style.left = `${offsetX}px`;
    xLineEl.style.display = 'block';
    const label = xLineEl.querySelector('.global-ruler-x-line-label');
    if (label) {
      label.textContent = `${mm}`;
    }
  }
  if (guideXEl) {
    guideXEl.style.left = `${designerX}px`;
    guideXEl.style.display = 'block';
  }
  if (guideXLabelEl) {
    guideXLabelEl.textContent = `${mm}`;
    guideXLabelEl.style.left = `${designerX}px`;
    guideXLabelEl.style.display = 'block';
  }
}

function showGuideY(offsetY: number): void {
  const visibleY = offsetY - scrollTop;
  const designerY = rulerSize + visibleY;
  const mm = Math.round((offsetY + styleSnapshot.startY) / styleSnapshot.scale / styleSnapshot.gap);

  if (yLineEl) {
    yLineEl.style.top = `${offsetY}px`;
    yLineEl.style.display = 'block';
    const label = yLineEl.querySelector('.global-ruler-y-line-label');
    if (label) {
      label.textContent = `${mm}`;
    }
  }
  if (guideYEl) {
    guideYEl.style.top = `${designerY}px`;
    guideYEl.style.display = 'block';
  }
  if (guideYLabelEl) {
    guideYLabelEl.textContent = `${mm}`;
    guideYLabelEl.style.top = `${designerY}px`;
    guideYLabelEl.style.display = 'block';
  }
}

/** sv-print onScrollXBoxMove / onScrollYBoxMove */
function onRulerXMove(event: MouseEvent): void {
  showGuideX(event.offsetX);
}

function onRulerYMove(event: MouseEvent): void {
  showGuideY(event.offsetY);
}

function bindRulerPointerEvents(): void {
  xCanvasEl?.removeEventListener('mousemove', onRulerXMove);
  yCanvasEl?.removeEventListener('mousemove', onRulerYMove);
  xTrackEl?.removeEventListener('mouseleave', hideRulerGuides);
  yTrackEl?.removeEventListener('mouseleave', hideRulerGuides);
  xCanvasEl?.addEventListener('mousemove', onRulerXMove);
  yCanvasEl?.addEventListener('mousemove', onRulerYMove);
  xTrackEl?.addEventListener('mouseleave', hideRulerGuides);
  yTrackEl?.addEventListener('mouseleave', hideRulerGuides);
}

function renderRulerRects(rects: SelectedRect[]): void {
  if (!xRectBoxEl || !yRectBoxEl) {
    return;
  }
  xRectBoxEl.innerHTML = rects
    .map(
      (rect) =>
        `<div class="global-ruler-x-rect" data-rid="${rect.id}" style="left:${rect.left}px;width:${rect.width}px"></div>`,
    )
    .join('');
  yRectBoxEl.innerHTML = rects
    .map(
      (rect) =>
        `<div class="global-ruler-y-rect" data-rid="${rect.id}" style="top:${rect.top}px;height:${rect.height}px"></div>`,
    )
    .join('');
}

/** sv-print S：同步选中元素在标尺上的高亮区 */
function syncSelectedRects(scale = props.scale): void {
  const tpl = props.getTemplate?.();
  const selected = (tpl?.editingPanel?.getSelectEls?.() ?? []) as HiprintSelectElement[];
  if (selected.length === 0) {
    renderRulerRects([]);
    return;
  }

  const rects: SelectedRect[] = selected.map((el) => {
    const rectInfo = el.options.getRectInfo?.() ?? { w: el.options.width, h: el.options.height };
    const widthPt = rectInfo.w || el.options.width;
    const heightPt = rectInfo.h || el.options.height;
    return {
      id: el.id,
      left: toPtPx(el.options.posLeft?.() ?? 0) * scale + padLeft,
      top: toPtPx(el.options.posTop?.() ?? 0) * scale + padTop,
      width: toPtPx(widthPt) * scale,
      height: toPtPx(heightPt) * scale,
    };
  });
  renderRulerRects(rects);
}

function onDesignerMouseMove(): void {
  syncSelectedRects(props.scale);
}

function unbindTemplateEvents(): void {
  if (!boundTemplateId) {
    return;
  }
  const hinnn = (window as HinnnWindow).hinnn;
  if (!hinnn?.event) {
    boundTemplateId = null;
    return;
  }
  const id = boundTemplateId;
  hinnn.event.off(`BuildCustomOptionSettingEventKey_${id}`, templateHandlers.onSelect);
  hinnn.event.off(`PrintElementSelectEventKey_${id}`, templateHandlers.onSelect);
  hinnn.event.off(`hiprintTemplateMouseUp_${id}`, templateHandlers.onSelect);
  hinnn.event.off(`hiprintTemplateUpdate_${id}`, templateHandlers.onUpdate);
  boundTemplateId = null;
}

function bindTemplateEvents(): void {
  const tpl = props.getTemplate?.() as (HiprintTemplateInstance & { id?: string }) | null;
  const templateId = tpl?.id;
  if (!templateId || boundTemplateId === templateId) {
    if (templateId) {
      syncSelectedRects(props.scale);
    }
    return;
  }
  unbindTemplateEvents();
  const hinnn = (window as HinnnWindow).hinnn;
  if (!hinnn?.event) {
    return;
  }
  boundTemplateId = templateId;
  hinnn.event.on(`BuildCustomOptionSettingEventKey_${templateId}`, templateHandlers.onSelect);
  hinnn.event.on(`PrintElementSelectEventKey_${templateId}`, templateHandlers.onSelect);
  hinnn.event.on(`hiprintTemplateMouseUp_${templateId}`, templateHandlers.onSelect);
  hinnn.event.on(`hiprintTemplateUpdate_${templateId}`, templateHandlers.onUpdate);
  syncSelectedRects(props.scale);
}

function hasCanvas(): boolean {
  return Boolean(getHostEl(`ruler-x-${rulerId}`) && getHostEl(`ruler-y-${rulerId}`));
}

function syncTrackTransform(): void {
  if (xTrackEl) {
    xTrackEl.style.transform = `translateX(${-scrollLeft}px)`;
  }
  if (yTrackEl) {
    yTrackEl.style.transform = `translateY(${-scrollTop}px)`;
  }
}

function onScroll(event: Event): void {
  const target = event.target as HTMLElement;
  scrollLeft = target.scrollLeft;
  scrollTop = target.scrollTop;
  syncTrackTransform();
  if (rulerInstance?.isReady()) {
    rulerInstance.scrollLeft = scrollLeft;
    rulerInstance.scrollTop = scrollTop;
    rulerInstance.drawCanvas();
  }
}

/** sv-print J：测量纸张 mm 并同步 inner 宽高 */
function measureLayout(scale = 1): boolean {
  scrollEl = getHostEl(props.scrollId);
  if (!scrollEl) {
    return false;
  }

  const inner = scrollEl.querySelector('.printTemplate') as HTMLElement | null;
  if (!inner) {
    return false;
  }

  padLeft = Math.max(Number.parseFloat(scrollEl.style.paddingLeft) || 0, rulerSize);
  padRight = Math.max(Number.parseFloat(scrollEl.style.paddingRight) || 0, rulerSize);
  padTop = Number.parseFloat(scrollEl.style.paddingTop) || 40;
  padBottom = Number.parseFloat(scrollEl.style.paddingBottom) || 40;

  contentWidth = inner.scrollWidth * scale + padLeft + padRight;

  const $paper = $(scrollEl).find('.hiprint-printPaper');
  if ($paper.length <= 0) {
    return false;
  }

  const mmW = Number.parseFloat(($paper[0] as HTMLElement).style.width);
  const mmH = Number.parseFloat(($paper[0] as HTMLElement).style.height);
  if (!Number.isFinite(mmW) || !Number.isFinite(mmH)) {
    return false;
  }

  $(inner).css({ width: `${mmW * scale}mm`, height: `${mmH * scale}mm` });
  const nested = inner.firstElementChild as HTMLElement | null;
  if (nested) {
    $(nested).css({ width: `${mmW * scale}mm`, height: `${mmH * scale}mm` });
  }

  centerPaper(scale);
  return true;
}

/** sv-print I：水平居中纸张（padding） */
function centerPaper(scale = 1): void {
  if (!scrollEl) {
    return;
  }
  const inner = scrollEl.querySelector('.printTemplate') as HTMLElement | null;
  if (!inner) {
    return;
  }

  const clientW = scrollEl.clientWidth;
  const paperPxW = inner.clientWidth;
  const paperPxH = inner.clientHeight;

  if (paperPxW > 0 && clientW > paperPxW + rulerSize) {
    padLeft = padRight = Math.max((clientW - rulerSize - paperPxW) / 2, rulerSize);
    contentWidth = paperPxW * scale + padLeft + padRight + rulerSize;
    $(scrollEl).css({
      padding: `${padTop}px ${padRight}px ${padBottom}px ${padLeft}px`,
    });
  }

  if (paperPxH > 0) {
    contentHeight = paperPxH * scale + padTop + padBottom;
    $(scrollEl).css({
      padding: `${padTop}px ${padRight}px ${padBottom}px ${padLeft}px`,
    });
  }
}

function ensureRulerInstance(): null | Ruler {
  if (!hasCanvas()) {
    return null;
  }
  if (!rulerInstance) {
    rulerInstance = new Ruler(styleSnapshot);
  }
  return rulerInstance.isReady() ? rulerInstance : null;
}

/** sv-print Q：startX/startY 取负 padding，paperRect 高亮纸张区 */
function applyRulerStyle(scale = 1): void {
  scrollEl = getHostEl(props.scrollId);
  const inner = scrollEl?.querySelector('.printTemplate') as HTMLElement | null;
  if (!scrollEl || !inner) {
    return;
  }

  const paperPxW = inner.clientWidth;
  const paperPxH = inner.clientHeight;
  const rulerW = Math.max(contentWidth, scrollEl.scrollWidth, paperPxW + padLeft + padRight);
  const rulerH = Math.max(contentHeight, scrollEl.scrollHeight, paperPxH + padTop + padBottom);

  styleSnapshot = {
    ...DEFAULT_RULER_STYLE,
    width: rulerW,
    height: rulerH,
    startX: -padLeft,
    startY: -padTop,
    scale,
    paperRect: true,
    paperRectColor: getDesignerCssVar('--sv-workspace-bg', '#f5f5f5'),
    paperWidth: paperPxW,
    paperHeight: paperPxH,
    gap: DEFAULT_RULER_STYLE.gap * scale,
  };

  const ruler = ensureRulerInstance();
  if (!ruler) {
    return;
  }
  ruler.resize(styleSnapshot);
  syncTrackTransform();
}

/** 刷新标尺（对外暴露） */
async function updateRuler(scale = props.scale): Promise<void> {
  if (updating) {
    return;
  }
  updating = true;
  try {
    bindTrackElements();
    if (!measureLayout(scale)) {
      return;
    }
    void scrollEl?.offsetHeight;
    applyRulerStyle(scale);
    bindRulerPointerEvents();
    bindTemplateEvents();
    syncSelectedRects(scale);
  } finally {
    updating = false;
  }
}

function scheduleUpdateRuler(scale = props.scale): void {
  if (resizeTimer) {
    clearTimeout(resizeTimer);
  }
  resizeTimer = setTimeout(() => {
    void updateRuler(scale);
  }, 80);
}

function initEvent(): void {
  scrollEl = getHostEl(props.scrollId);
  if (!scrollEl) {
    return;
  }
  bindTrackElements();
  scrollLeft = scrollEl.scrollLeft;
  scrollTop = scrollEl.scrollTop;
  scrollEl.addEventListener('scroll', onScroll, { passive: true });
  document
    .querySelector('.hiprint-designer-root')
    ?.removeEventListener('mousemove', onDesignerMouseMove);
  document
    .querySelector('.hiprint-designer-root')
    ?.addEventListener('mousemove', onDesignerMouseMove, {
      passive: true,
    });
  syncTrackTransform();
}

function observeHosts(): void {
  resizeObserver?.disconnect();
  resizeObserver = new ResizeObserver(() => {
    scheduleUpdateRuler(props.scale);
  });
  const xHost = getHostEl(props.xHostId);
  const yHost = getHostEl(props.yHostId);
  if (xHost) {
    resizeObserver.observe(xHost);
  }
  if (yHost) {
    resizeObserver.observe(yHost);
  }
  if (scrollEl) {
    resizeObserver.observe(scrollEl);
  }
}

onMounted(async () => {
  await nextTick();
  initEvent();
  observeHosts();
});

onBeforeUnmount(() => {
  scrollEl?.removeEventListener('scroll', onScroll);
  xCanvasEl?.removeEventListener('mousemove', onRulerXMove);
  yCanvasEl?.removeEventListener('mousemove', onRulerYMove);
  xTrackEl?.removeEventListener('mouseleave', hideRulerGuides);
  yTrackEl?.removeEventListener('mouseleave', hideRulerGuides);
  document
    .querySelector('.hiprint-designer-root')
    ?.removeEventListener('mousemove', onDesignerMouseMove);
  unbindTemplateEvents();
  resizeObserver?.disconnect();
  if (resizeTimer) {
    clearTimeout(resizeTimer);
  }
  rulerInstance?.destroy();
  rulerInstance = null;
});

watch(
  () => props.scale,
  (scale) => {
    void updateRuler(scale);
  },
);

defineExpose({ updateRuler, initEvent });
</script>

<template>
  <span class="sr-only" aria-hidden="true"></span>
</template>
