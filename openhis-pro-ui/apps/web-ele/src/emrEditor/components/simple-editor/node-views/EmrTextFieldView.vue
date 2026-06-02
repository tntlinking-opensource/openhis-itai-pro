<script setup lang="ts">
import { computed } from 'vue';

import { nodeViewProps, NodeViewWrapper } from '@tiptap/vue-3';

import { useEmrIslandDomEditable } from '../use-emr-editor-caps';

const props = defineProps(nodeViewProps);

/** 固定模板时不属「可改元数据/自由文本」范围，保持不可填 */
const islandWhenLocked = computed(() => false);
const domEditable = useEmrIslandDomEditable(islandWhenLocked);
</script>

<template>
  <NodeViewWrapper as="div" class="emr-widget emr-text-field">
    <span v-if="props.node.attrs.label" class="emr-widget__label">{{
      props.node.attrs.label
    }}</span>
    <input
      type="text"
      class="emr-widget__input"
      :value="props.node.attrs.value"
      :disabled="!domEditable"
      @input="
        props.updateAttributes({
          value: ($event.target as HTMLInputElement).value,
        })
      "
    />
  </NodeViewWrapper>
</template>

<style scoped>
.emr-widget {
  margin: 0.6em 0;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fafafa;
}

.emr-widget__label {
  display: block;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}

.emr-widget__input {
  width: 100%;
  box-sizing: border-box;
  padding: 6px 8px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 14px;
  background: #fff;
}
</style>
