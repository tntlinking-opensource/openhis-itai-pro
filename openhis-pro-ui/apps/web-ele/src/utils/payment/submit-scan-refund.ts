import type { ClientRefundResultDto } from '#/api/payment/payment.types';

import { isUnionPayWinReady } from '#/api/local/zxplatWin';
import { httpListOriginalPayChannelCodes } from '#/api/outpatient-refund/outpatient-refund.http';
import { httpGetBpcOrdersByPayment, type BpcOrderSummaryDto } from '#/api/payment/union-pay-bpc';
import {
  queryOrderApi,
  refundApi,
  toClientRefundResultDto,
  UnionPayWinError,
} from '#/api/local/unionPayWin';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

import { mayRequireUnionPayBpcRefund } from './scan-refund-gate';
import {
  isUnionPayBpcModuleUnavailableError,
  probeUnionPayBpcModuleAvailable,
} from './union-pay-bpc-module';

export interface SubmitScanRefundParams {
  originalPaymentId: number | string;
  /**
   * 调用方提示金额（元，医院账口径）。有 BPC 成功消费单时：Win/银联退货一律按
   * 渠道患者实付 `paidAmtFen ?? txnAmtFen`，避免把医院账 5 元打给银联（患者只付了 4.99）。
   * 医院 HIS 冲账仍按原支付下单金额，不读本字段覆盖。
   */
  refundAmountYuan?: number | string;
  /** 已知原支付落库渠道时可跳过查渠道接口（退费页分组已带） */
  originalPayChannelCodes?: string[];
  /** 已知银联已退成功时可传入，跳过 Win 重退（半成功收口） */
  knownClientRefundResult?: ClientRefundResultDto | null;
}

/** 退费前银联路径判定（unionPay 包 → 原支付渠道 → BPC 原单）。 */
export type ScanRefundPath =
  | { kind: 'module_unavailable' }
  | { kind: 'none' }
  | { kind: 'unionpay'; successOrder: BpcOrderSummaryDto }
  /** BPC 消费单已 REFUNDED：银联侧已退，只需带凭证收口院内 */
  | { kind: 'already_refunded'; order: BpcOrderSummaryDto };

const CLIENT_REFUND_CACHE_PREFIX = 'zxplat.scanRefund.clientResult:';

/** 本机银联退货凭证 sessionStorage key。 */
function cacheStorageKey(paymentId: number | string): string {
  return `${CLIENT_REFUND_CACHE_PREFIX}${String(paymentId)}`;
}

/** 读取本机缓存的银联退货凭证（Win 成功、院内未收口时供重试）。 */
export function peekCachedClientRefundResult(
  paymentId: number | string,
): ClientRefundResultDto | null {
  try {
    const raw = sessionStorage.getItem(cacheStorageKey(paymentId));
    if (!raw) {
      return null;
    }
    return JSON.parse(raw) as ClientRefundResultDto;
  } catch {
    return null;
  }
}

export function cacheClientRefundResult(
  paymentId: number | string,
  result: ClientRefundResultDto,
): void {
  try {
    sessionStorage.setItem(cacheStorageKey(paymentId), JSON.stringify(result));
  } catch {
    // sessionStorage 不可用时忽略；重试仍可依赖「已退」幂等
  }
}

export function clearCachedClientRefundResult(paymentId: number | string): void {
  try {
    sessionStorage.removeItem(cacheStorageKey(paymentId));
  } catch {
    // ignore
  }
}

function findBpcSuccessOrder(orders: BpcOrderSummaryDto[]): BpcOrderSummaryDto | undefined {
  return orders.find(
    (row) =>
      row.status === 'SUCCESS' &&
      (row.tranType === 'C' || !row.tranType) &&
      row.tradeNo &&
      row.merTradeNo,
  );
}

/** 仅消费单已标 REFUNDED 时视为银联已退（禁止回落到未退的 C 单）。 */
function findBpcAlreadyRefundedOrder(orders: BpcOrderSummaryDto[]): BpcOrderSummaryDto | undefined {
  return orders.find(
    (row) =>
      row.status === 'REFUNDED' &&
      (row.tranType === 'C' || !row.tranType) &&
      row.merTradeNo &&
      row.tradeNo,
  );
}

/**
 * 银联 Win 应退元：渠道患者实付 `paidAmtFen`，无实付时回落院内下单 `txnAmtFen`。
 * 与 BPC Gate 一致；医院 HIS 冲账仍按原支付下单金额，不在此混用。
 *
 * @param order BPC 原消费单摘要
 * @param fallback 无 BPC 金额时的兜底（元）
 */
