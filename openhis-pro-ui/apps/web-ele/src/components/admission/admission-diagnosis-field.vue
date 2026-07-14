<script setup lang="ts">
import type { OptionDto } from '#/api/inpatient-registration';

import { computed, ref, watch } from 'vue';

import { ElInput, ElOption, ElRadio, ElRadioGroup, ElSelect, ElTag } from 'element-plus';

import type {
  AdmissionDiagnosisFormState,
  AdmissionDiagnosisMedicineType,
} from './admission-diagnosis.types';
import { createEmptyAdmissionDiagnosisFormState } from './admission-diagnosis.types';

import { httpPageTcmConditionDefinitionCatalog } from '#/api/doctor-station/tcm.http';
import {
  formatTcmDiagnosisDisplayName,
  mapTcmCatalogRowToConditionDefinitionRow,
  resolveTcmCatalogRole,
  type TcmCatalogKind,
} from '#/components/doctor-station/diagnosis/services/tcm-diagnosis-display';

defineOptions({ name: 'AdmissionDiagnosisField' });

const props = withDefaults(
  defineProps<{
    modelValue: AdmissionDiagnosisFormState;
    westernOptions?: OptionDto[];
    disabled?: boolean;
    readonly?: boolean;
    showDescription?: boolean;
  }>(),
  {
    westernOptions: () => [],
    disabled: false,
    readonly: false,
    showDescription: true,
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: AdmissionDiagnosisFormState];
}>();

const tcmDiseaseOptions = ref<Array<{ label: string; value: string }>>([]);
const tcmSyndromeOptions = ref<Array<{ label: string; value: string }>>([]);
const tcmDiseaseLoading = ref(false);
const tcmSyndromeLoading = ref(false);

const westernSelectOptions = computed(() =>
  (props.westernOptions ?? []).map((item) => ({
    label: item.label,
    value: String(item.value),
  })),
);

const isTcmMode = computed(() => props.modelValue.medicineType === 'tcm');

const readonlyText = computed(() => props.modelValue.readonlyDisplay?.trim() || '—');

const tcmSummaryText = computed(() =>
  formatTcmDiagnosisDisplayName(props.modelValue.tcmDiseaseName, props.modelValue.tcmSyndromeName),
);

const showTcmSummary = computed(
  () =>
    isTcmMode.value &&
    Boolean(props.modelValue.tcmDiseaseDefinitionId) &&
    Boolean(props.modelValue.tcmSyndromeDefinitionId) &&
    Boolean(tcmSummaryText.value),
);

/** 局部更新表单状态并同步 v-model */
function patchModelValue(patch: Partial<AdmissionDiagnosisFormState>) {
  emit('update:modelValue', { ...props.modelValue, ...patch });
}

/** 远程检索中医疾病目录 */
function handleTcmDiseaseRemoteSearch(keyword: string) {
  void loadTcmCatalogOptions('disease', keyword);
}

/** 远程检索中医证候目录 */
function handleTcmSyndromeRemoteSearch(keyword: string) {
  void loadTcmCatalogOptions('syndrome', keyword);
}

/** 切换中西医类型（radio 回调） */
function handleMedicineTypeRadioChange(val: string | number | boolean | undefined) {
  handleMedicineTypeChange(String(val) as AdmissionDiagnosisMedicineType);
}

/** 切换中西医时清空另一侧候选 */
function handleMedicineTypeChange(next: AdmissionDiagnosisMedicineType) {
  patchModelValue({
    ...createEmptyAdmissionDiagnosisFormState(),
    medicineType: next,
    diagnosisDescription: props.modelValue.diagnosisDescription,
    conditionId: props.modelValue.conditionId,
    readonlyDisplay: props.modelValue.readonlyDisplay,
  });
  if (next === 'tcm') {
    void loadTcmCatalogOptions('disease', '');
    void loadTcmCatalogOptions('syndrome', '');
  }
}

