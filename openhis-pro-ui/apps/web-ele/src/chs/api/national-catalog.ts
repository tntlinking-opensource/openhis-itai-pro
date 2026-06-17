/**
 * 国家医保目录 — 对接 {@code POST /api/app/insuranceNationalCatalog/<method>}
 */
import type { Catalog1312FilterParams } from '#/chs/constants/catalog-1312-filter';
import type { ApiResponse } from '#/types/api-response';
import type { PageParams } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/insuranceNationalCatalog';

export interface QueryNationalCatalogParams extends PageParams, Catalog1312FilterParams {
  /** 契约业务编码（fin_contract.bus_no），与 chs-contract 配置键一致 */
  contractBusNo: string;
  /** 本地无结果时是否调 1312 补库 */
  queryPlatformIfMiss?: boolean;
  /** 来源过滤，选码默认 1312 */
  sourceInfno?: string;
  /** 按 hilist_code 去重（选码列表） */
  dedupeByHilistCode?: boolean;
  aprvno?: string;
  spec?: string;
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
}

export interface SuggestMedicationCatalogParams {
  contractBusNo: string;
  listType?: string;
  name?: string;
  approvalNumber?: string;
  nationalDrugCode?: string;
  totalVolume?: string;
  doseFormCode?: string;
  queryPlatformIfMiss?: boolean;
  maxCandidates?: number;
}

export type CatalogRecommendLevel = 'UNIQUE_SUGGEST' | 'MULTI_CHOICE' | 'NO_MATCH';

export interface CatalogMatchCandidate extends NationalCatalogItem {
  matchScore?: number;
  matchReason?: string;
}

export interface MedicationCatalogMappingSuggest {
  recommendLevel?: CatalogRecommendLevel;
  hint?: string;
  candidates?: CatalogMatchCandidate[];
}

export interface Catalog3301UploadRowResult {
  productId?: number | string;
  productTable?: string;
  fixmedinsHilistId?: string;
  success?: boolean;
  errorMsg?: string;
}

export interface Catalog3301UploadResult {
  allSuccess?: boolean;
  successCount?: number;
  failCount?: number;
  summary?: string;
  rows?: Catalog3301UploadRowResult[];
}

export interface Catalog3302RevokeRowResult {
  productId?: number | string;
  productTable?: string;
  fixmedinsHilistId?: string;
  success?: boolean;
  errorMsg?: string;
}

export interface Catalog3302RevokeResult {
  allSuccess?: boolean;
  successCount?: number;
  failCount?: number;
  summary?: string;
  rows?: Catalog3302RevokeRowResult[];
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
  infnoLabel?: string;
  localVerShort?: string;
  syncStatusText?: string;
  lastFilenameShort?: string;
  lastZipCached?: boolean;
  localCatalogRowCount?: number;
}

export interface CatalogDownloadVersionItem {
  index?: number;
  requestVer?: string;
  requestVerShort?: string;
  txtVersionName?: string;
  txtVersionNameShort?: string;
  nextVersion?: string;
  nextVersionShort?: string;
  nextDownloadVer?: string;
  nextDownloadVerShort?: string;
  parsedRowCount?: number;
  fileQuryNo?: string;
  filename?: string;
  dataCnt?: number;
  zipFromCache?: boolean;
}

export interface CatalogDownloadPhaseSummary {
  phaseNo?: number;
  phaseKey?: string;
  title?: string;
  detail?: string;
  metric?: number;
}

export interface CatalogDownloadPreview {
  infno?: string;
  startLocalVer?: string;
  startLocalVerShort?: string;
  pendingVersionCount?: number;
  hasMore?: boolean;
  versions?: CatalogDownloadVersionItem[];
}

export async function queryNationalCatalogPageApi(params: QueryNationalCatalogParams) {
  return requestClient.post<ApiResponse<unknown>>(`${BASE}/queryNationalCatalogPage`, params);
}

/** 分页查询国家目录（解析统一分页块） */
export async function pageNationalCatalogApi(params: QueryNationalCatalogParams) {
  const res = await queryNationalCatalogPageApi(params);
  return parsePagedRowsFromApiData<NationalCatalogItem>(res.data);
}

export async function suggestMedicationCatalogMappingApi(
  params: SuggestMedicationCatalogParams,
): Promise<MedicationCatalogMappingSuggest> {
  const res = await requestClient.post<ApiResponse<MedicationCatalogMappingSuggest>>(
    `${BASE}/suggestMedicationCatalogMapping`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? { recommendLevel: 'NO_MATCH', candidates: [] };
}

export async function refreshFromPlatform1312Api(params: Refresh1312Params): Promise<number> {
  const res = await requestClient.post<ApiResponse<number>>(
    `${BASE}/refreshFromPlatform1312`,
    params,
    chsLongRequestConfig(),
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

export interface StartCatalogDownloadParams {
  contractBusNo: string;
  contractId?: number | string;
  /** 接口号 infno（1301/1302/1305…），默认 1301 */
  infno?: string;
  /** 单次请求最多连续下载版本数 */
  maxVersions?: number;
  /** 忽略游标，从规范初始 ver 全量拉取 */
  forceStartFromZero?: boolean;
  /** true 时清空本地 ZIP 缓存后再拉（默认 false，已下载 ZIP 复用） */
  clearZipCache?: boolean;
  /** 显式指定 data.ver 起点 */
  startLocalVer?: string;
  /** 1305 版本分支 F002 / F003 */
  verBranch?: string;
}

export interface CatalogDownloadResult {
  infno?: string;
  upsertCount?: number;
  versionBatches?: number;
  totalVersionCount?: number;
  versions?: CatalogDownloadVersionItem[];
  localVer?: string;
  latestCursorId?: number | string;
  cursorSnapshotCount?: number;
  phaseSummaries?: CatalogDownloadPhaseSummary[];
  hasMore?: boolean;
  lastFileQuryNo?: string;
  lastFilename?: string;
}

export interface CatalogDownloadJobStart {
  jobId?: string;
  infno?: string;
  totalVersions?: number;
  message?: string;
}

export interface CatalogDownloadJobProgress {
  jobId?: string;
  phase?: string;
  totalVersions?: number;
  processedVersions?: number;
  parsedRows?: number;
  upsertedRows?: number;
  percent?: number;
  detailMessage?: string;
  finished?: boolean;
  success?: boolean;
  result?: CatalogDownloadResult;
  errorMessage?: string;
}

export async function previewCatalogDownloadApi(
  params: StartCatalogDownloadParams,
): Promise<CatalogDownloadPreview> {
  const res = await requestClient.post<ApiResponse<CatalogDownloadPreview>>(
    `${BASE}/previewCatalogDownload`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

export async function startCatalogDownloadApi(
  params: StartCatalogDownloadParams,
): Promise<CatalogDownloadJobStart> {
  const res = await requestClient.post<ApiResponse<CatalogDownloadJobStart>>(
    `${BASE}/startCatalogDownload`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

export async function getCatalogDownloadJobProgressApi(
  jobId: string,
): Promise<CatalogDownloadJobProgress> {
  const res = await requestClient.post<ApiResponse<CatalogDownloadJobProgress>>(
    `${BASE}/getCatalogDownloadJobProgress`,
    { value: jobId },
  );
  return res.data ?? {};
}
