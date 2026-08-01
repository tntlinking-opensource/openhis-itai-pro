/**
 * 药房盘点单 — 对接 {@code POST /api/app/inventoryPharmacyStocktake/<method>}
 * 契约见 AS-INV-PHA-006-SDD。
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  INV_ALL_PHARMACIES_LABEL,
  mapInitOptionDtoToLocationSelectOptions,
  mapLocationOptionsToCabinetRows,
  pickInitOptionDtoList,
  prependAllLocationsCabinetOption,
} from '#/utils/inventory/inventory-workbench-list';
import {
  stocktakeStatusLabel,
  normalizeStocktakeStatusSort,
} from '#/utils/inventory/stocktake-doc-actions';

const BASE = '/inventoryPharmacyStocktake';

export const PHA_STOCKTAKE_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface StocktakeOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadPharmacyStocktakeFormInitRequest {
  organizationId?: number;
}

export interface LoadPharmacyStocktakeFormInitResponse {
  pharmacyLocationOptions?: StocktakeOptionDto[];
  inboundItemTableTypeOptions?: StocktakeOptionDto[];
  statusOptions?: StocktakeOptionDto[];
  pharmacistOptions?: StocktakeOptionDto[];
  warehouseKeeperOptions?: StocktakeOptionDto[];
}

export interface PagePharmacyStocktakesRequest {
  purposeLocationId: number | string;
  statusEnum?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface PharmacyStocktakeListRowDto {
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

export interface PagePharmacyStocktakesResponse {
  total?: number;
  records: PharmacyStocktakeListRowDto[];
}

export interface GetPharmacyStocktakeDetailRequest {
  busNo: string;
}

export interface PharmacyStocktakeDetailLineDto {
  lineId?: number | string;
  itemTable?: string;
  itemId?: number | string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  traceNo?: string;
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

export interface GetPharmacyStocktakeDetailResponse {
  busNo?: string;
  statusEnum?: string | number;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  practitionerId?: number | string;
  applicantId?: number | string;
  applyTime?: string;
  approverId?: number | string;
  approvalTime?: string;
  lines: PharmacyStocktakeDetailLineDto[];
}

export interface PharmacyStocktakeLineDto {
  itemTable?: string;
  itemId?: number | string;
  profitLossQuantity: number;
  actualQuantity?: number;
  actualAmount?: number;
  profitLossAmount?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  inventoryItemId?: number | string;
  price?: number;
  remake?: string;
  occurrenceTime?: string;
}

export interface CreatePharmacyStocktakeRequest {
  purposeLocationId: number | string;
  practitionerId: string;
  categoryEnum?: string;
  lines: PharmacyStocktakeLineDto[];
}

export interface CreatePharmacyStocktakeResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface UpdatePharmacyStocktakeRequest {
  busNo: string;
  purposeLocationId: number | string;
  practitionerId: string;
  categoryEnum?: string;
  lines: PharmacyStocktakeLineDto[];
}

export interface UpdatePharmacyStocktakeResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitPharmacyStocktakeApprovalRequest {
  busNo: string;
}

export interface WithdrawPharmacyStocktakeApprovalRequest {
  busNo: string;
}

export interface AgreePharmacyStocktakeRequest {
  busNo: string;
  /** @deprecated CHS 启用即上传，请使用 contractBusNo */
  medicareUploadEnabled?: boolean;
  contractBusNo?: string;
}

export interface RejectPharmacyStocktakeRequest {
  busNo: string;
  changeReasonCode?: string;
  changeReason?: string;
}

export interface RemovePharmacyStocktakeRequest {
  busNo: string;
}

export interface PageProductsForPharmacyStocktakeRequest {
  purposeLocationId: number | string;
  productTableName: string;
  keyword?: string;
  /** true=仅返回账面量>0；false/未传=含零库存 */
  positiveQuantityOnly?: boolean;
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
  traceNo?: string;
  expirationDate?: string;
  availableQuantity?: number | string;
  unitBands?: StocktakeProductUnitBandDto[];
  purchaseUnitPrice?: number | string;
  inventoryItemId?: number | string;
}

export interface PageProductsForPharmacyStocktakeResponse {
  total?: number;
  records: StocktakeProductItemDto[];
}

export interface PharmacyStocktakeLocationOption {
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

export function mapPharmacyStocktakeInitItemTableTypes(
  opts?: StocktakeOptionDto[],
): ApiOptionItem[] {
  return (opts ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
    sort:
      o.sort !== undefined && o.sort !== '' && Number.isFinite(Number(o.sort))
        ? Number(o.sort)
        : undefined,
  }));
}

export function mapPharmacyStocktakeInitToLocationOptions(
  list?: StocktakeOptionDto[],
): PharmacyStocktakeLocationOption[] {
  return mapInitOptionDtoToLocationSelectOptions(list);
}

