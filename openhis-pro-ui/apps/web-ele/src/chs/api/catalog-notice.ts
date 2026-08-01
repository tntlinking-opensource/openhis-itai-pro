/**
 * 医保文件管理 — {@code POST /api/app/insuranceCatalogNotice/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

import { chsLongRequestConfig } from '#/chs/constants/request-timeout';

const BASE = '/insuranceCatalogNotice';

export type NoticeScanStatus = 'NEW' | 'DUPLICATE' | 'FAILED';
export type NoticeScanImportStatus = 'IMPORTED' | 'SKIPPED' | 'FAILED';

export interface NoticeScanItem {
  packDir?: string;
  sourceFilename?: string;
  docKind?: string;
  category?: string;
  title?: string;
  noticeDate?: string;
  lineCount?: number;
  addCount?: number;
  stopCount?: number;
  modifyCount?: number;
  setPayClassCount?: number;
  status?: NoticeScanStatus;
  existingDocId?: number | string;
  message?: string;
  sourceFileHash?: string;
}

export interface ScanNoticeCanonicalDirParams {
  contractId?: number | string;
  scanRoot?: string;
}

export interface ScanNoticeCanonicalDirResult {
  scanToken?: string;
  scanRoot?: string;
  newCount?: number;
  duplicateCount?: number;
  failedCount?: number;
  items?: NoticeScanItem[];
}

export interface StartScanNoticeCanonicalDirResult {
  jobId?: string;
}

export type NoticeScanJobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';

export interface GetScanNoticeCanonicalDirProgressResult {
  jobId?: string;
  status?: NoticeScanJobStatus | string;
  total?: number;
  processed?: number;
  percent?: number;
  currentPackDir?: string;
  message?: string;
  result?: ScanNoticeCanonicalDirResult;
}

export interface ConfirmNoticeScanImportParams {
  scanToken: string;
  packDirs?: string[];
  contractId?: number | string;
  force?: boolean;
}

export interface NoticeScanImportResult {
  packDir?: string;
  docId?: number | string;
  status?: NoticeScanImportStatus;
  message?: string;
}

export interface ConfirmNoticeScanImportResult {
  importedCount?: number;
  skippedCount?: number;
  failedCount?: number;
  results?: NoticeScanImportResult[];
}

export interface StartConfirmNoticeScanImportResult {
  jobId?: string;
}

export type NoticeImportJobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';

export interface GetConfirmNoticeScanImportProgressResult {
  jobId?: string;
  status?: NoticeImportJobStatus | string;
  total?: number;
  processed?: number;
  percent?: number;
  currentPackDir?: string;
  message?: string;
  importedCount?: number;
  skippedCount?: number;
  failedCount?: number;
  remainingCount?: number;
  resumable?: boolean;
  results?: NoticeScanImportResult[];
}

const IMPORT_JOB_STORAGE_KEY = 'chs-notice-import-jobId';

/** 持久化入库任务 ID，刷新页面后可续跑轮询 */
export function saveImportJobId(jobId: string | undefined | null) {
  if (jobId) {
    sessionStorage.setItem(IMPORT_JOB_STORAGE_KEY, String(jobId));
  } else {
    sessionStorage.removeItem(IMPORT_JOB_STORAGE_KEY);
  }
}

export function loadImportJobId(): string | null {
  return sessionStorage.getItem(IMPORT_JOB_STORAGE_KEY);
}

export interface NoticeDocRow {
  id?: number | string;
  docKind?: string;
  category?: string;
  title?: string;
  sourceFilename?: string;
  noticeDate?: string;
  effectiveDate?: string;
  parseStatus?: string;
  applyStatus?: string;
  lineCount?: number;
  importedAt?: string;
}

export interface NoticeLineRow {
  action?: string;
  hilistCode?: string;
  projectCode?: string;
  hilistName?: string;
  beginDate?: string;
  endDate?: string;
  chrgitmLv?: string;
  priceLimitAmt?: number | string;
  sheetName?: string;
  rowNo?: number;
  warning?: string;
  matchStatus?: string;
  applyResult?: string;
}

export interface PageNoticeDocParams {
  page?: number;
  size?: number;
  docKind?: string;
  category?: string;
  applyStatus?: string;
  keyword?: string;
}

export interface PageNoticeLineParams {
  docId: number | string;
  page?: number;
  size?: number;
  action?: string;
  matchStatus?: string;
}

export interface PageResult<T> {
  total: number;
  records: T[];
}

export interface DownloadNoticePackResult {
  fileName?: string;
  contentBase64?: string;
}

/** 应用差异预览样例 */
export interface ApplyPreviewSample {
  action?: string;
  hilistCode?: string;
  projectCode?: string;
  effectSummary?: string;
}

export interface PreviewNoticeApplyParams {
  docId: number | string;
}

export interface PreviewNoticeApplyResult {
  overlayUpsertCount?: number;
  writebackCount?: number;
  unchargedSyncCount?: number;
  samples?: ApplyPreviewSample[];
}

