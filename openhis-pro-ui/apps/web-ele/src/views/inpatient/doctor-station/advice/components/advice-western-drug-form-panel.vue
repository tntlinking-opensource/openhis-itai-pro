<script setup lang="ts">
import type { AdviceDrugItem } from '../services/advice.types';

import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';

import { computed, watch } from 'vue';

import { ElCheckbox, ElInput, ElInputNumber, ElOption, ElSelect, ElSwitch } from 'element-plus';

import { formatAdviceDrugInfoStockText } from '#/utils/doctor-station/advice-catalog-stock-display';
import { formatPackagePerMinUnitText } from '#/utils/doctor-station/medication-package-min-unit-display';
import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';

import { parseOptionalNumberInput } from '../services/advice-edit';
import {
  contractSelectionFromValue,
  filterFeeNatureOptionsByChrgitmLv,
  formatMoneyStrYuan,
  formatUnitPriceYuan,
  type FeeNatureOption,
} from '../services/advice-format';
import {
  roundMedicationDoseDisplayQty,
  totalUnitSelectOptions,
} from '../services/medication-unit-bands';

/** 剂量单位输入框展示值：界面保留 3 位小数。 */
function doseDisplayValue(raw: number | null | undefined): number | undefined {
  if (raw === null || raw === undefined) return undefined;
  const n = Number(raw);
  if (!Number.isFinite(n)) return undefined;
  return roundMedicationDoseDisplayQty(n);
}

/**
 * 剂量侧输入：展示 3 位时若仅为展示回写，保留内存中的 6 位换算值；
 * 用户真正改数时再写入并反算最小单位。
 */
function onDoseConvertRatioInput(
  value: unknown,
  current: number | null | undefined,
  setDose: (v: number | undefined) => void,
  applyToSplit: (v: number) => void,
) {
  const v = parseOptionalNumberInput(value);
  const cur = Number(current);
  if (
    v !== undefined &&
    Number.isFinite(v) &&
    Number.isFinite(cur) &&
    roundMedicationDoseDisplayQty(cur) === roundMedicationDoseDisplayQty(v) &&
    Math.abs(cur - v) > 1e-9
  ) {
    applyToSplit(cur);
    return;
  }
  setDose(v);
  if (v !== undefined && Number.isFinite(v)) {
    applyToSplit(v);
  }
}

/** 本行西药明细表单与父级交互（闭包在父级按 index 绑定）。 */
export interface WesternDrugFormHandlers {
  setField: <K extends keyof AdviceDrugItem>(key: K, value: AdviceDrugItem[K]) => void;
  applySplitDoseToDoseSide: (singleDose: number) => void;
  applyDoseQtyToSplitSide: (doseQty: number) => void;
  /** 频次/用药天数变更：调频次模块算执行次数 */
  debouncedRecalcFrequencyTotal: () => void;
  /** 单次剂量等变更：仅前端重算总量 */
  debouncedEnsureFrequencyCountAndRecalcTotal: () => void;
  /** 医生手工修改总量 */
  handleTotalAmountManualInput: (value: unknown) => void;
  /** 医生手工修改总量单位（仅换算数量，不触发自动重算） */
  handleTotalAmountUnitManualSelect: (value: unknown) => void;
}

const props = defineProps<{
  formHandlers: WesternDrugFormHandlers;
  /** 长期医嘱：隐藏总量与自动推算总量 */
  hideTotalAmount?: boolean;
  item: AdviceDrugItem;
  feeNatureOptions: FeeNatureOption[];
  medicationRateOptions: TerminologyOptionDto[];
  /** 频次码 → day_times 段数（变动剂量展开框） */
  dayTimesCountByRateCode?: Record<string, number>;
  medicationRouteOptions: TerminologyOptionDto[];
  /** IF-IP-DOR-18 输液滴速选项（仅 injectFlag 药展示） */
  medicationSpeedOptions?: TerminologyOptionDto[];
}>();

/** 医保等级=自费时仅保留自费合同下拉 */
const itemFeeNatureOptions = computed(() =>
  filterFeeNatureOptionsByChrgitmLv(props.feeNatureOptions, props.item.chrgitmLv),
);

