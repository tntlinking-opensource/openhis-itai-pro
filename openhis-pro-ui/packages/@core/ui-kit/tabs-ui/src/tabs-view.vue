<script setup lang="ts">
import type { TabsEmits, TabsProps } from './types';

import { useForwardPropsEmits } from '@zxplat-core/composables';
import { ChevronLeft, ChevronRight } from '@zxplat-core/icons';
import { VbenScrollbar } from '@zxplat-core/shadcn-ui';

import { Tabs, TabsChrome } from './components';
import { useTabsDrag } from './use-tabs-drag';
import { useTabsViewScroll } from './use-tabs-view-scroll';

interface Props extends TabsProps {}

defineOptions({
  name: 'TabsView',
});

const props = withDefaults(defineProps<Props>(), {
  contentClass: 'vben-tabs-content',
  draggable: true,
  styleType: 'chrome',
  wheelable: true,
});

const emit = defineEmits<TabsEmits>();

const forward = useForwardPropsEmits(props, emit);

const {
  handleScrollAt,
  handleWheel,
  scrollbarRef,
  scrollDirection,
  scrollIsAtLeft,
  scrollIsAtRight,
  showScrollButton,
} = useTabsViewScroll(props);

function onWheel(e: WheelEvent) {
  if (props.wheelable) {
    handleWheel(e);
    e.stopPropagation();
    e.preventDefault();
  }
}

useTabsDrag(props, emit);
</script>

<template>
  <div
    class="flex min-w-0 flex-1 items-center overflow-hidden"
    :style="{ height: 'var(--vben-tabbar-height, 38px)' }"
  >
    <!-- 左侧滚动按钮 -->
    <span
      v-show="showScrollButton"
      :class="{
        'cursor-pointer text-muted-foreground hover:bg-muted': !scrollIsAtLeft,
        'pointer-events-none opacity-30': scrollIsAtLeft,
      }"
      class="flex items-center self-stretch border-r px-2"
      @click="scrollDirection('left')"
    >
      <ChevronLeft class="size-4" />
    </span>

    <div
      :class="{
        'pt-[3px]': styleType === 'chrome',
      }"
      class="flex h-full min-w-0 flex-1 items-center overflow-hidden"
    >
      <VbenScrollbar
        ref="scrollbarRef"
        :shadow-bottom="false"
        :shadow-top="false"
        class="h-full w-full"
        horizontal
        scroll-bar-class="z-10 hidden "
        shadow
        shadow-left
        shadow-right
        @scroll-at="handleScrollAt"
        @wheel="onWheel"
      >
        <TabsChrome v-if="styleType === 'chrome'" v-bind="{ ...forward, ...$attrs, ...$props }" />

        <Tabs v-else v-bind="{ ...forward, ...$attrs, ...$props }" />
      </VbenScrollbar>
    </div>

    <!-- 右侧滚动按钮 -->
    <span
      v-show="showScrollButton"
      :class="{
        'cursor-pointer text-muted-foreground hover:bg-muted': !scrollIsAtRight,
        'pointer-events-none opacity-30': scrollIsAtRight,
      }"
      class="flex cursor-pointer items-center self-stretch border-l px-2 text-muted-foreground hover:bg-muted"
      @click="scrollDirection('right')"
    >
      <ChevronRight class="size-4" />
    </span>
  </div>
</template>
