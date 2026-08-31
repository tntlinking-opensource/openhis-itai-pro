/**
 * 码上放心采购入库上游拉单 — {@code POST /api/app/msfxInboundUpstream/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import type {
  MsfxUpstreamBillDetailDto,
  MsfxUpstreamBillListDto,
  MsfxUpstreamImportDto,
} from './types';

import { requestClient } from '#/api/request';

const BASE = '/msfxInboundUpstream';

export interface ListMsfxUpstreamBillsRequest {
  billBeginDate?: string;
  billEndDate?: string;
  fromUserId?: string;
  billCode?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface MsfxUpstreamDetailRequest {
  billCode: string;
  fromRefUserId?: string;
  assRefEntId?: string;
}

export interface MsfxResolveUpstreamRequest {
  code: string;
}

export interface MsfxApplyUpstreamRequest {
  importId?: number | string;
  hisBusNo: string;
  /** 上游出库单号；importId 找不到时后端按单号兜底 */
  upstreamBillCode?: string;
}

export interface MsfxHisBusNoRequest {
  hisBusNo: string;
}

export function listMsfxUpstreamBillsApi(data: ListMsfxUpstreamBillsRequest) {
  return requestClient.post<ApiResponse<MsfxUpstreamBillListDto>>(
    `${BASE}/listUpstreamBills`,
    data,
  );
}

export function getMsfxUpstreamBillDetailApi(data: MsfxUpstreamDetailRequest) {
  // 明细可能逐码查 relation，需较长超时
  return requestClient.post<ApiResponse<MsfxUpstreamBillDetailDto>>(
    `${BASE}/getUpstreamBillDetail`,
    data,
    { timeout: 120_000 },
  );
}

export function resolveMsfxUpstreamByScanApi(data: MsfxResolveUpstreamRequest) {
  return requestClient.post<ApiResponse<MsfxUpstreamBillListDto>>(
    `${BASE}/resolveUpstreamByScan`,
    data,
  );
}

export function applyMsfxUpstreamToInboundApi(data: MsfxApplyUpstreamRequest) {
  return requestClient.post<ApiResponse<MsfxUpstreamImportDto>>(
    `${BASE}/applyUpstreamToInbound`,
    data,
  );
}

export function getMsfxUpstreamImportByHisBusNoApi(data: MsfxHisBusNoRequest) {
  return requestClient.post<ApiResponse<MsfxUpstreamImportDto | null>>(
    `${BASE}/getUpstreamImportByHisBusNo`,
    data,
  );
}
