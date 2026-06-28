/** 医保电子凭证（01）建议最长有效间隔（毫秒），超时须重新展码并刷新预结算。 */
export const YB_EC_VOUCHER_STALE_MS = 5 * 60 * 1000;

export function isEcVoucherMediumType(mediumType?: null | string): boolean {
  return String(mediumType ?? '').trim() === '01';
}

export function isEcVoucherStale(readAtMs?: null | number, nowMs = Date.now()): boolean {
  if (readAtMs === null || readAtMs === undefined || !Number.isFinite(readAtMs)) {
    return false;
  }
  return nowMs - readAtMs > YB_EC_VOUCHER_STALE_MS;
}

/** 后端 INS-015 或平台文案：就诊凭证/ecToken 失效，须重新展码并刷新预结算。 */
export function isInsuranceCredentialExpiredError(
  errorCode?: null | string,
  rawMessage?: null | string,
): boolean {
  if (errorCode === 'INS-015') {
    return true;
  }
  const msg = String(rawMessage ?? '').trim();
  if (!msg) {
    return false;
  }
  if (msg.includes('凭证已失效') || msg.includes('重新展码') || msg.includes('刷新预结算')) {
    return true;
  }
  if (
    msg.includes('电子凭证') &&
    (msg.includes('失效') || msg.includes('过期') || msg.includes('无效'))
  ) {
    return true;
  }
  return msg.includes('ecToken') || msg.includes('mdtrt_cert') || msg.includes('就诊凭证');
}

export function formatInsuranceCredentialExpiredHint(): string {
  return '医保电子凭证可能已过期，请先重新扫医保码并刷新预结算，再确认缴费。';
}
