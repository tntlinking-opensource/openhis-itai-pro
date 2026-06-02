/**
 * 病案首页 API（CaseRecordHomepageAppService）
 */

import type {
  CaseRecordDetailDto,
  HomepageDto,
  RefreshAutoFillParams,
  SaveHomepageParams,
} from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

export async function getHomepageApi(caseRecordId: string) {
  return publicRequestClient.post<HomepageDto>('/caseRecordHomepage/getHomepage', {
    value: preserveApiIdForRequest(caseRecordId),
  });
}

export async function saveHomepageApi(params: SaveHomepageParams) {
  return publicRequestClient.post<HomepageDto>('/caseRecordHomepage/saveHomepage', {
    ...params,
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
  });
}

export async function refreshAutoFillApi(params: RefreshAutoFillParams) {
  return publicRequestClient.post<HomepageDto>('/caseRecordHomepage/refreshAutoFill', {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    sections: params.sections,
    forceOverwrite: params.forceOverwrite ?? false,
  });
}

export async function submitHomepageApi(caseRecordId: string) {
  return publicRequestClient.post<CaseRecordDetailDto>('/caseRecordHomepage/submit', {
    caseRecordId: preserveApiIdForRequest(caseRecordId),
  });
}
