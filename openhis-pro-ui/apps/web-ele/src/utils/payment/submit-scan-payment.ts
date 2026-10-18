import type { ClientScanResultDto } from '#/api/payment/payment.types';

import { httpPreparePrepayAdvanceDeposit } from '#/api/inpatient-prepay';
import {
  httpAbandonPendingScan,
  httpApplyClientScanResult,
  httpGetBpcOrdersByPayment,
  httpPrepareConsumerScan,
  type BpcOrderSummaryDto,
} from '#/api/payment/union-pay-bpc';
import { consumerScanApi, toClientScanResultDto, UnionPayWinError } from '#/api/local/unionPayWin';
import { pollUnionPayOrder } from '#/utils/payment/poll-union-pay-order';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

import { cacheClientScanResult, peekCachedClientScanResult } from './scan-settle-cache';
import { recoverApplyClientScanFailure } from './scan-settle-recovery';

/**
 * 关闭本笔预下单 PENDING（失败吞掉，避免盖住主错误）。
 */
async function abandonPendingQuietly(params: {
  paymentId: number | string;
  merTradeNo: string;
  reason: string;
  respCode: string;
}): Promise<void> {
  try {
    await httpAbandonPendingScan({
      paymentId: params.paymentId,
      merTradeNo: params.merTradeNo,
      reason: params.reason,
      respCode: params.respCode,
    });
  } catch {
    // 关单失败不阻断主流程提示；prepare 重试与草稿作废仍会收口
  }
}

export interface SubmitScanPaymentParams {
  paymentDraftId: number | string;
  amount: number | string;
  scanCode: string;
  detailIndex?: number;
}

export interface SubmitScanPaymentResult {
  clientScanResult: ClientScanResultDto;
  merTradeNo: string;
}

export interface SubmitPrepayScanPaymentParams {
  encounterId: string;
  patientId: string;
  amount: number | string;
  scanCode: string;
  payChannelCode: string;
  operatorId?: string;
  remark?: string;
}

/**
 * 查找未退货的 SUCCESS 消费单（与退货路径口径一致）。
 *
 * @param orders BPC 订单列表
 */
export function findBpcSuccessOrder(orders: BpcOrderSummaryDto[]): BpcOrderSummaryDto | undefined {
  return orders.find(
    (row) =>
      row.status === 'SUCCESS' &&
      (row.tranType === 'C' || !row.tranType) &&
      row.tradeNo &&
      row.merTradeNo,
  );
}

/**
 * 将已落库 SUCCESS 单组装为 clientScanResult，供补结算重传。
 *
 * @param order SUCCESS 消费单
 */
export function toClientScanFromBpcOrder(order: BpcOrderSummaryDto): ClientScanResultDto {
  return {
    merTradeNo: String(order.merTradeNo ?? ''),
    tradeNo: String(order.tradeNo ?? ''),
    payType: String(order.payType ?? ''),
    respCode: '00',
    respMsg: order.respMsg || '银联已扣款，请补结算',
    txnAmtFen: order.txnAmtFen,
    paidAmtFen: order.paidAmtFen,
  };
}

/** 预交金：preparePrepayAdvanceDeposit → prepareConsumerScan → Win 被扫 */
export async function submitPrepayScanPayment(
  params: SubmitPrepayScanPaymentParams,
): Promise<SubmitScanPaymentResult & { paymentDraftId: string }> {
  const amountNum = Number(params.amount ?? 0);
  if (!Number.isFinite(amountNum) || amountNum <= 0) {
    throw new Error('扫码金额无效');
  }
  const preparedDraft = await httpPreparePrepayAdvanceDeposit({
    encounterId: params.encounterId,
    patientId: params.patientId,
    amount: amountNum,
    paymentDetails: [{ amount: amountNum, payChannelCode: params.payChannelCode }],
    operatorId: params.operatorId,
    remark: params.remark,
  });
  const paymentDraftId = preparedDraft.paymentDraftId;
  if (paymentDraftId == null || paymentDraftId === '') {
    throw new Error('预交金预下单失败');
  }
  const scanOutcome = await submitScanPayment({
    paymentDraftId,
    amount: amountNum,
    scanCode: params.scanCode,
  });
  return { ...scanOutcome, paymentDraftId: String(paymentDraftId) };
}

/**
 * prepareConsumerScan → Win consumer-scan → 立即 applyClientScanResult →（必要时）查单轮询。
 *
 * <p>若草稿已有 SUCCESS / 本机半成功缓存，直接返回凭证并禁止再次扣款。</p>
 */
