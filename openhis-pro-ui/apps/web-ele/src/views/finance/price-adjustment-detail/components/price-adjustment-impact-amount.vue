<script setup lang="ts">
import { computed } from 'vue';

import { CaretBottom, CaretTop } from '@element-plus/icons-vue';
import { ElIcon } from 'element-plus';

import { formatAdjustmentAmount } from '../utils/format-amount';
import { resolveImpactTextClass, resolveImpactTone } from '../utils/format-impact';
import { formatPurchaseMoneyDisplay } from '#/utils/format/money-scale';

defineOptions({ name: 'PriceAdjustmentImpactAmount' });

interface Props {
  value?: unknown;
  showArrow?: boolean;
  align?: 'center' | 'left' | 'right';
  /** 进价盈亏用 purchase（三位口径）；售价盈亏默认 sale */
  moneyKind?: 'purchase' | 'sale';
}

const props = withDefaults(defineProps<Props>(), {
  value: undefined,
  showArrow: true,
  align: 'right',
  moneyKind: 'sale',
});

const tone = computed(() => resolveImpactTone(props.value));
const textClass = computed(() => resolveImpactTextClass(props.value));
const formatted = computed(() => {
  if (props.moneyKind === 'purchase') {
    if (props.value === null || props.value === undefined || props.value === '') return '—';
    return formatPurchaseMoneyDisplay(props.value as string | number) || '—';
  }
  return formatAdjustmentAmount(props.value);
});
const showUp = computed(() => props.showArrow && tone.value === 'profit');
const showDown = computed(() => props.showArrow && tone.value === 'loss');
const alignClass = computed(() => {
  if (props.align === 'center') {
    return 'justify-center';
  }
  if (props.align === 'left') {
    return 'justify-start';
  }
  return 'justify-end';
});
</script>

<template>
  <span class="inline-flex items-center gap-0.5 tabular-nums" :class="[textClass, alignClass]">
    <ElIcon v-if="showUp" :size="14">
      <CaretTop />
    </ElIcon>
    <ElIcon v-if="showDown" :size="14">
      <CaretBottom />
    </ElIcon>
    {{ formatted }}
  </span>
</template>
