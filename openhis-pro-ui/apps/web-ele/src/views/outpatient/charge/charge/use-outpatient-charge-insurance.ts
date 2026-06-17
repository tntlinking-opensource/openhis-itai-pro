import type { GetOpInsuranceSettleStatusResponse } from '#/api/insurance-outpatient-settlement';
import type {
  ConfirmOutpatientChargeSettlementResponse,
  PendingChargeItemRowDto,
} from '#/api/outpatient-charge/outpatient-charge.types';

import { ElMessage } from 'element-plus';

import { httpGetOutpatientInsuranceSettleStatus } from '#/api/insurance-outpatient-settlement';
import { httpGetInsurancePersonInfo } from '#/chs/api/insurance-person';
import {
  DEFAULT_CARD_READER_HOST,
  DEFAULT_CARD_READER_PORT,
  invokeYbPlugin,
  isYbPluginOnline,
  YB_PLUGIN_FUNCTION_ID,
} from '#/chs/utils/yb-plugin';

const SELF_PAY_HINTS = ['自费', '现金', '个人'];

export const OUTPATIENT_SETTLEMENT_MODE = {
  INSURANCE_ASYNC: 'INSURANCE_ASYNC',
  SYNC: 'SYNC',
} as const;

export const OP_INSURANCE_SETTLE_POLL_INTERVAL_MS = 2000;
export const OP_INSURANCE_SETTLE_POLL_MAX_ATTEMPTS = 90;

const TERMINAL_COORDINATION_STATES = new Set([
  'COMPLETED',
  'FAILED',
  'UNKNOWN',
  'CANCELLED',
  'COMPENSATED',
]);

function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

export interface OutpatientChargeInsuranceContext {
  contractId: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  psnNo: string;
  ybCardInfo: string;
  ybMediumType: string;
}

/** 所选明细是否绑定多个记账账户。 */
export function hasMultipleChargeAccounts(rows: PendingChargeItemRowDto[]): boolean {
  const accountIds = new Set<string>();
  for (const row of rows) {
    accountIds.add(String(row.accountId ?? '__default__'));
  }
  return accountIds.size > 1;
}

/** 是否包含医保记账项。 */
export function selectionIncludesInsuranceItems(rows: PendingChargeItemRowDto[]): boolean {
  for (const row of rows) {
    if (row.contractId) {
      return true;
    }
    const nature = String(row.feeNatureDisplay ?? '').trim();
    if (!nature) {
      continue;
    }
    if (SELF_PAY_HINTS.some((hint) => nature.includes(hint))) {
      continue;
    }
    if (nature.includes('医保')) {
      return true;
    }
  }
  return false;
}

export function resolveInsuranceContractId(rows: PendingChargeItemRowDto[]): string | undefined {
  for (const row of rows) {
    const id = String(row.contractId ?? '').trim();
    if (id) {
      return id;
    }
  }
  return undefined;
}

/** 读卡并 1101，返回预结算所需医保上下文；纯自费返回 null。 */
export async function resolveOutpatientChargeInsuranceContext(
  rows: PendingChargeItemRowDto[],
): Promise<OutpatientChargeInsuranceContext | null> {
  if (!selectionIncludesInsuranceItems(rows)) {
    return null;
  }
  const contractId = resolveInsuranceContractId(rows);
  if (!contractId) {
    ElMessage.warning('所选费用项缺少医保合同，请先转医保或检查账户绑定');
    return null;
  }
  if (!(await isYbPluginOnline())) {
    return null;
  }
  const cardInfo = await invokeYbPlugin({
    FunctionId: YB_PLUGIN_FUNCTION_ID.SOCIAL_CARD,
    IP: DEFAULT_CARD_READER_HOST,
    PORT: DEFAULT_CARD_READER_PORT,
    TIMEOUT: 60,
    LOG_PATH: 'C:/neu_log/',
    SFZ_DRIVER_TYPE: 0,
  });
  const mdtrtCertNo = cardInfo.trim();
  if (!mdtrtCertNo) {
    throw new Error('读卡未返回有效凭证号');
  }
  const person = await httpGetInsurancePersonInfo({
    contractId,
    mdtrtCertType: '03',
    mdtrtCertNo,
  });
  return {
    contractId,
    insuplcAdmdvs: person.insuplcAdmdvs,
    insutype: person.insutype,
    psnNo: person.psnNo,
    ybCardInfo: mdtrtCertNo,
    ybMediumType: '03',
  };
}

/** 正式结算响应是否为医保异步协同单路径。 */
export function isOutpatientInsuranceAsyncConfirmResponse(
  response: ConfirmOutpatientChargeSettlementResponse | null | undefined,
): boolean {
  if (!response) {
    return false;
  }
  return (
    response.settlementMode === OUTPATIENT_SETTLEMENT_MODE.INSURANCE_ASYNC &&
    Boolean(response.paymentId)
  );
}

export interface PollOutpatientInsuranceSettleOptions {
  intervalMs?: number;
  maxAttempts?: number;
  onTick?: (status: GetOpInsuranceSettleStatusResponse, attempt: number) => void;
}

/** 轮询 OP_SETTLE 协同单直至终态（2s 间隔）。 */
export async function pollOutpatientInsuranceSettleStatus(
  paymentId: string,
  options?: PollOutpatientInsuranceSettleOptions,
): Promise<GetOpInsuranceSettleStatusResponse> {
  const intervalMs = options?.intervalMs ?? OP_INSURANCE_SETTLE_POLL_INTERVAL_MS;
  const maxAttempts = options?.maxAttempts ?? OP_INSURANCE_SETTLE_POLL_MAX_ATTEMPTS;
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    const status = await httpGetOutpatientInsuranceSettleStatus({ paymentId });
    options?.onTick?.(status, attempt);
    const state = String(status.state ?? '').trim();
    if (status.completed || state === 'COMPLETED' || status.paymentStatus === 'SUCCESS') {
      return status;
    }
    if (state === 'FAILED' || state === 'CANCELLED') {
      throw new Error(status.errorMsg?.trim() || '医保门诊结算失败');
    }
    if (state === 'UNKNOWN') {
      throw new Error(status.errorMsg?.trim() || '结算结果待确认，请勿重复收费，请联系医保办处理');
    }
    if (TERMINAL_COORDINATION_STATES.has(state) && state !== 'COMPLETED') {
      throw new Error(status.errorMsg?.trim() || `医保协同单异常状态：${state}`);
    }
    if (attempt < maxAttempts) {
      await sleep(intervalMs);
    }
  }
  throw new Error('医保门诊结算超时，请稍后在已收费页签核对或联系医保办');
}
