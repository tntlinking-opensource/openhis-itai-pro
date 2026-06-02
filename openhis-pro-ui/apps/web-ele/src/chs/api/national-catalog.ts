/**
 * 国家医保目录 — 对接 {@code POST /api/app/insuranceNationalCatalog/<method>}
 */
import type { Catalog1312FilterParams } from '#/chs/constants/catalog-1312-filter';
import type { ApiResponse } from '#/types/api-response';
import type { PageParams } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/insuranceNationalCatalog';

export interface QueryNationalCatalogParams extends PageParams, Catalog1312FilterParams {
  /** 契约业务编码（fin_contract.bus_no），与 chs-contract 配置键一致 */
  contractBusNo: string;
  /** 本地无结果时是否调 1312 补库 */
  queryPlatformIfMiss?: boolean;
}

export interface NationalCatalogItem {
  id?: number | string;
  contractId?: number | string;
  listType?: string;
  hilistCode?: string;
  hilistName?: string;
  chrgitmLv?: string;
  lmtUsedFlag?: string;
  begndate?: string;
  enddate?: string;
  valiFlag?: string;
  pinyin?: string;
  sourceInfno?: string;
  updtTime?: string;
}

export interface Refresh1312Params extends Catalog1312FilterParams {
  contractBusNo: string;
  pageNum?: number;
  pageSize?: number;
}

export interface CatalogDownloadCursor {
  id?: number | string;
  contractId?: number | string;
  infno?: string;
  localVer?: string;
  lastFileQuryNo?: string;
  lastFilename?: string;
  lastSuccessAt?: string;
}

export async function queryNationalCatalogPageApi(params: QueryNationalCatalogParams) {
  return requestClient.post<ApiResponse<unknown>>(`${BASE}/queryNationalCatalogPage`, params);
}

/** 分页查询国家目录（解析统一分页块） */
export async function pageNationalCatalogApi(params: QueryNationalCatalogParams) {
  const res = await queryNationalCatalogPageApi(params);
  return parsePagedRowsFromApiData<NationalCatalogItem>(res.data);
}

export async function refreshFromPlatform1312Api(params: Refresh1312Params): Promise<number> {
  const res = await requestClient.post<ApiResponse<number>>(
    `${BASE}/refreshFromPlatform1312`,
    params,
  );
  return Number(res.data ?? 0);
}

export async function listDownloadCursorsApi(
  contractBusNo: string,
): Promise<CatalogDownloadCursor[]> {
  const res = await requestClient.post<ApiResponse<CatalogDownloadCursor[]>>(
    `${BASE}/listDownloadCursors`,
    { contractBusNo },
  );
  return res.data ?? [];
}
