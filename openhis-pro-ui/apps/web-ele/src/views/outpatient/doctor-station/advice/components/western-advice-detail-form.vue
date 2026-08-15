<script setup lang="ts">
import type { AdviceDrugItem, AdviceDrugPatchPayload } from '../services/advice.types';

import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { DiagnosisConfirmPayload, DiagnosisItem } from '#/components/doctor-station/diagnosis';

import { computed, ref, watch } from 'vue';

import { ElCheckbox, ElInputNumber, ElMessage, ElOption, ElSelect } from 'element-plus';

import {
  AdviceDiagnosisBindPanel,
  DiagnosisPickerDialog,
} from '#/components/doctor-station/diagnosis';
import { formatAdviceDrugInfoStockText } from '#/utils/doctor-station/advice-catalog-stock-display';
import { formatPackagePerMinUnitText } from '#/utils/doctor-station/medication-package-min-unit-display';

import {
  contractSelectionFromValue,
  filterFeeNatureOptionsByChrgitmLv,
  formatMoneyStrYuan,
  formatUnitPriceYuan,
  type FeeNatureOption,
} from '../services/advice-format';
import {
  convertTotalAmountWhenUnitChanges,
  resolveAdviceLineUnitPrice,
  totalUnitSelectOptions,
} from '../services/medication-unit-bands';

defineOptions({ name: 'WesternAdviceDetailForm' });

const props = withDefaults(
  defineProps<{
    calcTotalFee: (item: AdviceDrugItem) => string;
    /** init 返回的费用性质（合同）下拉 */
    feeNatureOptions?: FeeNatureOption[];
    item: AdviceDrugItem;
    /** IF-DOR-18 频次选项（与父级 `initDoctorStationAdvice` 一致） */
    medicationRateOptions?: TerminologyOptionDto[];
    /** IF-DOR-18 用法选项 */
    medicationRouteOptions?: TerminologyOptionDto[];
    patientDiagnoses: DiagnosisItem[];
    encounterId?: string;
    patientId?: string;
  }>(),
  {
    medicationRateOptions: () => [],
    medicationRouteOptions: () => [],
    feeNatureOptions: () => [],
    encounterId: '',
    patientId: '',
  },
);

const emit = defineEmits<{
  patch: [payload: AdviceDrugPatchPayload];
  'update:patientDiagnoses': [value: DiagnosisItem[]];
  /** 选择诊断弹窗内删除就诊诊断后，通知父级刷新 */
  diagnosisDeleted: [encounterDiagnosisId: string];
  /** 选择诊断弹窗内切换主诊后，通知父级刷新 */
  diagnosisMainSwitched: [];
  /** 打开选择诊断弹窗时已拉取最新列表 */
  diagnosisOptionsRefreshed: [
    payload: { displayRows: EncounterDiagnosisRowDto[]; items: DiagnosisItem[] },
  ];
}>();

const patch = <K extends keyof AdviceDrugItem>(key: K, value: AdviceDrugItem[K]) => {
  emit('patch', { key, value });
};

/** 医保等级=自费时仅保留自费合同下拉 */
const itemFeeNatureOptions = computed(() =>
  filterFeeNatureOptionsByChrgitmLv(props.feeNatureOptions, props.item.chrgitmLv),
);

