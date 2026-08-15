<!--
  库存单据批量追溯码扫码桥接：映射网格行 ↔ 弹窗，确认后写回 traceNo。
  应扫个数按目录 product.traceUnitCode + traceUnitRatio（半盒 ceil）算好，弹窗只消费 requiredTraceCount。
  打开时批量查主表品批是否有码（lotHasTraceMaster），供有码硬/无码软分叉。
-->
<script setup lang="ts">
import type {
  CatalogTraceUnitHint,
  InboundProductUnitBand,
} from '#/utils/inventory/inbound-product-unit-bands';
import type { TraceBatchScanLine, TraceScanSceneKey } from '#/utils/trace';

import { computed, onBeforeUnmount, ref } from 'vue';

import { batchExistsTraceMasterByItemLotApi } from '#/api/inventory/warehouse/inventory-detail';
import TraceBatchScanDialog from '#/components/trace/trace-batch-scan-dialog.vue';
import {
  persistDisplayQtyAsSplitForBands,
  resolveCatalogTraceUnitHint,
  resolveUnitCodeRatioToSplit,
} from '#/utils/inventory/inbound-product-unit-bands';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { computeRequiredTraceCount, isTraceSlotEmpty } from '#/utils/trace';

defineOptions({ name: 'InvDocTraceScanBridge' });

interface InvDocTraceRow {
  id: string;
  locationId?: number | string;
  itemId?: number | string;
  lotNumber?: string;
  batchNo?: string;
  traceNo?: string;
  /** 操作（录入）单位编码 */
  purchaseUnit?: string;
  unitCode?: string;
  unitBands?: InboundProductUnitBand[];
  selectedBandKind?: string;
  itemQuantity?: number;
  purchaseQty?: number;
  quantity?: number;
  qty?: number;
  /** 盘点实盘数量（应扫个数按实盘/剩余码口径） */
  actualQuantity?: number;
  /** 损益数量（可负；应扫个数取绝对值） */
  gainLossQuantity?: number;
  /** 选品/详情带回的目录提示（含游离追溯单位） */
  productCatalog?: CatalogTraceUnitHint & { unitBands?: InboundProductUnitBand[] };
  medicinePick?: { name?: string } | Record<string, unknown>;
  spec?: string;
  productName?: string;
  itemName?: string;
  name?: string;
  /** 可选：父页已回填的有码批标志 */
  lotHasTraceMaster?: boolean;
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
    /**
     * 超应扫个数硬拒。进货默认 true；期初须传 false（与后端不做码数硬 Gate 对齐）。
     */
    hardScanCountGate?: boolean;
  }>(),
  {
    rows: () => [],
    scene: 'inventoryOut',
    lotField: 'lotNumber',
    defaultLocationId: '',
    disabled: false,
    hardScanCountGate: undefined,
  },
);

const visible = ref(false);
const traceByLineId = ref<Record<string, string>>({});
/** 本次扫码会话使用的行快照（open 时写入） */
const activeRows = ref<InvDocTraceRow[]>([]);
/** 行 id → 主表该品批是否有码（open 时批量回填） */
const lotHasMasterByLineId = ref<Record<string, boolean>>({});

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

/** 从网格行取展示数量（当前计量带下；损益取绝对值；盘点取实盘） */
function resolveDisplayQty(row: InvDocTraceRow): number | undefined {
  const gainLoss = Number(row.gainLossQuantity);
  if (Number.isFinite(gainLoss) && gainLoss !== 0) {
    return Math.abs(gainLoss);
  }
  for (const key of ['actualQuantity', 'purchaseQty', 'itemQuantity', 'quantity', 'qty'] as const) {
    const n = Number(row[key]);
    if (Number.isFinite(n) && n > 0) return n;
  }
  return undefined;
}

/** 操作单位：优先 purchaseUnit，其次 unitCode */
function resolveOperationUnitCode(row: InvDocTraceRow): string {
  return String(row.purchaseUnit ?? row.unitCode ?? '').trim();
}

/** 目录追溯单位：仅取 productCatalog，不走行上废弃字段 */
function resolveRowCatalogTrace(row: InvDocTraceRow): CatalogTraceUnitHint | undefined {
  return row.productCatalog ?? undefined;
}

/**
 * 按约定公式算应扫码数：同单位 ceil(qty)；异单位 ceil(拆零量/每码拆零)。
 * 追溯单位与倍数一律来自目录 productCatalog（半盒与出库同式向上取整）。
 */
