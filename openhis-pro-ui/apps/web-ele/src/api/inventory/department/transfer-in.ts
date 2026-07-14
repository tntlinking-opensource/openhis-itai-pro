/**
 * 科室调入单（只读壳单台账）— 对接 Whale {@code POST /api/app/inventoryDepartmentTransferIn/<method>}
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

const BASE = '/inventoryDepartmentTransferIn';

export const DEPT_TRANSFER_IN_TYPE_ENUM = 'DEPT_TRANSFER_IN';

export interface TransferInOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadTransferInFormInitRequest {
  organizationId?: number;
}

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
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  applyTime?: string;
  approvalTime?: string;
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
  records?: TransferInListRowDto[];
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
  lines: TransferInDetailLineDto[];
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

/** init 目的科室药房下拉 → 侧栏 cabinet 行（含「全部科室药房」）。 */
export function mapDepartmentTransferInInitToCabinetRows(
  list?: TransferInOptionDto[],
): Record<string, unknown>[] {
  return mapInitOptionsToCabinetRowsWithAll(list ?? [], INV_ALL_DEPT_PHARMACIES_LABEL);
}

/** 工作站左侧列表（只读）：purposeLocationId=0 表示全部；日期/关键字在前端筛。 */
export async function pageDepartmentTransferInWorkbenchListApi(params: {
  purposeLocationId?: number | string;
  typeEnum?: string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: TransferInListRowDto[]; total: number }> {
  const pid = resolveWorkbenchListPurposeLocationId(params.purposeLocationId);
  const res = await pageTransferInsApi({
    purposeLocationId: pid,
    typeEnum: params.typeEnum ?? DEPT_TRANSFER_IN_TYPE_ENUM,
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
  ) as TransferInListRowDto[];
  const filtered = (params.applyTimeBegin && params.applyTimeEnd) || params.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}
