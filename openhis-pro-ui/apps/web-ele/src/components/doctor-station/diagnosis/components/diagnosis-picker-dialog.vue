<script setup lang="ts">
import type {
  DiagnosisConfirmPayload,
  DiagnosisItem,
  DiagnosisMedicineType,
  DiagnosisScopeType,
} from '../services/diagnosis.types';

import type {
  ConditionDefinitionRowResponse,
  EncounterDiagnosisRowDto,
} from '#/api/doctor-station/diagnosis.types';

import { computed, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import {
  saveEncounterDiagnosisRows,
  type DoctorStationDiagnosisScope,
} from '../services/encounter-diagnosis.service';
import {
  appendTcmDiagnosisGroupBatch,
  resolveTcmDiagnosisHttp,
} from '../services/tcm-diagnosis-groups.service';
import {
  formatTcmDiagnosisDisplayName,
  mapTcmCatalogRowToConditionDefinitionRow,
  resolveTcmCatalogRole,
  type TcmCatalogKind,
  type TcmDiagnosisPick,
} from '../services/tcm-diagnosis-display';
import { createDoctorStationDiagnosisHttpClient } from '#/api/doctor-station/diagnosis-api-client';

defineOptions({ name: 'DiagnosisPickerDialog' });

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    existingDiagnosisList?: EncounterDiagnosisRowDto[];
    modelValue: boolean;
    patientDiagnoses?: DiagnosisItem[];
    patientId?: string;
    stationScope?: DoctorStationDiagnosisScope;
  }>(),
  {
    encounterId: '',
    existingDiagnosisList: () => [],
    patientDiagnoses: () => [],
    patientId: '',
    stationScope: 'outpatient',
  },
);

const diagnosisHttp = computed(() =>
  createDoctorStationDiagnosisHttpClient(
    props.stationScope === 'inpatient'
      ? '/inpatientDoctorStationDiagnosis'
      : '/doctorStationDiagnosis',
  ),
);

const tcmHttp = computed(() => resolveTcmDiagnosisHttp(props.stationScope));

const emit = defineEmits<{
  confirm: [value: DiagnosisConfirmPayload];
  saved: [];
  'update:modelValue': [value: boolean];
}>();

/** 有就诊上下文时走检索定义 + 确定并保存；否则走医嘱侧本地候选 */
const encounterMode = computed(
  () => Boolean(props.encounterId?.trim()) && Boolean(props.patientId?.trim()),
);

// —— 就诊检索模式（接口分页） ——
const medicineTypeCode = ref<'1' | '2'>('1');
const tcmCatalogKind = ref<TcmCatalogKind>('disease');
const keyword = ref('');
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);
const records = ref<ConditionDefinitionRowResponse[]>([]);
const tableLoading = ref(false);
const saveLoading = ref(false);
const selectedRow = ref<ConditionDefinitionRowResponse | null>(null);
const tcmDiseasePick = ref<TcmDiagnosisPick | null>(null);
const tcmSyndromePick = ref<TcmDiagnosisPick | null>(null);

const isTcmMode = computed(() => medicineTypeCode.value === '2');

/** 表格名称列标题：中医按当前检索子类切换 */
const catalogNameColumnLabel = computed(() => {
  if (!isTcmMode.value) return '诊断名称';
  return tcmCatalogKind.value === 'syndrome' ? '证候名称' : '疾病名称';
});

/** 拉取当前页诊断定义目录（西医 IF-DOR-07 / 中医 IF-DOR-31） */
async function loadDefinitionPage() {
  tableLoading.value = true;
  try {
    if (isTcmMode.value) {
      const res = await tcmHttp.value.httpPageTcmConditionDefinitionCatalog({
        catalogRole: resolveTcmCatalogRole(tcmCatalogKind.value),
        keyword: keyword.value.trim() || undefined,
        pageNo: pageNo.value,
        pageSize: pageSize.value,
      });
      records.value = (res.rows ?? []).map(mapTcmCatalogRowToConditionDefinitionRow);
      total.value = Number(res.total ?? 0);
      selectedRow.value = records.value[0] ?? null;
      return;
    }
    const res = await diagnosisHttp.value.httpGetConditionDefinitionMetadata({
      diagnosisKindCode: '1',
      keyword: keyword.value.trim() || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    });
    records.value = res.records ?? [];
    total.value = Number(res.total ?? res.totalCount ?? 0);
    selectedRow.value = records.value[0] ?? null;
  } finally {
    tableLoading.value = false;
  }
}

