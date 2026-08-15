import type { PreSettleOutpatientChargesResponse } from '#/api/outpatient-charge/outpatient-charge.types';
import type { PrepareWorkInjuryInvokeResult } from './work-injury.types';

import { ElMessage } from 'element-plus';

import { useUserStore } from '@vben/stores';

import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';
import { normalizeWorkInjuryWinOutput } from './probe-work-injury-filing';
import {
  httpRefreshWorkInjuryPreSettleAmounts,
  httpWorkInjuryPrepareInvoke,
  httpWorkInjurySubmitInvokeResult,
  httpWorkInjuryListSettlementsByPayment,
  httpWorkInjuryStartReverse,
} from './work-injury.http';
import {
  invokeWorkInjuryPlugin,
  isWorkInjuryPluginOnline,
  isWorkInjury2205EmptyRevoke,
} from './work-injury-plugin';
import {
  isWorkInjuryModuleUnavailableError,
  probeWorkInjuryModuleAvailable,
} from './work-injury-module';

async function reportWorkInjuryWinFailure(
  step: PrepareWorkInjuryInvokeResult,
  errMsg: string,
  submitContext: Record<string, unknown>,
): Promise<void> {
  if (!step.sessionId || !step.stepToken) {
    return;
  }
  try {
    await httpWorkInjurySubmitInvokeResult({
      sessionId: step.sessionId,
      stepToken: step.stepToken,
      success: false,
      infcode: '-1',
      errMsg: errMsg.slice(0, 500),
      output: {},
      context: submitContext,
    });
  } catch {
    // 回写失败不掩盖原始 Win 错误
  }
}

async function invokeUntil2206(
  prep: PrepareWorkInjuryInvokeResult,
  encounterId: string,
  paymentDraftId: string,
  chargeItemIds: string[],
  contractId?: string | number,
): Promise<string> {
  let step = prep;
  const submitContext = { chargeItemIds };

  // 单边补落后首步即 COMPLETED：无需再调 Win
  if (step.nextAction === 'COMPLETED' && step.sessionId) {
    return step.sessionId;
  }

  if (step.nextAction !== 'INVOKE_WIN') {
    throw new Error(
      step.errMsg?.trim() ||
        `工伤预结算首步异常：nextAction=${step.nextAction ?? '(空)'}，infno=${step.infno ?? '(空)'}`,
    );
  }

  while (step.nextAction === 'INVOKE_WIN') {
    const infno = step.infno;
    if (!step.sessionId || !step.stepToken || !infno) {
      throw new Error('工伤 invoke prepare 缺少 sessionId/stepToken/infno');
    }
    let winRes: Awaited<ReturnType<typeof invokeWorkInjuryPlugin>>;
    try {
      winRes = await invokeWorkInjuryPlugin(step);
    } catch (error) {
      const errMsg = error instanceof Error ? error.message : String(error ?? '工伤 Win 调用失败');
      // 插件未识别时的兜底：2205 空撤仍交 Whale 幂等推进
      if (infno === '2205' && isWorkInjury2205EmptyRevoke(infno, errMsg)) {
        const sub = await httpWorkInjurySubmitInvokeResult({
          sessionId: step.sessionId!,
          stepToken: step.stepToken!,
          success: false,
          infcode: '-1',
          errMsg,
          output: {},
          context: submitContext,
        });
        if (sub.nextAction === 'FAILED') {
          throw new Error(sub.errMsg || errMsg);
        }
        if (sub.nextAction === 'COMPLETED') {
          throw new Error(`工伤预结算链在 ${infno} 提前结束，未到达 2206`);
        }
        step = await httpWorkInjuryPrepareInvoke({
          phase: 'OP_SETTLE',
          sessionId: step.sessionId,
          encounterId,
          paymentDraftId,
          contractId,
          context: { chargeItemIds },
        });
        continue;
      }
      await reportWorkInjuryWinFailure(step, errMsg, submitContext);
      throw error;
    }
    const sub = await httpWorkInjurySubmitInvokeResult({
      sessionId: step.sessionId,
      stepToken: step.stepToken,
      success: winRes.success !== false,
      infcode: winRes.infcode != null ? String(winRes.infcode) : '0',
      errMsg: winRes.errMsg,
      // 9102 等交易 output 为数组，须包成 Map，否则后端 Map 反序列化失败、会话卡在 step0
      output: normalizeWorkInjuryWinOutput(winRes.output),
      winMsgid: winRes.msgid,
      winRawInput: winRes.rawInput,
      context: submitContext,
    });
    if (sub.nextAction === 'FAILED') {
      throw new Error(sub.errMsg || '工伤 invoke 失败');
    }
    // 2206 后仍有 2207：停在 PREPARE_NEXT，正式收费再跑 2207
    if (infno === '2206' && (sub.nextAction === 'PREPARE_NEXT' || sub.nextAction === 'COMPLETED')) {
      return step.sessionId;
    }
    // 2203 单边补落后整链 COMPLETED（平台已结）
    if (sub.nextAction === 'COMPLETED') {
      return step.sessionId;
    }
    step = await httpWorkInjuryPrepareInvoke({
      phase: 'OP_SETTLE',
      sessionId: step.sessionId,
      encounterId,
      paymentDraftId,
      contractId,
      context: { chargeItemIds },
    });
  }
  throw new Error(
    `工伤预结算未完成 2206：lastInfno=${step.infno ?? '(空)'}，nextAction=${step.nextAction ?? '(空)'}`,
  );
}

/** 预结算返回 WORK_INJURY_INVOKE 时，Win invoke 至 2206 并刷新应付金额。 */
export async function runWorkInjuryChargePreSettleInvoke(
  preSettle: PreSettleOutpatientChargesResponse,
  encounterId: string,
  chargeItemIds: string[],
  contractId?: string | number,
): Promise<PreSettleOutpatientChargesResponse> {
  const step = preSettle.workInjuryInvokeStep;
  if (!step || preSettle.settlementComposition !== 'WORK_INJURY_INVOKE') {
    return preSettle;
  }
  if (step.nextAction !== 'COMPLETED' && !(await isWorkInjuryPluginOnline())) {
    throw new Error('请启动 OpenHIS.Win 并启用工伤模块（/api/work-injury/health）');
  }

  const sessionId = await invokeUntil2206(
    step,
    encounterId,
    preSettle.paymentDraftId,
    chargeItemIds,
    contractId,
  );

  const refreshed = await httpRefreshWorkInjuryPreSettleAmounts({
    paymentDraftId: preSettle.paymentDraftId,
    encounterId,
  });

  const unilateral = step.nextAction === 'COMPLETED';
  ElMessage.success(
    unilateral ? '平台已结算，本地已补齐单边，请确认收费' : '工伤预结算（2206）已完成',
  );
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
  contractId?: string | number,
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
    contractId,
    context: { chargeItemIds },
  });
  // 单边补落后会话已 COMPLETED，无需再调 2207
  if (prep.nextAction === 'COMPLETED') {
    return;
  }
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
    output: normalizeWorkInjuryWinOutput(winRes.output),
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
    const userStore = useUserStore();
    const operatorId =
      params.operatorId ?? resolveLoginPractitionerIdForRequest(userStore.userInfo);
    const prep = await httpWorkInjuryStartReverse({
      settlementRecordId: record.settlementRecordId,
      paymentId: params.originalPaymentId,
      encounterId: params.encounterId,
      contractId: record.contractId,
      operatorId,
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
        output: normalizeWorkInjuryWinOutput(winRes.output),
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
