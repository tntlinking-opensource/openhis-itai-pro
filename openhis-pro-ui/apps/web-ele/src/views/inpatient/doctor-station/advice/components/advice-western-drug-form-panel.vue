<script setup lang="ts">
import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';
import type { AdviceDrugItem } from '../services/advice.types';

import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';

import { computed } from 'vue';

import { ElButton, ElCheckbox, ElInputNumber, ElOption, ElSelect, ElSwitch } from 'element-plus';

import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';

import { parseOptionalNumberInput } from '../services/advice-edit';
import {
  contractSelectionFromValue,
  formatMoneyStrYuan,
  formatUnitPriceYuan,
} from '../services/advice-format';
import { totalUnitSelectOptions } from '../services/medication-unit-bands';

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
  handleDiagnosisFocus: () => void;
  openDiagnosisDialog: () => void;
}

const props = defineProps<{
  formHandlers: WesternDrugFormHandlers;
  /** 长期医嘱：隐藏总量与自动推算总量 */
  hideTotalAmount?: boolean;
  item: AdviceDrugItem;
  feeNatureOptions: TerminologyOptionDto[];
  medicationRateOptions: TerminologyOptionDto[];
  medicationRouteOptions: TerminologyOptionDto[];
  patientDiagnoses: DiagnosisItem[];
}>();

function patchContract(contractId: string) {
  const sel = contractSelectionFromValue(contractId, props.feeNatureOptions);
  props.formHandlers.setField('contractId', sel.contractId);
  props.formHandlers.setField('feeNature', sel.feeNature);
}

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
</script>

<template>
  <div class="advice-product-form-root">
    <div class="product-info-row">
      <span class="meta-label product-info-label">药品信息:</span>
      <span class="product-text">
        {{ item.selectedDrugName || '-' }}【{{ item.selectedDrugSpec || '-' }}】 单价：
        {{ formatUnitPriceYuan(item.selectedDrugPrice) }}
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
      <div class="field-item diagnosis-field-item">
        <span class="meta-label">诊断</span>
        <div class="diagnosis-field-wrap">
          <ElSelect
            :model-value="item.diagnosis"
            filterable
            clearable
            placeholder="请选择诊断"
            @focus="formHandlers.handleDiagnosisFocus()"
            @update:model-value="
              (value: unknown) => formHandlers.setField('diagnosis', String(value || ''))
            "
          >
            <ElOption
              v-for="diagnosis in patientDiagnoses"
              :key="diagnosis.id"
              :label="diagnosis.name"
              :value="diagnosis.name"
            />
          </ElSelect>
          <ElButton link type="primary" @click="formHandlers.openDiagnosisDialog()">
            添加诊断
          </ElButton>
        </div>
      </div>
      <div class="field-item">
        <span class="meta-label">费用性质</span>
        <ElSelect
          :model-value="item.contractId"
          placeholder="请选择"
          @update:model-value="(value: unknown) => patchContract(String(value || ''))"
        >
          <ElOption
            v-for="opt in feeNatureOptions"
            :key="String(opt.value ?? '')"
            :label="String(opt.label ?? opt.value ?? '')"
            :value="String(opt.value ?? '')"
          />
        </ElSelect>
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
    <div class="drug-form-row bottom-row">
      <div class="field-item dose-convert-item">
        <span class="meta-label">单次用量</span>
        <div class="dose-convert-wrap">
          <div class="dose-num-with-unit">
            <ElInputNumber
              class="dose-qty-input"
              :class="item.selectedId ? 'western-single-dose-primary' : ''"
              :model-value="item.singleDose ?? undefined"
              :min="0"
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
              :model-value="item.doseConvertRatio ?? undefined"
              :min="0"
              :controls="false"
              @update:model-value="
                (value: unknown) => {
                  const v = parseOptionalNumberInput(value);
                  formHandlers.setField(
                    'doseConvertRatio',
                    v as AdviceDrugItem['doseConvertRatio'],
                  );
                  if (v !== undefined && Number.isFinite(v)) {
                    formHandlers.applyDoseQtyToSplitSide(v);
                  }
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

.product-total-amount {
  color: #0f766e;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
}

.drug-form-row {
  display: grid;
  align-items: end;
  width: fit-content;
  max-width: 100%;
}

.top-row {
  grid-template-columns: minmax(260px, 1fr) minmax(220px, 0.9fr) minmax(100px, auto);
  gap: 10px 14px;
  margin-bottom: 10px;
}

.bottom-row {
  grid-template-columns: auto 220px 200px 170px auto;
  gap: 10px 10px;
}

.field-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  column-gap: 8px;
  min-width: 0;
}

.field-item :deep(.el-select),
.field-item :deep(.el-input-number) {
  width: 100%;
}

.diagnosis-field-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.diagnosis-field-item {
  min-width: 0;
}

.diagnosis-field-wrap :deep(.el-select) {
  flex: 1;
  min-width: 120px;
}

.skin-test-item :deep(.el-checkbox) {
  display: flex;
  align-items: center;
  min-height: 32px;
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
  .bottom-row {
    grid-template-columns: repeat(2, minmax(260px, 1fr));
  }

  .dose-convert-item {
    grid-column: 1 / -1;
  }

  .total-amount-item {
    grid-column: 1 / -1;
    max-width: 320px;
  }
}
</style>
