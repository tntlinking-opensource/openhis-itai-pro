/**
 * 病案质控 API（MrmsCaseRecordQcAppService）
 */

import type {
  AddManualDefectParams,
  ArchiveCaseRecordParams,
  CaseRecordDetailDto,
  DefectActionParams,
  ListDefectsParams,
  ListQcRunsParams,
  QcDefectDto,
  QcRunResultDto,
  QcRunSummaryDto,
  RerunQcParams,
  StartQcParams,
  UnarchiveCaseRecordParams,
} from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const BASE = '/mrmsCaseRecordQc';

function withCaseRecordId<T extends { caseRecordId?: string }>(params: T) {
  return {
    ...params,
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
  };
}

export async function startQcApi(params: StartQcParams) {
  return publicRequestClient.post<QcRunResultDto>(`${BASE}/startQc`, withCaseRecordId(params));
}

export async function rerunQcApi(params: RerunQcParams) {
  return publicRequestClient.post<QcRunResultDto>(`${BASE}/rerunQc`, withCaseRecordId(params));
}

export async function listDefectsApi(params: ListDefectsParams) {
  return publicRequestClient.post<QcDefectDto[]>(`${BASE}/listDefects`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    statusCode: params.statusCode,
    severityCode: params.severityCode,
  });
}

export async function listQcRunsApi(params: ListQcRunsParams) {
  return publicRequestClient.post<QcRunSummaryDto[]>(
    `${BASE}/listQcRuns`,
    withCaseRecordId(params),
  );
}

export async function markRectifiedApi(params: DefectActionParams) {
  return publicRequestClient.post<QcDefectDto>(`${BASE}/markRectified`, {
    defectId: preserveApiIdForRequest(params.defectId),
  });
}

export async function closeDefectApi(params: DefectActionParams) {
  return publicRequestClient.post<QcDefectDto>(`${BASE}/closeDefect`, {
    defectId: preserveApiIdForRequest(params.defectId),
  });
}

export async function addManualDefectApi(params: AddManualDefectParams) {
  return publicRequestClient.post<QcDefectDto>(`${BASE}/addManualDefect`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    severityCode: params.severityCode,
    defectMessage: params.defectMessage,
  });
}

export async function archiveCaseRecordApi(params: ArchiveCaseRecordParams) {
  return publicRequestClient.post<CaseRecordDetailDto>(`${BASE}/archive`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    qualityGradeCode: params.qualityGradeCode,
  });
}

export async function unarchiveCaseRecordApi(params: UnarchiveCaseRecordParams) {
  return publicRequestClient.post<CaseRecordDetailDto>(`${BASE}/unarchive`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    unarchiveReason: params.unarchiveReason,
  });
}
