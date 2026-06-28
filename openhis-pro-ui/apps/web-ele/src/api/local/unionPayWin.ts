/**
 * OpenHIS.Win 银联 HTTP 客户端（/api/unionpay/*）。
 */
import { getOpenHisWinOrigin } from '#/api/local/openhisWin';

const DEFAULT_TIMEOUT_MS = 65_000;
const QUERY_TIMEOUT_MS = 15_000;

export type UnionPayWinErrorCode = 'HTTP' | 'NETWORK' | 'PARSE' | 'BUSINESS';

export class UnionPayWinError extends Error {
  readonly code: UnionPayWinErrorCode;

  readonly detail?: string;

  constructor(message: string, code: UnionPayWinErrorCode, detail?: string) {
    super(message);
    this.name = 'UnionPayWinError';
    this.code = code;
    this.detail = detail;
  }
}

export interface UnionPayWinBaseResponse {
  success?: boolean;
  respCode?: string;
  respMsg?: string;
  tradeNo?: string;
  payType?: string;
  merTradeNo?: string;
  txnAmtFen?: number;
  queryStatus?: string;
  refNo?: string;
}

export interface ConsumerScanRequest {
  merTradeNo: string;
  scanCode: string;
  amountYuan: string;
  posNo?: string;
}

export interface QueryOrderRequest {
  merTradeNo: string;
  tradeNo?: string;
  posNo?: string;
}

export interface RefundRequest {
  merTradeNo: string;
  tradeNo: string;
  refundAmountYuan: string;
  refundMerTradeNo?: string;
  originalTxnDate?: string;
  originalRefNo?: string;
  originalVoucherNo?: string;
  posNo?: string;
}

function getUnionPayBaseUrl(origin = getOpenHisWinOrigin()): string {
  return `${origin}/api/unionpay`;
}

async function postUnionPay<T extends UnionPayWinBaseResponse>(
  path: string,
  body: Record<string, unknown>,
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<T> {
  const url = `${getUnionPayBaseUrl()}${path}`;
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    const text = await response.text();
    let payload: T;
    try {
      payload = JSON.parse(text) as T;
    } catch {
      throw new UnionPayWinError('银联本地桥接响应解析失败', 'PARSE', text);
    }
    if (!response.ok) {
      throw new UnionPayWinError(
        payload.respMsg || `银联请求失败 (${response.status})`,
        'HTTP',
        text,
      );
    }
    return payload;
  } catch (error) {
    if (error instanceof UnionPayWinError) {
      throw error;
    }
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new UnionPayWinError(
        `银联请求超时（${Math.round(timeoutMs / 1000)} 秒）`,
        'NETWORK',
        url,
      );
    }
    throw new UnionPayWinError('无法连接 OpenHIS.Win 银联桥接', 'NETWORK', url);
  } finally {
    window.clearTimeout(timer);
  }
}

/** 被扫消费（定长 C / B2C 00） */
export async function consumerScanApi(body: ConsumerScanRequest): Promise<UnionPayWinBaseResponse> {
  const result = await postUnionPay<UnionPayWinBaseResponse>('/consumer-scan', body);
  if (!result.success) {
    throw new UnionPayWinError(result.respMsg || '银联被扫失败', 'BUSINESS', result.respCode);
  }
  return result;
}

/** 查单（定长 G / B2C 04） */
export async function queryOrderApi(body: QueryOrderRequest): Promise<UnionPayWinBaseResponse> {
  return postUnionPay<UnionPayWinBaseResponse>('/query-order', body, QUERY_TIMEOUT_MS);
}

/** 退货（定长 R / B2C 02） */
export async function refundApi(body: RefundRequest): Promise<UnionPayWinBaseResponse> {
  const result = await postUnionPay<UnionPayWinBaseResponse>('/refund', body);
  if (!result.success) {
    throw new UnionPayWinError(result.respMsg || '银联退货失败', 'BUSINESS', result.respCode);
  }
  return result;
}

/** 映射 Win 回传 → Whale settle clientScanResult */
export function toClientScanResultDto(result: UnionPayWinBaseResponse) {
  return {
    merTradeNo: result.merTradeNo ?? '',
    tradeNo: result.tradeNo ?? '',
    payType: result.payType ?? '',
    respCode: result.respCode ?? '',
    respMsg: result.respMsg ?? '',
    txnAmtFen: result.txnAmtFen,
  };
}

/** 映射 Win 回传 → Whale 退费 clientRefundResult */
export function toClientRefundResultDto(result: UnionPayWinBaseResponse, refundAmountYuan: string) {
  return {
    merTradeNo: result.merTradeNo ?? '',
    tradeNo: result.tradeNo ?? '',
    // refundMerTradeNo 由 Whale 生成；Win 回传的 merTradeNo 为原单回显，不可当作逆向单号
    respCode: result.respCode ?? '',
    respMsg: result.respMsg ?? '',
    refundAmountYuan,
  };
}
