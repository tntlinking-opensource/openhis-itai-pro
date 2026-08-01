/**
 * 体检目录配置 — {@code POST /api/app/peisCatalogConfig/<method>}
 */
import { publicRequestClient } from '#/api/request';

export interface PeisPagedResult<T> {
  content?: T[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

export interface SearchCatalogRequest {
  keyword?: string;
  pageNo: number;
  pageSize: number;
  statusEnum?: string;
}

export interface PeisObservationDefDto {
  definitionId?: string | number;
  code?: string;
  display?: string;
  valueTypeEnum?: string;
  categoryCode?: string;
  unitCode?: string;
  statusEnum?: string;
  orgId?: string | number;
}

export interface PeisActivityDto {
  activityId?: string | number;
  code?: string;
  display?: string;
  typeEnum?: string;
  statusEnum?: string;
  observationDefinitionIds?: Array<string | number>;
  orgId?: string | number;
}

export interface PeisPackageDto {
  packageActivityId?: string | number;
  code?: string;
  display?: string;
  statusEnum?: string;
  childActivityIds?: Array<string | number>;
}

/** 雪花主键以 string 提交，避免 JS Number 精度丢失 */
export type IdLike = string | number;

export interface SaveObservationDefRequest {
  definitionId?: IdLike;
  code: string;
  display: string;
  valueTypeEnum: string;
  categoryCode: string;
  unitCode?: string;
  statusEnum: string;
  orgId?: IdLike;
}

export interface SaveActivityRequest {
  activityId?: IdLike;
  display: string;
  typeEnum: string;
  statusEnum: string;
  observationDefinitionIds?: IdLike[];
  orgId?: IdLike;
}

export interface SavePackageRequest {
  packageActivityId?: IdLike;
  display: string;
  statusEnum: string;
  childActivityIds?: IdLike[];
}

export function searchObservationDefApi(params: SearchCatalogRequest) {
  return publicRequestClient.post<PeisPagedResult<PeisObservationDefDto>>(
    '/peisCatalogConfig/searchObservationDef',
    params,
  );
}

export function saveObservationDefApi(params: SaveObservationDefRequest) {
  return publicRequestClient.post<PeisObservationDefDto>(
    '/peisCatalogConfig/saveObservationDef',
    params,
  );
}

export function deleteObservationDefApi(params: { definitionId: IdLike }) {
  return publicRequestClient.post<void>('/peisCatalogConfig/deleteObservationDef', params);
}

export function searchActivityApi(params: SearchCatalogRequest) {
  return publicRequestClient.post<PeisPagedResult<PeisActivityDto>>(
    '/peisCatalogConfig/searchActivity',
    params,
  );
}

export function saveActivityApi(params: SaveActivityRequest) {
  return publicRequestClient.post<PeisActivityDto>('/peisCatalogConfig/saveActivity', params);
}

export function searchPackageApi(params: SearchCatalogRequest) {
  return publicRequestClient.post<PeisPagedResult<PeisPackageDto>>(
    '/peisCatalogConfig/searchPackage',
    params,
  );
}

export function savePackageApi(params: SavePackageRequest) {
  return publicRequestClient.post<PeisPackageDto>('/peisCatalogConfig/savePackage', params);
}
