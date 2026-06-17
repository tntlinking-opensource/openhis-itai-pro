<script setup lang="ts">
import type { AdviceDrugItem } from '../services/advice.types';

import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';

import { formatMoneyStrYuan, formatUnitPriceYuan } from '../services/advice-format';
import { totalAmountUnitDisplayLabel } from '../services/medication-unit-bands';

defineOptions({ name: 'WesternAdviceCollapsed' });

const props = withDefaults(
  defineProps<{
    calcTotalFee: (item: AdviceDrugItem) => string;
    item: AdviceDrugItem;
    medicationRateOptions?: TerminologyOptionDto[];
    medicationRouteOptions?: TerminologyOptionDto[];
  }>(),
  {
    medicationRateOptions: () => [],
    medicationRouteOptions: () => [],
  },
);

function terminologyLabel(options: TerminologyOptionDto[] | undefined, code: string): string {
  const c = String(code || '').trim();
  if (!c) return '—';
  const hit = (options ?? []).find((o) => String(o.value ?? '').trim() === c);
  const lb = String(hit?.label ?? '').trim();
  return lb || c;
}

function summaryLine(item: AdviceDrugItem) {
  const usageLabel = terminologyLabel(props.medicationRouteOptions, item.usage);
  const freqLabel = terminologyLabel(props.medicationRateOptions, item.frequency);
  const totalUnitLabel = totalAmountUnitDisplayLabel(item);
  const sd =
    item.singleDose === undefined ||
    item.singleDose === null ||
    !Number.isFinite(Number(item.singleDose))
      ? '—'
      : String(item.singleDose);
  const ta =
    item.totalAmount === undefined ||
    item.totalAmount === null ||
    !Number.isFinite(Number(item.totalAmount))
      ? '—'
      : String(item.totalAmount);
  return `${sd}${item.doseMainUnit || ''}/次，共${ta}${totalUnitLabel}，${usageLabel}，${freqLabel}`;
}
</script>

<template>
  <div class="collapsed-summary">
    <div class="collapsed-title">
      {{ item.selectedDrugName || '-' }}【{{ item.selectedDrugSpec || '-' }}】 单价：
      {{ formatUnitPriceYuan(item.selectedDrugPrice) }}
    </div>
    <div class="collapsed-meta">
      {{ item.pharmacy }} | {{ item.diagnosis || '-' }} | {{ item.feeNature }} |
      {{ summaryLine(item) }} | 总金额：{{ formatMoneyStrYuan(calcTotalFee(item)) }}
    </div>
  </div>
</template>

<style scoped>
.collapsed-summary {
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
  padding: 8px 10px;
}

.collapsed-title {
  color: #0f172a;
  font-size: 14px;
  font-weight: 600;
}

.collapsed-meta {
  margin-top: 4px;
  color: #475569;
  font-size: 12px;
}
</style>
