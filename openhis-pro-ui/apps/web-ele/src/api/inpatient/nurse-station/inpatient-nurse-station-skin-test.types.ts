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
  pricedFlag?: boolean | null;
  recordedDate?: null | string;
  severityCode?: null | string;
  severityText?: null | string;
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
  accountId: string;
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
  recordedDate?: null | string;
  severityCode?: null | string;
  verificationStatusCode: string;
}

export interface SaveNurseStationSkinTestRecordResponse {
  allergyRecordId?: null | string;
  firstTimePriced?: boolean | null;
  skinTestServiceRequestId?: null | string;
}

export interface DeleteNurseStationSkinTestRecordRequest {
  encounterId: string;
  allergyRecordId: string;
}

export interface DeleteNurseStationSkinTestRecordResponse {
  success?: boolean;
}
