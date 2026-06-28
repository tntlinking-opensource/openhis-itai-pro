import type { ClientRefundResultDto } from '#/api/payment/payment.types';

import { isUnionPayWinReady } from '#/api/local/openhisWin';
import { httpListOriginalPayChannelCodes } from '#/api/outpatient-refund/outpatient-refund.http';
import { httpGetBpcOrdersByPayment, type BpcOrderSummaryDto } from '#/api/payment/union-pay-bpc';
import { refundApi, toClientRefundResultDto } from '#/api/local/unionPayWin';

import { mayRequireUnionPayBpcRefund } from './scan-refund-gate';
import {
  isUnionPayBpcModuleUnavailableError,
  probeUnionPayBpcModuleAvailable,
} from './union-pay-bpc-module';

export interface SubmitScanRefundParams {
  originalPaymentId: number | string;
  refundAmountYuan: number | string;
  /** 已知原支付落库渠道时可跳过查渠道接口（退费页分组已带） */
  originalPayChannelCodes?: string[];
}

/** 退费前银联路径判定（unionPay 包 → 原支付渠道 → BPC 原单）。 */
export type ScanRefundPath =
  | { kind: 'module_unavailable' }
  | { kind: 'none' }
  | { kind: 'unionpay'; successOrder: BpcOrderSummaryDto };

function findBpcSuccessOrder(orders: BpcOrderSummaryDto[]): BpcOrderSummaryDto | undefined {
  return orders.find((row) => row.status === 'SUCCESS' && row.tradeNo && row.merTradeNo);
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
 * - 原支付仅现金/医保等 → 普通退，不调 BPC
 * - 无 SUCCESS 消费单 → 普通退（固定码微信支付宝等）
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
  if (!mayRequireUnionPayBpcRefund(payChannelCodes)) {
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
  if (!successOrder?.tradeNo || !successOrder.merTradeNo) {
    return { kind: 'none' };
  }
  return { kind: 'unionpay', successOrder };
}

/** 查 BPC 原单是否存在 SUCCESS 消费单（模块未开通时返回 false）。 */
export async function hasBpcSuccessOrder(
  originalPaymentId: number | string,
  originalPayChannelCodes?: string[],
): Promise<boolean> {
  const path = await resolveScanRefundPath(originalPaymentId, originalPayChannelCodes);
  return path.kind === 'unionpay';
}

/**
 * 原路银联退货：unionPay 包 → 原支付渠道 → BPC → Win R。
 * 普通退（现金等或包未起）返回 null；须银联退但 Win/退货失败则抛错。
 */
export async function submitScanRefund(
  params: SubmitScanRefundParams,
): Promise<ClientRefundResultDto | null> {
  const path = await resolveScanRefundPath(
    params.originalPaymentId,
    params.originalPayChannelCodes,
  );
  if (path.kind === 'module_unavailable' || path.kind === 'none') {
    return null;
  }

  const winReady = await isUnionPayWinReady();
  if (!winReady) {
    throw new Error('原支付为扫码收单，请启动本地客户端（OpenHIS.Win）并确认银联组件已安装');
  }

  const refundAmountYuan = Number(params.refundAmountYuan ?? 0).toFixed(2);
  const refundResult = await refundApi({
    merTradeNo: path.successOrder.merTradeNo!,
    tradeNo: path.successOrder.tradeNo!,
    refundAmountYuan,
  });
  return toClientRefundResultDto(refundResult, refundAmountYuan);
}
