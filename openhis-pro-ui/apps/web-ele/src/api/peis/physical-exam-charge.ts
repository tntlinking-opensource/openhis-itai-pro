/**
 * 体检收费 API（AS-PE-FIN-001）。
 */

import type { OptionDto } from '#/api/outpatient-registration';

import { publicRequestClient } from '#/api/request';

/** 队列页签 */
export type PhysicalExamChargeQueueTabCode = 'COMPLETE' | 'WAITING';

/** 收费类型：个检 / 团检 */
export type PhysicalExamChargeKindCode = 'GROUP' | 'PERSONAL';

export interface LoadPhysicalExamChargePageInitResponse {
  chargeItemStatusOptions: OptionDto[];
  paymentChannelOptions?: OptionDto[];
}

export interface PhysicalExamChargeQueueRowDto {
  ageText?: null | string;
  busNo?: null | string;
  encounterId: string;
  genderCode?: null | string;
  genderEnumText?: null | string;
  packageName?: null | string;
  patientId: string;
  patientName?: null | string;
  plannedAmount?: null | number | string;
  queueLabelCode: string;
  /** 是否团检 */
  groupFlag?: boolean | null;
  settledAmount?: null | number | string;
  startTime?: null | string;
}

export interface PagePhysicalExamChargeQueueRequest {
  /** PERSONAL 个检 / GROUP 团检 */
  chargeKind?: PhysicalExamChargeKindCode;
  endDate?: null | string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
  startDate?: null | string;
}

export interface PagePhysicalExamChargeQueueResponse {
  records: PhysicalExamChargeQueueRowDto[];
  total: number | string;
}

export interface PhysicalExamChargeItemRowDto {
  chargeItemId: string;
  catalogUnitPrice?: null | number | string;
  itemLabel?: null | string;
  overrideReasonCode?: null | string;
  priceEditable?: boolean | null;
  quantity?: null | number | string;
  /** 收费锚点表名：个检套餐为 wkf_activity_definition，加项为 wkf_service_request */
  serviceTable?: null | string;
  serviceRequestId?: null | string;
  status?: null | string;
  totalPrice?: null | number | string;
  unitPrice?: null | number | string;
}

export interface LoadPhysicalExamChargeItemsResponse {
  busNo?: null | string;
  encounterId: string;
  items: PhysicalExamChargeItemRowDto[];
  packageName?: null | string;
  patientId: string;
  plannedTotalAmount?: null | number | string;
  /** 懒生成非阻断警告（如应收缺零售价） */
  pricingWarnings?: null | string[];
  settledTotalAmount?: null | number | string;
}

export interface AdjustPhysicalExamChargePricesRequest {
  encounterId: string;
  items: Array<{
    chargeItemId: string;
    overrideReasonCode?: string;
    overrideReasonText?: string;
    unitPrice: number | string;
  }>;
}

export interface PreSettlePhysicalExamChargesRequest {
  chargeItemIds: string[];
  encounterId: string;
}

export interface PhysicalExamPreSettleLinePreviewDto {
  chargeItemId: string;
  itemLabel?: null | string;
  totalPrice?: null | number | string;
}

export interface PreSettlePhysicalExamChargesResponse {
  displayAmount?: null | number | string;
  paymentDraftId: string;
  previews: PhysicalExamPreSettleLinePreviewDto[];
}

export interface PhysicalExamPaymentDetailSubmitDto {
  amount: number | string;
  payChannelCode: string;
}

export interface ConfirmPhysicalExamChargeSettlementRequest {
  billDate?: null | string;
  chargeItemIds: string[];
  encounterId: string;
  outcomeEnum: string;
  paymentDetails: PhysicalExamPaymentDetailSubmitDto[];
  paymentDraftId: string;
}

export interface ConfirmPhysicalExamChargeSettlementResponse {
  invoiceId?: null | string;
  paymentId?: null | string;
  success?: boolean;
  successMessageCode?: null | string;
}

export interface RefreshPhysicalExamChargeStatusResponse {
  paid?: boolean;
  unknown?: boolean;
  unpaid?: boolean;
}

export interface PreviewPhysicalExamRefundRequest {
  chargeItemIds: string[];
  encounterId: string;
}