watch([medicineTypeCode, pageSize], () => {
  if (!props.modelValue || !encounterMode.value) return;
  pageNo.value = 1;
  void loadDefinitionPage();
});

watch(tcmCatalogKind, () => {
  if (!props.modelValue || !encounterMode.value || !isTcmMode.value) return;
  pageNo.value = 1;
  selectedRow.value = null;
  void loadDefinitionPage();
});

watch(medicineTypeCode, (next) => {
  if (next === '1') {
    tcmCatalogKind.value = 'disease';
    tcmDiseasePick.value = null;
    tcmSyndromePick.value = null;
  }
});

// —— 医嘱本地候选模式 ——
const medicineType = ref<DiagnosisMedicineType>('western');
const legacyTcmCatalogKind = ref<TcmCatalogKind>('disease');
const scopeType = ref<DiagnosisScopeType>('all');
const selectedId = ref('');
const saveAsMainDiagnosis = ref(false);
const saveAsCommon = ref(false);
const legacyTcmDiseasePick = ref<TcmDiagnosisPick | null>(null);
const legacyTcmSyndromePick = ref<TcmDiagnosisPick | null>(null);

const sourceDiagnoses = computed(() => props.patientDiagnoses);

const displayDiagnoses = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  return sourceDiagnoses.value.filter((item) => {
    if (item.medicineType !== medicineType.value) return false;
    if (scopeType.value === 'common' && !item.isCommon) return false;
    if (scopeType.value === 'history' && !item.isHistory) return false;
    if (!text) return true;
    return item.name.toLowerCase().includes(text) || item.code.toLowerCase().includes(text);
  });
});

const selectedDiagnosis = computed(() => {
  return displayDiagnoses.value.find((item) => item.id === selectedId.value) || null;
});

const legacyIsTcmMode = computed(() => medicineType.value === 'tcm');

/** 将检索行转为中医疾病/证候候选 */
function toTcmPick(row: ConditionDefinitionRowResponse): TcmDiagnosisPick {
  return {
    conditionDefinitionId: row.conditionDefinitionId ?? '',
    conditionCode: row.conditionCode ?? '',
    conditionName: row.conditionName ?? '',
  };
}

/** 就诊模式：选中疾病后自动切到证候检索 */
function switchEncounterTcmCatalogToSyndrome() {
  if (tcmCatalogKind.value === 'syndrome') return;
  keyword.value = '';
  tcmCatalogKind.value = 'syndrome';
}

/** 就诊模式：表格选中行写入疾病或证候表单 */
function applyEncounterRowPick(row: ConditionDefinitionRowResponse | null) {
  if (!row) return;
  if (!isTcmMode.value) return;
  const pick = toTcmPick(row);
  if (tcmCatalogKind.value === 'syndrome') {
    tcmSyndromePick.value = pick;
    return;
  }
  tcmDiseasePick.value = pick;
  switchEncounterTcmCatalogToSyndrome();
}

/** 重置中医表单候选 */
function resetTcmFormPicks() {
  tcmDiseasePick.value = null;
  tcmSyndromePick.value = null;
  legacyTcmDiseasePick.value = null;
  legacyTcmSyndromePick.value = null;
}

function closeDialog() {
  emit('update:modelValue', false);
}

function onDialogModelValue(value: unknown) {
  emit('update:modelValue', Boolean(value));
}

/** 弹窗打开：按模式初始化 */
function onDialogOpen() {
  resetTcmFormPicks();
  keyword.value = '';
  pageNo.value = 1;
  saveAsMainDiagnosis.value = false;
  saveAsCommon.value = false;
  medicineTypeCode.value = '1';
  tcmCatalogKind.value = 'disease';
  medicineType.value = 'western';
  legacyTcmCatalogKind.value = 'disease';
  if (encounterMode.value) {
    void loadDefinitionPage();
    return;
  }
  selectedId.value = displayDiagnoses.value[0]?.id || '';
}

function handleEncounterSearch() {
  pageNo.value = 1;
  void loadDefinitionPage();
}

function handleEncounterPageChange(p: number) {
  pageNo.value = p;
  void loadDefinitionPage();
}

function handleEncounterCurrentChange(row: ConditionDefinitionRowResponse | undefined) {
  selectedRow.value = row ?? null;
  applyEncounterRowPick(row ?? null);
}

function handleEncounterRowDblclick(row: ConditionDefinitionRowResponse) {
  if (!row) return;
  selectedRow.value = row;
  applyEncounterRowPick(row);
  // 中医须病+证齐备后再保存，双击仅写入当前侧候选
  if (isTcmMode.value) return;
  void handleEncounterConfirmSave(row);
}