export async function submitScanPayment(
  params: SubmitScanPaymentParams,
): Promise<SubmitScanPaymentResult> {
  const amountNum = Number(params.amount ?? 0);
  if (!Number.isFinite(amountNum) || amountNum <= 0) {
    throw new Error('扫码金额无效');
  }
  const paymentDraftId = params.paymentDraftId;

  // Pre：本机半成功缓存优先（避免重复 C）。
  const cached = peekCachedClientScanResult(paymentDraftId);
  if (
    cached &&
    String(cached.respCode ?? '').trim() === '00' &&
    String(cached.merTradeNo ?? '').trim() &&
    String(cached.tradeNo ?? '').trim()
  ) {
    return { merTradeNo: cached.merTradeNo, clientScanResult: cached };
  }

  // Pre：服务端已 SUCCESS 则复用凭证，走补结算而非重扫。
  try {
    const orders = (await httpGetBpcOrdersByPayment(paymentDraftId)) ?? [];
    const successOrder = findBpcSuccessOrder(orders);
    if (successOrder) {
      const clientScanResult = toClientScanFromBpcOrder(successOrder);
      cacheClientScanResult(paymentDraftId, clientScanResult);
      return {
        merTradeNo: clientScanResult.merTradeNo,
        clientScanResult,
      };
    }
  } catch {
    // 查单失败不阻断正常首次扫码；prepare 侧仍有 SUCCESS 硬拦截
  }

  const scanCode = params.scanCode.trim();
  if (!scanCode) {
    throw new Error('请输入付款码');
  }

  const prepared = await httpPrepareConsumerScan({
    paymentId: paymentDraftId,
    detailIndex: params.detailIndex ?? 0,
    amount: amountNum,
  });
  const merTradeNo = prepared.merTradeNo?.trim();
  if (!merTradeNo) {
    throw new Error('预下单失败：未返回商户订单号');
  }

  const amountYuan = formatMoneyDisplay(amountNum);
  let clientScanResult: ClientScanResultDto;
  try {
    const scanResult = await consumerScanApi({
      merTradeNo,
      scanCode,
      amountYuan,
      posNo: prepared.posNo,
    });
    clientScanResult = toClientScanResultDto(scanResult);
  } catch (error) {
    const shouldPoll =
      error instanceof UnionPayWinError &&
      (error.code === 'NETWORK' || error.code === 'HTTP' || error.message.includes('超时'));
    if (!shouldPoll) {
      await abandonPendingQuietly({
        paymentId: paymentDraftId,
        merTradeNo,
        reason: 'client scan failed',
        respCode: 'CL',
      });
      throw error;
    }
    const polled = await pollUnionPayOrder({ merTradeNo });
    if (!polled?.success) {
      await abandonPendingQuietly({
        paymentId: paymentDraftId,
        merTradeNo,
        reason: 'query timeout unresolved',
        respCode: 'TO',
      });
      throw new Error('支付结果未确认，请重试或改其它支付方式');
    }
    clientScanResult = toClientScanResultDto(polled);
  }

  // 非成功回传：落 FAILED，避免 PENDING 悬挂
  if (String(clientScanResult.respCode ?? '').trim() !== '00') {
    await abandonPendingQuietly({
      paymentId: paymentDraftId,
      merTradeNo: clientScanResult.merTradeNo || merTradeNo,
      reason: clientScanResult.respMsg || 'scan rejected',
      respCode: 'CL',
    });
    throw new Error(clientScanResult.respMsg || '银联被扫失败');
  }

  const normalized: ClientScanResultDto = {
    ...clientScanResult,
    merTradeNo: clientScanResult.merTradeNo || merTradeNo,
    respCode: clientScanResult.respCode || '00',
  };

  /** 组装与首次一致的 apply 入参（含 txnAmtFen=0 全额券场景）。 */
  const buildApplyBody = () => ({
    paymentId: paymentDraftId,
    detailIndex: params.detailIndex ?? 0,
    amount: amountNum,
    merTradeNo: normalized.merTradeNo,
    tradeNo: String(normalized.tradeNo ?? ''),
    payType: String(normalized.payType ?? ''),
    respCode: normalized.respCode || '00',
    respMsg: normalized.respMsg,
    txnAmtFen: normalized.txnAmtFen,
  });

  // Cmd：Win 成功后立刻落 BPC SUCCESS；失败则弹「补落账/银联退货」，禁止重扫。
  try {
    await httpApplyClientScanResult(buildApplyBody());
  } catch (error) {
    const detail = error instanceof Error ? error.message : '';
    const recovered = await recoverApplyClientScanFailure(
      normalized,
      async () => {
        await httpApplyClientScanResult(buildApplyBody());
      },
      {
        paymentDraftId,
        detailIndex: params.detailIndex ?? 0,
        amountYuan: amountNum,
        refundAmountYuan: amountNum,
        applyErrorMessage: detail,
      },
    );
    return {
      merTradeNo: recovered.merTradeNo,
      clientScanResult: recovered,
    };
  }

  cacheClientScanResult(paymentDraftId, normalized);
  return {
    merTradeNo: normalized.merTradeNo,
    clientScanResult: normalized,
  };
}
