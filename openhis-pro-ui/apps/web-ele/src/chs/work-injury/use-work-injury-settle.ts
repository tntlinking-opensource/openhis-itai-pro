import type { PreSettleOutpatientChargesResponse } from '#/api/outpatient-charge/outpatient-charge.types';
import type { PrepareWorkInjuryInvokeResult } from './work-injury.types';

import { ElMessage } from 'element-plus';

import {
  httpRefreshWorkInjuryPreSettleAmounts,
  httpWorkInjuryPrepareInvoke,
  httpWorkInjurySubmitInvokeResult,
  httpWorkInjuryListSettlementsByPayment,
  httpWorkInjuryStartReverse,
} from './work-injury.http';
import { invokeWorkInjuryPlugin, isWorkInjuryPluginOnline } from './work-injury-plugin';
import {
  isWorkInjuryModuleUnavailableError,
  probeWorkInjuryModuleAvailable,
} from './work-injury-module';

async function invokeUntil2206(
  prep: PrepareWorkInjuryInvokeResult,
  encounterId: string,
  paymentDraftId: string,
  chargeItemIds: string[],
): Promise<string | undefined> {
  let step = prep;
  const submitContext = { chargeItemIds };

  while (step.nextAction === 'INVOKE_WIN') {
    const infno = step.infno;
    if (!step.sessionId || !step.stepToken || !infno) {
      throw new Error('工伤 invoke prepare 缺少 sessionId/stepToken/infno');
    }
    const winRes = await invokeWorkInjuryPlugin(step);
    const sub = await httpWorkInjurySubmitInvokeResult({
      sessionId: step.sessionId,
      stepToken: step.stepToken,
      success: winRes.success !== false,
      infcode: winRes.infcode != null ? String(winRes.infcode) : '0',
      errMsg: winRes.errMsg,
      output:
        winRes.output && typeof winRes.output === 'object'
          ? (winRes.output as Record<string, unknown>)
          : {},
      winMsgid: winRes.msgid,
      winRawInput: winRes.rawInput,
      context: submitContext,
    });
    if (sub.nextAction === 'FAILED') {
      throw new Error(sub.errMsg || '工伤 invoke 失败');
    }
    if (infno === '2206' && sub.nextAction === 'PREPARE_NEXT') {
      return step.sessionId;
    }
    if (sub.nextAction === 'COMPLETED') {
      return step.sessionId;
    }
    step = await httpWorkInjuryPrepareInvoke({
      phase: 'OP_SETTLE',
      sessionId: step.sessionId,
      encounterId,
      paymentDraftId,
      context: { chargeItemIds },
    });
  }
  return step.sessionId;
}

/** 预结算返回 WORK_INJURY_INVOKE 时，Win invoke 至 2206 并刷新应付金额。 */
export async function runWorkInjuryChargePreSettleInvoke(
  preSettle: PreSettleOutpatientChargesResponse,
  encounterId: string,
  chargeItemIds: string[],
): Promise<PreSettleOutpatientChargesResponse> {
  const step = preSettle.workInjuryInvokeStep;
  if (!step || preSettle.settlementComposition !== 'WORK_INJURY_INVOKE') {
    return preSettle;
  }
  if (!(await isWorkInjuryPluginOnline())) {
    throw new Error('请启动 OpenHIS.Win 并启用工伤模块（/api/work-injury/health）');
  }

  const sessionId = await invokeUntil2206(
    step,
    encounterId,
    preSettle.paymentDraftId,
    chargeItemIds,
  );

  const refreshed = await httpRefreshWorkInjuryPreSettleAmounts({
    paymentDraftId: preSettle.paymentDraftId,
    encounterId,
  });

  ElMessage.success('工伤预结算（2206）已完成');
  return {
    ...preSettle,
    settlementComposition: 'INSURANCE_ONLY',
    insurancePreSettled: true,
    displayAmount: refreshed.displayAmount ?? preSettle.displayAmount,
    ybChrgBchno: refreshed.ybChrgBchno ?? preSettle.ybChrgBchno,
    ybMedfeeSumamt: refreshed.ybMedfeeSumamt,
    ybFundPaySumamt: refreshed.ybFundPaySumamt,
    ybAcctPay: refreshed.ybAcctPay,
    ybPsnCashPay: refreshed.ybPsnCashPay,
    workInjuryInvokeStep: undefined,
    workInjurySessionId: sessionId,
  };
}

