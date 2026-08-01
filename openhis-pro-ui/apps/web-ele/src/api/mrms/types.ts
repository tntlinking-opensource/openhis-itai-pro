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

/** 病案检索列表行（CaseRecordListItemDto） */
export interface CaseRecordListItemDto {
  caseRecordId?: string;
  medcasno?: string;
  psnName?: string;
  iptNo?: string;
  gendCode?: string;
  admTime?: string;
  dscgTime?: string;
  admCatyName?: string;
  dscgCatyName?: string;
  chfdrName?: string;
  mainDiagName?: string;
  statusCode?: CaseRecordStatusCode | string;
  statusLabel?: string;
}

/** @deprecated 使用 CaseRecordListItemDto */
export type CaseRecordSummaryDto = CaseRecordListItemDto;

/** 病案主档详情（CaseRecordDetailDto · loadPage / lookup） */
export interface CaseRecordDetailDto {
  caseRecordId?: string;
  patientId?: string;
  encounterId?: string;
  medcasno?: string;
  statusCode?: CaseRecordStatusCode | string;
  statusLabel?: string;
  patnIptCnt?: number;
  returnReason?: string;
}

export interface QueryCaseRecordParams extends PageParams {
  keyword?: string;
  statusCode?: string;
  /** 多状态 IN 过滤（与 statusCode 二选一，优先 statusCodes） */
  statusCodes?: string[];
  /** 入院科别名称过滤 */
  admCatyName?: string;
  /** 主治医师 practitionerId 过滤 */
  attendingPractitionerId?: string;
  /** 出院日期起 */
  dscgFrom?: string;
  /** 出院日期止 */
  dscgTo?: string;
}

