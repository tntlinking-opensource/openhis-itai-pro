<script setup lang="ts">
import { computed } from 'vue';

import { ElTag } from 'element-plus';

import DrugOrderGroupCell from '#/components/outpatient/drug-order-group-cell.vue';
import { resolveSkinTestOutcomePillClass } from '#/utils/advice/advice-status-display';
import {
  formatMedicationDoseDisplay,
  formatMedicationDoseSummaryLine,
} from '#/utils/doctor-station/medication-dose-display';
import { parseTcmCollapsedHerbDisplay } from '#/utils/tcm-prescription-display';

defineOptions({ name: 'AdviceOrderDisplayCell' });

/** 按方合并后的一味饮片（与住院 AdviceRowItem.tcmHerbLines 同构） */
interface TcmPrescriptionHerbLine {
  name: string;
  dose: string;
}

const props = withDefaults(
  defineProps<{
    /** 申请单整单号（有值时在部位/扩展行右侧展示 Tag） */
    applyFormNo?: null | string;
    content?: null | string;
    dosageText?: null | string;
    extension?: null | string;
    groupItems?: AdviceDrugLineSource[] | null;
    rowKey: string;
    /** 中药方折叠行：药名+剂量改流式网格，避免横排 nowrap 挤成竖字 */
    tcmFlag?: boolean;
    /** 单行（非成组）皮试结果 code */
    skinTestOutcomeCode?: null | string;
    /** 单行（非成组）皮试结果展示 */
    skinTestOutcomeText?: null | string;
    /** 病案首页：按方合并后的标题（如中药汤剂） */
    tcmPrescriptionTitle?: string;
    /** 病案首页：同方饮片行 */
    tcmHerbLines?: TcmPrescriptionHerbLine[] | null;
    /** 病案首页：方闭合用法/付数 */
    tcmClosingMeta?: string;
    /** 病案首页：代煎 companion 文案 */
    tcmDecoctionText?: string;
  }>(),
  {
    applyFormNo: undefined,
    content: undefined,
    dosageText: undefined,
    extension: undefined,
    groupItems: undefined,
    tcmFlag: false,
    skinTestOutcomeCode: undefined,
    skinTestOutcomeText: undefined,
    tcmPrescriptionTitle: undefined,
    tcmHerbLines: undefined,
    tcmClosingMeta: undefined,
    tcmDecoctionText: undefined,
  },
);

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
  /** 医嘱备注（成组共用行与扩展行同源） */
  remark?: string;
  dosageDisplay?: string;
  /** 成组共用行滴速展示 */
  speedDisplay?: string;
  /** 皮试结果 code / 展示（仅皮试药且已登记） */
  skinTestOutcomeCode?: string;
  skinTestOutcomeText?: string;
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

/** 从扩展行抽出备注正文（成组不走 fallback，需单独拼到共用行） */
function remarkFromExtension(extension: string): string {
  for (const part of extension.split('；')) {
    const text = part.trim();
    if (text.startsWith('备注：')) {
      return text.slice('备注：'.length).trim();
    }
  }
  return '';
}

/** 组内首条非空备注；缺则回落 extension 的「备注：」段 */
function pickSharedRemark(items: readonly unknown[]): string {
  for (const item of items) {
    const remark = str(toLineSource(item).remark);
    if (remark) {
      return remark;
    }
  }
  return remarkFromExtension(str(props.extension));
}

/** 组内共用行：用法、频次、滴速、天数、备注；缺省则不拼该段 */
function formatSharedLine(items: readonly unknown[]): string {
  if (items.length === 0) return '';
  const d = toLineSource(items[0]);
  const usage = str(d.usage);
  const freq = str(d.frequency);
  const days = str(d.medicationDays);
  // 同组滴速一致；首行常为溶媒无码，从组内任一条取展示名
  let speed = '';
  for (const item of items) {
    speed = str(toLineSource(item).speedDisplay);
    if (speed) {
      break;
    }
  }
  const dayPart = days ? `（${days}天）` : '';
  const parts: string[] = [];
  if (usage && freq) {
    parts.push(`${usage}，${freq}`);
  } else if (usage || freq) {
    parts.push(usage || freq);
  }
  if (speed) {
    parts.push(`滴速：${speed}`);
  }
  const body = parts.length === 0 ? dayPart : `${parts.join('，')}${dayPart}`;
  const remark = pickSharedRemark(items);
  const remarkPart = remark ? `备注：${remark}` : '';
  if (body && remarkPart) {
    return `${body}；${remarkPart}`;
  }
  return body || remarkPart;
}

const contentTrim = computed(() => str(props.content));
const dosageTrim = computed(() => str(props.dosageText));
const extensionTrim = computed(() => str(props.extension));
const applyFormNoTrim = computed(() => str(props.applyFormNo));

