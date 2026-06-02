<!--
  采购退货：药品检索表格（共用 WarehouseMedicationSearchTable + 退货 medication-info）。
-->
<script lang="ts" setup>
import { pagePurchaseReturnMedicationApi } from '#/api/warehouse/purchase-return';
import WarehouseMedicationSearchTable from '#/components/warehouse/warehouse-medication-search-table.vue';

defineOptions({
  name: 'PurchaseReturnMedicationList',
});

const props = withDefaults(
  defineProps<{
    locationId?: number | string;
    keyword?: string;
    supplierId?: number | string;
  }>(),
  {
    keyword: '',
    locationId: '',
    supplierId: '',
  },
);

const emit = defineEmits<{
  selectRow: [row: Record<string, unknown>];
}>();

async function fetchMedications(params: Record<string, unknown>) {
  return pagePurchaseReturnMedicationApi(params);
}

function handleSelect(row: Record<string, unknown>) {
  emit('selectRow', row);
}
</script>

<template>
  <WarehouseMedicationSearchTable
    :search-key="props.keyword"
    :location-id="props.locationId"
    :supplier-id="props.supplierId"
    :fetch-page="fetchMedications"
    show-supplier-column
    @select-row="handleSelect"
  />
</template>