export interface HomepageDto {
  caseRecordId?: string;
  versionNo?: number;
  frozen?: boolean;
  iptMedcasHmpgSn?: string;
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
  iptDays?: number;
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

/** 首页编辑页 loadPage 出参（HomepagePageDto） */
export interface HomepagePageDto {
  caseRecord?: CaseRecordDetailDto;
  homepage?: HomepageDto;
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
  iptNo?: string;
  admWayCode?: string;
  admCatyCode?: string;
  admTime?: string;
  dscgCatyCode?: string;
  dscgTime?: string;
  iptDays?: number;
  dscgWayCode?: string;
  chfdrName?: string;
  atddrName?: string;
  iptDrName?: string;
  extension?: Record<string, unknown>;
  diagnoses?: DiagnosisLineDto[];
  procedures?: ProcedureLineDto[];
  /** 前端表单扩展字段（保存时写入 extension） */
  admCatyName?: string;
  dscgCatyName?: string;
  transferDeptSummary?: string;
  deptDirectorName?: string;
  respNursName?: string;
  ntlyCode?: string;
  natyCode?: string;
  prfsCode?: string;
  mrgStasCode?: string;
  currAddr?: string;
  psnTel?: string;
  conerName?: string;
  conerTel?: string;
  conerAddr?: string;
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
  code?: string;
  name?: string;
  pyStr?: string;
}

export interface ReturnHomepageParams {
  caseRecordId: string;
  returnReason: string;
}

export interface PdfGenerateResultDto {
  pdfBase64?: string;
  pageCount?: number;
  renderedAt?: string;
  /** @deprecated 后端未返回，打印日志可选 */
  contentHash?: string;
}

/** 质控缺陷（QcDefectDto） */
export interface QcDefectDto {
  defectId?: string;
  caseRecordId?: string;
  ruleCode?: string;
  defectMessage?: string;
  severityCode?: QcDefectSeverityCode | string;
  statusCode?: QcDefectStatusCode | string;
  responsiblePractitionerId?: string;
  rectifiedAt?: string;
  closedAt?: string;
  createdAt?: string;
}

export type QcDefectSeverityCode = 'block' | 'info' | 'warn';

export type QcDefectStatusCode = 'closed' | 'open' | 'rectified';

/** 质控执行结果（QcRunResultDto） */
export interface QcRunResultDto {
  qcRunId?: string;
  passed?: boolean;
  openBlockCount?: number;
  openWarnCount?: number;
  defects?: QcDefectDto[];
  caseRecord?: CaseRecordDetailDto;
}

/** 质控批次摘要（QcRunSummaryDto） */
export interface QcRunSummaryDto {
  qcRunId?: string;
  triggerCode?: string;
  passed?: boolean;
  defectOpenCount?: number;
  defectWarnCount?: number;
  finishedAt?: string;
}

export interface StartQcParams {
  caseRecordId: string;
}

export interface RerunQcParams {
  caseRecordId: string;
}

export interface ListDefectsParams {
  caseRecordId: string;
  statusCode?: string;
  severityCode?: string;
}

export interface ListQcRunsParams {
  caseRecordId: string;
}

export interface DefectActionParams {
  defectId: string;
}

export interface AddManualDefectParams {
  caseRecordId: string;
  severityCode: string;
  defectMessage: string;
}

export interface ArchiveCaseRecordParams {
  caseRecordId: string;
  qualityGradeCode?: string;
}

/** 卷宗目录项（VolumeItemDto） */
export interface VolumeItemDto {
  itemType?: 'emr_document' | 'homepage' | string;
  refId?: string;
  title?: string;
  documentTypeCode?: string;
  documentTypeName?: string;
  createdAt?: string;
  printable?: boolean;
}

/** 卷宗目录（VolumeCatalogDto） */
export interface VolumeCatalogDto {
  caseRecordId?: string;
  medcasno?: string;
  homepageItem?: VolumeItemDto;
  documents?: VolumeItemDto[];
}

export interface GetVolumeCatalogParams {
  caseRecordId: string;
}

export interface PreviewVolumeParams {
  caseRecordId: string;
  documentIds?: string[];
  includeHomepage?: boolean;
}

export interface VolumePdfResultDto {
  pdfBase64?: string;
  pageCount?: number;
  renderedAt?: string;
}

/** 借阅申请（BorrowRequestItemDto） */
export interface BorrowRequestItemDto {
  id?: string;
  caseRecordId?: string;
  medcasno?: string;
  psnName?: string;
  requestTypeCode?: string;
  applicantName?: string;
  applicantDeptName?: string;
  purpose?: string;
  scopeDesc?: string;
  copyCount?: number;
  statusCode?: string;
  approveComment?: string;
  approvedAt?: string;
  borrowedAt?: string;
  dueReturnAt?: string;
  returnedAt?: string;
  createdAt?: string;
}

export interface ApplyBorrowParams {
  caseRecordId: string;
  requestTypeCode?: string;
  applicantName?: string;
  applicantDeptName?: string;
  purpose: string;
  scopeDesc?: string;
  copyCount?: number;
  patientAuthorizationInfo?: string;
}

export interface BorrowActionParams {
  borrowRequestId: string;
  comment?: string;
}

export interface BorrowLifecycleParams {
  borrowRequestId: string;
  dueReturnAt?: string;
}

export interface QueryBorrowPageParams extends PageParams {
  statusCode?: string;
  keyword?: string;
  caseRecordId?: string;
}

/** 4401 上传摘要（CaseRecordUploadSummaryDto） */
export interface CaseRecordUploadSummaryDto {
  caseRecordId?: string;
  medcasno?: string;
  psnName?: string;
  dscgTime?: string;
  archivedAt?: string;
  chsUploadStatusCode?: string;
  chsUploadError?: string;
}

export interface UploadResultDto {
  statusCode?: string;
  uploadLogId?: string;
  infcode?: string;
  errMsg?: string;
  assembledAt?: string;
}

export interface UploadLogDto {
  id?: string;
  caseRecordId?: string;
  contractBusNo?: string;
  statusCode?: string;
  infcode?: string;
  errMsg?: string;
  requestDigest?: string;
  createdAt?: string;
}

export interface Upload4401Params {
  caseRecordId: string;
  contractBusNo: string;
}

export interface RetryUploadParams {
  uploadLogId: string;
}

export interface ListUploadLogsParams {
  caseRecordId: string;
}

export interface QueryUploadPageParams extends PageParams {
  statusCode?: string;
  keyword?: string;
}

export interface UnarchiveCaseRecordParams {
  caseRecordId: string;
  unarchiveReason: string;
}

/** 门诊病历查询列表行 */
export interface OutpatientRecordListItemDto {
  documentId?: string;
  patientId?: string;
  encounterId?: string;
  patientName?: string;
  outpatientNo?: string;
  deptName?: string;
  encounterTime?: string;
  title?: string;
  documentTypeCode?: string;
  documentTypeName?: string;
  statusCode?: string;
  updatedAt?: string;
  printable?: boolean;
}

/** 门诊病历查询分页参数 */
export interface QueryOutpatientRecordParams extends PageParams {
  keyword?: string;
  statusCode?: string;
  deptName?: string;
  encounterDateFrom?: string;
  encounterDateTo?: string;
}
