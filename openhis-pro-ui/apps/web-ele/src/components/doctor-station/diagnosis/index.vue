<script setup lang="ts">
import type {
  CommonDiagnosesResponse,
  EncounterDiagnosisRowDto,
  OpspFilingDiagnosisRowDto,
  PatientOpspFilingDiagnosisResponse,
} from '#/api/doctor-station/diagnosis.types';

import { httpEnsureOpspFilingDiagnosis } from '#/api/doctor-station/diagnosis.http';
import { createDoctorStationDiagnosisHttpClient } from '#/api/doctor-station/diagnosis-api-client';

import { computed, inject, onActivated, onDeactivated, ref, watch } from 'vue';

import { ElEmpty, ElMessage, ElMessageBox } from 'element-plus';

import { ENSURE_CLINICAL_RECEPTION_KEY } from '#/components/doctor-station/clinical-reception-context';
import { COMMON_DIAGNOSIS_MAINTAIN_VERSION_KEY } from '#/components/doctor-station/diagnosis/common-diagnosis-maintain.context';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  GENERAL_OUTPATIENT_MEDICAL_TYPE_CODE,
  resolveOpspMedicalTypeCode,
} from '#/utils/chronic-medical-type';

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
  saveOutpatientEncounterDiagnosisMedicalTypeRows,
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
const unbindCommonLoading = ref(false);
const pickerOpen = ref(false);
/** 正在切换主诊的就诊诊断行主键 */
const switchingMainId = ref('');

const diagnosisHttp = computed(() =>
  createDoctorStationDiagnosisHttpClient(
    props.stationScope === 'inpatient'
      ? '/inpatientDoctorStationDiagnosis'
      : '/doctorStationDiagnosis',
  ),
);

const ensureClinicalReception = inject(ENSURE_CLINICAL_RECEPTION_KEY, undefined);
/** 工具栏维护常用诊断后递增，用于刷新快捷树 */
const commonDxMaintainVersion = inject(COMMON_DIAGNOSIS_MAINTAIN_VERSION_KEY, null);

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

