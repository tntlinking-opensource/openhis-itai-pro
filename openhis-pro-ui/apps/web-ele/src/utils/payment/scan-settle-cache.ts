import type { ClientScanResultDto } from '#/api/payment/payment.types';

const CLIENT_SCAN_CACHE_PREFIX = 'zxplat.scanSettle.clientResult:';

/**
 * 半成功扫码凭证缓存键（按支付草稿）。
 *
 * @param paymentId 支付草稿/支付单主键
 */
function cacheStorageKey(paymentId: number | string): string {
  return `${CLIENT_SCAN_CACHE_PREFIX}${String(paymentId)}`;
}

/**
 * 读取本机缓存的银联被扫凭证（Win 成功、院内未落账时供补结算/禁重扫）。
 *
 * @param paymentId 支付草稿主键
 */
export function peekCachedClientScanResult(paymentId: number | string): ClientScanResultDto | null {
  try {
    const raw = sessionStorage.getItem(cacheStorageKey(paymentId));
    if (!raw) {
      return null;
    }
    return JSON.parse(raw) as ClientScanResultDto;
  } catch {
    return null;
  }
}

/**
 * 缓存银联被扫成功凭证。
 *
 * @param paymentId 支付草稿主键
 * @param result Win/BPC 回传
 */
export function cacheClientScanResult(
  paymentId: number | string,
  result: ClientScanResultDto,
): void {
  try {
    sessionStorage.setItem(cacheStorageKey(paymentId), JSON.stringify(result));
  } catch {
    // sessionStorage 不可用时忽略；仍依赖后端 SUCCESS 拦截
  }
}

/**
 * 清除半成功扫码凭证缓存（院内落账成功或银联已退货后）。
 *
 * @param paymentId 支付草稿主键
 */
export function clearCachedClientScanResult(paymentId: number | string): void {
  try {
    sessionStorage.removeItem(cacheStorageKey(paymentId));
  } catch {
    // ignore
  }
}

/**
 * 是否存在可用的半成功扫码凭证（含 merTradeNo/tradeNo）。
 *
 * @param paymentId 支付草稿主键
 */
export function hasCachedClientScanSuccess(paymentId: number | string): boolean {
  const cached = peekCachedClientScanResult(paymentId);
  if (!cached) {
    return false;
  }
  return Boolean(
    String(cached.merTradeNo ?? '').trim() &&
      String(cached.tradeNo ?? '').trim() &&
      String(cached.respCode ?? '').trim() === '00',
  );
}
