/**
 * 病案首页工作台列表行（对接 CaseRecordListItemDto 后的视图模型）。
 */

export interface HomepageListRow {
  /**
   * 行键：优先 caseRecordId，未建档用 encounterId（防 Long 精度丢失）。
   */
  id: string;
  /** 住院就诊主键 */
  encounterId: string;
  /** 病案主档主键；未建档为空 */
  caseRecordId?: string;
  /** 患者主键 */
  patientId?: string;
  /** 患者姓名 */
  patientName: string;
  /**
   * 性别 AdministrativeGender code（MALE/FEMALE）；临床侧栏体温单抬头。
   */
  genderEnum?: string;
  /** 年龄展示文案（如 26岁） */
  ageText?: string;
  /**
   * 床位展示名（活动床或末次已完成床；可能带「原…（未占床）」前缀）。
   */
  bedName?: string;
  /** 病区/病室展示名 */
  wardName?: string;
  /** 住院号 */
  iptNo: string;
  /** 病案号 */
  medcasno: string;
  /** 证件号 */
  certno?: string;
  /** 科室（入院科别名） */
  admDeptName?: string;
  /** 入院科室主键（临床同科室门禁） */
  organizationId?: string;
  /** 费用性质/医保类型展示（同护士站 contractDisplay，如「市医保」） */
  insuranceTypeText?: string;
  /** 入院时间 */
  admTime?: string;
  /** 出院时间 */
  dscgTime?: string;
  /** 出院诊断摘要 */
  dischargeDiagnosisText?: string;
  /** 是否出院 1/0 */
  dischargedCode: string;
  /** 是否结算 1/0（无数据默认 0） */
  settledCode: string;
  /** 首页上传状态 code（none/pending/success/failed/skipped） */
  homepageUploadStatusCode: string;
  /** 结算上传状态 code（同口径；本期可空→none） */
  settleUploadStatusCode: string;
  /** 医嘱上传状态 code */
  orderUploadStatusCode: string;
  /** 病案工作流状态 */
  statusCode?: string;
  /** 病例是否已归档（就诊下存在 LOCKED 文书） */
  clinicalArchived?: boolean;
}

/** 列表查询表单（含 TableLayout 本地日期区间） */
export interface HomepageListQuery {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  /** 入院科别名称，空=全部 */
  admCatyName?: string;
  /** 入院时间段 [起, 止]；后端按 startTime 闭区间过滤 */
  admTimeRange?: string[];
  /** 出院时间段 [起, 止]；后端按 endTime 闭区间过滤 */
  dscgTimeRange?: string[];
  homepageUploadStatus?: string;
  settleUploadStatus?: string;
  dischargedCode?: string;
  settledCode?: string;
}
