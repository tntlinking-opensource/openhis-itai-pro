<script setup lang="ts">
import { computed } from 'vue';

import { nodeViewProps, NodeViewWrapper } from '@tiptap/vue-3';

import { useEmrIslandDomEditable } from '../use-emr-editor-caps';

const props = defineProps(nodeViewProps);

/** 固定模板书写态仍允许勾选 */
const islandWhenLocked = computed(() => true);
const domEditable = useEmrIslandDomEditable(islandWhenLocked);

/** 已勾：√；未勾：□。不用系统 checkbox，打印与屏幕同一套字 */
const markChar = computed(() => (props.node.attrs.checked ? '√' : '□'));

/**
 * 点整组切换勾选；同一 fieldKey 的纸质组（评估等级/护理等级）互斥。
 * 工具栏插入的默认 fieldKey=checkbox 仍可多选。
 */
function onToggle() {
  if (!domEditable.value) return;
  const checked = !props.node.attrs.checked;
  const selfPos = props.getPos?.();
  if (typeof selfPos !== 'number') return;
  const fieldKey = String(props.node.attrs.fieldKey ?? 'checkbox');
  const { tr } = props.editor.state;
  tr.setNodeMarkup(selfPos, undefined, { ...props.node.attrs, checked });
  const exclusive = checked && fieldKey !== '' && fieldKey !== 'checkbox';
  if (exclusive) {
    props.editor.state.doc.descendants((node, pos) => {
      if (node.type.name !== 'emrCheckboxField') return;
      if (pos === selfPos) return;
      if (String(node.attrs.fieldKey ?? '') !== fieldKey) return;
      if (!node.attrs.checked) return;
      tr.setNodeMarkup(pos, undefined, { ...node.attrs, checked: false });
    });
  }
  props.editor.view.dispatch(tr);
}
</script>

<template>
  <NodeViewWrapper as="span" class="emr-checkbox-field" contenteditable="false">
    <span
      class="emr-checkbox-field__row"
      role="checkbox"
      :aria-checked="!!props.node.attrs.checked"
      :aria-disabled="!domEditable"
      @pointerdown.stop
      @mousedown.stop
      @click.stop="onToggle"
    >
      <span class="emr-checkbox-field__mark">{{ markChar }}</span>
      <span class="emr-checkbox-field__text">{{ props.node.attrs.label || '' }}</span>
    </span>
  </NodeViewWrapper>
</template>

<style scoped>
.emr-checkbox-field {
  display: inline;
  margin: 0 0.15em;
  vertical-align: baseline;
  user-select: none;
}

.emr-checkbox-field__row {
  display: inline;
  cursor: pointer;
  font-size: inherit;
  line-height: inherit;
  white-space: nowrap;
}

.emr-checkbox-field__row[aria-disabled='true'] {
  cursor: default;
}

.emr-checkbox-field__mark,
.emr-checkbox-field__text {
  font: inherit;
  font-size: inherit;
  line-height: inherit;
  color: inherit;
}
</style>
