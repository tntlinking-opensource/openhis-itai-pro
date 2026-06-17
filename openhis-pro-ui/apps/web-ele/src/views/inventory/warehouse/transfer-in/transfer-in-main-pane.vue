<script setup lang="ts">
import TransferMainPane from '@inventory/components/transfer/transfer-main-pane.vue';
defineOptions({ name: 'InventoryWarehouseTransferInMainPane' });
</script>
<template><TransferMainPane variant="in" v-bind="$attrs" /></template>
