<script setup lang="ts">
import type {
  HospitalWideLotGroup,
  TraceBatchScanLine,
  TraceScanLogItem,
  TraceScanLogStatus,
  TraceScanSceneKey,
} from '#/utils/trace';
import type { InventoryItemTraceNumberRowDto } from '#/api/inventory/warehouse/inventory-detail';

import { computed, nextTick, ref, watch } from 'vue';

import {
  ElButton,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import TraceCodeTooltip from '#/components/trace/trace-code-tooltip.vue';
import {
  TRACE_CODE_LENGTH,
  TRACE_SCAN_SCENE_PRESETS,
  applyTraceBind,
  batchResolveTraceIdentityByPreset,
  canAcceptMoreTraceOnLine,
  collectBoundTraceCodes,
  countBoundTraceCodesOnLine,
  countPendingTraceScan,
  dedupeTraceCodes,
  extractTraceCodes,
  fetchHospitalWideInHospitalTraceItems,
  fetchInStockTraceCodes,
  filterNewTraceCodes,
  generateRandomTraceCodes,
  findTraceMatchCandidateLines,
  groupHospitalWideTracesByLot,
  isRemainingCoverageReached,
  isTraceSlotEmpty,
  pickTraceBindTargetLine,
  resolveRequiredTraceCount,
  splitTraceNoCommaString,
  sumBoundRemainingSplit,
  traceLineScanFullMessage,
  traceNoCommaContains,
} from '#/utils/trace';

defineOptions({
  name: 'TraceBatchScanDialog',
});

const props = withDefaults(
  defineProps<{
    lines: TraceBatchScanLine[];
    scene?: TraceScanSceneKey;
    subtitle?: string;
    title?: string;
    showRxNoColumn?: boolean;
    /** 明细行未带 locationId 时的默认库房（查在库追溯码用） */
    defaultLocationId?: string | number;
    /**
     * 超应扫个数时是否硬拒绝再绑。
     * 默认：进货 scene 为 true（BR-TRC-019）；期初须显式传 false。
     * false 时仍按有码批硬拒 / 无码批软提示（BR-TRC-020）。
     */
    hardScanCountGate?: boolean;
  }>(),
  {
    scene: 'dispense',
    subtitle: '',
    title: '批量扫描追溯码',
    showRxNoColumn: false,
    defaultLocationId: '',
    hardScanCountGate: undefined,
  },
);

/** 进货默认硬控个数；其它场景默认走有码/无码分叉 */
const enforceScanCountGate = computed(() => props.hardScanCountGate ?? props.scene === 'inbound');

const SCENE_LABELS: Record<TraceScanSceneKey, string> = {
  dispense: '发药',
  inbound: '进货',
  inventoryOut: '库房出库',
  stocktake: '盘点',
  gainLoss: '损益',
};

type LineDisplayStatus = 'bound' | 'error' | 'pending';
type BindResult = 'bound' | 'duplicate' | 'invalid' | 'not_found' | 'not_in_hospital';

interface BatchBindStats {
  bound: number;
  duplicate: number;
  invalid: number;
  notFound: number;
  notInHospital: number;
}

const visible = defineModel<boolean>({ default: false });
const traceByLineId = defineModel<Record<string, string>>('traceByLineId', { required: true });

const isDev = import.meta.env.DEV;

const snapshotTrace = ref<Record<string, string>>({});
const scanInput = ref('');
const scanInputRef = ref<InstanceType<typeof ElInput> | null>(null);
const mockScanCount = ref(1);
const resolving = ref(false);
const scanLog = ref<TraceScanLogItem[]>([]);
const rowAlertById = ref<Record<string, boolean>>({});
/** 扫码反查得到的主档剩余拆零（消耗侧盖住判定） */
const remainingByTraceNo = ref<Record<string, number>>({});
/** 用户锁定的目标行；空表示自动匹配 */
const currentLineId = ref('');
/** 药品展开：全院在院码缓存（按明细行 id；含全部批号） */
const hospitalTraceByLineId = ref<Record<string, InventoryItemTraceNumberRowDto[]>>({});
/** 药品展开是否已加载（区分「未拉」与「拉到空」） */
const hospitalTraceLoadedByLineId = ref<Record<string, boolean>>({});
/** 药品展开加载中（按行） */
const expandLoadingByLineId = ref<Record<string, boolean>>({});
/** 批号二级展开：`${lineId}::${lotNumber}` → 是否展开 */
const lotExpandOpenByKey = ref<Record<string, boolean>>({});

const scenePreset = () => TRACE_SCAN_SCENE_PRESETS[props.scene];
const isCommaMode = computed(() => scenePreset().bindMode === 'comma');

/** 实际参与绑定的明细行 */
const effectiveLines = computed((): TraceBatchScanLine[] => props.lines);

/** 某行当前展示的追溯码 */
function effectiveTrace(row: TraceBatchScanLine): string {
  const v = traceByLineId.value[row.id];
  if (v !== undefined && v.trim() !== '') return v.trim();
  return (row.traceNo ?? '').trim();
}

/** 明细行展示状态 */
function lineDisplayStatus(row: TraceBatchScanLine): LineDisplayStatus {
  if (rowAlertById.value[row.id]) return 'error';
  if (!isTraceSlotEmpty(effectiveTrace(row))) return 'bound';
  return 'pending';
}

const boundLineCount = computed(
  () => effectiveLines.value.filter((row) => !isTraceSlotEmpty(effectiveTrace(row))).length,
);

const totalBoundCodeCount = computed(
  () => collectBoundTraceCodes(effectiveLines.value, traceByLineId.value).size,
);

const totalLineCount = computed(() => effectiveLines.value.length);

const scanLogCount = computed(() => scanLog.value.length);

/** 待绑行优先排序 */
const displayLines = computed(() => {
  const rank: Record<LineDisplayStatus, number> = { pending: 0, error: 1, bound: 2 };
  return [...effectiveLines.value].sort(
    (a, b) => rank[lineDisplayStatus(a)] - rank[lineDisplayStatus(b)],
  );
});

/** 当前选中行 */
const currentLine = computed(
  () => effectiveLines.value.find((row) => row.id === currentLineId.value) ?? null,
);

/** 用户锁定目标行时的展示文案 */
const currentTargetLabel = computed(() => {
  const row = currentLine.value;
  if (!row) return '';
  const lot = String(row.lotNumber ?? '').trim();
  const pending = linePendingScan(row);
  const parts = [lineDisplayName(row)];
  if (lot) parts.push(lot);
  if (pending !== null) parts.push(`待扫 ${pending}`);
  return parts.join(' · ');
});

/** 弹窗打开时拉快照并重置（不预选行，默认自动匹配） */
watch(visible, (open) => {
  if (!open) return;
  snapshotTrace.value = { ...traceByLineId.value };
  scanInput.value = '';
  mockScanCount.value = 1;
  scanLog.value = [];
  rowAlertById.value = {};
  remainingByTraceNo.value = {};
  currentLineId.value = '';
  hospitalTraceByLineId.value = {};
  hospitalTraceLoadedByLineId.value = {};
  expandLoadingByLineId.value = {};
  lotExpandOpenByKey.value = {};
});

/** 弹窗打开后聚焦扫码框 */
function handleDialogOpened(): void {
  void nextTick(() => {
    setTimeout(() => focusScanInput(), 30);
  });
}

/** 行是否仍需扫码（消耗侧：剩余未盖住；否则按个数待扫） */
function lineStillNeedsScan(row: TraceBatchScanLine): boolean {
  const preset = scenePreset();
  if (preset.bindMode === 'comma') {
    const opts = lineCoverageOpts(row);
    const covered = isRemainingCoverageReached(row.operationSplitQty, opts?.boundRemainingSum);
    if (covered === true) return false;
    if (covered === false) return true;
    const pending = countPendingTraceScan(row, effectiveTrace(row), preset.bindMode);
    if (pending === null) return lineDisplayStatus(row) === 'pending';
    return pending > 0;
  }
  return lineDisplayStatus(row) === 'pending';
}

/**
 * 消耗侧盖住判定入参：汇总本行已绑码主档剩余。
 *
 * @param row 扫码行
 */
function lineCoverageOpts(row: TraceBatchScanLine): { boundRemainingSum?: number } | undefined {
  if (row.operationSplitQty == null) return undefined;
  const boundRemainingSum = sumBoundRemainingSplit(effectiveTrace(row), remainingByTraceNo.value);
  if (boundRemainingSum === undefined) return undefined;
  return { boundRemainingSum };
}

/** 缓存反查得到的主档剩余拆零 */
function rememberTraceRemaining(
  traceNo: string,
  remaining: number | string | undefined | null,
): void {
  const code = String(traceNo ?? '').trim();
  const rem = Number(remaining);
  if (!code || !Number.isFinite(rem) || rem < 0) return;
  remainingByTraceNo.value = { ...remainingByTraceNo.value, [code]: rem };
}

/** 取 ElInput 内部原生 input，便于可靠 focus */
function getScanNativeInput(): HTMLInputElement | null {
  const inst = scanInputRef.value as
    | { input?: HTMLInputElement; textarea?: HTMLInputElement; $el?: HTMLElement }
    | null
    | undefined;
  if (!inst) return null;
  if (inst.input instanceof HTMLInputElement) return inst.input;
  if (inst.textarea instanceof HTMLInputElement) return inst.textarea;
  const el = inst.$el;
  if (el instanceof HTMLElement) {
    return el.querySelector('input');
  }
  return null;
}

/**
 * 允许暂时离开扫码框的目标（开发模拟数量、页脚按钮、关闭）。
 * 其余操作一律把焦点抢回扫码框，保证扫码枪随时可录入。
 */
function isAllowedAwayTarget(el: Element | null): boolean {
  if (!el || !(el instanceof HTMLElement)) return false;
  if (el.closest('.scan-dev')) return true;
  if (el.closest('.scan-dialog-foot__actions')) return true;
  if (el.closest('.el-dialog__headerbtn')) return true;
  return false;
}

/** 聚焦扫码框（弹窗打开期间可反复调用） */
function focusScanInput(): void {
  if (!visible.value) return;
  void nextTick(() => {
    if (!visible.value) return;
    const inst = scanInputRef.value as { focus?: () => void } | null;
    inst?.focus?.();
    getScanNativeInput()?.focus?.();
  });
}

/** 操作后延迟抢回焦点，先让点击/清除等业务逻辑跑完 */
function scheduleFocusScanInput(): void {
  if (!visible.value) return;
  window.setTimeout(() => {
    if (!visible.value) return;
    const active = document.activeElement;
    if (isAllowedAwayTarget(active)) return;
    const native = getScanNativeInput();
    if (native && active === native) return;
    focusScanInput();
  }, 0);
}

/** 扫码框失焦时抢回（点表格/按钮/空白都会失焦） */
function handleScanInputBlur(): void {
  scheduleFocusScanInput();
}

/**
 * 点击明细行：锁定目标；再点同一行取消锁定（回到自动匹配）。
 */
function selectLine(row: TraceBatchScanLine): void {
  if (currentLineId.value === row.id) {
    currentLineId.value = '';
  } else {
    currentLineId.value = row.id;
  }
  scheduleFocusScanInput();
}

/** 按序号锁定目标（键盘 Alt+1～9；禁止裸数字，避免扫码枪改锁定行） */
function selectLineByIndex(index: number): void {
  const row = displayLines.value[index];
  if (row) currentLineId.value = row.id;
}

/** 按展示顺序切换锁定行；无锁定时 ↑↓ 从首行起 */
function selectAdjacentLine(delta: -1 | 1): void {
  const lines = displayLines.value;
  if (lines.length === 0) return;
  const idx = lines.findIndex((row) => row.id === currentLineId.value);
  if (idx < 0) {
    currentLineId.value = lines[0]!.id;
    return;
  }
  const next = (idx + delta + lines.length) % lines.length;
  currentLineId.value = lines[next]!.id;
}

/** 锁定模式下：单行绑满后跳到下一待绑行；无待绑则取消锁定 */
function advanceToNextPendingLine(): void {
  const lines = displayLines.value;
  const idx = lines.findIndex((row) => row.id === currentLineId.value);
  for (let i = idx + 1; i < lines.length; i += 1) {
    const row = lines[i]!;
    if (lineStillNeedsScan(row)) {
      currentLineId.value = row.id;
      return;
    }
  }
  const next = lines.find((row) => lineStillNeedsScan(row));
  if (next) {
    currentLineId.value = next.id;
    return;
  }
  currentLineId.value = '';
}

/**
 * 绑定成功后：仅「用户已锁定行」时才推进目标；
 * 自动匹配模式不写 currentLineId，避免误进锁定。
 */
function advanceAfterBind(row: TraceBatchScanLine): void {
  if (!currentLineId.value) return;
  const preset = scenePreset();
  if (preset.bindMode === 'single') {
    advanceToNextPendingLine();
    return;
  }
  const opts = lineCoverageOpts(row);
  const covered = isRemainingCoverageReached(row.operationSplitQty, opts?.boundRemainingSum);
  if (covered === true) {
    advanceToNextPendingLine();
    return;
  }
  if (covered === false) return;
  const pending = countPendingTraceScan(row, effectiveTrace(row), preset.bindMode);
  if (pending === 0) advanceToNextPendingLine();
}

/**
 * 扫码框键盘：Enter 提交，↑↓ 切换锁定行；Alt+1～9 按序锁定。
 * 裸数字绝不改锁定——扫码枪会连续送 0–9，否则已固定的 A 会被改成 B。
 */
function handleScanKeydown(event: KeyboardEvent): void {
  if (event.key === 'ArrowUp') {
    event.preventDefault();
    selectAdjacentLine(-1);
    return;
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault();
    selectAdjacentLine(1);
    return;
  }
  const num = Number(event.key);
  if (num >= 1 && num <= 9 && event.altKey && !event.ctrlKey && !event.metaKey && !event.shiftKey) {
    event.preventDefault();
    selectLineByIndex(num - 1);
  }
}

/**
 * 未建档兜底目标：仅使用用户已锁定的行。
 * 未锁定时返回 undefined，由调用方提示点选（避免新码误落到首行）。
 */
function pickFallbackTargetLine(): TraceBatchScanLine | undefined {
  const preset = scenePreset();
  if (!currentLineId.value) return undefined;
  const row = effectiveLines.value.find((line) => line.id === currentLineId.value);
  if (!row) return undefined;
  const cur = effectiveTrace(row);
  if (preset.bindMode === 'single' && isTraceSlotEmpty(cur)) return row;
  if (preset.bindMode === 'comma') return row;
  return undefined;
}

/** 当前锁定行为无码批时，允许 NOT_FOUND 软录入 */
function softFallbackOk(): boolean {
  const row = currentLine.value;
  if (!row) return false;
  return !isHardTraceLine(row);
}

/** 写入扫码流水 */
function pushLog(code: string, status: TraceScanLogStatus, message: string): void {
  if (status === 'duplicate') return;
  const timeLabel = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  const id = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
  scanLog.value = [{ id, code, status, message, timeLabel }, ...scanLog.value].slice(0, 200);
}

/** 药品名称展示 */
function lineDisplayName(row: TraceBatchScanLine): string {
  return String(row.itemName ?? '').trim() || '未指定药品';
}

/** 待扫数量展示 */
function pendingScanLabel(row: TraceBatchScanLine): string {
  const pending = linePendingScan(row);
  if (pending === null) return '—';
  return String(pending);
}

/** 已扫数量展示 */
function scannedScanLabel(row: TraceBatchScanLine): string {
  return String(lineScannedCount(row));
}

/** 行已绑追溯码数量 */
function lineScannedCount(row: TraceBatchScanLine): number {
  return countBoundTraceCodesOnLine(effectiveTrace(row), scenePreset().bindMode);
}

/** 行剩余待扫数量 */
function linePendingScan(row: TraceBatchScanLine): number | null {
  return countPendingTraceScan(row, effectiveTrace(row), scenePreset().bindMode);
}

/** 解析行库房（行上优先，否则弹窗 defaultLocationId） */
function resolveLineLocationId(row: TraceBatchScanLine): string {
  const fromRow = String(row.locationId ?? '').trim();
  if (fromRow) return fromRow;
  return String(props.defaultLocationId ?? '').trim();
}

/**
 * 单位 code → 展示文案：优先后端 Label，否则回落 code，皆空则「-」。
 *
 * @param code 单位编码
 * @param label 后端术语转译
 */
function unitDisplayLabel(code?: string, label?: string): string {
  const text = String(label ?? '').trim();
  if (text) return text;
  const raw = String(code ?? '').trim();
  return raw || '-';
}

/** 展开行展示：主档拆零剩余量文案 */
function remainingSplitDisplay(item: InventoryItemTraceNumberRowDto): string {
  const qty = item.remainingSplitQuantity;
  if (qty === null || qty === undefined || String(qty).trim() === '') return '-';
  return String(qty);
}

/** 批号二级展开缓存键 */
function lotExpandKey(lineId: string, lotNumber: string): string {
  return `${lineId}::${lotNumber}`;
}

/**
 * 取某药品明细行已缓存的全院在院码，按批号分组。
 *
 * @param lineId 明细行 id
 */
function hospitalLotGroupsForLine(lineId: string): HospitalWideLotGroup[] {
  const items = hospitalTraceByLineId.value[lineId] ?? [];
  return groupHospitalWideTracesByLot(items);
}

/**
 * 懒加载某药品的全院在院码（全部批号；只读浏览，不绑码）。
 * 不依赖发药行批号/库房；展开与行锁定分离。
 */
async function loadHospitalWideTracesForLine(row: TraceBatchScanLine): Promise<void> {
  const lineId = String(row.id ?? '').trim();
  if (!lineId) return;
  if (hospitalTraceLoadedByLineId.value[lineId]) return;
  if (expandLoadingByLineId.value[lineId]) return;

  const itemId = String(row.itemId ?? '').trim();
  if (!itemId) {
    ElMessage.warning('药品信息不完整，无法查询在院追溯码');
    hospitalTraceByLineId.value = { ...hospitalTraceByLineId.value, [lineId]: [] };
    hospitalTraceLoadedByLineId.value = {
      ...hospitalTraceLoadedByLineId.value,
      [lineId]: true,
    };
    return;
  }

  expandLoadingByLineId.value = { ...expandLoadingByLineId.value, [lineId]: true };
  try {
    const items = await fetchHospitalWideInHospitalTraceItems({ itemId });
    hospitalTraceByLineId.value = { ...hospitalTraceByLineId.value, [lineId]: items };
    hospitalTraceLoadedByLineId.value = {
      ...hospitalTraceLoadedByLineId.value,
      [lineId]: true,
    };
  } catch {
    ElMessage.error('在院追溯码查询失败');
    hospitalTraceByLineId.value = { ...hospitalTraceByLineId.value, [lineId]: [] };
    hospitalTraceLoadedByLineId.value = {
      ...hospitalTraceLoadedByLineId.value,
      [lineId]: true,
    };
  } finally {
    const next = { ...expandLoadingByLineId.value };
    delete next[lineId];
    expandLoadingByLineId.value = next;
  }
}

/**
 * 药品明细表展开/收起：仅展开时拉取该药全院在院码；不改变当前锁定行。
 */
function handleLineExpandChange(row: TraceBatchScanLine, expandedRows: TraceBatchScanLine[]): void {
  const open = expandedRows.some((item) => item.id === row.id);
  if (open) {
    void loadHospitalWideTracesForLine(row);
  }
  scheduleFocusScanInput();
}

/** 切换批号二级展开（展示该批下追溯码列表） */
function toggleLotExpand(lineId: string, lotNumber: string): void {
  const key = lotExpandKey(lineId, lotNumber);
  const next = { ...lotExpandOpenByKey.value };
  next[key] = !next[key];
  lotExpandOpenByKey.value = next;
  scheduleFocusScanInput();
}

/** 批号是否处于二级展开 */
function isLotExpandOpen(lineId: string, lotNumber: string): boolean {
  return lotExpandOpenByKey.value[lotExpandKey(lineId, lotNumber)] === true;
}

/** 明细行样式 */
function lineRowClass(row: TraceBatchScanLine): string {
  const classes: string[] = ['line-row'];
  if (row.id === currentLineId.value) classes.push('line-row--active');
  if (lineDisplayStatus(row) === 'bound') classes.push('line-row--bound');
  if (lineDisplayStatus(row) === 'error') classes.push('line-row--error');
  return classes.join(' ');
}

/** 流水是否成功 */
function logStatusIsOk(status: TraceScanLogStatus): boolean {
  return status === 'ok';
}

function markLineError(lineId: string): void {
  rowAlertById.value = { ...rowAlertById.value, [lineId]: true };
}

function clearLineError(lineId: string): void {
  if (!rowAlertById.value[lineId]) return;
  const next = { ...rowAlertById.value };
  delete next[lineId];
  rowAlertById.value = next;
}

function isTraceBoundInSession(traceNo: string, boundPool: Set<string>): boolean {
  return boundPool.has(traceNo.trim());
}

function markNotInHospitalLines(itemId: string, lotNumber: string): void {
  const candidates = findTraceMatchCandidateLines(effectiveLines.value, itemId, lotNumber);
  if (candidates.length === 1) markLineError(candidates[0]!.id);
}

/**
 * 行已扫满/已盖住时是否拒绝再绑。
 *
 * <p>进货（{@link enforceScanCountGate}）超限一律硬拒，避免新批无码时误走软放行。
 * 发药/出库等：有码批硬拒；无码批仅提示仍允许绑（BR-TRC-020）。</p>
 */
function rejectIfLineScanFull(row: TraceBatchScanLine, code: string): boolean {
  const preset = scenePreset();
  const cur = effectiveTrace(row);
  const opts = lineCoverageOpts(row);
  if (canAcceptMoreTraceOnLine(row, cur, preset.bindMode, opts)) return false;
  const msg = traceLineScanFullMessage(row, preset.bindMode, opts);
  pushLog(code, 'invalid', msg);
  ElMessage.warning(msg);
  // 进货硬 Gate，或有码批：拒绝再绑
  if (enforceScanCountGate.value || isHardTraceLine(row)) {
    markLineError(row.id);
    return true;
  }
  // 无码批软提示：继续允许录入
  return false;
}

/** 有码批才走反查/在院硬校验；无码批（期初未扫）仅软提示 */
function isHardTraceLine(row: TraceBatchScanLine): boolean {
  return row.lotHasTraceMaster === true;
}

/** 直接写入指定行追溯码（手输 / 反查失败兜底） */
function bindManualCodeToLine(row: TraceBatchScanLine, rawCode: string): boolean {
  const trimmed = rawCode.trim();
  if (!trimmed) return false;

  if (trimmed.length !== TRACE_CODE_LENGTH) {
    pushLog(trimmed, 'invalid', `须为 ${TRACE_CODE_LENGTH} 位数字`);
    ElMessage.warning(`追溯码须为 ${TRACE_CODE_LENGTH} 位数字`);
    return false;
  }

  const preset = scenePreset();
  const boundPool = collectBoundTraceCodes(effectiveLines.value, traceByLineId.value);
  if (isTraceBoundInSession(trimmed, boundPool)) {
    ElMessage.warning('该追溯码已绑定');
    return false;
  }

  const cur = effectiveTrace(row);
  if (preset.bindMode === 'comma' && traceNoCommaContains(cur, trimmed)) {
    ElMessage.warning('该行已包含该追溯码');
    return false;
  }

  if (rejectIfLineScanFull(row, trimmed)) return false;

  traceByLineId.value = {
    ...traceByLineId.value,
    [row.id]: applyTraceBind(cur, trimmed, preset.bindMode),
  };
  clearLineError(row.id);
  // 手动绑到指定行时保持/写入锁定，便于连续扫同一药
  currentLineId.value = row.id;
  pushLog(trimmed, 'ok', `已录入 → ${lineDisplayName(row)}`);
  advanceAfterBind(row);
  return true;
}

/**
 * 反查成功后选目标行：
 * - 已锁定行：只认该行；码的品项须与锁定行一致，否则拒绝（不落到其它行）；
 * - 未锁定：按品+批自动匹配未满行。
 */
function bindResolvedItem(
  traceNo: string,
  itemId: string,
  lotNumber: string,
  boundPool: Set<string>,
  stats: BatchBindStats,
): BindResult {
  const preset = scenePreset();
  const trimmed = traceNo.trim();

  if (isTraceBoundInSession(trimmed, boundPool)) {
    stats.duplicate += 1;
    return 'duplicate';
  }

  const candidates = findTraceMatchCandidateLines(effectiveLines.value, itemId, lotNumber);
  const pinned = currentLine.value;
  let target: TraceBatchScanLine | undefined;

  if (pinned) {
    // 锁定=固定本行：只比品项，避免行上批号空/不一致时误拒同药，更不会绑到其它药
    const pinnedItem = String(pinned.itemId ?? '').trim();
    const wantItem = String(itemId ?? '').trim();
    if (!pinnedItem || !wantItem || pinnedItem !== wantItem) {
      stats.notFound += 1;
      pushLog(trimmed, 'not_found', `与选中药品不符（${lineDisplayName(pinned)}）`);
      ElMessage.warning(`该追溯码与选中药品不符：${lineDisplayName(pinned)}`);
      markLineError(pinned.id);
      return 'not_found';
    }
    target = pinned;
  } else {
    target = pickTraceBindTargetLine(
      candidates,
      trimmed,
      preset.bindMode,
      traceByLineId.value,
      remainingByTraceNo.value,
    );
    if (!target && preset.allowNotFound) {
      target = pickFallbackTargetLine();
    }
  }

  if (!target) {
    stats.notFound += 1;
    if (
      candidates.length > 0 &&
      candidates.every(
        (line) =>
          !canAcceptMoreTraceOnLine(
            line,
            effectiveTrace(line),
            preset.bindMode,
            lineCoverageOpts(line),
          ),
      )
    ) {
      const msg = traceLineScanFullMessage(
        candidates[0]!,
        preset.bindMode,
        lineCoverageOpts(candidates[0]!),
      );
      pushLog(trimmed, 'invalid', msg);
      ElMessage.warning(msg);
      markLineError(candidates[0]!.id);
    } else {
      pushLog(trimmed, 'not_found', '未找到匹配的明细');
      ElMessage.warning('未找到匹配的明细');
    }
    return 'not_found';
  }

  const boundElsewhere = effectiveLines.value.some(
    (r) =>
      r.id !== target!.id &&
      !isTraceSlotEmpty(effectiveTrace(r)) &&
      (preset.bindMode === 'single'
        ? effectiveTrace(r) === trimmed
        : traceNoCommaContains(effectiveTrace(r), trimmed)),
  );
  if (boundElsewhere) {
    stats.duplicate += 1;
    return 'duplicate';
  }

  const cur = effectiveTrace(target);
  if (preset.bindMode === 'comma' && traceNoCommaContains(cur, trimmed)) {
    stats.duplicate += 1;
    return 'duplicate';
  }
  if (preset.bindMode === 'single' && !isTraceSlotEmpty(cur) && cur.trim() === trimmed) {
    stats.duplicate += 1;
    return 'duplicate';
  }

  if (rejectIfLineScanFull(target, trimmed)) {
    stats.invalid += 1;
    return 'invalid';
  }

  traceByLineId.value = {
    ...traceByLineId.value,
    [target.id]: applyTraceBind(cur, trimmed, preset.bindMode),
  };
  boundPool.add(trimmed);
  clearLineError(target.id);
  stats.bound += 1;
  pushLog(trimmed, 'ok', `已绑定 → ${lineDisplayName(target)}`);
  advanceAfterBind(target);
  return 'bound';
}

function bindFailedResolveItem(
  traceNo: string,
  resolveStatus: string,
  boundPool: Set<string>,
  stats: BatchBindStats,
): BindResult {
  const preset = scenePreset();
  const trimmed = traceNo.trim();

  if (resolveStatus === 'DUPLICATE_IN_REQUEST') {
    stats.duplicate += 1;
    return 'duplicate';
  }

  if (resolveStatus === 'NOT_IN_HOSPITAL' && preset.rejectNotInHospital) {
    stats.notInHospital += 1;
    pushLog(trimmed, 'not_in_hospital', '追溯码不在院内或已耗尽');
    ElMessage.warning('追溯码不在院内或已耗尽');
    return 'not_in_hospital';
  }

  if (resolveStatus === 'NOT_FOUND' && (preset.allowNotFound || softFallbackOk())) {
    if (isTraceBoundInSession(trimmed, boundPool)) {
      stats.duplicate += 1;
      return 'duplicate';
    }
    const fb = pickFallbackTargetLine();
    if (fb && (preset.allowNotFound || !isHardTraceLine(fb))) {
      if (rejectIfLineScanFull(fb, trimmed)) {
        stats.invalid += 1;
        return 'invalid';
      }
      traceByLineId.value = {
        ...traceByLineId.value,
        [fb.id]: applyTraceBind(effectiveTrace(fb), trimmed, preset.bindMode),
      };
      boundPool.add(trimmed);
      clearLineError(fb.id);
      stats.bound += 1;
      pushLog(trimmed, 'ok', `新码录入 → ${lineDisplayName(fb)}`);
      advanceAfterBind(fb);
      return 'bound';
    }
    stats.notFound += 1;
    pushLog(trimmed, 'not_found', '未建档，请先点选要录入的药品');
    ElMessage.warning('未建档，请先点选要录入的药品');
    return 'not_found';
  }

  stats.notFound += 1;
  const msg = resolveStatus === 'NOT_FOUND' ? '库中未找到该追溯码' : '无法使用该追溯码';
  pushLog(trimmed, 'not_found', msg);
  ElMessage.warning(resolveStatus === 'NOT_FOUND' ? '未找到该追溯码' : '无法使用该追溯码');
  return 'not_found';
}

async function processBatchBind(codes: string[]): Promise<BatchBindStats> {
  const stats: BatchBindStats = {
    bound: 0,
    duplicate: 0,
    invalid: 0,
    notFound: 0,
    notInHospital: 0,
  };

  if (codes.length === 0) return stats;

  if (effectiveLines.value.length === 0) {
    stats.notFound += codes.length;
    for (const code of codes) {
      pushLog(code, 'not_found', '当前无明细行');
    }
    ElMessage.warning('当前无明细行');
    return stats;
  }

  const preset = scenePreset();
  const boundPool = collectBoundTraceCodes(effectiveLines.value, traceByLineId.value);
  const toResolve = filterNewTraceCodes(codes, boundPool);
  stats.duplicate += codes.length - toResolve.length;
  if (toResolve.length === 0) return stats;

  /**
   * 无码批锁定行 / 进货盘点锁定行：直接录入，跳过反查。
   * 有码批仍走反查（本库优先、否则全院）。
   */
  if (currentLine.value && (preset.allowNotFound || !isHardTraceLine(currentLine.value))) {
    for (const trimmed of toResolve) {
      if (isTraceBoundInSession(trimmed, boundPool)) {
        stats.duplicate += 1;
        continue;
      }
      if (bindManualCodeToLine(currentLine.value, trimmed)) {
        boundPool.add(trimmed);
        stats.bound += 1;
      }
    }
    return stats;
  }

  resolving.value = true;
  try {
    const res = await batchResolveTraceIdentityByPreset(toResolve, preset);

    for (const trimmed of toResolve) {
      if (isTraceBoundInSession(trimmed, boundPool)) {
        stats.duplicate += 1;
        continue;
      }

      const fail = res.failItems?.find((f) => (f.traceNo ?? '').trim() === trimmed);
      if (fail) {
        bindFailedResolveItem(
          trimmed,
          String(fail.resolveStatusEnum ?? '').trim(),
          boundPool,
          stats,
        );
        continue;
      }

      const item = res.items?.find((i) => (i.traceNo ?? '').trim() === trimmed);
      if (!item) {
        stats.notFound += 1;
        pushLog(trimmed, 'not_found', '反查无结果');
        continue;
      }

      const resolveStatus = String(item.resolveStatusEnum ?? 'OK').trim();
      const itemId = String(item.itemId ?? '').trim();
      const lotNumber = String(item.lotNumber ?? '').trim();

      if (resolveStatus === 'NOT_IN_HOSPITAL' && preset.rejectNotInHospital) {
        stats.notInHospital += 1;
        if (itemId && lotNumber) markNotInHospitalLines(itemId, lotNumber);
        pushLog(trimmed, 'not_in_hospital', '追溯码不在院内或已耗尽');
        ElMessage.warning('追溯码不在院内或已耗尽');
        continue;
      }

      if (resolveStatus === 'NOT_FOUND' && preset.allowNotFound) {
        bindFailedResolveItem(trimmed, resolveStatus, boundPool, stats);
        continue;
      }

      if (!itemId || !lotNumber) {
        stats.notFound += 1;
        pushLog(trimmed, 'not_found', '反查结果缺少药品或批号');
        continue;
      }

      rememberTraceRemaining(trimmed, item.remainingSplitQuantity);
      bindResolvedItem(trimmed, itemId, lotNumber, boundPool, stats);
    }
  } catch {
    let anyBound = false;
    for (const code of toResolve) {
      if (
        preset.allowNotFound &&
        currentLine.value &&
        bindManualCodeToLine(currentLine.value, code)
      ) {
        anyBound = true;
        continue;
      }
      pushLog(code, 'not_found', '追溯码反查失败');
    }
    if (!anyBound) ElMessage.error('追溯码反查失败，请稍后重试');
  } finally {
    resolving.value = false;
  }

  return stats;
}

async function handleScanEnter(): Promise<void> {
  const raw = scanInput.value;
  scanInput.value = '';
  if (!raw.trim()) {
    focusScanInput();
    return;
  }

  const { bad, ok } = extractTraceCodes(raw);
  if (bad.length > 0) {
    for (const b of bad) {
      pushLog(b, 'invalid', `须为 ${TRACE_CODE_LENGTH} 位数字`);
    }
    ElMessage.warning(`有 ${bad.length} 段长度不符合 ${TRACE_CODE_LENGTH} 位，已跳过`);
  }

  let codes = dedupeTraceCodes(ok);

  await processBatchBind(codes);
  focusScanInput();
}

/** 模拟扫码（仅开发环境）：按数量随机生成追溯码 */
async function handleSimulateScan(): Promise<void> {
  const codes = generateRandomTraceCodes(mockScanCount.value);
  await processBatchBind(codes);
  focusScanInput();
}

/**
 * 同弹窗其它行「本库有码」的追溯码集合（发药全院回落时不得占用）。
 * 例：门诊西药房行有码时，门诊药房空库回落不能先抢走西药房码。
 */
async function collectSiblingLocationReservedCodes(
  row: TraceBatchScanLine,
  itemId: string,
  lotNumber: string,
): Promise<Set<string>> {
  const reserved = new Set<string>();
  const selfLoc = resolveLineLocationId(row);
  for (const other of effectiveLines.value) {
    if (!other || other.id === row.id) continue;
    if (String(other.itemId ?? '').trim() !== itemId) continue;
    if (String(other.lotNumber ?? '').trim() !== lotNumber) continue;
    const otherLoc = resolveLineLocationId(other);
    if (!otherLoc || otherLoc === selfLoc) continue;
    const codes = await fetchInStockTraceCodes({
      locationId: otherLoc,
      itemId,
      lotNumber,
      hospitalWideFallbackWhenEmpty: false,
    });
    for (const code of codes) {
      reserved.add(code);
    }
  }
  return reserved;
}

/** 从本库在库追溯码中按行数量自动取码填入（开发测试用） */
async function handleFetchInStockTraceCodes(): Promise<void> {
  const row = currentLine.value;
  if (!row) {
    ElMessage.warning('请先在右侧选择药品');
    return;
  }

  const locationId = resolveLineLocationId(row);
  const itemId = String(row.itemId ?? '').trim();
  const lotNumber = String(row.lotNumber ?? '').trim();
  if (!locationId) {
    ElMessage.warning(
      props.scene === 'dispense'
        ? '缺少发放库房，请先在发药列表选择发放库房'
        : props.scene === 'inbound'
          ? '库取需要库房：请先选单头入库库房（进货新码通常库中尚无，建议直接扫码）'
          : '库取需要库房：请确认单头已选源库房/库位（该按钮只查本库在库码）',
    );
    return;
  }
  // 进货为新码入库：本库通常尚无该批码，库取无意义
  if (props.scene === 'inbound') {
    ElMessage.warning('进货请直接扫描或手输追溯码；库取仅用于出库类本库已有码');
    return;
  }
  if (!itemId || !lotNumber) {
    ElMessage.warning('药品或批号不完整，无法查询在库追溯码');
    return;
  }

  const preset = scenePreset();
  const pending = linePendingScan(row);
  const needCount = pending ?? resolveRequiredTraceCount(row, preset.bindMode);
  if (needCount === null || needCount <= 0) {
    ElMessage.warning(pending === 0 ? '该行已扫满' : '无法确定取码数量，请检查行数量');
    return;
  }

  resolving.value = true;
  try {
    // 本库优先：有码只用本库
    let allCodes = await fetchInStockTraceCodes({
      locationId,
      itemId,
      lotNumber,
      hospitalWideFallbackWhenEmpty: false,
    });
    let usedHospitalFallback = false;
    // 发药且本库空：全院同批，但排除同弹窗其它发放库房已有的本库码
    if (allCodes.length === 0 && props.scene === 'dispense') {
      usedHospitalFallback = true;
      const hospitalCodes = await fetchInStockTraceCodes({
        locationId,
        itemId,
        lotNumber,
        hospitalWideFallbackWhenEmpty: true,
      });
      const reserved = await collectSiblingLocationReservedCodes(row, itemId, lotNumber);
      allCodes = hospitalCodes.filter((code) => !reserved.has(code));
    }
    if (allCodes.length === 0) {
      ElMessage.warning(
        props.scene === 'dispense'
          ? usedHospitalFallback
            ? '本库无码，且全院可用在院码已被其它发放库房占用或为空'
            : '本库无在库追溯码'
          : '本库无在库追溯码',
      );
      return;
    }

    const boundPool = collectBoundTraceCodes(effectiveLines.value, traceByLineId.value);
    const cur = effectiveTrace(row);
    const available = allCodes.filter((code) => {
      if (isTraceBoundInSession(code, boundPool)) return false;
      if (preset.bindMode === 'comma' && traceNoCommaContains(cur, code)) return false;
      if (preset.bindMode === 'single' && !isTraceSlotEmpty(cur)) return false;
      return true;
    });
    const picked = available.slice(0, needCount);
    if (picked.length === 0) {
      ElMessage.warning(
        props.scene === 'dispense'
          ? '当前发放库房可用追溯码不足或已被其它行占用'
          : '在库追溯码均已被占用或本行已绑满',
      );
      return;
    }

    let nextTrace = cur;
    for (const code of picked) {
      nextTrace = applyTraceBind(nextTrace, code, preset.bindMode);
      boundPool.add(code);
      const src = usedHospitalFallback ? '全院库取' : '库取';
      pushLog(code, 'ok', `${src} → ${lineDisplayName(row)}`);
    }

    traceByLineId.value = { ...traceByLineId.value, [row.id]: nextTrace };
    clearLineError(row.id);
    currentLineId.value = row.id;

    if (picked.length < needCount) {
      ElMessage.warning(`仅取到 ${picked.length}/${needCount} 个在库码`);
    } else {
      ElMessage.success(`已自动填入 ${picked.length} 个追溯码`);
    }
    advanceAfterBind(row);
  } catch {
    ElMessage.error('在库追溯码查询失败');
  } finally {
    resolving.value = false;
    focusScanInput();
  }
}

function handleCancel(): void {
  traceByLineId.value = { ...snapshotTrace.value };
  visible.value = false;
}

function handleConfirm(): void {
  visible.value = false;
}

/** 清除指定行已绑追溯码 */
function clearLineTrace(row: TraceBatchScanLine): void {
  traceByLineId.value = Object.fromEntries(
    Object.entries(traceByLineId.value).filter(([k]) => k !== row.id),
  );
  clearLineError(row.id);
  pushLog('—', 'ok', `已清除 ${lineDisplayName(row)}`);
  scheduleFocusScanInput();
}
</script>

<template>
  <AppDialog
    v-model="visible"
    width="1400px"
    top="3vh"
    class="trace-batch-scan-dialog"
    destroy-on-close
    :show-close="true"
    @opened="handleDialogOpened"
  >
    <template #header>
      <div class="scan-dialog-head">
        <span class="scan-dialog-head__title">{{ props.title }}</span>
        <ElTag size="small" type="primary" effect="plain">{{ SCENE_LABELS[props.scene] }}</ElTag>
        <span v-if="props.subtitle" class="scan-dialog-head__subtitle">{{ props.subtitle }}</span>
      </div>
    </template>

    <div class="scan-page" @mouseup="scheduleFocusScanInput">
      <ElInput
        ref="scanInputRef"
        v-model="scanInput"
        clearable
        size="large"
        class="scan-page__input font-mono"
        placeholder="扫描或输入追溯码"
        @keydown.enter.prevent="handleScanEnter"
        @keydown="handleScanKeydown"
        @blur="handleScanInputBlur"
      >
        <template v-if="resolving" #suffix>
          <span class="scan-page__spinner" />
        </template>
      </ElInput>

      <div class="scan-current-target" :class="{ 'scan-current-target--empty': !currentLine }">
        {{
          currentLine
            ? `锁定：${currentTargetLabel}（再点该行取消，回到自动匹配）`
            : '自动匹配 · 点选右侧药品可锁定后专扫该行 · ↑↓ 切换 · Alt+1～9 按序锁定 · 展开可按批号查看全院在院码'
        }}
      </div>

      <div class="scan-split">
        <section class="scan-split__log">
          <header class="scan-split__title">
            <span>扫码流水</span>
            <span class="scan-split__title-stat">流水数量 {{ scanLogCount }}</span>
          </header>
          <ElTable
            :data="scanLog"
            size="small"
            height="520"
            row-key="id"
            class="scan-split__table"
            empty-text="—"
          >
            <ElTableColumn label="" width="44" align="center">
              <template #default="{ row }">
                <span
                  class="log-result"
                  :class="logStatusIsOk(row.status) ? 'log-result--ok' : 'log-result--fail'"
                >
                  {{ logStatusIsOk(row.status) ? '✓' : '✗' }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="code" label="追溯码" min-width="180">
              <template #default="{ row }">
                <TraceCodeTooltip :value="row.code" />
              </template>
            </ElTableColumn>
            <ElTableColumn prop="message" label="说明" min-width="100" show-overflow-tooltip />
          </ElTable>
        </section>

        <section class="scan-split__lines">
          <header class="scan-split__title">
            <span>药品明细</span>
            <span class="scan-split__title-stat">已扫 {{ totalBoundCodeCount }}</span>
          </header>
          <ElTable
            :data="displayLines"
            size="small"
            height="520"
            row-key="id"
            class="scan-split__table"
            empty-text="—"
            highlight-current-row
            :row-class-name="({ row }: { row: TraceBatchScanLine }) => lineRowClass(row)"
            @row-click="selectLine"
            @expand-change="handleLineExpandChange"
          >
            <ElTableColumn type="expand" width="36">
              <template #default="{ row }">
                <div class="line-expand" @click.stop>
                  <div v-if="expandLoadingByLineId[row.id]" class="line-expand__hint">
                    加载该药全院在院追溯码…
                  </div>
                  <div
                    v-else-if="hospitalLotGroupsForLine(row.id).length === 0"
                    class="line-expand__hint"
                  >
                    该药无在院追溯码
                  </div>
                  <div v-else class="lot-group-list">
                    <div
                      v-for="lot in hospitalLotGroupsForLine(row.id)"
                      :key="lot.lotNumber"
                      class="lot-group"
                    >
                      <button
                        type="button"
                        class="lot-group__head"
                        @click="toggleLotExpand(row.id, lot.lotNumber)"
                      >
                        <span class="lot-group__arrow">
                          {{ isLotExpandOpen(row.id, lot.lotNumber) ? '▼' : '▶' }}
                        </span>
                        <span class="lot-group__lot">批号 {{ lot.lotNumber }}</span>
                        <span class="lot-group__count">{{ lot.codes.length }} 码</span>
                      </button>
                      <ElTable
                        v-if="isLotExpandOpen(row.id, lot.lotNumber)"
                        :data="lot.codes"
                        size="small"
                        border
                        class="line-expand__table"
                        empty-text="—"
                        max-height="200"
                      >
                        <ElTableColumn label="追溯码" min-width="200">
                          <template #default="{ row: item }">
                            <TraceCodeTooltip :value="item.traceNo" />
                          </template>
                        </ElTableColumn>
                        <ElTableColumn label="剩余拆零量" width="110" align="right">
                          <template #default="{ row: item }">
                            {{ remainingSplitDisplay(item) }}
                          </template>
                        </ElTableColumn>
                        <ElTableColumn label="拆零单位" width="96" align="center">
                          <template #default="{ row: item }">
                            {{ unitDisplayLabel(item.splitUnitCode, item.splitUnitCodeLabel) }}
                          </template>
                        </ElTableColumn>
                        <ElTableColumn label="追溯码单位" width="110" align="center">
                          <template #default="{ row: item }">
                            {{ unitDisplayLabel(item.traceUnitCode, item.traceUnitCodeLabel) }}
                          </template>
                        </ElTableColumn>
                      </ElTable>
                    </div>
                  </div>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="药品" min-width="200">
              <template #default="{ row }">
                <div class="line-drug-cell">
                  <span class="line-drug-cell__name">{{ lineDisplayName(row) }}</span>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="spec" label="规格" width="100" show-overflow-tooltip />
            <ElTableColumn prop="lotNumber" label="批号" width="100" show-overflow-tooltip />
            <ElTableColumn label="待扫" width="52" align="center">
              <template #default="{ row }">
                <span
                  class="pending-scan-cell"
                  :class="{
                    'pending-scan-cell--done': linePendingScan(row) === 0,
                    'pending-scan-cell--pending': (linePendingScan(row) ?? 0) > 0,
                  }"
                >
                  {{ pendingScanLabel(row) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="已扫" width="52" align="center">
              <template #default="{ row }">
                <span
                  class="scanned-scan-cell"
                  :class="{ 'scanned-scan-cell--active': lineScannedCount(row) > 0 }"
                >
                  {{ scannedScanLabel(row) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn
              v-if="props.showRxNoColumn"
              prop="rxNo"
              label="处方号"
              width="96"
              show-overflow-tooltip
            />
            <ElTableColumn label="追溯码" min-width="260">
              <template #default="{ row }">
                <TraceCodeTooltip :value="effectiveTrace(row)" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="" width="52" align="center" fixed="right">
              <template #default="{ row }">
                <ElButton
                  link
                  type="danger"
                  size="small"
                  :disabled="isTraceSlotEmpty(effectiveTrace(row))"
                  @click.stop="clearLineTrace(row)"
                >
                  清除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </section>
      </div>

      <div v-if="isDev" class="scan-dev">
        <ElInputNumber
          v-model="mockScanCount"
          :min="1"
          :max="50"
          :step="1"
          :disabled="resolving"
          controls-position="right"
          size="small"
        />
        <ElButton size="small" :disabled="resolving" @click="handleSimulateScan">
          模拟扫码
        </ElButton>
        <ElButton size="small" :disabled="resolving" @click="handleFetchInStockTraceCodes">
          库取追溯码
        </ElButton>
      </div>
    </div>

    <template #footer>
      <div class="scan-dialog-foot">
        <span class="scan-dialog-foot__stat">
          {{ boundLineCount }}/{{ totalLineCount }} 行
          <template v-if="isCommaMode"> · {{ totalBoundCodeCount }} 码</template>
        </span>
        <div class="scan-dialog-foot__actions">
          <ElButton @click="handleCancel">取消</ElButton>
          <ElButton type="primary" :loading="resolving" @click="handleConfirm">确定</ElButton>
        </div>
      </div>
    </template>
  </AppDialog>
</template>

<style scoped lang="scss">
.scan-dialog-head {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.scan-dialog-head__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.scan-dialog-head__subtitle {
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-regular);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scan-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.scan-page__input :deep(.el-input__inner) {
  font-size: 16px;
  letter-spacing: 0.05em;
}

.scan-current-target {
  padding: 8px 12px;
  font-size: 13px;
  color: var(--el-text-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 6px;
}

.scan-current-target--empty {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color-lighter);
}

.scan-page__spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid var(--el-border-color);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: scan-spin 0.7s linear infinite;
}

@keyframes scan-spin {
  to {
    transform: rotate(360deg);
  }
}

.scan-split {
  display: flex;
  gap: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.scan-split__log {
  flex: 0 0 30%;
  min-width: 0;
  border-right: 1px solid var(--el-border-color-lighter);
}

.scan-split__lines {
  flex: 1;
  min-width: 0;
}

.scan-split__title {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.scan-split__title-stat {
  font-size: 12px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}

.scan-split__table {
  width: 100%;
}

.scan-split__table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.scan-split__table :deep(th.el-table__cell) {
  font-weight: 500;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-blank);
}

.scan-split__table :deep(.el-table__row) {
  cursor: pointer;
}

.line-expand {
  padding: 8px 12px 12px 36px;
  background: var(--el-fill-color-blank);
}

.line-expand__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.line-expand__table {
  width: 100%;
  margin-top: 6px;
}

.line-expand__table :deep(.el-table__row) {
  cursor: default;
}

.lot-group-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.lot-group__head {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 6px 8px;
  font-size: 13px;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.lot-group__arrow {
  width: 14px;
  font-size: 10px;
  color: var(--el-text-color-secondary);
}

.lot-group__lot {
  font-weight: 600;
}

.lot-group__count {
  margin-left: auto;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}

.scan-split__table :deep(.line-row--active) {
  background-color: var(--el-color-primary-light-9);
}

.scan-split__table :deep(.line-row--bound) {
  background-color: var(--el-color-success-light-9);
}

.scan-split__table :deep(.line-row--error) {
  background-color: var(--el-color-danger-light-9);
}

.log-result {
  font-size: 16px;
  font-weight: 600;
  line-height: 1;
}

.log-result--ok {
  color: var(--el-color-success);
}

.log-result--fail {
  color: var(--el-color-danger);
}

.pending-scan-cell {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.pending-scan-cell--pending {
  color: var(--el-color-warning);
}

.pending-scan-cell--done {
  color: var(--el-color-success);
}

.scanned-scan-cell {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.scanned-scan-cell--active {
  color: var(--el-color-primary);
}

.line-drug-cell {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.line-drug-cell__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scan-dev {
  display: flex;
  gap: 8px;
  align-items: center;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.scan-dialog-foot {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.scan-dialog-foot__stat {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.scan-dialog-foot__actions {
  display: flex;
  gap: 8px;
}
</style>
