/**
 * 体检收费 API（AS-PE-FIN-001）。
 */

import type { OptionDto } from '#/api/outpatient-registration';

import { publicRequestClient } from '#/api/request';

/** 队列页签 */
export type PhysicalExamChargeQueueTabCode = 'COMPLETE' | 'WAITING';

export interface LoadPhysicalExamChargePageInitResponse {
  chargeItemStatusOptions: OptionDto[];
  paymentChannelOptions?: OptionDto[];
}

export interface PhysicalExamChargeQueueRowDto {
  busNo?: null | string;
  encounterId: string;
  packageName?: null | string;
  patientId: string;
  patientName?: null | string;
  plannedAmount?: null | number | string;
  queueLabelCode: string;
  settledAmount?: null | number | string;
  startTime?: null | string;
}

export interface PagePhysicalExamChargeQueueRequest {
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
  itemLabel?: null | string;
  quantity?: null | number | string;
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
  settledTotalAmount?: null | number | string;
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
