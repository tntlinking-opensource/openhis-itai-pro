/**
 * 门诊病历查询 API（MrmsOutpatientRecordListAppService）
 */

import type { PageApiResponse } from '#/types/api-response';

import type { OutpatientRecordListItemDto, QueryOutpatientRecordParams } from './types';

import { publicRequestClient } from '#/api/request';

export async function queryOutpatientRecordPageApi(params: QueryOutpatientRecordParams) {
  return publicRequestClient.post<PageApiResponse<OutpatientRecordListItemDto>>(
    '/mrmsOutpatientRecordList/queryPage',
    params,
  );
}
