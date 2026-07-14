<script setup lang="ts">
import { ElDrawer } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';

defineOptions({ name: 'ServiceApplyShellWrap' });

withDefaults(
  defineProps<{
    shell?: 'dialog' | 'drawer';
    title?: string;
  }>(),
  {
    shell: 'dialog',
    title: '',
  },
);

const modelValue = defineModel<boolean>({ default: false });
</script>

<template>
  <AppDialog
    v-if="shell === 'dialog'"
    v-model="modelValue"
    :title="title"
    width="1120px"
    top="4vh"
    destroy-on-close
    append-to-body
    class="service-apply-dialog"
  >
    <slot />
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </AppDialog>
  <ElDrawer
    v-else
    v-model="modelValue"
    :title="title"
    size="80%"
    direction="rtl"
    destroy-on-close
    append-to-body
    class="service-apply-drawer"
  >
    <slot />
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </ElDrawer>
</template>

<style scoped>
.service-apply-drawer :deep(.el-drawer__body) {
  display: flex;
  flex-direction: column;
  padding: 0 16px 16px;
  overflow: hidden;
}
</style>
