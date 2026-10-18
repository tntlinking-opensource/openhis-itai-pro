/**
 * 医技执行应用服务请求/响应类型（Long 主键一律 string）。
 */

import type { ServiceRequestDetailSummaryDto } from '#/api/doctor-station/advice.types';
import type { PageParams } from '#/types/api-response';

/** 就诊类别：门诊 / 住院 */
export type MedicalTechEncounterScope = 'INPATIENT' | 'OUTPATIENT';

/** 住院队列在院态：在院（默认）/ 不在院（已出院） */
export type MedicalTechInpatientStayFilter = 'IN_HOSPITAL' | 'NOT_IN_HOSPITAL';

/**
 * 队列执行态：待执行 / 已执行。
 * 门诊按本站诊疗项目+耗材整单；住院仅按诊疗项目（不看耗材）。
 */
export type MedicalTechExecutionQueueFilter = 'EXECUTED' | 'PENDING';

/** 分页查询医技患者队列 */
export interface PageMedicalTechPatientQueueRequest extends PageParams {
  endDate?: null | string;
  encounterScope: MedicalTechEncounterScope;
  /**
   * 队列执行态；空=不按执行态收窄。默认前端传 PENDING。
   */
  executionQueueFilter?: MedicalTechExecutionQueueFilter | null;
  /**
   * 住院在院态筛选；仅住院生效。空=在院。
   */
  inpatientStayFilter?: MedicalTechInpatientStayFilter | null;
  keyword?: string;
  /** true=仅本站仍有待发耗材的就诊 */
  pendingDeviceOnly?: boolean;
  startDate?: null | string;
}

export interface MedicalTechPatientQueueItemDto {
  /** 展示用账户余额（住院；与 availableBalance 一致） */
  accountBalance?: null | number | string;
  accountBalanceDisplayMode?: null | string;
  accountBalanceSubText?: null | string;
  accountBalanceSummaryText?: null | string;
  ageText?: null | string;
  availableBalance?: null | number | string;
  birthDate?: null | string;
  contractDisplay?: null | string;
  encounterId: string;
  /**
   * 就诊开始时间：门诊=挂号/就诊；住院=入院时间
   */
  encounterTime?: null | string;
  /**
   * 出院时间（仅住院；未出院可空）
   */
  dischargeTime?: null | string;
  genderEnum?: null | string;
  genderEnumText?: null | string;
  hasPendingDevice?: boolean | null;
  /** 身份证号（信息条回显） */
  idCard?: null | string;
  lowBalanceWarning?: boolean | null;
  /** 护理级别（仅住院） */
  nursingLevelName?: null | string;
  patientName?: null | string;
  pendingSettlementTotal?: null | number | string;
  /** 手机号（信息条回显） */
  phone?: null | string;
  prepayNetBalance?: null | number | string;
  slipNo?: null | string;
  /** 就诊状态码（仅住院） */
  statusEnum?: null | string;
  /** 就诊状态展示（仅住院） */
  statusEnumText?: null | string;
  /** 病区名称（仅住院） */
  wardName?: null | string;
  /** 病房名称（仅住院） */
  houseName?: null | string;
  /** 床位名称（仅住院） */
  bedName?: null | string;
  /** 诊断摘要（仅住院） */
  diagnosisSummary?: null | string;
}

export interface PageMedicalTechPatientQueueResponse {
  content: MedicalTechPatientQueueItemDto[];
  pageNo?: number;
  pageSize?: number;
  totalCount: number;
}

/** 加载医技执行单 */
export interface LoadMedicalTechExecutionSheetRequest {
  encounterId: string;
  encounterScope: MedicalTechEncounterScope;
  /**
   * 与左侧队列同码：PENDING/EXECUTED；同时收窄右侧诊疗（门诊另收窄耗材）。
   * 空=不按执行态收窄。
   */
  executionQueueFilter?: MedicalTechExecutionQueueFilter | null;
  serviceCategoryCode?: null | string;
}

