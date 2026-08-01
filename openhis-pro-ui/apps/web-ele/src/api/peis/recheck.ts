/**
 * 复查管理 API（AS-PEIS-REC-001）。
 */
import { publicRequestClient } from '#/api/request';

import type { IdLike } from './catalog-config';

export interface RecheckRowDto {
  busNo?: string;
  encounterId?: IdLike;
  progressLabel?: string;
  reason?: string;
  statusCode?: string;
  taskId?: IdLike;
}

export interface SearchRecheckRequest {
  orgId?: IdLike;
  pageNo: number;
  pageSize: number;
  statusCode?: string;
}

export interface SearchRecheckResponse {
  records: RecheckRowDto[];
  total: number | string;
}

export function httpSearchRecheck(data: SearchRecheckRequest) {
  return publicRequestClient.post<SearchRecheckResponse>('/peisRecheckManage/searchRecheck', data);
}

export function httpCreateRecheck(data: {
  encounterId: IdLike;
  operatorId?: IdLike;
  reason: string;
}) {
  return publicRequestClient.post<{ encounterId?: IdLike; taskId?: IdLike }>(
    '/peisRecheckManage/createRecheck',
    data,
  );
}

export function httpConfirmRecheckItems(data: {
  activityIds: IdLike[];
  operatorId?: IdLike;
  taskId: IdLike;
}) {
  return publicRequestClient.post<void>('/peisRecheckManage/confirmRecheckItems', data);
}

export function httpCompleteRecheck(data: {
  diagnosticReportId: IdLike;
  operatorId?: IdLike;
  taskId: IdLike;
}) {
  return publicRequestClient.post<void>('/peisRecheckManage/completeRecheck', data);
}
