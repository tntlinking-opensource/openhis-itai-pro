import type { ApiResponse } from '#/types/api-response';

import { baseRequestClient, loginRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    password?: string;
    username?: string;
  }

  /** 登录用户信息 */
  export interface LoginUser {
    id: number;
    userName: string;
    name: null | string;
    surname: null | string;
    email: string;
    phoneNumber: null | string;
    isActive: boolean;
    isLockedOut: boolean;
    lockoutEnd: null | string;
    accessFailedCount: number;
    tenantId: null | number;
    extraProperties: null | Record<string, any>;
    createdAt: string;
    updatedAt: string;
    /**
     * adm_practitioner.id（后端 Long 常用 ToStringSerializer，运行时多为 string）
     */
    practitionerId?: null | number | string;
    /**
     * adm_organization.id / 医务人员主档 org_id
     */
    organizationId?: null | number | string;
  }

  /** 授权策略字典 */
  export interface GrantedPolicies {
    [policy: string]: boolean;
  }

  /** 授权配置 */
  export interface AuthConfig {
    grantedPolicies: GrantedPolicies;
  }

  /** 当前登录用户信息（配置中的 currentUser） */
  export interface CurrentUser {
    isAuthenticated: boolean;
    id: number;
    tenantId: null | number;
    userName: string;
    name: null | string;
    surName: null | string;
    email: string;
    emailVerified: boolean | null;
    phoneNumber: null | string;
    phoneNumberVerified: boolean | null;
    roles: string[];
    sessionId: null | string;
    impersonatorUserId: null | number;
    impersonatorTenantId: null | number;
    impersonatorUserName: null | string;
    impersonatorTenantName: null | string;
    /** adm_practitioner.id（后端序列化后常见为 string） */
    practitionerId?: null | number | string;
    /** adm_organization.id / 医务人员主档 org_id */
    organizationId?: null | number | string;
  }

  /** 当前租户信息 */
  export interface CurrentTenant {
    id: null | number;
    name: null | string;
    isAvailable: boolean;
  }

  /** 多租户信息 */
  export interface MultiTenancyInfo {
    isEnabled: boolean;
  }

  /** 系统设置 */
  export interface SettingInfo {
    values: Record<string, any>;
  }

  /** 登录返回的配置整体（configuration） */
  export interface LoginConfiguration {
    auth: AuthConfig;
    currentUser: CurrentUser;
    currentTenant: CurrentTenant;
    multiTenancy: MultiTenancyInfo;
    setting: SettingInfo;
    extraProperties: Record<string, any>;
  }

  /** 登录接口返回值 */
  export interface LoginResult {
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
    user: LoginUser;
    configuration: LoginConfiguration;
  }

  export interface RefreshTokenResult {
    data: string;
    status: number;
  }

  export interface LoginParamsByOrg {
    organizationUnitId: string;
  }

  /** 租户信息 */
  export interface TenantInfo {
    id: number;
    name: string;
    code: string;
    description: string;
    isActive: boolean;
    extraProperties: null;
    createdAt: string;
    updatedAt: string;
    createdBy: string;
    updatedBy: string;
  }

  export interface OrganizationInfo {
    children: null;
    code: string;
    createdAt: string;
    displayName: string;
    id: string;
    isEnabled: boolean;
    parentDisplayName: null;
    parentId: null;
    remark: null;
    sortOrder: number;
    tenantId: string;
    updatedAt: null;
  }
}

/**
 * 登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return loginRequestClient.post<ApiResponse<AuthApi.LoginResult>>('/auth/login', data);
}

export async function getTenantsApi() {
  return requestClient.post<ApiResponse<AuthApi.TenantInfo[]>>('/auth/getAvailableTenants');
}

/**
 * 刷新accessToken
 */
export async function refreshTokenApi() {
  return baseRequestClient.post<AuthApi.RefreshTokenResult>('/auth/refresh', {
    withCredentials: true,
  });
}

/**
 * 退出登录
 */
export async function logoutApi() {
  return baseRequestClient.post('/auth/logout', {
    withCredentials: true,
  });
}

/**
 * 获取用户权限码
 */
export async function getAccessCodesApi() {
  return requestClient.get<string[]>('/auth/codes');
}

/**
 * 获取租户列表
 */
export async function getTenantListApi() {
  return requestClient.get<AuthApi.TenantInfo[]>('/system/tenant/list');
}

/**
 * 登录
 */
export async function loginByOrgApi(data: AuthApi.LoginParamsByOrg) {
  return requestClient.post('/auth/selectOrganizationUnit', data);
}
/**
 * 分页查询组织列表
 */
export async function getOrganizationApiByLoginUser() {
  return requestClient.post('/auth/getCurrentOrganizationUnitEntity');
}
