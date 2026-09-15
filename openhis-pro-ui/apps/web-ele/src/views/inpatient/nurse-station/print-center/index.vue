<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type { PrintDocType, PrintPendingRow } from './print-center.types';

import type { TableColumn } from '@vben-core/table-layout-ui';

import { computed, nextTick, onMounted, onUnmounted, reactive, ref, unref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';

import InpatientPatientInfoHeader from '#/components/inpatient/patient-info-header.vue';
import RegisterStyleTableLayout from '#/components/layouts/register-style-table-layout.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import { PRINT_CENTER_HIDDEN_DOC_TYPES } from '#/views/inpatient/nurse-station/print-center/print-center-doc.config';

import {
  buildNurseStationStickyPatientInfoFromModulePatient,
  buildNurseStationStickyPatientInfoPlaceholder,
} from '../components/build-nurse-station-sticky-patient-info';
import {
  getPrintDocColumns,
  isDayQueryDocType,
  isPrintDocVisibleInCenter,
  listAllVisiblePrintDocs,
  needsFrequencySplitSwitch,
  needsOralMedInfusionFilter,
  resolveDefaultPrintDocType,
  resolvePrintEmptyDescription,
  resolveScopeGuide,
} from './doc-registry';
import { loadPrintPendingRows } from './load-print-pending';
import { PRINT_CENTER_WIRED_DOC_TYPES, printInpatientPrintCenter } from './print-center-print';
import { findPrintDocType, resolvePrintScopeLabel } from './print-center.types';
import { buildDefaultDateRangeShortcuts } from '#/utils/format/date-picker-shortcuts';
import { resolveInfusionTimesPerDaySync } from '#/utils/print/mappers/outpatient-infusion-label';

import './print-center.css';

/** 院点可配置隐藏的单据 code（须 #/ 导入以走 override） */
const hiddenPrintDocTypeSet = new Set<PrintDocType>(PRINT_CENTER_HIDDEN_DOC_TYPES);

/** 默认查询区间：当天～当天 */
function buildDefaultDayRange(): [string, string] {
  const today = dayjs().format('YYYY-MM-DD');
  return [today, today];
}

interface Props {
  patients?: NurseStationModulePatient[];
  /** 范围不符时：左侧只保留当前已选第一人 */
  onKeepFirstPatient?: () => void;
  /** 清空左侧患者勾选 */
  onClearPatients?: () => void;
}

defineOptions({
  name: 'InpatientNurseStationPrintCenterModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
  onKeepFirstPatient: undefined,
  onClearPatients: undefined,
});

/** 主类型：T1 复杂工作站（打印中心壳）；附加：T3 待打列表勾选 */

const defaultDocType = resolveDefaultPrintDocType(hiddenPrintDocTypeSet);
const activeDocType = ref<PrintDocType>(defaultDocType);
/** 有时间条件的单据：日期范围，默认当天，配合上一天/下一天整段平移 */
const dayQueryRange = ref<[string, string]>(buildDefaultDayRange());
const dateRangeShortcuts = buildDefaultDateRangeShortcuts();
/** 患者用药单筛选：all 全部 / infusion 输液药 / non_infusion 非输液药 */
const oralMedInfusionFilter = ref<'all' | 'infusion' | 'non_infusion'>('all');
/** 巡视卡：按频次打印（默认开；关则每患者一张） */
const patrolSplitByFrequency = ref(true);
const copies = ref(1);
const selectedRowIds = ref<string[]>([]);
/** 挂号皮肤表：程序化勾选时忽略 selection-change，避免 clear 冲掉已选 */
let syncingSelection = false;
const pendingTableRef = ref<{
  clearSelection?: () => void | Promise<void>;
  gridApi?: unknown;
} | null>(null);
const printing = ref(false);
const loadingPending = ref(false);
const pendingRows = ref<PrintPendingRow[]>([]);
/** 待打列表加载序号：快速切页签时丢弃过期响应 */
let pendingLoadSeq = 0;
/** RegisterStyleTableLayout 必填分页占位（本页不分页） */
const pendingQueryParams = reactive({ pageNo: 1, pageSize: 500 });
/** 打印前确认抽屉（清单确认，不改打印装载语义） */
const confirmVisible = ref(false);

/** 当前单据是否已对接真实打印模板 */
const isDocWired = computed(() => PRINT_CENTER_WIRED_DOC_TYPES.includes(activeDocType.value));

/** 平铺可见单据（体温单/院点隐藏项已过滤；不展示 count 角标） */
const docTypeOptions = computed(() => listAllVisiblePrintDocs(hiddenPrintDocTypeSet));

/** 当前单据配置 */
const activeDoc = computed(() => findPrintDocType(activeDocType.value));

/** 面板标题：当前单据名 */
const panelTitle = computed(() => activeDoc.value?.label ?? '待打清单');

/** 表列（按单据语义） */
const tableColumns = computed(() => getPrintDocColumns(activeDocType.value));

/** 输液贴 / 巡视卡内容列折行（覆盖挂号表 nowrap） */
const WRAP_TITLE_DOC_TYPES = new Set<PrintDocType>(['infusion_label', 'patrol_card']);

/** 挂号皮肤列：勾选列 + 单据语义列 */
const registerTableColumns = computed<TableColumn[]>(() => {
  const wrapTitle = WRAP_TITLE_DOC_TYPES.has(activeDocType.value);
  // 内容列走插槽折行，其余列保持溢出提示
  return [
    { type: 'selection', width: 48, align: 'center', headerAlign: 'center' },
    ...tableColumns.value.map((col) => ({
      prop: col.key,
      label: col.label,
      width: col.width,
      minWidth: col.minWidth,
      align: col.key === 'title' && wrapTitle ? 'left' : (col.align ?? 'left'),
      headerAlign: col.align === 'left' || (col.key === 'title' && wrapTitle) ? 'left' : 'center',
      showOverflowTooltip: !(col.key === 'title' && wrapTitle),
      ...(col.key === 'statusText' ? { slot: 'statusText' } : {}),
      ...(col.key === 'printStatusText' ? { slot: 'printStatusText' } : {}),
      ...(col.key === 'title' && wrapTitle ? { slot: 'titleText' } : {}),
    })),
  ];
});

/** 解析 RegisterStyle / TableLayout 透出的 vxe grid 实例 */
function resolvePendingGrid(): null | {
  clearCheckboxRow?: () => void;
  setCheckboxRow?: (row: PrintPendingRow, checked: boolean) => void;
} {
  const exposed = pendingTableRef.value;
  if (!exposed) {
    return null;
  }
  const gridApi = unref(exposed.gridApi as { value?: unknown } | unknown);
  return unref(gridApi) as typeof gridApi as null | {
    clearCheckboxRow?: () => void;
    setCheckboxRow?: (row: PrintPendingRow, checked: boolean) => void;
  };
}

/** 查询日起止（非法时回退当天） */
const queryDateFrom = computed(() => {
  const from = dayQueryRange.value?.[0];
  return from && /^\d{4}-\d{2}-\d{2}$/.test(from) ? from : dayjs().format('YYYY-MM-DD');
});
const queryDateTo = computed(() => {
  const to = dayQueryRange.value?.[1];
  return to && /^\d{4}-\d{2}-\d{2}$/.test(to) ? to : queryDateFrom.value;
});

/** 空态/提示用区间文案 */
const dateRangeLabel = computed(() =>
  queryDateFrom.value === queryDateTo.value
    ? queryDateFrom.value
    : `${queryDateFrom.value} ~ ${queryDateTo.value}`,
);

/** 医嘱截止时间：有日期条件锚定区间末日，否则今天末 */
const executionDeadline = computed(() => {
  if (isDayQueryDocType(activeDocType.value)) {
    return `${queryDateTo.value} 23:59:59`;
  }
  return `${dayjs().format('YYYY-MM-DD')} 23:59:59`;
});

/** 患者选择是否满足当前单据范围 */
const patientScopeOk = computed(() => {
  const scope = activeDoc.value?.scope;
  const count = props.patients.length;
  if (!scope) {
    return false;
  }
  if (scope === 'ward') {
    return true;
  }
  if (scope === 'single') {
    return count === 1;
  }
  return count >= 1;
});

/** 任务条：范围是否需要纠正 */
const scopeNeedsFix = computed(() => !patientScopeOk.value && activeDoc.value?.scope !== 'ward');

/** 任务条摘要：已选患者 */
const taskPatientSummary = computed(() => {
  const count = props.patients.length;
  if (activeDoc.value?.scope === 'ward') {
    return count > 0 ? `病区单据 · 已选 ${count} 人（可不绑）` : '病区单据 · 可不选患者';
  }
  if (count === 0) {
    return '尚未选择患者';
  }
  if (count === 1 && props.patients[0]) {
    const p = props.patients[0];
    return `${p.bedNo || '—'} 床 ${p.name}`;
  }
  const preview = props.patients
    .slice(0, 3)
    .map((p) => `${p.bedNo || '—'} ${p.name}`)
    .join('、');
  return `已选 ${count} 人：${preview}${count > 3 ? '…' : ''}`;
});

/** 顶部患者信息条 */
const stickyPatientInfo = computed(() => {
  if (props.patients.length === 0) {
    return buildNurseStationStickyPatientInfoPlaceholder({
      name: '请选择患者',
      diagnosis: '左侧勾选患者后可打印',
    });
  }
  if (activeDoc.value?.scope === 'single' && props.patients.length !== 1) {
    return buildNurseStationStickyPatientInfoPlaceholder({
      name: '请选择单一患者',
      diagnosis: `当前已选 ${props.patients.length} 人，本单据仅支持单选`,
    });
  }
  if (activeDoc.value?.scope === 'multiple' && props.patients.length === 0) {
    return buildNurseStationStickyPatientInfoPlaceholder({
      name: '请勾选患者',
      diagnosis: '本单据支持多选批量打印',
    });
  }
  if (props.patients.length === 1 && props.patients[0]) {
    return buildNurseStationStickyPatientInfoFromModulePatient(props.patients[0]);
  }
  return buildNurseStationStickyPatientInfoPlaceholder({
    name: `已选 ${props.patients.length} 位患者`,
    diagnosis: props.patients
      .slice(0, 4)
      .map((p) => `${p.bedNo || '—'} ${p.name}`)
      .join('、')
      .concat(props.patients.length > 4 ? '…' : ''),
  });
});

/** 列表展示行（患者用药单可按输液筛选） */
const displayRows = computed(() => {
  let rows = pendingRows.value;
  if (activeDocType.value === 'oral_med_sheet') {
    const filter = oralMedInfusionFilter.value;
    // 用药单：按是否输液收窄展示行
    if (filter === 'infusion') {
      rows = pendingRows.value.filter((row) => row.infusionFlag === true);
    } else if (filter === 'non_infusion') {
      rows = pendingRows.value.filter((row) => row.infusionFlag !== true);
    }
  }
  return rows;
});

/** 已勾选行 */
const selectedRows = computed(() =>
  displayRows.value.filter((row) => selectedRowIds.value.includes(row.id)),
);

/** 预估页数（巡视卡按患者成卡，再按频次出纸） */
const estimatedPages = computed(() => {
  const rows = selectedRows.value.length > 0 ? selectedRows.value : displayRows.value;
  const n = rows.length;
  if (activeDocType.value === 'patrol_card') {
    return estimatePatrolSheetCount(rows, patrolSplitByFrequency.value);
  }
  if (activeDoc.value?.previewKind === 'label') {
    return Math.max(1, Math.ceil(n / 8));
  }
  if (activeDoc.value?.previewKind === 'paper') {
    return Math.max(1, n);
  }
  return Math.max(1, Math.ceil(n / 20));
});

/** 是否可打印 */
const canPrint = computed(
  () =>
    isDocWired.value &&
    patientScopeOk.value &&
    (selectedRows.value.length > 0 || displayRows.value.length > 0),
);

/** 空态文案 */
const emptyDescription = computed(() =>
  resolvePrintEmptyDescription({
    docType: activeDocType.value,
    dateRangeLabel: dateRangeLabel.value,
    oralMedInfusionFilter: oralMedInfusionFilter.value,
    patientScopeOk: patientScopeOk.value,
    scope: activeDoc.value?.scope,
  }),
);

/** 范围引导文案 */
const scopeGuideText = computed(() => resolveScopeGuide(activeDoc.value?.scope));

/** 巡视卡预估张数：按就诊合成一张，再按频次翻倍 */
function estimatePatrolSheetCount(rows: PrintPendingRow[], splitByFrequency: boolean): number {
  const byEncounter = new Map<string, PrintPendingRow[]>();
  for (const row of rows) {
    const encounterId = String(row.encounterId ?? '').trim();
    if (!encounterId) {
      continue;
    }
    const existing = byEncounter.get(encounterId);
    if (!existing) {
      byEncounter.set(encounterId, [row]);
      continue;
    }
    existing.push(row);
  }
  let sheetCount = 0;
  for (const encounterRows of byEncounter.values()) {
    if (!splitByFrequency) {
      sheetCount += 1;
      continue;
    }
    let maxTimes = 1;
    // 该患者勾选组取最大每日次数（日两次打两张）
    for (const row of encounterRows) {
      const times = resolveInfusionTimesPerDaySync(row.rateCode, row.daySlotCount);
      if (times > maxTimes) {
        maxTimes = times;
      }
    }
    sheetCount += maxTimes;
  }
  return Math.max(1, sheetCount);
}

/** 按 id 列表同步表格勾选态（挂号皮肤 vxe checkbox） */
async function syncTableSelection(ids: string[]) {
  syncingSelection = true;
  selectedRowIds.value = ids;
  try {
    // 等 RegisterStyle 在 tableData 变更后的自动 clear 跑完
    await nextTick();
    await nextTick();
    const grid = resolvePendingGrid();
    grid?.clearCheckboxRow?.();
    for (const row of displayRows.value) {
      if (ids.includes(row.id)) {
        grid?.setCheckboxRow?.(row, true);
      }
    }
    selectedRowIds.value = ids;
  } finally {
    await nextTick();
    syncingSelection = false;
  }
}

/** 勾选当前可见未打印行；无未打印则全选 */
function selectUnprintedOrAllRows() {
  const ids = displayRows.value
    .filter((row) => row.printStatusCode !== 'printed')
    .map((row) => row.id);
  const fallbackIds = ids.length > 0 ? ids : displayRows.value.map((row) => row.id);
  void syncTableSelection(fallbackIds);
}

/** 默认勾选：巡视卡全选，其余优先未打印行 */
function selectDefaultPendingRows() {
  if (activeDocType.value === 'patrol_card') {
    void syncTableSelection(displayRows.value.map((row) => row.id));
    return;
  }
  selectUnprintedOrAllRows();
}

/** 拉取当前单据待打列表（无数据为空态，不弹错误） */
async function refreshPendingRows(options?: { silent?: boolean }) {
  if (!patientScopeOk.value && activeDoc.value?.scope !== 'ward') {
    pendingLoadSeq += 1;
    pendingRows.value = [];
    selectedRowIds.value = [];
    loadingPending.value = false;
    return;
  }

  const seq = ++pendingLoadSeq;
  loadingPending.value = true;
  try {
    // 带时间条件：传查询区间；无日期单据亦传当天（加载侧忽略）
    const dayFrom = queryDateFrom.value;
    const dayTo = queryDateTo.value;
    const rows = await loadPrintPendingRows({
      docType: activeDocType.value,
      patients: props.patients,
      dateFrom: dayFrom,
      dateTo: dayTo,
      executionDeadline: executionDeadline.value,
    });
    if (seq !== pendingLoadSeq) {
      return;
    }
    pendingRows.value = rows;
    await nextTick();
    selectDefaultPendingRows();
    if (!options?.silent) {
      ElMessage.success(`已刷新，共 ${rows.length} 条`);
    }
  } finally {
    if (seq === pendingLoadSeq) {
      loadingPending.value = false;
    }
  }
}

/** 切换打印单据 */
function handleDocTypeChange(docType: PrintDocType) {
  if (!isPrintDocVisibleInCenter(docType, hiddenPrintDocTypeSet)) {
    return;
  }
  if (activeDocType.value === docType) {
    return;
  }
  // 先出 loading，避免旧空态/旧列表在请求返回前闪一下
  pendingLoadSeq += 1;
  loadingPending.value = true;
  pendingRows.value = [];
  selectedRowIds.value = [];
  activeDocType.value = docType;
  // 带时间条件的单据：切到该类型时默认当天
  if (isDayQueryDocType(docType)) {
    dayQueryRange.value = buildDefaultDayRange();
  }
  // 切离患者用药单时复位筛选
  if (docType !== 'oral_med_sheet') {
    oralMedInfusionFilter.value = 'all';
  }
}

/** 表格勾选变化时同步已选 id */
function handleSelectionChange(rows: PrintPendingRow[]) {
  if (syncingSelection) {
    return;
  }
  selectedRowIds.value = rows.map((row) => row.id);
}

/** 查询：按当前条件重新拉业务数据 */
function handleQuery() {
  void refreshPendingRows();
}

/** 日期区间：整段向前平移 1 天 */
function handleDayQueryPrevDay() {
  const from = dayjs(queryDateFrom.value).subtract(1, 'day').format('YYYY-MM-DD');
  const to = dayjs(queryDateTo.value).subtract(1, 'day').format('YYYY-MM-DD');
  dayQueryRange.value = [from, to];
}

/** 日期区间：整段向后平移 1 天 */
function handleDayQueryNextDay() {
  const from = dayjs(queryDateFrom.value).add(1, 'day').format('YYYY-MM-DD');
  const to = dayjs(queryDateTo.value).add(1, 'day').format('YYYY-MM-DD');
  dayQueryRange.value = [from, to];
}

/** 日期区间变更后兜底非法值 */
function handleDayQueryRangeChange(value: [string, string] | null) {
  if (!value?.[0] || !value?.[1]) {
    dayQueryRange.value = buildDefaultDayRange();
  }
}

/** 全选当前可见待打行 */
function handleSelectAllPending() {
  selectUnprintedOrAllRows();
}

/** 清空勾选 */
function handleClearSelection() {
  void syncTableSelection([]);
}

/** 只留左侧第一人 */
function handleKeepFirstPatient() {
  props.onKeepFirstPatient?.();
}

/** 清空左侧患者 */
function handleClearPatients() {
  props.onClearPatients?.();
}

/** 打开打印确认 */
function handleOpenConfirm() {
  if (!isDocWired.value) {
    ElMessage.warning(`${activeDoc.value?.label ?? '该单据'}打印模板尚未对接`);
    return;
  }
  if (!canPrint.value) {
    ElMessage.warning(emptyDescription.value);
    return;
  }
  if (selectedRowIds.value.length === 0) {
    selectDefaultPendingRows();
  }
  confirmVisible.value = true;
}

/** 发送打印（确认后执行，链路与改前一致） */
async function handlePrint() {
  if (!isDocWired.value) {
    ElMessage.warning(`${activeDoc.value?.label ?? '该单据'}打印模板尚未对接`);
    return;
  }
  if (!canPrint.value) {
    ElMessage.warning(emptyDescription.value);
    return;
  }
  if (selectedRowIds.value.length === 0) {
    selectDefaultPendingRows();
  }
  if (printing.value) {
    return;
  }
  printing.value = true;
  try {
    // 费用类：打印前静默重拉待打列表，避免挂起页用旧清单做空态判断
    if (activeDocType.value === 'fee_list' || activeDocType.value === 'fee_detail') {
      await refreshPendingRows({ silent: true });
      if (pendingRows.value.length <= 0) {
        ElMessage.warning(`暂无「${activeDoc.value?.label ?? '费用单据'}」可打印数据`);
        return;
      }
      if (selectedRowIds.value.length === 0) {
        selectDefaultPendingRows();
      }
    }
    const result = await printInpatientPrintCenter({
      docType: activeDocType.value,
      patients: props.patients,
      selectedRows: selectedRows.value.length > 0 ? selectedRows.value : displayRows.value,
      copies: copies.value,
      dateFrom: queryDateFrom.value,
      dateTo: queryDateTo.value,
      executionDeadline: executionDeadline.value,
      splitByFrequency: patrolSplitByFrequency.value,
    });
    if (result.ok) {
      confirmVisible.value = false;
      ElMessage.success(`已发送打印：${activeDoc.value?.label ?? '单据'}，共 ${result.count} 份`);
      // 摆药单等依赖审计回显：打印成功后静默刷新状态
      void refreshPendingRows({ silent: true });
    }
  } finally {
    printing.value = false;
  }
}

/** 页签重新可见时：费用单据静默刷新待打列表 */
function handlePrintCenterVisibilityChange() {
  if (document.visibilityState !== 'visible') {
    return;
  }
  if (activeDocType.value !== 'fee_list' && activeDocType.value !== 'fee_detail') {
    return;
  }
  void refreshPendingRows({ silent: true });
}

onMounted(() => {
  document.addEventListener('visibilitychange', handlePrintCenterVisibilityChange);
});

onUnmounted(() => {
  document.removeEventListener('visibilitychange', handlePrintCenterVisibilityChange);
});

/** 患者 / 单据 / 查询区间变化时重新加载 */
watch(
  [() => props.patients.map((p) => p.id).join(','), activeDocType, queryDateFrom, queryDateTo],
  () => {
    void refreshPendingRows({ silent: true });
  },
  { immediate: true },
);

/** 用药单是否输液变化时仅重勾选，不重新请求 */
watch(oralMedInfusionFilter, () => {
  selectDefaultPendingRows();
});
</script>

<template>
  <div class="print-center-page bg-background-deep flex min-h-0 flex-1 flex-col">
    <InpatientPatientInfoHeader :patient-info="stickyPatientInfo" />

    <div class="print-center-shell bg-background flex min-h-0 flex-1 flex-col">
      <!-- 任务条：谁 + 打什么 + 范围引导 -->
      <div class="print-center-task" :class="{ 'is-warn': scopeNeedsFix }" role="status">
        <div class="print-center-task__main min-w-0">
          <span class="print-center-task__label">当前任务</span>
          <span class="print-center-task__doc">{{ panelTitle }}</span>
          <ElTag size="small" effect="plain" type="info">
            {{ resolvePrintScopeLabel(activeDoc?.scope ?? 'single') }}
          </ElTag>
          <span class="print-center-task__sep" aria-hidden="true">·</span>
          <span class="print-center-task__patients truncate">{{ taskPatientSummary }}</span>
        </div>
        <div class="print-center-task__actions flex shrink-0 items-center gap-2">
          <template v-if="scopeNeedsFix">
            <span class="print-center-task__hint truncate">{{ scopeGuideText }}</span>
            <ElButton
              v-if="activeDoc?.scope === 'single' && patients.length > 1 && onKeepFirstPatient"
              size="small"
              type="primary"
              plain
              @click="handleKeepFirstPatient"
            >
              只留第一人
            </ElButton>
            <ElButton
              v-if="onClearPatients && patients.length > 0"
              size="small"
              plain
              @click="handleClearPatients"
            >
              清空选人
            </ElButton>
          </template>
          <ElTooltip
            v-else-if="activeDoc?.description"
            :content="activeDoc.description"
            placement="bottom"
          >
            <button type="button" class="print-center-task__help" aria-label="单据说明">?</button>
          </ElTooltip>
        </div>
      </div>

      <!-- 单据类型：单层平铺 -->
      <div class="print-center-docs" role="tablist" aria-label="打印单据类型">
        <button
          v-for="doc in docTypeOptions"
          :key="doc.code"
          type="button"
          role="tab"
          class="print-center-docs__item"
          :class="{ 'is-active': activeDocType === doc.code }"
          :aria-selected="activeDocType === doc.code"
          @click="handleDocTypeChange(doc.code)"
        >
          <span class="print-center-docs__label">{{ doc.label }}</span>
        </button>
      </div>

      <!-- 条件工具栏：仅渲染当前单据需要的控件 -->
      <div class="print-center-filters">
        <div class="print-center-filters__left flex flex-wrap items-center gap-2">
          <template v-if="isDayQueryDocType(activeDocType)">
            <ElButton class="print-center-btn" size="default" @click="handleDayQueryPrevDay">
              上一天
            </ElButton>
            <ElDatePicker
              v-model="dayQueryRange"
              type="daterange"
              size="default"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :shortcuts="dateRangeShortcuts"
              class="print-center-daterange"
              @change="handleDayQueryRangeChange"
            />
            <ElButton class="print-center-btn" size="default" @click="handleDayQueryNextDay">
              下一天
            </ElButton>
          </template>

          <ElRadioGroup
            v-if="needsOralMedInfusionFilter(activeDocType)"
            v-model="oralMedInfusionFilter"
            size="default"
          >
            <ElRadio value="all">全部</ElRadio>
            <ElRadio value="infusion">输液药</ElRadio>
            <ElRadio value="non_infusion">非输液药</ElRadio>
          </ElRadioGroup>

          <label
            v-if="needsFrequencySplitSwitch(activeDocType)"
            class="print-center-freq-switch inline-flex items-center gap-1.5"
          >
            <span>按频次打印</span>
            <ElSwitch v-model="patrolSplitByFrequency" />
            <ElTooltip
              content="开启后按医嘱频次打印：日两次打印两张，关闭后不再按频次拆开。"
              placement="bottom"
            >
              <button type="button" class="print-center-task__help" aria-label="按频次打印说明">
                ?
              </button>
            </ElTooltip>
          </label>
        </div>

        <div class="print-center-filters__right flex shrink-0 items-center gap-2">
          <ElButton class="print-center-btn" size="default" @click="handleSelectAllPending">
            全选未打
          </ElButton>
          <ElButton class="print-center-btn" size="default" @click="handleClearSelection">
            清空
          </ElButton>
          <ElButton class="print-center-btn" type="primary" size="default" @click="handleQuery">
            刷新
          </ElButton>
        </div>
      </div>

      <!-- 主区：待打清单（自有布局，不再套 TriPane） -->
      <div class="print-center-body flex min-h-0 flex-1 flex-col">
        <div class="print-center-panel flex min-h-0 flex-1 flex-col">
          <div class="print-center-panel__head">
            <SectionTitle :title="panelTitle" />
            <span v-if="displayRows.length > 0" class="print-center-panel__count">
              共 {{ displayRows.length }} 条
            </span>
          </div>
          <div
            v-loading="loadingPending"
            element-loading-text="正在加载待打数据…"
            class="print-center-panel__content relative flex min-h-0 flex-1 flex-col"
          >
            <div
              v-if="!loadingPending && displayRows.length === 0"
              class="print-center-panel__empty flex flex-1 flex-col items-center justify-center gap-3"
            >
              <ElEmpty :description="emptyDescription" />
              <div v-if="scopeNeedsFix" class="flex flex-wrap items-center justify-center gap-2">
                <ElButton
                  v-if="activeDoc?.scope === 'single' && patients.length > 1 && onKeepFirstPatient"
                  type="primary"
                  @click="handleKeepFirstPatient"
                >
                  只留第一人后继续
                </ElButton>
                <ElButton
                  v-if="onClearPatients && patients.length > 0"
                  plain
                  @click="handleClearPatients"
                >
                  清空选人
                </ElButton>
              </div>
            </div>
            <div
              v-else-if="!loadingPending"
              class="print-center-panel__table min-h-0 flex-1 overflow-hidden"
            >
              <RegisterStyleTableLayout
                ref="pendingTableRef"
                class="print-center-register-table h-full min-h-0 flex-1 !p-0"
                :table-data="displayRows"
                :loading="false"
                :total="displayRows.length"
                :query-params="pendingQueryParams"
                :table-columns="registerTableColumns"
                :show-top-query="false"
                :show-pagination="false"
                row-key="id"
                :highlight-current-row="true"
                @selection-change="handleSelectionChange"
              >
                <template #titleText="{ row }">
                  <span class="print-center-cell-wrap">{{ row.title }}</span>
                </template>
                <template #statusText="{ row }">
                  <ElTag
                    size="small"
                    :type="
                      row.statusCode === 'pending' ||
                      row.statusCode === 'ready' ||
                      row.statusCode === 'executable'
                        ? 'warning'
                        : 'info'
                    "
                  >
                    {{ row.statusText }}
                  </ElTag>
                </template>
                <template #printStatusText="{ row }">
                  <ElTag
                    size="small"
                    :type="row.printStatusCode === 'printed' ? 'success' : 'warning'"
                  >
                    {{ row.printStatusText || '未打印' }}
                  </ElTag>
                </template>
              </RegisterStyleTableLayout>
            </div>
            <!-- 加载中占位：保证 v-loading 有可视区域 -->
            <div v-else class="print-center-panel__loading-placeholder min-h-0 flex-1" />
          </div>
        </div>

        <div class="print-center-footer flex w-full shrink-0 items-center justify-between gap-3">
          <div class="print-center-footer__meta flex flex-wrap items-center gap-x-3 gap-y-1">
            <span>
              已勾选
              <strong>{{ selectedRows.length }}</strong>
              项
            </span>
            <span class="print-center-footer__sep" aria-hidden="true">·</span>
            <span>
              预估
              <strong>{{ estimatedPages }}</strong>
              页
            </span>
            <span class="print-center-footer__sep" aria-hidden="true">·</span>
            <span class="print-center-footer__copies inline-flex items-center gap-1.5">
              份数
              <ElInputNumber
                v-model="copies"
                :min="1"
                :max="9"
                size="small"
                controls-position="right"
                class="print-center-copies"
              />
            </span>
          </div>
          <div class="flex shrink-0 items-center gap-2">
            <ElButton
              class="print-center-btn"
              type="primary"
              plain
              :disabled="!canPrint"
              @click="handleOpenConfirm"
            >
              确认清单
            </ElButton>
            <ElButton
              type="primary"
              class="print-center-btn print-center-footer__print"
              :disabled="!canPrint"
              :loading="printing"
              @click="handlePrint"
            >
              打印
            </ElButton>
          </div>
        </div>
      </div>
    </div>

    <ElDrawer
      v-model="confirmVisible"
      title="打印确认"
      size="420px"
      append-to-body
      destroy-on-close
    >
      <div class="print-center-confirm flex h-full flex-col gap-3">
        <div class="print-center-confirm__meta">
          <div>
            单据：<strong>{{ panelTitle }}</strong>
          </div>
          <div>勾选 {{ selectedRows.length || displayRows.length }} 项 · 份数 {{ copies }}</div>
          <div class="print-center-confirm__hint">
            请核对下方清单后再发送打印（与直接点「打印」相同）。
          </div>
        </div>
        <div class="print-center-confirm__list min-h-0 flex-1 overflow-auto">
          <div
            v-for="row in selectedRows.length > 0 ? selectedRows : displayRows"
            :key="row.id"
            class="print-center-confirm__row"
          >
            <div class="print-center-confirm__title">
              {{ row.bedNo || '—' }} {{ row.patientName }} · {{ row.title }}
            </div>
            <div v-if="row.subtitle || row.timeText" class="print-center-confirm__sub">
              {{ [row.subtitle, row.timeText].filter(Boolean).join(' · ') }}
            </div>
          </div>
        </div>
        <div class="flex shrink-0 justify-end gap-2">
          <ElButton @click="confirmVisible = false">取消</ElButton>
          <ElButton type="primary" :loading="printing" @click="handlePrint">确认打印</ElButton>
        </div>
      </div>
    </ElDrawer>
  </div>
</template>
