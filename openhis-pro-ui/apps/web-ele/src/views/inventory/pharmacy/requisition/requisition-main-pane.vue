<script setup lang="ts">
import RequisitionMainPane from '@inventory/components/requisition/requisition-main-pane.vue';
defineOptions({ name: 'InventoryPharmacyRequisitionMainPane' });
</script>
<template><RequisitionMainPane variant="pharmacy" v-bind="$attrs" /></template>
