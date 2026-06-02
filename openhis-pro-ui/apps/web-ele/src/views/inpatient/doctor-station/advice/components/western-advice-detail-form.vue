<script setup lang="ts">
import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';

import type { DiagnosisConfirmPayload, DiagnosisItem } from '#/components/doctor-station/diagnosis';
import type { AdviceDrugItem, AdviceDrugPatchPayload } from '../services/advice.types';

import { ref } from 'vue';

import { ElButton, ElCheckbox, ElInputNumber, ElMessage, ElOption, ElSelect } from 'element-plus';

import { DiagnosisPickerDialog } from '#/components/doctor-station/diagnosis';
import { formatMoneyStrYuan, formatUnitPriceYuan } from '../services/advice-format';
import {
  convertTotalAmountWhenUnitChanges,
  resolveAdviceLineUnitPrice,
  totalUnitSelectOptions,
} from '../services/medication-unit-bands';

const props = withDefaults(
  defineProps<{
    item: AdviceDrugItem;
    /** IF-DOR-18 频次选项（与父级 `initDoctorStationAdvice` 一致） */
    medicationRateOptions?: TerminologyOptionDto[];
    /** IF-DOR-18 用法选项 */
    medicationRouteOptions?: TerminologyOptionDto[];
    patientDiagnoses: DiagnosisItem[];
    calcTotalFee: (item: AdviceDrugItem) => string;
  }>(),
  {
    medicationRateOptions: () => [],
    medicationRouteOptions: () => [],
  },
);

const emit = defineEmits<{
  'update:patientDiagnoses': [value: DiagnosisItem[]];
  patch: [payload: AdviceDrugPatchPayload];
}>();

const patch = <K extends keyof AdviceDrugItem>(key: K, value: AdviceDrugItem[K]) => {
  emit('patch', { key, value });
};

function handleTotalAmountUnitChange(value: unknown) {
  const nu = String(value ?? '').trim();
  const it = props.item;
  const converted = convertTotalAmountWhenUnitChanges({
    oldUnit: String(it.totalAmountUnit ?? ''),
    newUnit: nu,
    bands: it.unitBands,
    splitCode: String(it.totalUnitSplitCode ?? ''),
    salesCode: String(it.totalUnitSalesCode ?? ''),
    splitToSalesK: it.totalAmountSplitToSalesK,
    currentAmount: Number(it.totalAmount),
  });
  if (converted !== undefined) {
    patch('totalAmount', converted);
  }
  patch('totalAmountUnit', nu);
  const linePrice = resolveAdviceLineUnitPrice({
    bands: it.unitBands,
    catalogUnitPrice: it.catalogUnitPrice,
    totalAmountUnit: nu,
  });
  if (linePrice !== undefined) {
    patch('selectedDrugPrice', linePrice);
  }
}

const diagnosisDialogVisible = ref(false);
const diagnosisDialogOpenByAuto = ref(false);
const diagnosisDialogConfirmed = ref(false);
const diagnosisAutoPromptMuted = ref(false);

function openDiagnosisDialog(byAuto = false) {
  diagnosisDialogOpenByAuto.value = byAuto;
  diagnosisDialogConfirmed.value = false;
  if (!byAuto) {
    diagnosisAutoPromptMuted.value = false;
  }
  diagnosisDialogVisible.value = true;
}

function handleDiagnosisFocus() {
  const current = props.item;
  if (
    props.patientDiagnoses.length === 0 &&
    !current.diagnosis &&
    !diagnosisAutoPromptMuted.value
  ) {
    openDiagnosisDialog(true);
  }
}

function handleDiagnosisConfirm(payload: DiagnosisConfirmPayload) {
  diagnosisDialogConfirmed.value = true;
  const exists = props.patientDiagnoses.some((item) => item.id === payload.selected.id);
  const next = exists ? props.patientDiagnoses : [...props.patientDiagnoses, payload.selected];
  emit('update:patientDiagnoses', next);
  patch('diagnosis', payload.selected.name);
  diagnosisAutoPromptMuted.value = false;
  if (payload.saveAsCommon) {
    ElMessage.success('诊断已保存为常用');
  }
}

function handleDiagnosisDialogVisibleChange(visible: boolean) {
  diagnosisDialogVisible.value = visible;
  if (visible) return;
  if (diagnosisDialogOpenByAuto.value && !diagnosisDialogConfirmed.value) {
    diagnosisAutoPromptMuted.value = true;
    ElMessage.info('当前患者尚未开立诊断，可点击诊断字段右侧“新增”直接开诊断');
  }
  diagnosisDialogOpenByAuto.value = false;
  diagnosisDialogConfirmed.value = false;
}
</script>

