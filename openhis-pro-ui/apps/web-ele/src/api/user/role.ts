import type { PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

export interface RoleListParams extends PageParams {
  keyword?: string;
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

/** 岗位分配可选内置角色（服务端解析 id，勿写死种子 id） */
export interface RoleOptionDto {
  id: string;
  displayName: string;
  builtIn?: boolean;
  /** 服务端 code，仅逻辑判断，不在界面展示 */
  code?: string;
  assignmentScopes?: string[];
}

export interface CreateRoleParams {
  displayName: string;
  description?: string;
  isDefault?: boolean;
}

export interface UpdateRoleParams {
  id: string;
  displayName: string;
  description: string;
}

/**
 * 绑定权限参数
 */
export interface AssignPermissionsParams {
  id: string;
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

/** 获取角色的岗位范围声明（AssignmentScope） */
export async function getAssignmentScopesApi(roleId: string) {
  return publicRequestClient.post<string[]>('/role/getAssignmentScopes', { id: roleId });
}

/** 全量替换角色的岗位范围声明 */
export async function assignAssignmentScopesApi(roleId: string, scopeCodes: string[]) {
  return publicRequestClient.post('/role/assignAssignmentScopes', { id: roleId, scopeCodes });
}

/** 获取角色的管理能力声明（StaffScope） */
export async function getStaffScopesApi(roleId: string) {
  return publicRequestClient.post<string[]>('/role/getStaffScopes', { id: roleId });
}

/** 全量替换角色的管理能力声明 */
export async function assignStaffScopesApi(roleId: string, scopeCodes: string[]) {
  return publicRequestClient.post('/role/assignStaffScopes', { id: roleId, scopeCodes });
}

/**
 * 删除角色
 */
export async function deleteRoleApi(id: number): Promise<void> {
  return publicRequestClient.post('/role/delete', { id });
}
