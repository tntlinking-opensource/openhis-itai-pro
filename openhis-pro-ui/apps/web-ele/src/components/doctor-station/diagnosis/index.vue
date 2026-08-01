<script setup lang="ts">
import type {
  CommonDiagnosesResponse,
  EncounterDiagnosisRowDto,
  PatientOpspFilingDiagnosisResponse,
} from '#/api/doctor-station/diagnosis.types';

import { computed, inject, onActivated, onDeactivated, ref, watch } from 'vue';

import { ElEmpty, ElMessage, ElMessageBox } from 'element-plus';

import { ENSURE_CLINICAL_RECEPTION_KEY } from '#/components/doctor-station/clinical-reception-context';

import DiagnosisPickerDialog from '#/components/doctor-station/diagnosis/components/diagnosis-picker-dialog.vue';
import DiagnosisQuickTree from './components/diagnosis-quick-tree.vue';
import DiagnosisSavedTable from './components/diagnosis-saved-table.vue';
import {
  deleteEncounterDiagnosisRow,
  deleteTcmDiagnosisGroupRow,
  fetchEncounterDiagnosisTabData,
  saveEncounterDiagnosisRows,
  saveInpatientEncounterDiagnosisIptTypeRows,
  saveInpatientEncounterDiagnosisReorder,
  switchMainEncounterDiagnosisRow,
  type DoctorStationDiagnosisScope,
} from './services/encounter-diagnosis.service';
import { appendTcmDiagnosisGroupBatch } from '#/components/doctor-station/diagnosis/services/tcm-diagnosis-groups.service';
import { isTcmDiagnosisRow } from './services/merge-encounter-diagnosis-display';

defineOptions({ name: 'DoctorStationDiagnosisList' });

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    patientId?: string;
    /** 门诊默认；住院医生站传 inpatient */
    stationScope?: DoctorStationDiagnosisScope;
  }>(),
  {
    encounterId: '',
    patientId: '',
    stationScope: 'outpatient',
  },
);

const diagnosisRows = ref<EncounterDiagnosisRowDto[]>([]);
const displayDiagnosisRows = ref<EncounterDiagnosisRowDto[]>([]);
const businessClass = ref<CommonDiagnosesResponse | null>(null);
const opspFiling = ref<null | PatientOpspFilingDiagnosisResponse>(null);
const listLoading = ref(false);
const quickSaveLoading = ref(false);
const pickerOpen = ref(false);
/** 正在切换主诊的就诊诊断行主键 */
const switchingMainId = ref('');

const ensureClinicalReception = inject(ENSURE_CLINICAL_RECEPTION_KEY, undefined);

const contextReady = computed(
  () => Boolean(props.encounterId?.trim()) && Boolean(props.patientId?.trim()),
);

/** 激活 / 工具栏刷新 / 保存后：统一入口 */
async function reload() {
  if (!contextReady.value) {
    diagnosisRows.value = [];
    displayDiagnosisRows.value = [];
    businessClass.value = null;
    opspFiling.value = null;
    return;
  }
  listLoading.value = true;
  try {
    const data = await fetchEncounterDiagnosisTabData(
      {
        encounterId: props.encounterId.trim(),
        patientId: props.patientId.trim(),
      },
      props.stationScope,
    );
    if (!data) {
      diagnosisRows.value = [];
      displayDiagnosisRows.value = [];
      businessClass.value = null;
      opspFiling.value = null;
      return;
    }
    diagnosisRows.value = data.diagnosisList;
    displayDiagnosisRows.value = data.displayDiagnosisList;
    businessClass.value = data.businessClass;
    opspFiling.value = data.opspFiling;
  } finally {
    listLoading.value = false;
  }
}

defineExpose({ reload });

/** KeepAlive 内仅激活页签拉数；换患者时由 watch 刷新当前可见 Tab */
const isTabActive = ref(false);

onActivated(() => {
  isTabActive.value = true;
  void reload();
});

onDeactivated(() => {
  isTabActive.value = false;
});

watch(
  () => [props.encounterId, props.patientId],
  () => {
    if (!isTabActive.value) return;
    void reload();
  },
);

/** 合并后的全量列表提交保存并刷新 */
async function persistMergedRows(rows: EncounterDiagnosisRowDto[]) {
  if (!contextReady.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (rows.length === 0) {
    ElMessage.warning('诊断列表不能为空');
    return;
  }
  const encounterId = props.encounterId.trim();
  const patientId = props.patientId.trim();
  quickSaveLoading.value = true;
  try {
    await saveEncounterDiagnosisRows(encounterId, patientId, rows, props.stationScope);
    ElMessage.success('诊断已保存');
    await reload();
  } finally {
    quickSaveLoading.value = false;
  }
}

/** 点击「新增诊断」：先接诊/切在诊再打开检索弹窗 */
async function handleAddDiagnosisClick() {
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return;
  }
  pickerOpen.value = true;
}

