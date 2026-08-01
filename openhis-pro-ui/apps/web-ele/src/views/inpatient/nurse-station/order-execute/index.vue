<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type {
  NurseStationOrderExecutePlanSlot,
  NurseStationOrderExecuteProjectType,
  NurseStationOrderExecuteTableRow,
} from './services/map-nurse-station-order-execute';

import type { NurseStationOrderExecuteTab } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.types';

import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElMessage, ElMessageBox } from 'element-plus';

import { httpCancelNurseStationOrderExecutions } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-cancel-execute.http';
import {
  httpExecuteNurseStationOrders,
  httpListNurseStationOrderExecute,
  httpSkipNurseStationOrderSlots,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.http';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE,
  isEncounterClinicalOrderMutationBlocked,
} from '#/utils/inpatient/encounter-clinical-order-gate';
import {
  validateExecuteActualNotBeforeOpened,
  validateNurseExecuteActualTimeBounds,
} from '#/utils/inpatient/encounter-order-time-bounds';
import {
  adviceTherapyTypeClass,
  getAdviceStatusTagType,
} from '#/views/inpatient/doctor-station/advice/services/advice-list';
import { printInfusionLabel } from '#/utils/print/print-nurse-station';
import { print, PRINT_SCENARIO } from '#/utils/print/runtime';

import NurseStationPatientOrderCollapseTitle from '../components/nurse-station-patient-order-collapse-title.vue';
import { nurseStationPatientListPanelContextKey } from '../components/patient-list-panel.context';
import OrderExecutePrintDrawer from './components/order-execute-print-drawer.vue';
import {
  applyLongTermBindQtyMemoryToIndex,
  filterExecuteIndexByProjectTypes,
  indexExecuteGroupsByEncounterId,
  mapSelectedSlotsToCancelProcedureIds,
  mapSelectedSlotsToSkipItems,
  mapSelectedSlotsToWriteItems,
  mergeExecutePatientGroups,
  NURSE_STATION_ORDER_EXECUTE_PROJECT_TYPES,
  resolveExecuteActualTime,
  resolveExecuteBindItemDisplayText,
  resolveExecuteBindItemKey,
  resolveExecuteBindItemUnitLabel,
  resolveExecuteBindQuantityValue,
  resolveExecuteScheduleColumnLabel,
  writeLongTermBindQtyMemory,
} from './services/map-nurse-station-order-execute';
import {
  buildExamApplyPrintSourceFromExecuteRow,
  buildInfusionPrintSourceFromExecuteRow,
  buildLabApplyPrintSourceFromExecuteRow,
  buildOralMedLabelPrintSourceFromExecuteRow,
  resolveExecuteRowLabelPrintQueryDate,
  resolveExecuteRowPrintDocType,
} from './services/resolve-order-execute-label-print';

type ExecuteTab = NurseStationOrderExecuteTab;
type TimeTypeFilter = 'all' | 'long' | 'temp';
/** 右键行打印类型文案 */
type OrderRowPrintDocType = NonNullable<ReturnType<typeof resolveExecuteRowPrintDocType>>;

