<script setup lang="ts">
import type { EmrDataElementWidget } from '../data-element-types';
import type { InlineFieldNavMode } from '../use-inline-field-keydown';

import type { ComputedRef } from 'vue';

import { computed, inject, nextTick, onMounted, ref, watch } from 'vue';

import { nodeViewProps, NodeViewWrapper } from '@tiptap/vue-3';

import { getDataElementDef } from '../data-element-registry';
import { isEmrDataElementWidget } from '../data-element-types';
import { parseEmrWidgetOptions, resolveEmrWidgetOptionLabel } from '../emr-widget-options';
import {
  contentEditableCaretOffset,
  insertPlainTextAtCaret,
  peekPinnedIslandCaret,
  pinIslandCaret,
  readContentEditablePlainText,
  setContentEditableCaretOffset,
} from '../inline-contenteditable-caret';
import { useEmrIslandDomEditable } from '../use-emr-editor-caps';
import {
  captureIslandDeleteSelection,
  handleInlineFieldKeydown,
  handleIslandContentEditableBeforeInputDelete,
  handleIslandContentEditableBeforeInputText,
  insertNewlineInContentEditableIsland,
} from '../use-inline-field-keydown';

const props = defineProps(nodeViewProps);

const elementId = computed(() => String(props.node.attrs.elementId ?? ''));
const labelSnapshot = computed(() => String(props.node.attrs.labelSnapshot ?? ''));
/** 字段提示（title / 文书态占位）；模板态正文不再重复展示字段名 */
const fieldHint = computed(
  () => String(props.node.attrs.placeholder ?? '') || labelSnapshot.value || '填写',
);
const valueStr = computed(() => String(props.node.attrs.value ?? ''));
/** type=date 只认 YYYY-MM-DD；带时刻的落库值需截断，否则控件空白却仍显 [] */
const dateInputValue = computed(() => {
  const raw = valueStr.value.trim();
  if (!raw) return '';
  const m = raw.match(/^(\d{4}-\d{2}-\d{2})/);
  return m?.[1] ?? '';
});
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
const isSlotWidget = computed(() =>
  ['select', 'radio', 'date', 'checkbox', 'number'].includes(effectiveWidget.value),
);

/** 文书 / 模板：text 类贴近打印（默认无括号）；select/date/number 与自由文本同构带蓝括号 */
const surfaceInject = inject<ComputedRef<'document' | 'template'>>('emrEditorSurface');
const surface = computed(() => surfaceInject?.value ?? 'document');
const printLike = computed(() => true);
const showBrackets = computed(() => false);

/** 模板态空值不回填字段名，避免「过敏史：过敏史」；绑定信息见 title */
const displayText = computed(() => valueStr.value);

/** 模板态自由文本不回填字段名；下拉/日期等仍显示插入时配置的占位提示 */
const visualPlaceholder = computed(() => {
  const tip = String(props.node.attrs.placeholder ?? '').trim();
  if (tip) return tip;
  if (surface.value === 'template') return '';
  return fieldHint.value;
});

const selectOptions = computed(() => parseEmrWidgetOptions(optionsJson.value));

/** 同组 radio 的 name，避免页内多组互斥串台 */
const radioGroupName = computed(() => {
  const sid = String(props.node.attrs.slotId ?? '').trim();
  if (sid) return `emr-radio-${sid}`;
  const pos = props.getPos?.();
  return `emr-radio-${typeof pos === 'number' ? pos : elementId.value || 'x'}`;
});