/** 当前频次日内时刻段数（展开框数；无则 0） */
const unevenDoseSlotCount = computed(() => {
  const rate = String(props.item.frequency ?? '').trim();
  if (!rate) return 0;
  const map = props.dayTimesCountByRateCode ?? {};
  const n = Number(map[rate]);
  if (!Number.isFinite(n) || n < 1) return 0;
  return Math.trunc(n);
});

/** 是否可勾选变动剂量（长期 + 频次有 day_times） */
const canUnevenDose = computed(
  () => Boolean(props.hideTotalAmount) && unevenDoseSlotCount.value >= 2,
);

/** 分次剂量框下标列表（供 v-for） */
const unevenDoseSlotIndexes = computed(() => {
  const n = unevenDoseSlotCount.value;
  if (n < 1) return [] as number[];
  return Array.from({ length: n }, (_, i) => i);
});

/**
 * 勾选变动剂量：开则按频次段数铺框（首框带当前单次剂量）；取消则清空。
 */
function onUnevenDoseToggle(enabled: boolean) {
  props.formHandlers.setField('unevenDoseEnabled', enabled);
  if (!enabled) {
    props.formHandlers.setField('doseScheduleSlots', undefined);
    return;
  }
  const count = unevenDoseSlotCount.value;
  if (count < 1) {
    props.formHandlers.setField('unevenDoseEnabled', false);
    return;
  }
  const current = Number(props.item.doseConvertRatio ?? props.item.singleDose);
  const seed =
    Number.isFinite(current) && current > 0 ? roundMedicationDoseDisplayQty(current) : undefined;
  const slots: Array<number | undefined> = [];
  for (let i = 0; i < count; i++) {
    slots.push(i === 0 ? seed : undefined);
  }
  props.formHandlers.setField('doseScheduleSlots', slots);
}

/**
 * 改某一分次剂量框。
 */
function onDoseScheduleSlotInput(index: number, value: unknown) {
  const slots = [...(props.item.doseScheduleSlots ?? [])];
  const count = unevenDoseSlotCount.value;
  while (slots.length < count) {
    slots.push(undefined);
  }
  slots[index] = parseOptionalNumberInput(value);
  props.formHandlers.setField('doseScheduleSlots', slots);
}

/** 频次变更：若已开不一致且段数变化，重铺框长。 */
watch(
  () => [props.item.frequency, unevenDoseSlotCount.value, props.item.unevenDoseEnabled] as const,
  ([, count, enabled]) => {
    if (!enabled) return;
    const prev = props.item.doseScheduleSlots ?? [];
    if (count < 2) {
      // dayTimes 未就绪时保留已回显的分次框，避免撤回后再编辑被误关
      if (prev.length >= 2) return;
      // 频次无法展开多框：自动关闭
      props.formHandlers.setField('unevenDoseEnabled', false);
      props.formHandlers.setField('doseScheduleSlots', undefined);
      return;
    }
    if (prev.length === count) return;
    const next: Array<number | undefined> = [];
    for (let i = 0; i < count; i++) {
      next.push(prev[i]);
    }
    props.formHandlers.setField('doseScheduleSlots', next);
  },
);

function patchContract(contractId: string) {
  const sel = contractSelectionFromValue(contractId, itemFeeNatureOptions.value);
  props.formHandlers.setField('contractId', sel.contractId);
  props.formHandlers.setField('feeNature', sel.feeNature);
}

/** 当前合同不在收窄后的下拉里时，自动切到可用项 */
watch(
  itemFeeNatureOptions,
  (opts) => {
    const cur = String(props.item.contractId ?? '').trim();
    if (opts.length === 0) return;
    if (cur && opts.some((o) => String(o.value ?? '').trim() === cur)) return;
    const first = String(opts[0]?.value ?? '').trim();
    if (first) patchContract(first);
  },
  { immediate: true },
);

const autoCalcTotalAmount = defineModel<boolean>('autoCalcTotalAmount', { required: true });

const calcTotalFee = computed(() => {
  const item = props.item;
  const price = Number(item.selectedDrugPrice || 0);
  const amount = Number(item.totalAmount ?? 0);
  if (price <= 0 || amount <= 0) {
    return '0.00';
  }
  return formatMoneyDisplay(calcMoneyLineTotal(price, amount));
});

