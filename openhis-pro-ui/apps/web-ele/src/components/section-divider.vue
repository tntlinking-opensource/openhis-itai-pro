<script setup lang="ts">
import { computed } from 'vue';

import { ChevronDown } from '@vben/icons';

interface Props {
  title?: string;
  label?: string;
  /** 根节点栅格类名（表单列数变化时需整行拉满时用 col-span-full） */
  rootClass?: string;
  /** 分区标题条更矮、字号更小，节省纵向空间 */
  compact?: boolean;
  collapsible?: boolean;
  expanded?: boolean;
  onToggle?: (expanded: boolean) => void;
}

const props = withDefaults(defineProps<Props>(), {
  title: undefined,
  label: undefined,
  rootClass: 'col-span-3 w-full',
  compact: false,
  collapsible: false,
  expanded: true,
  onToggle: undefined,
});

const emit = defineEmits<{
  'update:expanded': [value: boolean];
}>();

const displayTitle = computed(() => props.title || props.label || '');

const isExpanded = computed({
  get: () => props.expanded,
  set: (value) => {
    emit('update:expanded', value);
    props.onToggle?.(value);
  },
});

function handleToggle() {
  if (props.collapsible) {
    isExpanded.value = !isExpanded.value;
  }
}
</script>

<template>
  <div :class="rootClass">
    <div
      class="flex items-center bg-gray-100"
      :class="[
        compact ? 'h-8 px-2' : 'h-10 px-4',
        collapsible && 'cursor-pointer hover:bg-gray-200',
      ]"
      @click="handleToggle"
    >
      <span class="flex-1 font-semibold text-gray-900" :class="compact ? 'text-xs' : 'text-base'">
        {{ displayTitle }}
      </span>
      <div
        v-if="collapsible"
        class="transition-transform duration-200"
        :class="[isExpanded && 'rotate-180']"
      >
        <ChevronDown class="size-4 text-gray-600" />
      </div>
    </div>
  </div>
</template>
