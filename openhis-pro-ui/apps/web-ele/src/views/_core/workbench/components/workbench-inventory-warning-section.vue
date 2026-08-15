<!--
  工作台库存/效期预警入口：后端按库存明细菜单权限（Navigation.Invstocklist）返回 visible；
  点击跳转库存明细并带 warningMode；效期预警额外带 expireWithinDays 供查询区回填。
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

const showSection = computed(() => visible.value);

/** 跳转库存明细，并带上预警模式及对应可见筛选项。 */
function navigateWarning(
  mode: typeof INVENTORY_WARNING_MODE_EXPIRE | typeof INVENTORY_WARNING_MODE_LOW_STOCK,
) {
  const query: Record<string, string> = { warningMode: mode };
  // 效期预警：把「近效期(天)」一并写入路由，供明细查询区回填
  if (mode === INVENTORY_WARNING_MODE_EXPIRE) {
    query.expireWithinDays = String(expireDays.value);
  }
  void router.push({
    path: '/inventory/stock-list',
    query,
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
