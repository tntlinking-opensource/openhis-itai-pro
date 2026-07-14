<script setup lang="ts">
import RequisitionAsidePane from '@inventory/components/requisition/requisition-aside-pane.vue';
defineOptions({ name: 'InventoryDepartmentRequisitionAsidePane' });
</script>
<template><RequisitionAsidePane v-bind="$attrs" /></template>
