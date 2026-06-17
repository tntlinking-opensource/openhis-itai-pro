<script setup lang="ts">
import GainLossMainPane from '@inventory/components/gain-loss/gain-loss-main-pane.vue';

defineOptions({ name: 'InventoryWarehouseGainLossMainPane' });
</script>

<template>
  <GainLossMainPane variant="warehouse" v-bind="$attrs" />
</template>