/** 组装待保存的就诊诊断行（西医单条；中医走 IF-DOR-32 病证组接口） */
function buildEncounterAppendRow(): EncounterDiagnosisRowDto | null {
  if (isTcmMode.value) return null;
  const resolved = selectedRow.value;
  if (!resolved) return null;
  return {
    conditionDefinitionId: resolved.conditionDefinitionId,
    conditionCode: resolved.conditionCode ?? '',
    conditionDefinitionName: resolved.conditionName ?? '',
    medicineType: resolved.medicineType,
  };
}

/** 选中定义并入列表并提交保存；中医走病证组批次，西医走就诊诊断批次 */
async function handleEncounterConfirmSave(pick?: ConditionDefinitionRowResponse | null) {
  if (pick) {
    selectedRow.value = pick;
    applyEncounterRowPick(pick);
  }

  const eid = props.encounterId.trim();
  const pid = props.patientId.trim();
  if (!eid || !pid) {
    ElMessage.warning('请先选择患者');
    return;
  }

  if (isTcmMode.value) {
    if (!tcmDiseasePick.value?.conditionDefinitionId) {
      ElMessage.warning('请先选择中医疾病');
      return;
    }
    if (!tcmSyndromePick.value?.conditionDefinitionId) {
      ElMessage.warning('请先选择中医证候');
      return;
    }
    saveLoading.value = true;
    try {
      const result = await appendTcmDiagnosisGroupBatch(
        eid,
        pid,
        tcmDiseasePick.value,
        tcmSyndromePick.value,
        saveAsMainDiagnosis.value,
        props.stationScope,
      );
      if (result === 'duplicate') {
        ElMessage.info('该病证组已在当前列表中');
        return;
      }
      ElMessage.success('中医病证已保存');
      emit('saved');
      closeDialog();
    } finally {
      saveLoading.value = false;
    }
    return;
  }

  const append = buildEncounterAppendRow();
  if (!append) {
    ElMessage.warning('请先在上方列表中选择诊断');
    return;
  }
  if (
    props.existingDiagnosisList.some(
      (r) => r.conditionDefinitionId === append.conditionDefinitionId,
    )
  ) {
    ElMessage.info('该诊断已在当前列表中');
    return;
  }
  let merged: EncounterDiagnosisRowDto[];
  if (saveAsMainDiagnosis.value) {
    const cleared = props.existingDiagnosisList.map((r) => ({ ...r, mainDiagnosisFlag: 0 }));
    append.mainDiagnosisFlag = 1;
    merged = [...cleared, append];
  } else {
    merged = [...props.existingDiagnosisList, append];
  }
  saveLoading.value = true;
  try {
    await saveEncounterDiagnosisRows(eid, pid, merged, props.stationScope);
    ElMessage.success('诊断已保存');
    emit('saved');
    closeDialog();
  } finally {
    saveLoading.value = false;
  }
}

/** 本地候选：选中疾病后自动切到证候检索 */
function switchLegacyTcmCatalogToSyndrome() {
  if (legacyTcmCatalogKind.value === 'syndrome') return;
  legacyTcmCatalogKind.value = 'syndrome';
}

/** 本地候选：表格选中写入中医疾病/证候 */
function applyLegacyRowPick(row: DiagnosisItem | undefined) {
  if (!row || !legacyIsTcmMode.value) return;
  const pick: TcmDiagnosisPick = {
    conditionDefinitionId: row.conditionDefinitionId ?? row.id,
    conditionCode: row.code,
    conditionName: row.name,
  };
  if (legacyTcmCatalogKind.value === 'syndrome') {
    legacyTcmSyndromePick.value = pick;
    return;
  }
  legacyTcmDiseasePick.value = pick;
  switchLegacyTcmCatalogToSyndrome();
}

function handleLegacyConfirm() {
  if (legacyIsTcmMode.value) {
    if (!legacyTcmDiseasePick.value?.conditionDefinitionId) {
      ElMessage.warning('请先选择中医疾病');
      return;
    }
    if (!legacyTcmSyndromePick.value?.conditionDefinitionId) {
      ElMessage.warning('请先选择中医证候');
      return;
    }
    const displayName = formatTcmDiagnosisDisplayName(
      legacyTcmDiseasePick.value.conditionName,
      legacyTcmSyndromePick.value.conditionName,
    );
    emit('confirm', {
      selected: {
        id: legacyTcmDiseasePick.value.conditionDefinitionId,
        code: legacyTcmDiseasePick.value.conditionCode,
        name: displayName,
        medicineType: 'tcm',
        conditionDefinitionId: legacyTcmDiseasePick.value.conditionDefinitionId,
      },
      saveAsMainDiagnosis: saveAsMainDiagnosis.value,
      saveAsCommon: saveAsCommon.value,
    });
    closeDialog();
    return;
  }
  if (!selectedDiagnosis.value) return;
  emit('confirm', {
    selected: selectedDiagnosis.value as DiagnosisItem,
    saveAsMainDiagnosis: saveAsMainDiagnosis.value,
    saveAsCommon: saveAsCommon.value,
  });
  closeDialog();
}

