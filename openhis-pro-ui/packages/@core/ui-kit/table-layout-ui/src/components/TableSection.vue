<script setup lang="ts">
defineOptions({
  name: 'TableSection',
});
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col">
    <slot></slot>
  </div>
</template>

<style scoped lang="scss">
:deep(.editable-table) {
  display: flex;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}
</style>
