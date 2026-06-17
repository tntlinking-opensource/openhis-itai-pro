<script setup lang="ts">
import type { PrintElementDefDto } from '#/api/report/report-design';
import type { LayoutItem } from '#/types/report-layout';

import { computed, ref } from 'vue';

import { ElButton, ElOption, ElSelect, ElTag } from 'element-plus';

import { filterAbsoluteLayoutItems } from '#/utils/report/absolute-layout-item';
import {
  computeEffectiveBox,
  formatBoxSummary,
  isOverlayCapableElement,
} from '#/utils/report/overlay-layout';
import { setReportElementDragData } from '#/utils/report/report-design-drag';

defineOptions({ name: 'LayoutItemPanel' });

const props = defineProps<{
  items: LayoutItem[];
  elements: PrintElementDefDto[];
  selectedIndex: number;
  editable?: boolean;
  layoutMode?: string;
  printOffsetMm?: number[];
}>();

const emit = defineEmits<{
  'update:selectedIndex': [index: number];
  'update:items': [items: LayoutItem[]];
  add: [elementCode: string];
}>();

const isOverlay = computed(() => props.layoutMode === 'overlay');

const elementLabelMap = computed(() => {
  const map = new Map<string, string>();
  for (const el of props.elements) {
    if (el.elementCode) {
      map.set(el.elementCode, el.label ?? el.elementCode);
    }
  }
  return map;
});

function isInLayout(elementCode: string) {
  return props.items.some((i) => i.elementCode === elementCode && i.visible !== false);
}

const addableOptions = computed(() =>
  props.elements.filter(
    (el) =>
      el.elementCode &&
      !isInLayout(el.elementCode) &&
      (!isOverlay.value || isOverlayCapableElement(el)),
  ),
);

/** ElSelect 重复选同一项时 change 不触发，用 key 强制重置 */
const addSelectKey = ref(0);

function handleAddPick(code: string | number | boolean | undefined) {
  const elementCode = String(code ?? '').trim();
  if (!elementCode) {
    return;
  }
  emit('add', elementCode);
  addSelectKey.value += 1;
}

function labelOf(item: LayoutItem) {
  return elementLabelMap.value.get(item.elementCode) ?? item.elementCode;
}

function patchItems(next: LayoutItem[]) {
  emit('update:items', next);
}

function move(index: number, delta: number) {
  const target = index + delta;
  if (target < 0 || target >= props.items.length) {
    return;
  }
  const next = [...props.items];
  const [row] = next.splice(index, 1);
  next.splice(target, 0, row);
  patchItems(next);
  emit('update:selectedIndex', target);
}

function isRequired(item: LayoutItem) {
  return props.elements.find((e) => e.elementCode === item.elementCode)?.required === true;
}

function canRemove(item: LayoutItem) {
  return props.editable === true && !isRequired(item);
}

function remove(index: number) {
  if (!canRemove(props.items[index]!)) {
    return;
  }
  const next = props.items.filter((_, i) => i !== index);
  patchItems(next);
  const nextIndex = Math.min(index, Math.max(0, next.length - 1));
  emit('update:selectedIndex', nextIndex);
}

const elementMap = computed(() => {
  const map = new Map<string, PrintElementDefDto>();
  for (const el of props.elements) {
    if (el.elementCode) {
      map.set(el.elementCode, el);
    }
  }
  return map;
});

function boxSummary(item: LayoutItem) {
  const def = elementMap.value.get(item.elementCode);
  return formatBoxSummary(computeEffectiveBox(item, def, props.printOffsetMm));
}

function showBoxSummary(item: LayoutItem) {
  if (isOverlay.value) {
    return true;
  }
  return filterAbsoluteLayoutItems([item], elementMap.value).length > 0;
}

function onPaletteDragStart(event: DragEvent, code: string) {
  if (!props.editable) {
    event.preventDefault();
    return;
  }
  setReportElementDragData(event, { kind: 'palette', code });
}
</script>

<template>
  <div class="layout-item-panel">
    <div v-if="editable && addableOptions.length" class="layout-item-panel__palette">
      <div class="layout-item-panel__palette-title">元素库（拖到设计区或点击添加）</div>
      <div class="layout-item-panel__palette-list">
        <button
          v-for="el in addableOptions"
          :key="el.elementCode"
          type="button"
          class="layout-item-panel__palette-chip"
          draggable="true"
          @dragstart="onPaletteDragStart($event, el.elementCode!)"
          @click="emit('add', el.elementCode!)"
        >
          {{ el.label ?? el.elementCode }}
        </button>
      </div>
    </div>
    <div v-if="editable && addableOptions.length" class="layout-item-panel__add">
      <ElSelect
        :key="addSelectKey"
        placeholder="添加元素"
        filterable
        clearable
        size="small"
        @change="handleAddPick"
      >
        <ElOption
          v-for="el in addableOptions"
          :key="el.elementCode"
          :label="el.label ?? el.elementCode"
          :value="el.elementCode!"
        />
      </ElSelect>
    </div>
    <ul class="layout-item-panel__list">
      <li
        v-for="(item, index) in items"
        :key="`${item.elementCode}-${index}`"
        class="layout-item-panel__row"
        :class="{ 'is-selected': index === selectedIndex }"
        @click="emit('update:selectedIndex', index)"
      >
        <div class="layout-item-panel__row-head">
          <span class="layout-item-panel__label" :title="labelOf(item)">{{ labelOf(item) }}</span>
          <ElTag v-if="isRequired(item)" size="small" type="info" effect="plain">必填</ElTag>
          <div v-if="editable" class="layout-item-panel__ops" @click.stop>
            <ElButton link size="small" :disabled="index === 0" @click="move(index, -1)"
              >↑</ElButton
            >
            <ElButton
              link
              size="small"
              :disabled="index >= items.length - 1"
              @click="move(index, 1)"
            >
              ↓
            </ElButton>
            <ElButton
              link
              size="small"
              type="danger"
              :disabled="!canRemove(item)"
              @click="remove(index)"
            >
              移除
            </ElButton>
          </div>
        </div>
        <div class="layout-item-panel__row-meta">
          <span v-if="showBoxSummary(item)" class="layout-item-panel__box">{{
            boxSummary(item)
          }}</span>
          <span v-else class="layout-item-panel__code">{{ item.elementCode }}</span>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.layout-item-panel {
  min-width: 0;
}

.layout-item-panel__palette {
  margin-bottom: 10px;
}

.layout-item-panel__palette-title {
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.layout-item-panel__palette-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.layout-item-panel__palette-chip {
  padding: 4px 8px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  color: var(--el-text-color-primary);
  cursor: grab;
}

.layout-item-panel__palette-chip:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.layout-item-panel__add {
  margin-bottom: 8px;
}

.layout-item-panel__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.layout-item-panel__row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 6px;
  border-radius: 4px;
  cursor: pointer;
}

.layout-item-panel__row.is-selected {
  background: var(--el-color-primary-light-9);
}

.layout-item-panel__row-head {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.layout-item-panel__row-meta {
  min-width: 0;
  padding-left: 2px;
}

.layout-item-panel__label {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.4;
  word-break: break-all;
}

.layout-item-panel__code {
  display: block;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.layout-item-panel__box {
  display: block;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, monospace;
  word-break: break-all;
}

.layout-item-panel__ops {
  display: flex;
  flex-shrink: 0;
  gap: 0;
  margin-left: auto;
}
</style>
