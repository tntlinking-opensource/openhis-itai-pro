import type { UserInfo } from '#/api/user/user';
import type { PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

export interface TenantListParams extends PageParams {
  code?: string;
  keyword?: string;
  isActive?: string;
}

export interface TenantInfo {
  id: string;
  name: string;
  code: string;
  description?: string;
  extraProperties?: Record<string, any> | string;
  isActive: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: null | string;
  updatedBy?: null | string;
}

/**
 * 创建租户参数
 */
export interface CreateTenantParams {
  code: string;
  name: string;
  description?: string;
  extraProperties?: string;
  isActive: string;
}

/**
 * 更新租户参数
 */
export interface UpdateTenantParams {
  id: number;
  request: {
    description?: string;
    extraProperties?: string;
    isActive: string;
    name: string;
  };
}

/**
 * 查询租户列表
 */
export async function getTenantListApi(params: TenantListParams) {
  return publicRequestClient.post<PageApiResponse<TenantInfo>>('/tenant/getList', params);
}

/**
 * 创建租户
 */
export async function createTenantApi(params: CreateTenantParams): Promise<void> {
  return publicRequestClient.post('/tenant/create', params);
}

/**
 * 更新租户
 */
export async function updateTenantApi(params: UpdateTenantParams): Promise<void> {
  return publicRequestClient.post('/tenant/update', params);
}

/**
 * 删除租户
 */
export async function deleteTenantApi(id: number | string): Promise<void> {
  return publicRequestClient.post('/tenant/delete', preserveApiIdForRequest(id));
}

/**
 * 分配用户到租户
 */
export async function assignUsersToTenantApi(params: {
  tenantId: string;
  userIds: string[];
}): Promise<void> {
  return publicRequestClient.post('/tenantUser/assignUsers', params);
}
/**
 * 获取租户用户列表
 */
export async function getTenantUsersApi(params: { id: string }): Promise<UserInfo[]> {
  return publicRequestClient.post('/tenantUser/getUsers', params);
}
/**
 * 移除用户
 */
export async function removeUsersApi(params: {
  tenantId: string;
  userIds: string[];
}): Promise<void> {
  return publicRequestClient.post('/tenantUser/removeUsers', params);
}
