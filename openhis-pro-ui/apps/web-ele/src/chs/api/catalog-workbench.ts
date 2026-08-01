/**
 * 医保目录对码 Workbench — {@code POST /api/app/insuranceCatalogWorkbench/<method>}
 * <p>含院内待对码分页与平台已对照（1317）镜像查询/同步。</p>
 */
import type { ApiResponse } from '#/types/api-response';
import type { PageParams } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/insuranceCatalogWorkbench';

export type CatalogMappingWorkbenchStatus = 'NOT_ON_PLATFORM' | 'PENDING_UPLOAD' | 'NO_YB_NO';

export interface PendingCatalogMappingRow {
  productTable: string;
  productId: number | string;
  productTypeLabel?: string;
  busNo?: string;
  name?: string;
  ybNo?: string;
  ybMatchFlag?: boolean;
  chrgitmLv?: number;
  listType?: string;
}

export interface PagePendingCatalogMappingParams extends PageParams {
  contractBusNo?: string;
  productTable?: string;
  mappingStatus?: CatalogMappingWorkbenchStatus;
  keyword?: string;
}

export interface PagePendingCatalogMappingResult {
  rows?: PendingCatalogMappingRow[];
  total?: number;
  pageNo?: number;
  pageSize?: number;
  /** 平台无 + 有医保码（可 3301） */
  pendingUploadCount?: number;
  /** 平台无对照总数（待处理） */
  notOnPlatformCount?: number;
  /** @deprecated 兼容字段，同 notOnPlatformCount */
  matchedCount?: number;
  noYbNoCount?: number;
}

export async function pagePendingCatalogMappingsApi(
  params: PagePendingCatalogMappingParams,
): Promise<PagePendingCatalogMappingResult> {
  const res = await requestClient.post<ApiResponse<PagePendingCatalogMappingResult>>(
    `${BASE}/pagePendingMappings`,
    params,
  );
  return (
    res.data ?? {
      rows: [],
      total: 0,
      pageNo: params.pageNo ?? 1,
      pageSize: params.pageSize ?? 20,
    }
  );
}

/** 1317 本地镜像行 */
export interface InstitutionCatalogMatchRow {
  id?: number | string;
  fixmedinsCode?: string;
  medinsListCodg?: string;
  medinsListName?: string;
  listType?: string;
  medListCodg?: string;
  valiFlag?: string;
  begndate?: string;
  enddate?: string;
  aprvno?: string;
  spec?: string;
  dosform?: string;
  memo?: string;
  updtTime?: string;
  /** ACTIVE / INACTIVE / MISSING */
  hospitalMatchStatus?: string;
  hospitalProductTable?: string;
  hospitalProductId?: number | string;
  hospitalStatusEnum?: string;
}

export type HospitalMatchFilter = '' | 'ALL' | 'ACTIVE' | 'UNUSED';

export interface PageInstitutionCatalogMatchParams extends PageParams {
  contractBusNo?: string;
  listType?: string;
  valiFlag?: string;
  keyword?: string;
  /** ALL / ACTIVE / UNUSED（院内停用或无匹配） */
  hospitalMatchFilter?: HospitalMatchFilter;
}

export interface PageInstitutionCatalogMatchResult {
  rows?: InstitutionCatalogMatchRow[];
  total?: number;
  pageNo?: number;
  pageSize?: number;
}

/** 1317 同步结果 */
export interface Refresh1317Result {
  upserted?: number;
  writtenBack?: number;
  skipped?: number;
  pagesFetched?: number;
  /** 平台返回总记录数 recordCounts */
  platformRecordCounts?: number;
  /** 平台返回总页数 pages */
  platformPages?: number;
}

export interface Refresh1317Params {
  contractBusNo?: string;
  listType?: string;
  valiFlag?: string;
  keyword?: string;
  medinsListCodg?: string;
  medListCodg?: string;
  queryDate?: string;
  begndate?: string;
  updtTime?: string;
  platformPageSize?: number;
}

/**
 * 分页查询本地 1317 机构目录匹配镜像。
 */
export async function queryInstitutionCatalogMatchPageApi(
  params: PageInstitutionCatalogMatchParams,
): Promise<PageInstitutionCatalogMatchResult> {
  const res = await requestClient.post<ApiResponse<PageInstitutionCatalogMatchResult>>(
    `${BASE}/queryInstitutionCatalogMatchPage`,
    params,
  );
  return (
    res.data ?? {
      rows: [],
      total: 0,
      pageNo: params.pageNo ?? 1,
      pageSize: params.pageSize ?? 20,
    }
  );
}

/**
 * 从平台同步 1317（落库 + 正向回写院内对码）。
 */
export async function refreshFromPlatform1317Api(
  params: Refresh1317Params,
): Promise<Refresh1317Result> {
  const res = await requestClient.post<ApiResponse<Refresh1317Result>>(
    `${BASE}/refreshFromPlatform1317`,
    params,
  );
  return res.data ?? { upserted: 0, writtenBack: 0, skipped: 0, pagesFetched: 0 };
}

export interface RevokePlatformCatalogMatch3302Params {
  contractBusNo?: string;
  matchId?: number | string;
}

export interface Catalog3302RevokeResultLite {
  allSuccess?: boolean;
  successCount?: number;
  failCount?: number;
  summary?: string;
}

/**
 * 按平台已对照镜像行撤销 3302（含院内停用/孤儿）。
 */
export async function revokePlatformCatalogMatch3302Api(
  params: RevokePlatformCatalogMatch3302Params,
): Promise<Catalog3302RevokeResultLite> {
  const res = await requestClient.post<ApiResponse<Catalog3302RevokeResultLite>>(
    `${BASE}/revokePlatformCatalogMatch3302`,
    params,
  );
  return res.data ?? { allSuccess: false, successCount: 0, failCount: 1 };
}
