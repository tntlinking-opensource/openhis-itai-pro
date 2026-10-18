/**
 * 医技计费（住院诊疗/耗材划价）应用服务请求/响应类型（Long 主键一律 string）。
 *
 * <p>来源码均为 medical-tech-pricing（医技划价）。</p>
 */

import type { ApiOptionItem, PageParams } from '#/types/api-response';

/** 加载医技计费初始化（耗材房选项） */
export interface LoadMedicalTechChargingInitRequest {
  encounterId: string;
}

export interface LoadMedicalTechChargingInitResponse {
  defaultLocationId?: null | string;
  encounterId?: null | string;
  locationOptions?: ApiOptionItem[] | null;
}

/** 分页查询耗材选品目录 */
export interface PageMedicalTechDeviceCatalogRequest extends PageParams {
  encounterId: string;
  keyword?: string;
  locationId?: null | string;
  pricingFlag?: null | number;
}

/** 分页查询诊疗选品目录（执行科室=登录 TECH） */
export interface PageMedicalTechServiceCatalogRequest extends PageParams {
  categoryCode?: string;
  encounterId: string;
  keyword?: string;
}

export interface MedicalTechDeviceCatalogUnitBandDto {
  bandKind?: null | string;
  ratioToSplit?: null | number | string;
  unitCode?: null | string;
  unitName?: null | string;
  unitPrice?: null | number | string;
}

export interface MedicalTechDeviceCatalogRowDto {
  availableQty?: null | number | string;
  definitionId: string;
  itemCode?: null | string;
  itemName?: null | string;
  locationId?: null | string;
  lotNumber?: null | string;
  specification?: null | string;
  unitBands?: MedicalTechDeviceCatalogUnitBandDto[] | null;
  unitCode?: null | string;
  unitName?: null | string;
  unitPrice?: null | number | string;
}

export interface PageMedicalTechDeviceCatalogResponse {
  content: MedicalTechDeviceCatalogRowDto[];
  pageNo?: number;
  pageSize?: number;
  totalCount: number;
}

/** 诊疗选品分页响应（行结构与耗材目录行同形，definitionId=activityId） */
export type PageMedicalTechServiceCatalogResponse = PageMedicalTechDeviceCatalogResponse;

/** 耗材划价写行 */
export interface MedicalTechDevicePricingWriteItemDto {
  definitionId: string;
  locationId: string;
  lotNumber?: null | string;
  quantity: number | string;
  unitCode: string;
}

/** 诊疗划价写行 */
export interface MedicalTechServicePricingWriteItemDto {
  activityId: string;
  quantity: number | string;
  unitCode: string;
}

export interface ConfirmMedicalTechDevicePricingRequest {
  accountId?: null | string;
  encounterId: string;
  items: MedicalTechDevicePricingWriteItemDto[];
}

export interface ConfirmMedicalTechDevicePricingResponse {
  createdRequestIds?: null | string[];
  message?: null | string;
  succeeded?: boolean | null;
}

export interface ConfirmMedicalTechServicePricingRequest {
  accountId?: null | string;
  encounterId: string;
  items: MedicalTechServicePricingWriteItemDto[];
}

export type ConfirmMedicalTechServicePricingResponse =
  ConfirmMedicalTechDevicePricingResponse;

/** 已划价耗材列表 */
export interface ListMedicalTechPricedDevicesRequest {
  encounterId: string;
}

export interface MedicalTechPricedDeviceRowDto {
  /** 计费人主键（划价操作人） */
  billingPractitionerId?: null | string;
  /** 计费人展示名 */
  billingPractitionerName?: null | string;
  cancellable?: boolean | null;
  chargeItemId?: null | string;
  chargeStatusEnum?: null | string;
  dispenseId?: null | string;
  dispenseStatusEnum?: null | string;
  /** 确费人主键；空=未确费 */
  feeConfirmerId?: null | string;
  /** 确费人展示名 */
  feeConfirmerName?: null | string;
  itemCode?: null | string;
  itemName?: null | string;
  locationId?: null | string;
  /**
   * 行类型：DEVICE_REQUEST（默认）或 SERVICE_REQUEST。
   */
  orderType?: null | string;
  pricedTime?: null | string;
  quantity?: null | number | string;
  requestId: string;
  totalPrice?: null | number | string;
  unitPrice?: null | number | string;
}

export interface ListMedicalTechPricedDevicesResponse {
  rows?: MedicalTechPricedDeviceRowDto[] | null;
}

/** 取消划价 */
export interface MedicalTechDevicePricingCancelItemDto {
  /** DEVICE_REQUEST | SERVICE_REQUEST；空按耗材兼容 */
  orderType?: null | string;
  requestId: string;
}

export interface CancelMedicalTechDevicePricingRequest {
  encounterId: string;
  items: MedicalTechDevicePricingCancelItemDto[];
}

export interface CancelMedicalTechDevicePricingResponse {
  cancelledCount?: null | number;
  message?: null | string;
  succeeded?: boolean | null;
}