export interface MedicalTechExecutionLineDto {
  cancelledCount?: null | number;
  chargeAmount?: null | number | string;
  chargeStatusEnum?: null | string;
  chargeStatusEnumText?: null | string;
  completedCount?: null | number;
  dispenseId?: null | string;
  dispenseStatusEnum?: null | string;
  dispenseStatusEnumText?: null | string;
  itemCode?: null | string;
  itemName?: null | string;
  lotNumber?: null | string;
  plannedExecutionCount?: null | number;
  prescriptionNo?: null | string;
  /** 申请业务号（采血贴/LIS applyNo） */
  busNo?: null | string;
  quantity?: null | number | string;
  remark?: null | string;
  /** 申请单部位/样本明细（AR 前缀；与医生站/护士站同口径） */
  serviceRequestDetails?: ServiceRequestDetailSummaryDto[] | null;
  requestId: string;
  requestTable: string;
  /** 是否可勾选（尚可执行或净剩可取消；批量执行/取消共用勾选列） */
  selectable?: boolean | null;
  /** 诊疗类目 code（检验 22） */
  serviceCategoryCode?: null | string;
  specification?: null | string;
  /** 合管检验设备主键；同设备一组一张，名称拼接，张数取组内最大 */
  specimenTubeMergeDeviceId?: null | string;
  specimenTubeMergeGroupNo?: null | number | string;
  /** 医嘱优先级 code（加急=EM） */
  priorityEnum?: null | string;
  /** 开方医生主键 */
  requesterId?: null | string;
  /** 开方医生姓名 */
  requesterName?: null | string;
  /** 开方科室主键 */
  requestingOrgId?: null | string;
  /** 开方科室名称 */
  requestingOrgName?: null | string;
  /** 执行科室主键（ServiceRequest.org_id；共看组按此筛项目） */
  executingOrgId?: null | string;
  /** 执行科室名称 */
  executingOrgName?: null | string;
  unitName?: null | string;
  unitPrice?: null | number | string;
}

/** 共看组项目类型筛选项（CT/DR 等，按执行科室区分；配置匹配用 busNo） */
export interface MedicalTechExecutingOrgOptionDto {
  organizationBusNo?: null | string;
  organizationId: string;
  organizationName?: null | string;
}

/** 开立带出耗材预览（住院只读；不参与执行/取消） */
export interface MedicalTechBoundSupplyItemDto {
  bindingId?: null | string;
  deviceDefinitionId?: null | string;
  /** 目录编号（耗材定义 bus_no） */
  itemCode?: null | string;
  itemName?: null | string;
  previewKey: string;
  quantity?: null | number | string;
  sourceItemName?: null | string;
  sourceRequestId?: null | string;
  specification?: null | string;
  /** 参考总价（单价 × 数量） */
  totalPrice?: null | number | string;
  unitName?: null | string;
  unitPrice?: null | number | string;
}

export interface LoadMedicalTechExecutionSheetResponse {
  /** 开立带出耗材预览（住院）；与 deviceLines 合并只读展示 */
  boundSupplyItems?: MedicalTechBoundSupplyItemDto[] | null;
  deviceLines?: MedicalTechExecutionLineDto[] | null;
  serviceLines?: MedicalTechExecutionLineDto[] | null;
  /** 共看组时非空；项目类型 CT/DR 筛（按执行科室区分），不筛患者 */
  sharedExecutingOrgFilterOptions?: MedicalTechExecutingOrgOptionDto[] | null;
}

/** 分页查询医技执行记录 */
export interface PageMedicalTechProcedureRecordsRequest extends PageParams {
  encounterId: string;
  encounterScope: MedicalTechEncounterScope;
  requestId?: null | string;
  statusEnum?: null | string;
}

export interface MedicalTechProcedureRecordDto {
  actionDisplayText?: null | string;
  operatorDisplayName?: null | string;
  orderItemDisplayName?: null | string;
  procedureId: string;
  recordedTime?: null | string;
  requestId?: null | string;
  statusEnum?: null | string;
}

export interface PageMedicalTechProcedureRecordsResponse {
  content: MedicalTechProcedureRecordDto[];
  pageNo?: number;
  pageSize?: number;
  totalCount: number;
}

/** 查询净剩可取消执行记录 */
export interface ListMedicalTechNetRemainingProceduresRequest {
  encounterId: string;
  encounterScope: MedicalTechEncounterScope;
  requestId?: null | string;
  requestTable?: null | string;
}

export interface ListMedicalTechNetRemainingProceduresResponse {
  records?: MedicalTechProcedureRecordDto[] | null;
}

/** 批量执行勾选项 */
export interface MedicalTechPerformItemDto {
  dispenseId?: null | string;
  requestId: string;
  requestTable: string;
}

export interface BatchPerformMedicalTechExecutionRequest {
  deviceItems?: MedicalTechPerformItemDto[] | null;
  deviceLocationId?: null | string;
  encounterId: string;
  encounterScope: MedicalTechEncounterScope;
  serviceItems?: MedicalTechPerformItemDto[] | null;
}

export interface BatchPerformMedicalTechExecutionResponse {
  message?: null | string;
  successCount?: null | number;
}

/** 取消诊疗项 */
export interface MedicalTechCancelProcedureItemDto {
  procedureId: string;
}

/** 取消耗材项 */
export interface MedicalTechCancelDeviceItemDto {
  dispenseId: string;
}

export interface BatchCancelMedicalTechExecutionRequest {
  deviceItems?: MedicalTechCancelDeviceItemDto[] | null;
  encounterId: string;
  encounterScope: MedicalTechEncounterScope;
  procedureItems?: MedicalTechCancelProcedureItemDto[] | null;
}

export interface BatchCancelMedicalTechExecutionResponse {
  message?: null | string;
  successCount?: null | number;
}
