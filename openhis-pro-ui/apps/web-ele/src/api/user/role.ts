import type { PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

export interface RoleListParams extends PageParams {
  keyword?: string;
  isDefault?: string;
  isStatic?: string;
}

export interface RoleInfo {
  code: string;
  createdAt: string;
  createdBy: null | string;
  description: string;
  displayName: string;
  id: number;
  isDefault: boolean;
  isStatic: boolean;
  name: string;
  tenantId: null | number;
  updatedAt: null | string;
  updatedBy: null | string;
  permissions?: string[];
  permissionNames?: string[];
}

export interface CreateRoleParams {
  isDefault: string;
  isStatic: string;
  code: string;
  displayName: string;
  name: string;
  description: string;
  permissions?: string[];
}

export interface UpdateRoleParams {
  id: number;
  isDefault: string;
  displayName: string;
  description: string;
  permissions?: string[];
}

/**
 * 绑定权限参数
 */
export interface AssignPermissionsParams {
  id: number;
  request: {
    permissionNames: string[];
  };
}

export async function getRoleListApi(params: RoleListParams) {
  return publicRequestClient.post<PageApiResponse<RoleInfo>>('/role/getList', params);
}

/**
 * 获取角色详情
 */
export async function getRoleByIdApi(id: number): Promise<RoleInfo> {
  return publicRequestClient.post<RoleInfo>('/role/getById', { id });
}

export async function createRoleApi(params: CreateRoleParams) {
  return publicRequestClient.post<{ data: { id: number } }>('/role/create', params);
}

export async function updateRoleApi(params: UpdateRoleParams) {
  return publicRequestClient.post('/role/update', params);
}

/**
 * 绑定权限
 */
export async function assignPermissionsApi(params: AssignPermissionsParams): Promise<void> {
  return publicRequestClient.post('/role/assignPermissions', params);
}

/**
 * 删除角色
 */
export async function deleteRoleApi(id: number): Promise<void> {
  return publicRequestClient.post('/role/delete', { id });
}
