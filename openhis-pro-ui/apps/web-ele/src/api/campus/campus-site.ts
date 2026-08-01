/**
 * 校区 — POST /api/app/campusSite/<method>
 */
import type { CampusPagedResult, IdLike } from './types';

import { publicRequestClient } from '#/api/request';

export interface CampusSiteDto {
  id?: IdLike;
  campusCode?: string;
  campusName?: string;
  shortName?: string;
  address?: string;
  isPrimary?: boolean;
  activeFlag?: boolean;
  sortOrder?: number;
  remark?: string;
}

export function pageCampusSitesApi(params: {
  keyword?: string;
  activeFlag?: boolean | null;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<CampusPagedResult<CampusSiteDto>>(
    '/campusSite/pageCampusSites',
    params,
  );
}

export function createCampusSiteApi(params: {
  campusCode: string;
  campusName: string;
  shortName?: string;
  address?: string;
  isPrimary?: boolean;
  sortOrder?: number;
  remark?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusSite/createCampusSite', params);
}

export function updateCampusSiteApi(params: {
  id: IdLike;
  campusName: string;
  shortName?: string;
  address?: string;
  sortOrder?: number;
  remark?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusSite/updateCampusSite', params);
}

export function changeCampusSiteActiveApi(params: { id: IdLike; activeFlag: boolean }) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusSite/changeCampusSiteActive', params);
}

export function setPrimaryCampusSiteApi(params: { id: IdLike }) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusSite/setPrimaryCampusSite', params);
}

export function listActiveCampusSitesApi(params: Record<string, never> = {}) {
  return publicRequestClient.post<{ records?: CampusSiteDto[] }>(
    '/campusSite/listActiveCampusSites',
    params,
  );
}
