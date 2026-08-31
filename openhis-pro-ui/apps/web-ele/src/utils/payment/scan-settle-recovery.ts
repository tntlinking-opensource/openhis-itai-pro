import type { ClientScanResultDto } from '#/api/payment/payment.types';

import { ElMessageBox } from 'element-plus';

import { isUnionPayWinReady } from '#/api/local/openhisWin';
import { refundApi, toClientRefundResultDto, UnionPayWinError } from '#/api/local/unionPayWin';
import { httpApplyClientRefundResult } from '#/api/payment/union-pay-bpc';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

import { clearCachedClientScanResult } from './scan-settle-cache';

/** 银联已成功但本地 settle 未完成（用户取消补结算且未退货） */
export class ScanSettleIncompleteError extends Error {
  readonly clientScanResult: ClientScanResultDto;

  constructor(message: string, clientScanResult: ClientScanResultDto) {
    super(message);
    this.name = 'ScanSettleIncompleteError';
    this.clientScanResult = clientScanResult;
  }
}

/** 半成功已发起银联退货并收口 BPC */
export class ScanSettleRefundedError extends Error {
  readonly clientScanResult: ClientScanResultDto;

  constructor(message: string, clientScanResult: ClientScanResultDto) {
    super(message);
    this.name = 'ScanSettleRefundedError';
    this.clientScanResult = clientScanResult;
  }
}

export interface SettleWithScanRecoveryOptions {
  /** 支付草稿主键：退货成功后清缓存并落 BPC REFUNDED */
  paymentDraftId?: number | string;
  /** 退货金额（元）；缺省按 paidAmtFen / txnAmtFen */
  refundAmountYuan?: number | string;
}

/**
 * 解析半成功退货金额（元）。
 *
 * @param clientScanResult 扫码凭证
 * @param fallback 调用方提示金额
 */
function resolveRefundAmountYuan(
  clientScanResult: ClientScanResultDto,
  fallback?: number | string,
): string {
  const paidFen = Number(clientScanResult.paidAmtFen ?? clientScanResult.txnAmtFen ?? 0);
  if (Number.isFinite(paidFen) && paidFen > 0) {
    return formatMoneyDisplay(paidFen / 100);
  }
  return formatMoneyDisplay(fallback ?? 0);
}

/**
 * 半成功一键银联退货：Win R → applyClientRefundResult → 清本机缓存。
 *
 * @param paymentDraftId 支付草稿
 * @param clientScanResult 原消费凭证
 * @param refundAmountYuan 退货金额
 */
async function refundHalfSuccessScan(
  paymentDraftId: number | string,
  clientScanResult: ClientScanResultDto,
  refundAmountYuan: string,
): Promise<void> {
  const merTradeNo = String(clientScanResult.merTradeNo ?? '').trim();
  const tradeNo = String(clientScanResult.tradeNo ?? '').trim();
  if (!merTradeNo || !tradeNo) {
    throw new Error('缺少银联原单号，无法退货');
  }
  const winReady = await isUnionPayWinReady();
  if (!winReady) {
    throw new Error('请启动本地客户端（OpenHIS.Win）后再发起银联退货');
  }
  const winResult = await refundApi({
    merTradeNo,
    tradeNo,
    refundAmountYuan,
  });
  const clientRefund = toClientRefundResultDto(winResult, refundAmountYuan);
  try {
    await httpApplyClientRefundResult({
      paymentId: paymentDraftId,
      merTradeNo,
      tradeNo,
      refundAmount: refundAmountYuan,
      respCode: clientRefund.respCode || '00',
      respMsg: clientRefund.respMsg,
    });
  } catch {
    // Win 已退成功：即便 BPC 收口失败也清缓存，避免收银员再次补结算误用已退单
  }
  clearCachedClientScanResult(paymentDraftId);
}

/**
 * 扫码后 settle：失败时提示补结算；取消则可一键银联退货（禁止静默重扫）。
 *
 * @param clientScanResult Win/BPC 凭证
 * @param settle 院内落账
 * @param options 草稿主键与退货金额
 */
export async function settleWithScanRecovery<T>(
  clientScanResult: ClientScanResultDto,
  settle: () => Promise<T>,
  options?: SettleWithScanRecoveryOptions,
): Promise<T> {
  try {
    return await settle();
  } catch (error) {
    const message = error instanceof Error ? error.message : '结算失败';
    let action: 'retry' | 'refund' | 'abort' = 'abort';
    try {
      await ElMessageBox.confirm(
        `${message}。银联扣款已成功：请选「补结算」（不会重复扣款），或「银联退货」取消本笔。`,
        '补结算 / 退货',
        {
          confirmButtonText: '补结算',
          cancelButtonText: '银联退货',
          distinguishCancelAndClose: true,
          type: 'warning',
          closeOnClickModal: false,
          closeOnPressEscape: false,
        },
      );
      action = 'retry';
    } catch (boxError) {
      // Element Plus：cancel → 'cancel'；关闭 → 'close'
      if (boxError === 'cancel') {
        action = 'refund';
      } else {
        action = 'abort';
      }
    }

    if (action === 'retry') {
      return await settle();
    }

    if (action === 'refund') {
      const paymentDraftId = options?.paymentDraftId;
      if (paymentDraftId == null || paymentDraftId === '') {
        throw new ScanSettleIncompleteError(
          `${message}。银联已扣款但无法自动退货（缺少支付草稿）：请联系信息科，勿重复扫码。`,
          clientScanResult,
        );
      }
      const refundAmountYuan = resolveRefundAmountYuan(clientScanResult, options?.refundAmountYuan);
      try {
        await refundHalfSuccessScan(paymentDraftId, clientScanResult, refundAmountYuan);
      } catch (refundError) {
        const refundMessage = refundError instanceof Error ? refundError.message : '银联退货失败';
        const already =
          refundError instanceof UnionPayWinError &&
          /已全额退款|订单已全额退款|订单已退货|已经退货|重复退货|重复退款/.test(refundMessage);
        if (!already) {
          throw new ScanSettleIncompleteError(
            `${message}。尝试银联退货失败：${refundMessage}。请联系信息科，勿重复扫码。`,
            clientScanResult,
          );
        }
        clearCachedClientScanResult(paymentDraftId);
      }
      throw new ScanSettleRefundedError(
        `院内落账失败，已对银联发起退货 ¥${refundAmountYuan}。请重新读卡/预结算后再收费，勿直接重扫原码。`,
        clientScanResult,
      );
    }

    throw new ScanSettleIncompleteError(
      `${message}。银联已扣款但院内未落账：请立即再次确认收费完成补结算，或选择银联退货；勿重复扫码。`,
      clientScanResult,
    );
  }
}
