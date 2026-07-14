<script setup lang="ts">
import TransferMainPane from '@inventory/components/transfer/transfer-main-pane.vue';
defineOptions({ name: 'InventoryPharmacyTransferInMainPane' });
</script>
<template><TransferMainPane domain="pharmacy" variant="in" v-bind="$attrs" /></template>
