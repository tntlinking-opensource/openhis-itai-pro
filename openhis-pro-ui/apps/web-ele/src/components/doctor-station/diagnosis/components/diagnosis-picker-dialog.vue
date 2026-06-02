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
const keyword = ref('');
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);
const records = ref<ConditionDefinitionRowResponse[]>([]);
const tableLoading = ref(false);
const saveLoading = ref(false);
const selectedRow = ref<ConditionDefinitionRowResponse | null>(null);

/** 拉取当前页诊断定义目录 */
async function loadDefinitionPage() {
  tableLoading.value = true;
  try {
    const res = await diagnosisHttp.value.httpGetConditionDefinitionMetadata({
      medicineTypeCode: medicineTypeCode.value,
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

// —— 医嘱本地候选模式 ——
const medicineType = ref<DiagnosisMedicineType>('western');
const scopeType = ref<DiagnosisScopeType>('all');
const selectedId = ref('');
const saveAsMainDiagnosis = ref(false);
const saveAsCommon = ref(false);

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

function closeDialog() {
  emit('update:modelValue', false);
}

function onDialogModelValue(value: unknown) {
  emit('update:modelValue', Boolean(value));
}

/** 弹窗打开：按模式初始化 */
function onDialogOpen() {
  if (encounterMode.value) {
    keyword.value = '';
    pageNo.value = 1;
    saveAsMainDiagnosis.value = false;
    saveAsCommon.value = false;
    void loadDefinitionPage();
    return;
  }
  keyword.value = '';
  saveAsMainDiagnosis.value = false;
  saveAsCommon.value = false;
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
}

function handleEncounterRowDblclick(row: ConditionDefinitionRowResponse) {
  if (!row) return;
  selectedRow.value = row;
  void handleEncounterConfirmSave(row);
}

/** 选中定义并入列表并提交保存；`pick` 传入时用于双击行与当前选中同步 */
async function handleEncounterConfirmSave(pick?: ConditionDefinitionRowResponse | null) {
  const resolved = pick ?? selectedRow.value;
  if (!resolved) return;
  const eid = props.encounterId.trim();
  const pid = props.patientId.trim();
  if (!eid || !pid) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const append: EncounterDiagnosisRowDto = {
    conditionDefinitionId: resolved.conditionDefinitionId,
    conditionCode: resolved.conditionCode ?? '',
    conditionDefinitionName: resolved.conditionName ?? '',
    medicineType: resolved.medicineType,
  };
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

function handleLegacyConfirm() {
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
}

function onLegacyTableRow(row: DiagnosisItem | undefined) {
  setLegacySelectedIdFromRow(row);
}

/** 双击行：按当前主诊/常用勾选直接确认（与底部「确认」一致） */
function handleLegacyRowDblclick(row: DiagnosisItem | undefined) {
  if (!row) return;
  emit('confirm', {
    selected: row,
    saveAsMainDiagnosis: saveAsMainDiagnosis.value,
    saveAsCommon: saveAsCommon.value,
  });
  closeDialog();
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :width="encounterMode ? '900px' : '880px'"
    :title="encounterMode ? '检索诊断定义' : '新增诊断'"
    append-to-body
    @update:model-value="onDialogModelValue"
    @open="onDialogOpen"
  >
    <!-- 就诊站：分页检索已发布定义 -->
    <div v-if="encounterMode" class="diagnosis-dialog flex flex-col gap-3">
      <ElRadioGroup v-model="medicineTypeCode" size="small">
        <ElRadioButton label="1">西医</ElRadioButton>
        <ElRadioButton label="2">中医</ElRadioButton>
      </ElRadioGroup>
      <div class="flex gap-2">
        <ElInput
          v-model="keyword"
          class="flex-1"
          placeholder="编码 / 名称 / 拼音 / 五笔"
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
          <ElTableColumn prop="conditionName" label="诊断名称" min-width="260" />
          <ElTableColumn label="类别" width="100" align="center">
            <template #default="{ row }">
              {{ row.diagnosisKindText || row.medicineType || '—' }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="sourceEnumText" label="来源" width="120" show-overflow-tooltip />
          <ElTableColumn prop="ybFlagText" label="医保" width="90" align="center" />
        </ElTable>
      </div>
      <div class="form-row">
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
        <ElRadioGroup v-model="medicineType" size="small">
          <ElRadioButton label="western">西医诊断</ElRadioButton>
          <ElRadioButton label="tcm">中医诊断</ElRadioButton>
        </ElRadioGroup>
        <ElRadioGroup v-model="scopeType" size="small">
          <ElRadio label="all">全部诊断</ElRadio>
          <ElRadio label="common">常用诊断</ElRadio>
          <ElRadio label="history">历史诊断</ElRadio>
        </ElRadioGroup>
      </div>
      <ElInput v-model="keyword" placeholder="请输入诊断编码/名称" clearable />
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
          <ElTableColumn prop="name" label="诊断名称" min-width="380" />
          <ElTableColumn label="收藏" width="90" align="center">
            <template #default="{ row }">
              <span :class="row.isCommon ? 'star active' : 'star'">★</span>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
      <div class="form-row">
        <span class="label">选择结果</span>
        <ElInput
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
          :disabled="!selectedRow"
          :loading="saveLoading"
          @click="() => handleEncounterConfirmSave()"
        >
          确定并保存
        </ElButton>
      </div>
      <div v-else class="dialog-footer">
        <ElButton @click="closeDialog">取消</ElButton>
        <ElButton type="primary" :disabled="!selectedDiagnosis" @click="handleLegacyConfirm">
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
  color: var(--el-text-color-regular);
  font-size: 14px;
  text-align: right;
}

.star {
  color: #d1d5db;
}

.star.active {
  color: #f59e0b;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