const titleTip = computed(() => {
  const widget = effectiveWidget.value;
  let kind = '自由文本';
  if (widget === 'select') kind = '下拉';
  else if (widget === 'radio') kind = '单选';
  else if (widget === 'date') kind = '日期';
  else if (widget === 'checkbox') kind = '复选';
  if (!elementId.value) {
    return `${kind}（模板内选项）· ${fieldHint.value}`;
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
const textareaRef = ref<HTMLTextAreaElement | null>(null);
const selectRef = ref<HTMLSelectElement | null>(null);
const dateInputRef = ref<HTMLInputElement | null>(null);

function syncTextareaHeight() {
  nextTick(() => {
    const el = textareaRef.value;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.max(el.scrollHeight, 40)}px`;
  });
}

function syncTextFieldFromAttrs() {
  nextTick(() => {
    const el = textFieldRef.value;
    if (!el || props.editor.view?.composing) return;
    const next = valueStr.value;
    const dom = readContentEditablePlainText(el);
    const pinned = peekPinnedIslandCaret(el);
    if (document.activeElement === el) {
      if (dom !== next && pinned != null) {
        el.textContent = next;
        setContentEditableCaretOffset(el, Math.min(pinned, next.length));
      }
      return;
    }
    if (dom === next) return;
    el.textContent = next;
    if (pinned != null) {
      setContentEditableCaretOffset(el, Math.min(pinned, next.length));
    }
  });
}

function onTextFieldInput() {
  const el = textFieldRef.value;
  if (!el) return;
  const next = readContentEditablePlainText(el);
  if (next === valueStr.value) return;
  const caret = contentEditableCaretOffset(el);
  if (caret >= 0) pinIslandCaret(el, caret, 400);
  props.updateAttributes({ value: next });
  nextTick(() => {
    const now = textFieldRef.value;
    if (!now) return;
    const pinned = peekPinnedIslandCaret(now);
    if (pinned == null) return;
    const at = Math.min(pinned, readContentEditablePlainText(now).length);
    setContentEditableCaretOffset(now, at);
    requestAnimationFrame(() => {
      const later = textFieldRef.value;
      if (!later) return;
      const latest = peekPinnedIslandCaret(later) ?? pinned;
      setContentEditableCaretOffset(
        later,
        Math.min(latest, readContentEditablePlainText(later).length),
      );
    });
  });
}

function onTextFieldPaste(e: ClipboardEvent) {
  e.preventDefault();
  const t = e.clipboardData?.getData('text/plain') ?? '';
  insertPlainTextAtCaret(t);
  onTextFieldInput();
}

function onTextFieldKeydownCapture(e: KeyboardEvent) {
  if (e.key !== 'Backspace' && e.key !== 'Delete') return;
  if (e.ctrlKey || e.metaKey || e.altKey) return;
  captureIslandDeleteSelection(textFieldRef.value);
}

function onTextFieldKeydown(e: KeyboardEvent) {
  const pos = props.getPos?.();
  if (typeof pos !== 'number') return;
  handleInlineFieldKeydown(props.editor, pos, e, 'contentEditable');
}

/** beforeinput：部分环境 Enter 只走 insertParagraph，不经 keydown */
function onTextFieldBeforeInput(e: InputEvent) {
  const pos = props.getPos?.();
  if (typeof pos !== 'number') return;
  const el = textFieldRef.value;
  if (!el?.isContentEditable) return;
  if (handleIslandContentEditableBeforeInputText(el, e, onTextFieldInput)) {
    return;
  }
  const t = e.inputType;
  if (t !== 'insertParagraph' && t !== 'insertLineBreak') {
    handleIslandContentEditableBeforeInputDelete(props.editor, pos, el, e);
    return;
  }
  if (insertNewlineInContentEditableIsland(props.editor, pos, el)) {
    e.preventDefault();
    e.stopPropagation();
  }
}

/** 下拉宽度跟当前展示文案走，避免占一整行 */
function syncSelectWidth() {
  nextTick(() => {
    const el = selectRef.value;
    if (!el) return;
    const label =
      resolveEmrWidgetOptionLabel(selectOptions.value, valueStr.value) ||
      fieldHint.value ||
      '请选择';
    const probe = document.createElement('span');
    probe.style.cssText =
      'position:absolute;visibility:hidden;white-space:nowrap;font:inherit;pointer-events:none';
    probe.textContent = label;
    document.body.append(probe);
    const w = Math.ceil(probe.getBoundingClientRect().width) + 22;
    probe.remove();
    el.style.width = `${Math.max(48, Math.min(w, 220))}px`;
  });
}

/** 数字岛：按输入框当前值回写 attrs，并钉住光标，避免 NodeView 刷新把后续数字插到格首（B23） */
function onNumberInput(e: Event) {
  const el = e.target as HTMLInputElement;
  const caret = el.selectionStart ?? el.value.length;
  props.updateAttributes({ value: el.value });
  nextTick(() => {
    const input = lineInputRef.value;
    if (!input || document.activeElement !== input) return;
    const at = Math.min(caret, input.value.length);
    input.setSelectionRange(at, at);
  });
}

function onTextareaInput(e: Event) {
  props.updateAttributes({ value: (e.target as HTMLTextAreaElement).value });
  syncTextareaHeight();
}

function onSelectChange(e: Event) {
  props.updateAttributes({ value: (e.target as HTMLSelectElement).value });
  syncSelectWidth();
}

function onDateInput(e: Event) {
  props.updateAttributes({ value: (e.target as HTMLInputElement).value });
}

/** 点击整格直接弹出日期选择器（不必点日历图标） */
function openDatePicker(e: Event) {
  if (!domEditable.value) return;
  const el = dateInputRef.value ?? (e.currentTarget as HTMLInputElement | null);
  if (!el || el.disabled) return;
  // 阻止编辑器抢焦点；再主动 showPicker
  e.stopPropagation();
  try {
    el.focus({ preventScroll: true });
    const picker = el as HTMLInputElement & { showPicker?: () => void };
    if (typeof picker.showPicker === 'function') {
      picker.showPicker();
    }
  } catch {
    // 部分浏览器要求用户手势且可能抛 InvalidStateError，忽略即可
  }
}

/** 点击整格聚焦下拉，避免编辑器抢 mousedown */
function openSelect(e: Event) {
  if (!domEditable.value) return;
  const el = selectRef.value ?? (e.currentTarget as HTMLSelectElement | null);
  if (!el || el.disabled) return;
  e.stopPropagation();
  el.focus({ preventScroll: true });
}

function onRadioChange(code: string) {
  props.updateAttributes({ value: code });
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

const isRequired = computed(() => {
  const r = props.node.attrs.required;
  return r === true || r === '1' || r === 'true';
});

const isRequiredEmpty = computed(() => {
  if (!isRequired.value || !editable.value) return false;
  if (effectiveWidget.value === 'checkbox') {
    return !checkboxChecked.value;
  }
  return !valueStr.value.trim();
});

const selectDisplayLabel = computed(() =>
  resolveEmrWidgetOptionLabel(selectOptions.value, valueStr.value),
);

/** v-once 冻住 contenteditable；只读打开后再复用必须按 caps 写回 DOM */
function syncIslandDomEditable() {
  const el = textFieldRef.value;
  if (!el) return;
  const next = domEditable.value;
  if (el.isContentEditable === next) return;
  el.contentEditable = next ? 'true' : 'false';
}

onMounted(() => {
  if (editable.value && isTextWidget.value) {
    syncTextFieldFromAttrs();
    syncIslandDomEditable();
  }
  if (editable.value && effectiveWidget.value === 'multiline') {
    syncTextareaHeight();
  }
  if (editable.value && effectiveWidget.value === 'select') {
    syncSelectWidth();
  }
});

watch(domEditable, () => {
  if (isTextWidget.value) syncIslandDomEditable();
});

watch([valueStr, effectiveWidget, editable, optionsJson], () => {
  if (editable.value && isTextWidget.value) {
    syncTextFieldFromAttrs();
  }
  if (editable.value && effectiveWidget.value === 'multiline') {
    syncTextareaHeight();
  }
  if (editable.value && effectiveWidget.value === 'select') {
    syncSelectWidth();
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
      'emr-de--slot': isSlotWidget,
      'emr-de--select': effectiveWidget === 'select',
      'emr-de--radio': effectiveWidget === 'radio',
      'emr-de--date': effectiveWidget === 'date',
      'emr-de--checkbox': effectiveWidget === 'checkbox',
      'emr-de--print-like': printLike,
      'emr-de--required': isRequired,
      'emr-de--required-empty': isRequiredEmpty,
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
      <span
        v-else-if="effectiveWidget === 'select'"
        class="emr-de-text-bracket emr-de-slot-bracket"
      >
        <span class="emr-bracket-field__bracket" aria-hidden="true">[</span>
        <span class="emr-de__readonly-val emr-de__readonly-val--slot">{{
          selectDisplayLabel
        }}</span>
        <span class="emr-bracket-field__bracket" aria-hidden="true">]</span>
      </span>
      <span v-else-if="effectiveWidget === 'radio'" class="emr-de__readonly-val">{{
        selectDisplayLabel
      }}</span>
      <span
        v-else-if="effectiveWidget === 'date' || effectiveWidget === 'number'"
        class="emr-de-text-bracket emr-de-slot-bracket"
        :class="{ 'emr-de-text-bracket--plain': !displayText }"
      >
        <span v-if="displayText" class="emr-bracket-field__bracket" aria-hidden="true">[</span>
        <span class="emr-de__readonly-val emr-de__readonly-val--slot emr-de__readonly-val--wrap">{{
          displayText || visualPlaceholder
        }}</span>
        <span v-if="displayText" class="emr-bracket-field__bracket" aria-hidden="true">]</span>
      </span>
      <span
        v-else-if="effectiveWidget === 'multiline'"
        class="emr-de__readonly-val emr-de__readonly-val--multiline"
        >{{ displayText }}</span
      >
      <span
        v-else-if="effectiveWidget === 'text'"
        class="emr-de-text-bracket emr-de-text-bracket--readonly"
        :class="{ 'emr-de-text-bracket--plain': !showBrackets }"
      >
        <span v-if="showBrackets" class="emr-bracket-field__bracket" aria-hidden="true">[</span>
        <span
          class="emr-de__readonly-val emr-de__readonly-val--wrap"
          :class="{ 'emr-de__readonly-val--bracket': showBrackets }"
          >{{ displayText }}</span
        >
        <span v-if="showBrackets" class="emr-bracket-field__bracket" aria-hidden="true">]</span>
      </span>
      <span v-else class="emr-de__readonly-val emr-de__readonly-val--wrap">{{ displayText }}</span>
    </template>

    <template v-else>
      <span
        v-if="effectiveWidget === 'text'"
        class="emr-de-text-bracket"
        :class="{ 'emr-de-text-bracket--plain': !showBrackets }"
      >
        <span v-if="showBrackets" class="emr-bracket-field__bracket" aria-hidden="true">[</span>
        <span
          ref="textFieldRef"
          v-once
          role="textbox"
          class="emr-inline-text__field emr-de__text-field"
          tabindex="0"
          spellcheck="false"
          :data-placeholder="visualPlaceholder"
          :contenteditable="domEditable ? 'true' : 'false'"
          :aria-label="titleTip"
          @pointerdown.stop
          @mousedown.stop
          @input="onTextFieldInput"
          @beforeinput="onTextFieldBeforeInput"
          @paste="onTextFieldPaste"
          @keydown.capture="onTextFieldKeydownCapture"
          @keydown="onTextFieldKeydown"
        ></span>
        <span v-if="showBrackets" class="emr-bracket-field__bracket" aria-hidden="true">]</span>
      </span>
      <template v-else-if="effectiveWidget === 'multiline'">
        <textarea
          ref="textareaRef"
          class="emr-de__control emr-de__control--textarea"
          :value="valueStr"
          :placeholder="visualPlaceholder"
          rows="2"
          :disabled="!domEditable"
          :aria-label="titleTip"
          spellcheck="false"
          @input="onTextareaInput"
          @keydown="keydownHandler('multiline')"
        ></textarea>
      </template>
      <span
        v-else-if="effectiveWidget === 'number'"
        class="emr-de-text-bracket emr-de-slot-bracket emr-de-number"
        :class="{ 'emr-de-text-bracket--plain': !valueStr }"
        @pointerdown.stop
        @mousedown.stop
      >
        <span v-if="valueStr" class="emr-bracket-field__bracket" aria-hidden="true">[</span>
        <span class="emr-de-number__wrap">
          <span class="emr-de-number__text" :class="{ 'emr-de-number__text--empty': !valueStr }">{{
            valueStr || visualPlaceholder
          }}</span>
          <input
            ref="lineInputRef"
            class="emr-de__control emr-de__control--number emr-de__slot-control emr-de-number__input opacity-0"
            :class="{ 'emr-de__slot-control--empty': !valueStr }"
            type="text"
            inputmode="decimal"
            :value="valueStr"
            :placeholder="visualPlaceholder"
            :disabled="!domEditable"
            :aria-label="titleTip"
            :title="valueStr ? valueStr : visualPlaceholder || fieldHint"
            @input="onNumberInput"
            @beforeinput.stop
            @keydown.stop="keydownHandler('number')"
          />
        </span>
        <span v-if="valueStr" class="emr-bracket-field__bracket" aria-hidden="true">]</span>
      </span>
      <span
        v-else-if="effectiveWidget === 'select'"
        class="emr-de-text-bracket emr-de-slot-bracket"
        @pointerdown.stop="openSelect"
      >
        <span class="emr-bracket-field__bracket" aria-hidden="true">[</span>
        <select
          ref="selectRef"
          class="emr-de__control emr-de__control--select emr-de__slot-control"
          :class="{ 'emr-de__slot-control--empty': !valueStr }"
          :value="valueStr"
          :disabled="!domEditable"
          :aria-label="titleTip"
          @pointerdown.stop="openSelect"
          @change="onSelectChange"
          @keydown="keydownHandler('select')"
        >
          <option value="">{{ visualPlaceholder || fieldHint || '请选择' }}</option>
          <option v-for="opt in selectOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
        <span class="emr-bracket-field__bracket" aria-hidden="true">]</span>
      </span>
      <span
        v-else-if="effectiveWidget === 'radio'"
        class="emr-de__radio-group"
        role="radiogroup"
        :aria-label="titleTip"
        @pointerdown.stop
        @mousedown.stop
      >
        <span v-if="!valueStr && visualPlaceholder" class="emr-de__slot-placeholder">{{
          visualPlaceholder
        }}</span>
        <label v-for="opt in selectOptions" :key="opt.value" class="emr-de__radio-item">
          <input
            class="emr-de__radio"
            type="radio"
            :name="radioGroupName"
            :value="opt.value"
            :checked="valueStr === opt.value"
            :disabled="!domEditable"
            @change="onRadioChange(opt.value)"
            @keydown="keydownHandler('radio')"
          />
          <span class="emr-de__radio-label">{{ opt.label }}</span>
        </label>
      </span>
      <span
        v-else-if="effectiveWidget === 'date'"
        class="emr-de-text-bracket emr-de-slot-bracket emr-de-date"
        :class="{ 'emr-de-text-bracket--plain': !valueStr }"
        @pointerdown.stop="openDatePicker"
      >
        <span v-if="valueStr" class="emr-bracket-field__bracket" aria-hidden="true">[</span>
        <span class="emr-de-date__wrap">
          <span
            class="emr-de-date__text"
            :class="{ 'emr-de-date__text--empty': !dateInputValue }"
            >{{ dateInputValue || visualPlaceholder }}</span
          >
          <input
            ref="dateInputRef"
            class="emr-de__control emr-de__control--date emr-de__slot-control emr-de-date__input opacity-0"
            :class="{ 'emr-de__slot-control--empty': !valueStr }"
            type="date"
            :value="dateInputValue"
            :disabled="!domEditable"
            :aria-label="titleTip"
            :title="valueStr ? valueStr : visualPlaceholder || fieldHint"
            @pointerdown.stop="openDatePicker"
            @input="onDateInput"
            @keydown="keydownHandler('date')"
          />
        </span>
        <span v-if="valueStr" class="emr-bracket-field__bracket" aria-hidden="true">]</span>
      </span>
      <label
        v-else-if="effectiveWidget === 'checkbox'"
        class="emr-de__checkbox-wrap"
        @pointerdown.stop
        @mousedown.stop
      >
        <input
          class="emr-de__checkbox"
          type="checkbox"
          :checked="checkboxChecked"
          :disabled="!domEditable"
          :aria-label="titleTip"
          @change="onCheckboxChange"
          @keydown="keydownHandler('checkbox')"
        />
        <span class="emr-de__checkbox-label">{{ labelSnapshot || fieldHint || '选项' }}</span>
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

.emr-de--text,
.emr-de--slot {
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

.emr-de--radio {
  white-space: normal;
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

.emr-de__readonly-val--slot {
  color: #111827;
  min-width: 2ch;
}

/* 文书态：已填值接近打印正文；可填项仍保留浅底/下划线锚点 */
.emr-de--print-like.emr-de--text,
.emr-de--print-like.emr-de--slot {
  margin: 0;
  padding: 0;
  min-width: 2.5em;
  border: none;
  background: transparent;
}

.emr-de--print-like .emr-de__readonly-val {
  color: #111827;
}

.emr-de--print-like .emr-de__text-field {
  color: #111827;
  min-width: 3ch;
}

.emr-de--print-like .emr-de__text-field:empty::before {
  color: #9ca3af;
}

.emr-de--print-like .emr-de__control--textarea {
  border: none;
  background: transparent;
  box-shadow: none;
  padding: 0;
  min-height: 2.5em;
  resize: none;
  overflow: hidden;
  width: 100%;
  max-width: 100%;
  color: #111827;
}

.emr-de--print-like:not(.emr-de--readonly).emr-de--multiline .emr-de__control--textarea {
  min-height: 2.8em;
}

.emr-de--print-like .emr-de__text-field:focus,
.emr-de--print-like .emr-de__slot-control:focus {
  box-shadow: inset 0 -1px 0 0 #0d9488;
  border-radius: 1px;
  outline: none;
  border-bottom-color: transparent;
}

.emr-de--free:not(.emr-de--text):not(.emr-de--slot) {
  border: 1px dashed #cbd5e1;
  background: #f8fafc;
  color: #334155;
}

.emr-de--bound:not(.emr-de--text):not(.emr-de--slot) {
  border: 1px solid transparent;
  background: #f1f5f9;
  color: #1e293b;
}

.emr-de--readonly.emr-de--bound:not(.emr-de--text):not(.emr-de--slot) {
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

/* 空槽聚焦让开占位，避免光标叠在「请在这里填写…」上 */
.emr-de-text-bracket:focus-within .emr-de__text-field::before,
.emr-de__text-field:focus::before,
.emr-de__text-field:focus-visible::before {
  content: none !important;
}

.emr-de-text-bracket:focus-within .emr-de__text-field:empty,
.emr-de__text-field:focus:empty,
.emr-de__text-field:focus-visible:empty {
  min-width: 1ch;
  caret-color: transparent;
}

.emr-de-text-bracket:focus-within .emr-de__text-field:empty::after,
.emr-de__text-field:focus:empty::after,
.emr-de__text-field:focus-visible:empty::after {
  content: '';
  display: inline-block;
  width: 1px;
  height: 1.05em;
  background: #111827;
  vertical-align: text-bottom;
  animation: emr-de-text-caret-blink 1s step-end infinite;
}

@keyframes emr-de-text-caret-blink {
  50% {
    opacity: 0;
  }
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
  display: block;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: 2.5em;
  resize: none;
  overflow: hidden;
  white-space: pre-wrap;
  word-break: break-word;
  box-sizing: border-box;
  field-sizing: content;
}

/* 下拉 / 日期 / 数字：与自由文本同构 —— 方括号 + 透明槽，无原生边框盒 */
.emr-de-slot-bracket {
  display: inline-flex;
  align-items: baseline;
  max-width: 100%;
}

.emr-de__slot-control {
  appearance: none;
  -webkit-appearance: none;
  max-width: 220px;
  min-width: 2ch;
  padding: 0;
  margin: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  color: #111827;
  font: inherit;
  font-size: inherit;
  line-height: inherit;
  vertical-align: baseline;
  cursor: pointer;
}

.emr-de__slot-control--empty,
.emr-de__slot-control::placeholder {
  color: #9ca3af;
}

.emr-de__control--select.emr-de__slot-control {
  /* 给下拉箭头留一点空间，但不画成输入框 */
  padding-right: 0.9em;
  background-image:
    linear-gradient(45deg, transparent 50%, #94a3b8 50%),
    linear-gradient(135deg, #94a3b8 50%, transparent 50%);
  background-position:
    calc(100% - 0.45em) 55%,
    calc(100% - 0.15em) 55%;
  background-size:
    5px 5px,
    5px 5px;
  background-repeat: no-repeat;
}

.emr-de__control--date.emr-de__slot-control {
  max-width: 11em;
  min-width: 7em;
}

.emr-de-date__wrap {
  position: relative;
  display: inline-block;
  min-width: 7em;
  max-width: 11em;
  vertical-align: baseline;
}

.emr-de-date__text {
  font: inherit;
  font-size: inherit;
  line-height: inherit;
  color: #111827;
  white-space: nowrap;
}

.emr-de-date__text--empty {
  color: #9ca3af;
}

.emr-de-date__input {
  position: absolute;
  inset: 0;
  opacity: 0;
  color: transparent;
  -webkit-text-fill-color: transparent;
  caret-color: transparent;
  width: 100%;
  max-width: none;
  min-width: 0;
  height: 100%;
  cursor: pointer;
}

.emr-de-date__input::-webkit-datetime-edit,
.emr-de-date__input::-webkit-datetime-edit-fields-wrapper,
.emr-de-date__input::-webkit-datetime-edit-text,
.emr-de-date__input::-webkit-datetime-edit-year-field,
.emr-de-date__input::-webkit-datetime-edit-month-field,
.emr-de-date__input::-webkit-datetime-edit-day-field {
  color: transparent;
  opacity: 0;
}

.emr-de__control--date.emr-de__slot-control::-webkit-calendar-picker-indicator {
  opacity: 0;
  cursor: pointer;
  padding: 0;
  margin: 0 0 0 2px;
  width: 0.95em;
  height: 0.95em;
}

.emr-de-number__wrap {
  position: relative;
  display: inline-block;
  min-width: 2.5em;
  /* 不要 max-width：占位词比 8em 宽时点击落在透明输入外，字会打进正文（B23） */
  vertical-align: baseline;
}

.emr-de-number__text {
  font: inherit;
  font-size: inherit;
  line-height: inherit;
  color: #111827;
  white-space: nowrap;
}

.emr-de-number__text--empty {
  color: #9ca3af;
}

.emr-de-number__input {
  position: absolute;
  inset: 0;
  opacity: 0;
  width: auto;
  max-width: none;
  min-width: 0;
  height: auto;
  cursor: text;
}

.emr-de__control--number.emr-de__slot-control {
  cursor: text;
  /* 隐藏 number 上下箭头，观感贴近自由文本 */
  -moz-appearance: textfield;
}

.emr-de__control--number.emr-de__slot-control::-webkit-outer-spin-button,
.emr-de__control--number.emr-de__slot-control::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.emr-de__slot-control:focus {
  box-shadow: inset 0 -1px 0 0 #0d9488;
  border-radius: 1px;
  outline: none;
}

.emr-de__control:disabled {
  opacity: 0.88;
  cursor: not-allowed;
}

/* 日期/数字展示走 span；disabled 不能抬透明度，否则 Chrome 会叠出 2026/09/01 */
.emr-de__control.emr-de-date__input:disabled,
.emr-de__control.emr-de-number__input:disabled {
  opacity: 0;
  cursor: default;
}

.emr-de--bound .emr-de__slot-control:focus {
  box-shadow: inset 0 -1px 0 0 #5c6bc0;
}
.emr-de__radio-group {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0 10px;
  max-width: 100%;
  vertical-align: baseline;
}

.emr-de__radio-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin: 0;
  padding: 0;
  cursor: pointer;
  vertical-align: baseline;
}

.emr-de__radio {
  margin: 0;
  flex-shrink: 0;
  width: 0.9em;
  height: 0.9em;
}

.emr-de__radio-label {
  font: inherit;
  font-size: inherit;
  line-height: inherit;
  color: #111827;
}

.emr-de__slot-placeholder {
  margin-right: 6px;
  color: #9ca3af;
  font: inherit;
  font-size: inherit;
  line-height: inherit;
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
