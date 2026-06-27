/**
 * 病案检索 API（MrmsCaseRecordListAppService）
 */

import type { PageApiResponse, PageParams } from '#/types/api-response';

import type { CaseRecordListItemDto, QueryCaseRecordParams } from './types';

import { publicRequestClient } from '#/api/request';

export async function queryCaseRecordPageApi(params: QueryCaseRecordParams) {
  return publicRequestClient.post<PageApiResponse<CaseRecordListItemDto>>(
    '/mrmsCaseRecordList/queryPage',
    params,
  );
}

export type { QueryCaseRecordParams, PageParams };
