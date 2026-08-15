/**
 * 银联 BPC — POST /api/app/unionPayBpc/{method}
 */
import { publicRequestClient } from '#/api/request';

const BASE = '/unionPayBpc';

export interface PrepareConsumerScanRequest {
  paymentId: number | string;
  detailIndex?: number;
  amount: number | string;
  fixmedinsCode?: string;
  operatorId?: number | string;
  orgId?: number | string;
}

export interface PrepareConsumerScanResponse {
  orderId?: number | string;
  merTradeNo?: string;
  posNo?: string;
  mid?: string;
  tid?: string;
  txnAmtFen?: number;
  transport?: string;
}

export interface BpcOrderSummaryDto {
  orderId?: number | string;
  paymentId?: number | string;
  detailIndex?: number;
  merTradeNo?: string;
  tradeNo?: string;
  tranType?: string;
  txnAmtFen?: number;
  payType?: string;
  status?: string;
  respCode?: string;
  respMsg?: string;
  posNo?: string;
}

export async function httpPrepareConsumerScan(body: PrepareConsumerScanRequest) {
  return publicRequestClient.post<PrepareConsumerScanResponse>(`${BASE}/prepareConsumerScan`, body);
}

/** Win 被扫成功后立即落 BPC SUCCESS（独立于 settle 事务，防半成功丢单） */
export interface ApplyClientScanResultRequest {
  paymentId: number | string;
  detailIndex?: number;
  amount: number | string;
  merTradeNo: string;
  tradeNo: string;
  payType: string;
  respCode: string;
  respMsg?: string;
  txnAmtFen?: number;
}

export async function httpApplyClientScanResult(body: ApplyClientScanResultRequest) {
  return publicRequestClient.post(`${BASE}/applyClientScanResult`, body);
}

/** Win 退货成功后落 BPC REFUNDED（HIS 未落账的半成功退货收口） */
export interface ApplyClientRefundResultRequest {
  paymentId: number | string;
  merTradeNo: string;
  tradeNo: string;
  refundAmount: number | string;
  respCode: string;
  respMsg?: string;
  refundMerTradeNo?: string;
}

export async function httpApplyClientRefundResult(body: ApplyClientRefundResultRequest) {
  return publicRequestClient.post(`${BASE}/applyClientRefundResult`, body);
}

export async function httpGetBpcOrdersByPayment(paymentId: number | string) {
  return publicRequestClient.post<BpcOrderSummaryDto[]>(`${BASE}/getOrderByPayment`, {
    paymentId,
  });
}

export interface RetrySettleFromBpcOrderRequest {
  paymentId: number | string;
  merTradeNo: string;
}

export interface RetrySettleFromBpcOrderResponse {
  eligible?: boolean;
  message?: string;
}

export async function httpRetrySettleFromBpcOrder(body: RetrySettleFromBpcOrderRequest) {
  return publicRequestClient.post<RetrySettleFromBpcOrderResponse>(
    `${BASE}/retrySettleFromBpcOrder`,
    body,
  );
}

/**
 * 探测 unionPayBpc 包是否已装配（空请求即可，模块未起时框架返回 NOT_FOUND / MODULE_NOT_AVAILABLE）。
 */
export async function httpProbeUnionPayBpcModule(): Promise<void> {
  await publicRequestClient.post(`${BASE}/retrySettleFromBpcOrder`, {});
}

export interface PageBpcOrdersRequest {
  pageNo?: number;
  pageSize?: number;
  dateFrom?: string;
  dateTo?: string;
  /** 仅 BPC 已成功、HIS payment 仍为 DRAFT 的半成功单 */
  pendingSettleOnly?: boolean;
  /** 仅 HIS 已退、银联未退的孤儿单 */
  orphanRefundOnly?: boolean;
  paymentId?: number | string;
  merTradeNo?: string;
  tradeNo?: string;
  status?: string;
  payType?: string;
  tranType?: string;
}

export interface BpcOrderListItemDto {
  orderId?: number | string;
  paymentId?: number | string;
  detailIndex?: number;
  merTradeNo?: string;
  tradeNo?: string;
  tranType?: string;
  txnAmtFen?: number;
  payType?: string;
  status?: string;
  respCode?: string;
  respMsg?: string;
  posNo?: string;
  /** fin_payment_reconciliation.status_enum */
  paymentStatus?: string;
  /** BPC SUCCESS + C 且 payment 仍为 DRAFT */
  pendingSettle?: boolean;
  /** HIS 已逆向退费，BPC 消费单仍 SUCCESS */
  orphanRefund?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface PageBpcOrdersResponse {
  total?: number;
  rows?: BpcOrderListItemDto[];
}

export async function httpPageBpcOrders(body: PageBpcOrdersRequest) {
  return publicRequestClient.post<PageBpcOrdersResponse>(`${BASE}/pageOrders`, body);
}

export async function httpQueryBpcOrder(body: {
  merTradeNo?: string;
  tradeNo?: string;
  paymentId?: number | string;
}) {
  return publicRequestClient.post<{
    success?: boolean;
    orderId?: number | string;
    merTradeNo?: string;
    tradeNo?: string;
    status?: string;
    payType?: string;
    txnAmtFen?: number;
  }>(`${BASE}/queryOrder`, body);
}

export interface ConfirmOrphanClientRefundRequest {
  paymentId: number | string;
  merTradeNo: string;
  tradeNo: string;
  refundAmount: number | string;
  respCode: string;
  respMsg?: string;
  refundMerTradeNo?: string;
  operatorId?: number | string;
}

export interface ConfirmOrphanClientRefundResponse {
  success?: boolean;
  orderId?: number | string;
  merTradeNo?: string;
  refundMerTradeNo?: string;
  respMsg?: string;
}

/** 孤儿补退确认：仅落 BPC 退货，不重做 HIS 本地退费 */
export async function httpConfirmOrphanClientRefund(body: ConfirmOrphanClientRefundRequest) {
  return publicRequestClient.post<ConfirmOrphanClientRefundResponse>(
    `${BASE}/confirmOrphanClientRefund`,
    body,
  );
}