export interface ApplyNoticeDocParams {
  docId: number | string;
  confirm: boolean;
}

export interface StartApplyNoticeDocsParams {
  docIds: Array<number | string>;
  confirm: boolean;
}

export interface ApplyNoticeDocResult {
  appliedLineCount?: number;
  failedLineCount?: number;
  writebackCount?: number;
  syncedUnchargedCount?: number;
}

export interface StartApplyNoticeDocResult {
  jobId?: string;
}

export interface GetApplyNoticeDocProgressResult {
  jobId?: string;
  status?: string;
  total?: number;
  processed?: number;
  percent?: number;
  message?: string;
  currentDocId?: number | string;
  docTotal?: number;
  docProcessed?: number;
  appliedLineCount?: number;
  failedLineCount?: number;
  writebackCount?: number;
  syncedUnchargedCount?: number;
}

export interface ComplianceIssueRow {
  code?: string;
  hilistCode?: string;
  projectCode?: string;
  message?: string;
  severity?: string;
}

export interface GetComplianceReportParams {
  contractId?: number | string;
  listType?: string;
}

export interface GetComplianceReportResult {
  ranAt?: string;
  summaryJson?: string;
  issues?: ComplianceIssueRow[];
}

export interface RerunNoticeComplianceParams {
  contractId?: number | string;
  listType?: string;
}

export interface RerunNoticeComplianceResult {
  reportId?: number | string;
  issueCount?: number;
  ranAt?: string;
}

export interface NoticeTodoSummary {
  stopOpenCount?: number;
  payClassOpenCount?: number;
}

export interface NoticeTodoRow {
  id?: number | string;
  todoType?: string;
  hilistCode?: string;
  projectCode?: string;
  productName?: string;
  effectiveDate?: string;
  docId?: number | string;
  status?: string;
}

export interface PageNoticeTodoParams {
  page?: number;
  size?: number;
  todoType?: string;
  status?: string;
}

export interface PageNoticeTodoResult {
  summary?: NoticeTodoSummary;
  total: number;
  records: NoticeTodoRow[];
}

export interface CloseNoticeTodoParams {
  todoId: number | string;
  closeReason: string;
}

export interface CloseNoticeTodoResult {
  todoId?: number | string;
  status?: string;
}

