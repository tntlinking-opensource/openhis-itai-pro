<!--
  药库采购退货 ElTable/VXE 明细入口；损益单已改用 WarehouseOrderDetailEdit，仅保留采购退货。
-->
<script lang="ts" setup>
import { computed, ref, useAttrs } from 'vue';

import PurchaseReturnImpl from '#/views/warehouse/purchase-return/detail/warehouse-pharmacy-stock-order-table-purchase-return.vue';

defineOptions({
  name: 'WarehousePharmacyStockOrderTable',
  inheritAttrs: false,
});

const props = defineProps({
  documentType: {
    type: String,
    required: true,
    validator: (v: string) => v === 'purchase-return',
  },
});

const attrs = useAttrs();

const innerRef = ref<null | { resetAllData?: () => void }>(null);

const Inner = computed(() => PurchaseReturnImpl);

/** documentType 仅占位 props；其余全部由 attrs 透传给内部实现 */
const forwarded = computed(() => ({ ...attrs }));

defineExpose({
  resetAllData: () => innerRef.value?.resetAllData?.(),
});
</script>

<template>
  <component :is="Inner" ref="innerRef" v-bind="forwarded" />
</template>