function setLegacySelectedIdFromRow(row: DiagnosisItem | undefined) {
  selectedId.value = row?.id || '';
  applyLegacyRowPick(row);
}

function onLegacyTableRow(row: DiagnosisItem | undefined) {
  setLegacySelectedIdFromRow(row);
}

/** 双击行：按当前主诊/常用勾选直接确认（与底部「确认」一致） */
function handleLegacyRowDblclick(row: DiagnosisItem | undefined) {
  if (!row) return;
  applyLegacyRowPick(row);
  if (legacyIsTcmMode.value) {
    handleLegacyConfirm();
    return;
  }
  emit('confirm', {
    selected: row,
    saveAsMainDiagnosis: saveAsMainDiagnosis.value,
    saveAsCommon: saveAsCommon.value,
  });
  closeDialog();
}

watch(medicineType, (next) => {
  if (next === 'western') {
    legacyTcmCatalogKind.value = 'disease';
    legacyTcmDiseasePick.value = null;
    legacyTcmSyndromePick.value = null;
  }
});

const encounterConfirmDisabled = computed(() => {
  if (isTcmMode.value) {
    return (
      !tcmDiseasePick.value?.conditionDefinitionId || !tcmSyndromePick.value?.conditionDefinitionId
    );
  }
  return !selectedRow.value;
});

