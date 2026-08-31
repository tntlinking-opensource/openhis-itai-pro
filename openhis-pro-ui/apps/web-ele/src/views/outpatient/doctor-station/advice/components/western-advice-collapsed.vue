<script setup lang="ts">
import type { AdviceDrugItem } from '../services/advice.types';

import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';
import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';

import { computed } from 'vue';

import { formatMoneyStrYuan, formatUnitPriceYuan } from '../services/advice-format';
import {
  buildAdviceDiagnosisDisplaySegments,
  buildDiagnosisLookupById,
} from '../services/advice-diagnosis-display';
import { totalAmountUnitDisplayLabel } from '../services/medication-unit-bands';
import { formatWesternEditDoseSegment } from '#/utils/doctor-station/medication-dose-display';

defineOptions({ name: 'WesternAdviceCollapsed' });

const props = withDefaults(
  defineProps<{
    calcTotalFee: (item: AdviceDrugItem) => string;
    item: AdviceDrugItem;
    medicationRateOptions?: TerminologyOptionDto[];
    medicationRouteOptions?: TerminologyOptionDto[];
    /** 就诊诊断候选项，用于收起态慢病角标 */
    patientDiagnoses?: DiagnosisItem[];
  }>(),
  {
    medicationRateOptions: () => [],
    medicationRouteOptions: () => [],
    patientDiagnoses: () => [],
  },
);

function terminologyLabel(options: TerminologyOptionDto[] | undefined, code: string): string {
  const c = String(code || '').trim();
  if (!c) return '—';
  const hit = (options ?? []).find((o) => String(o.value ?? '').trim() === c);
  const lb = String(hit?.label ?? '').trim();
  return lb || c;
}

/** 诊断名（含慢/特角标文案） */
const diagnosisText = computed(() => {
  const segments = buildAdviceDiagnosisDisplaySegments({
    diagnosisSummary: props.item.diagnosis || '-',
    encounterDiagnosisIds: props.item.encounterDiagnosisIds,
    diagnosisById: buildDiagnosisLookupById(props.patientDiagnoses ?? []),
  });
  return segments.map((seg) => (seg.tag ? `${seg.name}（${seg.tag}）` : seg.name)).join('、');
});

/** 收起态剂量+用法频次摘要（剂量段含最小单位括号，单位相同则省略） */
function summaryLine(item: AdviceDrugItem) {
  const usageLabel = terminologyLabel(props.medicationRouteOptions, item.usage);
  const freqLabel = terminologyLabel(props.medicationRateOptions, item.frequency);
  const totalUnitLabel = totalAmountUnitDisplayLabel(item);
  const dosePart = formatWesternEditDoseSegment(item);
  const ta =
    item.totalAmount === undefined ||
    item.totalAmount === null ||
    !Number.isFinite(Number(item.totalAmount))
      ? '—'
      : String(item.totalAmount);
  return `${dosePart}，共${ta}${totalUnitLabel}，${usageLabel}，${freqLabel}`;
}
</script>

<template>
  <div class="collapsed-summary">
    <div class="collapsed-title">
      {{ item.selectedDrugName || '-' }}【{{ item.selectedDrugSpec || '-' }}】 单价：
      {{ formatUnitPriceYuan(item.selectedDrugPrice) }}
    </div>
    <div class="collapsed-meta">
      {{ item.pharmacy }} | {{ diagnosisText }} | {{ item.feeNature }} | {{ summaryLine(item) }} |
      总金额：{{ formatMoneyStrYuan(calcTotalFee(item)) }}
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
