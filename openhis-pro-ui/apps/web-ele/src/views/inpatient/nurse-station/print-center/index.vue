<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type {
  InfusionTherapyFilter,
  OrderEntrustFilter,
  TempOrderSurgeryAnesthFilter,
} from './doc-registry';
import type { PrintDocType, PrintPendingRow } from './print-center.types';

import type { TableColumn } from '@zxplat-core/table-layout-ui';

import { computed, nextTick, onMounted, onUnmounted, reactive, ref, unref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElMessage, ElProgress } from 'element-plus';

import InpatientPatientInfoHeader from '#/components/inpatient/patient-info-header.vue';
import RegisterStyleTableLayout from '#/components/layouts/register-style-table-layout.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import { PRINT_CENTER_HIDDEN_DOC_TYPES } from '#/views/inpatient/nurse-station/print-center/print-center-doc.config';

import {
  buildNurseStationStickyPatientInfoFromModulePatient,
  buildNurseStationStickyPatientInfoPlaceholder,
} from '../components/build-nurse-station-sticky-patient-info';
import {
  filterInfusionRowsByTherapy,
  filterOrderRowsByEntrust,
  filterTempOrderRowsBySurgeryAnesth,
  getPrintDocColumns,
  isDayQueryDocType,
  isPrintDocVisibleInCenter,
  listAllVisiblePrintDocs,
  needsFrequencySplitSwitch,
  needsInfusionTherapyFilter,
  needsOralMedInfusionFilter,
  needsOrderEntrustFilter,
  needsTempOrderSurgeryAnesthFilter,
  resolveDefaultPrintDocType,
  resolvePrintCenterScenarioCode,
  resolvePrintEmptyDescription,
  resolveScopeGuide,
} from './doc-registry';
import { loadPrintPendingRows } from './load-print-pending';
import {
  PRINT_CENTER_WIRED_DOC_TYPES,
  printInpatientPrintCenter,
  type PrintCenterProgress,
} from './print-center-print';
import { usePrintCenterPrinters } from './use-print-center-printers';
import { findPrintDocType, resolvePrintScopeLabel } from './print-center.types';
import { buildDefaultDateRangeShortcuts } from '#/utils/format/date-picker-shortcuts';
import { resolveInfusionTimesPerDaySync } from '#/utils/print/mappers/outpatient-infusion-label';
import {
  clusterRowsBySpecimenMerge,
  resolveSpecimenBarcodeLabelCount,
  resolveSpecimenBarcodeMergeClusterKey,
} from '#/utils/print/nurse-station-print-targets';

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
/** 输液贴 / 输液巡视卡长临筛选：all 全部 / long 长期 / temp 临时 */
const infusionTherapyFilter = ref<InfusionTherapyFilter>('all');
/** 临时医嘱筛选：all 全部 / surgery 手术 / anesthesia 麻醉 / entrust 嘱托（互斥） */
const tempOrderSurgeryAnesthFilter = ref<TempOrderSurgeryAnesthFilter>('all');
/** 长期/临时医嘱筛选：all 全部 / entrust 嘱托 */
const orderEntrustFilter = ref<OrderEntrustFilter>('all');
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
/** 批量打印进度（采血贴等） */
const printProgressVisible = ref(false);
const printProgress = ref<PrintCenterProgress>({ current: 0, total: 0, label: '' });

const printProgressPercent = computed(() => {
  const total = printProgress.value.total;
  if (total <= 0) {
    return 0;
  }
  return Math.min(100, Math.round((printProgress.value.current / total) * 100));
});
const loadingPending = ref(false);
const pendingRows = ref<PrintPendingRow[]>([]);
/** 待打列表加载序号：快速切页签时丢弃过期响应 */
let pendingLoadSeq = 0;
/** RegisterStyleTableLayout 必填分页占位（本页不分页） */
const pendingQueryParams = reactive({ pageNo: 1, pageSize: 500 });
/** 打印前确认抽屉（清单确认，不改打印装载语义） */
const confirmVisible = ref(false);

/** 平铺可见单据（体温单/院点隐藏项已过滤；不展示 count 角标） */
const docTypeOptions = computed(() => listAllVisiblePrintDocs(hiddenPrintDocTypeSet));

const {
  printersLoading,
  printerByDoc,
  printerHint,
  loadPrinters,
  printerOptionsFor,
  handlePrinterChange: setPrinterPreference,
  resolveSelectedPrinter,
} = usePrintCenterPrinters(() => docTypeOptions.value.map((doc) => doc.code));

