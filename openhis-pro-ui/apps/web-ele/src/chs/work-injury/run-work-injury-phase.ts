import type {
  PrepareWorkInjuryInvokeRequest,
  PrepareWorkInjuryInvokeResult,
  SubmitWorkInjuryInvokeResultResponse,
} from './work-injury.types';

import { httpWorkInjuryPrepareInvoke, httpWorkInjurySubmitInvokeResult } from './work-injury.http';
import { invokeWorkInjuryPlugin } from './work-injury-plugin';
import { normalizeWorkInjuryWinOutput } from './probe-work-injury-filing';

export interface RunWorkInjuryPhaseOptions {
  /** 首步 prepare 请求（无 sessionId）。 */
  initialRequest: PrepareWorkInjuryInvokeRequest;
  /** 已由后端 prepare/start* 返回的首步载荷（避免重复 prepare 覆盖 stepToken）。 */
  initialPrep?: PrepareWorkInjuryInvokeResult;
  /** submit 时附加 context（如 settlementRecordId / chargeItemIds）。 */
  submitContext?: Record<string, unknown>;
  pluginUrl?: string;
}

/**
 * prepare → Win invoke → submit 循环，直至 COMPLETED / FAILED。
 * 对齐 docs/zxplat-module-work-injury/11-客户端invoke会话-详细设计.md §7。
 */
export async function runWorkInjuryPhase(
  options: RunWorkInjuryPhaseOptions,
): Promise<SubmitWorkInjuryInvokeResultResponse> {
  let prep = options.initialPrep ?? (await httpWorkInjuryPrepareInvoke(options.initialRequest));
  const submitContext = options.submitContext;

  while (prep.nextAction === 'INVOKE_WIN') {
    if (!prep.sessionId || !prep.stepToken || !prep.infno) {
      throw new Error('工伤 invoke prepare 缺少 sessionId/stepToken/infno');
    }
    const winRes = await invokeWorkInjuryPlugin(prep, options.pluginUrl);
    const normalizedOutput = normalizeWorkInjuryWinOutput(winRes.output);
    const sub = await httpWorkInjurySubmitInvokeResult({
      sessionId: prep.sessionId,
      stepToken: prep.stepToken,
      success: winRes.success !== false,
      infcode: winRes.infcode != null ? String(winRes.infcode) : '0',
      errMsg: winRes.errMsg,
      output: normalizedOutput,
      winMsgid: winRes.msgid,
      winRawInput: winRes.rawInput,
      context: submitContext,
    });

    if (sub.nextAction === 'COMPLETED') {
      return sub;
    }
    if (sub.nextAction === 'FAILED') {
      throw new Error(sub.errMsg || '工伤 invoke 失败');
    }

    prep = await httpWorkInjuryPrepareInvoke({
      phase: options.initialRequest.phase,
      sessionId: prep.sessionId,
      encounterId: options.initialRequest.encounterId,
      contractId: options.initialRequest.contractId,
      paymentDraftId: options.initialRequest.paymentDraftId,
      context: options.initialRequest.context,
    });
  }

  if (prep.nextAction === 'COMPLETED') {
    return { sessionId: prep.sessionId, nextAction: 'COMPLETED' };
  }
  if (prep.nextAction === 'FAILED') {
    throw new Error(prep.errMsg || '工伤 invoke 失败');
  }
  throw new Error(`未知 nextAction: ${prep.nextAction ?? '(空)'}`);
}

/** 已有 session 时继续 prepare → Win → submit。 */
export async function continueWorkInjurySession(
  prep: PrepareWorkInjuryInvokeResult,
  phase: string,
  base: Omit<PrepareWorkInjuryInvokeRequest, 'phase' | 'sessionId'>,
  submitContext?: Record<string, unknown>,
  pluginUrl?: string,
): Promise<SubmitWorkInjuryInvokeResultResponse> {
  return runWorkInjuryPhase({
    initialRequest: { ...base, phase, sessionId: prep.sessionId },
    initialPrep: prep,
    submitContext,
    pluginUrl,
  });
}
