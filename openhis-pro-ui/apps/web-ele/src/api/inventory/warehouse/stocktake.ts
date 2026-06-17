/**
 * 药库盘库单 — 对接 {@code POST /api/app/inventoryWarehouseStocktake/<method>}
 * 契约见 AS-INV-WHS-007-SDD。
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  stocktakeStatusLabel,
  normalizeStocktakeStatusSort,
} from '#/utils/inventory/stocktake-doc-actions';

const BASE = '/inventoryWarehouseStocktake';

export const WHS_STOCKTAKE_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface StocktakeOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadStocktakeFormInitRequest {
  organizationId?: number;
}

export interface LoadStocktakeFormInitResponse {
  warehouseLocationOptions?: StocktakeOptionDto[];
  inboundItemTableTypeOptions?: StocktakeOptionDto[];
  statusOptions?: StocktakeOptionDto[];
  pharmacistOptions?: StocktakeOptionDto[];
  warehouseKeeperOptions?: StocktakeOptionDto[];
}

export interface PageStocktakesRequest {
  purposeLocationId: number | string;
  statusEnum?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface StocktakeListRowDto {
  busNo?: string;
  statusEnum?: string | number;
  statusEnumText?: string;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  itemCount?: number;
  profitLossQuantitySum?: number;
  actualAmountSum?: number;
  applicantId?: number | string;
  applicantName?: string;
  applyTime?: string;
  approverId?: number | string;
  approverName?: string;
  approvalTime?: string;
  occurrenceTime?: string;
}

export interface PageStocktakesResponse {
  total?: number;
  records: StocktakeListRowDto[];
}

export interface GetStocktakeDetailRequest {
  busNo: string;
}

export interface StocktakeDetailLineDto {
  lineId?: number | string;
  itemTable?: string;
  itemId?: number | string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  expirationDate?: string;
  unitCode?: string;
  bookQuantity?: number;
  profitLossQuantity?: number;
  actualQuantity?: number;
  actualAmount?: number;
  profitLossAmount?: number;
  price?: number;
  remake?: string;
  occurrenceTime?: string;
  productCatalog?: unknown;
}

export interface GetStocktakeDetailResponse {
  busNo?: string;
  statusEnum?: string | number;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  practitionerId?: number | string;
  applicantId?: number | string;
  applyTime?: string;
  approverId?: number | string;
  approvalTime?: string;
  lines: StocktakeDetailLineDto[];
}

export interface StocktakeLineDto {
  itemTable?: string;
  itemId?: number | string;
  profitLossQuantity: number;
  actualQuantity?: number;
  actualAmount?: number;
  profitLossAmount?: number;
  unitCode?: string;
  lotNumber?: string;
  inventoryItemId?: number | string;
  price?: number;
  remake?: string;
  occurrenceTime?: string;
}

export interface CreateStocktakeRequest {
  purposeLocationId: number | string;
  practitionerId: string;
  categoryEnum?: string;
  lines: StocktakeLineDto[];
}

export interface CreateStocktakeResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface UpdateStocktakeRequest {
  busNo: string;
  purposeLocationId: number | string;
  practitionerId: string;
  categoryEnum?: string;
  lines: StocktakeLineDto[];
}

export interface UpdateStocktakeResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitStocktakeApprovalRequest {
  busNo: string;
}

export interface WithdrawStocktakeApprovalRequest {
  busNo: string;
}

export interface AgreeStocktakeRequest {
  busNo: string;
  /** @deprecated CHS 启用即上传，请使用 contractBusNo */
  medicareUploadEnabled?: boolean;
  contractBusNo?: string;
}

export interface RejectStocktakeRequest {
  busNo: string;
  changeReasonCode?: string;
  changeReason?: string;
}

export interface RemoveStocktakeRequest {
  busNo: string;
}

export interface PageProductsForStocktakeRequest {
  purposeLocationId: number | string;
  productTableName: string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface StocktakeProductUnitBandDto {
  bandKind?: string;
  unitCode?: string;
  unitName?: string;
  ratioToSplit?: number | string;
  unitPrice?: number | string;
}

export interface StocktakeProductItemDto {
  productTableName?: string;
  productDefinitionId?: number | string;
  /** 项目业务编码（选品下拉「项目编码」列） */
  productBusNo?: string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  expirationDate?: string;
  availableQuantity?: number | string;
  unitBands?: StocktakeProductUnitBandDto[];
  purchaseUnitPrice?: number | string;
  inventoryItemId?: number | string;
}

export interface PageProductsForStocktakeResponse {
  total?: number;
  records: StocktakeProductItemDto[];
}

export interface WarehouseStocktakeLocationOption {
  locationId?: number | string;
  displayName?: string;
}

function optionDtoToCabinetRow(o: StocktakeOptionDto): Record<string, unknown> {
  const raw = o.value;
  if (raw === undefined || raw === null) {
    return { id: '', value: '', name: o.label ?? '', label: o.label ?? '' };
  }
  if (typeof raw === 'string') {
    const s = raw.trim();
    return { id: s, value: s, name: o.label ?? '', label: o.label ?? '' };
  }
  if (typeof raw === 'number') {
    const id = Number.isSafeInteger(raw) ? raw : String(raw);
    return { id, value: raw, name: o.label ?? '', label: o.label ?? '' };
  }
  const s = String(raw).trim();
  return { id: s, value: s, name: o.label ?? '', label: o.label ?? '' };
}

export function mapStocktakeInitItemTableTypes(opts?: StocktakeOptionDto[]): ApiOptionItem[] {
  return (opts ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
    sort:
      o.sort !== undefined && o.sort !== '' && Number.isFinite(Number(o.sort))
        ? Number(o.sort)
        : undefined,
  }));
}