/** 包装与最小单位不同时：「每盒30片」类文案 */
const packagePerMinUnitText = computed(() => formatPackagePerMinUnitText(props.item));

/** 选品库存：「库存：4盒6片」；无库存字段时为空 */
const stockDisplayText = computed(() => formatAdviceDrugInfoStockText(props.item));

/** 输液/注射药展示滴速（目录 inject_flag / 列表 injectionFlag / 已有滴速码） */
const showSpeedField = computed(
  () => props.item.injectFlag === true || Boolean(String(props.item.speedCode ?? '').trim()),
);
</script>

<template>
  <div class="advice-product-form-root">
    <div class="product-info-row">
      <span class="meta-label product-info-label">药品信息:</span>
      <span class="product-text">
        {{ item.selectedDrugName || '-' }}【{{ item.selectedDrugSpec || '-' }}】
        <template v-if="packagePerMinUnitText">
          <span class="product-info-gap">{{ packagePerMinUnitText }}</span>
        </template>
        <template v-if="stockDisplayText">
          <span class="product-info-gap">{{ stockDisplayText }}</span>
        </template>
        <span class="product-info-gap"
          >单价：{{ formatUnitPriceYuan(item.selectedDrugPrice) }}</span
        >
        <span v-if="item.chrgitmLvText" class="chrgitm-lv-text product-info-gap">{{
          item.chrgitmLvText
        }}</span>
      </span>
      <div class="product-info-trailing">
        <div
          v-if="!hideTotalAmount"
          class="auto-calc-total-switch flex shrink-0 items-center gap-2 text-sm"
        >
          <span class="whitespace-nowrap text-[var(--el-text-color-secondary)]">自动计算总量</span>
          <ElSwitch v-model="autoCalcTotalAmount" />
        </div>
        <span class="product-total-amount">总金额：{{ formatMoneyStrYuan(calcTotalFee) }}</span>
      </div>
    </div>
    <div class="drug-form-row top-row">
      <div class="field-item">
        <span class="meta-label">费用性质</span>
        <ElSelect
          v-if="itemFeeNatureOptions.length > 1"
          :model-value="item.contractId"
          placeholder="请选择"
          @update:model-value="(value: unknown) => patchContract(String(value || ''))"
        >
          <ElOption
            v-for="opt in itemFeeNatureOptions"
            :key="String(opt.value ?? '')"
            :label="String(opt.label ?? opt.value ?? '')"
            :value="String(opt.value ?? '')"
          />
        </ElSelect>
        <span v-else class="fee-nature-readonly">{{ item.feeNature || '自费' }}</span>
      </div>
      <div class="field-item skin-test-item">
        <span class="meta-label">皮试</span>
        <ElCheckbox
          :model-value="Boolean(item.skinTest)"
          @update:model-value="
            (value: unknown) => formHandlers.setField('skinTest', Boolean(value))
          "
        />
      </div>
    </div>
    <!-- 单次用量、首日用量、用法、频次同一行；变动剂量紧跟频次 -->
    <div class="drug-form-row route-row">
      <div
        class="field-item dose-convert-item"
        :class="{ 'is-dose-locked': item.unevenDoseEnabled }"
      >
        <span class="meta-label">单次用量</span>
        <div class="dose-convert-wrap">
          <div class="dose-num-with-unit">
            <ElInputNumber
              class="dose-qty-input"
              :class="
                item.selectedId && !item.unevenDoseEnabled ? 'western-single-dose-primary' : ''
              "
              :disabled="Boolean(item.unevenDoseEnabled)"
              :model-value="item.singleDose ?? undefined"
              :min="0"
              :precision="2"
              :controls="false"
              @update:model-value="
                (value: unknown) => {
                  const v = parseOptionalNumberInput(value);
                  formHandlers.setField('singleDose', v as AdviceDrugItem['singleDose']);
                  if (v !== undefined && Number.isFinite(v)) {
                    formHandlers.applySplitDoseToDoseSide(v);
                  }
                  formHandlers.debouncedEnsureFrequencyCountAndRecalcTotal();
                }
              "
            />
            <span class="dose-unit-tail" :title="item.doseMainUnit || ''">{{
              item.doseMainUnit || '—'
            }}</span>
          </div>
          <span class="equals-sign">=</span>
          <div class="dose-num-with-unit">
            <ElInputNumber
              class="dose-qty-input"
              :disabled="Boolean(item.unevenDoseEnabled)"
              :model-value="doseDisplayValue(item.doseConvertRatio)"
              :min="0"
              :precision="3"
              :controls="false"
              @update:model-value="
                (value: unknown) => {
                  onDoseConvertRatioInput(
                    value,
                    item.doseConvertRatio,
                    (v) =>
                      formHandlers.setField(
                        'doseConvertRatio',
                        v as AdviceDrugItem['doseConvertRatio'],
                      ),
                    (v) => formHandlers.applyDoseQtyToSplitSide(v),
                  );
                  formHandlers.debouncedEnsureFrequencyCountAndRecalcTotal();
                }
              "
            />
            <span class="dose-unit-tail" :title="item.doseSubUnit || ''">{{
              item.doseSubUnit || '—'
            }}</span>
          </div>
        </div>
      </div>
      <!-- 长期：首日按计量单位录入；落库由草稿层换算为最小单位 -->
      <div v-if="hideTotalAmount" class="field-item first-dose-item">
        <span class="meta-label">首日用量</span>
        <div class="dose-num-with-unit">
          <ElInputNumber
            class="dose-qty-input"
            :model-value="item.firstDose ?? undefined"
            :min="0"
            :controls="false"
            placeholder="可选"
            @update:model-value="
              (value: unknown) => {
                const v = parseOptionalNumberInput(value);
                formHandlers.setField('firstDose', v as AdviceDrugItem['firstDose']);
                formHandlers.setField('firstDoseUnitCode', undefined);
              }
            "
          />
          <span class="dose-unit-tail dose-unit-tail--wide" :title="item.doseSubUnit || ''">{{
            item.doseSubUnit || '—'
          }}</span>
        </div>
      </div>
      <div class="field-item">
        <span class="meta-label">用法</span>
        <ElSelect
          :model-value="item.usage"
          filterable
          clearable
          placeholder="请选择"
          @update:model-value="
            (value: unknown) => formHandlers.setField('usage', String(value || ''))
          "
        >
          <ElOption
            v-for="opt in medicationRouteOptions"
            :key="String(opt.value ?? '')"
            :label="String(opt.label ?? opt.value ?? '')"
            :value="String(opt.value ?? '')"
          />
        </ElSelect>
      </div>
      <div class="field-item">
        <span class="meta-label">频次</span>
        <ElSelect
          :model-value="item.frequency"
          filterable
          clearable
          placeholder="请选择"
          @update:model-value="
            (value: unknown) => {
              formHandlers.setField('frequency', String(value || ''));
              formHandlers.debouncedRecalcFrequencyTotal();
            }
          "
        >
          <ElOption
            v-for="opt in medicationRateOptions"
            :key="String(opt.value ?? '')"
            :label="String(opt.label ?? opt.value ?? '')"
            :value="String(opt.value ?? '')"
          />
        </ElSelect>
      </div>
      <div v-if="canUnevenDose" class="field-item uneven-check-item">
        <span class="meta-label">变动剂量</span>
        <ElCheckbox
          :model-value="Boolean(item.unevenDoseEnabled)"
          @update:model-value="(value: unknown) => onUnevenDoseToggle(Boolean(value))"
        />
      </div>
      <div v-if="showSpeedField" class="field-item">
        <span class="meta-label">滴速</span>
        <ElSelect
          :model-value="item.speedCode"
          filterable
          clearable
          placeholder="请选择"
          @update:model-value="
            (value: unknown) => formHandlers.setField('speedCode', String(value || '') || undefined)
          "
        >
          <ElOption
            v-for="opt in medicationSpeedOptions ?? []"
            :key="String(opt.value ?? '')"
            :label="String(opt.label ?? opt.value ?? '')"
            :value="String(opt.value ?? '')"
          />
        </ElSelect>
      </div>
      <div v-if="!hideTotalAmount" class="field-item">
        <span class="meta-label">用药天数</span>
        <ElInputNumber
          :model-value="item.medicationDays ?? undefined"
          :min="0"
          :controls="false"
          @update:model-value="
            (value: unknown) => {
              formHandlers.setField(
                'medicationDays',
                parseOptionalNumberInput(value) as AdviceDrugItem['medicationDays'],
              );
              formHandlers.debouncedRecalcFrequencyTotal();
            }
          "
        />
      </div>
      <div v-if="!hideTotalAmount" class="field-item total-amount-item">
        <span class="meta-label">总量</span>
        <div class="total-amount-wrap">
          <ElInputNumber
            class="dose-qty-input"
            :model-value="item.totalAmount ?? undefined"
            :min="0"
            :controls="false"
            @update:model-value="
              (value: unknown) => formHandlers.handleTotalAmountManualInput(value)
            "
          />
          <ElSelect
            :model-value="item.totalAmountUnit"
            class="unit-select total-unit-select"
            placeholder="选药"
            @update:model-value="
              (value: unknown) => formHandlers.handleTotalAmountUnitManualSelect(value)
            "
          >
            <ElOption
              v-for="opt in totalUnitSelectOptions(item)"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </div>
      </div>
    </div>
    <!-- 分次剂量独占一行，跟在用法频次后面 -->
    <div
      v-if="hideTotalAmount && item.unevenDoseEnabled && unevenDoseSlotCount >= 2"
      class="drug-form-row schedule-row"
    >
      <div class="field-item schedule-field">
        <span class="meta-label">分次剂量</span>
        <div class="uneven-dose-slots">
          <div
            v-for="slotIndex in unevenDoseSlotIndexes"
            :key="`dose-slot-${slotIndex}`"
            class="uneven-slot-chip"
          >
            <span class="slot-ordinal">第{{ slotIndex + 1 }}次</span>
            <ElInputNumber
              class="dose-qty-input"
              :model-value="item.doseScheduleSlots?.[slotIndex] ?? undefined"
              :min="0"
              :controls="false"
              placeholder="必填"
              @update:model-value="(value: unknown) => onDoseScheduleSlotInput(slotIndex, value)"
            />
            <span class="dose-unit-tail dose-unit-tail--wide" :title="item.doseSubUnit || ''">{{
              item.doseSubUnit || '—'
            }}</span>
          </div>
        </div>
      </div>
    </div>
    <div class="drug-form-row remark-row">
      <div class="field-item remark-item">
        <span class="meta-label">备注</span>
        <ElInput
          clearable
          maxlength="200"
          show-word-limit
          placeholder="选填，护士站随医嘱显示"
          :model-value="item.remark ?? ''"
          @update:model-value="
            (v: string) => formHandlers.setField('remark', String(v ?? '') || undefined)
          "
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.advice-product-form-root {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.product-info-row {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px dashed #dbeafe;
  padding-bottom: 8px;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.product-info-label {
  font-size: 16px;
  font-weight: 700;
}

.product-text {
  color: #1f2937;
  font-size: 16px;
  font-weight: 700;
  flex: 1;
  min-width: 0;
}

.product-info-gap {
  margin-left: 12px;
}

/* 医保等级：仅展示甲/乙/自费等文案，醒目区分于品名单价 */
.chrgitm-lv-text {
  color: var(--el-color-warning);
  font-weight: 700;
}

.product-info-trailing {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.meta-label {
  color: #4b5563;
  font-size: 12px;
  white-space: nowrap;
}

.field-item > .meta-label {
  width: 64px;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 32px;
  text-align: right;
  padding-right: 8px;
}

.field-item > .meta-label::after {
  content: ':';
  margin-left: 2px;
}

/* 四字及以上标签按内容宽，避免折行 */
.dose-convert-item > .meta-label,
.schedule-field > .meta-label,
.uneven-check-item > .meta-label,
.route-row .first-dose-item > .meta-label {
  width: auto;
  min-width: 64px;
}

.product-total-amount {
  color: #0f766e;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
}

.drug-form-row {
  display: grid;
  align-items: center;
  width: 100%;
  max-width: 100%;
}

.top-row {
  grid-template-columns: minmax(220px, 0.9fr) minmax(100px, auto);
  gap: 10px 14px;
  margin-bottom: 10px;
  width: fit-content;
}

/* 分次剂量：独占一行，横向芯片排布 */
.schedule-row {
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
  margin-top: 10px;
  width: 100%;
}

.schedule-field {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  width: 100%;
}

.schedule-field > .meta-label {
  line-height: 32px;
}

.uneven-dose-slots {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
  min-width: 0;
  padding: 6px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}

.uneven-slot-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.slot-ordinal {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: var(--el-component-size, 32px);
  white-space: nowrap;
}

.schedule-field :deep(.dose-qty-input.el-input-number),
.field-item.first-dose-item :deep(.dose-qty-input.el-input-number) {
  width: 80px;
  max-width: 80px;
  flex: 0 0 80px;
}

/* 用法 / 频次 / 滴速（临嘱含天数总量） */
.route-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  width: 100%;
}

.route-row .field-item {
  flex: 0 1 200px;
  min-width: 160px;
  max-width: 240px;
}

/* 单次用量、首日用量、变动剂量按内容宽，不按下拉框宽度撑开 */
.route-row .dose-convert-item,
.route-row .uneven-check-item,
.route-row .first-dose-item {
  flex: 0 0 auto;
  min-width: 0;
  max-width: none;
}

.route-row .total-amount-item {
  flex: 0 1 260px;
  max-width: 280px;
}

.remark-row {
  grid-template-columns: minmax(280px, 1fr);
  gap: 10px;
  margin-top: 10px;
  width: 500px;
  max-width: 100%;
}

.remark-item {
  grid-template-columns: auto minmax(0, 1fr);
}

.field-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  column-gap: 8px;
  min-width: 0;
}

.uneven-check-item {
  grid-template-columns: auto auto;
}

.field-item :deep(.el-select),
.field-item :deep(.el-input-number) {
  width: 100%;
}

.skin-test-item :deep(.el-checkbox),
.uneven-check-item :deep(.el-checkbox) {
  display: flex;
  align-items: center;
  min-height: 32px;
}

/* 仅开立表单上的复选框加深边框，不作用到医嘱表格 */
.skin-test-item :deep(.el-checkbox__input:not(.is-disabled) .el-checkbox__inner),
.uneven-check-item :deep(.el-checkbox__input:not(.is-disabled) .el-checkbox__inner) {
  border-color: var(--el-text-color-regular);
}

.skin-test-item :deep(.el-checkbox__input.is-checked .el-checkbox__inner),
.uneven-check-item :deep(.el-checkbox__input.is-checked .el-checkbox__inner),
.skin-test-item :deep(.el-checkbox__input:not(.is-disabled):hover .el-checkbox__inner),
.uneven-check-item :deep(.el-checkbox__input:not(.is-disabled):hover .el-checkbox__inner) {
  border-color: var(--el-color-primary);
}

/* 变动剂量勾选后单次用量只读置灰 */
.dose-convert-item.is-dose-locked .dose-unit-tail,
.dose-convert-item.is-dose-locked .equals-sign {
  color: var(--el-text-color-disabled);
}

.dose-convert-item.is-dose-locked :deep(.el-input__inner) {
  color: var(--el-disabled-text-color);
  -webkit-text-fill-color: var(--el-disabled-text-color);
}

.dose-convert-wrap,
.total-amount-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.dose-num-with-unit {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.dose-convert-wrap :deep(.dose-qty-input),
.total-amount-wrap :deep(.dose-qty-input) {
  width: 86px;
  max-width: 86px;
  flex: 0 0 86px;
}

.dose-unit-tail {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: var(--el-component-size, 32px);
  user-select: none;
  white-space: nowrap;
  max-width: 4.5em;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dose-unit-tail--wide {
  max-width: 6em;
}

.total-amount-wrap .total-unit-select {
  width: 72px;
  min-width: 56px;
  max-width: 72px;
  flex: 0 0 72px;
}

.dose-convert-wrap .equals-sign {
  display: inline-flex;
  align-items: center;
  align-self: center;
  height: var(--el-component-size, 32px);
  line-height: 1;
  color: #6b7280;
  font-weight: 700;
  flex-shrink: 0;
}

@media (max-width: 1400px) {
  .route-row .field-item {
    flex: 1 1 220px;
    max-width: none;
  }

  .route-row .dose-convert-item,
  .route-row .uneven-check-item,
  .route-row .first-dose-item {
    flex: 0 0 auto;
    max-width: none;
  }

  .route-row .total-amount-item {
    flex: 1 1 100%;
    max-width: 320px;
  }
}
</style>
