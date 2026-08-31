import type { ApiOptionItem, ApiResponse, PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

/** 检验设备发布状态 code（对齐后端 PublicationStatus） */
export const LAB_DEVICE_STATUS_ENUM = {
  DRAFT: 'DRAFT',
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
  DISCARDED: 'DISCARDED',
} as const;

export interface LabDeviceItem {
  id?: string;
  busNo?: string;
  name?: string;
  pyStr?: string;
  wbStr?: string;
  orgId?: string;
  orgName?: string;
  locationId?: string;
  locationName?: string;
  statusEnum?: string;
  statusEnumText?: string;
  categoryCode?: string;
  categoryCodeText?: string;
  modelNumber?: string;
  serialNumber?: string;
  manufacturerId?: string;
  manufacturerName?: string;
  manufactureDate?: string;
  expirationDate?: string;
  description?: string;
  updatedAt?: string;
}

export interface PageLabDeviceParams extends PageParams {
  keyword?: string;
  statusEnum?: string;
  categoryCode?: string;
  orgId?: string;
}

export interface LabDeviceInitResponse {
  categoryCodeOptions?: ApiOptionItem[];
  statusEnumOptions?: ApiOptionItem[];
  organizationOptions?: ApiOptionItem[];
  locationOptions?: ApiOptionItem[];
  manufacturerOptions?: ApiOptionItem[];
  supplierListOptions?: ApiOptionItem[];
}

export interface CreateLabDeviceParams {
  name: string;
  categoryCode?: string;
  orgId?: string;
  locationId?: string;
  modelNumber?: string;
  serialNumber?: string;
  manufacturerId?: string;
  manufactureDate?: string;
  expirationDate?: string;
  description?: string;
  pyStr?: string;
  wbStr?: string;
}

export interface UpdateLabDeviceParams extends CreateLabDeviceParams {
  id: string;
}

export interface SearchLabDeviceParams {
  keyword?: string;
  limit?: number;
}

/** 加载检验设备维护页下拉选项 */
export async function initLabDeviceApi() {
  return publicRequestClient.post<LabDeviceInitResponse>('/labDevice/init');
}

/** 分页查询检验设备列表 */
export async function pageLabDeviceApi(params: PageLabDeviceParams) {
  return publicRequestClient.post<ApiResponse<PageApiResponse<LabDeviceItem>>>(
    '/labDevice/page',
    params,
  );
}

/** 按主键查询检验设备详情 */
export async function getLabDeviceApi(id: string) {
  return publicRequestClient.post<LabDeviceItem>('/labDevice/getLabDevice', { id });
}

/** 新建检验设备 */
export async function createLabDeviceApi(params: CreateLabDeviceParams) {
  return publicRequestClient.post<LabDeviceItem>('/labDevice/createLabDevice', params);
}

/** 维护检验设备 */
export async function updateLabDeviceApi(params: UpdateLabDeviceParams) {
  return publicRequestClient.post<LabDeviceItem>('/labDevice/updateLabDevice', params);
}

/** 逻辑删除检验设备 */
export async function deleteLabDeviceApi(id: string) {
  return publicRequestClient.post<unknown>('/labDevice/deleteLabDevice', { id });
}

/** 启用检验设备 */
export async function enableLabDeviceApi(id: string) {
  return publicRequestClient.post<LabDeviceItem>('/labDevice/enableLabDevice', { value: id });
}

/** 停用检验设备 */
export async function disableLabDeviceApi(id: string) {
  return publicRequestClient.post<LabDeviceItem>('/labDevice/disableLabDevice', { value: id });
}

/** 关键字检索检验设备（下拉/侧栏候选） */
export async function searchLabDeviceApi(params?: SearchLabDeviceParams) {
  return publicRequestClient.post<LabDeviceItem[]>('/labDevice/search', params ?? {});
}
