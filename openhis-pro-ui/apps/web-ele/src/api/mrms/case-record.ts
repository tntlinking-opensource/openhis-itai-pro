/**
 * 病案主档 API（CaseRecordAppService / CaseRecordWorkflowAppService）
 */

import type { PageApiResponse, PageParams } from '#/types/api-response';

import type {
  CaseRecordActionParams,
  CaseRecordDetailDto,
  CaseRecordSummaryDto,
  QueryCaseRecordParams,
} from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

export async function queryCaseRecordPageApi(params: QueryCaseRecordParams) {
  return publicRequestClient.post<PageApiResponse<CaseRecordSummaryDto>>(
    '/caseRecord/queryPage',
    params,
  );
}

export async function getCaseRecordByIdApi(caseRecordId: string) {
  return publicRequestClient.post<CaseRecordDetailDto>('/caseRecord/getById', {
    value: preserveApiIdForRequest(caseRecordId),
  });
}

export async function getCaseRecordByEncounterApi(encounterId: string) {
  return publicRequestClient.post<CaseRecordDetailDto>('/caseRecord/getByEncounterId', {
    value: preserveApiIdForRequest(encounterId),
  });
}

export async function receiveCaseRecordApi(params: CaseRecordActionParams) {
  return publicRequestClient.post<CaseRecordDetailDto>('/caseRecordWorkflow/receive', {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    reason: params.reason,
  });
}

export async function returnCaseRecordApi(params: CaseRecordActionParams) {
  return publicRequestClient.post<CaseRecordDetailDto>('/caseRecordWorkflow/returnToDoctor', {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    reason: params.reason,
  });
}

export type { QueryCaseRecordParams, PageParams };
