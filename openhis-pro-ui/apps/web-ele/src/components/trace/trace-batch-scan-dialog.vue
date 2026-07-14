<script setup lang="ts">
import type {
  TraceBatchScanLine,
  TraceScanLogItem,
  TraceScanLogStatus,
  TraceScanSceneKey,
} from '#/utils/trace';

import { computed, nextTick, ref, watch } from 'vue';

import {
  ElButton,
  ElCheckbox,
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
  TRACE_MOCK_DISPENSE_LINES,
  TRACE_MOCK_IDENTITY_BY_CODE,
  TRACE_MOCK_SCAN_LINES,
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
  fetchInStockTraceCodes,
  filterNewTraceCodes,
  generateRandomTraceCodes,
  findTraceMatchCandidateLines,
  isTraceSlotEmpty,
  pickTraceBindTargetLine,
  pickTraceFallbackLineForNotFound,
  resolveRequiredTraceCount,
  splitTraceNoCommaString,
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
    /** 开发环境强制使用本地演示明细 */
    useMockLines?: boolean;
    /** 明细行未带 locationId 时的默认库房（查在库追溯码用） */
    defaultLocationId?: string | number;
  }>(),
  {
    scene: 'dispense',
    subtitle: '',
    title: '批量扫描追溯码',
    showRxNoColumn: false,
    useMockLines: false,
    defaultLocationId: '',
  },
);

const SCENE_LABELS: Record<TraceScanSceneKey, string> = {
  dispense: '发药',
  inbound: '进货',
  inventoryOut: '库房出库',
  stocktake: '盘点损益',
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
const currentLineId = ref('');
/** 各行是否按箱码展开（key = lineId） */
const boxScanByLineId = ref<Record<string, boolean>>({});

const scenePreset = () => TRACE_SCAN_SCENE_PRESETS[props.scene];
const isCommaMode = computed(() => scenePreset().bindMode === 'comma');

/** 进货/盘点：先选行再录入，不依赖库内反查 */
const isTargetFirstMode = computed(() => scenePreset().allowNotFound);

/** 开发环境且无传入明细时，使用本地演示数据 */
const usingMockLines = computed(() => isDev && (props.useMockLines || props.lines.length === 0));

/** 实际参与绑定的明细行 */
const effectiveLines = computed((): TraceBatchScanLine[] => {
  if (usingMockLines.value) {
    return props.showRxNoColumn ? TRACE_MOCK_DISPENSE_LINES : TRACE_MOCK_SCAN_LINES;
  }
  return props.lines;
});

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

/** 当前扫入目标展示文案 */
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

/** 弹窗打开时拉快照并重置 */
watch(visible, (open) => {
  if (!open) return;
  snapshotTrace.value = { ...traceByLineId.value };
  scanInput.value = '';
  mockScanCount.value = 1;
  scanLog.value = [];
  rowAlertById.value = {};
  currentLineId.value = '';
  initBoxScanByLine();
});

/** 初始化各行箱码开关（默认不勾选） */
function initBoxScanByLine(): void {
  const next: Record<string, boolean> = {};
  for (const row of effectiveLines.value) {
    next[row.id] = false;
  }
  boxScanByLineId.value = next;
}

function isLineBoxScanEnabled(lineId: string): boolean {
  return boxScanByLineId.value[lineId] ?? false;
}

function setLineBoxScan(lineId: string, enabled: boolean): void {
  boxScanByLineId.value = { ...boxScanByLineId.value, [lineId]: enabled };
}

function handleLineBoxScanChange(row: TraceBatchScanLine, enabled: boolean): void {
  setLineBoxScan(row.id, enabled);
  currentLineId.value = row.id;
}

/** 当前选中行是否箱码模式 */
function currentLineBoxScanEnabled(): boolean {
  if (!currentLineId.value) return false;
  return isLineBoxScanEnabled(currentLineId.value);
}

/** 弹窗打开后聚焦扫码框并选中首行待绑 */
function handleDialogOpened(): void {
  void nextTick(() => {
    initCurrentLine();
    setTimeout(() => scanInputRef.value?.focus?.(), 30);
  });
}

/** 选中第一个待绑行，否则首行 */
function initCurrentLine(): void {
  const needScan = displayLines.value.find((row) => lineStillNeedsScan(row));
  if (needScan) {
    currentLineId.value = needScan.id;
    return;
  }
  currentLineId.value = displayLines.value[0]?.id ?? '';
}

/** 行是否仍需扫码（comma 看待扫数量，single 看是否未绑） */
function lineStillNeedsScan(row: TraceBatchScanLine): boolean {
  const preset = scenePreset();
  if (preset.bindMode === 'comma' && preset.allowNotFound) {
    const pending = countPendingTraceScan(row, effectiveTrace(row), preset.bindMode);
    if (pending === null) return lineDisplayStatus(row) === 'pending';
    return pending > 0;
  }
  return lineDisplayStatus(row) === 'pending';
}

/** 多药品时须先选中目标行 */
function ensureScanTargetLine(): boolean {
  if (!isTargetFirstMode.value) return true;
  if (effectiveLines.value.length <= 1) {
    if (!currentLineId.value && effectiveLines.value[0]) {
      currentLineId.value = effectiveLines.value[0]!.id;
    }
    return true;
  }
  if (currentLine.value) return true;
  ElMessage.warning('请先在右侧选择要录入的药品');
  return false;
}

/** 聚焦扫码框 */
function focusScanInput(): void {
  void nextTick(() => scanInputRef.value?.focus?.());
}

/** 选中指定明细行 */
function selectLine(row: TraceBatchScanLine): void {
  currentLineId.value = row.id;
}

/** 按序号选中目标（键盘 1-9） */
function selectLineByIndex(index: number): void {
  const row = displayLines.value[index];
  if (row) currentLineId.value = row.id;
}

/** 按展示顺序切换当前行 */
function selectAdjacentLine(delta: -1 | 1): void {
  const lines = displayLines.value;
  if (lines.length === 0) return;
  const idx = lines.findIndex((row) => row.id === currentLineId.value);
  const base = idx >= 0 ? idx : 0;
  const next = (base + delta + lines.length) % lines.length;
  currentLineId.value = lines[next]!.id;
}

/** 单行绑满后自动跳到下一待绑行 */
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
  if (next) currentLineId.value = next.id;
}

