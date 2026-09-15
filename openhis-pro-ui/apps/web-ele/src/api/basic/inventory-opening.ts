/**
 * 基础数据 · 库存期初 — 对接 Whale {@code POST /api/app/basicInventoryOpening/<method>}
 * （AS-BAS-INV-001）
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_WAREHOUSES_LABEL,
  isAllLocationsFilterOptionValue,
  mapLocationOptionsToCabinetRows,
  prependAllLocationsCabinetOption,
  resolveWorkbenchListPurposeLocationId,
} from '#/utils/inventory/inventory-workbench-list';
import {
  normalizeStocktakeStatusSort,
  stocktakeStatusLabel,
} from '#/utils/inventory/stocktake-doc-actions';

const BASE = '/basicInventoryOpening';

export interface LocationOpeningOptionDto {
  value?: number | string;
  label?: string;
  openingClosed?: boolean;
  formEnum?: string;
}

export interface InventoryOpeningOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadInventoryOpeningFormInitRequest {
  organizationId?: number | string;
}

export interface LoadInventoryOpeningFormInitResponse {
  warehouseOptions?: LocationOpeningOptionDto[];
  pharmacyOptions?: LocationOpeningOptionDto[];
  departmentPharmacyOptions?: LocationOpeningOptionDto[];
  statusOptions?: InventoryOpeningOptionDto[];
  practitionerOptions?: InventoryOpeningOptionDto[];
  supplierOptions?: InventoryOpeningOptionDto[];
}

export interface InventoryOpeningListRowDto {
  busNo?: string;
  typeEnum?: string;
  locationId?: number | string;
  locationName?: string;
  statusEnum?: string | number;
  statusDisplay?: string;
  applicantName?: string;
  approverName?: string;
  applyTime?: string;
  approvalTime?: string;
  lineCount?: number;
  totalQuantity?: number;
  /** 进价总额 */
  totalPrice?: number;
  currentEffective?: boolean;
}

