import { publicRequestClient } from '#/api/request';

export interface PermissionInfo {
  id: number;
  name: string;
  displayName: string;
  description?: string;
  providerName?: string;
  providerKey?: string;
  tenantId?: null | number;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: null | string;
  updatedBy?: null | string;
  children?: PermissionInfo[];
  parentName?: string;
  /** 目录分组节点为 false，不可授权 */
  grantable?: boolean;
}

/**
 * 创建权限参数
 */
export interface CreatePermissionParams {
  parentName?: string;
  displayName: string;
  name: string;
  description?: string;
}

/**
 * 更新权限参数
 */
export interface UpdatePermissionParams {
  id: number;
  request: {
    description?: string;
    displayName: string;
    parentName?: string;
  };
}

/**
 * 获取权限树形结构
 */
export async function getPermissionListApi(): Promise<PermissionInfo[]> {
  return publicRequestClient.post<PermissionInfo[]>('/permission/getTree');
}

/**
 * 创建权限
 */
export async function createPermissionApi(params: CreatePermissionParams): Promise<void> {
  return publicRequestClient.post('/permission/create', params);
}

/**
 * 更新权限
 */
export async function updatePermissionApi(params: UpdatePermissionParams): Promise<void> {
  return publicRequestClient.post('/permission/update', params);
}

/**
 * 根据角色ID获取权限
 */
export interface GetPermissionsByRoleParams {
  value: string;
}

export async function getPermissionsByRoleApi(
  params: GetPermissionsByRoleParams,
): Promise<string[]> {
  return publicRequestClient.post<string[]>('/permission/getByRole', params);
}

/**
 * 删除权限
 */
export async function deletePermissionApi(params: { id: number }): Promise<void> {
  return publicRequestClient.post('/permission/delete', params);
}
