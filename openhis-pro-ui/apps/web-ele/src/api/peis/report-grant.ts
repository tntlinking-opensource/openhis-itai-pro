/**
 * 报告发放 API（AS-PEIS-REP-002）。
 */
import { publicRequestClient } from '#/api/request';

import type { IdLike } from './catalog-config';

export interface GrantRowDto {
  busNo?: string;
  diagnosticReportId?: IdLike;
  encounterId?: IdLike;
  grantStatusCode?: string;
  patientName?: string;
  reportFinalTime?: string;
}

export interface SearchPendingGrantRequest {
  dateFrom?: string;
  dateTo?: string;
  grantStatusCode?: string;
  orgId?: IdLike;
  pageNo: number;
  pageSize: number;
}

export interface SearchPendingGrantResponse {
  records: GrantRowDto[];
  total: number | string;
}

export function httpSearchPendingGrant(data: SearchPendingGrantRequest) {
  return publicRequestClient.post<SearchPendingGrantResponse>(
    '/peisReportGrant/searchPendingGrant',
    data,
  );
}

export function httpRegisterGrant(data: { diagnosticReportId: IdLike; operatorId?: IdLike }) {
  return publicRequestClient.post<void>('/peisReportGrant/registerGrant', data);
}

export function httpSignGrant(data: {
  diagnosticReportId: IdLike;
  operatorId?: IdLike;
  recipientName?: string;
}) {
  return publicRequestClient.post<void>('/peisReportGrant/signGrant', data);
}

export function httpBatchGroupPrint(data: {
  encounterIds?: IdLike[];
  groupBatchId?: IdLike;
  operatorId?: IdLike;
}) {
  return publicRequestClient.post<{
    failedEncounterIds?: IdLike[];
    message?: string;
    successCount?: number;
  }>('/peisReportGrant/batchGroupPrint', data);
}
