/** Opensource stub — APIs disabled. */
import type { ApiResponse } from '#/types/api-response';

function disabled<T>(): Promise<ApiResponse<T>> {
  return Promise.reject(new Error('Optional module disabled in opensource build'));
}

export interface MsfxExpandTraceBoxItemDto {
  sourceTraceNo?: string;
  childTraceNos?: string[];
  expandStatusEnum?: string;
  message?: string;
}

export interface MsfxExpandTraceBoxCodesResponse {
  items?: MsfxExpandTraceBoxItemDto[];
}

export function expandMsfxTraceBoxCodesApi(_data: { traceNos: string[] }) {
  return disabled<MsfxExpandTraceBoxCodesResponse>();
}

export interface MsfxEntInfoRowDto {
  entId?: string;
  entName?: string;
  refEntId?: string;
  [key: string]: unknown;
}

export interface MsfxEntInfoListDto {
  rows?: MsfxEntInfoRowDto[];
}

export function listMsfxEntInfoApi(_data: { entName: string }) {
  return disabled<MsfxEntInfoListDto>();
}

export interface MsfxServiceEndDateDto {
  endDate?: string;
}

export function getMsfxServiceEndDateApi() {
  return disabled<MsfxServiceEndDateDto>();
}
