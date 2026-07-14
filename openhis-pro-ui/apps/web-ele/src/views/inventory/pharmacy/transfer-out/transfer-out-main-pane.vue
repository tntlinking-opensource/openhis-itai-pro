<script setup lang="ts">
import TransferMainPane from '@inventory/components/transfer/transfer-main-pane.vue';
defineOptions({ name: 'InventoryPharmacyTransferOutMainPane' });
</script>
<template><TransferMainPane domain="pharmacy" variant="out" v-bind="$attrs" /></template>
