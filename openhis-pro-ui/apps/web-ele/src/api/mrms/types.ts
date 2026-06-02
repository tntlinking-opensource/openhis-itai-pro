/**
 * MRMS 病案管理 API 类型（与 whale-mrms application DTO 对齐）
 */

import type { PageParams } from '#/types/api-response';

/** 病案主档状态 code */
export type CaseRecordStatusCode =
  | 'archived'
  | 'draft'
  | 'qcing'
  | 'received'
  | 'returned'
  | 'submitted';

export interface CaseRecordSummaryDto {
  id?: string;
  encounterId?: string;
  patientId?: string;
  medcasno?: string;
  statusCode?: CaseRecordStatusCode | string;
  patientName?: string;
  genderCode?: string;
  inpatientNo?: string;
  deptName?: string;
  admissionAt?: string;
  dischargeAt?: string;
  primaryDiagnosisText?: string;
}

export interface CaseRecordDetailDto extends CaseRecordSummaryDto {
  patnIptCnt?: number;
  deptCode?: string;
  attendingPractitionerId?: string;
  attendingPractitionerName?: string;
  returnReason?: string;
  qualityGradeCode?: string;
}

export interface QueryCaseRecordParams extends PageParams {
  keyword?: string;
  statusCode?: string;
  deptCode?: string;
  dischargeFrom?: string;
  dischargeTo?: string;
}

export interface HomepageDto {
  caseRecordId?: string;
  versionNo?: number;
  frozen?: boolean;
  psnName?: string;
  gendCode?: string;
  brdy?: string;
  certno?: string;
  ntlyCode?: string;
  natyCode?: string;
  prfsCode?: string;
  mrgStasCode?: string;
  currAddr?: string;
  psnTel?: string;
  conerName?: string;
  conerTel?: string;
  conerAddr?: string;
  iptNo?: string;
  admWayCode?: string;
  admCatyCode?: string;
  admCatyName?: string;
  dscgCatyCode?: string;
  dscgCatyName?: string;
  admTime?: string;
  dscgTime?: string;
  actIptDays?: number;
  dscgWayCode?: string;
  transferDeptSummary?: string;
  deptDirectorName?: string;
  chfdrName?: string;
  atddrName?: string;
  iptDrName?: string;
  respNursName?: string;
  fieldSources?: Record<string, string>;
  extension?: Record<string, unknown>;
  diagnoses?: DiagnosisLineDto[];
  procedures?: ProcedureLineDto[];
}

export interface DiagnosisLineDto {
  id?: string;
  diagTypeCode?: string;
  sortNo?: number;
  diagCode?: string;
  diagName?: string;
  admCondCode?: string;
}

export interface ProcedureLineDto {
  id?: string;
  sortNo?: number;
  oprnOprtCode?: string;
  oprnOprtName?: string;
  oprnDate?: string;
  oprnLevelCode?: string;
  oprnIncisionCode?: string;
  healGradeCode?: string;
  anstWayCode?: string;
}

export interface SaveHomepageParams {
  caseRecordId: string;
  psnName?: string;
  gendCode?: string;
  brdy?: string;
  certno?: string;
  ntlyCode?: string;
  natyCode?: string;
  prfsCode?: string;
  mrgStasCode?: string;
  currAddr?: string;
  psnTel?: string;
  conerName?: string;
  conerTel?: string;
  conerAddr?: string;
  iptNo?: string;
  admWayCode?: string;
  admCatyCode?: string;
  admCatyName?: string;
  dscgCatyCode?: string;
  dscgCatyName?: string;
  admTime?: string;
  dscgTime?: string;
  actIptDays?: number;
  dscgWayCode?: string;
  transferDeptSummary?: string;
  deptDirectorName?: string;
  chfdrName?: string;
  atddrName?: string;
  iptDrName?: string;
  respNursName?: string;
  extension?: Record<string, unknown>;
  diagnoses?: DiagnosisLineDto[];
  procedures?: ProcedureLineDto[];
}

export type RefreshSectionCode =
  | 'all'
  | 'basic'
  | 'diagnosis'
  | 'fee'
  | 'inpatient'
  | 'procedure'
  | 'staff';

export interface RefreshAutoFillParams {
  caseRecordId: string;
  sections: RefreshSectionCode[];
  forceOverwrite?: boolean;
}

export interface SearchIcdParams {
  keyword?: string;
  limit?: number;
}

export interface IcdItemDto {
  id?: string;
  code?: string;
  name?: string;
  pyStr?: string;
  wbStr?: string;
}

export interface CaseRecordActionParams {
  caseRecordId: string;
  reason?: string;
}

export interface PdfGenerateResultDto {
  url?: string;
  pdfBase64?: string;
  pageCount?: number;
}
