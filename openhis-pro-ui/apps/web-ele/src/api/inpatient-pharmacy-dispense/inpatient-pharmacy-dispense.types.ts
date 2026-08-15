/**
 * 住院发药应用服务请求/响应类型（与后端 DTO camelCase 对齐；Long 类主键用 string）。
 */

import type { ApiOptionItem, PageParams } from '#/types/api-response';

/** Spring Data 风格分页块 */
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

export interface LoadInpatientPharmacyDispensePageInitRequest {
  /** 当前药房库位 adm_location.id（非 organization） */
  pharmacyLocationId?: null | string;
  pageNo?: number;
  pageSize?: number;
  practitionerRoleFilterCode?: number;
}

export interface LoadInpatientPharmacyDispensePageInitResponse {
  pharmacyOrganizationOptions?: ApiOptionItem[];
  dispenseStatusFilterOptions?: ApiOptionItem[];
  notPerformedReasonOptions?: ApiOptionItem[];
  wardOptions?: ApiOptionItem[];
  preparerCandidatePage?: SpringPage<PractitionerOptionDto>;
  receiverCandidatePage?: SpringPage<PractitionerOptionDto>;
}

export interface PagePendingDispenseInpatientEncounterQueueRequest extends PageParams {
  startDate?: null | string;
  endDate?: null | string;
  keyword?: null | string;
  wardId?: null | string;
  dispenseStatusEnumIn?: null | string[];
}

export interface PendingDispenseInpatientEncounterQueueRowDto {
  encounterId: string;
  encounterBusNo?: null | string;
  patientId?: null | string;
  patientName?: null | string;
  genderEnum?: null | string;
  ageText?: null | string;
  wardId?: null | string;
  wardName?: null | string;
  bedName?: null | string;
  organizationName?: null | string;
  attendingDoctorName?: null | string;
  statusEnum?: null | string;
  statusEnumText?: null | string;
  startTime?: null | string;
  /** 费用性质展示名（就诊优先合同名；空则前端回落自费） */
  contractDisplay?: null | string;
}

export interface PagePendingDispenseInpatientEncounterQueueResponse {
  content?: PendingDispenseInpatientEncounterQueueRowDto[];
  totalCount?: number | string;
  pageNo?: number;
  pageSize?: number;
}

export interface PageEncounterMedicationDispenseSlipsRequest extends PageParams {
  encounterId: string;
  locationId?: null | string;
  tcmOnly?: boolean;
  westernOnly?: boolean;
  dispenseStatusEnumIn?: null | string[];
}

export interface EncounterMedicationDispenseSlipRowDto {
  encounterId: string;
  encounterBusNo?: null | string;
  dispenseId: string;
  medReqId: string;
  /** 护士执行记录主键；按日虚拟发药单用 */
  procedureId?: null | string;
  /**
   * 护士认定实际执行时刻（recorded_time）。
   * 前端按自然日虚拟分发药单；与汇总领药 planned 无关。
   */
  recordedTime?: null | string;
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
  /** 包装单位码（待扫换算） */
  packageUnitCode?: null | string;
  /** 包装相对拆零倍数 */
  packageUnitRatio?: null | number | string;
  /** 拆零单位码 */
  splitUnitCode?: null | string;
  /** 追溯码主档贴码单位（cus_trace_number） */
  traceUnitCode?: null | string;
  /** 追溯码主档贴码单位展示文案（UNIT_CODE） */
  traceUnitCodeText?: null | string;
  /** 追溯码主档贴码比例快照 */
  traceUnitRatio?: null | number | string;
  /** 主表该品批是否已有追溯码（有码批） */
  lotHasTraceMaster?: boolean | null;
  /**
   * 发放库房×批号当前剩余数量（最小单位，现查）。
   * 住院笺面「剩余库存」回显用。
   */
  remainingQuantity?: null | number | string;
  dispensePerQuantity?: null | number;
  dispensePerSupplyDays?: null | number;
  requestQuantity?: null | number | string;
  quantity: number | string;
  unitPrice?: null | number | string;
  totalPrice?: null | number | string;
  prescriptionGroupId?: null | string;
  requestOpenedTime?: null | string;
  skinTestFlag?: null | boolean;
  skinTestFlagText?: null | string;
  tcmFlag?: null | boolean;
  tcmFlagText?: null | string;
  infusionFlag?: null | boolean;
  infusionFlagText?: null | string;
  partAttributeCode?: null | string;
  partAttributeText?: null | string;
  encounterMedicationTypeCode?: null | string;
  encounterMedicationTypeText?: null | string;
  dispenseEnum?: null | string;
  dispenseEnumText?: null | string;
  therapyEnum?: null | string;
  therapyEnumText?: null | string;
  busNo?: null | string;
  /** 费用性质展示（就诊合同名优先；与队列 contractDisplay / 门诊 slip 同口径） */
  feeNatureText?: null | string;
  patientName?: null | string;
  ageText?: null | string;
  genderEnumText?: null | string;
  patientPhone?: null | string;
  preparerPractitionerName?: null | string;
  pricingPractitionerName?: null | string;
}

export interface PageEncounterMedicationDispenseSlipsResponse {
  content?: EncounterMedicationDispenseSlipRowDto[];
  totalCount?: number | string;
  pageNo?: number;
  pageSize?: number;
}

export interface SavePrepareInpatientDispenseLineSubmitDto {
  dispenseId: string;
  traceNo?: null | string;
}

export interface SavePrepareInpatientMedicationDispenseRequest {
  encounterId: string;
  preparerPractitionerId: string;
  lines: SavePrepareInpatientDispenseLineSubmitDto[];
}

export interface SavePrepareInpatientMedicationDispenseResponse {
  success?: boolean;
  message?: null | string;
  processedDispenseIds?: string[];
}

export interface DispenseMedicationLineSubmitDto {
  dispenseId: string;
}

export interface DispenseInpatientMedicationsRequest {
  encounterId: string;
  lines: DispenseMedicationLineSubmitDto[];
  /** @deprecated CHS 启用即上传，请使用 contractBusNo */
  medicareUploadEnabled?: boolean;
  contractBusNo?: string;
}

export interface DispenseInpatientMedicationsResponse {
  success?: boolean;
  message?: null | string;
  processedDispenseIds?: string[];
}

export interface CancelMedicationDispenseLineSubmitDto {
  dispenseId: string;
  notPerformedReasonEnum: string;
}

export interface CancelInpatientMedicationDispenseRequest {
  encounterId: string;
  lines: CancelMedicationDispenseLineSubmitDto[];
}

export interface CancelInpatientMedicationDispenseResponse {
  success?: boolean;
  message?: null | string;
  processedDispenseIds?: string[];
}
