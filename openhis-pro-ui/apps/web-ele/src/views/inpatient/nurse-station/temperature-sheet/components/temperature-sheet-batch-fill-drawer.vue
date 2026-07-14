<script setup lang="ts">
import type { NurseStationModulePatient } from '../../components/nurse-station-module-patient.types';
import type { PatientItem } from '../../types';
import type { TemperatureSheetBatchFillRowDraft } from '../services/temperature-sheet-batch-fill';

import type { TemperatureSheetTimeSlot } from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.types';
import type { OptionDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station.types';

import { computed, inject, nextTick, ref } from 'vue';

import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';

import { httpLoadNurseStationTemperatureSheetInit } from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.http';
import { httpSaveNurseStationTemperatureSheetAtRecordedTime } from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.http';
import { useFormKeyboardNavigation } from '#/hooks/use-form-keyboard-navigation';
import { useRequest } from '#/hooks/use-request';
import { nurseStationPatientListPanelContextKey } from '../../components/patient-list-panel.context';
import TemperatureSheetFillSlotSpecial from './temperature-sheet-fill-slot-special.vue';
import TemperatureSheetSingleFillDrawer from './temperature-sheet-single-fill-drawer.vue';
import {
  collectBatchTemperatureSheetSaveRequests,
  hasBatchFillRowSaveContent,
  isBatchFillRowPersisted,
  loadTemperatureSheetBatchFillRows,
  saveTemperatureSheetBatchFillRows,
  TEMPERATURE_SHEET_BATCH_SAVE_LIMIT,
} from '../services/temperature-sheet-batch-fill';
import {
  isBloodPressureInputDisabled,
  TEMPERATURE_SHEET_TIME_SLOTS,
} from '../services/map-nurse-station-temperature-sheet';
import {
  buildSpecialEventLabelMap,
  buildSpecialEventSelectOptions,
  type TemperatureSheetSpecialEventOption,
  validateFillRowsSpecialEvents,
} from '../services/temperature-sheet-special-events';
import {
  formatTemperatureSheetTimeSlotRange,
  resolveTemperatureSheetTimeSlotFromHour,
} from '../services/temperature-sheet-time-format';
import '../styles/temperature-sheet-fill-drawer.css';

defineOptions({
  name: 'TemperatureSheetBatchFillDrawer',
});

const visible = defineModel<boolean>('visible', { default: false });

const emit = defineEmits<{
  saved: [];
}>();

const listPanelContext = inject(nurseStationPatientListPanelContextKey, null);
const initRequest = useRequest(httpLoadNurseStationTemperatureSheetInit, { manual: true });

const recordDate = ref('');
const activeTimeSlot = ref<TemperatureSheetTimeSlot>(
  resolveTemperatureSheetTimeSlotFromHour(dayjs().hour()),
);
const batchRows = ref<TemperatureSheetBatchFillRowDraft[]>([]);
const loading = ref(false);
const saving = ref(false);
const loadProgress = ref({ done: 0, total: 0 });
const batchFormAreaRef = ref<HTMLElement | null>(null);
const singleFillDrawerRef = ref<InstanceType<typeof TemperatureSheetSingleFillDrawer> | null>(null);
const specialEventOptions = ref<TemperatureSheetSpecialEventOption[]>([]);

const timeSlotOptions = TEMPERATURE_SHEET_TIME_SLOTS.map((slot) => ({
  label: `${slot}:00（${formatTemperatureSheetTimeSlotRange(slot)}）`,
  value: slot,
}));

const specialEventSelectOptions = computed(() =>
  buildSpecialEventSelectOptions(specialEventOptions.value),
);
const specialEventLabelMap = computed(() => buildSpecialEventLabelMap(specialEventOptions.value));

/** 将 init 接口 OptionDto 转为特殊事件下拉项 */
function mapSpecialEventOptionsFromInit(options: OptionDto[] | null | undefined) {
  return (options ?? [])
    .map((option) => ({
      label: (option.label ?? '').trim(),
      value: String(option.value ?? '').trim(),
    }))
    .filter((option) => option.value.length > 0);
}

/** 将左侧患者队列项映射为体温单模块入参 */
function mapPatientItemToModulePatient(item: PatientItem): NurseStationModulePatient {
  const regionName = listPanelContext?.regionNameByIdRecord.value[item.regionId] ?? '-';
  const wardName = listPanelContext?.wardNameByIdRecord.value[item.wardId] ?? '-';
  return {
    id: item.id,
    patientId: item.patientId,
    encounterBusNo: item.encounterBusNo,
    admissionAt: item.startTime,
    dischargeAt: item.endTime,
    bedNo: item.bedNo,
    name: item.name,
    gender: item.gender,
    age: item.age,
    nurseLevel: item.level,
    feeType: item.feeType,
    diagnosis: item.diagnosis,
    regionName,
    wardName,
    pendingAudit: item.pendingAudit,
    pendingExecute: item.pendingExecute,
    accountId: item.accountId,
    accountBalance: item.accountBalance,
    accountBalanceDisplayMode: item.accountBalanceDisplayMode,
    accountBalanceSummaryText: item.accountBalanceSummaryText,
    accountBalanceSubText: item.accountBalanceSubText,
    availableBalance: item.availableBalance,
    pendingSettlementTotal: item.pendingSettlementTotal,
    lowBalanceWarning: item.lowBalanceWarning,
    riskTags: item.riskText ? [item.riskText] : [],
  };
}

/** 左侧筛选列表中的在院患者（批量录入数据源） */
const wardPatients = computed<NurseStationModulePatient[]>(() => {
  const rows = listPanelContext?.filteredPatients.value ?? [];
  return rows.map((item) => mapPatientItemToModulePatient(item));
});

const patientCountText = computed(() => `${wardPatients.value.length} 位患者`);
const loadProgressText = computed(() => {
  if (!loading.value || loadProgress.value.total === 0) {
    return '';
  }
  return `加载中 ${loadProgress.value.done}/${loadProgress.value.total}`;
});
const activeTimeSlotRangeText = computed(() =>
  formatTemperatureSheetTimeSlotRange(activeTimeSlot.value),
);
const filledRowCount = computed(
  () => batchRows.value.filter((row) => hasBatchFillRowSaveContent(row)).length,
);

const { handleFormKeydown: handleBatchFormKeydown } = useFormKeyboardNavigation(batchFormAreaRef, {
  keys: ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Enter'],
  resolveFocusIndex: ({ currentIndex, direction, event, focusable }) => {
    const currentTarget = focusable[currentIndex];
    const currentCell = currentTarget?.closest<HTMLElement>('.nav-cell');
    if ((event.key === 'ArrowUp' || event.key === 'ArrowDown') && currentCell) {
      const { navCol, navRow } = currentCell.dataset;
      if (navCol !== undefined && navRow !== undefined) {
        const nextRow = Number(navRow) + (event.key === 'ArrowUp' ? -1 : 1);
        const nextCell = batchFormAreaRef.value?.querySelector<HTMLElement>(
          `.nav-cell[data-nav-col="${navCol}"][data-nav-row="${nextRow}"]`,
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

/** 初始化录入日期为今天 */
function initRecordDateIfEmpty(): void {
  if (!recordDate.value.trim()) {
    recordDate.value = dayjs().format('YYYY-MM-DD');
  }
}

/** 加载特殊事件字典（首次打开时） */
async function ensureSpecialEventOptionsLoaded(): Promise<void> {
  if (specialEventOptions.value.length > 0) {
    return;
  }
  const initRes = await initRequest.run();
  specialEventOptions.value = mapSpecialEventOptionsFromInit(initRes.specialEventOptions);
}

/** 加载批量回显数据 */
async function loadBatchRows(): Promise<void> {
  initRecordDateIfEmpty();
  if (!recordDate.value.trim()) {
    batchRows.value = [];
    return;
  }
  if (wardPatients.value.length === 0) {
    batchRows.value = [];
    ElMessage.warning('左侧患者列表为空，请先筛选病区患者');
    return;
  }

  loading.value = true;
  loadProgress.value = { done: 0, total: wardPatients.value.length };
  try {
    batchRows.value = await loadTemperatureSheetBatchFillRows(
      wardPatients.value,
      recordDate.value,
      activeTimeSlot.value,
      (progress) => {
        loadProgress.value = progress;
      },
    );
  } finally {
    loading.value = false;
  }
}

/** 预加载数据后再打开抽屉，避免滑入后内容撑高导致二次上弹 */
async function prepareOpen(): Promise<void> {
  initRecordDateIfEmpty();
  activeTimeSlot.value = resolveTemperatureSheetTimeSlotFromHour(dayjs().hour());
  await ensureSpecialEventOptionsLoaded();
  await loadBatchRows();
}

/** 打开后聚焦首个体温输入框 */
async function focusFirstInput(): Promise<void> {
  await nextTick();
  const firstInput = batchFormAreaRef.value?.querySelector<HTMLInputElement>(
    '.batch-row .el-input input',
  );
  firstInput?.focus();
}

/** 录入日期加减天数并重新加载 */
function shiftRecordDateByDays(offset: number): void {
  const base = recordDate.value?.trim() ? dayjs(recordDate.value) : dayjs();
  if (!base.isValid()) {
    return;
  }
  recordDate.value = base.add(offset, 'day').format('YYYY-MM-DD');
  if (visible.value) {
    void loadBatchRows();
  }
}

/** 判断该行血压输入是否禁用 */
function isRowBloodPressureDisabled(row: TemperatureSheetBatchFillRowDraft): boolean {
  return isBloodPressureInputDisabled(row.slotRow, row.dayRows);
}

/** 保存前校验各行特殊事件 */
function validateBatchSpecialEvents(): string | null {
  for (const row of batchRows.value) {
    const error = validateFillRowsSpecialEvents([row.slotRow], specialEventLabelMap.value);
    if (error) {
      return `${row.bedNo} ${row.name}：${error}`;
    }
  }
  return null;
}

/** 打开该患者的单人全时点录入抽屉 */
async function openSingleFillForRow(row: TemperatureSheetBatchFillRowDraft): Promise<void> {
  const drawer = singleFillDrawerRef.value;
  if (!drawer) {
    return;
  }
  await drawer.open({
    patient: {
      encounterId: row.encounterId,
      patientId: row.patientId,
      bedNo: row.bedNo,
      name: row.name,
      gender: row.gender,
    },
    recordDate: recordDate.value,
    activeTimeSlot: activeTimeSlot.value,
  });
}

/** 单人录入保存后刷新批量列表对应行 */
async function handleSingleFillSaved(): Promise<void> {
  if (!visible.value || !recordDate.value.trim()) {
    return;
  }
  await loadBatchRows();
  emit('saved');
}

/** 批量保存已填写行 */
async function handleSaveBatch(): Promise<void> {
  if (!recordDate.value.trim()) {
    ElMessage.warning('请选择录入日期');
    return;
  }

  const specialEventError = validateBatchSpecialEvents();
  if (specialEventError) {
    ElMessage.warning(specialEventError);
    return;
  }

  const saveRequests = collectBatchTemperatureSheetSaveRequests(batchRows.value, recordDate.value);
  if (saveRequests.length === 0) {
    ElMessage.warning('请至少为一位患者填写生命体征');
    return;
  }
  if (saveRequests.length > TEMPERATURE_SHEET_BATCH_SAVE_LIMIT) {
    ElMessage.warning(`单次最多保存 ${TEMPERATURE_SHEET_BATCH_SAVE_LIMIT} 条，请分批提交`);
    return;
  }

  const rowByEncounterId = new Map(batchRows.value.map((row) => [row.encounterId, row] as const));

  saving.value = true;
  try {
    const result = await saveTemperatureSheetBatchFillRows(
      saveRequests,
      rowByEncounterId,
      httpSaveNurseStationTemperatureSheetAtRecordedTime,
    );
    if (result.savedCount > 0) {
      ElMessage.success(`已保存 ${result.savedCount} 位患者的体温记录`);
      emit('saved');
    }
    if (result.failedPatients.length > 0) {
      const preview = result.failedPatients
        .slice(0, 3)
        .map((item) => `${item.bedNo}床 ${item.name}`)
        .join('、');
      const suffix =
        result.failedPatients.length > 3 ? ` 等 ${result.failedPatients.length} 人` : '';
      ElMessage.warning(`部分保存失败：${preview}${suffix}`);
    }
  } finally {
    saving.value = false;
  }
}

defineExpose({
  prepareOpen,
  focusFirstInput,
});
</script>

<template>
  <ElDrawer
    v-model="visible"
    :append-to-body="true"
    :destroy-on-close="false"
    :with-header="false"
    class="temp-batch-fill-drawer"
    direction="btt"
    size="72%"
  >
    <div ref="batchFormAreaRef" class="batch-fill-drawer" @keydown="handleBatchFormKeydown">
      <div class="batch-toolbar">
        <div class="batch-toolbar-left">
          <div class="batch-record-date-nav">
            <ElDatePicker
              v-model="recordDate"
              format="YYYY-MM-DD"
              placeholder="录入日期"
              type="date"
              value-format="YYYY-MM-DD"
              @change="loadBatchRows"
            />
            <ElSelect v-model="activeTimeSlot" style="width: 220px" @change="loadBatchRows">
              <ElOption
                v-for="option in timeSlotOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </ElSelect>
            <ElButton plain size="small" @click="shiftRecordDateByDays(-1)">上一天</ElButton>
            <ElButton plain size="small" @click="shiftRecordDateByDays(1)">下一天</ElButton>
          </div>
          <ElTag type="info">{{ patientCountText }} · {{ activeTimeSlotRangeText }}</ElTag>
          <ElTag v-if="loadProgressText" type="warning">{{ loadProgressText }}</ElTag>
        </div>
        <div class="batch-toolbar-right">
          <ElTag type="success">待保存 {{ filledRowCount }} 条</ElTag>
          <ElTag size="small" type="info">姓名可点开单人录入</ElTag>
          <ElTag type="info">键盘：←/→ 切换，↑/↓ 同列下一位患者</ElTag>
        </div>
      </div>

      <section class="batch-card">
        <header class="batch-card-title">
          生命体征（按时点批量录入）
          <ElTag size="small" type="info"
            >字段与单人录入一致；快捷项：出院/物理降温/药物降温/体温不升</ElTag
          >
        </header>

        <div v-loading="loading" class="batch-card-body">
          <div class="batch-grid-head">
            <span>床号</span>
            <span>姓名</span>
            <span>体温(℃)</span>
            <span>脉搏</span>
            <span>呼吸</span>
            <span>心率</span>
            <span>收缩压</span>
            <span>舒张压</span>
            <span>特殊</span>
          </div>

          <div class="batch-grid-scroll">
            <ElEmpty v-if="!loading && batchRows.length === 0" description="暂无患者可录入" />

            <div
              v-for="(row, index) in batchRows"
              :key="row.encounterId"
              class="batch-row"
              :class="{
                'is-persisted': isBatchFillRowPersisted(row),
                'is-load-error': Boolean(row.loadError),
              }"
            >
              <button
                class="batch-bed-cell batch-patient-link"
                title="打开单人录入"
                type="button"
                @click="openSingleFillForRow(row)"
              >
                {{ row.bedNo }}
              </button>
              <button
                class="batch-name-cell batch-patient-link"
                title="打开单人录入"
                type="button"
                @click="openSingleFillForRow(row)"
              >
                <span class="batch-name">{{ row.name }}</span>
                <span v-if="row.gender" class="batch-gender">{{ row.gender }}</span>
                <ElTag
                  v-if="row.loadError"
                  class="batch-load-error-tag"
                  size="small"
                  type="danger"
                  >{{ row.loadError }}</ElTag
                >
              </button>
              <div class="nav-cell" :data-nav-col="0" :data-nav-row="index">
                <ElInput v-model="row.slotRow.temp" placeholder="体温">
                  <template #suffix>℃</template>
                </ElInput>
              </div>
              <div class="nav-cell" :data-nav-col="1" :data-nav-row="index">
                <ElInput v-model="row.slotRow.pulse" placeholder="脉搏">
                  <template #suffix>次/分</template>
                </ElInput>
              </div>
              <div class="nav-cell" :data-nav-col="2" :data-nav-row="index">
                <ElInput v-model="row.slotRow.respiratory" placeholder="呼吸">
                  <template #suffix>次/分</template>
                </ElInput>
              </div>
              <div class="nav-cell" :data-nav-col="3" :data-nav-row="index">
                <ElInput v-model="row.slotRow.heartRate" placeholder="心率">
                  <template #suffix>bpm</template>
                </ElInput>
              </div>
              <div class="nav-cell" :data-nav-col="4" :data-nav-row="index">
                <ElInput
                  v-model="row.slotRow.systolic"
                  :disabled="isRowBloodPressureDisabled(row)"
                  placeholder="收缩压"
                >
                  <template #suffix>mmHg</template>
                </ElInput>
              </div>
              <div class="nav-cell" :data-nav-col="5" :data-nav-row="index">
                <ElInput
                  v-model="row.slotRow.diastolic"
                  :disabled="isRowBloodPressureDisabled(row)"
                  placeholder="舒张压"
                >
                  <template #suffix>mmHg</template>
                </ElInput>
              </div>
              <TemperatureSheetFillSlotSpecial
                :row="row.slotRow"
                :special-event-select-options="specialEventSelectOptions"
              />
            </div>
          </div>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="batch-footer">
        <ElButton @click="visible = false">取消</ElButton>
        <ElButton :loading="saving" type="primary" @click="handleSaveBatch">保存</ElButton>
      </div>
    </template>
  </ElDrawer>

  <TemperatureSheetSingleFillDrawer ref="singleFillDrawerRef" @saved="handleSingleFillSaved" />
</template>

<style scoped>
.batch-fill-drawer {
  display: flex;
  flex-direction: column;
  gap: 4px;
  height: 100%;
  min-height: 0;
}
.batch-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 2px;
  flex-shrink: 0;
}
.batch-toolbar-left,
.batch-toolbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.batch-record-date-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.batch-card {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}
.batch-card-title {
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
  flex-shrink: 0;
}
.batch-card-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
.batch-grid-head,
.batch-row {
  display: grid;
  grid-template-columns: 88px 88px repeat(6, 160px) minmax(0, 1fr);
  gap: 8px;
  align-items: start;
}
.batch-grid-head {
  padding: 6px 8px;
  background: #eff6ff;
  font-size: 12px;
  color: #334155;
  text-align: center;
  align-items: center;
  flex-shrink: 0;
}
.batch-grid-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 4px 8px 8px;
}
.batch-row {
  padding: 8px 0;
  border-bottom: 1px dashed #e5e7eb;
}
.batch-row:last-child {
  border-bottom: none;
}
.batch-row.is-persisted {
  margin: 0 -4px;
  padding: 8px 4px;
  background: #f0fdf4;
  border-radius: 6px;
}
.batch-row.is-load-error {
  background: #fef2f2;
}
.batch-patient-link {
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  font: inherit;
  cursor: pointer;
  text-align: inherit;
}
.batch-bed-cell,
.batch-name-cell {
  align-self: center;
}
.batch-bed-cell {
  text-align: center;
  font-weight: 700;
  font-size: 13px;
  color: #1f2937;
}
.batch-bed-cell:hover,
.batch-name-cell:hover .batch-name {
  color: var(--el-color-primary);
}
.batch-name-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  min-width: 0;
  line-height: 1.3;
  text-align: left;
}
.batch-name {
  font-weight: 700;
  font-size: 13px;
  color: var(--el-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.batch-name-cell:hover .batch-name {
  text-decoration: underline;
  text-underline-offset: 2px;
}
.batch-gender {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 400;
  color: #334155;
}
.batch-load-error-tag {
  flex-shrink: 0;
}
.batch-row .nav-cell :deep(.el-input) {
  width: 100%;
}
.batch-row .nav-cell :deep(.el-input__suffix) {
  color: #6b7280;
  font-size: 11px;
}
.batch-footer {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}
</style>