/** 中药折叠行：空格并列药名/剂量 → 配对单元；付数拆到扩展行旁 */
const tcmCollapsed = computed(() => {
  if (props.tcmFlag !== true) {
    return null;
  }
  if ((props.tcmHerbLines?.length ?? 0) > 0) {
    return null;
  }
  return parseTcmCollapsedHerbDisplay(props.content, props.dosageText);
});

/** 按方合并后的饮片明细 */
const tcmRxHerbLines = computed(() => props.tcmHerbLines ?? []);

/** 方标题（中药汤剂） */
const tcmRxTitle = computed(() => str(props.tcmPrescriptionTitle));

/** 方闭合：用法 + 付数 */
const tcmRxClosing = computed(() => str(props.tcmClosingMeta));

/** 代煎 companion 文案 */
const tcmRxDecoction = computed(() => str(props.tcmDecoctionText));

/** 病案首页按方合并块：有饮片明细时走竖排汤剂版式 */
const showTcmPrescriptionBlock = computed(
  () => props.tcmFlag === true && tcmRxHerbLines.value.length > 0,
);

const tcmHerbPairs = computed(() => tcmCollapsed.value?.pairs ?? []);

const tcmDoseCountText = computed(() => tcmCollapsed.value?.doseCountText ?? '');

const tcmDosageFallback = computed(() => tcmCollapsed.value?.dosageWithoutCount ?? '');

const showFallbackMain = computed(
  () => contentTrim.value.length > 0 || dosageTrim.value.length > 0,
);

/** 扩展行：申请单/折叠付数；汤剂块内已画代煎，此处不再重复 */
const showExtensionRow = computed(
  () =>
    (extensionTrim.value.length > 0 && !showTcmPrescriptionBlock.value) ||
    applyFormNoTrim.value.length > 0 ||
    tcmDoseCountText.value.length > 0 ||
    (tcmRxDecoction.value.length > 0 && !showTcmPrescriptionBlock.value),
);

/** 无组或空数组时交给 DrugOrderGroupCell #fallback */
const shellItems = computed(() => {
  const g = props.groupItems;
  if (g === null || g === undefined || g.length === 0) {
    return undefined;
  }
  return g;
});

/** 单行皮试结果角标（非成组 fallback 用） */
const fallbackSkinTestOutcomeText = computed(() => str(props.skinTestOutcomeText));
const fallbackSkinTestOutcomeCode = computed(() => str(props.skinTestOutcomeCode));

/** 成组行内皮试结果角标 */
function lineSkinTestOutcomeText(item: unknown): string {
  return str(toLineSource(item).skinTestOutcomeText);
}

function lineSkinTestOutcomeCode(item: unknown): string {
  return str(toLineSource(item).skinTestOutcomeCode);
}
</script>

