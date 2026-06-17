<script setup lang="ts">
import type { PrintElementDefDto } from '#/api/report/report-design';
import type { LayoutItem, LayoutPage } from '#/types/report-layout';

import { computed, ref } from 'vue';

import { ElEmpty, ElTag } from 'element-plus';

import {
  decodeReportElementDrag,
  REPORT_ELEMENT_DRAG_MIME,
  setReportElementDragData,
} from '#/utils/report/report-design-drag';
import OverlayBoxGuide from '#/components/report/designer/OverlayBoxGuide.vue';
import {
  filterAbsoluteLayoutItems,
  isAbsoluteLayoutItem,
} from '#/utils/report/absolute-layout-item';
import { computeEffectiveBox, ROLL_DESIGN_HEIGHT_MM } from '#/utils/report/overlay-layout';
import type { LayoutBox } from '#/types/report-layout';

defineOptions({ name: 'FlowDesignCanvas' });

const props = withDefaults(
  defineProps<{
    items?: LayoutItem[];
    elements?: PrintElementDefDto[];
    selectedIndex?: number;
    editable?: boolean;
    page?: LayoutPage | null;
    pipeline?: string;
  }>(),
  {
    items: () => [],
    elements: () => [],
    selectedIndex: 0,
    editable: false,
    page: null,
    pipeline: 'page',
  },
);

const emit = defineEmits<{
  'update:selectedIndex': [index: number];
  'update:items': [items: LayoutItem[]];
  add: [elementCode: string, insertIndex?: number];
  boxChange: [payload: { elementCode: string; box: LayoutBox }];
}>();

const dropInsertIndex = ref<number | null>(null);
const draggingIndex = ref<number | null>(null);

const paperWidthMm = computed(() => {
  if (props.page?.widthMm && props.page.widthMm > 0) {
    return props.page.widthMm;
  }
  return props.pipeline === 'roll' ? 80 : undefined;
});

const isRollPipeline = computed(() => props.pipeline === 'roll' || paperWidthMm.value != null);

const elementMap = computed(() => {
  const map = new Map<string, PrintElementDefDto>();
  for (const el of props.elements ?? []) {
    if (el.elementCode) {
      map.set(el.elementCode, el);
    }
  }
  return map;
});

const absoluteLayoutItems = computed(() =>
  filterAbsoluteLayoutItems(props.items ?? [], elementMap.value),
);

const hasRollAbsoluteSplit = computed(
  () => isRollPipeline.value && absoluteLayoutItems.value.length > 0,
);

const rollPaperHeightMm = computed(() => {
  let maxBottom = 160;
  for (const item of absoluteLayoutItems.value) {
    const def = elementMap.value.get(item.elementCode);
    const box = computeEffectiveBox(item, def);
    if (box?.yMm != null && box?.heightMm != null) {
      maxBottom = Math.max(maxBottom, box.yMm + box.heightMm + 12);
    }
  }
  return Math.min(ROLL_DESIGN_HEIGHT_MM, Math.max(180, maxBottom));
});

const rollDesignPage = computed(() => ({
  widthMm: paperWidthMm.value ?? 80,
  layoutMode: 'flow' as const,
}));

const rollPageSizeOptions = computed(() => ({
  rollDesignHeightMm: rollPaperHeightMm.value,
}));

const flowStackItems = computed(() =>
  (props.items ?? [])
    .map((item, index) => ({ item, index }))
    .filter(({ item }) => !isAbsoluteLayoutItem(item, elementMap.value.get(item.elementCode))),
);

const selectedElementCode = computed(() => props.items?.[props.selectedIndex]?.elementCode ?? null);

function onBoxChange(payload: { elementCode: string; box: LayoutBox }) {
  emit('boxChange', payload);
}

const paperStyle = computed(() => {
  if (hasRollAbsoluteSplit.value) {
    return {
      width: '100%',
      maxWidth: '100%',
    };
  }
  if (!paperWidthMm.value) {
    return undefined;
  }
  return {
    width: `${paperWidthMm.value}mm`,
    maxWidth: '100%',
  };
});

const rollZoneStyle = computed(() => {
  if (!hasRollAbsoluteSplit.value) {
    return undefined;
  }
  const widthMm = paperWidthMm.value ?? 80;
  return {
    width: `min(${widthMm}mm, 100%)`,
    maxWidth: '100%',
    flex: '0 1 auto',
    minHeight: `${rollPaperHeightMm.value}mm`,
    alignSelf: 'stretch',
  };
});

function labelOf(item: LayoutItem) {
  const def = elementMap.value.get(item.elementCode);
  return item.labelOverride || def?.label || item.elementCode;
}

