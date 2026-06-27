import type { PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

export interface PageSettingsParams extends PageParams {
  sortDirection?: string;
  name?: string;
  sortBy?: string;
  keyword?: string;
  providerName?: string;
  providerKey?: string;
}

export interface Setting {
  id: number;
  name: string;
  value: string;
  description: string;
  providerName?: string;
  providerKey?: string;
}

export interface CreateSettingParams {
  name: string;
  description: string;
  value: string;
  providerName: string;
  providerKey: string;
}

export interface UpdateSettingParams {
  id: number;
  request: {
    description: string;
    value: string;
  };
}

export interface SettingProvider {
  name: string;
  label: string;
}

export interface SettingInitResponse {
  providers: Array<{ label: string; value: string }>;
}

export interface ProviderKeyOption {
  key: string;
  label: string;
}

export interface GetEffectiveValueParams {
  name: string;
}

export interface SettingDefinition {
  name: string;
  title: string;
}

export const pageSettingsApi = async (params: PageSettingsParams) => {
  return publicRequestClient.post<PageApiResponse<Setting>>('/setting/getList', params);
};

/**
 * 创建设置
 */
export const createSettingApi = async (params: CreateSettingParams) => {
  return publicRequestClient.post('/setting/create', params);
};

/**
 * 更新设置
 */
export const updateSettingApi = async (params: UpdateSettingParams) => {
  return publicRequestClient.post('/setting/update', params);
};

/**
 * 删除设置
 */
export const deleteSettingApi = async (id: number | string) => {
  return publicRequestClient.post('/setting/delete', preserveApiIdForRequest(id));
};

/**
 * 初始化（获取提供者名称）
 */
export const initSettingApi = async () => {
  return publicRequestClient.post<SettingInitResponse>('/setting/init', {});
};

/**
 * 获取提供者键可选值
 */
export const getSettingProviderKeysApi = async (providerName: string) => {
  return publicRequestClient.post<ProviderKeyOption[]>('/setting/getProviderKeys', {
    value: providerName,
  });
};

/**
 * 按优先级获取最终值（G > U > R > T）
 */
export const getEffectiveSettingValueApi = async (params: GetEffectiveValueParams) => {
  return publicRequestClient.post<string>('/setting/getEffectiveValue', { name: params.name });
};

/**
 * 初始化配置项定义（来自后端枚举）
 */
export const initSettingDefinitionsApi = async () => {
  return publicRequestClient.post<SettingDefinition[]>('/setting/initDefinitions', {});
};