function resolveRequiredTraceCount(row: InvDocTraceRow): number | undefined {
  const displayQty = resolveDisplayQty(row);
  if (displayQty === undefined) return undefined;

  const bands = row.unitBands ?? row.productCatalog?.unitBands ?? [];
  const opUnit = resolveOperationUnitCode(row);
  const catalogTrace = resolveRowCatalogTrace(row);
  const catalogHint = resolveCatalogTraceUnitHint(catalogTrace, bands);
  const traceUnit = catalogHint?.traceUnitCode ?? '';
  const mode = props.scene === 'inbound' ? 'inbound' : 'outbound';

  // 有计量带或目录追溯倍数：按拆零公比换算（半盒 → ceil）
  if (bands.length > 0 || catalogHint) {
    const persisted = persistDisplayQtyAsSplitForBands({
      bands,
      displayQty,
      legacyUnitCode: opUnit,
      selectedBandKind: row.selectedBandKind,
    });
    const perTrace = resolveUnitCodeRatioToSplit(bands, traceUnit || opUnit, catalogTrace);

    return computeRequiredTraceCount({
      operationQty: displayQty,
      operationUnitCode: opUnit,
      traceUnitCode: traceUnit || opUnit,
      operationSplitQty: persisted.itemQuantity,
      perTraceSplitQty: perTrace ?? catalogHint?.traceUnitRatio,
      mode,
    });
  }

  // 无计量带且无目录追溯：同阶回落展示数量
  return computeRequiredTraceCount({
    operationQty: displayQty,
    operationUnitCode: opUnit,
    traceUnitCode: opUnit,
    mode,
  });
}

/**
 * 本行操作拆零量（消耗/流转盖住判定）；进货不走此口径。
 *
 * @param row 单据明细行
 */
function resolveOperationSplitQty(row: InvDocTraceRow): number | undefined {
  const displayQty = resolveDisplayQty(row);
  if (displayQty === undefined) return undefined;
  const bands = row.unitBands ?? row.productCatalog?.unitBands ?? [];
  const opUnit = resolveOperationUnitCode(row);
  if (bands.length > 0) {
    const persisted = persistDisplayQtyAsSplitForBands({
      bands,
      displayQty,
      legacyUnitCode: opUnit,
      selectedBandKind: row.selectedBandKind,
    });
    return persisted.itemQuantity;
  }
  return displayQty;
}

/** 组装品批存在性 map 键（与后端 itemId|lot 对齐用） */
function buildLotMasterLookupKey(itemId: string, lot: string): string {
  return `${itemId}|${lot}`;
}

/**
 * 打开时批量查主表品批有码标志；失败时按行上已有值或 false（软）回落，不挡开窗。
 */
async function refreshLotHasMasterFlags(rows: InvDocTraceRow[]): Promise<void> {
  const next: Record<string, boolean> = {};
  const keys: { itemId: string; lotNumber: string }[] = [];
  const lineKeyByLookup = new Map<string, string[]>();

  for (const row of rows) {
    // 父页已显式传入时优先采用
    if (row.lotHasTraceMaster === true || row.lotHasTraceMaster === false) {
      next[row.id] = row.lotHasTraceMaster;
      continue;
    }
    const itemId = preserveApiIdForRequest(row.itemId);
    const lot = resolveLot(row);
    if (!itemId || !lot) {
      next[row.id] = false;
      continue;
    }
    const lookup = buildLotMasterLookupKey(itemId, lot);
    const lineIds = lineKeyByLookup.get(lookup) ?? [];
    lineIds.push(row.id);
    lineKeyByLookup.set(lookup, lineIds);
    if (!keys.some((k) => k.itemId === itemId && k.lotNumber === lot)) {
      keys.push({ itemId, lotNumber: lot });
    }
  }

  if (keys.length > 0) {
    try {
      const res = await batchExistsTraceMasterByItemLotApi({ keys });
      for (const item of res.items ?? []) {
        const itemId = preserveApiIdForRequest(item.itemId);
        const lot = String(item.lotNumber ?? '').trim();
        if (!itemId || !lot) continue;
        const lookup = buildLotMasterLookupKey(itemId, lot);
        const lineIds = lineKeyByLookup.get(lookup) ?? [];
        const hasMaster = item.lotHasTraceMaster === true;
        for (const lineId of lineIds) {
          next[lineId] = hasMaster;
        }
      }
    } catch {
      // 查询失败：未写入的行按无码批软路径，避免卡死扫码
      for (const row of rows) {
        if (next[row.id] === undefined) {
          next[row.id] = false;
        }
      }
    }
  }

  // 未命中结果的行默认无码批
  for (const row of rows) {
    if (next[row.id] === undefined) {
      next[row.id] = false;
    }
  }
  lotHasMasterByLineId.value = next;
}

