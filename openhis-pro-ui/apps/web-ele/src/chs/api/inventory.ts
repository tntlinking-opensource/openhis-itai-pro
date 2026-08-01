/**
 * 医保进销存运维 — 对接 {@code POST /api/app/insuranceInventory/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';

const BASE = '/insuranceInventory';

export interface InventoryContractRequest {
  contractBusNo: string;
  contractId?: number | string;
}

export interface LocalInvUploadRecord {
  id?: number | string;
  contractBusNo?: string;
  infno?: string;
  fixmedinsBchno?: string;
  medListCodg?: string;
  sourceType?: string;
  sourceId?: number | string;
  sourceLineId?: number | string;
  uploadStatus?: string;
  platformRetRslt?: string;
  platformMsg?: string;
  requestJson?: string;
  responseJson?: string;
  retryCount?: number;
  createdAt?: string;
}

export interface ListLocalInvUploadRecordsParams {
  /** 医保契约 busNo；与后端 AND 筛选对齐 */
  contractBusNo?: string;
  infno?: string;
  sourceType?: string;
  sourceId?: number | string;
  fixmedinsBchno?: string;
  uploadStatus?: string;
  /** 创建时间起，yyyy-MM-dd HH:mm:ss */
  createdFrom?: string;
  /** 创建时间止，yyyy-MM-dd HH:mm:ss */
  createdTo?: string;
}

export interface QueryPlatformInvParams extends InventoryContractRequest {
  medinsListCodg?: string;
  fixmedinsBchno?: string;
  begndate?: string;
  enddate?: string;
  medListCodg?: string;
  medinsProdSelNo?: string;
  medinsProdPurcNo?: string;
  medinsProdInvNo?: string;
  psnNo?: string;
  certno?: string;
  mdtrtId?: string;
  drugTracCodg?: string;
  valiFlag?: string;
}

export interface PlatformInvQueryResult {
  infno?: string;
  rows?: Array<Record<string, unknown>>;
}

export async function listLocalInvUploadRecordsApi(
  params: ListLocalInvUploadRecordsParams,
): Promise<LocalInvUploadRecord[]> {
  const res = await requestClient.post<ApiResponse<LocalInvUploadRecord[]>>(
    `${BASE}/listLocalUploadRecords`,
    params,
  );
  return res.data ?? [];
}

/** 按流水 id 拉详情（含完整 request/response，补传弹窗回显用） */
export async function getLocalInvUploadRecordApi(
  uploadId: number | string,
): Promise<LocalInvUploadRecord | null> {
  const res = await requestClient.post<ApiResponse<LocalInvUploadRecord | null>>(
    `${BASE}/getLocalUploadRecord`,
    { uploadId },
  );
  return res.data ?? null;
}

