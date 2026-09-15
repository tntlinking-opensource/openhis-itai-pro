/**
 * 门诊医生站 · 诊断录入 · 与后端 Request/Response DTO 字段名一致（camelCase）。
 * 凡 Java `Long` 主键/外键：请求体与前端持有的值一律用 `string`，禁止 `Number(...)` 以免精度丢失。
 */

/** getEncounterDiagnosis */
export interface GetEncounterDiagnosisRequest {
  encounterId: string;
  patientId: string;
}

/** 就诊诊断行（与 EncounterDiagnosisRowDto 对齐） */
export interface EncounterDiagnosisRowDto {
  conditionId?: null | string;
  encounterDiagnosisId?: null | string;
  conditionDefinitionId?: null | string;
  conditionCode?: null | string;
  conditionDefinitionName?: null | string;
  /** 诊断类别展示文案（列表回显用，优先于 medicineType） */
  diagnosisKindText?: null | string;
  /** WESTERN | TCM */
  medicineType?: null | string;
  /** 与 medicineType 同义，读模型快照字段 */
  diagnosisKind?: null | string;
  /** 中医病证组号（同组病+证共用） */
  tcmSyndromeGroup?: null | string;
  /** 目录来源码：tcm_dx / tcm_syndrome 等 */
  sourceEnum?: null | string;
  /** 合并展示行：证候事实 id */
  syndromeConditionId?: null | string;
  /** 合并展示行：证候目录 id */
  syndromeDefinitionId?: null | string;
  verificationStatusEnum?: null | number;
  verificationStatusEnumText?: null | string;
  mainDiagnosisFlag?: null | number;
  diagnosisSortNo?: null | number;
  medicalTypeCode?: null | string;
  diagnosisDesc?: null | string;
  iptDiseaseTypeCode?: null | string;
  /** 住院患者疾病诊断类型展示文案 */
  iptDiseaseTypeText?: null | string;
  /** 医保诊断编码；未对码时为空 */
  ybNo?: null | string;
}

export interface EncounterDiagnosisResponse {
  diagnosisList?: EncounterDiagnosisRowDto[] | null;
  /** 与统一分页字段并存时，优先使用 {@link diagnosisList}；无则读 {@code content} */
  content?: EncounterDiagnosisRowDto[] | null;
  totalCount?: null | number;
}

/** getConditionDefinitionBusinessClass */
export interface GetConditionDefinitionBusinessClassRequest {
  patientId: string;
}

export interface CommonDiagnosisRowDto {
  conditionDefinitionId?: null | string;
  syndromeDefinitionId?: null | string;
  conditionCode?: null | string;
  conditionDefinitionName?: null | string;
  /** 兼容旧读模型字段 */
  conditionName?: null | string;
  medicineType?: null | string;
  diagnosisKind?: null | string;
  tcmSyndromeGroup?: null | string;
  sourceEnum?: null | number;
  sourceEnumText?: null | string;
  /** 条件模板主键：个人/科室常用解绑用 */
  templateId?: null | string;
}

/** 与后端 OptionDto 对齐 */
export interface DiagnosisOptionDto {
  value?: null | string;
  label?: null | string;
}

export interface CommonDiagnosesResponse {
  patientHistoryList?: CommonDiagnosisRowDto[] | null;
  doctorCommonUseList?: CommonDiagnosisRowDto[] | null;
  userPersonalList?: CommonDiagnosisRowDto[] | null;
  organizationList?: CommonDiagnosisRowDto[] | null;
  /** 住院医生站：诊断类型列（患者疾病诊断类型）下拉 */
  iptDiseaseTypeOptions?: DiagnosisOptionDto[] | null;
  /** 门诊医生站：诊断类型列（医保医疗类别 YbMedType）下拉 */
  medicalTypeOptions?: DiagnosisOptionDto[] | null;
}

/** getPatientOpspFilingDiagnosis */
export interface GetPatientOpspFilingDiagnosisRequest {
  encounterId: string;
  patientId: string;
}

/** 慢特病备案可导入行 */
export interface OpspFilingDiagnosisRowDto {
  opspDiseCode?: null | string;
  opspDiseName?: null | string;
  beginDate?: null | string;
  endDate?: null | string;
  valid?: boolean | null;
  /** matched / unmatched / expired / alreadyOnEncounter / ambiguous */
  matchStatus?: null | string;
  matchHint?: null | string;
  conditionDefinitionId?: null | string;
  conditionCode?: null | string;
  conditionDefinitionName?: null | string;
  matchField?: null | string;
  diagnosisKind?: null | string;
  /** 医保医疗类别：140104 门诊慢病 / 140201 门诊特病（5302A 分查） */
  medicalTypeCode?: null | string;
}

export interface PatientOpspFilingDiagnosisResponse {
  /** false 时前端隐藏「慢病备案」入口（无 CHS） */
  opspFilingImportEnabled?: boolean | null;
  filingList?: OpspFilingDiagnosisRowDto[] | null;
}

/** ensureOpspFilingDiagnosis：未匹配慢病备案点击时补录 chronic_spec */
export interface EnsureOpspFilingDiagnosisRequest {
  encounterId: string;
  patientId: string;
  opspDiseCode: string;
  opspDiseName?: null | string;
}

