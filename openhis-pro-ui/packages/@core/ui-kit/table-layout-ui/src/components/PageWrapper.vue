<script setup lang="ts">
defineOptions({
  name: 'PageWrapper',
});
</script>

<template>
  <div class="flex h-full flex-col overflow-hidden">
    <slot></slot>
  </div>
</template>
