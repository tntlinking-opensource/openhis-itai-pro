<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { nodeViewProps, NodeViewWrapper } from '@tiptap/vue-3';

import {
  insertPlainTextAtCaret,
  readContentEditablePlainText,
} from '../inline-contenteditable-caret';
import { useEmrIslandDomEditable } from '../use-emr-editor-caps';
import { handleInlineFieldKeydown } from '../use-inline-field-keydown';

const props = defineProps(nodeViewProps);

const islandWhenLocked = computed(() => true);
const domEditable = useEmrIslandDomEditable(islandWhenLocked);

const displayLabel = computed(
  () => String(props.node.attrs.label ?? props.node.attrs.placeholder ?? '填写') || '填写',
);
const value = computed(() => String(props.node.attrs.value ?? ''));

const textFieldRef = ref<HTMLElement | null>(null);

const titleTip = computed(() => `自由文本（不绑定患者字段）· ${displayLabel.value || '可编辑'}`);

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
    class="emr-free-text emr-bracket-field"
    data-node-view-wrapper=""
    :title="titleTip"
  >
    <span class="emr-bracket-field__bracket" aria-hidden="true">[</span>
    <span
      ref="textFieldRef"
      role="textbox"
      class="emr-inline-text__field emr-bracket-field__body emr-free-text__text"
      tabindex="0"
      spellcheck="false"
      :data-placeholder="displayLabel"
      :contenteditable="domEditable ? 'true' : 'false'"
      :aria-label="titleTip"
      @input="onEditableInput"
      @paste="onPaste"
      @keydown="onKeydown"
    ></span>
    <span class="emr-bracket-field__bracket" aria-hidden="true">]</span>
  </NodeViewWrapper>
</template>

<style scoped>
.emr-free-text.emr-bracket-field {
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

.emr-free-text__text {
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

.emr-free-text__text:not(:empty) {
  color: #64748b;
}

.emr-free-text__text:empty::before {
  content: attr(data-placeholder);
  color: #9ca3af;
  pointer-events: none;
}

.emr-free-text__text:focus {
  box-shadow: inset 0 -1px 0 0 #0d9488;
  border-radius: 1px;
}

.emr-free-text__text[contenteditable='false'] {
  opacity: 0.88;
  cursor: not-allowed;
}
</style>
