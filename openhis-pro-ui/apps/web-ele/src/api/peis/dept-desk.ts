/**
 * 分科诊台 API（AS-PEIS-DES-001）。
 */

import { publicRequestClient } from '#/api/request';

export interface PendingDeskRowDto {
  activityDisplay?: null | string;
  activityId?: null | string;
  busNo?: null | string;
  encounterId: string;
  encounterStatusEnum?: null | string;
  execStatusEnum?: null | string;
  groupFlag?: boolean | null;
  lisDispatchEnum?: null | string;
  patientAge?: null | number;
  patientId: string;
  patientName?: null | string;
  serviceRequestId: string;
}

export interface SearchPendingRequest {
  deptOrgId?: null | string;
  endDate?: null | string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
  startDate?: null | string;
}

export interface SearchPendingResponse {
  records: PendingDeskRowDto[];
  total: number | string;
}

export interface ObservationRowDto {
  categoryCode?: null | string;
  definitionDisplay?: null | string;
  definitionId: string;
  id?: null | string;
  interpretationCode?: null | string;
  statusEnum?: null | string;
  valueQuantity?: null | number | string;
  valueString?: null | string;
}

export interface ObservationListResponse {
  observations: ObservationRowDto[];
  serviceRequestId: string;
}

export interface SaveObservationRequest {
  definitionId: string;
  deptOrgId?: null | string;
  finalizeFlag?: boolean;
  interpretationCode?: null | string;
  serviceRequestId: string;
  valueQuantity?: null | number | string;
  valueString?: null | string;
}

export interface SaveObservationResponse {
  observationId: string;
  statusEnum?: null | string;
}

export interface BatchSaveObservationsResponse {
  results?: SaveObservationResponse[];
  successCount?: number;
}

export interface ApplyCommonResultRequest {
  commonResultId: string;
  deptOrgId?: null | string;
  finalizeFlag?: boolean;
  serviceRequestId: string;
}

export interface SaveProjectConclusionRequest {
  conditionDefinitionId?: null | string;
  definitionId?: string;
  deptOrgId?: null | string;
  serviceRequestId: string;
  valueString: string;
}

/** @deprecated 使用 {@link SaveProjectConclusionRequest} */
export type SaveDeptConclusionRequest = SaveProjectConclusionRequest;

export async function httpSearchPendingDeptDesk(
  body: SearchPendingRequest,
): Promise<SearchPendingResponse> {
  return publicRequestClient.post<SearchPendingResponse>('/peisDeptExamDesk/searchPending', body);
}

export async function httpLoadObservationsByServiceRequest(body: {
  activityId?: null | string;
  serviceRequestId: string;
}): Promise<ObservationListResponse> {
  return publicRequestClient.post<ObservationListResponse>(
    '/peisDeptExamDesk/loadObservationsByServiceRequest',
    body,
  );
}

export async function httpSaveObservation(
  body: SaveObservationRequest,
): Promise<SaveObservationResponse> {
  return publicRequestClient.post<SaveObservationResponse>(
    '/peisDeptExamDesk/saveObservation',
    body,
  );
}

export async function httpBatchSaveObservations(body: {
  items: SaveObservationRequest[];
}): Promise<BatchSaveObservationsResponse> {
  return publicRequestClient.post<BatchSaveObservationsResponse>(
    '/peisDeptExamDesk/batchSaveObservations',
    body,
  );
}

export async function httpApplyCommonResult(
  body: ApplyCommonResultRequest,
): Promise<SaveObservationResponse> {
  return publicRequestClient.post<SaveObservationResponse>(
    '/peisDeptExamDesk/applyCommonResult',
    body,
  );
}

export async function httpSaveProjectConclusion(
  body: SaveProjectConclusionRequest,
): Promise<SaveObservationResponse> {
  return publicRequestClient.post<SaveObservationResponse>(
    '/peisDeptExamDesk/saveProjectConclusion',
    body,
  );
}

export async function httpProjectResults(body: { serviceRequestId: string }): Promise<void> {
  return publicRequestClient.post<void>('/peisDeptExamDesk/projectResults', body);
}
