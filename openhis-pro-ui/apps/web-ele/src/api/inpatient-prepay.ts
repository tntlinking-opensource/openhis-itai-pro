/**
 * 住院预交金：`/inpatientPrepay` 请求与类型（camelCase，与后端一致）。
 */

import type { PageApiResponse, PageParams } from '#/types/api-response';
import type { OptionDto } from '#/api/outpatient-registration';

import { publicRequestClient } from '#/api/request';

/** 在院患者队列分页请求 */
export interface PagePrepayPatientQueueParams extends PageParams {
  admissionDate?: string;
  keyword?: string;
  /**
   * 在院范围：IN_HOSPITAL / NOT_IN_HOSPITAL / ALL
   * （与后端 InpatientPrepayStayScope 一致）
   */
  stayScope?: string;
}

/** 在院患者队列行 */
export interface PrepayQueueItemDto {
  accountBalance?: number | string;
  accountBalanceDisplayMode?: string;
  accountBalanceSubText?: string;
  accountBalanceSummaryText?: string;
  availableBalance?: number | string;
  ageText?: string;
  bedName?: string;
  contractDisplay?: string;
  encounterBusNo: string;
  encounterId: string;
  genderEnum?: string;
  genderEnumText?: string;
  lowBalanceWarning?: boolean;
  organizationName?: string;
  patientId: string;
  patientName: string;
  pendingSettlementTotal?: number | string;
  /** 库内可退余额（退费上限） */
  prepayNetBalance?: number | string;
  startTime?: string;
  statusEnum: string;
  statusEnumText: string;
  wardName?: string;
}

/** 预交金流水查询请求 */
export interface ListPrepayPaymentRecordsParams {
  encounterId?: string;
  patientId?: string;
}

/** 预交金支付明细 */
export interface PrepayPaymentDetailDto {
  accountId?: string;
  amount: number | string;
  payChannelCode: string;
}

/** 预交金流水行 */
export interface PrepayPaymentRecordDto {
  amount: number | string;
  bizTypeText: string;
  operatorName?: string;
  /** 支付渠道 code：CASH / WECHAT / ALIPAY / BANK_CARD 等 */
  payChannelCode?: string;
  payMethodText?: string;
  paymentEnum: string;
  paymentId: string;
  statusEnum: string;
  statusEnumText: string;
  ticketNo?: string;
  tradeTime: string;
}

/** 预交金流水列表响应 */
export interface ListPrepayPaymentRecordsResult {
  records: PrepayPaymentRecordDto[];
}

/** 收取预交金请求 */
export interface CollectPrepayAdvanceDepositParams {
  amount: number | string;
  encounterId: string;
  patientId: string;
  paymentCode?: string;
  paymentDetails: PrepayPaymentDetailDto[];
  paymentDraftId?: string;
  clientScanResult?: import('#/api/payment/payment.types').ClientScanResultDto;
  operatorId?: string;
  remark?: string;
}

/** 收取预交金响应 */
export interface CollectPrepayAdvanceDepositResult {
  accountBalance: number | string;
  paymentId: string;
}

/** 预交金扫码预下单响应 */
export interface PreparePrepayAdvanceDepositResult {
  paymentDraftId: string;
}

/** 退预交金请求 */
export interface RefundPrepayAdvanceDepositParams {
  encounterId: string;
  patientId: string;
  paymentDetails: PrepayPaymentDetailDto[];
  operatorId?: string;
  refundAmount: number | string;
  remark?: string;
  originalPaymentId?: string;
  clientRefundResult?: import('#/api/payment/payment.types').ClientRefundResultDto;
}

/** 退预交金响应 */
export interface RefundPrepayAdvanceDepositResult {
  accountBalance: number | string;
  paymentId: string;
}

/** 预交金页初始化请求 */
export type LoadInpatientPrepayPageInitRequest = Record<string, never>;

/** 预交金页初始化响应 */
export interface LoadInpatientPrepayPageInitResponse {
  paymentChannelOptions?: OptionDto[];
}

/** 预交金页初始化 */
export async function httpLoadInpatientPrepayPageInit(
  params: LoadInpatientPrepayPageInitRequest = {},
): Promise<LoadInpatientPrepayPageInitResponse> {
  return publicRequestClient.post<LoadInpatientPrepayPageInitResponse>(
    '/inpatientPrepay/loadInpatientPrepayPageInit',
    params,
  );
}

/** 在院患者队列分页查询 */
export async function httpPagePrepayPatientQueue(
  params: PagePrepayPatientQueueParams,
): Promise<PageApiResponse<PrepayQueueItemDto>> {
  return publicRequestClient.post<PageApiResponse<PrepayQueueItemDto>>(
    '/inpatientPrepay/pagePrepayPatientQueue',
    params,
  );
}

/** 按就诊加载预交金流水 */
export async function httpListPrepayPaymentRecords(
  params: ListPrepayPaymentRecordsParams,
): Promise<ListPrepayPaymentRecordsResult> {
  return publicRequestClient.post<ListPrepayPaymentRecordsResult>(
    '/inpatientPrepay/listPrepayPaymentRecords',
    params,
  );
}

/** 收取预交金（续收） */
export async function httpCollectPrepayAdvanceDeposit(
  body: CollectPrepayAdvanceDepositParams,
): Promise<CollectPrepayAdvanceDepositResult> {
  return publicRequestClient.post<CollectPrepayAdvanceDepositResult>(
    '/inpatientPrepay/collectPrepayAdvanceDeposit',
    body,
  );
}

/** 预交金扫码预下单（落 DRAFT） */
export async function httpPreparePrepayAdvanceDeposit(
  body: CollectPrepayAdvanceDepositParams,
): Promise<PreparePrepayAdvanceDepositResult> {
  return publicRequestClient.post<PreparePrepayAdvanceDepositResult>(
    '/inpatientPrepay/preparePrepayAdvanceDeposit',
    body,
  );
}

/** 退预交金 */
export async function httpRefundPrepayAdvanceDeposit(
  body: RefundPrepayAdvanceDepositParams,
): Promise<RefundPrepayAdvanceDepositResult> {
  return publicRequestClient.post<RefundPrepayAdvanceDepositResult>(
    '/inpatientPrepay/refundPrepayAdvanceDeposit',
    body,
  );
}