/** 未选打印机时下拉框高亮（点打印触发，不弹 toast） */
const printerSelectHighlight = ref(false);

/** 选择打印机并清除高亮 */
function handlePrinterChange(docType: PrintDocType, printerName: string) {
  setPrinterPreference(docType, printerName);
  if (String(printerName ?? '').trim()) {
    printerSelectHighlight.value = false;
  }
}

/** 当前单据是否已对接真实打印模板 */
const isDocWired = computed(() => PRINT_CENTER_WIRED_DOC_TYPES.includes(activeDocType.value));

/** 当前单据配置 */
const activeDoc = computed(() => findPrintDocType(activeDocType.value));

/** 面板标题：当前单据名 */
const panelTitle = computed(() => activeDoc.value?.label ?? '待打清单');

/** 表列（按单据语义） */
const tableColumns = computed(() => getPrintDocColumns(activeDocType.value));

/** 输液贴 / 巡视卡 / 采血贴内容列折行（覆盖挂号表 nowrap） */
const WRAP_TITLE_DOC_TYPES = new Set<PrintDocType>([
  'infusion_label',
  'patrol_card',
  'specimen_barcode',
]);

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
      ...(col.key === 'therapyTypeText' ? { slot: 'therapyTypeText' } : {}),
      ...(col.key === 'printStatusText' ? { slot: 'printStatusText' } : {}),
      ...(col.key === 'title' && wrapTitle ? { slot: 'titleText' } : {}),
    })),
  ];
});

/**
 * 输液贴长临标记颜色：长期蓝、临时橙，其余灰。
 * 只按 therapyTypeCode，不用展示文案。
 */
function infusionTherapyTagType(code?: string): 'info' | 'primary' | 'warning' {
  const normalized = String(code ?? '')
    .trim()
    .toUpperCase();
  if (normalized === 'LONG_TERM') {
    return 'primary';
  }
  if (normalized === 'TEMPORARY') {
    return 'warning';
  }
  return 'info';
}

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

/** 采血贴合管聚类键；无合管时为空（不与邻行折叠） */
function specimenMergeId(row: PrintPendingRow): string {
  return resolveSpecimenBarcodeMergeClusterKey(
    row.specimenTubeMergeDeviceId,
    row.specimenTubeMergeGroupNo,
    row.priorityEnum,
    row.encounterId,
  );
}

/** 雪花主键升序（合管锚点与打印一致） */
function compareSpecimenOrderIdAsc(left: string, right: string): number {
  try {
    const a = BigInt(left);
    const b = BigInt(right);
    if (a < b) {
      return -1;
    }
    if (a > b) {
      return 1;
    }
    return 0;
  } catch {
    return left.localeCompare(right);
  }
}

/**
 * 采血贴列表：合管组折叠为一行（项目名顿号拼接），避免 rowspan 占多行高度留白。
 * 成员原行走 specimenMergeMembers，打印前再展开。
 */
function collapseSpecimenBarcodeDisplayRows(rows: PrintPendingRow[]): PrintPendingRow[] {
  const clustered = clusterRowsBySpecimenMerge(rows, specimenMergeId);
  const result: PrintPendingRow[] = [];
  let index = 0;
  while (index < clustered.length) {
    const head = clustered[index];
    if (!head) {
      break;
    }
    const mergeKey = specimenMergeId(head);
    if (!mergeKey) {
      result.push(head);
      index += 1;
      continue;
    }
    const members: PrintPendingRow[] = [head];
    let next = index + 1;
    while (next < clustered.length) {
      const row = clustered[next];
      if (!row || specimenMergeId(row) !== mergeKey) {
        break;
      }
      members.push(row);
      next += 1;
    }
    if (members.length <= 1) {
      result.push(head);
      index = next;
      continue;
    }
    let anchor = members[0]!;
    for (const member of members) {
      const anchorOrderId = String(anchor.orderId ?? '').trim();
      const memberOrderId = String(member.orderId ?? '').trim();
      if (
        memberOrderId &&
        (!anchorOrderId || compareSpecimenOrderIdAsc(memberOrderId, anchorOrderId) < 0)
      ) {
        anchor = member;
      }
    }
    const titles: string[] = [];
    let maxLabelCount = 1;
    let allPrinted = true;
    let hasPending = false;
    for (const member of members) {
      const title = String(member.title ?? '').trim();
      if (title && !titles.includes(title)) {
        titles.push(title);
      }
      maxLabelCount = Math.max(
        maxLabelCount,
        resolveSpecimenBarcodeLabelCount(member.dosageDisplay),
      );
      if (member.printStatusCode !== 'printed') {
        allPrinted = false;
      }
      if (member.statusCode === 'pending' || member.statusCode === 'executable') {
        hasPending = true;
      }
    }
    result.push({
      ...anchor,
      id: `specimen-merge:${mergeKey}`,
      title: titles.join('、'),
      subtitle: `合管 ${members.length} 项`,
      dosageDisplay: String(maxLabelCount),
      pageHint: maxLabelCount > 1 ? `${maxLabelCount}张` : '采血贴',
      statusCode: hasPending ? 'pending' : anchor.statusCode,
      statusText: hasPending ? '待执行' : anchor.statusText,
      printStatusCode: allPrinted ? 'printed' : 'unprinted',
      printStatusText: allPrinted ? '已打印' : '未打印',
      specimenMergeMembers: members,
    });
    index = next;
  }
  return result;
}

