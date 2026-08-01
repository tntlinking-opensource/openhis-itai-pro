/** 住院护士站皮试管理（nurseStationSkinTest）请求/响应类型 */

export interface NurseStationSkinTestEnumOptionDto {
  code: string;
  displayText: string;
}

export interface NurseStationSkinTestLotOptionDto {
  locationId?: null | string;
  lotNumber?: null | string;
  medicationId?: null | string;
}

export interface NurseStationSkinTestRowDto {
  allergyRecordId?: null | string;
  checkPractitionerId?: null | string;
  checkPractitionerName?: null | string;
  clinicalStatusCode?: null | string;
  clinicalStatusText?: null | string;
  criticalityCode?: null | string;
  criticalityText?: null | string;
  description?: null | string;
  encounterId?: null | string;
  /** 皮试检查是否已执行（可登记结果） */
  executedFlag?: boolean | null;
  lotNumber?: null | string;
  lotNumberOptions?: NurseStationSkinTestLotOptionDto[] | null;
  medicationId?: null | string;
  medicationName?: null | string;
  medicationRequestId: string;
  openDoctorId?: null | string;
  openDoctorName?: null | string;
  patientId?: null | string;
  performLocationId?: null | string;
  practitionerId?: null | string;
  practitionerName?: null | string;
  /** @deprecated 后端 v1.4 已改为 executedFlag；保留兼容旧响应 */
  pricedFlag?: boolean | null;
  /** 已登记只读（allergyRecordId 非空） */
  readOnlyFlag?: boolean | null;
  /** 登记/开始时间（cln_allergy_intolerance.recorded_date） */
  recordedDate?: null | string;
  /** 末次反应时间（cln_allergy_intolerance.last_occurrence） */
  lastOccurrence?: null | string;
  severityCode?: null | string;
  severityText?: null | string;
  /** companion 皮试检查 SR id（列表行主键） */
  skinTestServiceRequestId?: null | string;
  verificationStatusCode?: null | string;
  verificationStatusText?: null | string;
}

export interface LoadNurseStationSkinTestInitResponse {
  clinicalStatusOptions?: NurseStationSkinTestEnumOptionDto[] | null;
  criticalityOptions?: NurseStationSkinTestEnumOptionDto[] | null;
  nurseOptions?: { label?: null | string; value?: null | number | string }[] | null;
  severityOptions?: NurseStationSkinTestEnumOptionDto[] | null;
  verificationStatusOptions?: NurseStationSkinTestEnumOptionDto[] | null;
}

export interface ListNurseStationSkinTestRowsRequest {
  encounterId: string;
  patientId: string;
}

export interface ListNurseStationSkinTestRowsResponse {
  rows?: NurseStationSkinTestRowDto[] | null;
}

export interface SaveNurseStationSkinTestRecordRequest {
  /** 可选；本 Tab 不计费，后端不再校验 */
  accountId?: null | string;
  allergyRecordId?: null | string;
  checkPractitionerId: string;
  clinicalStatusCode: string;
  criticalityCode?: null | string;
  description?: null | string;
  encounterId: string;
  /** 必填；须 ∈ MedicationDispense 发放批号候选 */
  lotNumber: string;
  medicationId: string;
  medicationRequestId: string;
  patientId: string;
  practitionerId: string;
  /** 登记/开始时间 */
  recordedDate?: null | string;
  /** 末次反应时间（结束时间） */
  lastOccurrence?: null | string;
  severityCode?: null | string;
  /** companion SR；多行时必传 */
  skinTestServiceRequestId?: null | string;
  verificationStatusCode: string;
}

export interface SaveNurseStationSkinTestRecordResponse {
  allergyRecordId?: null | string;
  skinTestServiceRequestId?: null | string;
}

export interface DeleteNurseStationSkinTestRecordRequest {
  encounterId: string;
  allergyRecordId: string;
}

export interface DeleteNurseStationSkinTestRecordResponse {
  success?: boolean;
}
