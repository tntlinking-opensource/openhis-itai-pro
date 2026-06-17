import type { PageParams } from '#/types/api-response';

/** 划价选品目录分页查询 */
export interface PageNurseStationPricingCatalogRequest extends PageParams {
  encounterId?: string;
  patientId?: string;
  keyword?: string;
  productTableNames?: string[];
  organizationUnitId?: string;
  pharmacyLocationId?: string;
  sortMode?: string;
}

/** 目录行单位 band（与产品读模型对齐） */
export interface NurseStationPricingCatalogUnitBandDto {
  bandKind?: string;
  unitCode?: string;
  unitName?: string;
  ratioToSplit?: number | string;
}

/** 划价选品目录行 */
export interface NurseStationPricingCatalogRowDto {
  productDefinitionId?: string;
  productTableName?: string;
  busNo?: string;
  name?: string;
  pyCode?: string;
  wbCode?: string;
  categoryCode?: string;
  categoryCodeDictText?: string;
  unitCode?: string;
  unitName?: string;
  unitBands?: NurseStationPricingCatalogUnitBandDto[];
  chargeItemDefinitionId?: string;
  unitPrice?: number | string;
  /** 读模型 product_id；部分接口与 activityId/deviceDefId 同义 */
  productId?: string;
  activityId?: string;
  deviceDefId?: string;
  childrenJson?: string;
  oxygenOrEcgFlag?: boolean;
}

/** 划价选品目录分页响应 */
export interface PageNurseStationPricingCatalogResponse {
  total?: number;
  records?: NurseStationPricingCatalogRowDto[];
  totalCount?: number;
  content?: NurseStationPricingCatalogRowDto[];
  rows?: NurseStationPricingCatalogRowDto[];
}

/** 补费父锚点（直接划价禁止携带） */
export interface NurseStationPricingParentRequestAnchorDto {
  orderType?: 'MED_REQUEST' | 'SERVICE_REQUEST' | string;
  requestId?: string;
}

/** 划价确认写行 */
export interface NurseStationPricingWriteItemDto {
  encounterId: string;
  patientId: string;
  accountId: string;
  productTableName: string;
  productDefinitionId: string;
  activityId?: string;
  deviceDefId?: string;
  quantity: number | string;
  unitCode: string;
  chargeItemDefinitionId: string;
  unitPrice: number | string;
  totalPrice: number | string;
  performLocation?: string;
  encounterDiagnosisId?: string;
  parentRequestAnchor?: NurseStationPricingParentRequestAnchorDto;
}

/** 直接划价确认 */
export interface ConfirmDirectNurseStationPricingRequest {
  items: NurseStationPricingWriteItemDto[];
}

export interface ConfirmDirectNurseStationPricingResponse {
  success?: boolean;
  processedCount?: number;
  createdServiceRequestIds?: string[];
  createdDeviceRequestIds?: string[];
}

/** 医嘱补费父医嘱列表查询 */
export interface ListNurseStationSupplementParentOrdersRequest {
  encounterIds: string[];
  therapyType?: 'all' | 'long' | 'temp' | string;
  keyword?: string;
  orgId?: string;
}

/** 医嘱补费父医嘱行（含补费子项；子项 basedOnId 指向父级 requestId） */
export interface NurseStationSupplementParentOrderRowDto {
  listRowKey?: string;
  requestId?: string;
  basedOnId?: string;
  orderType?: 'DEVICE_REQUEST' | 'MED_REQUEST' | 'SERVICE_REQUEST' | string;
  encounterId?: string;
  patientId?: string;
  statusEnum?: string;
  statusDisplay?: string;
  therapyEnum?: string;
  therapyDisplay?: string;
  adviceDisplayName?: string;
  specificationText?: string;
  dosageDisplay?: string;
  usageDisplay?: string;
  frequencyDisplay?: string;
  authoredTime?: string;
  stopTime?: string;
  requesterName?: string;
  performerLocationDisplay?: string;
  categoryEnum?: string;
  groupNo?: number;
  sortNumber?: number;
  /** 子项：划价项目名称 */
  productDisplayName?: string;
  pricingModeDisplay?: string;
  quantity?: number | string;
  unitDisplay?: string;
  unitPrice?: number | string;
  totalPrice?: number | string;
  pricedTime?: string;
  chargeStatusDisplay?: string;
}

export interface ListNurseStationSupplementParentOrdersResponse {
  groups?: NurseStationSupplementParentGroupDto[];
}

/** 补费父医嘱按就诊分组 */
export interface NurseStationSupplementParentGroupDto {
  encounterId?: string;
  orders?: NurseStationSupplementParentOrderRowDto[];
}

/** 医嘱补费确认 */
export interface ConfirmSupplementNurseStationPricingRequest {
  items: NurseStationPricingWriteItemDto[];
}

export interface ConfirmSupplementNurseStationPricingResponse {
  success?: boolean;
  processedCount?: number;
  createdServiceRequestIds?: string[];
  createdDeviceRequestIds?: string[];
}

/** 已划价列表查询 */
export interface ListNurseStationPricedOrdersRequest {
  encounterIds: string[];
  pricedTimeFrom?: string;
  pricedTimeTo?: string;
  parentRequestId?: string;
}

/** 已划价列表行 */
export interface NurseStationPricedOrderRowDto {
  listRowKey?: string;
  orderId?: string;
  orderType?: string;
  encounterId?: string;
  patientId?: string;
  productDisplayName?: string;
  quantity?: number | string;
  unitCode?: string;
  unitDisplay?: string;
  unitPrice?: number | string;
  totalPrice?: number | string;
  pricedTime?: string;
  chargeStatusEnum?: string;
  chargeStatusDisplay?: string;
  dispenseStatusEnum?: string;
  dispenseStatusDisplay?: string;
  pricingModeCode?: string;
  pricingModeDisplay?: string;
  parentRequestId?: string;
  /** 与 parentRequestId 同义，关联父医嘱 requestId */
  basedOnId?: string;
  parentOrderType?: string;
  parentAdviceDisplayName?: string;
}

/** 按就诊分组的已划价列表 */
export interface NurseStationPricingPatientGroupDto {
  encounterId?: string;
  pricedOrders?: NurseStationPricedOrderRowDto[];
}

export interface ListNurseStationPricedOrdersResponse {
  groups?: NurseStationPricingPatientGroupDto[];
}
