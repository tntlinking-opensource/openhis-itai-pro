import type { GetOpInsuranceRevokeStatusResponse } from '#/api/insurance-outpatient-revocation';
import type { ExecuteOutpatientBillRefundByPaymentResponse } from '#/api/outpatient-refund/outpatient-refund.types';

import { httpGetOutpatientInsuranceRevokeStatus } from '#/api/insurance-outpatient-revocation';

export const OUTPATIENT_INSURANCE_REVOKE_MODE = {
  INSURANCE_ASYNC: 'INSURANCE_ASYNC',
  SYNC: 'SYNC',
} as const;

export const OP_INSURANCE_REVOKE_POLL_INTERVAL_MS = 2000;
export const OP_INSURANCE_REVOKE_POLL_MAX_ATTEMPTS = 90;

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

/** 退费响应是否为医保撤销异步协同单路径。 */
export function isOutpatientInsuranceAsyncRevokeResponse(
  response: ExecuteOutpatientBillRefundByPaymentResponse | null | undefined,
): boolean {
  if (!response) {
    return false;
  }
  return (
    response.insuranceRevokeMode === OUTPATIENT_INSURANCE_REVOKE_MODE.INSURANCE_ASYNC &&
    Boolean(response.originalPaymentId) &&
    response.success === false
  );
}

export interface PollOutpatientInsuranceRevokeOptions {
  intervalMs?: number;
  maxAttempts?: number;
  onTick?: (status: GetOpInsuranceRevokeStatusResponse, attempt: number) => void;
}

/** 轮询 OP_SETTLE_REVERSE 协同单直至终态（2s 间隔）。 */
export async function pollOutpatientInsuranceRevokeStatus(
  originalPaymentId: string,
  options?: PollOutpatientInsuranceRevokeOptions,
): Promise<GetOpInsuranceRevokeStatusResponse> {
  const intervalMs = options?.intervalMs ?? OP_INSURANCE_REVOKE_POLL_INTERVAL_MS;
  const maxAttempts = options?.maxAttempts ?? OP_INSURANCE_REVOKE_POLL_MAX_ATTEMPTS;
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    const status = await httpGetOutpatientInsuranceRevokeStatus({ originalPaymentId });
    options?.onTick?.(status, attempt);
    const state = String(status.state ?? '').trim();
    if (status.completed || state === 'COMPLETED' || status.insuranceRevoked) {
      return status;
    }
    if (state === 'FAILED' || state === 'CANCELLED') {
      throw new Error(status.errorMsg?.trim() || '医保门诊结算撤销失败');
    }
    if (state === 'UNKNOWN') {
      throw new Error(status.errorMsg?.trim() || '撤销结果待确认，请勿重复退费，请联系医保办处理');
    }
    if (TERMINAL_COORDINATION_STATES.has(state) && state !== 'COMPLETED') {
      throw new Error(status.errorMsg?.trim() || `医保撤销协同单异常状态：${state}`);
    }
    if (attempt < maxAttempts) {
      await sleep(intervalMs);
    }
  }
  throw new Error('医保门诊结算撤销超时，请稍后核对或联系医保办');
}