/** 绑定成功后：single 或 comma 扫满则切下一行 */
function advanceAfterBind(row: TraceBatchScanLine): void {
  const preset = scenePreset();
  if (preset.bindMode === 'single') {
    advanceToNextPendingLine();
    return;
  }
  if (preset.allowNotFound) {
    const pending = countPendingTraceScan(row, effectiveTrace(row), preset.bindMode);
    if (pending === 0) advanceToNextPendingLine();
  }
}

/** 扫码框键盘：Enter 提交，↑↓/数字切换目标 */
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
  if (num >= 1 && num <= 9 && !event.ctrlKey && !event.altKey) {
    selectLineByIndex(num - 1);
  }
}

/** 未匹配时优先绑到当前选中行 */
function pickFallbackTargetLine(): TraceBatchScanLine | undefined {
  const preset = scenePreset();
  if (currentLineId.value) {
    const row = effectiveLines.value.find((line) => line.id === currentLineId.value);
    if (row) {
      const cur = effectiveTrace(row);
      if (preset.bindMode === 'single' && isTraceSlotEmpty(cur)) return row;
      if (preset.bindMode === 'comma') return row;
    }
  }
  return pickTraceFallbackLineForNotFound(
    effectiveLines.value,
    traceByLineId.value,
    preset.bindMode,
  );
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

/** 行已扫满时拒绝绑码并提示 */
function rejectIfLineScanFull(row: TraceBatchScanLine, code: string): boolean {
  const preset = scenePreset();
  const cur = effectiveTrace(row);
  if (canAcceptMoreTraceOnLine(row, cur, preset.bindMode)) return false;
  const msg = traceLineScanFullMessage(row, preset.bindMode);
  pushLog(code, 'invalid', msg);
  ElMessage.warning(msg);
  markLineError(row.id);
  return true;
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
  currentLineId.value = row.id;
  pushLog(trimmed, 'ok', `已录入 → ${lineDisplayName(row)}`);
  advanceAfterBind(row);
  return true;
}

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
  let target = pickTraceBindTargetLine(candidates, trimmed, preset.bindMode, traceByLineId.value);

  if (!target && preset.allowNotFound) {
    target = pickFallbackTargetLine();
  }

  if (!target) {
    stats.notFound += 1;
    if (
      candidates.length > 0 &&
      candidates.every(
        (line) => !canAcceptMoreTraceOnLine(line, effectiveTrace(line), preset.bindMode),
      )
    ) {
      const msg = traceLineScanFullMessage(candidates[0]!, preset.bindMode);
      pushLog(trimmed, 'invalid', msg);
      ElMessage.warning(msg);
      markLineError(candidates[0]!.id);
    } else {
      pushLog(trimmed, 'not_found', '未找到匹配的明细，请按数字键切换目标');
      ElMessage.warning('未找到匹配的明细，请先选择绑码目标');
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
  currentLineId.value = target.id;
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

  if (resolveStatus === 'NOT_FOUND' && preset.allowNotFound) {
    if (isTraceBoundInSession(trimmed, boundPool)) {
      stats.duplicate += 1;
      return 'duplicate';
    }
    const fb = pickFallbackTargetLine();
    if (fb) {
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
      currentLineId.value = fb.id;
      stats.bound += 1;
      pushLog(trimmed, 'ok', `新码录入 → ${lineDisplayName(fb)}`);
      advanceAfterBind(fb);
      return 'bound';
    }
    stats.notFound += 1;
    pushLog(trimmed, 'not_found', '未建档，请先选择绑码目标');
    ElMessage.warning('未建档，请先选择绑码目标');
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

  /** 进货/盘点：有选中行时直接录入当前行，跳过反查 */
  if (isTargetFirstMode.value && currentLine.value) {
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
    if (usingMockLines.value) {
      for (const trimmed of toResolve) {
        if (isTraceBoundInSession(trimmed, boundPool)) {
          stats.duplicate += 1;
          continue;
        }
        const identity = TRACE_MOCK_IDENTITY_BY_CODE[trimmed];
        if (identity) {
          bindResolvedItem(trimmed, identity.itemId, identity.lotNumber, boundPool, stats);
          continue;
        }
        if (
          preset.allowNotFound &&
          currentLine.value &&
          bindManualCodeToLine(currentLine.value, trimmed)
        ) {
          boundPool.add(trimmed);
          stats.bound += 1;
          continue;
        }
        stats.notFound += 1;
        pushLog(trimmed, 'not_found', '演示数据未收录该码，请换演示码或切换目标后重试');
      }
      return stats;
    }

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

  if (!ensureScanTargetLine()) {
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
  if (currentLineBoxScanEnabled() && codes.length > 0) {
    resolving.value = true;
    try {
      const { expanded, items } = await expandTraceBoxCodes(codes, {
        useMock: usingMockLines.value,
      });
      for (const item of items) {
        if (!item.ok) {
          pushLog(item.sourceCode, 'not_found', item.message ?? '箱码展开失败');
          continue;
        }
        if (item.childCodes.length > 1) {
          pushLog(item.sourceCode, 'ok', `箱码展开 → ${item.childCodes.length} 个小码`);
        }
      }
      if (expanded.length === 0) {
        ElMessage.warning('箱码展开失败，请改扫小码或确认该箱码已入库');
        focusScanInput();
        return;
      }
      codes = expanded;
    } catch {
      for (const code of codes) {
        pushLog(code, 'not_found', '箱码展开接口不可用');
      }
      ElMessage.warning('箱码展开失败，请改扫小码或稍后重试');
      focusScanInput();
      return;
    } finally {
      resolving.value = false;
    }
  }

  await processBatchBind(codes);
  focusScanInput();
}

/** 模拟扫码（仅开发环境）：按数量随机生成追溯码 */
async function handleSimulateScan(): Promise<void> {
  if (!ensureScanTargetLine()) return;

  const codes = generateRandomTraceCodes(mockScanCount.value);
  await processBatchBind(codes);
  focusScanInput();
}

/** 从本库在库追溯码中按行数量自动取码填入（开发测试用） */
async function handleFetchInStockTraceCodes(): Promise<void> {
  const row = currentLine.value;
  if (!row) {
    ElMessage.warning('请先在右侧选择药品');
    return;
  }
  if (!ensureScanTargetLine()) return;

  const locationId = resolveLineLocationId(row);
  const itemId = String(row.itemId ?? '').trim();
  const lotNumber = String(row.lotNumber ?? '').trim();
  if (!locationId) {
    ElMessage.warning(
      props.scene === 'dispense'
        ? '缺少发放库房，请先在发药列表选择发放库房'
        : '缺少库房信息，请确认单头已选库房',
    );
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
    const allCodes = await fetchInStockTraceCodes({
      locationId,
      itemId,
      lotNumber,
      useMock: usingMockLines.value,
    });
    if (allCodes.length === 0) {
      ElMessage.warning('本库无在库追溯码');
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
      ElMessage.warning('在库追溯码均已被占用或本行已绑满');
      return;
    }

    let nextTrace = cur;
    for (const code of picked) {
      nextTrace = applyTraceBind(nextTrace, code, preset.bindMode);
      boundPool.add(code);
      pushLog(code, 'ok', `库取 → ${lineDisplayName(row)}`);
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

    <div class="scan-page">
      <ElInput
        ref="scanInputRef"
        v-model="scanInput"
        clearable
        size="large"
        class="scan-page__input font-mono"
        placeholder="扫描或输入追溯码"
        @keydown.enter.prevent="handleScanEnter"
        @keydown="handleScanKeydown"
      >
        <template v-if="resolving" #suffix>
          <span class="scan-page__spinner" />
        </template>
      </ElInput>

      <div
        v-if="isTargetFirstMode"
        class="scan-current-target"
        :class="{ 'scan-current-target--empty': !currentLine }"
      >
        {{ currentLine ? `当前：${currentTargetLabel}` : '请先在右侧选择要录入的药品' }}
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
          >
            <ElTableColumn label="药品" min-width="200">
              <template #default="{ row }">
                <div class="line-drug-cell">
                  <span class="line-drug-cell__name">{{ lineDisplayName(row) }}</span>
                  <ElCheckbox
                    :model-value="isLineBoxScanEnabled(row.id)"
                    size="small"
                    class="line-drug-cell__box"
                    @click.stop
                    @update:model-value="(v: boolean) => handleLineBoxScanChange(row, v)"
                  >
                    箱码
                  </ElCheckbox>
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

.line-drug-cell__box {
  flex-shrink: 0;
}

.line-drug-cell__box :deep(.el-checkbox__label) {
  padding-left: 4px;
  font-size: 12px;
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
