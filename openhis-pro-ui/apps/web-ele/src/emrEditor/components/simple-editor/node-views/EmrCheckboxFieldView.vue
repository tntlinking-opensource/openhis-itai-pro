<script setup lang="ts">
import { computed } from 'vue';

import { nodeViewProps, NodeViewWrapper } from '@tiptap/vue-3';

import { useEmrIslandDomEditable } from '../use-emr-editor-caps';

const props = defineProps(nodeViewProps);

/** 固定模板书写态仍允许勾选 */
const islandWhenLocked = computed(() => true);
const domEditable = useEmrIslandDomEditable(islandWhenLocked);
</script>

<template>
  <NodeViewWrapper as="span" class="emr-checkbox-field" contenteditable="false">
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
      <span class="emr-checkbox-field__text">{{ props.node.attrs.label || '' }}</span>
    </label>
  </NodeViewWrapper>
</template>

<style scoped>
.emr-checkbox-field {
  display: inline-flex;
  margin: 0 0.15em;
  vertical-align: baseline;
  user-select: none;
}

.emr-checkbox-field__row {
  display: inline-flex;
  align-items: center;
  gap: 0.15em;
  cursor: pointer;
  font-size: inherit;
  line-height: inherit;
}

.emr-checkbox-field__box {
  width: 0.95em;
  height: 0.95em;
  margin: 0;
  flex-shrink: 0;
  cursor: pointer;
}

.emr-checkbox-field__box:disabled {
  cursor: default;
}

.emr-checkbox-field__text {
  white-space: nowrap;
}
</style>
