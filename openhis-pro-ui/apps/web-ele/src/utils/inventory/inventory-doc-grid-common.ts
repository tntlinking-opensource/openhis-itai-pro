import type { VxeGridPropTypes, VxeGridProps } from '#/adapter/vxe-table';

import { nextTick } from 'vue';

import type { useInventoryDocGridEdit } from '#/composables/use-inventory-doc-grid-edit';
import {
  applyWarehouseNonInboundBandKindToRow,
  convertInboundQtyBetweenBandKinds,
  hydrateWarehouseDetailQtyFromPersisted,
  hydrateWarehouseSupplyLineQtyForGrid,
  inboundUnitBandNameByKind,
  inboundUnitBandSelectOptions,
  normalizeInboundProductUnitBands,
  type InboundProductUnitBand,
  warehouseNonInboundDefaultBandKind,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  inventoryPickNumberField,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';

/** 单头表单 grid 列间距（与调拨/损益页对齐）。 */
export const INV_DOC_HEAD_FORM_GRID_CLASS =
  'grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4';

/** 单头区与明细表之间的间距。 */
export const INV_DOC_HEAD_SECTION_CLASS = 'inv-doc-head-section shrink-0 mb-4 pb-3';

/** 明细行是否已绑定后端项目主键（选品完成） */
export function isPresentItemId(v: unknown): boolean {
  if (v === undefined || v === null) return false;
  if (typeof v === 'string') return v.trim() !== '';
  if (typeof v === 'number') return Number.isFinite(v);
  if (typeof v === 'bigint') return true;
  return String(v).trim() !== '';
}

export interface CreateDefaultInvDocGridOptionsParams {
  columns: VxeGridPropTypes.Column[];
  /** vxe beforeEditMethod；默认始终允许（由外层 patch 控制 enabled） */
  beforeEditMethod?: () => boolean;
}

/** 库房单据明细表 gridOptions 基线（row 编辑 + keepSource） */
export function createDefaultInvDocGridOptions<TRow extends object>(
  params: CreateDefaultInvDocGridOptionsParams,
): VxeGridProps<TRow> {
  return {
    autoResize: true,
    height: '100%',
    border: true,
    stripe: true,
    showOverflow: true,
    showFooter: false,
    size: 'medium',
    keepSource: true,
    columnConfig: { resizable: true },
    data: [],
    editConfig: {
      enabled: true,
      trigger: 'click',
      mode: 'row',
      autoClear: false,
      beforeEditMethod: params.beforeEditMethod ?? (() => true),
    },
    columns: params.columns,
    pagerConfig: { enabled: false },
  };
}

/** 读取 vxe 全量明细行 */
export function collectInventoryGridFullData<TRow>(
  gridApi: { grid?: unknown } | null | undefined,
): TRow[] {
  const g = gridApi?.grid as null | { getFullData?: () => TRow[] };
  return g?.getFullData?.() ?? [];
}

/** 选品回调后：双 nextTick 再聚焦首填列 */
export function scheduleInventoryProductPickFocus<TRow extends object>(
  gridEdit: Pick<ReturnType<typeof useInventoryDocGridEdit<TRow>>, 'onProductSelected'>,
  row: TRow,
): void {
  void nextTick(async () => {
    await nextTick();
    await gridEdit.onProductSelected(row);
  });
}

export type InventoryQtyUnitListRow = {
  unitCode?: string;
  unitCodeDictText?: string;
  selectedBandKind?: string;
  __prevBandKind?: string;
  unitList?: { label?: string; value: string; value_dictText?: string }[];
  unitBands?: InboundProductUnitBand[];
  price?: number;
};

/** 药库非入库选品回填：从 pick.unitBands 写入计量带、单位码与展示单价（与出库单同构）。 */
export function applyWarehouseNonInboundProductPickUnitsToRow(
  pick: Record<string, unknown>,
  row: InventoryQtyUnitListRow,
  options?: { legacyUnitPrice?: number },
): void {
  const bands = normalizeInboundProductUnitBands(pick.unitBands);
  row.unitBands = bands.length > 0 ? bands : undefined;
  const legacyUnitPrice =
    options?.legacyUnitPrice ?? inventoryPickNumberField(pick, 'purchaseUnitPrice');
  if (bands.length > 0) {
    const kind = warehouseNonInboundDefaultBandKind(bands);
    row.selectedBandKind = kind;
    row.unitList = inboundUnitBandSelectOptions(bands);
    row.__prevBandKind = kind;
    applyWarehouseNonInboundBandKindToRow(row, kind, legacyUnitPrice);
    return;
  }
  const fallbackCode =
    inventoryPickStringField(pick, 'minUnitCode') || inventoryPickStringField(pick, 'unitCode');
  row.selectedBandKind = 'LEGACY';
  row.__prevBandKind = 'LEGACY';
  row.unitCode = fallbackCode;
  row.unitList = [{ value: 'LEGACY', label: fallbackCode || '单位' }];
  if (Number.isFinite(legacyUnitPrice) && legacyUnitPrice > 0) {
    row.price = legacyUnitPrice;
  }
}

/** 药库非入库明细：切换计量带时换算数量并回写 unitCode / 单价。 */
export function handleWarehouseNonInboundUnitBandChanged(
  row: InventoryQtyUnitListRow,
  args: {
    getQty: () => number;
    setQty: (qty: number) => void;
    onAfterChanged?: () => void;
  },
): void {
  const newK = String(row.selectedBandKind ?? '')
    .trim()
    .toUpperCase();
  const oldK = String(row.__prevBandKind ?? '')
    .trim()
    .toUpperCase();
  const bands = row.unitBands ?? [];
  if (oldK && newK && oldK !== newK && oldK !== 'LEGACY' && newK !== 'LEGACY' && bands.length > 0) {
    const nq = convertInboundQtyBetweenBandKinds(args.getQty(), oldK, newK, bands);
    if (nq !== undefined) {
      args.setQty(nq);
    }
  }
  applyWarehouseNonInboundBandKindToRow(row, newK);
  row.__prevBandKind = newK;
  args.onAfterChanged?.();
}

/** 同步行 unitList/selectedBandKind，供 `_qtyUnit` + InventoryQtyUnitCell 合并展示。 */
export function applyInventoryRowQtyUnitList(row: InventoryQtyUnitListRow): void {
  if (row.unitBands && row.unitBands.length > 0) {
    row.unitList = inboundUnitBandSelectOptions(row.unitBands);
    if (!row.selectedBandKind) {
      row.selectedBandKind = warehouseNonInboundDefaultBandKind(row.unitBands);
      row.__prevBandKind = row.selectedBandKind;
    }
    return;
  }
  const label = String(row.unitCodeDictText ?? row.unitCode ?? '').trim() || '单位';
  row.selectedBandKind = row.selectedBandKind ?? 'LEGACY';
  row.__prevBandKind = row.selectedBandKind;
  row.unitList = [{ value: 'LEGACY', label }];
}

/** 详情/上游行输入：后端 SPLIT 数量 + 保存时 unitCode */
export interface HydrateInventoryDetailLineInput {
  itemQuantity?: null | number;
  unitCode?: null | string;
  unitCodeDictText?: null | string;
  productCatalog?: { unitBands?: unknown; unitBandsJson?: unknown } | null;
  unitBands?: unknown;
  unitBandsJson?: unknown;
}

export interface HydratedInventoryDetailQty {
  bandKind: string;
  displayQty: number;
  unitBands?: InboundProductUnitBand[];
  unitCode: string;
  unitCodeDictText?: string;
  unitList?: { label: string; value: string }[];
}

/** 详情/上游明细：SPLIT → 按 unitCode 还原展示数量与计量带（INV 改造清单 §1.2） */
export function hydrateInventoryDetailLineQtyForGrid(
  ln: HydrateInventoryDetailLineInput,
  options?: { useSupplyLineHydrate?: boolean },
): HydratedInventoryDetailQty {
  const catalog = ln.productCatalog;
  const bands = normalizeInboundProductUnitBands(
    ln.unitBands ?? catalog?.unitBands,
    ln.unitBandsJson ?? catalog?.unitBandsJson,
  );
  const rawQty =
    ln.itemQuantity === undefined || ln.itemQuantity === null ? 0 : Number(ln.itemQuantity);
  const persistedQty = Number.isFinite(rawQty) ? rawQty : 0;
  const unitCodeStr = String(ln.unitCode ?? '').trim();
  const dictText = String(ln.unitCodeDictText ?? '').trim();

  if (bands.length === 0) {
    return {
      displayQty: persistedQty,
      bandKind: 'LEGACY',
      unitCode: unitCodeStr,
      unitCodeDictText: dictText || unitCodeStr,
    };
  }

  const hydrateFn = options?.useSupplyLineHydrate
    ? hydrateWarehouseSupplyLineQtyForGrid
    : hydrateWarehouseDetailQtyFromPersisted;
  const hydrated = hydrateFn({
    bands,
    persistedItemQuantity: persistedQty,
    persistedUnitCode: unitCodeStr,
  });
  const unitList = inboundUnitBandSelectOptions(bands);
  const bandLabel =
    inboundUnitBandNameByKind(bands, hydrated.bandKind) || dictText || hydrated.unitCode;
  const enrichedUnitList = unitList.map((o) => {
    if (o.value !== hydrated.bandKind) return o;
    const lbl = String(o.label ?? '').trim();
    if (!lbl || lbl === o.value) {
      return { ...o, label: bandLabel || lbl || o.value };
    }
    return o;
  });

  return {
    displayQty: hydrated.displayQty,
    bandKind: hydrated.bandKind,
    unitBands: bands,
    unitCode: hydrated.unitCode,
    unitCodeDictText: dictText || bandLabel || hydrated.unitCode,
    unitList: enrichedUnitList,
  };
}

/** 将 {@link hydrateInventoryDetailLineQtyForGrid} 结果写入网格行（itemQuantity + selectedBandKind 模式） */
export function applyHydratedInventoryDetailQtyToRow(
  row: InventoryQtyUnitListRow & { itemQuantity?: number },
  hydrated: HydratedInventoryDetailQty,
): void {
  row.itemQuantity = hydrated.displayQty;
  row.unitCode = hydrated.unitCode;
  if (hydrated.unitCodeDictText) {
    row.unitCodeDictText = hydrated.unitCodeDictText;
  }
  if (hydrated.unitBands?.length) {
    row.unitBands = hydrated.unitBands;
    row.unitList = hydrated.unitList ?? inboundUnitBandSelectOptions(hydrated.unitBands);
    row.selectedBandKind = hydrated.bandKind;
    row.__prevBandKind = hydrated.bandKind;
    return;
  }
  applyInventoryRowQtyUnitList(row);
}

export interface CreateInventoryQtyUnitColumnOptions {
  qtyField?: string;
  unitField?: string;
  title?: string;
  width?: number;
  /** 允许负数（损溢单：负=报损，正=报溢） */
  allowNegative?: boolean;
  /** 自定义表头 slot 名（如 `gainLossQtyHeader`） */
  headerSlot?: string;
  onUnitChange?: (row: Record<string, unknown>) => void;
}

/** 可编辑明细：数量 + 单位合并列（金标准 InventoryQtyUnitCell）。 */
export function createInventoryQtyUnitColumn(
  options?: CreateInventoryQtyUnitColumnOptions,
): VxeGridPropTypes.Column {
  const qtyField = options?.qtyField ?? 'itemQuantity';
  const unitField = options?.unitField ?? 'selectedBandKind';
  return {
    field: '_qtyUnit',
    title: options?.title ?? '数量',
    width: options?.width ?? 168,
    align: 'center',
    headerAlign: 'center',
    ...(options?.headerSlot ? { slots: { header: options.headerSlot } } : {}),
    editRender: {
      name: 'InventoryQtyUnitCell',
      props: {
        qtyField,
        unitField,
        ...(options?.allowNegative ? { allowNegative: true } : {}),
        ...(options?.onUnitChange ? { onUnitChange: options.onUnitChange } : {}),
      },
    },
  };
}

export interface CreateInventoryQtyUnitReadonlyColumnOptions {
  qtyField?: string;
  unitLabelField?: string;
  unitCodeField?: string;
  unitBandsField?: string;
  /** 为 true 时按 unitBands + unitCode 将 SPLIT 数量换算后再展示 */
  hydrateFromSplit?: boolean;
  title?: string;
  width?: number;
}

function formatInventoryQtyUnitReadonlyText(
  row: Record<string, unknown>,
  options?: CreateInventoryQtyUnitReadonlyColumnOptions,
): string {
  const qtyField = options?.qtyField ?? 'itemQuantity';
  const unitLabelField = options?.unitLabelField ?? 'unitCode';
  const unitCodeField = options?.unitCodeField ?? 'unitCode';
  const unitBandsField = options?.unitBandsField ?? 'unitBands';
  const rawQty = Number(row[qtyField]);
  const persistedQty = Number.isFinite(rawQty) ? rawQty : 0;
  const dictText = String(row[unitLabelField] ?? row[unitCodeField] ?? '').trim();

  if (options?.hydrateFromSplit) {
    const catalog = row.productCatalog as
      | { unitBands?: unknown; unitBandsJson?: unknown }
      | undefined;
    const hydrated = hydrateInventoryDetailLineQtyForGrid({
      itemQuantity: persistedQty,
      unitCode: String(row[unitCodeField] ?? ''),
      unitCodeDictText: dictText,
      productCatalog: catalog,
      unitBands: row[unitBandsField],
    });
    const qtyPart = String(hydrated.displayQty);
    const unitPart =
      hydrated.unitCodeDictText ||
      inboundUnitBandNameByKind(hydrated.unitBands, hydrated.bandKind) ||
      hydrated.unitCode ||
      dictText;
    if (!qtyPart && !unitPart) return '';
    if (!unitPart) return qtyPart;
    if (!qtyPart) return unitPart;
    return `${qtyPart} ${unitPart}`;
  }

  const qtyPart = Number.isFinite(rawQty) ? String(rawQty) : '';
  const unitPart = dictText;
  if (!qtyPart && !unitPart) return '';
  if (!unitPart) return qtyPart;
  if (!qtyPart) return unitPart;
  return `${qtyPart} ${unitPart}`;
}

/** 只读明细：数量 + 单位合并展示。 */
export function createInventoryQtyUnitReadonlyColumn(
  options?: CreateInventoryQtyUnitReadonlyColumnOptions,
): VxeGridPropTypes.Column {
  return {
    field: '_qtyUnit',
    title: options?.title ?? '数量',
    width: options?.width ?? 120,
    align: 'center',
    headerAlign: 'center',
    formatter: ({ row }) => formatInventoryQtyUnitReadonlyText(row, options),
  };
}
