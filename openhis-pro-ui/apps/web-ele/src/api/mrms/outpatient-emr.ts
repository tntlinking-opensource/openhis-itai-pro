/**
 * 门诊病历查询 API（MrmsOutpatientEmrQueryAppService）
 */

import type { PageApiResponse } from '#/types/api-response';

import type { OutpatientEmrListItemDto, QueryOutpatientEmrParams } from './types';

import { publicRequestClient } from '#/api/request';

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
