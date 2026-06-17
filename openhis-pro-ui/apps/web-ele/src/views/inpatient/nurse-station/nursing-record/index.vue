<script setup lang="ts">
import type {
  NursingRecordPaperHeader,
  NursingRecordPatient,
  NursingRecordRow,
  NursingRecordViewRow,
} from './components/nursing-record.types';

import { computed, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import {
  FORM_KEYBOARD_GRID_NAV_KEYS,
  useFormKeyboardNavigation,
} from '#/hooks/use-form-keyboard-navigation';

import { resolvePaperTableFocusIndex } from './services/nursing-record-paper-keyboard';

import NursingRecordPaperTable from './components/nursing-record-paper-table.vue';
import NursingRecordTimelineItem from './components/nursing-record-timeline-item.vue';

interface Props {
  patients?: NursingRecordPatient[];
}

const props = withDefaults(defineProps<Props>(), { patients: () => [] });

defineOptions({
  name: 'InpatientNurseStationNursingRecordModule',
});

type RecordViewMode = 'paper' | 'timeline';

const activePatient = computed(() => props.patients[0]);

/** 纸质单抬头患者信息，展示在表格上方。 */
const paperHeaderInfo = computed<NursingRecordPaperHeader | null>(() => {
  if (!activePatient.value) {
    return null;
  }
  const patient = activePatient.value;
  return {
    admitAt: '2026-01-13',
    age: patient.age,
    bedNo: patient.bedNo,
    dischargeAt: '',
    gender: patient.gender,
    inpatientNo: patient.id,
    name: patient.name,
    wardName: patient.wardName,
  };
});

let rowSeed = 0;
const recordList = ref<NursingRecordViewRow[]>([]);
const editBackupMap = ref<Record<string, NursingRecordRow>>({});
const formAreaRef = ref<HTMLElement | null>(null);
const viewMode = ref<RecordViewMode>('paper');
const { handleFormKeydown } = useFormKeyboardNavigation(formAreaRef, {
  keys: FORM_KEYBOARD_GRID_NAV_KEYS,
  navigationMode: 'grid',
  gridTolerance: { row: 32, col: 48 },
  resolveFocusIndex: resolvePaperTableFocusIndex,
});

/** 按日期+时间倒序排列护理记录，确保最近记录优先显示。 */
const timelineRecords = computed(() => {
  return recordList.value.toSorted((a, b) => {
    const aTime = new Date(`${a.recordDate}T${a.recordTime || '00:00'}:00`).getTime();
    const bTime = new Date(`${b.recordDate}T${b.recordTime || '00:00'}:00`).getTime();
    return bTime - aTime;
  });
});

/** 纸质单按时间正序展示，与纸质记录单书写习惯一致。 */
const paperRecords = computed(() => {
  return recordList.value.toSorted((a, b) => {
    const aTime = new Date(`${a.recordDate}T${a.recordTime || '00:00'}:00`).getTime();
    const bTime = new Date(`${b.recordDate}T${b.recordTime || '00:00'}:00`).getTime();
    return aTime - bTime;
  });
});

/** 当周纸质单日期范围文案（周一至周日）。 */
const paperWeekRangeLabel = computed(() => {
  const weekStart = getWeekStartDate();
  const weekEnd = new Date(weekStart);
  weekEnd.setDate(weekEnd.getDate() + 6);
  return `本周 ${formatDateMd(weekStart)} ~ ${formatDateMd(weekEnd)}`;
});

/** 纸质单未保存条数，供工具栏提示。 */
const paperUnsavedCount = computed(() => {
  return recordList.value.filter((row) => row.isDirty).length;
});

/** 生成一条护理记录草稿，支持传入局部字段覆盖默认值。 */
function createEmptyRow(partial: Partial<NursingRecordRow> = {}): NursingRecordRow {
  rowSeed += 1;
  return {
    id: `record-${rowSeed}`,
    recordDate: '',
    recordTime: '',
    consciousness: '',
    temperature: '',
    heartRate: '',
    pulse: '',
    respiratory: '',
    bloodPressure: '',
    pupilLeft: '',
    pupilRight: '',
    oxygenSaturation: '',
    oxygenRate: '',
    oxygenMode: '',
    oxygenFlow: '',
    intakeName: '',
    intakeAmount: '',
    intakeRoute: '',
    outputName: '',
    outputAmount: '',
    outputColorCharacter: '',
    reservedCol1: '',
    reservedCol2: '',
    skinStatus: '',
    tubeCare: '',
    conditionMeasure: '',
    nurseSignature: '',
    ...partial,
  };
}
/** 纸质单每日预置录入时点。 */
const PAPER_RECORD_TIMES = ['08:00', '14:00'] as const;

/** 取本周一作为周起始日。 */
function getWeekStartDate(base = new Date()) {
  const date = new Date(base);
  const weekday = date.getDay();
  const offset = weekday === 0 ? -6 : 1 - weekday;
  date.setDate(date.getDate() + offset);
  date.setHours(0, 0, 0, 0);
  return date;
}

/** 格式化为 YYYY-MM-DD。 */
function formatDateYmd(date: Date) {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** 格式化为 MM-DD，用于周范围展示。 */
function formatDateMd(date: Date) {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${month}-${day}`;
}

/** 生成当周 7 天纸质单骨架行，并写入演示数据。 */
function buildWeekPaperRows(patientName?: string): NursingRecordViewRow[] {
  const weekStart = getWeekStartDate();
  const rows: NursingRecordViewRow[] = [];

  // 按周一至周日、每日固定时点展开行
  for (let dayIndex = 0; dayIndex < 7; dayIndex += 1) {
    const dayDate = new Date(weekStart);
    dayDate.setDate(dayDate.getDate() + dayIndex);
    const recordDate = formatDateYmd(dayDate);

    for (const recordTime of PAPER_RECORD_TIMES) {
      rows.push({
        ...createEmptyRow({ recordDate, recordTime }),
        rowType: 'skeleton',
      });
    }
  }

  if (rows[0]) {
    Object.assign(rows[0], {
      bloodPressure: '128/76',
      conditionMeasure: '患者神志清，继续常规巡视，观察末梢循环。',
      consciousness: '清醒',
      heartRate: '82',
      nurseSignature: '责任护士A',
      oxygenSaturation: '98',
      pulse: '82',
      respiratory: '20',
      skinStatus: '完整',
      temperature: '36.8',
      tubeCare: '静脉通路',
    });
  }
  if (rows[1]) {
    Object.assign(rows[1], {
      bloodPressure: '126/74',
      conditionMeasure: `${patientName || '患者'} 午间一般情况平稳，宣教翻身与饮水。`,
      consciousness: '清醒',
      heartRate: '78',
      intakeAmount: '250',
      intakeName: '温开水',
      nurseSignature: '责任护士A',
      outputAmount: '180',
      outputColorCharacter: '淡黄清亮',
      outputName: '尿液',
      pulse: '78',
      respiratory: '19',
      skinStatus: '完整',
      temperature: '36.7',
    });
  }

  return rows;
}
/** 清理指定记录的编辑备份，避免取消时回滚到过期快照。 */
function clearEditBackup(id: string) {
  const { [id]: _removed, ...rest } = editBackupMap.value;
  editBackupMap.value = rest;
}
/** 生成当前时刻的日期与时间（YYYY-MM-DD / HH:mm）。 */
function getNowDateTime() {
  const now = new Date();
  const year = now.getFullYear();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  const hours = `${now.getHours()}`.padStart(2, '0');
  const minutes = `${now.getMinutes()}`.padStart(2, '0');
  return {
    date: `${year}-${month}-${day}`,
    time: `${hours}:${minutes}`,
  };
}
/** 新增一条护理记录并直接进入编辑态。 */
function handleAddRecord(baseRow?: NursingRecordRow) {
  const nowDateTime = getNowDateTime();
  const latest = baseRow || recordList.value.at(-1);
  recordList.value.unshift({
    ...createEmptyRow({
      nurseSignature: latest?.nurseSignature || '',
      recordDate: nowDateTime.date,
      recordTime: nowDateTime.time,
    }),
    isEditing: true,
    isNew: true,
  });
}
/** 复制指定护理记录，便于连续时段快速补录。 */
function handleCopyRecord(row: NursingRecordRow) {
  recordList.value.push(
    createEmptyRow({
      ...row,
      recordTime: '',
    }),
  );
  ElMessage.success('已复制记录');
}
/** 删除指定护理记录，删空后恢复当周骨架行。 */
function handleDeleteRecord(id: string) {
  recordList.value = recordList.value.filter((item) => item.id !== id);
  clearEditBackup(id);
  if (recordList.value.length === 0) {
    recordList.value = buildWeekPaperRows(activePatient.value?.name);
  }
}
/** 将展示卡片切换到编辑态并缓存取消时所需快照。 */
function handleEditRecord(row: NursingRecordViewRow) {
  if (row.isEditing) return;
  editBackupMap.value[row.id] = { ...row };
  row.isEditing = true;
}
/** 保存当前编辑卡片并返回展示态。 */
function handleSaveRecord(row: NursingRecordViewRow) {
  row.isEditing = false;
  row.isNew = false;
  clearEditBackup(row.id);
  ElMessage.success('护理记录已保存');
}
/** 取消编辑：新建卡片直接移除，已有卡片回滚到编辑前快照。 */
function handleCancelEditRecord(row: NursingRecordViewRow) {
  if (row.isNew) {
    handleDeleteRecord(row.id);
    return;
  }
  const backup = editBackupMap.value[row.id];
  if (!backup) {
    row.isEditing = false;
    return;
  }
  Object.assign(row, backup, { isEditing: false, isNew: false });
  clearEditBackup(row.id);
}
/** 打印护理记录单（IP-07 · nursing_record 模板）。 */
async function handlePrintRecord(_row?: NursingRecordRow) {
  const patient = activePatient.value;
  if (!patient?.id) {
    ElMessage.warning('请先选择患者');
    return;
  }
  try {
    await printReportTemplate({
      templateCode: 'nursing_record',
      encounterId: patient.id,
      jobName: `护理记录单-${patient.name}`,
    });
    ElMessage.success('护理记录单已发送打印');
  } catch {
    // composable 已提示
  }
}
/** 纸质单模式：新增额外时点行并写入当前时刻。 */
function handlePaperAddRow() {
  const nowDateTime = getNowDateTime();
  const latest = recordList.value.at(-1);
  recordList.value.push({
    ...createEmptyRow({
      nurseSignature: latest?.nurseSignature || '',
      recordDate: nowDateTime.date,
      recordTime: nowDateTime.time,
    }),
    rowType: 'extra',
  });
}
/** 纸质单模式：保存全部记录并清除未保存标记。 */
function handlePaperSaveAll() {
  recordList.value.forEach((row) => {
    row.isDirty = false;
  });
  ElMessage.success('护理记录已保存');
}
/** 纸质单模式：仅允许删除额外插入行。 */
function handlePaperDeleteRow(id: string) {
  const target = recordList.value.find((item) => item.id === id);
  if (!target || target.rowType !== 'extra') {
    ElMessage.warning('骨架行不可删除，可清空内容后保存');
    return;
  }
  handleDeleteRecord(id);
}
/** 纸质单字段变更时标记该行未保存。 */
function handlePaperMarkDirty(id: string) {
  const target = recordList.value.find((item) => item.id === id);
  if (!target) {
    return;
  }
  target.isDirty = true;
}
/** 在护理记录编辑区统一处理键盘导航（纸质单与时间轴均启用）。 */
function handleRecordAreaKeydown(event: KeyboardEvent) {
  handleFormKeydown(event);
}

watch(
  activePatient,
  (patient) => {
    recordList.value = buildWeekPaperRows(patient?.name);
    editBackupMap.value = {};
  },
  { immediate: true },
);
</script>

<template>
  <div class="nursing-record-page">
    <ElEmpty v-if="!activePatient" description="请先从左侧选择一位患者" />

    <div v-else ref="formAreaRef" class="record-content" @keydown.capture="handleRecordAreaKeydown">
      <NursingRecordPaperTable
        v-if="viewMode === 'paper'"
        :paper-header="paperHeaderInfo"
        :rows="paperRecords"
        :unsaved-count="paperUnsavedCount"
        :week-range-label="paperWeekRangeLabel"
        @add-row="handlePaperAddRow"
        @delete-row="handlePaperDeleteRow"
        @mark-dirty="handlePaperMarkDirty"
        @print="handlePrintRecord()"
        @save-all="handlePaperSaveAll"
      >
        <template #toolbar-extra>
          <ElSegmented
            v-model="viewMode"
            :options="[
              { label: '纸质单', value: 'paper' },
              { label: '时间轴', value: 'timeline' },
            ]"
          />
        </template>
      </NursingRecordPaperTable>

      <template v-else>
        <div class="record-toolbar">
          <ElSegmented
            v-model="viewMode"
            :options="[
              { label: '纸质单', value: 'paper' },
              { label: '时间轴', value: 'timeline' },
            ]"
          />
        </div>

        <div class="record-timeline">
          <NursingRecordTimelineItem
            v-for="(row, index) in timelineRecords"
            :key="row.id"
            :index="index"
            :row="row"
            :total="timelineRecords.length"
            @add="handleAddRecord"
            @cancel="handleCancelEditRecord"
            @copy="handleCopyRecord"
            @delete="(item) => handleDeleteRecord(item.id)"
            @edit="handleEditRecord"
            @print="handlePrintRecord"
            @save="handleSaveRecord"
          />
        </div>
      </template>
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

.record-view-switch,
.record-toolbar {
  display: flex;
  justify-content: flex-end;
}

.record-toolbar :deep(.el-segmented) {
  --el-border-radius-base: 6px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  padding: 2px;
}

.record-timeline {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: 12px;
  box-sizing: border-box;
}
</style>
