<script setup lang="ts">
import type { ComputedRef } from 'vue';

import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { nodeViewProps, NodeViewWrapper } from '@tiptap/vue-3';

import {
  fillableNodeExtraClass,
  isFillableAtomValueEmpty,
  resolveFillableFieldKind,
  resolveFillableVisualPlaceholder,
} from './fillable-field-view.shared';
import { stripFillablePlaceholders } from '../emr-fillable-json';
import {
  contentEditableCaretOffset,
  clearPinnedIslandCaret,
  insertPlainTextAtCaret,
  peekPinnedIslandCaret,
  pinIslandCaret,
  readContentEditablePlainText,
  setContentEditableCaret,
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

defineOptions({ name: 'EmrFillableFieldView' });

const props = defineProps(nodeViewProps);

const kind = computed(() => resolveFillableFieldKind(props.node));
const valueStr = computed(() => String(props.node.attrs.value ?? ''));
const editable = computed(() => props.node.attrs.editable !== false);
const fieldKey = computed(() => String(props.node.attrs.fieldKey ?? ''));
const label = computed(() =>
  String(props.node.attrs.label ?? props.node.attrs.labelSnapshot ?? ''),
);

const surfaceInject = inject<ComputedRef<'document' | 'template'>>('emrEditorSurface');
const surface = computed(() => surfaceInject?.value ?? 'document');

const visualPlaceholder = computed(() =>
  resolveFillableVisualPlaceholder(props.node.attrs, surface.value, kind.value, '填写'),
);

const isEmpty = computed(() => isFillableAtomValueEmpty(props.node.attrs));
const isRequired = computed(() => {
  const r = props.node.attrs.required;
  return r === true || r === '1' || r === 'true';
});
const isRequiredEmpty = computed(() => isRequired.value && editable.value && isEmpty.value);

const titleTip = computed(() => {
  if (kind.value === 'metadata') {
    const mode = editable.value ? '可编辑' : '随患者信息更新';
    return `元数据 ${fieldKey.value || label.value} · ${mode}`;
  }
  if (kind.value === 'freeText') {
    return `自由文本 · ${label.value || '填写'}`;
  }
  return label.value || '填写';
});

/** 文书锁下仍允许编辑的岛屿 */
const islandWhenLocked = computed(() => {
  if (kind.value === 'freeText') return true;
  return editable.value;
});
const domEditable = useEmrIslandDomEditable(islandWhenLocked);

const textFieldRef = ref<HTMLElement | null>(null);
const focused = ref(false);

const wrapperClass = computed(() => {
  const base = fillableNodeExtraClass(props.node, kind.value);
  const state = [
    base,
    isEmpty.value ? 'emr-fillable--empty' : '',
    focused.value ? 'emr-fillable--active is-active' : '',
    isRequiredEmpty.value ? 'emr-fillable--required-empty' : '',
    !editable.value ? 'emr-fillable--readonly' : '',
  ]
    .filter(Boolean)
    .join(' ');
  return state;
});

/** 外部 attrs 变更时同步 DOM（撤销/重做后即使聚焦也须对齐） */
function syncTextFieldFromAttrs() {
  nextTick(() => {
    const el = textFieldRef.value;
    if (!el || props.editor.view?.composing) return;
    const next = valueStr.value;
    const dom = readContentEditablePlainText(el);
    if (dom === next) return;
    // 聚焦输入中 attrs 常落后 DOM 一拍，勿用旧值覆盖（符号/快打会被吃掉）
    if (document.activeElement === el) return;
    const pinned = peekPinnedIslandCaret(el);
    const caret = pinned ?? -1;
    el.textContent = next;
    if (pinned != null && caret >= 0) {
      setContentEditableCaretOffset(el, Math.min(caret, next.length));
    }
  });
}

/** 输入回写 attrs.value（剥掉历史 ZWSP，避免落库） */
function onTextFieldInput() {
  const el = textFieldRef.value;
  if (!el) return;
  // 短钉光标：分页若刚好重挂岛屿垫片，用当前偏移恢复，避免飞文末
  const caret = contentEditableCaretOffset(el);
  if (caret >= 0) pinIslandCaret(el, caret, 500);
  else clearPinnedIslandCaret(el);
  props.updateAttributes({ value: stripFillablePlaceholders(readContentEditablePlainText(el)) });
}

/** 粘贴纯文本 */
function onTextFieldPaste(e: ClipboardEvent) {
  e.preventDefault();
  const t = e.clipboardData?.getData('text/plain') ?? '';
  insertPlainTextAtCaret(t);
  onTextFieldInput();
}

/** capture：删键 bubble 前选区可能已折叠，先快照拖选范围 */
function onTextFieldKeydownCapture(e: KeyboardEvent) {
  if (e.key !== 'Backspace' && e.key !== 'Delete') return;
  if (e.ctrlKey || e.metaKey || e.altKey) return;
  captureIslandDeleteSelection(textFieldRef.value);
}

/** 键盘：Enter 跳下一项、方向键串联；批量删见 beforeinput / keydown */
function onTextFieldKeydown(e: KeyboardEvent) {
  const pos = props.getPos?.();
  if (typeof pos !== 'number') return;
  handleInlineFieldKeydown(props.editor, pos, e, 'contentEditable');
}

/** beforeinput：换行兜底 + 拖选删 / 有垫片时的折叠删 */
function onTextFieldBeforeInput(e: InputEvent) {
  const pos = props.getPos?.();
  if (typeof pos !== 'number') return;
  const el = textFieldRef.value;
  if (!el?.isContentEditable) return;
  if (handleIslandContentEditableBeforeInputText(el, e, onTextFieldInput)) {
    return;
  }
  const t = e.inputType;
  // 部分环境 Enter 只走 beforeinput（insertParagraph / insertLineBreak），不经 keydown 默认可插 \n
  if (t === 'insertParagraph' || t === 'insertLineBreak') {
    if (insertNewlineInContentEditableIsland(props.editor, pos, el)) {
      e.preventDefault();
      e.stopPropagation();
    }
    return;
  }
  if (handleIslandContentEditableBeforeInputDelete(props.editor, pos, el, e)) {
    return;
  }
}

/** 按住微移时 PM 会拖选 atom 抢焦；松手/微移阈值内把焦点按回岛屿 */
const CLICK_DRAG_SLOP_PX = 8;
let pressOrigin: null | { x: number; y: number } = null;
let pressCleanup: null | (() => void) = null;

function clearPressTracking() {
  pressCleanup?.();
  pressCleanup = null;
  pressOrigin = null;
}

function focusIslandInput() {
  const el = textFieldRef.value;
  if (!el || !domEditable.value) return;
  if (document.activeElement !== el) {
    el.focus({ preventScroll: true });
  }
  focused.value = true;
}

/**
 * mousedown 后跟踪 mouseup/mousemove：
 * - 位移在阈值内视为「点击」，松手强制回焦
 * - 空槽 preventDefault，避免默认拖选启动
 */
function armPressFocusCompensation(e: MouseEvent, opts?: { preventDefaultForEmpty?: boolean }) {
  if (e.button !== 0) return;
  e.stopPropagation();
  if (opts?.preventDefaultForEmpty && isEmpty.value) {
    e.preventDefault();
  }

  clearPressTracking();
  pressOrigin = { x: e.clientX, y: e.clientY };
  focusIslandInput();

  const onMove = (ev: MouseEvent) => {
    if (!pressOrigin) return;
    const dx = Math.abs(ev.clientX - pressOrigin.x);
    const dy = Math.abs(ev.clientY - pressOrigin.y);
    // 仅微移补偿：大范围拖选留给 contenteditable 自己
    if (dx > CLICK_DRAG_SLOP_PX || dy > CLICK_DRAG_SLOP_PX) return;
    const el = textFieldRef.value;
    if (el && document.activeElement !== el) {
      focusIslandInput();
    }
  };

  const onUp = (ev: MouseEvent) => {
    const origin = pressOrigin;
    clearPressTracking();
    if (!origin) return;
    const dx = Math.abs(ev.clientX - origin.x);
    const dy = Math.abs(ev.clientY - origin.y);
    const el = textFieldRef.value;
    const upInside = Boolean(el && (ev.target === el || el.contains(ev.target as Node)));
    // 微移误拖，或松手仍在本槽内：回焦
    if ((dx <= CLICK_DRAG_SLOP_PX && dy <= CLICK_DRAG_SLOP_PX) || upInside) {
      requestAnimationFrame(() => {
        focusIslandInput();
      });
    }
  };

  window.addEventListener('mousemove', onMove, true);
  window.addEventListener('mouseup', onUp, true);
  pressCleanup = () => {
    window.removeEventListener('mousemove', onMove, true);
    window.removeEventListener('mouseup', onUp, true);
  };
}

/** 点击括号区时聚焦槽内；正文区点击交给浏览器落点 */
function onShellMouseDown(e: MouseEvent) {
  if (!domEditable.value) return;
  const target = e.target;
  if (!(target instanceof Element)) return;
  if (target.closest('.emr-fillable-field__input')) return;
  armPressFocusCompensation(e, { preventDefaultForEmpty: true });
  const el = textFieldRef.value;
  if (el) setContentEditableCaret(el, 'start');
}

/**
 * 阻止冒泡到 PM；微移拖选时用补偿回焦。
 * 禁止在此改 textContent（会触发 atom NodeView 重建）。
 */
function onInputMouseDown(e: MouseEvent) {
  if (!domEditable.value) return;
  clearPinnedIslandCaret(textFieldRef.value ?? undefined);
  armPressFocusCompensation(e, { preventDefaultForEmpty: true });
}

function onInputDragStart(e: DragEvent) {
  // atom 可被 PM 拖选；岛屿内禁止原生/编辑器拖拽启动
  e.preventDefault();
  e.stopPropagation();
}

function onTextFieldFocus() {
  focused.value = true;
}

function onTextFieldBlur() {
  focused.value = false;
  // 点工具栏「保存」时焦点已离开，须在 blur 当帧把 DOM 字写入 attrs
  onTextFieldInput();
}

onMounted(() => {
  syncTextFieldFromAttrs();
});

onBeforeUnmount(() => {
  clearPressTracking();
});

watch(valueStr, () => {
  syncTextFieldFromAttrs();
});
</script>

<template>
  <NodeViewWrapper as="span" :class="wrapperClass" :title="titleTip" data-node-view-wrapper="">
    <span
      v-if="!editable"
      class="emr-fillable-field__shell emr-fillable-field__shell--readonly"
      :class="{
        'emr-fillable-field__shell--empty': isEmpty,
        'emr-fillable-field__shell--plain': kind === 'metadata',
      }"
    >
      <span class="emr-fillable-field__readonly-text" :data-placeholder="visualPlaceholder">{{
        valueStr
      }}</span>
    </span>

    <span
      v-else
      class="emr-fillable-field__shell"
      :class="{ 'emr-fillable-field__shell--plain': kind === 'metadata' }"
      @mousedown="onShellMouseDown"
    >
      <span
        ref="textFieldRef"
        role="textbox"
        class="emr-inline-text__field emr-fillable-field__input"
        tabindex="0"
        spellcheck="false"
        :contenteditable="domEditable ? 'true' : 'false'"
        :data-placeholder="visualPlaceholder"
        :aria-label="titleTip"
        @mousedown="onInputMouseDown"
        @dragstart="onInputDragStart"
        @input="onTextFieldInput"
        @beforeinput="onTextFieldBeforeInput"
        @paste="onTextFieldPaste"
        @keydown.capture="onTextFieldKeydownCapture"
        @keydown="onTextFieldKeydown"
        @focus="onTextFieldFocus"
        @blur="onTextFieldBlur"
      ></span>
    </span>
  </NodeViewWrapper>
