<!--
  库房/药房域共用右侧主区骨架（仅布局组件，置于 views/inventory/components）。
  可选顶栏 + 默认主体 + 可选底部单据操作条；toolbar / actionBar 为具名插槽。
-->
<script setup lang="ts">
import { useSlots } from 'vue';

defineOptions({ name: 'InvMainShell' });

const slots = useSlots();
</script>

<template>
  <div
    class="inv-main-shell flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-[var(--el-bg-color)]"
  >
    <div
      v-if="slots.toolbar"
      class="inv-main-shell__toolbar shrink-0 border-b border-[var(--el-border-color-extra-light)] px-2 py-1.5"
    >
      <slot name="toolbar" />
    </div>

    <div class="inv-main-shell__body flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <slot />
    </div>

    <div
      v-if="slots.actionBar"
      class="inv-main-shell__action-bar flex shrink-0 flex-wrap items-center justify-center gap-2 bg-[var(--el-fill-color-blank)] px-2 py-2"
    >
      <slot name="actionBar" />
    </div>
  </div>
</template>
