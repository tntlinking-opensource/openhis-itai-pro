/**

 * 住院病例查询 API（MrmsInpatientEmrQueryAppService）

 */

import type { PageApiResponse } from '#/types/api-response';

import type { InpatientEmrListItemDto, QueryInpatientEmrParams } from './types';

import { publicRequestClient } from '#/api/request';

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

/** 解档病例：解锁 LOCKED 文书（仅 ROLE_MRMS_ADMIN；病案主档已归档时后端拒绝） */

export async function unarchiveInpatientClinicalCaseApi(
  params: ArchiveInpatientClinicalCaseParams,
) {
  return publicRequestClient.post<void>(
    '/mrmsInpatientEmrQuery/unarchiveClinicalCase',

    params,
  );
}
