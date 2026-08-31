/**
 * 报表中心 HTTP 超时与请求封装。
 *
 * Axios 实例默认 10s；报表汇总/分页/导出跨多日常超时。
 * 所有 `api/report/**`、门诊费用明细报表等须走本模块，禁止裸用默认 timeout。
 *
 * Axios：`timeout: 0` 表示不限制等待时间。
 * 可通过 `VITE_REPORT_REQUEST_TIMEOUT_MS` 覆盖（毫秒；0=不超时）。
 */
import { publicRequestClient } from '#/api/request';

/** 报表默认超时：3 分钟。 */
const REPORT_DEFAULT_TIMEOUT_MS = 180_000;

/**
 * 解析报表请求超时毫秒。
 *
 * @returns 超时毫秒；0 表示不限制
 */
export function resolveReportRequestTimeoutMs(): number {
  const fromEnv = import.meta.env.VITE_REPORT_REQUEST_TIMEOUT_MS;
  // 未配置：默认 3 分钟
  if (fromEnv === undefined || fromEnv === null || String(fromEnv).trim() === '') {
    return REPORT_DEFAULT_TIMEOUT_MS;
  }
  const text = String(fromEnv).trim();
  // 显式 0：不限制等待（运维排障）
  if (text === '0') {
    return 0;
  }
  const parsed = Number(text);
  // 合法非负毫秒则采用；非法回落默认
  if (Number.isFinite(parsed) && parsed >= 0) {
    return parsed;
  }
  return REPORT_DEFAULT_TIMEOUT_MS;
}

/**
 * 报表长耗时请求配置（须调用本函数，禁止把函数引用当 config 传入）。
 *
 * @returns Axios 配置片段（含 timeout）
 */
export function reportLongRequestConfig(): { timeout: number } {
  return { timeout: resolveReportRequestTimeoutMs() };
}

/**
 * 合并报表超时到可选请求配置（调用方 timeout 优先）。
 *
 * @param config 可选 Axios 配置
 * @returns 已带报表超时的配置
 */
export function withReportTimeout(config?: { timeout?: number; [key: string]: unknown }): {
  timeout: number;
  [key: string]: unknown;
} {
  const base = reportLongRequestConfig();
  if (!config) {
    return base;
  }
  // 调用方显式 timeout（含 0）优先；未传则用报表默认
  if (config.timeout !== undefined && config.timeout !== null) {
    return { ...base, ...config, timeout: config.timeout };
  }
  return { ...base, ...config, timeout: base.timeout };
}

/**
 * 报表专用 POST：默认带长超时，避免漏传 config 仍走 10s。
 *
 * @param url 路径（与 publicRequestClient 一致）
 * @param data 请求体
 * @param config 可选覆盖（timeout 优先于报表默认）
 */
export function reportPost<T = unknown>(
  url: string,
  data?: unknown,
  config?: { timeout?: number; [key: string]: unknown },
): Promise<T> {
  return publicRequestClient.post<T>(url, data, withReportTimeout(config));
}
