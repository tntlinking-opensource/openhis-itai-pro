/** 医嘱校对 Tab：未校对 / 已校对 / 已停止 / 已作废 */
export type NurseStationOrderAuditTab = 'pending' | 'passed' | 'stopped' | 'suspended';

/** 长临筛选：全部 / 长期 / 临时 */
export type NurseStationOrderAuditTherapyFilter = 'all' | 'long' | 'temp';

/** 中西药 Tab：west 西药 / tcm 中药 */
export type NurseStationOrderAuditMedicineCategoryTab = 'tcm' | 'west';

/** 写操作锚点：药品申请 / 诊疗申请 */
export type NurseStationOrderAuditOrderType = 'MED_REQUEST' | 'SERVICE_REQUEST';

/** 医嘱校对列表查询 */
export interface ListNurseStationOrderAuditRequest {
  encounterIds: string[];
  auditTab: NurseStationOrderAuditTab;
  therapyType?: NurseStationOrderAuditTherapyFilter;
  medicineCategoryTab?: NurseStationOrderAuditMedicineCategoryTab;
}

/** 列表行（与后端 NurseStationOrderAuditRowDto 对齐） */
export interface NurseStationOrderAuditRowDto {
  listRowKey?: string;
  requestId?: string;
  orderType?: NurseStationOrderAuditOrderType | string;
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
  /** 是否中药饮片方（方级折叠行） */
  tcmFlag?: boolean;
  prescriptionNo?: string;
  /** 方内全部药品申请 id（整方校对/退回扩行） */
  lineRequestIds?: string[];
  sufferingFlag?: boolean;
  chineseHerbsDoseQuantity?: number | string;
}

/** 按就诊分组的医嘱列表 */
export interface NurseStationOrderAuditPatientGroupDto {
  encounterId?: string;
  orders?: NurseStationOrderAuditRowDto[];
}

/** 医嘱校对列表响应 */
export interface ListNurseStationOrderAuditResponse {
  groups?: NurseStationOrderAuditPatientGroupDto[];
}

/** 批量写操作单行 */
export interface NurseStationOrderAuditWriteItemDto {
  orderType: NurseStationOrderAuditOrderType;
  requestId: string;
}

/** 批量校对通过 */
export interface VerifyNurseStationOrdersRequest {
  items: NurseStationOrderAuditWriteItemDto[];
}

export interface VerifyNurseStationOrdersResponse {
  success?: boolean;
  processedCount?: number;
}

/** 批量退回 */
export interface RejectNurseStationOrdersRequest {
  items: NurseStationOrderAuditWriteItemDto[];
  reason?: string;
}

export interface RejectNurseStationOrdersResponse {
  success?: boolean;
  processedCount?: number;
}
