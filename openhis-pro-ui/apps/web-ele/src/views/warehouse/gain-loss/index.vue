<!-- 兼容入口：药库损益单实现位于 @inventory/warehouse/gain-loss。 -->
<script setup lang="ts">
import InventoryWarehouseGainLossPage from '@inventory/warehouse/gain-loss/index.vue';

defineOptions({ name: 'WarehouseGainLossCompat' });
</script>

<template>
  <InventoryWarehouseGainLossPage />
</template>
