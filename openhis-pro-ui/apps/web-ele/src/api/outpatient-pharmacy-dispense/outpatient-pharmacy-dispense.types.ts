/**
 * 门诊发药应用服务请求/响应类型（与后端 DTO 字段名 camelCase 对齐；Long 类主键用 string）。
 */

import type { ApiOptionItem } from '#/types/api-response';

/** 分页块（Spring Data 风格，字段以联调实际响应为准） */
export interface SpringPage<T> {
  content?: T[];
  totalElements?: number;
  total?: number;
  number?: number;
  size?: number;
}

export interface PractitionerOptionDto {
  practitionerId: string;
  displayName: string;
  orgId?: null | string;
  orgName?: null | string;
}

export interface LoadPharmacyDispensePageInitRequest {
  /** 当前药房库位 adm_location.id（非 organization） */
  pharmacyLocationId?: null | string;
  pageNo?: number;
  pageSize?: number;
  pharmacistRoleFilterCode?: number;
}

export interface LoadPharmacyDispensePageInitResponse {
  pharmacyOrganizationOptions?: ApiOptionItem[];
  dispenseStatusFilterOptions?: ApiOptionItem[];
  encounterClassOptions?: ApiOptionItem[];
  notPerformedReasonOptions?: ApiOptionItem[];
  preparerCandidatePage?: SpringPage<PractitionerOptionDto>;
}

export interface PagePendingDispenseEncounterQueueRequest {
  /** 时间窗下限（含），对齐 AbstractPageQuery.startDate；按就诊 startTime 收窄 */
  startDate?: null | string;
  /** 时间窗上限（含），对齐 AbstractPageQuery.endDate；按就诊 startTime 收窄 */
  endDate?: null | string;
  keyword?: null | string;
  /** 待发药 Tab：PREPARATION + IN_PROGRESS；已发药 Tab：COMPLETED；空则由后端按约定默认 */
  dispenseStatusEnumIn?: null | string[];
  /** SDD：页码 ≥1 */
  pageNo: number;
  pageSize: number;
}

export interface PendingDispenseEncounterQueueRowDto {
  encounterId: string;
  encounterBusNo?: null | string;
  patientId?: null | string;
  patientName?: null | string;
  genderEnum?: null | string;
  /** 后端解析的性别展示文案，前端只展示不翻译 */
  genderEnumText?: null | string;
  ageText?: null | string;
  organizationName?: null | string;
  doctorName?: null | string;
  statusEnum?: null | string;
  statusEnumText?: null | string;
  dispensePendingSummaryText?: null | string;
  startTime?: null | string;
  /** 患者电话（门诊上下文 phone） */
  patientPhone?: null | string;
  /** 费用性质展示名（就诊优先合同名；与医生站卡片同口径） */
  contractDisplay?: null | string;
}

export interface PagePendingDispenseEncounterQueueResponse {
  total?: number | string;
  rows?: PendingDispenseEncounterQueueRowDto[];
}

export interface PageEncounterMedicationDispenseSlipsRequest {
  encounterId: string;
  tcmOnly?: boolean;
  westernOnly?: boolean;
  dispenseStatusEnumIn?: null | string[];
  requestStatusEnumIn?: null | string[];
  pageNo: number;
  pageSize: number;
}

export interface MedicationDispenseInventoryDetailDto {
  pharmacyOrganizationId: string;
  pharmacyDisplayName?: null | string;
  lotNumber?: null | string;
  /**
   * 剩余库存（最小/拆零单位）。有值时优先于 availableQuantity。
   * 后端可能序列化为 number。
   */
  remainingQuantity?: null | number | string;
  /** 可用数量（最小/拆零单位；无 remainingQuantity 时回落） */
  availableQuantity: number | string;
  unitCode: string;
  unitText?: null | string;
}

