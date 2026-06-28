/** Stale 跨角色写失败统一提示（SYS-IMP-007 E1/E2） */
export const STALE_CROSS_ROLE_REFRESH_MESSAGE = '数据已变更，请刷新后重试';

/**
 * 跨角色 Stale / 源态冲突业务码（Gate 矩阵 §1～§3）。
 * 命中时前端统一文案，并触发列表 invalidate（E3）。
 */
export const STALE_CROSS_ROLE_ERROR_CODES = new Set<string>([
  // 门诊收费 / 签退
  'EX-OPC-411',
  // 住院签退
  'EX-IP-ORD-103',
  // 取消执行
  'EX-IP-CXL-203',
  'EX-IP-CXL-204',
  // 护士执行
  'EX-IP-EXE-301',
  'EX-IP-EXE-303',
  // 汇总 / 核发
  'EX-IP-SUM-201',
  'EX-IPS-201',
  // 入出转 / 登记
  'EX-IP-ATD-201',
  'EX-IP-ATD-207',
  'EX-IP-ATD-304',
  'EX-IP-ATD-402',
  'EX-IP-ATD-504',
  'EX-IP-REG-003',
  // 发药
  'MEDICATION_DISPENSE_INVALID_STATUS',
  'MEDICATION_DISPENSE_NOT_FOUND',
  // 预约 / 号源
  'APPOINTMENT_RULE_CONFLICT',
  'SLOT_003',
  'REG-BIZ-409',
]);

/** 从请求异常解析后端 errorCode */
export function extractRequestErrorCode(error: unknown): string | undefined {
  const err = error as {
    errorCode?: string;
    response?: { data?: { errorCode?: string } };
    data?: { errorCode?: string };
  };
  const code = err?.errorCode ?? err?.response?.data?.errorCode ?? err?.data?.errorCode;
  return typeof code === 'string' && code.trim().length > 0 ? code.trim() : undefined;
}

/** 是否为 Stale 跨角色 / 源态冲突错误（含 message 内嵌 errorCode 兜底） */
export function isStaleCrossRoleError(error: unknown): boolean {
  const code = extractRequestErrorCode(error);
  if (code && STALE_CROSS_ROLE_ERROR_CODES.has(code)) {
    return true;
  }
  const message =
    error instanceof Error
      ? error.message
      : typeof error === 'object' && error !== null && 'message' in error
        ? String((error as { message?: unknown }).message ?? '')
        : String(error ?? '');
  for (const staleCode of STALE_CROSS_ROLE_ERROR_CODES) {
    if (message.includes(staleCode)) {
      return true;
    }
  }
  return false;
}

/**
 * 全局错误拦截器用：命中 Stale 码则返回统一文案，否则 null 走默认展示。
 */
export function formatStaleCrossRoleError(
  errorCode: string | undefined,
  _message?: string,
): string | null {
  if (errorCode && STALE_CROSS_ROLE_ERROR_CODES.has(errorCode)) {
    return STALE_CROSS_ROLE_REFRESH_MESSAGE;
  }
  return null;
}

/**
 * Stale 写失败时刷新本地列表/床位图（E3）。
 * @returns 是否已识别为 Stale 并完成 refresh
 */
export async function refreshOnStaleCrossRoleError(
  error: unknown,
  refresh: () => void | Promise<void>,
): Promise<boolean> {
  if (!isStaleCrossRoleError(error)) {
    return false;
  }
  await refresh();
  return true;
}
