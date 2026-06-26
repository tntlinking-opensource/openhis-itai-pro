import type {
  Department,
  DepartmentParams,
  DepartmentTree,
  InitDepartmentResponse,
  QueryDepartmentListParams,
} from './department.types';

import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

export async function getDepartmentTreeApi(): Promise<DepartmentTree[]> {
  const res = await requestClient.post<DepartmentTree[]>('/department/getTree');
  return unwrapApiList<DepartmentTree>(res);
}

/** 科室管理列表（POST /department/getDepartmentList） */
export async function pageDepartmentApi(
  params?: QueryDepartmentListParams,
): Promise<ApiResponse<Department[]>> {
  return requestClient.post('/department/getDepartmentList', params ?? {});
}

export async function getDepartmentApi(id: number | string): Promise<Department> {
  return requestClient.post<Department>(
    '/department/getDepartmentById',
    preserveApiIdForRequest(id),
  );
}

export async function createDepartmentApi(params: DepartmentParams): Promise<void> {
  return requestClient.post('/department/createDepartment', params);
}

export async function editDepartmentApi(params: DepartmentParams): Promise<void> {
  return requestClient.post('/department/editDepartment', params);
}

export async function initDepartmentApi(): Promise<InitDepartmentResponse> {
  return requestClient.post<InitDepartmentResponse>('/department/init');
}

export async function activeDepartmentApi(ids: string | string[]): Promise<void> {
  const idArray = Array.isArray(ids) ? ids : [ids];
  return requestClient.post('/department/activeDepartment', idArray);
}

export async function inactiveDepartmentApi(ids: string | string[]): Promise<void> {
  const idArray = Array.isArray(ids) ? ids : [ids];
  return requestClient.post('/department/inactiveDepartment', idArray);
}

export async function deleteDepartmentApi(id: number | string): Promise<void> {
  return requestClient.post('/department/deleteDepartment', preserveApiIdForRequest(id));
}
