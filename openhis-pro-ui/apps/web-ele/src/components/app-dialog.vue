<script setup lang="ts">
import { ElDialog } from 'element-plus';

defineOptions({
  inheritAttrs: false,
});

const props = withDefaults(
  defineProps<{
    modal?: boolean;
    top?: string;
  }>(),
  {
    modal: true,
    top: '7vh',
  },
);

const model = defineModel<boolean>({ default: false });
</script>

<template>
  <ElDialog v-model="model" v-bind="$attrs" :top="props.top" :modal="props.modal">
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