/** 采血贴打印前：折叠行展开为成员原行 */
function expandSpecimenBarcodePrintRows(rows: PrintPendingRow[]): PrintPendingRow[] {
  const expanded: PrintPendingRow[] = [];
  for (const row of rows) {
    const members = row.specimenMergeMembers;
    if (members && members.length > 0) {
      expanded.push(...members);
      continue;
    }
    expanded.push(row);
  }
  return expanded;
}

/** 列表展示行（患者用药单按输液筛选；临时医嘱按手术/麻醉筛选；长/临按嘱托筛选；采血贴按合管折叠） */
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
  if (needsInfusionTherapyFilter(activeDocType.value)) {
    rows = filterInfusionRowsByTherapy(rows, infusionTherapyFilter.value);
  }
  if (needsTempOrderSurgeryAnesthFilter(activeDocType.value)) {
    rows = filterTempOrderRowsBySurgeryAnesth(rows, tempOrderSurgeryAnesthFilter.value);
  }
  if (needsOrderEntrustFilter(activeDocType.value)) {
    rows = filterOrderRowsByEntrust(rows, orderEntrustFilter.value);
  }
  // 采血贴：合管组折叠为一行，与实际贴签张数对齐
  if (activeDocType.value === 'specimen_barcode') {
    return collapseSpecimenBarcodeDisplayRows(rows);
  }
  return rows;
});

/** 连续行合并信息：isFirst + rowspan（仅输液贴按患者用） */
function buildConsecutiveSpanByRowId(
  rows: PrintPendingRow[],
  groupKeyOf: (row: PrintPendingRow) => string,
): Map<string, { isFirst: boolean; rowspan: number; groupKey: string }> {
  const map = new Map<string, { isFirst: boolean; rowspan: number; groupKey: string }>();
  let i = 0;
  while (i < rows.length) {
    const head = rows[i];
    if (!head) {
      break;
    }
    const groupKey = groupKeyOf(head);
    let j = i + 1;
    while (j < rows.length) {
      const next = rows[j];
      if (!next || groupKeyOf(next) !== groupKey) {
        break;
      }
      j += 1;
    }
    const rowspan = j - i;
    for (let k = i; k < j; k += 1) {
      const row = rows[k];
      if (!row) {
        continue;
      }
      map.set(row.id, { isFirst: k === i, rowspan, groupKey });
    }
    i = j;
  }
  return map;
}

/** 输液贴：按患者合并床号/姓名列，方便多患者批量时一眼分清 */
const infusionPatientSpanByRowId = computed(() => {
  if (activeDocType.value !== 'infusion_label') {
    return new Map<string, { isFirst: boolean; rowspan: number; groupKey: string }>();
  }
  return buildConsecutiveSpanByRowId(
    displayRows.value,
    (row) => String(row.encounterId ?? '').trim() || `row:${row.id}`,
  );
});

/** 采血贴：勾选行对应的检验项数（折叠行计成员数） */
const selectedSpecimenItemCount = computed(() => {
  if (activeDocType.value !== 'specimen_barcode') {
    return selectedRows.value.length;
  }
  let count = 0;
  for (const row of selectedRows.value) {
    count += row.specimenMergeMembers?.length ?? 1;
  }
  return count;
});