export function mapWarehouseStocktakeInitToLocationOptions(
  list?: StocktakeOptionDto[],
): WarehouseStocktakeLocationOption[] {
  return (list ?? []).map((o) => ({
    locationId: o.value,
    displayName: o.label,
  }));
}

function mapStocktakeListRowToQueueCard(r: StocktakeListRowDto): Record<string, unknown> {
  const st = normalizeStocktakeStatusSort(r.statusEnum);
  return {
    ...r,
    supplyBusNo: r.busNo,
    statusEnum_enumText: r.statusEnumText ?? stocktakeStatusLabel(st),
    applicantId_dictText: r.applicantName ?? '',
    approverId_dictText: r.approverName ?? '',
    purposeLocationId_dictText: r.purposeLocationName ?? '',
  };
}

export async function loadWarehouseStocktakeFormInitApi(body?: LoadStocktakeFormInitRequest) {
  return requestClient.post<ApiResponse<LoadStocktakeFormInitResponse>>(
    `${BASE}/loadStocktakeFormInit`,
    body ?? {},
  );
}

export async function pageWarehouseStocktakesApi(body: PageStocktakesRequest) {
  return requestClient.post<ApiResponse<PageStocktakesResponse>>(`${BASE}/pageStocktakes`, body);
}

export async function getWarehouseStocktakeDetailApi(body: GetStocktakeDetailRequest) {
  return requestClient.post<ApiResponse<GetStocktakeDetailResponse>>(
    `${BASE}/getStocktakeDetail`,
    body,
  );
}

export async function createWarehouseStocktakeApi(body: CreateStocktakeRequest) {
  return requestClient.post<ApiResponse<CreateStocktakeResponse>>(`${BASE}/createStocktake`, body);
}

export async function updateWarehouseStocktakeApi(body: UpdateStocktakeRequest) {
  return requestClient.post<ApiResponse<UpdateStocktakeResponse>>(`${BASE}/updateStocktake`, body);
}

export async function submitWarehouseStocktakeApprovalApi(body: SubmitStocktakeApprovalRequest) {
  return requestClient.post<ApiResponse<{ statusEnum?: string | number }>>(
    `${BASE}/submitStocktakeApproval`,
    body,
  );
}

export async function withdrawWarehouseStocktakeApprovalApi(
  body: WithdrawStocktakeApprovalRequest,
) {
  return requestClient.post<ApiResponse<{ statusEnum?: string | number }>>(
    `${BASE}/withdrawStocktakeApproval`,
    body,
  );
}

export async function agreeWarehouseStocktakeApi(body: AgreeStocktakeRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeStocktake`, body);
}

export async function rejectWarehouseStocktakeApi(body: RejectStocktakeRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/rejectStocktake`, body);
}

export async function removeWarehouseStocktakeApi(body: RemoveStocktakeRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/removeStocktake`, body);
}

export async function pageProductsForWarehouseStocktakeApi(body: PageProductsForStocktakeRequest) {
  return requestClient.post<ApiResponse<PageProductsForStocktakeResponse>>(
    `${BASE}/pageProductsForStocktake`,
    body,
  );
}

/** 工作站左侧列表。 */
export async function pageWarehouseStocktakeWorkbenchListApi(params?: {
  purposeLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
}): Promise<{ records: Record<string, unknown>[]; total: number }> {
  const pid = params?.purposeLocationId;
  const purposeLocationId =
    pid === undefined || pid === null || String(pid).trim() === '' ? '0' : String(pid).trim();

  const res = await pageWarehouseStocktakesApi({
    purposeLocationId,
    applyTimeFrom: params?.applyTimeBegin ? `${params.applyTimeBegin}T00:00:00` : undefined,
    applyTimeTo: params?.applyTimeEnd ? `${params.applyTimeEnd}T23:59:59` : undefined,
    pageNo: 1,
    pageSize: 100,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<StocktakeListRowDto>(res.data);
  const kw = params?.keyword?.trim().toLowerCase();
  let records = content.map((r) => mapStocktakeListRowToQueueCard(r));
  if (kw) {
    records = records.filter((r) => {
      const no = String(r.busNo ?? r.supplyBusNo ?? '').toLowerCase();
      return no.includes(kw);
    });
  }
  return { records, total: kw ? records.length : totalCount };
}

/** 进入页面：药库盘库 init。 */
export async function initWarehouseStocktakeWorkbenchApi(): Promise<{
  cabinetListOptions: Record<string, unknown>[];
  locationRawOptions: WarehouseStocktakeLocationOption[];
  itemTableTypeOptions: ApiOptionItem[];
}> {
  const res = await loadWarehouseStocktakeFormInitApi({});
  const d = res.data;
  const rows = (d?.warehouseLocationOptions ?? []).map(optionDtoToCabinetRow);
  return {
    cabinetListOptions: rows,
    locationRawOptions: mapWarehouseStocktakeInitToLocationOptions(d?.warehouseLocationOptions),
    itemTableTypeOptions: mapStocktakeInitItemTableTypes(d?.inboundItemTableTypeOptions),
  };
}