function mapPharmacyStocktakeListRowToQueueCard(
  r: PharmacyStocktakeListRowDto,
): Record<string, unknown> {
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

export async function loadPharmacyStocktakeFormInitApi(
  body?: LoadPharmacyStocktakeFormInitRequest,
) {
  return requestClient.post<ApiResponse<LoadPharmacyStocktakeFormInitResponse>>(
    `${BASE}/loadPharmacyStocktakeFormInit`,
    body ?? {},
  );
}

export async function pagePharmacyStocktakesApi(body: PagePharmacyStocktakesRequest) {
  return requestClient.post<ApiResponse<PagePharmacyStocktakesResponse>>(
    `${BASE}/pagePharmacyStocktakes`,
    body,
  );
}

export async function getPharmacyStocktakeDetailApi(body: GetPharmacyStocktakeDetailRequest) {
  return requestClient.post<ApiResponse<GetPharmacyStocktakeDetailResponse>>(
    `${BASE}/getPharmacyStocktakeDetail`,
    body,
  );
}

export async function createPharmacyStocktakeApi(body: CreatePharmacyStocktakeRequest) {
  return requestClient.post<ApiResponse<CreatePharmacyStocktakeResponse>>(
    `${BASE}/createPharmacyStocktake`,
    body,
  );
}

export async function updatePharmacyStocktakeApi(body: UpdatePharmacyStocktakeRequest) {
  return requestClient.post<ApiResponse<UpdatePharmacyStocktakeResponse>>(
    `${BASE}/updatePharmacyStocktake`,
    body,
  );
}

export async function submitPharmacyStocktakeApprovalApi(
  body: SubmitPharmacyStocktakeApprovalRequest,
) {
  return requestClient.post<ApiResponse<{ statusEnum?: string | number }>>(
    `${BASE}/submitPharmacyStocktakeApproval`,
    body,
  );
}

export async function withdrawPharmacyStocktakeApprovalApi(
  body: WithdrawPharmacyStocktakeApprovalRequest,
) {
  return requestClient.post<ApiResponse<{ statusEnum?: string | number }>>(
    `${BASE}/withdrawPharmacyStocktakeApproval`,
    body,
  );
}

export async function agreePharmacyStocktakeApi(body: AgreePharmacyStocktakeRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/agreePharmacyStocktake`,
    body,
  );
}

export async function rejectPharmacyStocktakeApi(body: RejectPharmacyStocktakeRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectPharmacyStocktake`,
    body,
  );
}

export async function removePharmacyStocktakeApi(body: RemovePharmacyStocktakeRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removePharmacyStocktake`,
    body,
  );
}

export async function pageProductsForPharmacyStocktakeApi(
  body: PageProductsForPharmacyStocktakeRequest,
) {
  return requestClient.post<ApiResponse<PageProductsForPharmacyStocktakeResponse>>(
    `${BASE}/pageProductsForPharmacyStocktake`,
    body,
  );
}

/** 工作站左侧列表。 */
export async function pagePharmacyStocktakeWorkbenchListApi(params?: {
  purposeLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
}): Promise<{ records: Record<string, unknown>[]; total: number }> {
  const pid = params?.purposeLocationId;
  const purposeLocationId =
    pid === undefined || pid === null || String(pid).trim() === '' ? '0' : String(pid).trim();

  const res = await pagePharmacyStocktakesApi({
    purposeLocationId,
    pageNo: 1,
    pageSize: 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<PharmacyStocktakeListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params?.applyTimeBegin,
    params?.applyTimeEnd,
  );
  const kw = params?.keyword?.trim().toLowerCase();
  let records = dateFiltered.map((r) => mapPharmacyStocktakeListRowToQueueCard(r));
  if (kw) {
    records = records.filter((r) => {
      const no = String(r.busNo ?? r.supplyBusNo ?? '').toLowerCase();
      return no.includes(kw);
    });
  }
  const filtered = params?.applyTimeBegin && params?.applyTimeEnd;
  return { records, total: kw || filtered ? records.length : totalCount };
}

/** 进入页面：药房盘点 init。 */
export async function initPharmacyStocktakeWorkbenchApi(): Promise<{
  cabinetListOptions: Record<string, unknown>[];
  locationRawOptions: PharmacyStocktakeLocationOption[];
  itemTableTypeOptions: ApiOptionItem[];
}> {
  const res = await loadPharmacyStocktakeFormInitApi({});
  const d = res.data;
  const locationOpts = pickInitOptionDtoList(
    d as Record<string, unknown> | undefined,
    'pharmacyLocationOptions',
  );
  const rows = mapLocationOptionsToCabinetRows(
    locationOpts.map((o) => ({ value: o.value, label: o.label })),
    INV_ALL_PHARMACIES_LABEL,
  );
  return {
    cabinetListOptions: prependAllLocationsCabinetOption(rows, INV_ALL_PHARMACIES_LABEL),
    locationRawOptions: mapPharmacyStocktakeInitToLocationOptions(locationOpts),
    itemTableTypeOptions: mapPharmacyStocktakeInitItemTableTypes(d?.inboundItemTableTypeOptions),
  };
}
