/** 住院医生站医嘱开立：Long 主键请求侧为 string；与门诊 DTO 同构处复用共享类型 */

export type {
  ActivityExecutionLocationRuleRowDto,
  AssignMedicationGroupPayload,
  ComputeMedicationFrequencyExecutionsRequest,
  ComputeMedicationFrequencyExecutionsResponse,
  DoctorStationAdviceCatalogRowDto,
  DoctorStationAdviceDraftBindItemDto,
  DoctorStationAdviceOrderBindRowDto,
  DoctorStationAdviceResourceKeyDto,
  ListActivityExecutionLocationsRequest,
  ListActivityExecutionLocationsResponse,
  ListDoctorStationServiceApplyFormsRequest,
  ListDoctorStationServiceApplyFormsResponse,
  PrepickedProductKeyDto,
  ResolveDoctorStationAdviceDraftBindsRequest,
  ResolveDoctorStationAdviceDraftBindsResponse,
  SaveDeviceAdviceDraftLinePayload,
  SaveDoctorStationServiceApplyFormRequest,
  SaveDoctorStationServiceApplyFormResponse,
  SaveMedicationAdviceDraftLinePayload,
  SaveServiceAdviceDraftLinePayload,
  ServiceApplyFormHeaderDto,
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
  ListDoctorStationServiceApplyFormsRequest,
  ListDoctorStationServiceApplyFormsResponse,
  PageDoctorStationAdviceCatalogRequest,
  PageDoctorStationAdviceCatalogResponse,
  SaveDoctorStationAdviceDraftBatchRequest,
  SaveDoctorStationAdviceDraftBatchResponse,
  SaveDoctorStationServiceApplyFormRequest,
  SaveDoctorStationServiceApplyFormResponse,
  SaveMedicationAdviceDraftLinePayload,
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
  stopperId?: string;
  stopperName?: string;
  /** 停嘱校对人；非空时医生不可取消停嘱 */
  stopPerformerCheckId?: string;
  conditionDefinitionId?: string;
  /** 长嘱皮试药换批提示：建议追加皮试检查（IF-IP-DOR-27） */
  needAppendSkinTestFlag?: boolean;
  /** 改量链 based_on 表名 */
  basedOnTable?: string;
  /** 改量链 based_on 源嘱主键 */
  basedOnId?: string;
  /** 是否命中口服一盒一点剩余规则（BR-IP-EXE-038） */
  oralPackageBatchInventoryApplied?: boolean;
  /** 已吃拆零量 */
  oralPackageConsumedSplitQuantity?: number | string;
  /** 剩余拆零量 */
  oralPackageRemainSplitQuantity?: number | string;
  /** 拆零单位展示 */
  oralPackageSplitUnitDisplay?: string;
  /** 预计下次领盒日 yyyy-MM-dd */
  oralPackageNextClaimDate?: string;
}

/** 长期药品改量（IF-IP-DOR-28：停旧+同药草稿，待签发） */
export interface ChangeLongTermMedicationDoseInpatientDoctorStationAdviceRequest {
  encounterId: string;
  patientId: string;
  sourceMedicationRequestId: string;
  /** 在用改量时可传停嘱时间；已停忽略 */
  stopTime?: string;
  medicationPayload: SaveMedicationAdviceDraftLinePayload;
}

export interface ChangeLongTermMedicationDoseInpatientDoctorStationAdviceResponse {
  success?: boolean;
  message?: string;
  newMedicationRequestId?: string;
  sourceMedicationRequestId?: string;
  basedOnId?: string;
}

/** 改开立/停嘱时间（IF-IP-DOR-26；禁止改签发） */
export interface ChangeInpatientDoctorStationAdviceBusinessTimeRequest {
  encounterId: string;
  patientId: string;
  requestId: string;
  productTableName: string;
  /** OPENED：改开立时间；STOP：改停嘱时间 */
  timeKind: 'OPENED' | 'STOP';
  /** ISO-8601 本地时间 */
  newTime: string;
}

export interface ChangeInpatientDoctorStationAdviceBusinessTimeResponse {
  success?: boolean;
  message?: string;
}

/** 长期皮试药追加皮试检查（IF-IP-DOR-27） */
export interface AppendSkinTestCompanionForLongTermMedicationRequest {
  encounterId: string;
  patientId: string;
  medicationRequestIds: string[];
}

export interface AppendSkinTestCompanionForLongTermMedicationResponse {
  success?: boolean;
  message?: string;
  serviceRequestIds?: string[];
}

/** 加载当前住院就诊医嘱统一列表 */
export interface ListInpatientDoctorStationAdviceRequest {
  encounterId: string;
  patientId: string;
  /** LONG_TERM / TEMPORARY / DISCHARGE_MEDICATION；空表示不过滤 */
  therapyTypeCode?: string;
  /**
   * 纳入中药饮片（病案首页医嘱列表为 true；开立 Tab 不传，保持与中医 Tab 分域）。
   */
  includeTcmMedication?: boolean;
}

export interface ListInpatientDoctorStationAdviceResponse {
  /** 标准分页列表（后端 PagedResult） */
  content?: InpatientDoctorStationAdviceRowDto[];
  /** @deprecated 兼容旧字段；实际 list 在 content */
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

/** 取消停嘱（仅长期且已停嘱） */
export interface CancelStopLongTermAdviceLineRequest {
  requestId: string;
  productTableName: string;
}

export interface CancelStopLongTermInpatientDoctorStationAdviceRequest {
  encounterId: string;
  patientId: string;
  lines: CancelStopLongTermAdviceLineRequest[];
}

export interface CancelStopLongTermInpatientDoctorStationAdviceResponse {
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

/** 住院检查检验申请单：请求/响应与门诊同构（后端子类继承） */
export type SaveInpatientDoctorStationServiceApplyFormRequest =
  SaveDoctorStationServiceApplyFormRequest;
export type SaveInpatientDoctorStationServiceApplyFormResponse =
  SaveDoctorStationServiceApplyFormResponse;
export type ListInpatientDoctorStationServiceApplyFormsRequest =
  ListDoctorStationServiceApplyFormsRequest;
export type ListInpatientDoctorStationServiceApplyFormsResponse =
  ListDoctorStationServiceApplyFormsResponse;