export interface PageInventoryOpeningsRequest {
  locationId?: number | string;
  statusEnum?: string;
  busNo?: string;
  keyword?: string;
  startDate?: string;
  endDate?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageInventoryOpeningsResponse {
  total?: number;
  records?: InventoryOpeningListRowDto[];
}

export interface GetInventoryOpeningDetailRequest {
  busNo: string;
}

export interface InventoryOpeningDetailLineDto {
  itemTable?: string;
  itemId?: number | string;
  itemName?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  endTime?: string;
  startTime?: string;
  itemQuantity?: number;
  unitCode?: string;
  unitCodeDictText?: string;
  price?: number;
  salePrice?: number;
  totalPrice?: number;
  traceNo?: string;
  supplierId?: number | string;
  /** 产品目录提示（含游离追溯单位） */
  productCatalog?: {
    unitBands?: unknown;
    traceUnitCode?: string;
    traceUnitRatio?: number;
  };
}

export interface GetInventoryOpeningDetailResponse {
  busNo?: string;
  typeEnum?: string;
  statusEnum?: string | number;
  locationId?: number | string;
  locationName?: string;
  practitionerId?: number | string;
  occurrenceTime?: string;
  applyTime?: string;
  approvalTime?: string;
  lines?: InventoryOpeningDetailLineDto[];
}

export interface InventoryOpeningLineDto {
  itemTable?: string;
  itemId?: number | string;
  lotNumber?: string;
  endTime?: string;
  startTime?: string;
  itemQuantity?: number;
  unitCode?: string;
  price?: number;
  salePrice?: number;
  totalPrice?: number;
  traceNo?: string;
  supplierId?: number | string;
}

export interface CreateInventoryOpeningRequest {
  locationId: number | string;
  practitionerId?: number | string;
  occurrenceTime: string;
  lines?: InventoryOpeningLineDto[];
}

export interface CreateInventoryOpeningResponse {
  busNo?: string;
}

export interface UpdateInventoryOpeningRequest {
  busNo: string;
  locationId: number | string;
  practitionerId?: number | string;
  occurrenceTime: string;
  lines?: InventoryOpeningLineDto[];
}

export interface SubmitInventoryOpeningApprovalRequest {
  busNo: string;
}

export interface WithdrawInventoryOpeningApprovalRequest {
  busNo: string;
}

export interface AgreeInventoryOpeningRequest {
  busNo: string;
}

export interface RejectInventoryOpeningRequest {
  busNo: string;
  reason?: string;
}

export interface RemoveInventoryOpeningRequest {
  busNo: string;
}

export interface PageProductsForInventoryOpeningRequest {
  locationId?: number | string;
  productTableName?: string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface InventoryOpeningProductRowDto {
  itemTable?: string;
  itemId?: number | string;
  productBusNo?: string;
  itemName?: string;
  specification?: string;
  categoryCode?: string;
  categoryCodeDictText?: string;
  unitCode?: string;
  purchasePrice?: number;
  manufacturer?: string;
  /** 目录单位四槽（与进货选品同结构） */
  unitBands?: unknown[];
}

export interface PageInventoryOpeningsProductsResponse {
  total?: number;
  records?: InventoryOpeningProductRowDto[];
}

export interface CloseInventoryOpeningRequest {
  locationId: number | string;
}

export interface CloseInventoryOpeningResponse {
  locationId?: number | string;
  openingClosed?: boolean;
}

export interface VoidInventoryOpeningRequest {
  busNo: string;
  reason?: string;
}

export interface VoidInventoryOpeningResponse {
  busNo?: string;
  success?: boolean;
}

export interface CopyInventoryOpeningFromPreviousRequest {
  locationId: number | string;
  sourceBusNo?: string;
}

export async function loadInventoryOpeningFormInitApi(body?: LoadInventoryOpeningFormInitRequest) {
  return requestClient.post<ApiResponse<LoadInventoryOpeningFormInitResponse>>(
    `${BASE}/loadInventoryOpeningFormInit`,
    body ?? {},
  );
}

export async function pageInventoryOpeningsApi(body: PageInventoryOpeningsRequest) {
  return requestClient.post<ApiResponse<PageInventoryOpeningsResponse>>(
    `${BASE}/pageInventoryOpenings`,
    body,
  );
}

export async function getInventoryOpeningDetailApi(body: GetInventoryOpeningDetailRequest) {
  return requestClient.post<ApiResponse<GetInventoryOpeningDetailResponse>>(
    `${BASE}/getInventoryOpeningDetail`,
    body,
  );
}

export async function createInventoryOpeningApi(body: CreateInventoryOpeningRequest) {
  return requestClient.post<ApiResponse<CreateInventoryOpeningResponse>>(
    `${BASE}/createInventoryOpening`,
    body,
  );
}

export async function updateInventoryOpeningApi(body: UpdateInventoryOpeningRequest) {
  return requestClient.post<ApiResponse<CreateInventoryOpeningResponse>>(
    `${BASE}/updateInventoryOpening`,
    body,
  );
}

export async function submitInventoryOpeningApprovalApi(
  body: SubmitInventoryOpeningApprovalRequest,
) {
  return requestClient.post<ApiResponse<{ success?: boolean; statusEnum?: string | number }>>(
    `${BASE}/submitInventoryOpeningApproval`,
    body,
  );
}

export async function withdrawInventoryOpeningApprovalApi(
  body: WithdrawInventoryOpeningApprovalRequest,
) {
  return requestClient.post<ApiResponse<{ success?: boolean; statusEnum?: string | number }>>(
    `${BASE}/withdrawInventoryOpeningApproval`,
    body,
  );
}

/** 审批同意：明细多时入账（库存+批号价+追溯）可能超过默认 10s。 */
export async function agreeInventoryOpeningApi(body: AgreeInventoryOpeningRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/agreeInventoryOpening`,
    body,
    { timeout: 0 },
  );
}

export async function rejectInventoryOpeningApi(body: RejectInventoryOpeningRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectInventoryOpening`,
    body,
  );
}

export async function removeInventoryOpeningApi(body: RemoveInventoryOpeningRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removeInventoryOpening`,
    body,
  );
}

export async function pageProductsForInventoryOpeningApi(
  body: PageProductsForInventoryOpeningRequest,
) {
  return requestClient.post<ApiResponse<PageInventoryOpeningsProductsResponse>>(
    `${BASE}/pageProductsForInventoryOpening`,
    body,
  );
}

export async function closeInventoryOpeningApi(body: CloseInventoryOpeningRequest) {
  return requestClient.post<ApiResponse<CloseInventoryOpeningResponse>>(
    `${BASE}/closeInventoryOpening`,
    body,
  );
}

export async function voidInventoryOpeningApi(body: VoidInventoryOpeningRequest) {
  return requestClient.post<ApiResponse<VoidInventoryOpeningResponse>>(
    `${BASE}/voidInventoryOpening`,
    body,
  );
}

export async function copyInventoryOpeningFromPreviousApi(
  body: CopyInventoryOpeningFromPreviousRequest,
) {
  return requestClient.post<ApiResponse<CreateInventoryOpeningResponse>>(
    `${BASE}/copyInventoryOpeningFromPrevious`,
    body,
  );
}

export function mapInventoryOpeningInitOptionsToApiItems(
  list?: InventoryOpeningOptionDto[],
): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}

/** 三类库房合并为单头/选品下拉（关闭库带后缀）。 */
export function mapInventoryOpeningLocationOptions(
  data?: LoadInventoryOpeningFormInitResponse | null,
): Array<ApiOptionItem & { formEnum?: string; openingClosed?: boolean }> {
  const groups = [data?.warehouseOptions, data?.pharmacyOptions, data?.departmentPharmacyOptions];
  const out: Array<ApiOptionItem & { formEnum?: string; openingClosed?: boolean }> = [];
  for (const list of groups) {
    for (const o of list ?? []) {
      const closed = Boolean(o.openingClosed);
      const base = String(o.label ?? '').trim();
      out.push({
        label: closed ? `${base}（已关闭）` : base,
        value: String(o.value ?? ''),
        formEnum: o.formEnum,
        openingClosed: closed,
      });
    }
  }
  return out;
}

/** init 库房下拉 → 侧栏 cabinet 行（含「全部库房」）。 */
export function mapInventoryOpeningInitToCabinetRows(
  data?: LoadInventoryOpeningFormInitResponse | null,
): Record<string, unknown>[] {
  const merged = mapInventoryOpeningLocationOptions(data);
  const byValue = new Map(merged.map((o) => [String(o.value), o]));
  const rows = mapLocationOptionsToCabinetRows(
    merged.map((o) => ({ value: o.value, label: o.label })),
    INV_ALL_WAREHOUSES_LABEL,
  ).map((row) => {
    const key = String(row.id ?? row.value ?? '');
    const src = byValue.get(key);
    return {
      ...row,
      openingClosed: src?.openingClosed,
      formEnum: src?.formEnum,
    };
  });
  return prependAllLocationsCabinetOption(rows, INV_ALL_WAREHOUSES_LABEL);
}

/** 列表行 → 侧栏卡片字段。 */
export function mapInventoryOpeningListRowToQueueCard(
  r: InventoryOpeningListRowDto,
): Record<string, unknown> {
  const st = normalizeStocktakeStatusSort(r.statusEnum);
  const baseStatus = r.statusDisplay ?? stocktakeStatusLabel(st);
  const statusText = r.currentEffective ? `${baseStatus} · 当前有效` : baseStatus;
  return {
    ...r,
    supplyBusNo: r.busNo,
    purposeLocationId: r.locationId,
    purposeLocationName: r.locationName,
    statusEnum_enumText: statusText,
    applicantId_dictText: r.applicantName ?? '',
    approverId_dictText: r.approverName ?? '',
    purposeLocationId_dictText: r.locationName ?? '',
    lineCount: r.lineCount,
    totalPrice: r.totalPrice,
    currentEffective: r.currentEffective,
  };
}

/** 侧栏 daterange（yyyy-MM-dd）→ 后端 LocalDateTime（yyyy-MM-dd HH:mm:ss）闭区间边界。 */
function toBackendDateTimeBound(
  dateOnly: string | undefined,
  endOfDay: boolean,
): string | undefined {
  const d = dateOnly?.trim();
  if (!d) return undefined;
  if (d.includes(' ') || d.includes('T')) return d;
  return endOfDay ? `${d} 23:59:59` : `${d} 00:00:00`;
}

/** 工作站左侧列表：locationId=0 表示全部；日期走后端 start/end，前端再兜底筛。 */
export async function pageInventoryOpeningWorkbenchListApi(params?: {
  locationId?: number | string;
  keyword?: string;
  statusEnum?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: Record<string, unknown>[]; total: number }> {
  const locationRaw = resolveWorkbenchListPurposeLocationId(params?.locationId);
  // 「全部库房」归一为字符串 '0'，须用 isAll… 判断；勿写 === 0（严格相等恒为 false）
  const res = await pageInventoryOpeningsApi({
    locationId: isAllLocationsFilterOptionValue(locationRaw) ? undefined : locationRaw,
    statusEnum: params?.statusEnum?.trim() || undefined,
    busNo: params?.keyword?.trim() || undefined,
    startDate: toBackendDateTimeBound(params?.applyTimeBegin, false),
    endDate: toBackendDateTimeBound(params?.applyTimeEnd, true),
    pageNo: params?.pageNo ?? 1,
    pageSize: params?.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<InventoryOpeningListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params?.applyTimeBegin,
    params?.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered.map((r) => mapInventoryOpeningListRowToQueueCard(r)),
    params?.keyword,
  ) as Record<string, unknown>[];
  const filtered = (params?.applyTimeBegin && params?.applyTimeEnd) || params?.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}

/** 期初选品行 → 进货风格选品行（供 PurchaseOrderMedicineSelect）。 */
export function mapInventoryOpeningProductToPickRow(
  r: InventoryOpeningProductRowDto,
): Record<string, unknown> {
  const defId = r.itemId;
  const purchase = r.purchasePrice;
  return {
    ...r,
    productTableName: r.itemTable,
    productDefinitionId: defId,
    productBusNo: r.productBusNo,
    productName: r.itemName,
    merchandiseName: r.itemName,
    totalVolume: r.specification,
    specification: r.specification,
    purchasePrice: purchase,
    purchaseUnitPrice: purchase,
    unitCode: r.unitCode,
    manufacturer: r.manufacturer,
    categoryCode: r.categoryCode,
    categoryCodeDictText: r.categoryCodeDictText,
    unitBands: r.unitBands,
    id: defId === undefined || defId === null ? '' : String(defId),
  };
}
