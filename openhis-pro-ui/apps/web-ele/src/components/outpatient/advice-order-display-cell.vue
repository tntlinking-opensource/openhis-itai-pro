<script setup lang="ts">
import { computed } from 'vue';

import DrugOrderGroupCell from '#/components/outpatient/drug-order-group-cell.vue';

defineOptions({ name: 'AdviceOrderDisplayCell' });

const props = defineProps<{
  content?: null | string;
  dosageText?: null | string;
  extension?: null | string;
  groupItems?: AdviceDrugLineSource[] | null;
  rowKey: string;
}>();

/**
 * 与医嘱编辑保存项展示字段对齐（仅声明本组件用到的键；调用方传入完整 DTO 亦可）。
 */
interface AdviceDrugLineSource {
  selectedDrugName?: string;
  selectedDrugSpec?: string;
  pharmacy?: string;
  singleDose?: number | string;
  doseSubUnit?: string;
  totalAmount?: number | string;
  totalAmountUnit?: string;
  usage?: string;
  frequency?: string;
  medicationDays?: number | string;
}

function str(v: unknown): string {
  if (v === null || v === undefined) return '';
  return String(v).trim();
}

function toLineSource(item: unknown): AdviceDrugLineSource {
  return (item ?? {}) as AdviceDrugLineSource;
}

/** 题名：名称【规格】（药房）；缺省则不拼对应片段 */
function formatDrugTitle(d: AdviceDrugLineSource): string {
  const name = str(d.selectedDrugName);
  const spec = str(d.selectedDrugSpec);
  const ph = str(d.pharmacy);
  let out = name;
  if (spec) out += `【${spec}】`;
  if (ph) out += `（${ph}）`;
  return out;
}

/** 剂量行；字段不全则只拼有的部分，全无则空串 */
function formatDrugDose(d: AdviceDrugLineSource): string {
  const sd = str(d.singleDose);
  const su = str(d.doseSubUnit);
  const ta = str(d.totalAmount);
  const tu = str(d.totalAmountUnit);
  const left = sd && su ? `${sd}${su}/次` : '';
  const right = ta && tu ? `共${ta}${tu}` : '';
  if (left && right) return `${left}，${right}。`;
  if (left) return `${left}。`;
  if (right) return `${right}。`;
  return '';
}

function lineParts(item: unknown) {
  const d = toLineSource(item);
  return { title: formatDrugTitle(d), dose: formatDrugDose(d) };
}

/** 组内共用行：用法、频次、天数；缺省则不拼该段 */
function formatSharedLine(items: readonly unknown[]): string {
  if (items.length === 0) return '';
  const d = toLineSource(items[0]);
  const usage = str(d.usage);
  const freq = str(d.frequency);
  const days = str(d.medicationDays);
  const dayPart = days ? `（${days}天）` : '';
  if (usage && freq) return `${usage}，${freq}${dayPart}`;
  if (usage) return `${usage}${dayPart}`;
  if (freq) return `${freq}${dayPart}`;
  return dayPart;
}

const contentTrim = computed(() => str(props.content));
const dosageTrim = computed(() => str(props.dosageText));
const extensionTrim = computed(() => str(props.extension));

const showFallbackMain = computed(
  () => contentTrim.value.length > 0 || dosageTrim.value.length > 0,
);

/** 无组或空数组时交给 DrugOrderGroupCell #fallback */
const shellItems = computed(() => {
  const g = props.groupItems;
  if (g === null || g === undefined || g.length === 0) {
    return undefined;
  }
  return g;
});
</script>

<template>
  <DrugOrderGroupCell :row-key="rowKey" :items="shellItems">
    <template #line="{ item }">
      <div class="advice-order-line-row">
        <span v-if="lineParts(item).title" class="drug-main-text">{{ lineParts(item).title }}</span>
        <span v-if="lineParts(item).dose" class="group-drug-dose">{{ lineParts(item).dose }}</span>
      </div>
    </template>
    <template #shared="{ items }">
      <span v-if="formatSharedLine(items)">{{ formatSharedLine(items) }}</span>
    </template>
    <template #main="{ item }">
      <div class="advice-order-line-row">
        <span v-if="lineParts(item).title" class="drug-main-text">{{ lineParts(item).title }}</span>
        <span v-if="lineParts(item).dose" class="group-drug-dose">{{ lineParts(item).dose }}</span>
      </div>
    </template>
    <template #fallback>
      <div v-if="showFallbackMain" class="advice-order-line-row">
        <span v-if="contentTrim" class="drug-main-text">{{ contentTrim }}</span>
        <span v-if="dosageTrim" class="group-drug-dose">{{ dosageTrim }}</span>
      </div>
      <div v-if="extensionTrim" class="normal-drug-shared">{{ extensionTrim }}</div>
    </template>
  </DrugOrderGroupCell>
</template>

<style scoped>
/* 药品信息居左，单次剂量+总量居右，便于扫读 */
.advice-order-line-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-width: 0;
}

.advice-order-line-row .drug-main-text {
  flex: 1 1 auto;
  min-width: 0;
  text-align: left;
}

.advice-order-line-row .group-drug-dose {
  flex: 0 0 auto;
  text-align: right;
  white-space: nowrap;
}
</style>