function patchContract(contractId: string) {
  const sel = contractSelectionFromValue(contractId, itemFeeNatureOptions.value);
  patch('contractId', sel.contractId);
  patch('feeNature', sel.feeNature);
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

/** 包装与最小单位不同时：「每盒30片」类文案 */
const packagePerMinUnitText = computed(() => formatPackagePerMinUnitText(props.item));

/** 选品库存：「库存：4盒6片」；无库存字段时为空 */
const stockDisplayText = computed(() => formatAdviceDrugInfoStockText(props.item));

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

function handleDiagnosisConfirm(payload: DiagnosisConfirmPayload) {
  diagnosisDialogConfirmed.value = true;
  const exists = props.patientDiagnoses.some((item) => item.id === payload.selected.id);
  const next = exists ? props.patientDiagnoses : [...props.patientDiagnoses, payload.selected];
  emit('update:patientDiagnoses', next);
  const eid = String(payload.selected.encounterDiagnosisId ?? '').trim();
  if (eid) {
    const raw = [...(props.item.encounterDiagnosisIds ?? []), eid];
    const mainIdSet = new Set(
      next
        .filter((d) => Number(d.mainDiagnosisFlag) === 1)
        .map((d) => String(d.encounterDiagnosisId ?? d.id ?? '').trim())
        .filter(Boolean),
    );
    const mains = raw.filter((id) => mainIdSet.has(id));
    const others = raw.filter((id) => !mainIdSet.has(id));
    const ordered = [...mains, ...others];
    const names = ordered
      .map((id) => {
        const hit = next.find((d) => String(d.encounterDiagnosisId ?? d.id ?? '').trim() === id);
        return String(hit?.name ?? '').trim();
      })
      .filter(Boolean);
    patch('encounterDiagnosisIds', ordered);
    patch('diagnosis', names.join('、'));
  } else {
    patch('diagnosis', payload.selected.name);
  }
  diagnosisAutoPromptMuted.value = false;
  if (payload.saveAsPersonal || payload.saveAsDepartment || payload.saveAsCommon) {
    ElMessage.success('诊断已保存为常用');
  }
}

/** 多选诊断：主诊优先，同步展示名。 */
function patchEncounterDiagnosisIds(value: unknown) {
  const raw = Array.isArray(value)
    ? value.map((id) => String(id ?? '').trim()).filter(Boolean)
    : [];
  const mainIdSet = new Set(
    props.patientDiagnoses
      .filter((d) => Number(d.mainDiagnosisFlag) === 1)
      .map((d) => String(d.encounterDiagnosisId ?? d.id ?? '').trim())
      .filter(Boolean),
  );
  const mains = raw.filter((id) => mainIdSet.has(id));
  const others = raw.filter((id) => !mainIdSet.has(id));
  const ordered = [...mains, ...others];
  const names = ordered
    .map((id) => {
      const hit = props.patientDiagnoses.find(
        (d) => String(d.encounterDiagnosisId ?? d.id ?? '').trim() === id,
      );
      return String(hit?.name ?? '').trim();
    })
    .filter(Boolean);
  patch('encounterDiagnosisIds', ordered);
  patch('diagnosis', names.join('、'));
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
          <AdviceDiagnosisBindPanel
            :selected-ids="item.encounterDiagnosisIds ?? []"
            :options="patientDiagnoses"
            :encounter-id="encounterId"
            :patient-id="patientId"
            @update:selected-ids="patchEncounterDiagnosisIds"
            @add-diagnosis="openDiagnosisDialog(false)"
            @deleted="(id) => emit('diagnosisDeleted', id)"
            @main-switched="() => emit('diagnosisMainSwitched')"
            @options-refreshed="(payload) => emit('diagnosisOptionsRefreshed', payload)"
          />
        </div>
      </div>
      <div class="field-item fee-nature-field">
        <span class="meta-label">费用性质</span>
        <!-- 单自费户或医保等级=自费：无下拉；双账户且非自费等级才可选医保/自费 -->
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

.product-info-gap {
  margin-left: 12px;
}

/* 医保等级：仅展示甲/乙/自费等文案，醒目区分于品名单价 */
.chrgitm-lv-text {
  color: var(--el-color-warning);
  font-weight: 700;
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
  grid-template-columns: 220px 320px auto auto;
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

.fee-nature-field :deep(.el-select) {
  width: 130px;
}

.diagnosis-field-wrap {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.diagnosis-field-wrap :deep(.dx-bind) {
  width: 100%;
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
