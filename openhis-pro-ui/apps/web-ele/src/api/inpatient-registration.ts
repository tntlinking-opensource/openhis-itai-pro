/**
 * 住院登记：`/inpatientRegistration` 请求与类型（camelCase，与后端一致）。
 */

import type { PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

/** 通用下拉选项 */
export interface OptionDto {
  disabled?: boolean;
  label: string;
  value: string;
}

/** 登记列表状态筛选码 */
export type RegistrationListStatus =
  | 'ADMITTED'
  | 'CANCELLED'
  | 'COMPLETED'
  | 'DISCHARGED'
  | 'ENTERED_IN_ERROR'
  | 'ON_HOLD'
  | 'PENDING'
  | 'PLANNED'
  | 'PRE_ADMISSION'
  | 'WAIT_ADMISSION';

/** 待办列表分页请求 */
export interface PageRegistrationQueueParams extends PageParams {
  /** 入院日起（含），yyyy-MM-dd */
  admissionDateFrom?: string;
  /** 入院日止（含），yyyy-MM-dd */
  admissionDateTo?: string;
  idCard?: string;
  keyword?: string;
  /** 住院科室主键（字符串，防 Long 精度丢失） */
  organizationId?: string;
  registrationListStatus?: RegistrationListStatus;
  registrationListStatuses?: RegistrationListStatus[];
  /** 入院病区主键（字符串，防 Long 精度丢失） */
  wardId?: string;
}

/** 待办列表行 */
export interface RegistrationQueueItemDto {
  ambEncounterId?: string;
  bedId?: string;
  bedName?: string;
  cancelable: boolean;
  encounterBusNo: string;
  encounterId: string;
  insurancePlatformRegistered?: boolean;
  inWayCode?: string;
  inWayCodeText?: string;
  organizationId?: string;
  organizationName?: string;
  patientId: string;
  patientName: string;
  /** 费用性质展示名（就诊优先合同名） */
  contractDisplay?: string;
  /**
   * 入院办理意向（医生转住院提示：INSURANCE=医保办 / SELF_PAY=自费办）。
   * 待登记时常无合同；本字段提示款台准备办理方式。
   */
  chargeIntent?: string;
  /** 入院办理意向展示文案 */
  chargeIntentText?: string;
  /**
   * 险种类型码（310 职工基本医疗保险 / 390 城乡居民基本医疗保险 / 340 离休）。
   * 列表在费用性质旁展示「职工医保」「居民医保」或「离休」。
   */
  insutype?: string;
  startTime?: string;
  statusEnum: string;
  statusEnumText: string;
  /** 是否允许自费转医保（IF-IP-REG-07） */
  switchToInsuranceEnabled?: boolean;
  /** 是否允许医保转自费（IF-IP-REG-09） */
  switchToSelfPayEnabled?: boolean;
  wardId?: string;
  wardName?: string;
}

/** 登记上下文加载请求 */
export interface LoadRegistrationContextParams {
  encounterId?: string;
  organizationId?: string;
}

/** 登记弹窗患者主档 */
export interface RegistrationPatientDto {
  birthDate?: string;
  genderEnum?: string;
  idCard?: string;
  name?: string;
  patientId?: string;
  phone?: string;
  /** 婚姻状况编码 */
  maritalStatusEnum?: string;
  /** 职业编码 */
  prfsEnum?: string;
  /** 现住址详细地址 */
  address?: string;
  addressProvince?: string;
  addressCity?: string;
  addressDistrict?: string;
  addressStreet?: string;
  /** 国籍（country_code → 病案 ntly） */
  countryCode?: string;
  /** 民族（nationality_code → 病案 naty） */
  nationalityCode?: string;
  /** 籍贯（code 斜杠拼接） */
  nativePlace?: string;
  /** 工作单位 */
  workCompany?: string;
  /** 联系人姓名 */
  linkName?: string;
  /** 联系人关系编码 */
  linkRelationCode?: number | string;
  /** 联系人电话 */
  linkTelcom?: string;
}

/** 登记弹窗住院就诊 */
export interface RegistrationEncounterDto {
  admitSourceCode?: string;
  admitSourceCodeText?: string;
  ambEncounterId?: string;
  encounterBusNo?: string;
  encounterId?: string;
  inWayCode?: string;
  inWayCodeText?: string;
  organizationId?: string;
  organizationName?: string;
  /** 源门诊接诊医生姓名（门诊转住院只读展示） */
  outpatientDoctorName?: string;
  primaryConditionDefinitionId?: string;
  primaryConditionDefinitionName?: string;
  priorityEnum?: string;
  startTime?: string;
  statusEnum?: string;
  wardId?: string;
  wardName?: string;
  /** 护士分床后的床位 ID */
  bedId?: string;
  /** 护士分床后的床位名称 */
  bedName?: string;
  /** 主治医生医务人员 ID */
  attendingDoctorId?: string;
  /** 主治医生姓名 */
  attendingDoctorName?: string;
  /**
   * 费用性质（合同）ID。
   * 已登记：优先医保账户合同，否则个人现金户；未开户可空。
   */
  contractId?: string;
  /** 医保险种类别码（310 职工 / 390 居民等；弹窗只读展示） */
  insutype?: string;
  /**
   * 入院办理意向（医生转住院提示：INSURANCE=医保办 / SELF_PAY=自费办）。
   * 非真实费用性质，登记合同仍以收费员选择为准。
   */
  chargeIntent?: string;
  /** 入院办理意向展示文案（医保办/自费办） */
  chargeIntentText?: string;
  /** 意向预交金总额（门诊转住院 DRAFT 回显；收费员可改） */
  advanceDepositAmount?: number | string;
  /** 意向预交金草稿支付主键（可选） */
  paymentDraftId?: string;
}

/** 登记上下文加载响应 */
export interface LoadRegistrationContextResult {
  admitSourceCodeOptions?: OptionDto[];
  /** 患者病情下拉（术语 adm_cond_code） */
  admCondCodeOptions?: OptionDto[];
  contractOptions: OptionDto[];
  /** 入院科室下拉（类别=住院） */
  organizationOptions?: OptionDto[];
  inWayCodeOptions?: OptionDto[];
  encounter?: RegistrationEncounterDto | null;
  patient?: RegistrationPatientDto | null;
  readOnly: boolean;
  /** 是否允许自费转医保（IF-IP-REG-07） */
  switchToInsuranceEnabled?: boolean;
  /** 是否允许医保转自费（IF-IP-REG-09） */
  switchToSelfPayEnabled?: boolean;
}

/** 预交金支付明细 */
export interface PaymentDetailDto {
  accountId?: string;
  amount: number | string;
  payChannelCode: string;
}

/** 入院诊断提交行 */
export interface AdmissionDiagnosisSubmitDto {
  conditionDefinitionId?: string;
  /** 已有就诊诊断行主键；更新/引用时携带 */
  encounterDiagnosisId?: string;
  description?: string;
  primaryFlag: boolean;
  /** 是否中医诊断；中医一病一症须两条均为 true */
  tcmFlag?: boolean;
  /** 中医病证组号（同组病+证共用；可空由后端分配） */
  tcmSyndromeGroup?: string;
}

/** 提交住院登记请求 */
export interface SubmitRegistrationParams {
  admissionDiagnoses?: AdmissionDiagnosisSubmitDto[];
  admitSourceCode?: string;
  advanceDepositAmount?: number | string;
  attendingDoctorId?: string;
  birthDate?: string;
  contractId: string;
  encounterId?: string;
  genderEnum?: string;
  idCard?: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  inWayCode?: string;
  medType?: string;
  operatorId: string;
  organizationId: string;
  patientId?: string;
  patientName?: string;
  paymentDetails?: PaymentDetailDto[];
  phone?: string;
  /** 婚姻状况编码 */
  maritalStatusEnum?: string;
  /** 职业编码 */
  prfsEnum?: string;
  /** 现住址详细地址 */
  address?: string;
  addressProvince?: string;
  addressCity?: string;
  addressDistrict?: string;
  addressStreet?: string;
  countryCode?: string;
  nationalityCode?: string;
  nativePlace?: string;
  workCompany?: string;
  linkName?: string;
  linkRelationCode?: number;
  linkTelcom?: string;
  priorityEnum?: string;
  psnNo?: string;
  startTime: string;
  updatePatientFromIdCard?: boolean;
  wardId: string;
  ybCardInfo?: string;
  ybMediumType?: string;
}

/** 提交住院登记响应 */
export interface SubmitRegistrationResult {
  accountOpened?: boolean;
  encounterBusNo: string;
  encounterId: string;
  paymentId?: string;
  statusEnum: string;
  ybMdtrtId?: string;
}

/** 取消住院登记请求 */
export interface CancelRegistrationParams {
  cancelReason?: string;
  encounterId: string;
  operatorId: string;
}

/** 取消住院登记响应 */
export interface CancelRegistrationResult {
  encounterId: string;
  statusEnum: string;
  success: boolean;
}

/** 住院自费转医保请求（IF-IP-REG-07） */
export interface SwitchInpatientToInsuranceParams {
  attendingDoctorId: string;
  contractId: string;
  encounterId: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  medType?: string;
  operatorId: string;
  psnNo: string;
  ybCardInfo?: string;
  ybMediumType?: string;
}

/** 住院医保转自费请求（IF-IP-REG-09） */
export interface SwitchInpatientToSelfPayParams {
  encounterId: string;
  operatorId: string;
}

/** 住院医保转自费响应 */
export interface SwitchInpatientToSelfPayResult {
  selfPayAccountId?: string;
  success?: boolean;
  switchedChargeItemCount?: number;
}

/** 住院自费转医保响应 */
export interface SwitchInpatientToInsuranceResult {
  insuranceAccountId?: string;
  messageCode?: string;
  success?: boolean;
  switchedChargeItemCount?: number;
  ybMdtrtId?: string;
}

/** 待入科医保退入院响应 */
export interface CancelInsuranceInpatientAdmissionResult {
  encounterStatus?: string;
  insurancePlatformRevoked?: boolean;
}

/** 更正入院科室请求（IF-IP-REG-08） */
export interface ChangeAdmissionDepartmentParams {
  bedId?: string;
  encounterId: string;
  operatorId: string;
  organizationId: string;
  wardId: string;
}

/** 更正入院科室响应 */
export interface ChangeAdmissionDepartmentResult {
  insurancePlatformChanged?: boolean;
  success?: boolean;
}

/** 入院科室变更可选床请求 */
export interface ListAdmissionDepartmentBedsParams {
  encounterId?: string;
  wardId: string;
}

/** 入院科室变更可选床 */
export interface AdmissionDepartmentBedOptionDto {
  bedId: string;
  bedName?: string;
  currentBed?: boolean;
  houseId: string;
  houseName?: string;
}

/** 入院科室变更可选床响应 */
export interface ListAdmissionDepartmentBedsResult {
  beds?: AdmissionDepartmentBedOptionDto[];
}

/** 按科室查询病区列表请求 */
export interface ListWardsByDepartmentParams {
  organizationId: string;
}

/** 病区选项 */
export interface WardOptionDto {
  /** 主挂靠科室名称。科室变更下列其他科室病区时用于区分。 */
  organizationName?: string;
  wardCode?: string;
  wardId: string;
  wardName: string;
}

/** 按科室查询病区列表响应 */
export interface ListWardsByDepartmentResult {
  wards: WardOptionDto[];
}

/** 病区床位余量请求 */
export interface LoadWardBedCapacityParams {
  wardId: string;
}

/** 病区床位余量响应 */
export interface LoadWardBedCapacityResult {
  totalBedCount: number | string;
  vacantBedCount: number | string;
  wardId: string;
}

/** 住院登记待办列表分页查询 */
export async function httpPageInpatientRegistrationQueue(
  params: PageRegistrationQueueParams,
): Promise<PageApiResponse<RegistrationQueueItemDto>> {
  return publicRequestClient.post<PageApiResponse<RegistrationQueueItemDto>>(
    '/inpatientRegistration/pageInpatientRegistrationQueue',
    params,
  );
}

/** 登记弹窗上下文加载（患者、就诊、费用性质） */
export async function httpLoadInpatientRegistrationContext(
  params: LoadRegistrationContextParams = {},
): Promise<LoadRegistrationContextResult> {
  return publicRequestClient.post<LoadRegistrationContextResult>(
    '/inpatientRegistration/loadInpatientRegistrationContext',
    params,
  );
}

/** 提交住院登记（含可选预交金） */
export async function httpSubmitInpatientRegistration(
  body: SubmitRegistrationParams,
): Promise<SubmitRegistrationResult> {
  return publicRequestClient.post<SubmitRegistrationResult>(
    '/inpatientRegistration/submitInpatientRegistration',
    body,
  );
}

/** 取消住院登记 */
export async function httpCancelInpatientRegistration(
  body: CancelRegistrationParams,
): Promise<CancelRegistrationResult> {
  return publicRequestClient.post<CancelRegistrationResult>(
    '/inpatientRegistration/cancelInpatientRegistration',
    body,
  );
}

/** 待入科医保退入院（撤销 2404 并取消登记） */
export async function httpCancelInsuranceInpatientAdmission(
  body: CancelRegistrationParams,
): Promise<CancelInsuranceInpatientAdmissionResult> {
  return publicRequestClient.post<CancelInsuranceInpatientAdmissionResult>(
    '/inpatientRegistration/cancelInsuranceInpatientAdmission',
    body,
  );
}

/** 住院医保转自费（IF-IP-REG-09） */
export async function httpSwitchInpatientToSelfPay(
  body: SwitchInpatientToSelfPayParams,
): Promise<SwitchInpatientToSelfPayResult> {
  return publicRequestClient.post<SwitchInpatientToSelfPayResult>(
    '/inpatientRegistration/switchEncounterToSelfPay',
    body,
  );
}

/** 住院自费转医保（IF-IP-REG-07） */
export async function httpSwitchInpatientToInsurance(
  body: SwitchInpatientToInsuranceParams,
): Promise<SwitchInpatientToInsuranceResult> {
  return publicRequestClient.post<SwitchInpatientToInsuranceResult>(
    '/inpatientRegistration/switchEncounterToInsurance',
    body,
  );
}

/** 更正入院科室：改院内科室/病区/床位，已医保入院时再发 2403 */
export async function httpChangeAdmissionDepartment(
  body: ChangeAdmissionDepartmentParams,
): Promise<ChangeAdmissionDepartmentResult> {
  return publicRequestClient.post<ChangeAdmissionDepartmentResult>(
    '/inpatientRegistration/changeAdmissionDepartment',
    body,
  );
}

/** 更正入院科室可选病区：全部启用病区，不限目标科室 */
export async function httpListAdmissionDepartmentWards(): Promise<ListWardsByDepartmentResult> {
  return publicRequestClient.post<ListWardsByDepartmentResult>(
    '/inpatientRegistration/listAdmissionDepartmentWards',
    {},
  );
}

/** 更正入院科室时按病区列空床（含本就诊当前床） */
export async function httpListAdmissionDepartmentBeds(
  params: ListAdmissionDepartmentBedsParams,
): Promise<ListAdmissionDepartmentBedsResult> {
  return publicRequestClient.post<ListAdmissionDepartmentBedsResult>(
    '/inpatientRegistration/listAdmissionDepartmentBeds',
    params,
  );
}

/** 按科室查询病区列表 */
export async function httpListWardsByDepartment(
  params: ListWardsByDepartmentParams,
): Promise<ListWardsByDepartmentResult> {
  return publicRequestClient.post<ListWardsByDepartmentResult>(
    '/inpatientRegistration/listWardsByDepartment',
    params,
  );
}

/** 病区床位余量查询 */
export async function httpLoadWardBedCapacity(
  params: LoadWardBedCapacityParams,
): Promise<LoadWardBedCapacityResult> {
  return publicRequestClient.post<LoadWardBedCapacityResult>(
    '/inpatientRegistration/loadWardBedCapacity',
    params,
  );
}

/** 住院登记页初始化请求 */
export type LoadInpatientRegistrationPageInitRequest = Record<string, never>;

/** 住院登记页初始化响应 */
export interface LoadInpatientRegistrationPageInitResponse {
  paymentChannelOptions?: OptionDto[];
}

/** 住院登记页初始化 */
export async function httpLoadInpatientRegistrationPageInit(
  params: LoadInpatientRegistrationPageInitRequest = {},
): Promise<LoadInpatientRegistrationPageInitResponse> {
  return publicRequestClient.post<LoadInpatientRegistrationPageInitResponse>(
    '/inpatientRegistration/loadInpatientRegistrationPageInit',
    params,
  );
}
