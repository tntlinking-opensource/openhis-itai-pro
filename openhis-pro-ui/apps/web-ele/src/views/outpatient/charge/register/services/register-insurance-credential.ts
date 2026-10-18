/** 医保电子凭证（01）建议最长有效间隔（毫秒），超时须重新展码并刷新预结算。 */
export const YB_EC_VOUCHER_STALE_MS = 5 * 60 * 1000;

/**
 * 身份证 SignBuff / 社保卡卡串建议最长有效间隔（毫秒）。
 * 市医保 FMI 对过期卡密常报「解密卡串失败…已超时」；现场约 3～4 分钟即拒，取 2 分钟保守拦截。
 */
export const YB_CARD_CIPHER_STALE_MS = 2 * 60 * 1000;

/** 是否电子凭证介质（01）。 */
export function isEcVoucherMediumType(mediumType?: null | string): boolean {
  return String(mediumType ?? '').trim() === '01';
}

/** 是否身份证读卡介质（02，含 SignBuff 卡密上送）。 */
export function isIdCardMediumType(mediumType?: null | string): boolean {
  return String(mediumType ?? '').trim() === '02';
}

/** 是否社保卡读卡介质（03）。 */
export function isSocialCardMediumType(mediumType?: null | string): boolean {
  return String(mediumType ?? '').trim() === '03';
}

/**
 * 是否需在确认缴费前做凭证时效校验并提供「重读凭证并刷新预结算」。
 * 01 电子凭证、02 身份证卡密、03 社保卡卡串均有平台侧短时效。
 */
export function needsCredentialRefreshBeforePay(mediumType?: null | string): boolean {
  const t = String(mediumType ?? '').trim();
  return t === '01' || t === '02' || t === '03';
}

/**
 * 按介质解析建议有效间隔。
 *
 * @param mediumType 01/02/03
 */
export function resolveCredentialStaleMs(mediumType?: null | string): number {
  if (isEcVoucherMediumType(mediumType)) {
    return YB_EC_VOUCHER_STALE_MS;
  }
  return YB_CARD_CIPHER_STALE_MS;
}

/**
 * 读卡/展码时刻是否已超过建议有效间隔。
 *
 * @param readAtMs 最近一次读凭证时间戳
 * @param mediumType 就诊凭证类型
 * @param nowMs 当前时间
 */
export function isCredentialStale(
  readAtMs?: null | number,
  mediumType?: null | string,
  nowMs = Date.now(),
): boolean {
  if (readAtMs === null || readAtMs === undefined || !Number.isFinite(readAtMs)) {
    return false;
  }
  if (!needsCredentialRefreshBeforePay(mediumType)) {
    return false;
  }
  return nowMs - readAtMs > resolveCredentialStaleMs(mediumType);
}

/** @deprecated 请用 {@link isCredentialStale}；保留别名避免旧调用断裂。 */
export function isEcVoucherStale(readAtMs?: null | number, nowMs = Date.now()): boolean {
  return isCredentialStale(readAtMs, '01', nowMs);
}

/** 后端 INS-015 或平台文案：就诊凭证/ecToken/卡密已失效，须重新读卡或展码并刷新预结算。 */
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
  if (msg.includes('ecToken') || msg.includes('mdtrt_cert') || msg.includes('就诊凭证')) {
    return true;
  }
  // 市医保 FMI：身份证 SignBuff / 社保卡卡串
  if (msg.includes('解密卡串') || msg.includes('encodeCard')) {
    return true;
  }
  if (
    msg.includes('卡串') &&
    (msg.includes('超时') || msg.includes('无效') || msg.includes('过期') || msg.includes('失效'))
  ) {
    return true;
  }
  return false;
}

/**
 * 按介质生成过期提示文案。
 *
 * @param mediumType 01/02/03；未知时给通用句
 */
export function formatInsuranceCredentialExpiredHint(mediumType?: null | string): string {
  if (isIdCardMediumType(mediumType)) {
    return '身份证读卡卡密可能已过期，请重新读身份证并刷新预结算后再确认缴费。';
  }
  if (isSocialCardMediumType(mediumType)) {
    return '社保卡卡串可能已过期，请重新刷社保卡并刷新预结算后再确认缴费。';
  }
  if (isEcVoucherMediumType(mediumType)) {
    return '医保电子凭证可能已过期，请先重新扫医保码并刷新预结算，再确认缴费。';
  }
  return '医保就诊凭证可能已过期，请重新读卡/扫码并刷新预结算后再确认缴费。';
}

/**
 * 确认弹窗「重读凭证」按钮文案。
 *
 * @param mediumType 01/02/03
 */
export function formatCredentialRescanButtonLabel(mediumType?: null | string): string {
  if (isIdCardMediumType(mediumType)) {
    return '重新读身份证并刷新预结算';
  }
  if (isSocialCardMediumType(mediumType)) {
    return '重新刷社保卡并刷新预结算';
  }
  return '重新扫医保码并刷新预结算';
}
