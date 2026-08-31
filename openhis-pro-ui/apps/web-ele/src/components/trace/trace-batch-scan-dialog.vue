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
  ElMessageBox,
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
  expandTraceBoxCodes,
  extractTraceCodes,
  fetchHospitalWideInHospitalTraceItems,
  fetchInStockTraceCodes,
  filterNewTraceCodes,
  findNonStandardTraceDrugLines,
  generateRandomTraceCodes,
  findTraceMatchCandidateLines,
  groupHospitalWideTracesByLot,
  isNonStandardLengthTraceCode,
  isNonStandardTraceDrugName,
  isRemainingCoverageReached,
  isStandardTraceCode,
  isTraceSlotEmpty,
  pickTraceBindTargetLine,
  resolveMaxTraceCount,
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
/**
 * 扫码过程软提示（不打断）：code → 原因列表（可多条并存）。
 * 确定时一次汇总全部问题；硬拦仍即时 ElMessage。
 */
const softConfirmHintsByCode = ref<Record<string, string[]>>({});

const scenePreset = () => TRACE_SCAN_SCENE_PRESETS[props.scene];
const isCommaMode = computed(() => scenePreset().bindMode === 'comma');

/** 实际参与绑定的明细行 */
const effectiveLines = computed((): TraceBatchScanLine[] => props.lines);

/** 某行当前展示的追溯码（绑定表有键含空串=已清除，禁止回落 lines.traceNo） */
function effectiveTrace(row: TraceBatchScanLine): string {
  const v = traceByLineId.value[row.id];
  if (v !== undefined) return v.trim();
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

/** 明细中是否存在非标追溯码品（依洛尤单抗等）：扫码解析放宽 20 位 */
const hasNonStandardTraceDrugLine = computed(() =>
  effectiveLines.value.some((row) => isNonStandardTraceDrugName(row.itemName)),
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
  softConfirmHintsByCode.value = {};
});

/** 弹窗打开后聚焦扫码框 */
function handleDialogOpened(): void {
  void nextTick(() => {
    setTimeout(() => focusScanInput(), 30);
  });
}

/** 行是否仍需扫码（消耗侧：剩余未盖住或硬上限未满；进货按应扫个数） */
function lineStillNeedsScan(row: TraceBatchScanLine): boolean {
  const preset = scenePreset();
  if (preset.bindMode === 'comma') {
    const opts = lineCoverageOpts(row);
    const covered = isRemainingCoverageReached(row.operationSplitQty, opts?.boundRemainingSum);
    if (covered === true) return false;
    if (covered === false) return true;
    const pending = countPendingTraceScan(row, effectiveTrace(row), preset.bindMode, opts);
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
  // null/undefined/空串禁止 Number()：Number(null)===0 会把缺剩余误当成耗尽
  if (remaining === null || remaining === undefined || String(remaining).trim() === '') return;
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
 * 允许暂时离开扫码框的目标（开发模拟、页脚、关闭、展开区复制选码）。
 * 其余操作一律把焦点抢回扫码框，保证扫码枪随时可录入。
 */
function isAllowedAwayTarget(el: Element | null): boolean {
  if (!el || !(el instanceof HTMLElement)) return false;
  if (el.closest('.scan-dev')) return true;
  if (el.closest('.scan-dialog-foot__actions')) return true;
  if (el.closest('.el-dialog__headerbtn')) return true;
  // 展开在院码：须能拖选/点复制，禁止抢焦冲掉选区
  if (el.closest('.line-expand')) return true;
  if (el.closest('.scan-split__log')) return true;
  return false;
}

/** 当前是否有未折叠的文本选区（拖选复制中） */
function hasActiveTextSelection(): boolean {
  const sel = window.getSelection();
  if (!sel || sel.isCollapsed) return false;
  return String(sel.toString()).trim().length > 0;
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
    // 拖选复制中勿抢焦，否则选区瞬间消失无法 Ctrl+C
    if (hasActiveTextSelection()) return;
    const active = document.activeElement;
    if (isAllowedAwayTarget(active)) return;
    const native = getScanNativeInput();
    if (native && active === native) return;
    focusScanInput();
  }, 0);
}

/** 扫码框失焦时抢回（点表格/按钮/空白都会失焦；展开区/选区除外） */
function handleScanInputBlur(): void {
  scheduleFocusScanInput();
}

/**
 * 页面 mouseup：默认抢回扫码框；点在展开/流水或正在拖选时放过。
 *
 * @param event 鼠标抬起
 */
