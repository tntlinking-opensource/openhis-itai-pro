/**
 * 日结 / 交账现算长耗时接口的 HTTP 超时。
 *
 * 默认 Axios 为 10s；整日挂载现算常超过该阈值。
 * Axios：`timeout: 0` 表示不限制等待时间。
 * 可通过 `VITE_DAILY_SETTLEMENT_REQUEST_TIMEOUT_MS` 覆盖（毫秒；0=不超时）。
 */
export function dailySettlementLongRequestConfig(): { timeout: number } {
  const fromEnv = import.meta.env.VITE_DAILY_SETTLEMENT_REQUEST_TIMEOUT_MS;
  if (fromEnv === undefined || fromEnv === null || String(fromEnv).trim() === '') {
    // 默认 2 分钟：止血超时，同时避免无限挂起
    return { timeout: 120_000 };
  }
  const text = String(fromEnv).trim();
  if (text === '0') {
    return { timeout: 0 };
  }
  const parsed = Number(text);
  if (Number.isFinite(parsed) && parsed >= 0) {
    return { timeout: parsed };
  }
  return { timeout: 120_000 };
}
