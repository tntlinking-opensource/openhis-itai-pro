<script setup lang="ts">
import type { ComputedRef } from 'vue';

import { computed, inject, nextTick, onMounted, ref, watch } from 'vue';

import { nodeViewProps, NodeViewWrapper } from '@tiptap/vue-3';

import {
  insertPlainTextAtCaret,
  readContentEditablePlainText,
} from '../inline-contenteditable-caret';
import { useEmrIslandDomEditable } from '../use-emr-editor-caps';
import { handleInlineFieldKeydown } from '../use-inline-field-keydown';

const props = defineProps(nodeViewProps);

const fieldKey = computed(() => String(props.node.attrs.fieldKey ?? ''));
const label = computed(() => String(props.node.attrs.label ?? ''));
const editable = computed(() => props.node.attrs.editable !== false);
const value = computed(() => String(props.node.attrs.value ?? ''));

const surfaceInject = inject<ComputedRef<'document' | 'template'>>('emrEditorSurface');
const surface = computed(() => surfaceInject?.value ?? 'document');
/** 模板 / 文书均不展示蓝括号，绑定名见 title */
const showBrackets = computed(() => false);

const displayValue = computed(() => value.value);

const isSignatureImage = computed(() => value.value.trim().startsWith('data:image/'));

const islandWhenLocked = computed(() => editable.value);
const domEditable = useEmrIslandDomEditable(islandWhenLocked);

const textFieldRef = ref<HTMLElement | null>(null);

const titleTip = computed(() => {
  const k = fieldKey.value;
  const l = label.value;
  const mode = editable.value ? '可编辑' : '随患者信息更新';
  if (k && l) return `字段 ${k}（${l}）· ${mode}`;
  return k ? `字段 ${k} · ${mode}` : l || '元数据字段';
});

const visualPlaceholder = computed(() =>
  surface.value === 'template' ? '' : label.value || fieldKey.value || '填写',
);

function syncDomFromAttrs() {
  nextTick(() => {
    const el = textFieldRef.value;
    if (!el || document.activeElement === el) return;
    const next = value.value;
    if (readContentEditablePlainText(el) !== next) {
      el.textContent = next;
    }
  });
}

function onEditableInput() {
  const el = textFieldRef.value;
  if (!el) return;
  props.updateAttributes({ value: readContentEditablePlainText(el) });
}

function onPaste(e: ClipboardEvent) {
  e.preventDefault();
  const t = e.clipboardData?.getData('text/plain') ?? '';
  insertPlainTextAtCaret(t);
  onEditableInput();
}

function onKeydown(e: KeyboardEvent) {
  const pos = props.getPos?.();
  if (typeof pos !== 'number') return;
  handleInlineFieldKeydown(props.editor, pos, e, 'contentEditable');
}

onMounted(() => {
  syncDomFromAttrs();
});

watch(value, () => {
  syncDomFromAttrs();
});
</script>

<template>
  <NodeViewWrapper
    as="span"
    class="emr-metadata-chip emr-bracket-field"
    :class="{
      'emr-metadata-chip--readonly': !editable,
      'emr-metadata-chip--plain': !showBrackets,
    }"
    data-node-view-wrapper=""
    :title="titleTip"
  >
    <span v-if="showBrackets" class="emr-bracket-field__bracket" aria-hidden="true">[</span>
    <span
      v-if="editable"
      ref="textFieldRef"
      role="textbox"
      class="emr-inline-text__field emr-bracket-field__body emr-metadata-chip__text"
      tabindex="0"
      spellcheck="false"
      :data-placeholder="visualPlaceholder"
      :contenteditable="domEditable ? 'true' : 'false'"
      :aria-label="titleTip"
      @input="onEditableInput"
      @paste="onPaste"
      @keydown="onKeydown"
    ></span>
    <img
      v-else-if="isSignatureImage"
      :src="value"
      alt=""
      class="emr-metadata-chip__signature-img"
      :aria-label="titleTip"
    />
    <span v-else class="emr-metadata-chip__value" :aria-label="titleTip">{{ displayValue }}</span>
    <span v-if="showBrackets" class="emr-bracket-field__bracket" aria-hidden="true">]</span>
  </NodeViewWrapper>
</template>

<style scoped>
.emr-metadata-chip.emr-bracket-field {
  display: inline-flex;
  box-decoration-break: clone;
  align-items: baseline;
  max-width: 100%;
  margin: 0 1px;
  padding: 0;
  border: none;
  background: transparent;
  font: inherit;
  font-size: inherit;
  line-height: inherit;
  vertical-align: baseline;
  white-space: normal;
  box-sizing: border-box;
  min-width: 2.5em;
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

.emr-metadata-chip__signature-img {
  display: inline-block;
  max-height: 1.5em;
  max-width: 120px;
  vertical-align: middle;
  object-fit: contain;
}

.emr-metadata-chip__value {
  display: inline-block;
  max-width: 100%;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
  vertical-align: baseline;
  color: #111827;
  min-width: 2ch;
  min-height: 1em;
}

.emr-metadata-chip__text {
  display: inline-block;
  min-width: 2ch;
  max-width: 100%;
  outline: none;
  cursor: text;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
  vertical-align: baseline;
  color: #111827;
}

.emr-metadata-chip__text:empty::before {
  content: attr(data-placeholder);
  color: #9ca3af;
  pointer-events: none;
}

.emr-metadata-chip__text:focus {
  box-shadow: inset 0 -1px 0 0 #5c6bc0;
  border-radius: 1px;
}

.emr-metadata-chip__text[contenteditable='false'] {
  opacity: 0.88;
  cursor: not-allowed;
}
</style>
