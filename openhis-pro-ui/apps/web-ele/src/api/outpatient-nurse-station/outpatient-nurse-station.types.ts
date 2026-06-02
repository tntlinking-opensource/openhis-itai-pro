/**
 * 门诊护士站应用服务请求/响应类型（与后端 camelCase、Long→JSON 字符串约定一致）。
 */

import type {
  AdviceRequestExtensionSnapshotDto,
  ProcedurePerformerItemDto,
} from '#/api/doctor-station/advice.types';

/** 分页查询护士站患者队列 */
export interface PageNurseStationPatientQueueRequest {
  organizationId: string;
  endDate?: null | string;
  keyword?: string;
  /** 1-based，与后端 AbstractPageQuery 一致（B.10.11） */
  pageNo?: number;
  pageSize?: number;
  startDate?: null | string;
}

export interface NurseStationPatientQueueItemDto {
  ageText?: null | string;
  birthDate?: null | string;
  encounterId: string;
  encounterTime?: null | string;
  genderEnum?: null | string;
  genderEnumText?: null | string;
  patientName?: null | string;
  slipNo?: null | string;
}

export interface PageNurseStationPatientQueueResponse {
  records: NurseStationPatientQueueItemDto[];
  total: number | string;
}

/** 按就诊查询处置单 */
export interface QueryEncounterTreatmentSheetRequest {
  encounterId: string;
  serviceCategoryCode?: null | string;
}

export interface NurseStationTreatmentLineDto {
  cancelCount?: null | number;
  chargeStatusEnum?: null | string;
  chargeStatusEnumText?: null | string;
  displayStatusEnum?: null | string;
  displayStatusEnumText?: null | string;
  deviceCategoryCode?: null | string;
  dispenseId?: null | string;
  dispenseStatusEnum?: null | string;
  dispenseStatusEnumText?: null | string;
  displayTotalPrice?: null | number | string;
  encounterId?: null | string;
  groupId?: null | string;
  itemCode?: null | string;
  itemName?: null | string;
  medCategoryCode?: null | string;
  medicationExecuteNum?: null | number;
  orderType?: null | string;
  performCount?: null | number;
  plannedPerformCount?: null | number;
  pyStr?: null | string;
  quantity?: null | number | string;
  refundPairRequestId?: null | string;
  requestId: string;
  requestTable: string;
  serviceCategoryCode?: null | string;
  sortNumber?: null | number;
  specText?: null | string;
  unitCode?: null | string;
  unitPrice?: null | number | string;
  wbStr?: null | string;
}

export interface QueryEncounterTreatmentSheetResponse {
  deviceLines: NurseStationTreatmentLineDto[];
  medicationLines: NurseStationTreatmentLineDto[];
  serviceLines: NurseStationTreatmentLineDto[];
}

/** 分页查询诊疗执行记录 */
export interface ListProcedureRecordsForNurseStationRequest {
  categoryEnum?: null | string;
  encounterId: string;
  endTime?: null | string;
  pageNo?: number;
  pageSize?: number;
  requestId?: null | string;
  startTime?: null | string;
  statusEnum?: null | string;
}

export interface OrderExecutionSheetItemDto {
  categoryEnum?: null | string;
  encounterId?: null | string;
  occurrenceTime?: null | string;
  requestExtensions?: AdviceRequestExtensionSnapshotDto | null;
  performers?: ProcedurePerformerItemDto[] | null;
  patientId?: null | string;
  procedureId: string;
  recordedTime?: null | string;
  recorderId?: null | string;
  requestId?: null | string;
  requestTable?: null | string;
  resultText?: null | string;
  statusEnum?: null | string;
}

export interface ListProcedureRecordsForNurseStationResponse {
  records: OrderExecutionSheetItemDto[];
  total: number | string;
}

/** 批量处置执行 */
export interface BatchPerformDispositionRequest {
  deviceDispenseIds?: string[];
  encounterId: string;
  inventoryLocationId?: null | string;
  medicationRequestIds?: string[];
  operatorId: string;
  performerPractitionerId?: null | string;
  serviceRequestIds?: string[];
}

export interface BatchPerformDispositionResponse {
  completedDeviceDispenseIds?: string[];
  performedMedicationIds?: string[];
  performedServiceIds?: string[];
  success?: boolean;
}

/** IF-NUR-03B 净剩可取消执行记录查询 */
export interface ListNetRemainingProcedureRecordsForNurseStationRequest {
  encounterId: string;
  requestId?: null | string;
  requestTable?: null | string;
}

export interface NetRemainingProcedureRecordItemDto {
  procedureId: string;
  requestId?: null | string;
  requestTable?: null | string;
  statusEnum?: null | string;
  statusEnumText?: null | string;
  categoryEnum?: null | string;
  encounterId?: null | string;
  patientId?: null | string;
  occurrenceTime?: null | string;
  recordedTime?: null | string;
  recorderId?: null | string;
  resultText?: null | string;
}

export interface ListNetRemainingProcedureRecordsForNurseStationResponse {
  records?: NetRemainingProcedureRecordItemDto[];
}

/** IF-NUR-05 批量取消执行 */
export interface BatchCancelDispositionRequest {
  encounterId: string;
  operatorId: string;
  statusReasonText: string;
  procedureIds?: string[];
  deviceDispenseIds?: string[];
}

export interface BatchCancelDispositionResponse {
  success?: boolean;
  cancelledProcedureIds?: string[];
  revertedDeviceDispenseIds?: string[];
}
