<script setup lang="ts">
import { cn as mergeClassNames } from '@zxplat-core/shared/utils';

const props = defineProps<{
  class?: any;
}>();
</script>

<template>
  <div
    :class="
      mergeClassNames('rounded-xl border border-border bg-card text-card-foreground', props.class)
    "
  >
    <slot></slot>
  </div>
</template>
