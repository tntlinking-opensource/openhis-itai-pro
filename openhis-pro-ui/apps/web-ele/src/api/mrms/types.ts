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

/** 病案检索列表行（CaseRecordListItemDto：以住院就诊为行，主档可空） */
export interface CaseRecordListItemDto {
  /** 住院就诊主键（列表行锚点） */
  encounterId?: string;
  /** 患者主键 */
  patientId?: string;
  /** 病案主档主键；未建档时为空 */
  caseRecordId?: string;
  medcasno?: string;
  psnName?: string;
  iptNo?: string;
  gendCode?: string;
  /** 年龄展示文案（如 26岁）；临床侧栏体温单抬头 */
  ageText?: string;
  /** 床位名（出院无活动床时为「原{房}-{床}（未占床）」） */
  bedName?: string;
  /** 病区/病室名（无活动病区时为末次已完成病区） */
  wardName?: string;
  certno?: string;
  admTime?: string;
  dscgTime?: string;
  admCatyName?: string;
  dscgCatyName?: string;
  chfdrName?: string;
  /** 入院科室主键（同科室临床门禁） */
  organizationId?: string;
  mainDiagName?: string;
  statusCode?: CaseRecordStatusCode | string;
  statusLabel?: string;
  /** 4401 首页上传状态（空=未上传） */
  chsUploadStatusCode?: string;
  /** 是否已结算 1/0（COMPLETED/DISCHARGED） */
  settledCode?: string;
  /** 费用性质/医保类型展示（同护士站 contractDisplay） */
  insuranceTypeText?: string;
  /** 结算上传状态（本期常为空） */
  settleUploadStatusCode?: string;
  /** 医嘱上传状态（本期常为空） */
  orderUploadStatusCode?: string;
  /** 病例是否已归档（住院病例查询回填；首页可空） */
  clinicalArchived?: boolean;
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
  /** 入院日期起 yyyy-MM-dd */
  admFrom?: string;
  /** 入院日期止 yyyy-MM-dd */
  admTo?: string;
  /** 出院日期起 yyyy-MM-dd */
  dscgFrom?: string;
  /** 出院日期止 yyyy-MM-dd */
  dscgTo?: string;
  /** 是否出院 1/0 */
  dischargedCode?: string;
  /** 首页上传状态 none/pending/success/failed/skipped（对齐 ChsUploadStatus） */
  homepageUploadStatus?: string;
  /** 结算上传状态（同口径；列未落库时仅 none 可命中） */
  settleUploadStatus?: string;
  /** 是否已结算 1/0 */
  settledCode?: string;
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
  conerRltsCode?: string;
  conerAddr?: string;
  trtTypeCode?: string;
  spgaNurscareDays?: number;
  lv1NurscareDays?: number;
  scdNurscareDays?: number;
  lv3NurscareDays?: number;
  otpWmDise?: string;
  wmDiseCode?: string;
  otpTcmDise?: string;
  tcmDiseCode?: string;
  brnDamgBfadmComaDura?: string;
  brnDamgAfadmComaDura?: string;
  ventUsedDura?: string;
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
  mainOprnFlag?: string;
  oprnLevelCode?: string;
  oprnIncisionCode?: string;
  healGradeCode?: string;
  anstWayCode?: string;
  operDrName?: string;
  anstDrName?: string;
  valiFlag?: string;
}

export interface SaveHomepageParams {
  /** 已建档时传；首次保存可空，此时须带 encounterId */
  caseRecordId?: string;
  /** 住院就诊主键；无 caseRecordId 时建档用 */
  encounterId?: string;
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
  conerRltsCode?: string;
  conerAddr?: string;
  trtTypeCode?: string;
  spgaNurscareDays?: number;
  lv1NurscareDays?: number;
  scdNurscareDays?: number;
  lv3NurscareDays?: number;
  otpWmDise?: string;
  wmDiseCode?: string;
  otpTcmDise?: string;
  tcmDiseCode?: string;
  brnDamgBfadmComaDura?: string;
  brnDamgAfadmComaDura?: string;
  ventUsedDura?: string;
  iptNo?: string;
  admWayCode?: string;
  admCatyCode?: string;
  admCatyName?: string;
  admTime?: string;
  dscgCatyCode?: string;
  dscgCatyName?: string;
  dscgTime?: string;
  iptDays?: number;
  transferDeptSummary?: string;
  dscgWayCode?: string;
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
  /** 4101A 平台清单流水号 */
  setlListId?: string;
  /** 一键编排中本步是否跳过 */
  skipped?: boolean;
}

