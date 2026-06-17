/**
 * 医保（CHS）长耗时接口的 HTTP 超时。
 *
 * Axios：`timeout: 0` 表示不限制等待时间（目录下载单批 13xx→9102→解压入库常超过 10 秒）。
 * 可通过环境变量 `VITE_CHS_REQUEST_TIMEOUT_MS` 覆盖（毫秒，正整数；设为 0 同样表示不超时）。
 */
export function chsLongRequestConfig(): { timeout: number } {
  const fromEnv = import.meta.env.VITE_CHS_REQUEST_TIMEOUT_MS;
  if (fromEnv === undefined || fromEnv === null || String(fromEnv).trim() === '') {
    return { timeout: 0 };
  }
  const text = String(fromEnv).trim();
  if (text === '0') {
    return { timeout: 0 };
  }
  const parsed = Number(text);
  if (Number.isFinite(parsed) && parsed >= 0) {
    return { timeout: parsed };
  }
  return { timeout: 0 };
}
