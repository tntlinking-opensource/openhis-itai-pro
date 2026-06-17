/**
 * 科室盘点单 — 对接 {@code POST /api/app/inventoryDeptStocktake/<method>}
 * 契约见 AS-INV-DEP-006-SDD。
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  stocktakeStatusLabel,
  normalizeStocktakeStatusSort,
} from '#/utils/inventory/stocktake-doc-actions';

const BASE = '/inventoryDeptStocktake';

export const DEP_STOCKTAKE_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface StocktakeOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadDeptStocktakeFormInitRequest {
  organizationId?: number;
}

export interface LoadDeptStocktakeFormInitResponse {
  deptLocationOptions?: StocktakeOptionDto[];
  inboundItemTableTypeOptions?: StocktakeOptionDto[];
  statusOptions?: StocktakeOptionDto[];
  nurseOptions?: StocktakeOptionDto[];
  warehouseKeeperOptions?: StocktakeOptionDto[];
}

export interface PageDeptStocktakesRequest {
  purposeLocationId: number | string;
  statusEnum?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface DeptStocktakeListRowDto {
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

export interface PageDeptStocktakesResponse {
  total?: number;
  records: DeptStocktakeListRowDto[];
}

export interface GetDeptStocktakeDetailRequest {
  busNo: string;
}

export interface DeptStocktakeDetailLineDto {
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

export interface GetDeptStocktakeDetailResponse {
  busNo?: string;
  statusEnum?: string | number;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  practitionerId?: number | string;
  applicantId?: number | string;
  applyTime?: string;
  approverId?: number | string;
  approvalTime?: string;
  lines: DeptStocktakeDetailLineDto[];
}

export interface DeptStocktakeLineDto {
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

export interface CreateDeptStocktakeRequest {
  purposeLocationId: number | string;
  practitionerId: string;
  categoryEnum?: string;
  lines: DeptStocktakeLineDto[];
}

export interface CreateDeptStocktakeResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface UpdateDeptStocktakeRequest {
  busNo: string;
  purposeLocationId: number | string;
  practitionerId: string;
  categoryEnum?: string;
  lines: DeptStocktakeLineDto[];
}

export interface UpdateDeptStocktakeResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitDeptStocktakeApprovalRequest {
  busNo: string;
}

export interface WithdrawDeptStocktakeApprovalRequest {
  busNo: string;
}

export interface AgreeDeptStocktakeRequest {
  busNo: string;
  /** @deprecated CHS 启用即上传，请使用 contractBusNo */
  medicareUploadEnabled?: boolean;
  contractBusNo?: string;
}

export interface RejectDeptStocktakeRequest {
  busNo: string;
  changeReasonCode?: string;
  changeReason?: string;
}

export interface RemoveDeptStocktakeRequest {
  busNo: string;
}

export interface PageProductsForDeptStocktakeRequest {
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

export interface PageProductsForDeptStocktakeResponse {
  total?: number;
  records: StocktakeProductItemDto[];
}

export interface DeptStocktakeLocationOption {
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

export function mapDeptStocktakeInitItemTableTypes(opts?: StocktakeOptionDto[]): ApiOptionItem[] {
  return (opts ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
    sort:
      o.sort !== undefined && o.sort !== '' && Number.isFinite(Number(o.sort))
        ? Number(o.sort)
        : undefined,
  }));
}

export function mapDeptStocktakeInitToLocationOptions(
  list?: StocktakeOptionDto[],
): DeptStocktakeLocationOption[] {
  return (list ?? []).map((o) => ({
    locationId: o.value,
    displayName: o.label,
  }));
}

