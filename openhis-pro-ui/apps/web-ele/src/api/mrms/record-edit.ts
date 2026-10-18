/**
 * 已归档文书修改审核（MrmsRecordEditFacadeAppService）。
 * 不直接保存文书；审核通过后由服务端回写为最新版本。
 */

import type { PageApiResponse } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const BASE = '/mrmsRecordEditFacade';
const TARGET_KIND = 'document';

export interface DocumentEditApplicationDto {
  applicationId?: string;
  documentId?: string;
  statusEnum?: string;
  applyReason?: string;
  rejectReason?: string;
  fromVersion?: number;
  toVersion?: number;
  oldContentJson?: string;
  newContentJson?: string;
  submittedAt?: string;
  appliedAt?: string;
}

/** 创建已归档文书的修改申请 */
export async function createDocumentEditApplicationApi(params: {
  documentId: string;
  applyReason: string;
  encounterClassCode?: string;
}) {
  return publicRequestClient.post<DocumentEditApplicationDto>(`${BASE}/createEditApplication`, {
    targetKind: TARGET_KIND,
    documentId: preserveApiIdForRequest(params.documentId),
    applyReason: params.applyReason,
    encounterClassCode: params.encounterClassCode,
  });
}

/** 写入待审正文（仍不改正式病历） */
export async function saveDocumentEditDraftApi(params: {
  applicationId: string;
  newContentJson: string;
}) {
  return publicRequestClient.post<DocumentEditApplicationDto>(`${BASE}/saveEditDraft`, {
    targetKind: TARGET_KIND,
    applicationId: preserveApiIdForRequest(params.applicationId),
    newContentJson: params.newContentJson,
  });
}

/** 交审：起草 → 待审核 */
export async function submitDocumentEditForReviewApi(params: { applicationId: string }) {
  return publicRequestClient.post<DocumentEditApplicationDto>(`${BASE}/submitEditForReview`, {
    targetKind: TARGET_KIND,
    applicationId: preserveApiIdForRequest(params.applicationId),
  });
}

/** 待审修改分页 */
export async function pageDocumentEditApplicationsApi(params: {
  pageNo: number;
  pageSize: number;
  statusEnum?: string;
}) {
  return publicRequestClient.post<PageApiResponse<DocumentEditApplicationDto>>(
    `${BASE}/pageEditApplications`,
    {
      targetKind: TARGET_KIND,
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      statusEnum: params.statusEnum,
    },
  );
}

/** 打开申请，含修改前/修改后正文 */
export async function getDocumentEditApplicationApi(params: { applicationId: string }) {
  return publicRequestClient.post<DocumentEditApplicationDto>(`${BASE}/getEditApplication`, {
    targetKind: TARGET_KIND,
    applicationId: preserveApiIdForRequest(params.applicationId),
  });
}

/** 同意：回写文书正文并升版本，状态仍为已归档 */
export async function approveDocumentEditApi(params: { applicationId: string }) {
  return publicRequestClient.post<DocumentEditApplicationDto>(`${BASE}/approveEdit`, {
    targetKind: TARGET_KIND,
    applicationId: preserveApiIdForRequest(params.applicationId),
  });
}

/** 驳回修改申请 */
export async function rejectDocumentEditApi(params: {
  applicationId: string;
  rejectReason: string;
}) {
  return publicRequestClient.post<DocumentEditApplicationDto>(`${BASE}/rejectEdit`, {
    targetKind: TARGET_KIND,
    applicationId: preserveApiIdForRequest(params.applicationId),
    rejectReason: params.rejectReason,
  });
}
