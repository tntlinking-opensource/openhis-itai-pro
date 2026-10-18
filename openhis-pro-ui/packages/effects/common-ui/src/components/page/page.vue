<script setup lang="ts">
import type { StyleValue } from 'vue';

import type { PageProps } from './types';

import { computed, nextTick, onMounted, ref, useTemplateRef } from 'vue';

import { CSS_VARIABLE_LAYOUT_CONTENT_HEIGHT } from '@zxplat-core/shared/constants';
import { cn } from '@zxplat-core/shared/utils';

defineOptions({
  name: 'Page',
});

const { autoContentHeight = false, heightOffset = 0, tagContent } = defineProps<PageProps>();

const headerHeight = ref(0);
const footerHeight = ref(0);
const shouldAutoHeight = ref(false);

const headerRef = useTemplateRef<HTMLDivElement>('headerRef');
const footerRef = useTemplateRef<HTMLDivElement>('footerRef');

const contentStyle = computed<StyleValue>(() => {
  if (autoContentHeight) {
    return {
      height: `calc(var(${CSS_VARIABLE_LAYOUT_CONTENT_HEIGHT}) - ${headerHeight.value}px - ${footerHeight.value}px - ${typeof heightOffset === 'number' ? `${heightOffset}px` : heightOffset})`,
      overflowY: shouldAutoHeight.value ? 'auto' : 'unset',
    };
  }
  return {};
});

async function calcContentHeight() {
  if (!autoContentHeight) {
    return;
  }
  await nextTick();
  headerHeight.value = headerRef.value?.offsetHeight || 0;
  footerHeight.value = footerRef.value?.offsetHeight || 0;
  setTimeout(() => {
    shouldAutoHeight.value = true;
  }, 30);
}

onMounted(() => {
  calcContentHeight();
});
</script>

<template>
  <div class="bg-background-card relative flex h-full flex-col">
    <div
      v-if="tagContent || $slots.tag"
      class="absolute left-0 top-0 z-10 flex gap-2 rounded-br-xl border-b border-r bg-amber-50 px-2 py-1"
    >
      <slot name="tag">
        <div class="flex items-center">
          <span class="text-sm font-semibold text-gray-800">
            {{ typeof tagContent === 'function' ? tagContent() : tagContent }}
          </span>
        </div>
      </slot>
    </div>
    <div
      v-if="description || $slots.description || title || $slots.title || $slots.extra"
      ref="headerRef"
      :class="cn('bg-card border-border relative flex items-end border-b px-6 py-4', headerClass)"
    >
      <div class="flex flex-auto flex-wrap items-baseline gap-x-3 gap-y-1">
        <slot name="title">
          <div v-if="title" class="text-lg font-semibold">
            {{ title }}
          </div>
        </slot>

        <slot name="description">
          <p v-if="description" class="text-muted-foreground text-sm">
            {{ description }}
          </p>
        </slot>
      </div>

      <div v-if="$slots.extra">
        <slot name="extra"></slot>
      </div>
    </div>

    <div
      :class="cn('h-full overflow-auto p-2 pb-0', contentClass, tagContent || $slots.tag)"
      :style="contentStyle"
    >
      <slot></slot>
    </div>
    <div
      v-if="$slots.footer"
      ref="footerRef"
      :class="cn('bg-card align-center flex justify-center px-6 py-2 pb-4', footerClass)"
    >
      <slot name="footer"></slot>
    </div>
  </div>
</template>
