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
  startTime?: null | string;
  endTime?: null | string;
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
  ageText?: null | string;
  organizationName?: null | string;
  doctorName?: null | string;
  statusEnum?: null | string;
  statusEnumText?: null | string;
  dispensePendingSummaryText?: null | string;
  startTime?: null | string;
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
  /** 后端可能序列化为 number */
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
  dispensePerQuantity?: null | number;
  dispensePerSupplyDays?: null | number;
  requestQuantity?: null | number | string;
  /** 后端可能序列化为 number */
  quantity: number | string;
  unitPrice?: null | number | string;
  totalPrice?: null | number | string;
  prescriptionGroupId?: null | string;
  requestAuthoredTime?: null | string;
  skinTestFlag?: null | number;
  skinTestFlagText?: null | string;
  tcmFlag?: null | boolean;
  tcmFlagText?: null | string;
  infusionFlag?: null | number;
  partAttributeCode?: null | string;
  encounterMedicationTypeCode?: null | string;
  encounterMedicationTypeText?: null | string;
  dispenseEnum?: null | string;
  therapyEnum?: null | string;
  busNo?: null | string;
  inventoryDetailList?: null | MedicationDispenseInventoryDetailDto[];
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
}

export interface DispenseOutpatientMedicationsRequest {
  encounterId: string;
  /** @deprecated CHS 启用即上传，请使用 contractBusNo */
  medicareUploadEnabled?: boolean;
  contractBusNo?: string;
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
