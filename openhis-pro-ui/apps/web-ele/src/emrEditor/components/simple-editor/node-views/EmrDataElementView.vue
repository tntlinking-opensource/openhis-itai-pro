<script setup lang="ts">
import type { EmrDataElementWidget } from '../data-element-types';
import type { InlineFieldNavMode } from '../use-inline-field-keydown';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { nodeViewProps, NodeViewWrapper } from '@tiptap/vue-3';

import { getDataElementDef } from '../data-element-registry';
import { isEmrDataElementWidget } from '../data-element-types';
import {
  insertPlainTextAtCaret,
  readContentEditablePlainText,
} from '../inline-contenteditable-caret';
import { useEmrIslandDomEditable } from '../use-emr-editor-caps';
import { handleInlineFieldKeydown } from '../use-inline-field-keydown';

const props = defineProps(nodeViewProps);

const elementId = computed(() => String(props.node.attrs.elementId ?? ''));
const labelSnapshot = computed(() => String(props.node.attrs.labelSnapshot ?? ''));
const placeholder = computed(
  () => String(props.node.attrs.placeholder ?? '') || labelSnapshot.value || '填写',
);
const valueStr = computed(() => String(props.node.attrs.value ?? ''));
const optionsJson = computed(() => String(props.node.attrs.optionsJson ?? '[]'));
const editable = computed(() => props.node.attrs.editable !== false);

const def = computed(() => getDataElementDef(elementId.value));

const effectiveWidget = computed((): EmrDataElementWidget => {
  const o = String(props.node.attrs.widgetOverride ?? '').trim();
  if (o && isEmrDataElementWidget(o)) return o;
  if (elementId.value && def.value?.widget) return def.value.widget;
  return 'text';
});

const isTextWidget = computed(() => effectiveWidget.value === 'text');

function parseSelectOptions(json: string): { label: string; value: string }[] {
  try {
    const raw = JSON.parse(json || '[]');
    if (!Array.isArray(raw)) return [];
    return raw
      .map((item: unknown) => {
        if (typeof item === 'string') return { value: item, label: item };
        if (item && typeof item === 'object') {
          const o = item as Record<string, unknown>;
          const v = String(o.value ?? o.code ?? '');
          const l = String(o.label ?? o.value ?? o.code ?? '');
          return { value: v || l, label: l || v };
        }
        return { value: '', label: '' };
      })
      .filter((x) => x.value || x.label);
  } catch {
    return [];
  }
}

const selectOptions = computed(() => parseSelectOptions(optionsJson.value));

const titleTip = computed(() => {
  if (!elementId.value) {
    return `自由文本（不绑定数据元）· ${placeholder.value}`;
  }
  const n = labelSnapshot.value || def.value?.name || elementId.value;
  const mode = editable.value ? '可编辑' : '随患者信息更新';
  return `数据元 ${elementId.value}（${n}）· ${mode}`;
});

const isFree = computed(() => !elementId.value);

/** 固定模板：无 elementId 的占位等同自由文本；有 id 则仅可编辑数据元可填 */
const islandWhenLocked = computed(() => {
  if (!elementId.value) return true;
  return editable.value;
});
const domEditable = useEmrIslandDomEditable(islandWhenLocked);

const textFieldRef = ref<HTMLElement | null>(null);
const lineInputRef = ref<HTMLInputElement | null>(null);

function syncTextFieldFromAttrs() {
  nextTick(() => {
    const el = textFieldRef.value;
    if (!el || document.activeElement === el) return;
    const next = valueStr.value;
    if (readContentEditablePlainText(el) !== next) {
      el.textContent = next;
    }
  });
}

function onTextFieldInput() {
  const el = textFieldRef.value;
  if (!el) return;
  props.updateAttributes({ value: readContentEditablePlainText(el) });
}

function onTextFieldPaste(e: ClipboardEvent) {
  e.preventDefault();
  const t = e.clipboardData?.getData('text/plain') ?? '';
  insertPlainTextAtCaret(t);
  onTextFieldInput();
}

function onTextFieldKeydown(e: KeyboardEvent) {
  const pos = props.getPos?.();
  if (typeof pos !== 'number') return;
  handleInlineFieldKeydown(props.editor, pos, e, 'contentEditable');
}

function syncLineInputWidth() {
  nextTick(() => {
    const el = lineInputRef.value;
    if (!el) return;
    el.style.width = '1px';
    const minPx = 28;
    el.style.width = `${Math.max(minPx, el.scrollWidth)}px`;
  });
}

function onNumberInput(e: Event) {
  props.updateAttributes({ value: (e.target as HTMLInputElement).value });
  syncLineInputWidth();
}

