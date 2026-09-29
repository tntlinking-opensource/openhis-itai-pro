import {
  extractRequestErrorCode,
  extractRequestErrorMessage,
} from '#/utils/http/stale-cross-role-error';

/** 业务空态：调用方自行处理，全局拦截器不弹错误 toast */
export const EXPECTED_EMPTY_RESPONSE_ERROR_CODES = new Set<string>([
  /** 住院结算 IF-IP-STL-07：尚无历史结算单（待结算页签正常态） */
  'EX-IP-STL-110',
]);

/** 业务空态文案：与码并列判定（医技站非 TECH 登录不弹错） */
export const EXPECTED_EMPTY_RESPONSE_MESSAGES = new Set<string>([
  '当前登录科室不是医技科',
  /** 计费页未挂载时偶发请求 / 门诊误入计费接口：不弹错 */
  '医技计费仅支持住院就诊',
]);

/** 是否为预期空态业务码/文案（如无历史结算单、非医技科） */
export function isExpectedEmptyResponseError(error: unknown): boolean {
  const code = extractRequestErrorCode(error);
  if (code != null && EXPECTED_EMPTY_RESPONSE_ERROR_CODES.has(code)) {
    return true;
  }
  const message = extractRequestErrorMessage(error);
  return message != null && EXPECTED_EMPTY_RESPONSE_MESSAGES.has(message);
}