export interface OneClickReportResultDto {
  homepage4401?: UploadResultDto;
  settle4101A?: UploadResultDto;
  commit4102?: UploadResultDto;
  allSuccess?: boolean;
}

/** 4103 平台结算清单查询结果 */
export interface SettleListQuery4103Dto {
  success?: boolean;
  errMsg?: string;
  infcode?: string;
  psnNo?: string;
  setlId?: string;
  setlListId?: string;
  medcasno?: string;
  psnName?: string;
  setlTime?: string;
  medfeeSumamt?: number;
  setlStatusText?: string;
  platformMsg?: string;
  setlinfo?: Record<string, unknown>;
}

export interface UploadLogDto {
  id?: string;
  caseRecordId?: string;
  contractBusNo?: string;
  /** 交易号 4401/4101A/4102/4103 */
  infno?: string;
  statusCode?: string;
  infcode?: string;
  errMsg?: string;
  requestDigest?: string;
  createdAt?: string;
}

export interface Upload4401Params {
  caseRecordId: string;
  /** 可省略：后端从就诊医保账户解析 */
  contractBusNo?: string;
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
export interface OutpatientEmrListItemDto {
  documentId?: string;
  patientId?: string;
  encounterId?: string;
  patientName?: string;
  outpatientNo?: string;
  deptName?: string;
  /** 接诊医生姓名 */
  doctorName?: string;
  /** 接诊医生医务人员主键（活动 ADMITTER；与登录 practitionerId 比对控制可编辑） */
  admitterPractitionerId?: string;
  encounterTime?: string;
  title?: string;
  documentTypeCode?: string;
  documentTypeName?: string;
  statusCode?: string;
  updatedAt?: string;
  printable?: boolean;
  /** 病例是否已归档（文书 LOCKED/ARCHIVED） */
  clinicalArchived?: boolean;
}

/** 门诊病历查询分页参数 */
export interface QueryOutpatientEmrParams extends PageParams {
  keyword?: string;
  statusCode?: string;
  /** 就诊科室主键（string，防雪花精度丢失） */
  organizationId?: string;
  /** 接诊医生医务人员主键（活动 ADMITTER） */
  admitterPractitionerId?: string;
  encounterDateFrom?: string;
  encounterDateTo?: string;
}

/**
 * 住院病例查询列表行（字段口径与 CaseRecordListItemDto 对齐，独立菜单出参）。
 * 主键一律 string，避免雪花精度丢失。
 */
export interface InpatientEmrListItemDto {
  /** 住院就诊主键（列表行锚点） */
  encounterId?: string;
  /** 患者主键 */
  patientId?: string;
  /** 病案主档主键；未建档时为空 */
  caseRecordId?: string;
  medcasno?: string;
  psnName?: string;
  iptNo?: string;
  gendCode?: string;
  /** 年龄展示文案（如 26岁）；临床侧栏体温单抬头 */
  ageText?: string;
  /** 床位名（出院无活动床时为「原{房}-{床}（未占床）」） */
  bedName?: string;
  /** 病区/病室名（无活动病区时为末次已完成病区） */
  wardName?: string;
  certno?: string;
  admTime?: string;
  dscgTime?: string;
  admCatyName?: string;
  dscgCatyName?: string;
  chfdrName?: string;
  /** 入院科室主键（同科室临床门禁） */
  organizationId?: string;
  mainDiagName?: string;
  statusCode?: CaseRecordStatusCode | string;
  statusLabel?: string;
  /** 4401 首页上传状态（空=未上传） */
  chsUploadStatusCode?: string;
  /** 是否已结算 1/0 */
  settledCode?: string;
  /** 费用性质展示 */
  insuranceTypeText?: string;
  settleUploadStatusCode?: string;
  orderUploadStatusCode?: string;
  /** 病例是否已归档（就诊下存在 LOCKED 文书） */
  clinicalArchived?: boolean;
}

/** 住院病例查询分页参数 */
export interface QueryInpatientEmrParams extends PageParams {
  keyword?: string;
  /** 入院科别名称过滤 */
  admCatyName?: string;
  /** 主治医师 practitionerId（string，防雪花精度丢失） */
  attendingPractitionerId?: string;
  /** 入院日期起 yyyy-MM-dd */
  admFrom?: string;
  /** 入院日期止 yyyy-MM-dd */
  admTo?: string;
  /** 出院日期起 yyyy-MM-dd */
  dscgFrom?: string;
  /** 出院日期止 yyyy-MM-dd */
  dscgTo?: string;
  /** 是否出院 1/0 */
  dischargedCode?: string;
}