function mapDeptStocktakeListRowToQueueCard(r: DeptStocktakeListRowDto): Record<string, unknown> {
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

export async function loadDeptStocktakeFormInitApi(body?: LoadDeptStocktakeFormInitRequest) {
  return requestClient.post<ApiResponse<LoadDeptStocktakeFormInitResponse>>(
    `${BASE}/loadDeptStocktakeFormInit`,
    body ?? {},
  );
}

export async function pageDeptStocktakesApi(body: PageDeptStocktakesRequest) {
  return requestClient.post<ApiResponse<PageDeptStocktakesResponse>>(
    `${BASE}/pageDeptStocktakes`,
    body,
  );
}

export async function getDeptStocktakeDetailApi(body: GetDeptStocktakeDetailRequest) {
  return requestClient.post<ApiResponse<GetDeptStocktakeDetailResponse>>(
    `${BASE}/getDeptStocktakeDetail`,
    body,
  );
}

export async function createDeptStocktakeApi(body: CreateDeptStocktakeRequest) {
  return requestClient.post<ApiResponse<CreateDeptStocktakeResponse>>(
    `${BASE}/createDeptStocktake`,
    body,
  );
}

export async function updateDeptStocktakeApi(body: UpdateDeptStocktakeRequest) {
  return requestClient.post<ApiResponse<UpdateDeptStocktakeResponse>>(
    `${BASE}/updateDeptStocktake`,
    body,
  );
}

export async function submitDeptStocktakeApprovalApi(body: SubmitDeptStocktakeApprovalRequest) {
  return requestClient.post<ApiResponse<{ statusEnum?: string | number }>>(
    `${BASE}/submitDeptStocktakeApproval`,
    body,
  );
}

export async function withdrawDeptStocktakeApprovalApi(body: WithdrawDeptStocktakeApprovalRequest) {
  return requestClient.post<ApiResponse<{ statusEnum?: string | number }>>(
    `${BASE}/withdrawDeptStocktakeApproval`,
    body,
  );
}

export async function agreeDeptStocktakeApi(body: AgreeDeptStocktakeRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeDeptStocktake`, body);
}

export async function rejectDeptStocktakeApi(body: RejectDeptStocktakeRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectDeptStocktake`,
    body,
  );
}

export async function removeDeptStocktakeApi(body: RemoveDeptStocktakeRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removeDeptStocktake`,
    body,
  );
}

export async function pageProductsForDeptStocktakeApi(body: PageProductsForDeptStocktakeRequest) {
  return requestClient.post<ApiResponse<PageProductsForDeptStocktakeResponse>>(
    `${BASE}/pageProductsForDeptStocktake`,
    body,
  );
}

/** 工作站左侧列表。 */
export async function pageDeptStocktakeWorkbenchListApi(params?: {
  purposeLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
}): Promise<{ records: Record<string, unknown>[]; total: number }> {
  const pid = params?.purposeLocationId;
  const purposeLocationId =
    pid === undefined || pid === null || String(pid).trim() === '' ? '0' : String(pid).trim();

  const res = await pageDeptStocktakesApi({
    purposeLocationId,
    applyTimeFrom: params?.applyTimeBegin ? `${params.applyTimeBegin}T00:00:00` : undefined,
    applyTimeTo: params?.applyTimeEnd ? `${params.applyTimeEnd}T23:59:59` : undefined,
    pageNo: 1,
    pageSize: 100,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<DeptStocktakeListRowDto>(res.data);
  const kw = params?.keyword?.trim().toLowerCase();
  let records = content.map((r) => mapDeptStocktakeListRowToQueueCard(r));
  if (kw) {
    records = records.filter((r) => {
      const no = String(r.busNo ?? r.supplyBusNo ?? '').toLowerCase();
      return no.includes(kw);
    });
  }
  return { records, total: kw ? records.length : totalCount };
}

/** 进入页面：科室盘点 init。 */
export async function initDeptStocktakeWorkbenchApi(): Promise<{
  cabinetListOptions: Record<string, unknown>[];
  locationRawOptions: DeptStocktakeLocationOption[];
  itemTableTypeOptions: ApiOptionItem[];
}> {
  const res = await loadDeptStocktakeFormInitApi({});
  const d = res.data;
  const rows = (d?.deptLocationOptions ?? []).map(optionDtoToCabinetRow);
  return {
    cabinetListOptions: rows,
    locationRawOptions: mapDeptStocktakeInitToLocationOptions(d?.deptLocationOptions),
    itemTableTypeOptions: mapDeptStocktakeInitItemTableTypes(d?.inboundItemTableTypeOptions),
  };
}