export interface EncounterMedicationDispenseSlipRowDto {
  encounterId: string;
  encounterBusNo?: null | string;
  dispenseId: string;
  medReqId: string;
  medicationId: string;
  dispenseLocationId: string;
  dispenseLocationName?: null | string;
  lotNumber?: null | string;
  traceNo?: null | string;
  orderingDepartmentId?: null | string;
  orderingDepartmentName?: null | string;
  orderingPractitionerId?: null | string;
  orderingPractitionerName?: null | string;
  dispensingPractitionerName?: null | string;
  medicationCategoryCode?: null | string;
  medicationCategoryText?: null | string;
  statusEnum: string;
  statusEnumText?: null | string;
  conditionSummaryText?: null | string;
  prescriptionNo?: null | string;
  medicationName?: null | string;
  merchandiseName?: null | string;
  specificationText?: null | string;
  /**
   * 包装最小单位换算比展示（服务端拼装），如 1盒/36片；规格列后展示。
   */
  packageSplitRatioText?: null | string;
  manufacturerName?: null | string;
  productTableName?: null | string;
  doseValue?: null | number | string;
  splitRatio?: null | number | string;
  frequencyCode?: null | string;
  frequencyText?: null | string;
  routeCode?: null | string;
  routeText?: null | string;
  doseUnitCode?: null | string;
  doseUnitText?: null | string;
  unitCode: string;
  unitText?: null | string;
  /** 包装单位码（产品定义）；发药追溯默认按包装计码 */
  packageUnitCode?: null | string;
  /** 包装相对拆零倍数（1 包装 = N 拆零） */
  packageUnitRatio?: null | number | string;
  /** 拆零单位码 */
  splitUnitCode?: null | string;
  /** 追溯码主档贴码单位（cus_trace_number；非目录） */
  traceUnitCode?: null | string;
  /** 追溯码主档贴码单位展示文案（UNIT_CODE） */
  traceUnitCodeText?: null | string;
  /** 追溯码主档贴码比例快照（1 码对应拆零量） */
  traceUnitRatio?: null | number | string;
  /** 主表该品批是否已有追溯码（有码批=true；无码批仅软提示） */
  lotHasTraceMaster?: boolean | null;
  dispensePerQuantity?: null | number;
  dispensePerSupplyDays?: null | number;
  requestQuantity?: null | number | string;
  /** 中药付数（方头；勿与 requestQuantity/quantity 混淆） */
  chineseHerbsDoseQuantity?: null | number | string;
  /** 后端可能序列化为 number */
  quantity: number | string;
  unitPrice?: null | number | string;
  totalPrice?: null | number | string;
  prescriptionGroupId?: null | string;
  requestOpenedTime?: null | string;
  skinTestFlag?: null | number;
  skinTestFlagText?: null | string;
  tcmFlag?: null | boolean;
  tcmFlagText?: null | string;
  infusionFlag?: null | number;
  partAttributeCode?: null | string;
  encounterMedicationTypeCode?: null | string;
  /** 医保医疗类别展示（YbMedType；adm_encounter_diagnosis.med_type；一张方一种） */
  encounterMedicationTypeText?: null | string;
  dispenseEnum?: null | string;
  therapyEnum?: null | string;
  busNo?: null | string;
  inventoryDetailList?: null | MedicationDispenseInventoryDetailDto[];
  /**
   * 发放库房×本行批号当前账面剩余数量（最小单位，IF-OPP-03 现查；
   * 与 inventoryDetailList 候选明细分列）
   */
  remainingQuantity?: null | number | string;
  patientName?: null | string;
  ageText?: null | string;
  genderEnumText?: null | string;
  patientPhone?: null | string;
  hospitalName?: null | string;
  feeNatureText?: null | string;
  prescriptionDocumentTitle?: null | string;
  prescriptionDateText?: null | string;
  prescriptionTypeLine1?: null | string;
  prescriptionTypeLine2?: null | string;
  prescriptionTotalPrice?: null | number | string;
  prescriptionTotalPriceText?: null | string;
  preparerPractitionerName?: null | string;
  pricingPractitionerName?: null | string;
  medicationDisplayLeft?: null | string;
  medicationDisplayRight?: null | string;
  medicationUsageDisplayText?: null | string;
  /** 单味特殊煎法术语码（dosage_instruction） */
  dosageInstructionCode?: null | string;
  /** 单味特殊煎法展示名 */
  dosageInstructionText?: null | string;
  /** 方头是否代煎（同源药嘱 sufferingFlag；同处方号各行一致） */
  sufferingFlag?: boolean | null;
  /** 同处方号代煎费金额 */
  decoctionChargeAmount?: null | number | string;
  /** 代煎费数量（付数） */
  decoctionChargeQuantity?: null | number | string;
  /** 代煎费数量单位码 */
  decoctionChargeUnitCode?: null | string;
  /** 代煎诊疗项目名称 */
  decoctionActivityName?: null | string;
}

export interface PageEncounterMedicationDispenseSlipsResponse {
  total?: number | string;
  rows?: EncounterMedicationDispenseSlipRowDto[];
}

export interface SavePrepareDispenseLineSubmitDto {
  dispenseId: string;
  dispenseLocationId: string;
  lotNumber?: null | string;
  traceNo?: null | string;
  quantity: string;
}

export interface SavePrepareOutpatientMedicationDispenseRequest {
  encounterId: string;
  preparerPractitionerId: string;
  lines: SavePrepareDispenseLineSubmitDto[];
}

export interface SavePrepareOutpatientMedicationDispenseResponse {
  success?: boolean;
  message?: null | string;
  processedDispenseIds?: string[];
}

export interface DispenseMedicationLineSubmitDto {
  dispenseId: string;
  /** 核发前最终追溯码；已配药重试改扫时须带上，否则仍按旧码扣主档 */
  traceNo?: null | string;
}

export interface DispenseOutpatientMedicationsRequest {
  encounterId: string;
  lines: DispenseMedicationLineSubmitDto[];
}

export interface DispenseOutpatientMedicationsResponse {
  success?: boolean;
  message?: null | string;
  processedDispenseIds?: string[];
}

export interface CancelMedicationDispenseLineSubmitDto {
  dispenseId: string;
  notPerformedReasonEnum: string;
}

export interface CancelOutpatientMedicationDispenseRequest {
  encounterId: string;
  lines: CancelMedicationDispenseLineSubmitDto[];
}

export interface CancelOutpatientMedicationDispenseResponse {
  success?: boolean;
  message?: null | string;
  processedDispenseIds?: string[];
}

/** 批量扫码身份反查（只读预览） */
export interface BatchResolveTraceIdentityRequest {
  traceCodes: string[];
  sceneContext?: 'DISPENSE' | 'INBOUND' | 'INVENTORY' | 'RETURN' | 'STOCKTAKE';
}

export interface TraceIdentityResolveItemDto {
  traceNo?: string;
  resolveStatusEnum?: string;
  itemTable?: string;
  itemId?: string;
  lotNumber?: string;
  traceUnitCode?: string;
  /** 主档贴码比例快照（1 码对应拆零量） */
  traceUnitRatio?: number | string;
  remainingSplitQuantity?: number | string;
  inHospitalStatusEnum?: string;
}

export interface TraceIdentityResolveFailItemDto {
  traceNo?: string;
  resolveStatusEnum?: string;
}

export interface BatchResolveTraceIdentityResponse {
  items?: TraceIdentityResolveItemDto[];
  failItems?: TraceIdentityResolveFailItemDto[];
}
