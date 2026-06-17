import type { PageApiResponse, PageParams } from '#/types/api-response';

/** 病区病房树查询（登录者管理范围） */
export interface LoadWardTreeRequest {
  [key: string]: never;
}

/** 病区树节点 */
export interface NurseStationWardTreeNodeDto {
  busNo?: string;
  displayOrder?: number;
  houseNodes?: NurseStationHouseTreeNodeDto[];
  locationId: string;
  name: string;
}

/** 病房树节点 */
export interface NurseStationHouseTreeNodeDto {
  busNo?: string;
  displayOrder?: number;
  locationId: string;
  name: string;
}

/** 病区病房树响应 */
export interface LoadWardTreeResponse {
  wardNodes: NurseStationWardTreeNodeDto[];
}

/** 在院患者队列分页请求 */
export interface PagePatientQueueRequest extends PageParams {
  houseId?: string;
  keyword?: string;
  nurseStationExecutionDate?: string;
  nurseStationTaskFilter?: string;
  wardId?: string;
}

/** 在院患者队列行 */
export interface PatientQueueItemDto {
  accountId?: string;
  accountBalance?: number | string;
  admissionTime?: string;
  ageText?: string;
  arrearsAmount?: number | string;
  attendingDoctorName?: string;
  bedId?: string;
  bedName?: string;
  diagnosisSummary?: string;
  encounterBusNo?: string;
  encounterId: string;
  genderEnum?: string;
  genderEnumText?: string;
  houseName?: string;
  lowBalanceWarning?: boolean;
  organizationId?: string;
  organizationName?: string;
  paidAmount?: number | string;
  patientId: string;
  patientName: string;
  pendingAuditCount?: number;
  pendingExecuteCount?: number;
  pinyinCode?: string;
  primaryNurseName?: string;
  priorityEnum?: string;
  startTime?: string;
  statusEnum?: string;
  statusEnumText?: string;
  totalAmount?: number | string;
  wardId?: string;
  wardName?: string;
  wubiCode?: string;
}

/** 在院患者队列分页响应 */
export type PagePatientQueueResponse = PageApiResponse<PatientQueueItemDto>;

/** 患者详情查询请求 */
export interface GetPatientDetailRequest {
  encounterId: string;
}

/** 参与者展示行 */
export interface ParticipantDto {
  practitionerId?: string;
  practitionerName?: string;
  roleCode?: string;
  roleCodeText?: string;
}

/** 参与者维护提交行 */
export interface ParticipantSubmitDto {
  practitionerId: string;
  roleCode: string;
}

/** 患者全景详情（列表字段超集） */
export interface PatientDetailDto extends PatientQueueItemDto {
  diagnosisList?: string[];
  idCardNo?: string;
  locationJson?: string;
  participants?: ParticipantDto[];
  patientPhone?: string;
}

/** 患者详情查询响应 */
export interface GetPatientDetailResponse {
  detail?: PatientDetailDto;
}

/** 患者信息维护请求 */
export interface SavePatientInfoRequest {
  encounterId: string;
  participants?: ParticipantSubmitDto[];
  priorityEnum?: string;
}

/** 患者信息维护响应 */
export interface SavePatientInfoResponse {
  encounterId?: string;
  saved?: boolean;
}

/** 分床责任医护下拉初始化请求 */
export interface LoadAdmissionParticipantInitRequest {
  organizationId?: string;
  /** 入院病区位置 id（form_enum=WARD） */
  wardLocationId?: string;
}

/** 分床责任医护下拉初始化响应 */
export interface LoadAdmissionParticipantInitResponse {
  doctorOptions?: OptionDto[];
  primaryNurseOptions?: OptionDto[];
}

/** 通用下拉选项（与后端 OptionDto 一致） */
export interface OptionDto {
  disabled?: boolean;
  label: string;
  value: string;
}
