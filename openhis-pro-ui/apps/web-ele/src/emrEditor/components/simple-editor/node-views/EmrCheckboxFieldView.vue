<script setup lang="ts">
import { computed } from 'vue';

import { nodeViewProps, NodeViewWrapper } from '@tiptap/vue-3';

import { useEmrIslandDomEditable } from '../use-emr-editor-caps';

const props = defineProps(nodeViewProps);

const islandWhenLocked = computed(() => false);
const domEditable = useEmrIslandDomEditable(islandWhenLocked);
</script>

<template>
  <NodeViewWrapper as="div" class="emr-widget emr-checkbox-field">
    <label class="emr-checkbox-field__row">
      <input
        type="checkbox"
        class="emr-checkbox-field__box"
        :checked="!!props.node.attrs.checked"
        :disabled="!domEditable"
        @change="
          props.updateAttributes({
            checked: ($event.target as HTMLInputElement).checked,
          })
        "
      />
      <span class="emr-checkbox-field__text">{{ props.node.attrs.label || '复选框' }}</span>
    </label>
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

.emr-checkbox-field__row {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
}

.emr-checkbox-field__box {
  width: 16px;
  height: 16px;
}
</style>
