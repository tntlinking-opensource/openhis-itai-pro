import type { ApiResponse, PageApiResponse, PageParams } from '#/types/api-response';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { publicRequestClient } from '../request';

/** 22=LIS检验 / 23=PACS检查 */
export const LIS_PACS_CATEGORY = {
  LIS: '22',
  PACS: '23',
} as const;

export interface LisPacsItemMappingItem {
  id?: string;
  activityId?: string;
  activityBusNo?: string;
  activityName?: string;
  categoryEnum?: string;
  categoryLabel?: string;
  externalCode?: string;
  externalName?: string;
  activeFlag?: boolean;
}

export interface UnmappedActivityItem {
  activityId?: string;
  activityBusNo?: string;
  activityName?: string;
  categoryEnum?: string;
  categoryLabel?: string;
  /** 从「院内旧编码=」解析的建议外发码 */
  suggestedExternalCode?: string;
  /** 前端可编辑外送码（未提交） */
  draftExternalCode?: string;
}

export interface PageLisPacsItemMappingParams extends PageParams {
  categoryEnum?: string;
  keyword?: string;
  activeFlag?: boolean | null;
}

export interface PageUnmappedActivityParams extends PageParams {
  categoryEnum?: string;
  keyword?: string;
}

export interface SaveLisPacsItemMappingParams {
  id?: string;
  activityId: string;
  externalCode: string;
  externalName?: string;
  activeFlag?: boolean;
}

export interface BatchSaveLisPacsItemMappingParams {
  items: SaveLisPacsItemMappingParams[];
}

export interface BatchSaveLisPacsItemMappingResult {
  succeeded?: number;
  failed?: number;
  errors?: string[];
}

/** 院点 AppService 前缀随 VITE_ZXPLAT_PROJECT 变化（默认朝阳，兼容产品主线） */
const SITE =
  String(import.meta.env.VITE_ZXPLAT_PROJECT ?? 'H22010402403').trim() || 'H22010402403';
const BASE = `/${SITE.toLowerCase()}LisPacsItemMapping`;

/** 已对码列表分页 */
export async function pageLisPacsItemMappingApi(params: PageLisPacsItemMappingParams) {
  return publicRequestClient.post<ApiResponse<PageApiResponse<LisPacsItemMappingItem>>>(
    `${BASE}/page`,
    params,
  );
}

/** 未对码诊疗项分页 */
export async function pageUnmappedActivityApi(params: PageUnmappedActivityParams) {
  return publicRequestClient.post<ApiResponse<PageApiResponse<UnmappedActivityItem>>>(
    `${BASE}/pageUnmappedActivities`,
    params,
  );
}

/** 新建/更新对照 */
export async function saveLisPacsItemMappingApi(params: SaveLisPacsItemMappingParams) {
  return publicRequestClient.post<ApiResponse<string>>(`${BASE}/save`, params);
}

/** 批量对码 */
export async function batchSaveLisPacsItemMappingApi(params: BatchSaveLisPacsItemMappingParams) {
  return publicRequestClient.post<ApiResponse<BatchSaveLisPacsItemMappingResult>>(
    `${BASE}/batchSave`,
    params,
  );
}

/** 逻辑删除 */
export async function deleteLisPacsItemMappingApi(id: number | string) {
  return publicRequestClient.post<ApiResponse<null>>(`${BASE}/delete`, {
    value: preserveApiIdForRequest(id),
  });
}

/** 启用 */
export async function activateLisPacsItemMappingApi(id: number | string) {
  return publicRequestClient.post<ApiResponse<null>>(`${BASE}/activate`, {
    value: preserveApiIdForRequest(id),
  });
}

/** 停用 */
export async function deactivateLisPacsItemMappingApi(id: number | string) {
  return publicRequestClient.post<ApiResponse<null>>(`${BASE}/deactivate`, {
    value: preserveApiIdForRequest(id),
  });
}
