<script setup lang="ts">
import { provide } from 'vue';

import { TABLE_LAYOUT_OVERLAY_HOST } from '@zxplat-core/table-layout-ui';
import { ElDialog } from 'element-plus';

defineOptions({
  inheritAttrs: false,
});

const props = withDefaults(
  defineProps<{
    /** 默认挂到 body，避免弹层留在页面 flex 树里牵动高度 */
    appendToBody?: boolean;
    modal?: boolean;
    top?: string;
  }>(),
  {
    appendToBody: true,
    modal: true,
    top: '7vh',
  },
);

const model = defineModel<boolean>({ default: false });

/** 子级 TableLayout 识别弹层宿主，关闭「填满父级」误撑高 */
provide(TABLE_LAYOUT_OVERLAY_HOST, true);
</script>

<template>
  <ElDialog
    v-model="model"
    v-bind="$attrs"
    :append-to-body="props.appendToBody"
    :top="props.top"
    :modal="props.modal"
  >
    <template v-if="$slots.header" #header>
      <slot name="header"></slot>
    </template>
    <template v-if="$slots.default">
      <slot></slot>
    </template>
    <template v-if="$slots.footer" #footer>
      <slot name="footer"></slot>
    </template>
  </ElDialog>
</template>