function onTextareaInput(e: Event) {
  props.updateAttributes({ value: (e.target as HTMLTextAreaElement).value });
}

function onSelectChange(e: Event) {
  props.updateAttributes({ value: (e.target as HTMLSelectElement).value });
}

function onCheckboxChange(e: Event) {
  const checked = (e.target as HTMLInputElement).checked;
  props.updateAttributes({ value: checked ? 'true' : 'false' });
}

function keydownHandler(mode: InlineFieldNavMode) {
  return (e: KeyboardEvent) => {
    const pos = props.getPos?.();
    if (typeof pos !== 'number') return;
    handleInlineFieldKeydown(props.editor, pos, e, mode);
  };
}

const checkboxChecked = computed(() => valueStr.value === 'true' || valueStr.value === '1');

const selectDisplayLabel = computed(() => {
  const v = valueStr.value;
  const hit = selectOptions.value.find((o) => o.value === v);
  return hit?.label ?? v;
});

onMounted(() => {
  if (editable.value && isTextWidget.value) {
    syncTextFieldFromAttrs();
  }
  if (editable.value && effectiveWidget.value === 'number') {
    syncLineInputWidth();
  }
});

watch([valueStr, effectiveWidget, editable], () => {
  if (editable.value && isTextWidget.value) {
    syncTextFieldFromAttrs();
  }
  if (editable.value && effectiveWidget.value === 'number') {
    syncLineInputWidth();
  }
});
</script>

<template>
  <NodeViewWrapper
    as="span"
    class="emr-de"
    :class="{
      'emr-de--free': isFree,
      'emr-de--bound': !isFree,
      'emr-de--readonly': !editable,
      'emr-de--multiline': effectiveWidget === 'multiline',
      'emr-de--text': isTextWidget,
    }"
    data-node-view-wrapper=""
    :title="titleTip"
  >
    <template v-if="!editable">
      <span
        v-if="effectiveWidget === 'checkbox'"
        class="emr-de__readonly-val"
        :aria-label="titleTip"
      >
        {{ checkboxChecked ? '是' : '否' }}
      </span>
      <span v-else-if="effectiveWidget === 'select'" class="emr-de__readonly-val">{{
        selectDisplayLabel
      }}</span>
      <span
        v-else-if="effectiveWidget === 'multiline'"
        class="emr-de__readonly-val emr-de__readonly-val--multiline"
        >{{ valueStr }}</span
      >
      <span
        v-else-if="effectiveWidget === 'text'"
        class="emr-de-text-bracket emr-de-text-bracket--readonly"
      >
        <span class="emr-bracket-field__bracket" aria-hidden="true">[</span>
        <span
          class="emr-de__readonly-val emr-de__readonly-val--wrap emr-de__readonly-val--bracket"
          >{{ valueStr }}</span
        >
        <span class="emr-bracket-field__bracket" aria-hidden="true">]</span>
      </span>
      <span v-else class="emr-de__readonly-val emr-de__readonly-val--wrap">{{ valueStr }}</span>
    </template>

    <template v-else>
      <span v-if="effectiveWidget === 'text'" class="emr-de-text-bracket">
        <span class="emr-bracket-field__bracket" aria-hidden="true">[</span>
        <span
          ref="textFieldRef"
          role="textbox"
          class="emr-inline-text__field emr-de__text-field"
          tabindex="0"
          spellcheck="false"
          :data-placeholder="placeholder"
          :contenteditable="domEditable ? 'true' : 'false'"
          :aria-label="titleTip"
          @input="onTextFieldInput"
          @paste="onTextFieldPaste"
          @keydown="onTextFieldKeydown"
        ></span>
        <span class="emr-bracket-field__bracket" aria-hidden="true">]</span>
      </span>
      <textarea
        v-else-if="effectiveWidget === 'multiline'"
        class="emr-de__control emr-de__control--textarea"
        :value="valueStr"
        :placeholder="placeholder"
        rows="2"
        :disabled="!domEditable"
        :aria-label="titleTip"
        spellcheck="false"
        @input="onTextareaInput"
        @keydown="keydownHandler('multiline')"
      ></textarea>
      <input
        v-else-if="effectiveWidget === 'number'"
        ref="lineInputRef"
        class="emr-de__control emr-de__control--number"
        type="number"
        :value="valueStr"
        :placeholder="placeholder"
        :disabled="!domEditable"
        :aria-label="titleTip"
        @input="onNumberInput"
        @keydown="keydownHandler('number')"
      />
      <select
        v-else-if="effectiveWidget === 'select'"
        class="emr-de__control emr-de__control--select"
        :value="valueStr"
        :disabled="!domEditable"
        :aria-label="titleTip"
        @change="onSelectChange"
        @keydown="keydownHandler('select')"
      >
        <option v-if="selectOptions.length === 0" value="">{{ placeholder }}</option>
        <option v-for="opt in selectOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
      <label v-else-if="effectiveWidget === 'checkbox'" class="emr-de__checkbox-wrap">
        <input
          class="emr-de__checkbox"
          type="checkbox"
          :checked="checkboxChecked"
          :disabled="!domEditable"
          :aria-label="titleTip"
          @change="onCheckboxChange"
          @keydown="keydownHandler('checkbox')"
        />
        <span class="emr-de__checkbox-label">{{ labelSnapshot || placeholder }}</span>
      </label>
    </template>
  </NodeViewWrapper>
