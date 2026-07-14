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
    /** 是否具备财务审批角色（动账类供应单与调价 approve/reject） */
    canFinanceApprove?: boolean;
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

  export interface ImpersonateParams {
    userId: number | string;
    tenantId?: null | number | string;
    reason?: string;
  }

  export interface ImpersonateUser {
    id: number | string;
    userName: string;
    name?: null | string;
    email?: string;
  }

  export interface ImpersonateResult {
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
    user: ImpersonateUser;
    impersonator: ImpersonateUser;
    configuration: LoginConfiguration;
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

  /** 登录选医院/科室候选项 */
  export interface LoginOrganizationOption {
    id: number | string;
    code?: string;
    displayName?: string;
    name?: string;
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
 * 选择租户（写入服务端会话，后续业务 API 均以此为准）
 */
export async function selectTenantApi(data: { tenantId: number | string }) {
  return requestClient.post<ApiResponse<AuthApi.TenantInfo>>('/auth/selectTenant', data);
}

export async function getAvailableHospitalsApi() {
  return requestClient.post<ApiResponse<AuthApi.LoginOrganizationOption[]>>(
    '/auth/getAvailableHospitals',
  );
}

export async function selectHospitalApi(data: { hospitalId: number | string }) {
  return requestClient.post<ApiResponse<AuthApi.LoginOrganizationOption>>(
    '/auth/selectHospital',
    data,
  );
}

/**
 * 刷新accessToken
 */
export async function refreshTokenApi(refreshToken: string) {
  return loginRequestClient.post<ApiResponse<AuthApi.LoginResult>>('/auth/refreshToken', {
    refreshToken,
  });
}

/**
 * 退出登录
 */
export async function logoutApi(refreshToken?: string) {
  return baseRequestClient.post('/auth/logout', refreshToken ? { refreshToken } : {});
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
 * 平台管理员在无可登录科室时跳过选科（用于租户初始化：先建医院/科室）
 */
export async function skipOrganizationUnitSelectionApi() {
  return requestClient.post('/auth/skipOrganizationUnitSelection');
}

/**
 * 代理登录
 */
export async function impersonateApi(data: AuthApi.ImpersonateParams) {
  return requestClient.post<ApiResponse<AuthApi.ImpersonateResult>>('/auth/impersonate', data);
}

/**
 * 停止代理登录
 */
export async function stopImpersonatingApi(refreshToken: string) {
  return requestClient.post('/auth/stopImpersonating', { refreshToken });
}

/**
 * 获取应用配置（权限、currentUser 等）
 * requestClient 使用 responseReturn: 'body'，此处解包 data 供业务直接使用。
 */
export async function getApplicationConfigurationApi(): Promise<AuthApi.LoginConfiguration> {
  const res = await requestClient.post<ApiResponse<AuthApi.LoginConfiguration>>(
    '/applicationConfiguration/get',
    {},
  );
  return res.data;
}
/**
 * 用户关联的全部组织单元（展示用，不用于登录选科室）
 */
export async function getOrganizationApiByLoginUser() {
  return requestClient.post<AuthApi.OrganizationInfo[]>('/auth/getCurrentOrganizationUnitEntity');
}

/**
 * 登录选科室：用户关联科室 ∩ 岗位管辖科室
 */
export async function getAvailableOrganizationUnitsApi() {
  return requestClient.post<AuthApi.OrganizationInfo[]>('/auth/getAvailableOrganizationUnits');
}
