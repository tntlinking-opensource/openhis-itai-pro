/**
 * 码上放心箱码展开 / 企业查询 API。
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

export interface MsfxExpandTraceBoxItemDto {
  sourceTraceNo?: string;
  childTraceNos?: string[];
  expandStatusEnum?: string;
  message?: string;
}

export interface MsfxExpandTraceBoxCodesResponse {
  items?: MsfxExpandTraceBoxItemDto[];
}

export function expandMsfxTraceBoxCodesApi(data: { traceNos: string[] }) {
  return requestClient.post<ApiResponse<MsfxExpandTraceBoxCodesResponse>>(
    '/msfxBoxExpand/expandTraceBoxCodes',
    data,
  );
}

export interface MsfxEntInfoRowDto {
  entId?: string;
  refEntId?: string;
  entName?: string;
  auditStatus?: string;
  settleStatus?: string;
}

export interface MsfxEntInfoListDto {
  responseSuccess?: boolean;
  msgCode?: string;
  msgInfo?: string;
  rows?: MsfxEntInfoRowDto[];
}

export function listMsfxEntInfoApi(data: { entName: string }) {
  return requestClient.post<ApiResponse<MsfxEntInfoListDto>>('/msfxEnterprise/listEntInfo', data);
}

export interface MsfxServiceEndDateDto {
  responseSuccess?: boolean;
  msgCode?: string;
  msgInfo?: string;
  endDate?: string;
}

export function getMsfxServiceEndDateApi() {
  return requestClient.post<ApiResponse<MsfxServiceEndDateDto>>(
    '/msfxEnterprise/getServiceEndDate',
    {},
  );
}
