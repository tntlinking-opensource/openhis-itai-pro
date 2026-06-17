<!-- 兼容入口：原 warehouse/transfer 已拆分为 transfer-in / transfer-out；本页重定向至调拨出库。 -->
<script setup lang="ts">
import InventoryWarehouseTransferOutPage from '@inventory/warehouse/transfer-out/index.vue';

defineOptions({ name: 'WarehouseTransferCompat' });
</script>

<template>
  <InventoryWarehouseTransferOutPage />
</template>
