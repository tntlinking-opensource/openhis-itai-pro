/**

 * 住院病例查询 API（MrmsInpatientEmrQueryAppService）

 */

import type { PageApiResponse } from '#/types/api-response';

import type { InpatientEmrListItemDto, QueryInpatientEmrParams } from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 分页检索住院病例（以住院就诊为行） */

export async function queryInpatientEmrPageApi(params: QueryInpatientEmrParams) {
  return publicRequestClient.post<PageApiResponse<InpatientEmrListItemDto>>(
    '/mrmsInpatientEmrQuery/queryPage',

    params,
  );
}

/** 住院归档/解档病例请求（按就诊锁定 EMR 文书） */

export interface ArchiveInpatientClinicalCaseParams {
  /** 住院就诊主键（string，防雪花精度丢失） */

  encounterId: string;
}

/** 归档病例：锁定该次住院就诊下文书（仅 ROLE_MRMS_ADMIN） */

export async function archiveInpatientClinicalCaseApi(params: ArchiveInpatientClinicalCaseParams) {
  return publicRequestClient.post<void>(
    '/mrmsInpatientEmrQuery/archiveClinicalCase',

    params,
  );
}

/** 解档病例：ARCHIVED → DRAFT（仅 ROLE_MRMS_ADMIN；病案主档已归档时后端拒绝） */

export async function unarchiveInpatientClinicalCaseApi(
  params: ArchiveInpatientClinicalCaseParams,
) {
  return publicRequestClient.post<void>(
    '/mrmsInpatientEmrQuery/unarchiveClinicalCase',

    params,
  );
}

/** 单份住院文书提交归档（DRAFT → PENDING_ARCHIVE） */
export interface SubmitInpatientDocumentArchiveParams {
  /** 文书主键（string，防雪花精度丢失） */
  documentId: string;
}

/** 批量住院文书提交归档 */
export interface SubmitInpatientDocumentArchiveBatchParams {
  /** 文书主键列表 */
  documentIds: string[];
}

/** 单份提交归档审批 */
export async function submitInpatientDocumentArchiveApi(
  params: SubmitInpatientDocumentArchiveParams,
) {
  return publicRequestClient.post<void>('/mrmsInpatientEmrQuery/submitDocumentArchive', {
    documentId: preserveApiIdForRequest(params.documentId),
  });
}

/** 批量提交归档审批（任一份失败整批回滚） */
export async function submitInpatientDocumentArchiveBatchApi(
  params: SubmitInpatientDocumentArchiveBatchParams,
) {
  return publicRequestClient.post<void>('/mrmsInpatientEmrQuery/submitDocumentArchiveBatch', {
    documentIds: (params.documentIds ?? [])
      .map((id) => preserveApiIdForRequest(id))
      .filter(Boolean),
  });
}
