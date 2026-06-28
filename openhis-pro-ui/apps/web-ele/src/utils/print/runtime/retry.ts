import type { HiprintPrintRequest, HiprintPrintResponse } from './hiprint-client';

import { postHiprintPrint } from './hiprint-client';
import { PRINT_ERROR_CODE } from './types';

const MAX_BUSY_RETRIES = 3;

/** PRINT_BUSY 时按 retryAfterMs 重试 hiprint 直打 */
export async function postHiprintPrintWithRetry(
  request: HiprintPrintRequest,
): Promise<HiprintPrintResponse> {
  let last: HiprintPrintResponse = {
    ok: false,
    code: PRINT_ERROR_CODE.PRINT_FAILED,
    message: '打印失败',
  };
  for (let attempt = 0; attempt <= MAX_BUSY_RETRIES; attempt += 1) {
    last = await postHiprintPrint(request);
    if (last.ok) {
      return last;
    }
    const busy = last.code === PRINT_ERROR_CODE.PRINT_BUSY || last.code === 'PRINT_BUSY';
    if (!busy || attempt >= MAX_BUSY_RETRIES) {
      return last;
    }
    const waitMs = last.retryAfterMs ?? 500;
    await new Promise((resolve) => window.setTimeout(resolve, waitMs));
  }
  return last;
}
