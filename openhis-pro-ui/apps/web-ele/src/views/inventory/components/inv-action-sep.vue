<!--
  库房单据操作条内竖向分隔（视觉分组，无业务逻辑）。
-->
<script setup lang="ts">
defineOptions({ name: 'InvActionSep' });
</script>

<template>
  <span
    class="inv-action-sep mx-1 h-4 w-px shrink-0 bg-[var(--el-border-color-lighter)]"
    aria-hidden="true"
  />
</template>
