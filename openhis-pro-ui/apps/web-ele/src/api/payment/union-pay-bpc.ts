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
