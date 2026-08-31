import type { ApiOptionItem, PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

import type { LabDeviceItem } from './lab-device';

/** 合管绑定目标表：诊疗活动 */
export const SPECIMEN_TUBE_MERGE_TARGET_TABLE_ACTIVITY = 'wkf_activity_definition';
/** 合管绑定目标表：耗材定义 */
export const SPECIMEN_TUBE_MERGE_TARGET_TABLE_DEVICE = 'adm_device_definition';

/** 检验类诊疗目录类别 code */
export const LAB_ACTIVITY_CATEGORY_CODE = '22';

/** 合管配置页采血费勾选展示名（与后端 BLOOD_COLLECTION_FEE_ACTIVITY_NAME 一致，按名称匹配诊疗目录） */
export const BLOOD_COLLECTION_FEE_LABEL = '采血费';

export interface SpecimenTubeMergeInitResponse {
  activityOptions?: ApiOptionItem[];
  consumableOptions?: ApiOptionItem[];
}

export interface PageSpecimenTubeMergeDevicesParams extends PageParams {
  keyword?: string;
}

export interface SearchLabActivitiesParams {
  keyword?: string;
  limit?: number;
}

export interface ListConsumableOptionsParams {
  keyword?: string;
}

export interface SpecimenTubeMergeConsumableLine {
  id?: string;
  targetId: string;
  targetName?: string;
  quantity: number;
  unitCode: string;
  unitName?: string;
  displayOrder?: number;
}

export interface SpecimenTubeMergeBindings {
  deviceId: string;
  activityIds: string[];
  bindBloodCollectionFee: boolean;
  consumableLines: SpecimenTubeMergeConsumableLine[];
}

export interface SaveSpecimenTubeMergeBindingsParams {
  deviceId: string;
  activityIds: string[];
  bindBloodCollectionFee: boolean;
  consumableLines: Array<{
    targetId: string;
    quantity: number;
    unitCode: string;
    displayOrder?: number;
  }>;
}

export interface SpecimenTubeMergeUnitOptionsByTargetParams {
  targetTable: string;
  targetId: string;
}

export interface SpecimenTubeMergeUnitOptionsByTargetResponse {
  unitCode?: string;
  unitOptions?: ApiOptionItem[];
}

/** 加载标本合管配置页初始化数据 */
export async function initSpecimenTubeMergeApi() {
  return publicRequestClient.post<SpecimenTubeMergeInitResponse>('/specimenTubeMerge/init');
}

/** 分页查询启用态检验设备（左侧设备列表） */
export async function pageSpecimenTubeMergeDevicesApi(params: PageSpecimenTubeMergeDevicesParams) {
  return publicRequestClient.post<PageApiResponse<LabDeviceItem>>(
    '/specimenTubeMerge/pageDevices',
    params,
  );
}

/** 查询指定设备的合管绑定 */
export async function getSpecimenTubeMergeBindingsApi(deviceId: string) {
  return publicRequestClient.post<SpecimenTubeMergeBindings>('/specimenTubeMerge/getBindings', {
    deviceId,
  });
}

/** 全量保存指定设备的合管绑定 */
export async function saveSpecimenTubeMergeBindingsApi(
  params: SaveSpecimenTubeMergeBindingsParams,
) {
  return publicRequestClient.post<unknown>('/specimenTubeMerge/saveBindings', params);
}

/** 按绑定目标解析可选单位 */
export async function specimenTubeMergeUnitOptionsByTargetApi(
  params: SpecimenTubeMergeUnitOptionsByTargetParams,
) {
  return publicRequestClient.post<SpecimenTubeMergeUnitOptionsByTargetResponse>(
    '/specimenTubeMerge/unitOptionsByTarget',
    params,
  );
}

/** 检索检验类诊疗项目（activity_category_code=22） */
export async function searchLabActivitiesApi(params?: SearchLabActivitiesParams) {
  return publicRequestClient.post<ApiOptionItem[]>(
    '/specimenTubeMerge/searchActivities',
    params ?? {},
  );
}

/** 检索合管耗材候选 */
export async function listConsumableOptionsApi(params?: ListConsumableOptionsParams) {
  return publicRequestClient.post<ApiOptionItem[]>(
    '/specimenTubeMerge/listConsumableOptions',
    params ?? {},
  );
}
