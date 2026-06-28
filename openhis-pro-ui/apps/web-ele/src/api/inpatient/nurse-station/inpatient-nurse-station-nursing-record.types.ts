/** 住院护士站护理记录（nurseStationNursingRecord）请求/响应类型 */

export interface NurseStationNursingRecordViewModeOptionDto {
  code?: null | string;
  defaultSelected?: boolean | null;
  displayText?: null | string;
}

export interface NurseStationNursingRecordEnumOptionDto {
  label?: null | string;
  value?: null | string;
}

export interface NurseStationNursingRecordRowDto {
  bloodPressure?: null | string;
  conditionMeasure?: null | string;
  consciousness?: null | string;
  heartRate?: null | string;
  intakeAmount?: null | string;
  intakeName?: null | string;
  intakeRoute?: null | string;
  nurseSignature?: null | string;
  outputAmount?: null | string;
  outputColorCharacter?: null | string;
  outputName?: null | string;
  oxygenFlow?: null | string;
  oxygenMode?: null | string;
  oxygenRate?: null | string;
  oxygenSaturation?: null | string;
  pupilLeft?: null | string;
  pupilRight?: null | string;
  pulse?: null | string;
  recordDate?: null | string;
  recordTime?: null | string;
  respiratory?: null | string;
  skinStatus?: null | string;
  temperature?: null | string;
  tubeCare?: null | string;
  vitalSignId?: null | string;
}

export interface NurseStationNursingRecordPaperHeaderDto {
  admitAt?: null | string;
  age?: null | string;
  bedNo?: null | string;
  dischargeAt?: null | string;
  gender?: null | string;
  inpatientNo?: null | string;
  name?: null | string;
  wardName?: null | string;
}

export interface LoadNurseStationNursingRecordInitResponse {
  defaultWeekRangeDescription?: null | string;
  viewModeOptions?: NurseStationNursingRecordViewModeOptionDto[] | null;
  vitalSignItemOptions?: NurseStationNursingRecordEnumOptionDto[] | null;
}

export interface ListNurseStationNursingRecordsRequest {
  encounterId: string;
  patientId: string;
  recordedAtFrom?: string;
  recordedAtTo?: string;
  sortAsc?: boolean;
}

export interface ListNurseStationNursingRecordsResponse {
  paperHeader?: NurseStationNursingRecordPaperHeaderDto | null;
  rows?: NurseStationNursingRecordRowDto[] | null;
}

export interface SaveNurseStationNursingRecordBatchItemRequest {
  bloodPressure?: null | string;
  conditionMeasure?: null | string;
  consciousness?: null | string;
  heartRate?: null | string;
  intakeAmount?: null | string;
  intakeName?: null | string;
  intakeRoute?: null | string;
  outputAmount?: null | string;
  outputColorCharacter?: null | string;
  outputName?: null | string;
  oxygenFlow?: null | string;
  oxygenMode?: null | string;
  oxygenRate?: null | string;
  oxygenSaturation?: null | string;
  pupilLeft?: null | string;
  pupilRight?: null | string;
  pulse?: null | string;
  recordDate?: string;
  recordTime?: null | string;
  respiratory?: null | string;
  rowType?: null | string;
  skinStatus?: null | string;
  temperature?: null | string;
  tubeCare?: null | string;
  vitalSignId?: null | string;
}

export interface SaveNurseStationNursingRecordsBatchRequest {
  encounterId: string;
  patientId: string;
  records: SaveNurseStationNursingRecordBatchItemRequest[];
}

export interface SaveNurseStationNursingRecordsBatchResponse {
  rows?: NurseStationNursingRecordRowDto[] | null;
  savedCount?: null | number;
}

export interface DeleteNurseStationNursingRecordRequest {
  encounterId: string;
  patientId: string;
  vitalSignId: string;
}

export interface DeleteNurseStationNursingRecordResponse {
  success?: boolean;
}
