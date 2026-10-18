/**
 * MRMS 病案管理 API 类型（与 zxplat-mrms application DTO 对齐）
 */

import type { PageParams } from '#/types/api-response';

/** 病案主档状态 code（大写，与 CaseRecordStatus.getCode 对齐；仅归档三态） */
export type CaseRecordStatusCode = 'ARCHIVED' | 'DRAFT' | 'PENDING_ARCHIVE';

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
  /**
   * 当前/末次病区主键（就诊上下文 wardId，string 防雪花精度丢失）。
   * 体温单/护理记录门禁与护士管辖病区比对。
   */
  wardId?: string;
  certno?: string;
  /** 医保人员编号 psn_no */
  psnNo?: string;
  /** 医保结算 ID setl_id */
  setlId?: string;
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
  /**
   * 入院科室主键（就诊 organization_id；诊断医生下拉 / 同科室门禁）。
   * loadPage 从住院上下文回填；不写入首页表。
   */
  organizationId?: string;
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
  /** 院内病案号；首次保存发号后由 saveHomepage 回传 */
  medcasno?: string;
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
  /** 实施临床路径（1中医 2西医 3否） */
  clinicalPathCode?: string;
  /** 使用医疗机构中药制剂（1是 2否） */
  tcmPrepCode?: string;
  /** 使用中医诊疗设备（1是 2否） */
  tcmDeviceCode?: string;
  /** 使用中医诊疗技术（1是 2否） */
  tcmTechCode?: string;
  /** 辨证施护（1是 2否） */
  syndromeNurseCode?: string;
  /**
   * 是否启用「中医信息」段（只读；医院 medins_type 判定，不落库）
   */
  tcmInfoEnabled?: boolean;
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
  /** 医疗付费方式 */
  payWayCode?: string;
  /** 健康卡号 */
  healthCardNo?: string;
  /** 年龄（岁） */
  ageYear?: number;
  /** 不足1周岁年龄（月） */
  ageMonth?: number;
  /** 出生地省市区 code（库列斜杠拼接，接口为数组） */
  birplcCodes?: string[];
  /** 出生地中文地名（卫统 CSD / HQMS A22） */
  birplcName?: string;
  /** 籍贯省市 code（库列斜杠拼接，接口为数组） */
  naplCodes?: string[];
  /** 籍贯中文地名（卫统 GG；HQMS A23C 用 codes→RC036） */
  naplName?: string;
  /** 现住址省市区 code（库列斜杠拼接，接口为数组） */
  currAddrCodes?: string[];
  /** 现住址详细地址 */
  currAddrDetail?: string;
  /** 现住址邮编 */
  currAddrPoscode?: string;
  /** 户口地址省市区 code（库列斜杠拼接，接口为数组） */
  resdAddrCodes?: string[];
  /** 户口详细地址 */
  resdAddrDetail?: string;
  /** 户口邮编 */
  resdAddrPoscode?: string;
  /** 工作单位及地址 */
  emprAddr?: string;
  /** 单位电话 */
  emprTel?: string;
  /** 单位邮编 */
  emprPoscode?: string;
  /** 入院病区 */
  admWard?: string;
  /** 入院床位 */
  admBed?: string;
  /** 出院病区 */
  dscgWard?: string;
  /** 出院床位 */
  dscgBed?: string;
  /** 药物过敏 1无 2有 */
  drugAllergyCode?: string;
  /** 过敏药物 */
  allergyDrugName?: string;
  /** 病理号 */
  pathologyNo?: string;
  /** 主要诊断治愈好转 */
  mainDiagCureCode?: string;
  /** 抢救次数 */
  rescueCount?: number;
  /** 抢救成功次数 */
  rescueSuccessCount?: number;
  /** 临床路径管理（异于中医 clinicalPathCode） */
  clinicPathManageCode?: string;
  /** 诊断符合：门诊与出院 */
  diagMatchOpDscg?: string;
  /** 诊断符合：入院与出院 */
  diagMatchAdmDscg?: string;
  /** 诊断符合：术前与术后 */
  diagMatchPrePost?: string;
  /** 诊断符合：临床与病理 */
  diagMatchClinicPath?: string;
  /** 诊断符合：放射与病理 */
  diagMatchRadioPath?: string;
  /** 死亡患者尸检 1是 2否 */
  autopsyCode?: string;
  /** 血型 ABO */
  aboCode?: string;
  /** Rh */
  rhCode?: string;
  /** 进修医师 */
  trainDrName?: string;
  /** 实习医师 */
  intnDrName?: string;
  /** 编码员 */
  codrName?: string;
  /** 病案质量 */
  medcasQltCode?: string;
  /** 质控日期 */
  qltctrlDate?: string;
  /** 质控医师 */
  qltctrlDrName?: string;
  /** 质控护士 */
  qltctrlNursName?: string;
  /** 拟接收医院 */
  acpMedinsName?: string;
  /** 拟接收社区/卫生院 */
  acpCommunityName?: string;
  /** 31天再住院计划 1无 2有 */
  rinp31Flag?: string;
  /** 再住院目的 */
  rinp31Purpose?: string;
  fieldSources?: Record<string, string>;
  /**
   * 费用现查结果（键=4401 费用参数代码，如 medfee_sumamt；不落库）。
   */
  fees?: Record<string, number>;
  diagnoses?: DiagnosisLineDto[];
  procedures?: ProcedureLineDto[];
  /** 重症监护行（对齐老 IN_HOS_ICUINFO） */
  icus?: IcuLineDto[];
  /** 输血行（对齐老 IN_HOS_BLDINFO） */
  blds?: BldLineDto[];
}