</template>

<style scoped>
.emr-fillable-field__shell {
  display: inline;
  vertical-align: baseline;
  box-decoration-break: clone;
  -webkit-box-decoration-break: clone;
  white-space: normal;
  font: inherit;
  font-size: inherit;
  line-height: inherit;
}

.emr-fillable-field__shell::before {
  content: '[';
  color: #93c5fd;
  font-weight: 600;
  user-select: none;
  cursor: text;
}

.emr-fillable-field__shell::after {
  content: ']';
  color: #93c5fd;
  font-weight: 600;
  user-select: none;
}

/* 元数据：不画蓝括号（贴近旧版门诊病历、省横向宽度） */
.emr-fillable-field__shell--plain::before,
.emr-fillable-field__shell--plain::after {
  content: none !important;
}

.emr-fillable-field__input {
  display: inline;
  min-width: 0.5em;
  outline: none;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
  font: inherit;
  font-size: inherit;
  line-height: inherit;
  vertical-align: baseline;
  color: #111827;
  caret-color: #111827;
}

.emr-fillable-field__input:empty::before {
  content: attr(data-placeholder);
  color: #9ca3af;
  font-weight: 400;
  pointer-events: none;
}

/* 空槽聚焦：去掉占位，用 CSS 假光标（不写 DOM，避免 NodeView 重建） */
.emr-fillable-field__input:focus:empty::before,
.emr-fillable-field__input:focus-visible:empty::before {
  content: none;
}

.emr-fillable-field__input:focus:empty,
.emr-fillable-field__input:focus-visible:empty {
  min-width: 1ch;
}

.emr-fillable-field__input:focus:empty::after,
.emr-fillable-field__input:focus-visible:empty::after {
  content: '';
  display: inline-block;
  width: 1px;
  height: 1.05em;
  background: #111827;
  vertical-align: text-bottom;
  animation: emr-fillable-caret-blink 1s step-end infinite;
}

@keyframes emr-fillable-caret-blink {
  50% {
    opacity: 0;
  }
}

.emr-fillable-field__readonly-text {
  display: inline;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
  color: #64748b;
}

.emr-fillable-field__readonly-text:empty::before {
  content: attr(data-placeholder);
  color: #9ca3af;
  font-weight: 400;
  pointer-events: none;
}
</style>
