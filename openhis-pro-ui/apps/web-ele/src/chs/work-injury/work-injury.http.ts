import type {
  BuildWorkInjury2301PayloadRequest,
  BuildWorkInjury2301PayloadResponse,
  PrepareWorkInjuryInvokeRequest,
  PrepareWorkInjuryInvokeResult,
  SubmitWorkInjuryInvokeResultRequest,
  WorkInjuryReverseCommand,
  WorkInjurySettlementRecordResult,
} from './work-injury.types';

import type { WorkInjurySubmitInvokeResultResponse } from '#/api/insurance/work-injury.types';

import { publicRequestClient } from '#/api/request';

const INVOKE_BASE = '/workInjuryInvoke';
const MODULE_BASE = '/workInjuryModule';
const REGISTER_BASE = '/workInjuryRegister';
const CHARGE_BASE = '/workInjuryCharge';
const REFUND_BASE = '/workInjuryRefund';

export async function httpWorkInjuryPrepareInvoke(body: PrepareWorkInjuryInvokeRequest) {
  return publicRequestClient.post<PrepareWorkInjuryInvokeResult>(
    `${INVOKE_BASE}/prepareInvoke`,
    body,
  );
}

export async function httpWorkInjurySubmitInvokeResult(body: SubmitWorkInjuryInvokeResultRequest) {
  return publicRequestClient.post<WorkInjurySubmitInvokeResultResponse>(
    `${INVOKE_BASE}/submitInvokeResult`,
    body,
  );
}

/** 【2301】invoke 入口预览 Win input（不发 Win）。 */
export async function httpWorkInjuryBuild2301Payload(body: BuildWorkInjury2301PayloadRequest) {
  return publicRequestClient.post<BuildWorkInjury2301PayloadResponse>(
    `${INVOKE_BASE}/build2301Payload`,
    body,
  );
}

export async function httpWorkInjuryListSettlementsByPayment(paymentId: string | number) {
  return publicRequestClient.post<WorkInjurySettlementRecordResult[]>(
    `${REFUND_BASE}/listSettlementsByPayment`,
    { paymentId },
  );
}

export async function httpWorkInjuryStartReverse(body: WorkInjuryReverseCommand) {
  return publicRequestClient.post<PrepareWorkInjuryInvokeResult>(
    `${REFUND_BASE}/startReverse`,
    body,
  );
}

export async function httpWorkInjuryBindQualification(body: {
  encounterId: string | number;
  contractId?: string | number;
  qualificationId: string;
}) {
  return publicRequestClient.post<void>(`${REGISTER_BASE}/bindQualification`, body);
}

export async function httpWorkInjuryModuleCapability() {
  return publicRequestClient.post<{
    enabled?: boolean;
    winRequired?: boolean;
    fixmedinsCode?: string;
    contractBusNo?: string;
  }>(`${MODULE_BASE}/getCapability`, {});
}

export async function httpRefreshWorkInjuryPreSettleAmounts(body: {
  paymentDraftId: string | number;
  encounterId: string | number;
}) {
  return publicRequestClient.post<{
    displayAmount?: number | string;
    ybChrgBchno?: string;
    ybMedfeeSumamt?: number | string;
    ybFundPaySumamt?: number | string;
    ybAcctPay?: number | string;
    ybPsnCashPay?: number | string;
  }>(`${CHARGE_BASE}/refreshPreSettleAmounts`, body);
}