/** 首页编辑页 loadPage 出参（HomepagePageDto） */
export interface HomepagePageDto {
  caseRecord?: CaseRecordDetailDto;
  homepage?: HomepageDto;
  /** 是否启用中医信息（与 homepage.tcmInfoEnabled 同口径） */
  tcmInfoEnabled?: boolean;
}

export interface DiagnosisLineDto {
  id?: string;
  diagTypeCode?: string;
  sortNo?: number;
  diagCode?: string;
  diagName?: string;
  /**
   * 目录来源（不落库）：tcm_dx 中医疾病目录，tcm_syndrome 中医证候目录，其余为西医目录。
   */
  sourceEnum?: string;
  admCondCode?: string;
  /** 诊断转归/病情（1治愈 2好转 3未愈 4死亡 9其他） */
  outIllStatusCode?: string;
  /** 确诊时间（手填，YYYY-MM-DD HH:mm:ss） */
  confirmTime?: string;
  /** 说明（手填） */
  diagRemark?: string;
  /** 诊断医生 practitionerId（出院下拉，string 防精度丢失） */
  confirmDrId?: string;
  /** 诊断医生姓名（下拉选中冗余） */
  confirmDrName?: string;
  /** 是否上传医保（1是 0否） */
  needYbUp?: string;
}

/** 首页重症监护行（终改真源 mrms_homepage_icu） */
export interface IcuLineDto {
  /** 行主键（出站 ToString） */
  id?: string;
  /** 同页排序号 */
  sortNo?: number;
  /** 重症监护病房类型（ICU_TYPE） */
  scsCutdWardType?: string;
  /** 进入重症监护时间 */
  scsCutdInpoolTime?: string;
  /** 退出重症监护时间 */
  scsCutdExitTime?: string;
  /** 合计时长 天/时/分 */
  scsCutdSumDura?: string;
  /** 有效标志 */
  valiFlag?: string;
}

/** 首页输血行（终改真源 mrms_homepage_bld） */
export interface BldLineDto {
  /** 行主键（出站 ToString） */
  id?: string;
  /** 同页排序号 */
  sortNo?: number;
  /** 输血品种（bld_cat_code） */
  bldCat?: string;
  /** 输血量 */
  bldAmt?: string;
  /** 输血计量单位 */
  bldUnt?: string;
  /** 有效标志 */
  valiFlag?: string;
}

