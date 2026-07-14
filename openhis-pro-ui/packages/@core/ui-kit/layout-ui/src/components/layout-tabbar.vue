<script setup lang="ts">
import type { CSSProperties } from 'vue';

import { computed } from 'vue';

interface Props {
  /**
   * 高度
   */
  height: number;
}

const props = withDefaults(defineProps<Props>(), {});

const style = computed((): CSSProperties => {
  const { height } = props;
  return {
    '--vben-tabbar-height': `${height}px`,
    flex: '0 0 auto',
    height: `${height}px`,
    minHeight: `${height}px`,
  };
});
</script>

<template>
  <section
    :style="style"
    class="flex w-full shrink-0 items-center overflow-hidden border-b border-border bg-background"
  >
    <slot></slot>
  </section>
</template>
