<script setup lang="ts">
import type { AdviceDrugItem } from '#/views/outpatient/doctor-station/advice';

defineOptions({ name: 'TcmAdviceCollapsed' });

defineProps<{
  item: AdviceDrugItem;
}>();

/** 特殊煎法展示：空视为无。 */
function formatSpecialDecoction(raw: string | undefined) {
  const v = (raw ?? '').trim();
  return v || '无';
}
</script>

<template>
  <div class="collapsed-summary">
    <div class="collapsed-title">
      {{ item.selectedDrugName || '-' }}【{{ item.selectedDrugSpec || '-' }}】 单价：
      {{ item.selectedDrugPrice || '-' }}
    </div>
    <div class="collapsed-meta">
      药房：{{ item.pharmacy || '-' }} | 单次剂量：{{ item.singleDose }}g | 特殊煎法：{{
        formatSpecialDecoction(item.tcmSpecialDecoction)
      }}
    </div>
  </div>
</template>

<style scoped>
.collapsed-summary {
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  padding: 8px 10px;
}

.collapsed-title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.collapsed-meta {
  margin-top: 4px;
  color: var(--el-text-color-regular);
  font-size: 12px;
}
</style>
