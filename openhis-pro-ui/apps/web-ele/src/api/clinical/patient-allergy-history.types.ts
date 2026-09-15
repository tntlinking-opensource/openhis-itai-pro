/** 患者过敏史（patientAllergyHistory）请求/响应类型 */

export interface PatientAllergyHistoryEnumOptionDto {
  code: string;
  displayText: string;
}

export interface PatientAllergyHistoryRowDto {
  allergenName?: null | string;
  categoryCode?: null | string;
  categoryText?: null | string;
  clinicalStatusCode?: null | string;
  clinicalStatusText?: null | string;
  encounterId?: null | string;
  id?: null | string;
  note?: null | string;
  patientId?: null | string;
  recordedDate?: null | string;
  severityCode?: null | string;
  severityText?: null | string;
  sourceReadOnly?: boolean | null;
  typeCode?: null | string;
  typeText?: null | string;
  verificationStatusCode?: null | string;
  verificationStatusText?: null | string;
}

export interface LoadPatientAllergyHistoryInitResponse {
  categoryOptions?: PatientAllergyHistoryEnumOptionDto[] | null;
  criticalityOptions?: PatientAllergyHistoryEnumOptionDto[] | null;
  severityOptions?: PatientAllergyHistoryEnumOptionDto[] | null;
  verificationStatusOptions?: PatientAllergyHistoryEnumOptionDto[] | null;
}

export interface ListPatientAllergyHistoryRequest {
  encounterId: string;
  patientId: string;
}

export interface ListPatientAllergyHistoryResponse {
  historyFlag?: boolean | null;
  nkdaFlag?: boolean | null;
  rows?: PatientAllergyHistoryRowDto[] | null;
  summaryText?: null | string;
}

export interface SavePatientAllergyHistoryRequest {
  categoryCode?: null | string;
  clinicalStatusCode?: null | string;
  criticalityCode?: null | string;
  description: string;
  encounterId: string;
  id?: null | string;
  note?: null | string;
  patientId: string;
  severityCode?: null | string;
  verificationStatusCode?: null | string;
}

export interface SavePatientNkdaRequest {
  encounterId: string;
  patientId: string;
  verificationStatusCode?: null | string;
}

export interface DeletePatientAllergyHistoryRequest {
  allergyRecordId: string;
  encounterId: string;
  patientId: string;
}

export interface SavePatientAllergyHistoryResponse {
  allergyRecordId?: null | string;
}