/** 拉取中医疾病/证候目录（登记场景无就诊上下文，仅读目录） */
async function loadTcmCatalogOptions(catalogKind: TcmCatalogKind, keyword: string) {
  const loadingRef = catalogKind === 'disease' ? tcmDiseaseLoading : tcmSyndromeLoading;
  const optionsRef = catalogKind === 'disease' ? tcmDiseaseOptions : tcmSyndromeOptions;
  loadingRef.value = true;
  try {
    const res = await httpPageTcmConditionDefinitionCatalog({
      catalogRole: resolveTcmCatalogRole(catalogKind),
      keyword: keyword.trim() || undefined,
      pageNo: 1,
      pageSize: 50,
    });
    const loaded = (res.rows ?? [])
      .map(mapTcmCatalogRowToConditionDefinitionRow)
      .filter((row) => row.conditionDefinitionId && row.conditionName)
      .map((row) => ({
        label: String(row.conditionName),
        value: String(row.conditionDefinitionId),
      }));
    if (catalogKind === 'disease') {
      optionsRef.value = mergeTcmPrefillOption(
        loaded,
        props.modelValue.tcmDiseaseDefinitionId,
        props.modelValue.tcmDiseaseName,
      );
    } else {
      optionsRef.value = mergeTcmPrefillOption(
        loaded,
        props.modelValue.tcmSyndromeDefinitionId,
        props.modelValue.tcmSyndromeName,
      );
    }
  } finally {
    loadingRef.value = false;
  }
}

/** 选中中医疾病后清空证候并刷新证候目录 */
function handleTcmDiseaseChange(value: string) {
  const hit = tcmDiseaseOptions.value.find((item) => item.value === value);
  patchModelValue({
    tcmDiseaseDefinitionId: value,
    tcmDiseaseName: hit?.label ?? '',
    tcmSyndromeDefinitionId: '',
    tcmSyndromeName: '',
  });
  void loadTcmCatalogOptions('syndrome', '');
}

/** 选中中医证候 */
function handleTcmSyndromeChange(value: string) {
  const hit = tcmSyndromeOptions.value.find((item) => item.value === value);
  patchModelValue({
    tcmSyndromeDefinitionId: value,
    tcmSyndromeName: hit?.label ?? '',
  });
}

/** 预填值写入中医下拉，避免远程目录未加载时 ElSelect 空白 */
function mergeTcmPrefillOption(
  options: Array<{ label: string; value: string }>,
  definitionId: string,
  name: string,
) {
  const value = definitionId.trim();
  const label = name.trim();
  if (!value || !label) {
    return options;
  }
  if (options.some((item) => item.value === value)) {
    return options;
  }
  return [{ label, value }, ...options];
}

/** 同步中医预填项到本地下拉候选 */
function syncTcmPrefillOptions(state: AdmissionDiagnosisFormState) {
  if (props.readonly) {
    return;
  }
  tcmDiseaseOptions.value = mergeTcmPrefillOption(
    tcmDiseaseOptions.value,
    state.tcmDiseaseDefinitionId,
    state.tcmDiseaseName,
  );
  tcmSyndromeOptions.value = mergeTcmPrefillOption(
    tcmSyndromeOptions.value,
    state.tcmSyndromeDefinitionId,
    state.tcmSyndromeName,
  );
}

watch(
  () => props.modelValue.medicineType,
  (type) => {
    if (type === 'tcm' && !props.readonly) {
      syncTcmPrefillOptions(props.modelValue);
      void loadTcmCatalogOptions('disease', '');
    }
  },
  { immediate: true },
);

watch(
  () => [
    props.modelValue.tcmDiseaseDefinitionId,
    props.modelValue.tcmDiseaseName,
    props.modelValue.tcmSyndromeDefinitionId,
    props.modelValue.tcmSyndromeName,
    props.readonly,
  ],
  () => {
    if (props.modelValue.medicineType === 'tcm' && !props.readonly) {
      syncTcmPrefillOptions(props.modelValue);
    }
  },
  { immediate: true },
);
</script>