export interface ProcedureLineDto {
  id?: string;
  sortNo?: number;
  /** 国临码（落库） */
  oprnOprtCode?: string;
  oprnOprtName?: string;
  /** 手术日期 YYYY-MM-DD */
  oprnDate?: string;
  /** 业务手术时间 */
  oprnTime?: string;
  mainOprnFlag?: string;
  oprnLevelCode?: string;
  oprnIncisionCode?: string;
  /** 愈合等级（仅首页终改） */
  healGradeCode?: string;
  anstWayCode?: string;
  operDrName?: string;
  asit1Name?: string;
  asit2Name?: string;
  nurseName?: string;
  anstDrName?: string;
  anstStartTime?: string;
  anstEndTime?: string;
  specimen?: string;
  note?: string;
  sendLabFlag?: boolean;
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
  /** 实施临床路径（1中医 2西医 3否） */
  clinicalPathCode?: string;
  /** 使用医疗机构中药制剂（1是 2否） */
  tcmPrepCode?: string;
  /** 使用中医诊疗设备（1是 2否） */
  tcmDeviceCode?: string;
  /** 使用中医诊疗技术（1是 2否） */
  tcmTechCode?: string;
  /** 辨证施护（1是 2否） */
  syndromeNurseCode?: string;
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
  /** 医疗付费方式 */
  payWayCode?: string;
  /** 健康卡号 */
  healthCardNo?: string;
  /** 年龄（岁） */
  ageYear?: number;
  /** 不足1周岁年龄（月） */
  ageMonth?: number;
  /** 出生地省市区 code（库列斜杠拼接，接口为数组） */
  birplcCodes?: string[];
  /** 出生地中文地名（卫统 CSD / HQMS A22） */
  birplcName?: string;
  /** 籍贯省市 code（库列斜杠拼接，接口为数组） */
  naplCodes?: string[];
  /** 籍贯中文地名（卫统 GG；HQMS A23C 用 codes→RC036） */
  naplName?: string;
  /** 现住址省市区 code（库列斜杠拼接，接口为数组） */
  currAddrCodes?: string[];
  /** 现住址详细地址 */
  currAddrDetail?: string;
  /** 现住址邮编 */
  currAddrPoscode?: string;
  /** 户口地址省市区 code（库列斜杠拼接，接口为数组） */
  resdAddrCodes?: string[];
  /** 户口详细地址 */
  resdAddrDetail?: string;
  /** 户口邮编 */
  resdAddrPoscode?: string;
  /** 工作单位及地址 */
  emprAddr?: string;
  /** 单位电话 */
  emprTel?: string;
  /** 单位邮编 */
  emprPoscode?: string;
  /** 入院病区 */
  admWard?: string;
  /** 入院床位 */
  admBed?: string;
  /** 出院病区 */
  dscgWard?: string;
  /** 出院床位 */
  dscgBed?: string;
  /** 药物过敏 1无 2有 */
  drugAllergyCode?: string;
  /** 过敏药物 */
  allergyDrugName?: string;
  /** 病理号 */
  pathologyNo?: string;
  /** 主要诊断治愈好转 */
  mainDiagCureCode?: string;
  /** 抢救次数 */
  rescueCount?: number;
  /** 抢救成功次数 */
  rescueSuccessCount?: number;
  /** 临床路径管理（异于中医 clinicalPathCode） */
  clinicPathManageCode?: string;
  /** 诊断符合：门诊与出院 */
  diagMatchOpDscg?: string;
  /** 诊断符合：入院与出院 */
  diagMatchAdmDscg?: string;
  /** 诊断符合：术前与术后 */
  diagMatchPrePost?: string;
  /** 诊断符合：临床与病理 */
  diagMatchClinicPath?: string;
  /** 诊断符合：放射与病理 */
  diagMatchRadioPath?: string;
  /** 死亡患者尸检 1是 2否 */
  autopsyCode?: string;
  /** 血型 ABO */
  aboCode?: string;
  /** Rh */
  rhCode?: string;
  /** 进修医师 */
  trainDrName?: string;
  /** 实习医师 */
  intnDrName?: string;
  /** 编码员 */
  codrName?: string;
  /** 病案质量 */
  medcasQltCode?: string;
  /** 质控日期 */
  qltctrlDate?: string;
  /** 质控医师 */
  qltctrlDrName?: string;
  /** 质控护士 */
  qltctrlNursName?: string;
  /** 拟接收医院 */
  acpMedinsName?: string;
  /** 拟接收社区/卫生院 */
  acpCommunityName?: string;
  /** 31天再住院计划 1有 2无（与页面口径一致以后端为准） */
  rinp31Flag?: string;
  /** 再住院目的 */
  rinp31Purpose?: string;
  diagnoses?: DiagnosisLineDto[];
  procedures?: ProcedureLineDto[];
  icus?: IcuLineDto[];
  blds?: BldLineDto[];
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
  /** 页码（从 1 起；手术选码分页） */
  pageNo?: number;
  /** 每页条数 */
  pageSize?: number;
}