</template>

<style scoped>
.emr-de {
  display: inline;
  box-decoration-break: clone;
  margin: 0 1px;
  padding: 1px 6px;
  border-radius: 4px;
  font: inherit;
  font-size: inherit;
  line-height: inherit;
  vertical-align: baseline;
  white-space: nowrap;
}

.emr-de--multiline {
  white-space: normal;
}

.emr-de--text {
  display: inline-flex;
  align-items: baseline;
  max-width: 100%;
  white-space: normal;
  box-sizing: border-box;
  vertical-align: baseline;
  padding: 0;
  border: none;
  background: transparent;
}

.emr-de-text-bracket {
  display: inline-flex;
  align-items: baseline;
  max-width: 100%;
}

.emr-bracket-field__bracket {
  color: var(--el-color-primary, #409eff);
  font-weight: 600;
  user-select: none;
  pointer-events: none;
  font: inherit;
  font-size: inherit;
  line-height: inherit;
}

.emr-de__readonly-val--bracket {
  color: #64748b;
}

.emr-de--free:not(.emr-de--text) {
  border: 1px dashed #cbd5e1;
  background: #f8fafc;
  color: #334155;
}

.emr-de--bound:not(.emr-de--text) {
  border: 1px solid transparent;
  background: #f1f5f9;
  color: #1e293b;
}

.emr-de--readonly.emr-de--bound:not(.emr-de--text) {
  background: #e8eaf6;
  color: #283593;
}

.emr-de__readonly-val {
  display: inline;
  white-space: nowrap;
}

.emr-de__readonly-val--multiline {
  white-space: pre-wrap;
  word-break: break-word;
}

.emr-de__readonly-val--wrap {
  display: inline-block;
  max-width: 100%;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
  vertical-align: baseline;
}

.emr-de__text-field {
  display: inline-block;
  min-width: 2ch;
  max-width: 100%;
  outline: none;
  cursor: text;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
  vertical-align: baseline;
  color: #9ca3af;
}

.emr-de__text-field:not(:empty) {
  color: #64748b;
}

.emr-de__text-field:empty::before {
  content: attr(data-placeholder);
  color: #9ca3af;
  pointer-events: none;
}

.emr-de--free .emr-de__text-field:focus {
  box-shadow: inset 0 -1px 0 0 #0d9488;
  border-radius: 1px;
}

.emr-de--bound .emr-de__text-field:focus {
  box-shadow: inset 0 -1px 0 0 #5c6bc0;
  border-radius: 1px;
}

.emr-de__text-field[contenteditable='false'] {
  opacity: 0.88;
  cursor: not-allowed;
}

.emr-de__control {
  display: inline-block;
  box-sizing: content-box;
  min-width: 0;
  max-width: none;
  margin: 0;
  padding: 0;
  border: none;
  outline: none;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: inherit;
  line-height: inherit;
  vertical-align: baseline;
}

.emr-de__control--number {
  white-space: nowrap;
  min-width: 2.5em;
}

.emr-de__control--textarea {
  display: inline-block;
  vertical-align: bottom;
  min-width: 120px;
  min-height: 2.5em;
  resize: vertical;
  white-space: pre-wrap;
  word-break: break-word;
}

.emr-de__control--select {
  max-width: 240px;
  cursor: pointer;
}

.emr-de__control:disabled {
  opacity: 0.88;
  cursor: not-allowed;
}

.emr-de__control--number:focus {
  box-shadow: inset 0 -1px 0 0 #0d9488;
  border-radius: 1px;
}

.emr-de--bound .emr-de__control--number:focus {
  box-shadow: inset 0 -1px 0 0 #5c6bc0;
}

.emr-de__checkbox-wrap {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  padding: 0;
  cursor: pointer;
  vertical-align: baseline;
}

.emr-de__checkbox {
  margin: 0;
  flex-shrink: 0;
}

.emr-de__checkbox-label {
  font: inherit;
  font-size: inherit;
  line-height: inherit;
}
</style>
