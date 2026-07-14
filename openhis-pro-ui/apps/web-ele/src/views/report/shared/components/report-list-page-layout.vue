<script setup lang="ts">
defineOptions({ name: 'ReportListPageLayout' });
</script>

<template>
  <div class="report-list-page">
    <slot name="extra" />
    <slot name="query" />
    <slot name="toolbar" />
    <slot name="table" />
    <slot name="footer" />
  </div>
</template>

<style lang="scss">
@use '../report-list-page.scss';
</style>
