<script setup lang="ts">
import type {
  TemperatureSheetExtraRecordDraft,
  TemperatureSheetFillRowDraft,
} from '../services/map-nurse-station-temperature-sheet';
import type {
  TemperatureSheetSpecialEventOption,
  TemperatureSheetSpecialEventStore,
} from '../services/temperature-sheet-special-events';

import type { TemperatureSheetTimeSlot } from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.types';
import type { OptionDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station.types';

import { computed, nextTick, ref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';

import {
  httpGetNurseStationTemperatureSheetData,
  httpLoadNurseStationTemperatureSheetInit,
  httpSaveNurseStationTemperatureSheetAtRecordedTime,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.http';
import { useFormKeyboardNavigation } from '#/hooks/use-form-keyboard-navigation';
import { useRequest } from '#/hooks/use-request';

import {
  applyFillSlotsToEditableDraft,
  applySavedVitalSignIdsToFillRows,
  collectTemperatureSheetSaveRequests,
  createDefaultEditableFillRows,
  createEmptyExtraRecord,
  createEmptyFillRow,
  formatTemperatureSheetDisplayDate,
  formatTemperatureSheetTimeSlotHm,
  hasEditableFillRowVitalContent,
  isBloodPressureInputDisabled,
  resolveMergedTemperatureSheetGridRows,
  shiftTemperatureSheetRecordDateYmd,
  syncFillRowChartTimeSlot,
} from '../services/map-nurse-station-temperature-sheet';
import {
  buildSpecialEventLabelMap,
  buildSpecialEventSelectOptions,
  buildSpecialEventStoreFromGridRows,
  createEmptySpecialEventStore,
  extractSlotFlagsFromFillRows,
  mergeSlotFlagsForDate,
  syncSpecialEventShortcutFields,
  syncSpecialEventTimesOnSelectionChange,
  validateFillRowsSpecialEvents,
  validateSpecialEventStore,
} from '../services/temperature-sheet-special-events';
import TemperatureSheetFillSlotSpecial from './temperature-sheet-fill-slot-special.vue';
import TemperatureSheetBottomTimePicker from './temperature-sheet-bottom-time-picker.vue';
import '../styles/temperature-sheet-fill-drawer.css';

defineOptions({
  name: 'TemperatureSheetSingleFillDrawer',
});

/** 单人录入目标患者 */
export interface TemperatureSheetSingleFillPatient {
  admissionAt?: string;
  bedNo: string;
  encounterId: string;
  gender?: string;
  name: string;
  patientId: string;
}

interface Props {
  /** 嵌套在批量抽屉之上时提高层级 */
  zIndex?: number;
}

const props = withDefaults(defineProps<Props>(), {
  zIndex: 3001,
});

const visible = defineModel<boolean>('visible', { default: false });

const emit = defineEmits<{
  saved: [encounterId: string];
}>();

const WINDOW_DAY_COUNT = 7;
const WINDOW_RANGE = '7d' as const;

const dataRequest = useRequest(httpGetNurseStationTemperatureSheetData, { manual: true });
const initRequest = useRequest(httpLoadNurseStationTemperatureSheetInit, { manual: true });

const currentPatient = ref<TemperatureSheetSingleFillPatient | null>(null);
const recordDate = ref('');
const activeFillSlotIndex = ref(0);
const savingAll = ref(false);
const fillFormAreaRef = ref<HTMLElement | null>(null);
const tempRecordRows = ref<TemperatureSheetFillRowDraft[]>(createDefaultEditableFillRows());
const extraRecord = ref<TemperatureSheetExtraRecordDraft>(createEmptyExtraRecord());
const specialEventStore = ref<TemperatureSheetSpecialEventStore>(createEmptySpecialEventStore());
const specialEventOptions = ref<TemperatureSheetSpecialEventOption[]>([]);

const specialEventSelectOptions = computed(() =>
  buildSpecialEventSelectOptions(specialEventOptions.value),
);
const specialEventLabelMap = computed(() => buildSpecialEventLabelMap(specialEventOptions.value));
/** 单人录入最多条数（防误点） */
const MAX_EDITABLE_FILL_ROWS = 48;
const canAddFillRow = computed(() => tempRecordRows.value.length < MAX_EDITABLE_FILL_ROWS);

const patientTitleText = computed(() => {
  const patient = currentPatient.value;
  if (!patient) {
    return '';
  }
  const bedNo = patient.bedNo?.trim() || '-';
  const name = patient.name?.trim() || '-';
  const gender = patient.gender?.trim();
  return gender ? `${bedNo}床 ${name}（${gender}）` : `${bedNo}床 ${name}`;
});

/** 补充记录中前一日统计项的归属日期（录入日 - 1） */
const supplementStatisticalDateLabel = computed(() => {
  const entryDate = recordDate.value.trim();
  if (!entryDate) {
    return '';
  }
  return formatTemperatureSheetDisplayDate(shiftTemperatureSheetRecordDateYmd(entryDate, -1));
});

const { handleFormKeydown: handleFillFormKeydown } = useFormKeyboardNavigation(fillFormAreaRef, {
  keys: ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Enter'],
  resolveFocusIndex: ({ currentIndex, direction, event, focusable }) => {
    const currentTarget = focusable[currentIndex];
    const currentCell = currentTarget?.closest<HTMLElement>('.nav-cell');
    if ((event.key === 'ArrowUp' || event.key === 'ArrowDown') && currentCell) {
      const { navCol, navGroup, navRow } = currentCell.dataset;
      if (navGroup === 'vital' && navCol !== undefined && navRow !== undefined) {
        const nextRow = Number(navRow) + (event.key === 'ArrowUp' ? -1 : 1);
        const nextCell = fillFormAreaRef.value?.querySelector<HTMLElement>(
          `.nav-cell[data-nav-group="${navGroup}"][data-nav-col="${navCol}"][data-nav-row="${nextRow}"]`,
        );
        if (nextCell) {
          const nextFocusableIndex = focusable.findIndex(
            (el) => el === nextCell || nextCell.contains(el),
          );
          if (nextFocusableIndex !== -1) return nextFocusableIndex;
        }
      }
    }
    return currentIndex + direction;
  },
});

/** 将 init 接口 OptionDto 转为特殊事件下拉项 */
function mapSpecialEventOptionsFromInit(options: OptionDto[] | null | undefined) {
  return (options ?? [])
    .map((option) => ({
      label: (option.label ?? '').trim(),
      value: String(option.value ?? '').trim(),
    }))
    .filter((option) => option.value.length > 0);
}

/** 加载特殊事件字典（首次打开时） */
async function ensureSpecialEventOptionsLoaded(): Promise<void> {
  if (specialEventOptions.value.length > 0) {
    return;
  }
  const initRes = await initRequest.run();
  specialEventOptions.value = mapSpecialEventOptionsFromInit(initRes.specialEventOptions);
}

/** 按时次解析默认激活行索引 */
function resolveActiveSlotIndex(
  timeSlot?: TemperatureSheetTimeSlot,
  rows: TemperatureSheetFillRowDraft[] = tempRecordRows.value,
): number {
  if (!timeSlot) {
    return 0;
  }
  const index = rows.findIndex((row) => row.timeSlot === timeSlot);
  return index >= 0 ? index : 0;
}

/** 加载指定患者、日期的录入回显 */
async function loadFillDrawerSlots(): Promise<void> {
  const patient = currentPatient.value;
  if (!patient || !recordDate.value.trim()) {
    tempRecordRows.value = createDefaultEditableFillRows();
    extraRecord.value = createEmptyExtraRecord();
    specialEventStore.value = createEmptySpecialEventStore();
    return;
  }

  const response = await dataRequest.run({
    encounterId: patient.encounterId,
    patientId: patient.patientId,
    windowRange: WINDOW_RANGE,
    pageIndex: 0,
    focusRecordDate: recordDate.value,
  });

  const sheetGridRows = resolveMergedTemperatureSheetGridRows(response, WINDOW_DAY_COUNT);
  specialEventStore.value = buildSpecialEventStoreFromGridRows(sheetGridRows);

  if (response.fillSlots) {
    const draft = applyFillSlotsToEditableDraft(response.fillSlots);
    draft.rows.forEach((row) => {
      syncSpecialEventTimesOnSelectionChange(row);
      syncSpecialEventShortcutFields(row);
    });
    tempRecordRows.value = draft.rows;
    extraRecord.value = draft.extraRecord;
    return;
  }

  tempRecordRows.value = createDefaultEditableFillRows();
  extraRecord.value = createEmptyExtraRecord();
}

/** 修改行测量时间（自选时分，禁止与其他行重复） */
function handleRowRecordTimeChange(row: TemperatureSheetFillRowDraft, timeHm: null | string): void {
  if (!timeHm?.trim()) {
    return;
  }
  const nextTime = timeHm.trim();
  if (nextTime === row.recordTime) {
    return;
  }
  const duplicated = tempRecordRows.value.some(
    (item) => item.rowUid !== row.rowUid && item.recordTime === nextTime,
  );
  if (duplicated) {
    ElMessage.warning(`${nextTime} 已有录入行`);
    return;
  }
  row.recordTime = nextTime;
  syncFillRowChartTimeSlot(row);
  syncSpecialEventTimesOnSelectionChange(row);
  syncSpecialEventShortcutFields(row);
}

/** 手动新增一条待填写行 */
function handleAddFillRow(): void {
  if (tempRecordRows.value.length >= MAX_EDITABLE_FILL_ROWS) {
    ElMessage.warning(`单日最多 ${MAX_EDITABLE_FILL_ROWS} 条`);
    return;
  }
  const row = createEmptyFillRow();
  // 若与已有行撞同一时分，顺延一分钟
  while (tempRecordRows.value.some((item) => item.recordTime === row.recordTime)) {
    const [hourText, minuteText] = row.recordTime.split(':');
    const hour = Number(hourText);
    const minute = Number(minuteText);
    const nextMinuteTotal = hour * 60 + minute + 1;
    const nextHour = Math.floor(nextMinuteTotal / 60) % 24;
    const nextMinute = nextMinuteTotal % 60;
    row.recordTime = `${String(nextHour).padStart(2, '0')}:${String(nextMinute).padStart(2, '0')}`;
    syncFillRowChartTimeSlot(row);
  }
  tempRecordRows.value.push(row);
  activeFillSlotIndex.value = tempRecordRows.value.length - 1;
}

/** 删除录入行（至少保留 1 条） */
function handleRemoveFillRow(index: number): void {
  if (tempRecordRows.value.length <= 1) {
    ElMessage.warning('至少保留一条待填写记录');
    return;
  }
  tempRecordRows.value.splice(index, 1);
  if (activeFillSlotIndex.value >= tempRecordRows.value.length) {
    activeFillSlotIndex.value = tempRecordRows.value.length - 1;
  }
}

/** 打开后聚焦首个体温输入框 */
async function focusFirstInput(): Promise<void> {
  await nextTick();
  const rows = fillFormAreaRef.value?.querySelectorAll<HTMLElement>('.fill-row');
  const activeRow = rows?.[activeFillSlotIndex.value];
  const firstInput = activeRow?.querySelector<HTMLInputElement>('.el-input input');
  firstInput?.focus();
}

/** 预加载数据后打开抽屉 */
async function open(options: {
  activeTimeSlot?: TemperatureSheetTimeSlot;
  patient: TemperatureSheetSingleFillPatient;
  recordDate?: string;
}): Promise<void> {
  currentPatient.value = options.patient;
  recordDate.value = options.recordDate?.trim() || dayjs().format('YYYY-MM-DD');
  await ensureSpecialEventOptionsLoaded();
  await loadFillDrawerSlots();
  // 从批量入口带入时次：空行时落到该槽整点
  if (options.activeTimeSlot && tempRecordRows.value.length === 1) {
    const onlyRow = tempRecordRows.value[0];
    if (onlyRow && !hasEditableFillRowVitalContent(onlyRow)) {
      onlyRow.recordTime = formatTemperatureSheetTimeSlotHm(options.activeTimeSlot);
      syncFillRowChartTimeSlot(onlyRow);
    }
  }
  activeFillSlotIndex.value = resolveActiveSlotIndex(options.activeTimeSlot);
  visible.value = true;
  await focusFirstInput();
}

/** 录入日期加减天数并重新加载 */
function shiftRecordDateByDays(offset: number): void {
  const base = recordDate.value?.trim() ? dayjs(recordDate.value) : dayjs();
  if (!base.isValid()) {
    return;
  }
  recordDate.value = base.add(offset, 'day').format('YYYY-MM-DD');
  if (visible.value) {
    void loadFillDrawerSlots();
  }
}

/** 复制上一行生命体征（不含时间；首行不可用） */
function copyPrevRow(index: number): void {
  if (index <= 0) {
    ElMessage.warning('已是第一条，无法复制上条');
    return;
  }
  const prev = tempRecordRows.value[index - 1];
  const current = tempRecordRows.value[index];
  if (!prev || !current) {
    return;
  }
  const hasPrevVital =
    Boolean(prev.temp.trim()) ||
    Boolean(prev.pulse.trim()) ||
    Boolean(prev.respiratory.trim()) ||
    Boolean(prev.heartRate.trim()) ||
    Boolean(prev.systolic.trim()) ||
    Boolean(prev.diastolic.trim());
  if (!hasPrevVital) {
    ElMessage.warning('上一条暂无生命体征可复制');
    return;
  }
  // 整行替换，避免个别输入框未及时回显
  tempRecordRows.value[index] = {
    ...current,
    temp: prev.temp,
    pulse: prev.pulse,
    respiratory: prev.respiratory,
    heartRate: prev.heartRate,
    systolic: prev.systolic,
    diastolic: prev.diastolic,
  };
  ElMessage.success('已复制上条生命体征');
}

/** 保存前校验特殊事件（合并当日编辑到窗口存储） */
function validateSpecialEventsBeforeSave(): string | null {
  if (!recordDate.value) {
    return null;
  }
  const specialError = validateFillRowsSpecialEvents(
    tempRecordRows.value,
    specialEventLabelMap.value,
  );
  if (specialError) {
    return specialError;
  }
  const slotFlags = extractSlotFlagsFromFillRows(tempRecordRows.value, recordDate.value);
  const mergedStore = mergeSlotFlagsForDate(specialEventStore.value, recordDate.value, slotFlags);
  return validateSpecialEventStore(mergedStore);
}

/** 将首行体温同步到全部时次 */
function fillSameTemperature(value?: string): void {
  const text = (value ?? '').trim();
  if (!text) {
    return;
  }
  tempRecordRows.value.forEach((row) => {
    row.temp = text;
  });
}

/** 将首行生命体征同步到其余时次 */
function fillSameFromFirstRow(): void {
  const firstRow = tempRecordRows.value[0];
  if (!firstRow) {
    return;
  }
  tempRecordRows.value.forEach((row, index) => {
    if (index === 0) {
      return;
    }
    row.temp = firstRow.temp;
    row.pulse = firstRow.pulse;
    row.respiratory = firstRow.respiratory;
    row.heartRate = firstRow.heartRate;
  });
}

/** 保存前校验测量时间是否重复 */
function validateFillRowTimesUnique(): string | null {
  const seen = new Set<string>();
  for (const row of tempRecordRows.value) {
    if (seen.has(row.recordTime)) {
      return `${row.recordTime} 重复，请调整后再保存`;
    }
    seen.add(row.recordTime);
  }
  return null;
}

/** 批量保存当日全部已填写时次 */
async function handleSaveAllSlots(): Promise<void> {
  const patient = currentPatient.value;
  if (!patient || !recordDate.value) {
    ElMessage.warning('请先选择患者与录入日期');
    return;
  }

  const timeError = validateFillRowTimesUnique();
  if (timeError) {
    ElMessage.warning(timeError);
    return;
  }

  const saveRequests = collectTemperatureSheetSaveRequests(
    tempRecordRows.value,
    extraRecord.value,
    recordDate.value,
    patient.encounterId,
    patient.patientId,
  );
  if (saveRequests.length === 0) {
    ElMessage.warning('请至少填写一项生命体征或特殊事件');
    return;
  }

  const specialEventError = validateSpecialEventsBeforeSave();
  if (specialEventError) {
    ElMessage.warning(specialEventError);
    return;
  }

  savingAll.value = true;
  try {
    const savedResults: Array<{ timeSlot: string; vitalSignId?: string }> = [];
    // 后端单槽保存，按有时次内容逐条提交（含特殊事件）
    for (const request of saveRequests) {
      const response = await httpSaveNurseStationTemperatureSheetAtRecordedTime(request);
      savedResults.push({
        timeSlot: request.timeSlot,
        vitalSignId: response.vitalSignId ?? undefined,
      });
    }

    tempRecordRows.value = applySavedVitalSignIdsToFillRows(tempRecordRows.value, savedResults);
    await loadFillDrawerSlots();
    ElMessage.success(`已保存 ${saveRequests.length} 条体温单记录`);
    emit('saved', patient.encounterId);
  } finally {
    savingAll.value = false;
  }
}

watch(recordDate, () => {
  if (!visible.value) {
    return;
  }
  void loadFillDrawerSlots();
});

defineExpose({
  open,
});
</script>

<template>
  <ElDrawer
    v-model="visible"
    :append-to-body="true"
    :destroy-on-close="false"
    :with-header="false"
    :z-index="props.zIndex"
    class="temp-fill-drawer temp-single-fill-drawer"
    direction="btt"
    size="72%"
  >
    <div ref="fillFormAreaRef" class="fill-drawer" @keydown="handleFillFormKeydown">
      <div class="fill-toolbar">
        <div class="fill-toolbar-left">
          <div class="fill-record-date-nav">
            <ElDatePicker
              v-model="recordDate"
              format="YYYY-MM-DD"
              placeholder="录入日期"
              popper-class="temperature-sheet-fill-popper"
              teleported
              type="date"
              value-format="YYYY-MM-DD"
            />
            <ElButton plain size="small" @click="shiftRecordDateByDays(-1)">上一天</ElButton>
            <ElButton plain size="small" @click="shiftRecordDateByDays(1)">下一天</ElButton>
          </div>
          <ElTag v-if="patientTitleText" type="info">{{ patientTitleText }}</ElTag>
          <ElTag type="info">键盘快捷：←/→ 切换，↑/↓ 同列切换，Enter 下一项</ElTag>
        </div>
        <div class="fill-toolbar-right">
          <ElButton plain @click="fillSameTemperature(tempRecordRows[0]?.temp)">
            同步体温到全部
          </ElButton>
          <ElButton plain @click="fillSameFromFirstRow">同步首行生命体征</ElButton>
          <ElButton :disabled="!canAddFillRow" type="primary" @click="handleAddFillRow">
            新增
          </ElButton>
        </div>
      </div>

      <div class="fill-drawer-main">
        <section class="fill-card">
          <header class="fill-card-title">
            生命体征（主录入）
            <ElTag size="small" type="info"
              >快捷项：出院/物理降温/药物降温/体温不升；图表竖排仅展示入院/转科/手术/分娩/出院/死亡；转科/死亡/分娩/出院须选时间；入院由患者入院时间自动带入</ElTag
            >
          </header>
          <div class="fill-grid-head">
            <span>时间</span>
            <span>体温(℃)</span>
            <span>脉搏</span>
            <span>呼吸</span>
            <span>心率</span>
            <span>收缩压</span>
            <span>舒张压</span>
            <span>特殊</span>
            <span>操作</span>
          </div>
          <div class="fill-grid-body">
            <div
              v-for="(row, index) in tempRecordRows"
              :key="row.rowUid"
              class="fill-row"
              :class="{ 'is-active-slot': index === activeFillSlotIndex }"
              @click="activeFillSlotIndex = index"
            >
              <div
                class="time-slot-cell nav-cell"
                :data-nav-col="-1"
                data-nav-group="vital"
                :data-nav-row="index"
                @click.stop
              >
                <TemperatureSheetBottomTimePicker
                  :model-value="row.recordTime"
                  @update:model-value="(value) => handleRowRecordTimeChange(row, value)"
                />
              </div>
              <div class="nav-cell" :data-nav-col="0" data-nav-group="vital" :data-nav-row="index">
                <ElInput v-model="row.temp" placeholder="体温">
                  <template #suffix>℃</template>
                </ElInput>
              </div>
              <div class="nav-cell" :data-nav-col="1" data-nav-group="vital" :data-nav-row="index">
                <ElInput v-model="row.pulse" placeholder="脉搏">
                  <template #suffix>次/分</template>
                </ElInput>
              </div>
              <div class="nav-cell" :data-nav-col="2" data-nav-group="vital" :data-nav-row="index">
                <ElInput v-model="row.respiratory" placeholder="呼吸">
                  <template #suffix>次/分</template>
                </ElInput>
              </div>
              <div class="nav-cell" :data-nav-col="3" data-nav-group="vital" :data-nav-row="index">
                <ElInput v-model="row.heartRate" placeholder="心率">
                  <template #suffix>bpm</template>
                </ElInput>
              </div>
              <div class="nav-cell" :data-nav-col="4" data-nav-group="vital" :data-nav-row="index">
                <ElInput
                  v-model="row.systolic"
                  :disabled="isBloodPressureInputDisabled(row, tempRecordRows)"
                  placeholder="收缩压"
                >
                  <template #suffix>mmHg</template>
                </ElInput>
              </div>
              <div class="nav-cell" :data-nav-col="5" data-nav-group="vital" :data-nav-row="index">
                <ElInput
                  v-model="row.diastolic"
                  :disabled="isBloodPressureInputDisabled(row, tempRecordRows)"
                  placeholder="舒张压"
                >
                  <template #suffix>mmHg</template>
                </ElInput>
              </div>
              <TemperatureSheetFillSlotSpecial
                :row="row"
                :special-event-select-options="specialEventSelectOptions"
              />
              <div class="fill-row-action">
                <ElButton
                  :disabled="index === 0"
                  link
                  type="primary"
                  @click.stop="copyPrevRow(index)"
                >
                  复制上条
                </ElButton>
                <ElButton
                  :disabled="tempRecordRows.length <= 1"
                  link
                  type="danger"
                  @click.stop="handleRemoveFillRow(index)"
                >
                  删除
                </ElButton>
              </div>
            </div>
          </div>
        </section>

        <section class="fill-card fill-extra-card">
          <header class="fill-card-title">
            补充记录
            <ElTag size="small" type="info">
              大便次数、小便次数、总入量、其他、尿量为前一日（{{
                supplementStatisticalDateLabel || '—'
              }}）统计，保存后显示在前一日列
            </ElTag>
          </header>
          <div class="extra-grid">
            <div class="extra-item">
              <span class="extra-label">体重</span>
              <ElInput v-model="extraRecord.weight" placeholder="体重">
                <template #suffix>kg</template>
              </ElInput>
            </div>
            <div class="extra-item">
              <span class="extra-label">小便次数</span>
              <ElInput v-model="extraRecord.urineCount" placeholder="次数">
                <template #suffix>次</template>
              </ElInput>
            </div>
            <div class="extra-item">
              <span class="extra-label">腹围</span>
              <ElInput v-model="extraRecord.abdominalCircumference" placeholder="腹围">
                <template #suffix>cm</template>
              </ElInput>
            </div>
            <div class="extra-item">
              <span class="extra-label">大便次数</span>
              <ElInput v-model="extraRecord.stoolCount" placeholder="次数">
                <template #suffix>次</template>
              </ElInput>
            </div>
            <div class="extra-item">
              <span class="extra-label">身高</span>
              <ElInput v-model="extraRecord.height" placeholder="身高">
                <template #suffix>cm</template>
              </ElInput>
            </div>
            <div class="extra-item">
              <span class="extra-label">入量</span>
              <ElInput v-model="extraRecord.intake" placeholder="入量">
                <template #suffix>ml</template>
              </ElInput>
            </div>
            <div class="extra-item">
              <span class="extra-label">尿量</span>
              <ElInput v-model="extraRecord.urine" placeholder="尿量">
                <template #suffix>ml</template>
              </ElInput>
            </div>
            <div class="extra-item">
              <span class="extra-label">其他</span>
              <ElInput v-model="extraRecord.other" placeholder="其他">
                <template #suffix>ml</template>
              </ElInput>
            </div>
          </div>
        </section>
      </div>
    </div>
    <template #footer>
      <div class="fill-footer">
        <ElButton @click="visible = false">取消</ElButton>
        <ElButton :loading="savingAll" type="primary" @click="handleSaveAllSlots">保存</ElButton>
      </div>
    </template>
  </ElDrawer>
</template>

<style scoped>
.fill-drawer {
  display: flex;
  flex-direction: column;
  gap: 4px;
  height: 100%;
  min-height: 0;
}
.fill-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 2px;
  flex-shrink: 0;
}
.fill-toolbar-left,
.fill-toolbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.fill-record-date-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.fill-drawer-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  flex: 1;
  overflow: auto;
}
.fill-card {
  display: flex;
  flex-direction: column;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  overflow-x: auto;
  overflow-y: hidden;
}
.fill-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 32px;
  padding: 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  background: #f8fafc;
  border-bottom: 1px solid #e5e7eb;
}
.fill-grid-head,
.fill-row {
  display: grid;
  grid-template-columns: 120px repeat(6, 160px) minmax(520px, 1fr) 120px;
  gap: 8px;
  align-items: start;
}
.fill-grid-head {
  padding: 6px 8px;
  background: #eff6ff;
  font-size: 12px;
  color: #334155;
  text-align: center;
  align-items: center;
}
.fill-grid-body {
  flex: 1;
  overflow: auto;
  padding: 4px 8px 8px;
}
.fill-row {
  padding: 8px 0;
  border-bottom: 1px dashed #e5e7eb;
}
.fill-row:last-child {
  border-bottom: none;
}
.fill-row.is-active-slot {
  margin: 0 -4px;
  padding: 8px 4px;
  background: #eff6ff;
  border-radius: 6px;
  outline: 1px solid #93c5fd;
}
.fill-row-action {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: center;
  gap: 2px;
  padding-top: 4px;
}
.time-slot-cell {
  display: flex;
  align-items: center;
  justify-content: center;
}
.time-slot-cell :deep(.bottom-time-trigger) {
  width: 100%;
}
.fill-row .nav-cell :deep(.el-input),
.extra-grid :deep(.el-input) {
  width: 100%;
}
.fill-row .nav-cell :deep(.el-input__suffix),
.extra-grid :deep(.el-input__suffix) {
  color: #6b7280;
  font-size: 11px;
}
.fill-extra-card {
  flex-shrink: 0;
}
.extra-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 8px;
  padding: 8px;
  align-items: end;
}
.extra-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.extra-label {
  height: 18px;
  line-height: 18px;
  text-align: center;
  font-size: 12px;
  color: #4b5563;
  white-space: nowrap;
}
.extra-item :deep(.el-input__wrapper) {
  min-height: 26px;
}
.fill-footer {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}
</style>