/** 行库房：行上非空优先，否则单头 defaultLocationId（空串视为未传） */
function resolveScanLineLocationId(row: InvDocTraceRow): string | undefined {
  const fromRow = String(row.locationId ?? '').trim();
  if (fromRow) return fromRow;
  const fromHead = String(props.defaultLocationId ?? '').trim();
  return fromHead || undefined;
}

/**
 * 从计量带解析追溯码单位展示名（同 code 优先 TRACE 槽 unitName）。
 *
 * @param code 追溯码单位 code
 * @param bands 行/目录计量带
 */
function resolveTraceUnitLabelFromBands(
  code: string | undefined,
  bands: InboundProductUnitBand[] | undefined,
): string | undefined {
  const unitCode = String(code ?? '').trim();
  if (!unitCode || !bands?.length) return undefined;
  const traceBand = bands.find(
    (b) =>
      String(b?.bandKind ?? '').toUpperCase() === 'TRACE' &&
      String(b?.unitCode ?? '').trim() === unitCode,
  );
  const traceName = String(traceBand?.unitName ?? '').trim();
  if (traceName) return traceName;
  // 游离追溯单位可能与包装等同码：回落任意同 code 的 unitName
  const anyBand = bands.find((b) => String(b?.unitCode ?? '').trim() === unitCode);
  const anyName = String(anyBand?.unitName ?? '').trim();
  return anyName || undefined;
}

const scanLines = computed((): TraceBatchScanLine[] =>
  activeRows.value.map((row) => {
    const bands = row.unitBands ?? row.productCatalog?.unitBands ?? [];
    const catalogHint = resolveCatalogTraceUnitHint(resolveRowCatalogTrace(row), bands);
    return {
      id: row.id,
      locationId: resolveScanLineLocationId(row),
      itemId: row.itemId === undefined || row.itemId === null ? '' : String(row.itemId),
      lotNumber: resolveLot(row),
      itemName: resolveItemName(row),
      spec: row.spec,
      traceNo: row.traceNo,
      // 目录贴码单位/比例：药品明细列与应扫个数同源
      traceUnitCode: catalogHint?.traceUnitCode,
      traceUnitCodeLabel: resolveTraceUnitLabelFromBands(catalogHint?.traceUnitCode, bands),
      traceUnitRatio: catalogHint?.traceUnitRatio,
      operationUnitCode: resolveOperationUnitCode(row) || undefined,
      requiredTraceCount: resolveRequiredTraceCount(row),
      // 消耗/流转：盖住判定用拆零量（进货 scene 不传，保持个数硬控）
      operationSplitQty: props.scene === 'inbound' ? undefined : resolveOperationSplitQty(row),
      lotHasTraceMaster: lotHasMasterByLineId.value[row.id] === true,
    };
  }),
);

/** 打开批量扫码弹窗并初始化绑定表 */
async function open(rows?: InvDocTraceRow[]): Promise<void> {
  const source = rows ?? props.rows;
  if (props.disabled || source.length === 0) return;
  const init: Record<string, string> = {};
  for (const row of source) {
    const t = String(row.traceNo ?? '').trim();
    if (!isTraceSlotEmpty(t)) init[row.id] = t;
  }
  traceByLineId.value = init;
  activeRows.value = source;
  await refreshLotHasMasterFlags(source);
  visible.value = true;
}

/** 将弹窗绑定表写回网格行（确定/取消关窗或页卸载时） */
function flushTraceBindingsToRows(): void {
  for (const row of activeRows.value) {
    const bound = traceByLineId.value[row.id];
    if (bound !== undefined) {
      row.traceNo = bound.trim();
    }
  }
}

const emit = defineEmits<{
  /** 绑定表已写回网格（父页可据此落 localStorage 缓存） */
  bindingsChanged: [];
}>();

/** 弹窗关闭后将绑定结果写回网格行 */
function handleDialogClosed(open: boolean): void {
  if (open) return;
  flushTraceBindingsToRows();
  emit('bindingsChanged');
}

/** 切菜单时弹窗可能仍开着：卸载前先写回，避免扫码只停在弹窗内存 */
onBeforeUnmount(() => {
  if (!visible.value) return;
  flushTraceBindingsToRows();
  emit('bindingsChanged');
});

defineExpose({ open });
</script>

<template>
  <TraceBatchScanDialog
    v-model="visible"
    v-model:trace-by-line-id="traceByLineId"
    :lines="scanLines"
    :scene="props.scene"
    :default-location-id="props.defaultLocationId"
    :hard-scan-count-gate="props.hardScanCountGate"
    @update:model-value="handleDialogClosed"
  />
</template>
