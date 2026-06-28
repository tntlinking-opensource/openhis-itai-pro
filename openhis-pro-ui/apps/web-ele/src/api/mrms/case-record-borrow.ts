/**
 * 病案借阅 API（MrmsCaseRecordBorrowAppService）
 */

import type {
  ApplyBorrowParams,
  BorrowActionParams,
  BorrowLifecycleParams,
  BorrowRequestItemDto,
  QueryBorrowPageParams,
} from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/mrmsCaseRecordBorrow';

export async function applyBorrowApi(params: ApplyBorrowParams) {
  return publicRequestClient.post<BorrowRequestItemDto>(`${BASE}/apply`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    requestTypeCode: params.requestTypeCode,
    applicantName: params.applicantName,
    applicantDeptName: params.applicantDeptName,
    purpose: params.purpose,
    scopeDesc: params.scopeDesc,
    copyCount: params.copyCount,
    patientAuthorizationInfo: params.patientAuthorizationInfo,
  });
}

export async function approveBorrowApi(params: BorrowActionParams) {
  return publicRequestClient.post<BorrowRequestItemDto>(`${BASE}/approve`, {
    borrowRequestId: preserveApiIdForRequest(params.borrowRequestId),
    comment: params.comment,
  });
}

export async function rejectBorrowApi(params: BorrowActionParams) {
  return publicRequestClient.post<BorrowRequestItemDto>(`${BASE}/reject`, {
    borrowRequestId: preserveApiIdForRequest(params.borrowRequestId),
    comment: params.comment,
  });
}

export async function markBorrowedApi(params: BorrowLifecycleParams) {
  return publicRequestClient.post<BorrowRequestItemDto>(`${BASE}/markBorrowed`, {
    borrowRequestId: preserveApiIdForRequest(params.borrowRequestId),
    dueReturnAt: params.dueReturnAt,
  });
}

export async function markReturnedApi(params: BorrowLifecycleParams) {
  return publicRequestClient.post<BorrowRequestItemDto>(`${BASE}/markReturned`, {
    borrowRequestId: preserveApiIdForRequest(params.borrowRequestId),
  });
}

export async function queryBorrowPageApi(params: QueryBorrowPageParams) {
  const data = await publicRequestClient.post(`${BASE}/queryPage`, {
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 20,
    statusCode: params.statusCode,
    keyword: params.keyword,
    caseRecordId: params.caseRecordId ? preserveApiIdForRequest(params.caseRecordId) : undefined,
  });
  return parsePagedRowsFromApiData<BorrowRequestItemDto>(data);
}
