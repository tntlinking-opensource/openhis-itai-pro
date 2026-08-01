/**
 * 学院 — POST /api/app/campusCollege/<method>
 */
import type { CampusPagedResult, IdLike } from './types';

import { publicRequestClient } from '#/api/request';

export interface CampusCollegeDto {
  id?: IdLike;
  collegeCode?: string;
  collegeName?: string;
  campusSiteId?: IdLike;
  campusCode?: string;
  campusName?: string;
  activeFlag?: boolean;
  sortOrder?: number;
  remark?: string;
}

export function pageCollegesApi(params: {
  keyword?: string;
  campusSiteId?: IdLike | null;
  activeFlag?: boolean | null;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<CampusPagedResult<CampusCollegeDto>>(
    '/campusCollege/pageColleges',
    params,
  );
}

export function createCollegeApi(params: {
  collegeCode: string;
  collegeName: string;
  campusSiteId?: IdLike | null;
  sortOrder?: number;
  remark?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusCollege/createCollege', params);
}

export function updateCollegeApi(params: {
  id: IdLike;
  collegeName: string;
  campusSiteId?: IdLike | null;
  sortOrder?: number;
  remark?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusCollege/updateCollege', params);
}

export function changeCollegeActiveApi(params: { id: IdLike; activeFlag: boolean }) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusCollege/changeCollegeActive', params);
}

export function listActiveCollegesApi(params: { campusCode?: string | null } = {}) {
  return publicRequestClient.post<{ records?: CampusCollegeDto[] }>(
    '/campusCollege/listActiveColleges',
    params,
  );
}