/** 采血贴合管后预计张数（与列表行 / 打印分组一致） */
const specimenMergeLabelCount = computed(() => {
  if (activeDocType.value !== 'specimen_barcode') {
    return 0;
  }
  const rows = selectedRows.value.length > 0 ? selectedRows.value : displayRows.value;
  let total = 0;
  for (const row of rows) {
    total += resolveSpecimenBarcodeLabelCount(row.dosageDisplay);
  }
  return total;
});

/** 当前列表涉及患者数（输液贴分组提示） */
const displayPatientCount = computed(() => {
  const ids = new Set<string>();
  for (const row of displayRows.value) {
    const id = String(row.encounterId ?? '').trim();
    if (id) {
      ids.add(id);
    }
  }
  return ids.size;
});

/** VxeGrid spanMethod：仅输液贴按患者合并床号/姓名 */
function handlePrintCenterSpanMethod(params: {
  column: { field?: string; property?: string; type?: string };
  row: PrintPendingRow;
}) {
  if (activeDocType.value !== 'infusion_label') {
    return undefined;
  }
  const field = String(params.column.field ?? params.column.property ?? '').trim();
  if (field !== 'bedNo' && field !== 'patientName') {
    return undefined;
  }
  const info = infusionPatientSpanByRowId.value.get(params.row.id);
  if (!info) {
    return undefined;
  }
  if (info.isFirst) {
    return { rowspan: info.rowspan, colspan: 1 };
  }
  return { rowspan: 0, colspan: 0 };
}

/** 已勾选行 */
const selectedRows = computed(() =>
  displayRows.value.filter((row) => selectedRowIds.value.includes(row.id)),
);

/** 预估页数（巡视卡按患者成卡；检查/检验单按就诊合并成一张；采血贴按合管后张数） */
const estimatedPages = computed(() => {
  const rows = selectedRows.value.length > 0 ? selectedRows.value : displayRows.value;
  const n = rows.length;
  if (activeDocType.value === 'patrol_card') {
    return estimatePatrolSheetCount(rows, patrolSplitByFrequency.value);
  }
  // 与 buildServiceApplyPrintSources 一致：一张单一就诊，不能按勾选行数估页
  if (activeDocType.value === 'exam_apply' || activeDocType.value === 'lab_apply') {
    return estimateServiceApplySheetCount(rows);
  }
  if (activeDocType.value === 'specimen_barcode') {
    return Math.max(1, specimenMergeLabelCount.value * Math.max(1, copies.value));
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
    tempOrderSurgeryAnesthFilter: tempOrderSurgeryAnesthFilter.value,
    orderEntrustFilter: orderEntrustFilter.value,
    infusionTherapyFilter: infusionTherapyFilter.value,
    patientScopeOk: patientScopeOk.value,
    scope: activeDoc.value?.scope,
  }),
);

/** 范围引导文案 */
const scopeGuideText = computed(() => resolveScopeGuide(activeDoc.value?.scope));