/** 侧栏常用树选中叶子：并入列表并保存 */
async function handleQuickAppend(append: EncounterDiagnosisRowDto) {
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return;
  }
  const encounterId = props.encounterId?.trim();
  const patientId = props.patientId?.trim();
  const diseaseId = append.conditionDefinitionId?.trim();
  const syndromeId = append.syndromeDefinitionId?.trim();
  const isTcmPair = Boolean(diseaseId && syndromeId);

  if (isTcmPair && encounterId && patientId) {
    quickSaveLoading.value = true;
    try {
      const result = await appendTcmDiagnosisGroupBatch(
        encounterId,
        patientId,
        {
          conditionDefinitionId: diseaseId!,
          conditionCode: append.conditionCode ?? '',
          conditionName: append.conditionDefinitionName ?? '',
        },
        {
          conditionDefinitionId: syndromeId!,
          conditionCode: '',
          conditionName: '',
        },
        diagnosisRows.value.length === 0,
        props.stationScope,
      );
      if (result === 'duplicate') {
        ElMessage.info('该中医病证组已在当前列表中');
        return;
      }
      ElMessage.success('诊断已保存');
      await reload();
    } finally {
      quickSaveLoading.value = false;
    }
    return;
  }

  await persistMergedRows([...diagnosisRows.value, append]);
}

function isSameDiagnosisRow(a: EncounterDiagnosisRowDto, b: EncounterDiagnosisRowDto): boolean {
  const aEd = String(a.encounterDiagnosisId ?? '').trim();
  const bEd = String(b.encounterDiagnosisId ?? '').trim();
  if (aEd && bEd && aEd === bEd) {
    return true;
  }
  if (
    a.conditionDefinitionId &&
    b.conditionDefinitionId &&
    a.conditionDefinitionId === b.conditionDefinitionId
  ) {
    return true;
  }
  return false;
}

/** 住院：诊断类型变更后保存（中医病证组仅同步诊断类型，西医走 IF-IP-DOR-05 全量对齐） */
async function handleIptTypeChange(row: EncounterDiagnosisRowDto, code: string | undefined) {
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return;
  }
  const opt = businessClass.value?.iptDiseaseTypeOptions?.find((o) => o.value === code);
  const groupKey = row.tcmSyndromeGroup?.trim();
  const next = diagnosisRows.value.map((item) => {
    if (groupKey && isTcmDiagnosisRow(item)) {
      const itemGroupKey = String(item.tcmSyndromeGroup ?? '').trim();
      const matchesGroup =
        itemGroupKey === groupKey ||
        item.encounterDiagnosisId === row.encounterDiagnosisId ||
        item.conditionDefinitionId === row.conditionDefinitionId ||
        item.conditionDefinitionId === row.syndromeDefinitionId;
      if (!matchesGroup) {
        return item;
      }
    } else if (!isSameDiagnosisRow(item, row)) {
      return item;
    }
    return {
      ...item,
      iptDiseaseTypeCode: code ?? null,
      iptDiseaseTypeText: opt?.label ?? null,
    };
  });
  if (!contextReady.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const encounterId = props.encounterId.trim();
  const patientId = props.patientId.trim();
  diagnosisRows.value = next;
  quickSaveLoading.value = true;
  try {
    if (props.stationScope === 'inpatient') {
      await saveInpatientEncounterDiagnosisIptTypeRows(encounterId, patientId, next);
    } else {
      await saveEncounterDiagnosisRows(encounterId, patientId, next, props.stationScope);
    }
    ElMessage.success('诊断已保存');
    await reload();
  } finally {
    quickSaveLoading.value = false;
  }
}

/** 删除已持久化行（中医病证组走整组删除，西医走单条删除） */
async function handleRemove(row: EncounterDiagnosisRowDto) {
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return;
  }
  const encounterId = props.encounterId?.trim();
  const patientId = props.patientId?.trim();
  const groupKey = row.tcmSyndromeGroup?.trim();
  if (!encounterId) return;
  try {
    if (groupKey && patientId) {
      await deleteTcmDiagnosisGroupRow(encounterId, patientId, groupKey, props.stationScope);
    } else {
      const encounterDiagnosisId = String(row.encounterDiagnosisId ?? '').trim();
      if (!encounterDiagnosisId) return;
      await deleteEncounterDiagnosisRow(encounterId, encounterDiagnosisId, props.stationScope);
    }
    ElMessage.success('已删除');
    await reload();
  } catch {
    // 全局拦截器已提示
  }
}