export interface PreviewPhysicalExamRefundResponse {
  originalPayChannelCodes?: string[];
  originalPaymentId: string;
  previews?: PhysicalExamPreSettleLinePreviewDto[];
  refundableAmount?: null | number | string;
  refundingChargeItemIds: string[];
}

export interface RefundPhysicalExamChargesRequest {
  billDate?: string;
  clientRefundResult?: import('#/api/payment/payment.types').ClientRefundResultDto;
  encounterId: string;
  originalPaymentId: string;
  refundingChargeItemIds: string[];
}

export interface RefundPhysicalExamChargesResponse {
  originalPaymentId?: string;
  reversePaymentId?: string;
  success?: boolean;
}

export async function httpLoadPhysicalExamChargePageInit(): Promise<LoadPhysicalExamChargePageInitResponse> {
  return publicRequestClient.post<LoadPhysicalExamChargePageInitResponse>(
    '/physicalExamCharge/loadPageInit',
    {},
  );
}

export async function httpPagePhysicalExamWaitingQueue(
  body: PagePhysicalExamChargeQueueRequest,
): Promise<PagePhysicalExamChargeQueueResponse> {
  return publicRequestClient.post<PagePhysicalExamChargeQueueResponse>(
    '/physicalExamCharge/pageWaitingQueue',
    body,
  );
}

export async function httpPagePhysicalExamCompleteQueue(
  body: PagePhysicalExamChargeQueueRequest,
): Promise<PagePhysicalExamChargeQueueResponse> {
  return publicRequestClient.post<PagePhysicalExamChargeQueueResponse>(
    '/physicalExamCharge/pageCompleteQueue',
    body,
  );
}

export async function httpLoadPhysicalExamChargeItems(body: {
  encounterId: string;
  patientId?: string;
}): Promise<LoadPhysicalExamChargeItemsResponse> {
  return publicRequestClient.post<LoadPhysicalExamChargeItemsResponse>(
    '/physicalExamCharge/loadChargeItems',
    body,
  );
}

export async function httpAdjustPhysicalExamChargePrices(
  body: AdjustPhysicalExamChargePricesRequest,
): Promise<LoadPhysicalExamChargeItemsResponse> {
  return publicRequestClient.post<LoadPhysicalExamChargeItemsResponse>(
    '/physicalExamCharge/adjustChargeItemPrices',
    body,
  );
}

export async function httpPreSettlePhysicalExamCharges(
  body: PreSettlePhysicalExamChargesRequest,
): Promise<PreSettlePhysicalExamChargesResponse> {
  return publicRequestClient.post<PreSettlePhysicalExamChargesResponse>(
    '/physicalExamCharge/preSettleCharges',
    body,
  );
}

export async function httpConfirmPhysicalExamChargeSettlement(
  body: ConfirmPhysicalExamChargeSettlementRequest,
): Promise<ConfirmPhysicalExamChargeSettlementResponse> {
  return publicRequestClient.post<ConfirmPhysicalExamChargeSettlementResponse>(
    '/physicalExamCharge/confirmChargeSettlement',
    body,
  );
}

export async function httpRefreshPhysicalExamChargeStatus(body: {
  encounterId: string;
}): Promise<RefreshPhysicalExamChargeStatusResponse> {
  return publicRequestClient.post<RefreshPhysicalExamChargeStatusResponse>(
    '/physicalExamCharge/refreshChargeStatus',
    body,
  );
}

/** 预览已结项目对应的原支付整单应退金额 */
export async function httpPreviewPhysicalExamRefund(
  body: PreviewPhysicalExamRefundRequest,
): Promise<PreviewPhysicalExamRefundResponse> {
  return publicRequestClient.post<PreviewPhysicalExamRefundResponse>(
    '/physicalExamCharge/previewRefundCharges',
    body,
  );
}

/** 收银员执行体检账单整单退费 */
export async function httpRefundPhysicalExamCharges(
  body: RefundPhysicalExamChargesRequest,
): Promise<RefundPhysicalExamChargesResponse> {
  return publicRequestClient.post<RefundPhysicalExamChargesResponse>(
    '/physicalExamCharge/refundCharges',
    body,
  );
}
