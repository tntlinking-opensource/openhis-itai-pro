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
  statusEnum?: null | number;
}

/** getConditionDefinitionMetadata：分页行与总数（兼容旧 {@code records}/{@code total} 与统一 {@code content}/{@code totalCount}） */
export interface ConditionDefinitionPageResponse {
  total?: null | number | string;
  totalCount?: null | number | string;
  totalElements?: null | number | string;
  pages?: null | number;
  current?: null | number;
  size?: null | number;
  pageNo?: null | number;
  pageSize?: null | number;
  records?: ConditionDefinitionRowResponse[] | null;
  content?: ConditionDefinitionRowResponse[] | null;
  rows?: ConditionDefinitionRowResponse[] | null;
}

/** saveDoctorDiagnosis */
export interface SaveEncounterDiagnosisChildRequest {
  conditionId?: null | string;
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
  savedConditionIds?: null | string[];
}

/** delEncounterDiagnosis */
export interface DeleteEncounterDiagnosisRequest {
  conditionId: string;
  encounterId: string;
}

export interface DeleteEncounterDiagnosisResponse {
  success?: boolean | null;
  message?: null | string;
}
