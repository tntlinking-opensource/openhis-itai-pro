/**
 * 病历/病案归档审批 API（MrmsRecordArchiveApprovalAppService）
 */

import type { PageApiResponse } from '#/types/api-response';

import type {
  ApproveArchiveBatchParams,
  ApproveArchiveParams,
  GetPendingArchiveDetailParams,
  PagePendingArchiveParams,
  PendingArchiveDetailDto,
  PendingArchiveListItemDto,
  RejectArchiveBatchParams,
  RejectArchiveParams,
} from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const BASE = '/mrmsRecordArchiveApproval';

/** 待归档审批分页 */
export async function pagePendingArchiveApi(params: PagePendingArchiveParams) {
  return publicRequestClient.post<PageApiResponse<PendingArchiveListItemDto>>(
    `${BASE}/pagePendingArchive`,
    {
      pageNo: params.pageNo ?? 1,
      pageSize: params.pageSize ?? 20,
      keyword: params.keyword?.trim() || undefined,
      bizCategory: params.bizCategory,
    },
  );
}

/** 待审详情只读预览 */
export async function getPendingArchiveDetailApi(params: GetPendingArchiveDetailParams) {
  return publicRequestClient.post<PendingArchiveDetailDto>(`${BASE}/getPendingArchiveDetail`, {
    bizCategory: params.bizCategory,
    bizId: preserveApiIdForRequest(params.bizId),
  });
}

/** 同意归档 */
export async function approveArchiveApi(params: ApproveArchiveParams) {
  return publicRequestClient.post<void>(`${BASE}/approveArchive`, {
    bizCategory: params.bizCategory,
    bizId: preserveApiIdForRequest(params.bizId),
    qualityGradeCode: params.qualityGradeCode?.trim() || undefined,
  });
}

/** 驳回归档（须填 rejectReason） */
export async function rejectArchiveApi(params: RejectArchiveParams) {
  return publicRequestClient.post<void>(`${BASE}/rejectArchive`, {
    bizCategory: params.bizCategory,
    bizId: preserveApiIdForRequest(params.bizId),
    rejectReason: params.rejectReason,
  });
}

/** 批量同意归档（同批须同 bizCategory） */
export async function approveArchiveBatchApi(params: ApproveArchiveBatchParams) {
  return publicRequestClient.post<void>(`${BASE}/approveArchiveBatch`, {
    bizCategory: params.bizCategory,
    bizIds: (params.bizIds ?? []).map((id) => preserveApiIdForRequest(id)).filter(Boolean),
    qualityGradeCode: params.qualityGradeCode?.trim() || undefined,
  });
}

/** 批量驳回归档 */
export async function rejectArchiveBatchApi(params: RejectArchiveBatchParams) {
  return publicRequestClient.post<void>(`${BASE}/rejectArchiveBatch`, {
    bizCategory: params.bizCategory,
    bizIds: (params.bizIds ?? []).map((id) => preserveApiIdForRequest(id)).filter(Boolean),
    rejectReason: params.rejectReason,
  });
}