function renderTypeOf(item: LayoutItem) {
  return elementMap.value.get(item.elementCode)?.renderType ?? 'block';
}

function isHidden(item: LayoutItem) {
  return item.visible === false;
}

function selectIndex(index: number) {
  emit('update:selectedIndex', index);
}

function clearDropState() {
  dropInsertIndex.value = null;
  draggingIndex.value = null;
}

/** 根据指针在区块上的垂直位置，决定插入到该 index 前或后 */
function insertIndexFromBlock(event: DragEvent, index: number) {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  return event.clientY < rect.top + rect.height / 2 ? index : index + 1;
}

function readDragPayload(event: DragEvent) {
  const raw =
    event.dataTransfer?.getData(REPORT_ELEMENT_DRAG_MIME) ||
    event.dataTransfer?.getData('text/plain') ||
    '';
  return decodeReportElementDrag(raw);
}

function reorderItems(fromIndex: number, insertAt: number) {
  const items = [...props.items];
  if (fromIndex < 0 || fromIndex >= items.length) {
    return;
  }
  let target = Math.max(0, Math.min(insertAt, items.length));
  const [row] = items.splice(fromIndex, 1);
  if (fromIndex < target) {
    target -= 1;
  }
  items.splice(target, 0, row);
  emit('update:items', items);
  emit('update:selectedIndex', target);
}

function onItemDragStart(event: DragEvent, index: number) {
  if (!props.editable) {
    event.preventDefault();
    return;
  }
  draggingIndex.value = index;
  setReportElementDragData(event, { kind: 'reorder', index });
}

