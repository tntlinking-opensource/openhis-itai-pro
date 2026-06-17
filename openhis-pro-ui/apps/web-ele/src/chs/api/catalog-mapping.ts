/**
 * 医保目录对照 — {@code POST /api/app/insuranceCatalogMapping/<method>}
 */
import type { Catalog3301UploadResult, Catalog3302RevokeResult } from '#/chs/api/national-catalog';
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';

const BASE = '/insuranceCatalogMapping';

export interface UploadMedicationCatalog3301Params {
  contractBusNo: string;
  medicationDefId: number | string;
  listType?: string;
}

export interface RevokeMedicationCatalog3302Params {
  contractBusNo: string;
  medicationDefId: number | string;
  listType?: string;
}

export async function uploadMedicationCatalog3301Api(
  params: UploadMedicationCatalog3301Params,
): Promise<Catalog3301UploadResult> {
  const res = await requestClient.post<ApiResponse<Catalog3301UploadResult>>(
    `${BASE}/uploadMedicationCatalog3301`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? { allSuccess: false, successCount: 0, failCount: 1 };
}

export async function revokeMedicationCatalog3302Api(
  params: RevokeMedicationCatalog3302Params,
): Promise<Catalog3302RevokeResult> {
  const res = await requestClient.post<ApiResponse<Catalog3302RevokeResult>>(
    `${BASE}/revokeMedicationCatalog3302`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? { allSuccess: false, successCount: 0, failCount: 1 };
}

export interface UploadActivityCatalog3301Params {
  contractBusNo: string;
  activityDefId: number | string;
  listType?: string;
}

export interface RevokeActivityCatalog3302Params {
  contractBusNo: string;
  activityDefId: number | string;
  listType?: string;
}

export interface UploadDeviceCatalog3301Params {
  contractBusNo: string;
  deviceDefId: number | string;
  listType?: string;
}

export interface RevokeDeviceCatalog3302Params {
  contractBusNo: string;
  deviceDefId: number | string;
  listType?: string;
}

export async function uploadActivityCatalog3301Api(
  params: UploadActivityCatalog3301Params,
): Promise<Catalog3301UploadResult> {
  const res = await requestClient.post<ApiResponse<Catalog3301UploadResult>>(
    `${BASE}/uploadActivityCatalog3301`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? { allSuccess: false, successCount: 0, failCount: 1 };
}

export async function revokeActivityCatalog3302Api(
  params: RevokeActivityCatalog3302Params,
): Promise<Catalog3302RevokeResult> {
  const res = await requestClient.post<ApiResponse<Catalog3302RevokeResult>>(
    `${BASE}/revokeActivityCatalog3302`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? { allSuccess: false, successCount: 0, failCount: 1 };
}

export async function uploadDeviceCatalog3301Api(
  params: UploadDeviceCatalog3301Params,
): Promise<Catalog3301UploadResult> {
  const res = await requestClient.post<ApiResponse<Catalog3301UploadResult>>(
    `${BASE}/uploadDeviceCatalog3301`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? { allSuccess: false, successCount: 0, failCount: 1 };
}

export async function revokeDeviceCatalog3302Api(
  params: RevokeDeviceCatalog3302Params,
): Promise<Catalog3302RevokeResult> {
  const res = await requestClient.post<ApiResponse<Catalog3302RevokeResult>>(
    `${BASE}/revokeDeviceCatalog3302`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? { allSuccess: false, successCount: 0, failCount: 1 };
}
