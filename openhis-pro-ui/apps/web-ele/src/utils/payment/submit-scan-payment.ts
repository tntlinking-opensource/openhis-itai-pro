import type { ClientScanResultDto } from '#/api/payment/payment.types';

import { httpPreparePrepayAdvanceDeposit } from '#/api/inpatient-prepay';
import { httpPrepareConsumerScan } from '#/api/payment/union-pay-bpc';
import { consumerScanApi, toClientScanResultDto, UnionPayWinError } from '#/api/local/unionPayWin';
import { pollUnionPayOrder } from '#/utils/payment/poll-union-pay-order';

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

/** prepareConsumerScan → Win consumer-scan →（必要时）查单轮询 */
export async function submitScanPayment(
  params: SubmitScanPaymentParams,
): Promise<SubmitScanPaymentResult> {
  const amountNum = Number(params.amount ?? 0);
  if (!Number.isFinite(amountNum) || amountNum <= 0) {
    throw new Error('扫码金额无效');
  }
  const scanCode = params.scanCode.trim();
  if (!scanCode) {
    throw new Error('请输入付款码');
  }

  const prepared = await httpPrepareConsumerScan({
    paymentId: params.paymentDraftId,
    detailIndex: params.detailIndex ?? 0,
    amount: amountNum,
  });
  const merTradeNo = prepared.merTradeNo?.trim();
  if (!merTradeNo) {
    throw new Error('预下单失败：未返回商户订单号');
  }

  const amountYuan = amountNum.toFixed(2);
  try {
    const scanResult = await consumerScanApi({
      merTradeNo,
      scanCode,
      amountYuan,
      posNo: prepared.posNo,
    });
    return {
      merTradeNo,
      clientScanResult: toClientScanResultDto(scanResult),
    };
  } catch (error) {
    const shouldPoll =
      error instanceof UnionPayWinError &&
      (error.code === 'NETWORK' || error.code === 'HTTP' || error.message.includes('超时'));
    if (!shouldPoll) {
      throw error;
    }
  }

  const polled = await pollUnionPayOrder({ merTradeNo });
  if (!polled?.success) {
    throw new Error('支付结果未确认，请重试或改其它支付方式');
  }

  return {
    merTradeNo,
    clientScanResult: toClientScanResultDto(polled),
  };
}
