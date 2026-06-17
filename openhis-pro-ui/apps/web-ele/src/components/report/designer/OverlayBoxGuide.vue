<script setup lang="ts">
import type { PrintElementDefDto } from '#/api/report/report-design';
import type { LayoutBox, LayoutItem, LayoutPage } from '#/types/report-layout';

import { computed, onBeforeUnmount, ref } from 'vue';

import {
  clampEffectiveBoxToPage,
  clientPointToPageMm,
  computeEffectiveBox,
  hasBoxGeometry,
  overlayBoxTextMetrics,
  pageSizeMm,
  persistBoxFromEffective,
  type PageSizeOptions,
} from '#/utils/report/overlay-layout';
import { snapBoxMove, snapBoxResize, type SnapGuideLine } from '#/utils/report/overlay-box-snap';
import { formatOverlayFieldPreviewText } from '#/utils/report/bind-path';

defineOptions({ name: 'OverlayBoxGuide' });

const props = withDefaults(
  defineProps<{
    items?: LayoutItem[];
    elements?: PrintElementDefDto[];
    page?: LayoutPage | null;
    selectedElementCode?: string | null;
    editable?: boolean;
    printOffsetMm?: number[];
    pageSizeOptions?: PageSizeOptions;
    /** 元素包样例数据，用于落格框内预览 bindPath 值 */
    sampleData?: Record<string, unknown> | null;
  }>(),
  {
    items: () => [],
    elements: () => [],
    page: null,
    selectedElementCode: null,
    editable: false,
    printOffsetMm: undefined,
    pageSizeOptions: undefined,
    sampleData: null,
  },
);

const emit = defineEmits<{
  select: [elementCode: string];
  boxChange: [payload: { elementCode: string; box: LayoutBox }];
}>();

const svgRef = ref<SVGSVGElement | null>(null);

type DragMode = 'move' | 'resize-se';

interface DragState {
  mode: DragMode;
  elementCode: string;
  startBox: LayoutBox;
  /** 按下点相对框左上角的偏移，避免拖拽跳动 */
  grabOffsetMm: { xMm: number; yMm: number };
  pointerId: number;
}

const dragState = ref<DragState | null>(null);
const draftEffective = ref<LayoutBox | null>(null);
const snapGuides = ref<SnapGuideLine[]>([]);

const pageMm = computed(() => pageSizeMm(props.page, props.pageSizeOptions));

const elementMap = computed(() => {
  const map = new Map<string, PrintElementDefDto>();
  for (const el of props.elements ?? []) {
    if (el.elementCode) {
      map.set(el.elementCode, el);
    }
  }
  return map;
});

const boxes = computed(() => {
  const offset = props.printOffsetMm ?? props.page?.overlay?.printOffsetMm;
  const out: Array<{
    code: string;
    label: string;
    previewText: string;
    renderType: string;
    effective: LayoutBox;
    selected: boolean;
    inherited: boolean;
    textMetrics: ReturnType<typeof overlayBoxTextMetrics>;
  }> = [];
  for (const item of props.items ?? []) {
    if (item.visible === false) {
      continue;
    }
    const def = elementMap.value.get(item.elementCode);
    const dragging = dragState.value?.elementCode === item.elementCode && draftEffective.value;
    const effective = dragging ? draftEffective.value! : computeEffectiveBox(item, def, offset);
    if (!effective || !hasBoxGeometry(effective)) {
      continue;
    }
    const previewText = formatOverlayFieldPreviewText({
      label: def?.label,
      labelOverride: item.labelOverride,
      defaultLabel: def?.defaultLabel,
      bindPath: def?.bindPath,
      renderType: def?.renderType,
      sampleRoot: props.sampleData,
    });
    out.push({
      code: item.elementCode,
      label: def?.label ?? item.elementCode,
      previewText,
      renderType: def?.renderType ?? 'block',
      effective,
      selected: item.elementCode === props.selectedElementCode,
      inherited: !hasBoxGeometry(item.box),
      textMetrics: overlayBoxTextMetrics(effective),
    });
  }
  return out;
});

function itemOf(code: string) {
  return props.items?.find((i) => i.elementCode === code);
}

function effectiveOf(code: string): LayoutBox | null {
  const row = boxes.value.find((b) => b.code === code);
  return row?.effective ?? null;
}

function otherBoxesForSnap(excludeCode: string): LayoutBox[] {
  const offset = props.printOffsetMm ?? props.page?.overlay?.printOffsetMm;
  const out: LayoutBox[] = [];
  for (const item of props.items ?? []) {
    if (item.visible === false || item.elementCode === excludeCode) {
      continue;
    }
    const def = elementMap.value.get(item.elementCode);
    const effective = computeEffectiveBox(item, def, offset);
    if (effective && hasBoxGeometry(effective)) {
      out.push(effective);
    }
  }
  return out;
}

