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
  /** @deprecated 列表状态筛选已废止，勿传 */
  nurseStationStatusFilter?: string;
  wardId?: string;
}

/** 在院患者队列行 */
export interface PatientQueueItemDto {
  accountId?: string;
  accountBalance?: number | string;
  accountBalanceDisplayMode?: string;
  accountBalanceSubText?: string;
  accountBalanceSummaryText?: string;
  availableBalance?: number | string;
  admissionTime?: string;
  ageText?: string;
  arrearsAmount?: number | string;
  attendingDoctorName?: string;
  bedId?: string;
  bedName?: string;
  /** 费用性质/医保类型展示（有医保账户优先） */
  contractDisplay?: string;
  diagnosisSummary?: string;
  encounterBusNo?: string;
  encounterId: string;
  endTime?: string;
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
  pendingSettlementTotal?: number | string;
  pinyinCode?: string;
  primaryNurseName?: string;
  priorityEnum?: string;
  nursingLevelActivityId?: string;
  /** 护理级别展示名（IF-IP-NUR-02 · BR-IP-NUR-015） */
  nursingLevelName?: string;
  /** 护理级别医嘱主键 */
  nursingLevelServiceRequestId?: string;
  /** 护理级别医嘱状态 DRAFT/ACTIVE/VERIFIED/STOPPED */
  nursingLevelStatusEnum?: string;
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

/** 入院体征提交/回填块（IF-IP-NUR-03/04 与 ATD-02 ASSIGN 复用） */
export interface AdmissionVitalSignSubmitDto {
  bloodPressureHigh?: string;
  bloodPressureLow?: string;
  heartRate?: string;
  heightCm?: string;
  pulse?: string;
  temperature?: string;
  weightKg?: string;
}

/** 患者全景详情（列表字段超集） */
export interface PatientDetailDto extends PatientQueueItemDto {
  admissionVitalSigns?: AdmissionVitalSignSubmitDto;
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
  admissionVitalSigns?: AdmissionVitalSignSubmitDto;
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
  /** 患者病情下拉（术语 adm_cond_code） */
  admCondCodeOptions?: OptionDto[];
  doctorOptions?: OptionDto[];
  primaryNurseOptions?: OptionDto[];
}

/** 通用下拉选项（与后端 OptionDto 一致） */
export interface OptionDto {
  disabled?: boolean;
  label: string;
  value: string;
}