function onDragOverInsert(event: DragEvent, insertAt: number) {
  if (!props.editable) {
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  dropInsertIndex.value = insertAt;
}

function onBlockDragOver(event: DragEvent, index: number) {
  if (!props.editable) {
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  dropInsertIndex.value = insertIndexFromBlock(event, index);
}

function onCanvasDragOver(event: DragEvent) {
  if (!props.editable) {
    return;
  }
  event.preventDefault();
}

function onDropAt(event: DragEvent, insertAt: number) {
  if (!props.editable) {
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  const payload = readDragPayload(event);
  clearDropState();
  if (!payload) {
    return;
  }
  if (payload.kind === 'palette') {
    emit('add', payload.code, insertAt);
    return;
  }
  reorderItems(payload.index, insertAt);
}

function onBlockDrop(event: DragEvent, index: number) {
  onDropAt(event, insertIndexFromBlock(event, index));
}

function onCanvasDrop(event: DragEvent) {
  if (
    (event.target as HTMLElement).closest(
      '.flow-design-canvas__block, .flow-design-canvas__drop-line',
    )
  ) {
    return;
  }
  onDropAt(event, props.items.length);
}

function blockClass(item: LayoutItem, index: number) {
  return {
    'is-selected': index === props.selectedIndex,
    'is-hidden': isHidden(item),
    'is-dragging': draggingIndex.value === index,
  };
}
</script>

<template>
  <div class="flow-design-canvas">
    <div class="flow-design-canvas__toolbar">
      <span class="flow-design-canvas__title">排版设计区</span>
      <ElTag v-if="paperWidthMm" size="small" type="info">{{ paperWidthMm }}mm 热敏纸</ElTag>
      <span v-if="editable" class="flow-design-canvas__hint">
        从左侧元素库拖入，或在区块间调整顺序；定位框拖拽带对齐吸附（红虚线为参考线）
      </span>
      <span v-else-if="!editable" class="flow-design-canvas__hint">出厂模板需先复制为院点草稿</span>
    </div>

    <div
      class="flow-design-canvas__paper-wrap"
      :class="{ 'is-editable': editable, 'is-drag-over': dropInsertIndex !== null }"
      @dragover="onCanvasDragOver"
      @drop="onCanvasDrop"
    >
      <div
        class="flow-design-canvas__paper"
        :class="{
          'flow-design-canvas__paper--roll': hasRollAbsoluteSplit,
        }"
        :style="paperStyle"
      >
        <div v-if="hasRollAbsoluteSplit" class="flow-design-canvas__roll-split">
          <aside class="flow-design-canvas__roll-zone" :style="rollZoneStyle">
            <div class="flow-design-canvas__roll-zone-label">绝对层定位区（二维码等）</div>
            <div class="flow-design-canvas__roll-zone-canvas">
              <OverlayBoxGuide
                class="flow-design-canvas__overlay-layer"
                :items="absoluteLayoutItems"
                :elements="elements"
                :page="rollDesignPage"
                :page-size-options="rollPageSizeOptions"
                :selected-element-code="selectedElementCode"
                :editable="editable"
                @select="
                  (code) => {
                    const idx = items.findIndex((i) => i.elementCode === code);
                    if (idx >= 0) selectIndex(idx);
                  }
                "
                @box-change="onBoxChange"
              />
            </div>
            <div class="flow-design-canvas__positioned-list">
              <button
                v-for="item in absoluteLayoutItems"
                :key="item.elementCode"
                type="button"
                class="flow-design-canvas__positioned-chip"
                :class="{ 'is-selected': item.elementCode === selectedElementCode }"
                @click="
                  () => {
                    const idx = items.findIndex((i) => i.elementCode === item.elementCode);
                    if (idx >= 0) selectIndex(idx);
                  }
                "
              >
                {{ labelOf(item) }} · 纸面定位
              </button>
            </div>
          </aside>

          <div class="flow-design-canvas__flow-layer flow-design-canvas__flow-layer--split">
            <template v-if="flowStackItems.length">
              <template
                v-for="{ item, index } in flowStackItems"
                :key="`${item.elementCode}-${index}`"
              >
                <div
                  v-if="editable"
                  class="flow-design-canvas__drop-line"
                  :class="{ 'is-active': dropInsertIndex === index }"
                  @dragover="onDragOverInsert($event, index)"
                  @drop="onDropAt($event, index)"
                />
                <div
                  class="flow-design-canvas__block"
                  :class="blockClass(item, index)"
                  :draggable="editable"
                  @click="selectIndex(index)"
                  @dragstart="onItemDragStart($event, index)"
                  @dragover="onBlockDragOver($event, index)"
                  @drop="onBlockDrop($event, index)"
                  @dragend="clearDropState"
                >
                  <span v-if="editable" class="flow-design-canvas__handle" title="拖动排序"
                    >⋮⋮</span
                  >
                  <div class="flow-design-canvas__block-body">
                    <div class="flow-design-canvas__block-head">
                      <strong>{{ labelOf(item) }}</strong>
                      <ElTag size="small" type="info">{{ renderTypeOf(item) }}</ElTag>
                      <ElTag v-if="isHidden(item)" size="small" type="warning">已隐藏</ElTag>
                    </div>
                    <div
                      class="flow-design-canvas__mock"
                      :class="`flow-design-canvas__mock--${renderTypeOf(item)}`"
                    >
                      <template v-if="renderTypeOf(item) === 'dividerLine'">
                        <hr />
                      </template>
                      <template v-else-if="renderTypeOf(item).includes('table')">
                        <div class="flow-design-canvas__table">
                          <span /><span /><span /><span />
                        </div>
                      </template>
                      <template v-else-if="renderTypeOf(item) === 'kvRow'">
                        <div
                          v-if="(item.columns ?? 1) > 1"
                          class="flow-design-canvas__kv-grid"
                          :style="{ gridTemplateColumns: `repeat(${item.columns}, 1fr)` }"
                        >
                          <div v-for="n in item.columns" :key="n" class="flow-design-canvas__kv">
                            <span />：<span />
                          </div>
                        </div>
                        <template v-else>
                          <div class="flow-design-canvas__kv"><span />：<span /></div>
                          <div class="flow-design-canvas__kv"><span />：<span /></div>
                        </template>
                      </template>
                      <template v-else>
                        <div class="flow-design-canvas__text-line" />
                      </template>
                    </div>
                  </div>
                </div>
              </template>
              <div
                v-if="editable"
                class="flow-design-canvas__drop-line flow-design-canvas__drop-line--tail"
                :class="{ 'is-active': dropInsertIndex === items.length }"
                @dragover="onDragOverInsert($event, items.length)"
                @drop="onDropAt($event, items.length)"
              />
            </template>

            <div
              v-if="!flowStackItems.length"
              class="flow-design-canvas__empty"
              :class="{ 'is-active': dropInsertIndex === 0 }"
              @dragover="onDragOverInsert($event, 0)"
              @drop="onDropAt($event, 0)"
            >
              <ElEmpty :image-size="56" description="拖入左侧元素开始排版" />
            </div>
          </div>
        </div>

        <div v-else class="flow-design-canvas__flow-layer">
          <template v-if="flowStackItems.length">
            <template
              v-for="{ item, index } in flowStackItems"
              :key="`${item.elementCode}-${index}`"
            >
              <div
                v-if="editable"
                class="flow-design-canvas__drop-line"
                :class="{ 'is-active': dropInsertIndex === index }"
                @dragover="onDragOverInsert($event, index)"
                @drop="onDropAt($event, index)"
              />
              <div
                class="flow-design-canvas__block"
                :class="blockClass(item, index)"
                :draggable="editable"
                @click="selectIndex(index)"
                @dragstart="onItemDragStart($event, index)"
                @dragover="onBlockDragOver($event, index)"
                @drop="onBlockDrop($event, index)"
                @dragend="clearDropState"
              >
                <span v-if="editable" class="flow-design-canvas__handle" title="拖动排序">⋮⋮</span>
                <div class="flow-design-canvas__block-body">
                  <div class="flow-design-canvas__block-head">
                    <strong>{{ labelOf(item) }}</strong>
                    <ElTag size="small" type="info">{{ renderTypeOf(item) }}</ElTag>
                    <ElTag v-if="isHidden(item)" size="small" type="warning">已隐藏</ElTag>
                  </div>
                  <div
                    class="flow-design-canvas__mock"
                    :class="`flow-design-canvas__mock--${renderTypeOf(item)}`"
                  >
                    <template v-if="renderTypeOf(item) === 'dividerLine'">
                      <hr />
                    </template>
                    <template v-else-if="renderTypeOf(item).includes('table')">
                      <div class="flow-design-canvas__table"><span /><span /><span /><span /></div>
                    </template>
                    <template v-else-if="renderTypeOf(item) === 'kvRow'">
                      <div
                        v-if="(item.columns ?? 1) > 1"
                        class="flow-design-canvas__kv-grid"
                        :style="{ gridTemplateColumns: `repeat(${item.columns}, 1fr)` }"
                      >
                        <div v-for="n in item.columns" :key="n" class="flow-design-canvas__kv">
                          <span />：<span />
                        </div>
                      </div>
                      <template v-else>
                        <div class="flow-design-canvas__kv"><span />：<span /></div>
                        <div class="flow-design-canvas__kv"><span />：<span /></div>
                      </template>
                    </template>
                    <template v-else>
                      <div class="flow-design-canvas__text-line" />
                    </template>
                  </div>
                </div>
              </div>
            </template>
            <div
              v-if="editable"
              class="flow-design-canvas__drop-line flow-design-canvas__drop-line--tail"
              :class="{ 'is-active': dropInsertIndex === items.length }"
              @dragover="onDragOverInsert($event, items.length)"
              @drop="onDropAt($event, items.length)"
            />
          </template>

          <div
            v-if="!flowStackItems.length && !absoluteLayoutItems.length"
            class="flow-design-canvas__empty"
            :class="{ 'is-active': dropInsertIndex === 0 }"
            @dragover="onDragOverInsert($event, 0)"
            @drop="onDropAt($event, 0)"
          >
            <ElEmpty :image-size="56" description="拖入左侧元素开始排版" />
          </div>
        </div>

        <div
          v-if="absoluteLayoutItems.length && !hasRollAbsoluteSplit"
          class="flow-design-canvas__positioned-list"
        >
          <button
            v-for="item in absoluteLayoutItems"
            :key="item.elementCode"
            type="button"
            class="flow-design-canvas__positioned-chip"
            :class="{ 'is-selected': item.elementCode === selectedElementCode }"
            @click="
              () => {
                const idx = items.findIndex((i) => i.elementCode === item.elementCode);
                if (idx >= 0) selectIndex(idx);
              }
            "
          >
            {{ labelOf(item) }} · 纸面定位
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.flow-design-canvas {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.flow-design-canvas__toolbar {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.flow-design-canvas__title {
  font-size: 13px;
  font-weight: 600;
}

.flow-design-canvas__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.flow-design-canvas__paper {
  position: relative;
  margin: 0 auto;
  min-height: 120px;
  padding: 12px 10px;
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 6%);
  box-sizing: border-box;
}

.flow-design-canvas__paper--roll {
  width: 100%;
  max-width: 100%;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.flow-design-canvas__paper:not(.flow-design-canvas__paper--roll) {
  margin: 0 auto;
}

.flow-design-canvas__roll-split {
  display: flex;
  flex-direction: row;
  align-items: stretch;
  gap: 12px;
  width: 100%;
  flex: 1;
  min-height: 0;
}

.flow-design-canvas__roll-zone {
  position: relative;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px dashed var(--el-border-color-lighter);
  border-radius: 4px;
  background: rgb(255 255 255 / 92%);
  max-height: 100%;
}

.flow-design-canvas__roll-zone .flow-design-canvas__overlay-layer {
  position: absolute;
  inset: 0;
}

.flow-design-canvas__roll-zone-canvas {
  position: relative;
  flex: 1;
  width: 100%;
  min-height: 120px;
}

.flow-design-canvas__roll-zone .flow-design-canvas__positioned-list {
  flex-shrink: 0;
  margin-top: 8px;
  padding: 8px 6px 6px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.flow-design-canvas__flow-layer--split {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.flow-design-canvas__roll-zone-label {
  position: absolute;
  top: 4px;
  left: 6px;
  z-index: 5;
  padding: 0 4px;
  font-size: 10px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  background: rgb(255 255 255 / 88%);
  border-radius: 2px;
  pointer-events: none;
}

.flow-design-canvas__flow-layer {
  position: relative;
  z-index: 1;
}

.flow-design-canvas__overlay-layer {
  z-index: 2;
}

.flow-design-canvas__qrcode-mock {
  width: 48px;
  height: 48px;
  margin: 0 auto;
  background:
    linear-gradient(45deg, #333 25%, transparent 25%) 0 0 / 8px 8px,
    linear-gradient(-45deg, #333 25%, transparent 25%) 0 0 / 8px 8px,
    linear-gradient(45deg, transparent 75%, #333 75%) 0 0 / 8px 8px,
    linear-gradient(-45deg, transparent 75%, #333 75%) 0 0 / 8px 8px;
  border: 1px solid var(--el-border-color);
}

.flow-design-canvas__positioned-list {
  position: relative;
  z-index: 4;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.flow-design-canvas__positioned-chip {
  padding: 4px 8px;
  font-size: 11px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.flow-design-canvas__positioned-chip.is-selected {
  color: var(--el-color-danger);
  border-color: var(--el-color-danger);
}

.flow-design-canvas__paper-wrap {
  flex: 1;
  min-height: 0;
  padding: 12px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  overflow: auto;
  display: flex;
  flex-direction: column;
}

.flow-design-canvas__paper-wrap.is-editable.is-drag-over {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.flow-design-canvas__drop-line {
  height: 10px;
  margin: 0;
  border-radius: 3px;
  transition: background 0.15s;
}

.flow-design-canvas__drop-line.is-active {
  height: 8px;
  background: var(--el-color-primary);
}

.flow-design-canvas__block {
  display: flex;
  gap: 6px;
  padding: 8px;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s,
    opacity 0.15s;
}

.flow-design-canvas__block:hover {
  border-color: var(--el-border-color);
  background: var(--el-fill-color-light);
}

.flow-design-canvas__block.is-selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.flow-design-canvas__block.is-hidden {
  opacity: 0.45;
}

.flow-design-canvas__block.is-dragging {
  opacity: 0.35;
}

.flow-design-canvas__handle {
  flex-shrink: 0;
  width: 14px;
  color: var(--el-text-color-secondary);
  cursor: grab;
  user-select: none;
  line-height: 1.2;
  font-size: 12px;
}

.flow-design-canvas__block-body {
  flex: 1;
  min-width: 0;
}

.flow-design-canvas__block-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 12px;
}

.flow-design-canvas__mock {
  pointer-events: none;
}

.flow-design-canvas__text-line {
  height: 10px;
  border-radius: 2px;
  background: linear-gradient(90deg, var(--el-border-color) 0 65%, transparent 65%);
}

.flow-design-canvas__mock--staticTitle .flow-design-canvas__text-line {
  height: 12px;
  background: linear-gradient(90deg, var(--el-border-color) 0 45%, transparent 45%);
}

.flow-design-canvas__kv-grid {
  display: grid;
  gap: 4px;
  margin-bottom: 4px;
}

.flow-design-canvas__kv-grid .flow-design-canvas__kv {
  margin-bottom: 0;
}

.flow-design-canvas__kv {
  display: flex;
  gap: 4px;
  margin-bottom: 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.flow-design-canvas__kv span:first-child {
  width: 36%;
  height: 8px;
  border-radius: 2px;
  background: var(--el-border-color-lighter);
}

.flow-design-canvas__kv span:last-child {
  flex: 1;
  height: 8px;
  border-radius: 2px;
  background: var(--el-border-color-lighter);
}

.flow-design-canvas__table {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
}

.flow-design-canvas__table span {
  height: 8px;
  border-radius: 2px;
  background: var(--el-border-color-lighter);
}

.flow-design-canvas__mock--dividerLine hr {
  margin: 4px 0;
  border: none;
  border-top: 1px dashed var(--el-border-color);
}

.flow-design-canvas__empty {
  padding: 24px 8px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
}

.flow-design-canvas__empty.is-active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
</style>
