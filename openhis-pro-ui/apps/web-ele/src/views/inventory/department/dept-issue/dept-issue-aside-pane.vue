<script setup lang="ts">
import GainLossAsidePane from '@inventory/components/gain-loss/gain-loss-aside-pane.vue';
defineOptions({ name: 'InventoryDepartmentDeptIssueAsidePane' });
</script>
<template>
  <GainLossAsidePane
    v-bind="$attrs"
    location-label="科室药房"
    queue-title="历史领用单"
    add-label="新增领用单"
    empty-add-label="新增领用单"
    empty-description="暂无领用单"
  />
</template>
