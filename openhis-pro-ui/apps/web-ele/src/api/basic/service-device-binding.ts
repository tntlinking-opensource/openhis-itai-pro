import type { ApiOptionItem, PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 绑定类型：与领域 ServiceDeviceTemplateTypeCode 一致 */
export const SERVICE_DEVICE_BIND_TYPE_USAGE = 'USAGE';
export const SERVICE_DEVICE_BIND_TYPE_ACTIVITY = 'ACTIVITY';

/** 适用范围：与领域 ServiceDeviceTemplateRangeCode 一致 */
export const SERVICE_DEVICE_RANGE_OUTPATIENT = 'OUTPATIENT';
export const SERVICE_DEVICE_RANGE_INPATIENT = 'INPATIENT';

/** 目标表名：与 TableNameConstants 一致 */
export const SERVICE_DEVICE_TARGET_TABLE_ACTIVITY = 'wkf_activity_definition';
export const SERVICE_DEVICE_TARGET_TABLE_DEVICE = 'adm_device_definition';

/** 发布状态：与 PublicationStatus 一致 */
export const SERVICE_DEVICE_STATUS_ACTIVE = 'ACTIVE';
export const SERVICE_DEVICE_STATUS_INACTIVE = 'INACTIVE';

export interface ServiceDeviceBindingItem {
  id?: string;
  rangeCode?: string;
  rangeName?: string;
  typeCode?: string;
  typeName?: string;
  itemTable?: string;
  itemNo?: string;
  itemName?: string;
  devActTable?: string;
  devActTableName?: string;
  devActId?: string;
  devActName?: string;
  quantity?: number;
  unitCode?: string;
  unitName?: string;
  statusEnum?: string;
  statusName?: string;
}

export interface ServiceDeviceBindingInitResponse {
  statusOptions?: ApiOptionItem[];
  rangeOptions?: ApiOptionItem[];
  bindTypeOptions?: ApiOptionItem[];
  unitOptions?: ApiOptionItem[];
  usageSourceOptions?: ApiOptionItem[];
  activitySourceOptions?: ApiOptionItem[];
  deviceDefOptions?: ApiOptionItem[];
  targetTableOptions?: ApiOptionItem[];
}

export interface PageServiceDeviceBindingParams extends PageParams {
  rangeCode?: string;
  bindType?: string;
  sourceId?: string;
  statusEnum?: string;
}

export interface SaveServiceDeviceBindingParams {
  id?: string;
  rangeCode?: string;
  bindType: string;
  sourceId: string;
  targetId: string;
  targetTable: string;
  quantity: number;
  unitCode?: string;
  statusEnum: string;
}

export interface ServiceDeviceBindingUnitOptionsByTargetParams {
  targetTable: string;
  targetId: string;
}

export interface ListDeviceDefOptionsParams {
  keyword?: string;
}

export interface ServiceDeviceBindingUnitOptionsByTargetResponse {
  unitCode?: string;
  unitOptions?: ApiOptionItem[];
}

export async function initServiceDeviceBindingApi() {
  return publicRequestClient.post<ServiceDeviceBindingInitResponse>('/serviceDeviceBinding/init');
}

/** 实时拉取耗材绑定目标下拉（避免 init 快照在其他 Tab 新增耗材后过期） */
export async function listDeviceDefOptionsApi(params?: ListDeviceDefOptionsParams) {
  return publicRequestClient.post<ApiOptionItem[]>(
    '/serviceDeviceBinding/listDeviceDefOptions',
    params ?? {},
  );
}

export async function pageServiceDeviceBindingApi(params: PageServiceDeviceBindingParams) {
  return publicRequestClient.post<PageApiResponse<ServiceDeviceBindingItem>>(
    '/serviceDeviceBinding/pageBindings',
    params,
  );
}

export async function saveServiceDeviceBindingApi(params: SaveServiceDeviceBindingParams) {
  return publicRequestClient.post<string>('/serviceDeviceBinding/saveBinding', params);
}

export async function deleteServiceDeviceBindingApi(id: number | string) {
  return publicRequestClient.post<unknown>(
    '/serviceDeviceBinding/deleteBinding',
    preserveApiIdForRequest(id),
  );
}

export async function serviceDeviceBindingUnitOptionsByTargetApi(
  params: ServiceDeviceBindingUnitOptionsByTargetParams,
) {
  return publicRequestClient.post<ServiceDeviceBindingUnitOptionsByTargetResponse>(
    '/serviceDeviceBinding/unitOptionsByTarget',
    params,
  );
}
