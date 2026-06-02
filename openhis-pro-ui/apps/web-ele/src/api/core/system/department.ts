import type {
  Department,
  DepartmentParams,
  DepartmentTree,
  InitDepartmentResponse,
} from './department.types';

import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

export async function getDepartmentTreeApi(): Promise<DepartmentTree[]> {
  return requestClient.post<DepartmentTree[]>('/department/getTree');
}

export async function pageDepartmentApi(): Promise<ApiResponse<Department[]>> {
  return requestClient.post('/department/getDepartmentList');
}

export async function getDepartmentApi(id: number | string): Promise<Department> {
  return requestClient.post<Department>('/department/getDepartmentById', String(id));
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
