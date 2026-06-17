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
  quantity: number | string;
  unitPrice?: null | number | string;
  totalPrice?: null | number | string;
  prescriptionGroupId?: null | string;
  requestAuthoredTime?: null | string;
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
