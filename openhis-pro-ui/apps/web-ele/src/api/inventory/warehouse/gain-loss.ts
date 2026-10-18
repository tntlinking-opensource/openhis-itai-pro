/**
 * 药库损益单 — 对接 ZXPlat {@code POST /api/app/inventoryWarehouseGainLoss/<method>}
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_WAREHOUSES_LABEL,
  mapLocationOptionsToCabinetRows,
  prependAllLocationsCabinetOption,
  resolveWorkbenchListPurposeLocationId,
} from '#/utils/inventory/inventory-workbench-list';
import {
  normalizeStocktakeStatusSort,
  stocktakeStatusLabel,
} from '#/utils/inventory/stocktake-doc-actions';

const BASE = '/inventoryWarehouseGainLoss';

export const WHS_GAIN_LOSS_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface GainLossOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadGainLossFormInitRequest {}

export interface LoadGainLossFormInitResponse {
  warehouseLocationOptions?: GainLossOptionDto[];
  inboundItemTableTypeOptions?: GainLossOptionDto[];
  statusOptions?: GainLossOptionDto[];
  pharmacistOptions?: GainLossOptionDto[];
  warehouseKeeperOptions?: GainLossOptionDto[];
}

export interface GainLossListRowDto {
  busNo?: string;
  statusEnum?: string | number;
  statusEnumText?: string;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  itemCount?: number;
  gainLossQuantitySum?: number;
  /** 盈亏金额合计（可负；侧栏总金额） */
  gainLossAmountSum?: number;
  actualAmountSum?: number;
  applicantId?: number | string;
  applicantName?: string;
  applyTime?: string;
  approverName?: string;
  /** 驳回态侧栏回显 */
  rejectReason?: string;
}

export interface PageGainLosssRequest {
  purposeLocationId: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageGainLosssResponse {
  total?: number;
  totalCount?: number;
  content?: GainLossListRowDto[];
}

export interface GetGainLossDetailRequest {
  busNo: string;
}

export interface GainLossDetailLineDto {
  lineId?: number | string;
  itemTable?: string;
  itemId?: number | string;
  inventoryItemId?: number | string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  traceNo?: string;
  unitCode?: string;
  bookQuantity?: number;
  gainLossQuantity?: number;
  actualQuantity?: number;
  actualAmount?: number;
  gainLossAmount?: number;
  price?: number;
  reason?: string;
  remake?: string;
  occurrenceTime?: string;
  productCatalog?: { unitBands?: unknown };
}

export interface GetGainLossDetailResponse {
  busNo?: string;
  statusEnum?: string | number;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  practitionerId?: number | string;
  applicantId?: number | string;
  applyTime?: string;
  lines: GainLossDetailLineDto[];
}

export interface GainLossLineDto {
  itemTable?: string;
  itemId?: number | string;
  inventoryItemId?: number | string;
  gainLossQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  reason?: string;
  price?: number;
  gainLossAmount?: number;
  occurrenceTime?: string;
}

export interface CreateGainLossRequest {
  purposeLocationId: number | string;
  practitionerId: number | string;
  categoryEnum?: string;
  lines: GainLossLineDto[];
}

export interface CreateGainLossResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface UpdateGainLossRequest {
  busNo: string;
  purposeLocationId: number | string;
  practitionerId: number | string;
  categoryEnum?: string;
  lines: GainLossLineDto[];
}

export interface UpdateGainLossResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitGainLossApprovalRequest {
  busNo: string;
}

export interface SubmitGainLossApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface WithdrawGainLossApprovalRequest {
  busNo: string;
}

export interface WithdrawGainLossApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface AgreeGainLossRequest {
  busNo: string;
  contractBusNo?: string;
}

export interface RejectGainLossRequest {
  busNo: string;
  rejectReason?: string;
}

export interface RemoveGainLossRequest {
  busNo: string;
}

export interface PageProductsForGainLossRequest {
  productTableName?: string;
  keyword?: string;
  purposeLocationId?: number | string;
  /** true=仅返回账面量>0；false/未传=含零库存 */
  positiveQuantityOnly?: boolean;
  pageNo: number;
  pageSize: number;
}

export interface ProductDefinitionItemDto {
  productDefinitionId?: number;
  productTableName?: string;
  productName?: string;
  merchandiseName?: string;
  unitCode?: string;
  minUnitCode?: string;
  lotNumber?: string;
  price?: number;
}

export interface PageProductsForGainLossResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export async function loadGainLossFormInitApi(body?: LoadGainLossFormInitRequest) {
  return requestClient.post<ApiResponse<LoadGainLossFormInitResponse>>(
    `${BASE}/loadGainLossFormInit`,
    body ?? {},
  );
}

export async function pageGainLosssApi(body: PageGainLosssRequest) {
  return requestClient.post<ApiResponse<PageGainLosssResponse>>(`${BASE}/pageGainLosss`, body);
}

export async function getGainLossDetailApi(body: GetGainLossDetailRequest) {
  return requestClient.post<ApiResponse<GetGainLossDetailResponse>>(
    `${BASE}/getGainLossDetail`,
    body,
  );
}

export async function createGainLossApi(body: CreateGainLossRequest) {
  return requestClient.post<ApiResponse<CreateGainLossResponse>>(`${BASE}/createGainLoss`, body);
}

export async function updateGainLossApi(body: UpdateGainLossRequest) {
  return requestClient.post<ApiResponse<UpdateGainLossResponse>>(`${BASE}/updateGainLoss`, body);
}

export async function submitGainLossApprovalApi(body: SubmitGainLossApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitGainLossApprovalResponse>>(
    `${BASE}/submitGainLossApproval`,
    body,
  );
}

export async function withdrawGainLossApprovalApi(body: WithdrawGainLossApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawGainLossApprovalResponse>>(
    `${BASE}/withdrawGainLossApproval`,
    body,
  );
}

export async function agreeGainLossApi(body: AgreeGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeGainLoss`, body);
}

export async function rejectGainLossApi(body: RejectGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/rejectGainLoss`, body);
}

