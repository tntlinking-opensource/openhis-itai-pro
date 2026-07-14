/**
 * 药房请领单 — 对接 {@code POST /api/app/inventoryPharmacyRequisition/<method>}
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_PHARMACIES_LABEL,
  mapInitOptionDtoToApiItems,
  mapInitOptionsToCabinetRowsWithAll,
  resolveWorkbenchListPurposeLocationId,
} from '#/utils/inventory/inventory-workbench-list';

const BASE = '/inventoryPharmacyRequisition';

// --- DTO（与 Java 驼峰一致）---

/** 与后端 {@link com.openhis.whale.core.application.dto.OptionDto} 同构。 */
export interface PharmacyRequisitionOptionDto {
  sort?: string | number;
  value?: string;
  label?: string;
}

/**
 * @deprecated 历史字段名；请使用 {@link PharmacyRequisitionOptionDto}（value/label）。
 * 仍被出库等页引用，保留兼容别名。
 */
export interface PharmacyLocationOptionDto extends PharmacyRequisitionOptionDto {
  locationId?: number | string;
  displayName?: string;
}

export interface LoadPharmacyRequisitionFormInitRequest {
  organizationId?: number;
}

export interface LoadPharmacyRequisitionFormInitResponse {
  pharmacyLocationOptions: PharmacyRequisitionOptionDto[];
  warehouseLocationOptions: PharmacyRequisitionOptionDto[];
  statusOptions?: PharmacyRequisitionOptionDto[];
}

export interface PharmacyRequisitionListRowDto {
  busNo?: string;
  typeEnum?: string | number;
  statusEnum?: string | number;
  sourceLocationId?: number | string;
  sourceLocationName?: string;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  applyTime?: string;
  approvalTime?: string;
  occurrenceTime?: string;
}

export interface PagePharmacyRequisitionsRequest {
  /** 目的药房，对应读模型 purpose_location_id；0 表示全部 */
  purposeLocationId: number | string;
  statusEnum?: string | number;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
  typeEnum?: string | number;
}

export interface WarehouseDocumentDetailRowDto {
  id?: number;
  busNo?: string;
  typeEnum?: number;
  statusEnum?: number;
  sourceLocationId?: number;
  purposeLocationId?: number;
  supplierId?: number;
  itemId?: number;
  itemTable?: string;
  itemQuantity?: number;
  unitCode?: string;
  applyTime?: string;
  approvalTime?: string;
  totalPrice?: number;
}

export interface PagePharmacyRequisitionsResponse {
  total?: number;
  totalCount?: number;
  records?: PharmacyRequisitionListRowDto[];
  content?: PharmacyRequisitionListRowDto[];
}

export interface GetPharmacyRequisitionDetailRequest {
  busNo: string;
}

export interface PharmacyRequisitionDetailLineDto {
  id?: number | string;
  itemTable?: string;
  itemId?: number | string;
  inventoryItemId?: number | string;
  itemQuantity?: number;
  unitCode?: string;
  unitCodeDictText?: string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  startTime?: string;
  endTime?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  remake?: string;
  occurrenceTime?: string;
  productCatalog?: { unitBands?: unknown };
}

export interface GetPharmacyRequisitionDetailResponse {
  busNo?: string;
  typeEnum?: number;
  statusEnum?: number;
  sourceLocationId?: number;
  purposeLocationId?: number;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  lines: PharmacyRequisitionDetailLineDto[];
}

export interface PharmacyRequisitionLineDto {
  itemTable?: string;
  itemId?: number | string;
  inventoryItemId?: number | string;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
}

export interface CreatePharmacyRequisitionRequest {
  sourceLocationId: number | string;
  purposeLocationId: number | string;
  sourceTypeEnum: string | number;
  purposeTypeEnum: string | number;
  categoryEnum?: string | number;
  practitionerId: number | string;
  lines: PharmacyRequisitionLineDto[];
  typeEnum?: string | number;
}

export interface UpdatePharmacyRequisitionRequest {
  busNo: string;
  sourceLocationId: number | string;
  purposeLocationId: number | string;
  sourceTypeEnum: string | number;
  purposeTypeEnum: string | number;
  categoryEnum?: string | number;
  practitionerId: number | string;
  lines: PharmacyRequisitionLineDto[];
  typeEnum?: string | number;
}

export interface CreatePharmacyRequisitionResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface UpdatePharmacyRequisitionResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface SubmitPharmacyRequisitionApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface WithdrawPharmacyRequisitionApprovalRequest {
  busNo: string;
}

export interface SubmitPharmacyRequisitionApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface WithdrawPharmacyRequisitionApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface AgreePharmacyRequisitionRequest {
  busNo: string;
  operatorId?: number;
  approvalClock?: string;
}

export interface AgreePharmacyRequisitionResponse {
  busNo?: string;
  stockOutBusNo?: string;
  pharmacyStockInBusNo?: string;
}

export interface RejectPharmacyRequisitionRequest {
  busNo: string;
  rejectReason?: string;
  operatorId?: number;
  approvalClock?: string;
}

export interface RemovePharmacyRequisitionRequest {
  busNo: string;
}

export interface PageProductsForPharmacyRequisitionRequest {
  productTableName?: string;
  keyword?: string;
  categoryCode?: string;
  activeFlag?: boolean;
  supplierId?: number;
  manufacturer?: string;
  includeInventory?: boolean;
  sourceLocationId?: number;
  pageNo: number;
  pageSize: number;
  sortField?: string;
  sortOrder?: string;
}

export interface ProductDefinitionItemDto {
  productDefinitionId?: number | string;
  productTableName?: string;
  productName?: string;
  merchandiseName?: string;
  specification?: string;
  unitCode?: string;
  minUnitCode?: string;
  categoryCode?: string;
  price?: number;
  purchasePrice?: number;
  purchaseUnitPrice?: number;
  manufacturer?: string;
  lotNumber?: string;
  expirationDate?: string;
  inventoryItemId?: number | string;
  availableQuantity?: number;
  pyStr?: string;
  wbStr?: string;
}

export interface PageProductsForPharmacyRequisitionResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

// --- 与后端 SupplyType / SupplyRequestType / SupplyCategory getCode() 字符串一致 ---

/** {@link com.openhis.health.domain.share.enums.SupplyType#PHARMACY_REQUISITION} */
export const PHA_REQUISITION_TYPE_ENUM = 'PHARMACY_REQUISITION';
/** 源：药库 CABINET */
export const PHA_REQUISITION_SOURCE_TYPE_ENUM = 'CABINET';
/** 目的：药房 PHARMACY */
export const PHA_REQUISITION_PURPOSE_TYPE_ENUM = 'PHARMACY';
/** {@link com.openhis.health.domain.share.enums.SupplyCategory#STOCK_SUPPLY} */
export const PHA_REQUISITION_CATEGORY_ENUM = 'STOCK_SUPPLY';

/** @deprecated 使用 {@link PHA_REQUISITION_TYPE_ENUM} */
export const PHA_QL_TYPE_ENUM = 12;
/** @deprecated 使用 {@link PHA_REQUISITION_SOURCE_TYPE_ENUM} */
export const PHA_QL_SOURCE_TYPE_CABINET = 11;
/** @deprecated 使用 {@link PHA_REQUISITION_PURPOSE_TYPE_ENUM} */
export const PHA_QL_PURPOSE_TYPE_PHARMACY = 16;
/** @deprecated 使用 {@link PHA_REQUISITION_CATEGORY_ENUM} */
export const PHA_QL_CATEGORY_STOCK = 1;

// --- API ---

export async function loadPharmacyRequisitionFormInitApi(
  body?: LoadPharmacyRequisitionFormInitRequest,
) {
  return requestClient.post<ApiResponse<LoadPharmacyRequisitionFormInitResponse>>(
    `${BASE}/loadPharmacyRequisitionFormInit`,
    body ?? {},
  );
}

export async function pagePharmacyRequisitionsApi(body: PagePharmacyRequisitionsRequest) {
  return requestClient.post<ApiResponse<PagePharmacyRequisitionsResponse>>(
    `${BASE}/pagePharmacyRequisitions`,
    body,
  );
}

