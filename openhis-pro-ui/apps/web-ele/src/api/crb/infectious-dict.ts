/**
 * 传染病编码对照 — POST /api/app/infectiousDict/{method}
 */
import type { ApiResponse, PageApiResponse, PageParams } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const BASE = '/infectiousDict';

export type CrbResultShape = 'QUAL' | 'QUANT';
export type CrbAnnex2Scope = 'PATHOGEN' | 'CLIN_CHEM' | 'DRUG_SENS';

export interface CrbCodeMapPageParams extends PageParams {
  keyword?: string;
  unmappedOnly?: boolean;
}

export interface DeptCodeMapRowDto {
  id?: string;
  locationBusNo?: string;
  locationName?: string;
  targetDeptCode?: string;
  targetDeptName?: string;
  mapped?: boolean;
}

export interface LabItemMapRowDto {
  id?: string;
  itemBusNo?: string;
  itemName?: string;
  nationalItemCode?: string;
  resultShape?: CrbResultShape | string;
  annex2Scope?: CrbAnnex2Scope | string;
  pushEnabled?: boolean;
  mapped?: boolean;
}

export interface DrugCodeMapRowDto {
  id?: string;
  medBusNo?: string;
  medName?: string;
  nationalDrugCode?: string;
  nationalDrugName?: string;
  mapped?: boolean;
}

export interface SaveDeptCodeMapRequest {
  id?: string;
  locationBusNo: string;
  locationName?: string;
  targetDeptCode?: string;
  targetDeptName?: string;
}

export interface SaveLabItemMapRequest {
  id?: string;
  itemBusNo: string;
  itemName?: string;
  nationalItemCode?: string;
  resultShape?: CrbResultShape | string;
  annex2Scope?: CrbAnnex2Scope | string;
  pushEnabled?: boolean;
}

export interface SaveDrugCodeMapRequest {
  id?: string;
  medBusNo: string;
  medName?: string;
  nationalDrugCode?: string;
  nationalDrugName?: string;
}

export interface SaveCodeMapResponse {
  id?: string;
  mapped?: boolean;
  pushEnabled?: boolean;
  /** 请求启用推送但被附件二规则强制关闭。 */
  pushForcedOff?: boolean;
  /** 给信息科的提示。 */
  hint?: string;
}

export interface ImportCodeMapResponse {
  successCount?: number;
  skipCount?: number;
  failCount?: number;
  messages?: string[];
}

interface TenantScopedBody<T> {
  tenantId: string | number;
  request: T;
}

interface TenantIdBody {
  tenantId: string | number;
}

/** 组装带 tenantId 与 request 的请求体。 */
function withTenantRequest<T>(tenantId: string | number, request: T): TenantScopedBody<T> {
  return { tenantId: preserveApiIdForRequest(tenantId) ?? tenantId, request };
}

/** 分页查询科室对照。 */
export async function pageDeptCodeMapApi(tenantId: string | number, request: CrbCodeMapPageParams) {
  const res = await requestClient.post<ApiResponse<PageApiResponse<DeptCodeMapRowDto>>>(
    `${BASE}/pageDeptCodeMaps`,
    withTenantRequest(tenantId, request),
  );
  return res.data;
}

/** 保存科室对照。 */
export async function saveDeptCodeMapApi(
  tenantId: string | number,
  request: SaveDeptCodeMapRequest,
) {
  const payload: SaveDeptCodeMapRequest = {
    ...request,
    id: preserveApiIdForRequest(request.id),
  };
  const res = await requestClient.post<ApiResponse<SaveCodeMapResponse>>(
    `${BASE}/saveDeptCodeMap`,
    withTenantRequest(tenantId, payload),
  );
  return res.data;
}