export interface EnsureOpspFilingDiagnosisResponse {
  opspDiseCode?: null | string;
  opspDiseName?: null | string;
  conditionDefinitionId?: null | string;
  conditionCode?: null | string;
  conditionDefinitionName?: null | string;
  diagnosisKind?: null | string;
  /** 医保医疗类别：140104 / 140201 */
  medicalTypeCode?: null | string;
  /** true：本次对 chronic_spec 新建或启用；false：复用已有目录未写库 */
  created?: boolean | null;
}

/** getConditionDefinitionMetadata */
export interface GetConditionDefinitionMetadataRequest {
  /**
   * 中西医筛选：`1` 西医，`2` 中医全量；`5` 中医疾病，`6` 中医证候（与后端 diagnosisKindCode 对齐）。
   * @deprecated 请使用 {@link diagnosisKindCode}
   */
  medicineTypeCode?: null | string;
  diagnosisKindCode?: null | string;
  keyword?: null | string;
  pageNo: number;
  pageSize: number;
}

export interface ConditionDefinitionRowResponse {
  conditionDefinitionId?: null | string;
  conditionCode?: null | string;
  conditionName?: null | string;
  /** 读模型展示名（如「西医」）；与 {@link medicineType} 编码并存 */
  diagnosisKindText?: null | string;
  pyStr?: null | string;
  wbStr?: null | string;
  /** 如 WESTERN / TCM，与后端 {@code diagnosisKind} 对齐 */
  medicineType?: null | string;
  sourceEnum?: null | number;
  sourceEnumText?: null | string;
  ybFlag?: null | number;
  ybFlagText?: null | string;
  /** 医保诊断编码；未对码时为空，列表展示「-」 */
  ybNo?: null | string;
  statusEnum?: null | number;
}

/** getConditionDefinitionMetadata：分页行与总数（对齐后端 PagedResult：content / totalCount） */
export interface ConditionDefinitionPageResponse {
  content?: ConditionDefinitionRowResponse[] | null;
  totalCount?: null | number | string;
  /** @deprecated 优先读 {@link totalCount} */
  total?: null | number | string;
  pageNo?: null | number;
  pageSize?: null | number;
}

/** saveDoctorDiagnosis */
export interface SaveEncounterDiagnosisChildRequest {
  encounterDiagnosisId?: null | string;
  conditionDefinitionId: string;
  conditionCode: string;
  conditionName: string;
  verificationStatusEnum?: null | number;
  ybNo?: null | string;
  mainDiagnosisFlag?: null | number;
  diagnosisSortNo?: null | number;
  medicalTypeCode?: null | string;
  diagnosisDesc?: null | string;
  iptDiseaseTypeCode?: null | string;
  categoryCode?: null | string;
  severityCode?: null | string;
}

export interface SaveEncounterDiagnosisRequest {
  encounterId: string;
  patientId: string;
  diagnosisChildList: SaveEncounterDiagnosisChildRequest[];
}

export interface SaveEncounterDiagnosisResponse {
  savedCount?: null | number;
  savedEncounterDiagnosisIds?: null | string[];
  /** @deprecated 请用 savedEncounterDiagnosisIds */
  savedConditionIds?: null | string[];
}

/** delEncounterDiagnosis */
export interface DeleteEncounterDiagnosisRequest {
  encounterDiagnosisId: string;
  encounterId: string;
  patientId?: string;
}

export interface DeleteEncounterDiagnosisResponse {
  success?: boolean | null;
  message?: null | string;
}

/** switchMainEncounterDiagnosis */
export interface SwitchMainEncounterDiagnosisRequest {
  encounterDiagnosisId: string;
  encounterId: string;
  patientId?: string;
}

export interface SwitchMainEncounterDiagnosisResponse {
  success?: boolean | null;
  message?: null | string;
}

/** bindCommonDiagnosis（IF-DOR-08A） */
export interface BindCommonDiagnosisRequest {
  conditionDefinitionId: string;
  /** 中医证候定义主键（可空） */
  syndromeDefinitionId?: null | string;
  saveAsPersonal?: boolean;
  saveAsDepartment?: boolean;
}

export interface BindCommonDiagnosisResponse {
  success?: boolean | null;
  message?: null | string;
  personalBoundCount?: null | number;
  departmentBoundCount?: null | number;
}

/** listMaintainableCommonDiagnoses：可维护常用诊断行（含模板主键） */
export interface MaintainableCommonDiagnosisRowDto {
  templateId?: null | string;
  conditionDefinitionId?: null | string;
  conditionCode?: null | string;
  conditionDefinitionName?: null | string;
  diagnosisKind?: null | string;
  diagnosisKindText?: null | string;
  ybNo?: null | string;
  sortWeight?: null | number;
}

export interface ListMaintainableCommonDiagnosesResponse {
  personalList?: MaintainableCommonDiagnosisRowDto[] | null;
  departmentList?: MaintainableCommonDiagnosisRowDto[] | null;
}

/** unbindCommonDiagnosis */
export interface UnbindCommonDiagnosisRequest {
  templateIds: string[];
}

export interface UnbindCommonDiagnosisResponse {
  success?: boolean | null;
  message?: null | string;
  unboundCount?: null | number;
}
