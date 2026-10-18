/**
 * 病案编码 API（MrmsCaseRecordCodingAppService · ICD Picker）
 */

import type { IcdItemDto, SearchIcdPageResult, SearchIcdParams } from './types';

import { publicRequestClient } from '#/api/request';

const BASE = '/mrmsCaseRecordCoding';

export async function searchIcd10Api(params: SearchIcdParams) {
  return publicRequestClient.post<IcdItemDto[]>(`${BASE}/searchIcd10`, params);
}

/** 手术选码：关键字可空，打开下拉即可分页浏览 */
export async function searchIcd9Cm3Api(params: SearchIcdParams) {
  return publicRequestClient.post<SearchIcdPageResult>(`${BASE}/searchIcd9Cm3`, params);
}
