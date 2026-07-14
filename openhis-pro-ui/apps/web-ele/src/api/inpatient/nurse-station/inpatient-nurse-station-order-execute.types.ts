/** 执行 Tab：未执行 / 已执行 / 不执行 / 取消执行 / 全部 */
export type NurseStationOrderExecuteTab = 'all' | 'cancelled' | 'executed' | 'pending' | 'skipped';

/** 长临筛选：全部 / 长期 / 临时 */
export type NurseStationOrderExecuteTherapyFilter = 'all' | 'long' | 'temp';

/** 中西药 Tab：west 西药 / tcm 中药 */
export type NurseStationOrderExecuteMedicineCategoryTab = 'tcm' | 'west';

/** 写操作锚点：药品申请 / 诊疗申请 */
export type NurseStationOrderExecuteOrderType = 'MED_REQUEST' | 'SERVICE_REQUEST';

/** 医嘱执行列表查询 */
export interface ListNurseStationOrderExecuteRequest {
  encounterIds: string[];
  executeTab: NurseStationOrderExecuteTab;
  therapyType?: NurseStationOrderExecuteTherapyFilter;
  medicineCategoryTab?: NurseStationOrderExecuteMedicineCategoryTab;
  executionDeadline: string;
}

/** 执行槽位（与后端 NurseStationOrderExecuteSlotDto 对齐） */
export interface NurseStationOrderExecuteSlotDto {
  planTime?: string;
  procedureId?: string;
  statusEnum?: string;
  recordedTime?: string;
}

/** 绑定项（列表回传、写路径原样回传） */
export interface NurseStationOrderExecuteBindItemDto {
  bindType?: string;
  bindingId?: string;
  activityId?: string;
  deviceId?: string;
  /** 展示名（与后端 displayName 对齐） */
  displayName?: string;
  /** 兼容旧字段 */
  bindDisplayName?: string;
  busNo?: string;
  specificationText?: string;
  unitPrice?: number;
  chargeItemDefinitionId?: string;
  quantityNumerator?: number;
  quantityDenominator?: number;
  unitCode?: string;
  unitCode_dictText?: string;
  priority?: number;
}

/** 列表行（与后端 NurseStationOrderExecuteRowDto 对齐） */
export interface NurseStationOrderExecuteRowDto {
  listRowKey?: string;
  orderId?: string;
  orderType?: NurseStationOrderExecuteOrderType | string;
  encounterId?: string;
  patientId?: string;
  statusEnum?: string;
  /** 请求状态展示（与后端 statusEnumText 对齐） */
  statusEnumText?: string;
  therapyEnum?: string;
  /** 长临展示（与后端 therapyEnumText 对齐） */
  therapyEnumText?: string;
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
  pendingSlots?: NurseStationOrderExecuteSlotDto[];
  executedSlots?: NurseStationOrderExecuteSlotDto[];
  skippedSlots?: NurseStationOrderExecuteSlotDto[];
  cancelledSlots?: NurseStationOrderExecuteSlotDto[];
  bindItems?: NurseStationOrderExecuteBindItemDto[];
  /** 是否中药饮片方（方级折叠行） */
  tcmFlag?: boolean;
  prescriptionNo?: string;
  /** 方内全部药品申请 id（整方执行扩行） */
  lineRequestIds?: string[];
  sufferingFlag?: boolean;
  chineseHerbsDoseQuantity?: number | string;
}

/** 按就诊分组的医嘱列表 */
export interface NurseStationOrderExecutePatientGroupDto {
  encounterId?: string;
  orders?: NurseStationOrderExecuteRowDto[];
}

/** 医嘱执行列表响应 */
export interface ListNurseStationOrderExecuteResponse {
  groups?: NurseStationOrderExecutePatientGroupDto[];
}

/** 写路径槽位（执行 / 不执行） */
export interface NurseStationOrderExecuteWriteSlotDto {
  orderId: string;
  orderType: NurseStationOrderExecuteOrderType;
  planTime: string;
  /** 执行时必填：患者账户主键（来自在院患者队列 accountId） */
  accountId?: string;
  actualTime?: string;
  bindItems?: NurseStationOrderExecuteBindItemDto[];
  prescriptionNo?: string;
  /** 中药方内全部药品申请主键（列表 lineRequestIds 回传） */
  lineRequestIds?: string[];
}

/** 批量执行医嘱 */
export interface ExecuteNurseStationOrdersRequest {
  items: NurseStationOrderExecuteWriteSlotDto[];
}

export interface ExecuteNurseStationOrdersResponse {
  success?: boolean;
  processedCount?: number;
}

/** 批量登记不执行 */
export interface SkipNurseStationOrderSlotsRequest {
  items: NurseStationOrderExecuteWriteSlotDto[];
  /** 不执行原因（必填） */
  statusReasonText: string;
}

export interface SkipNurseStationOrderSlotsResponse {
  success?: boolean;
  processedCount?: number;
}
