<script setup lang="ts">
import GainLossMainPane from '@inventory/components/gain-loss/gain-loss-main-pane.vue';
defineOptions({ name: 'InventoryDepartmentGainLossMainPane' });
</script>
<template>
  <GainLossMainPane variant="department" v-bind="$attrs" />
</template>
