import type {
  LoadStockOutFormInitResponse,
  StockOutFormInitOptionDto,
  StockOutListRowDto,
  WarehouseOptionDto,
} from '#/api/inventory/warehouse/stock-out';

import {
  loadStockOutFormInitApi,
  pageStockOutsApi,
  WHS_SO_TYPE_ENUM,
} from '#/api/inventory/warehouse/stock-out';
import { stockOutStatusLabel } from '#/utils/inventory/stock-out-doc-actions';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  coerceWorkbenchListLocationIdForApi,
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  isAllLocationsFilterOptionValue,
  INV_ALL_PHARMACIES_LABEL,
  INV_ALL_WAREHOUSES_LABEL,
  prependAllLocationsCabinetOption,
} from '#/utils/inventory/inventory-workbench-list';

export interface WarehouseStockOutInitResponse {
  /** 与进货侧栏药库下拉同构：id/name/value/label（含「全部库房」） */
  cabinetListOptions: Record<string, unknown>[];
  /** 侧栏「目的药房」下拉（含「全部药房」；与主区真实药房列表分离） */
  pharmacyCabinetListOptions: Record<string, unknown>[];
  /** 主区源药库 ElSelect：由 {@code warehouseLocationOptions} 映射（不含「全部」） */
  warehouseOptions: WarehouseOptionDto[];
  /** 主区目的药房：由 {@code pharmacyLocationOptions} 映射（不含「全部」） */
  pharmacyLocationOptions: WarehouseOptionDto[];
  inboundItemTableTypeOptions: StockOutFormInitOptionDto[];
  statusOptions: StockOutFormInitOptionDto[];
  pharmacistOptions: StockOutFormInitOptionDto[];
  warehouseKeeperOptions: StockOutFormInitOptionDto[];
}

/** 将 SupplyStatus（排序号或 code）映射为卡片副文案。 */
function statusLabel(raw?: unknown): string {
  if (raw === undefined || raw === null) return '';
  return stockOutStatusLabel(raw);
}

/** 从列表行解析业务单号。 */
function resolveBusNo(row?: null | Record<string, unknown>): string | undefined {
  if (!row) return undefined;
  for (const k of ['busNo', 'supplyBusNo', 'orderBusNo']) {
    const raw = row[k];
    if (raw === undefined || raw === null) continue;
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return undefined;
}

function stockOutInitOptionToCabinetRow(o: StockOutFormInitOptionDto): Record<string, unknown> {
  const id = o.value ?? o.sort;
  const label = o.label ?? String(id ?? '');
  return {
    id,
    value: id,
    name: label,
    label,
  };
}

function stockOutInitOptionsToWarehouseOptions(
  list: StockOutFormInitOptionDto[],
): WarehouseOptionDto[] {
  return list.map((o) => ({
    locationId: o.value ?? o.sort,
    displayName: o.label,
  }));
}

function stockOutInitOptionsToPharmacyOptions(
  list: StockOutFormInitOptionDto[],
): WarehouseOptionDto[] {
  return list.map((o) => ({
    locationId: o.value ?? o.sort,
    displayName: o.label,
  }));
}

function emptyInitPayload(): WarehouseStockOutInitResponse {
  return {
    cabinetListOptions: [],
    pharmacyCabinetListOptions: [],
    warehouseOptions: [],
    pharmacyLocationOptions: [],
    inboundItemTableTypeOptions: [],
    statusOptions: [],
    pharmacistOptions: [],
    warehouseKeeperOptions: [],
  };
}

/** 列表行补充卡片展示字段。 */
function mapListRow(r: StockOutListRowDto): Record<string, unknown> {
  return {
    ...r,
    supplyBusNo: r.busNo,
    statusEnum_enumText: statusLabel(r.statusEnum),
  };
}

/** 同一 busNo 多明细行时只保留一条用于侧栏卡片。 */
function dedupeByBusNo(records: StockOutListRowDto[]): Record<string, unknown>[] {
  const seen = new Set<string>();
  const out: Record<string, unknown>[] = [];
  for (const raw of records) {
    const row = mapListRow(raw);
    const bn = resolveBusNo(row);
    if (!bn || seen.has(bn)) continue;
    seen.add(bn);
    out.push(row);
  }
  return out;
}

export async function initWarehouseStockOutApi(): Promise<WarehouseStockOutInitResponse> {
  const res = await loadStockOutFormInitApi({});
  const data = res.data as LoadStockOutFormInitResponse | undefined;
  if (!data) {
    return emptyInitPayload();
  }
  const whOpts = data.warehouseLocationOptions ?? [];
  const phOpts = data.pharmacyLocationOptions ?? [];
  const pharmacyRows = phOpts.map(stockOutInitOptionToCabinetRow);
  return {
    cabinetListOptions: prependAllLocationsCabinetOption(
      whOpts.map(stockOutInitOptionToCabinetRow),
      INV_ALL_WAREHOUSES_LABEL,
    ),
    pharmacyCabinetListOptions: prependAllLocationsCabinetOption(
      pharmacyRows,
      INV_ALL_PHARMACIES_LABEL,
    ),
    warehouseOptions: stockOutInitOptionsToWarehouseOptions(whOpts),
    pharmacyLocationOptions: stockOutInitOptionsToPharmacyOptions(phOpts),
    inboundItemTableTypeOptions: data.inboundItemTableTypeOptions ?? [],
    statusOptions: data.statusOptions ?? [],
    pharmacistOptions: data.pharmacistOptions ?? [],
    warehouseKeeperOptions: data.warehouseKeeperOptions ?? [],
  };
}

export interface WarehouseStockOutPageQuery {
  /** 源药库 */
  sourceLocationId?: number | string;
  /** 目的药房（后端列表仅按源库查询时，在此做客户端过滤） */
  purposeLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
}

/** 读模型行上的目的库位与侧栏所选是否一致（「全部药房」=0 不过滤）。 */
function rowMatchesPurposeLocation(
  row: Record<string, unknown>,
  purposeId?: number | string,
): boolean {
  if (
    purposeId === undefined ||
    purposeId === null ||
    purposeId === '' ||
    isAllLocationsFilterOptionValue(purposeId)
  ) {
    return true;
  }
  const pid = row.purposeLocationId;
  return String(pid ?? '') === String(purposeId);
}

/**
 * 侧栏历史出库单：源药库/目的药房默认「全部」(0) 即查全部可见单；
 * 按源库拉取后按目的药房过滤并去重 busNo。
 */
export async function pageWarehouseStockOutListApi(params?: WarehouseStockOutPageQuery) {
  const sourceLocationId = coerceWorkbenchListLocationIdForApi(params?.sourceLocationId);
  const purposeLocationId = coerceWorkbenchListLocationIdForApi(params?.purposeLocationId);
  const res = await pageStockOutsApi({
    sourceLocationId,
    keyword: params?.keyword?.trim() || undefined,
    pageNo: 1,
    pageSize: 200,
    typeEnum: WHS_SO_TYPE_ENUM,
  });
  const { content: rawRecords } = parsePagedRowsFromApiData<StockOutListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    rawRecords,
    params?.applyTimeBegin,
    params?.applyTimeEnd,
  );
  const deduped = dedupeByBusNo(dateFiltered);
  const purposeFiltered = deduped.filter((row) =>
    rowMatchesPurposeLocation(row, purposeLocationId),
  );
  const records = filterWorkbenchListRowsByKeyword(purposeFiltered, params?.keyword);
  return { records, total: records.length };
}