export async function removeGainLossApi(body: RemoveGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/removeGainLoss`, body);
}

export async function pageProductsForGainLossApi(body: PageProductsForGainLossRequest) {
  return requestClient.post<ApiResponse<PageProductsForGainLossResponse>>(
    `${BASE}/pageProductsForGainLoss`,
    body,
  );
}

export function mapGainLossInitOptionsToApiItems(list?: GainLossOptionDto[]): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}

/** init 药库下拉 → 侧栏 cabinet 行（含「全部库房」）。 */
export function mapGainLossInitToCabinetRows(
  list?: GainLossOptionDto[],
): Record<string, unknown>[] {
  const rows = mapLocationOptionsToCabinetRows(
    (list ?? []).map((o) => ({ value: o.value, label: o.label })),
    INV_ALL_WAREHOUSES_LABEL,
  );
  return prependAllLocationsCabinetOption(rows, INV_ALL_WAREHOUSES_LABEL);
}

/** 列表行 → 侧栏卡片队列字段（对齐盘点单 mapStocktakeListRowToQueueCard）。 */
export function mapGainLossListRowToQueueCard(r: {
  actualAmountSum?: number;
  applicantName?: string;
  approverName?: string;
  busNo?: string;
  gainLossAmountSum?: number;
  purposeLocationName?: string;
  statusEnum?: number | string;
  statusEnumText?: string;
  [key: string]: unknown;
}): Record<string, unknown> {
  const st = normalizeStocktakeStatusSort(r.statusEnum);
  return {
    ...r,
    supplyBusNo: r.busNo,
    statusEnum_enumText: r.statusEnumText ?? stocktakeStatusLabel(st),
    applicantId_dictText: r.applicantName ?? '',
    approverId_dictText: r.approverName ?? '',
    purposeLocationId_dictText: r.purposeLocationName ?? '',
    // 侧栏总金额取盈亏金额合计（可负），勿回落实盘金额
    totalPrice: r.gainLossAmountSum,
  };
}

/** 工作站左侧列表：purposeLocationId=0 表示全部；日期在前端筛。 */
export async function pageWarehouseGainLossWorkbenchListApi(params?: {
  purposeLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: Record<string, unknown>[]; total: number }> {
  const res = await pageGainLosssApi({
    purposeLocationId: resolveWorkbenchListPurposeLocationId(params?.purposeLocationId),
    keyword: params?.keyword?.trim() || undefined,
    pageNo: params?.pageNo ?? 1,
    pageSize: params?.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<GainLossListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params?.applyTimeBegin,
    params?.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered.map((r) => mapGainLossListRowToQueueCard(r)),
    params?.keyword,
  ) as Record<string, unknown>[];
  const filtered = (params?.applyTimeBegin && params?.applyTimeEnd) || params?.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}