/** 正式结算前 invoke 2207（沿用 2206 后的 sessionId）。 */
export async function runWorkInjuryCharge2207Invoke(
  preSettle: PreSettleOutpatientChargesResponse,
  encounterId: string,
  chargeItemIds: string[],
): Promise<void> {
  if (!(await isWorkInjuryPluginOnline())) {
    throw new Error('请启动 OpenHIS.Win 并启用工伤模块');
  }
  if (!preSettle.workInjurySessionId) {
    throw new Error('缺少工伤 invoke 会话，请重新预结算');
  }
  const submitContext = { chargeItemIds };
  const prep = await httpWorkInjuryPrepareInvoke({
    phase: 'OP_SETTLE',
    sessionId: preSettle.workInjurySessionId,
    encounterId,
    paymentDraftId: preSettle.paymentDraftId,
    context: { chargeItemIds },
  });
  if (prep.nextAction !== 'INVOKE_WIN' || prep.infno !== '2207') {
    throw new Error(`工伤 2207 prepare 异常：infno=${prep.infno ?? '(空)'}`);
  }
  if (!prep.sessionId || !prep.stepToken) {
    throw new Error('工伤 2207 prepare 缺少 sessionId/stepToken');
  }
  const winRes = await invokeWorkInjuryPlugin(prep);
  const sub = await httpWorkInjurySubmitInvokeResult({
    sessionId: prep.sessionId,
    stepToken: prep.stepToken,
    success: winRes.success !== false,
    infcode: winRes.infcode != null ? String(winRes.infcode) : '0',
    errMsg: winRes.errMsg,
    output:
      winRes.output && typeof winRes.output === 'object'
        ? (winRes.output as Record<string, unknown>)
        : {},
    winMsgid: winRes.msgid,
    winRawInput: winRes.rawInput,
    context: submitContext,
  });
  if (sub.nextAction === 'FAILED') {
    throw new Error(sub.errMsg || '工伤 2207 失败');
  }
}

/** 退费前 invoke 2208 撤销；工伤模块未装配时静默跳过。 */
export async function runWorkInjuryRefundReverse(params: {
  encounterId: string;
  originalPaymentId: string;
  operatorId?: string | number;
}): Promise<void> {
  const moduleOn = await probeWorkInjuryModuleAvailable();
  if (moduleOn === false) {
    return;
  }

  let records: Awaited<ReturnType<typeof httpWorkInjuryListSettlementsByPayment>>;
  try {
    records = await httpWorkInjuryListSettlementsByPayment(params.originalPaymentId);
  } catch (error) {
    if (isWorkInjuryModuleUnavailableError(error)) {
      return;
    }
    throw error;
  }

  const pending = records.filter((row) => row.status !== 'REVOKED');
  if (pending.length === 0) {
    return;
  }
  if (!(await isWorkInjuryPluginOnline())) {
    throw new Error('请启动 OpenHIS.Win 并启用工伤模块');
  }
  for (const record of pending) {
    if (!record.settlementRecordId) {
      continue;
    }
    const prep = await httpWorkInjuryStartReverse({
      settlementRecordId: record.settlementRecordId,
      paymentId: params.originalPaymentId,
      encounterId: params.encounterId,
      contractId: record.contractId,
      operatorId: params.operatorId,
    });
    let step = prep;
    const submitContext = { settlementRecordId: record.settlementRecordId };
    while (step.nextAction === 'INVOKE_WIN') {
      if (!step.sessionId || !step.stepToken) {
        throw new Error('工伤 2208 prepare 无效');
      }
      const winRes = await invokeWorkInjuryPlugin(step);
      const sub = await httpWorkInjurySubmitInvokeResult({
        sessionId: step.sessionId,
        stepToken: step.stepToken,
        success: winRes.success !== false,
        infcode: winRes.infcode != null ? String(winRes.infcode) : '0',
        errMsg: winRes.errMsg,
        output:
          winRes.output && typeof winRes.output === 'object'
            ? (winRes.output as Record<string, unknown>)
            : {},
        winMsgid: winRes.msgid,
        winRawInput: winRes.rawInput,
        context: submitContext,
      });
      if (sub.nextAction === 'FAILED') {
        throw new Error(sub.errMsg || '工伤 2208 失败');
      }
      if (sub.nextAction === 'COMPLETED') {
        break;
      }
      step = await httpWorkInjuryPrepareInvoke({
        phase: 'OP_REVERSE',
        sessionId: step.sessionId,
        encounterId: params.encounterId,
        context: submitContext,
      });
    }
  }
  ElMessage.success('工伤结算撤销（2208）已完成');
}
