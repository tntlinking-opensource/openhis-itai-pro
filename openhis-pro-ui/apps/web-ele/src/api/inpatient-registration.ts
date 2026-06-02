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
export type RegistrationListStatus = 'ADMITTED' | 'PLANNED' | 'WAIT_ADMISSION';

/** 待办列表分页请求 */
export interface PageRegistrationQueueParams extends PageParams {
  idCard?: string;
  keyword?: string;
  registrationListStatus?: RegistrationListStatus;
}

/** 待办列表行 */
export interface RegistrationQueueItemDto {
  ambEncounterId?: string;
  bedId?: string;
  bedName?: string;
  cancelable: boolean;
  encounterBusNo: string;
  encounterId: string;
  inWayCode?: string;
  organizationId?: string;
  organizationName?: string;
  patientId: string;
  patientName: string;
  startTime?: string;
  statusEnum: string;
  statusEnumText: string;
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
}

/** 登记弹窗住院就诊 */
export interface RegistrationEncounterDto {
  admitSourceCode?: string;
  ambEncounterId?: string;
  encounterBusNo?: string;
  encounterId?: string;
  inWayCode?: string;
  organizationId?: string;
  organizationName?: string;
  primaryConditionDefinitionId?: string;
  primaryConditionDefinitionName?: string;
  priorityEnum?: string;
  startTime?: string;
  statusEnum?: string;
  wardId?: string;
  wardName?: string;
}

/** 登记上下文加载响应 */
export interface LoadRegistrationContextResult {
  contractOptions: OptionDto[];
  encounter?: RegistrationEncounterDto | null;
  patient?: RegistrationPatientDto | null;
  readOnly: boolean;
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
  conditionId?: string;
  description?: string;
  primaryFlag: boolean;
}

/** 提交住院登记请求 */
export interface SubmitRegistrationParams {
  admissionDiagnoses?: AdmissionDiagnosisSubmitDto[];
  admitSourceCode?: string;
  advanceDepositAmount?: number | string;
  birthDate?: string;
  contractId: string;
  encounterId?: string;
  genderEnum?: string;
  idCard?: string;
  inWayCode?: string;
  operatorId: string;
  organizationId: string;
  patientId?: string;
  patientName?: string;
  paymentDetails?: PaymentDetailDto[];
  phone?: string;
  priorityEnum?: string;
  startTime: string;
  updatePatientFromIdCard?: boolean;
  wardId: string;
}

/** 提交住院登记响应 */
export interface SubmitRegistrationResult {
  accountOpened?: boolean;
  encounterBusNo: string;
  encounterId: string;
  paymentId?: string;
  statusEnum: string;
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

/** 按科室查询病区列表请求 */
export interface ListWardsByDepartmentParams {
  organizationId: string;
}

/** 病区选项 */
export interface WardOptionDto {
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
