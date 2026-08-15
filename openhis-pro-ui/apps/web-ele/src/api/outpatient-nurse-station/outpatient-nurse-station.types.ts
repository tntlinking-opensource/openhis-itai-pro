/**
 * 门诊护士站应用服务请求/响应类型（与后端 camelCase、Long→JSON 字符串约定一致）。
 */

import type { ServiceRequestDetailSummaryDto } from '#/api/doctor-station/advice.types';

/** 分页查询护士站患者队列 */
export interface PageNurseStationPatientQueueRequest {
  endDate?: null | string;
  keyword?: string;
  /**
   * 开立科室（可选）。空/未传=全部；非空等值匹配就诊 organization_id。
   * 须经 preserveApiIdForRequest，禁止 Number()。
   */
  orgId?: null | string;
  /** 1-based，与后端 AbstractPageQuery 一致（B.10.11） */
  pageNo?: number;
  pageSize?: number;
  startDate?: null | string;
}

export interface NurseStationPatientQueueItemDto {
  ageText?: null | string;
  birthDate?: null | string;
  /** 费用性质展示（有医保账户优先医保合同名） */
  contractDisplay?: null | string;
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
  dispensePerDuration?: null | number;
  dispenseStatusEnum?: null | string;
  dispenseStatusEnumText?: null | string;
  displayTotalPrice?: null | number | string;
  dose?: null | number | string;
  doseUnitCode?: null | string;
  doseUnitName?: null | string;
  /** 单次剂量展示串（后端；最小单位为主，括号附计量，单位相同时无括号） */
  dosageDisplay?: null | string;
  splitDose?: null | number | string;
  splitDoseUnitCode?: null | string;
  splitDoseUnitName?: null | string;
  encounterId?: null | string;
  groupId?: null | string;
  /** 是否输液/注射相关（优先 med_medication_request.infusion_flag，未落库时回落 med_medication_definition.inject_flag） */
  infusionFlag?: boolean | null;
  infusionFlagText?: null | string;
  /** 是否皮试药（med_medication_request.skin_test_flag） */
  skinTestFlag?: boolean | null;
  /** 是否中药医嘱（med_medication_request.tcm_flag） */
  tcmFlag?: boolean | null;
  itemCode?: null | string;
  itemName?: null | string;
  lastPerformerDisplay?: null | string;
  lastPerformerPractitionerId?: null | string;
  medCategoryCode?: null | string;
  medCategoryCodeText?: null | string;
  medicationExecuteNum?: null | number;
  methodCode?: null | string;
  methodName?: null | string;
  orderType?: null | string;
  performCount?: null | number;
  plannedPerformCount?: null | number;
  prescriptionNo?: null | string;
  pyStr?: null | string;
  quantity?: null | number | string;
  rateCode?: null | string;
  rateName?: null | string;
  refundPairRequestId?: null | string;
  /** 申请单行纯备注（wkf_service_request.remark） */
  remark?: null | string;
  requestId: string;
  requestTable: string;
  /** 申请单部位/样本明细摘要（AR 前缀申请单） */
  serviceRequestDetails?: ServiceRequestDetailSummaryDto[] | null;
  serviceCategoryCode?: null | string;
  serviceCategoryCodeText?: null | string;
  sortNumber?: null | number;
  specText?: null | string;
  unitCode?: null | string;
  unitName?: null | string;
  unitPrice?: null | number | string;
  wbStr?: null | string;
}

export interface QueryEncounterTreatmentSheetResponse {
  deviceLines: NurseStationTreatmentLineDto[];
  medicationLines: NurseStationTreatmentLineDto[];
  serviceLines: NurseStationTreatmentLineDto[];
}

