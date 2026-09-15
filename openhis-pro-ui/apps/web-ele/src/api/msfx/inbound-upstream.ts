/** Opensource stub — APIs disabled. */
import type { ApiResponse } from '#/types/api-response';

import type {
  MsfxUpstreamBillDetailDto,
  MsfxUpstreamBillListDto,
  MsfxUpstreamImportDto,
} from './types';

function disabled<T>(): Promise<ApiResponse<T>> {
  return Promise.reject(new Error('Optional module disabled in opensource build'));
}

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
  upstreamBillCode?: string;
}

export interface MsfxHisBusNoRequest {
  hisBusNo: string;
}

export function listMsfxUpstreamBillsApi(_data: ListMsfxUpstreamBillsRequest) {
  return disabled<MsfxUpstreamBillListDto>();
}

export function getMsfxUpstreamBillDetailApi(_data: MsfxUpstreamDetailRequest) {
  return disabled<MsfxUpstreamBillDetailDto>();
}

export function resolveMsfxUpstreamByScanApi(_data: MsfxResolveUpstreamRequest) {
  return disabled<MsfxUpstreamBillListDto>();
}

export function applyMsfxUpstreamToInboundApi(_data: MsfxApplyUpstreamRequest) {
  return disabled<MsfxUpstreamImportDto>();
}

export function getMsfxUpstreamImportByHisBusNoApi(_data: MsfxHisBusNoRequest) {
  return disabled<MsfxUpstreamImportDto | null>();
}
