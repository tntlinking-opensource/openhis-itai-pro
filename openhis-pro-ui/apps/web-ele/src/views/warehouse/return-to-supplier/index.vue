<!-- 兼容入口：药库退货单实现位于 @inventory/warehouse/return-to-supplier。 -->
<script setup lang="ts">
import InventoryWarehouseReturnToSupplierPage from '@inventory/warehouse/return-to-supplier/index.vue';

defineOptions({ name: 'WarehouseReturnToSupplierCompat' });
</script>

<template>
  <InventoryWarehouseReturnToSupplierPage />
</template>
