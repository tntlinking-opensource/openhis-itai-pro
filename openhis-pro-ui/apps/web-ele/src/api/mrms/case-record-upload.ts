/**
 * 4401 上传 API（MrmsCaseRecordUploadAppService）
 */

import type {
  CaseRecordUploadSummaryDto,
  ListUploadLogsParams,
  QueryUploadPageParams,
  RetryUploadParams,
  Upload4401Params,
  UploadLogDto,
  UploadResultDto,
} from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/mrmsCaseRecordUpload';

export async function upload4401Api(params: Upload4401Params) {
  return publicRequestClient.post<UploadResultDto>(`${BASE}/upload`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    contractBusNo: params.contractBusNo,
  });
}

export async function retryUploadApi(params: RetryUploadParams) {
  return publicRequestClient.post<UploadResultDto>(`${BASE}/retry`, {
    uploadLogId: preserveApiIdForRequest(params.uploadLogId),
  });
}

export async function queryUploadLogsApi(params: ListUploadLogsParams) {
  return publicRequestClient.post<UploadLogDto[]>(`${BASE}/queryUploadLogs`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
  });
}

export async function queryUploadPageApi(params: QueryUploadPageParams) {
  const data = await publicRequestClient.post(`${BASE}/queryUploadPage`, {
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 20,
    statusCode: params.statusCode,
    keyword: params.keyword,
  });
  return parsePagedRowsFromApiData<CaseRecordUploadSummaryDto>(data);
}

export async function previewAssembled4401Api(params: Upload4401Params) {
  return publicRequestClient.post<Record<string, unknown>>(`${BASE}/previewAssembled`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    contractBusNo: params.contractBusNo,
  });
}
