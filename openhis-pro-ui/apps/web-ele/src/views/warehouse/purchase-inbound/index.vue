<!-- 兼容入口：实现已迁至 @inventory/warehouse/purchase-inbound。 -->
<script setup lang="ts">
import InventoryWarehousePurchaseInbound from '@inventory/warehouse/purchase-inbound/index.vue';

defineOptions({ name: 'WarehousePurchaseInbound' });
</script>

<template>
  <InventoryWarehousePurchaseInbound />
</template>
