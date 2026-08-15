/**
 * 报表中心长耗时查询的 HTTP 超时。
 *
 * 默认 Axios 为 10s；跨多日汇总/明细分页/导出常超过该阈值。
 * Axios：`timeout: 0` 表示不限制等待时间。
 * 可通过 `VITE_REPORT_REQUEST_TIMEOUT_MS` 覆盖（毫秒；0=不超时）。
 *
 * @returns Axios 请求配置片段（仅含 timeout）
 */
export function reportLongRequestConfig(): { timeout: number } {
  const fromEnv = import.meta.env.VITE_REPORT_REQUEST_TIMEOUT_MS;
  // 未配置环境变量：默认 2 分钟，止血超时同时避免无限挂起
  if (fromEnv === undefined || fromEnv === null || String(fromEnv).trim() === '') {
    return { timeout: 120_000 };
  }
  const text = String(fromEnv).trim();
  // 显式 0：不限制等待（运维排障用）
  if (text === '0') {
    return { timeout: 0 };
  }
  const parsed = Number(text);
  // 合法非负毫秒则采用；非法值回落默认 2 分钟
  if (Number.isFinite(parsed) && parsed >= 0) {
    return { timeout: parsed };
  }
  return { timeout: 120_000 };
}
