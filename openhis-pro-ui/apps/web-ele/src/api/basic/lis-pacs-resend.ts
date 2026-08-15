import type { ApiResponse, PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '../request';

export interface FailedDispatchItem {
  serviceRequestId?: string;
  lis?: boolean;
  systemLabel?: string;
  failCount?: number;
  lastError?: string;
  updatedAt?: string;
  prescriptionNo?: string;
  activityId?: string;
  activityBusNo?: string;
  activityName?: string;
  encounterBusNo?: string;
  patientName?: string;
}

export interface PageFailedDispatchParams extends PageParams {
  categoryEnum?: string;
}

export interface ResendOneParams {
  serviceRequestId: string;
  lis: boolean;
}

export interface ResendResult {
  succeeded?: number;
  failed?: number;
  errors?: string[];
}

const SITE =
  String(import.meta.env.VITE_OPENHIS_PROJECT ?? 'H22010402403').trim() || 'H22010402403';
const BASE = `/${SITE.toLowerCase()}MedSyncResend`;

export async function pageFailedDispatchApi(params: PageFailedDispatchParams) {
  return publicRequestClient.post<ApiResponse<PageApiResponse<FailedDispatchItem>>>(
    `${BASE}/pageFailed`,
    params,
  );
}

export async function resendOneApi(params: ResendOneParams) {
  return publicRequestClient.post<ApiResponse<ResendResult>>(`${BASE}/resendOne`, params);
}

export async function resendBatchApi(items: ResendOneParams[]) {
  return publicRequestClient.post<ApiResponse<ResendResult>>(`${BASE}/resendBatch`, { items });
}
