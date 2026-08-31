/**
 * 码上放心上传工作台 — {@code POST /api/app/msfxUploadWorkbench/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import type {
  MsfxHisSceneEnum,
  MsfxInboundExpandSmallCodesDto,
  MsfxUploadRecordDto,
  MsfxUploadStatusDto,
} from './types';

import { requestClient } from '#/api/request';

const BASE = '/msfxUploadWorkbench';

export interface GetMsfxUploadStatusRequest {
  busNo: string;
  billType?: string;
  hisSceneEnum?: MsfxHisSceneEnum | string;
}

export interface MsfxManualUploadRequest {
  busNo: string;
  hisSceneEnum: MsfxHisSceneEnum | string;
  supplierEntId?: string;
  /** listupout 常为 ref 形数字，配合名称纠偏为 ent_id */
  supplierRefEntId?: string;
  supplierEntName?: string;
  triggerSourceEnum?: string;
  billTime?: string;
  exemptFlag?: boolean;
  /** 显式追溯码（确认窗拉取小码时传中大码） */
  traceCodes?: string[];
  /** 只展开一层（大→中 / 中→小）；默认拉到最小包装 */
  oneLevelOnly?: boolean;
}

export interface MsfxRecordIdRequest {
  recordId: number | string;
  renewBillCode?: boolean;
}

export function getMsfxUploadStatusApi(data: GetMsfxUploadStatusRequest) {
  return requestClient.post<ApiResponse<MsfxUploadStatusDto | null>>(
    `${BASE}/getUploadStatus`,
    data,
  );
}

export function manualMsfxUploadApi(data: MsfxManualUploadRequest) {
  return requestClient.post<ApiResponse<MsfxUploadRecordDto>>(`${BASE}/manualUpload`, data);
}

export function refreshMsfxBillStatusApi(data: MsfxRecordIdRequest) {
  return requestClient.post<ApiResponse<MsfxUploadRecordDto>>(`${BASE}/refreshBillStatus`, data);
}

export function retryMsfxUploadApi(data: MsfxRecordIdRequest) {
  return requestClient.post<ApiResponse<MsfxUploadRecordDto>>(`${BASE}/retryUpload`, data);
}

export function uploadAndExpandInboundSmallCodesApi(data: MsfxManualUploadRequest) {
  return requestClient.post<ApiResponse<MsfxInboundExpandSmallCodesDto>>(
    `${BASE}/uploadAndExpandInboundSmallCodes`,
    data,
    { timeout: 120_000 },
  );
}

export interface MsfxUploadPageRequest {
  busNo?: string;
  billType?: string;
  uploadStatusEnum?: string;
  pageNo?: number;
  pageSize?: number;
  startDate?: string;
  endDate?: string;
}

export interface MsfxUploadAttemptDto {
  id?: number | string;
  recordId?: number | string;
  attemptNo?: number;
  attemptTypeEnum?: string;
  topModel?: string;
  successFlag?: boolean;
  msgCode?: string;
  msgInfo?: string;
  requestSummary?: string;
  responseSummary?: string;
  durationMs?: number;
  createdAt?: string;
}

export interface MsfxPartFailCodeDto {
  traceCode?: string;
  reason?: string;
}

export interface MsfxPartFailDetailDto {
  recordId?: number | string;
  billCode?: string;
  items?: MsfxPartFailCodeDto[];
}

export function pageMsfxUploadRecordsApi(data: MsfxUploadPageRequest) {
  return requestClient.post<ApiResponse<{ content?: MsfxUploadRecordDto[]; totalCount?: number }>>(
    `${BASE}/pageUploadRecords`,
    data,
  );
}

export function listMsfxUploadAttemptsApi(data: MsfxRecordIdRequest) {
  return requestClient.post<ApiResponse<MsfxUploadAttemptDto[]>>(
    `${BASE}/listUploadAttempts`,
    data,
  );
}

export function queryMsfxPartFailDetailApi(data: MsfxRecordIdRequest) {
  return requestClient.post<ApiResponse<MsfxPartFailDetailDto>>(
    `${BASE}/queryPartFailDetail`,
    data,
  );
}
