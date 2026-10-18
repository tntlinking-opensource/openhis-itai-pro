import type { PageParams } from '#/types/api-response';

/** 组套/快捷套用预选产品键（与 IF-IP-PRC-01 prepicked 对齐） */
export interface PrepickedProductKeyDto {
  productDefinitionId: string;
  productTableName: string;
}

/** 划价选品目录分页查询 */
export interface PageNurseStationPricingCatalogRequest extends PageParams {
  encounterId?: string;
  patientId?: string;
  keyword?: string;
  productTableNames?: string[];
  /** 类目 code；不传则不按类目收窄（自动计费查全部诊疗项目） */
  categoryCode?: string;
  organizationUnitId?: string;
  pharmacyLocationId?: string;
  sortMode?: string;
  /** 组套套用：按行 id 收窄目录（含价/库位） */
  prepickedProductKeys?: PrepickedProductKeyDto[];
}

/** 目录行单位 band（与产品读模型对齐；各槽可含 unitPrice） */
export interface NurseStationPricingCatalogUnitBandDto {
  bandKind?: string;
  unitCode?: string;
  unitName?: string;
  ratioToSplit?: number | string;
  /** 该槽单位单价（包装锚线性换算） */
  unitPrice?: number | string;
}

/** 划价选品目录行 */
export interface NurseStationPricingCatalogRowDto {
  productDefinitionId?: string;
  productTableName?: string;
  busNo?: string;
  name?: string;
  /** 规格/装量（产品 totalVolume） */
  totalVolume?: string;
  /** 货位展示名（库存库位名 / 产品默认库位名） */
  locationName?: string;
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
  /** 耗材默认发放库房（写路径回传 performLocation） */
  defaultDispenseLocationId?: string;
  /** 默认发放库房展示名（可选） */
  defaultDispenseLocationName?: string;
  /** 耗材库存项主键（批号行） */
  inventoryItemId?: string;
  /** 耗材批号 */
  lotNumber?: string;
  /** 批号剩余数量（拆零净量） */
  remainingQuantity?: number | string;
  /** 剩余数量展示文案（如 4盒6支） */
  availableQuantityText?: string;
  /** 效期截止时刻 */
  expirationDate?: string;
  childrenJson?: string;
  oxygenOrEcgFlag?: boolean;
}

/** 划价选品目录分页响应 */
export interface PageNurseStationPricingCatalogResponse {
  total?: number;
  totalCount?: number;
  content?: NurseStationPricingCatalogRowDto[];
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
  /** 耗材选中批号（目录 lotNumber 回传；锁定批号价） */
  lotNumber?: string;
}

