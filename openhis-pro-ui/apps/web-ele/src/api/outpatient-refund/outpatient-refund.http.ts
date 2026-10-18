import type {
  ExecuteOutpatientBillRefundByPaymentRequest,
  ExecuteOutpatientBillRefundByPaymentResponse,
  ListOriginalPayChannelCodesRequest,
  LoadOutpatientRefundExecutionDetailRequest,
  LoadOutpatientRefundExecutionDetailResponse,
  LoadOutpatientRefundPageInitRequest,
  LoadOutpatientRefundPageInitResponse,
  OriginalPayChannelAmountDto,
  PageOutpatientRefundQueueRequest,
  PageOutpatientRefundQueueResponse,
  SwitchPreservedChargeItemsAccountResponse,
  SwitchPreservedChargeItemsRequest,
  VerifyRefundableBeforeExecutionRequest,
  VerifyRefundableBeforeExecutionResponse,
} from './outpatient-refund.types';

import { publicRequestClient } from '#/api/request';

/** 加载门诊退费页初始化（筛选项等） */
export async function httpLoadOutpatientRefundPageInit(
  body: LoadOutpatientRefundPageInitRequest = {},
): Promise<LoadOutpatientRefundPageInitResponse> {
  return publicRequestClient.post<LoadOutpatientRefundPageInitResponse>(
    '/outpatientRefund/loadOutpatientRefundPageInit',
    body,
  );
}

/** 分页查询待退费患者队列 */
export async function httpPageOutpatientRefundQueue(
  body: PageOutpatientRefundQueueRequest,
): Promise<PageOutpatientRefundQueueResponse> {
  return publicRequestClient.post<PageOutpatientRefundQueueResponse>(
    '/outpatientRefund/pageOutpatientRefundQueue',
    body,
  );
}

/** 加载就诊待执行退费明细（按原支付分组） */
export async function httpLoadOutpatientRefundExecutionDetail(
  body: LoadOutpatientRefundExecutionDetailRequest,
): Promise<LoadOutpatientRefundExecutionDetailResponse> {
  return publicRequestClient.post<LoadOutpatientRefundExecutionDetailResponse>(
    '/outpatientRefund/loadOutpatientRefundExecutionDetail',
    body,
  );
}

/** 执行前可退门禁（退药/退耗未完成则拒绝） */
export async function httpVerifyRefundableBeforeExecution(
  body: VerifyRefundableBeforeExecutionRequest,
): Promise<VerifyRefundableBeforeExecutionResponse> {
  return publicRequestClient.post<VerifyRefundableBeforeExecutionResponse>(
    '/outpatientRefund/verifyRefundableBeforeExecution',
    body,
  );
}

/** 按原支付主单查询落库渠道码（退费前判定是否可能银联原路退） */
export async function httpListOriginalPayChannelCodes(
  body: ListOriginalPayChannelCodesRequest,
): Promise<string[]> {
  return publicRequestClient.post<string[]>('/outpatientRefund/listOriginalPayChannelCodes', body);
}

/** 按原支付主单汇总各渠道实付（退号确认窗原路金额，不用就诊预结算现金） */
export async function httpListOriginalPayChannelAmounts(
  body: ListOriginalPayChannelCodesRequest,
): Promise<OriginalPayChannelAmountDto[]> {
  return publicRequestClient.post<OriginalPayChannelAmountDto[]>(
    '/outpatientRefund/listOriginalPayChannelAmounts',
    body,
  );
}

/** 按原支付主单执行门诊账单退费 */
export async function httpExecuteOutpatientBillRefundByPayment(
  body: ExecuteOutpatientBillRefundByPaymentRequest,
): Promise<ExecuteOutpatientBillRefundByPaymentResponse> {
  return publicRequestClient.post<ExecuteOutpatientBillRefundByPaymentResponse>(
    '/outpatientRefund/executeOutpatientBillRefundByPayment',
    body,
  );
}

/** 半退保留行批量转医保记账账户 */
export async function httpSwitchPreservedChargeItemsToInsurance(
  body: SwitchPreservedChargeItemsRequest,
): Promise<SwitchPreservedChargeItemsAccountResponse> {
  return publicRequestClient.post<SwitchPreservedChargeItemsAccountResponse>(
    '/outpatientRefund/switchPreservedChargeItemsToInsurance',
    body,
  );
}

/** 半退保留行批量转自费记账账户 */
export async function httpSwitchPreservedChargeItemsToSelfPay(
  body: SwitchPreservedChargeItemsRequest,
): Promise<SwitchPreservedChargeItemsAccountResponse> {
  return publicRequestClient.post<SwitchPreservedChargeItemsAccountResponse>(
    '/outpatientRefund/switchPreservedChargeItemsToSelfPay',
    body,
  );
}
