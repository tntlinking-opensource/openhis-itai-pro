<script setup lang="ts">
import type { AdviceLineEditorKind } from '../services/advice-row';
import type { AdviceDrugItem } from '../services/advice.types';

import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';
import type { PerformOrgOption } from '#/views/doctor-station/advice-shared/perform-org-options.service';

import { computed, ref, watch } from 'vue';

import { ElInput, ElInputNumber, ElOption, ElSelect } from 'element-plus';

import { AdviceDiagnosisBindPanel } from '#/components/doctor-station/diagnosis';
import { httpListActivityExecutionLocations } from '#/api/doctor-station/advice.http';
import {
  loadAllDepartmentPerformOrgOptions,
  mapActivityExecutionRulesToPerformOrgOptions,
  mergeCurrentPerformOrgIntoOptions,
} from '#/views/doctor-station/advice-shared/perform-org-options.service';

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
  parseColonDefinitionId,
  parseProductTableNameFromSelectedId,
} from '../services/advice-row';
import {
  convertTotalAmountWhenUnitChanges,
  resolveAdviceLineUnitPrice,
  totalAmountUnitDisplayLabel,
  totalUnitSelectOptions,
} from '../services/medication-unit-bands';
import { resolveServicePreviewBillQuantity } from '../services/service-quantity-calc';

defineOptions({ name: 'AdviceDeviceServiceFormPanel' });

const props = withDefaults(
  defineProps<{
    editorKind: Extract<AdviceLineEditorKind, 'DEVICE' | 'SERVICE'>;
    /** 诊疗可执行科室接口门控：与就诊一致 */
    encounterId?: string;
    /** init 返回的费用性质（合同）下拉 */
    feeNatureOptions?: FeeNatureOption[];
    item: AdviceDrugItem;
    /** 打开就诊诊断录入弹窗 */
    openDiagnosisDialog: () => void;
    patientDiagnoses?: DiagnosisItem[];
    patientId?: string;
    setField: <K extends keyof AdviceDrugItem>(key: K, value: AdviceDrugItem[K]) => void;
    /** 选择诊断弹窗内删除就诊诊断后回调 */
    onEncounterDiagnosisDeleted?: (deletedId: string) => void | Promise<void>;
    /** 选择诊断弹窗内切换主诊后回调 */
    onEncounterDiagnosisMainSwitched?: () => void | Promise<void>;
    /** 打开选择诊断弹窗时已拉取最新列表，同步父级候选项 */
    onEncounterDiagnosisOptionsRefreshed?: (payload: {
      displayRows: EncounterDiagnosisRowDto[];
      items: DiagnosisItem[];
    }) => void;
  }>(),
  {
    encounterId: '',
    feeNatureOptions: () => [],
    patientDiagnoses: () => [],
    patientId: '',
    onEncounterDiagnosisDeleted: undefined,
    onEncounterDiagnosisMainSwitched: undefined,
    onEncounterDiagnosisOptionsRefreshed: undefined,
  },
);

/** 医保等级=自费时仅保留自费合同下拉 */
const itemFeeNatureOptions = computed(() =>
  filterFeeNatureOptionsByChrgitmLv(props.feeNatureOptions, props.item.chrgitmLv),
);

/** 用户切换费用性质：同步合同主键与展示名 */
function patchContract(contractId: string) {
  const sel = contractSelectionFromValue(contractId, itemFeeNatureOptions.value);
  props.setField('contractId', sel.contractId as AdviceDrugItem['contractId']);
  props.setField('feeNature', sel.feeNature as AdviceDrugItem['feeNature']);
}

const productInfoTitle = computed(() => (props.editorKind === 'DEVICE' ? '耗材信息' : '诊疗信息'));

