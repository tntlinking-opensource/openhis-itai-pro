/**
 * 目录文件下载 — {@code POST /api/app/insuranceCatalogDownload/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';

const BASE = '/insuranceCatalogDownload';

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

export interface StartCatalogDownloadParams {
  contractBusNo: string;
  contractId?: number | string;
  /** 接口号 infno（1301/1302/1305…），默认 1301 */
  infno?: string;
  maxVersions?: number;
  forceStartFromZero?: boolean;
  clearZipCache?: boolean;
  startLocalVer?: string;
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

export async function listDownloadCursorsApi(
  contractBusNo: string,
): Promise<CatalogDownloadCursor[]> {
  const res = await requestClient.post<ApiResponse<{ rows?: CatalogDownloadCursor[] }>>(
    `${BASE}/listDownloadCursors`,
    { contractBusNo },
  );
  const data = res.data;
  if (Array.isArray(data)) {
    return data;
  }
  return data?.rows ?? [];
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
    { jobId },
  );
  return res.data ?? {};
}