/** 工具栏维护常用诊断变更后刷新快捷树（KeepAlive 下即使未激活也更新，激活时即可见） */
watch(
  () => commonDxMaintainVersion?.value,
  (ver, prev) => {
    if (ver === undefined || ver === null) return;
    if (prev === undefined) return;
    if (ver === prev) return;
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

/**
 * 未匹配慢病备案点击：调用 IF-DOR-06C 仅补录 chronic_spec 后导入。
 * @param src 侧栏慢病备案行
 */
async function handleEnsureOpspAppend(src: OpspFilingDiagnosisRowDto) {
  // 住院站无慢病备案导入
  if (props.stationScope !== 'outpatient') {
    ElMessage.warning(src.matchHint || '该慢病备案不可导入');
    return;
  }
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return;
  }
  const encounterId = props.encounterId?.trim();
  const patientId = props.patientId?.trim();
  const opspDiseCode = String(src.opspDiseCode ?? '').trim();
  // 缺就诊/患者/备案码时无法补录
  if (!encounterId || !patientId || !opspDiseCode) {
    ElMessage.warning(src.matchHint || '该慢病备案不可导入');
    return;
  }
  quickSaveLoading.value = true;
  try {
    const ensured = await httpEnsureOpspFilingDiagnosis({
      encounterId,
      patientId,
      opspDiseCode,
      opspDiseName: src.opspDiseName ?? undefined,
    });
    const defId = String(ensured.conditionDefinitionId ?? '').trim();
    if (!defId) {
      ElMessage.warning('慢病目录补录失败，请联系信息科');
      return;
    }
    // 本地把该备案行标为已匹配，避免重复点击再补录
    if (opspFiling.value?.filingList) {
      opspFiling.value = {
        ...opspFiling.value,
        filingList: opspFiling.value.filingList.map((row) => {
          if (String(row.opspDiseCode ?? '').trim() !== opspDiseCode) {
            return row;
          }
          return {
            ...row,
            matchStatus: 'matched',
            matchHint: undefined,
            conditionDefinitionId: defId,
            conditionCode: ensured.conditionCode ?? opspDiseCode,
            conditionDefinitionName:
              ensured.conditionDefinitionName ?? row.opspDiseName ?? undefined,
            diagnosisKind: ensured.diagnosisKind ?? row.diagnosisKind,
            medicalTypeCode: ensured.medicalTypeCode ?? row.medicalTypeCode,
          };
        }),
      };
    }
    // 已在当前就诊列表：只提示，不重复保存
    if (diagnosisRows.value.some((r) => String(r.conditionDefinitionId ?? '').trim() === defId)) {
      if (ensured.created) {
        ElMessage.info('已补录慢病目录，该诊断已在当前列表中');
      } else {
        ElMessage.info('该诊断已在当前列表中');
      }
      return;
    }
    await persistMergedRows([
      ...diagnosisRows.value,
      {
        conditionDefinitionId: defId,
        conditionCode: ensured.conditionCode ?? opspDiseCode,
        conditionDefinitionName: ensured.conditionDefinitionName ?? src.opspDiseName ?? '',
        medicineType: ensured.diagnosisKind ?? src.diagnosisKind ?? undefined,
        diagnosisKind: ensured.diagnosisKind ?? src.diagnosisKind ?? undefined,
        medicalTypeCode: resolveOpspMedicalTypeCode(ensured.medicalTypeCode ?? src.medicalTypeCode),
      },
    ]);
  } catch (e: unknown) {
    let msg = '';
    if (e && typeof e === 'object' && 'message' in e) {
      msg = String((e as { message?: unknown }).message ?? '');
    }
    ElMessage.warning(msg || src.matchHint || '慢病目录补录失败');
  } finally {
    quickSaveLoading.value = false;
  }
}

/** 侧栏个人/科室常用：解绑模板并刷新快捷树 */
async function handleQuickTreeUnbind(templateId: string) {
  const id = preserveApiIdForRequest(templateId) ?? String(templateId ?? '').trim();
  if (!id) {
    ElMessage.warning('缺少模板标识，无法删除');
    return;
  }
  unbindCommonLoading.value = true;
  try {
    await diagnosisHttp.value.httpUnbindCommonDiagnosis({ templateIds: [id] });
    ElMessage.success('已删除常用诊断');
    await reload();
  } catch {
    ElMessage.error('删除常用诊断失败，请稍后重试');
  } finally {
    unbindCommonLoading.value = false;
  }
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

/** 门诊：诊断类型（普通门诊/门诊慢病等）变更后保存 */
async function handleMedicalTypeChange(row: EncounterDiagnosisRowDto, code: string | undefined) {
  if (props.stationScope !== 'outpatient') {
    return;
  }
  if (ensureClinicalReception) {
    const ok = await ensureClinicalReception();
    if (!ok) return;
  }
  const prevCode = String(row.medicalTypeCode ?? '').trim() || GENERAL_OUTPATIENT_MEDICAL_TYPE_CODE;
  const nextCode = String(code ?? '').trim();
  if (prevCode === nextCode) {
    return;
  }
  const groupKey = row.tcmSyndromeGroup?.trim();
  // 中医病证组同步医疗类别；西医只改当前行
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
      medicalTypeCode: code ?? null,
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
    await saveOutpatientEncounterDiagnosisMedicalTypeRows(encounterId, patientId, next);
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
          :unbind-loading="unbindCommonLoading"
          @append="handleQuickAppend"
          @ensure-opsp-append="handleEnsureOpspAppend"
          @unbind="handleQuickTreeUnbind"
        />
        <DiagnosisSavedTable
          :rows="displayDiagnosisRows"
          :station-scope="props.stationScope"
          :ipt-disease-type-options="businessClass?.iptDiseaseTypeOptions ?? []"
          :medical-type-options="businessClass?.medicalTypeOptions ?? []"
          :switching-main-id="switchingMainId"
          @add-click="handleAddDiagnosisClick"
          @remove="handleRemove"
          @switch-main="handleSwitchMain"
          @ipt-type-change="handleIptTypeChange"
          @medical-type-change="handleMedicalTypeChange"
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
