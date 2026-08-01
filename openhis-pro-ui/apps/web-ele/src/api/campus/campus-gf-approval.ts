/**
 * 公费比例审批 — POST /api/app/campusGfApproval/<method>
 */
import type { CampusPagedResult, GfChargeMode, IdLike } from './types';

import type { GfRatioRequestDto } from './campus-gf-record';

import { publicRequestClient } from '#/api/request';

export function pageTodoApi(params: {
  chargeModeEnum?: GfChargeMode | null;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<CampusPagedResult<GfRatioRequestDto>>(
    '/campusGfApproval/pageTodo',
    params,
  );
}

export function pageFinishedApi(params: {
  chargeModeEnum?: GfChargeMode | null;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<CampusPagedResult<GfRatioRequestDto>>(
    '/campusGfApproval/pageFinished',
    params,
  );
}

export function approveApi(params: { requestId: IdLike; comment?: string }) {
  return publicRequestClient.post<{ requestId?: IdLike }>('/campusGfApproval/approve', params);
}

export function rejectApi(params: { requestId: IdLike; comment?: string }) {
  return publicRequestClient.post<{ requestId?: IdLike }>('/campusGfApproval/reject', params);
}

export function getApprovalDetailApi(params: { requestId: IdLike }) {
  return publicRequestClient.post<GfRatioRequestDto>('/campusGfApproval/getApprovalDetail', params);
}
