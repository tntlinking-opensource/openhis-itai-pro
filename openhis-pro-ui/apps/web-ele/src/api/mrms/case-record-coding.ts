/**
 * 病案编码 API（CaseRecordCodingAppService）
 */

import type { DiagnosisLineDto, IcdItemDto, ProcedureLineDto, SearchIcdParams } from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

export async function searchIcd10Api(params: SearchIcdParams) {
  return publicRequestClient.post<IcdItemDto[]>('/caseRecordCoding/searchIcd10', params);
}

export async function searchIcd9Cm3Api(params: SearchIcdParams) {
  return publicRequestClient.post<IcdItemDto[]>('/caseRecordCoding/searchIcd9Cm3', params);
}

export async function saveDiagnosisLinesApi(params: {
  caseRecordId: string;
  diagTypeCode: string;
  lines: DiagnosisLineDto[];
}) {
  return publicRequestClient.post<DiagnosisLineDto[]>('/caseRecordCoding/saveDiagnosisLines', {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    diagTypeCode: params.diagTypeCode,
    lines: params.lines,
  });
}

export async function saveProcedureLinesApi(params: {
  caseRecordId: string;
  lines: ProcedureLineDto[];
}) {
  return publicRequestClient.post<ProcedureLineDto[]>('/caseRecordCoding/saveProcedureLines', {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    lines: params.lines,
  });
}
