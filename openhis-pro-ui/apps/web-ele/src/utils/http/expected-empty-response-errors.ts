import { extractRequestErrorCode } from '#/utils/http/stale-cross-role-error';

/** 业务空态：调用方自行处理，全局拦截器不弹错误 toast */
export const EXPECTED_EMPTY_RESPONSE_ERROR_CODES = new Set<string>([
  /** 住院结算 IF-IP-STL-07：尚无历史结算单（待结算页签正常态） */
  'EX-IP-STL-110',
]);

/** 是否为预期空态业务码（如无历史结算单） */
export function isExpectedEmptyResponseError(error: unknown): boolean {
  const code = extractRequestErrorCode(error);
  return code != null && EXPECTED_EMPTY_RESPONSE_ERROR_CODES.has(code);
}