function handleScanPageMouseUp(event: MouseEvent): void {
  const target = event.target;
  if (target instanceof Element && isAllowedAwayTarget(target)) return;
  if (hasActiveTextSelection()) return;
  scheduleFocusScanInput();
}

/**
 * 复制展开区在院追溯码到剪贴板，便于手测粘贴进扫码框。
 *
 * @param raw 追溯码
 */
async function copyHospitalTraceCode(raw: string | number | null | undefined): Promise<void> {
  const code = String(raw ?? '').trim();
  if (!code || code === '—') return;
  try {
    await navigator.clipboard.writeText(code);
    ElMessage.success('已复制追溯码');
    // 复制后回到扫码框，便于 Ctrl+V
    focusScanInput();
  } catch {
    ElMessage.warning('复制失败，请在展开区拖选后 Ctrl+C');
  }
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
  // 缺剩余时按硬上限待扫；扫满上限再跳下一行（便于拼码）
  const pending = countPendingTraceScan(row, effectiveTrace(row), preset.bindMode, opts);
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
 * 未建档兜底目标：优先用户已锁定行；未锁定时仅当「只剩一行待绑」才自动落到该行。
 * 多行都待绑时不猜药品，避免新码误落到首行。
 */
function pickFallbackTargetLine(): TraceBatchScanLine | undefined {
  const preset = scenePreset();
  if (currentLineId.value) {
    const row = effectiveLines.value.find((line) => line.id === currentLineId.value);
    if (!row) return undefined;
    const cur = effectiveTrace(row);
    if (preset.bindMode === 'single' && isTraceSlotEmpty(cur)) return row;
    if (preset.bindMode === 'comma') return row;
    return undefined;
  }
  // 未锁定：仅一行仍需扫时允许软录入自动落到该行
  const pending = effectiveLines.value.filter((line) => lineStillNeedsScan(line));
  if (pending.length !== 1) return undefined;
  const only = pending[0]!;
  const cur = effectiveTrace(only);
  if (preset.bindMode === 'single' && isTraceSlotEmpty(cur)) return only;
  if (preset.bindMode === 'comma') return only;
  return undefined;
}

/**
 * 非标长度码按药名匹配目标行：优先已锁定的非标品行，否则在非标品候选中挑未满行。
 */
function pickNonStandardDrugTargetLine(traceNo: string): TraceBatchScanLine | undefined {
  const preset = scenePreset();
  const pinned = currentLine.value;
  if (pinned && isNonStandardTraceDrugName(pinned.itemName)) {
    const cur = effectiveTrace(pinned);
    if (canAcceptMoreTraceOnLine(pinned, cur, preset.bindMode, lineCoverageOpts(pinned))) {
      if (!(preset.bindMode === 'comma' && traceNoCommaContains(cur, traceNo))) {
        return pinned;
      }
    }
  }
  const candidates = findNonStandardTraceDrugLines(effectiveLines.value);
  return pickTraceBindTargetLine(
    candidates,
    traceNo,
    preset.bindMode,
    traceByLineId.value,
    remainingByTraceNo.value,
  );
}

/**
 * 当前锁定行允许 NOT_FOUND 软录入：
 * - 发药：已锁定即落到该行（锁定=专扫目标，与门诊/汇总同口径）；
 * - 其它场景：仅无码批。
 */
function softFallbackOk(): boolean {
  const row = currentLine.value;
  if (!row) return false;
  if (props.scene === 'dispense') return true;
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

/** 行剩余待扫数量（发药按硬上限；已盖住为 0） */
function linePendingScan(row: TraceBatchScanLine): number | null {
  return countPendingTraceScan(
    row,
    effectiveTrace(row),
    scenePreset().bindMode,
    lineCoverageOpts(row),
  );
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

/**
 * 药品明细「码倍」列：1 追溯码单位 = N 拆零（展示 ×N，省表头宽度）；无正数比例时「-」。
 *
 * @param row 扫码明细行
 */
function traceUnitRatioDisplay(row: TraceBatchScanLine): string {
  const ratio = Number(row.traceUnitRatio);
  if (!Number.isFinite(ratio) || ratio <= 0) return '-';
  return `×${ratio}`;
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

/**
 * 发药允许同一追溯码跨行复用（箱码/同品多明细共扫一码；BR-TRC-023）。
 * 进货等场景仍单内全局唯一。行内重复始终拒绝。
 */
function allowCrossRowSameTraceCode(): boolean {
  return props.scene === 'dispense';
}

/** 记录确定时再弹的软提示（同一码可累加多种原因，不覆盖） */
function rememberSoftConfirmHint(code: string, reason: string): void {
  const trimmed = code.trim();
  const tip = reason.trim();
  if (!trimmed || !tip) return;
  const prev = softConfirmHintsByCode.value[trimmed] ?? [];
  if (prev.includes(tip)) return;
  softConfirmHintsByCode.value = {
    ...softConfirmHintsByCode.value,
    [trimmed]: [...prev, tip],
  };
}

/**
 * 流水兜底回填软提示（漏记时确定仍能汇总）。
 * 仅认已成功落入流水的软继续类文案；同码多条原因全收。
 */
function harvestSoftHintsFromScanLog(): void {
  for (const row of scanLog.value) {
    if (row.status !== 'ok') continue;
    const code = String(row.code ?? '').trim();
    if (!code || code === '—') continue;
    const msg = String(row.message ?? '');
    if (msg.includes('不在院内')) {
      rememberSoftConfirmHint(code, '不在院内');
    }
    if (msg.includes('未建档') || msg.startsWith('已录入')) {
      rememberSoftConfirmHint(code, '未建档软录入');
    }
    if (msg.includes('已按原码继续') || msg.includes('采购入库') || msg.includes('展开失败')) {
      rememberSoftConfirmHint(code, '箱码未展开（采购入库/上游未出库），已按原码继续');
    }
  }
}

/** 收集当前明细上已绑的全部追溯码 */
function collectAllBoundTraceCodes(): string[] {
  const bound = new Set<string>();
  const bindMode = scenePreset().bindMode;
  for (const line of effectiveLines.value) {
    const cur = effectiveTrace(line);
    if (isTraceSlotEmpty(cur)) continue;
    if (bindMode === 'single') {
      const one = cur.trim();
      if (one) bound.add(one);
      continue;
    }
    for (const code of splitTraceNoCommaString(cur)) {
      if (code) bound.add(code);
    }
  }
  return [...bound];
}

/**
 * 确定前对已绑码重新反查，补齐「不在院/未建档」。
 * 流水与 hint 会在弹窗重开时清空，但已绑码仍在，必须以反查为准。
 */
async function refreshSoftHintsFromBoundCodes(): Promise<void> {
  const codes = collectAllBoundTraceCodes();
  if (codes.length === 0) return;

  try {
    const res = await batchResolveTraceIdentityByPreset(codes, scenePreset());
    for (const code of codes) {
      const fail = res.failItems?.find((f) => (f.traceNo ?? '').trim() === code);
      if (fail) {
        const st = String(fail.resolveStatusEnum ?? '').trim();
        // 发药软录入口径：未建档 / 不在院都提示「不在院内」
        if (st === 'NOT_IN_HOSPITAL' || st === 'NOT_FOUND') {
          rememberSoftConfirmHint(code, '不在院内');
        }
        continue;
      }

      const item = res.items?.find((i) => (i.traceNo ?? '').trim() === code);
      if (!item) {
        rememberSoftConfirmHint(code, '不在院内');
        continue;
      }

      const st = String(item.resolveStatusEnum ?? 'OK').trim();
      if (st === 'NOT_IN_HOSPITAL' || st === 'NOT_FOUND') {
        rememberSoftConfirmHint(code, '不在院内');
        continue;
      }
      // 主档剩余耗尽：一并告知
      const rem = Number(item.remainingSplitQuantity);
      if (Number.isFinite(rem) && rem <= 0) {
        rememberSoftConfirmHint(code, '追溯码剩余已耗尽');
      }
    }
  } catch {
    // 反查失败不挡确定；会话 hint / 流水仍可用
  }
}

/** 转义 MessageBox HTML，避免码内容破坏版式 */
function escapeMessageHtml(raw: string): string {
  return raw
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

/** 码级软提示正文（不在院 / 箱码未展开 / 未建档；同码多因都列） */
function buildCodeSoftHintSection(): string {
  harvestSoftHintsFromScanLog();
  const hints = softConfirmHintsByCode.value;
  if (Object.keys(hints).length === 0) return '';

  // 按原因归并：同一码可出现在多个原因下
  const codesByReason = new Map<string, string[]>();
  for (const [code, reasons] of Object.entries(hints)) {
    for (const reason of reasons) {
      const list = codesByReason.get(reason) ?? [];
      if (!list.includes(code)) list.push(code);
      codesByReason.set(reason, list);
    }
  }

  const parts: string[] = ['【追溯码问题】'];
  let idx = 1;
  for (const [reason, codes] of codesByReason) {
    const shown = codes.join('、');
    parts.push(`${idx}. ${reason}：${shown}`);
    idx += 1;
  }
  return parts.join('\n');
}

/** 待扫数量仍 > 0 的药品行（已达硬上限的不报；全部不足行都列） */
function buildPendingScanShortageSection(): string {
  const lines: string[] = [];
  for (const row of effectiveLines.value) {
    const scanned = lineScannedCount(row);
    const maxCodes = resolveMaxTraceCount(row);
    // 已扫满硬上限：不报数量不足（避免「待扫=已扫」误报）
    if (maxCodes !== null && scanned >= maxCodes) continue;
    const pending = linePendingScan(row);
    if (pending === null || pending <= 0) continue;
    lines.push(`「${lineDisplayName(row)}」还需扫 ${pending}（已扫 ${scanned}）`);
  }
  if (lines.length === 0) return '';
  const parts = ['【扫码数量不足】'];
  lines.forEach((line, i) => {
    parts.push(`${i + 1}. ${line}`);
  });
  return parts.join('\n');
}

/**
 * 跨行共扫同码剩余不足文案（全部不足码都列，不拦截）。
 * 仅当同一码实际绑在 ≥2 行，且多行操作拆零合计 &gt; 主档剩余。
 */
function buildCrossRowRemainingShortageSection(): string {
  if (!allowCrossRowSameTraceCode()) return '';
  const bindMode = scenePreset().bindMode;
  // 码 → 已绑该码的行数 / 操作拆零合计
  const lineCountByCode = new Map<string, number>();
  const plannedByCode = new Map<string, number>();

  for (const line of effectiveLines.value) {
    const cur = effectiveTrace(line);
    if (isTraceSlotEmpty(cur)) continue;
    const codes =
      bindMode === 'single' ? [cur.trim()].filter(Boolean) : splitTraceNoCommaString(cur);
    const op = Number(line.operationSplitQty);
    if (!Number.isFinite(op) || op <= 0) continue;
    // 行内同码只计一次
    const seenOnLine = new Set<string>();
    for (const code of codes) {
      if (!code || seenOnLine.has(code)) continue;
      seenOnLine.add(code);
      lineCountByCode.set(code, (lineCountByCode.get(code) ?? 0) + 1);
      plannedByCode.set(code, (plannedByCode.get(code) ?? 0) + op);
    }
  }

  const shortageCodes: string[] = [];
  for (const [code, plannedSplit] of plannedByCode) {
    // 单行多码拼盖不算跨行共扫
    if ((lineCountByCode.get(code) ?? 0) < 2) continue;
    const rem = Number(remainingByTraceNo.value[code]);
    if (!Number.isFinite(rem) || rem < 0) continue;
    if (plannedSplit > rem + 1e-9) {
      shortageCodes.push(`${code}（计划拆零 ${plannedSplit}，主档剩余 ${rem}）`);
    }
  }
  if (shortageCodes.length === 0) return '';
  const parts = ['【跨行共扫剩余不足】'];
  shortageCodes.forEach((line, i) => {
    parts.push(`${i + 1}. ${line}`);
  });
  return parts.join('\n');
}

/** 确定时一次汇总全部问题（码软提示 + 待扫不足 + 跨行剩余不足） */
function buildConfirmWarningText(): string {
  const sections = [
    buildCodeSoftHintSection(),
    buildPendingScanShortageSection(),
    buildCrossRowRemainingShortageSection(),
  ].filter((s) => s !== '');
  if (sections.length === 0) return '';
  return `请确认无误后继续：\n\n${sections.join('\n\n')}`;
}

/** 纯文本转 MessageBox HTML（保留换行，全部问题可见） */
function toConfirmWarningHtml(text: string): string {
  return escapeMessageHtml(text).replaceAll('\n', '<br/>');
}

function markNotInHospitalLines(itemId: string, lotNumber: string): void {
  const candidates = findTraceMatchCandidateLines(effectiveLines.value, itemId, lotNumber);
  if (candidates.length === 1) markLineError(candidates[0]!.id);
}

/**
 * 行已扫满/已盖住时是否拒绝再绑。
 *
 * <p>进货（{@link enforceScanCountGate}）超限一律硬拒。
 * 消耗侧：超过 {@code maxTraceCount} 或剩余已盖住 → 硬拒（扫多）；
 * 有码批同拒；无码批仅因「建议应扫」触顶时软提示仍可绑（BR-TRC-020）。</p>
 */
function rejectIfLineScanFull(row: TraceBatchScanLine, code: string): boolean {
  const preset = scenePreset();
  const cur = effectiveTrace(row);
  const opts = lineCoverageOpts(row);
  if (canAcceptMoreTraceOnLine(row, cur, preset.bindMode, opts)) return false;
  const msg = traceLineScanFullMessage(row, preset.bindMode, opts);
  pushLog(code, 'invalid', msg);
  ElMessage.warning(msg);

  const bound = countBoundTraceCodesOnLine(cur, preset.bindMode);
  const maxCodes = resolveMaxTraceCount(row);
  const covered = isRemainingCoverageReached(row.operationSplitQty, opts?.boundRemainingSum);
  // 进货硬 Gate / 扫多硬上限 / 已盖住 / 有码批：硬拒
  if (
    enforceScanCountGate.value ||
    (maxCodes !== null && bound >= maxCodes) ||
    covered === true ||
    isHardTraceLine(row)
  ) {
    markLineError(row.id);
    return true;
  }
  // 无码批且仅触「建议应扫」：软提示不拦
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

  const rowAllowsNonStandard = isNonStandardTraceDrugName(row.itemName);
  if (!isStandardTraceCode(trimmed)) {
    if (!rowAllowsNonStandard || !isNonStandardLengthTraceCode(trimmed)) {
      pushLog(trimmed, 'invalid', `须为 ${TRACE_CODE_LENGTH} 位数字`);
      ElMessage.warning(`追溯码须为 ${TRACE_CODE_LENGTH} 位数字`);
      return false;
    }
  }

  const preset = scenePreset();
  // 发药：不跨行去重；其它场景码在弹窗内全局唯一
  if (!allowCrossRowSameTraceCode()) {
    const boundPool = collectBoundTraceCodes(effectiveLines.value, traceByLineId.value);
    if (isTraceBoundInSession(trimmed, boundPool)) {
      ElMessage.warning('该追溯码已绑定');
      return false;
    }
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
  const crossRowOk = allowCrossRowSameTraceCode();

  // 进货等：弹窗内全局去重；发药允许同码跨行（箱码复用）
  if (!crossRowOk && isTraceBoundInSession(trimmed, boundPool)) {
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

  // 非发药：码已在其它行则拒；发药仅行内去重
  if (!crossRowOk) {
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
  // 发药跨行复用时不把码写入全局占用池，便于下一行再绑同码
  if (!crossRowOk) {
    boundPool.add(trimmed);
  }
  clearLineError(target.id);
  stats.bound += 1;
  pushLog(trimmed, 'ok', `已绑定 → ${lineDisplayName(target)}`);
  advanceAfterBind(target);
  return 'bound';
}

/**
 * 发药场景：主档已存在且剩余耗尽须硬拦（与后端 assertMasterCodesNotExhaustedForDispense 同口径）。
 */
function rejectExhaustedMasterOnScene(): boolean {
  return props.scene === 'dispense';
}

/**
 * 反查结果是否主档耗尽：NOT_IN_HOSPITAL，或剩余拆零 ≤ 0。
 *
 * @param resolveStatus 反查状态
 * @param remainingSplit 主档剩余拆零（可空）
 */
function isMasterExhausted(
  resolveStatus: string,
  remainingSplit?: null | number | string,
): boolean {
  if (resolveStatus === 'NOT_IN_HOSPITAL') return true;
  if (remainingSplit === undefined || remainingSplit === null || remainingSplit === '') {
    return false;
  }
  const rem = Number(remainingSplit);
  return Number.isFinite(rem) && rem <= 0;
}

/**
 * 从已展开的全院在院码缓存中，按追溯码反查所属明细行。
 * 反查 NOT_FOUND / 未锁定时作自动匹配兜底（用户已在展开区见过该码）。
 *
 * @param traceNo 追溯码
 */
function findLineByHospitalCachedTraceNo(
  traceNo: string,
): { line: TraceBatchScanLine; item: InventoryItemTraceNumberRowDto } | undefined {
  const want = String(traceNo ?? '').trim();
  if (!want) return undefined;
  for (const line of effectiveLines.value) {
    const items = hospitalTraceByLineId.value[line.id] ?? [];
    for (const item of items) {
      if (String(item.traceNo ?? '').trim() === want) {
        return { line, item };
      }
    }
  }
  return undefined;
}

/**
 * 把码绑到指定行（软录入 / 缓存命中共用）；成功则写流水与统计。
 *
 * @param row 目标行
 * @param trimmed 追溯码
 * @param boundPool 占用池
 * @param stats 批量统计
 * @param logMessage 流水说明
 */
function bindCodeToLineWithStats(
  row: TraceBatchScanLine,
  trimmed: string,
  boundPool: Set<string>,
  stats: BatchBindStats,
  logMessage: string,
): BindResult {
  if (rejectIfLineScanFull(row, trimmed)) {
    stats.invalid += 1;
    return 'invalid';
  }
  const preset = scenePreset();
  traceByLineId.value = {
    ...traceByLineId.value,
    [row.id]: applyTraceBind(effectiveTrace(row), trimmed, preset.bindMode),
  };
  boundPool.add(trimmed);
  clearLineError(row.id);
  stats.bound += 1;
  pushLog(trimmed, 'ok', logMessage);
  advanceAfterBind(row);
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

  // 发药：主档耗尽硬拦（剩余为 0 / 不在院）
  if (resolveStatus === 'NOT_IN_HOSPITAL' && rejectExhaustedMasterOnScene()) {
    stats.notInHospital += 1;
    pushLog(trimmed, 'not_in_hospital', '追溯码剩余已耗尽，不可再使用');
    ElMessage.error('追溯码剩余已耗尽，不可再使用');
    return 'not_in_hospital';
  }

  // 进货等仍可硬拒不在院
  if (resolveStatus === 'NOT_IN_HOSPITAL' && preset.rejectNotInHospital) {
    stats.notInHospital += 1;
    pushLog(trimmed, 'not_in_hospital', '追溯码不在院内或已耗尽');
    ElMessage.warning('追溯码不在院内或已耗尽');
    return 'not_in_hospital';
  }

  // 未建档软录入（发药耗尽/不在院硬拦不走此支）；不在院仅非发药软场景可落入
  const softIdentity =
    resolveStatus === 'NOT_FOUND' ||
    (resolveStatus === 'NOT_IN_HOSPITAL' && !rejectExhaustedMasterOnScene());
  if (softIdentity && (preset.allowNotFound || softFallbackOk())) {
    if (!allowCrossRowSameTraceCode() && isTraceBoundInSession(trimmed, boundPool)) {
      stats.duplicate += 1;
      return 'duplicate';
    }
    // 展开区已拉过该码：未锁定也可按缓存自动绑到对应药品（仍算自动匹配）
    const cached = findLineByHospitalCachedTraceNo(trimmed);
    if (cached && (preset.allowNotFound || !isHardTraceLine(cached.line) || softFallbackOk())) {
      rememberTraceRemaining(trimmed, cached.item.remainingSplitQuantity);
      return bindCodeToLineWithStats(
        cached.line,
        trimmed,
        boundPool,
        stats,
        `已绑定 → ${lineDisplayName(cached.line)}`,
      );
    }
    const fb = pickFallbackTargetLine();
    if (fb && (preset.allowNotFound || !isHardTraceLine(fb) || softFallbackOk())) {
      if (props.scene === 'dispense') {
        if (isHardTraceLine(fb)) {
          rememberSoftConfirmHint(trimmed, '不在院内');
          return bindCodeToLineWithStats(
            fb,
            trimmed,
            boundPool,
            stats,
            `不在院内 → ${lineDisplayName(fb)}`,
          );
        }
        rememberSoftConfirmHint(trimmed, '未建档软录入');
      }
      return bindCodeToLineWithStats(
        fb,
        trimmed,
        boundPool,
        stats,
        `已录入 → ${lineDisplayName(fb)}`,
      );
    }
    stats.notFound += 1;
    // 主档未命中才需要点选；主档命中会走自动匹配，勿与「自动匹配」文案打架
    pushLog(trimmed, 'not_found', '主档未找到该码，请点选右侧药品后再扫');
    ElMessage.warning('主档未找到该追溯码，无法自动匹配。请先点选右侧药品再扫（软录入）');
    return 'not_found';
  }

  stats.notFound += 1;
  const msg = resolveStatus === 'NOT_FOUND' ? '未找到该追溯码' : '无法使用该追溯码';
  pushLog(trimmed, 'not_found', msg);
  ElMessage.warning(msg);
  return 'not_found';
}

/**
 * 非标长度码：跳过主档反查，按药名绑到依洛尤单抗等非标品行。
 * @returns 仍须走标准反查的码列表
 */
function bindNonStandardLengthCodes(
  codes: string[],
  boundPool: Set<string>,
  stats: BatchBindStats,
  crossRowOk: boolean,
): string[] {
  const standard: string[] = [];
  for (const raw of codes) {
    const trimmed = raw.trim();
    if (!isNonStandardLengthTraceCode(trimmed)) {
      standard.push(trimmed);
      continue;
    }
    if (!crossRowOk && isTraceBoundInSession(trimmed, boundPool)) {
      stats.duplicate += 1;
      continue;
    }
    const target = pickNonStandardDrugTargetLine(trimmed);
    if (!target) {
      stats.invalid += 1;
      pushLog(trimmed, 'invalid', '非标追溯码仅可绑到依洛尤单抗等指定药品，请点选对应行后再扫');
      ElMessage.warning('非标追溯码请点选「依洛尤单抗」等对应药品后再扫');
      continue;
    }
    if (bindManualCodeToLine(target, trimmed)) {
      boundPool.add(trimmed);
      stats.bound += 1;
    } else {
      stats.invalid += 1;
    }
  }
  return standard;
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
  const crossRowOk = allowCrossRowSameTraceCode();
  // 发药不把其它行已绑码当占用，否则同箱码无法再绑到下一行氯化钠
  const boundPool = crossRowOk
    ? new Set<string>()
    : collectBoundTraceCodes(effectiveLines.value, traceByLineId.value);
  const toResolveAll = filterNewTraceCodes(codes, boundPool);
  stats.duplicate += codes.length - toResolveAll.length;
  if (toResolveAll.length === 0) return stats;

  // 非标长度码先按药名绑定，剩余标准 20 位再反查
  const toResolve = bindNonStandardLengthCodes(toResolveAll, boundPool, stats, crossRowOk);
  if (toResolve.length === 0) return stats;

  /**
   * 进货/盘点/损益锁定行：可跳过反查直接录入。
   * 发药锁定行仍须反查——主档耗尽要硬拦，未建档再软落到本行。
   */
  if (
    currentLine.value &&
    props.scene !== 'dispense' &&
    (preset.allowNotFound || !isHardTraceLine(currentLine.value))
  ) {
    for (const trimmed of toResolve) {
      if (!crossRowOk && isTraceBoundInSession(trimmed, boundPool)) {
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
      if (!crossRowOk && isTraceBoundInSession(trimmed, boundPool)) {
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

      // 发药：主档存在且剩余耗尽 → 硬拦（含 NOT_IN_HOSPITAL / remaining≤0）
      if (
        rejectExhaustedMasterOnScene() &&
        isMasterExhausted(resolveStatus, item.remainingSplitQuantity)
      ) {
        stats.notInHospital += 1;
        if (itemId && lotNumber) markNotInHospitalLines(itemId, lotNumber);
        pushLog(trimmed, 'not_in_hospital', '追溯码剩余已耗尽，不可再使用');
        ElMessage.error('追溯码剩余已耗尽，不可再使用');
        continue;
      }

      if (resolveStatus === 'NOT_IN_HOSPITAL' && preset.rejectNotInHospital) {
        stats.notInHospital += 1;
        if (itemId && lotNumber) markNotInHospitalLines(itemId, lotNumber);
        pushLog(trimmed, 'not_in_hospital', '追溯码不在院内或已耗尽');
        ElMessage.warning('追溯码不在院内或已耗尽');
        continue;
      }

      // 非发药消耗侧：不在院不硬拦，仍按品批匹配绑定（发药耗尽已在上方硬拦）
      if (resolveStatus === 'NOT_IN_HOSPITAL' && !preset.rejectNotInHospital) {
        if (itemId && lotNumber) {
          rememberTraceRemaining(trimmed, item.remainingSplitQuantity);
          bindResolvedItem(trimmed, itemId, lotNumber, boundPool, stats);
          continue;
        }
        bindFailedResolveItem(trimmed, resolveStatus, boundPool, stats);
        continue;
      }

      // 未建档软录入（发药锁定行 softFallback / 进货 allowNotFound）
      if (resolveStatus === 'NOT_FOUND' && (preset.allowNotFound || softFallbackOk())) {
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
        (preset.allowNotFound || softFallbackOk()) &&
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

  const { bad, ok } = extractTraceCodes(raw, {
    allowNonStandardLength: hasNonStandardTraceDrugLine.value,
  });
  if (bad.length > 0) {
    for (const b of bad) {
      pushLog(b, 'invalid', `须为 ${TRACE_CODE_LENGTH} 位数字`);
    }
    ElMessage.warning(`有 ${bad.length} 段长度不符合 ${TRACE_CODE_LENGTH} 位，已跳过`);
  }

  let codes = dedupeTraceCodes(ok);
  // 非标长度码（GS1 等）不走箱码展开，直接按药名绑定
  const nonStandardCodes = codes.filter((c) => isNonStandardLengthTraceCode(c));
  const standardForExpand = codes.filter((c) => !isNonStandardLengthTraceCode(c));
  // 箱/中码 → 小码（msfx query.relation；模块未启用时保留原码）
  if (standardForExpand.length > 0) {
    try {
      const { expanded, items } = await expandTraceBoxCodes(standardForExpand);
      for (const item of items) {
        if (!item.ok) {
          pushLog(item.sourceCode, 'invalid', item.message || '箱码展开失败');
        } else if (item.softFallback) {
          // 码上放心展开失败：原码仍继续绑定；确定时汇总，扫码不弹
          pushLog(item.sourceCode, 'ok', item.message || '展开失败，已按原码继续');
          rememberSoftConfirmHint(
            item.sourceCode,
            '箱码未展开（采购入库/上游未出库），已按原码继续',
          );
        } else if (item.message && item.childCodes.length > 1) {
          pushLog(item.sourceCode, 'ok', `展开 ${item.childCodes.length} 个小码`);
        }
      }
      // 硬失败仍即时提示（码未进入绑定）；软降级只记 hint
      const failed = items.filter((i) => !i.ok);
      if (failed.length > 0) {
        ElMessage.warning(failed[0]?.message || '部分箱码无法展开');
      }
      codes = [...nonStandardCodes, ...expanded];
    } catch {
      // 接口不可用：按原码继续绑定
      codes = [...nonStandardCodes, ...standardForExpand];
    }
  } else {
    codes = nonStandardCodes;
  }

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

/** 确定：先反查已绑码补齐不在院等，再汇总确认；取消留页，确定关窗 */
async function handleConfirm(): Promise<void> {
  resolving.value = true;
  try {
    // 流水重开会清空，必须以当前已绑码反查为准
    await refreshSoftHintsFromBoundCodes();
  } finally {
    resolving.value = false;
  }

  const hintText = buildConfirmWarningText();
  if (!hintText) {
    visible.value = false;
    return;
  }
  try {
    await ElMessageBox.confirm(toConfirmWarningHtml(hintText), '追溯码确认提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
      dangerouslyUseHTMLString: true,
      distinguishCancelAndClose: true,
      // 盖过扫码 AppDialog，避免被挡住
      zIndex: 4000,
    });
    // 确定：关闭扫码页
    visible.value = false;
  } catch (action) {
    // 取消 / 点右上角关闭：继续留在扫码页
    if (action === 'cancel' || action === 'close') {
      return;
    }
    // 弹窗异常：兜底提示且不关窗，避免静默丢问题
    ElMessage.warning(hintText.replace(/\n+/g, '；'));
  }
}

/** 清除指定行已绑追溯码（写空串保留键，避免 effectiveTrace 回落行上旧码） */
function clearLineTrace(row: TraceBatchScanLine): void {
  traceByLineId.value = {
    ...traceByLineId.value,
    [row.id]: '',
  };
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

    <div class="scan-page" @mouseup="handleScanPageMouseUp">
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
                        <ElTableColumn label="追溯码" min-width="220">
                          <template #default="{ row: item }">
                            <div class="hospital-trace-code-cell">
                              <span class="hospital-trace-code-cell__text font-mono">{{
                                item.traceNo || '—'
                              }}</span>
                              <ElButton
                                v-if="String(item.traceNo ?? '').trim()"
                                link
                                type="primary"
                                size="small"
                                class="hospital-trace-code-cell__copy"
                                @click.stop="copyHospitalTraceCode(item.traceNo)"
                              >
                                复制
                              </ElButton>
                            </div>
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
            <ElTableColumn label="追溯码单位" width="88" align="center" show-overflow-tooltip>
              <template #default="{ row }">
                {{ unitDisplayLabel(row.traceUnitCode, row.traceUnitCodeLabel) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="码倍" width="56" align="center" show-overflow-tooltip>
              <template #default="{ row }">
                {{ traceUnitRatioDisplay(row) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="追溯码" min-width="240">
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

.hospital-trace-code-cell {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.hospital-trace-code-cell__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  user-select: text;
  cursor: text;
}

.hospital-trace-code-cell__copy {
  flex-shrink: 0;
  padding: 0;
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
