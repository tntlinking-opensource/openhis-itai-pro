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
