<script setup lang="ts">
import RequisitionMainPane from '@inventory/components/requisition/requisition-main-pane.vue';
defineOptions({ name: 'InventoryDepartmentRequisitionMainPane' });
</script>
<template><RequisitionMainPane variant="department" v-bind="$attrs" /></template>