export async function retryPendingInvUploadsApi(params?: {
  batchSize?: number;
}): Promise<Record<string, number>> {
  const res = await requestClient.post<ApiResponse<Record<string, number>>>(
    `${BASE}/retryPendingUploads`,
    params ?? {},
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

export interface RetryUploadWithRequestParams {
  uploadId: number | string;
  requestJson?: string;
}

export interface RetryUploadWithRequestResult {
  succeeded?: boolean;
  uploadId?: number | string;
  uploadStatus?: string;
  platformMsg?: string;
  responseJson?: string;
}

export async function retryUploadWithRequestApi(
  params: RetryUploadWithRequestParams,
): Promise<RetryUploadWithRequestResult> {
  const res = await requestClient.post<ApiResponse<RetryUploadWithRequestResult>>(
    `${BASE}/retryUploadWithRequest`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

async function queryPlatformInvApi(
  method: string,
  params: QueryPlatformInvParams,
): Promise<PlatformInvQueryResult> {
  const res = await requestClient.post<ApiResponse<PlatformInvQueryResult>>(
    `${BASE}/${method}`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? { rows: [] };
}

export const queryPlatformInventory3508Api = (p: QueryPlatformInvParams) =>
  queryPlatformInvApi('queryPlatformInventory3508', p);

export const queryPlatformInvChange3509Api = (p: QueryPlatformInvParams) =>
  queryPlatformInvApi('queryPlatformInvChange3509', p);

export const queryPlatformPurchase3510Api = (p: QueryPlatformInvParams) =>
  queryPlatformInvApi('queryPlatformPurchase3510', p);

export const queryPlatformSale3511Api = (p: QueryPlatformInvParams) =>
  queryPlatformInvApi('queryPlatformSale3511', p);

export const queryPlatformInboundTrace3512Api = (p: QueryPlatformInvParams) =>
  queryPlatformInvApi('queryPlatformInboundTrace3512', p);

export const queryPlatformSaleTrace3513Api = (p: QueryPlatformInvParams) =>
  queryPlatformInvApi('queryPlatformSaleTrace3513', p);

export interface UploadStocktake3501Params extends InventoryContractRequest {
  remark?: string;
  lines: Array<{
    medListCodg: string;
    fixmedinsHilistId?: string;
    fixmedinsHilistName?: string;
    fixmedinsBchno: string;
    invdate: string;
    invCnt: number;
    manuLotnum?: string;
    manuDate?: string;
    expyEnd?: string;
    rxFlag?: string;
    memo?: string;
  }>;
}

export interface DeleteInvData3507Params extends InventoryContractRequest {
  fixmedinsBchno: string;
  invDataType: '1' | '2' | '3' | '4';
  sourceId?: number | string;
}

export interface InvUpload3501Result {
  uploadId?: number | string;
  uploadStatus?: string;
  fixmedinsBchno?: string;
  platformMsg?: string;
}

export interface InvDelete3507Result {
  uploadId?: number | string;
  uploadStatus?: string;
  fixmedinsBchno?: string;
  invDataType?: string;
  platformMsg?: string;
}

export async function uploadStocktake3501Api(params: UploadStocktake3501Params) {
  const res = await requestClient.post<ApiResponse<InvUpload3501Result[]>>(
    `${BASE}/uploadStocktake3501`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? [];
}

export interface UploadInvChange3502Params extends InventoryContractRequest {
  remark?: string;
  lines: Array<{
    medListCodg: string;
    invChgType: string;
    fixmedinsHilistId?: string;
    fixmedinsHilistName?: string;
    fixmedinsBchno: string;
    cnt: number;
    pric?: number;
    invChgTime: string;
    invChgOpterName?: string;
    rxFlag?: string;
    trdnFlag?: string;
    memo?: string;
  }>;
}

export interface InvUpload3502Result {
  uploadId?: number | string;
  uploadStatus?: string;
  fixmedinsBchno?: string;
  invChgType?: string;
  platformMsg?: string;
}

export async function uploadInvChange3502Api(params: UploadInvChange3502Params) {
  const res = await requestClient.post<ApiResponse<InvUpload3502Result[]>>(
    `${BASE}/uploadInvChange3502`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? [];
}

export interface InitializeInventoryParams extends InventoryContractRequest {
  locationId: number | string;
  remark?: string;
  maxLines?: number;
  pageNo?: number;
  skipInitialized?: boolean;
}

export interface InitializeInventoryLineResult {
  inventoryItemId?: number | string;
  fixmedinsBchno?: string;
  medListCodg?: string;
  upload3501Status?: string;
  upload3502Status?: string;
  platformMsg?: string;
}

export interface InitializeInventoryResult {
  attemptedPairs?: number;
  succeededPairs?: number;
  failedPairs?: number;
  skippedNoYbNo?: number;
  skippedAlreadyInitialized?: number;
  pageNo?: number;
  nextPageNo?: number;
  hasMore?: boolean;
  totalElements?: number;
  totalPages?: number;
  lines?: InitializeInventoryLineResult[];
}

export async function initializeInventoryApi(params: InitializeInventoryParams) {
  const res = await requestClient.post<ApiResponse<InitializeInventoryResult>>(
    `${BASE}/initializeInventory`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

export async function deleteInvData3507Api(params: DeleteInvData3507Params) {
  const res = await requestClient.post<ApiResponse<InvDelete3507Result>>(
    `${BASE}/deleteInvData3507`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

export interface InvCompareRow {
  diffType?: string;
  compareKey?: string;
  fixmedinsBchno?: string;
  medListCodg?: string;
  drugTracCodg?: string;
  localUploadId?: number | string;
  localUploadStatus?: string;
  localCreatedAt?: string;
  platformRow?: Record<string, unknown>;
}

export interface CompareLocalVsPlatformParams extends QueryPlatformInvParams {
  compareQueryInfno: '3508' | '3509' | '3510' | '3511' | '3512' | '3513';
}

export interface CompareLocalVsPlatformResult {
  compareQueryInfno?: string;
  localUploadInfno?: string;
  platformRowCount?: number;
  localRowCount?: number;
  matchCount?: number;
  localOnlyCount?: number;
  platformOnlyCount?: number;
  rows?: InvCompareRow[];
}

export async function compareLocalVsPlatformApi(params: CompareLocalVsPlatformParams) {
  const res = await requestClient.post<ApiResponse<CompareLocalVsPlatformResult>>(
    `${BASE}/compareLocalVsPlatform`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? { rows: [] };
}
