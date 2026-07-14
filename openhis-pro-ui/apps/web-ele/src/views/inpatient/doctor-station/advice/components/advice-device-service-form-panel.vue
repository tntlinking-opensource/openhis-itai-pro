<script setup lang="ts">
import type { AdviceDrugItem } from '../services/advice.types';

import type { InpatientAdviceTherapyType } from '../services/advice.types';

import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';

import { computed, ref, watch } from 'vue';

import { ElInput, ElInputNumber, ElOption, ElSelect } from 'element-plus';

import { httpListInpatientActivityExecutionLocations } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import {
  loadAllDepartmentPerformOrgOptions,
  mapActivityExecutionRulesToPerformOrgOptions,
  mergeCurrentPerformOrgIntoOptions,
  type PerformOrgOption,
} from '#/views/doctor-station/advice-shared/perform-org-options.service';

import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';

import { parseOptionalNumberInput } from '../services/advice-edit';
import { formatMoneyStrYuan, formatUnitPriceYuan } from '../services/advice-format';
import type { AdviceLineEditorKind } from '../services/advice-row';
import {
  isInpatientLongTermMedicationTherapy,
  parseColonDefinitionId,
  parseProductTableNameFromSelectedId,
} from '../services/advice-row';
import { totalAmountUnitDisplayLabel } from '../services/medication-unit-bands';

defineOptions({ name: 'AdviceDeviceServiceFormPanel' });

const props = defineProps<{
  editorKind: Extract<AdviceLineEditorKind, 'DEVICE' | 'SERVICE'>;
  item: AdviceDrugItem;
  setField: <K extends keyof AdviceDrugItem>(key: K, value: AdviceDrugItem[K]) => void;
  /** 诊疗可执行科室接口门控：与就诊一致 */
  encounterId?: string;
  patientId?: string;
  /** IF-DOR-18 频次选项（诊疗与药品共用） */
  medicationRateOptions?: TerminologyOptionDto[];
  /** 住院长期诊疗：隐藏执行次数 */
  therapyType?: InpatientAdviceTherapyType | string;
}>();

const medicationRateOptions = computed(() => props.medicationRateOptions ?? []);

const hideServiceExecutionCount = computed(
  () =>
    props.editorKind === 'SERVICE' &&
    isInpatientLongTermMedicationTherapy(props.therapyType ?? props.item.therapyEnum),
);

const productInfoTitle = computed(() => (props.editorKind === 'DEVICE' ? '耗材信息' : '诊疗信息'));

const calcTotalFee = computed(() => {
  const item = props.item;
  const price = Number(item.selectedDrugPrice || 0);
  const amount =
    props.editorKind === 'SERVICE'
      ? Number(item.serviceQuantity ?? 0)
      : Number(item.totalAmount ?? 0);
  if (price <= 0 || amount <= 0) {
    return '0.00';
  }
  return formatMoneyDisplay(calcMoneyLineTotal(price, amount));
});

/** 诊疗数量后只读单位展示（与药品单次剂量 `dose-unit-tail` 一致）。 */
const serviceUnitLabel = computed(() => {
  const label = totalAmountUnitDisplayLabel(props.item).trim();
  return label || '—';
});

/** 耗材：目录带回的计量单位（单选项为主）。 */
const deviceUnitOptions = computed(() => {
  const code = String(props.item.totalAmountUnit ?? '').trim();
  if (!code) return [];
  const name =
    String(props.item.totalUnitSalesName ?? props.item.totalUnitSplitName ?? '').trim() || code;
  return [{ value: code, label: name }];
});

/** 接口返回的诊疗可执行科室（去重后）；无配置时回落全院科室。 */
const serviceExecutionOrgOptions = ref<PerformOrgOption[]>([]);
const serviceExecutionOrgLoading = ref(false);
let serviceFetchToken = 0;

