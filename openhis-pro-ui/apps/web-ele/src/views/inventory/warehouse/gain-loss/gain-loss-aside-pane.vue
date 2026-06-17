<script setup lang="ts">
import GainLossAsidePane from '@inventory/components/gain-loss/gain-loss-aside-pane.vue';

defineOptions({ name: 'InventoryWarehouseGainLossAsidePane' });
</script>

<template>
  <GainLossAsidePane v-bind="$attrs" />
</template>
