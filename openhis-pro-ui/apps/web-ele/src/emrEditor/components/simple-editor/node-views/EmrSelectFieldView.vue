<script setup lang="ts">
import { computed } from 'vue';

import { nodeViewProps, NodeViewWrapper } from '@tiptap/vue-3';

import { useEmrIslandDomEditable } from '../use-emr-editor-caps';

const props = defineProps(nodeViewProps);

const islandWhenLocked = computed(() => false);
const domEditable = useEmrIslandDomEditable(islandWhenLocked);

const options = computed(() => {
  try {
    const raw = String(props.node.attrs.optionsJson || '[]');
    const list = JSON.parse(raw) as unknown;
    return Array.isArray(list) ? list.map(String) : [];
  } catch {
    return [] as string[];
  }
});
</script>

<template>
  <NodeViewWrapper as="div" class="emr-widget emr-select-field">
    <span v-if="props.node.attrs.label" class="emr-widget__label">{{
      props.node.attrs.label
    }}</span>
    <select
      class="emr-select-field__select"
      :value="props.node.attrs.value"
      :disabled="!domEditable"
      @change="
        props.updateAttributes({
          value: ($event.target as HTMLSelectElement).value,
        })
      "
    >
      <option v-for="opt in options" :key="opt" :value="opt">{{ opt }}</option>
    </select>
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

.emr-select-field__select {
  width: 100%;
  box-sizing: border-box;
  padding: 6px 8px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 14px;
  background: #fff;
}
</style>