const legacyConfirmDisabled = computed(() => {
  if (legacyIsTcmMode.value) {
    return (
      !legacyTcmDiseasePick.value?.conditionDefinitionId ||
      !legacyTcmSyndromePick.value?.conditionDefinitionId
    );
  }
  return !selectedDiagnosis.value;
});
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :width="encounterMode ? '920px' : '880px'"
    :title="encounterMode ? '新增诊断' : '新增诊断'"
    append-to-body
    @update:model-value="onDialogModelValue"
    @open="onDialogOpen"
  >
    <!-- 就诊站：分页检索已发布定义 -->
    <div v-if="encounterMode" class="diagnosis-dialog flex flex-col gap-3">
      <div class="toolbar-row">
        <ElRadioGroup v-model="medicineTypeCode" class="kind-radio-group">
          <ElRadioButton label="1">西医诊断</ElRadioButton>
          <ElRadioButton label="2">中医诊断</ElRadioButton>
        </ElRadioGroup>
      </div>
      <div class="search-row">
        <ElRadioGroup v-if="isTcmMode" v-model="tcmCatalogKind" class="tcm-catalog-radio">
          <ElRadio label="disease">疾病</ElRadio>
          <ElRadio label="syndrome">证候</ElRadio>
        </ElRadioGroup>
        <ElInput
          v-model="keyword"
          class="search-input"
          placeholder="请输入"
          clearable
          @keyup.enter="handleEncounterSearch"
        />
        <ElButton type="primary" :loading="tableLoading" @click="handleEncounterSearch">
          查询
        </ElButton>
      </div>
      <div class="table-wrap">
        <ElTable
          v-loading="tableLoading"
          :data="records"
          border
          height="320"
          highlight-current-row
          @current-change="handleEncounterCurrentChange"
          @row-dblclick="handleEncounterRowDblclick"
        >
          <ElTableColumn prop="conditionCode" label="诊断编码" width="200" />
          <ElTableColumn prop="conditionName" :label="catalogNameColumnLabel" min-width="260" />
        </ElTable>
      </div>
      <template v-if="isTcmMode">
        <div class="form-row">
          <span class="label required">疾病</span>
          <ElInput
            readonly
            :model-value="tcmDiseasePick?.conditionName ?? ''"
            placeholder="请在上方列表选择疾病"
          />
        </div>
        <div class="form-row">
          <span class="label required">证候</span>
          <ElInput
            readonly
            :model-value="tcmSyndromePick?.conditionName ?? ''"
            placeholder="请在上方列表选择证候"
          />
        </div>
      </template>
      <div v-else class="form-row">
        <span class="label">选择结果</span>
        <ElInput
          readonly
          :model-value="selectedRow?.conditionName || ''"
          placeholder="请先在上方列表中选择诊断"
        />
      </div>
      <div class="form-row checkbox-row">
        <ElCheckbox v-model="saveAsMainDiagnosis">主诊断</ElCheckbox>
        <ElCheckbox v-model="saveAsCommon">确认并存为常用</ElCheckbox>
      </div>
      <div class="flex justify-end">
        <ElPagination
          layout="prev, pager, next, total"
          :page-size="pageSize"
          :current-page="pageNo"
          :total="Number(total) || 0"
          @current-change="handleEncounterPageChange"
        />
      </div>
    </div>

    <!-- 医嘱：本地候选 -->
    <div v-else class="diagnosis-dialog">
      <div class="toolbar-row">
        <ElRadioGroup v-model="medicineType" class="kind-radio-group">
          <ElRadioButton label="western">西医诊断</ElRadioButton>
          <ElRadioButton label="tcm">中医诊断</ElRadioButton>
        </ElRadioGroup>
        <ElRadioGroup v-model="scopeType" size="small">
          <ElRadio label="all">全部诊断</ElRadio>
          <ElRadio label="common">常用诊断</ElRadio>
          <ElRadio label="history">历史诊断</ElRadio>
        </ElRadioGroup>
      </div>
      <div class="search-row">
        <ElRadioGroup
          v-if="legacyIsTcmMode"
          v-model="legacyTcmCatalogKind"
          class="tcm-catalog-radio"
        >
          <ElRadio label="disease">疾病</ElRadio>
          <ElRadio label="syndrome">证候</ElRadio>
        </ElRadioGroup>
        <ElInput v-model="keyword" class="search-input" placeholder="请输入" clearable />
      </div>
      <div class="table-wrap">
        <ElTable
          :data="displayDiagnoses"
          border
          height="320"
          highlight-current-row
          @current-change="onLegacyTableRow"
          @row-dblclick="handleLegacyRowDblclick"
        >
          <ElTableColumn prop="code" label="诊断编码" width="220" />
          <ElTableColumn
            prop="name"
            :label="
              legacyIsTcmMode
                ? legacyTcmCatalogKind === 'syndrome'
                  ? '证候名称'
                  : '疾病名称'
                : '诊断名称'
            "
            min-width="380"
          />
        </ElTable>
      </div>
      <template v-if="legacyIsTcmMode">
        <div class="form-row">
          <span class="label required">疾病</span>
          <ElInput
            readonly
            :model-value="legacyTcmDiseasePick?.conditionName ?? ''"
            placeholder="请在上方列表选择疾病"
          />
        </div>
        <div class="form-row">
          <span class="label required">证候</span>
          <ElInput
            readonly
            :model-value="legacyTcmSyndromePick?.conditionName ?? ''"
            placeholder="请在上方列表选择证候"
          />
        </div>
      </template>
      <div v-else class="form-row">
        <span class="label">选择结果</span>
        <ElInput
          readonly
          :model-value="selectedDiagnosis?.name || ''"
          placeholder="请先在上方列表中选择诊断"
        />
      </div>
      <div class="form-row checkbox-row">
        <ElCheckbox v-model="saveAsMainDiagnosis">主诊断</ElCheckbox>
        <ElCheckbox v-model="saveAsCommon">确认并存为常用</ElCheckbox>
      </div>
    </div>

    <template #footer>
      <div v-if="encounterMode" class="dialog-footer">
        <ElButton @click="closeDialog">取消</ElButton>
        <ElButton
          type="primary"
          :disabled="encounterConfirmDisabled"
          :loading="saveLoading"
          @click="() => handleEncounterConfirmSave()"
        >
          确定并保存
        </ElButton>
      </div>
      <div v-else class="dialog-footer">
        <ElButton @click="closeDialog">取消</ElButton>
        <ElButton type="primary" :disabled="legacyConfirmDisabled" @click="handleLegacyConfirm">
          确认
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.diagnosis-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.kind-radio-group :deep(.el-radio-button__inner) {
  min-width: 88px;
  padding: 8px 14px;
  font-size: 14px;
}

.tcm-catalog-radio {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 12px;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-input {
  flex: 1;
  min-width: 0;
}

.table-wrap {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.checkbox-row {
  gap: 20px;
  flex-wrap: wrap;
}

.label {
  width: 70px;
  flex-shrink: 0;
  color: var(--el-text-color-regular);
  font-size: 14px;
  text-align: right;
}

.label.required::before {
  content: '*';
  color: var(--el-color-danger);
  margin-right: 4px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
