import type { PrepareWorkInjuryInvokeResult } from './work-injury.types';

import { ElMessage } from 'element-plus';

import {
  httpWorkInjuryBindQualification,
  httpWorkInjuryPrepareInvoke,
  httpWorkInjurySubmitInvokeResult,
} from './work-injury.http';
import { invokeWorkInjuryPlugin, isWorkInjuryPluginOnline } from './work-injury-plugin';
import { normalizeWorkInjuryWinOutput } from './probe-work-injury-filing';
import { runWorkInjuryPhase } from './run-work-injury-phase';
import type { WorkInjury2301FormValues } from './work-injury-2301-form';
import { toWorkInjury2301Context } from './work-injury-2301-form';

export interface RunWorkInjuryRegister9102Params {
  encounterId: string | number;
  contractId: string | number;
}

export interface RunWorkInjuryRegister2301Params extends RunWorkInjuryRegister9102Params {
  /** 2301 表单；不传则仅依赖 anchor/上下文默认值。 */
  form?: WorkInjury2301FormValues;
}

/**
 * 挂号预结算后 invoke REG_QUERY（1101→9102），必要时 bindQualification。
 * @param options.silent 为 true 时不弹成功提示（详情弹窗内刷新用）。
 */
export async function runWorkInjuryRegister9102(
  params: RunWorkInjuryRegister9102Params,
  options?: { silent?: boolean },
): Promise<void> {
  if (!(await isWorkInjuryPluginOnline())) {
    throw new Error('请启动 OpenHIS.Win 并启用工伤模块（/api/work-injury/health）');
  }
  await runWorkInjuryPhase({
    initialRequest: {
      phase: 'REG_QUERY',
      encounterId: params.encounterId,
      contractId: params.contractId,
    },
  });
  if (!options?.silent) {
    ElMessage.success('工伤人员查询（1101）与审批查询（9102）已完成');
  }
}

/** 2301 审批上传（REG_APPROVAL：2301→9102）。 */
export async function runWorkInjuryRegister2301(
  params: RunWorkInjuryRegister2301Params,
): Promise<void> {
  if (!(await isWorkInjuryPluginOnline())) {
    throw new Error('请启动 OpenHIS.Win 并启用工伤模块');
  }
  await runWorkInjuryPhase({
    initialRequest: {
      phase: 'REG_APPROVAL',
      encounterId: params.encounterId,
      contractId: params.contractId,
      context: params.form ? toWorkInjury2301Context(params.form) : undefined,
    },
  });
  ElMessage.success('工伤审批上传（2301→9102）已完成');
}

export async function bindWorkInjuryQualification(params: {
  encounterId: string | number;
  contractId?: string | number;
  qualificationId: string;
}): Promise<void> {
  await httpWorkInjuryBindQualification({
    encounterId: params.encounterId,
    contractId: params.contractId,
    qualificationId: params.qualificationId,
  });
}

/** 解析 9102 submit 出参中的 qualification 列表（供 UI 选择）。 */
export function parse9102QualificationRows(
  output?: Record<string, unknown> | unknown[],
): Array<{ qualificationId: string; label: string }> {
  if (!output) {
    return [];
  }
  const root = normalizeWorkInjuryWinOutput(output);
  const raw =
    (Array.isArray(root.output) && root.output) ||
    (Array.isArray(root.approveinfo) && root.approveinfo) ||
    (Array.isArray(root.rows) && root.rows) ||
    [];
  const rows: Array<{ qualificationId: string; label: string }> = [];
  for (const item of raw) {
    if (!item || typeof item !== 'object') {
      continue;
    }
    const row = item as Record<string, unknown>;
    const qid = String(
      row.qualification_id ?? row.qualificationId ?? row.referral_id ?? row.referralId ?? '',
    ).trim();
    if (!qid || qid === '0') {
      continue;
    }
    const name = String(
      row.injure_affirm_id ?? row.approveType ?? row.approve_type ?? row.terminReason ?? qid,
    ).trim();
    rows.push({ qualificationId: qid, label: name || qid });
  }
  return rows;
}

/** 手动逐步 9102（返回最后一步 prepare，供多行选择场景）。 */
export async function prepareWorkInjury9102Step(
  params: RunWorkInjuryRegister9102Params,
): Promise<PrepareWorkInjuryInvokeResult> {
  return httpWorkInjuryPrepareInvoke({
    phase: 'REG_QUERY',
    encounterId: params.encounterId,
    contractId: params.contractId,
  });
}

export async function submitWorkInjury9102WinResult(params: {
  step: PrepareWorkInjuryInvokeResult;
  encounterId: string | number;
  contractId: string | number;
}): Promise<Record<string, unknown>> {
  const winRes = await invokeWorkInjuryPlugin(params.step);
  const normalizedOutput = normalizeWorkInjuryWinOutput(winRes.output);
  const sub = await httpWorkInjurySubmitInvokeResult({
    sessionId: params.step.sessionId!,
    stepToken: params.step.stepToken!,
    success: winRes.success !== false,
    infcode: winRes.infcode != null ? String(winRes.infcode) : '0',
    errMsg: winRes.errMsg,
    output: normalizedOutput,
    winMsgid: winRes.msgid,
    winRawInput: winRes.rawInput,
  });
  if (sub.nextAction === 'FAILED') {
    throw new Error(sub.errMsg || '9102 失败');
  }
  return normalizedOutput;
}
