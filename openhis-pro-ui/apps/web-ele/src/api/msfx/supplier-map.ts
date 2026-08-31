/**
 * 码上放心 · HIS 供应商对照 API — {@code POST /api/app/msfxSupplierMap/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/msfxSupplierMap';

export interface MsfxSupplierMapItem {
  id?: number | string;
  supplierId?: number | string;
  supplierName?: string;
  msfxEntId?: string;
  msfxRefEntId?: string;
  msfxEntName?: string;
  remark?: string;
  version?: number;
}

export interface MsfxSupplierMapPageParams {
  pageNo?: number;
  pageSize?: number;
}

export interface MsfxSupplierMapPageResult {
  content?: MsfxSupplierMapItem[];
  totalCount?: number;
  totalElements?: number;
}

export interface MsfxSupplierMapInitResult {
  supplierOptions?: Array<{ label: string; value: string }>;
}

export interface MsfxSupplierMapSaveParams {
  id?: number | string;
  supplierId: number | string;
  msfxEntId: string;
  msfxRefEntId?: string;
  msfxEntName?: string;
  remark?: string;
}

export type MsfxMapStatus = 'ALL' | 'MAPPED' | 'UNMAPPED' | '';

export interface MsfxHisSupplierWorkbenchRow {
  supplierId?: number | string;
  supplierName?: string;
  busNo?: string;
  typeEnum?: string;
  statusEnum?: string;
  mapped?: boolean;
  mapId?: number | string;
  msfxEntId?: string;
  msfxRefEntId?: string;
  msfxEntName?: string;
}

export interface MsfxPlatformPartnerWorkbenchRow {
  entId?: string;
  refEntId?: string;
  entName?: string;
  orgCode?: string;
  auditFlag?: string;
  mapped?: boolean;
  mapId?: number | string;
  supplierId?: number | string;
  supplierName?: string;
}

export interface MsfxPlatformPartnerPageResult {
  responseSuccess?: boolean;
  msgCode?: string;
  msgInfo?: string;
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
  content?: MsfxPlatformPartnerWorkbenchRow[];
}

export function initMsfxSupplierMapApi() {
  return requestClient.post<ApiResponse<MsfxSupplierMapInitResult>>(`${BASE}/init`, {});
}

export function pageMsfxSupplierMapApi(data: MsfxSupplierMapPageParams) {
  return requestClient.post<ApiResponse<MsfxSupplierMapPageResult>>(`${BASE}/page`, data);
}

export function getMsfxSupplierMapApi(data: { id: number | string }) {
  return requestClient.post<ApiResponse<MsfxSupplierMapItem>>(`${BASE}/get`, data);
}

export function getMsfxSupplierMapBySupplierIdApi(data: { supplierId: number | string }) {
  return requestClient.post<ApiResponse<MsfxSupplierMapItem | null>>(
    `${BASE}/getBySupplierId`,
    data,
  );
}

export function saveMsfxSupplierMapApi(data: MsfxSupplierMapSaveParams) {
  return requestClient.post<ApiResponse<MsfxSupplierMapItem>>(`${BASE}/save`, data);
}

export function deleteMsfxSupplierMapApi(data: { id: number | string }) {
  return requestClient.post<ApiResponse<void>>(`${BASE}/delete`, data);
}

export function pageMsfxHisSuppliersApi(data: {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  mapStatus?: MsfxMapStatus;
}) {
  return requestClient.post<
    ApiResponse<MsfxSupplierMapPageResult & { content?: MsfxHisSupplierWorkbenchRow[] }>
  >(`${BASE}/pageHisSuppliers`, data);
}

export function pageMsfxPlatformPartnersApi(data: {
  pageNo?: number;
  pageSize?: number;
  entName?: string;
  mapStatus?: MsfxMapStatus;
}) {
  return requestClient.post<ApiResponse<MsfxPlatformPartnerPageResult>>(
    `${BASE}/pagePlatformPartners`,
    data,
  );
}

export function pullAllMsfxPlatformPartnersApi(data: {
  entName?: string;
  mapStatus?: MsfxMapStatus;
}) {
  return requestClient.post<ApiResponse<MsfxPlatformPartnerPageResult>>(
    `${BASE}/pullAllPlatformPartners`,
    data,
    { timeout: 120_000 },
  );
}
