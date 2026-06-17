import type { RoleInfo } from './role';

import type { PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

export interface UserListParams extends PageParams {
  isLockedOut?: string;
  keyword?: string;
  isActive?: string;
}

export interface UserInfo {
  accessFailedCount: number;
  createdAt: string;
  email: string;
  extraProperties: null | Record<string, any>;
  id: string;
  isActive: boolean;
  isLockedOut: boolean;
  lockoutEnd: null | string;
  name: null | string;
  phoneNumber: null | string;
  surname: null | string;
  tenantId: string;
  updatedAt: string;
  userName: string;
}

export interface CreateUserParams {
  password: string;
  phoneNumber: string;
  name: string;
  extraProperties?: string;
  userName: string;
  isActive: string;
  email: string;
}

export interface UpdateUserParams {
  request: {
    email: string;
    extraProperties?: string;
    isActive: string;
    name: string;
    phoneNumber: string;
  };
  id: string;
}

export async function getUserListApi(params: UserListParams) {
  return publicRequestClient.post<PageApiResponse<UserInfo>>('/user/getList', params);
}

export async function createUserApi(params: CreateUserParams) {
  return publicRequestClient.post('/user/create', params);
}

export async function updateUserApi(params: UpdateUserParams) {
  return publicRequestClient.post('/user/update', params);
}

export async function unlockUserApi(params: { id: string }) {
  return publicRequestClient.post('/user/unlock', params);
}

export async function enableUserApi(params: { id: string }) {
  return publicRequestClient.post('/user/enable', params);
}

export async function disableUserApi(params: { id: string }) {
  return publicRequestClient.post('/user/disable', params);
}

export async function deleteUserApi(params: { id: string }) {
  return publicRequestClient.post('/user/delete', params);
}

export interface ChangePasswordParams {
  id: string;
  newPassword: string;
}

export async function changePasswordApi(params: ChangePasswordParams) {
  return publicRequestClient.post('/user/changePassword', params);
}

/**
 * 绑定角色参数
 */
export interface AssignRolesParams {
  id: string;
  request: {
    roleIds: string[];
  };
}

/**
 * 查询用户角色参数
 */
export interface GetUserRolesParams {
  value: string;
}

/**
 * 绑定角色
 */
export async function assignRolesApi(params: AssignRolesParams): Promise<void> {
  return publicRequestClient.post('/user/assignRoles', params);
}

/**
 * 查询用户角色
 */
export async function getUserRolesApi(params: GetUserRolesParams): Promise<RoleInfo[]> {
  return publicRequestClient.post<RoleInfo[]>('/user/getRoles', params);
}
