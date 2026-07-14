/**
 * 药库调拨出库 — 对接 Whale {@code POST /api/app/inventoryWarehouseTransferOut/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_WAREHOUSES_LABEL,
  mapInitOptionsToCabinetRowsWithAll,
  resolveWorkbenchListSourceLocationId,
} from '#/utils/inventory/inventory-workbench-list';

const BASE = '/inventoryWarehouseTransferOut';

export const WHS_TRANSFER_OUT_TYPE_ENUM = 'CABINET_TRANSFER_OUT';
export const WHS_TRANSFER_OUT_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface TransferOutOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadTransferOutFormInitRequest {
  organizationId?: number;
}

export interface LoadTransferOutFormInitResponse {
  warehouseLocationOptions?: TransferOutOptionDto[];
  pharmacyLocationOptions?: TransferOutOptionDto[];
  inboundItemTableTypeOptions?: TransferOutOptionDto[];
  statusOptions?: TransferOutOptionDto[];
  pharmacistOptions?: TransferOutOptionDto[];
  warehouseKeeperOptions?: TransferOutOptionDto[];
}

export interface TransferOutListRowDto {
  busNo?: string;
  originalBusNo?: string;
  statusEnum?: string | number;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  applyTime?: string;
  approvalTime?: string;
}

export interface PageTransferOutsRequest {
  sourceLocationId: number | string;
  purposeLocationId?: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
  typeEnum?: string;
}

export interface PageTransferOutsResponse {
  total?: number;
  totalCount?: number;
  records?: TransferOutListRowDto[];
  content?: TransferOutListRowDto[];
}

export interface GetTransferOutDetailRequest {
  busNo: string;
}

export interface TransferOutDetailLineDto {
  id?: number | string;
  itemTable?: string;
  itemId?: number | string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  itemQuantity?: number;
  unitCode?: string;
  unitCodeDictText?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  remake?: string;
  occurrenceTime?: string;
  productCatalog?: {
    merchandiseName?: string;
    productBusNo?: string;
    totalVolume?: string;
    unitBands?: unknown;
  };
}

export interface GetTransferOutDetailResponse {
  busNo?: string;
  originalBusNo?: string;
  statusEnum?: string | number;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum?: string;
  practitionerId?: number | string;
  lines: TransferOutDetailLineDto[];
}

export interface TransferOutLineDto {
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

export interface CreateTransferOutRequest {
  sourceLocationId: number | string;
  purposeLocationId: number | string;
  sourceTypeEnum: string;
  purposeTypeEnum: string;
  categoryEnum: string;
  originalBusNo?: string;
  practitionerId: number | string;
  lines: TransferOutLineDto[];
}

export interface UpdateTransferOutRequest {
  busNo: string;
  sourceLocationId: number | string;
  purposeLocationId: number | string;
  sourceTypeEnum: string;
  purposeTypeEnum: string;
  categoryEnum: string;
  originalBusNo?: string;
  practitionerId: number | string;
  lines: TransferOutLineDto[];
}

export interface CreateTransferOutResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface UpdateTransferOutResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitTransferOutApprovalRequest {
  busNo: string;
}

export interface SubmitTransferOutApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface WithdrawTransferOutApprovalRequest {
  busNo: string;
}

export interface WithdrawTransferOutApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface AgreeTransferOutRequest {
  busNo: string;
  contractBusNo?: string;
}

export interface RejectTransferOutRequest {
  busNo: string;
  rejectReason?: string;
}

export interface RemoveTransferOutRequest {
  busNo: string;
}

export interface PageProductsForTransferOutRequest {
  productTableName?: string;
  keyword?: string;
  sourceLocationId?: number | string;
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
  inventoryItemId?: number | string;
}

export interface PageProductsForTransferOutResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export interface PageLinkableRequisitionsRequest {
  sourceLocationId: number | string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageLinkableRequisitionsResponse {
  total?: number;
  records: TransferOutListRowDto[];
}

export async function loadTransferOutFormInitApi(body?: LoadTransferOutFormInitRequest) {
  return requestClient.post<ApiResponse<LoadTransferOutFormInitResponse>>(
    `${BASE}/loadTransferOutFormInit`,
    body ?? {},
  );
}

export async function pageTransferOutsApi(body: PageTransferOutsRequest) {
  return requestClient.post<ApiResponse<PageTransferOutsResponse>>(
    `${BASE}/pageTransferOuts`,
    body,
  );
}

export async function getTransferOutDetailApi(body: GetTransferOutDetailRequest) {
  return requestClient.post<ApiResponse<GetTransferOutDetailResponse>>(
    `${BASE}/getTransferOutDetail`,
    body,
  );
}

export async function createTransferOutApi(body: CreateTransferOutRequest) {
  return requestClient.post<ApiResponse<CreateTransferOutResponse>>(`${BASE}/createTransfer`, body);
}

export async function updateTransferOutApi(body: UpdateTransferOutRequest) {
  return requestClient.post<ApiResponse<UpdateTransferOutResponse>>(
    `${BASE}/updateTransferOut`,
    body,
  );
}

export async function submitTransferOutApprovalApi(body: SubmitTransferOutApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitTransferOutApprovalResponse>>(
    `${BASE}/submitTransferOutApproval`,
    body,
  );
}

export async function withdrawTransferOutApprovalApi(body: WithdrawTransferOutApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawTransferOutApprovalResponse>>(
    `${BASE}/withdrawTransferOutApproval`,
    body,
  );
}

export async function agreeTransferOutApi(body: AgreeTransferOutRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeTransferOut`, body);
}

export async function rejectTransferOutApi(body: RejectTransferOutRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/rejectTransferOut`, body);
}

export async function removeTransferOutApi(body: RemoveTransferOutRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/removeTransferOut`, body);
}

export async function pageProductsForTransferOutApi(body: PageProductsForTransferOutRequest) {
  return requestClient.post<ApiResponse<PageProductsForTransferOutResponse>>(
    `${BASE}/pageProductsForTransferOut`,
    body,
  );
}

export async function pageLinkableRequisitionsApi(body: PageLinkableRequisitionsRequest) {
  return requestClient.post<ApiResponse<PageLinkableRequisitionsResponse>>(
    `${BASE}/pageLinkableRequisitions`,
    body,
  );
}

/** init 药库下拉 → 侧栏 cabinet 行（含「全部库房」）。 */
export function mapTransferOutInitToCabinetRows(
  list?: TransferOutOptionDto[],
): Record<string, unknown>[] {
  return mapInitOptionsToCabinetRowsWithAll(list ?? [], INV_ALL_WAREHOUSES_LABEL);
}

/** 工作站左侧列表：sourceLocationId=0 表示全部；日期/关键字在前端筛。 */
export async function pageTransferOutWorkbenchListApi(params: {
  sourceLocationId?: number | string;
  typeEnum: string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: TransferOutListRowDto[]; total: number }> {
  const sid = resolveWorkbenchListSourceLocationId(params.sourceLocationId);
  const res = await pageTransferOutsApi({
    sourceLocationId: sid,
    typeEnum: params.typeEnum,
    keyword: params.keyword?.trim() || undefined,
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<TransferOutListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params.applyTimeBegin,
    params.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered as Record<string, unknown>[],
    params.keyword,
  ) as TransferOutListRowDto[];
  const filtered = (params.applyTimeBegin && params.applyTimeEnd) || params.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}
