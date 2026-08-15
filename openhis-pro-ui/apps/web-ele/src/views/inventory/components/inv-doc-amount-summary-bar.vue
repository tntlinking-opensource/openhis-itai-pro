<!--
  库存单据详情金额合计条（对齐调价单页脚样式；财务审批查看时与外部列表金额列同口径）。
-->
<script setup lang="ts">
defineOptions({ name: 'InvDocAmountSummaryBar' });

export interface InvDocAmountSummaryItem {
  label: string;
  value: string;
}

interface Props {
  items?: InvDocAmountSummaryItem[];
}

withDefaults(defineProps<Props>(), {
  items: () => [],
});
</script>

<template>
  <div
    v-if="items.length > 0"
    class="inv-doc-amount-summary-bar shrink-0 border-t border-[var(--el-border-color-extra-light)] py-2 text-sm text-[var(--el-text-color-regular)]"
  >
    <span v-for="(item, index) in items" :key="`${item.label}-${index}`">
      <span v-if="index > 0"> · </span>
      {{ item.label }}
      <strong class="tabular-nums text-[var(--el-text-color-primary)]">{{ item.value }}</strong>
      元
    </span>
  </div>
</template>