/** 切换主诊断（同就诊仅一条/一组；后端清空其它主诊） */
async function handleSwitchMain(row: EncounterDiagnosisRowDto) {
  if (Number(row.mainDiagnosisFlag) === 1) return;
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return;
  }
  const encounterId = props.encounterId?.trim();
  const patientId = props.patientId?.trim();
  const encounterDiagnosisId = String(row.encounterDiagnosisId ?? '').trim();
  if (!encounterId || !encounterDiagnosisId) {
    ElMessage.warning('缺少就诊或诊断信息，无法切换主诊断');
    return;
  }
  const currentMain = displayDiagnosisRows.value.find(
    (item) => Number(item.mainDiagnosisFlag) === 1,
  );
  const targetName = String(row.conditionDefinitionName ?? '').trim() || '该诊断';
  const currentName = String(currentMain?.conditionDefinitionName ?? '').trim();
  const confirmText = currentName
    ? `确认将「${targetName}」设为主诊断？当前主诊断「${currentName}」将取消主诊（一次就诊仅允许一个主诊断）。`
    : `确认将「${targetName}」设为主诊断？`;
  try {
    await ElMessageBox.confirm(confirmText, '切换主诊断', {
      type: 'warning',
      confirmButtonText: '设为主诊',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  switchingMainId.value = encounterDiagnosisId;
  try {
    await switchMainEncounterDiagnosisRow(
      encounterId,
      encounterDiagnosisId,
      props.stationScope,
      patientId || undefined,
    );
    ElMessage.success('已设为主诊断');
    await reload();
  } catch {
    // 全局拦截器已提示
  } finally {
    switchingMainId.value = '';
  }
}

/** 住院：在合并展示列表中定位行下标 */
function resolveDisplayRowIndex(row: EncounterDiagnosisRowDto): number {
  const groupKey = String(row.tcmSyndromeGroup ?? '').trim();
  if (groupKey) {
    return displayDiagnosisRows.value.findIndex(
      (item) => String(item.tcmSyndromeGroup ?? '').trim() === groupKey,
    );
  }
  return displayDiagnosisRows.value.findIndex((item) => isSameDiagnosisRow(item, row));
}

/** 住院：上移/下移后写回排序号并刷新 */
async function handleMove(row: EncounterDiagnosisRowDto, direction: 'down' | 'up') {
  if (props.stationScope !== 'inpatient') {
    return;
  }
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return;
  }
  if (!contextReady.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const idx = resolveDisplayRowIndex(row);
  const targetIdx = direction === 'up' ? idx - 1 : idx + 1;
  if (idx < 0 || targetIdx < 0 || targetIdx >= displayDiagnosisRows.value.length) {
    return;
  }

  // 交换相邻展示行，后续按新序写 rank / diagnosisSortNo
  const nextDisplay = [...displayDiagnosisRows.value];
  const current = nextDisplay[idx];
  const neighbor = nextDisplay[targetIdx];
  if (!current || !neighbor) {
    return;
  }
  nextDisplay[idx] = neighbor;
  nextDisplay[targetIdx] = current;

  const encounterId = props.encounterId.trim();
  const patientId = props.patientId.trim();
  quickSaveLoading.value = true;
  try {
    await saveInpatientEncounterDiagnosisReorder(
      encounterId,
      patientId,
      nextDisplay,
      diagnosisRows.value,
    );
    ElMessage.success('诊断顺序已更新');
    await reload();
  } catch {
    // 全局拦截器已提示
  } finally {
    quickSaveLoading.value = false;
  }
}
</script>

<template>
  <div
    v-loading="listLoading || quickSaveLoading"
    class="diagnosis-list-panel flex h-full min-h-0 flex-col p-2"
  >
    <ElEmpty
      v-if="!contextReady"
      class="flex flex-1 flex-col items-center justify-center"
      description="请先选择患者以加载诊断数据。"
    />
    <template v-else>
      <div class="diagnosis-list-body">
        <DiagnosisQuickTree
          :business-class="businessClass"
          :display-rows="displayDiagnosisRows"
          :opsp-filing="opspFiling"
          @append="handleQuickAppend"
        />
        <DiagnosisSavedTable
          :rows="displayDiagnosisRows"
          :station-scope="props.stationScope"
          :ipt-disease-type-options="businessClass?.iptDiseaseTypeOptions ?? []"
          :switching-main-id="switchingMainId"
          @add-click="handleAddDiagnosisClick"
          @remove="handleRemove"
          @switch-main="handleSwitchMain"
          @ipt-type-change="handleIptTypeChange"
          @move="handleMove"
        />
      </div>
      <DiagnosisPickerDialog
        v-model="pickerOpen"
        :encounter-id="props.encounterId.trim()"
        :patient-id="props.patientId.trim()"
        :station-scope="props.stationScope"
        :existing-diagnosis-list="diagnosisRows"
        @saved="reload"
      />
    </template>
  </div>
</template>

<style scoped>
.diagnosis-list-panel {
  gap: 10px;
  min-height: 0;
}

.diagnosis-list-body {
  /* default：DOCTOR_STATION_SIDEBAR_WIDTH_PX.diagnosisQuickTree */
  --diagnosis-quick-tree-width: 260px;
  display: grid;
  grid-template-columns: var(--diagnosis-quick-tree-width) minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  gap: 10px;
  flex: 1;
  min-height: 0;
  align-items: stretch;
}
</style>