/** 批量导入科室对照。 */
export async function importDeptCodeMapApi(
  tenantId: string | number,
  rows: SaveDeptCodeMapRequest[],
) {
  const res = await requestClient.post<ApiResponse<ImportCodeMapResponse>>(
    `${BASE}/importDeptCodeMaps`,
    withTenantRequest(tenantId, { rows }),
  );
  return res.data;
}

/** 软删科室对照。 */
export async function softDeleteDeptCodeMapApi(tenantId: string | number, id: string) {
  const res = await requestClient.post<ApiResponse<void>>(`${BASE}/softDeleteDeptCodeMap`, {
    tenantId: preserveApiIdForRequest(tenantId) ?? tenantId,
    id: preserveApiIdForRequest(id),
  });
  return res.data;
}

/** 分页查询检验对照。 */
export async function pageLabItemMapApi(tenantId: string | number, request: CrbCodeMapPageParams) {
  const res = await requestClient.post<ApiResponse<PageApiResponse<LabItemMapRowDto>>>(
    `${BASE}/pageLabItemMaps`,
    withTenantRequest(tenantId, request),
  );
  return res.data;
}

/** 保存检验对照。 */
export async function saveLabItemMapApi(tenantId: string | number, request: SaveLabItemMapRequest) {
  const payload: SaveLabItemMapRequest = {
    ...request,
    id: preserveApiIdForRequest(request.id),
  };
  const res = await requestClient.post<ApiResponse<SaveCodeMapResponse>>(
    `${BASE}/saveLabItemMap`,
    withTenantRequest(tenantId, payload),
  );
  return res.data;
}

/** 批量导入检验对照。 */
export async function importLabItemMapApi(
  tenantId: string | number,
  rows: SaveLabItemMapRequest[],
) {
  const res = await requestClient.post<ApiResponse<ImportCodeMapResponse>>(
    `${BASE}/importLabItemMaps`,
    withTenantRequest(tenantId, { rows }),
  );
  return res.data;
}

/** 软删检验对照。 */
export async function softDeleteLabItemMapApi(tenantId: string | number, id: string) {
  const res = await requestClient.post<ApiResponse<void>>(`${BASE}/softDeleteLabItemMap`, {
    tenantId: preserveApiIdForRequest(tenantId) ?? tenantId,
    id: preserveApiIdForRequest(id),
  });
  return res.data;
}

/** 分页查询药品对照。 */
export async function pageDrugCodeMapApi(tenantId: string | number, request: CrbCodeMapPageParams) {
  const res = await requestClient.post<ApiResponse<PageApiResponse<DrugCodeMapRowDto>>>(
    `${BASE}/pageDrugCodeMaps`,
    withTenantRequest(tenantId, request),
  );
  return res.data;
}

/** 保存药品对照。 */
export async function saveDrugCodeMapApi(
  tenantId: string | number,
  request: SaveDrugCodeMapRequest,
) {
  const payload: SaveDrugCodeMapRequest = {
    ...request,
    id: preserveApiIdForRequest(request.id),
  };
  const res = await requestClient.post<ApiResponse<SaveCodeMapResponse>>(
    `${BASE}/saveDrugCodeMap`,
    withTenantRequest(tenantId, payload),
  );
  return res.data;
}

/** 批量导入药品对照。 */
export async function importDrugCodeMapApi(
  tenantId: string | number,
  rows: SaveDrugCodeMapRequest[],
) {
  const res = await requestClient.post<ApiResponse<ImportCodeMapResponse>>(
    `${BASE}/importDrugCodeMaps`,
    withTenantRequest(tenantId, { rows }),
  );
  return res.data;
}

/** 软删药品对照。 */
export async function softDeleteDrugCodeMapApi(tenantId: string | number, id: string) {
  const res = await requestClient.post<ApiResponse<void>>(`${BASE}/softDeleteDrugCodeMap`, {
    tenantId: preserveApiIdForRequest(tenantId) ?? tenantId,
    id: preserveApiIdForRequest(id),
  });
  return res.data;
}

export type { TenantIdBody, TenantScopedBody };