defineOptions({
  name: 'InpatientNurseStationOrderExecuteModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

/** 取消执行写路径默认原因（与门诊护士站一致，前端不弹窗收集） */
const DEFAULT_CANCEL_STATUS_REASON = '护士站取消执行';

interface ExecutePatientCard {
  patient: NurseStationModulePatient;
  orders: NurseStationOrderExecuteTableRow[];
}

interface Props {
  patients?: NurseStationModulePatient[];
}

const WRITE_BATCH_LIMIT = 50;

/** 项目类型多选选项（value=code，label 仅展示） */
const projectTypeOptions: Array<{ label: string; value: NurseStationOrderExecuteProjectType }> = [
  { label: '西药', value: 'west' },
  { label: '中药', value: 'tcm' },
  { label: '检验', value: 'lab' },
  { label: '检查', value: 'exam' },
  { label: '治疗', value: 'treatment' },
  { label: '嘱托', value: 'entrust' },
];

const listPanelContext = inject(nurseStationPatientListPanelContextKey, null);

const statusTabs: Array<{ label: string; value: ExecuteTab }> = [
  { label: '待执行', value: 'pending' },
  { label: '已执行', value: 'executed' },
  { label: '不执行', value: 'skipped' },
  { label: '取消执行', value: 'cancelled' },
];

const activeStatus = ref<ExecuteTab>('pending');
/** 项目类型多选；默认不选（空选按全选拉取并过滤） */
const selectedProjectTypes = ref<NurseStationOrderExecuteProjectType[]>([]);
const activeTimeType = ref<TimeTypeFilter>('all');
/** 列表拉取 loading（可并行 west/tcm 两次请求） */
const listFetching = ref(false);
/** 槽位勾选态（按 slotKey 存，避免每次勾选克隆 Set 触发整表重渲染） */
const slotSelectionByKey = ref<Record<string, boolean>>({});
/** 右键改时覆盖：按 slotKey 存待执行 HH:mm（仅改时分，日期跟计划日） */
const slotAdjustHHmmByKey = ref<Record<string, string>>({});
/** 就地改时编辑态：按 slotKey 标记正在编辑的槽位 */
const slotTimeEditingByKey = ref<Record<string, boolean>>({});
/** 就地 TimePicker 实例（用于「修改时间」后自动展开面板） */
const slotTimePickerInstByKey = new Map<string, { handleOpen?: () => void }>();
const ordersByEncounterId = ref<Map<string, NurseStationOrderExecuteTableRow[]>>(new Map());
const executeDeadline = ref(dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss'));

/** 槽位右键菜单（跟随鼠标定位） */
const slotContextMenuVisible = ref(false);
const slotContextMenuX = ref(0);
const slotContextMenuY = ref(0);
const slotContextMenuSlot = ref<null | NurseStationOrderExecutePlanSlot>(null);
/** 右键所在医嘱（「当前医嘱已选」操作作用域） */
const slotContextMenuRow = ref<null | NurseStationOrderExecuteTableRow>(null);
/** 是否点在具体时点 Checkbox 上打开（决定是否显示单槽操作项） */
const slotContextMenuFromSlot = ref(false);
/** 右键打开节流：避免连点反复弹菜单 */
const SLOT_CONTEXT_MENU_THROTTLE_MS = 300;
let slotContextMenuOpenAt = 0;

const executeRequest = useRequest(httpExecuteNurseStationOrders, {
  manual: true,
  refreshOnStale: () => loadExecuteList(),
});
const skipRequest = useRequest(httpSkipNurseStationOrderSlots, {
  manual: true,
  refreshOnStale: () => loadExecuteList(),
});
const cancelExecuteRequest = useRequest(httpCancelNurseStationOrderExecutions, {
  manual: true,
  refreshOnStale: () => loadExecuteList(),
});

const encounterIds = computed(() =>
  props.patients.map((p) => String(p.id).trim()).filter((id) => id.length > 0),
);

/** 就诊 id → 住院账户 id（来自左侧患者队列） */
const accountIdByEncounterId = computed(() => {
  const map = new Map<string, string>();
  for (const patient of props.patients) {
    const encounterId = String(patient.id).trim();
    const accountId = preserveApiIdForRequest(patient.accountId);
    if (encounterId && accountId) {
      map.set(encounterId, accountId);
    }
  }
  return map;
});

/** 选中患者处于出院结算轨（待结算/已结算/已离院）：禁止执行/不执行/取消执行 */
const hasEncounterClinicalOrderBlocked = computed(() =>
  props.patients.some((p) => isEncounterClinicalOrderMutationBlocked(p.statusEnum)),
);

/** 待执行 / 取消执行 Tab：批量正向执行 */
const executeActionsEnabled = computed(
  () =>
    !hasEncounterClinicalOrderBlocked.value &&
    (activeStatus.value === 'pending' || activeStatus.value === 'cancelled'),
);

/** 待执行 Tab：批量不执行 */
const skipActionsEnabled = computed(
  () => !hasEncounterClinicalOrderBlocked.value && activeStatus.value === 'pending',
);

/** 已执行 Tab：批量取消执行 */
const cancelActionsEnabled = computed(
  () => !hasEncounterClinicalOrderBlocked.value && activeStatus.value === 'executed',
);

/** 待执行 / 已执行 / 取消执行 Tab 允许勾选槽位 */
const slotSelectionEnabled = computed(
  () => executeActionsEnabled.value || skipActionsEnabled.value || cancelActionsEnabled.value,
);

const visibleCards = computed<ExecutePatientCard[]>(() => {
  const index = ordersByEncounterId.value;
  return props.patients.map((patient) => {
    const encounterId = String(patient.id).trim();
    return {
      patient,
      orders: index.get(encounterId) ?? [],
    };
  });
});

const cardCountText = computed(() => `${visibleCards.value.length}位患者`);
const expandedPatientIds = ref<string[]>([]);

/** 时间列标题：待执行/不执行/取消=计划时间；已执行=执行时间 */
const scheduleColumnLabel = computed(() => resolveExecuteScheduleColumnLabel(activeStatus.value));

/** 当前 Tab 下全部可勾选槽位（用于全选） */
const allVisiblePlanSlots = computed(() => {
  const slots: NurseStationOrderExecutePlanSlot[] = [];
  for (const card of visibleCards.value) {
    for (const row of card.orders) {
      for (const schedule of row.planSchedules) {
        slots.push(...schedule.slots);
      }
    }
  }
  return slots;
});

/** 已勾选槽位数（供全选态与批量写路径复用） */
const selectedSlotCount = computed(() => {
  let count = 0;
  const selection = slotSelectionByKey.value;
  for (const key in selection) {
    if (selection[key]) {
      count += 1;
    }
  }
  return count;
});

/** 全选开关：当前 Tab 下可操作槽位已全部勾选 */
const selectedAll = computed(() => {
  const total = allVisiblePlanSlots.value.length;
  return total > 0 && selectedSlotCount.value === total;
});

/** 写路径过滤用：已勾选 slotKey 集合 */
function buildSelectedSlotKeySet(): Set<string> {
  const keys = new Set<string>();
  const selection = slotSelectionByKey.value;
  for (const key in selection) {
    if (selection[key]) {
      keys.add(key);
    }
  }
  return keys;
}

/** 清空槽位勾选、改时覆盖与就地编辑态 */
function clearSlotSelection() {
  slotSelectionByKey.value = {};
  slotAdjustHHmmByKey.value = {};
  slotTimeEditingByKey.value = {};
  slotTimePickerInstByKey.clear();
}

/** 勾选当前 Tab 下全部可操作时点 */
function selectAllVisiblePlanSlots() {
  if (!slotSelectionEnabled.value) {
    clearSlotSelection();
    return;
  }
  const next: Record<string, boolean> = {};
  for (const slot of allVisiblePlanSlots.value) {
    next[slot.slotKey] = true;
  }
  slotSelectionByKey.value = next;
}

/** 全选开关：选中或清空当前列表全部可操作时点 */
function handleSelectedAllChange(on: boolean) {
  if (!on) {
    clearSlotSelection();
    return;
  }
  selectAllVisiblePlanSlots();
}

watch(
  visibleCards,
  (cards) => {
    expandedPatientIds.value = cards.map((item) => item.patient.id);
  },
  { immediate: true },
);

watch(
  [encounterIds, activeStatus, selectedProjectTypes, activeTimeType, executeDeadline],
  () => {
    void loadExecuteList();
  },
  { immediate: true },
);

/** 主文案：优先医嘱名称摘要 */
function executeOrderDisplayedContent(item: NurseStationOrderExecuteTableRow): string {
  const c = item.content?.trim();
  if (c && c.length > 0) return item.content;
  return item.typeLabel;
}

/** 医嘱状态 Tag 颜色（已停嘱用红色） */
function getExecuteStatusTagType(row: NurseStationOrderExecuteTableRow) {
  if (row.halted) return 'danger';
  return getAdviceStatusTagType({
    statusEnum: row.statusEnum,
    status: row.statusDisplay,
  });
}

/** 是否药嘱行（绑定项下沉到用法频次下方） */
function isExecuteMedicineOrder(row: NurseStationOrderExecuteTableRow): boolean {
  return row.orderType === 'MED_REQUEST';
}

/** 待执行 Tab 允许护士手改绑定项数量 */
const bindQuantityEditable = computed(() => executeActionsEnabled.value);

/** 回写绑定项数量到列表行（执行写路径会原样携带 bindItems） */
function updateBindItemQuantity(
  encounterId: string,
  row: NurseStationOrderExecuteTableRow,
  bindIndex: number,
  value: number | undefined,
) {
  // 就诊键与列表索引同源（优先行上 encounterId，避免 patient.id 形态不一致导致静默失败）
  const encounterKey = String(row.encounterId ?? '').trim() || String(encounterId ?? '').trim();
  const rows = ordersByEncounterId.value.get(encounterKey);
  if (!rows) return;
  const rowIndex = rows.findIndex((item) => item.id === row.id);
  if (rowIndex === -1) return;
  const currentRow = rows[rowIndex];
  const bindItems = currentRow?.bindItems;
  if (!bindItems?.[bindIndex]) return;
  // 允许 0：本趟不带出该绑定项；清空输入按 0 处理
  const nextValue =
    value === null || value === undefined || Number.isNaN(value)
      ? 0
      : Math.max(0, Math.floor(value));
  // 不可变替换 bindItems，保证 ElTable 行与受控 InputNumber 同步刷新
  const nextBindItems = bindItems.map((bind, index) => {
    if (index !== bindIndex) {
      return bind;
    }
    return {
      ...bind,
      quantityNumerator: nextValue,
      // 手改按整数次/个计，分母归一，避免模板分数口径干扰写路径
      quantityDenominator: 1,
    };
  });
  rows[rowIndex] = {
    ...currentRow,
    bindItems: nextBindItems,
  };
  ordersByEncounterId.value = new Map(ordersByEncounterId.value);
  // 长期医嘱：手改带出量写入本地记忆，下次加载/执行仍回显该数量
  if (
    String(row.therapyTypeCode ?? '')
      .trim()
      .toUpperCase() === 'LONG_TERM'
  ) {
    const updatedBind = nextBindItems[bindIndex];
    if (updatedBind) {
      writeLongTermBindQtyMemory(row.orderId, updatedBind, nextValue);
    }
  }
}

/** 切换槽位勾选（按 key 原地更新，仅刷新对应勾选框） */
function toggleSlotSelection(slot: NurseStationOrderExecutePlanSlot, checked: boolean) {
  if (checked) {
    slotSelectionByKey.value[slot.slotKey] = true;
    return;
  }
  delete slotSelectionByKey.value[slot.slotKey];
}

/** 解析槽位当前展示/改时用的 HH:mm（右键覆盖 > 标签时刻 > 建议实际 > 计划） */
function resolveSlotCurrentHHmm(slot: NurseStationOrderExecutePlanSlot): string {
  const override = String(slotAdjustHHmmByKey.value[slot.slotKey] ?? '').trim();
  if (override) {
    return override;
  }
  // timeLabel 可能带操作人名，取首段 HH:mm
  const fromLabel = String(slot.timeLabel ?? '')
    .trim()
    .split(/\s+/)[0];
  if (fromLabel && /^\d{1,2}:\d{2}$/.test(fromLabel)) {
    return fromLabel.length === 4 ? `0${fromLabel}` : fromLabel;
  }
  const fromDefault = String(slot.defaultActualTime ?? '').trim();
  if (fromDefault) {
    const parsed = dayjs(fromDefault);
    if (parsed.isValid()) {
      return parsed.format('HH:mm');
    }
  }
  const fromPlan = dayjs(slot.planTime);
  return fromPlan.isValid() ? fromPlan.format('HH:mm') : '08:00';
}

/** checkbox 文案：有右键改时时显示新时分，否则用列表 timeLabel */
function resolveSlotCheckboxLabel(slot: NurseStationOrderExecutePlanSlot): string {
  const override = String(slotAdjustHHmmByKey.value[slot.slotKey] ?? '').trim();
  if (!override) {
    return slot.timeLabel;
  }
  const operator = String(slot.operatorName ?? '').trim();
  return operator ? `${override} ${operator}` : override;
}

/** 关闭医嘱行/时点右键菜单 */
function closeSlotContextMenu() {
  slotContextMenuVisible.value = false;
  slotContextMenuSlot.value = null;
  slotContextMenuRow.value = null;
  slotContextMenuFromSlot.value = false;
}

/** 当前右键行可打的打印类型（贴签 / 检查单 / 检验单） */
const contextMenuRowPrintDocType = computed(() => {
  const row = slotContextMenuRow.value;
  if (!row) {
    return null;
  }
  return resolveExecuteRowPrintDocType(row);
});

/** 右键打印菜单文案 */
const contextMenuRowPrintLabel = computed(() => {
  const docType = contextMenuRowPrintDocType.value;
  if (docType === 'infusion_label') {
    return '打印输液贴';
  }
  if (docType === 'oral_med_label') {
    return '打印口服药贴';
  }
  if (docType === 'exam_apply') {
    return '打印检查单';
  }
  if (docType === 'lab_apply') {
    return '打印检验单';
  }
  return '';
});

/** 定位并打开右键菜单（共用坐标与节流） */
function positionAndShowContextMenu(event: MouseEvent) {
  const now = Date.now();
  if (now - slotContextMenuOpenAt < SLOT_CONTEXT_MENU_THROTTLE_MS) {
    return false;
  }
  slotContextMenuOpenAt = now;
  const menuWidth = 200;
  const menuHeight = 320;
  const pad = 8;
  const maxX = window.innerWidth - menuWidth - pad;
  const maxY = window.innerHeight - menuHeight - pad;
  slotContextMenuX.value = Math.max(pad, Math.min(event.clientX, maxX));
  slotContextMenuY.value = Math.max(pad, Math.min(event.clientY, maxY));
  slotContextMenuVisible.value = true;
  return true;
}

/** 打开时点右键菜单（点在计划时点 Checkbox 上；含单槽执行/改时） */
function openSlotContextMenu(
  event: MouseEvent,
  slot: NurseStationOrderExecutePlanSlot,
  row: NurseStationOrderExecuteTableRow,
) {
  event.preventDefault();
  event.stopPropagation();
  // 就地改时中的时点不弹菜单
  if (slotTimeEditingByKey.value[slot.slotKey]) {
    return;
  }
  if (!positionAndShowContextMenu(event)) {
    return;
  }
  slotContextMenuFromSlot.value = true;
  slotContextMenuSlot.value = slot;
  slotContextMenuRow.value = row;
}

/** 打开医嘱行右键菜单（非整点；不展示单槽「修改时间/执行/不执行/取消执行」） */
function openRowContextMenu(event: MouseEvent, row: NurseStationOrderExecuteTableRow) {
  event.preventDefault();
  event.stopPropagation();
  const target = event.target;
  // 点在时点 Checkbox：由槽位菜单处理，避免行菜单抢先
  if (target instanceof Element && target.closest('.el-checkbox')) {
    return;
  }
  // 就地改时 TimePicker 上不弹行菜单
  if (
    target instanceof Element &&
    target.closest('.execute-slot-time-edit-wrap, .el-time-panel, .el-picker__popper')
  ) {
    return;
  }
  if (!positionAndShowContextMenu(event)) {
    return;
  }
  slotContextMenuFromSlot.value = false;
  slotContextMenuSlot.value = null;
  slotContextMenuRow.value = row;
}

/** 收集「当前医嘱」内已勾选的计划时点 */
function collectSelectedSlotKeysOnRow(row: NurseStationOrderExecuteTableRow): Set<string> {
  const selected = buildSelectedSlotKeySet();
  const keys = new Set<string>();
  for (const schedule of row.planSchedules) {
    for (const slot of schedule.slots) {
      if (selected.has(slot.slotKey)) {
        keys.add(slot.slotKey);
      }
    }
  }
  return keys;
}

/** 收集「当前医嘱」内已勾选的槽位对象 */
function collectSelectedSlotsOnRow(
  row: NurseStationOrderExecuteTableRow,
): NurseStationOrderExecutePlanSlot[] {
  const selected = buildSelectedSlotKeySet();
  const slots: NurseStationOrderExecutePlanSlot[] = [];
  for (const schedule of row.planSchedules) {
    for (const slot of schedule.slots) {
      if (selected.has(slot.slotKey)) {
        slots.push(slot);
      }
    }
  }
  return slots;
}

/** 规范化 HH:mm；无效则返回空串 */
function normalizeSlotHHmm(raw: string): string {
  const hhmm = String(raw ?? '').trim();
  if (!/^\d{1,2}:\d{2}$/.test(hhmm)) {
    return '';
  }
  const parsed = dayjs(`2000-01-01 ${hhmm}`);
  return parsed.isValid() ? parsed.format('HH:mm') : '';
}

/** 写入单槽改时覆盖 */
function applySlotAdjustHHmm(slotKey: string, hhmm: string) {
  const normalized = normalizeSlotHHmm(hhmm);
  if (!normalized) {
    return;
  }
  slotAdjustHHmmByKey.value = {
    ...slotAdjustHHmmByKey.value,
    [slotKey]: normalized,
  };
}

/** 按 encounterId 找模块患者（取入院/出院时间窗） */
function findModulePatientByEncounterId(
  encounterId: string,
): NurseStationModulePatient | undefined {
  const id = String(encounterId ?? '').trim();
  if (!id) {
    return undefined;
  }
  return props.patients.find((p) => String(p.id).trim() === id);
}

/** 按 slotKey 反查所属医嘱行 */
function findExecuteRowBySlotKey(slotKey: string): NurseStationOrderExecuteTableRow | undefined {
  const key = String(slotKey ?? '').trim();
  if (!key) {
    return undefined;
  }
  for (const rows of ordersByEncounterId.value.values()) {
    for (const row of rows) {
      for (const schedule of row.planSchedules) {
        if (schedule.slots.some((slot) => slot.slotKey === key)) {
          return row;
        }
      }
    }
  }
  return undefined;
}

/**
 * 校验单槽改时后的实际时刻是否落在入院~出院、开立~停嘱窗内。
 * @returns 违规文案；通过返回 null
 */
function validateSlotAdjustAgainstEncounterBounds(
  slot: NurseStationOrderExecutePlanSlot,
  hhmm: string,
): null | string {
  const row = findExecuteRowBySlotKey(slot.slotKey) ?? slotContextMenuRow.value;
  const patient = row ? findModulePatientByEncounterId(String(row.encounterId ?? '')) : undefined;
  const actualTime = resolveExecuteActualTime(slot.planTime, hhmm, slot.defaultActualTime);
  return validateNurseExecuteActualTimeBounds(
    actualTime,
    patient?.admissionAt ?? patient?.inWardAt,
    patient?.dischargeAt,
    row?.reqOpenedTime,
    row?.stopTime,
  );
}

/** 结束指定槽位的就地改时 */
function endSlotTimeEditing(slotKey: string) {
  if (!slotTimeEditingByKey.value[slotKey]) {
    return;
  }
  const next = { ...slotTimeEditingByKey.value };
  delete next[slotKey];
  slotTimeEditingByKey.value = next;
}

/** 结束全部就地改时 */
function clearSlotTimeEditing() {
  slotTimeEditingByKey.value = {};
}

/** 指定槽位进入就地改时（无弹窗） */
function enterSlotTimeEditing(slots: NurseStationOrderExecutePlanSlot[]) {
  if (slots.length === 0) {
    return;
  }
  const editing: Record<string, boolean> = { ...slotTimeEditingByKey.value };
  const adjustNext = { ...slotAdjustHHmmByKey.value };
  for (const slot of slots) {
    editing[slot.slotKey] = true;
    if (!adjustNext[slot.slotKey]) {
      adjustNext[slot.slotKey] = resolveSlotCurrentHHmm(slot);
    }
  }
  slotAdjustHHmmByKey.value = adjustNext;
  slotTimeEditingByKey.value = editing;
}

/** 绑定就地 TimePicker 实例（卸载时清理） */
function bindSlotTimePickerRef(slotKey: string, el: unknown) {
  if (el) {
    slotTimePickerInstByKey.set(slotKey, el as { handleOpen?: () => void });
    return;
  }
  slotTimePickerInstByKey.delete(slotKey);
}

/** 右键「修改时间」：仅当前槽就地编辑，并自动展开时分面板 */
async function handleContextMenuAdjustCurrentTime() {
  if (!executeActionsEnabled.value) {
    return;
  }
  const slot = slotContextMenuSlot.value;
  closeSlotContextMenu();
  if (!slot) {
    return;
  }
  enterSlotTimeEditing([slot]);
  await nextTick();
  slotTimePickerInstByKey.get(slot.slotKey)?.handleOpen?.();
}

/** 右键「修改当前医嘱已选时间」：仅当前医嘱内已勾选时点就地编辑 */
function handleContextMenuAdjustSelectedTime() {
  if (!executeActionsEnabled.value) {
    return;
  }
  const row = slotContextMenuRow.value;
  closeSlotContextMenu();
  if (!row) {
    return;
  }
  const slots = collectSelectedSlotsOnRow(row);
  if (slots.length === 0) {
    ElMessage.warning('当前医嘱没有勾选的计划时点');
    return;
  }
  enterSlotTimeEditing(slots);
}

/** 就地 TimePicker 变更：校验入院/出院窗后同步覆盖值 */
function handleSlotInlineTimeChange(slot: NurseStationOrderExecutePlanSlot, value: null | string) {
  if (!value) {
    return;
  }
  const boundErr = validateSlotAdjustAgainstEncounterBounds(slot, value);
  if (boundErr) {
    ElMessage.warning(boundErr);
    endSlotTimeEditing(slot.slotKey);
    return;
  }
  applySlotAdjustHHmm(slot.slotKey, value);
}

/** TimePicker 面板关闭后退出该槽编辑态 */
function handleSlotTimePickerVisible(slot: NurseStationOrderExecutePlanSlot, open: boolean) {
  if (open) {
    return;
  }
  endSlotTimeEditing(slot.slotKey);
}

/** 右键「执行」：仅当前槽 */
function handleContextMenuExecuteCurrent() {
  if (!executeActionsEnabled.value) {
    return;
  }
  const slot = slotContextMenuSlot.value;
  closeSlotContextMenu();
  if (!slot) {
    return;
  }
  void runExecuteOrders(new Set([slot.slotKey]));
}

/** 右键「不执行」：仅当前槽 */
function handleContextMenuSkipCurrent() {
  if (!skipActionsEnabled.value) {
    return;
  }
  const slot = slotContextMenuSlot.value;
  closeSlotContextMenu();
  if (!slot) {
    return;
  }
  void runSkipOrders(new Set([slot.slotKey]));
}

/** 右键「取消执行」：仅当前槽 */
function handleContextMenuCancelCurrent() {
  if (!cancelActionsEnabled.value) {
    return;
  }
  const slot = slotContextMenuSlot.value;
  closeSlotContextMenu();
  if (!slot) {
    return;
  }
  void runCancelExecute(new Set([slot.slotKey]));
}

/** 右键「执行当前医嘱已选时点」：仅当前医嘱内已勾选时点 */
function handleContextMenuExecuteSelected() {
  if (!executeActionsEnabled.value) {
    return;
  }
  const row = slotContextMenuRow.value;
  closeSlotContextMenu();
  if (!row) {
    return;
  }
  const keys = collectSelectedSlotKeysOnRow(row);
  if (keys.size === 0) {
    ElMessage.warning('当前医嘱没有勾选的计划时点');
    return;
  }
  void runExecuteOrders(keys);
}

/** 收集当前医嘱内全部可勾选槽位 key */
function collectOperableSlotKeysOnRow(row: NurseStationOrderExecuteTableRow): string[] {
  const keys: string[] = [];
  for (const schedule of row.planSchedules) {
    for (const slot of schedule.slots) {
      keys.push(slot.slotKey);
    }
  }
  return keys;
}

/** 右键「全选当前医嘱时点」：勾选本行全部可操作计划点 */
function handleContextMenuSelectAllOnRow() {
  const row = slotContextMenuRow.value;
  closeSlotContextMenu();
  if (!row) {
    return;
  }
  const next = { ...slotSelectionByKey.value };
  for (const key of collectOperableSlotKeysOnRow(row)) {
    next[key] = true;
  }
  slotSelectionByKey.value = next;
}

/** 右键「取消全选当前医嘱时点」：清空本行勾选 */
function handleContextMenuDeselectAllOnRow() {
  const row = slotContextMenuRow.value;
  closeSlotContextMenu();
  if (!row) {
    return;
  }
  const next = { ...slotSelectionByKey.value };
  for (const key of collectOperableSlotKeysOnRow(row)) {
    delete next[key];
  }
  slotSelectionByKey.value = next;
}

/** 全局点击/滚动/Esc 关闭右键菜单；Esc 同时退出就地改时 */
function handleGlobalCloseSlotContextMenu(event: Event) {
  if (event instanceof KeyboardEvent && event.key === 'Escape') {
    if (slotContextMenuVisible.value) {
      closeSlotContextMenu();
    }
    if (Object.keys(slotTimeEditingByKey.value).length > 0) {
      clearSlotTimeEditing();
    }
    return;
  }
  if (!slotContextMenuVisible.value) {
    return;
  }
  // 菜单内部点击不关，留给菜单项自己处理
  if (event instanceof MouseEvent) {
    const target = event.target;
    if (target instanceof Element && target.closest('.execute-slot-context-menu')) {
      return;
    }
  }
  closeSlotContextMenu();
}

/** 收集当前列表全部表格行 */
function collectAllTableRows(): NurseStationOrderExecuteTableRow[] {
  const rows: NurseStationOrderExecuteTableRow[] = [];
  for (const card of visibleCards.value) {
    rows.push(...card.orders);
  }
  return rows;
}

/** 按当前患者与筛选条件加载医嘱执行列表 */
async function loadExecuteList() {
  clearSlotSelection();
  ordersByEncounterId.value = new Map();

  if (encounterIds.value.length === 0) {
    return;
  }

  const deadline = String(executeDeadline.value ?? '').trim();
  if (!deadline) {
    ElMessage.warning('请选择截止时间');
    return;
  }

  // 空选按全选；决定需打 west（西药+诊疗）/ tcm（中药方）哪个后端 Tab
  const selected =
    selectedProjectTypes.value.length > 0
      ? selectedProjectTypes.value
      : NURSE_STATION_ORDER_EXECUTE_PROJECT_TYPES;
  const needTcm = selected.includes('tcm');
  const needWest = selected.some((t) => t !== 'tcm');

  const base = {
    encounterIds: encounterIds.value,
    executeTab: activeStatus.value,
    therapyType: activeTimeType.value,
    executionDeadline: deadline,
  };

  listFetching.value = true;
  try {
    const tasks: Array<ReturnType<typeof httpListNurseStationOrderExecute>> = [];
    if (needWest) {
      tasks.push(httpListNurseStationOrderExecute({ ...base, medicineCategoryTab: 'west' }));
    }
    if (needTcm) {
      tasks.push(httpListNurseStationOrderExecute({ ...base, medicineCategoryTab: 'tcm' }));
    }
    if (tasks.length === 0) {
      return;
    }
    const results = await Promise.all(tasks);
    const mergedGroups = mergeExecutePatientGroups(results.map((res) => res.groups));
    const indexed = indexExecuteGroupsByEncounterId(mergedGroups, activeStatus.value);
    // 长期带出量：本地记忆覆盖模板（与后端上次执行回显互补）
    const withMemory = applyLongTermBindQtyMemoryToIndex(indexed);
    ordersByEncounterId.value = filterExecuteIndexByProjectTypes(withMemory, selected);
    // 列表加载后默认全选当前 Tab 可操作时点（护士可再取消个别点）
    selectAllVisiblePlanSlots();
  } finally {
    listFetching.value = false;
  }
}

/** 工具栏「查询」：显式刷新列表 */
function handleRunFilterQuery() {
  void loadExecuteList();
}

/** 右键单行贴签打印中 */
const orderPrinting = ref(false);
/** 工具栏打印抽屉 */
const printDrawerVisible = ref(false);

/** 右键打印文案 */
function resolveOrderPrintLabel(docType: OrderRowPrintDocType): string {
  if (docType === 'infusion_label') {
    return '输液贴';
  }
  if (docType === 'oral_med_label') {
    return '口服药贴';
  }
  if (docType === 'exam_apply') {
    return '检查单';
  }
  return '检验单';
}

/** 打开打印抽屉（选单据 + 勾选待打项后再确认） */
function handleOpenPrintDrawer() {
  if (props.patients.length < 1) {
    ElMessage.warning('请先勾选患者');
    return;
  }
  printDrawerVisible.value = true;
}

/** 右键打印当前行：贴签 / 检查单 / 检验单（直接用列表已加载数据） */
async function handleContextMenuPrintLabel() {
  const row = slotContextMenuRow.value;
  const docType = row ? resolveExecuteRowPrintDocType(row) : null;
  closeSlotContextMenu();
  if (!row || !docType) {
    return;
  }
  if (orderPrinting.value) {
    return;
  }
  const encounterId = String(row.encounterId ?? '').trim();
  const patient = props.patients.find((p) => String(p.id).trim() === encounterId);
  if (!patient) {
    ElMessage.warning('未找到当前行对应患者');
    return;
  }
  const deadline = executeDeadline.value || dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss');
  const date = resolveExecuteRowLabelPrintQueryDate(row, deadline);
  const label = resolveOrderPrintLabel(docType);
  orderPrinting.value = true;
  try {
    if (docType === 'exam_apply') {
      const source = buildExamApplyPrintSourceFromExecuteRow(row, patient);
      if (!String(source.itemRows?.[0]?.itemName ?? '').trim()) {
        ElMessage.warning('当前医嘱暂无检查项目可打印');
        return;
      }
      const result = await print({
        scenarioCode: PRINT_SCENARIO.ipExamApplyPrint,
        source,
        jobName: `住院检查单·${patient.bedNo}${patient.name}`,
        bizRef: { encounterId, orderId: row.orderId },
      });
      if (result.ok) {
        ElMessage.success(`已发送打印：${label}，共 1 份`);
      }
      return;
    }

    if (docType === 'lab_apply') {
      const source = buildLabApplyPrintSourceFromExecuteRow(row, patient);
      if (!String(source.itemRows?.[0]?.itemName ?? '').trim()) {
        ElMessage.warning('当前医嘱暂无检验项目可打印');
        return;
      }
      const result = await print({
        scenarioCode: PRINT_SCENARIO.ipLabApplyPrint,
        source,
        jobName: `住院检验单·${patient.bedNo}${patient.name}`,
        bizRef: { encounterId, orderId: row.orderId },
      });
      if (result.ok) {
        ElMessage.success(`已发送打印：${label}，共 1 份`);
      }
      return;
    }

    if (docType === 'infusion_label') {
      const source = buildInfusionPrintSourceFromExecuteRow(row, patient, date);
      if ((source.medicationLines?.length ?? 0) <= 0) {
        ElMessage.warning('当前医嘱暂无输液药品可打印');
        return;
      }
      const result = await printInfusionLabel({
        encounterId,
        source,
        jobName: `输液贴·${patient.bedNo}${patient.name}`,
        scenarioCode: PRINT_SCENARIO.ipInfusionLabelPrint,
      });
      if (result.ok) {
        ElMessage.success(`已发送打印：${label}，共 ${result.count} 份`);
      }
      return;
    }

    const source = buildOralMedLabelPrintSourceFromExecuteRow(row, patient);
    if (!String(source.drugName ?? '').trim()) {
      ElMessage.warning('当前医嘱暂无口服药可打印');
      return;
    }
    const result = await print({
      scenarioCode: PRINT_SCENARIO.ipOralMedLabelPrint,
      source,
      jobName: `口服药贴·${patient.bedNo}${patient.name}`,
      bizRef: { encounterId, orderId: row.orderId },
    });
    if (result.ok) {
      ElMessage.success(`已发送打印：${label}，共 1 份`);
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : `打印${label}失败`;
    ElMessage.error(message);
  } finally {
    orderPrinting.value = false;
  }
}

/** 校验批量槽位数并提示 */
function assertBatchWithinLimit(count: number): boolean {
  if (count <= 0) {
    ElMessage.warning('请先勾选计划执行时点');
    return false;
  }
  if (count > WRITE_BATCH_LIMIT) {
    ElMessage.warning(`单次最多处理 ${WRITE_BATCH_LIMIT} 个计划点`);
    return false;
  }
  return true;
}

/** 写操作成功后刷新列表与左侧待执行计数 */
async function afterWriteSuccess(message: string) {
  ElMessage.success(message);
  await loadExecuteList();
  await listPanelContext?.refreshPatientFilters();
}

/** 按指定槽位集合执行（工具栏批量 / 右键单槽共用） */
async function runExecuteOrders(selectedKeys: Set<string>) {
  if (!executeActionsEnabled.value) return;
  const selectedCount = selectedKeys.size;
  if (!assertBatchWithinLimit(selectedCount)) return;

  try {
    await ElMessageBox.confirm(`确认执行已选 ${selectedCount} 个计划点？`, '执行医嘱', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  const tableRows = collectAllTableRows();
  const items = mapSelectedSlotsToWriteItems(
    tableRows,
    selectedKeys,
    slotAdjustHHmmByKey.value,
    true,
    accountIdByEncounterId.value,
    true,
  );
  if (items.length < selectedCount) {
    ElMessage.warning('部分患者缺少账户信息，请刷新患者列表后重试');
    return;
  }
  // 执行提交前再校一次改时结果，避免绕过就地校验
  const orderIdToEncounterId = new Map<string, string>();
  for (const row of tableRows) {
    const orderId = preserveApiIdForRequest(row.orderId);
    if (!orderId) {
      continue;
    }
    orderIdToEncounterId.set(orderId, String(row.encounterId ?? '').trim());
  }
  for (const item of items) {
    const orderId = preserveApiIdForRequest(item.orderId);
    const encounterId = orderId ? (orderIdToEncounterId.get(orderId) ?? '') : '';
    const patient = findModulePatientByEncounterId(encounterId);
    const row = tableRows.find((r) => preserveApiIdForRequest(r.orderId) === orderId);
    const boundErr = validateNurseExecuteActualTimeBounds(
      String(item.actualTime ?? ''),
      patient?.admissionAt ?? patient?.inWardAt,
      patient?.dischargeAt,
      row?.reqOpenedTime,
      row?.stopTime,
    );
    if (boundErr) {
      ElMessage.warning(boundErr);
      return;
    }
  }
  if (!assertBatchWithinLimit(items.length)) return;

  const res = await executeRequest.run({ items });
  const n = res.processedCount ?? items.length;
  await afterWriteSuccess(`已成功执行 ${n} 个计划点`);
  // BR-IP-EXE-034：长嘱皮试药跨批软提示（不阻断成功）
  if (res.warnings?.length) {
    await ElMessageBox.alert(res.warnings.join('\n'), '皮试批号提示', {
      type: 'warning',
      confirmButtonText: '知道了',
    });
  }
}

/** 批量执行医嘱：确认后按各槽计划/右键改时写入 actualTime */
async function handleExecuteOrders() {
  await runExecuteOrders(buildSelectedSlotKeySet());
}

/** 按指定槽位集合登记不执行 */
async function runSkipOrders(selectedKeys: Set<string>) {
  if (!skipActionsEnabled.value) return;
  const items = mapSelectedSlotsToSkipItems(collectAllTableRows(), selectedKeys);
  if (!assertBatchWithinLimit(items.length)) return;

  let statusReasonText: string | undefined;
  try {
    const { value } = await ElMessageBox.prompt(
      `确认将已选 ${items.length} 个计划点登记为不执行？请填写不执行原因：`,
      '不执行',
      {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '不执行原因',
        inputValidator: (val) => (val?.trim() ? true : '请填写不执行原因'),
      },
    );
    statusReasonText = value.trim();
  } catch {
    return;
  }

  const res = await skipRequest.run({ items, statusReasonText: statusReasonText ?? '' });
  const n = res.processedCount ?? items.length;
  await afterWriteSuccess(`已登记不执行 ${n} 个计划点`);
}

/** 批量登记不执行（需填写不执行原因） */
async function handleSkipOrders() {
  await runSkipOrders(buildSelectedSlotKeySet());
}

/** 按指定槽位集合取消执行 */
async function runCancelExecute(selectedKeys: Set<string>) {
  if (!cancelActionsEnabled.value) return;
  const selectedCount = selectedKeys.size;
  const { procedureIds, missingCount } = mapSelectedSlotsToCancelProcedureIds(
    collectAllTableRows(),
    selectedKeys,
  );
  if (missingCount > 0) {
    ElMessage.warning('部分计划点缺少执行记录，请刷新列表后重试');
    return;
  }
  if (!assertBatchWithinLimit(procedureIds.length)) return;
  if (procedureIds.length < selectedCount) {
    ElMessage.warning('部分计划点不可取消，请刷新后重试');
    return;
  }

  try {
    await ElMessageBox.confirm(
      `将取消已选 ${procedureIds.length} 个计划点；已发药将生成待退单，是否继续？`,
      '取消执行',
      {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }

  const res = await cancelExecuteRequest.run({
    encounterIds: encounterIds.value,
    procedureIds,
    statusReasonText: DEFAULT_CANCEL_STATUS_REASON,
  });
  const n = res.cancelledProcedureIds?.length ?? procedureIds.length;
  const summaryHint = (res.rebuiltSummaryNos?.length ?? 0) > 0 ? '，已自动重建汇总单' : '';
  await afterWriteSuccess(res.message?.trim() || `已成功取消 ${n} 个计划点${summaryHint}`);
}

/** 批量取消已执行计划点（确认提示，不弹窗填写原因） */
async function handleCancelExecute() {
  await runCancelExecute(buildSelectedSlotKeySet());
}

/**
 * 解析槽位用于「开立边界」校验的执行时刻：
 * 待执行优先右键改时/defaultActual；已执行优先 recorded。
 */
function resolveSlotCheckExecuteTime(slot: NurseStationOrderExecutePlanSlot): string {
  // 已执行 Tab：以 recorded 为准（无则回落 plan）
  if (activeStatus.value === 'executed') {
    const recorded = String(slot.recordedTime ?? '').trim();
    if (recorded) {
      return recorded;
    }
    return String(slot.planTime ?? '').trim();
  }
  // 待执行/不执行/取消：与写路径同口径（改时优先，否则 defaultActual/plan）
  const adjustHHmm = slotAdjustHHmmByKey.value[slot.slotKey];
  return resolveExecuteActualTime(slot.planTime, adjustHHmm, slot.defaultActualTime);
}

/**
 * 工具栏「校验执行时间」：扫选中患者当前列表全部医嘱槽位，
 * 排查执行时刻是否早于开立（理论上不应出现；缺开立字段跳过）。
 */
async function handleValidateExecuteTimesBeforeOpened() {
  if (props.patients.length < 1) {
    ElMessage.warning('请先选择患者');
    return;
  }
  // 违规清单：床号+姓名 + 医嘱内容 + 两时刻
  const violations: string[] = [];
  for (const card of visibleCards.value) {
    const patientLabel = `${card.patient.bedNo || '-'}床 ${card.patient.name || ''}`.trim();
    for (const row of card.orders) {
      const openedTime = String(row.reqOpenedTime ?? '').trim();
      // 无开立时间无法比对，与后端 Gate「缺字段不拦」同口径
      if (!openedTime) {
        continue;
      }
      for (const schedule of row.planSchedules) {
        for (const slot of schedule.slots) {
          const executeTime = resolveSlotCheckExecuteTime(slot);
          const err = validateExecuteActualNotBeforeOpened(executeTime, openedTime);
          if (!err) {
            continue;
          }
          const orderLabel = String(row.content ?? '').trim() || '医嘱';
          violations.push(
            `${patientLabel}｜${orderLabel}｜执行 ${executeTime}｜开立 ${openedTime}`,
          );
        }
      }
    }
  }
  if (violations.length === 0) {
    ElMessage.success('校验通过：所选患者医嘱执行时间均不早于开立时间');
    return;
  }
  const preview = violations.slice(0, 20).join('\n');
  const more = violations.length > 20 ? `\n…另有 ${violations.length - 20} 条未列出` : '';
  await ElMessageBox.alert(
    `发现 ${violations.length} 条执行时间早于开立时间：\n${preview}${more}`,
    '执行时间校验',
    {
      type: 'warning',
      confirmButtonText: '知道了',
    },
  );
}

onMounted(() => {
  // 右键菜单打开后：单击/滚轮/Esc 关闭
  document.addEventListener('mousedown', handleGlobalCloseSlotContextMenu, true);
  document.addEventListener('scroll', handleGlobalCloseSlotContextMenu, true);
  document.addEventListener('keydown', handleGlobalCloseSlotContextMenu, true);
});

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handleGlobalCloseSlotContextMenu, true);
  document.removeEventListener('scroll', handleGlobalCloseSlotContextMenu, true);
  document.removeEventListener('keydown', handleGlobalCloseSlotContextMenu, true);
});
</script>

<template>
  <div
    v-loading="listFetching || executeRequest.loading.value || skipRequest.loading.value"
    class="execute-module flex h-full min-h-0 w-full flex-1 flex-col overflow-hidden rounded-md bg-[#eef9fd]"
  >
    <ElAlert
      v-if="hasEncounterClinicalOrderBlocked"
      class="mx-2 mt-2 shrink-0"
      type="warning"
      :closable="false"
      show-icon
      :title="`${ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE}（不可执行、不执行或取消执行）`"
    />
    <!-- 第1行：状态导航 + 批量操作（主路径） -->
    <section
      class="execute-toolbar-main flex w-full flex-wrap items-center justify-between gap-x-3 gap-y-2 border-b border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)] px-2 py-1.5"
    >
      <ElRadioGroup v-model="activeStatus" class="execute-filter-segment shrink-0">
        <ElRadioButton v-for="status in statusTabs" :key="status.value" :label="status.value">
          {{ status.label }}
        </ElRadioButton>
      </ElRadioGroup>

      <div class="flex flex-wrap items-center gap-2">
        <span class="shrink-0 text-xs text-[var(--el-text-color-secondary)]">
          {{ cardCountText }}
        </span>
        <label
          v-if="slotSelectionEnabled"
          class="flex shrink-0 items-center gap-1 text-xs text-[var(--el-text-color-regular)]"
        >
          全选
          <ElSwitch
            :model-value="selectedAll"
            :disabled="allVisiblePlanSlots.length === 0"
            inline-prompt
            active-text=" "
            inactive-text=" "
            @update:model-value="handleSelectedAllChange"
          />
        </label>
        <span
          v-if="
            slotSelectionEnabled ||
            executeActionsEnabled ||
            skipActionsEnabled ||
            cancelActionsEnabled
          "
          class="execute-toolbar__divider"
          aria-hidden="true"
        ></span>
        <ElButton v-if="executeActionsEnabled" type="primary" @click="handleExecuteOrders">
          执行医嘱
        </ElButton>
        <ElButton v-if="skipActionsEnabled" type="warning" plain @click="handleSkipOrders">
          不执行
        </ElButton>
        <ElButton v-if="cancelActionsEnabled" type="danger" plain @click="handleCancelExecute">
          取消执行
        </ElButton>
        <ElButton plain :disabled="patients.length < 1" @click="handleOpenPrintDrawer">
          打印
        </ElButton>
        <ElButton
          plain
          :disabled="patients.length < 1"
          @click="handleValidateExecuteTimesBeforeOpened"
        >
          校验执行时间
        </ElButton>
      </div>
    </section>

    <!-- 第2行：条件筛选（次要，浅底） -->
    <section
      class="execute-toolbar-filter flex w-full flex-wrap items-center gap-x-3 gap-y-2 border-b border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-fill-color-blank)] px-2 py-1.5"
    >
      <span class="shrink-0 text-sm text-[var(--el-text-color-regular)]">项目类型</span>
      <ElCheckboxGroup
        v-model="selectedProjectTypes"
        class="execute-project-type-checkboxes inline-flex min-w-0 flex-wrap items-center gap-x-3"
      >
        <ElCheckbox
          v-for="opt in projectTypeOptions"
          :key="opt.value"
          :value="opt.value"
          :label="opt.value"
        >
          {{ opt.label }}
        </ElCheckbox>
      </ElCheckboxGroup>
      <span class="execute-toolbar__divider" aria-hidden="true"></span>
      <ElRadioGroup
        v-model="activeTimeType"
        class="execute-time-type-radio inline-flex shrink-0 flex-wrap items-center gap-3"
      >
        <ElRadio label="all">全部</ElRadio>
        <ElRadio label="long">长期</ElRadio>
        <ElRadio label="temp">临时</ElRadio>
      </ElRadioGroup>
      <span class="execute-toolbar__divider" aria-hidden="true"></span>
      <span class="shrink-0 text-sm text-[var(--el-text-color-regular)]">截止时间</span>
      <ElDatePicker
        v-model="executeDeadline"
        type="datetime"
        value-format="YYYY-MM-DD HH:mm:ss"
        format="YYYY-MM-DD HH:mm:ss"
        placeholder="请选择截止时间"
        class="execute-datetime"
      />
      <ElButton plain @click="handleRunFilterQuery">查询</ElButton>
    </section>

    <section v-if="visibleCards.length > 0" class="min-h-0 flex-1 overflow-auto p-2.5">
      <ElCollapse v-model="expandedPatientIds" class="patient-collapse">
        <ElCollapseItem
          v-for="card in visibleCards"
          :key="card.patient.id"
          :name="card.patient.id"
          class="patient-order-card mb-2.5 overflow-hidden rounded-lg border border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)] last:mb-0"
        >
          <template #title>
            <NurseStationPatientOrderCollapseTitle :patient="card.patient" />
          </template>

          <div class="card-table-wrap">
            <ElTable
              v-if="card.orders.length > 0"
              :data="card.orders"
              class="order-table order-table--horizontal-only order-execute-table"
              row-key="id"
              @row-contextmenu="
                (row: NurseStationOrderExecuteTableRow, _column: unknown, event: Event) =>
                  openRowContextMenu(event as MouseEvent, row)
              "
            >
              <ElTableColumn align="center" label="状态" width="96">
                <template #default="{ row }">
                  <div>
                    <ElTag
                      v-if="row.statusDisplay && row.statusDisplay !== '—'"
                      size="small"
                      effect="light"
                      :type="getExecuteStatusTagType(row)"
                    >
                      {{ row.statusDisplay }}
                    </ElTag>
                    <span v-else class="text-[var(--el-text-color-secondary)]">—</span>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn align="center" label="类型" min-width="108">
                <template #default="{ row }">
                  <div>
                    <span>{{ row.orderKindLabel }}</span>
                    <template v-if="row.therapyLabel">
                      <span class="ip-advice-type-sep"> / </span>
                      <span
                        class="ip-advice-therapy-text"
                        :class="adviceTherapyTypeClass(row.therapyTypeCode)"
                      >
                        {{ row.therapyLabel }}
                      </span>
                    </template>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn label="医嘱内容" min-width="360">
                <template #default="{ row }">
                  <div class="nurse-order-content-cell">
                    <template v-if="isExecuteMedicineOrder(row)">
                      <AdviceOrderDisplayCell
                        :row-key="row.id"
                        :content="executeOrderDisplayedContent(row)"
                        :dosage-text="row.dosage"
                        :extension="row.extension"
                        :group-items="row.groupItems"
                      />
                    </template>
                    <template v-else-if="row.bindItems?.length">
                      <div class="advice-order-line-row">
                        <span class="drug-main-text">
                          {{ executeOrderDisplayedContent(row) }}
                        </span>
                        <span v-if="row.dosage" class="group-drug-dose">{{ row.dosage }}</span>
                      </div>
                      <div v-if="row.extension" class="normal-drug-shared">{{ row.extension }}</div>
                    </template>
                    <AdviceOrderDisplayCell
                      v-else
                      :row-key="row.id"
                      :content="executeOrderDisplayedContent(row)"
                      :dosage-text="row.dosage"
                      :extension="row.extension"
                    />
                    <div
                      v-if="row.bindItems?.length"
                      class="order-execute-bind-follow"
                      :class="{ 'order-execute-bind-follow--after-extension': !!row.extension }"
                    >
                      <div
                        v-for="(bind, bindIndex) in row.bindItems"
                        :key="resolveExecuteBindItemKey(bind, bindIndex)"
                        class="order-execute-bind-attach-row"
                      >
                        <span class="order-execute-bind-tag">带</span>
                        <span
                          class="order-execute-bind-name"
                          :title="resolveExecuteBindItemDisplayText(bind)"
                        >
                          {{ resolveExecuteBindItemDisplayText(bind) }}
                        </span>
                        <div class="order-execute-bind-qty-group">
                          <ElInputNumber
                            v-if="bindQuantityEditable"
                            class="order-execute-bind-qty-input"
                            :model-value="resolveExecuteBindQuantityValue(bind)"
                            :min="0"
                            :step="1"
                            :precision="0"
                            :controls="false"
                            size="small"
                            @update:model-value="
                              (value) =>
                                updateBindItemQuantity(
                                  row.encounterId || card.patient.id,
                                  row,
                                  bindIndex,
                                  value,
                                )
                            "
                          />
                          <span v-else class="order-execute-bind-qty-readonly">
                            {{ resolveExecuteBindQuantityValue(bind) }}
                          </span>
                          <span
                            v-if="resolveExecuteBindItemUnitLabel(bind)"
                            class="order-execute-bind-unit"
                          >
                            {{ resolveExecuteBindItemUnitLabel(bind) }}
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn align="center" label="开始/终止" min-width="200">
                <template #default="{ row }">
                  <span class="start-stop-cell">{{ row.startStopText }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn align="center" label="执行科室" min-width="120">
                <template #default="{ row }">
                  <span class="execute-dept-cell">{{ row.executeDeptDisplay }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn :label="scheduleColumnLabel" min-width="260">
                <template #default="{ row }">
                  <div class="execute-schedule-list">
                    <div
                      v-for="schedule in row.planSchedules"
                      :key="`${row.id}-${schedule.date}`"
                      class="execute-schedule-row"
                    >
                      <span class="execute-schedule-date">{{ schedule.date }}</span>
                      <div class="execute-schedule-slots">
                        <template v-if="slotSelectionEnabled">
                          <template v-for="slot in schedule.slots" :key="slot.slotKey">
                            <span
                              v-if="slotTimeEditingByKey[slot.slotKey]"
                              class="execute-slot-time-edit-wrap"
                            >
                              <ElTimePicker
                                :ref="(el) => bindSlotTimePickerRef(slot.slotKey, el)"
                                :model-value="resolveSlotCurrentHHmm(slot)"
                                format="HH:mm"
                                value-format="HH:mm"
                                size="small"
                                :clearable="false"
                                teleported
                                class="execute-slot-time-edit"
                                style="width: 64px; height: 24px"
                                @update:model-value="
                                  (v: string | null) => handleSlotInlineTimeChange(slot, v)
                                "
                                @visible-change="
                                  (open: boolean) => handleSlotTimePickerVisible(slot, open)
                                "
                              />
                            </span>
                            <ElCheckbox
                              v-else
                              :model-value="!!slotSelectionByKey[slot.slotKey]"
                              border
                              size="small"
                              @update:model-value="(v: boolean) => toggleSlotSelection(slot, v)"
                              @contextmenu="(e: MouseEvent) => openSlotContextMenu(e, slot, row)"
                            >
                              <span>{{ resolveSlotCheckboxLabel(slot) }}</span>
                            </ElCheckbox>
                          </template>
                        </template>
                        <template v-else>
                          <span
                            v-for="slot in schedule.slots"
                            :key="slot.slotKey"
                            class="execute-slot-readonly"
                          >
                            {{ slot.timeLabel }}
                          </span>
                        </template>
                      </div>
                    </div>
                  </div>
                </template>
              </ElTableColumn>
            </ElTable>
            <div v-else class="py-6 text-center text-sm text-[var(--el-text-color-secondary)]">
              当前筛选下暂无医嘱
            </div>
          </div>
        </ElCollapseItem>
      </ElCollapse>
    </section>
    <section v-else class="flex min-h-0 min-w-0 flex-1 items-center justify-center bg-[#eef9fd]">
      <ElEmpty description="请先选择患者" />
    </section>

    <Teleport to="body">
      <ul
        v-if="slotContextMenuVisible"
        class="execute-slot-context-menu"
        :style="{ left: `${slotContextMenuX}px`, top: `${slotContextMenuY}px` }"
        @mousedown.stop
      >
        <template v-if="slotContextMenuFromSlot">
          <li
            class="execute-slot-context-menu__item"
            :class="{ 'is-disabled': !executeActionsEnabled }"
            @click="handleContextMenuAdjustCurrentTime"
          >
            修改时间
          </li>
          <li class="execute-slot-context-menu__divider"></li>
          <li
            class="execute-slot-context-menu__item"
            :class="{ 'is-disabled': !executeActionsEnabled }"
            @click="handleContextMenuExecuteCurrent"
          >
            执行
          </li>
          <li
            class="execute-slot-context-menu__item"
            :class="{ 'is-disabled': !skipActionsEnabled }"
            @click="handleContextMenuSkipCurrent"
          >
            不执行
          </li>
          <li
            class="execute-slot-context-menu__item"
            :class="{ 'is-disabled': !cancelActionsEnabled }"
            @click="handleContextMenuCancelCurrent"
          >
            取消执行
          </li>
          <li class="execute-slot-context-menu__divider"></li>
        </template>
        <li
          class="execute-slot-context-menu__item"
          :class="{ 'is-disabled': !executeActionsEnabled }"
          @click="handleContextMenuAdjustSelectedTime"
        >
          修改当前医嘱已选时间
        </li>
        <li class="execute-slot-context-menu__item" @click="handleContextMenuSelectAllOnRow">
          全选当前医嘱时点
        </li>
        <li class="execute-slot-context-menu__item" @click="handleContextMenuDeselectAllOnRow">
          取消全选当前医嘱时点
        </li>
        <li class="execute-slot-context-menu__divider"></li>
        <li
          class="execute-slot-context-menu__item"
          :class="{ 'is-disabled': !executeActionsEnabled }"
          @click="handleContextMenuExecuteSelected"
        >
          执行当前医嘱已选时点
        </li>
        <template v-if="contextMenuRowPrintDocType">
          <li class="execute-slot-context-menu__divider"></li>
          <li
            class="execute-slot-context-menu__item"
            :class="{ 'is-disabled': orderPrinting }"
            @click="handleContextMenuPrintLabel"
          >
            {{ contextMenuRowPrintLabel }}
          </li>
        </template>
      </ul>
    </Teleport>

    <OrderExecutePrintDrawer
      v-model:visible="printDrawerVisible"
      :patients="patients"
      :execution-deadline="executeDeadline"
    />
  </div>
</template>

<style scoped lang="scss">
@use '../components/nurse-station-patient-order-list.scss';

.execute-datetime {
  width: 200px;
  max-width: 100%;
}

.execute-project-type-checkboxes :deep(.el-checkbox) {
  margin-right: 0;
  height: auto;
  align-items: center;
}

.execute-toolbar__divider {
  display: inline-block;
  width: 1px;
  height: 20px;
  margin: 0 2px;
  background: var(--el-border-color-lighter);
  flex-shrink: 0;
}

.execute-time-type-radio :deep(.el-radio) {
  margin-right: 0;
  height: auto;
  align-items: center;
}

/** 状态 Segment：选中用 plain 浅底描边，避免实心蓝抢主操作 */
.execute-filter-segment :deep(.el-radio-button__inner) {
  color: var(--el-text-color-regular);
  background-color: var(--el-fill-color-blank);
  border-color: var(--el-border-color);
  box-shadow: none !important;
}

.execute-filter-segment :deep(.el-radio-button.is-active .el-radio-button__inner) {
  color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
  box-shadow: none !important;
}

.execute-filter-segment :deep(.el-radio-button:not(.is-active) .el-radio-button__inner:hover) {
  color: var(--el-color-primary);
}

/* 开立/停嘱各占一行；同行内「时间 + 人名」不换行（BR-IP-ORD-031） */
.start-stop-cell {
  display: inline-block;
  white-space: pre;
  font-size: 14px;
  line-height: 1.4;
  text-align: left;
}

.execute-slot-readonly {
  display: inline-flex;
  align-items: center;
  padding: 0 6px;
  height: 24px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-small);
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.execute-slot-context-menu {
  position: fixed;
  z-index: 4000;
  margin: 0;
  padding: 4px 0;
  min-width: 200px;
  list-style: none;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  box-shadow: var(--el-box-shadow-light);
  user-select: none;
}

.execute-slot-context-menu__item {
  padding: 7px 16px;
  font-size: 13px;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  cursor: pointer;
  white-space: nowrap;

  &:hover:not(.is-disabled) {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }

  &.is-disabled {
    color: var(--el-text-color-disabled);
    cursor: not-allowed;
  }
}

.execute-slot-context-menu__divider {
  height: 1px;
  margin: 4px 0;
  background: var(--el-border-color-lighter);
}

.order-execute-bind-follow {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.order-execute-bind-follow:not(.order-execute-bind-follow--after-extension) {
  border-top: 1px dashed #cbd5e1;
  padding-top: 3px;
}

.order-execute-bind-attach-row {
  align-items: center;
  display: flex;
  font-size: 12px;
  gap: 6px;
  min-width: 0;
}

.order-execute-bind-tag {
  color: #2563eb;
  flex-shrink: 0;
}

.order-execute-bind-name {
  color: var(--el-text-color-primary);
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-execute-bind-qty-group {
  align-items: center;
  display: inline-flex;
  flex-shrink: 0;
  gap: 2px;
  margin-left: auto;
}

.order-execute-bind-qty-input {
  width: 48px;
}

.order-execute-bind-qty-input :deep(.el-input__wrapper) {
  background: var(--el-fill-color-blank);
  box-shadow: 0 0 0 1px var(--el-border-color-lighter) inset;
  padding: 0 4px;
}

.order-execute-bind-qty-input :deep(.el-input__inner) {
  color: var(--el-text-color-regular);
  font-size: 12px;
  height: 22px;
  text-align: center;
}

.order-execute-bind-qty-readonly,
.order-execute-bind-unit {
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

/** 执行列表：行分隔更清晰，方便整行右键定位 */
.order-execute-table :deep(.el-table__body tr) {
  cursor: context-menu;
}

.order-execute-table :deep(.el-table__body td.el-table__cell) {
  border-bottom: 1px solid var(--el-border-color);
  padding-top: 10px;
  padding-bottom: 10px;
}

.order-execute-table :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--el-color-primary-light-9) !important;
}
</style>
