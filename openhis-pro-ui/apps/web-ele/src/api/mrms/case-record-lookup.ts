/**
 * 病案查档 API（MrmsCaseRecordLookupAppService · 医生站/护士站）
 */

import type { CaseRecordDetailDto, PdfGenerateResultDto } from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const BASE = '/mrmsCaseRecordLookup';

export async function getCaseRecordByEncounterApi(encounterId: string) {
  return publicRequestClient.post<CaseRecordDetailDto | null>(`${BASE}/getByEncounterId`, {
    encounterId: preserveApiIdForRequest(encounterId),
  });
}

export async function generateHomepagePdfViaLookupApi(caseRecordId: string) {
  return publicRequestClient.post<PdfGenerateResultDto>(`${BASE}/generateHomepagePdf`, {
    caseRecordId: preserveApiIdForRequest(caseRecordId),
  });
}
