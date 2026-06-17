<script setup lang="ts">
/**
 * 兼容后端菜单中的 component 路径（views/pharmacy/return-medicine/inpatient）。
 * 实际实现位于 views/inpatient/return-medicine。
 */
import RealPage from '#/views/inpatient/return-medicine/index.vue';
</script>

<template>
  <RealPage />
</template>
