<script setup lang="ts">
import type { OptionDto } from '#/api/inpatient-registration';

import { computed, ref, watch } from 'vue';

import { ElInput, ElOption, ElRadio, ElRadioGroup, ElSelect, ElTag } from 'element-plus';

import type {
  AdmissionDiagnosisFormState,
  AdmissionDiagnosisMedicineType,
} from './admission-diagnosis.types';
import { createEmptyAdmissionDiagnosisFormState } from './admission-diagnosis.types';

import { httpGetConditionDefinitionMetadata } from '#/api/doctor-station/diagnosis.http';
import { httpPageTcmConditionDefinitionCatalog } from '#/api/doctor-station/tcm.http';
import {
  formatTcmDiagnosisDisplayName,
  mapTcmCatalogRowToConditionDefinitionRow,
  resolveTcmCatalogRole,
  type TcmCatalogKind,
} from '#/components/doctor-station/diagnosis/services/tcm-diagnosis-display';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'AdmissionDiagnosisField' });

/** 西医/中医目录远程下拉每页条数（目录总量远大于此，须走关键字检索而非一次灌全） */
const CATALOG_REMOTE_PAGE_SIZE = 50;

const props = withDefaults(
  defineProps<{
    modelValue: AdmissionDiagnosisFormState;
    /** 父级预填的西医选项（仅用于当前选中项回显，不再作为全量目录） */
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
/** 西医目录远程检索结果（不含父级预填，避免把第一页误当全量） */
const westernRemoteOptions = ref<Array<{ label: string; value: string }>>([]);
/** 当前已选西医项缓存，检索替换列表后仍能回显 */
const westernPickedOption = ref<{ label: string; value: string } | null>(null);
/** 丢弃过期的西医远程检索响应 */
const westernSearchSeq = ref(0);

const isTcmMode = computed(() => props.modelValue.medicineType === 'tcm');

/** 西医下拉：远程页 + 已选项 + 父级预填（仅补当前选中 ID 的 label） */
const westernSelectOptions = computed(() => {
  const byValue = new Map<string, { label: string; value: string }>();
  // 远程检索页作为当前可见候选，不把父级全量第一页灌进来
  for (const item of westernRemoteOptions.value) {
    byValue.set(item.value, item);
  }
  const picked = westernPickedOption.value;
  if (picked && !byValue.has(picked.value)) {
    byValue.set(picked.value, picked);
  }
  const selectedId = props.modelValue.westernDefinitionId.trim();
  if (selectedId && !byValue.has(selectedId)) {
    // 仅补当前选中项的展示名，避免分页截断后 ElSelect 只显示 ID
    const fromParent = (props.westernOptions ?? []).find(
      (item) => String(item.value) === selectedId,
    );
    const label = String(fromParent?.label ?? '').trim();
    if (label) {
      byValue.set(selectedId, { label, value: selectedId });
    }
  }
  return [...byValue.values()];
});

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

const { run: runWesternCatalogPage, loading: westernLoading } = useRequest(
  httpGetConditionDefinitionMetadata,
  { manual: true, showErrorMessage: false },
);

/** 局部更新表单状态并同步 v-model */
function patchModelValue(patch: Partial<AdmissionDiagnosisFormState>) {
  emit('update:modelValue', { ...props.modelValue, ...patch });
}

/** 远程检索西医诊断目录 */
function handleWesternRemoteSearch(keyword: string) {
  void loadWesternCatalogOptions(keyword);
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

/** 切换中西医时清空另一侧候选（目录由 medicineType watch 重新拉取） */
function handleMedicineTypeChange(next: AdmissionDiagnosisMedicineType) {
  patchModelValue({
    ...createEmptyAdmissionDiagnosisFormState(),
    medicineType: next,
    diagnosisDescription: props.modelValue.diagnosisDescription,
    readonlyDisplay: props.modelValue.readonlyDisplay,
  });
  westernPickedOption.value = null;
  westernRemoteOptions.value = [];
}

/** 拉取西医诊断目录（关键字命中名称/编码/拼音/五笔；仅第一页，避免把分页窗口当全量） */
async function loadWesternCatalogOptions(keyword: string) {
  if (props.readonly) {
    return;
  }
  const seq = westernSearchSeq.value + 1;
  westernSearchSeq.value = seq;
  try {
    const res = await runWesternCatalogPage({
      diagnosisKindCode: '1',
      keyword: keyword.trim() || undefined,
      pageNo: 1,
      pageSize: CATALOG_REMOTE_PAGE_SIZE,
    });
    if (seq !== westernSearchSeq.value) {
      return;
    }
    // 只保留有主键和名称的目录行，作为当前关键字下的可见候选
    westernRemoteOptions.value = (res.content ?? [])
      .filter((row) => row.conditionDefinitionId && row.conditionName)
      .map((row) => ({
        label: String(row.conditionName),
        value: String(row.conditionDefinitionId),
      }));
  } catch {
    if (seq === westernSearchSeq.value) {
      westernRemoteOptions.value = [];
    }
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
      pageSize: CATALOG_REMOTE_PAGE_SIZE,
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

/** 选中西医诊断并缓存选项，便于后续远程检索仍回显 */
function handleWesternChange(value: string) {
  const selectedId = value.trim();
  const hit = westernSelectOptions.value.find((item) => item.value === selectedId);
  westernPickedOption.value = hit ? { label: hit.label, value: hit.value } : null;
  patchModelValue({
    westernDefinitionId: selectedId,
    encounterDiagnosisId: undefined,
  });
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
    if (props.readonly) {
      return;
    }
    if (type === 'tcm') {
      syncTcmPrefillOptions(props.modelValue);
      void loadTcmCatalogOptions('disease', '');
      void loadTcmCatalogOptions('syndrome', '');
      return;
    }
    void loadWesternCatalogOptions('');
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
          remote
          reserve-keyword
          clearable
          placeholder="输入名称、编码或拼音检索"
          class="admission-diagnosis-field__control"
          :disabled="disabled"
          :loading="westernLoading"
          :remote-method="handleWesternRemoteSearch"
          @update:model-value="(val) => handleWesternChange(String(val ?? ''))"
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
