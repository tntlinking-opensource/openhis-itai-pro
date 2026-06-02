<!-- 兼容入口：药库出库单实现位于 @inventory/warehouse/stock-out。 -->
<script setup lang="ts">
import InventoryWarehouseStockOutPage from '@inventory/warehouse/stock-out/index.vue';

defineOptions({ name: 'WarehouseStockOutCompat' });
</script>

<template>
  <InventoryWarehouseStockOutPage />
</template>
