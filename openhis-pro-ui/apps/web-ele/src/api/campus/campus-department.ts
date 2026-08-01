/**
 * 部门 — POST /api/app/campusDepartment/<method>
 */
import type { CampusPagedResult, IdLike } from './types';

import { publicRequestClient } from '#/api/request';

export interface CampusDepartmentDto {
  id?: IdLike;
  departmentCode?: string;
  departmentName?: string;
  campusSiteId?: IdLike;
  collegeId?: IdLike;
  deptTypeEnum?: string;
  activeFlag?: boolean;
  sortOrder?: number;
  remark?: string;
}

export function pageDepartmentsApi(params: {
  keyword?: string;
  campusSiteId?: IdLike | null;
  collegeId?: IdLike | null;
  activeFlag?: boolean | null;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<CampusPagedResult<CampusDepartmentDto>>(
    '/campusDepartment/pageDepartments',
    params,
  );
}

export function createDepartmentApi(params: {
  departmentCode: string;
  departmentName: string;
  campusSiteId: IdLike;
  collegeId?: IdLike | null;
  deptTypeEnum?: string;
  sortOrder?: number;
  remark?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusDepartment/createDepartment', params);
}

export function updateDepartmentApi(params: {
  id: IdLike;
  departmentName: string;
  campusSiteId: IdLike;
  collegeId?: IdLike | null;
  deptTypeEnum?: string;
  sortOrder?: number;
  remark?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusDepartment/updateDepartment', params);
}

export function changeDepartmentActiveApi(params: { id: IdLike; activeFlag: boolean }) {
  return publicRequestClient.post<{ id?: IdLike }>(
    '/campusDepartment/changeDepartmentActive',
    params,
  );
}

export function listDepartmentsApi(
  params: {
    campusSiteId?: IdLike | null;
    collegeId?: IdLike | null;
  } = {},
) {
  return publicRequestClient.post<{ records?: CampusDepartmentDto[] }>(
    '/campusDepartment/listDepartments',
    params,
  );
}
