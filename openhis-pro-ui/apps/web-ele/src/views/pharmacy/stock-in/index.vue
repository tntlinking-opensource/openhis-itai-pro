<!-- 兼容入口：药房入库单实现位于 @pharmacy/stock-in。 -->
<script setup lang="ts">
import InventoryPharmacyStockInPage from '@pharmacy/stock-in/index.vue';

defineOptions({ name: 'PharmacyStockInCompat' });
</script>

<template>
  <InventoryPharmacyStockInPage />
</template>
