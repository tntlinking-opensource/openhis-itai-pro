<script setup lang="ts">
import type { JSONContent } from '@tiptap/core';
import type { IRange } from '@hufe921/canvas-editor';

import { markRaw, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import CanvasEmrEditor from '#/emr-canvas/components/CanvasEmrEditor.vue';
import { stationDocToEditorData, wrapCanvasPayload } from '#/emr-canvas/station-bridge';

/** 外挂高度链：院点 override 生产包 scoped 会丢 */
import './canvas-station-editor.css';

defineOptions({ name: 'CanvasStationEditor' });

interface Props {
  modelValue?: JSONContent | null;
  metadataValues?: Record<string, string>;
  readOnly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  metadataValues: () => ({}),
  readOnly: false,
});

const emit = defineEmits<{
  change: [];
  'update:modelValue': [JSONContent];
}>();

const innerRef = ref<InstanceType<typeof CanvasEmrEditor> | null>(null);
const skipModelWatch = ref(false);
/** 打字只记脏，不把整篇文书拷进 v-model。页数多时每次 getValue 会明显卡。 */
const dirty = ref(false);
let lastAppliedModelFp = '';

function fingerprintModelValue(doc: unknown): string {
  try {
    return JSON.stringify(doc ?? null);
  } catch {
    return '';
  }
}

/** 当前纸面打成存盘信封。顺手回写 Tab，但打字路径不会走到这里。 */
function getJSON(): JSONContent {
  const viewport = innerRef.value?.holdViewport?.() ?? innerRef.value?.captureScrollPosition?.();
  try {
    const data = innerRef.value?.getData?.() ?? stationDocToEditorData(props.modelValue);
    const payload = wrapCanvasPayload(data) as unknown as JSONContent;
    skipModelWatch.value = true;
    lastAppliedModelFp = fingerprintModelValue(payload);
    emit('update:modelValue', markRaw(payload));
    void nextTick(() => {
      skipModelWatch.value = false;
    });
    return payload;
  } finally {
    innerRef.value?.releaseViewport?.(viewport);
  }
}

/** 与 SimpleEditor.isDirty 对齐，供未保存守卫。比较整篇 JSON 会随页数变慢，打字只看脏标记。 */
function isDirty(): boolean {
  return dirty.value;
}

/** 保存成功后清脏 */
function markSaved() {
  dirty.value = false;
}

function clearDraft() {
  markSaved();
}

/** 带入患者/就诊元数据 */
function applyMetadata() {
  innerRef.value?.applyMetadata?.();
}

/** 诊断空槽：只填尚无值的 conceptId */
function fillEmptyDiagnosisMetadata(values: Record<string, string>) {
  innerRef.value?.applyHostMetadata?.(values, false);
}

function validateRequired() {
  return true;
}

function exitPageChromeEdit() {
  /* Canvas 页眉在编辑器内切换，无独立侧车退出 */
}

function getEditor() {
  return null;
}

/** 交给内层 Canvas 编辑器打印 */
function printDocument() {
  return innerRef.value?.printDocument?.();
}

/** 快照 canvas 选区，供插入诊断/医嘱弹窗还原落点 */
function captureInsertRange() {
  return innerRef.value?.captureInsertRange?.() ?? null;
}

/** 在光标/冻结选区插入诊断、医嘱纯文本 */
function insertPlainText(text: string, range?: IRange | null) {
  return innerRef.value?.insertPlainText?.(text, range) === true;
}

/** 画布内容变化：只标脏。保存和打印走 getJSON，不在每个字上序列化全文。 */
function onInnerChange() {
  dirty.value = true;
  emit('change');
}

watch(
  () => props.modelValue,
  (next) => {
    const fp = fingerprintModelValue(next);
    if (fp === lastAppliedModelFp) return;
    lastAppliedModelFp = fp;
    if (skipModelWatch.value) return;
    innerRef.value?.setData?.(stationDocToEditorData(next));
  },
);

onBeforeUnmount(() => {
  if (!dirty.value) return;
  getJSON();
});

onMounted(() => {
  void nextTick(() => markSaved());
});

onActivated(() => {
  innerRef.value?.restoreScrollPosition?.();
});

defineExpose({
  getJSON,
  isDirty,
  markSaved,
  clearDraft,
  applyMetadata,
  fillEmptyDiagnosisMetadata,
  validateRequired,
  exitPageChromeEdit,
  getEditor,
  captureInsertRange,
  insertPlainText,
  printDocument,
  captureScrollPosition: () => innerRef.value?.captureScrollPosition?.() ?? null,
  restoreScrollPosition: (pos?: unknown) => innerRef.value?.restoreScrollPosition?.(pos as never),
  holdViewport: () => innerRef.value?.holdViewport?.() ?? innerRef.value?.captureScrollPosition?.() ?? null,
  releaseViewport: (pos?: unknown) => innerRef.value?.releaseViewport?.(pos as never),
  resetViewport: () => innerRef.value?.resetViewport?.(),
});
</script>

<template>
  <CanvasEmrEditor
    ref="innerRef"
    class="canvas-station-editor"
    :model-value="stationDocToEditorData(modelValue)"
    :metadata-values="metadataValues"
    :read-only="readOnly"
    :auto-apply-metadata="true"
    :show-metadata-sidebar="false"
    @change="onInnerChange"
  >
    <template v-if="$slots.print" #print>
      <slot name="print" />
    </template>
    <template v-if="$slots.host" #host>
      <slot name="host" />
    </template>
  </CanvasEmrEditor>
</template>

