<script setup lang="ts">
import TransferMainPane from '@inventory/components/transfer/transfer-main-pane.vue';
defineOptions({ name: 'InventoryWarehouseTransferOutMainPane' });
</script>
<template><TransferMainPane variant="out" v-bind="$attrs" /></template>