function resolveRefundAmountYuan(order: BpcOrderSummaryDto, fallback?: number | string): string {
  let channelFen = 0;
  if (order.paidAmtFen != null && Number(order.paidAmtFen) > 0) {
    channelFen = Number(order.paidAmtFen);
  } else if (order.txnAmtFen != null && Number(order.txnAmtFen) > 0) {
    channelFen = Number(order.txnAmtFen);
  }
  if (Number.isFinite(channelFen) && channelFen > 0) {
    return formatMoneyDisplay(channelFen / 100);
  }
  return formatMoneyDisplay(fallback ?? 0);
}

/** 银联明确「已退完」类文案（禁止用裸「已退」，避免查单假阳）。 */
function isAlreadyRefundedWinMessage(message: string): boolean {
  const text = message.trim();
  return /已全额退款|订单已全额退款|订单已退货|已经退货|重复退货|重复退款/.test(text);
}

/**
 * 缓存凭证须对应当前 BPC 原单：merTradeNo + 扫码段金额一致。
 */
function isCachedResultValidForOrder(
  cached: ClientRefundResultDto,
  order: BpcOrderSummaryDto,
): boolean {
  if (cached.respCode !== '00') {
    return false;
  }
  const cachedMer = String(cached.merTradeNo ?? '').trim();
  const orderMer = String(order.merTradeNo ?? '').trim();
  if (!cachedMer || cachedMer !== orderMer) {
    return false;
  }
  const channelFen = (() => {
    if (order.paidAmtFen != null && Number(order.paidAmtFen) > 0) {
      return Number(order.paidAmtFen);
    }
    if (order.txnAmtFen != null && Number(order.txnAmtFen) > 0) {
      return Number(order.txnAmtFen);
    }
    return 0;
  })();
  // 无渠道金额时仅校验商户单号，避免旧缓存误拦截
  if (!Number.isFinite(channelFen) || channelFen <= 0) {
    return Boolean(cachedMer);
  }
  const expected = resolveRefundAmountYuan(order);
  const cachedAmt = formatMoneyDisplay(cached.refundAmountYuan ?? 0);
  return cachedAmt === expected;
}

function resolveCachedClientRefund(
  paymentId: number | string,
  path: Extract<ScanRefundPath, { kind: 'unionpay' | 'already_refunded' }>,
  knownFromCaller?: ClientRefundResultDto | null,
): ClientRefundResultDto | null {
  const order = path.kind === 'unionpay' ? path.successOrder : path.order;
  const candidates = [knownFromCaller, peekCachedClientRefundResult(paymentId)].filter(
    Boolean,
  ) as ClientRefundResultDto[];
  for (const candidate of candidates) {
    if (isCachedResultValidForOrder(candidate, order)) {
      return {
        ...candidate,
        merTradeNo: String(order.merTradeNo),
        refundAmountYuan: resolveRefundAmountYuan(order, candidate.refundAmountYuan),
      };
    }
  }
  if (peekCachedClientRefundResult(paymentId)) {
    clearCachedClientRefundResult(paymentId);
  }
  return null;
}

async function resolveOriginalPayChannelCodes(
  originalPaymentId: number | string,
  known?: string[],
): Promise<string[]> {
  if (known !== undefined) {
    return known;
  }
  return (
    (await httpListOriginalPayChannelCodes({
      paymentId: String(originalPaymentId),
    })) ?? []
  );
}

/**
 * 判定本笔原支付是否须走银联退货。
 *
 * - unionPayBpc 包未起 → 普通退，后续渠道/BPC/Win 均不碰
 * - 原支付已知仅现金/医保等 → 普通退，不调 BPC
 * - 渠道未知/空 → 仍查 BPC（避免漏拦扫码单）
 * - 无 SUCCESS 消费单 → 普通退；消费单已 REFUNDED 则 already_refunded
 * - 有 SUCCESS 消费单 → 必须 Win 退货后再本地退
 * - 查单失败（非模块未开通）→ 抛错，不默认普通退
 */
export async function resolveScanRefundPath(
  originalPaymentId: number | string,
  originalPayChannelCodes?: string[],
): Promise<ScanRefundPath> {
  const moduleAvailable = await probeUnionPayBpcModuleAvailable();
  if (moduleAvailable === false) {
    return { kind: 'module_unavailable' };
  }

  const payChannelCodes = await resolveOriginalPayChannelCodes(
    originalPaymentId,
    originalPayChannelCodes,
  );
  if (payChannelCodes.length > 0 && !mayRequireUnionPayBpcRefund(payChannelCodes)) {
    return { kind: 'none' };
  }

  let orders: BpcOrderSummaryDto[];
  try {
    orders = (await httpGetBpcOrdersByPayment(originalPaymentId)) ?? [];
  } catch (error) {
    if (isUnionPayBpcModuleUnavailableError(error)) {
      return { kind: 'module_unavailable' };
    }
    const detail = error instanceof Error ? error.message : '';
    throw new Error(
      detail
        ? `无法查询银联收单记录：${detail}`
        : '无法查询银联收单记录，暂不能确认退费方式，请稍后重试',
    );
  }

  const successOrder = findBpcSuccessOrder(orders);
  if (successOrder?.tradeNo && successOrder.merTradeNo) {
    return { kind: 'unionpay', successOrder };
  }

  const refundedOrder = findBpcAlreadyRefundedOrder(orders);
  if (refundedOrder?.merTradeNo && refundedOrder.tradeNo) {
    return { kind: 'already_refunded', order: refundedOrder };
  }

  return { kind: 'none' };
}

