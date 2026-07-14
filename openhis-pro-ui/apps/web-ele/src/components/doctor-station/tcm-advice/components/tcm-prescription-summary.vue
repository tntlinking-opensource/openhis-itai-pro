<script setup lang="ts">
import { formatMoneyDisplay } from '#/utils/format/money-scale';

defineOptions({ name: 'TcmPrescriptionSummary' });

withDefaults(defineProps<Props>(), {
  methodLabel: '',
});

interface Props {
  chineseHerbsDoseQuantity?: number;
  herbCount: number;
  methodLabel?: string;
  singleDoseAmount: number;
  totalAmount: number;
}
</script>

<template>
  <div class="tcm-prescription-summary">
    <span>共 {{ herbCount }} 味</span>
    <span v-if="methodLabel" class="tcm-prescription-summary__sep">·</span>
    <span v-if="methodLabel">{{ methodLabel }}</span>
    <span class="tcm-prescription-summary__sep">·</span>
    <span>单剂 {{ formatMoneyDisplay(singleDoseAmount) }} 元</span>
    <span class="tcm-prescription-summary__sep">·</span>
    <span v-if="chineseHerbsDoseQuantity != null && chineseHerbsDoseQuantity > 0">
      {{ chineseHerbsDoseQuantity }} 付合计
      <strong class="tcm-prescription-summary__total">{{ formatMoneyDisplay(totalAmount) }}</strong>
      元
    </span>
    <span v-else>请先填写付数</span>
  </div>
</template>

<style scoped>
.tcm-prescription-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  border-top: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.tcm-prescription-summary__sep {
  color: var(--el-text-color-placeholder);
}

.tcm-prescription-summary__total {
  margin: 0 2px;
  color: var(--el-color-primary);
  font-size: 15px;
  font-weight: 600;
}
</style>
