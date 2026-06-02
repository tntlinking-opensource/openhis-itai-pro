<!--
  库房/药房域共用侧栏骨架（仅布局组件，置于 views/inventory/components）。
  顶区检索 + 中部单据队列滚动 + 底区操作；业务通过 header / queue / footer 插槽填入。
-->
<script setup lang="ts">
import { useSlots } from 'vue';

defineOptions({ name: 'InvAsideShell' });

const slots = useSlots();
</script>

<template>
  <div class="inv-aside-shell flex h-full min-h-0 w-full flex-col">
    <div v-if="slots.header" class="inv-aside-shell__header mb-2 shrink-0 px-2 pt-1">
      <slot name="header" />
    </div>

    <div v-if="slots.queue" class="inv-aside-shell__queue min-h-0 flex-1 overflow-y-auto px-2 pb-1">
      <slot name="queue" />
    </div>

    <div
      v-if="slots.footer"
      class="inv-aside-shell__footer border-border-base mt-auto shrink-0 border-t px-2 py-1.5"
    >
      <slot name="footer" />
    </div>
  </div>
</template>