function beginDrag(event: PointerEvent, code: string, mode: DragMode, captureTarget: Element) {
  emit('select', code);
  const effective = effectiveOf(code);
  if (!effective || !svgRef.value) {
    return;
  }
  const pointerMm = clientPointToPageMm(
    svgRef.value,
    event.clientX,
    event.clientY,
    props.page,
    props.pageSizeOptions,
    false,
  );
  dragState.value = {
    mode,
    elementCode: code,
    startBox: { ...effective },
    grabOffsetMm:
      mode === 'move'
        ? {
            xMm: pointerMm.xMm - (effective.xMm ?? 0),
            yMm: pointerMm.yMm - (effective.yMm ?? 0),
          }
        : {
            xMm: pointerMm.xMm - ((effective.xMm ?? 0) + (effective.widthMm ?? 1)),
            yMm: pointerMm.yMm - ((effective.yMm ?? 0) + (effective.heightMm ?? 1)),
          },
    pointerId: event.pointerId,
  };
  draftEffective.value = { ...effective };
  snapGuides.value = [];
  captureTarget.setPointerCapture(event.pointerId);
  bindPointerListeners();
}

function onBoxPointerDown(event: PointerEvent, code: string) {
  if (!props.editable) {
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  beginDrag(event, code, 'move', event.currentTarget as Element);
}

function onResizePointerDown(event: PointerEvent, code: string) {
  if (!props.editable) {
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  beginDrag(event, code, 'resize-se', event.currentTarget as Element);
}

function onPointerMove(event: PointerEvent) {
  const drag = dragState.value;
  if (!drag || !svgRef.value) {
    return;
  }
  const currentMm = clientPointToPageMm(
    svgRef.value,
    event.clientX,
    event.clientY,
    props.page,
    props.pageSizeOptions,
    false,
  );
  const { widthMm: pageW, heightMm: pageH } = pageSizeMm(props.page, props.pageSizeOptions);
  const others = otherBoxesForSnap(drag.elementCode);
  const start = drag.startBox;
  let next: LayoutBox;

  if (drag.mode === 'move') {
    const raw: LayoutBox = {
      ...start,
      xMm: currentMm.xMm - drag.grabOffsetMm.xMm,
      yMm: currentMm.yMm - drag.grabOffsetMm.yMm,
    };
    const snapped = snapBoxMove(raw, pageW, pageH, others);
    next = snapped.box;
    snapGuides.value = snapped.guides;
  } else {
    const seX = currentMm.xMm - drag.grabOffsetMm.xMm;
    const seY = currentMm.yMm - drag.grabOffsetMm.yMm;
    const raw: LayoutBox = {
      ...start,
      widthMm: Math.max(1, seX - (start.xMm ?? 0)),
      heightMm: Math.max(1, seY - (start.yMm ?? 0)),
    };
    const snapped = snapBoxResize(raw, pageW, pageH, others);
    next = snapped.box;
    snapGuides.value = snapped.guides;
  }

  draftEffective.value = clampEffectiveBoxToPage(next, props.page, props.pageSizeOptions);
}

function onPointerUp(event: PointerEvent) {
  const drag = dragState.value;
  if (drag && svgRef.value) {
    try {
      svgRef.value.releasePointerCapture(drag.pointerId);
    } catch {
      /* ignore */
    }
  }
  if (!drag || !draftEffective.value) {
    cleanupDrag();
    return;
  }
  const item = itemOf(drag.elementCode);
  const def = elementMap.value.get(drag.elementCode);
  if (item) {
    const box = persistBoxFromEffective(
      draftEffective.value,
      item,
      def,
      props.page,
      props.printOffsetMm ?? props.page?.overlay?.printOffsetMm,
      props.pageSizeOptions,
    );
    emit('boxChange', { elementCode: drag.elementCode, box });
  }
  cleanupDrag();
}

function cleanupDrag() {
  dragState.value = null;
  draftEffective.value = null;
  snapGuides.value = [];
  unbindPointerListeners();
}

function bindPointerListeners() {
  window.addEventListener('pointermove', onPointerMove);
  window.addEventListener('pointerup', onPointerUp);
  window.addEventListener('pointercancel', onPointerUp);
}

function unbindPointerListeners() {
  window.removeEventListener('pointermove', onPointerMove);
  window.removeEventListener('pointerup', onPointerUp);
  window.removeEventListener('pointercancel', onPointerUp);
}

onBeforeUnmount(() => {
  unbindPointerListeners();
});
</script>

<template>
  <svg
    ref="svgRef"
    class="overlay-box-guide"
    :class="{ 'is-editable': editable, 'is-dragging': !!dragState }"
    :viewBox="`0 0 ${pageMm.widthMm} ${pageMm.heightMm}`"
    preserveAspectRatio="none"
    aria-hidden="true"
  >
    <rect
      :width="pageMm.widthMm"
      :height="pageMm.heightMm"
      fill="none"
      stroke="var(--el-color-primary)"
      stroke-width="0.3"
      stroke-dasharray="2 1"
      opacity="0.35"
      pointer-events="none"
    />
    <g v-if="snapGuides.length" class="overlay-box-guide__snap-lines">
      <line
        v-for="(guide, idx) in snapGuides"
        :key="`${guide.orientation}-${guide.positionMm}-${idx}`"
        :x1="guide.orientation === 'v' ? guide.positionMm : 0"
        :y1="guide.orientation === 'h' ? guide.positionMm : 0"
        :x2="guide.orientation === 'v' ? guide.positionMm : pageMm.widthMm"
        :y2="guide.orientation === 'h' ? guide.positionMm : pageMm.heightMm"
        stroke="var(--el-color-danger)"
        stroke-width="0.25"
        stroke-dasharray="1.2 0.8"
        pointer-events="none"
      />
    </g>
    <g v-for="row in boxes" :key="row.code">
      <rect
        class="overlay-box-guide__hit"
        :class="{ 'is-selected': row.selected, 'is-qrcode': row.renderType === 'qrcode' }"
        :x="row.effective.xMm"
        :y="row.effective.yMm"
        :width="row.effective.widthMm"
        :height="row.effective.heightMm"
        :fill="row.renderType === 'qrcode' ? '#fff' : 'rgba(0,0,0,0.001)'"
        :stroke="row.selected ? 'var(--el-color-danger)' : 'var(--el-color-primary)'"
        :stroke-width="row.selected ? 0.45 : 0.3"
        :stroke-dasharray="row.inherited ? '1.2 0.6' : undefined"
        :opacity="row.selected ? 0.95 : 0.65"
        @pointerdown="(e: PointerEvent) => onBoxPointerDown(e, row.code)"
      />
      <template v-if="row.renderType === 'qrcode'">
        <rect
          :x="(row.effective.xMm ?? 0) + (row.effective.widthMm ?? 0) * 0.12"
          :y="(row.effective.yMm ?? 0) + (row.effective.heightMm ?? 0) * 0.12"
          :width="(row.effective.widthMm ?? 0) * 0.28"
          :height="(row.effective.widthMm ?? 0) * 0.28"
          fill="#111"
          pointer-events="none"
        />
        <rect
          :x="(row.effective.xMm ?? 0) + (row.effective.widthMm ?? 0) * 0.6"
          :y="(row.effective.yMm ?? 0) + (row.effective.heightMm ?? 0) * 0.12"
          :width="(row.effective.widthMm ?? 0) * 0.28"
          :height="(row.effective.widthMm ?? 0) * 0.28"
          fill="#111"
          pointer-events="none"
        />
        <rect
          :x="(row.effective.xMm ?? 0) + (row.effective.widthMm ?? 0) * 0.12"
          :y="(row.effective.yMm ?? 0) + (row.effective.heightMm ?? 0) * 0.6"
          :width="(row.effective.widthMm ?? 0) * 0.28"
          :height="(row.effective.widthMm ?? 0) * 0.28"
          fill="#111"
          pointer-events="none"
        />
        <text
          :x="(row.effective.xMm ?? 0) + (row.effective.widthMm ?? 0) / 2"
          :y="(row.effective.yMm ?? 0) + (row.effective.heightMm ?? 0) / 2 + 1"
          font-size="3"
          text-anchor="middle"
          fill="#666"
          pointer-events="none"
        >
          QR
        </text>
      </template>
      <text
        v-if="row.previewText && row.renderType !== 'qrcode'"
        :x="(row.effective.xMm ?? 0) + row.textMetrics.padXMm"
        :y="row.textMetrics.previewY"
        :font-size="row.textMetrics.previewFontMm"
        fill="#606266"
        pointer-events="none"
      >
        {{ row.previewText }}
      </text>
      <text
        v-else-if="row.renderType !== 'qrcode'"
        :x="(row.effective.xMm ?? 0) + row.textMetrics.padXMm"
        :y="row.textMetrics.labelY"
        :font-size="row.textMetrics.labelFontMm"
        :fill="row.selected ? 'var(--el-color-danger)' : 'var(--el-color-primary)'"
        pointer-events="none"
      >
        {{ row.label }}
      </text>
      <circle
        v-if="editable && row.selected"
        class="overlay-box-guide__handle"
        :cx="(row.effective.xMm ?? 0) + (row.effective.widthMm ?? 0)"
        :cy="(row.effective.yMm ?? 0) + (row.effective.heightMm ?? 0)"
        r="1.4"
        @pointerdown="(e: PointerEvent) => onResizePointerDown(e, row.code)"
      />
    </g>
  </svg>
</template>

<style scoped>
.overlay-box-guide {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 3;
  touch-action: none;
}

.overlay-box-guide.is-dragging {
  cursor: grabbing;
}

.overlay-box-guide.is-editable .overlay-box-guide__hit {
  pointer-events: all;
}

.overlay-box-guide__hit {
  cursor: grab;
}

.overlay-box-guide__hit.is-selected {
  cursor: move;
}

.overlay-box-guide__handle {
  fill: var(--el-color-danger);
  stroke: #fff;
  stroke-width: 0.25;
  cursor: se-resize;
  pointer-events: all;
}

.overlay-box-guide__snap-lines line {
  opacity: 0.85;
}
</style>
