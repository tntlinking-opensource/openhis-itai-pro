<script setup lang="ts">
import GainLossMainPane from '@inventory/components/gain-loss/gain-loss-main-pane.vue';
defineOptions({ name: 'InventoryPharmacyGainLossMainPane' });
</script>
<template>
  <GainLossMainPane variant="pharmacy" v-bind="$attrs" />
</template>
