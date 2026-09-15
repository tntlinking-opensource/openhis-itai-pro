/** Opensource stub — APIs disabled. */
import type { ApiResponse } from '#/types/api-response';

import type {
  MsfxHisSceneEnum,
  MsfxInboundExpandSmallCodesDto,
  MsfxUploadRecordDto,
  MsfxUploadStatusDto,
} from './types';

function disabled<T>(): Promise<ApiResponse<T>> {
  return Promise.reject(new Error('Optional module disabled in opensource build'));
}

export interface GetMsfxUploadStatusRequest {
  busNo: string;
  billType?: string;
  hisSceneEnum?: MsfxHisSceneEnum | string;
}

export interface MsfxManualUploadRequest {
  busNo: string;
  hisSceneEnum: MsfxHisSceneEnum | string;
  supplierEntId?: string;
  supplierRefEntId?: string;
  supplierEntName?: string;
  triggerSourceEnum?: string;
  billTime?: string;
  exemptFlag?: boolean;
  traceCodes?: string[];
  expandLedgerKey?: string;
  uploadTraceCodes?: string[];
  oneLevelOnly?: boolean;
}

export interface MsfxRecordIdRequest {
  recordId: number | string;
  renewBillCode?: boolean;
}

export interface MsfxUploadPageRequest {
  busNo?: string;
  billType?: string;
  uploadStatusEnum?: string;
  pageNo?: number;
  pageSize?: number;
  [key: string]: unknown;
}

export interface MsfxUploadAttemptDto {
  [key: string]: unknown;
}

export interface MsfxPartFailCodeDto {
  [key: string]: unknown;
}

export interface MsfxPartFailDetailDto {
  [key: string]: unknown;
}

export function getMsfxUploadStatusApi(_data: GetMsfxUploadStatusRequest) {
  return disabled<MsfxUploadStatusDto | null>();
}

export function manualMsfxUploadApi(_data: MsfxManualUploadRequest) {
  return disabled<MsfxUploadRecordDto>();
}

export function refreshMsfxBillStatusApi(_data: MsfxRecordIdRequest) {
  return disabled<MsfxUploadRecordDto>();
}

export function retryMsfxUploadApi(_data: MsfxRecordIdRequest) {
  return disabled<MsfxUploadRecordDto>();
}

export function uploadAndExpandInboundSmallCodesApi(_data: MsfxManualUploadRequest) {
  return disabled<MsfxInboundExpandSmallCodesDto>();
}

export function pageMsfxUploadRecordsApi(_data: MsfxUploadPageRequest) {
  return disabled<{ records?: MsfxUploadRecordDto[]; total?: number }>();
}

export function listMsfxUploadAttemptsApi(_data: MsfxRecordIdRequest) {
  return disabled<MsfxUploadAttemptDto[]>();
}

export function queryMsfxPartFailDetailApi(_data: MsfxRecordIdRequest) {
  return disabled<MsfxPartFailDetailDto>();
}