<template>
  <div class="advice-product-form-root">
    <div class="product-info-row">
      <span class="meta-label product-info-label">药品信息:</span>
      <span class="product-text">
        {{ item.selectedDrugName || '-' }}【{{ item.selectedDrugSpec || '-' }}】 单价：
        {{ formatUnitPriceYuan(item.selectedDrugPrice) }}
      </span>
      <span class="product-total-amount">总金额：{{ formatMoneyStrYuan(calcTotalFee(item)) }}</span>
    </div>
    <div class="drug-form-row top-row">
      <div class="field-item">
        <span class="meta-label">药房</span>
        <ElSelect
          :model-value="item.pharmacy"
          placeholder="请选择药房"
          @update:model-value="(value: unknown) => patch('pharmacy', String(value || ''))"
        >
          <ElOption label="住院西药房" value="住院西药房" />
          <ElOption label="门诊药房" value="门诊药房" />
        </ElSelect>
      </div>
      <div class="field-item">
        <span class="meta-label">诊断</span>
        <div class="diagnosis-field-wrap">
          <ElSelect
            :model-value="item.diagnosis"
            filterable
            clearable
            placeholder="请选择诊断"
            @focus="handleDiagnosisFocus"
            @update:model-value="(value: unknown) => patch('diagnosis', String(value || ''))"
          >
            <ElOption
              v-for="diagnosis in patientDiagnoses"
              :key="diagnosis.id"
              :label="diagnosis.name"
              :value="diagnosis.name"
            />
          </ElSelect>
          <ElButton link type="primary" @click="openDiagnosisDialog(false)">添加诊断</ElButton>
        </div>
      </div>
      <div class="field-item">
        <span class="meta-label">费用性质</span>
        <ElSelect
          :model-value="item.feeNature"
          placeholder="请选择"
          @update:model-value="(value: unknown) => patch('feeNature', String(value || ''))"
        >
          <ElOption label="自费" value="自费" />
          <ElOption label="医保" value="医保" />
        </ElSelect>
      </div>
      <div class="field-item skin-test-item">
        <span class="meta-label">皮试</span>
        <ElCheckbox
          :model-value="Boolean(item.skinTest)"
          @update:model-value="(value: unknown) => patch('skinTest', Boolean(value))"
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
              :model-value="item.singleDose"
              :min="0"
              :controls="false"
              @update:model-value="(value: unknown) => patch('singleDose', Number(value || 0))"
            />
            <span class="dose-unit-tail" :title="item.doseMainUnit || ''">{{
              item.doseMainUnit || '—'
            }}</span>
          </div>
          <span class="equals-sign">=</span>
          <div class="dose-num-with-unit">
            <ElInputNumber
              class="dose-qty-input"
              :model-value="item.doseConvertRatio"
              :min="0"
              :controls="false"
              @update:model-value="
                (value: unknown) => patch('doseConvertRatio', Number(value || 0))
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
          @update:model-value="(value: unknown) => patch('usage', String(value || ''))"
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
          @update:model-value="(value: unknown) => patch('frequency', String(value || ''))"
        >
          <ElOption
            v-for="opt in medicationRateOptions"
            :key="String(opt.value ?? '')"
            :label="String(opt.label ?? opt.value ?? '')"
            :value="String(opt.value ?? '')"
          />
        </ElSelect>
      </div>
      <div class="field-item">
        <span class="meta-label">用药天数</span>
        <ElInputNumber
          :model-value="item.medicationDays"
          :min="0"
          :controls="false"
          @update:model-value="(value: unknown) => patch('medicationDays', Number(value || 0))"
        />
      </div>
      <div class="field-item total-amount-item">
        <span class="meta-label">总量</span>
        <div class="total-amount-wrap">
          <ElInputNumber
            class="dose-qty-input"
            :model-value="item.totalAmount"
            :min="0"
            :controls="false"
            @update:model-value="(value: unknown) => patch('totalAmount', Number(value || 0))"
          />
          <ElSelect
            :model-value="item.totalAmountUnit"
            class="unit-select total-unit-select"
            placeholder="选药"
            @update:model-value="(value: unknown) => handleTotalAmountUnitChange(value)"
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

    <DiagnosisPickerDialog
      :model-value="diagnosisDialogVisible"
      :patient-diagnoses="patientDiagnoses"
      @update:model-value="handleDiagnosisDialogVisibleChange"
      @confirm="handleDiagnosisConfirm"
    />
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
  flex-shrink: 0;
  white-space: nowrap;
}

.drug-form-row {
  display: grid;
  align-items: end;
  width: fit-content;
  max-width: 100%;
}

.top-row {
  grid-template-columns: 220px 320px 220px 180px;
  gap: 10px 14px;
  margin-bottom: 10px;
}

.bottom-row {
  /* 用法列加宽；末列随总量+单位收缩 */
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
}

.diagnosis-field-wrap :deep(.el-select) {
  width: calc(100% - 48px);
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

.dose-convert-wrap {
  width: auto;
}

.total-amount-wrap {
  width: auto;
  flex-wrap: nowrap;
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
