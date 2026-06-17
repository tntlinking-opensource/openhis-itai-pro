import type { ApiOptionItem } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

export interface ServiceLocationItem {
  id?: string;
  organizationId?: string;
  organizationName?: string;
  itemTable?: string;
  itemTable_dictText?: string;
  distributionCategoryCode?: string;
  distributionCategoryCode_dictText?: string;
  defLocationId?: string;
  defLocationName?: string;
  itemDefinitionId?: string;
  itemDefinitionName?: string;
  methodCode?: string;
  methodCode_dictText?: string;
  startTime?: string;
  endTime?: string;
  priority?: number;
  displayOrder?: number;
  remark?: string;
}

export interface ServiceLocationInitResponse {
  organizationOptions?: ApiOptionItem[];
  defLocationOptions?: ApiOptionItem[];
  itemTableOptions?: ApiOptionItem[];
  medicationCategoryOptions?: ApiOptionItem[];
  deviceCategoryOptions?: ApiOptionItem[];
  /** 西药/中成药给药途径（method_code） */
  methodCodeOptions?: ApiOptionItem[];
  /** 中药用法（tcm_method_code） */
  tcmMethodCodeOptions?: ApiOptionItem[];
}

export interface ListServiceLocationByOrganizationParams {
  organizationId: string;
}

export interface SaveServiceLocationConfigItem {
  itemTable: string;
  distributionCategoryCode: string;
  defLocationId: string;
  itemDefinitionId?: string;
  methodCode?: string;
  startTime?: string;
  endTime?: string;
  priority: number;
  displayOrder?: number;
  remark?: string;
}

export interface SaveServiceLocationConfigParams {
  organizationId: string;
  items: SaveServiceLocationConfigItem[];
}

/** 物品表：药品定义 */
export const SERVICE_LOCATION_ITEM_TABLE_MEDICATION = 'med_medication_definition';
/** 物品表：耗材定义 */
export const SERVICE_LOCATION_ITEM_TABLE_DEVICE = 'adm_device_definition';

/** 中草药发放类别码（med_category_code，与后端 DoctorStationTcmConstants.HERBAL_CATEGORY_CODE 一致） */
export const SERVICE_LOCATION_HERBAL_CATEGORY_CODE = '4';

/** 规范化下拉选项 value/label 为 string */
export function normalizeServiceLocationOptionItems(list?: ApiOptionItem[]): ApiOptionItem[] {
  return (list ?? []).map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
    sort: item.sort,
  }));
}

/** 判断物品表是否为药品（兼容历史/简写 value） */
export function isMedicationItemTable(itemTable?: string): boolean {
  if (!itemTable) {
    return false;
  }
  const value = String(itemTable).trim();
  return (
    value === SERVICE_LOCATION_ITEM_TABLE_MEDICATION || value.includes('medication_definition')
  );
}

/** 判断物品表是否为耗材（兼容历史/简写 value） */
export function isDeviceItemTable(itemTable?: string): boolean {
  if (!itemTable) {
    return false;
  }
  const value = String(itemTable).trim();
  return value === SERVICE_LOCATION_ITEM_TABLE_DEVICE || value.includes('device_definition');
}

/** 按物品表解析对应发放类别下拉 */
export function resolveCategoryOptionsForItemTable(
  itemTable: string | undefined,
  medicationOptions: ApiOptionItem[],
  deviceOptions: ApiOptionItem[],
): ApiOptionItem[] {
  if (isMedicationItemTable(itemTable)) {
    return medicationOptions;
  }
  if (isDeviceItemTable(itemTable)) {
    return deviceOptions;
  }
  return [];
}

/** 判断药品发放类别是否为中草药 */
export function isHerbalMedicationCategory(categoryCode?: string): boolean {
  return String(categoryCode ?? '').trim() === SERVICE_LOCATION_HERBAL_CATEGORY_CODE;
}

/** 按物品表与发放类别解析用法/给药途径下拉（耗材无用法维度） */
export function resolveMethodOptionsForRule(
  itemTable: string | undefined,
  distributionCategoryCode: string | undefined,
  westernMethodOptions: ApiOptionItem[],
  tcmMethodOptions: ApiOptionItem[],
): ApiOptionItem[] {
  if (!isMedicationItemTable(itemTable)) {
    return [];
  }
  if (isHerbalMedicationCategory(distributionCategoryCode)) {
    return tcmMethodOptions;
  }
  return westernMethodOptions;
}

export async function initServiceLocationApi() {
  return publicRequestClient.post<ServiceLocationInitResponse>('/serviceLocationTemplate/init');
}

export async function listServiceLocationByOrganizationApi(
  params: ListServiceLocationByOrganizationParams,
) {
  return publicRequestClient.post<ServiceLocationItem[]>(
    '/serviceLocationTemplate/listByOrganization',
    { organizationId: preserveApiIdForRequest(params.organizationId) },
  );
}

export async function saveServiceLocationConfigApi(params: SaveServiceLocationConfigParams) {
  return publicRequestClient.post<unknown>('/serviceLocationTemplate/saveDistributionConfig', {
    organizationId: preserveApiIdForRequest(params.organizationId),
    items: params.items.map((item) => ({
      ...item,
      defLocationId: preserveApiIdForRequest(item.defLocationId),
      itemDefinitionId: preserveApiIdForRequest(item.itemDefinitionId),
    })),
  });
}