/** 诊疗行：按就诊 + 诊疗定义拉取执行科室规则，填充下拉；耗材不请求。 */
async function refreshServiceExecutionOrgOptions() {
  const token = ++serviceFetchToken;
  serviceExecutionOrgOptions.value = [];
  if (props.editorKind !== 'SERVICE') return;

  const encounterId = String(props.encounterId ?? '').trim();
  const patientId = String(props.patientId ?? '').trim();
  const selectedId = String(props.item.selectedId ?? '').trim();
  const tblRaw =
    String(props.item.productTableName ?? '').trim() ||
    String(parseProductTableNameFromSelectedId(selectedId) ?? '').trim();
  const tbl = tblRaw.toLowerCase();
  if (tbl !== 'wkf_activity_definition' || !encounterId || !patientId) return;

  const actId = parseColonDefinitionId(selectedId);
  if (!actId || !/^\d+$/.test(actId)) return;

  serviceExecutionOrgLoading.value = true;
  try {
    const res = await httpListInpatientActivityExecutionLocations({
      encounterId,
      patientId,
      activityDefinitionId: actId,
    });
    if (token !== serviceFetchToken) return;

    const ruleOpts = mapActivityExecutionRulesToPerformOrgOptions(res.rules);
    let opts: PerformOrgOption[];
    if (ruleOpts.length > 0) {
      opts = ruleOpts;
    } else {
      opts = await loadAllDepartmentPerformOrgOptions();
    }
    serviceExecutionOrgOptions.value = opts;

    if (token !== serviceFetchToken) return;
    const cur = String(props.item.performOrgId ?? '').trim();
    if (opts.length === 1) {
      const only = opts[0]!;
      if (cur !== only.value) {
        props.setField('performOrgId', only.value as AdviceDrugItem['performOrgId']);
        props.setField('performOrgName', only.label as AdviceDrugItem['performOrgName']);
      }
    } else if (
      ruleOpts.length > 0 &&
      opts.length > 1 &&
      cur &&
      !opts.some((o) => o.value === cur)
    ) {
      const first = opts[0]!;
      props.setField('performOrgId', first.value as AdviceDrugItem['performOrgId']);
      props.setField('performOrgName', first.label as AdviceDrugItem['performOrgName']);
    }
  } catch {
    if (token === serviceFetchToken) {
      serviceExecutionOrgOptions.value = await loadAllDepartmentPerformOrgOptions();
    }
  } finally {
    if (token === serviceFetchToken) {
      serviceExecutionOrgLoading.value = false;
    }
  }
}

watch(
  () =>
    [
      props.editorKind,
      String(props.encounterId ?? '').trim(),
      String(props.patientId ?? '').trim(),
      String(props.item.selectedId ?? '').trim(),
      String(props.item.productTableName ?? '').trim(),
    ].join('|'),
  () => {
    void refreshServiceExecutionOrgOptions();
  },
  { immediate: true },
);

const performOrgSelectOptions = computed(() => {
  if (props.editorKind === 'SERVICE') {
    return mergeCurrentPerformOrgIntoOptions(
      serviceExecutionOrgOptions.value,
      props.item.performOrgId,
      props.item.performOrgName,
    );
  }
  const id = String(props.item.performOrgId ?? '').trim();
  const name = String(props.item.performOrgName ?? '').trim();
  if (!id) {
    return [];
  }
  return [{ value: id, label: name || id }];
});

/** 用户切换执行科室：同步展示名。 */
function onPerformOrgSelect(value: unknown) {
  const v = String(value ?? '').trim();
  if (!v) {
    props.setField('performOrgId', undefined);
    props.setField('performOrgName', undefined);
    return;
  }
  const opt = performOrgSelectOptions.value.find((o) => o.value === v);
  props.setField('performOrgId', v as AdviceDrugItem['performOrgId']);
  props.setField('performOrgName', (opt?.label ?? '') as AdviceDrugItem['performOrgName']);
}
</script>

