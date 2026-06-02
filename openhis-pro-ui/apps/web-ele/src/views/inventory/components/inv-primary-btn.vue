<!--
  库房单据场景：主按钮（侧栏底栏全宽 / 空队列引导等）。
-->
<script setup lang="ts">
import { ElButton } from 'element-plus';

defineOptions({ name: 'InvPrimaryBtn' });

withDefaults(
  defineProps<{
    disabled?: boolean;
    /** 按钮文案 */
    label?: string;
  }>(),
  {
    disabled: false,
    label: '',
  },
);

const emit = defineEmits<{
  click: [];
}>();
</script>

<template>
  <ElButton class="w-full" type="primary" :disabled="disabled" @click="emit('click')">
    {{ label }}
  </ElButton>
</template>
