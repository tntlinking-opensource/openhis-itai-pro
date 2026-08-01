import type {
  ConfirmOutpatientChargeSettlementRequest,
  ConfirmOutpatientChargeSettlementResponse,
  LoadChargePageInitRequest,
  LoadChargePageInitResponse,
  LoadPendingChargeItemsRequest,
  LoadPendingChargeItemsResponse,
  PageOutpatientChargeQueueRequest,
  PageOutpatientChargeQueueResponse,
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

/** 获取收费小票打印数据 */
export async function httpPrintChargeReceipt(
  body: PrintChargeReceiptParams,
): Promise<PrintChargeReceiptResult> {
  return publicRequestClient.post<PrintChargeReceiptResult>(
    '/outpatientCharge/printChargeReceipt',
    body,
  );
}
