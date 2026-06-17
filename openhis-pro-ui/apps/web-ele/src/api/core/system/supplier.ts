import type { ApiOptionItem, PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

/**
 * 供应商类型枚举选项
 */
export interface SupplierTypeOption {
  label: string;
  value: number;
}

/**
 * 供应商数据
 */
export interface Supplier {
  id: number | string;
  busNo: string;
  name: string;
  typeEnum: number | string;
  address?: string;
  phone?: string;
  email?: string;
  /** 状态编码，与后端 SupplierStatus#getCode() 一致 */
  statusEnum?: string;
  statusEnum_enumText?: string;
  orgId?: number | string;
  pyStr?: string;
  wbStr?: string;
  [key: string]: unknown;
}

/**
 * 供应商初始化响应
 */
export interface InitSupplierResponse {
  supplierTypeOptions: SupplierTypeOption[];
  organizationOptions?: ApiOptionItem[];
  [key: string]: unknown;
}

/**
 * 创建供应商参数
 */
export interface CreateSupplierParams {
  busNo?: string;
  name: string;
  typeEnum: number;
  address?: string;
  phone?: string;
  email?: string;
  activeFlag?: boolean | number;
  [key: string]: unknown;
}

/**
 * 更新供应商参数
 */
export interface UpdateSupplierParams extends CreateSupplierParams {
  id: number | string;
}

/**
 * 获取供应商列表
 */
export interface SupplierListParams extends PageParams {
  keyword?: string;
  typeEnum?: Array<number | string>;
}

export async function getSupplierListApi(params: SupplierListParams) {
  return publicRequestClient.post<PageApiResponse<Supplier>>('/supplier/pageSupplierList', params);
}

/**
 * 供应商初始化（获取选项）
 */
export async function initSupplierApi() {
  return publicRequestClient.post<InitSupplierResponse>('/supplier/init', {});
}

/**
 * 获取供应商详情
 */
export async function getSupplierApi(id: string) {
  return publicRequestClient.post<Supplier>('/supplier/getSupplier', id);
}

/**
 * 创建供应商
 */
export async function createSupplierApi(params: CreateSupplierParams) {
  return publicRequestClient.post<Supplier>('/supplier/createSupplier', params);
}

/**
 * 更新供应商
 */
export async function updateSupplierApi(params: UpdateSupplierParams) {
  return publicRequestClient.post<Supplier>('/supplier/editSupplier', params);
}

/**
 * 删除供应商
 */
export async function deleteSupplierApi(id: number | string) {
  return publicRequestClient.post<null>('/supplier/deleteSupplier', id);
}

/**
 * 批量启用供应商
 */
export async function batchEnableSupplierApi(ids: (number | string)[]) {
  return publicRequestClient.post<null>('/supplier/batchEnableSupplier', ids);
}

/**
 * 批量停用供应商
 */
export async function batchDisableSupplierApi(ids: (number | string)[]) {
  return publicRequestClient.post<null>('/supplier/batchDisableSupplier', ids);
}
