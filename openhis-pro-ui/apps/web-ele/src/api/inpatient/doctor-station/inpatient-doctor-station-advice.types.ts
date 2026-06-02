/** 住院医生站医嘱开立：Long 主键请求侧为 string；与门诊 DTO 同构处复用共享类型 */

export type {
  ActivityExecutionLocationRuleRowDto,
  AssignMedicationGroupPayload,
  ComputeMedicationFrequencyExecutionsRequest,
  ComputeMedicationFrequencyExecutionsResponse,
  DoctorStationAdviceCatalogRowDto,
  DoctorStationAdviceOrderBindRowDto,
  DoctorStationAdviceResourceKeyDto,
  ListActivityExecutionLocationsRequest,
  ListActivityExecutionLocationsResponse,
  PrepickedProductKeyDto,
  SaveDeviceAdviceDraftLinePayload,
  SaveMedicationAdviceDraftLinePayload,
  SaveServiceAdviceDraftLinePayload,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';

import type {
  DoctorStationAdviceCatalogRowDto,
  DoctorStationAdviceDraftLineRequest,
  DoctorStationAdviceOrderBindRowDto,
  DoctorStationAdviceResourceKeyDto,
  DoctorStationAdviceRowDto,
  GetDoctorStationAdviceOrderBindInfoRequest,
  GetDoctorStationAdviceOrderBindInfoResponse,
  InitDoctorStationAdviceRequest,
  InitDoctorStationAdviceResponse,
  PageDoctorStationAdviceCatalogRequest,
  PageDoctorStationAdviceCatalogResponse,
  SaveDoctorStationAdviceDraftBatchRequest,
  SaveDoctorStationAdviceDraftBatchResponse,
  SignDoctorStationAdviceBatchRequest,
  SignDoctorStationAdviceBatchResponse,
  UpdateDoctorStationMedicationAdviceGroupsRequest,
  UpdateDoctorStationMedicationAdviceGroupsResponse,
  WithdrawDoctorStationAdviceBatchRequest,
  WithdrawDoctorStationAdviceBatchResponse,
} from '#/api/doctor-station/advice.types';

/** 住院医嘱列表行（在门诊行基础上增补长临等字段） */
export interface InpatientDoctorStationAdviceRowDto extends DoctorStationAdviceRowDto {
  therapyTypeCode?: string;
  generateSourceEnum?: string;
  stopTime?: string;
  conditionDefinitionId?: string;
  signCode?: string;
}

/** 加载当前住院就诊医嘱统一列表 */
export interface ListInpatientDoctorStationAdviceRequest {
  encounterId: string;
  patientId: string;
  /** LONG_TERM / TEMPORARY；空表示不过滤 */
  therapyTypeCode?: string;
}

export interface ListInpatientDoctorStationAdviceResponse {
  rows?: InpatientDoctorStationAdviceRowDto[];
  totalCount?: number;
}

export type InitInpatientDoctorStationAdviceRequest = InitDoctorStationAdviceRequest;
export type InitInpatientDoctorStationAdviceResponse = InitDoctorStationAdviceResponse;

export type PageInpatientDoctorStationAdviceCatalogRequest = PageDoctorStationAdviceCatalogRequest;
export type PageInpatientDoctorStationAdviceCatalogResponse =
  PageDoctorStationAdviceCatalogResponse;
export type InpatientDoctorStationAdviceCatalogRowDto = DoctorStationAdviceCatalogRowDto;

export type GetInpatientDoctorStationAdviceOrderBindInfoRequest =
  GetDoctorStationAdviceOrderBindInfoRequest;
export type GetInpatientDoctorStationAdviceOrderBindInfoResponse =
  GetDoctorStationAdviceOrderBindInfoResponse;
export type InpatientDoctorStationAdviceOrderBindRowDto = DoctorStationAdviceOrderBindRowDto;

/** 暂存批次行：批次级长临（与 medicationPayload.therapyEnum 二选一，后端 Mapper 归一） */
export interface InpatientDoctorStationAdviceDraftLineRequest
  extends DoctorStationAdviceDraftLineRequest {
  therapyTypeCode?: string;
  therapyEnum?: string;
}

export interface SaveInpatientDoctorStationAdviceDraftBatchRequest {
  encounterId: string;
  patientId: string;
  pricingSourceCode?: string;
  lines: InpatientDoctorStationAdviceDraftLineRequest[];
}

export type SaveInpatientDoctorStationAdviceDraftBatchResponse =
  SaveDoctorStationAdviceDraftBatchResponse;

export type SignInpatientDoctorStationAdviceBatchRequest = SignDoctorStationAdviceBatchRequest;
export type SignInpatientDoctorStationAdviceBatchResponse = SignDoctorStationAdviceBatchResponse;
export type WithdrawInpatientDoctorStationAdviceBatchRequest =
  WithdrawDoctorStationAdviceBatchRequest;
export type WithdrawInpatientDoctorStationAdviceBatchResponse =
  WithdrawDoctorStationAdviceBatchResponse;

export type UpdateInpatientDoctorStationMedicationAdviceGroupsRequest =
  UpdateDoctorStationMedicationAdviceGroupsRequest;
export type UpdateInpatientDoctorStationMedicationAdviceGroupsResponse =
  UpdateDoctorStationMedicationAdviceGroupsResponse;

/** 停嘱（仅长期医嘱） */
export interface StopLongTermAdviceLineRequest {
  requestId: string;
  productTableName: string;
  /** ISO-8601 本地时间；空则由服务端取当前时间 */
  stopTime?: string;
}

export interface StopLongTermInpatientDoctorStationAdviceRequest {
  encounterId: string;
  patientId: string;
  lines: StopLongTermAdviceLineRequest[];
}

export interface StopLongTermInpatientDoctorStationAdviceResponse {
  success?: boolean;
  message?: string;
}

/** 开立出院医嘱 */
export interface CreateDischargeInpatientDoctorStationAdviceRequest {
  encounterId: string;
  patientId: string;
  outWayCode: string;
  plannedDischargeTime?: string;
  reasonText?: string;
  sourceOrgId?: string;
  sourceLocationId?: string;
  targetOrgId?: string;
  targetLocationId?: string;
}

export interface CreateDischargeInpatientDoctorStationAdviceResponse {
  serviceRequestId?: string;
  encounterHistoryId?: string;
  success?: boolean;
  message?: string;
}
