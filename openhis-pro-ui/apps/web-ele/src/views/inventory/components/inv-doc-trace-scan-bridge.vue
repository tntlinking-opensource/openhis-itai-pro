<!--
  库存单据批量追溯码扫码桥接：映射网格行 ↔ 弹窗，确认后写回 traceNo。
-->
<script setup lang="ts">
import type { TraceBatchScanLine, TraceScanSceneKey } from '#/utils/trace';

import { computed, ref } from 'vue';

import TraceBatchScanDialog from '#/components/trace/trace-batch-scan-dialog.vue';
import { isTraceSlotEmpty } from '#/utils/trace';

defineOptions({ name: 'InvDocTraceScanBridge' });

interface InvDocTraceRow {
  id: string;
  locationId?: number | string;
  itemId?: number | string;
  lotNumber?: string;
  batchNo?: string;
  traceNo?: string;
  itemQuantity?: number;
  purchaseQty?: number;
  quantity?: number;
  qty?: number;
  medicinePick?: { name?: string } | Record<string, unknown>;
  spec?: string;
  productName?: string;
  itemName?: string;
  name?: string;
}

const props = withDefaults(
  defineProps<{
    /** 明细网格当前行数据（也可在 open(rows) 时传入） */
    rows?: InvDocTraceRow[];
    scene?: TraceScanSceneKey;
    /** 批号字段名：lotNumber 或 batchNo */
    lotField?: 'batchNo' | 'lotNumber';
    /** 明细行未带 locationId 时的默认库房 */
    defaultLocationId?: number | string;
    disabled?: boolean;
  }>(),
  {
    rows: () => [],
    scene: 'inventoryOut',
    lotField: 'lotNumber',
    defaultLocationId: '',
    disabled: false,
  },
);

const isDev = import.meta.env.DEV;

const visible = ref(false);
const traceByLineId = ref<Record<string, string>>({});
/** 本次扫码会话使用的行快照（open 时写入） */
const activeRows = ref<InvDocTraceRow[]>([]);
/** 开发环境打开演示数据 */
const useMockLines = ref(false);

/** 从网格行提取批号 */
function resolveLot(row: InvDocTraceRow): string {
  if (props.lotField === 'batchNo') {
    return String(row.batchNo ?? '').trim();
  }
  return String(row.lotNumber ?? '').trim();
}

/** 从网格行取药品展示名（兼容 medicinePick / productName 等字段） */
function resolveItemName(row: InvDocTraceRow): string {
  const pick = row.medicinePick;
  if (pick && typeof pick === 'object' && 'name' in pick) {
    const fromPick = String(pick.name ?? '').trim();
    if (fromPick) return fromPick;
  }
  for (const key of ['productName', 'itemName', 'name'] as const) {
    const value = String(row[key] ?? '').trim();
    if (value) return value;
  }
  return '';
}

/** 从网格行取应扫追溯码数量（行数量向上取整） */
function resolveRequiredTraceCount(row: InvDocTraceRow): number | undefined {
  for (const key of ['itemQuantity', 'purchaseQty', 'quantity', 'qty'] as const) {
    const n = Number(row[key]);
    if (Number.isFinite(n) && n > 0) return Math.ceil(n);
  }
  return undefined;
}

const scanLines = computed((): TraceBatchScanLine[] =>
  activeRows.value.map((row) => ({
    id: row.id,
    locationId:
      row.locationId === undefined || row.locationId === null
        ? String(props.defaultLocationId ?? '').trim() || undefined
        : String(row.locationId),
    itemId: row.itemId === undefined || row.itemId === null ? '' : String(row.itemId),
    lotNumber: resolveLot(row),
    itemName: resolveItemName(row),
    spec: row.spec,
    traceNo: row.traceNo,
    requiredTraceCount: resolveRequiredTraceCount(row),
  })),
);

/** 打开批量扫码弹窗并初始化绑定表 */
function open(rows?: InvDocTraceRow[]): void {
  const source = rows ?? props.rows;
  if (props.disabled) return;
  useMockLines.value = isDev && source.length === 0;
  if (!useMockLines.value && source.length === 0) return;
  const init: Record<string, string> = {};
  for (const row of source) {
    const t = String(row.traceNo ?? '').trim();
    if (!isTraceSlotEmpty(t)) init[row.id] = t;
  }
  traceByLineId.value = init;
  activeRows.value = source;
  visible.value = true;
}

/** 开发环境：仅用本地演示数据打开弹窗 */
function openMock(): void {
  if (props.disabled || !isDev) return;
  useMockLines.value = true;
  traceByLineId.value = {};
  activeRows.value = [];
  visible.value = true;
}

/** 弹窗关闭后将绑定结果写回网格行 */
function handleDialogClosed(open: boolean): void {
  if (open) return;
  if (useMockLines.value) {
    useMockLines.value = false;
    return;
  }
  for (const row of activeRows.value) {
    const bound = traceByLineId.value[row.id];
    if (bound !== undefined) {
      row.traceNo = bound.trim();
    }
  }
}

defineExpose({ open, openMock });
</script>

<template>
  <TraceBatchScanDialog
    v-model="visible"
    v-model:trace-by-line-id="traceByLineId"
    :lines="scanLines"
    :scene="props.scene"
    :use-mock-lines="useMockLines"
    :default-location-id="props.defaultLocationId"
    title="批量扫描追溯码"
    @update:model-value="handleDialogClosed"
  />
</template>
