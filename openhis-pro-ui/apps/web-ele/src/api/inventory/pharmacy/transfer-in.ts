/**
 * 药房调拨入库（药房入库）— 对接 Whale {@code POST /api/app/inventoryPharmacyTransferIn/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_PHARMACIES_LABEL,
  mapInitOptionsToCabinetRowsWithAll,
  resolveWorkbenchListPurposeLocationId,
} from '#/utils/inventory/inventory-workbench-list';

const BASE = '/inventoryPharmacyTransferIn';

export const PHA_TRANSFER_IN_TYPE_ENUM = 'PHARMACY_TRANSFER_IN';
export const WHS_TRANSFER_IN_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface TransferInOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadTransferInFormInitRequest {}

export interface LoadTransferInFormInitResponse {
  pharmacyLocationOptions?: TransferInOptionDto[];
  warehouseLocationOptions?: TransferInOptionDto[];
  inboundItemTableTypeOptions?: TransferInOptionDto[];
  statusOptions?: TransferInOptionDto[];
  pharmacistOptions?: TransferInOptionDto[];
  warehouseKeeperOptions?: TransferInOptionDto[];
}

export interface TransferInListRowDto {
  busNo?: string;
  originalBusNo?: string;
  statusEnum?: string | number;
  statusEnumText?: string;
  sourceLocationId?: number | string;
  sourceLocationName?: string;
  purposeLocationId?: number | string;
  applyTime?: string;
  applicantId?: number | string;
  /** 制单人姓名（后端 enrich） */
  creatorName?: string;
  approverName?: string;
  /** 同号各行 totalPrice 之和 */
  totalAmount?: number | string;
}

export interface PageTransferInsRequest {
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

export interface PageTransferInsResponse {
  total?: number;
  totalCount?: number;
  content?: TransferInListRowDto[];
}

export interface GetTransferInDetailRequest {
  busNo: string;
}

export interface TransferInDetailLineDto {
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
  /** 源库可用量（拆零） */
  availableQuantity?: number;
  remake?: string;
  occurrenceTime?: string;
  productCatalog?: {
    merchandiseName?: string;
    productBusNo?: string;
    totalVolume?: string;
    unitBands?: unknown;
  };
}

export interface GetTransferInDetailResponse {
  busNo?: string;
  originalBusNo?: string;
  statusEnum?: string | number;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum?: string;
  practitionerId?: number | string;
  practitionerName?: string;
  lines: TransferInDetailLineDto[];
}

export interface TransferInLineDto {
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

export interface CreateTransferInRequest {
  purposeLocationId: number | string;
  sourceLocationId: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum: string;
  originalBusNo?: string;
  practitionerId: number | string;
  lines: TransferInLineDto[];
}

export interface UpdateTransferInRequest {
  busNo: string;
  purposeLocationId: number | string;
  sourceLocationId: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum: string;
  originalBusNo?: string;
  practitionerId: number | string;
  lines: TransferInLineDto[];
}

export interface CreateTransferInResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface UpdateTransferInResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitTransferInApprovalRequest {
  busNo: string;
}

export interface SubmitTransferInApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface WithdrawTransferInApprovalRequest {
  busNo: string;
}

export interface WithdrawTransferInApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface AgreeTransferInRequest {
  busNo: string;
  contractBusNo?: string;
}

export interface RejectTransferInRequest {
  busNo: string;
  rejectReason?: string;
}

export interface RemoveTransferInRequest {
  busNo: string;
}

export interface PageProductsForTransferInRequest {
  productTableName?: string;
  keyword?: string;
  sourceLocationId?: number | string;
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
  inventoryItemId?: number | string;
}

export interface PageProductsForTransferInResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export async function loadTransferInFormInitApi(body?: LoadTransferInFormInitRequest) {
  return requestClient.post<ApiResponse<LoadTransferInFormInitResponse>>(
    `${BASE}/loadTransferInFormInit`,
    body ?? {},
  );
}

export async function pageTransferInsApi(body: PageTransferInsRequest) {
  return requestClient.post<ApiResponse<PageTransferInsResponse>>(`${BASE}/pageTransferIns`, body);
}

export async function getTransferInDetailApi(body: GetTransferInDetailRequest) {
  return requestClient.post<ApiResponse<GetTransferInDetailResponse>>(
    `${BASE}/getTransferInDetail`,
    body,
  );
}

export async function createTransferInApi(body: CreateTransferInRequest) {
  return requestClient.post<ApiResponse<CreateTransferInResponse>>(
    `${BASE}/createTransferIn`,
    body,
  );
}

export async function updateTransferInApi(body: UpdateTransferInRequest) {
  return requestClient.post<ApiResponse<UpdateTransferInResponse>>(
    `${BASE}/updateTransferIn`,
    body,
  );
}

export async function submitTransferInApprovalApi(body: SubmitTransferInApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitTransferInApprovalResponse>>(
    `${BASE}/submitTransferInApproval`,
    body,
  );
}

export async function withdrawTransferInApprovalApi(body: WithdrawTransferInApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawTransferInApprovalResponse>>(
    `${BASE}/withdrawTransferInApproval`,
    body,
  );
}

export async function agreeTransferInApi(body: AgreeTransferInRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeTransferIn`, body);
}

export async function rejectTransferInApi(body: RejectTransferInRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/rejectTransferIn`, body);
}

export async function removeTransferInApi(body: RemoveTransferInRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/removeTransferIn`, body);
}

export async function pageProductsForTransferInApi(body: PageProductsForTransferInRequest) {
  return requestClient.post<ApiResponse<PageProductsForTransferInResponse>>(
    `${BASE}/pageProductsForTransferIn`,
    body,
  );
}

/** 工作站左侧列表：须传 purposeLocationId；日期在前端筛。 */
/** init 药房下拉 → 侧栏 cabinet 行（含「全部药房」）。 */
export function mapPharmacyTransferInInitToCabinetRows(
  list?: TransferInOptionDto[],
): Record<string, unknown>[] {
  return mapInitOptionsToCabinetRowsWithAll(list ?? [], INV_ALL_PHARMACIES_LABEL);
}

/** 列表行 → 侧栏卡片队列字段（对齐出库/进货列表 enrich）。 */
function mapTransferInListRowToQueueCard(r: TransferInListRowDto): Record<string, unknown> {
  return {
    ...r,
    supplyBusNo: r.busNo,
    statusEnum_enumText: r.statusEnumText ?? '',
    applicantId_dictText: r.creatorName ?? '',
    approverId_dictText: r.approverName ?? '',
    totalPrice: r.totalAmount,
    totalAmount: r.totalAmount,
  };
}

/** 工作站左侧列表：purposeLocationId=0 表示全部；日期/关键字在前端筛。 */
export async function pageTransferInWorkbenchListApi(params: {
  purposeLocationId?: number | string;
  typeEnum: string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: Record<string, unknown>[]; total: number }> {
  const pid = resolveWorkbenchListPurposeLocationId(params.purposeLocationId);
  const res = await pageTransferInsApi({
    purposeLocationId: pid,
    typeEnum: params.typeEnum,
    keyword: params.keyword?.trim() || undefined,
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<TransferInListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params.applyTimeBegin,
    params.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered as Record<string, unknown>[],
    params.keyword,
  ).map((r) => mapTransferInListRowToQueueCard(r as TransferInListRowDto));
  const filtered = (params.applyTimeBegin && params.applyTimeEnd) || params.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}
