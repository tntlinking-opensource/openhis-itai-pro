/**
 * 国家医保目录管理 — {@code POST /api/app/insuranceNationalCatalog/<method>}
 */
import type { Catalog1312FilterParams } from '#/chs/constants/catalog-1312-filter';
import type { ApiResponse } from '#/types/api-response';
import type { PageParams } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/insuranceNationalCatalog';

export interface QueryNationalCatalogParams extends PageParams, Catalog1312FilterParams {
  contractBusNo: string;
  queryPlatformIfMiss?: boolean;
  sourceInfno?: string;
  dedupeByHilistCode?: boolean;
  aprvno?: string;
  spec?: string;
  /** 选码：排除平台已过期 */
  excludePlatformExpired?: boolean;
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
  spec?: string;
  dosform?: string;
  aprvno?: string;
  prodentpName?: string;
  /** 镜像 ⋈ overlay 合成后是否可选 */
  isSelectable?: boolean;
  /** 不可选原因 */
  unselectableReason?: string;
  noticeAvaliFlag?: string;
  noticeEndDate?: string;
  /** 局文限价上限 */
  priceUplmtAmt?: string;
  /** 1301 最小包装数量 */
  minPacCnt?: string;
  /** 1301 最小包装单位 */
  minPacunt?: string;
  /** 1301 最小制剂单位 */
  minPrepunt?: string;
  /** 1301 最小计价单位（限价口径） */
  minPrcunt?: string;
}

export interface Refresh1312Params extends Catalog1312FilterParams {
  contractBusNo: string;
  pageNum?: number;
  pageSize?: number;
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
    chsLongRequestConfig(),
  );
  return Number(res.data ?? 0);
}