export interface IcdItemDto {
  /** 目录定义主键（手术 PD id；string 防精度丢失） */
  id?: string;
  code?: string;
  name?: string;
  pyStr?: string;
  /** 医保对照码；手术选码可空表示未对码 */
  ybNo?: string;
}

/** 手术 ICD 选码分页（searchIcd9Cm3） */
export interface SearchIcdPageResult {
  items?: IcdItemDto[];
  totalCount?: number;
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

/** 4401/结算上报摘要（CaseRecordUploadSummaryDto） */
export interface CaseRecordUploadSummaryDto {
  caseRecordId?: string;
  medcasno?: string;
  psnName?: string;
  dscgTime?: string;
  archivedAt?: string;
  chsUploadStatusCode?: string;
  chsUploadError?: string;
  /** 结算清单上传标志 0/1/2 */
  settleUploadStatusCode?: string;
  settleUploadError?: string;
  setlListId?: string;
  /** 医保人员编号 */
  psnNo?: string;
  /** 医保结算 ID */
  setlId?: string;
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
  /** 平台 stas_type 码：0未提交/1已提交/2审核通过/3审核不通过 */
  setlStatusCode?: string;
  /** 平台状态中文（由后端映射；展示用，勿驱动分支） */
  setlStatusText?: string;
  platformMsg?: string;
  setlinfo?: Record<string, unknown>;
  /** 4103 平台 output 全量（含 setlinfo 及子表） */
  platformOutput?: Record<string, unknown>;
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
  /** 4401 / 4101A / 4102；空按结算清单口径 */
  infno?: string;
  dscgTimeFrom?: string;
  dscgTimeTo?: string;
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
  /** 病例是否已归档（文书 PENDING_ARCHIVE/ARCHIVED） */
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
  /**
   * 当前/末次病区主键（就诊上下文 wardId，string 防雪花精度丢失）。
   * 体温单/护理记录门禁与护士管辖病区比对。
   */
  wardId?: string;
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
  /** 病例是否已归档（就诊下存在 PENDING_ARCHIVE/ARCHIVED 文书） */
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

/** 归档审批业务类别（AS-MRMS-APR-001 bizCategory） */
export type ArchiveApprovalBizCategory = 'case_homepage' | 'ip_document' | 'op_document';

/** 待归档审批列表行 */
export interface PendingArchiveListItemDto {
  bizCategory?: ArchiveApprovalBizCategory | string;
  /** 文书 id 或 caseRecordId（string，防雪花精度丢失） */
  bizId?: string;
  title?: string;
  patientName?: string;
  /** 提交人姓名 */
  submitterName?: string;
  encounterId?: string;
  statusCode?: CaseRecordStatusCode | string;
  submittedAt?: string;
  organizationId?: string;
  versionNo?: number;
}

/** 待归档审批分页参数 */
export interface PagePendingArchiveParams extends PageParams {
  /** op_document / ip_document / case_homepage */
  bizCategory: ArchiveApprovalBizCategory | string;
  keyword?: string;
}

/** 待审详情 */
export interface PendingArchiveDetailDto {
  bizCategory?: ArchiveApprovalBizCategory | string;
  bizId?: string;
  title?: string;
  statusCode?: CaseRecordStatusCode | string;
  versionNo?: number;
  /** 文书正文 JSON */
  documentContent?: string;
  /** 首页 JSON 预览（本次提交） */
  homepagePreviewJson?: string;
  /** 最近一次已通过编辑申请里的首页 JSON；没有则为空 */
  homepageBaselineJson?: string;
  rejectReason?: string;
}

export interface GetPendingArchiveDetailParams {
  bizCategory: ArchiveApprovalBizCategory | string;
  bizId: string;
}

export interface ApproveArchiveParams {
  bizCategory: ArchiveApprovalBizCategory | string;
  bizId: string;
  /** 可选质量等级（首页） */
  qualityGradeCode?: string;
}

export interface RejectArchiveParams {
  bizCategory: ArchiveApprovalBizCategory | string;
  bizId: string;
  /** 驳回原因（必填） */
  rejectReason: string;
}

export interface ApproveArchiveBatchParams {
  bizCategory: ArchiveApprovalBizCategory | string;
  bizIds: string[];
  qualityGradeCode?: string;
}

export interface RejectArchiveBatchParams {
  bizCategory: ArchiveApprovalBizCategory | string;
  bizIds: string[];
  rejectReason: string;
}