/** 重新扫描配置目录下标准落地包（同步，兼容保留） */
export async function scanNoticeCanonicalDirApi(
  params: ScanNoticeCanonicalDirParams = {},
): Promise<ScanNoticeCanonicalDirResult> {
  const res = await requestClient.post<ApiResponse<ScanNoticeCanonicalDirResult>>(
    `${BASE}/scanNoticeCanonicalDir`,
    params,
    // 全量标准包扫描常超默认 10s
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

/** 启动异步扫描（推荐；配合进度轮询） */
export async function startScanNoticeCanonicalDirApi(
  params: ScanNoticeCanonicalDirParams = {},
): Promise<StartScanNoticeCanonicalDirResult> {
  const res = await requestClient.post<ApiResponse<StartScanNoticeCanonicalDirResult>>(
    `${BASE}/startScanNoticeCanonicalDir`,
    params,
  );
  return res.data ?? {};
}

/** 轮询异步扫描进度 */
export async function getScanNoticeCanonicalDirProgressApi(
  jobId: string,
): Promise<GetScanNoticeCanonicalDirProgressResult> {
  const res = await requestClient.post<ApiResponse<GetScanNoticeCanonicalDirProgressResult>>(
    `${BASE}/getScanNoticeCanonicalDirProgress`,
    { jobId },
  );
  return res.data ?? {};
}

/** 二次确认入库（同步，兼容保留） */
export async function confirmNoticeScanImportApi(
  params: ConfirmNoticeScanImportParams,
): Promise<ConfirmNoticeScanImportResult> {
  const res = await requestClient.post<ApiResponse<ConfirmNoticeScanImportResult>>(
    `${BASE}/confirmNoticeScanImport`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

/** 启动异步确认入库（推荐） */
export async function startConfirmNoticeScanImportApi(
  params: ConfirmNoticeScanImportParams,
): Promise<StartConfirmNoticeScanImportResult> {
  const res = await requestClient.post<ApiResponse<StartConfirmNoticeScanImportResult>>(
    `${BASE}/startConfirmNoticeScanImport`,
    params,
  );
  return res.data ?? {};
}

/** 轮询确认入库进度 */
export async function getConfirmNoticeScanImportProgressApi(
  jobId: string,
): Promise<GetConfirmNoticeScanImportProgressResult> {
  const res = await requestClient.post<ApiResponse<GetConfirmNoticeScanImportProgressResult>>(
    `${BASE}/getConfirmNoticeScanImportProgress`,
    { jobId },
  );
  return res.data ?? {};
}

/** 续跑中断的入库任务 */
export async function resumeConfirmNoticeScanImportApi(
  jobId: string,
): Promise<StartConfirmNoticeScanImportResult> {
  const res = await requestClient.post<ApiResponse<StartConfirmNoticeScanImportResult>>(
    `${BASE}/resumeConfirmNoticeScanImport`,
    { jobId },
  );
  return res.data ?? {};
}

/** 已入库文件头分页 */
export async function pageNoticeDocsApi(
  params: PageNoticeDocParams,
): Promise<PageResult<NoticeDocRow>> {
  const res = await requestClient.post<ApiResponse<PageResult<NoticeDocRow>>>(
    `${BASE}/pageNoticeDocs`,
    params,
  );
  const data = res.data;
  return {
    total: Number(data?.total ?? 0),
    records: Array.isArray(data?.records) ? data.records : [],
  };
}

/** 文件明细分页 */
export async function pageNoticeLinesApi(
  params: PageNoticeLineParams,
): Promise<PageResult<NoticeLineRow>> {
  const res = await requestClient.post<ApiResponse<PageResult<NoticeLineRow>>>(
    `${BASE}/pageNoticeLines`,
    params,
  );
  const data = res.data;
  return {
    total: Number(data?.total ?? 0),
    records: Array.isArray(data?.records) ? data.records : [],
  };
}

/** 下载已存标准落地包 */
export async function downloadNoticePackApi(
  docId: number | string,
): Promise<DownloadNoticePackResult> {
  const res = await requestClient.post<ApiResponse<DownloadNoticePackResult>>(
    `${BASE}/downloadNoticePack`,
    { docId },
  );
  return res.data ?? {};
}

/** 应用差异预览（只读；大包样例遍历可能超过默认 10s） */
export async function previewNoticeApplyApi(
  params: PreviewNoticeApplyParams,
): Promise<PreviewNoticeApplyResult> {
  const res = await requestClient.post<ApiResponse<PreviewNoticeApplyResult>>(
    `${BASE}/previewNoticeApply`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

/** 二次确认后应用文件（同步，兼容保留） */
export async function applyNoticeDocApi(
  params: ApplyNoticeDocParams,
): Promise<ApplyNoticeDocResult> {
  const res = await requestClient.post<ApiResponse<ApplyNoticeDocResult>>(
    `${BASE}/applyNoticeDoc`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

/** 启动异步应用（推荐；配合进度轮询） */
export async function startApplyNoticeDocApi(
  params: ApplyNoticeDocParams,
): Promise<StartApplyNoticeDocResult> {
  const res = await requestClient.post<ApiResponse<StartApplyNoticeDocResult>>(
    `${BASE}/startApplyNoticeDoc`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

/** 启动多文件串行异步应用（勾选批量） */
export async function startApplyNoticeDocsApi(
  params: StartApplyNoticeDocsParams,
): Promise<StartApplyNoticeDocResult> {
  const res = await requestClient.post<ApiResponse<StartApplyNoticeDocResult>>(
    `${BASE}/startApplyNoticeDocs`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

/** 轮询应用进度 */
export async function getApplyNoticeDocProgressApi(
  jobId: string,
): Promise<GetApplyNoticeDocProgressResult> {
  const res = await requestClient.post<ApiResponse<GetApplyNoticeDocProgressResult>>(
    `${BASE}/getApplyNoticeDocProgress`,
    { jobId },
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

/** 查询最新合规报告 */
export async function getComplianceReportApi(
  params: GetComplianceReportParams = {},
): Promise<GetComplianceReportResult> {
  const res = await requestClient.post<ApiResponse<GetComplianceReportResult>>(
    `${BASE}/getComplianceReport`,
    params,
  );
  return res.data ?? {};
}

/** 手动重跑合规校验（可能超过默认 10s） */
export async function rerunNoticeComplianceApi(
  params: RerunNoticeComplianceParams = {},
): Promise<RerunNoticeComplianceResult> {
  const res = await requestClient.post<ApiResponse<RerunNoticeComplianceResult>>(
    `${BASE}/rerunNoticeCompliance`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

/** 催办汇总与待办分页 */
export async function pageNoticeTodosApi(
  params: PageNoticeTodoParams = {},
): Promise<PageNoticeTodoResult> {
  const res = await requestClient.post<ApiResponse<PageNoticeTodoResult>>(
    `${BASE}/pageNoticeTodos`,
    params,
  );
  const data = res.data;
  return {
    summary: data?.summary ?? {},
    total: Number(data?.total ?? 0),
    records: Array.isArray(data?.records) ? data.records : [],
  };
}

/** 关闭待办（原因必填） */
export async function closeNoticeTodoApi(
  params: CloseNoticeTodoParams,
): Promise<CloseNoticeTodoResult> {
  const res = await requestClient.post<ApiResponse<CloseNoticeTodoResult>>(
    `${BASE}/closeNoticeTodo`,
    params,
  );
  return res.data ?? {};
}