/** 查 BPC 原单是否存在 SUCCESS 消费单（模块未开通时返回 false）。 */
export async function hasBpcSuccessOrder(
  originalPaymentId: number | string,
  originalPayChannelCodes?: string[],
): Promise<boolean> {
  const path = await resolveScanRefundPath(originalPaymentId, originalPayChannelCodes);
  return path.kind === 'unionpay';
}

function buildClientRefundResult(
  merTradeNo: string,
  tradeNo: string,
  refundAmountYuan: string,
  respMsg: string,
): ClientRefundResultDto {
  return {
    merTradeNo,
    tradeNo,
    respCode: '00',
    respMsg,
    refundAmountYuan,
  };
}

/**
 * 原路银联退货：unionPay 包 → 原支付渠道 → BPC → Win R。
 * 普通退（现金等或包未起）返回 null；须银联退但 Win/退货失败则抛错。
 *
 * 银联 Win 金额按渠道患者实付 `paidAmtFen ?? txnAmtFen`；医院 HIS 冲账仍按原下单。
 * Win 已成功或银联回报明确「已全额退款」时幂等返回凭证，供院内收口重试。
 */
export async function submitScanRefund(
  params: SubmitScanRefundParams,
): Promise<ClientRefundResultDto | null> {
  const paymentId = params.originalPaymentId;

  const path = await resolveScanRefundPath(paymentId, params.originalPayChannelCodes);
  if (path.kind === 'module_unavailable' || path.kind === 'none') {
    return null;
  }

  const cached = resolveCachedClientRefund(paymentId, path, params.knownClientRefundResult);
  if (cached) {
    return cached;
  }

  if (path.kind === 'already_refunded') {
    const refundAmountYuan = resolveRefundAmountYuan(path.order, params.refundAmountYuan);
    const result = buildClientRefundResult(
      path.order.merTradeNo!,
      path.order.tradeNo!,
      refundAmountYuan,
      '银联侧已退货，跳过重复退货',
    );
    cacheClientRefundResult(paymentId, result);
    return result;
  }

  const winReady = await isUnionPayWinReady();
  if (!winReady) {
    throw new Error('原支付为扫码收单，请启动本地客户端（Zxplat.Win）并确认银联组件已安装');
  }

  const merTradeNo = path.successOrder.merTradeNo!;
  const tradeNo = path.successOrder.tradeNo!;
  const refundAmountYuan = resolveRefundAmountYuan(path.successOrder, params.refundAmountYuan);

  try {
    const refundResult = await refundApi({
      merTradeNo,
      tradeNo,
      refundAmountYuan,
    });
    const clientResult = toClientRefundResultDto(refundResult, refundAmountYuan);
    const normalized: ClientRefundResultDto = {
      ...clientResult,
      merTradeNo,
      refundAmountYuan,
    };
    cacheClientRefundResult(paymentId, normalized);
    return normalized;
  } catch (error) {
    const message = error instanceof Error ? error.message : '';
    if (error instanceof UnionPayWinError && isAlreadyRefundedWinMessage(message)) {
      const result = buildClientRefundResult(
        merTradeNo,
        tradeNo,
        refundAmountYuan,
        message || '订单已全额退款',
      );
      cacheClientRefundResult(paymentId, result);
      return result;
    }

    // 头寸/超额：仅当查单文案明确「已全额退款/已退货」才幂等收口
    if (/头寸|超额|退货校验/.test(message)) {
      try {
        const query = await queryOrderApi({ merTradeNo, tradeNo });
        const queryMsg = `${query.respMsg ?? ''}`.trim();
        if (query.success && isAlreadyRefundedWinMessage(queryMsg)) {
          const result = buildClientRefundResult(
            merTradeNo,
            tradeNo,
            refundAmountYuan,
            query.respMsg || '查单确认已退货',
          );
          cacheClientRefundResult(paymentId, result);
          return result;
        }
      } catch {
        // 查单失败仍抛原错误
      }
      throw new Error(
        `${message}。银联只退扫码实付段（本单 ¥${refundAmountYuan}），不含医保个账；请勿另行人工退货，头寸恢复后在本页重试。`,
      );
    }

    throw error instanceof Error ? error : new Error(message || '银联退货失败');
  }
}
