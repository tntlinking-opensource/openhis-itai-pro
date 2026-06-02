/**
 * 门诊挂号：`/outpatientRegistration` 请求与类型（camelCase，与后端一致）。
 */

import { publicRequestClient } from '#/api/request';

/** 通用下拉选项 */
export interface OptionDto {
  value: string;
  label: string;
  disabled?: boolean;
}

/** 挂号页面初始化请求参数 */
export interface LoadRegisterFormInitParams {
  organizationId?: null | number | string;
  keyword?: string;
}

/** 挂号页面初始化返回：科室、医生、费用性质、号源类型、性别字典等 */
export interface LoadRegisterFormInitResult {
  contractOptions: OptionDto[];
  departmentOptions: OptionDto[];
  /** 患者性别单选项（如 MALE / FEMALE / UNKNOWN，与主数据字典一致） */
  genderOptions: OptionDto[];
  practitionerOptions: OptionDto[];
  serviceTypeOptions: OptionDto[];
}

/** 患者检索分页请求参数 */
export interface PagePatientsForSelectParams {
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

/** 患者检索结果行（用于下拉选择） */
export interface PatientItemDto {
  /** 周岁 */
  age?: null | number;
  birthDate?: string;
  genderCode?: string;
  genderText?: string;
  idCardNoMasked?: string;
  name: string;
  patientBusNo: string;
  patientId: string;
  phoneMasked?: string;
}

/** 患者检索分页返回 */
export interface PagePatientsForSelectResult {
  records: PatientItemDto[];
  total: number | string;
}

/** 医生下拉查询请求参数 */
export interface ListPractitionersParams {
  keyword?: string;
  organizationId?: null | number | string;
}

/** 医生下拉查询返回 */
export interface ListPractitionersResult {
  practitionerOptions: OptionDto[];
}

/** 挂号预结算请求参数（患者信息 + 挂号要素） */
export interface PreRegisterSettlementParams {
  birthDate: string;
  contractId: number | string;
  gender: string;
  idCardNo: string;
  name: string;
  organizationId: number | string;
  patientId?: null | number | string;
  phone: string;
  practitionerId: number | string;
  serviceTypeId: number | string;
}

/** 预结算费用明细行 */
export interface ChargeItemPreviewDto {
  amount: number | string;
  chargeItemCode: string;
  chargeItemName: string;
  quantity: number | string;
  unitPrice: number | string;
}

/** 预结算返回：支付草稿、总额、费用明细（及可选就诊标识） */
export interface PreRegisterSettlementResult {
  chargeItems: ChargeItemPreviewDto[];
  encounterId?: number | string;
  paymentDraftId: number | string;
  totalAmount: number | string;
}

/** 结算时提交的单渠道支付明细 */
export interface PaymentDetailDto {
  payAmount: number | string;
  payChannelCode: string;
  transactionNo?: string;
}

/** 挂号费正式结算请求参数 */
export interface PayRegisterFeeParams {
  paymentDetails: PaymentDetailDto[];
  paymentDraftId: number | string;
}

/** 挂号费正式结算返回 */
export interface PayRegisterFeeResult {
  invoiceId: number | string;
  paymentId: number | string;
  status: string;
}

/** 门诊挂号记录分页查询请求参数 */
export interface PageClinicRegistersParams {
  dateFrom: string;
  dateTo: string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

/**
 * 门诊挂号记录分页列表行（与 pageClinicRegisters 返回实体一致）。
 */
export interface ClinicRegisterItemDto {
  amount: number;
  /** 是否允许窗口退号；缺省时不限制退号按钮（由后端逐步收口时可下发 false） */
  canCancel?: boolean;
  contactPhone: string;
  doctorName: string;
  encounterBusNo: string;
  encounterId: string;
  organizationName: string;
  patientGenderCode: string;
  patientGenderText: string;
  patientName: string;
  /** 待正式结算时的支付草稿 id；无则不可点「收费」 */
  paymentDraftId?: string;
  printCount?: null | number;
  serviceTypeId: string;
  serviceTypeName: string;
  startTime: string;
  /** 就诊状态枚举编码，如 PLANNED */
  statusEnum: string;
  statusText: string;
}

/** 门诊挂号记录分页返回 */
export interface PageClinicRegistersResult {
  records: ClinicRegisterItemDto[];
  total: number | string;
}

/** 退号资格校验请求参数 */
export interface ValidateCancelableParams {
  encounterId: number | string;
  reason?: string;
}

/** 退号资格校验返回 */
export interface ValidateCancelableResult {
  cancelable: boolean;
  reasonCode?: string;
  reasonText?: string;
}

/** 退号并退费请求参数 */
export interface CancelWithRefundParams {
  cancelReason: string;
  encounterId: number | string;
  refundIntent?: string;
  refundRequestId: string;
}

/** 退号并退费返回 */
export interface CancelWithRefundResult {
  encounterStatus: string;
  redInvoiceId?: null | number | string;
  refundStatus: string;
}

/** 挂号历史表格行：与 `ClinicRegisterItemDto` 同源 */
export type RegisterHistoryTableRow = ClinicRegisterItemDto;

/** 挂号窗口打开时加载下拉主数据：科室、医生、费用性质、号源类型等 */
export async function httpLoadRegisterFormInit(
  params: LoadRegisterFormInitParams = {},
): Promise<LoadRegisterFormInitResult> {
  return publicRequestClient.post<LoadRegisterFormInitResult>(
    '/outpatientRegistration/loadRegisterFormInit',
    params,
  );
}

/** 患者检索分页（姓名、病历号、证件号等关键字模糊查） */
export async function httpPagePatientsForSelect(
  params: PagePatientsForSelectParams,
): Promise<PagePatientsForSelectResult> {
  return publicRequestClient.post<PagePatientsForSelectResult>(
    '/outpatientRegistration/pagePatientsForSelect',
    params,
  );
}

/** 按科室（可选）与关键字加载可挂号医生下拉 */
export async function httpListPractitioners(
  params: ListPractitionersParams = {},
): Promise<ListPractitionersResult> {
  return publicRequestClient.post<ListPractitionersResult>(
    '/outpatientRegistration/listPractitioners',
    params,
  );
}

/** 确认挂号要素后的预结算：询价、草稿、生成就诊锚点等（不落正式收款） */
export async function httpPreRegisterSettlement(
  body: PreRegisterSettlementParams,
): Promise<PreRegisterSettlementResult> {
  return publicRequestClient.post<PreRegisterSettlementResult>(
    '/outpatientRegistration/preRegisterSettlement',
    body,
  );
}

/** 按草稿与支付方式收取挂号费（正式结算） */
export async function httpPayRegisterFee(
  body: PayRegisterFeeParams,
): Promise<PayRegisterFeeResult> {
  return publicRequestClient.post<PayRegisterFeeResult>(
    '/outpatientRegistration/payRegisterFee',
    body,
  );
}

/** 挂号记录列表分页（按日期区间与关键字筛） */
export async function httpPageClinicRegisters(
  params: PageClinicRegistersParams,
): Promise<PageClinicRegistersResult> {
  return publicRequestClient.post<PageClinicRegistersResult>(
    '/outpatientRegistration/pageClinicRegisters',
    params,
  );
}

/** 退号前校验当前就诊是否允许退号 */
export async function httpValidateCancelable(
  body: ValidateCancelableParams,
): Promise<ValidateCancelableResult> {
  return publicRequestClient.post<ValidateCancelableResult>(
    '/outpatientRegistration/validateCancelable',
    body,
  );
}

/** 提交退号并按策略联动退费（含退款幂等键） */
export async function httpCancelWithRefund(
  body: CancelWithRefundParams,
): Promise<CancelWithRefundResult> {
  return publicRequestClient.post<CancelWithRefundResult>(
    '/outpatientRegistration/cancelWithRefund',
    body,
  );
}
