<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';

import type {
  NursingRecordPaperHeader,
  NursingRecordViewRow,
} from './components/nursing-record.types';

import { computed, onMounted, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import {
  httpDeleteNurseStationNursingRecord,
  httpListNurseStationNursingRecords,
  httpSaveNurseStationNursingRecordsBatch,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-nursing-record.http';
import {
  FORM_KEYBOARD_GRID_NAV_KEYS,
  useFormKeyboardNavigation,
} from '#/hooks/use-form-keyboard-navigation';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import NursingRecordPaperTable from './components/nursing-record-paper-table.vue';
import { isNursingRecordRowEmpty } from './components/nursing-record-row-state';
import { resolvePaperTableFocusIndex } from './services/nursing-record-paper-keyboard';
import {
  applyBatchSavedRowsToViewRows,
  applyNowTimeIfEmpty,
  buildWeekSkeletonRows,
  createEmptyNursingRecordRow,
  formatDateMd,
  getNowDateTime,
  getWeekStartDate,
  mapApiPaperHeaderToView,
  mapViewRowToBatchSaveItem,
  mergePaperNursingRecordRows,
  resolveCurrentWeekRange,
} from './services/map-nurse-station-nursing-record';

interface Props {
  patients?: NurseStationModulePatient[];
}

defineOptions({
  name: 'InpatientNurseStationNursingRecordModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

const recordList = ref<NursingRecordViewRow[]>([]);
const paperHeaderInfo = ref<NursingRecordPaperHeader | null>(null);
const formAreaRef = ref<HTMLElement | null>(null);
const savingAll = ref(false);
const deletingRowId = ref('');

const listRequest = useRequest(httpListNurseStationNursingRecords, { manual: true });
const { handleFormKeydown } = useFormKeyboardNavigation(formAreaRef, {
  keys: FORM_KEYBOARD_GRID_NAV_KEYS,
  navigationMode: 'grid',
  gridTolerance: { row: 32, col: 48 },
  resolveFocusIndex: resolvePaperTableFocusIndex,
});

/** 护理记录仅支持左侧单选患者 */
const canOperateRecord = computed(() => props.patients.length === 1);

/** 当前选中就诊上下文 */
const selectedPatientContext = computed(() => {
  if (!canOperateRecord.value) {
    return null;
  }
  const patient = props.patients[0];
  if (!patient) {
    return null;
  }
  const encounterId = preserveApiIdForRequest(patient.id);
  const patientId = preserveApiIdForRequest(patient.patientId ?? patient.id);
  if (!encounterId || !patientId) {
    return null;
  }
  return { encounterId, patientId, patient };
});

/** 纸质单展示顺序：已填行按时间排序，空白预制行保持原位 */
const paperRecords = computed(() => {
  return recordList.value.toSorted((a, b) => {
    const aEmpty = isNursingRecordRowEmpty(a) && !a.vitalSignId;
    const bEmpty = isNursingRecordRowEmpty(b) && !b.vitalSignId;
    if (!aEmpty && !bEmpty) {
      const aTime = new Date(`${a.recordDate}T${a.recordTime || '00:00'}:00`).getTime();
      const bTime = new Date(`${b.recordDate}T${b.recordTime || '00:00'}:00`).getTime();
      return aTime - bTime;
    }
    if (aEmpty !== bEmpty) {
      return aEmpty ? 1 : -1;
    }
    return 0;
  });
});

/** 当周纸质单日期范围文案 */
const paperWeekRangeLabel = computed(() => {
  const weekStart = getWeekStartDate();
  const weekEnd = new Date(weekStart);
  weekEnd.setDate(weekEnd.getDate() + 6);
  return `本周 ${formatDateMd(weekStart)} ~ ${formatDateMd(weekEnd)}`;
});

/** 纸质单未保存条数 */
const paperUnsavedCount = computed(() => recordList.value.filter((row) => row.isDirty).length);

/** 加载当周护理记录并与骨架行合并 */
async function loadNursingRecords() {
  const context = selectedPatientContext.value;
  if (!context) {
    recordList.value = [];
    paperHeaderInfo.value = null;
    return;
  }

  const weekRange = resolveCurrentWeekRange();
  const response = await listRequest.run({
    encounterId: context.encounterId,
    patientId: context.patientId,
    recordedAtFrom: weekRange.from,
    recordedAtTo: weekRange.to,
    sortAsc: true,
  });

  const skeletonRows = buildWeekSkeletonRows();
  recordList.value = mergePaperNursingRecordRows(skeletonRows, response.rows ?? []);
  paperHeaderInfo.value = mapApiPaperHeaderToView(response.paperHeader);
}
/** 纸质单新增额外时点行 */
function handlePaperAddRow() {
  const nowDateTime = getNowDateTime();
  const latest = recordList.value.at(-1);
  recordList.value.push({
    ...createEmptyNursingRecordRow({
      nurseSignature: latest?.nurseSignature || '',
      recordDate: nowDateTime.date,
      recordTime: nowDateTime.time,
    }),
    rowType: 'extra',
    isDirty: true,
  });
}

/** 纸质单批量保存全部未保存行 */
async function handlePaperSaveAll() {
  const context = selectedPatientContext.value;
  if (!context) {
    ElMessage.warning('请先选择患者');
    return;
  }

  const dirtyItems = recordList.value
    .map((row) => mapViewRowToBatchSaveItem(row))
    .filter((item): item is NonNullable<typeof item> => item !== null);

  if (dirtyItems.length === 0) {
    ElMessage.info('没有需要保存的变更');
    return;
  }

  savingAll.value = true;
  try {
    const response = await httpSaveNurseStationNursingRecordsBatch({
      encounterId: context.encounterId,
      patientId: context.patientId,
      records: dirtyItems,
    });
    recordList.value = applyBatchSavedRowsToViewRows(recordList.value, response.rows ?? []);
    ElMessage.success(`已保存 ${response.savedCount ?? dirtyItems.length} 条护理记录`);
  } finally {
    savingAll.value = false;
  }
}

/** 删除额外行；已落库行调删除接口 */
async function handlePaperDeleteRow(id: string) {
  const context = selectedPatientContext.value;
  const target = recordList.value.find((item) => item.id === id);
  if (!target || target.rowType !== 'extra') {
    ElMessage.warning('骨架行不可删除，可清空内容后保存');
    return;
  }

  if (target.vitalSignId && context) {
    deletingRowId.value = id;
    try {
      await httpDeleteNurseStationNursingRecord({
        vitalSignId: target.vitalSignId,
        encounterId: context.encounterId,
        patientId: context.patientId,
      });
    } finally {
      deletingRowId.value = '';
    }
  }

  recordList.value = recordList.value.filter((item) => item.id !== id);
  if (recordList.value.length === 0) {
    recordList.value = buildWeekSkeletonRows();
  }
  ElMessage.success('已删除记录');
}

/** 光标落入时间单元格时，自动写入当前时间并刷新视图。 */
function handleEnsureRowDatetime(id: string) {
  const index = recordList.value.findIndex((item) => item.id === id);
  if (index < 0) {
    return;
  }
  const row = recordList.value[index];
  if (!row || !applyNowTimeIfEmpty(row)) {
    return;
  }
  recordList.value.splice(index, 1, { ...row });
}

/** 纸质单字段变更时标记未保存 */
function handlePaperMarkDirty(id: string) {
  const target = recordList.value.find((item) => item.id === id);
  if (!target) {
    return;
  }
  target.isDirty = true;
}

/** 在护理记录编辑区统一处理键盘导航 */
function handleRecordAreaKeydown(event: KeyboardEvent) {
  handleFormKeydown(event);
}

onMounted(() => {
  void loadNursingRecords();
});

watch(
  selectedPatientContext,
  () => {
    void loadNursingRecords();
  },
  { deep: true },
);
</script>

<template>
  <div class="nursing-record-page">
    <ElEmpty v-if="!canOperateRecord" description="请先从左侧选择一位患者" />

    <div
      v-else
      ref="formAreaRef"
      v-loading="listRequest.loading.value"
      class="record-content"
      @keydown.capture="handleRecordAreaKeydown"
    >
      <NursingRecordPaperTable
        :paper-header="paperHeaderInfo"
        :rows="paperRecords"
        :saving-all="savingAll"
        :unsaved-count="paperUnsavedCount"
        :week-range-label="paperWeekRangeLabel"
        @add-row="handlePaperAddRow"
        @delete-row="handlePaperDeleteRow"
        @ensure-datetime="handleEnsureRowDatetime"
        @mark-dirty="handlePaperMarkDirty"
        @save-all="handlePaperSaveAll"
      />
    </div>
  </div>
</template>

<style scoped>
.nursing-record-page {
  display: flex;
  height: 100%;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: #f3f5f8;
}
.record-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  min-height: 0;
}
</style>
