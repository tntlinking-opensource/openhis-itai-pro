import type { GetInfoParams } from '#/types/api-response';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { publicRequestClient } from '../request';

/** 库房维护页物理形态，与后端 {@link LocationForm} code 一致 */
export const WAREHOUSE_FORM_ENUM = {
  PHARMACY: 'PHARMACY',
  WAREHOUSE: 'WAREHOUSE',
  DEPARTMENT_PHARMACY: 'DEPARTMENT_PHARMACY',
} as const;

export function isWarehouseFormEnum(formEnum?: null | string): boolean {
  return formEnum === WAREHOUSE_FORM_ENUM.WAREHOUSE;
}

export function isPharmacyFormEnum(formEnum?: null | string): boolean {
  return formEnum === WAREHOUSE_FORM_ENUM.PHARMACY;
}

export function isDepartmentPharmacyFormEnum(formEnum?: null | string): boolean {
  return formEnum === WAREHOUSE_FORM_ENUM.DEPARTMENT_PHARMACY;
}

/** 岗位「管理药房」可选范围：药房 + 科室药房（与后端 pharmacyLocationList 一致） */
export function isManagedPharmacyLocationFormEnum(formEnum?: null | string): boolean {
  return isPharmacyFormEnum(formEnum) || isDepartmentPharmacyFormEnum(formEnum);
}

export const WAREHOUSE_STATUS_ENUM = {
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
} as const;

export interface ListWarehouseParams {
  keyword?: string;
}

export interface WarehouseInit {
  formEnumOptions: Array<{ label: string; sort: number; value: string }>;
  locationStatusOptions: Array<{ label: string; sort: number; value: string }>;
  organizationOptions: Array<{ label: string; sort: number; value: string }>;
}

export interface Warehouse {
  busNo: string;
  children: [];
  displayOrder: null | number;
  formEnum: string;
  formEnumText: string;
  id: string;
  modeEnum: string;
  modeEnumText: string;
  name: string;
  operationalEnum: string;
  operationalEnumText: null | string;
  organizationId: null | string;
  organizationName?: null | string;
  pyStr: string;
  statusEnum: string;
  statusEnumText: string;
  typeCode: string;
  typeExtensions: Record<string, unknown> | null;
  wbStr: string;
}

export interface CreateWarehouseParams {
  formEnum: string;
  name: string;
  organizationId?: string;
}

export async function initWarehouseApi() {
  return publicRequestClient.post<WarehouseInit>('/warehouse/init');
}

export async function getWarehouseListApi(params?: ListWarehouseParams) {
  return publicRequestClient.post<Warehouse[]>('/warehouse/getWarehouseList', params ?? {});
}

export async function createWarehouseApi(params: CreateWarehouseParams) {
  const payload: CreateWarehouseParams = {
    formEnum: params.formEnum,
    name: params.name,
  };
  if (params.organizationId) {
    payload.organizationId = preserveApiIdForRequest(params.organizationId) ?? undefined;
  }
  return publicRequestClient.post<null>('/warehouse/createWarehouse', payload);
}

export async function editWarehouseApi(params: CreateWarehouseParams & { id: string }) {
  const payload: CreateWarehouseParams & { id: string } = {
    formEnum: params.formEnum,
    name: params.name,
    id: preserveApiIdForRequest(params.id) ?? params.id,
  };
  if (params.organizationId) {
    payload.organizationId = preserveApiIdForRequest(params.organizationId) ?? undefined;
  }
  return publicRequestClient.post<null>('/warehouse/editWarehouse', payload);
}

export async function deactivateWarehouseApi(params: GetInfoParams) {
  return publicRequestClient.post<null>('/warehouse/deactivateWarehouse', params);
}

export async function enableWarehouseApi(params: GetInfoParams) {
  return publicRequestClient.post<null>('/warehouse/enableWarehouse', params);
}

export async function deleteWarehouseApi(params: GetInfoParams) {
  return publicRequestClient.post<null>('/warehouse/deleteWarehouse', params);
}

export async function getWarehouseApi(params: GetInfoParams) {
  return publicRequestClient.post<Warehouse>('/warehouse/getWarehouse', params);
}
