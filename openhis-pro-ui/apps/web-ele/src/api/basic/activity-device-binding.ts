import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

export interface ActivityDeviceBindingItem {
  id?: number | string;
  rangeCode?: string;
  typeCode?: string;
  itemNo?: string;
  devActTable?: string;
  devActId?: number | string;
  quantity?: number;
  unitCode?: string;
  statusEnum?: number;
}

export interface ActivityDeviceBindingInitResponse {
  statusOptions?: Array<{ label: string; value: string }>;
  rangeOptions?: Array<{ label: string; value: string }>;
  bindTypeOptions?: Array<{ label: string; value: string }>;
  unitOptions?: Array<{ label: string; value: string }>;
  /** 用法来源列表（method_code） */
  usageSourceOptions?: Array<{ label: string; value: string }>;
  /** 诊疗来源列表（ActivityDefinition） */
  activitySourceOptions?: Array<{ label: string; value: string }>;
  /** 号源来源列表（RegistrationType） */
  sourceSourceOptions?: Array<{ label: string; value: string }>;
  /** 耗材定义列表（DeviceDefinition） */
  deviceDefOptions?: Array<{ label: string; value: string }>;
  /** 目标表下拉 */
  targetTableOptions?: Array<{ label: string; value: string }>;
}

export interface PageActivityDeviceBindingParams {
  rangeCode?: string;
  bindType?: number | string;
  sourceId?: number | string;
  statusEnum?: number;
  pageNo?: number;
  pageSize?: number;
}

export interface PageActivityDeviceBindingResponse {
  content?: ActivityDeviceBindingItem[];
  totalCount?: number;
  totalElements?: number;
}

export interface SaveActivityDeviceBindingParams {
  id?: number | string;
  rangeCode?: string;
  bindType: number;
  sourceId: number | string;
  targetType: number;
  targetId: number | string;
  targetTable: string;
  quantity: number;
  unitCode?: string;
  statusEnum: number;
}

export interface UnitOptionsByTargetParams {
  targetTable: string;
  targetId: number | string;
}

export interface UnitOptionsByTargetResponse {
  unitCode?: string;
  unitOptions?: Array<{ label: string; value: string }>;
}

export async function initActivityDeviceBindingApi() {
  return requestClient.post<ApiResponse<ActivityDeviceBindingInitResponse>>(
    '/activityDeviceBinding/init',
  );
}

export async function pageActivityDeviceBindingApi(
  params: PageActivityDeviceBindingParams,
): Promise<ApiResponse<PageActivityDeviceBindingResponse>> {
  return requestClient.post<ApiResponse<PageActivityDeviceBindingResponse>>(
    '/activityDeviceBinding/pageBindings',
    params,
  );
}

export async function saveActivityDeviceBindingApi(
  params: SaveActivityDeviceBindingParams,
): Promise<ApiResponse<{ id: string }>> {
  return requestClient.post<ApiResponse<{ id: string }>>(
    '/activityDeviceBinding/saveBinding',
    params,
  );
}

export async function deleteActivityDeviceBindingApi(id: string): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/activityDeviceBinding/deleteBinding', {
    value: id,
  });
}

export async function unitOptionsByTargetApi(
  params: UnitOptionsByTargetParams,
): Promise<ApiResponse<UnitOptionsByTargetResponse>> {
  return requestClient.post<ApiResponse<UnitOptionsByTargetResponse>>(
    '/activityDeviceBinding/unitOptionsByTarget',
    params,
  );
}
