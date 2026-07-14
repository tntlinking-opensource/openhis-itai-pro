/**
 * 科室药房请领单 — 对接 Whale {@code POST /api/app/inventoryDepartmentRequisition/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_DEPT_PHARMACIES_LABEL,
  mapInitOptionsToCabinetRowsWithAll,
  resolveWorkbenchListPurposeLocationId,
} from '#/utils/inventory/inventory-workbench-list';

const BASE = '/inventoryDepartmentRequisition';

/** {@link com.openhis.health.domain.share.enums.SupplyType#DEPT_REQUISITION} */
export const DEPT_REQUISITION_TYPE_ENUM = 'DEPT_REQUISITION';
/** {@link com.openhis.health.domain.share.enums.SupplyCategory#STOCK_SUPPLY} */
export const DEPT_REQUISITION_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface RequisitionOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadDepartmentRequisitionFormInitRequest {
  organizationId?: number;
}

export interface LoadDepartmentRequisitionFormInitResponse {
  pharmacyLocationOptions?: RequisitionOptionDto[];
  warehouseLocationOptions?: RequisitionOptionDto[];
  statusOptions?: RequisitionOptionDto[];
}

export interface DepartmentRequisitionListRowDto {
  busNo?: string;
  originalBusNo?: string;
  typeEnum?: string;
  statusEnum?: string | number;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  applyTime?: string;
  approvalTime?: string;
}

export interface PageDepartmentRequisitionsRequest {
  purposeLocationId: number | string;
  sourceLocationId?: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
  typeEnum?: string;
}

export interface PageDepartmentRequisitionsResponse {
  total?: number;
  totalCount?: number;
  records?: DepartmentRequisitionListRowDto[];
  content?: DepartmentRequisitionListRowDto[];
}

export interface GetDepartmentRequisitionDetailRequest {
  busNo: string;
}

export interface DepartmentRequisitionDetailLineDto {
  id?: number | string;
  itemTable?: string;
  itemId?: number | string;
  inventoryItemId?: number | string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  itemQuantity?: number;
  unitCode?: string;
  unitCodeDictText?: string;
  productCatalog?: { unitBands?: unknown };
  lotNumber?: string;
  startTime?: string;
  endTime?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  remake?: string;
  occurrenceTime?: string;
}

export interface GetDepartmentRequisitionDetailResponse {
  busNo?: string;
  typeEnum?: string;
  statusEnum?: string | number;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum?: string;
  originalBusNo?: string;
  lines: DepartmentRequisitionDetailLineDto[];
}

export interface DepartmentRequisitionLineDto {
  itemTable?: string;
  itemId?: number | string;
  inventoryItemId?: number | string;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  remake?: string;
  occurrenceTime?: string;
}

export interface CreateDepartmentRequisitionRequest {
  purposeLocationId: number | string;
  sourceLocationId: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum: string;
  busNo?: string;
  practitionerId: number | string;
  lines: DepartmentRequisitionLineDto[];
}

export interface UpdateDepartmentRequisitionRequest {
  busNo: string;
  purposeLocationId: number | string;
  sourceLocationId: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum: string;
  practitionerId: number | string;
  lines: DepartmentRequisitionLineDto[];
}

export interface CreateDepartmentRequisitionResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface UpdateDepartmentRequisitionResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitDepartmentRequisitionApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface SubmitDepartmentRequisitionApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface WithdrawDepartmentRequisitionApprovalRequest {
  busNo: string;
}

export interface WithdrawDepartmentRequisitionApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface AgreeDepartmentRequisitionRequest {
  busNo: string;
  operatorId?: number | string;
  approvalClock?: string;
}

export interface AgreeDepartmentRequisitionResponse {
  busNo?: string;
}

export interface RejectDepartmentRequisitionRequest {
  busNo: string;
  rejectReason?: string;
  operatorId?: number | string;
  approvalClock?: string;
}

export interface RemoveDepartmentRequisitionRequest {
  busNo: string;
}

export interface PageProductsForDepartmentRequisitionRequest {
  productTableName?: string;
  keyword?: string;
  categoryCode?: string;
  activeFlag?: boolean;
  supplierId?: number;
  manufacturer?: string;
  includeInventory?: boolean;
  sourceLocationId?: number | string;
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
  unitCode?: string;
  minUnitCode?: string;
  categoryCode?: string;
  price?: number;
  purchasePrice?: number;
  manufacturer?: string;
  pyStr?: string;
  wbStr?: string;
  lotNumber?: string;
  inventoryItemId?: number | string;
}

export interface PageProductsForDepartmentRequisitionResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export async function loadDepartmentRequisitionFormInitApi(
  body?: LoadDepartmentRequisitionFormInitRequest,
) {
  return requestClient.post<ApiResponse<LoadDepartmentRequisitionFormInitResponse>>(
    `${BASE}/loadDepartmentRequisitionFormInit`,
    body ?? {},
  );
}

export async function pageDepartmentRequisitionsApi(body: PageDepartmentRequisitionsRequest) {
  return requestClient.post<ApiResponse<PageDepartmentRequisitionsResponse>>(
    `${BASE}/pageDepartmentRequisitions`,
    body,
  );
}

export async function getDepartmentRequisitionDetailApi(
  body: GetDepartmentRequisitionDetailRequest,
) {
  return requestClient.post<ApiResponse<GetDepartmentRequisitionDetailResponse>>(
    `${BASE}/getDepartmentRequisitionDetail`,
    body,
  );
}

export async function createDepartmentRequisitionApi(body: CreateDepartmentRequisitionRequest) {
  return requestClient.post<ApiResponse<CreateDepartmentRequisitionResponse>>(
    `${BASE}/createDepartmentRequisition`,
    body,
  );
}

export async function updateDepartmentRequisitionApi(body: UpdateDepartmentRequisitionRequest) {
  return requestClient.post<ApiResponse<UpdateDepartmentRequisitionResponse>>(
    `${BASE}/updateDepartmentRequisition`,
    body,
  );
}

export async function submitDepartmentRequisitionApprovalApi(
  body: SubmitDepartmentRequisitionApprovalRequest,
) {
  return requestClient.post<ApiResponse<SubmitDepartmentRequisitionApprovalResponse>>(
    `${BASE}/submitDepartmentRequisitionApproval`,
    body,
  );
}

export async function withdrawDepartmentRequisitionApprovalApi(
  body: WithdrawDepartmentRequisitionApprovalRequest,
) {
  return requestClient.post<ApiResponse<WithdrawDepartmentRequisitionApprovalResponse>>(
    `${BASE}/withdrawDepartmentRequisitionApproval`,
    body,
  );
}

export async function agreeDepartmentRequisitionApi(body: AgreeDepartmentRequisitionRequest) {
  return requestClient.post<ApiResponse<AgreeDepartmentRequisitionResponse>>(
    `${BASE}/agreeDepartmentRequisition`,
    body,
  );
}

export async function rejectDepartmentRequisitionApi(body: RejectDepartmentRequisitionRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectDepartmentRequisition`,
    body,
  );
}

export async function removeDepartmentRequisitionApi(body: RemoveDepartmentRequisitionRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removeDepartmentRequisition`,
    body,
  );
}

export async function pageProductsForDepartmentRequisitionApi(
  body: PageProductsForDepartmentRequisitionRequest,
) {
  return requestClient.post<ApiResponse<PageProductsForDepartmentRequisitionResponse>>(
    `${BASE}/pageProductsForDepartmentRequisition`,
    body,
  );
}

/** init 科室药房下拉 → 侧栏 cabinet 行（含「全部科室药房」）。 */
export function mapDepartmentRequisitionInitToCabinetRows(
  list?: RequisitionOptionDto[],
): Record<string, unknown>[] {
  return mapInitOptionsToCabinetRowsWithAll(list ?? [], INV_ALL_DEPT_PHARMACIES_LABEL);
}

/** 工作站左侧列表：purposeLocationId=0 表示全部；日期/关键字在前端筛。 */
export async function pageDepartmentRequisitionWorkbenchListApi(params: {
  purposeLocationId?: number | string;
  typeEnum?: string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: DepartmentRequisitionListRowDto[]; total: number }> {
  const pid = resolveWorkbenchListPurposeLocationId(params.purposeLocationId);
  const res = await pageDepartmentRequisitionsApi({
    purposeLocationId: pid,
    typeEnum: params.typeEnum ?? DEPT_REQUISITION_TYPE_ENUM,
    keyword: params.keyword?.trim() || undefined,
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<DepartmentRequisitionListRowDto>(
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
  ) as DepartmentRequisitionListRowDto[];
  const filtered = (params.applyTimeBegin && params.applyTimeEnd) || params.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}
