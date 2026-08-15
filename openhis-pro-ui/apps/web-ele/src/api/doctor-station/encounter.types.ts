/**
 * 医生站就诊管理 · 与后端 DTO 字段名对齐（camelCase）。
 */

import type { EncounterDiagnosisRowDto } from './diagnosis.types';

/** IF-DOR-01 分页请求 */
export interface PageOutpatientInfoForDoctorStationRequest {
  organizationId: number;
  /**
   * 后端 `PageOutpatientInfoForDoctorStationRequest.encounterStatus`（EncounterStatus 持久化码）。
   * 不传时后端默认 REGISTERED/IN_PROGRESS/ON_HOLD/DISCHARGED 并集，读侧按 {@link staffPractitionerId} 做 BR-DOR-009 可见性过滤。
   */
  encounterStatus?: string;
  keyword?: string;
  startDate?: string;
  endDate?: string;
  pageNo: number;
  pageSize: number;
  /** 当前登录医师主键；必填，读侧按 BR-DOR-009 过滤可见患者 */
  staffPractitionerId?: number | string;
}

/**
 * IF-DOR-01 列表行（OutpatientInfoItemDto）
 * 与接口 JSON 一致；ID 类字段多为字符串大整数；可空字段为 `| null`。
 */
export interface OutpatientInfoItemDto {
  encounterId: string;
  encounterBusNo?: null | string;
  classEnum?: null | number;
  statusEnum?: null | string;
  statusEnumText?: null | string;
  patientId?: null | string;
  patientName?: null | string;
  genderEnum?: null | number;
  /** 读模型性别展示文案，与 `genderEnum` 二选一或并存；优先用于展示 */
  genderEnumText?: null | string;
  birthDate?: null | string;
  ageText?: null | string;
  idCard?: null | string;
  phone?: null | string;
  /** 患者地址全文（主档；医生站卡片回显） */
  address?: null | string;
  organizationId?: null | string;
  organizationName?: null | string;
  doctorId?: null | string;
  doctorName?: null | string;
  serviceTypeId?: null | string;
  serviceTypeName?: null | string;
  startTime?: null | string;
  receptionTime?: null | string;
  endTime?: null | string;
  displayOrder?: null | number;
  firstEnumText?: null | string;
  totalAmount?: null | number | string;
  paidAmount?: null | number | string;
  refundAmount?: null | number | string;
  diagnoses?: EncounterDiagnosisRowDto[] | null;
  triageLevelEnum?: null | number;
  triageTime?: null | string;
  workInjuryEncounter?: boolean | null;
  workInjuryApprovalStatusLabel?: null | string;
  workInjuryContractId?: null | number | string;
  workInjuryQualificationValid?: boolean | null;
  workInjuryNeeds2301Upload?: boolean | null;
  /** 费用性质展示（有医保账户优先医保合同名） */
  contractDisplay?: null | string;
  /** 险种类型码（310 职工 / 390 城乡居民等） */
  insutype?: null | string;
}

/** IF-DOR-01 分页响应 */
export interface PageOutpatientInfoForDoctorStationResponse {
  total: number;
  records: OutpatientInfoItemDto[];
}

/** 医生接诊写请求（与后端 JSON 字段名一致） */
export interface StartOutpatientReceptionRequest {
  encounterId: string;
  doctorId: string;
  /** 接诊时间，不传则由服务端默认当前时间 */
  receptionTime?: string;
  /** 由计划态直接接诊 */
  directFromPlanned?: boolean;
  operatorId: string;
}

/** 医生接诊写响应 */
export interface StartOutpatientReceptionResponse {
  encounterId: string;
  success: boolean;
}

/** IF-DOR-04 门诊完诊写请求 */
export interface CompleteOutpatientEncounterRequest {
  encounterId: string;
  /** 完诊时间，不传则由服务端默认当前时间 */
  completedAt?: string;
  /** 就诊小结或摘要 */
  summary?: string;
  operatorId: string;
}

/** IF-DOR-04 门诊完诊写响应 */
export interface CompleteOutpatientEncounterResponse {
  encounterId: string;
  success: boolean;
}

/** IF-DOR-06 取消接诊写请求（医生同意退号，回退待诊） */
export interface CancelOutpatientReceptionRequest {
  encounterId: string;
  operatorId: string;
}

/** IF-DOR-06 取消接诊写响应 */
export interface CancelOutpatientReceptionResponse {
  encounterId: string;
  success: boolean;
}
