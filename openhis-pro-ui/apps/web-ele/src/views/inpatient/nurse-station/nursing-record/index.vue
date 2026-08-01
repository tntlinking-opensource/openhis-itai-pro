<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';

import type {
  NursingRecordPaperHeader,
  NursingRecordViewRow,
} from './components/nursing-record.types';

import { computed, onMounted, ref, watch } from 'vue';

import { useUserStore } from '@vben/stores';
import { ElMessage } from 'element-plus';

import {
  httpDeleteNurseStationNursingRecord,
  httpListNurseStationNursingRecords,
  httpLoadNurseStationNursingRecordInit,
  httpSaveNurseStationNursingRecordsBatch,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-nursing-record.http';
import { httpLoadNurseStationSkinTestInit } from '#/api/inpatient/nurse-station/inpatient-nurse-station-skin-test.http';
import {
  FORM_KEYBOARD_GRID_NAV_KEYS,
  useFormKeyboardNavigation,
} from '#/hooks/use-form-keyboard-navigation';
import { useRequest } from '#/hooks/use-request';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import {
  resolveDefaultPractitionerIdForSelect,
  resolveLoginPractitionerLabelFromUserInfo,
} from '#/utils/inventory/inventory-workbench-list';

import NursingRecordPaperTable from '#/views/inpatient/nurse-station/nursing-record/components/nursing-record-paper-table.vue';

import { loadPrintPendingRows } from '../print-center/load-print-pending';
import { printInpatientPrintCenter } from '../print-center/print-center-print';
import { resolvePaperTableFocusIndex } from './services/nursing-record-paper-keyboard';
import {
  applyBatchSavedRowsToViewRows,
  buildWeekSkeletonRows,
  countInclusiveDays,
  createEmptyNursingRecordRow,
  getNowDateTime,
  mapApiPaperHeaderToView,
  mapViewRowToBatchSaveItem,
  mergePaperNursingRecordRows,
  normalizeNursingRecordNurseSignatures,
  resolveCurrentWeekRange,
} from './services/map-nurse-station-nursing-record';

interface Props {
  patients?: NurseStationModulePatient[];
}

interface NurseSignatureOption {
  label: string;
  value: string;
}

defineOptions({
  name: 'InpatientNurseStationNursingRecordModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

const userStore = useUserStore();

const recordList = ref<NursingRecordViewRow[]>([]);
const paperHeaderInfo = ref<NursingRecordPaperHeader | null>(null);
const nurseOptions = ref<NurseSignatureOption[]>([]);
const consciousnessOptions = ref<NurseSignatureOption[]>([]);
const formAreaRef = ref<HTMLElement | null>(null);
const savingAll = ref(false);
const deletingRowId = ref('');
const pageInitialized = ref(false);

const defaultWeekRange = resolveCurrentWeekRange();
/** 查询区间（默认本周，可改任意起止日） */
const queryDateRange = ref<[string, string]>([defaultWeekRange.from, defaultWeekRange.to]);
/** 护理记录打印中 */
const printingRecord = ref(false);

const listRequest = useRequest(httpListNurseStationNursingRecords, { manual: true });
const nurseInitRequest = useRequest(httpLoadNurseStationSkinTestInit, { manual: true });
const nursingRecordInitRequest = useRequest(httpLoadNurseStationNursingRecordInit, {
  manual: true,
});
const { handleFormKeydown } = useFormKeyboardNavigation(formAreaRef, {
  keys: FORM_KEYBOARD_GRID_NAV_KEYS,
  navigationMode: 'grid',
  gridTolerance: { row: 32, col: 48 },
  resolveFocusIndex: resolvePaperTableFocusIndex,
});

/** 护理记录仅支持左侧单选患者 */
const canOperateRecord = computed(() => props.patients.length === 1);

/** 当前登录人姓名（仅取 label，兜底展示） */
const defaultNurseSignatureLabel = computed(() =>
  resolveLoginPractitionerLabelFromUserInfo(
    userStore.userInfo as null | Record<string, unknown> | undefined,
  ),
);

/** 护士签名下拉默认 value（医务人员 id） */
const defaultNurseSignatureValue = computed(() => {
  const fromOptions = resolveDefaultPractitionerIdForSelect(
    nurseOptions.value,
    userStore.userInfo as null | Record<string, unknown> | undefined,
  );
  if (fromOptions) {
    return fromOptions;
  }
  return (
    resolveLoginPractitionerIdForRequest(
      userStore.userInfo as null | Record<string, unknown> | undefined,
    ) || ''
  );
});

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

/**
 * 纸质单展示顺序与 recordList 一致，编辑过程中不做实时重排。
 * 否则 focus 补默认时间/签名、填写病情观察后会触发排序，行内容看起来「串到下一行」。
 */
const paperRecords = computed(() => recordList.value);

/** 当前查询区间闭区间天数（骨架行数量依据） */
const queryDayCount = computed(() => {
  const [from, to] = queryDateRange.value;
  return countInclusiveDays(from, to);
});

/** 纸质单未保存条数 */
const paperUnsavedCount = computed(() => recordList.value.filter((row) => row.isDirty).length);

/** 加载护士签名与意识下拉 */
async function loadNurseSignatureOptions() {
  const [initRes, nursingInitRes] = await Promise.all([
    nurseInitRequest.run(),
    nursingRecordInitRequest.run(),
  ]);
  // 下拉 value 用医务人员 id，label 用姓名；保存时传 recorderId
  const mapped = (initRes.nurseOptions ?? [])
    .map((opt) => {
      const value = preserveApiIdForRequest(opt.value);
      const label = (opt.label ?? '').trim() || value || '';
      return value ? { label, value } : null;
    })
    .filter((opt): opt is NurseSignatureOption => opt !== null);

  const loginId = resolveLoginPractitionerIdForRequest(
    userStore.userInfo as null | Record<string, unknown> | undefined,
  );
  const loginLabel = defaultNurseSignatureLabel.value;
  if (loginId && !mapped.some((opt) => opt.value === loginId)) {
    mapped.unshift({ label: loginLabel || loginId, value: loginId });
  }
  nurseOptions.value = mapped;

  // 意识：纸质单落库存展示文案（与吸氧方式等自由文本列同构）
  consciousnessOptions.value = (nursingInitRes.consciousnessOptions ?? [])
    .map((opt) => {
      const label = `${opt.label ?? opt.value ?? ''}`.trim();
      return label ? { label, value: label } : null;
    })
    .filter((opt): opt is NurseSignatureOption => opt !== null);
}

/** 加载查询区间内护理记录并与骨架行合并 */
async function loadNursingRecords() {
  const context = selectedPatientContext.value;
  if (!context) {
    recordList.value = [];
    paperHeaderInfo.value = null;
    return;
  }

  if (!pageInitialized.value) {
    await loadNurseSignatureOptions();
    pageInitialized.value = true;
  }

  const [recordedAtFrom, recordedAtTo] = queryDateRange.value;
  const response = await listRequest.run({
    encounterId: context.encounterId,
    patientId: context.patientId,
    recordedAtFrom,
    recordedAtTo,
    sortAsc: true,
  });

  const skeletonRows = buildWeekSkeletonRows(queryDayCount.value);
  recordList.value = mergePaperNursingRecordRows(skeletonRows, response.rows ?? []);
  // 回显姓名对齐为下拉医务人员 id，便于签名可改选并回写
  normalizeNursingRecordNurseSignatures(recordList.value, nurseOptions.value);
  paperHeaderInfo.value = mapApiPaperHeaderToView(response.paperHeader);
}

/** 查询区间变更后重新加载 */
function handleQueryDateRangeChange(value: [string, string] | null) {
  if (!value?.[0] || !value[1]) {
    const weekRange = resolveCurrentWeekRange();
    queryDateRange.value = [weekRange.from, weekRange.to];
  }
  void loadNursingRecords();
}

/** 快捷切回本周并查询 */
function handleQueryCurrentWeek() {
  const weekRange = resolveCurrentWeekRange();
  queryDateRange.value = [weekRange.from, weekRange.to];
  void loadNursingRecords();
}

/** 纸质单新增额外时点行：带入当前时间与登录人签名 */
function handlePaperAddRow() {
  const now = getNowDateTime();
  const nurseSignature = defaultNurseSignatureValue.value || nurseOptions.value[0]?.value || '';
  recordList.value.push({
    ...createEmptyNursingRecordRow({
      recordDate: now.date,
      recordTime: now.time,
      nurseSignature,
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
    normalizeNursingRecordNurseSignatures(recordList.value, nurseOptions.value);
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
    recordList.value = buildWeekSkeletonRows(queryDayCount.value);
  }
  ElMessage.success('已删除记录');
}

/** 纸质单字段变更时标记未保存 */
function handlePaperMarkDirty(id: string) {
  const target = recordList.value.find((item) => item.id === id);
  if (!target) {
    return;
  }
  target.isDirty = true;
}

/** 刷新：按当前查询区间重新拉取护理记录 */
async function handlePaperRefresh() {
  await loadNursingRecords();
}

/** 按当前查询区间打印护理记录单 */
async function handlePrintNursingRecord() {
  if (!canOperateRecord.value || props.patients.length !== 1) {
    ElMessage.warning('请先从左侧选择一位患者');
    return;
  }
  if (printingRecord.value) {
    return;
  }
  const [dateFrom, dateTo] = queryDateRange.value;
  if (!dateFrom || !dateTo) {
    ElMessage.warning('请选择有效的查询日期');
    return;
  }
  printingRecord.value = true;
  try {
    const selectedRows = await loadPrintPendingRows({
      docType: 'nursing_record',
      patients: props.patients,
      dateFrom,
      dateTo,
      executionDeadline: `${dateTo} 23:59:59`,
    });
    if (selectedRows.length <= 0) {
      ElMessage.warning('暂无「护理记录」可打印数据');
      return;
    }
    const result = await printInpatientPrintCenter({
      docType: 'nursing_record',
      patients: props.patients,
      selectedRows,
      dateFrom,
      dateTo,
      executionDeadline: `${dateTo} 23:59:59`,
    });
    if (result.ok) {
      ElMessage.success(`已发送打印：护理记录，共 ${result.count} 份`);
    }
  } finally {
    printingRecord.value = false;
  }
}

/** 在护理记录编辑区统一处理键盘导航 */
function handleRecordAreaKeydown(event: KeyboardEvent) {
  handleFormKeydown(event);
}

onMounted(() => {
  void loadNursingRecords();
});

/** 仅在就诊切换时重载，避免患者卡片其它字段刷新导致未保存内容被冲掉 */
watch(
  () => selectedPatientContext.value?.encounterId,
  () => {
    void loadNursingRecords();
  },
);
</script>

<template>
  <div class="nursing-record-page">
    <ElEmpty v-if="!canOperateRecord" description="请先从左侧选择一位患者" />

    <div
      v-else
      ref="formAreaRef"
      v-loading="
        listRequest.loading.value ||
        nurseInitRequest.loading.value ||
        nursingRecordInitRequest.loading.value
      "
      class="record-content"
      @keydown.capture="handleRecordAreaKeydown"
    >
      <NursingRecordPaperTable
        :paper-header="paperHeaderInfo"
        :rows="paperRecords"
        :nurse-options="nurseOptions"
        :consciousness-options="consciousnessOptions"
        :default-nurse-signature-label="defaultNurseSignatureValue"
        :saving-all="savingAll"
        :unsaved-count="paperUnsavedCount"
        @add-row="handlePaperAddRow"
        @delete-row="handlePaperDeleteRow"
        @mark-dirty="handlePaperMarkDirty"
        @refresh="handlePaperRefresh"
        @save-all="handlePaperSaveAll"
      >
        <template #toolbar-main>
          <ElDatePicker
            v-model="queryDateRange"
            type="daterange"
            size="small"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clearable="false"
            class="nursing-record-date-range"
            @change="handleQueryDateRangeChange"
          />
          <ElButton size="small" plain @click="handleQueryCurrentWeek">本周</ElButton>
        </template>
        <template #toolbar-extra>
          <ElButton size="small" plain :loading="printingRecord" @click="handlePrintNursingRecord">
            打印
          </ElButton>
        </template>
      </NursingRecordPaperTable>
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

.nursing-record-date-range {
  width: 260px;
}
</style>
