/**
 * 门诊病历查询 API（MrmsOutpatientEmrQueryAppService）
 */

import type { PageApiResponse } from '#/types/api-response';

import type { OutpatientEmrListItemDto, QueryOutpatientEmrParams } from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 门诊病例查询分页 */
export async function queryOutpatientEmrPageApi(params: QueryOutpatientEmrParams) {
  return publicRequestClient.post<PageApiResponse<OutpatientEmrListItemDto>>(
    '/mrmsOutpatientEmrQuery/queryPage',
    params,
  );
}

/** 归档病例请求（按就诊锁定 EMR 文书） */
export interface ArchiveOutpatientClinicalCaseParams {
  /** 就诊主键（string，防雪花精度丢失） */
  encounterId: string;
}

/** 归档病例：锁定该次门诊就诊下文书（仅 ROLE_MRMS_ADMIN） */
export async function archiveOutpatientClinicalCaseApi(
  params: ArchiveOutpatientClinicalCaseParams,
) {
  return publicRequestClient.post<void>('/mrmsOutpatientEmrQuery/archiveClinicalCase', params);
}

/** 解档病例：ARCHIVED → DRAFT（仅 ROLE_MRMS_ADMIN；病案主档已归档时后端拒绝） */
export async function unarchiveOutpatientClinicalCaseApi(
  params: ArchiveOutpatientClinicalCaseParams,
) {
  return publicRequestClient.post<void>('/mrmsOutpatientEmrQuery/unarchiveClinicalCase', params);
}

/** 单份文书提交归档（DRAFT → PENDING_ARCHIVE） */
export interface SubmitOutpatientDocumentArchiveParams {
  /** 文书主键（string，防雪花精度丢失） */
  documentId: string;
}

/** 批量文书提交归档 */
export interface SubmitOutpatientDocumentArchiveBatchParams {
  /** 文书主键列表 */
  documentIds: string[];
}

/** 单份提交归档审批（IF-MRMS-OEMR-03） */
export async function submitOutpatientDocumentArchiveApi(
  params: SubmitOutpatientDocumentArchiveParams,
) {
  return publicRequestClient.post<void>('/mrmsOutpatientEmrQuery/submitDocumentArchive', {
    documentId: preserveApiIdForRequest(params.documentId),
  });
}

/** 批量提交归档审批（IF-MRMS-OEMR-04；任一份失败整批回滚） */
export async function submitOutpatientDocumentArchiveBatchApi(
  params: SubmitOutpatientDocumentArchiveBatchParams,
) {
  return publicRequestClient.post<void>('/mrmsOutpatientEmrQuery/submitDocumentArchiveBatch', {
    documentIds: (params.documentIds ?? [])
      .map((id) => preserveApiIdForRequest(id))
      .filter(Boolean),
  });
}