const calcTotalFee = computed(() => {
  const item = props.item;
  const price = Number(item.selectedDrugPrice || 0);
  // 诊疗：单价 × 单次执行数量 × 执行次数；耗材：单价 × 数量
  const amount =
    props.editorKind === 'SERVICE'
      ? resolveServicePreviewBillQuantity(item.serviceQuantity, item.totalAmount)
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

/** 耗材：计量带单位下拉（包装/售卖/拆零）；无带时回落当前单位单选项。 */
const deviceUnitOptions = computed(() => {
  const fromBands = totalUnitSelectOptions(props.item);
  if (fromBands.length > 0) return fromBands;
  const code = String(props.item.totalAmountUnit ?? '').trim();
  if (!code) return [];
  const name =
    String(
      props.item.totalUnitSplitName ??
        props.item.totalUnitSalesName ??
        props.item.totalUnitPackageName ??
        '',
    ).trim() || code;
  return [{ value: code, label: name }];
});

/** 耗材切换单位：换算数量并按目标单位刷新行单价。 */
function onDeviceTotalAmountUnitSelect(value: unknown) {
  const nu = String(value ?? '').trim();
  const item = props.item;
  if (!nu || nu === String(item.totalAmountUnit ?? '').trim()) return;
  const converted = convertTotalAmountWhenUnitChanges({
    oldUnit: String(item.totalAmountUnit ?? ''),
    newUnit: nu,
    bands: item.unitBands,
    splitCode: String(item.totalUnitSplitCode ?? ''),
    salesCode: String(item.totalUnitSalesCode ?? ''),
    splitToSalesK: item.totalAmountSplitToSalesK,
    currentAmount: Number(item.totalAmount ?? Number.NaN),
  });
  if (converted !== undefined) {
    props.setField('totalAmount', converted as AdviceDrugItem['totalAmount']);
  }
  props.setField('totalAmountUnit', nu as AdviceDrugItem['totalAmountUnit']);
  const linePrice = resolveAdviceLineUnitPrice({
    bands: item.unitBands,
    catalogUnitPrice: item.catalogUnitPrice,
    totalAmountUnit: nu,
  });
  if (linePrice !== undefined) {
    props.setField('selectedDrugPrice', linePrice as AdviceDrugItem['selectedDrugPrice']);
  }
}

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
    const res = await httpListActivityExecutionLocations({
      encounterId,
      patientId,
      activityDefinitionId: actId,
    });
    if (token !== serviceFetchToken) return;

    const ruleOpts = mapActivityExecutionRulesToPerformOrgOptions(res.rules);
    const opts = ruleOpts.length > 0 ? ruleOpts : await loadAllDepartmentPerformOrgOptions();
    serviceExecutionOrgOptions.value = opts;

    if (token !== serviceFetchToken) return;
    const cur = String(props.item.performOrgId ?? '').trim();
    if (opts.length === 1) {
      const only = opts[0];
      if (!only) {
        return;
      }
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
      const first = opts[0];
      if (!first) {
        return;
      }
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

/** 绑定诊断：主诊优先排序，并同步展示名。 */
function patchEncounterDiagnosisIds(value: unknown) {
  const raw = Array.isArray(value)
    ? value.map((id) => String(id ?? '').trim()).filter(Boolean)
    : [];
  const diagnoses = props.patientDiagnoses ?? [];
  const mainIdSet = new Set(
    diagnoses
      .filter((d) => Number(d.mainDiagnosisFlag) === 1)
      .map((d) => String(d.encounterDiagnosisId ?? d.id ?? '').trim())
      .filter(Boolean),
  );
  const mains = raw.filter((id) => mainIdSet.has(id));
  const others = raw.filter((id) => !mainIdSet.has(id));
  const ordered = [...mains, ...others];
  const names = ordered
    .map((id) => {
      const hit = diagnoses.find((d) => String(d.encounterDiagnosisId ?? d.id ?? '').trim() === id);
      return String(hit?.name ?? '').trim();
    })
    .filter(Boolean);
  props.setField('encounterDiagnosisIds', ordered);
  props.setField('diagnosis', names.join('、'));
}
</script>

<template>
  <div class="advice-product-form-root">
    <div class="product-info-row">
      <span class="meta-label product-info-label">{{ productInfoTitle }}:</span>
      <span class="product-text">
        {{ item.selectedDrugName || '-' }}【{{ item.selectedDrugSpec || '-' }}】 单价：
        {{ formatUnitPriceYuan(item.selectedDrugPrice) }}
        <span v-if="item.chrgitmLvText" class="chrgitm-lv-text"
          >&nbsp;{{ item.chrgitmLvText }}</span
        >
      </span>
      <span class="product-total-amount">总金额：{{ formatMoneyStrYuan(calcTotalFee) }}</span>
    </div>

    <!-- 耗材不绑定诊断；诊疗诊断区限宽，避免占满整行 -->
    <div v-if="editorKind === 'SERVICE'" class="drug-form-row top-row diagnosis-row">
      <div class="field-item diagnosis-field-item">
        <span class="meta-label">诊断</span>
        <div class="diagnosis-field-wrap">
          <AdviceDiagnosisBindPanel
            :selected-ids="item.encounterDiagnosisIds ?? []"
            :options="patientDiagnoses ?? []"
            :encounter-id="encounterId"
            :patient-id="patientId"
            @update:selected-ids="patchEncounterDiagnosisIds"
            @add-diagnosis="openDiagnosisDialog()"
            @deleted="(id) => void props.onEncounterDiagnosisDeleted?.(id)"
            @main-switched="() => void props.onEncounterDiagnosisMainSwitched?.()"
            @options-refreshed="(payload) => props.onEncounterDiagnosisOptionsRefreshed?.(payload)"
          />
        </div>
      </div>
      <div class="field-item fee-nature-field">
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
    </div>

    <div v-if="editorKind === 'DEVICE'" class="drug-form-row simple-row device-qty-form">
      <div class="field-item fee-nature-field">
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
            @update:model-value="onDeviceTotalAmountUnitSelect"
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

    <div v-else class="drug-form-row simple-row">
      <div class="field-item service-qty-item">
        <span class="meta-label">单次执行数量</span>
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

/* 耗材：费用性质 + 数量行；上方信息区保持默认样式 */
.device-qty-form {
  max-width: 460px;
  width: fit-content;
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 8px 12px;
}

.fee-nature-field :deep(.el-select) {
  width: 130px;
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
  width: 96px;
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

.top-row.diagnosis-row {
  grid-template-columns: minmax(260px, 420px);
  gap: 10px 14px;
  margin-bottom: 10px;
  width: fit-content;
  max-width: 100%;
}

.diagnosis-field-wrap {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
  width: 100%;
}

.diagnosis-field-item {
  min-width: 0;
  max-width: 420px;
}

.diagnosis-field-wrap :deep(.dx-bind) {
  flex: 1;
  min-width: 120px;
  max-width: 100%;
}

.simple-row {
  grid-template-columns: minmax(120px, auto) minmax(120px, auto) minmax(240px, 1fr);
  gap: 10px 14px;
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