/** 检查/检验申请单预估张数：按就诊合并，一张单对应一个就诊 */
function estimateServiceApplySheetCount(rows: PrintPendingRow[]): number {
  const encounterIds = new Set<string>();
  for (const row of rows) {
    const encounterId = String(row.encounterId ?? '').trim();
    if (encounterId) {
      encounterIds.add(encounterId);
    }
  }
  return Math.max(1, encounterIds.size || rows.length);
}

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
      tempOrderSurgeryAnesthFilter: tempOrderSurgeryAnesthFilter.value,
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
  printerSelectHighlight.value = false;
  activeDocType.value = docType;
  // 带时间条件的单据：切到该类型时默认当天
  if (isDayQueryDocType(docType)) {
    dayQueryRange.value = buildDefaultDayRange();
  }
  // 切离患者用药单时复位筛选
  if (docType !== 'oral_med_sheet') {
    oralMedInfusionFilter.value = 'all';
  }
  // 切离输液贴/巡视卡时复位长临筛选
  if (!needsInfusionTherapyFilter(docType)) {
    infusionTherapyFilter.value = 'all';
  }
  // 切离临时医嘱时复位手术/麻醉筛选
  if (docType !== 'temp_order') {
    tempOrderSurgeryAnesthFilter.value = 'all';
  }
  // 切离长/临医嘱时复位嘱托筛选
  if (!needsOrderEntrustFilter(docType)) {
    orderEntrustFilter.value = 'all';
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

/** 未选打印机时拦截打印：只高亮下拉框，不弹 toast */
function ensurePrinterSelected(): boolean {
  if (resolveSelectedPrinter(activeDocType.value)) {
    printerSelectHighlight.value = false;
    return true;
  }
  printerSelectHighlight.value = true;
  return false;
}

/** 打开打印确认 */
function handleOpenConfirm() {
  if (!isDocWired.value) {
    ElMessage.warning(`${activeDoc.value?.label ?? '该单据'}打印模板尚未对接`);
    return;
  }
  if (!ensurePrinterSelected()) {
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
  if (!ensurePrinterSelected()) {
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
  printProgress.value = { current: 0, total: 0, label: '准备打印…' };
  printProgressVisible.value = true;
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
    const printRows =
      selectedRows.value.length > 0 ? selectedRows.value : displayRows.value;
    const result = await printInpatientPrintCenter({
      docType: activeDocType.value,
      patients: props.patients,
      selectedRows:
        activeDocType.value === 'specimen_barcode'
          ? expandSpecimenBarcodePrintRows(printRows)
          : printRows,
      copies: copies.value,
      dateFrom: queryDateFrom.value,
      dateTo: queryDateTo.value,
      executionDeadline: executionDeadline.value,
      splitByFrequency: patrolSplitByFrequency.value,
      tempOrderSurgeryAnesthFilter: tempOrderSurgeryAnesthFilter.value,
      printer: resolveSelectedPrinter(activeDocType.value),
      onProgress: (progress) => {
        printProgress.value = progress;
      },
    });
    if (result.ok) {
      confirmVisible.value = false;
      // 采血贴批量在内部已 toast，避免重复成功提示
      if (activeDocType.value !== 'specimen_barcode') {
        ElMessage.success(`已发送打印：${activeDoc.value?.label ?? '单据'}，共 ${result.count} 份`);
      }
      // 摆药单等依赖审计回显：打印成功后静默刷新状态
      void refreshPendingRows({ silent: true });
    }
  } finally {
    printing.value = false;
    printProgressVisible.value = false;
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
  void loadPrinters();
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

/** 输液贴 / 巡视卡长临筛选变化时仅重勾选，不重新请求 */
watch(infusionTherapyFilter, () => {
  if (!needsInfusionTherapyFilter(activeDocType.value)) {
    return;
  }
  selectDefaultPendingRows();
});

/** 长/临嘱托筛选变化时仅重勾选，不重新请求 */
watch(orderEntrustFilter, () => {
  if (!needsOrderEntrustFilter(activeDocType.value)) {
    return;
  }
  selectDefaultPendingRows();
});

/** 临时医嘱手术/麻醉筛选变化：麻醉与正常取数口径不同，需重新拉列表 */
watch(tempOrderSurgeryAnesthFilter, () => {
  if (!needsTempOrderSurgeryAnesthFilter(activeDocType.value)) {
    return;
  }
  void refreshPendingRows({ silent: true });
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

      <!-- 条件工具栏：当前页签打印机 + 单据筛选 -->
      <div class="print-center-filters">
        <div class="print-center-filters__left flex flex-wrap items-center gap-2">
          <template v-if="resolvePrintCenterScenarioCode(activeDocType)">
            <span
              class="print-center-filters__hint"
              :class="{ 'is-printer-warn': printerSelectHighlight && !printerByDoc[activeDocType] }"
            >
              打印机
            </span>
            <ElSelect
              :model-value="printerByDoc[activeDocType] || ''"
              class="print-center-filters__printer"
              :class="{
                'is-printer-warn': printerSelectHighlight && !printerByDoc[activeDocType],
              }"
              size="default"
              filterable
              clearable
              :loading="printersLoading"
              :placeholder="printerHint || '请选择打印机'"
              @change="(value) => handlePrinterChange(activeDocType, String(value ?? ''))"
            >
              <ElOption
                v-for="item in printerOptionsFor(activeDocType)"
                :key="item.name"
                :label="item.displayName"
                :value="item.name"
              />
            </ElSelect>
          </template>

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
            v-if="needsInfusionTherapyFilter(activeDocType)"
            v-model="infusionTherapyFilter"
            size="default"
          >
            <ElRadio value="all">全部</ElRadio>
            <ElRadio value="long">长期</ElRadio>
            <ElRadio value="temp">临时</ElRadio>
          </ElRadioGroup>

          <ElRadioGroup
            v-if="needsOralMedInfusionFilter(activeDocType)"
            v-model="oralMedInfusionFilter"
            size="default"
          >
            <ElRadio value="all">全部</ElRadio>
            <ElRadio value="infusion">输液药</ElRadio>
            <ElRadio value="non_infusion">非输液药</ElRadio>
          </ElRadioGroup>

          <ElRadioGroup
            v-if="needsTempOrderSurgeryAnesthFilter(activeDocType)"
            v-model="tempOrderSurgeryAnesthFilter"
            size="default"
          >
            <ElRadio value="all">全部</ElRadio>
            <ElRadio value="surgery">手术</ElRadio>
            <ElRadio value="anesthesia">麻醉</ElRadio>
            <ElRadio value="entrust">嘱托</ElRadio>
          </ElRadioGroup>

          <ElRadioGroup
            v-if="needsOrderEntrustFilter(activeDocType)"
            v-model="orderEntrustFilter"
            size="default"
          >
            <ElRadio value="all">全部</ElRadio>
            <ElRadio value="entrust">嘱托</ElRadio>
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
              <template v-if="activeDocType === 'infusion_label' && displayPatientCount > 0">
                · {{ displayPatientCount }} 位患者
              </template>
              <template v-if="activeDocType === 'specimen_barcode'">
                · 合管后 {{ displayRows.length }} 张
              </template>
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
                :class="{
                  'print-center-register-table--patient-group': activeDocType === 'infusion_label',
                  'print-center-register-table--specimen-merge':
                    activeDocType === 'specimen_barcode',
                }"
                :table-data="displayRows"
                :loading="false"
                :total="displayRows.length"
                :query-params="pendingQueryParams"
                :table-columns="registerTableColumns"
                :show-top-query="false"
                :show-pagination="false"
                row-key="id"
                :highlight-current-row="true"
                :span-method="handlePrintCenterSpanMethod"
                @selection-change="handleSelectionChange"
              >
                <template #titleText="{ row }">
                  <span class="print-center-cell-wrap">{{ row.title }}</span>
                </template>
                <template #therapyTypeText="{ row }">
                  <ElTag
                    v-if="row.therapyTypeText"
                    size="small"
                    :type="infusionTherapyTagType(row.therapyTypeCode)"
                  >
                    {{ row.therapyTypeText }}
                  </ElTag>
                  <span v-else>—</span>
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

        <!-- 打印进度：仅占打印中心底栏上方，无遮罩、不挡其它操作 -->
        <div
          v-if="printProgressVisible"
          class="print-center-progress shrink-0"
          role="status"
          aria-live="polite"
        >
          <div class="print-center-progress__head">
            <span class="print-center-progress__title">正在打印</span>
            <span class="print-center-progress__count">
              <template v-if="printProgress.total > 0">
                {{ printProgress.current }} / {{ printProgress.total }}
              </template>
              <template v-else>准备中…</template>
            </span>
          </div>
          <ElProgress
            :percentage="printProgressPercent"
            :stroke-width="10"
            striped
            striped-flow
          />
          <div class="print-center-progress__label truncate">
            {{ printProgress.label || '发送中…' }}
          </div>
        </div>

        <div class="print-center-footer flex w-full shrink-0 items-center justify-between gap-3">
          <div class="print-center-footer__meta flex flex-wrap items-center gap-x-3 gap-y-1">
            <span>
              已勾选
              <strong>{{
                activeDocType === 'specimen_barcode'
                  ? selectedSpecimenItemCount
                  : selectedRows.length
              }}</strong>
              项
              <template
                v-if="
                  activeDocType === 'specimen_barcode' &&
                  selectedRows.length > 0 &&
                  selectedSpecimenItemCount !== selectedRows.length
                "
              >
                （合管后 {{ selectedRows.length }} 张）
              </template>
            </span>
            <span class="print-center-footer__sep" aria-hidden="true">·</span>
            <span>
              预估
              <strong>{{ estimatedPages }}</strong>
              {{ activeDocType === 'specimen_barcode' ? '张' : '页' }}
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
            <template v-if="printerSelectHighlight && !resolveSelectedPrinter(activeDocType)">
              <span class="print-center-footer__sep" aria-hidden="true">·</span>
              <span class="print-center-footer__printer-warn">请先选择打印机</span>
            </template>
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
          <div>
            打印机：
            <strong>{{ resolveSelectedPrinter(activeDocType) || '未选择' }}</strong>
          </div>
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
            <div v-if="row.therapyTypeText || row.subtitle || row.timeText" class="print-center-confirm__sub">
              {{ [row.therapyTypeText, row.subtitle, row.timeText].filter(Boolean).join(' · ') }}
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
