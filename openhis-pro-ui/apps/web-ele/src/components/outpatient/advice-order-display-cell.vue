<script setup lang="ts">
import { computed } from 'vue';

import { ElTag } from 'element-plus';

import DrugOrderGroupCell from '#/components/outpatient/drug-order-group-cell.vue';
import {
  formatMedicationDoseDisplay,
  formatMedicationDoseSummaryLine,
} from '#/utils/doctor-station/medication-dose-display';

defineOptions({ name: 'AdviceOrderDisplayCell' });

const props = defineProps<{
  /** 申请单整单号（有值时在部位/扩展行右侧展示 Tag） */
  applyFormNo?: null | string;
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
  dosageDisplay?: string;
  splitDose?: number | string;
  splitDoseUnitCode?: string;
  splitDoseUnitName?: string;
  doseUnitCode?: string;
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

/**
 * 剂量行：优先 dosageDisplay / 结构化计量+拆零；总量仍拼「共」。
 * 住院护士站仅下发整段 dosageDisplay 时走 singleDose 原样回显。
 */
function formatDrugDose(d: AdviceDrugLineSource): string {
  const readyDisplay = str(d.dosageDisplay);
  const dosePart = formatMedicationDoseDisplay({
    dosageDisplay: readyDisplay || undefined,
    dose: d.singleDose,
    doseUnitCode: d.doseUnitCode,
    doseUnitName: d.doseSubUnit,
    splitDose: d.splitDose,
    splitDoseUnitCode: d.splitDoseUnitCode,
    splitDoseUnitName: d.splitDoseUnitName,
  });
  // 仅有现成文案且无单位字段时（如住院护士 dosageDisplay 塞进 singleDose）
  const left = dosePart || (str(d.singleDose) && !str(d.doseSubUnit) ? str(d.singleDose) : '');
  return formatMedicationDoseSummaryLine({
    dosageDisplay: left || undefined,
    quantity: d.totalAmount,
    unitName: d.totalAmountUnit,
    includeTotal: true,
  });
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
const applyFormNoTrim = computed(() => str(props.applyFormNo));

const showFallbackMain = computed(
  () => contentTrim.value.length > 0 || dosageTrim.value.length > 0,
);

const showExtensionRow = computed(
  () => extensionTrim.value.length > 0 || applyFormNoTrim.value.length > 0,
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
      <div v-if="showExtensionRow" class="normal-drug-shared advice-order-extension-row">
        <span v-if="extensionTrim" class="advice-order-extension-text">{{ extensionTrim }}</span>
        <ElTag
          v-if="applyFormNoTrim"
          class="advice-order-apply-form-tag"
          size="small"
          effect="plain"
          type="primary"
        >
          申请单 {{ applyFormNoTrim }}
        </ElTag>
      </div>
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

/* 部位/样本等扩展文案居左，申请单号 Tag 居右 */
.advice-order-extension-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.advice-order-extension-text {
  flex: 1 1 auto;
  min-width: 0;
  text-align: left;
}

.advice-order-apply-form-tag {
  flex: 0 0 auto;
  margin-left: auto;
}
</style>