/** 登录者管辖药房下拉（耗材房） */
export interface ListManagedSupplyRoomOptionsResponse {
  supplyRoomOptions?: Array<{ label: string; sort?: number; value: string }>;
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
  /** 操作展示文案（如「执行完成」「取消执行」） */
  actionDisplayText?: null | string;
  categoryEnum?: null | string;
  encounterId?: null | string;
  /** 对应医嘱/项目展示名 */
  orderItemDisplayName?: null | string;
  occurrenceTime?: null | string;
  /** 操作人展示名 */
  operatorDisplayName?: null | string;
  /** 操作人列标题（「执行人」或「取消执行人」） */
  operatorRoleDisplayText?: null | string;
  patientId?: null | string;
  procedureId: string;
  recordedTime?: null | string;
  recorderId?: null | string;
  requestId?: null | string;
  requestTable?: null | string;
  resultText?: null | string;
  statusEnum?: null | string;
  /** 执行状态展示文案（IF-NUR-03） */
  statusEnumText?: null | string;
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
  /** 操作/执行医务人员；未传时后端取当前登录医务人员 */
  operatorId?: null | string;
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
  /** 操作医务人员；未传时后端取当前登录医务人员 */
  operatorId?: null | string;
  statusReasonText: string;
  procedureIds?: string[];
  deviceDispenseIds?: string[];
}

export interface BatchCancelDispositionResponse {
  success?: boolean;
  cancelledProcedureIds?: string[];
  revertedDeviceDispenseIds?: string[];
}

/** 皮试表单枚举选项 */
export interface OutpatientSkinTestEnumOptionDto {
  code: string;
  displayText: string;
}

/** 皮试批号候选 */
export interface OutpatientSkinTestLotOptionDto {
  locationId?: null | string;
  lotNumber?: null | string;
  medicationId?: null | string;
}

/** 皮试列表行 */
export interface OutpatientSkinTestRowDto {
  allergyRecordId?: null | string;
  checkPractitionerId?: null | string;
  checkPractitionerName?: null | string;
  clinicalStatusCode?: null | string;
  clinicalStatusText?: null | string;
  criticalityCode?: null | string;
  criticalityText?: null | string;
  description?: null | string;
  encounterId?: null | string;
  feeSettledFlag?: boolean | null;
  lotNumber?: null | string;
  lotNumberOptions?: OutpatientSkinTestLotOptionDto[] | null;
  medicationId?: null | string;
  medicationName?: null | string;
  medicationRequestId?: null | string;
  openDoctorId?: null | string;
  openDoctorName?: null | string;
  patientId?: null | string;
  performLocationId?: null | string;
  practitionerId?: null | string;
  practitionerName?: null | string;
  /** 登记/开始时间（cln_allergy_intolerance.recorded_date） */
  recordedDate?: null | string;
  /** 末次反应时间（cln_allergy_intolerance.last_occurrence） */
  lastOccurrence?: null | string;
  serviceRequestId: string;
  severityCode?: null | string;
  severityText?: null | string;
  verificationStatusCode?: null | string;
  verificationStatusText?: null | string;
}

export interface LoadOutpatientSkinTestInitResponse {
  clinicalStatusOptions?: OutpatientSkinTestEnumOptionDto[] | null;
  criticalityOptions?: OutpatientSkinTestEnumOptionDto[] | null;
  nurseOptions?: { label?: null | string; value?: null | number | string }[] | null;
  severityOptions?: OutpatientSkinTestEnumOptionDto[] | null;
  verificationStatusOptions?: OutpatientSkinTestEnumOptionDto[] | null;
}

export interface ListOutpatientSkinTestRowsRequest {
  encounterId: string;
  patientId?: null | string;
}

export interface ListOutpatientSkinTestRowsResponse {
  rows?: OutpatientSkinTestRowDto[] | null;
}

export interface SaveOutpatientSkinTestRecordRequest {
  allergyRecordId?: null | string;
  checkPractitionerId: string;
  clinicalStatusCode: string;
  criticalityCode?: null | string;
  description?: null | string;
  encounterId: string;
  lotNumber?: null | string;
  medicationId: string;
  medicationRequestId: string;
  patientId: string;
  practitionerId: string;
  /** 登记/开始时间 */
  recordedDate?: null | string;
  /** 末次反应时间（结束时间） */
  lastOccurrence?: null | string;
  serviceRequestId: string;
  severityCode?: null | string;
  verificationStatusCode: string;
}

export interface SaveOutpatientSkinTestRecordResponse {
  allergyRecordId?: null | string;
  serviceRequestId?: null | string;
}

export interface DeleteOutpatientSkinTestRecordRequest {
  allergyRecordId: string;
  encounterId: string;
}

export interface DeleteOutpatientSkinTestRecordResponse {
  success?: boolean;
}
