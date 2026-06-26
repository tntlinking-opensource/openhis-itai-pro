/**
 * 病案编码 API（MrmsCaseRecordCodingAppService · ICD Picker）
 */

import type { IcdItemDto, SearchIcdParams } from './types';

import { publicRequestClient } from '#/api/request';

const BASE = '/mrmsCaseRecordCoding';

export async function searchIcd10Api(params: SearchIcdParams) {
  return publicRequestClient.post<IcdItemDto[]>(`${BASE}/searchIcd10`, params);
}

export async function searchIcd9Cm3Api(params: SearchIcdParams) {
  return publicRequestClient.post<IcdItemDto[]>(`${BASE}/searchIcd9Cm3`, params);
}