export async function getPharmacyRequisitionDetailApi(body: GetPharmacyRequisitionDetailRequest) {
  return requestClient.post<ApiResponse<GetPharmacyRequisitionDetailResponse>>(
    `${BASE}/getPharmacyRequisitionDetail`,
    body,
  );
}

export async function createPharmacyRequisitionApi(body: CreatePharmacyRequisitionRequest) {
  return requestClient.post<ApiResponse<CreatePharmacyRequisitionResponse>>(
    `${BASE}/createPharmacyRequisition`,
    body,
  );
}

export async function updatePharmacyRequisitionApi(body: UpdatePharmacyRequisitionRequest) {
  return requestClient.post<ApiResponse<UpdatePharmacyRequisitionResponse>>(
    `${BASE}/updatePharmacyRequisition`,
    body,
  );
}

export async function submitPharmacyRequisitionApprovalApi(
  body: SubmitPharmacyRequisitionApprovalRequest,
) {
  return requestClient.post<ApiResponse<SubmitPharmacyRequisitionApprovalResponse>>(
    `${BASE}/submitPharmacyRequisitionApproval`,
    body,
  );
}

export async function withdrawPharmacyRequisitionApprovalApi(
  body: WithdrawPharmacyRequisitionApprovalRequest,
) {
  return requestClient.post<ApiResponse<WithdrawPharmacyRequisitionApprovalResponse>>(
    `${BASE}/withdrawPharmacyRequisitionApproval`,
    body,
  );
}

export async function agreePharmacyRequisitionApi(body: AgreePharmacyRequisitionRequest) {
  return requestClient.post<ApiResponse<AgreePharmacyRequisitionResponse>>(
    `${BASE}/agreePharmacyRequisition`,
    body,
  );
}

export async function rejectPharmacyRequisitionApi(body: RejectPharmacyRequisitionRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectPharmacyRequisition`,
    body,
  );
}

export async function removePharmacyRequisitionApi(body: RemovePharmacyRequisitionRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removePharmacyRequisition`,
    body,
  );
}

export async function pageProductsForPharmacyRequisitionApi(
  body: PageProductsForPharmacyRequisitionRequest,
) {
  return requestClient.post<ApiResponse<PageProductsForPharmacyRequisitionResponse>>(
    `${BASE}/pageProductsForPharmacyRequisition`,
    body,
  );
}

/** init 源药库下拉 → ApiOptionItem（单头选源库）。 */
export function mapPharmacyRequisitionWarehouseOptionsToApiItems(
  list?: PharmacyRequisitionOptionDto[],
): ApiOptionItem[] {
  return mapInitOptionDtoToApiItems(list);
}

/** init 目的药房下拉 → ApiOptionItem（单头选目的药房）。 */
export function mapPharmacyRequisitionPharmacyOptionsToApiItems(
  list?: PharmacyRequisitionOptionDto[],
): ApiOptionItem[] {
  return mapInitOptionDtoToApiItems(list);
}

/** init 目的药房下拉 → 侧栏 cabinet 行（含「全部药房」）。 */
export function mapPharmacyRequisitionInitToCabinetRows(
  list?: PharmacyRequisitionOptionDto[],
): Record<string, unknown>[] {
  return mapInitOptionsToCabinetRowsWithAll(list ?? [], INV_ALL_PHARMACIES_LABEL);
}

/** 工作站左侧列表：purposeLocationId=0 表示全部；日期/关键字在前端筛。 */
export async function pagePharmacyRequisitionWorkbenchListApi(params: {
  purposeLocationId?: number | string;
  typeEnum?: string | number;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: PharmacyRequisitionListRowDto[]; total: number }> {
  const res = await pagePharmacyRequisitionsApi({
    purposeLocationId: resolveWorkbenchListPurposeLocationId(params.purposeLocationId),
    typeEnum: params.typeEnum ?? PHA_REQUISITION_TYPE_ENUM,
    keyword: params.keyword?.trim() || undefined,
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<PharmacyRequisitionListRowDto>(
    res.data,
  );
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params.applyTimeBegin,
    params.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered as Record<string, unknown>[],
    params.keyword,
  ) as PharmacyRequisitionListRowDto[];
  const filtered = (params.applyTimeBegin && params.applyTimeEnd) || params.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}
