<!-- 兼容入口：药库库存明细（现库）位于 @inventory/stock-list。 -->
<script setup lang="ts">
import InventoryStockListPage from '@inventory/stock-list/index.vue';

defineOptions({ name: 'WarehouseInventoryStockList' });
</script>

<template>
  <InventoryStockListPage />
</template>
