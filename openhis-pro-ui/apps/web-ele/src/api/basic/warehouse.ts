import type { GetInfoParams } from '#/types/api-response';

import { publicRequestClient } from '../request';

export const WAREHOUSE_FORM_ENUM = {
  PHARMACY: '16',
  WAREHOUSE: '17',
} as const;

/** 后端 DTO 可能返回领域 code（WAREHOUSE/PHARMACY）或前端下拉 code（17/16） */
export function isWarehouseFormEnum(formEnum?: null | string): boolean {
  return formEnum === WAREHOUSE_FORM_ENUM.WAREHOUSE || formEnum === 'WAREHOUSE';
}

export function isPharmacyFormEnum(formEnum?: null | string): boolean {
  return formEnum === WAREHOUSE_FORM_ENUM.PHARMACY || formEnum === 'PHARMACY';
}

export const WAREHOUSE_STATUS_ENUM = {
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
} as const;

export interface ListWarehouseParams {
  keyword?: string;
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
}

export async function initWarehouseApi() {
  return publicRequestClient.post<Warehouse[]>('/warehouse/init');
}

export async function getWarehouseListApi(params?: ListWarehouseParams) {
  return publicRequestClient.post<Warehouse[]>('/warehouse/getWarehouseList', params ?? {});
}

export async function createWarehouseApi(params: CreateWarehouseParams) {
  return publicRequestClient.post<null>('/warehouse/createWarehouse', params);
}

export async function editWarehouseApi(params: CreateWarehouseParams & { id: string }) {
  return publicRequestClient.post<null>('/warehouse/editWarehouse', params);
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
