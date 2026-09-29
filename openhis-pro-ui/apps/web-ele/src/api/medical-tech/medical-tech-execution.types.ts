/**
 * 医技执行应用服务请求/响应类型（Long 主键一律 string）。
 */

import type { PageParams } from '#/types/api-response';

/** 就诊类别：门诊 / 住院 */
export type MedicalTechEncounterScope = 'INPATIENT' | 'OUTPATIENT';

/** 住院队列在院态：在院（默认）/ 不在院（已出院） */
export type MedicalTechInpatientStayFilter = 'IN_HOSPITAL' | 'NOT_IN_HOSPITAL';

/** 分页查询医技患者队列 */
export interface PageMedicalTechPatientQueueRequest extends PageParams {
  endDate?: null | string;
  encounterScope: MedicalTechEncounterScope;
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
  encounterTime?: null | string;
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
  quantity?: null | number | string;
  remark?: null | string;
  requestId: string;
  requestTable: string;
  selectable?: boolean | null;
  /** 诊疗类目 code（检验 22） */
  serviceCategoryCode?: null | string;
  specification?: null | string;
  /** 合管检验设备主键；同设备一组一张，名称拼接，张数取组内最大 */
  specimenTubeMergeDeviceId?: null | string;
  /** 医嘱优先级 code（加急=EM） */
  priorityEnum?: null | string;
  unitName?: null | string;
  unitPrice?: null | number | string;
}

export interface LoadMedicalTechExecutionSheetResponse {
  deviceLines?: MedicalTechExecutionLineDto[] | null;
  serviceLines?: MedicalTechExecutionLineDto[] | null;
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