/** 直接划价确认 */
export interface ConfirmDirectNurseStationPricingRequest {
  items: NurseStationPricingWriteItemDto[];
  /** 整批划价客户可见时刻，YYYY-MM-DD HH:mm:ss；空则服务端取当前时刻 */
  pricingTime?: string;
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
  /** 开立时间（后端 req_opened_time） */
  reqOpenedTime?: string;
  stopTime?: string;
  requesterName?: string;
  stopperId?: string;
  stopperName?: string;
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
  /** 整批划价客户可见时刻，YYYY-MM-DD HH:mm:ss；空则服务端取当前时刻 */
  pricingTime?: string;
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
  /** 划价/补费操作人姓名 */
  requesterName?: string;
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

/** 取消划价单行（与 IF-IP-PRC-05 列表行 orderType/orderId 对齐） */
export interface NurseStationPricingCancelItemDto {
  orderType: 'DEVICE_REQUEST' | 'SERVICE_REQUEST' | string;
  requestId: string;
  encounterId: string;
}

/** 取消划价（IF-IP-PRC-06） */
export interface CancelNurseStationPricingRequest {
  items: NurseStationPricingCancelItemDto[];
}

export interface CancelNurseStationPricingResponse {
  success?: boolean;
  processedCount?: number;
  cancelledServiceRequestIds?: string[];
  cancelledDeviceRequestIds?: string[];
}

/** 修改划价展示时间（行键与冲销同构） */
export interface ChangeNurseStationPricingBusinessTimeRequest {
  items: NurseStationPricingCancelItemDto[];
  /** 新的客户可见时刻，YYYY-MM-DD HH:mm:ss */
  newTime: string;
}

export interface ChangeNurseStationPricingBusinessTimeResponse {
  success?: boolean;
  processedCount?: number;
  message?: string;
}

/** 自动计费配置列表查询（billDate 空=全住院期护理行） */
export interface ListAutoRollConfigsRequest {
  encounterId: string;
  patientId: string;
  /** 业务日 YYYY-MM-DD；空表示全住院期 */
  billDate?: string;
  /** 是否装载计费人护士下拉；刷新可传 false，前端缓存首屏结果 */
  includeNurseOptions?: boolean;
}

/** 自动计费配置行 */
export interface NurseStationAutoRollConfigRowDto {
  ruleId?: string;
  ruleName?: string;
  statusEnum?: 'ACTIVE' | 'INACTIVE' | string;
  statusDictText?: string;
  encounterId?: string;
  triggerSourceTable?: string;
  triggerSourceId?: string;
  activityName?: string;
  chargeDefinitionId?: string;
  chargeDefinitionName?: string;
  unitPrice?: number | string;
  quantityValue?: number | string;
  performingOrgId?: string;
  priority?: number;
  remark?: string;
  /** 滚费业务时刻 HH:mm:ss */
  businessTime?: string;
  billingPractitionerId?: string;
  billingPractitionerName?: string;
}

/** 护理级别只读区 */
export interface NurseStationAutoRollNursingReadonlyDto {
  serviceRequestId?: string;
  activityId?: string;
  activityName?: string;
  quantity?: number | string;
  unitCode?: string;
  unitName?: string;
  checkTime?: string;
  statusEnum?: string;
  statusDictText?: string;
  chargeDefinitionId?: string;
  chargeDefinitionName?: string;
  unitPrice?: number | string;
  billDate?: string;
}

export interface ListAutoRollConfigsResponse {
  configs?: NurseStationAutoRollConfigRowDto[];
  /** 兼容字段：等同 nursingLevels 首行 */
  nursingLevelReadonly?: NurseStationAutoRollNursingReadonlyDto | null;
  /** 护理级别只读列表（可展开取消已滚费用） */
  nursingLevels?: NurseStationAutoRollNursingReadonlyDto[];
  /** 计费人护士下拉 */
  nurseOptions?: { value?: string; label?: string; sort?: string | number }[];
}

/**
 * 自动计费已滚费用记录查询（IF-IP-ARG-09）。
 * ruleId（床位配置）与 serviceRequestId（护理医嘱）二选一。
 */
export interface ListAutoRollPostedChargesRequest {
  encounterId: string;
  patientId: string;
  /** 床位自动计费配置主键（与 serviceRequestId 二选一） */
  ruleId?: string;
  /** 护理级别医嘱主键（与 ruleId 二选一） */
  serviceRequestId?: string;
  dateFrom?: string;
  dateTo?: string;
}

/** 已滚费用行（取消执行锚点 = procedureId） */
export interface NurseStationAutoRollPostedChargeRowDto {
  chargeItemId?: string;
  procedureId?: string;
  occurrenceTime?: string;
  quantityValue?: number | string;
  quantityUnit?: string;
  unitPrice?: number | string;
  totalPrice?: number | string;
  statusEnum?: string;
  statusDisplay?: string;
  cancelable?: boolean;
}

export interface ListAutoRollPostedChargesResponse {
  ruleId?: string;
  serviceRequestId?: string;
  charges?: NurseStationAutoRollPostedChargeRowDto[];
}

/** 新增自动计费配置 */
export interface CreateAutoGenRuleRequest {
  encounterId: string;
  patientId: string;
  ruleName: string;
  activityId: string;
  chargeDefinitionId: string;
  quantityValue?: number | string;
  performingOrgId?: string;
  priority?: number;
  remark?: string;
  /** 滚费业务时刻 HH:mm:ss，默认 23:59:59 */
  businessTime?: string;
  billingPractitionerId?: string;
}

export interface CreateAutoGenRuleResponse {
  ruleId?: string;
}

/** 更新自动计费配置 */
export interface UpdateAutoGenRuleRequest {
  ruleId: string;
  encounterId: string;
  patientId: string;
  ruleName?: string;
  activityId?: string;
  chargeDefinitionId?: string;
  quantityValue?: number | string;
  performingOrgId?: string;
  priority?: number;
  remark?: string;
  businessTime?: string;
  billingPractitionerId?: string;
}

export interface UpdateAutoGenRuleResponse {
  success?: boolean;
}

/** 删除 / 启停自动计费配置 */
export interface AutoGenRuleMutationRequest {
  ruleId: string;
  encounterId: string;
  patientId: string;
}

export interface AutoGenRuleMutationResponse {
  success?: boolean;
}
