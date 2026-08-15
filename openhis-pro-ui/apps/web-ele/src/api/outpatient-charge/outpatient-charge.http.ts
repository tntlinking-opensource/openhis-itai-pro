import type {
  ConfirmOutpatientChargeSettlementRequest,
  ConfirmOutpatientChargeSettlementResponse,
  ConfirmOutpatientInsuranceBeforeScanRequest,
  ConfirmOutpatientInsuranceBeforeScanResponse,
  LoadChargePageInitRequest,
  LoadChargePageInitResponse,
  LoadPendingChargeItemsRequest,
  LoadPendingChargeItemsResponse,
  PageOutpatientChargeQueueRequest,
  PageOutpatientChargeQueueResponse,
  PrepareUnionPayMakeupCollectRequest,
  PreSettleOutpatientChargesRequest,
  PreSettleOutpatientChargesResponse,
  PrintChargeReceiptParams,
  PrintChargeReceiptResult,
  SwitchEncounterChargeItemsAccountResponse,
  SwitchEncounterChargeItemsRequest,
} from './outpatient-charge.types';

import { publicRequestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';

/** 收费页下拉：收费项状态等 */
export async function httpLoadChargePageInit(
  body: LoadChargePageInitRequest = {},
): Promise<LoadChargePageInitResponse> {
  return publicRequestClient.post<LoadChargePageInitResponse>(
    '/outpatientCharge/loadChargePageInit',
    body,
  );
}

/** 分页加载待收/已结就诊队列 */
export async function httpPageOutpatientChargeQueue(
  body: PageOutpatientChargeQueueRequest,
): Promise<PageOutpatientChargeQueueResponse> {
  return publicRequestClient.post<PageOutpatientChargeQueueResponse>(
    '/outpatientCharge/pageOutpatientChargeQueue',
    body,
  );
}

/** 按就诊加载待收费用明细 */
export async function httpLoadPendingChargeItems(
  body: LoadPendingChargeItemsRequest,
): Promise<LoadPendingChargeItemsResponse> {
  return publicRequestClient.post<LoadPendingChargeItemsResponse>(
    '/outpatientCharge/loadPendingChargeItems',
    body,
  );
}

/** 就诊下费用项批量转医保记账账户 */
export async function httpSwitchEncounterChargeItemsToInsurance(
  body: SwitchEncounterChargeItemsRequest,
): Promise<SwitchEncounterChargeItemsAccountResponse> {
  return publicRequestClient.post<SwitchEncounterChargeItemsAccountResponse>(
    '/outpatientCharge/switchEncounterChargeItemsToInsurance',
    body,
  );
}

/** 就诊下费用项批量转自费记账账户 */
export async function httpSwitchEncounterChargeItemsToSelfPay(
  body: SwitchEncounterChargeItemsRequest,
): Promise<SwitchEncounterChargeItemsAccountResponse> {
  return publicRequestClient.post<SwitchEncounterChargeItemsAccountResponse>(
    '/outpatientCharge/switchEncounterChargeItemsToSelfPay',
    body,
  );
}

/** 门诊预结算：生成支付草稿与预览金额（含医保 2201–2206，使用长超时） */
export async function httpPreSettleOutpatientCharges(
  body: PreSettleOutpatientChargesRequest,
): Promise<PreSettleOutpatientChargesResponse> {
  return publicRequestClient.post<PreSettleOutpatientChargesResponse>(
    '/outpatientCharge/preSettleOutpatientCharges',
    body,
    chsLongRequestConfig(),
  );
}

/** 银联补收：复用已确认 2207，生成可扫码落账草稿（不重新预结算） */
export async function httpPrepareUnionPayMakeupCollect(
  body: PrepareUnionPayMakeupCollectRequest,
): Promise<PreSettleOutpatientChargesResponse> {
  return publicRequestClient.post<PreSettleOutpatientChargesResponse>(
    '/outpatientCharge/prepareUnionPayMakeupCollect',
    body,
  );
}

/** 门诊正式结算（确认收费；含医保 2207，使用长超时） */
export async function httpConfirmOutpatientChargeSettlement(
  body: ConfirmOutpatientChargeSettlementRequest,
): Promise<ConfirmOutpatientChargeSettlementResponse> {
  return publicRequestClient.post<ConfirmOutpatientChargeSettlementResponse>(
    '/outpatientCharge/confirmOutpatientChargeSettlement',
    body,
    chsLongRequestConfig(),
  );
}

/** 银联扫码前先完成医保 2207（失败则不发起 Win 扣款） */
export async function httpConfirmOutpatientInsuranceBeforeScan(
  body: ConfirmOutpatientInsuranceBeforeScanRequest,
): Promise<ConfirmOutpatientInsuranceBeforeScanResponse> {
  return publicRequestClient.post<ConfirmOutpatientInsuranceBeforeScanResponse>(
    '/outpatientCharge/confirmOutpatientInsuranceBeforeScan',
    body,
    chsLongRequestConfig(),
  );
}

/** 冲正草稿上已确认的医保 2207（Win 失败或运维补偿；参数名对齐 AppService） */
export async function httpCompensateInsuranceSettlementsByPayment(body: {
  paymentDraftId: string;
}): Promise<void> {
  await publicRequestClient.post(
    '/outpatientCharge/compensateInsuranceSettlementsByPayment',
    { paymentDraftId: body.paymentDraftId },
    chsLongRequestConfig(),
  );
}

/** 获取收费小票打印数据 */
export async function httpPrintChargeReceipt(
  body: PrintChargeReceiptParams,
): Promise<PrintChargeReceiptResult> {
  return publicRequestClient.post<PrintChargeReceiptResult>(
    '/outpatientCharge/printChargeReceipt',
    body,
  );
}
