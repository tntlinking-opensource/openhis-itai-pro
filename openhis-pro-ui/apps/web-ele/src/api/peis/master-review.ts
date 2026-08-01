/**
 * 总检诊台 API（AS-PEIS-MAS-001）。
 */

import { publicRequestClient } from '#/api/request';

export interface MasterPendingRowDto {
  busNo?: null | string;
  checkInTime?: null | string;
  deptCompleteRatio?: null | string;
  encounterId: string;
  patientName?: null | string;
}

export interface SearchPendingMasterRequest {
  endDate?: null | string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
  startDate?: null | string;
}

export interface SearchPendingMasterResponse {
  records: MasterPendingRowDto[];
  total: number | string;
}

export interface MasterObservationRowDto {
  categoryCode?: null | string;
  definitionId?: null | string;
  id?: null | string;
  interpretationCode?: null | string;
  serviceRequestId?: null | string;
  statusEnum?: null | string;
  valueQuantity?: null | number | string;
  valueString?: null | string;
}

export interface DeptSummarySectionDto {
  observations: MasterObservationRowDto[];
  serviceRequestId?: null | string;
}

export interface MasterSummaryResponse {
  conclusionText?: null | string;
  deptSections: DeptSummarySectionDto[];
  diagnosticReportId?: null | string;
  encounterId: string;
  patientId?: null | string;
  reportStatusEnum?: null | string;
  summaryText?: null | string;
}

export interface SaveMasterDraftRequest {
  conclusionText: string;
  encounterId: string;
  summaryText?: null | string;
}

export interface SaveMasterDraftResponse {
  diagnosticReportId: string;
  statusEnum?: null | string;
}

export interface CompleteMasterReviewResponse {
  diagnosticReportId: string;
  encounterId: string;
  statusEnum?: null | string;
}

export async function httpSearchPendingMaster(
  body: SearchPendingMasterRequest,
): Promise<SearchPendingMasterResponse> {
  return publicRequestClient.post<SearchPendingMasterResponse>(
    '/peisMasterReviewDesk/searchPendingMaster',
    body,
  );
}

export async function httpLoadMasterSummary(body: {
  encounterId: string;
}): Promise<MasterSummaryResponse> {
  return publicRequestClient.post<MasterSummaryResponse>('/peisMasterReviewDesk/loadSummary', body);
}

export async function httpSaveMasterDraft(
  body: SaveMasterDraftRequest,
): Promise<SaveMasterDraftResponse> {
  return publicRequestClient.post<SaveMasterDraftResponse>(
    '/peisMasterReviewDesk/saveMasterDraft',
    body,
  );
}

export async function httpCompleteMasterReview(body: {
  encounterId: string;
}): Promise<CompleteMasterReviewResponse> {
  return publicRequestClient.post<CompleteMasterReviewResponse>(
    '/peisMasterReviewDesk/completeMasterReview',
    body,
  );
}
