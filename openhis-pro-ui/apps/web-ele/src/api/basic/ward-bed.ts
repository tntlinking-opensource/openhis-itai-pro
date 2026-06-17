import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

import { publicRequestClient } from '../request';

/** 物理形式 code，与领域 LocationForm 一致。 */
export const WARD_BED_FORM_CODE = {
  WARD: 'WARD',
  HOUSE: 'HOUSE',
  BED: 'BED',
} as const;

/** 位置状态 code，与领域 LocationStatus 一致。 */
export const WARD_BED_STATUS_CODE = {
  DRAFT: 'DRAFT',
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
  SUSPENDED: 'SUSPENDED',
} as const;

/** 运营状态 code，与领域 LocationOperational 一致。 */
export const WARD_BED_OPERATIONAL_CODE = {
  NOT_IN_USE: 'NOT_IN_USE',
  IN_USE: 'IN_USE',
} as const;

/** 位置模式 code，与领域 LocationMode 一致。 */
export const WARD_BED_MODE_CODE = {
  INSTANCE: 'INSTANCE',
  KIND: 'KIND',
} as const;

/** 新建病区/病房/床位时的默认枚举 code。 */
export const WARD_BED_REGISTER_DEFAULT = {
  statusEnum: WARD_BED_STATUS_CODE.ACTIVE,
  operationalEnum: WARD_BED_OPERATIONAL_CODE.NOT_IN_USE,
  modeEnum: WARD_BED_MODE_CODE.INSTANCE,
  typeJson: '{}',
} as const;

export interface WardBed {
  id?: string;
  displayOrder?: number;
  typeJson?: string;
  pyStr?: string;
  modeEnum?: string;
  parentId?: string;
  typeCode?: string;
  formEnum?: string;
  organizationId?: number | string;
  statusEnum?: string;
  operationalEnum?: string;
  name?: string;
  busNo?: string;
  wbStr?: string;
  parent?: WardBed;
  [key: string]: unknown;
}

export interface CreateWardBedParams {
  name: string;
  formEnum: string;
  organizationId: number | string;
  parentId?: string;
  displayOrder?: number;
  typeJson?: string;
  pyStr?: string;
  modeEnum?: string;
  statusEnum?: string;
  operationalEnum?: string;
  busNo?: string;
  wbStr?: string;
}

export interface WardBedInitResponse {
  formEnums?: Array<{ label: string; value: string; code?: string }>;
  locationStatusOptions?: Array<{ label: string; value: string }>;
  organizationOptions?: Array<{ label: string; value: string }>;
}

export async function wardBedInitApi() {
  return publicRequestClient.post<WardBedInitResponse>('/wardBed/init');
}

export async function createWardBedApi(params: CreateWardBedParams) {
  return publicRequestClient.post<WardBed>('/wardBed/create', params);
}

export async function getWardBedByIdApi(id: number | string) {
  return publicRequestClient.post<WardBed>('/wardBed/getById', preserveApiIdForRequest(id));
}

export async function enableWardBedApi(id: number | string) {
  return publicRequestClient.post('/wardBed/enableLocation', preserveApiIdForRequest(id));
}

export async function disableWardBedApi(id: number | string) {
  return publicRequestClient.post('/wardBed/deactivateLocation', preserveApiIdForRequest(id));
}

export async function editWardBedApi(params: CreateWardBedParams & { id: string }) {
  return publicRequestClient.post<WardBed>('/wardBed/edit', params);
}

export async function getChildrenByParentBusNoApi(params: string) {
  return publicRequestClient.post('/wardBed/getChildrenByParentBusNo', {
    value: params,
  });
}

export async function deleteWardBedApi(params: string) {
  return publicRequestClient.post('/wardBed/deleteLocation', {
    value: params,
  });
}

export async function getWardListApi(params?: { id?: string }) {
  return publicRequestClient.post<WardBed[]>('/wardBed/getWardList', params ?? {});
}

export async function getWardBedPageApi(params?: { id?: string }) {
  return publicRequestClient.post<WardBed[]>('/wardBed/getHouseAndBedList', params);
}