<template>
  <DrugOrderGroupCell :row-key="rowKey" :items="shellItems">
    <template #line="{ item }">
      <div class="advice-order-line-row">
        <span v-if="lineParts(item).title" class="drug-main-text drug-main-text--with-tag">
          <span>{{ lineParts(item).title }}</span>
          <span
            v-if="lineSkinTestOutcomeText(item)"
            class="skin-test-outcome-pill"
            :class="resolveSkinTestOutcomePillClass(lineSkinTestOutcomeCode(item))"
          >
            {{ lineSkinTestOutcomeText(item) }}
          </span>
        </span>
        <span v-if="lineParts(item).dose" class="group-drug-dose">{{ lineParts(item).dose }}</span>
      </div>
    </template>
    <template #shared="{ items }">
      <span v-if="formatSharedLine(items)">{{ formatSharedLine(items) }}</span>
    </template>
    <template #main="{ item }">
      <div class="advice-order-line-row">
        <span v-if="lineParts(item).title" class="drug-main-text drug-main-text--with-tag">
          <span>{{ lineParts(item).title }}</span>
          <span
            v-if="lineSkinTestOutcomeText(item)"
            class="skin-test-outcome-pill"
            :class="resolveSkinTestOutcomePillClass(lineSkinTestOutcomeCode(item))"
          >
            {{ lineSkinTestOutcomeText(item) }}
          </span>
        </span>
        <span v-if="lineParts(item).dose" class="group-drug-dose">{{ lineParts(item).dose }}</span>
      </div>
    </template>
    <template #fallback>
      <!-- 病案首页：一张中药方一行，饮片竖排，末味后接用法/付数 -->
      <template v-if="showTcmPrescriptionBlock">
        <div class="tcm-rx-block">
          <div
            v-for="(herb, index) in tcmRxHerbLines"
            :key="`${rowKey}-rx-${index}`"
            class="tcm-rx-block__line"
          >
            <span v-if="index === 0 && tcmRxTitle" class="tcm-rx-block__title">
              {{ tcmRxTitle }}(
            </span>
            <span class="tcm-rx-block__herb">
              {{ herb.name }}<template v-if="herb.dose">:{{ herb.dose }}</template>
            </span>
            <span v-if="index === tcmRxHerbLines.length - 1" class="tcm-rx-block__close">
              )<template v-if="tcmRxClosing">{{ tcmRxClosing }}</template>
            </span>
          </div>
          <div v-if="tcmRxDecoction" class="tcm-rx-block__decoction">{{ tcmRxDecoction }}</div>
        </div>
      </template>
      <!-- 护士站折叠中药方：药名+剂量流式网格，避免横排 nowrap 挤成逐字竖排 -->
      <template v-else-if="tcmFlag">
        <div v-if="tcmHerbPairs.length > 0" class="tcm-herb-grid">
          <span
            v-for="(pair, index) in tcmHerbPairs"
            :key="`${rowKey}-tcm-${index}`"
            class="tcm-herb-chip"
          >
            <span class="tcm-herb-name">{{ pair.name }}</span>
            <span v-if="pair.dose" class="tcm-herb-dose">{{ pair.dose }}</span>
          </span>
        </div>
        <div v-else-if="showFallbackMain" class="advice-order-tcm-stack">
          <span v-if="contentTrim" class="drug-main-text">{{ contentTrim }}</span>
          <span v-if="tcmDosageFallback" class="group-drug-dose group-drug-dose--wrap">
            {{ tcmDosageFallback }}
          </span>
        </div>
      </template>
      <div v-else-if="showFallbackMain" class="advice-order-line-row">
        <span v-if="contentTrim" class="drug-main-text drug-main-text--with-tag">
          <span>{{ contentTrim }}</span>
          <span
            v-if="fallbackSkinTestOutcomeText"
            class="skin-test-outcome-pill"
            :class="resolveSkinTestOutcomePillClass(fallbackSkinTestOutcomeCode)"
          >
            {{ fallbackSkinTestOutcomeText }}
          </span>
        </span>
        <span v-if="dosageTrim" class="group-drug-dose">{{ dosageTrim }}</span>
      </div>
      <div v-if="showExtensionRow" class="normal-drug-shared advice-order-extension-row">
        <span v-if="tcmDoseCountText" class="advice-order-tcm-fu">{{ tcmDoseCountText }}</span>
        <span v-if="tcmRxDecoction" class="advice-order-extension-text">{{ tcmRxDecoction }}</span>
        <span v-else-if="extensionTrim" class="advice-order-extension-text">{{
          extensionTrim
        }}</span>
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

.drug-main-text--with-tag {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.skin-test-outcome-pill {
  flex-shrink: 0;
  display: inline-block;
  height: 20px;
  padding: 0 8px;
  border-radius: 4px;
  color: var(--el-color-white);
  font-size: 12px;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
  white-space: nowrap;
  vertical-align: middle;
}

.skin-test-outcome-pill--positive {
  background: var(--el-color-danger);
}

.skin-test-outcome-pill--negative {
  background: var(--el-color-success);
}

.skin-test-outcome-pill--default {
  background: var(--el-color-info);
}

.advice-order-line-row .group-drug-dose {
  flex: 0 0 auto;
  text-align: right;
  white-space: nowrap;
}

/* 中药方：药名与剂量同芯片，自动换行，避免长剂量 nowrap 挤扁药名 */
.tcm-herb-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  width: 100%;
  min-width: 0;
  line-height: 1.45;
}

.tcm-herb-chip {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  max-width: 100%;
  white-space: nowrap;
}

.tcm-herb-name {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 500;
}

.tcm-herb-dose {
  color: var(--el-text-color-regular);
  font-size: 12px;
}

/* 配对失败时纵向堆叠，剂量允许换行 */
.advice-order-tcm-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  min-width: 0;
}

.advice-order-tcm-stack .group-drug-dose--wrap {
  flex: none;
  text-align: left;
  white-space: normal;
  word-break: break-word;
}

.advice-order-tcm-fu {
  flex: 0 0 auto;
  color: var(--el-text-color-primary);
  font-weight: 600;
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

/* 病案首页：一张方竖排饮片，标题与首味同行，末味后闭合用法/付数 */
.tcm-rx-block {
  display: flex;
  flex-direction: column;
  gap: 1px;
  width: 100%;
  min-width: 0;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  white-space: normal;
  word-break: break-word;
}

.tcm-rx-block__line {
  display: block;
}

.tcm-rx-block__title,
.tcm-rx-block__herb,
.tcm-rx-block__close,
.tcm-rx-block__decoction {
  font-size: 14px;
}

.tcm-rx-block__decoction {
  margin-top: 2px;
}
</style>