<template>
  <div class="admission-diagnosis-field">
    <div v-if="readonly" class="admission-diagnosis-field__readonly">
      {{ readonlyText }}
    </div>

    <div v-else class="admission-diagnosis-field__panel">
      <div class="admission-diagnosis-field__type-row">
        <ElRadioGroup
          class="admission-diagnosis-field__type-group"
          :model-value="modelValue.medicineType"
          :disabled="disabled"
          @change="handleMedicineTypeRadioChange"
        >
          <ElRadio label="western">西医</ElRadio>
          <ElRadio label="tcm">中医</ElRadio>
        </ElRadioGroup>
        <ElTag
          v-if="showTcmSummary"
          type="info"
          effect="plain"
          class="admission-diagnosis-field__summary"
        >
          {{ tcmSummaryText }}
        </ElTag>
      </div>

      <div v-if="!isTcmMode" class="admission-diagnosis-field__picker-row">
        <ElSelect
          :model-value="modelValue.westernDefinitionId"
          filterable
          clearable
          placeholder="请选择西医入院诊断"
          class="admission-diagnosis-field__control"
          :disabled="disabled"
          @update:model-value="(val) => patchModelValue({ westernDefinitionId: String(val ?? '') })"
        >
          <ElOption
            v-for="item in westernSelectOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </div>

      <div v-else class="admission-diagnosis-field__tcm-grid">
        <div class="admission-diagnosis-field__tcm-item">
          <span class="admission-diagnosis-field__field-label">疾病</span>
          <ElSelect
            :model-value="modelValue.tcmDiseaseDefinitionId"
            filterable
            remote
            reserve-keyword
            clearable
            placeholder="检索并选择疾病"
            class="admission-diagnosis-field__control"
            :disabled="disabled"
            :loading="tcmDiseaseLoading"
            :remote-method="handleTcmDiseaseRemoteSearch"
            @update:model-value="(val) => handleTcmDiseaseChange(String(val ?? ''))"
          >
            <ElOption
              v-for="item in tcmDiseaseOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </div>
        <div class="admission-diagnosis-field__tcm-item">
          <span class="admission-diagnosis-field__field-label">证候</span>
          <ElSelect
            :model-value="modelValue.tcmSyndromeDefinitionId"
            filterable
            remote
            reserve-keyword
            clearable
            placeholder="检索并选择证候"
            class="admission-diagnosis-field__control"
            :disabled="disabled || !modelValue.tcmDiseaseDefinitionId"
            :loading="tcmSyndromeLoading"
            :remote-method="handleTcmSyndromeRemoteSearch"
            @update:model-value="(val) => handleTcmSyndromeChange(String(val ?? ''))"
          >
            <ElOption
              v-for="item in tcmSyndromeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </div>
      </div>

      <div v-if="showDescription" class="admission-diagnosis-field__desc-row">
        <span class="admission-diagnosis-field__field-label">诊断描述</span>
        <ElInput
          :model-value="modelValue.diagnosisDescription"
          placeholder="可选"
          class="admission-diagnosis-field__desc-input"
          :disabled="disabled"
          @update:model-value="
            (val) => patchModelValue({ diagnosisDescription: String(val ?? '') })
          "
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.admission-diagnosis-field {
  width: 100%;
}

.admission-diagnosis-field__readonly {
  box-sizing: border-box;
  min-height: 32px;
  padding: 0 11px;
  border: 1px solid var(--el-disabled-border-color);
  border-radius: var(--el-border-radius-base);
  background-color: var(--el-disabled-bg-color);
  color: var(--el-disabled-text-color);
  font-size: var(--el-font-size-base);
  line-height: 30px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admission-diagnosis-field__panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.admission-diagnosis-field__type-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 16px;
}

.admission-diagnosis-field__type-group {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.admission-diagnosis-field__type-group :deep(.el-radio) {
  height: 24px;
  margin-right: 0;
}

.admission-diagnosis-field__summary {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}

.admission-diagnosis-field__picker-row,
.admission-diagnosis-field__tcm-grid,
.admission-diagnosis-field__desc-row {
  display: grid;
  gap: 8px;
}

.admission-diagnosis-field__tcm-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.admission-diagnosis-field__tcm-item,
.admission-diagnosis-field__desc-row {
  display: grid;
  gap: 6px;
  align-content: start;
}

.admission-diagnosis-field__desc-row {
  grid-template-columns: 56px minmax(0, 1fr);
  align-items: center;
  padding-top: 2px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.admission-diagnosis-field__field-label {
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.admission-diagnosis-field__control,
.admission-diagnosis-field__desc-input {
  width: 100%;
}

@media (max-width: 720px) {
  .admission-diagnosis-field__tcm-grid {
    grid-template-columns: 1fr;
  }

  .admission-diagnosis-field__desc-row {
    grid-template-columns: 1fr;
    align-items: stretch;
  }
}
</style>