<template>
  <div class="advice-product-form-root">
    <div class="product-info-row">
      <span class="meta-label product-info-label">{{ productInfoTitle }}:</span>
      <span class="product-text">
        {{ item.selectedDrugName || '-' }}【{{ item.selectedDrugSpec || '-' }}】 单价：
        {{ formatUnitPriceYuan(item.selectedDrugPrice) }}
      </span>
      <span class="product-total-amount">总金额：{{ formatMoneyStrYuan(calcTotalFee) }}</span>
    </div>

    <div v-if="editorKind === 'DEVICE'" class="drug-form-row simple-row device-qty-form">
      <div class="field-item total-amount-item">
        <span class="meta-label">数量</span>
        <div class="total-amount-wrap device-qty-form__inputs">
          <ElInputNumber
            class="dose-qty-input simple-line-qty-primary"
            :model-value="item.totalAmount ?? undefined"
            :min="0"
            :controls="false"
            @update:model-value="
              (value: unknown) =>
                setField(
                  'totalAmount',
                  parseOptionalNumberInput(value) as AdviceDrugItem['totalAmount'],
                )
            "
          />
          <ElSelect
            v-if="deviceUnitOptions.length > 0"
            :model-value="item.totalAmountUnit"
            class="unit-select total-unit-select device-qty-form__unit"
            placeholder="单位"
            @update:model-value="
              (value: unknown) => setField('totalAmountUnit', String(value ?? ''))
            "
          >
            <ElOption
              v-for="opt in deviceUnitOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <ElInput
            v-else
            class="unit-input device-qty-form__unit"
            :model-value="item.totalAmountUnit"
            placeholder="单位"
            @update:model-value="(v: string) => setField('totalAmountUnit', v)"
          />
        </div>
      </div>
    </div>

    <div v-else class="drug-form-row simple-row service-form-row">
      <div class="field-item service-qty-item">
        <span class="meta-label">数量</span>
        <div class="dose-num-with-unit">
          <ElInputNumber
            class="dose-qty-input simple-line-qty-primary"
            :model-value="item.serviceQuantity ?? undefined"
            :min="0"
            :controls="false"
            @update:model-value="
              (value: unknown) =>
                setField(
                  'serviceQuantity',
                  parseOptionalNumberInput(value) as AdviceDrugItem['serviceQuantity'],
                )
            "
          />
          <span class="dose-unit-tail" :title="serviceUnitLabel">{{ serviceUnitLabel }}</span>
        </div>
      </div>
      <div class="field-item">
        <span class="meta-label">频次</span>
        <ElSelect
          :model-value="item.frequency"
          filterable
          placeholder="请选择频次"
          @update:model-value="(value: unknown) => setField('frequency', String(value ?? ''))"
        >
          <ElOption
            v-for="opt in medicationRateOptions"
            :key="String(opt.value ?? '')"
            :label="String(opt.label ?? opt.value ?? '')"
            :value="String(opt.value ?? '')"
          />
        </ElSelect>
      </div>
      <div v-if="!hideServiceExecutionCount" class="field-item">
        <span class="meta-label">执行次数</span>
        <ElInputNumber
          class="dose-qty-input simple-line-qty-primary"
          :model-value="item.totalAmount ?? undefined"
          :min="0"
          :controls="false"
          @update:model-value="
            (value: unknown) =>
              setField(
                'totalAmount',
                parseOptionalNumberInput(value) as AdviceDrugItem['totalAmount'],
              )
          "
        />
      </div>
      <div class="field-item perform-org-item">
        <span class="meta-label">执行科室</span>
        <ElSelect
          :model-value="item.performOrgId || undefined"
          filterable
          clearable
          :loading="serviceExecutionOrgLoading"
          placeholder="请选择执行科室"
          @update:model-value="onPerformOrgSelect"
        >
          <ElOption
            v-for="opt in performOrgSelectOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
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

/* 仅耗材「数量+单位」行：限制整行宽度 + 收窄单位控件；上方信息区保持默认样式 */
.device-qty-form {
  max-width: 300px;
  width: fit-content;
}

.device-qty-form__inputs :deep(.dose-qty-input) {
  width: 80px;
  max-width: 80px;
  flex: 0 0 80px;
}

.device-qty-form__unit {
  width: 72px !important;
  min-width: 56px;
  max-width: 80px;
  flex: 0 0 72px;
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
  width: 72px;
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

.simple-row {
  grid-template-columns: minmax(220px, auto) minmax(240px, 1fr);
  gap: 10px 14px;
}

.service-form-row {
  grid-template-columns: minmax(120px, auto) minmax(200px, 1fr) minmax(160px, auto) minmax(
      240px,
      1fr
    );
}

.device-qty-form.simple-row {
  grid-template-columns: 1fr;
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

.service-qty-item :deep(.el-input-number) {
  width: auto;
}

.dose-num-with-unit {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  min-width: 0;
}

.dose-num-with-unit :deep(.dose-qty-input) {
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

.total-amount-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.total-amount-wrap :deep(.dose-qty-input) {
  width: 86px;
  max-width: 86px;
  flex: 0 0 86px;
}

.total-amount-wrap .total-unit-select,
.total-amount-wrap .unit-input {
  width: 120px;
  min-width: 72px;
  max-width: 160px;
  flex: 0 1 120px;
}

.perform-org-item {
  grid-column: 1 / -1;
}

@media (min-width: 900px) {
  .perform-org-item {
    grid-column: auto;
  }
}
</style>
