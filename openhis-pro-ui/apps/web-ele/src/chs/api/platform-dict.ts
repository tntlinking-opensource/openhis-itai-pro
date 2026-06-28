/**
 * 1901 平台字典 — 对接 {@code POST /api/app/insurancePlatformDict/<method>}
 */
import type { ApiResponse } from '#/types/api-response';
import type { PageParams } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/insurancePlatformDict';

export interface SyncPlatformDictFrom1901Params {
  contractBusNo?: string;
  contractId?: number | string;
  /** 1901 type，默认 med_type（基线规范字典表） */
  dictType?: string;
  /** 1901 admdvs；缺省取契约 adm_vs */
  queryAdmdvs?: string;
  /** 根 parentValue，默认空串 */
  parentValue?: string;
  /** BFS 子级，默认 true */
  recursive?: boolean;
  pageSize?: number;
}

export interface PlatformDictSyncResult {
  dictType?: string;
  queryAdmdvs?: string;
  rowsUpserted?: number;
  status?: string;
  finishedAt?: string;
}

export interface PlatformDictSyncLog {
  dictType?: string;
  queryAdmdvs?: string;
  rowsUpserted?: number;
  status?: string;
  errorMessage?: string;
  startedAt?: string;
  finishedAt?: string;
}

export interface LookupPlatformDictLabelsParams {
  dictType?: string;
  dictValues?: string[];
}

export interface PlatformDictLabelsResult {
  dictType?: string;
  labelsByValue?: Record<string, string>;
}

export interface QueryPlatformDictPageParams extends PageParams {
  dictType: string;
  /** 区划码或地名关键字 */
  keyword?: string;
}

export interface PlatformDictItem {
  id?: number | string;
  dictType?: string;
  dictValue?: string;
  dictLabel?: string;
  parentValue?: string;
  sortOrder?: number;
  valiFlag?: string;
  queryAdmdvs?: string;
  syncedAt?: string;
}

export async function syncPlatformDictFrom1901Api(
  params: SyncPlatformDictFrom1901Params,
): Promise<PlatformDictSyncResult> {
  const res = await requestClient.post<ApiResponse<PlatformDictSyncResult>>(
    `${BASE}/syncFrom1901`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

export async function getPlatformDictLastSyncLogApi(
  dictType: string,
): Promise<PlatformDictSyncLog | null> {
  const res = await requestClient.post<ApiResponse<PlatformDictSyncLog | null>>(
    `${BASE}/getLastSyncLog`,
    { dictType },
  );
  return res.data ?? null;
}

export async function lookupPlatformDictLabelsApi(
  params: LookupPlatformDictLabelsParams,
): Promise<PlatformDictLabelsResult> {
  const res = await requestClient.post<ApiResponse<PlatformDictLabelsResult>>(
    `${BASE}/lookupDictLabels`,
    params,
  );
  return res.data ?? { labelsByValue: {} };
}

export async function queryPlatformDictPageApi(params: QueryPlatformDictPageParams) {
  return requestClient.post<ApiResponse<unknown>>(`${BASE}/queryPlatformDictPage`, params);
}

export async function pagePlatformDictApi(params: QueryPlatformDictPageParams) {
  const res = await queryPlatformDictPageApi(params);
  return parsePagedRowsFromApiData<PlatformDictItem>(res.data);
}
