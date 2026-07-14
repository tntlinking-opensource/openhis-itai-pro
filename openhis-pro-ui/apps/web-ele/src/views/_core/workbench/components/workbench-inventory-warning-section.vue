<!--
  工作台库存/效期预警入口：仅库管、药师可见；点击跳转库存明细并带 warningMode。
-->
<script setup lang="ts">
import type { LoadInventoryWarningWorkbenchSummaryResponse } from '#/api/inventory/warehouse/inventory-detail';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  INVENTORY_WARNING_MODE_EXPIRE,
  INVENTORY_WARNING_MODE_LOW_STOCK,
  loadInventoryWarningWorkbenchSummaryApi,
} from '#/api/inventory/warehouse/inventory-detail';
import { useRequest } from '#/hooks/use-request';

import WorkbenchStatCard from './workbench-stat-card.vue';

defineOptions({ name: 'WorkbenchInventoryWarningSection' });

const router = useRouter();
const summary = ref<LoadInventoryWarningWorkbenchSummaryResponse>({});

const { run: runSummary } = useRequest(loadInventoryWarningWorkbenchSummaryApi, {
  manual: true,
  onSuccess: (data) => {
    summary.value = data ?? {};
  },
});

const visible = computed(() => Boolean(summary.value.visible));

const expireCount = computed(() => Number(summary.value.expireWarningCount ?? 0));
const lowStockCount = computed(() => Number(summary.value.lowStockWarningCount ?? 0));
const expireDays = computed(() => summary.value.expireWarningDays ?? 180);
const minPackageQty = computed(() => summary.value.minPackageQuantityWarning ?? 30);

const showSection = computed(
  () => visible.value && (expireCount.value > 0 || lowStockCount.value > 0),
);

function navigateWarning(
  mode: typeof INVENTORY_WARNING_MODE_EXPIRE | typeof INVENTORY_WARNING_MODE_LOW_STOCK,
) {
  void router.push({
    path: '/inventory/stock-list',
    query: { warningMode: mode },
  });
}

onMounted(() => {
  runSummary({});
});
</script>

<template>
  <template v-if="showSection">
    <WorkbenchStatCard
      title="效期预警"
      :hint="`剩余效期 ≤ ${expireDays} 天`"
      :value="expireCount"
      icon="lucide:calendar-clock"
      icon-gradient="linear-gradient(135deg, #fed7aa 0%, #fdba74 100%)"
      icon-color="#c2410c"
      card-tint="#fff7ed"
      accent-color="#ea580c"
      value-color="#dc2626"
      @click="navigateWarning(INVENTORY_WARNING_MODE_EXPIRE)"
    />
    <WorkbenchStatCard
      title="库存预警"
      :hint="`全院汇总包装 < ${minPackageQty}`"
      :value="lowStockCount"
      icon="lucide:package-minus"
      icon-gradient="linear-gradient(135deg, #fecaca 0%, #fca5a5 100%)"
      icon-color="#b91c1c"
      card-tint="#fef2f2"
      accent-color="#dc2626"
      value-color="#dc2626"
      @click="navigateWarning(INVENTORY_WARNING_MODE_LOW_STOCK)"
    />
  </template>
</template>
